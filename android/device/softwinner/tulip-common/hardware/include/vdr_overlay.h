#ifndef __VDRV_ENC_I_H__
#define __VDRV_ENC_I_H__

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define OVERLAY_PALLETE_BASE_SIZE  1024         /* �м���������ɫ��ʹ�õĿռ��С*/
#define OVERLAY_PIXELS_BUF_BASE_SIZE  50*1024   /* �м�������overlay����ʹ�õĿռ��С*/

#define MAX_OVERLAY_BLOCK_NUM 40
#define OVERLAY_PALETTE_SIZE  64
#define OVERLAY_DATA_BUF_SIZE  0x40000          /*256k*/
#define OVERLAY_MAX_SRC_GROUP_NUM 20            /* overlay��������������Դ */

//add 
#define OVERLAY_MAX_SRC_INGROUP 13
#define OVERLAY_MAX_LIST_LEN 20
#define EPDK_YES 0


typedef struct pos_t
{
	unsigned int x;
	unsigned int y;
}pos_t;

typedef struct __rectsz_t
{
	unsigned int width;
	unsigned int height;
}__rectsz_t;



typedef struct overlay_pic_info
{
    unsigned char       ID;                 //src id
    __rectsz_t          size;               //for the size of one picture
    unsigned char*      pOverlayPixel;           //the index of the RGB data
}overlay_pic_info;

typedef struct overlay_src_init
{
    unsigned char                srcPicNum;                 //src id
    overlay_pic_info  	srcPic[OVERLAY_MAX_SRC_INGROUP];
    unsigned int               ppalette_yuv[16];              //the palette of yuv format
    unsigned char                color_num;                 //the color number of the palette
}overlay_src_init;


typedef struct dis_par
{
    pos_t   pos;               //the position of the log
    unsigned char     total;
    unsigned char     IDlist[OVERLAY_MAX_LIST_LEN];    //the index of the display of the picture
}dis_par;
  /* _MOD_HERB_GINGKO_H_ */



typedef struct overlay_block_header
{
    unsigned int    Next_BlkHdr_Addr;
    unsigned int    hor_plane;
    unsigned int    ver_plane;
    unsigned int    Mb_Pal_Idx;
}overlay_block_header;


typedef struct overlay_info
{
    unsigned char           isEnabled;                              /* ��ǰ�ж���block */
    unsigned int            nblock;                                 /* ��ǰ�ж���block */
    unsigned int            nsrc;                                   /* ��ǰ�ж�������Դ */
    overlay_src_init      	srcPicGroup[OVERLAY_MAX_SRC_GROUP_NUM]; /* ��Դ��Ϣ */
    dis_par       			dispInfo[MAX_OVERLAY_BLOCK_NUM];        /* ��ʾ��Ϣ */
    unsigned char           dispGroupIndex[MAX_OVERLAY_BLOCK_NUM];  /* ��ʾͼƬ����Դ�е�index */

    overlay_block_header* 	pblkHdrTbl;                             /*block header��*/
    unsigned int            blkHdrTblSize;
    unsigned char*          ppalette;                               /*���ø��Ĵ����ĵ�ɫ����Ϣ��ַ*/
    unsigned int            paletteBufSize;
    unsigned char*          pdata;                                  /*���ø��Ĵ��������ݵ�ַ*/
    unsigned int            dataBufSize;
}overlay_info;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif

