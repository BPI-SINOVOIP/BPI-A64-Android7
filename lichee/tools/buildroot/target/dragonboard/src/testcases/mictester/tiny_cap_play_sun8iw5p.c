/*
 * \file        tiny_cap_play.c
 * \brief       
 *
 * \version     1.0.0
 * \date        2012年05月31日
 * \author      James Deng <csjamesdeng@allwinnertech.com>
 * \Descriptions:
 *      create the inital version
 *
 * \version     1.1.0
 * \date        2012年09月26日
 * \author      Martin <zhengjiewen@allwinnertech.com> 
 * \Descriptions:
 *      add some new features:
 *      1. add dual mic test support
 *      2. add mic data sent to the mic_pipe function to support the
 *         mic power bar display
 *      3. changed the test pass condition. mic tset will be pass only
 *         if the captured data over the threshold
 *      4. music playback support 
 * Copyright (c) 2012 Allwinner Technology. All Rights Reserved.
 *
 */

#include <asoundlib.h>

#include "include/tinyalsa/asoundlib.h"


#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <signal.h>
#include <pthread.h>
#include "dragonboard_inc.h"
#include "sun8iw5p_display.h"

#define ID_RIFF 0x46464952
#define ID_WAVE 0x45564157
#define ID_FMT  0x20746d66
#define ID_DATA 0x61746164

#define FORMAT_PCM 1

#define CLAMP(x, low, high) \
    (((x) > (high)) ? (high) : (((x) < (low)) ? (low) : (x)))

struct wav_header {
    uint32_t riff_id;
    uint32_t riff_sz;
    uint32_t riff_fmt;
    uint32_t fmt_id;
    uint32_t fmt_sz;
    uint16_t audio_format;
    uint16_t num_channels;
    uint32_t sample_rate;
    uint32_t byte_rate;
    uint16_t block_align;
    uint16_t bits_per_sample;
    uint32_t data_id;
    uint32_t data_sz;
};

int capturing = 1;

disp_output_type disp_output_type_t;

//static snd_pcm_t *playback_handle;

int mic1_gain,mic2_gain;
int mic1_used,mic2_used;
int volume;
unsigned int mic1_threshold;
unsigned int mic2_threshold;
struct mixer *mixer;

unsigned int cap_play_sample(unsigned int device,
                            unsigned int channels, unsigned int rate,
                            unsigned int bits,int argc,char ** argv );

static void tinymix_set_value(struct mixer *mixer, unsigned int id,
                              unsigned int value);
static void tinymix_set_value_byname(struct mixer *mixer, const char *name,
                              unsigned int value);
static int tinymix_get_value_byname(struct mixer *mixer, const char *name);


void sigint_handler(int sig)
{
    printf("mic:sigint_handler sigint!\n");
    capturing = 0;
}

static void *jack_deamon(void *args)
{
    int fd;
    char jack_buf[16];
    int jack_state;
    char *path = "/sys/class/switch/h2w/state";
    int read_count;

    
    while(1){

        fd = open(path, O_RDONLY|O_NONBLOCK);
   	     if (fd < 0) {
       	     db_error("cannot open %s(%s)\n", path, strerror(errno));
             pthread_exit((void *)-1);
   		 }
    	
        read_count=read(fd,jack_buf,sizeof(jack_buf));
    	if (read_count<0){
			db_error("jack state read error\n");
			pthread_exit((void *)-1);
    	}
    	jack_state=atoi(jack_buf);
		if(jack_state>0)
    	{
    		if(tinymix_get_value_byname(mixer,"Speaker Function") != 0)
    		{    			
    			tinymix_set_value_byname(mixer,"Speaker Function",0);
	        	//tinymix_set_value_byname(mixer, "headphone volume control", 20);//headphone
    		}
    	}
		else
    	{
    		if(tinymix_get_value_byname(mixer,"Speaker Function") != 1)
    		{    			
    			tinymix_set_value_byname(mixer,"Speaker Function",1);
	    		//tinymix_set_value_byname(mixer, "speaker volume control", 20);//speaker
    		}
    	}
    	close(fd);
    	usleep(500000);
   }
    
    
}

static void init_hdmi_audio(void)
{

    int ret;
    unsigned int args[4];
   // snd_pcm_t *playback_handle;
    int disp;
    
    disp = open("/dev/disp", O_RDWR);
      if (disp == -1) {
          db_error("mictester: open /dev/disp failed(%s)\n", strerror(errno));     
      }

    args[0] = 0;
    disp_output_type_t = (disp_output_type)ioctl(disp, DISP_CMD_GET_OUTPUT_TYPE,(void*)args);
    
    if(disp_output_type_t==DISP_OUTPUT_TYPE_LCD){

        return;
    }
    
      while (1) {
          args[0] = 0;
          ret = ioctl(disp, DISP_CMD_HDMI_GET_HPD_STATUS, args);
          if (ret == 0) {
                sleep(1);
                continue;
          }
          if(ret==1) break;
      }
      
    close(disp);

    db_msg("mictester:set the hdmi audio done...\n");

     return;
  
}



int setup_mixer(int mixer_num)
{

    int r_input_source,l_input_source;

    mixer = mixer_open(mixer_num);
    if (!mixer) {
        printf( "Failed to open mixer\n");
        return EXIT_FAILURE;
    }

    if (script_fetch("mic", "mic1_threshold", &mic1_threshold, 1)) {
         mic1_threshold=0;
       db_msg("mictester: can't fetch mic1_threshold, use default threshold: %d\n", mic1_threshold);
      
    }
    mic1_threshold=CLAMP(mic1_threshold,0,32767);

    if (script_fetch("mic", "mic2_threshold", &mic2_threshold, 1)) {
         mic2_threshold=0;
         db_msg("mictester: can't fetch mic2_threshold, use default threshold: %d\n", mic2_threshold);
      
    }
    mic2_threshold=CLAMP(mic2_threshold,0,32767);
    
    db_msg("mic1_threshold=%d,mic2_threshold=%d\n",mic1_threshold,mic2_threshold);
    if (script_fetch("mic", "mic1_gain", &mic1_gain, 1)) {
       mic1_gain=1;
       db_msg("mictester: can't fetch mic1_gain, use default gain: %d\n", mic1_gain);
      
    }
    if (script_fetch("mic", "mic2_gain", &mic2_gain, 1)) {
        mic2_gain=1;
       db_msg("mictester: can't fetch mic2_gain, use default gain: %d\n", mic2_gain);
      
    }
    if(mic1_gain<0||mic1_gain>3) mic1_gain=1;
    if(mic2_gain<0||mic2_gain>3) mic2_gain=1;
    db_msg("mictester:mic1_gain=%d,mic2_gain=%d\n",mic1_gain,mic2_gain);
    
   if (script_fetch("mic", "volume", &volume, 1) || 
            volume < 0) {
        volume = 40;
    }
    if(volume>63) volume=63;
    db_msg("mictester:volume=%d\n",volume);

    if (script_fetch("mic", "mic1_used", &mic1_used, 1)) {
        mic1_used=1;
        db_msg("mictester: can't fetch mic1_used, set to default: %d\n", mic1_used);
      
    }
    
    if (script_fetch("mic", "mic2_used", &mic2_used, 1)) {
        mic2_used=1;
        db_msg("mictester: can't fetch mic2_used, set to default: %d\n", mic2_used);
      
    }
	/*tinymix_set_value(mixer, 0, volume);
	
	
        tinymix_set_value(mixer, 1,1);//Playback LPAMUTE SWITCH,0-1
	tinymix_set_value(mixer, 2,1);//Playback RPAMUTE SWITCH,0-1
	tinymix_set_value(mixer, 3, 0);//dacL as the left headphone pa input source 
	tinymix_set_value(mixer, 4, 0);//dacR as the right headphone pa input source    
	
	tinymix_set_value(mixer, 9,1);//Inter DAC analog left channel en,0-1
	tinymix_set_value(mixer, 10,1);//Inter DAC analog right channel en,0-1
    tinymix_set_value(mixer, 22,1);//Master microphone bias enable,0-1
    tinymix_set_value(mixer, 24,1);//MIC1 boost AMP enable,0-1
   	tinymix_set_value(mixer, 25, mic1_gain);//MIC1 boost AMP gain control,0-7
	tinymix_set_value(mixer, 26,1);//MIC2 boost AMP enable,0-1
	tinymix_set_value(mixer, 27, mic2_gain);//MIC2 boost AMP gain control,0-7
	if(mic1_used&&!mic2_used){
		 r_input_source= 64;
		 l_input_source= 64;
	 }else
	if(!mic1_used&&mic2_used){
	     r_input_source= 32;
		 l_input_source= 32;
	}else
	{
	     r_input_source= 64;
		 l_input_source= 32;
	}
//there is some error with the below code, bad device driver
	tinymix_set_value(mixer, 45,r_input_source);//Right ADC mixer mute ctrl,mic1
	tinymix_set_value(mixer, 46,l_input_source);//left ADC mixer mute ctrl,mic2
		
	tinymix_set_value(mixer, 41,1);//ADC Right channel en,0-1
	tinymix_set_value(mixer, 42,1);//ADC Left channel en,0-1
	*/	
//	mixer_close(mixer);
return 0;


}
int main(int argc, char **argv)
{
    struct wav_header header;
    char left_channel_data[16];
    
    unsigned int device = 0;
    unsigned int channels = 2;
    unsigned int rate = 48000;
    unsigned int bits = 16;
    unsigned int frames;
    int err;
    pthread_t deamon_id;
    init_script(atoi(argv[2]));
    
    header.riff_id = ID_RIFF;
    header.riff_sz = 0;
    header.riff_fmt = ID_WAVE;
    header.fmt_id = ID_FMT;
    header.fmt_sz = 16;
    header.audio_format = FORMAT_PCM;
    header.num_channels = channels;
    header.sample_rate = rate;
    header.byte_rate = (header.bits_per_sample / 8) * channels * rate;
    header.block_align = channels * (header.bits_per_sample / 8);
    header.bits_per_sample = bits;
    header.data_id = ID_DATA; 

    if(EXIT_FAILURE==setup_mixer(0)){
        db_error("mictester:can't set up mixer\n");
    }
    err = pthread_create(&deamon_id, NULL, jack_deamon, NULL);
    if (err != 0) {
       db_error("mictester: create jack_deamon thread failed\n");
    }
    /* install signal handler and begin capturing */
    signal(SIGINT, sigint_handler);
//    init_hdmi_audio();
    frames = cap_play_sample(device, header.num_channels,
                            header.sample_rate, header.bits_per_sample,argc,argv);
    printf("Captured %d frames\n", frames);
    
    mixer_close(mixer);
    
    return 0;
}

