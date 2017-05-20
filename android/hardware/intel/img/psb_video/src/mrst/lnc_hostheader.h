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
 *    Zeng Li <zeng.li@intel.com>
 *    Shengquan Yuan  <shengquan.yuan@intel.com>
 *    Binglin Chen <binglin.chen@intel.com>
 *
 */


/*
 * Description  DMA code for mtx Platform     : Generic
 */

#ifndef _LNC_HOSTHEADER_H_
#define _LNC_HOSTHEADER_H_


#include "img_types.h"

/* Structure contains QP parameters, used with the DoHeader() routine */
typedef struct {
    IMG_UINT32 H264_QP;
    IMG_UINT32 H263_MPG4_FrameQ_scale;
    IMG_UINT32 H263_MPG4_SliceQ_scale;
} MTX_QP_INFO;

/* #include "topaz_vlc_regs.h" */

/* Allocating 32 words (128 bytes aligned to 8 bytes) */
#define MAX_HEADERSIZEWORDS (32)

typedef enum {
    ELEMENT_STARTCODE_RAWDATA = 0,/* Rawdata that includes a start code */
    ELEMENT_RAWDATA,            /* Rawdata */
    ELEMENT_QP,                 /* Insert the H264 Picture Header QP parameter (no rawdata) */
    ELEMENT_SQP,                /* Insert the H264 Slice Header QP parameter (no rawdata) */
    ELEMENT_FRAMEQSCALE,        /* Insert the H263/MPEG4 Frame Q_scale parameter (vob_quant field) (no rawdata) */
    ELEMENT_SLICEQSCALE,        /* Insert the H263/MPEG4 Slice Q_scale parameter (quant_scale field) (no rawdata) */
    ELEMENT_INSERTBYTEALIGN_H264,/* Insert the byte align field (no rawdata) */
    ELEMENT_INSERTBYTEALIGN_MPG4 /* Insert the byte align field  (no rawdata) */
} HEADER_ELEMENT_TYPE;


typedef struct _MTX_HEADER_ELEMENT_ {
    HEADER_ELEMENT_TYPE Element_Type;
    IMG_UINT8 Size;
    IMG_UINT8 Bits;
} MTX_HEADER_ELEMENT;


typedef struct _MTX_HEADER_PARAMS_ {
    IMG_UINT32 Elements;
    MTX_HEADER_ELEMENT asElementStream[MAX_HEADERSIZEWORDS-1];
} MTX_HEADER_PARAMS;

#define ELEMENTS_EMPTY 9999

/* H264 Structures
 */

/* Define some constants for the variable elements in the header stream */
typedef enum _SHPROFILES {
    SH_PROFILE_BP = 0,
    SH_PROFILE_MP = 1
} SH_PROFILE_TYPE;

/* Level number definitions (integer level numbers, non-intermediary only.. except level 1b) */
typedef enum _SHLEVELS {
    SH_LEVEL_1 = 10,
    SH_LEVEL_1B = 111,
    SH_LEVEL_11 = 11,
    SH_LEVEL_12 = 12,
    SH_LEVEL_2 = 20,
    SH_LEVEL_3 = 30,
    SH_LEVEL_31 = 31,
    SH_LEVEL_32 = 32,
    SH_LEVEL_4 = 40,
    SH_LEVEL_5 = 50
} SH_LEVEL_TYPE;


typedef enum _SLHP_SLICEFRAME_TYPE_ {
    SLHP_P_SLICEFRAME_TYPE,
    SLHP_B_SLICEFRAME_TYPE,
    SLHP_I_SLICEFRAME_TYPE,
    SLHP_SP_SLICEFRAME_TYPE,
    SLHP_SI_SLICEFRAME_TYPE,

    SLHP_IDR_SLICEFRAME_TYPE

} SLHP_SLICEFRAME_TYPE;


/* Input parameters for the header generation
 * Some of the following data structures may have fields that are actually static..
 * may want to prune them down a bit later.
 */
typedef struct _H264_VUI_PARAMS_STRUC {
    IMG_UINT32 Time_Scale;
    IMG_UINT32 bit_rate_value_minus1; /* bitrate/64)-1 */
    IMG_UINT32 cbp_size_value_minus1; /* (bitrate*1.5)/16 */
    IMG_UINT8 CBR;
    IMG_UINT8 initial_cpb_removal_delay_length_minus1;
    IMG_UINT8 cpb_removal_delay_length_minus1;
    IMG_UINT8 dpb_output_delay_length_minus1;
    IMG_UINT8 time_offset_length;
} H264_VUI_PARAMS;

typedef struct _H264_CROP_PARAMS_STRUCT_ {
    IMG_BOOL bClip;
    IMG_UINT16 LeftCropOffset;
    IMG_UINT16 RightCropOffset;
    IMG_UINT16 TopCropOffset;
    IMG_UINT16 BottomCropOffset;
} H264_CROP_PARAMS;

typedef struct _H264_SEQUENCE_HEADER_PARAMS_STRUC {
    SH_PROFILE_TYPE ucProfile;
    SH_LEVEL_TYPE ucLevel;
    IMG_UINT8 ucMax_num_ref_frames;
    IMG_UINT8 ucWidth_in_mbs_minus1;
    IMG_UINT8 ucHeight_in_maps_units_minus1;
    IMG_UINT8 VUI_Params_Present;
    H264_VUI_PARAMS VUI_Params;
} H264_SEQUENCE_HEADER_PARAMS;


