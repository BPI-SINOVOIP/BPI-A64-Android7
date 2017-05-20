
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
    volatile int voice_thread_run_flag;          //flag, ���������־
    volatile int voice_thread_exit_flag;          //flag, �����߳��˳���־
    volatile int record_flag;          //flag, ���������־
};

int init_stream(struct dev_stream *dev_stream);
void close_stream(struct dev_stream *dev_stream);

void ReduceVolume(char *buf, int size, int repeat);

extern struct pcm_config bt_pcm_out_config;
extern struct pcm_config bt_pcm_in_config ;
extern struct pcm_config codec_out_config ;
extern struct pcm_config codec_in_config ;
#if 0
#define MIXER_AUDIO_ADC_VOL_CTRL_L						"ADC left channel volume"
#define MIXER_AUDIO_ADC_VOL_CTRL_R						"ADC right channel volume"
#define MIXER_AUDIO_ADC_APC_CTRL_ADCLG					"ADC left channel input gain control"
#define MIXER_AUDIO_ADC_APC_CTRL_ADCRG					"ADC right channel input gain control"

#define MIXER_AUDIO_DAC_VOL_CTRL_L						"DAC left channel volume"
#define MIXER_AUDIO_DAC_VOL_CTRL_R						"DAC right channel volume"
#define MIXER_AUDIO_DAC_MXR_GAIN_L						"DAC left channel mixer gain control"
#define MIXER_AUDIO_DAC_MXR_GAIN_R						"DAC right channel mixer gain control"

#define MIXER_AUDIO_ADC_SRCBST_CTRL_MIC1G				"MIC1 boost amplifier gain control"
#define MIXER_AUDIO_ADC_SRCBST_CTRL_MIC2G				"MIC2 boost amplifier gain control"
#define MIXER_AUDIO_ADC_SRCBST_CTRL_LINEING				"LINEINL-LINEINR pre-amplifier gain control"
#define MIXER_AUDIO_ADC_SRCBST_CTRL_AUXIG				"AUXI pre-amplifier gain control"

#define MIXER_AUDIO_OMIXER_BST1_CTRL_AXG				"AXin to L_R output mixer gain ctr"
#define MIXER_AUDIO_OMIXER_BST1_CTRL_MIC1G				"MIC1 BST stage to L_R outp mixer gain ctr"
#define MIXER_AUDIO_OMIXER_BST1_CTRL_MIC2G				"MIC2 BST stage to L_R outp mixer gain ctr"
#define MIXER_AUDIO_OMIXER_BST1_CTRL_LINEING			"LINEINL/R to L_R output mixer gain ctr"

#define MIXER_AUDIO_HEADPHONE_VOL_CTRL					"headphone volume control"
#define MIXER_AUDIO_EARPIECE_VOL_CTRL					"earpiece volume control"
#define MIXER_AUDIO_SPEAKER_VOL_CTRL					"speaker volume control"
#define MIXER_AUDIO_LINE_OUT_GAIN_CTRL					"line out gain control"

#define MIXER_AUDIO_PHONE_OUT             				"Audio phone out"
#define MIXER_AUDIO_PHONE_IN             				"Audio phone in"

#define MIXER_AUDIO_PHONE_IN_LEFT						"Audio phone in left"
#define MIXER_AUDIO_EARPIECE_OUT             			"Audio earpiece out"
#define MIXER_AUDIO_HEADPHONE_OUT             			"Audio headphone out"
#define MIXER_AUDIO_SPEAKER_OUT             			"Audio speaker out"
#define MIXER_AUDIO_SPEAKER_OUT_LEFT					"Audio speaker out left"
//#define MIXER_AUDIO_HEADPHONE_OUT_LEFT					"Audio headphone out left"

//#define MIXER_AUDIO_ADC_PHONE_IN						"Audio adc phonein"
//#define MIXER_AUDIO_DAC_PHONE_OUT						"Audio dac phoneout"
#define MIXER_AUDIO_PHONE_MAIN_MIC 						"Audio analog main mic"
#define MIXER_AUDIO_PHONE_HEADSET_MIC 					"Audio analog headsetmic"
#define MIXER_AUDIO_PHONE_VOICE_RECORDER 				"Audio phone voicerecord"
#define MIXER_AUDIO_PHONE_ENDCALL 						"Audio phone endcall"

#define MIXER_AUDIO_LINEIN_RECORD             			"Audio linein record"
#define MIXER_AUDIO_LINEIN_IN             				"Audio linein in"

#define MIXER_AUDIO_NOISE_ADCIN_REDUCED					"Audio noise adcin reduced"
//#define MIXER_AUDIO_NOISE_DACPHONEOUT_REDUCED			"Audio noise dacphoneout reduced"
#define MIXER_AUDIO_NORMAL_SPEAKER_HEADSET    			"Speaker Function"

/*analog BT*/
#define MIXER_AUDIO_ANALOG_BT_MIC						"Audio analog bt mic"
#define MIXER_AUDIO_ANALOG_BT_PHONEIN					"Audio analog bt phonein"

