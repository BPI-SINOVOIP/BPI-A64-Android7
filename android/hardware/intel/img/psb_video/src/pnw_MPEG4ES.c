/*
 * Copyright (c) 2011 Intel Corporation. All Rights Reserved.
 * Copyright (c) Imagination Technologies Limited, UK
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sub license, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice (including the
 * next paragraph) shall be included in all copies or substantial portions
 * of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT.
 * IN NO EVENT SHALL PRECISION INSIGHT AND/OR ITS SUPPLIERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Authors:
 *    Elaine Wang <elaine.wang@intel.com>
 *    Zeng Li <zeng.li@intel.com>
 *
 */

#include "psb_def.h"
#include "psb_drv_debug.h"
#include "psb_surface.h"
#include "psb_cmdbuf.h"
#include "pnw_MPEG4ES.h"
#include "pnw_hostcode.h"
#include "pnw_hostheader.h"

#include <stdlib.h>
#include <stdint.h>
#include <string.h>


#define TOPAZ_MPEG4_MAX_BITRATE 16000000

#define INIT_CONTEXT_MPEG4ES    context_ENC_p ctx = (context_ENC_p) obj_context->format_data
#define SURFACE(id)    ((object_surface_p) object_heap_lookup( &ctx->obj_context->driver_data->surface_heap, id ))
#define BUFFER(id)  ((object_buffer_p) object_heap_lookup( &ctx->obj_context->driver_data->buffer_heap, id ))



static void pnw_MPEG4ES_QueryConfigAttributes(
    VAProfile __maybe_unused profile,
    VAEntrypoint __maybe_unused entrypoint,
    VAConfigAttrib * attrib_list,
    int num_attribs)
{
    int i;

    drv_debug_msg(VIDEO_DEBUG_GENERAL, "pnw_MPEG4ES_QueryConfigAttributes\n");

    /* RateControl attributes */
    for (i = 0; i < num_attribs; i++) {
        switch (attrib_list[i].type) {
        case VAConfigAttribRTFormat:
            break;

        case VAConfigAttribRateControl:
            attrib_list[i].value = VA_RC_NONE | VA_RC_CBR | VA_RC_VBR;
            break;

        default:
            attrib_list[i].value = VA_ATTRIB_NOT_SUPPORTED;
            break;
        }
    }

    return;
}


static VAStatus pnw_MPEG4ES_ValidateConfig(
    object_config_p obj_config)
{
    int i;
    /* Check all attributes */
    for (i = 0; i < obj_config->attrib_count; i++) {
        switch (obj_config->attrib_list[i].type) {
        case VAConfigAttribRTFormat:
            /* Ignore */
            break;
        case VAConfigAttribRateControl:
            break;
        default:
            return VA_STATUS_ERROR_ATTR_NOT_SUPPORTED;
        }
    }

    return VA_STATUS_SUCCESS;
}


static VAStatus pnw_MPEG4ES_CreateContext(
    object_context_p obj_context,
    object_config_p obj_config)
{
    VAStatus vaStatus = VA_STATUS_SUCCESS;
    context_ENC_p ctx;
    int i;
    unsigned int eRCmode;

    drv_debug_msg(VIDEO_DEBUG_GENERAL, "pnw_MPEG4ES_CreateContext\n");

    vaStatus = pnw_CreateContext(obj_context, obj_config, 0);
    if (VA_STATUS_SUCCESS != vaStatus)
        return VA_STATUS_ERROR_ALLOCATION_FAILED;

    ctx = (context_ENC_p) obj_context->format_data;

    for (i = 0; i < obj_config->attrib_count; i++) {
        if (obj_config->attrib_list[i].type == VAConfigAttribRateControl)
            break;
    }

    if (i >= obj_config->attrib_count)
        eRCmode = VA_RC_NONE;
    else
        eRCmode = obj_config->attrib_list[i].value;


    if (eRCmode == VA_RC_VBR) {
        ctx->eCodec = IMG_CODEC_MPEG4_VBR;
        ctx->sRCParams.RCEnable = IMG_TRUE;
    } else if (eRCmode == VA_RC_CBR) {
        ctx->eCodec = IMG_CODEC_MPEG4_CBR;
        ctx->sRCParams.RCEnable = IMG_TRUE;
    } else if (eRCmode == VA_RC_NONE) {
        ctx->eCodec = IMG_CODEC_MPEG4_NO_RC;
        ctx->sRCParams.RCEnable = IMG_FALSE;
    } else
        return VA_STATUS_ERROR_UNSUPPORTED_RT_FORMAT;
    ctx->eFormat = IMG_CODEC_PL12;

    ctx->Slices = 1;
    ctx->ParallelCores = 1;

    ctx->IPEControl = pnw__get_ipe_control(ctx->eCodec);

    switch (obj_config->profile) {
    case VAProfileMPEG4Simple:
        ctx->profile_idc = 2;
        break;
    case VAProfileMPEG4AdvancedSimple:
        ctx->profile_idc = 3;
        break;
    default:
        ctx->profile_idc = 2;
        break;
    }

    return vaStatus;
}


