
#define LOG_TAG "codec_audio"
#define LOG_NDEBUG 0

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <utils/Log.h>
#include <cutils/properties.h>

#include <tinyalsa/asoundlib.h>

#include "codec_utils.h"

struct pcm_config bp_i2s_out_config =
{
	.channels = 1,
	.rate = 8000,
	.period_size = 1024,
	.period_count = 2,
	.format = PCM_FORMAT_S16_LE,
	.start_threshold = 0,
	.stop_threshold = 0,
	.silence_threshold = 0,
};

struct pcm_config bp_i2s_in_config =
{
	.channels = 1,
	.rate = 8000,
	.period_size = 1024,
	.period_count = 2,
	.format = PCM_FORMAT_S16_LE,
	.start_threshold = 0,
	.stop_threshold = 0,
	.silence_threshold = 0,
};

struct pcm_config bt_pcm_out_config =
{
	.channels = 1,
	.rate = 8000,
	.period_size = 1024,
	.period_count =2,
	.format = PCM_FORMAT_S16_LE,
	.start_threshold = 0,
	.stop_threshold = 0,
	.silence_threshold = 0,
};

struct pcm_config bt_pcm_in_config =
{
	.channels = 1,
	.rate = 8000,                
	.period_size = 1024,          
	.period_count = 2,           
	.format = PCM_FORMAT_S16_LE, 
	.start_threshold = 0,        
	.stop_threshold = 0,         
	.silence_threshold = 0,  
};

struct pcm_config codec_out_config =
{
	.channels = 1,
	.rate = 8000,                
	.period_size = 1024,          
	.period_count = 2,           
	.format = PCM_FORMAT_S16_LE, 
	.start_threshold = 0,        
	.stop_threshold = 0,         
	.silence_threshold = 0,  

};
struct pcm_config codec_in_config =
{
	.channels = 1,
	.rate = 8000,                
	.period_size = 1024,          
	.period_count = 2,           
	.format = PCM_FORMAT_S16_LE, 
	.start_threshold = 0,        
	.stop_threshold = 0,         
	.silence_threshold = 0,  
};


void grabPartialWakeLock() 
{
	c_plus_plus_grabPartialWakeLock();
}

void releaseWakeLock() 
{
	c_plus_plus_releaseWakeLock();
}

char *audio_dev_name[3]={"audiocodec","sndpcm", "sndi2s1"} ;

int init_stream(struct dev_stream *dev_stream) 
{
	enum device_type dev_type = CARD_UNKNOWN;
	int dev_direction =0;
	int dev_node = -1;

	switch (dev_stream->type){
		case BT: 
			dev_type = CARD_PCM;
			break;	
		case BT_FM: 
			dev_type = CARD_PCM;
			break;	
		case BP: 
			dev_type = CARD_I2S;
			break;	
		case FM: 
			dev_type = CARD_I2S;
			break;
		case CODEC: 
			dev_type = CARD_CODEC;
			break;	
		default:
			dev_type = CARD_UNKNOWN;				
	};

	if(dev_type == CARD_UNKNOWN){
		ALOGE("unknown stream");
		return -1;
	}

	dev_node = pcm_get_node_number(audio_dev_name[dev_type]);
	if (dev_node < 0) {
		ALOGE("err: get %s node number failed ", audio_dev_name[dev_type]);
		return -1;
	}


	dev_direction = dev_stream->direction == SENDER ? PCM_IN : PCM_OUT;

	dev_stream->dev = pcm_open(dev_node, 0, dev_direction, &(dev_stream->config));
	if (!pcm_is_ready(dev_stream->dev )) {
		ALOGE("err: Unable to open  device (%s)", pcm_get_error(dev_stream->dev));
		goto open_failed;
	}

	if (dev_direction == PCM_IN){ //only pcm_in alloc buffer
		dev_stream->buf_size = pcm_get_buffer_size(dev_stream->dev);
		dev_stream->buf = (void *)malloc(dev_stream->buf_size);
		if (dev_stream->buf == NULL) {
			ALOGE("Unable to allocate %d bytes", dev_stream->buf_size);
			goto malloc_failed;
		}
		//ALOGD("sender stream type:%d, pcm_read buf=%d bytes",dev_stream->type, dev_stream->buf_size);
	}
	memset(dev_stream->buf, 0, dev_stream->buf_size);

	ALOGD("dev_stream dev node =%d, type=%d, direction:%s, buf size:%d", 
			dev_node, dev_stream->type, dev_stream->direction == SENDER ? "PCM_IN" : "PCM_OUT", pcm_get_buffer_size(dev_stream->dev));

    	return 0;
malloc_failed:
	if (dev_stream->dev){
		pcm_close(dev_stream->dev);	
	}

open_failed:
    	return -1;
}

