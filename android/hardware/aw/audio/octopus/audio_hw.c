/*
 * Copyright (C) 2011 The Android Open Source Project
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

#define LOG_TAG "audio_hw_primary"
#define LOG_NDEBUG 0

#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <sys/time.h>
#include <stdlib.h>

#include <cutils/log.h>
#include <cutils/str_parms.h>
#include <cutils/properties.h>

#include <hardware/hardware.h>
#include <system/audio.h>
#include <hardware/audio.h>

#include <tinyalsa/asoundlib.h>
#include <audio_utils/resampler.h>
#include <audio_utils/echo_reference.h>
#include <hardware/audio_effect.h>
#include <audio_effects/effect_aec.h>
#include <fcntl.h>
#include <audio_route/audio_route.h>

#include "audio_iface.h"
#include "volume.h"
#include "audio_3d_surround.h"
#define  USE_3D_SURROUND 1

#define F_LOG ALOGV("%s, line: %d", __FUNCTION__, __LINE__);

#define USERECORD_44100_SAMPLERATE
#define call_button_voice 0
static int CASE_NAME = 0;
static bool NO_EARPIECE = 1;

#define MIXER_CARD 0

/* ALSA cards for A1X */
#define CARD_A1X_CODEC		0
#define CARD_A1X_HDMI		1
#define CARD_A1X_SPDIF		2
#define CARD_A1X_DEFAULT	0

/* ALSA ports for A1X */
#define PORT_CODEC			0
#define PORT_HDMI			0
#define PORT_SPDIF			0
#define PORT_MODEM			1
#define PORT_VIR_CODEC			2
#define PORT_bt                         3

#define SAMPLING_RATE_8K	8000
#define SAMPLING_RATE_11K	11025
#define SAMPLING_RATE_44K	44100
#define SAMPLING_RATE_48K	48000
#define SAMPLING_RATE_22050	22050
/* constraint imposed by ABE: all period sizes must be multiples of 24 */
#define ABE_BASE_FRAME_COUNT 24
/* number of base blocks in a short period (low latency) */
//#define SHORT_PERIOD_MULTIPLIER 44  /* 22 ms */
#define SHORT_PERIOD_MULTIPLIER 55 /* 40 ms */
/* number of frames per short period (low latency) */
//#define SHORT_PERIOD_SIZE (ABE_BASE_FRAME_COUNT * SHORT_PERIOD_MULTIPLIER)
#define SHORT_PERIOD_SIZE 1024
/* number of short periods in a long period (low power) */
//#define LONG_PERIOD_MULTIPLIER 14  /* 308 ms */
#define LONG_PERIOD_MULTIPLIER 6  /* 240 ms */
/* number of frames per long period (low power) */
#define LONG_PERIOD_SIZE (SHORT_PERIOD_SIZE * LONG_PERIOD_MULTIPLIER)
/* number of pseudo periods for playback */
#define PLAYBACK_PERIOD_COUNT 3
/* number of periods for capture */
#define CAPTURE_PERIOD_COUNT 2
/* minimum sleep time in out_write() when write threshold is not reached */
#define MIN_WRITE_SLEEP_US 5000

#define RESAMPLER_BUFFER_FRAMES (SHORT_PERIOD_SIZE * 2)
#define RESAMPLER_BUFFER_SIZE (4 * RESAMPLER_BUFFER_FRAMES)

/* android default out sampling rate*/
#define DEFAULT_OUT_SAMPLING_RATE SAMPLING_RATE_44K

/* audio codec default sampling rate*/
#define MM_SAMPLING_RATE SAMPLING_RATE_44K

/*wifi display buffer size*/
#define AF_BUFFER_SIZE 1024 * 80
#define AC100_XML_PATH "/system/etc/ac100_paths.xml"

static bool last_call_path_is_bt = 0;
static bool dmic_used = 0;
static bool spk_dul_used = 1;
static bool last_communication_is_bt = 0;

/*VOLUME CTL*/
#define MIXER_HP_VOLUME "headphone volume"
#define MIXER_SPK_VOLUME "speaker volume"
#define MIXER_EARPIECE_VOLUME "earpiece volume"

/*normal path*/
#define	media_speaker	"media-speaker"   /* OUT_DEVICE_SPEAKER */
#define	media_headset "media-headphones"/* OUT_DEVICE_HEADSET */
#define	media_headphones	"media-headphones"/* OUT_DEVICE_HEADPHONES */
#define	media_bluetooth_sco "com-ap-bt"/* OUT_DEVICE_BT_SCO */
#define	media_speaker_and_headphones	"phone-ring-voice"/* OUT_DEVICE_SPEAKER_AND_HEADSET */
#define	media_earpiece	"null"			/*OUT_DEVICE_EARPIECE*/
#define	media_single_speaker	"media-single-speaker"			/*OUT_DEVICE_SINGLE_SPEAKER*/
#define	media_mainmic	"media-main-mic"       /* IN_DEVICE_MAINMIC */
#define	media_main_dmic	"media-digital-mic"       /* IN_DEVICE_MAINMIC */
#define	media_headsetmic	"media-headset-mic"           /* IN_DEVICE_HEADSET */
#define	media_btmic	"com-bt-ap"           /* IN_DEVICE_HEADSET */

static int spkvolume_init = 0x1b;
static int earpiecevolume_init = 0x1e;
static int hpvolume_init = 0x3b;
enum tty_modes {
    TTY_MODE_OFF,
    TTY_MODE_VCO,
    TTY_MODE_HCO,
    TTY_MODE_FULL
};

enum {
    OUT_DEVICE_SPEAKER,
    OUT_DEVICE_HEADSET,
    OUT_DEVICE_HEADPHONES,
    OUT_DEVICE_BT_SCO,
    OUT_DEVICE_SPEAKER_AND_HEADSET,
    OUT_DEVICE_EARPIECE,
    OUT_DEVICE_SINGLE_SPEAKER,
    OUT_DEVICE_TAB_SIZE,           /* number of rows in route_configs[][] */
};

enum {
    IN_SOURCE_MAINMIC,
    IN_SOURCE_HEADSETMIC,
    IN_SOURCE_BTMIC,
    IN_SOURCE_TAB_SIZE,            /* number of lines in route_configs[][] */
    IN_SOURCE_NONE,
};
const char * const normal_route_configs[OUT_DEVICE_TAB_SIZE] = {

        media_speaker,             /* OUT_DEVICE_SPEAKER */
        media_headset,             /* OUT_DEVICE_HEADSET */
        media_headphones,          /* OUT_DEVICE_HEADPHONES */
        media_bluetooth_sco,             /* OUT_DEVICE_BT_SCO */
        media_speaker_and_headphones,    /* OUT_DEVICE_SPEAKER_AND_HEADSET */
        media_earpiece,    /* OUT_DEVICE_EARPIECE */
        media_single_speaker,    /* OUT_DEVICE_SINGLE_SPEAKER */
};
const char * const ringtone_route_configs[OUT_DEVICE_TAB_SIZE] = {

        media_speaker,             /* OUT_DEVICE_SPEAKER */
        "null",             /* OUT_DEVICE_HEADSET */
        "null",          /* OUT_DEVICE_HEADPHONES */
        "null",             /* OUT_DEVICE_BT_SCO */
        media_speaker_and_headphones,    /* OUT_DEVICE_SPEAKER_AND_HEADSET */
        "null",    /* OUT_DEVICE_EARPIECE */
        "null",
};
const char * const phone_keytone_route_configs[2][OUT_DEVICE_TAB_SIZE] = {
	{
	        "abb-phone-keytone-speaker",             /* OUT_DEVICE_SPEAKER */
	        "abb-phone-keytone-headphones",             /* OUT_DEVICE_HEADSET */
	        "abb-phone-keytone-headphones",          /* OUT_DEVICE_HEADPHONES */
	        "abb-phone-keytone-bt",             /* OUT_DEVICE_BT_SCO */
	        "null",    /* OUT_DEVICE_SPEAKER_AND_HEADSET */
	        "abb-phone-keytone-earpiece",    /* OUT_DEVICE_EARPIECE */
	        "null"
	},
        {
		"null",             /* OUT_DEVICE_SPEAKER */
	        "null",             /* OUT_DEVICE_HEADSET */
	        "null",          /* OUT_DEVICE_HEADPHONES */
	        "null",             /* OUT_DEVICE_BT_SCO */
	        "null",    /* OUT_DEVICE_SPEAKER_AND_HEADSET */
	        "null",    /* OUT_DEVICE_EARPIECE */
	        "null"
	}
};
const char * const phone_route_configs[2][OUT_DEVICE_TAB_SIZE] = {
	{
		/*plan_one*/
	        "analog-phone-speaker",             /* OUT_DEVICE_SPEAKER */
	        "analog-phone-headset",             /* OUT_DEVICE_HEADSET */
	        "analog-phone-headphone",          /* OUT_DEVICE_HEADPHONES */
	        "analog-phone-bt",             /* OUT_DEVICE_BT_SCO */
	        "null",    /* OUT_DEVICE_SPEAKER_AND_HEADSET */
	        "analog-phone-earpiece",    /* OUT_DEVICE_EARPIECE */
	        "null"
	}
	,
	{
		/*plan_two*/
	        "digital-phone-speaker",             /* OUT_DEVICE_SPEAKER */
	        "digital-phone-headset",             /* OUT_DEVICE_HEADSET */
	        "digital-phone-headphone",          /* OUT_DEVICE_HEADPHONES */
	        "digital-phone-bt",             /* OUT_DEVICE_BT_SCO */
	        "null",    /* OUT_DEVICE_SPEAKER_AND_HEADSET */
	        "digital-phone-earpiece",    /* OUT_DEVICE_EARPIECE */
	        "null"
	}
};
const char * const cap_normal_route_configs[IN_SOURCE_TAB_SIZE] = {
        media_mainmic,             /* IN_DEVICE_MAINMIC */
        media_headsetmic,             /* IN_DEVICE_HEADSET */
        media_btmic,             /* IN_DEVICE_bt */
};

const char * const dmic_cap_normal_route_configs[IN_SOURCE_TAB_SIZE] = {
        media_main_dmic,             /* IN_DEVICE_MAINMIC */
        media_headsetmic,             /* IN_DEVICE_HEADSET */
        media_btmic,             /* IN_DEVICE_bt */
};

const char * const cap_phone_normal_route_configs[2][IN_SOURCE_TAB_SIZE] = {
	{
	        "capture-abb-phone-mainmic",             /* IN_DEVICE_MAINMIC */
	        "capture-abb-phone-headsetmic",             /* IN_DEVICE_HEADSET */
	        "capture-abb-phone-btmic",             /* IN_DEVICE_bt */
	}
	,
	{
	        "capture-dbb-phone-mainmic",             /* IN_DEVICE_MAINMIC */
	        "capture-dbb-phone-headsetmic",             /* IN_DEVICE_HEADSET */
	        "capture-dbb-phone-btmic",             /* IN_DEVICE_bt */
	}
};
struct pcm_config pcm_config_mm_out = {
    .channels = 2,
    .rate = MM_SAMPLING_RATE,
    .period_size = SHORT_PERIOD_SIZE,
    .period_count = PLAYBACK_PERIOD_COUNT,
    .format = PCM_FORMAT_S16_LE,
};

struct pcm_config pcm_config_mm_in = {
    .channels = 2,
    .rate = MM_SAMPLING_RATE,
    .period_size = 1024,
    .period_count = CAPTURE_PERIOD_COUNT,
    .format = PCM_FORMAT_S16_LE,
};
struct pcm_config pcm_config_vx = {
    .channels = 1,
    .rate = SAMPLING_RATE_8K,
    .period_size = 160,
    .period_count = 2,
    .format = PCM_FORMAT_S16_LE,
};
struct route_setting
{
    char *ctl_name;
    int intval;
    char *strval;
};

struct mixer_volumectls
{
   struct mixer_ctl *hpvolume;
   struct mixer_ctl *spkvolume;
   struct mixer_ctl *earpiecevolume;
};

struct pcm_buf_manager
{
    pthread_mutex_t lock;       		/* see note below on mutex acquisition order */
    bool 		    BufExist;
    unsigned char   *BufStart;
    int             BufTotalLen;
    unsigned char   *BufReadPtr;
    int             DataLen;
    unsigned char   *BufWritPtr;
    int            	BufValideLen;
    int				SampleRate;
    int				Channel;
    struct sunxi_audio_device *dev;
};

struct sunxi_audio_device {
	struct audio_hw_device hw_device;

	pthread_mutex_t lock;       /* see note below on mutex acquisition order */
	struct mixer *mixer;
	struct mixer_volumectls mixer_volumectls;
	int mode;
	int out_device;
	int in_device;
	struct pcm *pcm_modem_dl;
	struct pcm *pcm_modem_ul;
	struct pcm *pcm_bt_dl;
	struct pcm *pcm_bt_ul;
	int in_call;
	float voice_volume;
	struct sunxi_stream_in *active_input;
	struct sunxi_stream_out *active_output;
	bool mic_mute;
	int tty_mode;
	struct echo_reference_itfe *echo_reference;
	bool bluetooth_nrec;
	int wb_amr;
	bool raw_flag;		// flag for raw data
	int fm_mode;
	bool in_record;
	bool bluetooth_voice;
	bool af_capture_flag;
	struct pcm_buf_manager PcmManager;
	struct audio_route *ar;
	struct volume_array *vol_array;
};

#if USE_3D_SURROUND
struct audio_3d_surround sur;
#endif

struct sunxi_stream_out {
    struct audio_stream_out stream;

    pthread_mutex_t lock;       /* see note below on mutex acquisition order */
    struct pcm_config config;
    struct pcm *pcm;
    struct resampler_itfe *resampler;
    char *buffer;
    int standby;
    struct echo_reference_itfe *echo_reference;
    struct sunxi_audio_device *dev;
    int write_threshold;
	uint64_t written;
};

#define MAX_PREPROCESSORS 3 /* maximum one AGC + one NS + one AEC per input stream */

struct sunxi_stream_in {
    struct audio_stream_in stream;

    pthread_mutex_t lock;       /* see note below on mutex acquisition order */
    struct pcm_config config;
    struct pcm *pcm;
    int device;
    struct resampler_itfe *resampler;
    struct resampler_buffer_provider buf_provider;
    int16_t *buffer;
    size_t frames_in;
    unsigned int requested_rate;
    int standby;
    int source;
    struct echo_reference_itfe *echo_reference;
    bool need_echo_reference;
    effect_handle_t preprocessors[MAX_PREPROCESSORS];
    int num_preprocessors;
    int16_t *proc_buf;
    size_t proc_buf_size;
    size_t proc_frames_in;
    int16_t *ref_buf;
    size_t ref_buf_size;
    size_t ref_frames_in;
    int read_status;

    struct sunxi_audio_device *dev;
};

/*wifi display buffer manager*/
static int WritePcmData(void * pInbuf, int inSize, struct pcm_buf_manager *PcmManager)
{
	ALOGV("RequestWriteBuf ++: size: %d", inSize);

	if (PcmManager->BufValideLen< inSize)
	{
		ALOGE("not enough buffer to write");
		return -1;
	}

	pthread_mutex_lock(&PcmManager->lock);

	if ((PcmManager->BufWritPtr + inSize)
		> (PcmManager->BufStart + PcmManager->BufTotalLen))
	{
		int endSize = PcmManager->BufStart + PcmManager->BufTotalLen
			- PcmManager->BufWritPtr;
		memcpy(PcmManager->BufWritPtr, pInbuf, endSize);
		memcpy(PcmManager->BufStart, (void *)((char *)pInbuf + endSize), inSize - endSize);

		PcmManager->BufWritPtr = PcmManager->BufWritPtr
			+ inSize - PcmManager->BufTotalLen;
	}
	else
	{
		memcpy(PcmManager->BufWritPtr, pInbuf, inSize);
		PcmManager->BufWritPtr += inSize;
	}

	PcmManager->BufValideLen -= inSize;
	PcmManager->DataLen += inSize;

	ALOGV("after wr: BufTotalLen: %d, DataLen: %d, BufValideLen: %d, BufReadPtr: %p, BufWritPtr: %p",
		PcmManager->BufTotalLen, PcmManager->DataLen, PcmManager->BufValideLen,
		PcmManager->BufReadPtr, PcmManager->BufWritPtr);

	pthread_mutex_unlock(&PcmManager->lock);
	ALOGV("RequestWriteBuf --");
	return 0;
}

static int ReadPcmData(void *pBuf, int uGetLen, struct pcm_buf_manager *PcmManager)
{
	int underflow = 0, fill_dc_size;
	int size_read = uGetLen;
	int timeout = 0, max_wait_count;

	struct sunxi_audio_device *adev = PcmManager->dev;

	max_wait_count = uGetLen * 100 / (PcmManager->SampleRate*PcmManager->Channel * 2) + 1; //normal
	max_wait_count *= 2;//twice

	ALOGV("ReadPcmDataForEnc ++, getLen: %d max_wait_count=%d", uGetLen,max_wait_count);
	if (adev->active_output != NULL)
	{
	    while(PcmManager->DataLen < uGetLen)
	    {
	        ALOGV("pcm is not enough for audio encoder! uGetLen: %d, uDataLen: %d\n",
				uGetLen, PcmManager->DataLen);
	        usleep(10 * 1000);
	        timeout++;
			if(timeout > max_wait_count) {
				if (PcmManager->DataLen < uGetLen) {
					underflow = 1;
					size_read = PcmManager->DataLen;
					fill_dc_size = uGetLen - PcmManager->DataLen;
					ALOGV("fill with dc size:%d",uGetLen - PcmManager->DataLen);
				}
			break;
	        }
	    }
	}
	else
	{
        ALOGV("pcm is not enough for audio encoder! uGetLen: %d, uDataLen: %d\n",
			uGetLen, PcmManager->DataLen);
		if (PcmManager->DataLen < uGetLen) {
			underflow = 1;
			size_read = PcmManager->DataLen;
			fill_dc_size = uGetLen - PcmManager->DataLen;
			usleep(fill_dc_size * 1000000 / 4 / PcmManager->SampleRate);
			ALOGV("fill with dc size:%d",uGetLen - PcmManager->DataLen);
		}
	}

    if((PcmManager->BufReadPtr + size_read)
		> (PcmManager->BufStart + PcmManager->BufTotalLen))
    {
        int len1 = PcmManager->BufStart
			+ PcmManager->BufTotalLen - PcmManager->BufReadPtr;
        memcpy((void *)pBuf, (void *)PcmManager->BufReadPtr, len1);
        memcpy((void *)((char *)pBuf + len1), (void *)PcmManager->BufStart, size_read - len1);
    }
    else
    {
        memcpy(pBuf, PcmManager->BufReadPtr, size_read);
    }

    pthread_mutex_lock(&PcmManager->lock);

    PcmManager->BufReadPtr += size_read;

    if(PcmManager->BufReadPtr
		>= PcmManager->BufStart + PcmManager->BufTotalLen)
    {
        PcmManager->BufReadPtr -= PcmManager->BufTotalLen;
    }
    PcmManager->DataLen -= size_read;
    PcmManager->BufValideLen += size_read;

    ALOGV("after rd: BufTotalLen: %d, DataLen: %d, BufValideLen: %d, pBufReadPtr: %p, pBufWritPtr: %p",
    PcmManager->BufTotalLen, PcmManager->DataLen, PcmManager->BufValideLen,
    PcmManager->BufReadPtr, PcmManager->BufWritPtr);

    pthread_mutex_unlock(&PcmManager->lock);
    ALOGV("ReadPcmDataForEnc --");

    if (underflow) {
	char *ptr = (char*)pBuf;
	memset(ptr+size_read, ptr[size_read-1], fill_dc_size);
    }

    return uGetLen;
}

/**
 * NOTE: when multiple mutexes have to be acquired, always respect the following order:
 *        hw device > in stream > out stream
 */

static void select_device(struct sunxi_audio_device *adev);
//static void select_device(struct sunxi_audio_device *adev);
static int adev_set_voice_volume(struct audio_hw_device *dev, float volume);
static int do_input_standby(struct sunxi_stream_in *in);
static int do_output_standby(struct sunxi_stream_out *out);

/* The enable flag when 0 makes the assumption that enums are disabled by
 * "Off" and integers/booleans by 0 */
static int set_route_by_array(struct mixer *mixer, struct route_setting *route,
                              int enable)
{
    struct mixer_ctl *ctl;
    unsigned int i, j;

    /* Go through the route array and set each value */
    i = 0;
    while (route[i].ctl_name) {
        ctl = mixer_get_ctl_by_name(mixer, route[i].ctl_name);
        if (!ctl)
            return -EINVAL;

        if (route[i].strval) {
            if (enable)
                mixer_ctl_set_enum_by_string(ctl, route[i].strval);
            else
                mixer_ctl_set_enum_by_string(ctl, "Off");
        } else {
            /* This ensures multiple (i.e. stereo) values are set jointly */
            for (j = 0; j < mixer_ctl_get_num_values(ctl); j++) {
                if (enable)
                    mixer_ctl_set_value(ctl, j, route[i].intval);
                else
                    mixer_ctl_set_value(ctl, j, 0);
            }
        }
        i++;
    }

    return 0;
}
static int bt_start_call(struct sunxi_audio_device *adev)
{
	ALOGE("Opening modem PCMs");
	ALOGV("%s, line: %d,CASE_NAME:%d", __FUNCTION__, __LINE__,CASE_NAME);
	if (CASE_NAME == 1){
		/* Open modem PCM channels */
		if (adev->pcm_modem_dl == NULL) {
			adev->pcm_modem_dl = pcm_open(0, PORT_MODEM, PCM_OUT, &pcm_config_vx);
			if (!pcm_is_ready(adev->pcm_modem_dl)) {
				ALOGE("cannot open PCM modem DL stream: %s", pcm_get_error(adev->pcm_modem_dl));
				goto err_open_dl;
			}
		}

		if (adev->pcm_modem_ul == NULL) {
			adev->pcm_modem_ul = pcm_open(0, PORT_MODEM, PCM_IN, &pcm_config_vx);
			if (!pcm_is_ready(adev->pcm_modem_ul)) {
				ALOGE("cannot open PCM modem UL stream: %s", pcm_get_error(adev->pcm_modem_ul));
				goto err_open_ul;
			}
		}

		/* Open bt PCM channels */
		if (adev->pcm_bt_dl == NULL) {
			adev->pcm_bt_dl = pcm_open(0, PORT_bt, PCM_OUT, &pcm_config_vx);
			if (!pcm_is_ready(adev->pcm_bt_dl)) {
				ALOGE("cannot open PCM bt DL stream: %s", pcm_get_error(adev->pcm_bt_dl));
				goto err_open_bt_dl;
			}
		}

		if (adev->pcm_bt_ul == NULL) {
			adev->pcm_bt_ul = pcm_open(0, PORT_bt, PCM_IN, &pcm_config_vx);
			if (!pcm_is_ready(adev->pcm_bt_ul)) {
				ALOGE("cannot open PCM bt UL stream: %s", pcm_get_error(adev->pcm_bt_ul));
				goto err_open_bt_ul;
			}
		}

		pcm_start(adev->pcm_modem_dl);
		pcm_start(adev->pcm_modem_ul);
		pcm_start(adev->pcm_bt_dl);
		pcm_start(adev->pcm_bt_ul);

	} else if(CASE_NAME == 2){
			/* Open bt PCM channels */
		if (adev->pcm_bt_dl == NULL) {
			adev->pcm_bt_dl = pcm_open(0, PORT_bt, PCM_OUT, &pcm_config_vx);
			if (!pcm_is_ready(adev->pcm_bt_dl)) {
				ALOGE("cannot open PCM bt DL stream: %s", pcm_get_error(adev->pcm_bt_dl));
				goto err_open_bt_dl;
			}
		}

		if (adev->pcm_bt_ul == NULL) {
			adev->pcm_bt_ul = pcm_open(0, PORT_bt, PCM_IN, &pcm_config_vx);
			if (!pcm_is_ready(adev->pcm_bt_ul)) {
				ALOGE("cannot open PCM bt UL stream: %s", pcm_get_error(adev->pcm_bt_ul));
				goto err_open_bt_ul;
			}
		}
		pcm_start(adev->pcm_bt_dl);
		pcm_start(adev->pcm_bt_ul);

	}else {
	ALOGV("%s,PHONE CASE ERR!!!!!!!! line: %d,CASE_NAME:%d", __FUNCTION__, __LINE__,CASE_NAME);
	}

	return 0;

err_open_ul:
	pcm_close(adev->pcm_modem_ul);
	adev->pcm_modem_ul = NULL;
err_open_dl:
	pcm_close(adev->pcm_modem_dl);
	adev->pcm_modem_dl = NULL;
err_open_bt_ul:
	pcm_close(adev->pcm_bt_ul);
	adev->pcm_bt_ul = NULL;
err_open_bt_dl:
	pcm_close(adev->pcm_bt_dl);
	adev->pcm_bt_dl = NULL;
	return -ENOMEM;
}
static void end_bt_call(struct sunxi_audio_device *adev)
{
	F_LOG;
	if (CASE_NAME == 1){
		pcm_stop(adev->pcm_modem_dl);
		pcm_stop(adev->pcm_modem_ul);
		pcm_close(adev->pcm_modem_dl);
		pcm_close(adev->pcm_modem_ul);
		adev->pcm_modem_dl = NULL;
		adev->pcm_modem_ul = NULL;

		pcm_stop(adev->pcm_bt_dl);
		pcm_stop(adev->pcm_bt_ul);
		pcm_close(adev->pcm_bt_dl);
		pcm_close(adev->pcm_bt_ul);
		adev->pcm_bt_dl = NULL;
		adev->pcm_bt_ul = NULL;
		//return 0;
	} else if(CASE_NAME == 2){
		pcm_stop(adev->pcm_bt_dl);
		pcm_stop(adev->pcm_bt_ul);
		pcm_close(adev->pcm_bt_dl);
		pcm_close(adev->pcm_bt_ul);
		adev->pcm_bt_dl = NULL;
		adev->pcm_bt_ul = NULL;
	}
        ALOGE("Closing modem PCMs");
}
static int start_call(struct sunxi_audio_device *adev)
{
	ALOGE("Opening modem PCMs");
	ALOGV("%s, line: %d,CASE_NAME:%d", __FUNCTION__, __LINE__,CASE_NAME);
	if (CASE_NAME == 1){
		return 0;
	} else if(CASE_NAME == 2){

	}else {
	ALOGV("%s,PHONE CASE ERR!!!!!!!! line: %d,CASE_NAME:%d", __FUNCTION__, __LINE__,CASE_NAME);
	}

	/* Open modem PCM channels */
	if (adev->pcm_modem_dl == NULL) {
        	adev->pcm_modem_dl = pcm_open(0, PORT_MODEM, PCM_OUT, &pcm_config_vx);
        	if (!pcm_is_ready(adev->pcm_modem_dl)) {
            		ALOGE("cannot open PCM modem DL stream: %s", pcm_get_error(adev->pcm_modem_dl));
			goto err_open_dl;
		}
	}

	if (adev->pcm_modem_ul == NULL) {
        	adev->pcm_modem_ul = pcm_open(0, PORT_MODEM, PCM_IN, &pcm_config_vx);
        	if (!pcm_is_ready(adev->pcm_modem_ul)) {
           		ALOGE("cannot open PCM modem UL stream: %s", pcm_get_error(adev->pcm_modem_ul));
			goto err_open_ul;
		}
	}

	pcm_start(adev->pcm_modem_dl);
	pcm_start(adev->pcm_modem_ul);
	return 0;

err_open_ul:
	pcm_close(adev->pcm_modem_ul);
	adev->pcm_modem_ul = NULL;
err_open_dl:
	pcm_close(adev->pcm_modem_dl);
	adev->pcm_modem_dl = NULL;

	return -ENOMEM;
}

static void end_call(struct sunxi_audio_device *adev)
{
	F_LOG;
	if (CASE_NAME == 1){
		if (last_call_path_is_bt) {
			end_bt_call(adev);
			last_call_path_is_bt = 0;
		}
		//return 0;
	} else if(CASE_NAME == 2){
		if (last_call_path_is_bt) {
			end_bt_call(adev);
			last_call_path_is_bt = 0;
		}
		ALOGE("Closing modem PCMs");
		pcm_stop(adev->pcm_modem_dl);
		pcm_stop(adev->pcm_modem_ul);
		pcm_close(adev->pcm_modem_dl);
		pcm_close(adev->pcm_modem_ul);
		adev->pcm_modem_dl = NULL;
		adev->pcm_modem_ul = NULL;
	}
	mixer_ctl_set_value(adev->mixer_volumectls.spkvolume, 0, spkvolume_init);
	mixer_ctl_set_value(adev->mixer_volumectls.earpiecevolume, 0, earpiecevolume_init);
	mixer_ctl_set_value(adev->mixer_volumectls.hpvolume, 0, hpvolume_init);
	//clear_phone_route();
	ALOGD("end_call, bluetooth_nrec=%d", adev->bluetooth_nrec);
}

static void set_incall_device(struct sunxi_audio_device *adev)
{
    int device_type;

	F_LOG;

    switch(adev->out_device) {
        case AUDIO_DEVICE_OUT_EARPIECE:
            device_type = RIL_AUDIO_PATH_EARPIECE;
            break;
        case AUDIO_DEVICE_OUT_SPEAKER:
        case AUDIO_DEVICE_OUT_AUX_DIGITAL:
            device_type = RIL_AUDIO_PATH_SPK;
            break;
        case AUDIO_DEVICE_OUT_WIRED_HEADSET:
            device_type = RIL_AUDIO_PATH_HEADSET;
            break;
        case AUDIO_DEVICE_OUT_WIRED_HEADPHONE:
            device_type = RIL_AUDIO_PATH_HEADSET;
            break;
        case AUDIO_DEVICE_OUT_BLUETOOTH_SCO:
        case AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET:
        case AUDIO_DEVICE_OUT_BLUETOOTH_SCO_CARKIT:
                device_type = RIL_AUDIO_PATH_BT;
            break;
        default:
            device_type = RIL_AUDIO_PATH_EARPIECE;
            break;
    }

	ril_set_call_audio_path(device_type);
}

static void set_bp_volume(struct sunxi_audio_device *adev, int volume)
{
    int device_type;

	F_LOG;

    switch(adev->out_device) {
        case AUDIO_DEVICE_OUT_EARPIECE:
            device_type = RIL_AUDIO_PATH_EARPIECE;
            break;
        case AUDIO_DEVICE_OUT_SPEAKER:
        case AUDIO_DEVICE_OUT_AUX_DIGITAL:
            device_type = RIL_AUDIO_PATH_SPK;
            break;
        case AUDIO_DEVICE_OUT_WIRED_HEADSET:
            device_type = RIL_AUDIO_PATH_HEADSET;
            break;
        case AUDIO_DEVICE_OUT_WIRED_HEADPHONE:
            device_type = RIL_AUDIO_PATH_HEADSET;
            break;
        case AUDIO_DEVICE_OUT_BLUETOOTH_SCO:
        case AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET:
        case AUDIO_DEVICE_OUT_BLUETOOTH_SCO_CARKIT:
                device_type = RIL_AUDIO_PATH_BT;
            break;
        default:
            device_type = RIL_AUDIO_PATH_EARPIECE;
            break;
    }

    ril_set_call_volume(device_type, volume);
}

static void force_all_standby(struct sunxi_audio_device *adev)
{
    struct sunxi_stream_in *in;
    struct sunxi_stream_out *out;

    if (adev->active_output) {
        out = adev->active_output;
        pthread_mutex_lock(&out->lock);
        do_output_standby(out);
        pthread_mutex_unlock(&out->lock);
    }
    if (adev->active_input) {
        in = adev->active_input;
        pthread_mutex_lock(&in->lock);
        do_input_standby(in);
        pthread_mutex_unlock(&in->lock);
    }
}

static void select_mode(struct sunxi_audio_device *adev)
{
    if (adev->mode == AUDIO_MODE_IN_CALL || adev->mode == AUDIO_MODE_MODE_FACTORY_TEST) {
        //ALOGV("Entering IN_CALL state, in_call=%d, adev->out_device is : %d", adev->in_call, adev->out_device);
        if (!adev->in_call) {
            force_all_standby(adev);
            /* force earpiece route for in call state if speaker is the
            only currently selected route. This prevents having to tear
            down the modem PCMs to change route from speaker to earpiece
            after the ringtone is played, but doesn't cause a route
            change if a headset or bt device is already connected. If
            speaker is not the only thing active, just remove it from
            the route. We'll assume it'll never be used initially during
            a call. This works because we're sure that the audio policy
            manager will update the output device after the audio mode
            change, even if the device selection did not change. */
            if (adev->out_device == AUDIO_DEVICE_OUT_SPEAKER) {
		F_LOG;
                adev->out_device = AUDIO_DEVICE_OUT_EARPIECE;
                adev->in_device = AUDIO_DEVICE_IN_BUILTIN_MIC & ~AUDIO_DEVICE_BIT_IN;
            } else{
		F_LOG
                adev->out_device &= ~AUDIO_DEVICE_OUT_SPEAKER;
            }

	    start_call(adev);
	    F_LOG;
	    select_device(adev);
	    adev_set_voice_volume(&adev->hw_device, adev->voice_volume);
	    adev->in_call = 1;
	}
    } else {
	ALOGV("Leaving IN_CALL state, in_call=%d, mode=%d",
	adev->in_call, adev->mode);
	if (adev->in_call) {
		adev->in_call = 0;
		end_call(adev);
		force_all_standby(adev);
		//select_device(adev);
		select_device(adev);
	}
    }

    if (adev->mode == AUDIO_MODE_IN_COMMUNICATION) {
	ALOGV("AUDIO_MODE_IN_COMMUNICATION");
	F_LOG;
	force_all_standby(adev);
	select_device(adev);
    }

    if (adev->mode == AUDIO_MODE_FM) {
	force_all_standby(adev);
	F_LOG;
	select_device(adev);
	adev_set_voice_volume(&adev->hw_device, adev->voice_volume);
    }

}

static int sysopen_music()
{
	int ret = -1, fd=0;
	int switch_to_headset = 0;
	 char h2w_state[2]={0};
	fd = open("/sys/class/switch/h2w/state", O_RDONLY);

	if (fd > 0){
		ret = read(fd, h2w_state, sizeof(h2w_state));
		close(fd);
		if ( (atoi(h2w_state) == 2 || atoi(h2w_state) == 1) )
		{
                	ALOGV("h2w_state:erjicharu");
                        switch_to_headset  =1;
		}
	}

	return switch_to_headset;
}

static int check_hdmi_status()
{
    int hdmi_fd;
	int switch_to_hdmi = 0;
	hdmi_fd = open("/sys/class/switch/hdmi/state", O_RDONLY);
	if (hdmi_fd)
	{
		char val;
		if (read(hdmi_fd, &val, 1) == 1 && val == '1')
		{
			ALOGD( "init hdmi_plug: IN");
			switch_to_hdmi = 1;
		}
		else
		{
			ALOGD( "init hdmi_plug: OUT");
			switch_to_hdmi = 0;
		}
		close(hdmi_fd);
	}
	else
	{
		ALOGD("open /sys/class/switch/hdmi/state failed");
		switch_to_hdmi = -1;
	}
	return switch_to_hdmi;
}