static void pnw_MPEG4ES_DestroyContext(
    object_context_p obj_context)
{
    drv_debug_msg(VIDEO_DEBUG_GENERAL, "pnw_MPEG4ES_DestroyPicture\n");

    pnw_DestroyContext(obj_context);
}

static VAStatus pnw_MPEG4ES_BeginPicture(
    object_context_p obj_context)
{
    INIT_CONTEXT_MPEG4ES;
    VAStatus vaStatus = VA_STATUS_SUCCESS;

    drv_debug_msg(VIDEO_DEBUG_GENERAL, "pnw_MPEG4ES_BeginPicture\n");

    vaStatus = pnw_BeginPicture(ctx);

    return vaStatus;
}

static VAStatus pnw__MPEG4ES_process_sequence_param(context_ENC_p ctx, object_buffer_p obj_buffer)
{
    VAStatus vaStatus = VA_STATUS_SUCCESS;
    VAEncSequenceParameterBufferMPEG4 *seq_params;
    pnw_cmdbuf_p cmdbuf = ctx->obj_context->pnw_cmdbuf;
    MPEG4_PROFILE_TYPE profile;
    int i, vop_time_increment_resolution;
    unsigned frame_size;

    ASSERT(obj_buffer->type == VAEncSequenceParameterBufferType);
    ASSERT(obj_buffer->num_elements == 1);
    ASSERT(obj_buffer->size == sizeof(VAEncSequenceParameterBufferMPEG4));

    //initialize the frame_rate and qp
    ctx->sRCParams.InitialQp = 15;
    ctx->sRCParams.MinQP = 1;
    ctx->sRCParams.FrameRate = 30;

    if ((obj_buffer->num_elements != 1) ||
        (obj_buffer->size != sizeof(VAEncSequenceParameterBufferMPEG4))) {
        return VA_STATUS_ERROR_UNKNOWN;
    }

    seq_params = (VAEncSequenceParameterBufferMPEG4 *) obj_buffer->buffer_data;
    obj_buffer->buffer_data = NULL;
    obj_buffer->size = 0;

    if (seq_params->bits_per_second > TOPAZ_MPEG4_MAX_BITRATE) {
        ctx->sRCParams.BitsPerSecond = TOPAZ_MPEG4_MAX_BITRATE;
        drv_debug_msg(VIDEO_DEBUG_GENERAL, " bits_per_second(%d) exceeds \
		the maximum bitrate, set it with %d\n",
                                 seq_params->bits_per_second,
                                 TOPAZ_MPEG4_MAX_BITRATE);
    } else
        ctx->sRCParams.BitsPerSecond = seq_params->bits_per_second;

    ctx->sRCParams.FrameRate = (seq_params->frame_rate < 1) ?
        1 : ((65535 < seq_params->frame_rate) ? 65535 : seq_params->frame_rate);
    ctx->sRCParams.InitialQp = seq_params->initial_qp;
    ctx->sRCParams.MinQP = seq_params->min_qp;
    ctx->sRCParams.BUSize = 0;  /* default 0, and will be set in pnw__setup_busize */

    ctx->sRCParams.Slices = 1;
    ctx->sRCParams.QCPOffset = 0;/* FIXME */

    if (ctx->sRCParams.IntraFreq != seq_params->intra_period
            && ctx->raw_frame_count != 0
            && ctx->sRCParams.IntraFreq != 0
            && ((ctx->obj_context->frame_count + 1) % ctx->sRCParams.IntraFreq) != 0
            && (!ctx->sRCParams.bDisableFrameSkipping)) {
        drv_debug_msg(VIDEO_DEBUG_ERROR,
                "Changing intra period value in the middle of a GOP is\n"
                "not allowed if frame skip isn't disabled.\n"
                "it can cause I frame been skipped\n");
        free(seq_params);
        return VA_STATUS_ERROR_INVALID_PARAMETER;
    }
    else
        ctx->sRCParams.IntraFreq = seq_params->intra_period;

    ctx->sRCParams.IntraFreq = seq_params->intra_period;

    frame_size = ctx->sRCParams.BitsPerSecond / ctx->sRCParams.FrameRate;

    ctx->sRCParams.BufferSize = ctx->sRCParams.BitsPerSecond;
    /* Header buffersize is specified in 16384 units, so ensure conformance
       of parameters. InitialLevel in units of 64, assured by this */

    ctx->sRCParams.BufferSize /= 16384;
    ctx->sRCParams.BufferSize *= 16384;

    ctx->sRCParams.InitialLevel = (3 * ctx->sRCParams.BufferSize) >> 4;
    /* Aligned with target frame size */
    ctx->sRCParams.InitialLevel += (frame_size / 2);
    ctx->sRCParams.InitialLevel /= frame_size;
    ctx->sRCParams.InitialLevel *= frame_size;
    ctx->sRCParams.InitialDelay = ctx->sRCParams.BufferSize - ctx->sRCParams.InitialLevel;
    ctx->buffer_size = ctx->sRCParams.BufferSize;

    if (ctx->raw_frame_count == 0) { /* Add Register IO behind begin Picture */
        for (i = (ctx->ParallelCores - 1); i >= 0; i--)
            pnw_set_bias(ctx, i);
    }

    cmdbuf = ctx->obj_context->pnw_cmdbuf;

    switch (ctx->profile_idc) {
    case 2:
        profile = SP;
        break;
    case 3:
        profile = ASP;
        break;
    default:
        profile = SP;
        break;
    }

    memset(cmdbuf->header_mem_p + ctx->seq_header_ofs,
           0,
           HEADER_SIZE);

    vop_time_increment_resolution = (seq_params->vop_time_increment_resolution < 1) ? 1 :
        ((65535 < seq_params->vop_time_increment_resolution) ? 65535 : seq_params->vop_time_increment_resolution);
    pnw__MPEG4_prepare_sequence_header(
        cmdbuf->header_mem_p + ctx->seq_header_ofs,
        0, /* BFrame? */
        profile, /* sProfile */
        seq_params->profile_and_level_indication, /* */
        seq_params->fixed_vop_time_increment, /*3,*/  /* sFixed_vop_time_increment */
        seq_params->video_object_layer_width,/* Picture_Width_Pixels */
        seq_params->video_object_layer_height, /* Picture_Height_Pixels */
        NULL,
        vop_time_increment_resolution); /* VopTimeResolution */

    ctx->MPEG4_vop_time_increment_resolution = vop_time_increment_resolution;

    pnw_cmdbuf_insert_command_package(ctx->obj_context,
                                      ctx->ParallelCores - 1, /* Send to the last core as this will complete first */
                                      MTX_CMDID_DO_HEADER,
                                      &cmdbuf->header_mem,
                                      ctx->seq_header_ofs);

    free(seq_params);
    return vaStatus;
}


