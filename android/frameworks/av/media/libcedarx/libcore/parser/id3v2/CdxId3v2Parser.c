/*
* Copyright (c) 2008-2016 Allwinner Technology Co. Ltd.
* All rights reserved.
*
* File : CdxId3Parser.c
* Description :
* History :
*   Author  : Khan <chengkan@allwinnertech.com>
*   Date    : 2014/12/08
*   Comment : 创建初始版本，实现 ID3_tag 的解析功能
*/

#include <CdxTypes.h>
#include <CdxParser.h>
#include <CdxStream.h>
#include <CdxMemory.h>
#include <CdxId3v2Parser.h>

#define ID3V2TAG "ID3"
#define METEDATAKEY    "uri"

static int Id3v2Init(CdxParserT *id3_impl)
{
    cdx_int32 ret = 0;
    cdx_int32 offset=0;
    cdx_int32 tmpFd = 0;

    struct Id3v2ParserImplS *impl = NULL;

    impl = (Id3v2ParserImplS*)id3_impl;

    impl->id3v2 = GenerateId3(impl->stream, NULL, 0, ktrue);
    if(!impl->id3v2)
    {
        CDX_LOGW("get id3 handle fail!");
        goto OPENFAILURE;
    }

    if(impl->id3v2)
        offset = impl->id3v2->mRawSize;

    if(impl->id3v2->mIsValid)
        CDX_LOGD("Parser id3 success");
    else
        CDX_LOGD("Id3_v2 parser get fail? Joking me....");

    CDX_LOGD("Skip to post id3 location : %d!", offset);
    CDX_LOGD("Actually offset %lld", CdxStreamTell(impl->stream));
    /*
    ret = CdxStreamSeek(impl->stream, offset, SEEK_CUR);
    if(ret==-1){
        CDX_LOGE("Skip id3 byte error!");
        goto OPENFAILURE;
    }*/
    impl->file_offset += offset;

    //reopen parser
    memset(&impl->cdxDataSource, 0x00, sizeof(impl->cdxDataSource));
    CdxStreamGetMetaData(impl->stream,METEDATAKEY,(void **)&impl->keyinfo);
    if(strncmp(impl->keyinfo, "file://", 7) == 0 || strncmp(impl->keyinfo, "fd://", 5) == 0)
    {
        ret = sscanf(impl->keyinfo, "fd://%d?offset=%lld&length=%lld", &tmpFd, &impl->fdoffset,
                     &impl->file_size);
        ret = sprintf(impl->newurl, "fd://%d?offset=%lld&length=%lld", tmpFd,
                      impl->fdoffset+impl->file_offset, impl->file_size - impl->file_offset);
    }
    else if(strncmp(impl->keyinfo, "http://", 7) == 0 || strncmp(impl->keyinfo, "https://", 8)== 0)
    {
        strcpy(impl->newurl, impl->keyinfo);
        impl->cdxDataSource.offset = impl->file_offset;
    }

    logd("impl->newurl(%s), impl->file_offset(%lld)", impl->newurl, impl->file_offset);
    impl->cdxDataSource.uri = impl->newurl;

    CdxStreamProbeDataT *probeData = CdxStreamGetProbeData(impl->stream);
    if (probeData->len > impl->file_offset)
    {
        probeData->len -= impl->file_offset;
        memmove(probeData->buf, probeData->buf + impl->file_offset, probeData->len);
        ret = CdxParserOpen(impl->stream, NO_NEED_DURATION, &impl->lock,
            &impl->forceStop, &impl->child, NULL);
        if (ret == 0)
        {
            impl->shareStreamWithChild = 1;
            impl->mErrno = PSR_OK;
            return 0;
        }
        else if (impl->child)
        {
            CdxParserClose(impl->child);
        }
    }

    ret = CdxParserPrepare(&impl->cdxDataSource, NO_NEED_DURATION, &impl->lock, &impl->forceStop,
        &impl->child, &impl->childStream, NULL, NULL);
    if(ret < 0)
    {
        CDX_LOGE("CdxParserPrepare fail");
        goto OPENFAILURE;
    }

    impl->mErrno = PSR_OK;
    return 0;
OPENFAILURE:
    CDX_LOGE("Id3OpenThread fail!!!");
    impl->mErrno = PSR_OPEN_FAIL;
    return -1;
}

