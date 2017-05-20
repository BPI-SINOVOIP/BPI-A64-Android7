/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "modules.audio.audio_hal"
/*#define LOG_NDEBUG 0*/

#include <errno.h>
#include <inttypes.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/time.h>

#include <log/log.h>
#include <cutils/str_parms.h>
#include <cutils/properties.h>

#include <hardware/audio.h>
#include <hardware/audio_alsaops.h>
#include <hardware/hardware.h>

#include <system/audio.h>

#include <tinyalsa/asoundlib.h>

#include <audio_utils/channels.h>

#include <dirent.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <sound/asound.h>


#define PCM_DEV_STR "pcm"
#if TARGET_AUDIO_PRIMARY
#define AUDIO_STR "ALC662 rev1 Analog"
#else
#define AUDIO_STR "USB Audio"
#endif
#define MAX_PATH_LEN 30

#define NBR_RETRIES 5
#define RETRY_WAIT_USEC 20000

/* FOR TESTING:
 * Set k_force_channels to force the number of channels to present to AudioFlinger.
 *   0 disables (this is default: present the device channels to AudioFlinger).
 *   2 forces to legacy stereo mode.
 *
 * Others values can be tried (up to 8).
 * TODO: AudioFlinger cannot support more than 8 active output channels
 * at this time, so limiting logic needs to be put here or communicated from above.
 */
static const unsigned k_force_channels = 0;

#include "alsa_device_profile.h"
#include "alsa_device_proxy.h"
#include "alsa_logging.h"

#define DEFAULT_INPUT_BUFFER_SIZE_MS 20

struct audio_device {
    struct audio_hw_device hw_device;

    pthread_mutex_t lock; /* see note below on mutex acquisition order */

    /* output */
    alsa_device_profile out_profile;

    /* input */
    alsa_device_profile in_profile;

    bool mic_muted;

    bool standby;
#if TARGET_AUDIO_PRIMARY
    unsigned int master_volume;
#endif
};

struct stream_out {
    struct audio_stream_out stream;

    pthread_mutex_t lock;               /* see note below on mutex acquisition order */
    pthread_mutex_t pre_lock;           /* acquire before lock to avoid DOS by playback thread */
    bool standby;

    struct audio_device *dev;           /* hardware information - only using this for the lock */

    alsa_device_profile * profile;      /* Points to the alsa_device_profile in the audio_device */
    alsa_device_proxy proxy;            /* state of the stream */

    unsigned hal_channel_count;         /* channel count exposed to AudioFlinger.
                                         * This may differ from the device channel count when
                                         * the device is not compatible with AudioFlinger
                                         * capabilities, e.g. exposes too many channels or
                                         * too few channels. */
    audio_channel_mask_t hal_channel_mask;   /* channel mask exposed to AudioFlinger. */

    void * conversion_buffer;           /* any conversions are put into here
                                         * they could come from here too if
                                         * there was a previous conversion */
    size_t conversion_buffer_size;      /* in bytes */
};

struct stream_in {
    struct audio_stream_in stream;

    pthread_mutex_t lock;               /* see note below on mutex acquisition order */
    pthread_mutex_t pre_lock;           /* acquire before lock to avoid DOS by capture thread */
    bool standby;

    struct audio_device *dev;           /* hardware information - only using this for the lock */

    alsa_device_profile * profile;      /* Points to the alsa_device_profile in the audio_device */
    alsa_device_proxy proxy;            /* state of the stream */

    unsigned hal_channel_count;         /* channel count exposed to AudioFlinger.
                                         * This may differ from the device channel count when
                                         * the device is not compatible with AudioFlinger
                                         * capabilities, e.g. exposes too many channels or
                                         * too few channels. */
    audio_channel_mask_t hal_channel_mask;   /* channel mask exposed to AudioFlinger. */

    /* We may need to read more data from the device in order to data reduce to 16bit, 4chan */
    void * conversion_buffer;           /* any conversions are put into here
                                         * they could come from here too if
                                         * there was a previous conversion */
    size_t conversion_buffer_size;      /* in bytes */
};

/*
 * NOTE: when multiple mutexes have to be acquired, always take the
 * stream_in or stream_out mutex first, followed by the audio_device mutex.
 * stream pre_lock is always acquired before stream lock to prevent starvation of control thread by
 * higher priority playback or capture thread.
 */


static int in_stream_card_number = -1, out_stream_card_number = -1;


/*
 * Examines a pcm-device file to see if its a USB Audio device and
 * returns its card-number. If no match, returns -1.
 */
