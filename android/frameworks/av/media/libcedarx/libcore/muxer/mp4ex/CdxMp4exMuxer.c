/*
 * Copyright (c) 2008-2016 Allwinner Technology Co. Ltd.
 * All rights reserved.
 *
 * File : CdxMp4Muxer.c
 * Description : Allwinner MP4 Muxer Definition
 * History :
 *   Author  :
 *   Date    :
 *   Comment :
 *
 */

#include "CdxMp4exMuxer.h"

#define MP4MKTAG(a,b,c,d) (a | (b << 8) | (c << 16) | (d << 24))

#define mp4open64(uri) open(uri, O_LARGEFILE | O_WRONLY)

#define mp4seek64(fd, offset, whence) lseek64(fd, offset, whence)

#define mp4tell64(fd) lseek64(fd, 0, SEEK_CUR)

#define mp4read64(fd, buf, len) read(fd, buf, len)

#define mp4write64(fd, buf, len) write(fd, buf, len)

#define mp4close64(fd) close(fd)


/***************************** Write Data to Stream File *****************************/
static cdx_int32 writeBuffer(CdxWriterT *s, cdx_uint8 *buf, cdx_int32 size)
{
    return CdxWriterWrite(s, (cdx_uint8*)buf, size);
}

static int writeByte(CdxWriterT *s, cdx_int32 b)
{
    return CdxWriterWrite(s, (cdx_uint8*)(&b), 1);
}

static int writeTag(CdxWriterT *s, const char *tag)
{
    int ret = 0;
    while (*tag)
    {
        ret = writeByte(s, *tag++);
    }
    return ret;
}

static int writeBe16(CdxWriterT *s, cdx_uint32 val)
{
    int ret;
    ret = writeByte(s, val >> 8);
    ret = writeByte(s, val);
    return ret;
}

static int writeBe24(CdxWriterT *s, cdx_uint32 val)
{
    int ret;
    ret = writeBe16(s, val >> 8);
    ret = writeByte(s, val);
    return ret;
}

static int writeBe32(CdxWriterT *s, cdx_uint32 val)
{
    val= ((val<<8)&0xFF00FF00) | ((val>>8)&0x00FF00FF);
    val= (val>>16) | (val<<16);
    return writeBuffer(s, (cdx_uint8*)&val, 4);
}

static int writeLe32(CdxWriterT *s, cdx_uint32 val)
{
    return writeBuffer(s, (cdx_uint8*)&val, 4);
}

static cdx_int32 writeBufferfd(int fd, char *buf, cdx_int32 size)
{
    return mp4write64(fd, (cdx_uint8*)buf, size);
}

static int writeBytefd(int fd, cdx_int32 b)
{
    return mp4write64(fd, (cdx_uint8*)(&b), 1);
}

static int writeTagfd(int fd, const char *tag)
{
    int ret = 0;
    while (*tag)
    {
        ret = writeBytefd(fd, *tag++);
    }
    return ret;
}

static int writeBe16fd(int fd, cdx_uint32 val)
{
    int ret;
    ret = writeBytefd(fd, val >> 8);
    ret = writeBytefd(fd, val);
    return ret;
}

static int writeBe24fd(int fd, cdx_uint32 val)
{
    int ret;
    ret = writeBe16fd(fd, val >> 8);
    ret = writeBytefd(fd, val);
    return ret;
}


static int writeBe32fd(int fd, cdx_uint32 val)
{
    val= ((val<<8)&0xFF00FF00) | ((val>>8)&0x00FF00FF);
    val= (val>>16) | (val<<16);
    return mp4write64(fd, (cdx_uint8*)&val, 4);
}

static cdx_int32 writeFtypTag(CdxWriterT *pb)
{
    cdx_int32 minor = 0x200;
    int ret;

    ret = writeBe32(pb, 28); /* size */
    ret = writeTag(pb, "ftyp");
    writeTag(pb, "isom");
    writeBe32(pb, minor);

    writeTag(pb, "isom");
    writeTag(pb, "iso2");

    ret = writeTag(pb, "mp41");

    return ret;
}

static cdx_int32 writeMdatTag(CdxWriterT *pb, MuxMOVCtx *mov)
{
    int ret;
    mov->mdat_pos = MOV_HEADER_RESERVE_SIZE;

    mov->mdat_start_pos = mov->mdat_pos + 8;

    ret = CdxWriterSeek(pb, mov->mdat_pos, SEEK_SET);
    if(ret < 0)
    {
        loge("seek failed");
        return ret;
    }

    ret = writeBe32(pb, 0); /* size placeholder */
    ret = writeTag(pb, "mdat");
    return ret;
}

static cdx_int32 writeFreeTag(int fd, cdx_int32 size)
{
    int ret;
    ret = writeBe32fd(fd, size);
    ret = writeTagfd(fd, "free");
    return ret;
}

static cdx_uint32 getUdtaTagSize()
{
    cdx_uint32 udta_tag_size = 4+4+4+4  //(be32_cache, tag_cache, be32_cache, tag_cache)
                +4+8+9+1;      //(0x001215c7, latitude, longitude, 0x2F)
    return udta_tag_size;
}


static int writeLtude(int fd, int degreex10000)
{
    int is_negative = (degreex10000 < 0);
    char sign = is_negative? '-': '+';

    // Handle the whole part
    char str[9];
    int wholePart = degreex10000 / 10000;
    if (wholePart == 0)
    {
        snprintf(str, 5, "%c%.2d.", sign, wholePart);
    }
    else
    {
        snprintf(str, 5, "%+.2d.", wholePart);
    }

    // Handle the fractional part
    int fractional_pat = degreex10000 - (wholePart * 10000);
    if (fractional_pat < 0)
    {
        fractional_pat = -fractional_pat;
    }
    snprintf(&str[4], 5, "%.4d", fractional_pat);

    // Do not write the null terminator
    writeBufferfd(fd, (char *)str, 8);
    return 0;
}

static int writeLgtude(int fd, int degreex10000)
{
    int is_negative = (degreex10000 < 0);
    char sign = is_negative? '-': '+';

    // Handle the whole part
    char str[10];
    int wholePart = degreex10000 / 10000;
    if (wholePart == 0)
    {
        snprintf(str, 6, "%c%.3d.", sign, wholePart);
    }
    else
    {
        snprintf(str, 6, "%+.3d.", wholePart);
    }

    // Handle the fractional part
    int fractional_pat = degreex10000 - (wholePart * 10000);
    if (fractional_pat < 0)
    {
        fractional_pat = -fractional_pat;
    }
    snprintf(&str[5], 5, "%.4d", fractional_pat);

    // Do not write the null terminator
    return writeBufferfd(fd, (char *)str, 9);
}

