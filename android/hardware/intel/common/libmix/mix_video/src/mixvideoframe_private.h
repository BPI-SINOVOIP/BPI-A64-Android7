/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_VIDEOFRAME_PRIVATE_H__
#define __MIX_VIDEOFRAME_PRIVATE_H__

#include "mixvideoframe.h"
#include "mixsurfacepool.h"

typedef enum _MixFrameType
{
  TYPE_I,
  TYPE_P,
  TYPE_B,
  TYPE_INVALID
} MixFrameType;

typedef struct _MixVideoFramePrivate MixVideoFramePrivate;

struct _MixVideoFramePrivate
{
  /*< private > */
  MixSurfacePool *pool;
  MixFrameType frame_type;
  gboolean is_skipped;
  MixVideoFrame *real_frame;
  GStaticRecMutex lock;
};

/**
* MIX_VIDEOFRAME_PRIVATE:
* 
* Get private structure of this class.
* @obj: class object for which to get private data.
*/
#define MIX_VIDEOFRAME_GET_PRIVATE(obj)  \
   (G_TYPE_INSTANCE_GET_PRIVATE ((obj), MIX_TYPE_VIDEOFRAME, MixVideoFramePrivate))


/* Private functions */
MIX_RESULT
mix_videoframe_set_pool (MixVideoFrame *obj, MixSurfacePool *pool);

MIX_RESULT
mix_videoframe_set_frame_type (MixVideoFrame *obj,  MixFrameType frame_type);

MIX_RESULT
mix_videoframe_get_frame_type (MixVideoFrame *obj,  MixFrameType *frame_type);

MIX_RESULT
mix_videoframe_set_is_skipped (MixVideoFrame *obj,  gboolean is_skipped);

MIX_RESULT
mix_videoframe_get_is_skipped (MixVideoFrame *obj,  gboolean *is_skipped);

MIX_RESULT
mix_videoframe_set_real_frame (MixVideoFrame *obj,  MixVideoFrame *real);

MIX_RESULT
mix_videoframe_get_real_frame (MixVideoFrame *obj,  MixVideoFrame **real);


#endif /* __MIX_VIDEOFRAME_PRIVATE_H__ */