static void select_device(struct sunxi_audio_device *adev)
{
	int ret = -1;
	int output_device_id = 0;
	int input_device_id = 0;
	const char *output_route = NULL;
	const char *input_route = NULL;
	const char *phone_route = NULL;
	int earpiece_on=0, headset_on=0, headphone_on=0, bt_on=0, speaker_on=0;
	int main_mic_on = 0,sub_mic_on = 0;
	int bton_temp = 0;

	if(!adev->ar)
		return;

	ALOGD(">>>>>> select_device");

	audio_route_reset(adev->ar);
	audio_route_apply_path(adev->ar, "media-speaker-off");
	
	if (adev->mode == AUDIO_MODE_IN_CALL){
		if(CASE_NAME <= 0){
			ALOGV("%s,PHONE CASE ERR!!!!!!!!!!!!!!!!!!!! line: %d,CASE_NAME:%d", __FUNCTION__, __LINE__,CASE_NAME);
			//return CASE_NAME;
		}
		headset_on = adev->out_device & AUDIO_DEVICE_OUT_WIRED_HEADSET;  // hp4p
		headphone_on = adev->out_device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE; // hp3p
		speaker_on = adev->out_device & AUDIO_DEVICE_OUT_SPEAKER;
		earpiece_on = adev->out_device & AUDIO_DEVICE_OUT_EARPIECE;
		bt_on = adev->out_device & AUDIO_DEVICE_OUT_ALL_SCO;
		//audio_route_reset(adev->ar);
		ALOGV("****LINE:%d,FUNC:%s, headset_on:%d, headphone_on:%d, speaker_on:%d, earpiece_on:%d, bt_on:%d",__LINE__,__FUNCTION__, headphone_on, headphone_on, speaker_on, earpiece_on, bt_on);
		if (last_call_path_is_bt && !bt_on) {
			end_bt_call(adev);
			last_call_path_is_bt = 0;
		}
		if ((headset_on || headphone_on) && speaker_on){
			output_device_id = OUT_DEVICE_SPEAKER_AND_HEADSET;
		} else if (earpiece_on) {
			F_LOG;
			if (NO_EARPIECE)
				{F_LOG;
				output_device_id = OUT_DEVICE_SPEAKER;
				}
			else
				{F_LOG;
				output_device_id = OUT_DEVICE_EARPIECE;
				}
		} else if (headset_on) {
			output_device_id = OUT_DEVICE_HEADSET;
		} else if (headphone_on){
			output_device_id = OUT_DEVICE_HEADPHONES;
		}else if(bt_on){
			bton_temp = 1;
			//bt_start_call(adev);
			//last_call_path_is_bt = 1;
			output_device_id = OUT_DEVICE_BT_SCO;
		}else if(speaker_on){
			output_device_id = OUT_DEVICE_SPEAKER;
		}
		ALOGV("****** output_id is : %d", output_device_id);
		phone_route = phone_route_configs[CASE_NAME-1][output_device_id];
		set_incall_device(adev);
	}
	if (adev->active_output) {
		ALOGV("active_output, ****LINE:%d,FUNC:%s, adev->out_device:%d",__LINE__,__FUNCTION__, adev->out_device);
		headset_on = adev->out_device & AUDIO_DEVICE_OUT_WIRED_HEADSET;  // hp4p
		headphone_on = adev->out_device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE; // hp3p
		speaker_on = adev->out_device & AUDIO_DEVICE_OUT_SPEAKER;
		earpiece_on = adev->out_device & AUDIO_DEVICE_OUT_EARPIECE;
		bt_on = adev->out_device & AUDIO_DEVICE_OUT_ALL_SCO;
		//audio_route_reset(adev->ar);
		ALOGV("****LINE:%d,FUNC:%s, headset_on:%d, headphone_on:%d, speaker_on:%d, earpiece_on:%d, bt_on:%d",__LINE__,__FUNCTION__, headset_on, headphone_on, speaker_on, earpiece_on, bt_on);
		if ((headset_on || headphone_on) && speaker_on){
			output_device_id = OUT_DEVICE_SPEAKER_AND_HEADSET;
		} else if (earpiece_on) {
			if (NO_EARPIECE)
				output_device_id = OUT_DEVICE_SPEAKER;
			else
				output_device_id = OUT_DEVICE_EARPIECE;
			//output_device_id = OUT_DEVICE_EARPIECE;
		} else if (headset_on) {
			output_device_id = OUT_DEVICE_HEADSET;
		} else if (headphone_on){
			output_device_id = OUT_DEVICE_HEADSET;
		}else if(bt_on){
			output_device_id = OUT_DEVICE_BT_SCO;
		}else if(speaker_on){
			output_device_id = OUT_DEVICE_SPEAKER;
		}
		ALOGV("****LINE:%d,FUNC:%s, output_device_id:%d",__LINE__,__FUNCTION__, output_device_id);
		switch (adev->mode){
		case AUDIO_MODE_NORMAL:
			ALOGV("NORMAL mode, ****LINE:%d,FUNC:%s, adev->out_device:%d",__LINE__,__FUNCTION__, adev->out_device);
#if 0
			if(sysopen_music())
				output_device_id = OUT_DEVICE_HEADSET;
			else
#endif
			//output_device_id = OUT_DEVICE_SPEAKER;
			//output_device_id = OUT_DEVICE_HEADSET;
			output_route = normal_route_configs[output_device_id];
			break;
		case AUDIO_MODE_RINGTONE:
			ALOGV("RINGTONE mode, ****LINE:%d,FUNC:%s, adev->out_device:%d",__LINE__,__FUNCTION__, adev->out_device);
			output_route = ringtone_route_configs[output_device_id];
			break;
		case AUDIO_MODE_FM:
			break;
		case AUDIO_MODE_MODE_FACTORY_TEST:
			break;
		case AUDIO_MODE_IN_CALL:
			ALOGV("IN_CALL mode, ****LINE:%d,FUNC:%s, adev->out_device:%d",__LINE__,__FUNCTION__, adev->out_device);
			output_route = phone_keytone_route_configs[CASE_NAME-1][output_device_id];
			break;
		case AUDIO_MODE_IN_COMMUNICATION:
			output_route = normal_route_configs[output_device_id];
			F_LOG;
			if (output_device_id == OUT_DEVICE_BT_SCO && !last_communication_is_bt) {
				F_LOG;
				/* Open modem PCM channels */
				if (adev->pcm_modem_dl == NULL) {
					adev->pcm_modem_dl = pcm_open(0, 4, PCM_OUT, &pcm_config_vx);
					if (!pcm_is_ready(adev->pcm_modem_dl)) {
						ALOGE("cannot open PCM modem DL stream: %s", pcm_get_error(adev->pcm_modem_dl));
						//goto err_open_dl;
						}
					}
				if (adev->pcm_modem_ul == NULL) {
					adev->pcm_modem_ul = pcm_open(0, 4, PCM_IN, &pcm_config_vx);
					if (!pcm_is_ready(adev->pcm_modem_ul)) {
						ALOGE("cannot open PCM modem UL stream: %s", pcm_get_error(adev->pcm_modem_ul));
						//goto err_open_ul;
						}
					}
				/* Open bt PCM channels */
				if (adev->pcm_bt_dl == NULL) {
					adev->pcm_bt_dl = pcm_open(0, PORT_bt, PCM_OUT, &pcm_config_vx);
					if (!pcm_is_ready(adev->pcm_bt_dl)) {
						ALOGE("cannot open PCM bt DL stream: %s", pcm_get_error(adev->pcm_bt_dl));
						//goto err_open_bt_dl;
						}
					}
				if (adev->pcm_bt_ul == NULL) {
					adev->pcm_bt_ul = pcm_open(0, PORT_bt, PCM_IN, &pcm_config_vx);
					if (!pcm_is_ready(adev->pcm_bt_ul)) {
						ALOGE("cannot open PCM bt UL stream: %s", pcm_get_error(adev->pcm_bt_ul));
						//goto err_open_bt_ul;
						}
					}
				pcm_start(adev->pcm_modem_dl);
				pcm_start(adev->pcm_modem_ul);
				pcm_start(adev->pcm_bt_dl);
				pcm_start(adev->pcm_bt_ul);
				last_communication_is_bt = true;

			}
			break;
		default:
			break;
		}

	}
	if (adev->active_input) {
		if(adev->out_device & AUDIO_DEVICE_OUT_ALL_SCO){
			adev->in_device = AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET;
		}
		int bt_on = adev->in_device & AUDIO_DEVICE_IN_ALL_SCO;
		ALOGV("record,****LINE:%d,FUNC:%s, adev->in_device:%x,AUDIO_DEVICE_IN_ALL_SCO:%x",__LINE__,__FUNCTION__, adev->in_device,AUDIO_DEVICE_IN_ALL_SCO);
		if (!bt_on) {
			if ((adev->mode != AUDIO_MODE_IN_CALL) && (adev->active_input != 0)) {
			/* sub mic is used for camcorder or VoIP on speaker phone */
			sub_mic_on = (adev->active_input->source == AUDIO_SOURCE_CAMCORDER) ||
	                         ((adev->out_device & AUDIO_DEVICE_OUT_SPEAKER) &&
	                          (adev->active_input->source == AUDIO_SOURCE_VOICE_COMMUNICATION));
	 		}
		        if (!sub_mic_on) {
		            headset_on = adev->in_device & AUDIO_DEVICE_IN_WIRED_HEADSET;
		            main_mic_on = adev->in_device & AUDIO_DEVICE_IN_BUILTIN_MIC;
		        }
		}
		if (headset_on){
			input_device_id = IN_SOURCE_HEADSETMIC;
		} else if (main_mic_on) {
			input_device_id = IN_SOURCE_MAINMIC;
		}else if (bt_on && (adev->mode == AUDIO_MODE_IN_COMMUNICATION || adev->mode == AUDIO_MODE_IN_CALL)) {
			input_device_id = IN_SOURCE_BTMIC;
		}else{
			input_device_id = IN_SOURCE_MAINMIC;
		}
		ALOGV("fm record,****LINE:%d,FUNC:%s,bt_on:%d,headset_on:%d,main_mic_on;%d,adev->in_device:%x,AUDIO_DEVICE_IN_ALL_SCO:%x",__LINE__,__FUNCTION__,bt_on,headset_on,main_mic_on,adev->in_device,AUDIO_DEVICE_IN_ALL_SCO);

		if (adev->mode == AUDIO_MODE_IN_CALL) {
			input_route = cap_phone_normal_route_configs[CASE_NAME-1][input_device_id];
			ALOGV("phone record,****LINE:%d,FUNC:%s, adev->in_device:%x",__LINE__,__FUNCTION__, adev->in_device);
		} else if (adev->mode == AUDIO_MODE_FM) {
			//fm_record_enable(true);
			//fm_record_route(adev->in_device);
			ALOGV("fm record,****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__);
		} else if (adev->mode == AUDIO_MODE_NORMAL) {
			if(dmic_used)
				input_route = dmic_cap_normal_route_configs[input_device_id];
			else
				input_route = cap_normal_route_configs[input_device_id];
			ALOGV("normal record,****LINE:%d,FUNC:%s,adev->in_device:%d",__LINE__,__FUNCTION__,adev->in_device);
		} else if (adev->mode == AUDIO_MODE_IN_COMMUNICATION) {
			if(dmic_used)
				input_route = dmic_cap_normal_route_configs[input_device_id];
			else
				input_route = cap_normal_route_configs[input_device_id];
			F_LOG;
		}

	}
	if (phone_route)
		audio_route_apply_path(adev->ar, phone_route);
	if (output_route)
        	audio_route_apply_path(adev->ar, output_route);
	if (input_route)
        	audio_route_apply_path(adev->ar, input_route);
	audio_route_update_mixer(adev->ar);
	if (adev->mode == AUDIO_MODE_IN_CALL ){
		if(bton_temp && last_call_path_is_bt == 0){
			bt_start_call(adev);
			last_call_path_is_bt = 1;
		}
	}
}

/* must be called with hw device and output stream mutexes locked */
static int start_output_stream(struct sunxi_stream_out *out)
{
	struct sunxi_audio_device *adev = out->dev;
	unsigned int card = CARD_A1X_DEFAULT;
	unsigned int port = PORT_CODEC;
	unsigned int ret;
	F_LOG;
	if (adev->mode == AUDIO_MODE_IN_CALL || adev->mode == AUDIO_MODE_MODE_FACTORY_TEST || adev->mode == AUDIO_MODE_FM) // 10-16 modify
	{
		return 0;
	}

	if (adev->raw_flag)
	{
		return 0;
	}

	#if 0
	int device = adev->out_device;
	char prop_value[512];
    ret = property_get("audio.routing", prop_value, "");
	if (ret > 0)
	{
		F_LOG;
		if(atoi(prop_value) == AUDIO_DEVICE_OUT_SPEAKER)
		{
			F_LOG;
			ALOGD("start_output_stream, AUDIO_DEVICE_OUT_SPEAKER");
			device = AUDIO_DEVICE_OUT_SPEAKER;
		}
		else if(atoi(prop_value) == AUDIO_DEVICE_OUT_AUX_DIGITAL)
		{
			F_LOG;
			ALOGD("start_output_stream AUDIO_DEVICE_OUT_AUX_DIGITAL");
			device = AUDIO_DEVICE_OUT_AUX_DIGITAL;
		}
		else if(atoi(prop_value) == AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET)
		{
			F_LOG;
			ALOGD("start_output_stream AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET");
			device = AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET;
		}
		else
		{
			F_LOG;
			ALOGW("unknown audio.routing : %s", prop_value);
		}
	}
	else
	{
		F_LOG;
		// ALOGW("get audio.routing failed");
	}
	adev->out_device = device;
	#endif

	if (1 == check_hdmi_status())
	{
	    int device = adev->out_device;
		device = AUDIO_DEVICE_OUT_AUX_DIGITAL;
		adev->out_device = device;
	}

	adev->active_output = out;
	if (adev->mode != AUDIO_MODE_IN_CALL) {
		/* FIXME: only works if only one output can be active at a time */
		select_device(adev);
	} else {
		select_device(adev);
	}
	/* S/PDIF takes priority over HDMI audio. In the case of multiple
	* devices, this will cause use of S/PDIF or HDMI only */
	out->config.rate = MM_SAMPLING_RATE;
	if (adev->out_device & AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET) {
		card = CARD_A1X_SPDIF;
		port = PORT_SPDIF;
	} else if(adev->out_device & AUDIO_DEVICE_OUT_AUX_DIGITAL) {
		card = CARD_A1X_HDMI;
		port = PORT_HDMI;
		out->config.rate = MM_SAMPLING_RATE;
	} else if(adev->out_device & AUDIO_DEVICE_OUT_ALL_SCO){
		out->config.rate = SAMPLING_RATE_8K;
		//out->config.channels = 1;
		ALOGV("****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__);
	}
	/* default to low power: will be corrected in out_write if necessary before first write to
	* tinyalsa.
	*/
	out->write_threshold = PLAYBACK_PERIOD_COUNT * SHORT_PERIOD_SIZE;
	out->config.start_threshold = SHORT_PERIOD_SIZE * 2;
	out->config.avail_min = SHORT_PERIOD_SIZE;

	out->write_threshold = 0;// SHORT_PERIOD_SIZE * PLAYBACK_PERIOD_COUNT;
	out->config.start_threshold = 0;//SHORT_PERIOD_SIZE * 2;
	out->config.avail_min = 0; //SHORT_PERIOD_SIZE;
	out->config.stop_threshold = 0;
	ALOGV("start_output_stream: card:%d, port:%d, rate:%d, period_count:%d, period_size:%d", card, port, out->config.rate, out->config.period_count, out->config.period_size);
	out->pcm = pcm_open(card, port, PCM_OUT | PCM_MONOTONIC, &out->config);
	//out->pcm = pcm_open_req(card, port, PCM_OUT | PCM_MMAP | PCM_NOIRQ, &out->config, DEFAULT_OUT_SAMPLING_RATE);
    if (!pcm_is_ready(out->pcm)) {
        ALOGE("cannot open pcm_out driver: %s", pcm_get_error(out->pcm));
        pcm_close(out->pcm);
        adev->active_output = NULL;
        return -ENOMEM;
    }

    if (adev->echo_reference != NULL)
        out->echo_reference = adev->echo_reference;

	if (DEFAULT_OUT_SAMPLING_RATE != out->config.rate)
	{
		ret = create_resampler(DEFAULT_OUT_SAMPLING_RATE,
							   out->config.rate,
							   2,
							   RESAMPLER_QUALITY_DEFAULT,
							   NULL,
							   &out->resampler);
		if (ret != 0)
		{
			ALOGE("create out resampler failed, %d -> %d", DEFAULT_OUT_SAMPLING_RATE, out->config.rate);
			return ret;
		}

		ALOGV("create out resampler OK, %d -> %d", DEFAULT_OUT_SAMPLING_RATE, out->config.rate);
	}
	else
	{
		ALOGV("do not use out resampler");
	}

	if (out->resampler)
	{
	    out->resampler->reset(out->resampler);
	}

    return 0;
}

static int check_input_parameters(uint32_t sample_rate, int format, int channel_count)
{
    if (format != AUDIO_FORMAT_PCM_16_BIT)
        return -EINVAL;

    if ((channel_count < 1) || (channel_count > 2))
        return -EINVAL;

    switch(sample_rate) {
    case 8000:
    case 11025:
    case 16000:
    case 22050:
    case 24000:
    case 32000:
    case 44100:
    case 48000:
        break;
    default:
        return -EINVAL;
    }

    return 0;
}

static size_t get_input_buffer_size(uint32_t sample_rate, int format, int channel_count)
{
    size_t size;
    size_t device_rate;

    if (check_input_parameters(sample_rate, format, channel_count) != 0)
        return 0;

    /* take resampling into account and return the closest majoring
    multiple of 16 frames, as audioflinger expects audio buffers to
    be a multiple of 16 frames */
    size = (pcm_config_mm_in.period_size * sample_rate) / pcm_config_mm_in.rate;
    size = ((size + 15) / 16) * 16;

    return size * channel_count * sizeof(short);
}

static void add_echo_reference(struct sunxi_stream_out *out,
                               struct echo_reference_itfe *reference)
{
    pthread_mutex_lock(&out->lock);
    out->echo_reference = reference;
    pthread_mutex_unlock(&out->lock);
}

static void remove_echo_reference(struct sunxi_stream_out *out,
                                  struct echo_reference_itfe *reference)
{
    pthread_mutex_lock(&out->lock);
    if (out->echo_reference == reference) {
        /* stop writing to echo reference */
        reference->write(reference, NULL);
        out->echo_reference = NULL;
    }
    pthread_mutex_unlock(&out->lock);
}

static void put_echo_reference(struct sunxi_audio_device *adev,
                          struct echo_reference_itfe *reference)
{
    if (adev->echo_reference != NULL &&
            reference == adev->echo_reference) {
        if (adev->active_output != NULL)
            remove_echo_reference(adev->active_output, reference);
        release_echo_reference(reference);
        adev->echo_reference = NULL;
    }
}

static struct echo_reference_itfe *get_echo_reference(struct sunxi_audio_device *adev,
                                               audio_format_t format,
                                               uint32_t channel_count,
                                               uint32_t sampling_rate)
{
    put_echo_reference(adev, adev->echo_reference);
    if (adev->active_output != NULL) {
        struct audio_stream *stream = &adev->active_output->stream.common;
        uint32_t wr_channel_count = popcount(stream->get_channels(stream));
        uint32_t wr_sampling_rate = stream->get_sample_rate(stream);

        int status = create_echo_reference(AUDIO_FORMAT_PCM_16_BIT,
                                           channel_count,
                                           sampling_rate,
                                           AUDIO_FORMAT_PCM_16_BIT,
                                           wr_channel_count,
                                           wr_sampling_rate,
                                           &adev->echo_reference);
        if (status == 0)
            add_echo_reference(adev->active_output, adev->echo_reference);
    }
    return adev->echo_reference;
}

static int get_playback_delay(struct sunxi_stream_out *out,
                       size_t frames,
                       struct echo_reference_buffer *buffer)
{
    size_t kernel_frames;
    int status;

    status = pcm_get_htimestamp(out->pcm, &kernel_frames, &buffer->time_stamp);
    if (status < 0) {
        buffer->time_stamp.tv_sec  = 0;
        buffer->time_stamp.tv_nsec = 0;
        buffer->delay_ns           = 0;
        ALOGV("get_playback_delay(): pcm_get_htimestamp error,"
                "setting playbackTimestamp to 0");
        return status;
    }

    kernel_frames = pcm_get_buffer_size(out->pcm) - kernel_frames;

    /* adjust render time stamp with delay added by current driver buffer.
     * Add the duration of current frame as we want the render time of the last
     * sample being written. */
    buffer->delay_ns = (long)(((int64_t)(kernel_frames + frames)* 1000000000)/
                            MM_SAMPLING_RATE);

    return 0;
}


static int out_get_presentation_position(const struct audio_stream_out *stream,
                                   uint64_t *frames, struct timespec *timestamp)
{
    struct sunxi_stream_out *out = (struct sunxi_stream_out *)stream;
    int ret = -1;

    pthread_mutex_lock(&out->lock);

    int i;
    // There is a question how to implement this correctly when there is more than one PCM stream.
    // We are just interested in the frames pending for playback in the kernel buffer here,
    // not the total played since start.  The current behavior should be safe because the
    // cases where both cards are active are marginal.
    //for (i = 0; i < PCM_TOTAL; i++)
        if (out->pcm) {
            size_t avail;
            if (pcm_get_htimestamp(out->pcm, &avail, timestamp) == 0) {
                size_t kernel_buffer_size = out->config.period_size * out->config.period_count;
                // FIXME This calculation is incorrect if there is buffering after app processor
                int64_t signed_frames = out->written - kernel_buffer_size + avail;
                // It would be unusual for this value to be negative, but check just in case ...
                if (signed_frames >= 0) {
                    *frames = signed_frames;
                    ret = 0;
                }
                //break;
            }
        }

    pthread_mutex_unlock(&out->lock);

    return ret;
}


static uint32_t out_get_sample_rate(const struct audio_stream *stream)
{
    return DEFAULT_OUT_SAMPLING_RATE;
}

static int out_set_sample_rate(struct audio_stream *stream, uint32_t rate)
{
    return 0;
}

static size_t out_get_buffer_size(const struct audio_stream *stream)
{
    struct sunxi_stream_out *out = (struct sunxi_stream_out *)stream;

    /* take resampling into account and return the closest majoring
    multiple of 16 frames, as audioflinger expects audio buffers to
    be a multiple of 16 frames */
    size_t size = (SHORT_PERIOD_SIZE * DEFAULT_OUT_SAMPLING_RATE) / out->config.rate;
    size = ((size + 15) / 16) * 16;
    return size * audio_stream_frame_size((struct audio_stream *)stream);
}

static audio_channel_mask_t out_get_channels(const struct audio_stream *stream)
{
    return AUDIO_CHANNEL_OUT_STEREO;
}

static audio_format_t out_get_format(const struct audio_stream *stream)
{
    return AUDIO_FORMAT_PCM_16_BIT;
}

static int out_set_format(struct audio_stream *stream, audio_format_t format)
{
    return 0;
}

/* must be called with hw device and output stream mutexes locked */
static int do_output_standby(struct sunxi_stream_out *out)
{
    struct sunxi_audio_device *adev = out->dev;

    if (!out->standby) {
        pcm_close(out->pcm);
        out->pcm = NULL;
        adev->active_output = 0;
		if (out->resampler) {
            release_resampler(out->resampler);
            out->resampler = 0;//close resample
        }
        /* stop writing to echo reference */
        if (out->echo_reference != NULL) {
            out->echo_reference->write(out->echo_reference, NULL);
            out->echo_reference = NULL;
        }
        out->standby = 1;
    }
    return 0;
}

static int out_standby(struct audio_stream *stream)
{
    struct sunxi_stream_out *out = (struct sunxi_stream_out *)stream;
    int status;
	ALOGD("out_standby");
    //pthread_mutex_lock(&out->dev->lock);
    pthread_mutex_lock(&out->lock);
    status = do_output_standby(out);
    pthread_mutex_unlock(&out->lock);
    //pthread_mutex_unlock(&out->dev->lock);
    return status;
}

static int out_dump(const struct audio_stream *stream, int fd)
{
    return 0;
}

static int out_set_parameters(struct audio_stream *stream, const char *kvpairs)
{
    struct sunxi_stream_out *out = (struct sunxi_stream_out *)stream;
    struct sunxi_audio_device *adev = out->dev;
    struct sunxi_stream_in *in;
    struct str_parms *parms;
    char *str;
    char value[32];
    int ret, val = 0;
    bool force_input_standby = false;

    parms = str_parms_create_str(kvpairs);

	ALOGV("out_set_parameters: %s", kvpairs);

	ret = str_parms_get_str(parms, AUDIO_PARAMETER_STREAM_ROUTING, value, sizeof(value));
	if (ret >= 0) {
		F_LOG;
	    val = atoi(value);
	    pthread_mutex_lock(&adev->lock);
	    pthread_mutex_lock(&out->lock);
		ALOGV(">>>>>>>> adev->mode:%d,adev->out_device is: %d, val is : %d", adev->mode,adev->out_device, val);
        if ((adev->out_device  != val) && (val != 0)) {
            if (out == adev->active_output) {
            	F_LOG;
                /* a change in output device may change the microphone selection */
                if (adev->active_input &&
                        adev->active_input->source == AUDIO_SOURCE_VOICE_COMMUNICATION) {
                    force_input_standby = true;
                }
                /* force standby if moving to/from HDMI */
                if (((val & AUDIO_DEVICE_OUT_AUX_DIGITAL) ^
                        (adev->out_device & AUDIO_DEVICE_OUT_AUX_DIGITAL)) ||
                        ((val & AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET) ^
                        (adev->out_device & AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET)) ||
                        ((val & AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET) ^
                        (adev->out_device & AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET)))
                    do_output_standby(out);
            }
            adev->out_device = val;
			F_LOG;
            select_device(adev);
			if (adev->mode == AUDIO_MODE_IN_CALL || adev->mode == AUDIO_MODE_MODE_FACTORY_TEST || adev->mode == AUDIO_MODE_FM){
				adev_set_voice_volume(&adev->hw_device, adev->voice_volume);
			}
        }
        pthread_mutex_unlock(&out->lock);
        if (force_input_standby) {
            in = adev->active_input;
            pthread_mutex_lock(&in->lock);
            do_input_standby(in);
           	pthread_mutex_unlock(&in->lock);
        }
		pthread_mutex_unlock(&adev->lock);
	}

	ret = str_parms_get_str(parms, AUDIO_PARAMETER_RAW_DATA_OUT, value, sizeof(value));
	if (ret >= 0)
	{
		bool bval = (atoi(value) == 1) ? true : false;
		ALOGV("AUDIO_PARAMETER_RAW_DATA_OUT: %d", bval);
		pthread_mutex_lock(&adev->lock);
		pthread_mutex_lock(&out->lock);
		if (adev->raw_flag != bval)
		{
			adev->raw_flag = bval;
			do_output_standby(out);
		}
		pthread_mutex_unlock(&out->lock);
		pthread_mutex_unlock(&adev->lock);
	}

    str_parms_destroy(parms);
    return ret;
}

static char * out_get_parameters(const struct audio_stream *stream, const char *keys)
{
    return strdup("");
}

static uint32_t out_get_latency(const struct audio_stream_out *stream)
{
    struct sunxi_stream_out *out = (struct sunxi_stream_out *)stream;

    return (SHORT_PERIOD_SIZE * PLAYBACK_PERIOD_COUNT * 1000) / out->config.rate;
}

static int out_set_volume(struct audio_stream_out *stream, float left,
                          float right)
{
    return -ENOSYS;
}

static ssize_t out_write(struct audio_stream_out *stream, const void* buffer,
                         size_t bytes)
{
    int ret;
    struct sunxi_stream_out *out = (struct sunxi_stream_out *)stream;
    struct sunxi_audio_device *adev = out->dev;
    size_t frame_size = audio_stream_frame_size(&out->stream.common);
    size_t in_frames = bytes / frame_size;
    size_t out_frames = RESAMPLER_BUFFER_SIZE / frame_size;
    bool force_input_standby = false;
    struct sunxi_stream_in *in;
    int kernel_frames;
    void *buf;
	if (adev->mode == AUDIO_MODE_IN_CALL || adev->mode == AUDIO_MODE_MODE_FACTORY_TEST || adev->mode == AUDIO_MODE_FM)   //10-16 modify
	{
		return bytes;
		if((CASE_NAME == 2) || adev->out_device==AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET) {
			ALOGD("mode in call, do not out_write");
			return bytes;
		}

	}

	if (adev->raw_flag)
	{
		return 0;
	}
	if (last_communication_is_bt && (adev->mode != AUDIO_MODE_IN_COMMUNICATION || adev->mode == AUDIO_MODE_IN_COMMUNICATION && adev->out_device!=AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET)) {
		pcm_stop(adev->pcm_modem_dl);
		pcm_stop(adev->pcm_modem_ul);
		pcm_close(adev->pcm_modem_dl);
		pcm_close(adev->pcm_modem_ul);
		adev->pcm_modem_dl = NULL;
		adev->pcm_modem_ul = NULL;

		pcm_stop(adev->pcm_bt_dl);
		pcm_stop(adev->pcm_bt_ul);
		pcm_close(adev->pcm_bt_dl);
		pcm_close(adev->pcm_bt_ul);
		adev->pcm_bt_dl = NULL;
		adev->pcm_bt_ul = NULL;
		last_communication_is_bt = false;
	}
    /* acquiring hw device mutex systematically is useful if a low priority thread is waiting
     * on the output stream mutex - e.g. executing select_mode() while holding the hw device
     * mutex
     */
    pthread_mutex_lock(&adev->lock);
	pthread_mutex_lock(&out->lock);
    if (out->standby) {
        ret = start_output_stream(out);
        if (ret != 0) {
            pthread_mutex_unlock(&adev->lock);
            goto exit;
        }
        out->standby = 0;
        /* a change in output device may change the microphone selection */
        if (adev->active_input &&
                adev->active_input->source == AUDIO_SOURCE_VOICE_COMMUNICATION){
            force_input_standby = true;
	    F_LOG;
        }
    }
    pthread_mutex_unlock(&adev->lock);
#if 0
    out->write_threshold = SHORT_PERIOD_SIZE * PLAYBACK_PERIOD_COUNT;
    out->config.avail_min = SHORT_PERIOD_SIZE;
	pcm_set_avail_min(out->pcm, out->config.avail_min);
#endif
    /* only use resampler if required */
    if (out->resampler) {
        out->resampler->resample_from_input(out->resampler,
                                            (int16_t *)buffer,
                                            &in_frames,
                                            (int16_t *)out->buffer,
                                            &out_frames);
        buf = out->buffer;
    } else {
        out_frames = in_frames;
        buf = (void *)buffer;
    }
    if (out->echo_reference != NULL) {
        struct echo_reference_buffer b;
        b.raw = (void *)buffer;
        b.frame_count = in_frames;

        get_playback_delay(out, out_frames, &b);
        out->echo_reference->write(out->echo_reference, &b);
    }

#if 0
    /* do not allow more than out->write_threshold frames in kernel pcm driver buffer */
    do {
        struct timespec time_stamp;

        if (pcm_get_htimestamp(out->pcm, (unsigned int *)&kernel_frames, &time_stamp) < 0)
            break;
        kernel_frames = pcm_get_buffer_size(out->pcm) - kernel_frames;

        if (kernel_frames > out->write_threshold) {
            unsigned long time = (unsigned long)
                    (((int64_t)(kernel_frames - out->write_threshold) * 1000000) /
                            MM_SAMPLING_RATE);
            if (time < MIN_WRITE_SLEEP_US)
                time = MIN_WRITE_SLEEP_US;
            usleep(time);
        }
    } while (kernel_frames > out->write_threshold);
#endif

	if (adev->af_capture_flag && adev->PcmManager.BufExist) {
		WritePcmData((void *)buf, out_frames * frame_size, &adev->PcmManager);
		memset(buf, 0, out_frames * frame_size); //mute
	}

#if  USE_3D_SURROUND
	if (sur_enable(&sur)) {
		if (sur_prepare(&sur, adev->out_device, spk_dul_used, out->config.rate,
			out->config.channels, out_frames)) {
			sur_process(&sur, (short*)buf, out_frames, out->config.channels);
		}
	}
#endif

    ret = pcm_write(out->pcm, (void *)buf, out_frames * frame_size);
	if(ret!=0)
	{
		do_output_standby(out);
	}else if (ret == 0)
        	out->written += bytes / (out->config.channels * sizeof(short)
    );

exit:
    pthread_mutex_unlock(&out->lock);

    if (ret != 0) {
        usleep(bytes * 1000000 / audio_stream_frame_size(&stream->common) /
               out_get_sample_rate(&stream->common));
    }

    if (force_input_standby) {
        pthread_mutex_lock(&adev->lock);
        if (adev->active_input) {
            in = adev->active_input;
            pthread_mutex_lock(&in->lock);
            do_input_standby(in);
            pthread_mutex_unlock(&in->lock);
        }
        pthread_mutex_unlock(&adev->lock);
    }

    return bytes;
}

static int out_get_render_position(const struct audio_stream_out *stream,
                                   uint32_t *dsp_frames)
{
    return -EINVAL;
}

static int out_add_audio_effect(const struct audio_stream *stream, effect_handle_t effect)
{
    return 0;
}

static int out_remove_audio_effect(const struct audio_stream *stream, effect_handle_t effect)
{
    return 0;
}

static int out_get_next_write_timestamp(const struct audio_stream_out *stream,
                                        int64_t *timestamp)
{
    return -EINVAL;
}

/** audio_stream_in implementation **/

static int get_next_buffer(struct resampler_buffer_provider *buffer_provider,
                                   struct resampler_buffer* buffer);
static void release_buffer(struct resampler_buffer_provider *buffer_provider,
                                  struct resampler_buffer* buffer);

/* must be called with hw device and input stream mutexes locked */
static int start_input_stream(struct sunxi_stream_in *in)
{
	int ret = 0;
	int in_ajust_rate = 0;
	struct sunxi_audio_device *adev = in->dev;

	adev->active_input = in;

	if (adev->mode == AUDIO_MODE_IN_CALL) { // && adev->bluetooth_voice
		ALOGD("in call mode , start_input_stream, return");
	//return 0;
	}

	//if (adev->mode != AUDIO_MODE_IN_CALL) {
	F_LOG;
	adev->in_device = in->device;
	select_device(adev);
	//}

	if (in->need_echo_reference && in->echo_reference == NULL)
        	in->echo_reference = get_echo_reference(adev,
                                        AUDIO_FORMAT_PCM_16_BIT,
                                        in->config.channels,
                                        in->requested_rate);

	in_ajust_rate = in->requested_rate;
	ALOGD(">>>>>> in_ajust_rate is : %d", in_ajust_rate);
		// out/in stream should be both 44.1K serial
	switch(CASE_NAME){
	case 0 :
	case 1 :
		in_ajust_rate = SAMPLING_RATE_44K;
		ALOGV("%s, line: %d,adev->mode:%d,adev->out_device:%d", __FUNCTION__, __LINE__,adev->mode,adev->out_device);
		//if((adev->mode == AUDIO_MODE_IN_CALL) && (adev->out_device == AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET) )
		if((adev->mode == AUDIO_MODE_IN_CALL) && (adev->out_device == AUDIO_DEVICE_OUT_BLUETOOTH_SCO) ){
			ALOGV("%s, line: %d,adev->mode:%d,adev->out_device:%d", __FUNCTION__, __LINE__,adev->mode,adev->out_device);
			in_ajust_rate = SAMPLING_RATE_8K;
			in->config.rate = SAMPLING_RATE_8K;
		}
		if((adev->mode == AUDIO_MODE_IN_COMMUNICATION) && (adev->out_device == AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET) ){
			ALOGV("%s, line: %d,adev->mode:%d,adev->out_device:%d", __FUNCTION__, __LINE__,adev->mode,adev->out_device);
			in_ajust_rate = SAMPLING_RATE_8K;
			in->config.rate = SAMPLING_RATE_8K;
		}
		break;
	case 2 :
		if(adev->mode == AUDIO_MODE_IN_CALL)
			in_ajust_rate = in->requested_rate;
		else
			in_ajust_rate = SAMPLING_RATE_44K;

	default :
		break;

	}
#if 0
	#ifdef USERECORD_44100_SAMPLERATE
		in_ajust_rate = SAMPLING_RATE_44K;
	#else
		if (!(in->requested_rate % SAMPLING_RATE_11K))
		{
			// OK
			in_ajust_rate = in->requested_rate;
		}
		else
		{
			in_ajust_rate = SAMPLING_RATE_11K * in->requested_rate / SAMPLING_RATE_8K;
			if (in_ajust_rate > SAMPLING_RATE_44K)
			{

			}
			ALOGV("out/in stream should be both 44.1K serial, force capture rate: %d", in_ajust_rate);
		}
	#endif
#endif
	ALOGV("rate:%d, period_count:%d, period_size:%d,channels:%d",  in->config.rate, in->config.period_count, in->config.period_size,in->config.channels);
	ALOGV("in_ajust_rate:%d", in_ajust_rate);
	if (adev->mode == AUDIO_MODE_IN_CALL)
		//in->pcm = pcm_open_req(0, PORT_VIR_CODEC, PCM_IN, &in->config, in_ajust_rate);
	    in->pcm = pcm_open(0, PORT_VIR_CODEC, PCM_IN, &in->config);
	else
		//in->pcm = pcm_open_req(0, PORT_CODEC, PCM_IN, &in->config, in_ajust_rate);
		in->pcm = pcm_open(0, PORT_CODEC, PCM_IN, &in->config);

    	if (!pcm_is_ready(in->pcm)) {
            ALOGE("cannot open pcm_in driver: %s", pcm_get_error(in->pcm));
            pcm_close(in->pcm);
            adev->active_input = NULL;
            return -ENOMEM;
    	}

	if (in->requested_rate != in->config.rate) {
		in->buf_provider.get_next_buffer = get_next_buffer;
		in->buf_provider.release_buffer = release_buffer;

		ret = create_resampler(in->config.rate,
							   in->requested_rate,
							   in->config.channels,
							   RESAMPLER_QUALITY_DEFAULT,
							   &in->buf_provider,
							   &in->resampler);
		if (ret != 0) {
			ALOGE("create in resampler failed, %d -> %d", in->config.rate, in->requested_rate);
			ret = -EINVAL;
			goto err;
		}

		ALOGV("create in resampler OK, %d -> %d", in->config.rate, in->requested_rate);
	}
	else
	{
		ALOGV("do not use in resampler");
	}

    /* if no supported sample rate is available, use the resampler */
    if (in->resampler) {
        in->resampler->reset(in->resampler);
        in->frames_in = 0;
    }
    return 0;

err:
    if (in->resampler) {
        release_resampler(in->resampler);
    }

    return -1;
}

static uint32_t in_get_sample_rate(const struct audio_stream *stream)
{
    struct sunxi_stream_in *in = (struct sunxi_stream_in *)stream;

    return in->requested_rate;
}

static int in_set_sample_rate(struct audio_stream *stream, uint32_t rate)
{
    return 0;
}

static size_t in_get_buffer_size(const struct audio_stream *stream)
{
    struct sunxi_stream_in *in = (struct sunxi_stream_in *)stream;

    return get_input_buffer_size(in->requested_rate,
                                 AUDIO_FORMAT_PCM_16_BIT,
                                 in->config.channels);
}

static audio_channel_mask_t in_get_channels(const struct audio_stream *stream)
{
    struct sunxi_stream_in *in = (struct sunxi_stream_in *)stream;

    if (in->config.channels == 1) {
        return AUDIO_CHANNEL_IN_MONO;
    } else {
        return AUDIO_CHANNEL_IN_STEREO;
    }
}

static audio_format_t in_get_format(const struct audio_stream *stream)
{
    return AUDIO_FORMAT_PCM_16_BIT;
}

static int in_set_format(struct audio_stream *stream, audio_format_t format)
{
    return 0;
}

/* must be called with hw device and input stream mutexes locked */
static int do_input_standby(struct sunxi_stream_in *in)
{
    struct sunxi_audio_device *adev = in->dev;

    if (!in->standby) {
        pcm_close(in->pcm);
        in->pcm = NULL;

        adev->active_input = 0;
		if (in->resampler){
            release_resampler(in->resampler);
            in->resampler = 0;
        }
        if (adev->mode != AUDIO_MODE_IN_CALL) {
            adev->in_device = AUDIO_DEVICE_NONE;
            select_device(adev);
        }

        if (in->echo_reference != NULL) {
            /* stop reading from echo reference */
            in->echo_reference->read(in->echo_reference, NULL);
            put_echo_reference(adev, in->echo_reference);
            in->echo_reference = NULL;
        }

        in->standby = 1;
    }
    return 0;
}

static int in_standby(struct audio_stream *stream)
{
    struct sunxi_stream_in *in = (struct sunxi_stream_in *)stream;
    int status;

    pthread_mutex_lock(&in->dev->lock);
    pthread_mutex_lock(&in->lock);
    status = do_input_standby(in);
    pthread_mutex_unlock(&in->lock);
    pthread_mutex_unlock(&in->dev->lock);
    return status;
}

static int in_dump(const struct audio_stream *stream, int fd)
{
    return 0;
}

static int in_set_parameters(struct audio_stream *stream, const char *kvpairs)
{
    struct sunxi_stream_in *in = (struct sunxi_stream_in *)stream;
    struct sunxi_audio_device *adev = in->dev;
    struct str_parms *parms;
    char *str;
    char value[128];
    int ret, val = 0;
    bool do_standby = false;

    ALOGV("in_set_parameters: %s", kvpairs);

    parms = str_parms_create_str(kvpairs);

    ret = str_parms_get_str(parms, AUDIO_PARAMETER_STREAM_INPUT_SOURCE, value, sizeof(value));

    pthread_mutex_lock(&adev->lock);
    pthread_mutex_lock(&in->lock);
    if (ret >= 0) {
        val = atoi(value);
        /* no audio source uses val == 0 */
        if ((in->source != val) && (val != 0)) {
            in->source = val;
            do_standby = true;
        }
    }

    ret = str_parms_get_str(parms, AUDIO_PARAMETER_STREAM_ROUTING, value, sizeof(value));
    if (ret >= 0) {
        val = atoi(value) & ~AUDIO_DEVICE_BIT_IN;
        if ((adev->mode != AUDIO_MODE_IN_CALL) && (in->device != val) && (val != 0)) {
            in->device = val;
            do_standby = true;
        } else if((adev->mode == AUDIO_MODE_IN_CALL) && (in->source != val) && (val != 0)) {
            in->device = val;

	            select_device(adev);
		}
	}

    if (do_standby)
        do_input_standby(in);
    pthread_mutex_unlock(&in->lock);
    pthread_mutex_unlock(&adev->lock);

    str_parms_destroy(parms);
    return ret;
}

static char * in_get_parameters(const struct audio_stream *stream,
                                const char *keys)
{
    return strdup("");
}

static int in_set_gain(struct audio_stream_in *stream, float gain)
{
    return 0;
}

static void get_capture_delay(struct sunxi_stream_in *in,
                       size_t frames,
                       struct echo_reference_buffer *buffer)
{

    /* read frames available in kernel driver buffer */
    size_t kernel_frames;
    struct timespec tstamp;
    long buf_delay;
    long rsmp_delay;
    long kernel_delay;
    long delay_ns;

    if (pcm_get_htimestamp(in->pcm, &kernel_frames, &tstamp) < 0) {
        buffer->time_stamp.tv_sec  = 0;
        buffer->time_stamp.tv_nsec = 0;
        buffer->delay_ns           = 0;
        ALOGW("read get_capture_delay(): pcm_htimestamp error");
        return;
    }

    /* read frames available in audio HAL input buffer
     * add number of frames being read as we want the capture time of first sample
     * in current buffer */
    buf_delay = (long)(((int64_t)(in->frames_in + in->proc_frames_in) * 1000000000)
                                    / in->config.rate);
    /* add delay introduced by resampler */
    rsmp_delay = 0;
    if (in->resampler) {
        rsmp_delay = in->resampler->delay_ns(in->resampler);
    }

    kernel_delay = (long)(((int64_t)kernel_frames * 1000000000) / in->config.rate);

    delay_ns = kernel_delay + buf_delay + rsmp_delay;

    buffer->time_stamp = tstamp;
    buffer->delay_ns   = delay_ns;
    ALOGV("get_capture_delay time_stamp = [%ld].[%ld], delay_ns: [%d],"
         " kernel_delay:[%ld], buf_delay:[%ld], rsmp_delay:[%ld], kernel_frames:[%d], "
         "in->frames_in:[%d], in->proc_frames_in:[%d], frames:[%d]",
         buffer->time_stamp.tv_sec , buffer->time_stamp.tv_nsec, buffer->delay_ns,
         kernel_delay, buf_delay, rsmp_delay, kernel_frames,
         in->frames_in, in->proc_frames_in, frames);
}

static int32_t update_echo_reference(struct sunxi_stream_in *in, size_t frames)
{
    struct echo_reference_buffer b;
    b.delay_ns = 0;

    ALOGV("update_echo_reference, frames = [%d], in->ref_frames_in = [%d],  "
          "b.frame_count = [%d]",
    frames, in->ref_frames_in, frames - in->ref_frames_in);
    if (in->ref_frames_in < frames) {
        if (in->ref_buf_size < frames) {
            in->ref_buf_size = frames;
            in->ref_buf = (int16_t *)realloc(in->ref_buf,
                                             in->ref_buf_size *
                                                 in->config.channels * sizeof(int16_t));
        }

        b.frame_count = frames - in->ref_frames_in;
        b.raw = (void *)(in->ref_buf + in->ref_frames_in * in->config.channels);

        get_capture_delay(in, frames, &b);

        if (in->echo_reference->read(in->echo_reference, &b) == 0)
        {
            in->ref_frames_in += b.frame_count;
            ALOGV("update_echo_reference: in->ref_frames_in:[%d], "
                    "in->ref_buf_size:[%d], frames:[%d], b.frame_count:[%d]",
                 in->ref_frames_in, in->ref_buf_size, frames, b.frame_count);
        }
    } else
    ALOGW("update_echo_reference: NOT enough frames to read ref buffer");
    return b.delay_ns;
}

static int set_preprocessor_param(effect_handle_t handle,
                           effect_param_t *param)
{
    uint32_t size = sizeof(int);
    uint32_t psize = ((param->psize - 1) / sizeof(int) + 1) * sizeof(int) +
                        param->vsize;

    int status = (*handle)->command(handle,
                                   EFFECT_CMD_SET_PARAM,
                                   sizeof (effect_param_t) + psize,
                                   param,
                                   &size,
                                   &param->status);
    if (status == 0)
        status = param->status;

    return status;
}

static int set_preprocessor_echo_delay(effect_handle_t handle,
                                     int32_t delay_us)
{
    uint32_t buf[sizeof(effect_param_t) / sizeof(uint32_t) + 2];
    effect_param_t *param = (effect_param_t *)buf;

    param->psize = sizeof(uint32_t);
    param->vsize = sizeof(uint32_t);
    *(uint32_t *)param->data = AEC_PARAM_ECHO_DELAY;
    *((int32_t *)param->data + 1) = delay_us;

    return set_preprocessor_param(handle, param);
}

static void push_echo_reference(struct sunxi_stream_in *in, size_t frames)
{
    /* read frames from echo reference buffer and update echo delay
     * in->ref_frames_in is updated with frames available in in->ref_buf */
    int32_t delay_us = update_echo_reference(in, frames)/1000;
    int i;
    audio_buffer_t buf;

    if (in->ref_frames_in < frames)
        frames = in->ref_frames_in;

    buf.frameCount = frames;
    buf.raw = in->ref_buf;

    for (i = 0; i < in->num_preprocessors; i++) {
        if ((*in->preprocessors[i])->process_reverse == NULL)
            continue;

        (*in->preprocessors[i])->process_reverse(in->preprocessors[i],
                                               &buf,
                                               NULL);
        set_preprocessor_echo_delay(in->preprocessors[i], delay_us);
    }

    in->ref_frames_in -= buf.frameCount;
    if (in->ref_frames_in) {
        memcpy(in->ref_buf,
               in->ref_buf + buf.frameCount * in->config.channels,
               in->ref_frames_in * in->config.channels * sizeof(int16_t));
    }
}

static int get_next_buffer(struct resampler_buffer_provider *buffer_provider,
                                   struct resampler_buffer* buffer)
{
    struct sunxi_stream_in *in;

    if (buffer_provider == NULL || buffer == NULL)
        return -EINVAL;

    in = (struct sunxi_stream_in *)((char *)buffer_provider -
                                   offsetof(struct sunxi_stream_in, buf_provider));

    if (in->pcm == NULL) {
        buffer->raw = NULL;
        buffer->frame_count = 0;
        in->read_status = -ENODEV;
        return -ENODEV;
    }

//	ALOGV("get_next_buffer: in->config.period_size: %d, audio_stream_frame_size: %d",
//		in->config.period_size, audio_stream_frame_size(&in->stream.common));
    if (in->frames_in == 0) {
        in->read_status = pcm_read(in->pcm,
                                   (void*)in->buffer,
                                   in->config.period_size *
                                       audio_stream_frame_size(&in->stream.common));
        if (in->read_status != 0) {
            ALOGE("get_next_buffer() pcm_read error %d, %s", in->read_status, strerror(errno));
            buffer->raw = NULL;
            buffer->frame_count = 0;
            return in->read_status;
        }
        in->frames_in = in->config.period_size;
    }

    buffer->frame_count = (buffer->frame_count > in->frames_in) ?
                                in->frames_in : buffer->frame_count;
    buffer->i16 = in->buffer + (in->config.period_size - in->frames_in) *
                                                in->config.channels;

    return in->read_status;

}

static void release_buffer(struct resampler_buffer_provider *buffer_provider,
                                  struct resampler_buffer* buffer)
{
    struct sunxi_stream_in *in;

    if (buffer_provider == NULL || buffer == NULL)
        return;

    in = (struct sunxi_stream_in *)((char *)buffer_provider -
                                   offsetof(struct sunxi_stream_in, buf_provider));

    in->frames_in -= buffer->frame_count;
}

/* read_frames() reads frames from kernel driver, down samples to capture rate
 * if necessary and output the number of frames requested to the buffer specified */
static ssize_t read_frames(struct sunxi_stream_in *in, void *buffer, ssize_t frames)
{
    ssize_t frames_wr = 0;

    while (frames_wr < frames) {
        size_t frames_rd = frames - frames_wr;
        if (in->resampler != NULL) {
            in->resampler->resample_from_provider(in->resampler,
                    (int16_t *)((char *)buffer +
                            frames_wr * audio_stream_frame_size(&in->stream.common)),
                    &frames_rd);
        } else {
            struct resampler_buffer buf = {
                    { raw : NULL, },
                    frame_count : frames_rd,
            };
            get_next_buffer(&in->buf_provider, &buf);
            if (buf.raw != NULL) {
                memcpy((char *)buffer +
                           frames_wr * audio_stream_frame_size(&in->stream.common),
                        buf.raw,
                        buf.frame_count * audio_stream_frame_size(&in->stream.common));
                frames_rd = buf.frame_count;
            }
            release_buffer(&in->buf_provider, &buf);
        }
        /* in->read_status is updated by getNextBuffer() also called by
         * in->resampler->resample_from_provider() */
        if (in->read_status != 0)
            return in->read_status;

        frames_wr += frames_rd;
    }
    return frames_wr;
}

/* process_frames() reads frames from kernel driver (via read_frames()),
 * calls the active audio pre processings and output the number of frames requested
 * to the buffer specified */
static ssize_t process_frames(struct sunxi_stream_in *in, void* buffer, ssize_t frames)
{
    ssize_t frames_wr = 0;
    audio_buffer_t in_buf;
    audio_buffer_t out_buf;
    int i;

    while (frames_wr < frames) {
        /* first reload enough frames at the end of process input buffer */
        if (in->proc_frames_in < (size_t)frames) {
            ssize_t frames_rd;

            if (in->proc_buf_size < (size_t)frames) {
                in->proc_buf_size = (size_t)frames;
                in->proc_buf = (int16_t *)realloc(in->proc_buf,
                                         in->proc_buf_size *
                                             in->config.channels * sizeof(int16_t));
                ALOGV("process_frames(): in->proc_buf %p size extended to %d frames",
                     in->proc_buf, in->proc_buf_size);
            }
            frames_rd = read_frames(in,
                                    in->proc_buf +
                                        in->proc_frames_in * in->config.channels,
                                    frames - in->proc_frames_in);
            if (frames_rd < 0) {
                frames_wr = frames_rd;
                break;
            }
            in->proc_frames_in += frames_rd;
        }

        if (in->echo_reference != NULL)
            push_echo_reference(in, in->proc_frames_in);

         /* in_buf.frameCount and out_buf.frameCount indicate respectively
          * the maximum number of frames to be consumed and produced by process() */
        in_buf.frameCount 	= in->proc_frames_in;
        in_buf.s16 			= in->proc_buf;
        out_buf.frameCount 	= frames - frames_wr;
        out_buf.s16 = (int16_t *)buffer + frames_wr * in->config.channels;

        for (i = 0; i < in->num_preprocessors; i++)
            (*in->preprocessors[i])->process(in->preprocessors[i],
                                               &in_buf,
                                               &out_buf);

        /* process() has updated the number of frames consumed and produced in
         * in_buf.frameCount and out_buf.frameCount respectively
         * move remaining frames to the beginning of in->proc_buf */
        in->proc_frames_in -= in_buf.frameCount;
        if (in->proc_frames_in) {
            memcpy(in->proc_buf,
                   in->proc_buf + in_buf.frameCount * in->config.channels,
                   in->proc_frames_in * in->config.channels * sizeof(int16_t));
        }

        /* if not enough frames were passed to process(), read more and retry. */
        if (out_buf.frameCount == 0)
            continue;

        frames_wr += out_buf.frameCount;
    }
    return frames_wr;
}

static ssize_t in_read(struct audio_stream_in *stream, void* buffer,
                       size_t bytes)
{
    int ret = 0;
    struct sunxi_stream_in *in 		= (struct sunxi_stream_in *)stream;
    struct sunxi_audio_device *adev = in->dev;
    size_t frames_rq 				= bytes / audio_stream_frame_size(&stream->common);
    int is_first_data = 0;

    if (adev->mode == AUDIO_MODE_IN_CALL) {
	//ALOGD("in call mode, in_read, return ;");
        memset(buffer, 0, bytes);
	//usleep(10000);
	//return 1;
    }

    /* acquiring hw device mutex systematically is useful if a low priority thread is waiting
     * on the input stream mutex - e.g. executing select_mode() while holding the hw device
     * mutex
     */
    if (adev->af_capture_flag && adev->PcmManager.BufExist) {
	    pthread_mutex_lock(&adev->lock);
    	pthread_mutex_lock(&in->lock);
	    if (in->standby) {
	    	in->standby = 0;
	    }
	    pthread_mutex_unlock(&adev->lock);

	    if (ret < 0)
	        goto exit;

		//if (bytes > adev->PcmManager.DataLen)
			//usleep(10000);

	    ret = ReadPcmData(buffer, bytes, &adev->PcmManager);

	    if (ret > 0)
	        ret = 0;

	    if (ret == 0 && adev->mic_mute)
	        memset(buffer, 0, bytes);

	    pthread_mutex_unlock(&in->lock);
   		return bytes;
	}

	pthread_mutex_lock(&adev->lock);
	pthread_mutex_lock(&in->lock);
	if (in->standby) {
		ret = start_input_stream(in);
		is_first_data = 1;
		if (ret == 0)
			in->standby = 0;
	}
	pthread_mutex_unlock(&adev->lock);

    if (ret < 0)
        goto exit;

    if (in->num_preprocessors != 0) {
        ret = read_frames(in, buffer, frames_rq);//ret = process_frames(in, buffer, frames_rq);

    } else if (in->resampler != NULL) {
        ret = read_frames(in, buffer, frames_rq);

	} else {
        ret = pcm_read(in->pcm, buffer, bytes);
	}


	/* If connect headset, set the first data to zero.
	 * There may be a noise pulse.
	 */
	if (is_first_data &&
			(adev->in_device & AUDIO_DEVICE_IN_WIRED_HEADSET)) {
		memset(buffer, 0, bytes);
	}

    if (ret > 0)
        ret = 0;

    if (ret == 0 && adev->mic_mute)
        memset(buffer, 0, bytes);

exit:
    if (ret < 0)
        usleep(bytes * 1000000 / audio_stream_frame_size(&stream->common) /
               in_get_sample_rate(&stream->common));

    pthread_mutex_unlock(&in->lock);
    return bytes;
}

static uint32_t in_get_input_frames_lost(struct audio_stream_in *stream)
{
    return 0;
}

static int in_add_audio_effect(const struct audio_stream *stream,
                               effect_handle_t effect)
{
    struct sunxi_stream_in *in = (struct sunxi_stream_in *)stream;
    int status;
    effect_descriptor_t desc;

    pthread_mutex_lock(&in->dev->lock);
    pthread_mutex_lock(&in->lock);
    if (in->num_preprocessors >= MAX_PREPROCESSORS) {
        status = -ENOSYS;
        goto exit;
    }

    status = (*effect)->get_descriptor(effect, &desc);
    if (status != 0)
        goto exit;

    in->preprocessors[in->num_preprocessors++] = effect;

    if (memcmp(&desc.type, FX_IID_AEC, sizeof(effect_uuid_t)) == 0) {
        in->need_echo_reference = true;
        do_input_standby(in);
    }

exit:

    pthread_mutex_unlock(&in->lock);
    pthread_mutex_unlock(&in->dev->lock);
    return status;
}

static int in_remove_audio_effect(const struct audio_stream *stream,
                                  effect_handle_t effect)
{
    struct sunxi_stream_in *in = (struct sunxi_stream_in *)stream;
    int i;
    int status = -EINVAL;
    bool found = false;
    effect_descriptor_t desc;

    pthread_mutex_lock(&in->dev->lock);
    pthread_mutex_lock(&in->lock);
    if (in->num_preprocessors <= 0) {
        status = -ENOSYS;
        goto exit;
    }

    for (i = 0; i < in->num_preprocessors; i++) {
        if (found) {
            in->preprocessors[i - 1] = in->preprocessors[i];
            continue;
        }
        if (in->preprocessors[i] == effect) {
            in->preprocessors[i] = NULL;
            status = 0;
            found = true;
        }
    }

    if (status != 0)
        goto exit;

    in->num_preprocessors--;

    status = (*effect)->get_descriptor(effect, &desc);
    if (status != 0)
        goto exit;
    if (memcmp(&desc.type, FX_IID_AEC, sizeof(effect_uuid_t)) == 0) {
        in->need_echo_reference = false;
        do_input_standby(in);
    }

exit:

    pthread_mutex_unlock(&in->lock);
    pthread_mutex_unlock(&in->dev->lock);
    return status;
}

static int adev_open_output_stream(struct audio_hw_device *dev,
                                   audio_io_handle_t handle,
                                   audio_devices_t devices,
                                   audio_output_flags_t flags,
                                   struct audio_config *config,
                                   struct audio_stream_out **stream_out)
{
	struct sunxi_audio_device *ladev = (struct sunxi_audio_device *)dev;
    struct sunxi_stream_out *out;
    int ret;

	ALOGV("adev_open_output_stream, flags: %x", flags);

    out = (struct sunxi_stream_out *)calloc(1, sizeof(struct sunxi_stream_out));
    if (!out)
        return -ENOMEM;

    out->buffer = malloc(RESAMPLER_BUFFER_SIZE); /* todo: allow for reallocing */

    out->stream.common.get_sample_rate 	= out_get_sample_rate;
    out->stream.common.set_sample_rate 	= out_set_sample_rate;
    out->stream.common.get_buffer_size 	= out_get_buffer_size;
    out->stream.common.get_channels 	= out_get_channels;
    out->stream.common.get_format 		= out_get_format;
    out->stream.common.set_format 		= out_set_format;
    out->stream.common.standby 			= out_standby;
    out->stream.common.dump 			= out_dump;
    out->stream.common.set_parameters 	= out_set_parameters;
    out->stream.common.get_parameters 	= out_get_parameters;
    out->stream.common.add_audio_effect = out_add_audio_effect;
    out->stream.common.remove_audio_effect = out_remove_audio_effect;
    out->stream.get_latency 			= out_get_latency;
    out->stream.set_volume 				= out_set_volume;
    out->stream.write 					= out_write;
    out->stream.get_render_position 	= out_get_render_position;
	out->stream.get_next_write_timestamp = out_get_next_write_timestamp;
	out->stream.get_presentation_position = out_get_presentation_position;

    out->config 						= pcm_config_mm_out;

    out->dev 		= ladev;
    out->standby 	= 1;

    /* FIXME: when we support multiple output devices, we will want to
     * do the following:
	 * adev->out_device = out->device;
     * select_output_device(adev);
     * This is because out_set_parameters() with a route is not
     * guaranteed to be called after an output stream is opened. */
	config->format 			= out_get_format(&out->stream.common);
    config->channel_mask 	= out_get_channels(&out->stream.common);
    config->sample_rate 	= out_get_sample_rate(&out->stream.common);

	ALOGV("+++++++++++++++ adev_open_output_stream: req_sample_rate: %d, fmt: %x, channel_count: %d",
		config->sample_rate, config->format, config->channel_mask);

    *stream_out = &out->stream;
    return 0;

err_open:
    free(out);
    *stream_out = NULL;
    return ret;
}

static void adev_close_output_stream(struct audio_hw_device *dev,
                                     struct audio_stream_out *stream)
{
    struct sunxi_stream_out *out = (struct sunxi_stream_out *)stream;
    struct sunxi_audio_device *adev = out->dev;

	if (adev->mode == AUDIO_MODE_IN_CALL || adev->mode == AUDIO_MODE_MODE_FACTORY_TEST  || adev->mode == AUDIO_MODE_FM)
	{
		ALOGW("mode in call, do not adev_close_output_stream");
		return ;
	}
    out_standby(&stream->common);

    if (out->buffer){
        free(out->buffer);
		out->buffer = 0;
	}
    if (out->resampler){
        release_resampler(out->resampler);
		out->resampler = 0;
    }
    free(stream);
}

static int adev_set_parameters(struct audio_hw_device *dev, const char *kvpairs)
{
    struct sunxi_audio_device *adev = (struct sunxi_audio_device *)dev;
    struct str_parms *parms;
    char *str;
    char value[32];
    int ret;

	ALOGV("adev_set_parameters, %s", kvpairs);

    parms 	= str_parms_create_str(kvpairs);
    ret 	= str_parms_get_str(parms, AUDIO_PARAMETER_KEY_TTY_MODE, value, sizeof(value));
    if (ret >= 0) {
        int tty_mode;

        if (strcmp(value, AUDIO_PARAMETER_VALUE_TTY_OFF) == 0)
            tty_mode = TTY_MODE_OFF;
        else if (strcmp(value, AUDIO_PARAMETER_VALUE_TTY_VCO) == 0)
            tty_mode = TTY_MODE_VCO;
        else if (strcmp(value, AUDIO_PARAMETER_VALUE_TTY_HCO) == 0)
            tty_mode = TTY_MODE_HCO;
        else if (strcmp(value, AUDIO_PARAMETER_VALUE_TTY_FULL) == 0)
            tty_mode = TTY_MODE_FULL;
        else
            return -EINVAL;

        pthread_mutex_lock(&adev->lock);
        if (tty_mode != adev->tty_mode) {
            adev->tty_mode = tty_mode;
            if (adev->mode == AUDIO_MODE_IN_CALL || adev->mode == AUDIO_MODE_MODE_FACTORY_TEST || adev->mode == AUDIO_MODE_FM) {
                select_device(adev);
            	adev_set_voice_volume(&adev->hw_device, adev->voice_volume);
			}
        }
        pthread_mutex_unlock(&adev->lock);
    }

    ret = str_parms_get_str(parms, AUDIO_PARAMETER_KEY_BT_NREC, value, sizeof(value));
    if (ret >= 0) {
        if (strcmp(value, AUDIO_PARAMETER_VALUE_ON) == 0)
            adev->bluetooth_nrec = true;
        else
            adev->bluetooth_nrec = false;
    }

    str_parms_destroy(parms);
    return ret;
}

static char * adev_get_parameters(const struct audio_hw_device *dev,
                                  const char *keys)
{
    if (!strcmp(keys, "routing"))
    {
	char prop_value[512];
	int ret = property_get("audio.routing", prop_value, "");
	if (ret > 0)
	{
	    return strdup(prop_value);
	}
    }
    return strdup("");
}

static int adev_init_check(const struct audio_hw_device *dev)
{
    return 0;
}

static int adev_set_voice_volume(struct audio_hw_device *dev, float volume)
{
        struct sunxi_audio_device *adev = (struct sunxi_audio_device *)dev;

	ALOGV("adev_set_voice_volume, volume: %f", volume);

	if (adev->mode == AUDIO_MODE_FM) {
		fm_volume(adev->out_device,(int)(volume*100/10));
		ALOGV("set fm debug,adev_set_voice_volume, volume: %f, intege of volume: %d", volume, (int)(volume*100/10));
	} else if (adev->mode == AUDIO_MODE_IN_CALL) {
		int level;
		int speaker_on=0,headset_on=0 ,headphone_on=0,earpiece_on=0;
		int speaker_vol=0, headset_vol=0, earpiece_vol=0;
		int volume_codec = (int)(volume*100/10);
		speaker_on = adev->out_device & AUDIO_DEVICE_OUT_SPEAKER;
		headset_on = adev->out_device & AUDIO_DEVICE_OUT_WIRED_HEADSET;  // with mic
		headphone_on = adev->out_device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE; // no mic

		earpiece_on = adev->out_device & AUDIO_DEVICE_OUT_EARPIECE;

		if (volume_codec >= 10) {
			level = 5;
		} else if (volume_codec >= 8){
			level = 4;
		} else if (volume_codec >= 6){
			level = 3;
		} else if (volume_codec >= 4){
			level = 2;
		} else if (volume_codec >= 2){
			level = 1;
		} else {
			level = 0;
		}

		ALOGD("adev_set_voice_volume, speaker_on: %d, earpiece_on: %d, headset_on: %d", speaker_on ,earpiece_on, headset_on);
		ALOGD("adev_set_voice_volume, volume:%d, level: %d", volume_codec, level);
		#if 0
		int i=0;
		for(i=0;i<=5;i++){
			ALOGD("\nadev->vol_array->speaker_spk_gain[level]:%d\n",adev->vol_array->speaker_spk_gain[i]);
			ALOGD("\nadev->vol_array->earpiece_hp_gain[level]:%d\n",adev->vol_array->earpiece_hp_gain[i]);
			ALOGD("\nadev->vol_array->headset_hp_gain[level]:%d\n",adev->vol_array->headset_hp_gain[i]);
		}
		#endif
		if (speaker_on  || (earpiece_on  && (NO_EARPIECE == 1))){
			mixer_ctl_set_value(adev->mixer_volumectls.spkvolume, 0, adev->vol_array->speaker_spk_gain[level]);
			ALOGD("adev_set_voice_volume, speaker volume:%d ", adev->vol_array->speaker_spk_gain[level]);
		} else if (earpiece_on && (NO_EARPIECE == 0) ){
			mixer_ctl_set_value(adev->mixer_volumectls.earpiecevolume, 0, adev->vol_array->earpiece_hp_gain[level]);
			ALOGD("adev_set_voice_volume, earpiece volume:%d ", adev->vol_array->earpiece_hp_gain[level]);
		} else if (headset_on || headphone_on){
			mixer_ctl_set_value(adev->mixer_volumectls.hpvolume, 0, adev->vol_array->headset_hp_gain[level]);
			ALOGD("adev_set_voice_volume, headset volume:%d ", adev->vol_array->headset_hp_gain[level]);
		}
		set_bp_volume(adev,(int)(volume*100/10));
		ALOGV("set phone debug,adev_set_voice_volume, volume: %f, intege of volume: %d", volume, (int)(volume*100/10));
	}
    return 0;
}

static int adev_set_master_volume(struct audio_hw_device *dev, float volume)
{
	F_LOG;
    return -ENOSYS;
}

static int adev_get_master_volume(struct audio_hw_device *dev, float *volume)
{
	F_LOG;
	return -ENOSYS;
}

static int adev_set_mode(struct audio_hw_device *dev, audio_mode_t mode)
{
    struct sunxi_audio_device *adev = (struct sunxi_audio_device *)dev;

    pthread_mutex_lock(&adev->lock);
    if (adev->mode != mode) {
        adev->mode = mode;
        select_mode(adev);
    }
    pthread_mutex_unlock(&adev->lock);

    return 0;
}

static int adev_set_mic_mute(struct audio_hw_device *dev, bool state)
{
    struct sunxi_audio_device *adev = (struct sunxi_audio_device *)dev;

    adev->mic_mute = state;

    return 0;
}

static int adev_get_mic_mute(const struct audio_hw_device *dev, bool *state)
{
    struct sunxi_audio_device *adev = (struct sunxi_audio_device *)dev;

    *state = adev->mic_mute;

    return 0;
}

static size_t adev_get_input_buffer_size(const struct audio_hw_device *dev,
                                         const struct audio_config *config)
{
    size_t size;
    int channel_count = popcount(config->channel_mask);
    if (check_input_parameters(config->sample_rate, config->format, channel_count) != 0)
        return 0;

    return get_input_buffer_size(config->sample_rate, config->format, channel_count);
}

static int adev_open_input_stream(struct audio_hw_device *dev,
                                  audio_io_handle_t handle,
                                  audio_devices_t devices,
                                  struct audio_config *config,
                                  struct audio_stream_in **stream_in)
{
    struct sunxi_audio_device *ladev = (struct sunxi_audio_device *)dev;
    struct sunxi_stream_in *in;
    int ret;
    int channel_count = popcount(config->channel_mask);

    *stream_in = NULL;

    if (check_input_parameters(config->sample_rate, config->format, channel_count) != 0)
        return -EINVAL;

    in = (struct sunxi_stream_in *)calloc(1, sizeof(struct sunxi_stream_in));
    if (!in)
        return -ENOMEM;

    in->stream.common.get_sample_rate 	= in_get_sample_rate;
    in->stream.common.set_sample_rate 	= in_set_sample_rate;
    in->stream.common.get_buffer_size 	= in_get_buffer_size;
    in->stream.common.get_channels 		= in_get_channels;
    in->stream.common.get_format 		= in_get_format;
    in->stream.common.set_format 		= in_set_format;
    in->stream.common.standby 			= in_standby;
    in->stream.common.dump 				= in_dump;
    in->stream.common.set_parameters 	= in_set_parameters;
    in->stream.common.get_parameters 	= in_get_parameters;
    in->stream.common.add_audio_effect 	= in_add_audio_effect;
    in->stream.common.remove_audio_effect = in_remove_audio_effect;
    in->stream.set_gain = in_set_gain;
    in->stream.read 	= in_read;
    in->stream.get_input_frames_lost = in_get_input_frames_lost;

    in->requested_rate 	= config->sample_rate;

    // default config
    memcpy(&in->config, &pcm_config_mm_in, sizeof(pcm_config_mm_in));
    in->config.channels = channel_count;
    //in->config.in_init_channels = channel_count;

    ALOGV("to malloc in-buffer: period_size: %d, frame_size: %d",
	in->config.period_size, audio_stream_frame_size(&in->stream.common));
    in->buffer = malloc(in->config.period_size *
                        audio_stream_frame_size(&in->stream.common) * 8);

    if (!in->buffer) {
        ret = -ENOMEM;
        goto err;
    }
    memset(in->buffer, 0, in->config.period_size *
                audio_stream_frame_size(&in->stream.common) * 8); //mute

    ladev->af_capture_flag = false;
    //devices = AUDIO_DEVICE_IN_WIFI_DISPLAY;//for test

    if (devices == AUDIO_DEVICE_IN_AF) {
		ALOGV("to malloc PcmManagerBuffer: Buffer_size: %d", AF_BUFFER_SIZE);
		ladev->PcmManager.BufStart= (unsigned char *)malloc(AF_BUFFER_SIZE);

		if(!ladev->PcmManager.BufStart) {
			ret = -ENOMEM;
			goto err;
   		}

		ladev->PcmManager.BufExist 		= true;
		ladev->PcmManager.BufTotalLen 	= AF_BUFFER_SIZE;
		ladev->PcmManager.BufWritPtr 	= ladev->PcmManager.BufStart;
		ladev->PcmManager.BufReadPtr 	= ladev->PcmManager.BufStart;
		ladev->PcmManager.BufValideLen	= ladev->PcmManager.BufTotalLen;
		ladev->PcmManager.DataLen 		= 0;
		ladev->PcmManager.SampleRate 	= config->sample_rate;
		ladev->PcmManager.Channel 		= 2;
		ladev->af_capture_flag 			= true;

		ladev->PcmManager.dev 			= (struct sunxi_audio_device *)ladev;
    }

    in->dev 	= ladev;
    in->standby = 1;
    in->device 	= devices & ~AUDIO_DEVICE_BIT_IN;

    *stream_in 	= &in->stream;
    return 0;

err:
    if (in->resampler)
        release_resampler(in->resampler);

    free(in);
    return ret;
}

static void adev_close_input_stream(struct audio_hw_device *dev,
                                   struct audio_stream_in *stream)
{
    struct sunxi_stream_in *in 			= (struct sunxi_stream_in *)stream;
    struct sunxi_audio_device *ladev 	= (struct sunxi_audio_device *)dev;

    in_standby(&stream->common);

    if (in->buffer) {
        free(in->buffer);
	in->buffer = 0;
    }
    if (in->resampler) {
        release_resampler(in->resampler);
	in->resampler = 0;
    }
    if (ladev->af_capture_flag) {
	ladev->af_capture_flag = false;
    }
    if (ladev->PcmManager.BufStart) {
	ladev->PcmManager.BufExist = false;
	free(ladev->PcmManager.BufStart);
	ladev->PcmManager.BufStart = 0;
    }
    free(stream);

    normal_record_enable(false);
    fm_record_enable(false);
    phone_record_enable(false);
    ALOGD("adev_close_input_stream set voice record status");
    return;
}

static int adev_dump(const audio_hw_device_t *device, int fd)
{
	return 0;
}

static int adev_close(hw_device_t *device)
{
	struct sunxi_audio_device *adev = (struct sunxi_audio_device *)device;
	audio_route_free(adev->ar);
	free(adev->vol_array);
	free(device);
#if USE_3D_SURROUND
	sur_exit(&sur);
#endif
	return 0;
}

static int case_init(void)
{
	int ret = -1;
	char prop_value[PROPERTY_VALUE_MAX];
	char device_name[PROPERTY_VALUE_MAX]={0};
	char dmic_usename[PROPERTY_VALUE_MAX]={0};

	ret = property_get("audio.without.earpiece", prop_value, "1");
	if (ret > 0)
	{
		if (atoi(prop_value) == 0)
		{
			NO_EARPIECE = 0;
			ALOGD("get property audio.without.earpiece: %d", NO_EARPIECE);
		}
	}
	ALOGV("******* no_earpiece is : %d", NO_EARPIECE);
	ALOGV("****LINE:%d,FUNC:%s, ret:%d",__LINE__,__FUNCTION__, ret);

	ret = property_get("ro.dmic.used", dmic_usename, "0");
	if(ret <= 0){
		ALOGE("wrn: get ro.dmic.used failed");
	}
	if(!strcmp(dmic_usename, "true")) {
		dmic_used = true;
	}else{
		dmic_used = false;
	}

	ret = property_get("ro.spk_dul.used", prop_value, "0");
	if (ret <= 0) {
		ALOGE("wrn: ro.spk_dul.used");
	}
	if (!strcmp(prop_value, "true")) {
		spk_dul_used = true;
	} else {
		spk_dul_used = false;
	}

	ret = property_get("ro.sw.audio.codec_plan_name", device_name, "0");
	if(ret <= 0){
		ALOGE("wrn: get ro.sw.audio.codec_plan_name failed");
	}
	ALOGD("PROPERTY_VALUE_MAX:%d,get ro.sw.audio.codec_plan_name =%s", PROPERTY_VALUE_MAX, device_name);
	if (strstr(device_name, "PLAN_ONE") != NULL){
		/*case:plan_one*/
		CASE_NAME = 1;
        }else if (strstr(device_name, "PLAN_TWO") != NULL){
		/*case:plan_two*/
		CASE_NAME = 2;
        } else {
        	/*case:pad*/
		CASE_NAME = 0;
	}

	return ret;
}
static int adev_open(const hw_module_t* module, const char* name,
                     hw_device_t** device)
{
	struct sunxi_audio_device *adev;
	int ret;

	if (strcmp(name, AUDIO_HARDWARE_INTERFACE) != 0)
        	return -EINVAL;

	adev = calloc(1, sizeof(struct sunxi_audio_device));
	if (!adev)
        	return -ENOMEM;
	adev->hw_device.common.tag 		= HARDWARE_DEVICE_TAG;
	adev->hw_device.common.version 	= AUDIO_DEVICE_API_VERSION_2_0;
	adev->hw_device.common.module 	= (struct hw_module_t *) module;
	adev->hw_device.common.close 	= adev_close;

	adev->hw_device.init_check 				= adev_init_check;
	adev->hw_device.set_voice_volume 		= adev_set_voice_volume;
	adev->hw_device.set_master_volume 		= adev_set_master_volume;
	adev->hw_device.get_master_volume 		= adev_get_master_volume;
	adev->hw_device.set_mode 				= adev_set_mode;
	adev->hw_device.set_mic_mute 			= adev_set_mic_mute;
	adev->hw_device.get_mic_mute 			= adev_get_mic_mute;
	adev->hw_device.set_parameters 			= adev_set_parameters;
	adev->hw_device.get_parameters 			= adev_get_parameters;
	adev->hw_device.get_input_buffer_size 	= adev_get_input_buffer_size;
	adev->hw_device.open_output_stream 		= adev_open_output_stream;
	adev->hw_device.close_output_stream 	= adev_close_output_stream;
	adev->hw_device.open_input_stream 		= adev_open_input_stream;
	adev->hw_device.close_input_stream 		= adev_close_input_stream;
	adev->hw_device.dump 					= adev_dump;
	adev->raw_flag 							= false;
	/* Set the default route before the PCM stream is opened */
	pthread_mutex_lock(&adev->lock);

	adev->mode 		= AUDIO_MODE_NORMAL;
	adev->out_device = AUDIO_DEVICE_OUT_SPEAKER;
	adev->in_device = AUDIO_DEVICE_IN_BUILTIN_MIC & ~AUDIO_DEVICE_BIT_IN;
	adev->ar = audio_route_init(MIXER_CARD, AC100_XML_PATH);
	//select_device(adev);
	adev->pcm_modem_dl 		= NULL;
	adev->pcm_modem_ul 		= NULL;
	adev->voice_volume 		= 1.0f;
	adev->tty_mode 			= TTY_MODE_OFF;
	adev->bluetooth_nrec 	= false;
	adev->wb_amr 			= 0;
	adev->fm_mode = 2;
	//adev->vol_array =
	adev->mixer = mixer_open(MIXER_CARD);
	if (!adev->mixer) {
		ALOGE("Unable to open the mixer, aborting.");
	}
	/*volume ctl*/
	adev->mixer_volumectls.hpvolume = mixer_get_ctl_by_name(adev->mixer,
								MIXER_HP_VOLUME);
	adev->mixer_volumectls.spkvolume = mixer_get_ctl_by_name(adev->mixer,
								MIXER_SPK_VOLUME);
	adev->mixer_volumectls.earpiecevolume = mixer_get_ctl_by_name(adev->mixer,
								MIXER_EARPIECE_VOLUME);
	pthread_mutex_unlock(&adev->lock);
	*device = &adev->hw_device.common;
	adev->vol_array = calloc(1, sizeof(struct volume_array));
	codec_voice_volume_init(adev->vol_array);
	ALOGD("line:%d,func:%s\n", __LINE__, __FUNCTION__);
	if (ril_dev_init() < 0 ) {
		ALOGE("err: ril_dev_init ****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__);
	}
	case_init();
#if USE_3D_SURROUND
	sur_init(&sur, MM_SAMPLING_RATE, 2, SHORT_PERIOD_SIZE);
#endif
	return 0;

error_out:
	free(adev);
	return -EINVAL;
}

static struct hw_module_methods_t hal_module_methods = {
    .open = adev_open,
};

struct audio_module HAL_MODULE_INFO_SYM = {
    .common = {
        .tag 				= HARDWARE_MODULE_TAG,
        .module_api_version = AUDIO_MODULE_API_VERSION_0_1,
		.hal_api_version 	= HARDWARE_HAL_API_VERSION,
        .id 				= AUDIO_HARDWARE_MODULE_ID,
        .name 				= "sunxi audio HW HAL",
        .author 			= "author",
        .methods 			= &hal_module_methods,
    },
};