/*
*     write udat in moov atom
*/
static cdx_int32 writeUdtaTag(int fd, MuxMOVCtx *mov)
{
    cdx_uint32 udta_tag_size = getUdtaTagSize();
    int ret;

    writeBe32fd(fd, udta_tag_size); /* size */

    writeTagfd(fd, "udta");

    writeBe32fd(fd, udta_tag_size-8);
    writeTagfd(fd, "\xA9xyz");

    /*
     * For historical reasons, any user data start
     * with "\0xA9", must be followed by its assoicated
     * language code.
     * 0x0012: text string length
     * 0x15c7: lang (locale) code: en
     */
    ret = writeBe32fd(fd, 0x001215c7);

    writeLtude(fd, mov->mov_latitudex);
    writeLgtude(fd, mov->mov_longitudex);
    ret = writeBytefd(fd, 0x2F);

    return ret;
}

static cdx_uint32 getMvhdTagSize()
{
    return 108;
}

/*
*    write mvhd atom in moov
*     we can write it at start time, expect the duration of the file
*/
static cdx_int32 writeMvhdTag(int fd, MuxMOVCtx *mov)
{
    int ret;
    cdx_int32 max_track_id = 1, i;

    for (i = 0; i < MAX_STREAMS_IN_MP4_FILE/*mov->nb_streams*/; i++)
    {
        if (mov->tracks[i])
        {
            if (max_track_id < mov->tracks[i]->track_id)
            {
                max_track_id = mov->tracks[i]->track_id;
            }
        }
    }

    cdx_uint32 mvhd_tag_size = getMvhdTagSize();

    ret = writeBe32fd(fd, mvhd_tag_size); /* size */
    writeTagfd(fd, "mvhd");
    writeBytefd(fd, 0);  /* version */
    writeBe24fd(fd, 0); /* flags */

    writeBe32fd(fd, mov->create_time); /* creation time */
    writeBe32fd(fd, mov->create_time); /* modification time */
    writeBe32fd(fd, mov->mov_timescale); /* timescale */

    /* duration of longest track */
    if(mov->tracks[0])
        writeBe32fd(fd, mov->tracks[0]->track_duration);

    ret = writeBe32fd(fd, 0x00010000); /* reserved (preferred rate) 1.0 = normal */
    writeBe16fd(fd, 0x0100); /* reserved (preferred volume) 1.0 = normal */
    writeBe16fd(fd, 0); /* reserved */
    writeBe32fd(fd, 0); /* reserved */
    writeBe32fd(fd, 0); /* reserved */

    /* Matrix structure */
    writeBe32fd(fd, 0x00010000); /* reserved */
    writeBe32fd(fd, 0x0); /* reserved */
    writeBe32fd(fd, 0x0); /* reserved */
    writeBe32fd(fd, 0x0); /* reserved */
    writeBe32fd(fd, 0x00010000); /* reserved */
    writeBe32fd(fd, 0x0); /* reserved */
    writeBe32fd(fd, 0x0); /* reserved */
    writeBe32fd(fd, 0x0); /* reserved */
    writeBe32fd(fd, 0x40000000); /* reserved */

    writeBe32fd(fd, 0); /* reserved (preview time) */
    writeBe32fd(fd, 0); /* reserved (preview duration) */
    writeBe32fd(fd, 0); /* reserved (poster time) */
    writeBe32fd(fd, 0); /* reserved (selection time) */
    writeBe32fd(fd, 0); /* reserved (selection duration) */
    ret = writeBe32fd(fd, 0); /* reserved (current time) */

    ret = writeBe32fd(fd, max_track_id + 1); /* Next track id */

    return ret;
}

/*********************************** Write Audio Tag *********************************/
#if 0
static cdx_uint32 getAudioTagSize(Mp4Track *track)
{
    cdx_int32 version = 0;
    cdx_uint32 audio_tag_size = 16 + 8 + 12;
    if (track->enc.codec_id == MUX_CODEC_ID_ADPCM)
    {
        version = 1;
    }
    if (version == 1)
    { /* SoundDescription V1 extended info */
        audio_tag_size += 16;
    }
    if (track->tag == MOV_MKTAG('m','p','4','a'))
    {
        audio_tag_size += videoGetEsdsTagSize(track);
    }

    return audio_tag_size;
}

static cdx_int32 stsdWriteAudioTag(int fd, Mp4Track *track)
{
    cdx_uint32 audio_tag_size = getAudioTagSize(track);
    //offset_t pos = url_ftell(pb);
    cdx_int32 version = 0;

    if (track->enc.codec_id == MUX_CODEC_ID_ADPCM)
    {
        version = 1;
    }

    writeBe32Stream(pb, audio_tag_size); /* size */
    writeLe32Stream(pb, track->tag); // store it byteswapped
    writeBe32Stream(pb, 0); /* Reserved */
    writeBe16Stream(pb, 0); /* Reserved */
    writeBe16Stream(pb, 1); /* Data-reference index, XXX  == 1 */

    /* SoundDescription */
    writeBe16Stream(pb, version); /* Version */
    writeBe16Stream(pb, 0); /* Revision level */
    writeBe32Stream(pb, 0); /* vendor */

    { /* reserved for mp4/3gp */
        writeBe16Stream(pb, track->enc.channels); //channel
        writeBe16Stream(pb, track->enc.bits_per_sample);//bits per sample
        writeBe16Stream(pb, 0); /* compression id = 0*/
    }

    writeBe16Stream(pb, 0); /* packet size (= 0) */
    writeBe16Stream(pb, track->enc.sample_rate); /* Time scale !!!??? */
    writeBe16Stream(pb, 0); /* Reserved */

    if (version == 1)
    { /* SoundDescription V1 extended info */
        if (track->enc.codec_id == MUX_CODEC_ID_ADPCM)
        {
            writeBe32Stream(pb, track->enc.frame_size); /* Samples per packet */
            /* Bytes per packet */
            writeBe32Stream(pb, track->enc.frame_size * (track->enc.bits_per_sample >> 3));
            writeBe32Stream(pb, track->enc.frame_size*(track->enc.bits_per_sample >> 3) *
                                    track->enc.channels); /* Bytes per frame */
            writeBe32Stream(pb, 2); /* Bytes per sample */
        }
        else
        {
            writeBe32Stream(pb, track->enc.frame_size); /* Samples per packet */
            writeBe32Stream(pb, track->sample_size / track->enc.channels); /* Bytes per packet */
            writeBe32Stream(pb, track->sample_size); /* Bytes per frame */
            writeBe32Stream(pb, 2); /* Bytes per sample */
        }
    }

    if (track->tag == MOV_MKTAG('m','p','4','a'))
    {
        videoWriteEsdsTag(pb, track);
    }

    //return updateSize(pb, pos);
    return audio_tag_size;
}
#endif
/*********************************** Write Audio Tag *********************************/

/******************************* write video info ********************************/

static int movBswap32(unsigned int val)
{
    val= ((val<<8)&0xFF00FF00) | ((val>>8)&0x00FF00FF);
    val= (val>>16) | (val<<16);
    return val;
}

static cdx_uint8 *searchAvcStartcode(cdx_uint8 *p, cdx_uint8 *end)
{
    cdx_uint8 *a = p + 4 - ((long)p & 3);

    for (end -= 3; p < a && p < end; p++)
    {
        if (p[0] == 0 && p[1] == 0 && p[2] == 1)
        {
            return p;
        }
    }

    for ( end -= 3; p < end; p += 4 )
    {
        cdx_uint32 x = *(const cdx_uint32*)p;
        if ((x - 0x01010101) & (~x) & 0x80808080)
        { // generic
            if (p[1] == 0)
            {
                if (p[0] == 0 && p[2] == 1)
                {
                    return (p - 1);
                 }
                if (p[2] == 0 && p[3] == 1)
                {
                    return p;
                }
            }
            if ( p[3] == 0 )
            {
                if ( p[2] == 0 && p[4] == 1 )
                {
                    return (p + 1);
                }
                if ( p[4] == 0 && p[5] == 1 )
                {
                    return (p + 2);
                }
            }
        }
    }

    for (end += 3; p < end; p++)
    {
        if (p[0] == 0 && p[1] == 0 && p[2] == 1)
        {
            return p;
        }
    }

    return (end + 3);
}

static int parseAvcNalus(cdx_uint8 *buf_in, cdx_uint8 **buf, int *size)
{
    cdx_uint8 *p = buf_in,*ptr_t;
    cdx_uint8 *end = p + *size;
    cdx_uint8 *nal_start, *nal_end;
    unsigned int nal_size,nal_size_b;

    ptr_t = *buf = malloc(*size + 256);
    nal_start = searchAvcStartcode(p, end);
    while (nal_start < end)
    {
        while (!*(nal_start++));
        nal_end = searchAvcStartcode(nal_start, end);
        nal_size = nal_end - nal_start;
        nal_size_b = movBswap32(nal_size);
        memcpy(ptr_t, &nal_size_b, 4);
        ptr_t += 4;
        memcpy(ptr_t, nal_start, nal_size);
        ptr_t += nal_size;
        nal_start = nal_end;
    }

    *size = ptr_t - *buf;
    return 0;
}

#define MP4_RB32(x)  ((((const cdx_uint8*)(x))[0] << 24) | \
                     (((const cdx_uint8*)(x))[1] << 16) | \
                     (((const cdx_uint8*)(x))[2] <<  8) | \
                      ((const cdx_uint8*)(x))[3])

static cdx_uint32 getSpsPpsSize(cdx_uint8 *data, cdx_int32 len)
{
    cdx_uint32 avcc_size = 0;
    if (len > 6)
    {
        /* check for h264 start code */
        if (MP4_RB32(data) == 0x00000001)
        {
            cdx_uint8 *buf=NULL, *end, *start;
            cdx_uint32 sps_size=0, pps_size=0;
            cdx_uint8 *sps=0, *pps=0;

            int ret = parseAvcNalus(data, &buf, &len);
            if (ret < 0)
            {
                logw(" ret[%d] of parseAvcNalus() < 0", ret);
                buf = NULL;
                return 0;
            }
            start = buf;
            end = buf + len;

            /* look for sps and pps */
            while (buf < end)
            {
                unsigned int size;
                cdx_uint8 nal_type;
                size = MP4_RB32(buf);
                nal_type = buf[4] & 0x1f;
                if (nal_type == 7)
                { /* SPS */
                    sps = buf + 4;
                    sps_size = size;
                }
                else if (nal_type == 8)
                { /* PPS */
                    pps = buf + 4;
                    pps_size = size;
                }
                buf += size + 4;
            }
            avcc_size = (6 + 2 + sps_size + 1 + 2 + pps_size);
            free(start);
        }
        else
        {
            avcc_size = len;
        }
    }
    return avcc_size;
}

static int writeSpsPps(int fd, cdx_uint8 *data, int len, Mp4Track *track)
{
    if (len > 6)
    {
        /* check for h264 start code */
        if (MP4_RB32(data) == 0x00000001)
        {
            cdx_uint8 *buf=NULL, *end, *start;
            cdx_uint32 sps_size=0, pps_size=0;
            cdx_uint8 *sps=0, *pps=0;

            int ret = parseAvcNalus(data, &buf, &len);
            if (ret < 0)
            {
                buf = NULL;
                return ret;
            }
            start = buf;
            end = buf + len;

            /* look for sps and pps */
            while (buf < end)
            {
                unsigned int size;
                cdx_uint8 nal_type;
                size = MP4_RB32(buf);
                nal_type = buf[4] & 0x1f;
                if (nal_type == 7)
                { /* SPS */
                    sps = buf + 4;
                    sps_size = size;
                } else if (nal_type == 8)
                { /* PPS */
                    pps = buf + 4;
                    pps_size = size;
                }
                buf += size + 4;
            }

            writeBytefd(fd, 1); /* version */
            writeBytefd(fd, sps[1]); /* profile */
            writeBytefd(fd, sps[2]); /* profile compat */
            writeBytefd(fd, sps[3]); /* level */
            /* 6 bits reserved (111111) + 2 bits nal size length - 1 (11) */
            writeBytefd(fd, 0xff);
            /* 3 bits reserved (111) + 5 bits number of sps (00001) */
            writeBytefd(fd, 0xe1);

            writeBe16fd(fd, sps_size);
            writeBufferfd(fd, (char*)sps, sps_size);
            writeBytefd(fd, 1); /* number of pps */
            writeBe16fd(fd, pps_size);
            writeBufferfd(fd, (char*)pps, pps_size);
            free(start);
        }
        else
        {
            writeBufferfd(fd, (char*)data, len);
        }
    }
    return 0;
}

static cdx_uint32 getAvccTagSize(Mp4Track *track)
{
    cdx_uint32 avcc_tag_size = 8;
    avcc_tag_size += getSpsPpsSize((cdx_uint8*)track->extra_data, track->extra_data_len);
    return avcc_tag_size;
}

static int writeAvccTag(int fd, Mp4Track *track)
{
    int ret;
    cdx_uint32 avcc_tag_size = getAvccTagSize(track);
    writeBe32fd(fd, avcc_tag_size);   /* size */
    ret = writeTagfd(fd, "avcC");
    ret = writeSpsPps(fd, (cdx_uint8*)track->extra_data, track->extra_data_len, track);
    return ret;
}