static int first_valid_sound_card(char *pcm_name, bool is_out_stream)
{
    int fd;
    char pcm_dev_path[MAX_PATH_LEN];
    struct snd_pcm_info info;
    char type;
    int pcm_name_length;

    ALOGV("%s enter",__func__);

    pcm_name_length = strlen(pcm_name);
    if (pcm_name_length < 2) {
        return -1;
    }
    type = is_out_stream ? 'p' : 'c';
    /* If pcm out then filename must end with 0p/0c */
    if ((pcm_name[pcm_name_length -2] != '0') && (pcm_name[pcm_name_length - 1] != type)) {
        ALOGV("%s exit",__func__);
        return -1;
    }

    snprintf(pcm_dev_path, sizeof(pcm_dev_path), "/dev/snd/%s", pcm_name);
    fd = open(pcm_dev_path, O_RDONLY);

    if (fd != -1) {
        if (!(ioctl(fd, SNDRV_PCM_IOCTL_INFO, &info))) {
            if (strstr(info.id, AUDIO_STR)) {
                close(fd);
                ALOGV("%s exit",__func__);
                return info.card;
            }
        } else {
            ALOGE("ioctl failed for file: %s", pcm_dev_path);
        }

        close(fd);
    }

    ALOGV("%s exit",__func__);
    return -1;
}

/*
 * Returns the number of the first valid USB Audio card
 * If none is found, returns -1.
 */
static int get_first_sound_card(bool is_out_stream)
{
    DIR *dir;
    struct dirent *de = NULL;
    int card_nr;

    ALOGV("%s enter",__func__);

    dir = opendir("/dev/snd");
    if (dir == NULL) {
        ALOGE("Could not open directory /dev/snd");
        ALOGV("%s exit",__func__);
        return -1;
    }

    while ((de = readdir(dir))) {
        if (strncmp(de->d_name, PCM_DEV_STR, sizeof(PCM_DEV_STR) - 1) == 0) {
            if ((card_nr = first_valid_sound_card(de->d_name, is_out_stream)) != -1) {
                closedir(dir);
                ALOGV("%s exit",__func__);
                return card_nr;
            }
        }
    }

    closedir(dir);
    ALOGW("No usb-card found in /dev/snd");
    ALOGV("%s exit",__func__);
    return -1;
}

static bool parse_card_device_params(bool is_out_stream, int *card, int *device)
{
    int try_time;
    int found_card = -1;

    if (is_out_stream) {
        if (out_stream_card_number != -1) {
            *card = out_stream_card_number;
            *device = 0;
            return true;
        }
    } else {
        if (in_stream_card_number != -1) {
            *card = in_stream_card_number;
            *device = 0;
            return true;
        }
    }

    for (try_time = 0; try_time < NBR_RETRIES; try_time++) {
        found_card = get_first_sound_card(is_out_stream);
        if (found_card == -1)
            usleep(RETRY_WAIT_USEC);
        else
            break;
    }

    if (found_card == -1) {
        *card = -1;
        *device = -1;
        return false;
    }

    if (is_out_stream) {
        out_stream_card_number = found_card;
    } else {
        in_stream_card_number = found_card;
    }

    *card = found_card;
    *device = 0;

    return true;
}

static char * device_get_parameters(alsa_device_profile * profile, const char * keys)
{
    if (profile->card < 0 || profile->device < 0) {
        return strdup("");
    }

    struct str_parms *query = str_parms_create_str(keys);
    struct str_parms *result = str_parms_create();

    /* These keys are from hardware/libhardware/include/audio.h */
    /* supported sample rates */
    if (str_parms_has_key(query, AUDIO_PARAMETER_STREAM_SUP_SAMPLING_RATES)) {
        char* rates_list = profile_get_sample_rate_strs(profile);
        str_parms_add_str(result, AUDIO_PARAMETER_STREAM_SUP_SAMPLING_RATES,
                          rates_list);
        free(rates_list);
    }

    /* supported channel counts */
    if (str_parms_has_key(query, AUDIO_PARAMETER_STREAM_SUP_CHANNELS)) {
        char* channels_list = profile_get_channel_count_strs(profile);
        str_parms_add_str(result, AUDIO_PARAMETER_STREAM_SUP_CHANNELS,
                          channels_list);
        free(channels_list);
    }

    /* supported sample formats */
    if (str_parms_has_key(query, AUDIO_PARAMETER_STREAM_SUP_FORMATS)) {
        char * format_params = profile_get_format_strs(profile);
        str_parms_add_str(result, AUDIO_PARAMETER_STREAM_SUP_FORMATS,
                          format_params);
        free(format_params);
    }
    str_parms_destroy(query);

    char* result_str = str_parms_to_str(result);
    str_parms_destroy(result);

    ALOGV("device_get_parameters = %s", result_str);

    return result_str;
}

void lock_input_stream(struct stream_in *in)
{
    pthread_mutex_lock(&in->pre_lock);
    pthread_mutex_lock(&in->lock);
    pthread_mutex_unlock(&in->pre_lock);
}

void lock_output_stream(struct stream_out *out)
{
    pthread_mutex_lock(&out->pre_lock);
    pthread_mutex_lock(&out->lock);
    pthread_mutex_unlock(&out->pre_lock);
}

/*
 * HAl Functions
 */
/**
 * NOTE: when multiple mutexes have to be acquired, always respect the
 * following order: hw device > out stream
 */

/*
 * OUT functions
 */