static cdx_int32 __Id3v2ParserControl(CdxParserT *parser, cdx_int32 cmd, void *param)
{
    struct Id3v2ParserImplS *impl = NULL;
    impl = (Id3v2ParserImplS*)parser;
    (void)param;
    if(!impl->child)
        return CDX_SUCCESS;
    switch (cmd)
    {
        case CDX_PSR_CMD_DISABLE_AUDIO:
        case CDX_PSR_CMD_DISABLE_VIDEO:
        case CDX_PSR_CMD_SWITCH_AUDIO:
            break;
        case CDX_PSR_CMD_SET_FORCESTOP:
            CdxParserForceStop(impl->child);
          break;
        case CDX_PSR_CMD_CLR_FORCESTOP:
            CdxParserClrForceStop(impl->child);
            break;
        default :
            CDX_LOGW("not implement...(%d)", cmd);
            break;
    }
    impl->flags = cmd;
    return CDX_SUCCESS;
}

static cdx_int32 __Id3v2ParserPrefetch(CdxParserT *parser, CdxPacketT *pkt)
{
    cdx_int32 ret = CDX_FAILURE;
    struct Id3v2ParserImplS *impl = NULL;
    impl = CdxContainerOf(parser, struct Id3v2ParserImplS, base);
    if(!impl->child)
        return ret;
    ret = CdxParserPrefetch(impl->child,pkt);
    impl->mErrno = CdxParserGetStatus(impl->child);
    return ret;
}

static cdx_int32 __Id3v2ParserRead(CdxParserT *parser, CdxPacketT *pkt)
{
    cdx_int32 ret = CDX_FAILURE;
    struct Id3v2ParserImplS *impl = NULL;
    impl = CdxContainerOf(parser, struct Id3v2ParserImplS, base);
    if(!impl->child)
        return ret;
    ret = CdxParserRead(impl->child,pkt);
    impl->mErrno = CdxParserGetStatus(impl->child);
    return ret;
}

static cdx_int32 __Id3v2ParserGetMediaInfo(CdxParserT *parser, CdxMediaInfoT *mediaInfo)
{
    cdx_int32 ret = CDX_FAILURE;
    struct Id3v2ParserImplS *impl = NULL;
    Iterator* it = NULL;
    impl = CdxContainerOf(parser, struct Id3v2ParserImplS, base);

    if(impl->id3v2 && impl->id3v2->mIsValid)
    {
        CDX_LOGD("id3v2 has vaild parsed...");
        mediaInfo->id3v2HadParsed = 1;
        Id3BaseGetMetaData(mediaInfo, impl->id3v2);
        Id3BaseExtraAlbumPic(mediaInfo, impl->id3v2);
    }
    if(!impl->child)
        return ret;
    ret = CdxParserGetMediaInfo(impl->child,mediaInfo);
    impl->mErrno = CdxParserGetStatus(impl->child);
    return ret;
}

static cdx_int32 __Id3v2ParserSeekTo(CdxParserT *parser, cdx_int64 timeUs)
{
    cdx_int32 ret = CDX_FAILURE;
    struct Id3v2ParserImplS *impl = NULL;
    impl = CdxContainerOf(parser, struct Id3v2ParserImplS, base);
    if(!impl->child)
        return ret;
    ret = CdxParserSeekTo(impl->child,timeUs);
    impl->mErrno = CdxParserGetStatus(impl->child);
    return ret;
}