static cdx_uint32 getVideoTagSize(Mp4Track *track)
{
    cdx_uint32 video_tag_size = 16 + 4 + 12 + 18 + 32 + 4;
    if (track->tag == MOV_MKTAG('a','v','c','1'))
    {
        video_tag_size += getAvccTagSize(track);
    }

    return video_tag_size;
}

static cdx_int32 writeVideoTagStsd(int fd, Mp4Track *track)
{
    int ret;
    cdx_uint32 video_tag_size = getVideoTagSize(track);
    char compressor_name[32] = {0};

    ret = writeBe32fd(fd, video_tag_size); /* size */
    writeBufferfd(fd, (char*)&track->tag, 4); // store it byteswapped
    writeBe32fd(fd, 0); /* Reserved */
    writeBe16fd(fd, 0); /* Reserved */
    writeBe16fd(fd, 1); /* Data-reference index */

    writeBe16fd(fd, 0); /* Codec stream_writer version */
    writeBe16fd(fd, 0); /* Codec revision level (must be set to 0) */
    {
        writeBe32fd(fd, 0); /* vendor */
        writeBe32fd(fd, 0); /* temporal quality */
        writeBe32fd(fd, 0); /* spatial quality */
    }
    writeBe16fd(fd, track->enc.width); /* Video width */
    writeBe16fd(fd, track->enc.height); /* Video height */
    writeBe32fd(fd, 0x00480000); /* Horizontal resolution 72dpi */
    writeBe32fd(fd, 0x00480000); /* Vertical resolution 72dpi */
    writeBe32fd(fd, 0); /* Data size (= 0) */
    writeBe16fd(fd, 1); /* Frame count (= 1) */

    writeBufferfd(fd, compressor_name, 32);

    writeBe16fd(fd, 0x18); /* Reserved */
    writeBe16fd(fd, 0xffff); /* Reserved */
    if (track->tag == MOV_MKTAG('a','v','c','1'))
    {
        ret = writeAvccTag(fd, track);
    }

    return ret;
}

/******************************* write video info end ********************************/

/****************************** write stbl level Info *********************************/
static cdx_uint32 getStsdTagSize(Mp4Track *track)
{
    cdx_uint32 stsd_tag_size = 16;
    if (track->enc.codec_type == CODEC_TYPE_VIDEO)
    {
        stsd_tag_size += getVideoTagSize(track);
    }
    else if (track->enc.codec_type == CODEC_TYPE_AUDIO)
    {
        loge("=========== do not support audio");
        //stsd_tag_size += stsdGetAudioTagSize(track);
    }
    return stsd_tag_size;
}

static cdx_int32 writeStsdInStbl(int fd, Mp4Track *track)
{
    cdx_uint32 stsd_tag_size = getStsdTagSize(track);
    writeBe32fd(fd, stsd_tag_size); /* size */
    writeTagfd(fd, "stsd");
    writeBe32fd(fd, 0); /* version & flags */
    writeBe32fd(fd, 1); /* entry count */
    if (track->enc.codec_type == CODEC_TYPE_VIDEO)
    {
        writeVideoTagStsd(fd, track);
    }
    else if (track->enc.codec_type == CODEC_TYPE_AUDIO)
    {
        logd("====== do not support audio now");
        //stsdWriteAudioTag(fd, track);
    }

    return stsd_tag_size;
}

static cdx_uint32 getSttsTagSize(Mp4Track *track)
{
    cdx_uint32 entries = 0;
    cdx_uint32 atom_size;
    if (track->enc.codec_type == CODEC_TYPE_AUDIO)
    {
        entries = 1;
        atom_size = 16 + (entries * 8);
    }
    else
    {
        loge("=============== stts size is 1, just for test");
        entries = 1;
        atom_size = 16 + (entries * 8);

        //entries = track->stts_total_num;
        //atom_size = 16 + (entries * 8);
    }
    return atom_size;
}

// Time-to-Sample Atom
static cdx_int32 writeSttsInStbl(int fd, Mp4Track *track)
{
    SttsTable stts_entries[1];
    cdx_uint32 entries = 0;
    cdx_uint32 atom_size;
    cdx_uint32 i,j;
    cdx_uint32 pos;

    if (track->enc.codec_type == CODEC_TYPE_AUDIO)
    {
        stts_entries[0].count = track->stsz_total_num;
        // for uncompressed audio, one frame == one sample
        if (track->enc.codec_id == MUX_CODEC_ID_PCM)
        {
            stts_entries[0].duration = 1;
        }
        else
        {
            stts_entries[0].duration = track->enc.frame_size;
        }
        entries = 1;
        atom_size = 16 + (entries * 8);
        writeBe32fd(fd, atom_size); /* size */
        writeTagfd(fd, "stts");
        writeBe32fd(fd, 0); /* version & flags */
        writeBe32fd(fd, entries); /* entry count */
        for (i=0; i<entries; i++)
        {
            writeBe32fd(fd, stts_entries[i].count);
            writeBe32fd(fd, stts_entries[i].duration);
        }
    }
    else
    {
        cdx_int32 skip_first_frame = 1;

        entries = 1;
        atom_size = 16 + (entries * 8);
        writeBe32fd(fd, atom_size); /* size */
        writeTagfd(fd, "stts");
        writeBe32fd(fd, 0); /* version & flags */
        writeBe32fd(fd, entries); /* entry count */

        loge("==================== stts atom just for test");

        //write last packet duration, set it to 0??
        writeBe32fd(fd, 1000000);//count
        writeBe32fd(fd, 30);
    }

    return atom_size;
}

static cdx_uint32 getStscTagSize(Mp4Track *track)
{
    cdx_uint32 stsc_tag_size;
    //stsc_tag_size = (track->stsc_total_num*3 + 4)*4;
    loge("================ stsc just for test");
    track->stsc_total_num = 1;
    stsc_tag_size = (3 + 4)*4;
    return stsc_tag_size;
}

/* Sample to chunk atom */
static cdx_int32 writeStscInStbl(int fd, Mp4Track *track)
{
    int ret;
    cdx_uint32 i;

    cdx_uint32 stsc_tag_size = getStscTagSize(track);
    //offset_t pos = url_ftell(pb);
    writeBe32fd(fd, stsc_tag_size); /* size */
    writeTagfd(fd, "stsc");
    writeBe32fd(fd, 0); // version & flags

    writeBe32fd(fd, track->stsc_total_num); // entry count

    loge("===== stsc total num is 1, just for test");

    ret = writeBe32fd(fd, 1); // first chunk
    ret = writeBe32fd(fd, 100000); // sample num in this chunk
    ret = writeBe32fd(fd, 0x01); // sample description index

    return ret;
}