static uint32_t out_get_sample_rate(const struct audio_stream *stream)
{
    uint32_t rate = proxy_get_sample_rate(&((struct stream_out*)stream)->proxy);
    ALOGV("out_get_sample_rate() = %d", rate);
    return rate;
}

static int out_set_sample_rate(struct audio_stream *stream, uint32_t rate)
{
    return 0;
}

static size_t out_get_buffer_size(const struct audio_stream *stream)
{
    const struct stream_out* out = (const struct stream_out*)stream;
    size_t buffer_size =
        proxy_get_period_size(&out->proxy) * audio_stream_out_frame_size(&(out->stream));
    return buffer_size;
}

static uint32_t out_get_channels(const struct audio_stream *stream)
{
    const struct stream_out *out = (const struct stream_out*)stream;
    return out->hal_channel_mask;
}

static audio_format_t out_get_format(const struct audio_stream *stream)
{
    /* Note: The HAL doesn't do any FORMAT conversion at this time. It
     * Relies on the framework to provide data in the specified format.
     * This could change in the future.
     */
    alsa_device_proxy * proxy = &((struct stream_out*)stream)->proxy;
    audio_format_t format = audio_format_from_pcm_format(proxy_get_format(proxy));
    return format;
}

static int out_set_format(struct audio_stream *stream, audio_format_t format)
{
    return 0;
}

static int out_standby(struct audio_stream *stream)
{
    struct stream_out *out = (struct stream_out *)stream;
    lock_output_stream(out);
    if (!out->standby) {
        pthread_mutex_lock(&out->dev->lock);
        proxy_close(&out->proxy);
        pthread_mutex_unlock(&out->dev->lock);
        out->standby = true;
    }
    pthread_mutex_unlock(&out->lock);

    return 0;
}

static int out_dump(const struct audio_stream *stream, int fd)
{
    return 0;
}

static int out_set_parameters(struct audio_stream *stream, const char *kvpairs)
{
    ALOGV("out_set_parameters() keys:%s", kvpairs);

    struct stream_out *out = (struct stream_out *)stream;

    int routing = 0;
    int ret_value = 0;
    int card = -1;
    int device = -1;

    if (!parse_card_device_params(true, &card, &device)) {
        // nothing to do
        return ret_value;
    }

    lock_output_stream(out);
    /* Lock the device because that is where the profile lives */
    pthread_mutex_lock(&out->dev->lock);

    if (!profile_is_cached_for(out->profile, card, device)) {
        /* cannot read pcm device info if playback is active */
        if (!out->standby)
            ret_value = -ENOSYS;
        else {
            int saved_card = out->profile->card;
            int saved_device = out->profile->device;
            out->profile->card = card;
            out->profile->device = device;
            ret_value = profile_read_device_info(out->profile) ? 0 : -EINVAL;
            if (ret_value != 0) {
                out->profile->card = saved_card;
                out->profile->device = saved_device;
            }
        }
    }

    pthread_mutex_unlock(&out->dev->lock);
    pthread_mutex_unlock(&out->lock);

    return ret_value;
}

static char * out_get_parameters(const struct audio_stream *stream, const char *keys)
{
    struct stream_out *out = (struct stream_out *)stream;
    lock_output_stream(out);
    pthread_mutex_lock(&out->dev->lock);

    char * params_str =  device_get_parameters(out->profile, keys);

    pthread_mutex_unlock(&out->lock);
    pthread_mutex_unlock(&out->dev->lock);

    return params_str;
}

static uint32_t out_get_latency(const struct audio_stream_out *stream)
{
    alsa_device_proxy * proxy = &((struct stream_out*)stream)->proxy;
    return proxy_get_latency(proxy);
}

static int out_set_volume(struct audio_stream_out *stream, float left, float right)
{
    return -ENOSYS;
}

/* must be called with hw device and output stream mutexes locked */
static int start_output_stream(struct stream_out *out)
{
    ALOGV("start_output_stream(card:%d device:%d)", out->profile->card, out->profile->device);

    return proxy_open(&out->proxy);
}

