/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEORENDERPARAMS_PRIVATE_H__
#define __MIX_VIDEORENDERPARAMS_PRIVATE_H__

typedef struct _MixVideoRenderParamsPrivate MixVideoRenderParamsPrivate;

struct _MixVideoRenderParamsPrivate {
	/*< private > */

	VARectangle *va_cliprects;
};

/**
 * MIX_VIDEO_PRIVATE:
 *
 * Get private structure of this class.
 * @obj: class object for which to get private data.
 */
#define MIX_VIDEORENDERPARAMS_GET_PRIVATE(obj)  \
   (G_TYPE_INSTANCE_GET_PRIVATE ((obj), MIX_TYPE_VIDEORENDERPARAMS, MixVideoRenderParamsPrivate))

/* Internal function */

MIX_RESULT mix_videorenderparams_get_cliprects_internal(
		MixVideoRenderParams * obj,
		VARectangle ** va_cliprects,
		guint* number_of_cliprects);

#endif /* __MIX_VIDEORENDERPARAMS_PRIVATE_H__ */
