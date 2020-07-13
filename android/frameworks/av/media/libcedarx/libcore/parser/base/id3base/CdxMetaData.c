/*
 * Copyright (c) 2008-2017 Allwinner Technology Co. Ltd.
 * All rights reserved.
 *
 * File : CdxMetaData.c
 * Description : Options of Meta data extracting...
 * History :
 *
 */

#include "CdxMetaData.h"

#ifdef  LOG_TAG
#undef  LOG_TAG
#endif

#define LOG_TAG "CdxMetaData"

void SetMetaData(CdxMediaInfoT *mediaInfo, META_IDX idx, const char* content)
{
    if(!strcmp(content, "null"))
    {
        CDX_LOGD("do nothing.... idx : %d", idx);
        return ;
    }
    switch(idx)
    {
        case ARTIST:
            CDX_LOGD("ARTIST : %s", content);
            CDX_LOGD("Strlen : %d", strlen(content));
            memcpy(mediaInfo->artist,content,strlen(content));
            mediaInfo->artistsz = strlen(content);
            break;
        case ALBUM:
            CDX_LOGD("ALBUM : %s", content);
            CDX_LOGD("Strlen : %d", strlen(content));
            memcpy(mediaInfo->album,content,strlen(content));
            mediaInfo->albumsz = strlen(content);
            break;
        case ALBUM_ARTIST:
            CDX_LOGD("ALBUM_ARTIST : %s", content);
            CDX_LOGD("Strlen : %d", strlen(content));
            memcpy(mediaInfo->albumArtist,content,strlen(content));
            mediaInfo->albumArtistsz = strlen(content);
            break;
        case TITLE:
            CDX_LOGD("TITLE : %s", content);
            CDX_LOGD("Strlen : %d", strlen(content));
            memcpy(mediaInfo->title,content,strlen(content));
            mediaInfo->titlesz = strlen(content);
            break;
        case COMPOSER:
            CDX_LOGD("COMPOSER : %s", content);
            CDX_LOGD("Strlen : %d", strlen(content));
            memcpy(mediaInfo->composer,content,strlen(content));
            mediaInfo->composersz = strlen(content);
            break;
        case GENRE:
            CDX_LOGD("GENRE : %s", content);
            CDX_LOGD("Strlen : %d", strlen(content));
            memcpy(mediaInfo->genre,content,strlen(content));
            mediaInfo->genresz = strlen(content);
            break;
        case YEAR:
            CDX_LOGD("YEAR : %s", content);
            CDX_LOGD("Strlen : %d", strlen(content));
            memcpy(mediaInfo->year,content,strlen(content));
            mediaInfo->yearsz = strlen(content);
            break;
        case AUTHOR:
            CDX_LOGD("AUTHOR : %s", content);
            CDX_LOGD("Strlen : %d", strlen(content));
            memcpy(mediaInfo->author,content,strlen(content));
            mediaInfo->authorsz = strlen(content);
            break;
        case CDTRACKNUMBER:
            CDX_LOGD("CDTRACKNUMBER : %s", content);
            CDX_LOGD("Strlen : %d", strlen(content));
            memcpy(mediaInfo->cdTrackNumber,content,strlen(content));
            mediaInfo->cdTrackNumbersz = strlen(content);
            break;
        case DISCNUMBER:
            CDX_LOGD("To fixme .... DISCNUMBER");
            break;
        case COMPILATION:
            CDX_LOGD("COMPILATION : %s", content);
            CDX_LOGD("Strlen : %d", strlen(content));
            memcpy(mediaInfo->compilation,content,strlen(content));
            mediaInfo->compilationsz = strlen(content);
            break;
        case DATE:
            CDX_LOGD("DATE : %s", content);
            CDX_LOGD("Strlen : %d", strlen(content));
            memcpy(mediaInfo->date,content,strlen(content));
            mediaInfo->datesz = strlen(content);
            break;
        default:
            CDX_LOGD("line : %d, content : %s", __LINE__, content);
            break;
    }
}

