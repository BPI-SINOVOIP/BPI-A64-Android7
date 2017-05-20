/*
* Copyright (c) 2009-2011 Intel Corporation.  All rights reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

#ifndef VIDEO_DECODER_DEFS_H_
#define VIDEO_DECODER_DEFS_H_

#include <va/va.h>
#include <stdint.h>

// format specific data, for future extension.
struct VideoExtensionBuffer {
    int32_t extType;
    int32_t extSize;
    uint8_t *extData;
};

typedef enum {
    PACKED_FRAME_TYPE,
} VIDEO_EXTENSION_TYPE;

struct VideoFrameRawData {
    int32_t width;
    int32_t height;
    int32_t pitch[3];
    int32_t offset[3];
    uint32_t fourcc;  //NV12
    int32_t size;
    uint8_t *data;
    bool own; // own data or derived from surface. If true, the library will release the memory during clearnup
};

struct PackedFrameData {
    int64_t timestamp;
    int32_t offSet;
};

// flags for VideoDecodeBuffer, VideoConfigBuffer and VideoRenderBuffer
typedef enum {
    // indicates if sample has discontinuity in time stamp (happen after seeking usually)
    HAS_DISCONTINUITY = 0x01,

    // indicates wheter the sample contains a complete frame or end of frame.
    HAS_COMPLETE_FRAME = 0x02,

    // indicate whether surfaceNumber field  in the VideoConfigBuffer is valid
    HAS_SURFACE_NUMBER = 0x04,

    // indicate whether profile field in the VideoConfigBuffer is valid
    HAS_VA_PROFILE = 0x08,

    // indicate whether output order will be the same as decoder order
    WANT_LOW_DELAY = 0x10, // make display order same as decoding order

    // indicates whether error concealment algorithm should be enabled to automatically conceal error.
    WANT_ERROR_CONCEALMENT = 0x20,

    // indicate wheter raw data should be output.
    WANT_RAW_OUTPUT = 0x40,

    // indicate sample is decoded but should not be displayed.
    WANT_DECODE_ONLY = 0x80,

    // indicate surfaceNumber field is valid and it contains minimum surface number to allocate.
    HAS_MINIMUM_SURFACE_NUMBER = 0x100,

    // indicates surface created will be protected
    WANT_SURFACE_PROTECTION = 0x400,

    // indicates if extra data is appended at end of buffer
    HAS_EXTRADATA = 0x800,

    // indicates if buffer contains codec data
    HAS_CODECDATA = 0x1000,

    // indicate if it use graphic buffer.
    USE_NATIVE_GRAPHIC_BUFFER = 0x2000,

    // indicate whether it is a sync frame in container
    IS_SYNC_FRAME = 0x4000,

    // indicate whether video decoder buffer contains secure data
    IS_SECURE_DATA = 0x8000,

    // indicate it's the last output frame of the sequence
    IS_EOS = 0x10000,

    // indicate should allocate tiling surfaces
    USE_TILING_MEMORY = 0x20000,

     // indicate the frame has resolution change
    IS_RESOLUTION_CHANGE = 0x40000,

    // indicate whether video decoder buffer contains only one field
    IS_SINGLE_FIELD = 0x80000,

    // indicate adaptive playback mode
    WANT_ADAPTIVE_PLAYBACK = 0x100000,

    // indicate the modular drm type
    IS_SUBSAMPLE_ENCRYPTION = 0x200000,

    // indicate meta data mode
    WANT_STORE_META_DATA = 0x400000,
} VIDEO_BUFFER_FLAG;

typedef enum
{
        DecodeHeaderError   = 0,
        DecodeMBError       = 1,
        DecodeSliceMissing  = 2,
        DecodeRefMissing    = 3,
} VideoDecodeErrorType;

#define MAX_ERR_NUM 10

struct VideoDecodeBuffer {
    uint8_t *data;
    int32_t size;
    int64_t timeStamp;
    uint32_t flag;
    uint32_t rotationDegrees;
    VideoExtensionBuffer *ext;
};


//#define MAX_GRAPHIC_BUFFER_NUM  (16 + 1 + 11)  // max DPB + 1 + AVC_EXTRA_NUM
#define MAX_GRAPHIC_BUFFER_NUM 64 // extended for VPP

struct VideoConfigBuffer {
    uint8_t *data;
    int32_t size;
    int32_t width;
    int32_t height;
    uint32_t surfaceNumber;
    VAProfile profile;
    uint32_t flag;
    void *graphicBufferHandler[MAX_GRAPHIC_BUFFER_NUM];
    uint32_t graphicBufferHStride;
    uint32_t graphicBufferVStride;
    uint32_t graphicBufferColorFormat;
    uint32_t graphicBufferWidth;
    uint32_t graphicBufferHeight;
    VideoExtensionBuffer *ext;
    void* nativeWindow;
    uint32_t rotationDegrees;
#ifdef TARGET_HAS_ISV
    uint32_t vppBufferNum;
#endif
};

struct VideoErrorInfo {
    VideoDecodeErrorType type;
    uint32_t num_mbs;
    union {
        struct {uint32_t start_mb; uint32_t end_mb;} mb_pos;
    } error_data;
};

struct VideoErrorBuffer {
    uint32_t errorNumber;   // Error number should be no more than MAX_ERR_NUM
	int64_t timeStamp;      // presentation time stamp
    VideoErrorInfo errorArray[MAX_ERR_NUM];
};

struct VideoRenderBuffer {
    VASurfaceID surface;
    VADisplay display;
    int32_t scanFormat;  //progressive,  top-field first, or bottom-field first
    int64_t timeStamp;  // presentation time stamp
    mutable volatile bool renderDone;  // indicated whether frame is rendered, this must be set to false by the client of this library once
                                        // surface is rendered. Not setting this flag will lead to DECODE_NO_SURFACE error.
    void * graphicBufferHandle;
    int32_t graphicBufferIndex;  //the index in graphichandle array
    uint32_t flag;
    mutable volatile bool driverRenderDone;
    VideoFrameRawData *rawData;

    VideoErrorBuffer errBuf;
};

struct VideoSurfaceBuffer {
    VideoRenderBuffer renderBuffer;
    int32_t pictureOrder;  // picture order count, valid only for AVC format
    bool referenceFrame;  // indicated whether frame associated with this surface is a reference I/P frame
    bool asReferernce; // indicated wheter frame is used as reference (as a result surface can not be used for decoding)
    VideoFrameRawData *mappedData;
    VideoSurfaceBuffer *next;
};

struct VideoFormatInfo {
    bool valid;  // indicates whether format info is valid. MimeType is always valid.
    char *mimeType;
    uint32_t width;
    uint32_t height;
    uint32_t surfaceWidth;
    uint32_t surfaceHeight;
    uint32_t surfaceNumber;
    VASurfaceID *ctxSurfaces;
    int32_t aspectX;
    int32_t aspectY;
    int32_t cropLeft;
    int32_t cropRight;
    int32_t cropTop;
    int32_t cropBottom;
    int32_t colorMatrix;
    int32_t videoRange;
    int32_t bitrate;
    int32_t framerateNom;
    int32_t framerateDenom;
    uint32_t actualBufferNeeded;
    int32_t flags; // indicate whether current picture is field or frame
    VideoExtensionBuffer *ext;
};

// TODO: categorize the follow errors as fatal and non-fatal.
typedef enum {
    DECODE_NOT_STARTED = -10,
    DECODE_NEED_RESTART = -9,
    DECODE_NO_CONFIG = -8,
    DECODE_NO_SURFACE = -7,
    DECODE_NO_REFERENCE = -6,
    DECODE_NO_PARSER = -5,
    DECODE_INVALID_DATA = -4,
    DECODE_DRIVER_FAIL = -3,
    DECODE_PARSER_FAIL = -2,
    DECODE_MEMORY_FAIL = -1,
    DECODE_FAIL = 0,
    DECODE_SUCCESS = 1,
    DECODE_FORMAT_CHANGE = 2,
    DECODE_FRAME_DROPPED = 3,
    DECODE_MULTIPLE_FRAME = 4,
} VIDEO_DECODE_STATUS;

typedef int32_t Decode_Status;

#ifndef NULL
#define NULL 0
#endif

inline bool checkFatalDecoderError(Decode_Status status) {
    if (status == DECODE_NOT_STARTED ||
        status == DECODE_NEED_RESTART ||
        status == DECODE_NO_PARSER ||
        status == DECODE_INVALID_DATA ||
        status == DECODE_MEMORY_FAIL ||
        status == DECODE_FAIL) {
        return true;
    } else {
        return false;
    }
}

#endif  // VIDEO_DECODER_DEFS_H_
