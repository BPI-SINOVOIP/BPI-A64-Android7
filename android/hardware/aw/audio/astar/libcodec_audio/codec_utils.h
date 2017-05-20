
#ifndef __CODEC_UTILS_H__
#define __CODEC_UTILS_H__

#include <tinyalsa/asoundlib.h>

#include <pthread.h>
#include <semaphore.h>

typedef enum voice_type {
    BT,
    FM,
    BT_FM,
    CODEC,
    BP,
}voice_type_t;

enum device_type {
    CARD_UNKNOWN=-1,
    CARD_CODEC=0,
    CARD_PCM=1,
    CARD_I2S=2,
};

typedef enum voice_direction {
    DOWNSTREAM,
    UPSTREAM,
}voice_direction_t;

extern char *audio_dev_name[3];
 

typedef enum stream_direction_type {
    SENDER, 
    RECEIVER,
}stream_direction_type_t;

struct dev_stream{
    voice_type_t type;
    stream_direction_type_t direction;
    struct pcm_config config;

    struct pcm * dev;
    char *buf;
    unsigned int buf_size;
};

typedef void *(* voice_thread)(void *param);

struct stream_transfer{
    struct dev_stream *stream_sender;
    struct dev_stream *stream_receiver;

    voice_direction_t  voice_direction;
    sem_t sem;
    pthread_t pid;
    voice_thread func;

    volatile int manage_thread_run_flag;    //flag, �����߳����б�־
    volatile int voice_thread_run_flag;          //flag, ��������־
    volatile int voice_thread_exit_flag;          //flag, �����߳��˳���־
    volatile int record_flag;          //flag, ��������־
};

int init_stream(struct dev_stream *dev_stream);
void close_stream(struct dev_stream *dev_stream);

void ReduceVolume(char *buf, int size, int repeat);

extern struct pcm_config bt_pcm_out_config;
extern struct pcm_config bt_pcm_in_config ;
extern struct pcm_config codec_out_config ;
extern struct pcm_config codec_in_config ;



/* Mixer control names */
#define MIXER_HEADPHONE_VOLUME_CONTROL 					"headphone volume control"
#define MIXER_EARPIECE_VOLUME_CONTROL 					"earpiece volume control"
#define MIXER_SPEAKER_VOLUME_CONTRO 					"speaker volume control"

#define MIXER_MIC1_G_BOOST_STAGE_OUTPUT_MIXER_CONTROL 			"MIC1_G boost stage output mixer control"
#define MIXER_MIC2_G_BOOST_STAGE_OUTPUT_MIXER_CONTROL 			"MIC2_G boost stage output mixer control"
#define MIXER_LINEIN_G_BOOST_STAGE_OUTPUT_MIXER_CONTROL 		"LINEIN_G boost stage output mixer control"
#define MIXER_PHONE_G_BOOST_STAGE_OUTPUT_MIXER_CONTROL 			"PHONE_G boost stage output mixer control"
#define MIXER_PHONE_PG_BOOST_STAGE_OUTPUT_MIXER_CONTROL 		"PHONE_PG boost stage output mixer control"
#define MIXER_PHONE_NG_BOOST_STAGE_OUTPUT_MIXER_CONTROL 		"PHONE_NG boost stage output mixer control"

#define MIXER_AUDIO_MIC1_GAIN_CONTROL 					"MIC1 boost AMP gain control"
#define MIXER_AUDIO_MIC2_GAIN_CONTROL 					"MIC2 boost AMP gain control"
#define MIXER_LINEOUT_VOLUME_CONTROL					"Lineout volume control"
#define MIXER_PHONEP_PHONEN_PRE_AMP_GAIN_CONTROL 			"PHONEP-PHONEN pre-amp gain control"
#define MIXER_PHONEOUT_GAIN_CONTROL 					"Phoneout gain control"

#define MIXER_ADC_INPUT_GAIN_CTRL 					"ADC input gain ctrl"

#define MIXER_AUDIO_PHONE_OUT 						"Audio phone out"
#define MIXER_AUDIO_PHONE_IN						"Audio phone in"
#define MIXER_AUDIO_EARPIECE_OUT 					"Audio earpiece out"
#define MIXER_AUDIO_HEADPHONE_OUT 					"Audio headphone out"
#define MIXER_AUDIO_SPEAKER_OUT 					"Audio speaker out"

#define MIXER_AUDIO_ANALOG_MAIN_MIC 					"Audio analog main mic"
#define MIXER_AUDIO_ANALOG_HEADSETMIC					"Audio analog headsetmic"
#define MIXER_AUDIO_PHONE_VOICE_RECORDER 				"Audio phone voicerecord"
#define MIXER_AUDIO_PHONE_ENDCALL 					"Audio phone endcall"
#define MIXER_AUDIO_LINEIN_RECORD 					"Audio linein record"
#define MIXER_AUDIO_LINEIN_IN						"Audio linein in"

#define MIXER_AUDIO_NORMAL_SPEAKER_HEADSET 				"Speaker Function"

#define MIXER_AUDIO_DIGITAL_MAIN_MIC 					"Audio digital main mic"
#define MIXER_AUDIO_DIGITAL_HEADSER_MIC					"Audio digital headset mic"
#define MIXER_AUDIO_DIGITAL_PHONE_OUT 					"Audio digital phone out"

#define MIXER_AUDIO_DIGITAL_PHONEIN					"Audio digital phonein"
#define MIXER_AUDIO_DIGITAL_CLK_FORMAT_STATUS 				"Audio digital clk format status"

#define MIXER_AUDIO_BT_CLK_FORMAT_STATUS 				"Audio bt clk format status"
#define MIXER_AUDIO_BT_OUT 						"Audio bt out"

