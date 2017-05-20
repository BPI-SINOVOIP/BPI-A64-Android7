#ifndef __VOLUME_H__
#define __VOLUME_H__

struct volume_array{
	int earpiece_phonepn_gain[6];
	int earpiece_mixer_gain[6];
	int earpiece_hp_gain[6];

	int headset_phonepn_gain[6];
	int headset_mixer_gain[6];
	int headset_hp_gain[6];

	int speaker_phonepn_gain[6];
	int speaker_mixer_gain[6];
	int speaker_spk_gain[6];

	int fm_headset_line_gain[6];
	int fm_headset_hp_gain[6];

	int fm_speaker_line_gain[6];
	int fm_speaker_spk_gain[6];

	int up_pcm_gain;
};

int codec_voice_volume_init(struct volume_array *vol_array);
#endif