typedef struct _H264_SLICE_HEADER_PARAMS_STRUC {
    IMG_UINT8 Start_Code_Prefix_Size_Bytes;
    SLHP_SLICEFRAME_TYPE SliceFrame_Type;
    IMG_UINT32 First_MB_Address;
    IMG_UINT8 Frame_Num_DO;
    IMG_UINT8 Picture_Num_DO;
    IMG_BOOL bUsesLongTermRef;
    IMG_BOOL bIsLongTermRef;
    IMG_UINT8 Disable_Deblocking_Filter_Idc;
} H264_SLICE_HEADER_PARAMS;



/* MPEG4 Structures
 */
typedef enum _MPEG4_PROFILE {
    SP = 1,
    ASP = 3
} MPEG4_PROFILE_TYPE;

typedef enum _FIXED_VOP_TIME_ENUM {
    _30FPS = 1,
    _15FPS = 2,
    _10FPS = 3
} FIXED_VOP_TIME_TYPE;

typedef struct _VBVPARAMS_STRUC {
    IMG_UINT32  First_half_bit_rate;
    IMG_UINT32  Latter_half_bit_rate;
    IMG_UINT32  First_half_vbv_buffer_size;
    IMG_UINT32  Latter_half_vbv_buffer_size;
    IMG_UINT32  First_half_vbv_occupancy;
    IMG_UINT32  Latter_half_vbv_occupancy;
} VBVPARAMS;


/*
 * H263 Structures
 */

typedef enum _VOP_CODING_ENUM {
    I_FRAME = 0,
    P_FRAME = 1
} VOP_CODING_TYPE, H263_PICTURE_CODING_TYPE;

typedef enum _SEARCH_RANGE_ENUM {
    PLUSMINUS_32 = 2,
    PLUSMINUS_64 = 3,
    FCODE_EQ_4 = 4
}  SEARCH_RANGE_TYPE;

typedef enum _H263_SOURCE_FORMAT_ENUM {
    _128x96_SubQCIF = 1,
    _176x144_QCIF = 2,
    _352x288_CIF = 3,
    _704x576_4CIF = 4
} H263_SOURCE_FORMAT_TYPE;


#define SIZEINBITS(a) (sizeof(a)*8)

/* H264 header preparation */
void lnc__H264_prepare_sequence_header(
    IMG_UINT32 *pHeaderMemory,
    IMG_UINT32 uiMaxNumRefFrames,
    IMG_UINT32 uiPicWidthInMbs,
    IMG_UINT32 uiPicHeightInMbs,
    IMG_BOOL VUI_present, H264_VUI_PARAMS *VUI_params,
    H264_CROP_PARAMS *psCropParams,
    IMG_UINT8 uiLevel,
    IMG_UINT8 uiProfile);

void lnc__H264_prepare_picture_header(IMG_UINT32 *pHeaderMemory);
void lnc__H264_prepare_slice_header(
    IMG_UINT32 *pHeaderMemory,
    IMG_BOOL    bIntraSlice,
    IMG_UINT32 uiDisableDeblockingFilterIDC,
    IMG_UINT32 uiFrameNumber,
    IMG_UINT32 uiFirst_MB_Address,
    IMG_UINT32 uiMBSkipRun,
    IMG_UINT32 ui_need_idr,
    IMG_BOOL bUsesLongTermRef,
    IMG_BOOL bIsLongTermRef,
    IMG_UINT16 uiIdrPicId);

void lnc__H264_prepare_eodofstream_header(IMG_UINT32 *pHeaderMemory);
void lnc__H264_prepare_endofpicture_header(IMG_UINT32 *pHeaderMemory);
void lnc__H264_prepare_endofsequence_header(IMG_UINT32 *pHeaderMemory);


/* MPEG4 header preparation */
void lnc__MPEG4_prepare_sequence_header(
    IMG_UINT32 *pHeaderMemory,
    IMG_BOOL bBFrame,
    MPEG4_PROFILE_TYPE sProfile,
    IMG_UINT8 Profile_and_level_indication,
    FIXED_VOP_TIME_TYPE sFixed_vop_time_increment,
    IMG_UINT32 Picture_Width_Pixels,
    IMG_UINT32 Picture_Height_Pixels,
    IMG_BOOL bVBVPresent,
    IMG_UINT32  First_half_bit_rate,
    IMG_UINT32  Latter_half_bit_rate,
    IMG_UINT32  First_half_vbv_buffer_size,
    IMG_UINT32  Latter_half_vbv_buffer_size,
    IMG_UINT32  First_half_vbv_occupancy,
    IMG_UINT32  Latter_half_vbv_occupancy,
    IMG_UINT32 VopTimeResolution);

void lnc__MPEG4_prepare_vop_header(
    IMG_UINT32 *pHeaderMem,
    IMG_BOOL bIsVOP_coded,
    IMG_UINT32 VOP_time_increment,
    IMG_UINT8 sSearch_range,
    IMG_UINT8 eVop_Coding_Type,
    IMG_UINT32 VopTimeResolution);


/* H263 header preparation */
void lnc__H263_prepare_sequence_header(
    IMG_UINT32 *pHeaderMem,
    IMG_UINT8 Profile_and_level_indication);

void lnc__H263_prepare_picture_header(
    IMG_UINT32 *pHeaderMem,
    IMG_UINT8 Temporal_Ref,
    H263_PICTURE_CODING_TYPE PictureCodingType,
    H263_SOURCE_FORMAT_TYPE SourceFormatType,
    IMG_UINT8 FrameRate,
    IMG_UINT16 PictureWidth,
    IMG_UINT16 PictureHeight,
    IMG_UINT8 *OptionalCustomPCF);

void lnc__H263_prepare_GOBslice_header(
    IMG_UINT32 *pHeaderMem,
    IMG_UINT8 GOBNumber,
    IMG_UINT8 GOBFrameId);


#endif /* _LNC_HOSTHEADER_H_ */