#define true  1
#define false 0


//typedef unsigned char bool;


static int xrun_recovery(snd_pcm_t *handle, int err)
{
    if (err == -EPIPE) {
        err = snd_pcm_prepare(handle);
    }

    if (err < 0) {
        db_warn("Can't recovery from underrun, prepare failed: %s\n", snd_strerror(err));
    }
    else if (err == -ESTRPIPE) {
        while ((err = snd_pcm_resume(handle)) == -EAGAIN) {
            sleep(1);

            if (err < 0) {
                err = snd_pcm_prepare(handle);
            }
            if (err < 0) {
                db_warn("Can't recovery from suspend, prepare failed: %s\n", snd_strerror(err));
            }
        }

        return 0;
    }

    return err;
}

static int sound_play_stop;
#define BUF_LEN   4096
char *buf[BUF_LEN];

static void *music_play(void *args)
{
    char path[256];
    int samplerate;
    int err;
    snd_pcm_t *playback_handle;
    snd_pcm_hw_params_t *hw_params;
    FILE *fp;
    
    db_msg("mictester:prepare play sound...\n");
    if (script_fetch("mic", "music_file", (int *)path, sizeof(path) / 4)) {
        db_warn("mictester:unknown sound file, use default\n");
        strcpy(path, "/dragonboard/data/test48000.pcm");
    }
    if (script_fetch("mic", "samplerate", &samplerate, 1)) {
        db_warn("mictester:unknown samplerate, use default #48000\n");
        samplerate = 48000;
    }
    db_msg("samplerate #%d\n", samplerate);

    err = snd_pcm_open(&playback_handle, "hw:0,0", SND_PCM_STREAM_PLAYBACK, 0);
    if (err < 0) {
        db_error("cannot open audio device (%s)\n", snd_strerror(err));
        pthread_exit((void *)-1);
    }

    err = snd_pcm_hw_params_malloc(&hw_params);
    if (err < 0) {
        db_error("cannot allocate hardware parameter structure (%s)\n", snd_strerror(err));
        pthread_exit((void *)-1);
    }

    err = snd_pcm_hw_params_any(playback_handle, hw_params);
    if (err < 0) {
        db_error("cannot initialize hardware parameter structure (%s)\n", snd_strerror(err));
        pthread_exit((void *)-1);
    }

    err = snd_pcm_hw_params_set_access(playback_handle, hw_params, SND_PCM_ACCESS_RW_INTERLEAVED);
    if (err < 0) {
        db_error("cannot allocate hardware parameter structure (%s)\n", snd_strerror(err));
        pthread_exit((void *)-1);
    }

    err = snd_pcm_hw_params_set_format(playback_handle, hw_params, SND_PCM_FORMAT_S16_LE);
    if (err < 0) {
        db_error("cannot allocate hardware parameter structure (%s)\n", snd_strerror(err));
        pthread_exit((void *)-1);
    }

    err = snd_pcm_hw_params_set_rate(playback_handle, hw_params, samplerate, 0);
    if (err < 0) {
        db_error("cannot set sample rate (%s)\n", snd_strerror(err));
        pthread_exit((void *)-1);
    }

    err = snd_pcm_hw_params_set_channels(playback_handle, hw_params, 2);
    if (err < 0) {
        db_error("cannot set channel count (%s), err = %d\n", snd_strerror(err), err);
        pthread_exit((void *)-1);
    }

    err = snd_pcm_hw_params(playback_handle, hw_params);
    if (err < 0) {
        db_error("cannot set parameters (%s)\n", snd_strerror(err));
        pthread_exit((void *)-1);
    }

    snd_pcm_hw_params_free(hw_params);

    db_msg("mictester:open test pcm file: %s\n", path);
    fp = fopen(path, "r");
    if (fp == NULL) {
        db_error("cannot open test pcm file(%s)\n", strerror(errno));
        pthread_exit((void *)-1);
    }
    
   

    db_msg("mictester:play it...\n");
    while (1) {
        while (!feof(fp)) {
            if (sound_play_stop) {
                goto out;
            }
                 
            err = fread(buf, 1, BUF_LEN, fp);
            if (err < 0) {
                db_warn("read test pcm failed(%s)\n", strerror(errno));
            }

            err = snd_pcm_writei(playback_handle, buf, BUF_LEN/4);
            if (err < 0) {
                err = xrun_recovery(playback_handle, err);
                if (err < 0) {
                    db_warn("write error: %s\n", snd_strerror(err));
                }
            }

            if (err == -EBADFD) {
                db_warn("PCM is not in the right state (SND_PCM_STATE_PREPARED or SND_PCM_STATE_RUNNING)\n");
            }
            if (err == -EPIPE) {
                db_warn("an underrun occurred\n");
            }
            if (err == -ESTRPIPE) {
                db_warn("a suspend event occurred (stream is suspended and waiting for an application recovery)\n");
            }

            if (feof(fp)) {
                fseek(fp, 0L, SEEK_SET);
            }
        }
    }

out:
    db_msg("play end...\n");
    fclose(fp);
    snd_pcm_close(playback_handle);
    pthread_exit(0);
}

unsigned int cap_play_sample(unsigned int device,
                            unsigned int channels, unsigned int rate,
                            unsigned int bits,int argc, char **argv)
{
    
    struct pcm_config cap_config;
    struct pcm_config play_config;
    struct pcm *cap_pcm;
    struct pcm *play_pcm;
	short *ptr;
    int amp_left,amp_right,i,err,loopcount=0;
    char audio_data[32];
    char *buffer;
    unsigned int size;
    void *tret;
    int delay = 0;
    pthread_t tid;
    int playtime=0;
    FILE *mic_pipe;   
    bool mic1_passed=false;
    bool mic2_passed=false;
    bool mic_passed =false;
    
    INIT_CMD_PIPE();
        sound_play_stop=0;
      if (script_fetch("mic", "music_playtime",&playtime,1)) {
         playtime=5;
         db_warn("unknown playtime, use default:%d\n",playtime);
       
       }
        playtime=CLAMP(playtime,0,100);
        db_msg("mictester: music_playtime=%d\n",playtime);
        if(playtime>0) {
          err = pthread_create(&tid, NULL, music_play, NULL);
          if (err != 0) {
             db_error("mictester: create music play thread failed\n");
          }
          sleep(playtime);
          sound_play_stop=1;
             if (pthread_join(tid, &tret)) {
                 db_error("mictester: can't join with sound play thread\n");
            }
             db_msg("mictester: sound play thread exit code #%d\n", (int)tret);
        }else
        {
            db_msg("not need to play music\n");
        }
        
      /* default: delay 5 second to go on */
    if (script_fetch("mic", "delay", &delay, 1) || 
            delay < 0) {
            sleep(5);
    }
    else if (delay > 0) {
        sleep(delay);
    }
    db_msg("let's open the mic pipe\n");
    mic_pipe = fopen(MIC_PIPE_NAME, "w");
    if(mic_pipe==NULL){
        printf("mic1tester:fail to open mic_pipe\n");
        return 0;
    }
    
    setlinebuf(mic_pipe);                                       
    
    cap_config.channels = channels;
    cap_config.rate = rate;
    cap_config.period_size = 1024;
    cap_config.period_count = 2;
    if (bits == 32) {
        cap_config.format = PCM_FORMAT_S32_LE;
    } else if (bits == 16) {
        cap_config.format = PCM_FORMAT_S16_LE;
    }
    cap_config.start_threshold = 0;
    cap_config.stop_threshold = 0;
    cap_config.silence_threshold = 0;

    play_config.channels = channels;
    play_config.rate = rate;
    play_config.period_size = 1024;
    play_config.period_count = 2;
    if (bits == 32)
        play_config.format = PCM_FORMAT_S32_LE;
    else if (bits == 16)
        play_config.format = PCM_FORMAT_S16_LE;
    play_config.start_threshold = 0;
    play_config.stop_threshold = 0;
    play_config.silence_threshold = 0;


    cap_pcm = pcm_open(0, device, PCM_IN, &cap_config);
    if(disp_output_type_t==DISP_OUTPUT_TYPE_HDMI){
        play_pcm = pcm_open(1, device, PCM_OUT, &play_config);//open hdmi audio codec
    }else
    {
        play_pcm = pcm_open(0, device, PCM_OUT, &play_config);//open audio codec
    }
    
     
    if (!cap_pcm || !pcm_is_ready(cap_pcm) || !play_pcm || !pcm_is_ready(play_pcm)) {
        fprintf(stderr, "Unable to open PCM device (%s)\n",
                pcm_get_error(cap_pcm));
        return 0;
    }

    size = pcm_get_buffer_size(cap_pcm);
    buffer = malloc(size);    
    if (!buffer) {
        fprintf(stderr, "Unable to allocate %d bytes\n", size);
        free(buffer);
        pcm_close(cap_pcm);
        pcm_close(play_pcm);
        return 0;
    }

    printf("Capturing sample: %u ch, %u hz, %u bit\n", channels, rate, bits);
    pcm_read(cap_pcm, buffer, size);//dump the noise
    while (capturing ) {
    	int ret = 0;
    	ret = pcm_read(cap_pcm, buffer, size);
        loopcount++;
    	//printf("Letf channel=%d;Right channel=%d\n",*((short*)buffer),*((short*)(buffer+2)));
    	if (ret == 0) {
			#if 0
    		if (pcm_write(play_pcm, buffer, size)) {
    		    fprintf(stderr, "Error playing sample\n");
                break;
    		}
			#endif
          
            if (loopcount<2) continue;
            loopcount=0;
            ptr=(short*)buffer;
            amp_left= 0;
            amp_right=0;   

            for(i=0;i<(size>>2);i++){
                amp_left+=abs(*ptr++);
                amp_right+=abs(*ptr++);      
            }
            amp_left/=i;
            if(amp_left>=32767) amp_left=32767;

            amp_right/=i;
            if(amp_right>=32767) amp_right=32767;
            
            if(!mic_passed){
                if(mic1_used){
                    if(!mic1_passed){
                        if(amp_left>=mic1_threshold){
                        mic1_passed=true;
                        db_msg("mic1 passed!\n");
                        }
                    }
                }
                if(!mic2_passed){
                    if(mic2_used){
                        if(amp_right>=mic2_threshold){
                           mic2_passed=true;
                         db_msg("mic2 passed!\n");
                        }
                    }
                }
                if(mic1_used&&mic2_used){
                    if(mic1_passed&&mic2_passed){
                         mic_passed=true;
                         db_msg("mic passed!\n");           
                         SEND_CMD_PIPE_OK();
                    }
                }else
                if(mic1_used&&!mic2_used){
                    if(mic1_passed){
                         mic_passed=true;
                         db_msg("mic passed!\n");           
                         SEND_CMD_PIPE_OK(); 
                    }
                }else
                if(!mic1_used&&mic2_used){
                    if(mic2_passed){
                         mic_passed=true;
                         db_msg("mic passed!\n");           
                         SEND_CMD_PIPE_OK(); 
                    }
                }
            }
            sprintf(audio_data,"%d#%d",amp_left,amp_right);
            fprintf(mic_pipe, "%s\n", audio_data);
    	}else{
            db_msg("mic overrun!\n");
            setup_mixer(0);
           }
    }
    free(buffer);
   pcm_close(cap_pcm);
   pcm_close(play_pcm);    
}