#define MIXER_AUDIO_BT_OUT								"Audio bt out"
#define MIXER_AUDIO_BT_CLK_FMT							"Audio bt clk format status"

/*digital BB route*/
#define MIXER_AUDIO_DIGITAL_MAIN_MIC					"Audio digital main mic"
#define MIXER_AUDIO_DIGITAL_HEADSET_MIC					"Audio digital headset mic"
#define MIXER_AUDIO_DIGITAL_PHONE_OUT					"Audio digital phone out"
#define MIXER_AUDIO_DIGITAL_DAC_OUT						"Audio digital dac out"
#define MIXER_AUDIO_DIGITAL_PHONEIN						"Audio digital phonein"
#define MIXER_AUDIO_DIGITAL_BB_CLK_FMT					"Audio digital clk format status"
//#define MIXER_AUDIO_DIGITAL_NOISE_REDUCED				"Audio digital noise reduced"
#define MIXER_AUDIO_DIGITAL_BT_MIC						"Audio digital bt mic"
#define MIXER_AUDIO_DIGITAL_BT_PHONEIN					"Audio digital bt phonein"

/*digital BT*/
#define MIXER_AUDIO_DIGITAL_BB_BT_CLK_FMT				"Audio digital bb bt clk format"
/*use for select i2s/pcm*/
#define MIXER_AUDIO_I2S_PCM_SEL							"I2s Or Pcm Audio Mode Select format"

#define MIXER_AUDIO_DIGITAL_BB_CAP_MIC 					"Audio digital bb capture mic"
#define MIXER_AUDIO_DIGITAL_BB_CAP_BT 					"Audio digital bb capture bt"
#define MIXER_AUDIO_ANALOG_BB_CAP_MAINMIC					"Audio analog bb capture mainmic"
#define MIXER_AUDIO_ANALOG_BB_CAP_HEADSETMIC					"Audio analog bb capture headsetmic"
#define MIXER_AUDIO_ANALOG_BB_CAP_BT					"Audio analog bb capture bt"
#define MIXER_AUDIO_HEADSETMIC_RECORD_FLAG                                     "Audio headsetmic voicerecord"
#endif

#define MIXER_AUDIO_speaker_volume  "speaker volume"
#define MIXER_AUDIO_headphone_volume  "headphone volume"

#define MIXER_AUDIO_AIF1IN0R_Mux  "AIF1IN0R Mux"
#define MIXER_AUDIO_AIF1IN0L_Mux  "AIF1IN0L Mux"

#define MIXER_AUDIO_DACR_Mixer_AIF1DA0R_Switch  "DACR Mixer AIF1DA0R Switch"
#define MIXER_AUDIO_DACL_Mixer_AIF1DA0L_Switch  "DACL Mixer AIF1DA0L Switch"

#define MIXER_AUDIO_Right_Output_Mixer_DACR_Switch  "Right Output Mixer DACR Switch"
#define MIXER_AUDIO_Left_Output_Mixer_DACL_Switch  "Left Output Mixer DACL Switch"

#define MIXER_AUDIO_SPK_L_Mux  "SPK_L Mux"
#define MIXER_AUDIO_SPK_R_Mux  "SPK_R Mux"

#define MIXER_AUDIO_External_Speaker_Switch  "External Speaker Switch"
#define MIXER_AUDIO_Headphone_Switch  "Headphone Switch"


/*capture*/
#define MIXER_AUDIO_AIF1OUT0L_Mux  "AIF1OUT0L Mux"
#define MIXER_AUDIO_AIF1OUT0R_Mux  "AIF1OUT0R Mux"

#define MIXER_AUDIO_AIF1_AD0L_Mixer_ADCL_Switch  "AIF1 AD0L Mixer ADCL Switch"
#define MIXER_AUDIO_AIF1_AD0R_Mixer_ADCR_Switch  "AIF1 AD0R Mixer ADCR Switch"

#define MIXER_AUDIO_LEFT_ADC_input_Mixer_MIC1_boost_Switch  "LEFT ADC input Mixer MIC1 boost Switch"
#define MIXER_AUDIO_RIGHT_ADC_input_Mixer_MIC1_boost_Switch  "RIGHT ADC input Mixer MIC1 boost Switch"

/*mic2*/
#define MIXER_AUDIO_RIGHT_ADC_input_Mixer_MIC2_boost_Switch  "RIGHT ADC input Mixer MIC2 boost Switch"
#define MIXER_AUDIO_LEFT_ADC_input_Mixer_MIC2_boost_Switch  "LEFT ADC input Mixer MIC2 boost Switch"

/*mic2 source*/
#define MIXER_AUDIO_MIC2_SRC  "MIC2 SRC"

struct mixer_ctls
{
	struct mixer_ctl *speaker_volume;
	struct mixer_ctl *headphone_volume;
	struct mixer_ctl *AIF1IN0R_Mux;
	struct mixer_ctl *AIF1IN0L_Mux;

	struct mixer_ctl *DACR_Mixer_AIF1DA0R_Switch;
	struct mixer_ctl *DACL_Mixer_AIF1DA0L_Switch;