#define MIXER_AUDIO_ANALOG_BT_MIC 					"Audio analog bt mic"
#define MIXER_AUDIO_ANALOG_BT_PHONEIN 					"Audio analog bt phonein"

#define MIXER_AUDIO_DIGITAL_BT_MIC 					"Audio digital bt mic"
#define MIXER_AUDIO_DIGITAL_BT_PHONEIN 					"Audio digital bt phonein"
#define MIXER_AUDIO_BT_BUTTON_VOICE					"Audio bt button voice"
#define MIXER_AUDIO_DIGITAL_BB_BT_CLK_FORMAT 				"Audio digital bb bt clk format"
#define MIXER_AUDIO_SYSTEM_BT_CAPTURE_FLAG 				"Audio system bt capture flag"

#define MIXER_AIF3_LOOPBACK						"aif3 loopback"
#define MIXER_AIF2_LOOPBACK 						"aif2 loopback"

#define MIXER_DIGITAL_BB_BT 						"digital_bb_bt"

#define MIXER_SYSTEM_PLAY_CAPTURE_SET_1 				"system play_capture set 1"
#define MIXER_SYSTEM_PLAY_CAPTURE_SET_2 				"system play_capture set 2"
#define MIXER_ANALOGBB_CAPTURE_MIC_SWITCH 				"Audio analog bb capture mic"

#define MIXER_AIF1_ADOL_MXR_SRC_AIF1DAOLDATA 				"AIF1_AD0L_MXR_SRC AIF1DA0Ldata"
#define MIXER_AIF1_ADOL_MXR_SRC_AIF2DACLDATA 				"AIF1_AD0L_MXR_SRC AIF2DACLdata"
#define MIXER_AIF1_ADOL_MXR_SRC_ADCLDATA 				"AIF1_AD0L_MXR_SRC ADCLdata"
#define MIXER_AIF1_ADOL_MXR_SRC_AIF2DACRDATA 				"AIF1_AD0L_MXR_SRC AIF2DACRdata"

#define MIXER_AIF1_ADOR_MXR_SRC_AIF1DAORDATA 				"AIF1_AD0R_MXR_SRC AIF1DA0Rdata"
#define MIXER_AIF1_ADOR_MXR_SRC_AIF2DACRDATA 				"AIF1_AD0R_MXR_SRC AIF2DACRdata"
#define MIXER_AIF1_ADOR_MXR_SRC_ADCRDATA 				"AIF1_AD0R_MXR_SRC ADCRdata"
#define MIXER_AIF1_ADOR_MXR_SRC_AIF2DACLDATA 				"AIF1_AD0R_MXR_SRC AIF2DACLdata"

struct mixer_ctls
{
    struct mixer_ctl *headphone_volume_control;
    struct mixer_ctl *earpiece_volume_control;
    struct mixer_ctl *speaker_volume_control;
    
    struct mixer_ctl *mic1_g_boost_stage_output_mixer_control;
    struct mixer_ctl *mic2_g_boost_stage_output_mixer_control;
    struct mixer_ctl *linein_g_boost_stage_output_mixer_control;
    struct mixer_ctl *phone_g_boost_stage_output_mixer_control;
    struct mixer_ctl *phone_pg_boost_stage_output_mixer_control;
    struct mixer_ctl *phone_ng_boost_stage_output_mixer_control;
    
    struct mixer_ctl *audio_mic1_gain;;
    struct mixer_ctl *audio_mic2_gain;;
    struct mixer_ctl *lineout_volume_control;
    struct mixer_ctl *phoneout_gain_control;
    struct mixer_ctl *phonep_phonen_pre_amp_gain_control;
    
    struct mixer_ctl *adc_input_gain_ctrl;
    
    struct mixer_ctl *audio_phone_out;
    struct mixer_ctl *audio_phone_in ;
    struct mixer_ctl *audio_earpiece_out;
    struct mixer_ctl *audio_headphone_out;
    struct mixer_ctl *audio_speaker_out;
    
    struct mixer_ctl *audio_analog_main_mic;
    struct mixer_ctl *audio_analog_headsetmic;
    struct mixer_ctl *audio_phone_voice_record;
    struct mixer_ctl *audio_phone_end_call;
    struct mixer_ctl *audio_linein_record;
    struct mixer_ctl *audio_linein_in;
    
    struct mixer_ctl *audio_spk_headset_switch;
    
    struct mixer_ctl *audio_digital_main_mic;
    struct mixer_ctl *audio_digital_headset_mic;
    struct mixer_ctl *audio_digital_phone_out;
    
    struct mixer_ctl *audio_digital_phonein;
    struct mixer_ctl *audio_digital_clk_format_status;
    
    struct mixer_ctl *audio_bt_clk_format_status;
    struct mixer_ctl *audio_bt_out;
    
    struct mixer_ctl *audio_analog_bt_mic;
    struct mixer_ctl *audio_analog_bt_phonein;
    
    struct mixer_ctl *audio_digital_bt_mic;
    struct mixer_ctl *audio_digital_bt_phonein;
    struct mixer_ctl *audio_bt_button_voice;
    struct mixer_ctl *audio_digital_bb_bt_clk_format;
    struct mixer_ctl *audio_system_bt_capture_flag;
    
    struct mixer_ctl *aif3_loopback;
    struct mixer_ctl *aif2_loopback;
    
    struct mixer_ctl *digital_bb_bt;  
    
    struct mixer_ctl *system_play_capture_set_1;
    struct mixer_ctl *system_play_capture_set_2;
    struct mixer_ctl *audio_analog_bb_capture_mic_switch;
};

int get_mixer(struct mixer_ctls *mixer_ctls);
extern void c_plus_plus_grabPartialWakeLock();
extern void c_plus_plus_releaseWakeLock();
void grabPartialWakeLock();
void releaseWakeLock();
#endif



