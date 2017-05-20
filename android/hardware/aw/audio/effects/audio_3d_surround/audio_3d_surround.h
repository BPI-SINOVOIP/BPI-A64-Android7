#ifndef __AUDIO_3D_SURROUND_H_
#define __AUDIO_3D_SURROUND_H_

/*
 * the 3d surround struct
 *
 */
struct audio_3d_surround {
	void *lib;

	void *sur_handle;
	int headp_use;
	void *(*process_init)(void *handle, int samp_rate, int chn, int num_frame, int headp_use);
	void (*surround_pro_in_out)(void *handle, short *buf, short *new_sp, int data_num);
	void (*process_exit)(void *handle);
	void (*set_space)(void *handle, double space_gain);
	void (*set_bass)(void *handle, double sub_gain);
	void (*set_defintion)(void *handle, double defintion_gain);
};

/*
 * interface for user
 *
 */
int sur_init(struct audio_3d_surround *sur, int samp_rate, int chn, int num_frame);
bool sur_enable(struct audio_3d_surround *sur);
bool sur_prepare(struct audio_3d_surround *sur, int out_device, int dul_spk_use,
				int samp_rate, int chn, int num_frame);
int sur_process(struct audio_3d_surround *sur, short *buf, int frames, int channels);
void sur_exit(struct audio_3d_surround *sur);

#endif