static VAStatus pnw__MPEG4ES_process_picture_param(context_ENC_p ctx, object_buffer_p obj_buffer)
{
    VAStatus vaStatus = VA_STATUS_SUCCESS;
    VAEncPictureParameterBufferMPEG4 *pBuffer;
    pnw_cmdbuf_p cmdbuf = ctx->obj_context->pnw_cmdbuf;
    unsigned int *pPictureHeaderMem;
    MTX_HEADER_PARAMS *psPicHeader;
    int i;
    IMG_BOOL bIsVOPCoded = IMG_TRUE;

    ASSERT(obj_buffer->type == VAEncPictureParameterBufferType);

    if ((obj_buffer->num_elements != 1) ||
        (obj_buffer->size != sizeof(VAEncPictureParameterBufferMPEG4))) {
        return VA_STATUS_ERROR_UNKNOWN;
    }

    /* Transfer ownership of VAEncPictureParameterBufferMPEG4 data */
    pBuffer = (VAEncPictureParameterBufferMPEG4 *) obj_buffer->buffer_data;
    obj_buffer->buffer_data = NULL;
    obj_buffer->size = 0;

    ctx->ref_surface = SURFACE(pBuffer->reference_picture);
    ctx->dest_surface = SURFACE(pBuffer->reconstructed_picture);
    ctx->coded_buf = BUFFER(pBuffer->coded_buf);

    ASSERT(ctx->Width == pBuffer->picture_width);
    ASSERT(ctx->Height == pBuffer->picture_height);

    /*if (ctx->sRCParams.RCEnable && ctx->sRCParams.FrameSkip)
        bIsVOPCoded = IMG_FALSE;*/

    ctx->FCode = 4 - 1; /* 4 is default value of "ui8Search_range" */

    pPictureHeaderMem = (unsigned int *)(cmdbuf->header_mem_p + ctx->pic_header_ofs);
    psPicHeader = (MTX_HEADER_PARAMS *)pPictureHeaderMem;

    memset(pPictureHeaderMem, 0, HEADER_SIZE);

    pnw__MPEG4_prepare_vop_header((unsigned char *)pPictureHeaderMem,
                                  bIsVOPCoded,
                                  pBuffer->vop_time_increment, /* In testbench, this should be FrameNum */
                                  4,/* default value is 4,search range */
                                  pBuffer->picture_type,
                                  ctx->MPEG4_vop_time_increment_resolution/* defaule value */);

    /* Mark this header as a complex header */
    psPicHeader->Elements |= 0x100;
    pPictureHeaderMem += ((HEADER_SIZE)  >> 3);

    pnw__MPEG4_prepare_vop_header((unsigned char *)pPictureHeaderMem,
                                  IMG_FALSE,
                                  pBuffer->vop_time_increment, /* In testbench, this should be FrameNum */
                                  4,/* default value is 4,search range */
                                  pBuffer->picture_type,
                                  ctx->MPEG4_vop_time_increment_resolution/* defaule value */);

    pnw_cmdbuf_insert_command_package(ctx->obj_context,
                                      ctx->ParallelCores - 1, /* Send to the last core as this will complete first */
                                      MTX_CMDID_DO_HEADER,
                                      &cmdbuf->header_mem,
                                      ctx->pic_header_ofs);

    /* Prepare START_PICTURE params */
    for (i = (ctx->ParallelCores - 1); i >= 0; i--)
        vaStatus = pnw_RenderPictureParameter(ctx, i);

    free(pBuffer);
    return vaStatus;
}