	struct mixer_ctl *Right_Output_Mixer_DACR_Switch;
	struct mixer_ctl *Left_Output_Mixer_DACL_Switch;

	struct mixer_ctl *SPK_L_Mux;
	struct mixer_ctl *SPK_R_Mux;

	struct mixer_ctl *External_Speaker_Switch;
	struct mixer_ctl *Headphone_Switch;


	/*capture*/
	struct mixer_ctl *AIF1OUT0L_Mux;
	struct mixer_ctl *AIF1OUT0R_Mux;

	struct mixer_ctl *AIF1_AD0L_Mixer_ADCL_Switch;
	struct mixer_ctl *AIF1_AD0R_Mixer_ADCR_Switch;

	struct mixer_ctl *LEFT_ADC_input_Mixer_MIC1_boost_Switch;
	struct mixer_ctl *RIGHT_ADC_input_Mixer_MIC1_boost_Switch;

	struct mixer_ctl *RIGHT_ADC_input_Mixer_MIC2_boost_Switch;
	struct mixer_ctl *LEFT_ADC_input_Mixer_MIC2_boost_Switch;

	struct mixer_ctl *MIC2_SRC;

	#if 0
	struct mixer_ctl *adc_left_chan_vol;
	struct mixer_ctl *adc_right_chan_vol;
	struct mixer_ctl *adc_left_chan_input_gain_ctrl;
	struct mixer_ctl *adc_right_chan_input_gain_ctrl;

	struct mixer_ctl *dac_left_chan_vol;
	struct mixer_ctl *dac_right_chan_vol;
	struct mixer_ctl *dac_left_chan_mixer_gain_ctrl;
	struct mixer_ctl *dac_right_chan_mixer_gain_ctrl;
	
	struct mixer_ctl *mic1_boost_amp_gain_ctrl;
	struct mixer_ctl *mic2_boost_amp_gain_ctrl;
	struct mixer_ctl *linein_l_r_boost_amp_gain_ctrl;
	struct mixer_ctl *auxi_boost_amp_gain_ctrl;
	
	struct mixer_ctl *axin_to_l_r_output_mixer_gain_ctrl;
	struct mixer_ctl *mic1_to_l_r_output_mixer_gain_ctrl;
	struct mixer_ctl *mic2_to_l_r_output_mixer_gain_ctrl;
	struct mixer_ctl *linein_to_l_r_output_mixer_gain_ctrl;
	
	struct mixer_ctl *headphone_vol_ctrl;
	struct mixer_ctl *earpiece_vol_ctrl;
	struct mixer_ctl *speaker_vol_ctrl;
	struct mixer_ctl *lineout_gain_ctrl;
	
	struct mixer_ctl *audio_phone_out;
    struct mixer_ctl *audio_phone_in;

	struct mixer_ctl *audio_phone_in_left;
	struct mixer_ctl *audio_earpiece_out;
    struct mixer_ctl *audio_headphone_out;
    struct mixer_ctl *audio_speaker_out;
	struct mixer_ctl *audio_speaker_out_left;
	struct mixer_ctl *audio_headphone_out_left;

//	struct mixer_ctl *audio_adc_phone_in;
//  struct mixer_ctl *audio_dac_phone_out;

	struct mixer_ctl *audio_phone_main_mic;
	struct mixer_ctl *audio_phone_headset_mic;
    struct mixer_ctl *audio_phone_voice_record;
    struct mixer_ctl *audio_phone_end_call;

    struct mixer_ctl *audio_linein_record;
    struct mixer_ctl *audio_linein_in;
	struct mixer_ctl *audio_noise_adcin_reduced;
//	struct mixer_ctl *audio_noise_dacphoneout_reduced;
	struct mixer_ctl *audio_spk_headset_switch;

	struct mixer_ctl *audio_analog_bt_mic;
	struct mixer_ctl *audio_analog_bt_phonein;

	struct mixer_ctl *audio_bt_out;
	struct mixer_ctl *audio_bt_clk_fmt;

	struct mixer_ctl *audio_digital_main_mic;
	struct mixer_ctl *audio_digital_headset_mic;
	struct mixer_ctl *audio_digital_phone_out;
	struct mixer_ctl *audio_digital_dac_out;
	struct mixer_ctl *audio_digital_phonein;
	struct mixer_ctl *audio_digital_clk_fmt_status;
	struct mixer_ctl *audio_digital_noise_reduced;
	struct mixer_ctl *audio_digital_bt_mic;
	struct mixer_ctl *audio_digital_bt_phonein;
	struct mixer_ctl *audio_bb_bt_clk_fmt;
	struct mixer_ctl *audio_i2s_pcm_sel;

	struct mixer_ctl *audio_digital_bb_capture_mic;
	struct mixer_ctl *audio_digital_bb_capture_bt;
	struct mixer_ctl *Audio_analog_bb_capture_mainmic;
	struct mixer_ctl *audio_analog_bb_capture_headsetmic;
	struct mixer_ctl *audio_analog_bb_capture_bt;
	struct mixer_ctl *headsetmic_record_flag;
	#endif
};

int get_mixer(struct mixer_ctls *mixer_ctls);

#endif