static void tinymix_set_value(struct mixer *mixer, unsigned int id,
                              unsigned int value)
{
    struct mixer_ctl *ctl;
    enum mixer_ctl_type type;
    unsigned int num_values;
    unsigned int i;

    ctl = mixer_get_ctl(mixer, id);
    type = mixer_ctl_get_type(ctl);
    num_values = mixer_ctl_get_num_values(ctl);

        for (i = 0; i < num_values; i++) {
            if (mixer_ctl_set_value(ctl, i, value)) {
                fprintf(stderr, "Error: invalid value\n");
                return;
            }
        }

}

static void tinymix_set_value_byname(struct mixer *mixer, const char *name,
                              unsigned int value)
{
    struct mixer_ctl *ctl;
    enum mixer_ctl_type type;
    unsigned int num_values;
    unsigned int i;

    ctl = mixer_get_ctl_by_name(mixer, name);
    type = mixer_ctl_get_type(ctl);
    num_values = mixer_ctl_get_num_values(ctl);

    //for (i = 0; i < num_values; i++) 
    {
        //if (mixer_ctl_set_value(ctl, 0, value)) 
		{
			mixer_ctl_set_value(ctl, 0, value);
            //fprintf(stderr, "Error: invalid value\n");
            return;
        }
    }
}

static int tinymix_get_value_byname(struct mixer *mixer, const char *name)
{
    struct mixer_ctl *ctl;
    enum mixer_ctl_type type;
    unsigned int num_values;
    unsigned int i;

    ctl = mixer_get_ctl_by_name(mixer, name);
    type = mixer_ctl_get_type(ctl);
    num_values = mixer_ctl_get_num_values(ctl);

    //for (i = 0; i < num_values; i++) 
    {
        //if (mixer_ctl_get_value(ctl, 0)) {
            //fprintf(stderr, "Error: invalid value\n");
            return mixer_ctl_get_value(ctl, 0);
        //}
    }
}