static VAStatus pnw__MPEG4ES_process_slice_param(context_ENC_p ctx, object_buffer_p obj_buffer)
{
    VAStatus vaStatus = VA_STATUS_SUCCESS;
    VAEncSliceParameterBuffer *pBuffer;
    pnw_cmdbuf_p cmdbuf = ctx->obj_context->pnw_cmdbuf;
    PIC_PARAMS *psPicParams = (PIC_PARAMS *)(cmdbuf->pic_params_p);
    unsigned int i;
    int slice_param_idx;

    ASSERT(obj_buffer->type == VAEncSliceParameterBufferType);

    pBuffer = (VAEncSliceParameterBuffer *) obj_buffer->buffer_data;

    /*In case the slice number changes*/
    if ((ctx->slice_param_cache != NULL) && (obj_buffer->num_elements != ctx->slice_param_num)) {
        drv_debug_msg(VIDEO_DEBUG_GENERAL, "Slice number changes. Previous value is %d. Now it's %d\n",
                                 ctx->slice_param_num, obj_buffer->num_elements);
        free(ctx->slice_param_cache);
        ctx->slice_param_cache = NULL;
        ctx->slice_param_num = 0;
    }

    if (NULL == ctx->slice_param_cache) {
        drv_debug_msg(VIDEO_DEBUG_GENERAL, "Allocate %d VAEncSliceParameterBuffer cache buffers\n", 2 * ctx->slice_param_num);
        ctx->slice_param_num = obj_buffer->num_elements;
        ctx->slice_param_cache = calloc(2 * ctx->slice_param_num, sizeof(VAEncSliceParameterBuffer));
        if (NULL == ctx->slice_param_cache) {
            drv_debug_msg(VIDEO_DEBUG_ERROR, "Run out of memory!\n");
            free(obj_buffer->buffer_data);
            return VA_STATUS_ERROR_ALLOCATION_FAILED;
        }
    }


    for (i = 0; i < obj_buffer->num_elements; i++) {

        unsigned char deblock_idc;

        deblock_idc = pBuffer->slice_flags.bits.disable_deblocking_filter_idc;

        if ((pBuffer->start_row_number == 0) && pBuffer->slice_flags.bits.is_intra) {
            pnw_reset_encoder_params(ctx);
            ctx->BelowParamsBufIdx = (ctx->BelowParamsBufIdx + 1) & 0x1;
        }

        /*The corresponding slice buffer cache*/
        slice_param_idx = (pBuffer->slice_flags.bits.is_intra ? 0 : 1) * ctx->slice_param_num + i;

        if (VAEncSliceParameter_Equal(&ctx->slice_param_cache[slice_param_idx], pBuffer) == 0) {
            /* cache current param parameters */
            memcpy(&ctx->slice_param_cache[slice_param_idx],
                   pBuffer, sizeof(VAEncSliceParameterBuffer));

            /* Setup InParams value*/
            pnw_setup_slice_params(ctx,
                                   pBuffer->start_row_number * 16,
                                   pBuffer->slice_height * 16,
                                   pBuffer->slice_flags.bits.is_intra,
                                   ctx->obj_context->frame_count > 0,
                                   psPicParams->sInParams.SeInitQP);
        }

        pnw__send_encode_slice_params(ctx,
                                      pBuffer->slice_flags.bits.is_intra,
                                      pBuffer->start_row_number * 16,
                                      deblock_idc,
                                      ctx->obj_context->frame_count,
                                      pBuffer->slice_height * 16,
                                      ctx->obj_context->slice_count);

        drv_debug_msg(VIDEO_DEBUG_GENERAL, "Now frame_count/slice_count is %d/%d\n",
                                 ctx->obj_context->frame_count, ctx->obj_context->slice_count);

        ctx->obj_context->slice_count++;
        pBuffer++;

        ASSERT(ctx->obj_context->slice_count < MAX_SLICES_PER_PICTURE);
    }

    free(obj_buffer->buffer_data);
    obj_buffer->buffer_data = NULL;

    return vaStatus;
}


