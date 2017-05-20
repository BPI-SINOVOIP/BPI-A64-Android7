/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEOFORMAT_VC1_H__
#define __MIX_VIDEOFORMAT_VC1_H__

#include "mixvideoformat.h"
#include "mixvideoframe_private.h"

//Note: this is only a max limit.  Actual number of surfaces allocated is calculated in mix_videoformat_vc1_initialize()
#define MIX_VIDEO_VC1_SURFACE_NUM	8

/*
 * Type macros.
 */
#define MIX_TYPE_VIDEOFORMAT_VC1                  (mix_videoformat_vc1_get_type ())
#define MIX_VIDEOFORMAT_VC1(obj)                  (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOFORMAT_VC1, MixVideoFormat_VC1))
#define MIX_IS_VIDEOFORMAT_VC1(obj)               (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOFORMAT_VC1))
#define MIX_VIDEOFORMAT_VC1_CLASS(klass)          (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOFORMAT_VC1, MixVideoFormat_VC1Class))
#define MIX_IS_VIDEOFORMAT_VC1_CLASS(klass)       (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOFORMAT_VC1))
#define MIX_VIDEOFORMAT_VC1_GET_CLASS(obj)        (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOFORMAT_VC1, MixVideoFormat_VC1Class))

typedef struct _MixVideoFormat_VC1 MixVideoFormat_VC1;
typedef struct _MixVideoFormat_VC1Class MixVideoFormat_VC1Class;

struct _MixVideoFormat_VC1 {
	/*< public > */
	MixVideoFormat parent;

	/*< public > */

	/*< private > */
	MixVideoFrame * reference_frames[2];
	gboolean haveBframes;
	gboolean loopFilter;
	MixVideoFrame * lastFrame;
};

/**
 * MixVideoFormat_VC1Class:
 *
 * MI-X Video object class
 */
struct _MixVideoFormat_VC1Class {
	/*< public > */
	MixVideoFormatClass parent_class;

	/* class members */

	/*< public > */
};

/**
 * mix_videoformat_vc1_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_videoformat_vc1_get_type(void);

/**
 * mix_videoformat_vc1_new:
 * @returns: A newly allocated instance of #MixVideoFormat_VC1
 *
 * Use this method to create new instance of #MixVideoFormat_VC1
 */
MixVideoFormat_VC1 *mix_videoformat_vc1_new(void);

/**
 * mix_videoformat_vc1_ref:
 * @mix: object to add reference
 * @returns: the MixVideoFormat_VC1 instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideoFormat_VC1 *mix_videoformat_vc1_ref(MixVideoFormat_VC1 * mix);

/**
 * mix_videoformat_vc1_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_videoformat_vc1_unref(obj) g_object_unref (G_OBJECT(obj))

/* Class Methods */

/* VC1 vmethods */
MIX_RESULT mix_videofmt_vc1_getcaps(MixVideoFormat *mix, GString *msg);
MIX_RESULT mix_videofmt_vc1_initialize(MixVideoFormat *mix,
                                  MixVideoConfigParamsDec * config_params,
                                  MixFrameManager * frame_mgr,
				  MixBufferPool * input_buf_pool,
				  MixSurfacePool ** surface_pool,
				  VADisplay va_display);
MIX_RESULT mix_videofmt_vc1_decode(MixVideoFormat *mix, MixBuffer * bufin[],
                gint bufincnt, MixVideoDecodeParams * decode_params);
MIX_RESULT mix_videofmt_vc1_flush(MixVideoFormat *mix);
MIX_RESULT mix_videofmt_vc1_eos(MixVideoFormat *mix);
MIX_RESULT mix_videofmt_vc1_deinitialize(MixVideoFormat *mix);

/* Local Methods */

MIX_RESULT mix_videofmt_vc1_handle_ref_frames(MixVideoFormat *mix,
                                        enum _picture_type frame_type,
                                        MixVideoFrame * current_frame);


MIX_RESULT mix_videofmt_vc1_process_decode(MixVideoFormat *mix,
                                        vbp_data_vc1 *data, 
					guint64 timestamp,
					gboolean discontinuity);


MIX_RESULT mix_videofmt_vc1_release_input_buffers(MixVideoFormat *mix, 
					guint64 timestamp);

#endif /* __MIX_VIDEOFORMAT_VC1_H__ */