static cdx_uint32 __Id3v2ParserAttribute(CdxParserT *parser)
{
    struct Id3v2ParserImplS *impl = NULL;
    impl = CdxContainerOf(parser, struct Id3v2ParserImplS, base);
    return CdxParserAttribute(impl->child);
}
#if 0
static cdx_int32 __Id3ParserForceStop(CdxParserT *parser)
{
    struct Id3v2ParserImplS *impl = NULL;
    impl = CdxContainerOf(parser, struct Id3v2ParserImplS, base);
    return CdxParserForceStop(impl->child);
}
#endif
static cdx_int32 __Id3v2ParserGetStatus(CdxParserT *parser)
{
    struct Id3v2ParserImplS *impl = NULL;
    impl = CdxContainerOf(parser, struct Id3v2ParserImplS, base);

    return impl->child?CdxParserGetStatus(impl->child):impl->mErrno;
}

static cdx_int32 __Id3v2ParserClose(CdxParserT *parser)
{
    struct Id3v2ParserImplS *impl = NULL;
    impl = CdxContainerOf(parser, struct Id3v2ParserImplS, base);
#if 0
    struct Id3Pic* thiz = NULL,*tmp = NULL;
    thiz = impl->pAlbumArt;
    impl->pAlbumArt = NULL;
    while(thiz != NULL)
    {
        if(thiz->addr!=NULL)
        {
            free(thiz->addr);
            CDX_LOGE("FREE PIC");
            thiz->addr = NULL;
        }
        tmp = thiz;
        thiz = thiz->father;
        if(tmp!=NULL)
        {
            free(tmp);
            impl->pAlbumArtid--;
            CDX_LOGE("FREE PIC COMPLETE impl->pAlbumArtid:%d",impl->pAlbumArtid);
            tmp = NULL;
        }
    }
#endif
    if(impl->child)
        CdxParserClose(impl->child);
    pthread_mutex_destroy(&impl->lock);
    if (impl->shareStreamWithChild == 0)
        CdxStreamClose(impl->stream);
    EraseId3(&impl->id3v2);
    CdxFree(impl);
    return CDX_SUCCESS;
}

static struct CdxParserOpsS id3v2ParserOps =
{
    .control = __Id3v2ParserControl,
    .prefetch = __Id3v2ParserPrefetch,
    .read = __Id3v2ParserRead,
    .getMediaInfo = __Id3v2ParserGetMediaInfo,
    .seekTo = __Id3v2ParserSeekTo,
    .attribute = __Id3v2ParserAttribute,
    .getStatus = __Id3v2ParserGetStatus,
    .close = __Id3v2ParserClose,
    .init = Id3v2Init
};

static cdx_uint32 __Id3v2ParserProbe(CdxStreamProbeDataT *probeData)
{
    CDX_CHECK(probeData);
    if(probeData->len < 10)
    {
        CDX_LOGE("Probe ID3_header data is not enough.");
        return 0;
    }

    if(memcmp(probeData->buf,ID3V2TAG,3))
    {
        CDX_LOGE("id3 probe failed.");
        return 0;
    }
    return 100;
}

static CdxParserT *__Id3v2ParserOpen(CdxStreamT *stream, cdx_uint32 flags)
{
    cdx_int32 ret = 0;
    struct Id3v2ParserImplS *impl;
    impl = CdxMalloc(sizeof(*impl));

    memset(impl, 0x00, sizeof(*impl));
    ret = pthread_mutex_init(&impl->lock, NULL);
    CDX_FORCE_CHECK(ret == 0);
    impl->stream = stream;
    impl->base.ops = &id3v2ParserOps;
    (void)flags;
    //ret = pthread_create(&impl->openTid, NULL, Id3OpenThread, (void*)impl);
    //CDX_FORCE_CHECK(!ret);
    impl->mErrno = PSR_INVALID;

    return &impl->base;
}

struct CdxParserCreatorS id3v2ParserCtor =
{
    .probe = __Id3v2ParserProbe,
    .create = __Id3v2ParserOpen
};
