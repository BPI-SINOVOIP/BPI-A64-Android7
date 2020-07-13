/*
* Copyright (c) 2008-2017 Allwinner Technology Co. Ltd.
* All rights reserved.
*
* File : CdxMetaData.h
* Description :
* History :
*   Author  : Khan <chengkan@allwinnertech.com>
*   Date    : 2017/3/7
*/
#include "BaseUtils.h"

#ifndef CDXMETADATA_H
#define CDXMETADATA_H

typedef enum _key_string KeyString;
typedef struct _Map Map;
typedef enum _META_TITLE_KINDS META_IDX;

enum _META_TITLE_KINDS
{
    ARTIST = 0,
    ALBUM,
    ALBUM_ARTIST,
    TITLE,
    COMPOSER,
    GENRE,
    YEAR,
    AUTHOR,
    CDTRACKNUMBER,
    DISCNUMBER,
    COMPILATION,
    DATE,
};

struct _Map{
    META_IDX idx;
    int key;
    const char *tag1;
    const char *tag2;
};

enum _key_string
{
    kKeyAlbum             = 'albu',  // cstring
    kKeyArtist            = 'arti',  // cstring
    kKeyAlbumArtist       = 'aart',  // cstring
    kKeyComposer          = 'comp',  // cstring
    kKeyGenre             = 'genr',  // cstring
    kKeyTitle             = 'titl',  // cstring
    kKeyYear              = 'year',  // cstring
    kKeyAlbumArt          = 'albA',  // compressed image data
    kKeyAlbumArtMIME      = 'alAM',  // cstring
    kKeyAuthor            = 'auth',  // cstring
    kKeyCDTrackNumber     = 'cdtr',  // cstring
    kKeyDiscNumber        = 'dnum',  // cstring
    kKeyDate              = 'date',  // cstring
    kKeyWriter            = 'writ',  // cstring
    kKeyCompilation       = 'cpil',  // cstring
    kKeyLocation          = 'loc ',  // cstring
    kKeyTimeScale         = 'tmsl',  // cdx_int32
};

void SetMetaData(CdxMediaInfoT *mediaInfo, META_IDX idx, const char* content);

#endif