void close_stream(struct dev_stream *dev_stream) 
{

	if (dev_stream->buf){
		free(dev_stream->buf);	
	}

	if (dev_stream->dev){
		pcm_close(dev_stream->dev);	
	}
}

void ReduceVolume(char *buf, int size, int repeat)
{
        int i,j;
        int zhen_shu;
        signed long minData = -0x8000;
        signed long maxData = 0x7FFF;
        signed short data ;
        unsigned char low, hight;

        if(!size){
                return;
        }   

	zhen_shu = size - size%2;

        for(i=0; i<zhen_shu; i+=2){
		low = buf[i];
                hight = buf[i+1];
                data = low | (hight << 8);  
                for(j=0; j< repeat; j++){
                        data = data / 1.25;    
                        if(data < minData){
                                data = minData; 
                        } else if (data > 0x7fff){
                                data = maxData;
                        }   
                }   
                buf[i] = (data) & 0x00ff;
                buf[i+1] = ((data)>>8) & 0xff;
        } 
}


int get_mixer(struct mixer_ctls *mixer_ctls)
{
    struct mixer *mixer;
	ALOGE("begain get mixer ......");
    mixer = mixer_open(0);
    if (!mixer) {
	    ALOGE("Unable to open the mixer, aborting.");
	    return -1;
    }    
    
    mixer_ctls->headphone_volume_control = mixer_get_ctl_by_name(mixer,
		    MIXER_HEADPHONE_VOLUME_CONTROL);
    if (!mixer_ctls->headphone_volume_control) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_HEADPHONE_VOLUME_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->earpiece_volume_control = mixer_get_ctl_by_name(mixer,
		    MIXER_EARPIECE_VOLUME_CONTROL);
    if (!mixer_ctls->earpiece_volume_control) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_EARPIECE_VOLUME_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->speaker_volume_control = mixer_get_ctl_by_name(mixer,
		    MIXER_SPEAKER_VOLUME_CONTRO);
    if (!mixer_ctls->speaker_volume_control) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_SPEAKER_VOLUME_CONTRO);
	    goto error_out;
    }
    
    mixer_ctls->mic1_g_boost_stage_output_mixer_control = mixer_get_ctl_by_name(mixer,
		    MIXER_MIC1_G_BOOST_STAGE_OUTPUT_MIXER_CONTROL);
    if (!mixer_ctls->mic1_g_boost_stage_output_mixer_control) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_MIC1_G_BOOST_STAGE_OUTPUT_MIXER_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->mic2_g_boost_stage_output_mixer_control = mixer_get_ctl_by_name(mixer,
		    MIXER_MIC2_G_BOOST_STAGE_OUTPUT_MIXER_CONTROL);
    if (!mixer_ctls->mic2_g_boost_stage_output_mixer_control) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_MIC2_G_BOOST_STAGE_OUTPUT_MIXER_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->linein_g_boost_stage_output_mixer_control = mixer_get_ctl_by_name(mixer,
		    MIXER_LINEIN_G_BOOST_STAGE_OUTPUT_MIXER_CONTROL);
    if (!mixer_ctls->linein_g_boost_stage_output_mixer_control) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_LINEIN_G_BOOST_STAGE_OUTPUT_MIXER_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->phone_g_boost_stage_output_mixer_control = mixer_get_ctl_by_name(mixer,
		    MIXER_PHONE_G_BOOST_STAGE_OUTPUT_MIXER_CONTROL);
    if (!mixer_ctls->phone_g_boost_stage_output_mixer_control) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_PHONE_G_BOOST_STAGE_OUTPUT_MIXER_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->phone_pg_boost_stage_output_mixer_control = mixer_get_ctl_by_name(mixer,
		    MIXER_PHONE_PG_BOOST_STAGE_OUTPUT_MIXER_CONTROL);
    if (!mixer_ctls->phone_pg_boost_stage_output_mixer_control) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_PHONE_PG_BOOST_STAGE_OUTPUT_MIXER_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->phone_ng_boost_stage_output_mixer_control = mixer_get_ctl_by_name(mixer,
		    MIXER_PHONE_NG_BOOST_STAGE_OUTPUT_MIXER_CONTROL);
    if (!mixer_ctls->phone_ng_boost_stage_output_mixer_control) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_PHONE_NG_BOOST_STAGE_OUTPUT_MIXER_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->audio_mic1_gain = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_MIC1_GAIN_CONTROL);
    if (!mixer_ctls->audio_mic1_gain) {
	    ALOGE("Unable to find '%s' mixer control", MIXER_AUDIO_MIC1_GAIN_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->audio_mic2_gain = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_MIC2_GAIN_CONTROL);
    if (!mixer_ctls->audio_mic2_gain) {
	    ALOGE("Unable to find '%s' mixer control", MIXER_AUDIO_MIC2_GAIN_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->lineout_volume_control = mixer_get_ctl_by_name(mixer,
		    MIXER_LINEOUT_VOLUME_CONTROL);
    if (!mixer_ctls->lineout_volume_control) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_LINEOUT_VOLUME_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->phoneout_gain_control = mixer_get_ctl_by_name(mixer,
		    MIXER_PHONEOUT_GAIN_CONTROL);
    if (!mixer_ctls->phoneout_gain_control) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_PHONEOUT_GAIN_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->phonep_phonen_pre_amp_gain_control = mixer_get_ctl_by_name(mixer,
		    MIXER_PHONEP_PHONEN_PRE_AMP_GAIN_CONTROL);
    if (!mixer_ctls->phonep_phonen_pre_amp_gain_control) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_PHONEP_PHONEN_PRE_AMP_GAIN_CONTROL);
	    goto error_out;
    }
    
    mixer_ctls->adc_input_gain_ctrl = mixer_get_ctl_by_name(mixer,
		    MIXER_ADC_INPUT_GAIN_CTRL);
    if (!mixer_ctls->adc_input_gain_ctrl) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_ADC_INPUT_GAIN_CTRL);
	    goto error_out;
    }
    
    mixer_ctls->audio_phone_out = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_PHONE_OUT);
    if (!mixer_ctls->audio_phone_out) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_PHONE_OUT);
	    goto error_out;
    }
    
    mixer_ctls->audio_phone_in = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_PHONE_IN);
    if (!mixer_ctls->audio_phone_in) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_PHONE_IN);
	    goto error_out;
    }
    
    mixer_ctls->audio_earpiece_out = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_EARPIECE_OUT);
    if (!mixer_ctls->audio_earpiece_out) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_EARPIECE_OUT);
	    goto error_out;
    }
    
    mixer_ctls->audio_headphone_out = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_HEADPHONE_OUT);
    if (!mixer_ctls->audio_headphone_out) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_HEADPHONE_OUT);
	    goto error_out;
    }
    
    mixer_ctls->audio_speaker_out = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_SPEAKER_OUT);
    if (!mixer_ctls->audio_speaker_out) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_SPEAKER_OUT);
	    goto error_out;
    }
    
    mixer_ctls->audio_analog_main_mic = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_ANALOG_MAIN_MIC);
    if (!mixer_ctls->audio_analog_main_mic) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_ANALOG_MAIN_MIC);
	    goto error_out;
    }
    
    mixer_ctls->audio_analog_headsetmic = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_ANALOG_HEADSETMIC);
    if (!mixer_ctls->audio_analog_headsetmic) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_ANALOG_HEADSETMIC);
	    goto error_out;
    }
    
    mixer_ctls->audio_phone_voice_record = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_PHONE_VOICE_RECORDER);
    if (!mixer_ctls->audio_phone_voice_record) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_PHONE_VOICE_RECORDER);
	    goto error_out;
    }    
    
    mixer_ctls->audio_phone_end_call = mixer_get_ctl_by_name(mixer,
    		MIXER_AUDIO_PHONE_ENDCALL);
    if (!mixer_ctls->audio_phone_end_call) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_PHONE_ENDCALL);
	    goto error_out;
    }
    
    mixer_ctls->audio_linein_record = mixer_get_ctl_by_name(mixer,
    		MIXER_AUDIO_LINEIN_RECORD);
    if (!mixer_ctls->audio_linein_record) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_LINEIN_RECORD);
	    goto error_out;
    }
    
    mixer_ctls->audio_linein_in = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_LINEIN_IN);
    if (!mixer_ctls->audio_linein_in) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_LINEIN_IN);
	    goto error_out;
    }
    
    mixer_ctls->audio_spk_headset_switch = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_NORMAL_SPEAKER_HEADSET);
    if (!mixer_ctls->audio_spk_headset_switch) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_NORMAL_SPEAKER_HEADSET);
	    goto error_out;
    }
    
    mixer_ctls->audio_digital_main_mic = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_DIGITAL_MAIN_MIC);
    if (!mixer_ctls->audio_digital_main_mic) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_DIGITAL_MAIN_MIC);
	    goto error_out;
    }
    
    mixer_ctls->audio_digital_headset_mic = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_DIGITAL_HEADSER_MIC);
    if (!mixer_ctls->audio_digital_headset_mic) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_DIGITAL_HEADSER_MIC);
	    goto error_out;
    }
    
    mixer_ctls->audio_digital_phone_out = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_DIGITAL_PHONE_OUT);
    if (!mixer_ctls->audio_digital_phone_out) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_DIGITAL_PHONE_OUT);
	    goto error_out;
    }
    
    mixer_ctls->audio_digital_phonein = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_DIGITAL_PHONEIN);
    if (!mixer_ctls->audio_digital_phonein) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_DIGITAL_PHONEIN);
	    goto error_out;
    }
    
    mixer_ctls->audio_digital_clk_format_status = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_DIGITAL_CLK_FORMAT_STATUS);
    if (!mixer_ctls->audio_digital_clk_format_status) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_DIGITAL_CLK_FORMAT_STATUS);
	    goto error_out;
    }
    
    mixer_ctls->audio_bt_clk_format_status = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_BT_CLK_FORMAT_STATUS);
    if (!mixer_ctls->audio_bt_clk_format_status) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_BT_CLK_FORMAT_STATUS);
	    goto error_out;
    }
    
    mixer_ctls->audio_bt_out = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_BT_OUT);
    if (!mixer_ctls->audio_bt_out) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_BT_OUT);
	    goto error_out;
    }
    
    mixer_ctls->audio_analog_bt_mic = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_ANALOG_BT_MIC);
    if (!mixer_ctls->audio_analog_bt_mic) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_ANALOG_BT_MIC);
	    goto error_out;
    }
    
    mixer_ctls->audio_analog_bt_phonein = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_ANALOG_BT_PHONEIN);
    if (!mixer_ctls->audio_analog_bt_phonein) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_ANALOG_BT_PHONEIN);
	    goto error_out;
    }
    
    mixer_ctls->audio_digital_bt_mic = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_DIGITAL_BT_MIC);
    if (!mixer_ctls->audio_digital_bt_mic) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_DIGITAL_BT_MIC);
	    goto error_out;
    }
    
    mixer_ctls->audio_digital_bt_phonein = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_DIGITAL_BT_PHONEIN);
    if (!mixer_ctls->audio_digital_bt_phonein) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_DIGITAL_BT_PHONEIN);
	    goto error_out;
    }
    
    mixer_ctls->audio_bt_button_voice = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_BT_BUTTON_VOICE);
    if (!mixer_ctls->audio_bt_button_voice) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_BT_BUTTON_VOICE);
	    goto error_out;
    }
    
    mixer_ctls->audio_digital_bb_bt_clk_format = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_DIGITAL_BB_BT_CLK_FORMAT);
    if (!mixer_ctls->audio_digital_bb_bt_clk_format) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_DIGITAL_BB_BT_CLK_FORMAT);
	    goto error_out;
    }
    
    mixer_ctls->audio_system_bt_capture_flag = mixer_get_ctl_by_name(mixer,
		    MIXER_AUDIO_SYSTEM_BT_CAPTURE_FLAG);
    if (!mixer_ctls->audio_system_bt_capture_flag) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AUDIO_SYSTEM_BT_CAPTURE_FLAG);
	    goto error_out;
    }
    
    mixer_ctls->aif3_loopback = mixer_get_ctl_by_name(mixer,
		    MIXER_AIF3_LOOPBACK);
    if (!mixer_ctls->aif3_loopback) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AIF3_LOOPBACK);
	    goto error_out;
    }
    
    mixer_ctls->aif2_loopback = mixer_get_ctl_by_name(mixer,
		    MIXER_AIF2_LOOPBACK);
    if (!mixer_ctls->aif2_loopback) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_AIF2_LOOPBACK);
	    goto error_out;
    }
    
    mixer_ctls->digital_bb_bt = mixer_get_ctl_by_name(mixer,
		    MIXER_DIGITAL_BB_BT);
    if (!mixer_ctls->digital_bb_bt) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_DIGITAL_BB_BT);
	    goto error_out;
    }
    
    mixer_ctls->system_play_capture_set_1 = mixer_get_ctl_by_name(mixer,
		    MIXER_SYSTEM_PLAY_CAPTURE_SET_1);
    if (!mixer_ctls->system_play_capture_set_1) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_SYSTEM_PLAY_CAPTURE_SET_1);
	    goto error_out;
    }
    
    mixer_ctls->system_play_capture_set_2 = mixer_get_ctl_by_name(mixer,
		    MIXER_SYSTEM_PLAY_CAPTURE_SET_2);
    if (!mixer_ctls->system_play_capture_set_2) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_SYSTEM_PLAY_CAPTURE_SET_2);
	    goto error_out;
    }

    mixer_ctls->audio_analog_bb_capture_mic_switch = mixer_get_ctl_by_name(mixer,
		    MIXER_ANALOGBB_CAPTURE_MIC_SWITCH);
    if (!mixer_ctls->audio_analog_bb_capture_mic_switch) {
	    ALOGE("Unable to find '%s' mixer control",MIXER_ANALOGBB_CAPTURE_MIC_SWITCH);
	    goto error_out;
    }
    return 0;

error_out:  
    mixer_close(mixer);
    return -1;
}