static VAStatus pnw_MPEG4ES_RenderPicture(
    object_context_p obj_context,
    object_buffer_p *buffers,
    int num_buffers)
{
    INIT_CONTEXT_MPEG4ES;
    VAStatus vaStatus = VA_STATUS_SUCCESS;
    int i;

    drv_debug_msg(VIDEO_DEBUG_GENERAL, "pnw_MPEG4ES_RenderPicture\n");

    for (i = 0; i < num_buffers; i++) {
        object_buffer_p obj_buffer = buffers[i];

        switch (obj_buffer->type) {
        case VAEncSequenceParameterBufferType:
            drv_debug_msg(VIDEO_DEBUG_GENERAL, "pnw_MPEG4ES_RenderPicture got VAEncSequenceParameterBufferType\n");
            vaStatus = pnw__MPEG4ES_process_sequence_param(ctx, obj_buffer);
            DEBUG_FAILURE;
            break;

        case VAEncPictureParameterBufferType:
            drv_debug_msg(VIDEO_DEBUG_GENERAL, "pnw_MPEG4ES_RenderPicture got VAEncPictureParameterBufferType\n");
            vaStatus = pnw__MPEG4ES_process_picture_param(ctx, obj_buffer);
            DEBUG_FAILURE;
            break;

        case VAEncSliceParameterBufferType:
            drv_debug_msg(VIDEO_DEBUG_GENERAL, "pnw_MPEG4ES_RenderPicture got VAEncSliceParameterBufferType\n");
            vaStatus = pnw__MPEG4ES_process_slice_param(ctx, obj_buffer);
            DEBUG_FAILURE;
            break;
        default:
            vaStatus = VA_STATUS_ERROR_UNKNOWN;
            DEBUG_FAILURE;
        }
    }

    return vaStatus;
}

static VAStatus pnw_MPEG4ES_EndPicture(
    object_context_p obj_context)
{
    INIT_CONTEXT_MPEG4ES;

    drv_debug_msg(VIDEO_DEBUG_GENERAL, "pnw_MPEG4ES_EndPicture\n");
    return pnw_EndPicture(ctx);
}


struct format_vtable_s pnw_MPEG4ES_vtable = {
queryConfigAttributes:
    pnw_MPEG4ES_QueryConfigAttributes,
validateConfig:
    pnw_MPEG4ES_ValidateConfig,
createContext:
    pnw_MPEG4ES_CreateContext,
destroyContext:
    pnw_MPEG4ES_DestroyContext,
beginPicture:
    pnw_MPEG4ES_BeginPicture,
renderPicture:
    pnw_MPEG4ES_RenderPicture,
endPicture:
    pnw_MPEG4ES_EndPicture
};