static ssize_t out_write(struct audio_stream_out *stream, const void* buffer, size_t bytes)
{
    int ret;
    struct stream_out *out = (struct stream_out *)stream;

    lock_output_stream(out);
    if (out->standby) {
        pthread_mutex_lock(&out->dev->lock);
        ret = start_output_stream(out);
        pthread_mutex_unlock(&out->dev->lock);
        if (ret != 0) {
            goto err;
        }
        out->standby = false;
    }

    alsa_device_proxy* proxy = &out->proxy;
    const void * write_buff = buffer;
    int num_write_buff_bytes = bytes;
    const int num_device_channels = proxy_get_channel_count(proxy); /* what we told alsa */
    const int num_req_channels = out->hal_channel_count; /* what we told AudioFlinger */
    if (num_device_channels != num_req_channels) {
        /* allocate buffer */
        const size_t required_conversion_buffer_size =
                 bytes * num_device_channels / num_req_channels;
        if (required_conversion_buffer_size > out->conversion_buffer_size) {
            out->conversion_buffer_size = required_conversion_buffer_size;
            out->conversion_buffer = realloc(out->conversion_buffer,
                                             out->conversion_buffer_size);
        }
        /* convert data */
        const audio_format_t audio_format = out_get_format(&(out->stream.common));
        const unsigned sample_size_in_bytes = audio_bytes_per_sample(audio_format);
        num_write_buff_bytes =
                adjust_channels(write_buff, num_req_channels,
                                out->conversion_buffer, num_device_channels,
                                sample_size_in_bytes, num_write_buff_bytes);
        write_buff = out->conversion_buffer;
    }

    if (write_buff != NULL && num_write_buff_bytes != 0) {
        proxy_write(&out->proxy, write_buff, num_write_buff_bytes);
    }

    pthread_mutex_unlock(&out->lock);

    return bytes;

err:
    pthread_mutex_unlock(&out->lock);
    if (ret != 0) {
        usleep(bytes * 1000000 / audio_stream_out_frame_size(stream) /
               out_get_sample_rate(&stream->common));
    }

    return bytes;
}

static int out_get_render_position(const struct audio_stream_out *stream, uint32_t *dsp_frames)
{
    return -EINVAL;
}

static int out_get_presentation_position(const struct audio_stream_out *stream,
                                         uint64_t *frames, struct timespec *timestamp)
{
    struct stream_out *out = (struct stream_out *)stream; // discard const qualifier
    lock_output_stream(out);

    const alsa_device_proxy *proxy = &out->proxy;
    const int ret = proxy_get_presentation_position(proxy, frames, timestamp);

    pthread_mutex_unlock(&out->lock);
    ALOGV("out_get_presentation_position() status:%d  frames:%llu",
            ret, (unsigned long long)*frames);
    return ret;
}

static int out_add_audio_effect(const struct audio_stream *stream, effect_handle_t effect)
{
    return 0;
}

static int out_remove_audio_effect(const struct audio_stream *stream, effect_handle_t effect)
{
    return 0;
}

static int out_get_next_write_timestamp(const struct audio_stream_out *stream, int64_t *timestamp)
{
    return -EINVAL;
}

static int adev_open_output_stream(struct audio_hw_device *dev,
                                   audio_io_handle_t handle,
                                   audio_devices_t devices,
                                   audio_output_flags_t flags,
                                   struct audio_config *config,
                                   struct audio_stream_out **stream_out,
                                   const char *address /*__unused*/)
{
    ALOGV("adev_open_output_stream() handle:0x%X, device:0x%X, flags:0x%X, addr:%s",
          handle, devices, flags, address);

    struct audio_device *adev = (struct audio_device *)dev;

    struct stream_out *out;
    out = (struct stream_out *)calloc(1, sizeof(struct stream_out));
    if (!out)
        return -ENOMEM;

    /* setup function pointers */
    out->stream.common.get_sample_rate = out_get_sample_rate;
    out->stream.common.set_sample_rate = out_set_sample_rate;
    out->stream.common.get_buffer_size = out_get_buffer_size;
    out->stream.common.get_channels = out_get_channels;
    out->stream.common.get_format = out_get_format;
    out->stream.common.set_format = out_set_format;
    out->stream.common.standby = out_standby;
    out->stream.common.dump = out_dump;
    out->stream.common.set_parameters = out_set_parameters;
    out->stream.common.get_parameters = out_get_parameters;
    out->stream.common.add_audio_effect = out_add_audio_effect;
    out->stream.common.remove_audio_effect = out_remove_audio_effect;
    out->stream.get_latency = out_get_latency;
    out->stream.set_volume = out_set_volume;
    out->stream.write = out_write;
    out->stream.get_render_position = out_get_render_position;
    out->stream.get_presentation_position = out_get_presentation_position;
    out->stream.get_next_write_timestamp = out_get_next_write_timestamp;

    pthread_mutex_init(&out->lock, (const pthread_mutexattr_t *) NULL);
    pthread_mutex_init(&out->pre_lock, (const pthread_mutexattr_t *) NULL);

    out->dev = adev;
    pthread_mutex_lock(&adev->lock);
    out->profile = &adev->out_profile;

    // build this to hand to the alsa_device_proxy
    struct pcm_config proxy_config;
    memset(&proxy_config, 0, sizeof(proxy_config));

    /* Pull out the card/device pair */
    parse_card_device_params(true, &(out->profile->card), &(out->profile->device));

    profile_read_device_info(out->profile);

    pthread_mutex_unlock(&adev->lock);

    int ret = 0;

    /* Rate */
    if (config->sample_rate == 0) {
        proxy_config.rate = config->sample_rate = profile_get_default_sample_rate(out->profile);
    } else if (profile_is_sample_rate_valid(out->profile, config->sample_rate)) {
        proxy_config.rate = config->sample_rate;
    } else {
        ALOGE("%s: The requested sample rate (%d) is not valid", __func__, config->sample_rate);
        proxy_config.rate = config->sample_rate = profile_get_default_sample_rate(out->profile);
        ret = -EINVAL;
    }

    /* Format */
    if (config->format == AUDIO_FORMAT_DEFAULT) {
        proxy_config.format = profile_get_default_format(out->profile);
        config->format = audio_format_from_pcm_format(proxy_config.format);
    } else {
        enum pcm_format fmt = pcm_format_from_audio_format(config->format);
        if (profile_is_format_valid(out->profile, fmt)) {
            proxy_config.format = fmt;
        } else {
            ALOGE("%s: The requested format (0x%x) is not valid", __func__, config->format);
            proxy_config.format = profile_get_default_format(out->profile);
            config->format = audio_format_from_pcm_format(proxy_config.format);
            ret = -EINVAL;
        }
    }

    /* Channels */
    unsigned proposed_channel_count = 0;
    if (k_force_channels) {
        proposed_channel_count = k_force_channels;
    } else if (config->channel_mask == AUDIO_CHANNEL_NONE) {
        proposed_channel_count =  profile_get_default_channel_count(out->profile);
    }
    if (proposed_channel_count != 0) {
        if (proposed_channel_count <= FCC_2) {
            // use channel position mask for mono and stereo
            config->channel_mask = audio_channel_out_mask_from_count(proposed_channel_count);
        } else {
            // use channel index mask for multichannel
            config->channel_mask =
                    audio_channel_mask_for_index_assignment_from_count(proposed_channel_count);
        }
        out->hal_channel_count = proposed_channel_count;
    } else {
        out->hal_channel_count = audio_channel_count_from_out_mask(config->channel_mask);
    }
    /* we can expose any channel mask, and emulate internally based on channel count. */
    out->hal_channel_mask = config->channel_mask;

    /* no validity checks are needed as proxy_prepare() forces channel_count to be valid.
     * and we emulate any channel count discrepancies in out_write(). */
    proxy_config.channels = proposed_channel_count;

    proxy_prepare(&out->proxy, out->profile, &proxy_config);

    /* TODO The retry mechanism isn't implemented in AudioPolicyManager/AudioFlinger. */
    ret = 0;

    out->conversion_buffer = NULL;
    out->conversion_buffer_size = 0;

    out->standby = true;

    *stream_out = &out->stream;

    return ret;

err_open:
    free(out);
    *stream_out = NULL;
    return -ENOSYS;
}

