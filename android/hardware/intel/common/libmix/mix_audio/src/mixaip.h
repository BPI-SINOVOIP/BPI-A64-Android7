/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_AUDIOINITPARAMS_H__
#define __MIX_AUDIOINITPARAMS_H__


#include <mixparams.h>

/**
 * MIX_TYPE_AUDIOINITPARAMS:
 * 
 * Get type of class.
 */
#define MIX_TYPE_AUDIOINITPARAMS (mix_aip_get_type ())

/**
 * MIX_AUDIOINITPARAMS:
 * @obj: object to be type-casted.
 * 
 * Type casting.
 */
#define MIX_AUDIOINITPARAMS(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_AUDIOINITPARAMS, MixAudioInitParams))

/**
 * MIX_IS_AUDIOINITPARAMS:
 * @obj: an object.
 * 
 * Checks if the given object is an instance of #MixParams
 */
#define MIX_IS_AUDIOINITPARAMS(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_AUDIOINITPARAMS))

/**
 * MIX_AUDIOINITPARAMS_CLASS:
 * @klass: class to be type-casted.
 * 
 * Type casting.
 */
#define MIX_AUDIOINITPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_AUDIOINITPARAMS, MixAudioInitParamsClass))

/**
 * MIX_IS_AUDIOINITPARAMS_CLASS:
 * @klass: a class.
 * 
 * Checks if the given class is #MixParamsClass
 */
#define MIX_IS_AUDIOINITPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_AUDIOINITPARAMS))

/**
 * MIX_AUDIOINITPARAMS_GET_CLASS:
 * @obj: a #MixParams object.
 * 
 * Get the class instance of the object.
 */
#define MIX_AUDIOINITPARAMS_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_AUDIOINITPARAMS, MixAudioInitParamsClass))

typedef struct _MixAudioInitParams        MixAudioInitParams;
typedef struct _MixAudioInitParamsClass   MixAudioInitParamsClass;

/**
 * MixAudioInitParams:
 * @parent: Parent.
 *
 * @MixAudio initialization parameter object.
 */
struct _MixAudioInitParams
{
  /*< public >*/
  MixParams parent;

  /*< private >*/
  void* reserved1;
  void* reserved2;
  void* reserved3;
  void* reserved4;
};

/**
 * MixAudioInitParamsClass:
 * @parent_class: Parent class.
 * 
 * @MixAudio initialization parameter object class structure.
 */
struct _MixAudioInitParamsClass
{
  /*< public >*/
  MixParamsClass parent_class;

  /* class members */
};

/**
 * mix_aip_get_type:
 * @returns: type
 * 
 * Get the type of object.
 */
GType mix_aip_get_type (void);

/**
 * mix_aip_new:
 * @returns: A newly allocated instance of #MixAudioInitParams
 * 
 * Use this method to create new instance of #MixAudioInitParams
 */
MixAudioInitParams *mix_aip_new(void);

/**
 * mix_aip_ref:
 * @mix: object to add reference
 * @returns: the MixAudioInitParams instance where reference count has been increased.
 * 
 * Add reference count.
 */
MixAudioInitParams *mix_aip_ref(MixAudioInitParams *mix);

/**
 * mix_aip_unref:
 * @obj: object to unref.
 * 
 * Decrement reference count of the object.
 */
#define mix_aip_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

#endif /* __MIX_AUDIOINITPARAMS_H__ */
