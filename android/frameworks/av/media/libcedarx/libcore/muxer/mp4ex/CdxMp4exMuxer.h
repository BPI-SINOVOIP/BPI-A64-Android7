/*
 * Copyright (c) 2008-2016 Allwinner Technology Co. Ltd.
 * All rights reserved.
 *
 * File : CdxMp4exMuxer.h
 * Description : Allwinner MP4 Muxer Definition
 * History :
 *   Author  :
 *   Date    :
 *   Comment :
 */

#ifndef CDX_MP4EX_MUXER_H
#define CDX_MP4EX_MUXER_H

#include "log.h"
#include "CdxMuxer.h"
#include "CdxMuxerBaseDef.h"
#include "CdxFsWriter.h"

#define offset_t cdx_int64
#define MAX_STREAMS_IN_MP4_FILE 2
#define MOV_MKTAG(a,b,c,d) (a | (b << 8) | (c << 16) | (d << 24))

//1280*720. 30fps, 17.5 minute
#define STCO_CACHE_SIZE (8 * 1024)        //about 1 hours !must times of tiny_page_size
#define STSZ_CACHE_SIZE (8 * 1024 * 4)    //(8*1024*16) about 1 hours !must times of tiny_page_size
#define STTS_CACHE_SIZE (STSZ_CACHE_SIZE) //about 1 hours !must times of tiny_page_size
#define STSC_CACHE_SIZE (STCO_CACHE_SIZE) //about 1 hours !must times of tiny_page_size

#define STSZ_CACHE_OFFSET_INFILE_VIDEO  (0 * 1024 * 1024)
#define STSZ_CACHE_OFFSET_INFILE_AUDIO  (2 * 1024 * 1024)
#define STTS_CACHE_OFFSET_INFILE_VIDEO  (4 * 1024 * 1024)
#define STCO_CACHE_OFFSET_INFILE_VIDEO  (6 * 1024 * 1024)
#define STCO_CACHE_OFFSET_INFILE_AUDIO  (6 * 1024 * 1024 + 512 * 1024)
#define STSC_CACHE_OFFSET_INFILE_VIDEO  (7 * 1024 * 1024)
#define STSC_CACHE_OFFSET_INFILE_AUDIO  (7 * 1024 * 1024 + 512 * 1024)

#define TOTAL_CACHE_SIZE (STCO_CACHE_SIZE * 2 + STSZ_CACHE_SIZE * 2 + STSC_CACHE_SIZE * 2 + \
                          STTS_CACHE_SIZE + MOV_CACHE_TINY_PAGE_SIZE) //32bit

#define KEYFRAME_CACHE_SIZE (8 * 1024 * 16)

#define MOV_HEADER_RESERVE_SIZE (1024 * 1024)

//set it to 2K !!attention it must set to below MOV_RESERVED_CACHE_ENTRIES
#define MOV_CACHE_TINY_PAGE_SIZE (1024 * 2)
#define MOV_CACHE_TINY_PAGE_SIZE_IN_BYTE (MOV_CACHE_TINY_PAGE_SIZE * 4)

#define MOV_BUF_NAME_LEN (128)

#define GLOBAL_TIMESCALE 1000

typedef enum {
    STCO_ID = 0,//don't change all
    STSZ_ID = 1,
    STSC_ID = 2,
    STTS_ID = 3
} MuxChunkID;

typedef struct SttsTable {
    cdx_int32 count;
    cdx_int32 duration;
} SttsTable;

typedef struct MuxAVCodecCtx {
    cdx_int32   width;
    cdx_int32   height;
    CodecType   codec_type; /* see CODEC_TYPE_xxx */
    cdx_int32   rotate_degree;
    cdx_uint32  codec_tag;
    cdx_uint32  codec_id;

    cdx_int32   channels;
    cdx_int32   frame_rate;
    cdx_int32   frame_size; // for audio, it means sample count a audioFrame contain;
                            // in fact, its value is assigned by pcmDataSize(MAXDECODESAMPLE=1024)
                            // which is transport by previous AudioSourceComponent.
                            // aac encoder will encode a frame from MAXDECODESAMPLE samples;
                            // but for pcm, one frame == one sample according to movSpec.
    cdx_int32   bits_per_sample;
    cdx_int32   sample_rate;
} MuxAVCodecCtx;

typedef struct Mp4Track
{
    cdx_int32           track_timescale;
    cdx_int32           time;
    cdx_int64           last_pts;
    cdx_int64           track_duration;
    cdx_int32           sample_size;
    cdx_int32           track_id;
    cdx_int32           stream_type; // 0: viedo, 1: audio
    cdx_int32           tag; ///< stsd fourcc, e,g, 'sowt', 'mp4a'
    MuxAVCodecCtx       enc;

    cdx_int32           extra_data_len;
    char*              extra_data;


    cdx_int64           stsz_offset; // the start offset of stsz atom
    cdx_int64           mdat_start_pos;

    cdx_uint32          stsz_total_num;
    cdx_uint32          stco_total_num;
    cdx_uint32          stsc_total_num;
    cdx_uint32          stts_total_num;
    cdx_uint32          stss_total_num;

//    cdx_uint32          stsz_tiny_pages;
//    cdx_uint32          stco_tiny_pages;
//    cdx_uint32          stsc_tiny_pages;
//    cdx_uint32          stts_tiny_pages;

//    cdx_uint32          key_frame_num;
} Mp4Track;

typedef struct MuxMOVCtx
{
    cdx_int64       create_time;
    cdx_int32       nb_streams;

    cdx_int64       mdat_pos;
    cdx_int64       mdat_start_pos; // raw bitstream start pos
    cdx_int64       mdat_size;

    cdx_int32       mov_timescale;

    // for user infomation
    cdx_int32       mov_geo_available;
    cdx_int32       mov_latitudex;
    cdx_int32       mov_longitudex;

    cdx_int32       rotate_degree;

    Mp4Track        *tracks[MAX_STREAMS_IN_MP4_FILE];

    cdx_int32       keyframe_num;
}MuxMOVCtx;

typedef struct Mp4exMuxCtx
{
    CdxMuxerT           muxInfo;
    CdxWriterT          *pWriter;

    MuxMOVCtx           *data_ctx;

    // for write moov header
    int                 fd_header;
} Mp4exMuxCtx;

#define MOV_TMPFILE_DIR "/mnt/UDISK/mp4tmp/"
#define MOV_TMPFILE_EXTEND_NAME ".tmp"


#endif /* CDX_MP4_MUXER_H */