static void adev_close_output_stream(struct audio_hw_device *dev,
                                     struct audio_stream_out *stream)
{
    struct stream_out *out = (struct stream_out *)stream;
    ALOGV("adev_close_output_stream(c:%d d:%d)", out->profile->card, out->profile->device);
    /* Close the pcm device */
    out_standby(&stream->common);

    free(out->conversion_buffer);

    out->conversion_buffer = NULL;
    out->conversion_buffer_size = 0;

    free(stream);
}

static size_t adev_get_input_buffer_size(const struct audio_hw_device *dev,
                                         const struct audio_config *config)
{
    /* TODO This needs to be calculated based on format/channels/rate */
    return 320;
}

/*
 * IN functions
 */
static uint32_t in_get_sample_rate(const struct audio_stream *stream)
{
    uint32_t rate = proxy_get_sample_rate(&((const struct stream_in *)stream)->proxy);
    ALOGV("in_get_sample_rate() = %d", rate);
    return rate;
}

static int in_set_sample_rate(struct audio_stream *stream, uint32_t rate)
{
    ALOGV("in_set_sample_rate(%d) - NOPE", rate);
    return -ENOSYS;
}

static size_t in_get_buffer_size(const struct audio_stream *stream)
{
    const struct stream_in * in = ((const struct stream_in*)stream);
    return proxy_get_period_size(&in->proxy) * audio_stream_in_frame_size(&(in->stream));
}

static uint32_t in_get_channels(const struct audio_stream *stream)
{
    const struct stream_in *in = (const struct stream_in*)stream;
    return in->hal_channel_mask;
}

static audio_format_t in_get_format(const struct audio_stream *stream)
{
     alsa_device_proxy *proxy = &((struct stream_in*)stream)->proxy;
     audio_format_t format = audio_format_from_pcm_format(proxy_get_format(proxy));
     return format;
}

static int in_set_format(struct audio_stream *stream, audio_format_t format)
{
    ALOGV("in_set_format(%d) - NOPE", format);

    return -ENOSYS;
}

static int in_standby(struct audio_stream *stream)
{
    struct stream_in *in = (struct stream_in *)stream;

    lock_input_stream(in);
    if (!in->standby) {
        pthread_mutex_lock(&in->dev->lock);
        proxy_close(&in->proxy);
        pthread_mutex_unlock(&in->dev->lock);
        in->standby = true;
    }

    pthread_mutex_unlock(&in->lock);

    return 0;
}

static int in_dump(const struct audio_stream *stream, int fd)
{
    return 0;
}

