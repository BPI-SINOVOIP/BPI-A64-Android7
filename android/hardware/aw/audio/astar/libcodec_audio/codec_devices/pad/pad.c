
#define LOG_TAG "codec_audio_pad"
#define LOG_NDEBUG 0

#include <stdlib.h>
#include <string.h>
#include <utils/Log.h>

#include <cutils/properties.h>

#include <system/audio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include "hal_codec.h"
#include "pad.h"

static bool in_call = false;

static void set_headphone_volume(struct mixer_ctl *reg, int volume)
{
	int i = 0; 
	int val=0;

	val = mixer_ctl_get_value(reg, 0);

	if(val == volume){
		//ALOGV("volume equal");
		return;
	} 

	if(val >volume){
		for(i=val; i>=volume; i--){
			mixer_ctl_set_value(reg, 0, i);
			usleep(5000);
		}
	} else {
		for(i=val; i<=volume; i++){
			mixer_ctl_set_value(reg, 0, i);
			usleep(5000);
		}
	}
}
static int set_normal_volume(struct codec_client *client, int path, int vol)
{
	int headset_on=0, headphone_on=0, speaker_on=0;

	headset_on = path & AUDIO_DEVICE_OUT_WIRED_HEADSET;  // hp4p
	headphone_on = path & AUDIO_DEVICE_OUT_WIRED_HEADPHONE; // hp3p 
	speaker_on = path & AUDIO_DEVICE_OUT_SPEAKER;

	if (speaker_on){
		ALOGV("****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__);
		mixer_ctl_set_value(client->mixer_ctls->speaker_volume_control, 0, vol);
	} else if ((headset_on || headphone_on)){
		ALOGV("****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__);
		mixer_ctl_set_value(client->mixer_ctls->headphone_volume_control, 0, vol);
	}

	return 0;
}

static int set_normal_path(struct codec_client *client, int path)
{
	int headset_on=0, headphone_on=0, speaker_on=0, earpiece_on=0;
	int switch_to_headset  =0;
	int ret = -1, fd=0;
	char prop_value[PROPERTY_VALUE_MAX]={0};
	char h2w_state[2]={0};

	headset_on = path & AUDIO_DEVICE_OUT_WIRED_HEADSET;  // hp4p
	headphone_on = path & AUDIO_DEVICE_OUT_WIRED_HEADPHONE; // hp3p 
	speaker_on = path & AUDIO_DEVICE_OUT_SPEAKER;
	earpiece_on = path & AUDIO_DEVICE_OUT_EARPIECE;

        mixer_ctl_set_value(client->mixer_ctls->audio_linein_in, 0, 0);  //turn off fm

	ret = property_get("dev.bootcomplete", prop_value, "0");
	if (ret > 0)
	{
		if (atoi(prop_value) == 0){
			fd = open("/sys/class/switch/h2w/state", O_RDONLY);
			if(fd>0){
				ret = read(fd, h2w_state, sizeof(h2w_state));
				close(fd);

				if ( (atoi(h2w_state) == 2 || atoi(h2w_state) == 1) )
				{
					switch_to_headset  =1;
				}
			}
		}
	}
	ALOGV("headset on is: %d, headphone on is: %d, speaker on is : %d ", headset_on, headphone_on, speaker_on);
	if(in_call){
		in_call = false;
		mixer_ctl_set_value(client->mixer_ctls->audio_phone_end_call, 0, 1);
	}
        mixer_ctl_set_value(client->mixer_ctls->audio_linein_in, 0, 0);  //turn off fm

	if (((headset_on || headphone_on) && speaker_on)){
		ALOGV("in normal mode, headset and speaker on,****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__);
		mixer_ctl_set_enum_by_string(client->mixer_ctls->audio_spk_headset_switch, "spk_headset");
	} else if(earpiece_on) {
		mixer_ctl_set_enum_by_string(client->mixer_ctls->audio_spk_headset_switch, "spk");
		ALOGV("in earpiece mode, pad no earpiece but spk,****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__); //no earpiece
	} else if(switch_to_headset) {
		mixer_ctl_set_enum_by_string(client->mixer_ctls->audio_spk_headset_switch, "headset");
		ALOGV("in boot switch_to_headset mode, headset,****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__); 
		switch_to_headset = 0;
	} else {
		ALOGV("in normal mode, headset or speaker on,****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__);
		mixer_ctl_set_enum_by_string(client->mixer_ctls->audio_spk_headset_switch, speaker_on ? "spk" : "headset");
	}
	return 0;
}

static int set_normal_record_enable(struct codec_client *client, bool enable)
{
	mixer_ctl_set_value(client->mixer_ctls->audio_linein_record, 0, 0);
	mixer_ctl_set_value(client->mixer_ctls->audio_phone_voice_record, 0, 0);
	ALOGV("normal record mode 4,****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__);
	return 0;
}

static int set_normal_record(struct codec_client *client, int path)
{
	ALOGV("normal record mode 4,****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__);
	return 0;
}


static int set_fm_volume(struct codec_client *client, int path, int volume)
{
	int speaker_on=0,headset_on=0 ,headphone_on=0;
	int level;

	headset_on = path & AUDIO_DEVICE_OUT_WIRED_HEADSET;  // hp4p
	headphone_on = path & AUDIO_DEVICE_OUT_WIRED_HEADPHONE; // hp3p 
	speaker_on = path & AUDIO_DEVICE_OUT_SPEAKER;

	if (volume >= 10) {
		level = 5;
	} else if (volume >= 8){
		level = 4;
	} else if (volume >= 6){
		level = 3;
	} else if (volume >= 4){
		level = 2;
	} else if (volume >= 2){
		level = 1;
	} else {
		level = 0;
	}

	if (speaker_on){
		//mixer_ctl_set_value(client->mixer_ctls->linein_g_boost_stage_output_mixer_control, 0, client->vol_array->fm_speaker_line_gain[level]);
		mixer_ctl_set_value(client->mixer_ctls->speaker_volume_control, 0, client->vol_array->fm_speaker_spk_gain[level]);
		//set_headphone_volume(client->mixer_ctls->lineout_volume_control, client->vol_array->fm_speaker_spk_gain[level]);
	} else {
		//mixer_ctl_set_value(client->mixer_ctls->linein_g_boost_stage_output_mixer_control, 0,  client->vol_array->fm_headset_line_gain[level]);
		mixer_ctl_set_value(client->mixer_ctls->headphone_volume_control, 0, client->vol_array->fm_headset_hp_gain[level]);
		//set_headphone_volume(client->mixer_ctls->master_playback_volume, client->vol_array->fm_headset_hp_gain[level]);
	}

	ALOGV("4 set fm , adev_set_voice_volume, volume: %d, level=%d", volume, level);
	return 0;
}

static int fm_last_dev=0; // 1 = speaker, 2= headset;
static int set_fm_path(struct codec_client *client, int path)
{
	int headset_on=0, headphone_on=0, speaker_on=0;

	headset_on = path & AUDIO_DEVICE_OUT_WIRED_HEADSET;  // hp4p
	headphone_on = path & AUDIO_DEVICE_OUT_WIRED_HEADPHONE; // hp3p 
	speaker_on = path & AUDIO_DEVICE_OUT_SPEAKER;


	ALOGV("FM mode, devices is:%d, ****LINE:%d,FUNC:%s", path ,__LINE__,__FUNCTION__);

	mixer_ctl_set_value(client->mixer_ctls->audio_phone_end_call, 0, 1);
	mixer_ctl_set_value(client->mixer_ctls->audio_linein_in, 0, 1);  

	//speaker and headset all turn on , direct return. Don't do with alarm and ringtone in fm mode;
	if( speaker_on && (headphone_on || headset_on) ){ 
		ALOGV("FM mode Don't do with alarm and ringtone in fm mode, ****LINE:%d,FUNC:%s", __LINE__,__FUNCTION__);
		if (fm_last_dev!=0){
			speaker_on = fm_last_dev == 1 ? 1 : 0;
		} else {
			return 0;
		}
	}

	if (speaker_on){
		mixer_ctl_set_value(client->mixer_ctls->audio_headphone_out, 0, 0);
		mixer_ctl_set_value(client->mixer_ctls->audio_speaker_out, 0, 1);
		fm_last_dev=1;
	} else {
		mixer_ctl_set_value(client->mixer_ctls->audio_speaker_out, 0, 0);
		mixer_ctl_set_value(client->mixer_ctls->audio_headphone_out, 0, 1);
		fm_last_dev=2;
	} 
	ALOGV("FM mode 4, devices is %s, ****LINE:%d,FUNC:%s", speaker_on ? "spk" : "headset",__LINE__,__FUNCTION__);

	return 0;
}
static int set_fm_record_enable(struct codec_client *client, bool enable)
{
	mixer_ctl_set_value(client->mixer_ctls->audio_phone_voice_record, 0, 0);

	if (enable){
		mixer_ctl_set_value(client->mixer_ctls->audio_linein_record, 0, 1);
	} else {
		mixer_ctl_set_value(client->mixer_ctls->audio_linein_record, 0, 0);
	}
	ALOGV("fm record mode 4,****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__);
	return 0;
}

static int set_fm_record(struct codec_client *client, int path)
{
	ALOGV("FM record mode 4, ****LINE:%d,FUNC:%s", __LINE__,__FUNCTION__);
	return 0;
}



int pad_init(void)
{
	ALOGV("****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__);
	return 0;
}

void pad_exit(void)
{
	ALOGV("****LINE:%d,FUNC:%s",__LINE__,__FUNCTION__);
}




struct normal_ops pad_normal_ops = {
    .set_normal_volume=set_normal_volume,
    .set_normal_path=set_normal_path,
    .set_normal_record_enable =set_normal_record_enable, 
    .set_normal_record=set_normal_record,
};

struct fm_ops pad_fm_ops = {
    .set_fm_volume=set_fm_volume,
    .set_fm_path=set_fm_path,
    .set_fm_record_enable =set_fm_record_enable, 
    .set_fm_record=set_fm_record,
};