static cdx_uint32 getStszTagSize(Mp4Track *track)
{
    cdx_uint32 stsz_tag_size;

    loge("============== stsz just for test, num: 100000");
    track->stsz_total_num = 100000;
    if (track->enc.codec_id == MUX_CODEC_ID_PCM)
    {
        stsz_tag_size = 5 * 4;
    }
    else
    {
        stsz_tag_size = (track->stsz_total_num + 5) * 4;
    }
    return stsz_tag_size;

}

/* Sample size atom */
static cdx_int32 writeStszInStbl(int fd, Mp4Track *track)
{
    int ret;

    cdx_uint32 stsz_tag_size = getStszTagSize(track);
    cdx_uint32 i,j;
    writeBe32fd(fd, stsz_tag_size); /* size */
    writeTagfd(fd, "stsz");
    writeBe32fd(fd, 0); /* version & flags */
    if (track->enc.codec_id == MUX_CODEC_ID_PCM)
    {
        writeBe32fd(fd, track->enc.channels * (track->enc.bits_per_sample >> 3));// sample size
    }
    else
    {
        writeBe32fd(fd, 0); // sample size
    }
    ret = writeBe32fd(fd, track->stsz_total_num); // sample count
    if (track->enc.codec_id == MUX_CODEC_ID_PCM)
    {
        return ret;
    }

    track->stsz_offset = mp4tell64(fd);

    loge("just set it to 0, it will update in writePacket");
    mp4seek64(fd, track->stsz_total_num*4, SEEK_CUR);

    return ret;
}

static cdx_uint32 getStcoTagSize(Mp4Track *track)
{
    loge("============== set stco size 1, just for test");
    track->stco_total_num = 1;
    cdx_uint32 stco_tag_size = (track->stco_total_num + 4) * 4;
    return stco_tag_size;
}

/* Chunk offset atom */
static cdx_int32 writeStcoInStbl(int fd, Mp4Track *track)
{
    cdx_uint32 i,j;
    int ret;

    cdx_uint32 stco_tag_size = getStcoTagSize(track);
    writeBe32fd(fd, stco_tag_size); /* size */
    writeTagfd(fd, "stco");
    writeBe32fd(fd, 0); /* version & flags */

    loge("=========== for test, stco_num: %d", track->stco_total_num );
    ret = writeBe32fd(fd, track->stco_total_num); /* entry count */

    ret = writeBe32fd(fd, track->mdat_start_pos);

    return ret; //updateSize(pb, pos);
}

/************************** write minf level atom start ************************/
static cdx_uint32 getVmhdTagSize()
{
    return 0x14;
}

static cdx_int32 writeVmhdInMinf(int fd, Mp4Track *track)
{
    int ret;
    cdx_uint32 vmhd_tag_size = getVmhdTagSize();
    ret = writeBe32fd(fd, vmhd_tag_size); /* size (always 0x14) */
    ret = writeTagfd(fd, "vmhd");
    writeBe32fd(fd, 0x01); /* version & flags */
    writeBe32fd(fd, 0x0);
    ret = writeBe32fd(fd, 0x0);
    return ret;
}

static cdx_uint32 getSmhdTagSize()
{
    return 16;
}

static cdx_int32 writeSmhdInMinf(int fd, Mp4Track *track)
{
    int ret;
    cdx_uint32 smhd_tag_size = getSmhdTagSize();
    ret = writeBe32fd(fd, smhd_tag_size); /* size */
    writeTagfd(fd, "smhd");
    writeBe32fd(fd, 0); /* version & flags */

    /* reserved (balance, normally = 0) */
    /* reserved */
    ret = writeBe32fd(fd, 0);
    return ret;
}

static cdx_uint32 getStblTagSize(Mp4Track *track)
{
    cdx_uint32 stbl_tag_size = 8;
    stbl_tag_size += getStsdTagSize(track);
    stbl_tag_size += getSttsTagSize(track);
    if (track->enc.codec_type == CODEC_TYPE_VIDEO)
    {
        loge("============= donot set stss, care");
        //stbl_tag_size += stblGetStssTagSize(track);
    }

    stbl_tag_size += getStscTagSize(track);
    stbl_tag_size += getStcoTagSize(track);

    stbl_tag_size += getStszTagSize(track);

    return stbl_tag_size;
}

static cdx_int32 writeStblInMinf(int fd, Mp4Track *track)
{
    int ret;
    cdx_uint32 stbl_tag_size = getStblTagSize(track);

    ret = writeBe32fd(fd, stbl_tag_size); /* size */
    ret = writeTagfd(fd, "stbl");
    ret = writeStsdInStbl(fd, track);


    writeSttsInStbl(fd, track);
    if (track->enc.codec_type == CODEC_TYPE_VIDEO)
    {
        loge("=========== donot set stss");
        //stblWriteStssTag(fd, track);
    }
    ret = writeStscInStbl(fd, track);

    ret = writeStcoInStbl(fd, track);

    ret = writeStszInStbl(fd, track);


    return ret;
}

/************************** write minf level atom end **************************/


/***************************** write media level atom*****************************/
static cdx_uint32 getMdhdTagSize()
{
    return 0x20;
}

static cdx_int32 writeMdhdInMdia(int fd, Mp4Track *track)
{
    int ret;
    cdx_uint32 mdhd_tag_size = getMdhdTagSize();
    ret = writeBe32fd(fd, mdhd_tag_size); /* size */
    writeTagfd(fd, "mdhd");
    writeBytefd(fd, 0);
    writeBe24fd(fd, 0); /* flags */

    writeBe32fd(fd, track->time); /* creation time */
    writeBe32fd(fd, track->time); /* modification time */
    writeBe32fd(fd, track->track_timescale); /* time scale (sample rate for audio) */
    writeBe32fd(fd, track->track_duration); /* duration */
    writeBe16fd(fd, /*track->language*/0); /* language */
    ret = writeBe16fd(fd, 0); /* reserved (quality) */

    return ret;
}

static cdx_uint32 getHdlrTagSize(Mp4Track *track)
{
    cdx_uint32 hdlr_tag_size = 32 + 1;

    if (!track)
    { /* no media --> data handler */
        hdlr_tag_size += strlen("DataHandler");
    }
    else
    {
        if (track->enc.codec_type == CODEC_TYPE_VIDEO)
        {
            hdlr_tag_size += strlen("VideoHandler");
        }
        else
        {
            hdlr_tag_size += strlen("SoundHandler");
        }
    }
    return hdlr_tag_size;
}