static int in_set_parameters(struct audio_stream *stream, const char *kvpairs)
{
    ALOGV("in_set_parameters() keys:%s", kvpairs);

    struct stream_in *in = (struct stream_in *)stream;

    char value[32];
    int param_val;
    int routing = 0;
    int ret_value = 0;
    int card = -1;
    int device = -1;

    if (!parse_card_device_params(false, &card, &device)) {
        // nothing to do
        return ret_value;
    }

    lock_input_stream(in);
    pthread_mutex_lock(&in->dev->lock);

    if (card >= 0 && device >= 0 && !profile_is_cached_for(in->profile, card, device)) {
        /* cannot read pcm device info if playback is active */
        if (!in->standby)
            ret_value = -ENOSYS;
        else {
            int saved_card = in->profile->card;
            int saved_device = in->profile->device;
            in->profile->card = card;
            in->profile->device = device;
            ret_value = profile_read_device_info(in->profile) ? 0 : -EINVAL;
            if (ret_value != 0) {
                in->profile->card = saved_card;
                in->profile->device = saved_device;
            }
        }
    }

    pthread_mutex_unlock(&in->dev->lock);
    pthread_mutex_unlock(&in->lock);

    return ret_value;
}

static char * in_get_parameters(const struct audio_stream *stream, const char *keys)
{
    struct stream_in *in = (struct stream_in *)stream;

    lock_input_stream(in);
    pthread_mutex_lock(&in->dev->lock);

    char * params_str =  device_get_parameters(in->profile, keys);

    pthread_mutex_unlock(&in->dev->lock);
    pthread_mutex_unlock(&in->lock);

    return params_str;
}

static int in_add_audio_effect(const struct audio_stream *stream, effect_handle_t effect)
{
    return 0;
}

static int in_remove_audio_effect(const struct audio_stream *stream, effect_handle_t effect)
{
    return 0;
}

static int in_set_gain(struct audio_stream_in *stream, float gain)
{
    return 0;
}

/* must be called with hw device and output stream mutexes locked */
static int start_input_stream(struct stream_in *in)
{
    ALOGV("ustart_input_stream(card:%d device:%d)", in->profile->card, in->profile->device);

    return proxy_open(&in->proxy);
}

/* TODO mutex stuff here (see out_write) */
static ssize_t in_read(struct audio_stream_in *stream, void* buffer, size_t bytes)
{
    size_t num_read_buff_bytes = 0;
    void * read_buff = buffer;
    void * out_buff = buffer;
    int ret = 0;

    struct stream_in * in = (struct stream_in *)stream;

    lock_input_stream(in);
    if (in->standby) {
        pthread_mutex_lock(&in->dev->lock);
        ret = start_input_stream(in);
        pthread_mutex_unlock(&in->dev->lock);
        if (ret != 0) {
            goto err;
        }
        in->standby = false;
    }

    alsa_device_profile * profile = in->profile;

    /*
     * OK, we need to figure out how much data to read to be able to output the requested
     * number of bytes in the HAL format (16-bit, stereo).
     */
    num_read_buff_bytes = bytes;
    int num_device_channels = proxy_get_channel_count(&in->proxy); /* what we told Alsa */
    int num_req_channels = in->hal_channel_count; /* what we told AudioFlinger */

    if (num_device_channels != num_req_channels) {
        num_read_buff_bytes = (num_device_channels * num_read_buff_bytes) / num_req_channels;
    }

    /* Setup/Realloc the conversion buffer (if necessary). */
    if (num_read_buff_bytes != bytes) {
        if (num_read_buff_bytes > in->conversion_buffer_size) {
            /*TODO Remove this when AudioPolicyManger/AudioFlinger support arbitrary formats
              (and do these conversions themselves) */
            in->conversion_buffer_size = num_read_buff_bytes;
            in->conversion_buffer = realloc(in->conversion_buffer, in->conversion_buffer_size);
        }
        read_buff = in->conversion_buffer;
    }

    ret = proxy_read(&in->proxy, read_buff, num_read_buff_bytes);
    if (ret == 0) {
        if (num_device_channels != num_req_channels) {
            // ALOGV("chans dev:%d req:%d", num_device_channels, num_req_channels);

            out_buff = buffer;
            /* Num Channels conversion */
            if (num_device_channels != num_req_channels) {
                audio_format_t audio_format = in_get_format(&(in->stream.common));
                unsigned sample_size_in_bytes = audio_bytes_per_sample(audio_format);

                num_read_buff_bytes =
                    adjust_channels(read_buff, num_device_channels,
                                    out_buff, num_req_channels,
                                    sample_size_in_bytes, num_read_buff_bytes);
            }
        }

        /* no need to acquire in->dev->lock to read mic_muted here as we don't change its state */
        if (num_read_buff_bytes > 0 && in->dev->mic_muted)
            memset(buffer, 0, num_read_buff_bytes);
    } else {
        num_read_buff_bytes = 0; // reset the value after USB headset is unplugged
    }

err:
    pthread_mutex_unlock(&in->lock);

    return num_read_buff_bytes;
}

static uint32_t in_get_input_frames_lost(struct audio_stream_in *stream)
{
    return 0;
}

static int adev_open_input_stream(struct audio_hw_device *dev,
                                  audio_io_handle_t handle,
                                  audio_devices_t devices,
                                  struct audio_config *config,
                                  struct audio_stream_in **stream_in,
                                  audio_input_flags_t flags __unused,
                                  const char *address /*__unused*/,
                                  audio_source_t source __unused)
{
    ALOGV("in adev_open_input_stream() rate:%" PRIu32 ", chanMask:0x%" PRIX32 ", fmt:%" PRIu8,
          config->sample_rate, config->channel_mask, config->format);

    struct stream_in *in = (struct stream_in *)calloc(1, sizeof(struct stream_in));
    int ret = 0;

    if (in == NULL)
        return -ENOMEM;

    /* setup function pointers */
    in->stream.common.get_sample_rate = in_get_sample_rate;
    in->stream.common.set_sample_rate = in_set_sample_rate;
    in->stream.common.get_buffer_size = in_get_buffer_size;
    in->stream.common.get_channels = in_get_channels;
    in->stream.common.get_format = in_get_format;
    in->stream.common.set_format = in_set_format;
    in->stream.common.standby = in_standby;
    in->stream.common.dump = in_dump;
    in->stream.common.set_parameters = in_set_parameters;
    in->stream.common.get_parameters = in_get_parameters;
    in->stream.common.add_audio_effect = in_add_audio_effect;
    in->stream.common.remove_audio_effect = in_remove_audio_effect;

    in->stream.set_gain = in_set_gain;
    in->stream.read = in_read;
    in->stream.get_input_frames_lost = in_get_input_frames_lost;

    pthread_mutex_init(&in->lock, (const pthread_mutexattr_t *) NULL);
    pthread_mutex_init(&in->pre_lock, (const pthread_mutexattr_t *) NULL);

    in->dev = (struct audio_device *)dev;
    pthread_mutex_lock(&in->dev->lock);

    in->profile = &in->dev->in_profile;

    struct pcm_config proxy_config;
    memset(&proxy_config, 0, sizeof(proxy_config));

    /* Pull out the card/device pair */
    parse_card_device_params(false, &(in->profile->card), &(in->profile->device));

    profile_read_device_info(in->profile);
    pthread_mutex_unlock(&in->dev->lock);

    /* Rate */
    if (config->sample_rate == 0) {
        proxy_config.rate = config->sample_rate = profile_get_default_sample_rate(in->profile);
    } else if (profile_is_sample_rate_valid(in->profile, config->sample_rate)) {
        proxy_config.rate = config->sample_rate;
    } else {
        ALOGE("%s: The requested sample rate (%d) is not valid", __func__, config->sample_rate);
        proxy_config.rate = config->sample_rate = profile_get_default_sample_rate(in->profile);
        ret = -EINVAL;
    }

    /* Format */
    if (config->format == AUDIO_FORMAT_DEFAULT) {
        proxy_config.format = profile_get_default_format(in->profile);
        config->format = audio_format_from_pcm_format(proxy_config.format);
    } else {
        enum pcm_format fmt = pcm_format_from_audio_format(config->format);
        if (profile_is_format_valid(in->profile, fmt)) {
            proxy_config.format = fmt;
        } else {
            ALOGE("%s: The requested format (0x%x) is not valid", __func__, config->format);
            proxy_config.format = profile_get_default_format(in->profile);
            config->format = audio_format_from_pcm_format(proxy_config.format);
            ret = -EINVAL;
        }
    }

    /* Channels */
    unsigned proposed_channel_count = 0;
    if (k_force_channels) {
        proposed_channel_count = k_force_channels;
    } else if (config->channel_mask == AUDIO_CHANNEL_NONE) {
        proposed_channel_count = profile_get_default_channel_count(in->profile);
    }
    if (proposed_channel_count != 0) {
        config->channel_mask = audio_channel_in_mask_from_count(proposed_channel_count);
        if (config->channel_mask == AUDIO_CHANNEL_INVALID)
            config->channel_mask =
                    audio_channel_mask_for_index_assignment_from_count(proposed_channel_count);
        in->hal_channel_count = proposed_channel_count;
    } else {
        in->hal_channel_count = audio_channel_count_from_in_mask(config->channel_mask);
    }
    /* we can expose any channel mask, and emulate internally based on channel count. */
    in->hal_channel_mask = config->channel_mask;

    proxy_config.channels = profile_get_default_channel_count(in->profile);
    proxy_prepare(&in->proxy, in->profile, &proxy_config);

    in->standby = true;

    in->conversion_buffer = NULL;
    in->conversion_buffer_size = 0;

    *stream_in = &in->stream;

    return ret;
}