static cdx_int32 writeHdlrInMdia(int fd, Mp4Track *track)
{
    int ret;
    char *descr, *hdlr, *hdlr_type;

    if (!track)
    { /* no media --> data handler */
        hdlr = "dhlr";
        hdlr_type = "url ";
        descr = "DataHandler";
    }
    else
    {
        hdlr = "\0\0\0\0";
        if (track->enc.codec_type == CODEC_TYPE_VIDEO)
        {
            hdlr_type = "vide";
            descr = "VideoHandler";
        }
        else
        {
            hdlr_type = "soun";
            descr = "SoundHandler";
        }
    }
    cdx_uint32 hdlr_tag_size = getHdlrTagSize(track);
    writeBe32fd(fd, hdlr_tag_size); /* size */
    writeTagfd(fd, "hdlr");
    writeBe32fd(fd, 0); /* Version & flags */
    writeBufferfd(fd, (char*)hdlr, 4); /* handler */
    writeTagfd(fd, hdlr_type); /* handler type */
    writeBe32fd(fd ,0); /* reserved */
    writeBe32fd(fd ,0); /* reserved */
    writeBe32fd(fd ,0); /* reserved */
    writeBytefd(fd, strlen((const char *)descr)); /* string counter */

    /* handler description */
    ret = writeBufferfd(fd, (char*)descr, strlen((const char *)descr));

    return ret;
}

static cdx_uint32 getMinfTagSize(Mp4Track *track)
{
    cdx_uint32 minf_tag_size = 8;
    if (track->enc.codec_type == CODEC_TYPE_VIDEO)
    {
        minf_tag_size += getVmhdTagSize();
    }
    else
    {
        minf_tag_size += getSmhdTagSize();
    }

    //minf_tag_size += minfGetDinfTagSize();
    minf_tag_size += getStblTagSize(track);
    return minf_tag_size;
}

static cdx_int32 writeMinfInMdia(int fd, Mp4Track *track)
{
    cdx_uint32 minf_tag_size = getMinfTagSize(track);
    writeBe32fd(fd, minf_tag_size); /* size */
    writeTagfd(fd, "minf");
    if (track->enc.codec_type == CODEC_TYPE_VIDEO)
    {
        writeVmhdInMinf(fd, track);
    }
    else
    {
        writeSmhdInMinf(fd, track);
    }

    // do not need this atom
    //minfWriteDinfTag(fd, track);
    writeStblInMinf(fd, track);

    return minf_tag_size;
}

/***************************** write media level atom*****************************/

/***************************** write track level atom*****************************/
static cdx_int32 rescaleRnd(cdx_int64 a, cdx_int64 b, cdx_int64 c)
{
    return (a * b + c - 1) / c;
}

static cdx_uint32 getTkhdTagSize()
{
    return 0x5c;
}

/*
*    write tkhd atom in track,
*    we can write it in starttime
*/
static cdx_int32 writeTkhdInTrak(int fd, Mp4Track *track)
{
    int ret;
    cdx_int64 duration = rescaleRnd(track->track_duration,
                                      GLOBAL_TIMESCALE, track->track_timescale);
    cdx_int32 version = 0;
    cdx_uint32 tkhd_tag_size = getTkhdTagSize();
    writeBe32fd(fd, tkhd_tag_size); /* size */
    writeTagfd(fd, "tkhd");
    writeBytefd(fd, version);
    writeBe24fd(fd, 0xf); /* flags (track enabled) */

    writeBe32fd(fd, track->time); /* creation time */
    writeBe32fd(fd, track->time); /* modification time */

    writeBe32fd(fd, track->track_id); /* track-id */
    writeBe32fd(fd, 0); /* reserved */
    writeBe32fd(fd, duration);

    writeBe32fd(fd, 0); /* reserved */
    writeBe32fd(fd, 0); /* reserved */
    writeBe32fd(fd, 0x0); /* reserved (Layer & Alternate group) */
    /* Volume, only for audio */
    if (track->enc.codec_type == CODEC_TYPE_AUDIO)
    {
        writeBe16fd(fd, 0x0100);
    }
    else
    {
        writeBe16fd(fd, 0);
    }
    writeBe16fd(fd, 0); /* reserved */

    {
        int degrees = track->enc.rotate_degree;
        cdx_uint32 a = 0x00010000;
        cdx_uint32 b = 0;
        cdx_uint32 c = 0;
        cdx_uint32 d = 0x00010000;
        switch (degrees)
        {
            case 0:
                break;
            case 90:
                a = 0;
                b = 0x00010000;
                c = 0xFFFF0000;
                d = 0;
                break;
            case 180:
                a = 0xFFFF0000;
                d = 0xFFFF0000;
                break;
            case 270:
                a = 0;
                b = 0xFFFF0000;
                c = 0x00010000;
                d = 0;
                break;
            default:
                loge("Should never reach this unknown rotation");
                break;
        }

        writeBe32fd(fd, a);           // a
        writeBe32fd(fd, b);           // b
        writeBe32fd(fd, 0);           // u
        writeBe32fd(fd, c);           // c
        writeBe32fd(fd, d);           // d
        writeBe32fd(fd, 0);           // v
        writeBe32fd(fd, 0);           // x
        writeBe32fd(fd, 0);           // y
        writeBe32fd(fd, 0x40000000);  // w
    }

    /* Track width and height, for visual only */
    if (track->enc.codec_type == CODEC_TYPE_VIDEO)
    {
        ret = writeBe32fd(fd, track->enc.width*0x10000);
        ret = writeBe32fd(fd, track->enc.height*0x10000);
    }
    else
    {
        ret = writeBe32fd(fd, 0);
        ret = writeBe32fd(fd, 0);
    }
    return ret;
}


static cdx_uint32 getMdiaTagSize(Mp4Track *track)
{
    loge("not complete yet \n");
    cdx_uint32 mdia_tag_size = 8;
    mdia_tag_size += getMdhdTagSize();
    mdia_tag_size += getHdlrTagSize(track);
    mdia_tag_size += getMinfTagSize(track);
    return mdia_tag_size;
}

static cdx_int32 writeMdiaInTrak(int fd, Mp4Track *track)
{
    int ret;
    cdx_uint32 mdia_tag_size = getMdiaTagSize(track);

    writeBe32fd(fd, mdia_tag_size); /* size */
    writeTagfd(fd, "mdia");
    ret = writeMdhdInMdia(fd, track);
    ret = writeHdlrInMdia(fd, track);
    ret = writeMinfInMdia(fd, track);

    return ret;
}
/********************* write track level atom end *************************/

static cdx_uint32 getTrakTagSize(Mp4Track *track)
{
    cdx_uint32 trak_tag_size = 8;
    trak_tag_size += getTkhdTagSize();
    trak_tag_size += getMdiaTagSize(track);
    return trak_tag_size;
}

static cdx_int32 writeTrakTag(int fd, Mp4Track *track)
{
    cdx_uint32 trak_tag_size = getTrakTagSize(track);

    writeBe32fd(fd, trak_tag_size); /* size */
    writeTagfd(fd, "trak");
    writeTkhdInTrak(fd, track);

    writeMdiaInTrak(fd, track);

    return trak_tag_size;
}