static void adev_close_input_stream(struct audio_hw_device *dev, struct audio_stream_in *stream)
{
    struct stream_in *in = (struct stream_in *)stream;

    /* Close the pcm device */
    in_standby(&stream->common);

    free(in->conversion_buffer);

    free(stream);
}

/*
 * ADEV Functions
 */
static int adev_set_parameters(struct audio_hw_device *dev, const char *kvpairs)
{
    return 0;
}

static char * adev_get_parameters(const struct audio_hw_device *dev, const char *keys)
{
    return strdup("");
}

static int adev_init_check(const struct audio_hw_device *dev)
{
    return 0;
}

static int adev_set_voice_volume(struct audio_hw_device *dev, float volume)
{
    return -ENOSYS;
}

static int adev_set_master_volume(struct audio_hw_device *dev, float volume)
{
#if TARGET_AUDIO_PRIMARY
    struct mixer *mixer;
    struct mixer_ctl *ctl;
    struct audio_device * adev = (struct audio_device *)dev;

    if ((0 > volume) || (1 < volume) || (NULL == adev))
      return -EINVAL;

    pthread_mutex_lock(&adev->lock);
    adev->master_volume = (int)(volume*100);

    if (!(mixer = mixer_open(1))) {
      pthread_mutex_unlock(&adev->lock);
      return -ENOSYS;
    }

    ctl = mixer_get_ctl(mixer,29);
    mixer_ctl_set_value(ctl,0,adev->master_volume);
    mixer_close(mixer);
    pthread_mutex_unlock(&adev->lock);
    return 0;
#else
    return -ENOSYS;
#endif
}

static int adev_set_mode(struct audio_hw_device *dev, audio_mode_t mode)
{
    return 0;
}

static int adev_set_mic_mute(struct audio_hw_device *dev, bool state)
{
    struct audio_device * adev = (struct audio_device *)dev;
    pthread_mutex_lock(&adev->lock);
    adev->mic_muted = state;
    pthread_mutex_unlock(&adev->lock);
    return -ENOSYS;
}

static int adev_get_mic_mute(const struct audio_hw_device *dev, bool *state)
{
    return -ENOSYS;
}

static int adev_dump(const audio_hw_device_t *device, int fd)
{
    return 0;
}

static int adev_close(hw_device_t *device)
{
    struct audio_device *adev = (struct audio_device *)device;
    free(device);

    return 0;
}

static int adev_open(const hw_module_t* module, const char* name, hw_device_t** device)
{
#if TARGET_AUDIO_PRIMARY
    struct mixer *mixer;
    struct mixer_ctl *ctl;
#endif
    if (strcmp(name, AUDIO_HARDWARE_INTERFACE) != 0)
        return -EINVAL;

    struct audio_device *adev = calloc(1, sizeof(struct audio_device));
    if (!adev)
        return -ENOMEM;

    profile_init(&adev->out_profile, PCM_OUT);
    profile_init(&adev->in_profile, PCM_IN);

    adev->hw_device.common.tag = HARDWARE_DEVICE_TAG;
    adev->hw_device.common.version = AUDIO_DEVICE_API_VERSION_2_0;
    adev->hw_device.common.module = (struct hw_module_t *)module;
    adev->hw_device.common.close = adev_close;

    adev->hw_device.init_check = adev_init_check;
    adev->hw_device.set_voice_volume = adev_set_voice_volume;
    adev->hw_device.set_master_volume = adev_set_master_volume;
    adev->hw_device.set_mode = adev_set_mode;
    adev->hw_device.set_mic_mute = adev_set_mic_mute;
    adev->hw_device.get_mic_mute = adev_get_mic_mute;
    adev->hw_device.set_parameters = adev_set_parameters;
    adev->hw_device.get_parameters = adev_get_parameters;
    adev->hw_device.get_input_buffer_size = adev_get_input_buffer_size;
    adev->hw_device.open_output_stream = adev_open_output_stream;
    adev->hw_device.close_output_stream = adev_close_output_stream;
    adev->hw_device.open_input_stream = adev_open_input_stream;
    adev->hw_device.close_input_stream = adev_close_input_stream;
    adev->hw_device.dump = adev_dump;

    *device = &adev->hw_device.common;
#if TARGET_AUDIO_PRIMARY
    mixer = mixer_open(1);

    if (mixer) {
        /* turning on master volume */
        ctl = mixer_get_ctl(mixer,30);
        mixer_ctl_set_value(ctl,0,1);

        /* setting master volume to value 50 */
        ctl = mixer_get_ctl(mixer,29);
        adev->master_volume = 50;
        mixer_ctl_set_value(ctl,0,adev->master_volume);
        mixer_close(mixer);
    }
#endif
    return 0;
}

static struct hw_module_methods_t hal_module_methods = {
    .open = adev_open,
};

struct audio_module HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = AUDIO_MODULE_API_VERSION_0_1,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = AUDIO_HARDWARE_MODULE_ID,
        .name = "audio HW HAL",
        .author = "The Android Open Source Project",
        .methods = &hal_module_methods,
    },
};