static cdx_uint32 getMoovTagSize(MuxMOVCtx *mov)
{
    cdx_int32 i;
    cdx_uint32 size = 0;
    size += 8;  //size, "moov" tag,
    if (mov->mov_geo_available)
    {
        size += getUdtaTagSize();
    }
    size += getMvhdTagSize();
    for (i = 0; i < MAX_STREAMS_IN_MP4_FILE/*mov->nb_streams*/; i++)
    {
        if (mov->tracks[i])
        {
            if (mov->tracks[i]->stsz_total_num> 0)
            {
                size += getTrakTagSize(mov->tracks[i]);
            }
        }
    }
    return size;
}

static cdx_int32 writeMoovTag(Mp4exMuxCtx *mp4)
{
    cdx_int32 i;
    cdx_int32 moov_size;
    int ret;
    MuxMOVCtx *data = mp4->data_ctx;

    logd("==== writeMoovTag");
    moov_size = getMoovTagSize(data);
    logd("+++++ moov_size: %d", moov_size);

    ret = mp4seek64(mp4->fd_header, 28, SEEK_SET);
    logd("====seek ret: %d, errno(%d), fd(%d)", ret, errno, mp4->fd_header);

    ret = writeBe32fd(mp4->fd_header, moov_size);
    logd("==== ret: %d, errno(%d), fd(%d)", ret, errno, mp4->fd_header);
    ret = writeTagfd(mp4->fd_header, "moov");

    data->mov_timescale = GLOBAL_TIMESCALE;

    if (data->mov_geo_available)
    {
        writeUdtaTag(mp4->fd_header, data);
    }

    for (i = 0; i < MAX_STREAMS_IN_MP4_FILE/*mov->nb_streams*/; i++)
    {
        if (data->tracks[i])
        {
             data->tracks[i]->time = data->create_time;
             data->tracks[i]->track_id = i+1;
//             data->tracks[i]->mov = data;
             data->tracks[i]->stream_type = i;
        }
    }

    writeMvhdTag(mp4->fd_header, data);
    for (i = 0; i < MAX_STREAMS_IN_MP4_FILE/*mov->nb_streams*/; i++)
    {
        // only write video track in start time
        if (data->tracks[i] && data->tracks[i]->enc.codec_type == CODEC_TYPE_VIDEO)
        {
            if (data->tracks[i]->stsz_total_num> 0)
            {
                 writeTrakTag(mp4->fd_header, data->tracks[i]);
            }
        }
    }

    if (moov_size + 28 + 8 <= MOV_HEADER_RESERVE_SIZE)
    {
        ret = mp4seek64(mp4->fd_header, moov_size + 28, SEEK_SET);
        writeFreeTag(mp4->fd_header, MOV_HEADER_RESERVE_SIZE - moov_size - 28);
    }

    return moov_size;
}


static cdx_int32 setCodecTag(Mp4Track *track)
{
    cdx_int32 tag = track->enc.codec_tag;

    switch(track->enc.codec_id)
    {
        case MUX_CODEC_ID_H264:
        {
            tag = MP4MKTAG('a','v','c','1');
            break;
        }
        case MUX_CODEC_ID_MPEG4:
        {
            tag = MP4MKTAG('m','p','4','v');
            break;
        }
        case MUX_CODEC_ID_AAC:
        {
            tag = MP4MKTAG('m','p','4','a');
            break;
        }
        case MUX_CODEC_ID_PCM:
        {
            tag = MP4MKTAG('s','o','w','t');
            break;
        }
        case MUX_CODEC_ID_ADPCM:
        {
            tag = MP4MKTAG('m','s',0x00,0x11);
            break;
        }
        case MUX_CODEC_ID_MJPEG:
        {
            tag = MP4MKTAG('m','j','p','a');
            break;
        }
        default:
        {
            break;
        }
    }

    return tag;
}


static int setVideoTrack(MuxMOVCtx *mov, MuxerVideoStreamInfoT *pVideoInfo)
{
    Mp4Track *trk = NULL;
    MuxAVCodecCtx *pVideo = NULL;

    trk = (Mp4Track*)malloc(sizeof(Mp4Track));
    if(trk == NULL)
    {
        loge("tracks[CODEC_TYPE_VIDEO] malloc failed\n");
        return -1;
    }
    memset(trk, 0, sizeof(Mp4Track));

    trk->track_timescale = GLOBAL_TIMESCALE;

    pVideo = &trk->enc;
    pVideo->codec_type = CODEC_TYPE_VIDEO;
    pVideo->height = pVideoInfo->nHeight;
    pVideo->width = pVideoInfo->nWidth;
    pVideo->frame_rate = pVideoInfo->nFrameRate;
    pVideo->rotate_degree = pVideoInfo->nRotateDegree;

    if (pVideoInfo->eCodeType == VENC_CODEC_H264)
    {
        pVideo->codec_id = MUX_CODEC_ID_H264;
    }
    else if (pVideoInfo->eCodeType == VENC_CODEC_JPEG)
    {
        pVideo->codec_id = MUX_CODEC_ID_MJPEG;
    }
    else
    {
        loge("unlown codectype(%d)\n", pVideoInfo->eCodeType);
    }

    mov->tracks[CODEC_TYPE_VIDEO] = trk;
    mov->create_time = pVideoInfo->nCreatTime;

    return 0;
}

static int setAudioTrack(MuxMOVCtx *mov, MuxerAudioStreamInfoT *pAudioInfo)
{
    Mp4Track *trk = NULL;
    MuxAVCodecCtx *pAudio = NULL;

    trk = (Mp4Track*)malloc(sizeof(Mp4Track));
    if(trk == NULL)
    {
        loge("tracks[CODEC_TYPE_AUDIO] malloc failed\n");
        return -1;
    }
    memset(trk, 0, sizeof(Mp4Track));

    pAudio = &trk->enc;
    pAudio->codec_type = CODEC_TYPE_AUDIO;
    pAudio->channels = pAudioInfo->nChannelNum;
    pAudio->bits_per_sample = pAudioInfo->nBitsPerSample;
    pAudio->frame_size = pAudioInfo->nSampleCntPerFrame;
    pAudio->sample_rate = pAudioInfo->nSampleRate;

    if(pAudioInfo->eCodecFormat == AUDIO_ENCODER_PCM_TYPE)
    {
        pAudio->codec_id = MUX_CODEC_ID_PCM;
    }
    else if(pAudioInfo->eCodecFormat == AUDIO_ENCODER_AAC_TYPE)
    {
        pAudio->codec_id = MUX_CODEC_ID_AAC;
    }
    else if(pAudioInfo->eCodecFormat == AUDIO_ENCODER_MP3_TYPE)
    {
        pAudio->codec_id = MUX_CODEC_ID_MP3;
    }
    else
    {
        loge("unlown codectype(%d)", pAudio->codec_id );
    }

    trk->track_timescale = pAudio->sample_rate;
    mov->tracks[CODEC_TYPE_AUDIO] = trk;

    return 0;
}

static int __Mp4exMuxerSetMediaInfo(CdxMuxerT *mux, CdxMuxerMediaInfoT *pMediaInfo)
{
    Mp4exMuxCtx *impl = (Mp4exMuxCtx*)mux;
    MuxMOVCtx* mov = impl->data_ctx;

    if(pMediaInfo == NULL)
    {
        loge("mediainfo is NULL");
        return -1;
    }

    if(pMediaInfo->audioNum > 0)
    {
       if(setAudioTrack(mov, &pMediaInfo->audio))
            return -1;
    }

    if(pMediaInfo->videoNum > 0)
    {
        if(setVideoTrack(mov, &pMediaInfo->video))
            return -1;
    }

    return 0;
}

static int __Mp4exMuxerWriteHeader(CdxMuxerT *mux)
{
    Mp4exMuxCtx *mp4Mux = (Mp4exMuxCtx*)mux;
    MuxMOVCtx* mov = mp4Mux->data_ctx;
    CdxWriterT* writer = mp4Mux->pWriter;
    Mp4Track* track = NULL;
    int i;
    int ret;

    logd("=== WriteHeader");
    ret = writeFtypTag(writer);
    if(ret < 0)
    {
        loge("wrtie ftyp tag failed");
        return -1;
    }

    ret = writeMdatTag(writer, mov);

    for(i=0; i<MAX_STREAMS_IN_MP4_FILE; i++)
    {
        track = mov->tracks[i];
        if(track)
        {
            track->tag = setCodecTag(track);
            track->mdat_start_pos = mov->mdat_start_pos;
            track->stsz_total_num = 100000;
        }
    }

    mp4seek64(mp4Mux->fd_header, 28, SEEK_SET);

    writeMoovTag(mp4Mux);

    for(i=0; i<MAX_STREAMS_IN_MP4_FILE; i++)
    {
        track = mov->tracks[i];
        if(track && track->enc.codec_type == CODEC_TYPE_VIDEO)
        {
            mp4seek64(mp4Mux->fd_header, track->stsz_offset, SEEK_SET);
            logd("====== stsz_offset: %llx, fd tell: %llx", track->stsz_offset, mp4tell64(mp4Mux->fd_header));
        }
    }

    return ret;
}

static int __Mp4exMuxerWriteExtraData(CdxMuxerT *mux, unsigned char *vos_data, int vos_len, int idx)
{
    Mp4exMuxCtx *mp4Mux = (Mp4exMuxCtx*)mux;
    MuxMOVCtx* mov = (MuxMOVCtx*)mp4Mux->data_ctx;
    Mp4Track* trk = mov->tracks[idx];
    if(vos_len)
    {
        trk->extra_data = (char*)malloc(vos_len);
        trk->extra_data_len = vos_len;
        memcpy(trk->extra_data, vos_data, vos_len);
    }
    else
    {
        trk->extra_data = NULL;
        trk->extra_data_len = 0;
    }

    return 0;
}

static int __Mp4exMuxerWritePacket(CdxMuxerT *mux, CdxMuxerPacketT *packet)
{
    int ret;
    Mp4exMuxCtx *mp4Mux = (Mp4exMuxCtx*)mux;
    ret = CdxWriterWrite(mp4Mux->pWriter, packet->buf, packet->buflen);
    if(ret < 0)
    {
        return ret;
    }

    logd("=== writePacket: %d", packet->buflen);
    ret = writeBe32fd(mp4Mux->fd_header, (cdx_uint32)packet->buflen);

    return ret;
}

static int __Mp4exMuxerWriteTrailer(CdxMuxerT *mux)
{
    return 0;
}

static int __Mp4exMuxerControl(CdxMuxerT *mux, int u_cmd, void *p_param)
{
    switch (u_cmd)
    {

    }
    return 0;
}

static int __Mp4exMuxerClose(CdxMuxerT *mux)
{
    int i;
    Mp4Track* track = NULL;
    Mp4exMuxCtx *mp4Mux = (Mp4exMuxCtx*)mux;
    MuxMOVCtx* mov = mp4Mux->data_ctx;
    if(mp4Mux)
    {
        if(mp4Mux->fd_header)
            mp4close64(mp4Mux->fd_header);

        for(i=0; i<MAX_STREAMS_IN_MP4_FILE; i++)
        {
            track = mov->tracks[i];
            if(track)
            {
                if(track->extra_data)
                    free(track->extra_data);
            }
        }
        free(mp4Mux);
    }

    return 0;
}

static struct CdxMuxerOpsS mp4exMuxerOps =
{
    .writeExtraData  = __Mp4exMuxerWriteExtraData,
    .writeHeader     = __Mp4exMuxerWriteHeader,
    .writePacket     = __Mp4exMuxerWritePacket,
    .writeTrailer    = __Mp4exMuxerWriteTrailer,
    .control         = __Mp4exMuxerControl,
    .setMediaInfo    = __Mp4exMuxerSetMediaInfo,
    .close           = __Mp4exMuxerClose
};

CdxMuxerT* __CdxMp4exMuxerOpen(CdxWriterT *pWriter)
{
    Mp4exMuxCtx *mp4Mux;

    logd("==== __CdxMp4exMuxerOpen");
    mp4Mux = (Mp4exMuxCtx*)malloc(sizeof(Mp4exMuxCtx));
    if(!mp4Mux)
    {
        return NULL;
    }
    memset(mp4Mux, 0x00, sizeof(Mp4exMuxCtx));

    mp4Mux->pWriter = pWriter;

    if(pWriter->uri == NULL)
    {
        loge("cannot get uri;");
        return NULL;
    }

    mp4Mux->data_ctx = (MuxMOVCtx*)malloc(sizeof(MuxMOVCtx));
    if(!mp4Mux->data_ctx)
    {
        free(mp4Mux);
        return NULL;
    }
    memset(mp4Mux->data_ctx, 0, sizeof(MuxMOVCtx));

    logd("==uri: %s", pWriter->uri);
    mp4Mux->fd_header = mp4open64(pWriter->uri);
    if(mp4Mux->fd_header < 0)
    {
        free(mp4Mux);
        loge("=== open file failed, errno(%d)", errno);
        return NULL;
    }

    mp4Mux->muxInfo.ops = &mp4exMuxerOps;

    return &mp4Mux->muxInfo;
}

CdxMuxerCreatorT mp4exMuxerCtor =
{
    .create = __CdxMp4exMuxerOpen
};

