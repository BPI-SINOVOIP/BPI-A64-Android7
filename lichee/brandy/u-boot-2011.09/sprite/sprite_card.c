/*
 * (C) Copyright 2007-2013
 * Allwinner Technology Co., Ltd. <www.allwinnertech.com>
 * Jerry Wang <wangflord@allwinnertech.com>
 *
 * See file CREDITS for list of people who contributed to this
 * project.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 */
#include <config.h>
#include <common.h>
#include <malloc.h>
#include "sparse/sparse.h"
#include <asm/arch/queue.h>
#include <sunxi_mbr.h>
#include <sys_partition.h>
#include <private_boot0.h>
#include <private_uboot.h>
#include "encrypt/encrypt.h"
#include "sprite_queue.h"
#include "sprite_download.h"
#include "sprite_verify.h"
#include "firmware/imgdecode.h"
#include "dos_part.h"
#include "gpt.h"
#include <boot_type.h>
#include <mmc.h>
#include <sys_config.h>
#include <private_boot0.h>
#define  SPRITE_CARD_HEAD_BUFF		   (32 * 1024)
#if defined (CONFIG_SUNXI_SPINOR)
#define  SPRITE_CARD_ONCE_DATA_DEAL    (2 * 1024 * 1024)
#else
#define  SPRITE_CARD_ONCE_DATA_DEAL    (16 * 1024 * 1024)
#endif
#define  SPRITE_CARD_ONCE_SECTOR_DEAL  (SPRITE_CARD_ONCE_DATA_DEAL/512)

static void *imghd = NULL;
static void *imgitemhd = NULL;

DECLARE_GLOBAL_DATA_PTR;

//extern int sunxi_flash_mmc_phywipe(unsigned long start_block, unsigned long nblock, unsigned long *skip);
static int __download_normal_part(dl_one_part_info *part_info,  uchar *source_buff);
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
uint sprite_card_firmware_start(void)
{
	return sunxi_partition_get_offset(1);
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int sprite_card_firmware_probe(char *name)
{
	debug("firmware name %s\n", name);
	imghd = Img_Open(name);
	if(!imghd)
	{
		return -1;
	}

	return 0;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int sprite_card_fetch_download_map(sunxi_download_info  *dl_map)
{
	imgitemhd = Img_OpenItem(imghd, "12345678", "1234567890DLINFO");
	if(!imgitemhd)
	{
		return -1;
	}
	debug("try to read item dl map\n");
	if(!Img_ReadItem(imghd, imgitemhd, (void *)dl_map, sizeof(sunxi_download_info)))
	{
		printf("sunxi sprite error : read dl map failed\n");

		return -1;
	}
	Img_CloseItem(imghd, imgitemhd);
	imgitemhd = NULL;
	//����ȡ��dlinfo�Ƿ���ȷ
	return sunxi_sprite_verify_dlmap(dl_map);
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int sprite_card_fetch_mbr(void  *img_mbr)
{
	imgitemhd = Img_OpenItem(imghd, "12345678", "1234567890___MBR");
	if(!imgitemhd)
	{
		return -1;
	}
	debug("try to read item dl map\n");
	if(!Img_ReadItem(imghd, imgitemhd, img_mbr, sizeof(sunxi_mbr_t) * SUNXI_MBR_COPY_NUM))
	{
		printf("sunxi sprite error : read mbr failed\n");

		return -1;
	}
	Img_CloseItem(imghd, imgitemhd);
	imgitemhd = NULL;

	return sunxi_sprite_verify_mbr(img_mbr);
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
static int __download_udisk(dl_one_part_info *part_info,  uchar *source_buff)
{
    HIMAGEITEM imgitemhd = NULL;
	u32  flash_sector;
	s64  packet_len;
	s32  ret = -1, ret1;

	//�򿪷�������
	imgitemhd = Img_OpenItem(imghd, "RFSFAT16", (char *)part_info->dl_filename);
	if(!imgitemhd)
	{
		printf("sunxi sprite error: open part %s failed\n", part_info->dl_filename);

		return -1;
	}
	//��ȡ���������ֽ���
	packet_len = Img_GetItemSize(imghd, imgitemhd);
	if (packet_len <= 0)
	{
		printf("sunxi sprite error: fetch part len %s failed\n", part_info->dl_filename);

		goto __download_udisk_err1;
	}
	if (packet_len <= FW_BURN_UDISK_MIN_SIZE)
	{
		printf("download UDISK: the data length of udisk is too small, ignore it\n");

		ret = 1;
		goto __download_udisk_err1;
	}
	//�������񹻴���Ҫ������¼
	flash_sector = sunxi_sprite_size();
	if(!flash_sector)
	{
		printf("sunxi sprite error: download_udisk, the flash size is invalid(0)\n");

		goto __download_udisk_err1;
	}
	printf("the flash size is %d MB\n", flash_sector/2/1024);	//�����M��λ
	part_info->lenlo = flash_sector - part_info->addrlo;
	part_info->lenhi = 0;
	printf("UDISK low is 0x%x Sectors\n", part_info->lenlo);
	printf("UDISK high is 0x%x Sectors\n", part_info->lenhi);

	ret = __download_normal_part(part_info, source_buff);
__download_udisk_err1:
	ret1 = Img_CloseItem(imghd, imgitemhd);
	if(ret1 != 0 )
	{
		printf("sunxi sprite error: __download_udisk, close udisk image failed\n");

		return -1;
	}

	return ret;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
static int __download_normal_part(dl_one_part_info *part_info,  uchar *source_buff)
{
	uint partstart_by_sector;		//������ʼ����
	uint tmp_partstart_by_sector;

	s64  partsize_by_byte;			//������С(�ֽڵ�λ)

	s64  partdata_by_byte;			//��Ҫ���صķ�������(�ֽڵ�λ)
	s64  tmp_partdata_by_bytes;

	uint onetime_read_sectors;		//һ�ζ�д��������
	uint first_write_bytes;

	uint imgfile_start;				//�����������ڵ�����
	uint tmp_imgfile_start;

	u8 *down_buffer       = source_buff + SPRITE_CARD_HEAD_BUFF;

	int  partdata_format;

	int  ret = -1;
	//*******************************************************************
	//��ȡ������ʼ����
	tmp_partstart_by_sector = partstart_by_sector = part_info->addrlo;
	//��ȡ������С���ֽ���
	partsize_by_byte     = part_info->lenlo;
	partsize_by_byte   <<= 9;
	//�򿪷�������
	imgitemhd = Img_OpenItem(imghd, "RFSFAT16", (char *)part_info->dl_filename);
	if(!imgitemhd)
	{
		printf("sunxi sprite error: open part %s failed\n", part_info->dl_filename);

		return -1;
	}
	//��ȡ���������ֽ���
	partdata_by_byte = Img_GetItemSize(imghd, imgitemhd);
	if (partdata_by_byte <= 0)
	{
		printf("sunxi sprite error: fetch part len %s failed\n", part_info->dl_filename);

		goto __download_normal_part_err1;
	}
	printf("partdata hi 0x%x\n", (uint)(partdata_by_byte>>32));
	printf("partdata lo 0x%x\n", (uint)partdata_by_byte);
	//����������ݳ���������С
	if(partdata_by_byte > partsize_by_byte)
	{
		printf("sunxi sprite: data size 0x%x is larger than part %s size 0x%x\n", (uint)(partdata_by_byte/512), part_info->dl_filename, (uint)(partsize_by_byte/512));

		goto __download_normal_part_err1;
	}
	//׼����ȡ������������
	tmp_partdata_by_bytes = partdata_by_byte;
	if(tmp_partdata_by_bytes >= SPRITE_CARD_ONCE_DATA_DEAL)
	{
		onetime_read_sectors = SPRITE_CARD_ONCE_SECTOR_DEAL;
		first_write_bytes    = SPRITE_CARD_ONCE_DATA_DEAL;
	}
	else
	{
		onetime_read_sectors = (tmp_partdata_by_bytes + 511)>>9;
		first_write_bytes    = (uint)tmp_partdata_by_bytes;
	}
	//��ʼ��ȡ��������
	imgfile_start = Img_GetItemStart(imghd, imgitemhd);
	if(!imgfile_start)
	{
		printf("sunxi sprite err : cant get part data imgfile_start %s\n", part_info->dl_filename);

		goto __download_normal_part_err1;
	}
	tmp_imgfile_start = imgfile_start;
	//������һ�ʹ̼��еķ������ݣ���СΪbuffer�ֽ���
	if(sunxi_flash_read(tmp_imgfile_start, onetime_read_sectors, down_buffer) != onetime_read_sectors)
	{
		printf("sunxi sprite error : read sdcard block %d, total %d failed\n", tmp_imgfile_start, onetime_read_sectors);

		goto __download_normal_part_err1;
	}
	//��һ��Ҫ����������
	tmp_imgfile_start += onetime_read_sectors;
	//���Բ鿴�Ƿ�sparse��ʽ
    partdata_format = unsparse_probe((char *)down_buffer, first_write_bytes, partstart_by_sector);		//�ж����ݸ�ʽ
    if(partdata_format != ANDROID_FORMAT_DETECT)
    {
    	//д���һ������
    	if(sunxi_sprite_write(tmp_partstart_by_sector, onetime_read_sectors, down_buffer) != onetime_read_sectors)
		{
			printf("sunxi sprite error: download rawdata error %s\n", part_info->dl_filename);

			goto __download_normal_part_err1;
		}
    	tmp_partdata_by_bytes   -= first_write_bytes;
		tmp_partstart_by_sector += onetime_read_sectors;

		while(tmp_partdata_by_bytes >= SPRITE_CARD_ONCE_DATA_DEAL)
		{
			//���������̼��еķ������ݣ���СΪbuffer�ֽ���
			if(sunxi_flash_read(tmp_imgfile_start, SPRITE_CARD_ONCE_SECTOR_DEAL, down_buffer) != SPRITE_CARD_ONCE_SECTOR_DEAL)
			{
				printf("sunxi sprite error : read sdcard block %d, total %d failed\n", tmp_imgfile_start, SPRITE_CARD_ONCE_SECTOR_DEAL);

				goto __download_normal_part_err1;
			}
			//д��flash
			if(sunxi_sprite_write(tmp_partstart_by_sector, SPRITE_CARD_ONCE_SECTOR_DEAL, down_buffer) != SPRITE_CARD_ONCE_SECTOR_DEAL)
			{
				printf("sunxi sprite error: download rawdata error %s, start 0x%x, sectors 0x%x\n", part_info->dl_filename, tmp_partstart_by_sector, SPRITE_CARD_ONCE_SECTOR_DEAL);

				goto __download_normal_part_err1;
			}
			tmp_imgfile_start       += SPRITE_CARD_ONCE_SECTOR_DEAL;
			tmp_partdata_by_bytes   -= SPRITE_CARD_ONCE_DATA_DEAL;
			tmp_partstart_by_sector += SPRITE_CARD_ONCE_SECTOR_DEAL;
		}
		if(tmp_partdata_by_bytes > 0)
		{
			uint rest_sectors = (tmp_partdata_by_bytes + 511)>>9;
			//���������̼��еķ������ݣ���СΪbuffer�ֽ���
			if(sunxi_flash_read(tmp_imgfile_start, rest_sectors, down_buffer) != rest_sectors)
			{
				printf("sunxi sprite error : read sdcard block %d, total %d failed\n", tmp_imgfile_start, rest_sectors);

				goto __download_normal_part_err1;
			}
			//д��flash
			if(sunxi_sprite_write(tmp_partstart_by_sector, rest_sectors, down_buffer) != rest_sectors)
			{
				printf("sunxi sprite error: download rawdata error %s, start 0x%x, sectors 0x%x\n", part_info->dl_filename, tmp_partstart_by_sector, rest_sectors);

				goto __download_normal_part_err1;
			}
		}
    }
    else
    {
    	if(unsparse_direct_write(down_buffer, first_write_bytes))
    	{
    		printf("sunxi sprite error: download sparse error %s\n", part_info->dl_filename);

    		goto __download_normal_part_err1;
    	}
    	tmp_partdata_by_bytes   -= first_write_bytes;

		while(tmp_partdata_by_bytes >= SPRITE_CARD_ONCE_DATA_DEAL)
		{
			//���������̼��еķ������ݣ���СΪbuffer�ֽ���
			if(sunxi_flash_read(tmp_imgfile_start, SPRITE_CARD_ONCE_SECTOR_DEAL, down_buffer) != SPRITE_CARD_ONCE_SECTOR_DEAL)
			{
				printf("sunxi sprite error : read sdcard block 0x%x, total 0x%x failed\n", tmp_imgfile_start, SPRITE_CARD_ONCE_SECTOR_DEAL);

				goto __download_normal_part_err1;
			}
			//д��flash
			if(unsparse_direct_write(down_buffer, SPRITE_CARD_ONCE_DATA_DEAL))
			{
				printf("sunxi sprite error: download sparse error %s\n", part_info->dl_filename);

				goto __download_normal_part_err1;
			}
			tmp_imgfile_start       += SPRITE_CARD_ONCE_SECTOR_DEAL;
			tmp_partdata_by_bytes   -= SPRITE_CARD_ONCE_DATA_DEAL;
		}
		if(tmp_partdata_by_bytes > 0)
		{
			uint rest_sectors = (tmp_partdata_by_bytes + 511)>>9;
			//���������̼��еķ������ݣ���СΪbuffer�ֽ���
			if(sunxi_flash_read(tmp_imgfile_start, rest_sectors, down_buffer) != rest_sectors)
			{
				printf("sunxi sprite error : read sdcard block 0x%x, total 0x%x failed\n", tmp_imgfile_start, rest_sectors);

				goto __download_normal_part_err1;
			}
			//д��flash
			if(unsparse_direct_write(down_buffer, tmp_partdata_by_bytes))
			{
				printf("sunxi sprite error: download sparse error %s\n", part_info->dl_filename);

				goto __download_normal_part_err1;
			}
		}
    }

    tick_printf("successed in writting part %s\n", part_info->name);
    ret = 0;
    if(imgitemhd)
    {
    	Img_CloseItem(imghd, imgitemhd);
    	imgitemhd = NULL;
    }
	//�ж��Ƿ���Ҫ����У��
    if(part_info->verify)
    {
    	uint active_verify;
    	uint origin_verify;
    	uchar verify_data[1024];

		ret = -1;
    	if(part_info->vf_filename[0])
    	{
	    	imgitemhd = Img_OpenItem(imghd, "RFSFAT16", (char *)part_info->vf_filename);
			if(!imgitemhd)
			{
				printf("sprite update warning: open part %s failed\n", part_info->vf_filename);

				goto __download_normal_part_err1;
			}
			if(!Img_ReadItem(imghd, imgitemhd, (void *)verify_data, 1024))   //��������
	        {
	            printf("sprite update warning: fail to read data from %s\n", part_info->vf_filename);

				goto __download_normal_part_err1;
	        }
	        if(partdata_format == ANDROID_FORMAT_DETECT)
	        {
	        	active_verify = sunxi_sprite_part_sparsedata_verify();
	        }
	    	else
	    	{
	            active_verify = sunxi_sprite_part_rawdata_verify(partstart_by_sector, partdata_by_byte);
	        }
	        {
	        	uint *tmp = (uint *)verify_data;

	        	origin_verify = *tmp;
	        }
	        printf("origin_verify value = %x, active_verify value = %x\n", origin_verify, active_verify);
	        if(origin_verify != active_verify)
	        {
	        	printf("origin checksum=%x, active checksum=%x\n", origin_verify, active_verify);
	        	printf("sunxi sprite: part %s verify error\n", part_info->dl_filename);

	        	goto __download_normal_part_err1;
	        }
	        ret = 0;
	    }
	    else
	    {
	    	printf("sunxi sprite err: part %s unablt to find verify file\n", part_info->dl_filename);
	    }
	    tick_printf("successed in verify part %s\n", part_info->name);
    }
    else
    {
    	printf("sunxi sprite err: part %s not need to verify\n", part_info->dl_filename);
    }

__download_normal_part_err1:
	if(imgitemhd)
    {
    	Img_CloseItem(imghd, imgitemhd);
    	imgitemhd = NULL;
    }

    return ret;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
static int __download_sysrecover_part(dl_one_part_info *part_info,  uchar *source_buff)
{
	uint partstart_by_sector;		//������ʼ����
	uint tmp_partstart_by_sector;

	s64  partsize_by_byte;			//������С(�ֽڵ�λ)

	s64  partdata_by_byte;			//��Ҫ���صķ�������(�ֽڵ�λ)
	s64  tmp_partdata_by_bytes;

	uint onetime_read_sectors;		//һ�ζ�д��������

	uint imgfile_start;				//�����������ڵ�����
	uint tmp_imgfile_start;

	u8 *down_buffer       = source_buff + SPRITE_CARD_HEAD_BUFF;

	int  ret = -1;
	//*******************************************************************
	//��ȡ������ʼ����
	tmp_partstart_by_sector = partstart_by_sector = part_info->addrlo;
	//��ȡ������С���ֽ���
	partsize_by_byte     = part_info->lenlo;
	partsize_by_byte   <<= 9;
	//�򿪷�������

	//��ȡ���������ֽ���
	partdata_by_byte = Img_GetSize(imghd);
	if (partdata_by_byte <= 0)
	{
		printf("sunxi sprite error: fetch part len %s failed\n", part_info->dl_filename);

		goto __download_sysrecover_part_err1;
	}
	//����������ݳ���������С
	if(partdata_by_byte > partsize_by_byte)
	{
		printf("sunxi sprite: data size 0x%x is larger than part %s size 0x%x\n", (uint)(partdata_by_byte/512), part_info->dl_filename, (uint)(partsize_by_byte/512));

		goto __download_sysrecover_part_err1;
	}
	//׼����ȡ������������
	tmp_partdata_by_bytes = partdata_by_byte;
	if(tmp_partdata_by_bytes >= SPRITE_CARD_ONCE_DATA_DEAL)
	{
		onetime_read_sectors = SPRITE_CARD_ONCE_SECTOR_DEAL;
	}
	else
	{
		onetime_read_sectors = (tmp_partdata_by_bytes + 511)>>9;
	}
	//��ʼ��ȡ��������
	imgfile_start = sprite_card_firmware_start();
	if(!imgfile_start)
	{
		printf("sunxi sprite err : cant get part data imgfile_start %s\n", part_info->dl_filename);

		goto __download_sysrecover_part_err1;
	}
	tmp_imgfile_start = imgfile_start;

	while(tmp_partdata_by_bytes >= SPRITE_CARD_ONCE_DATA_DEAL)
	{
		//���������̼��еķ������ݣ���СΪbuffer�ֽ���
		if(sunxi_flash_read(tmp_imgfile_start, onetime_read_sectors, down_buffer) != onetime_read_sectors)
		{
			printf("sunxi sprite error : read sdcard block %d, total %d failed\n", tmp_imgfile_start, onetime_read_sectors);

			goto __download_sysrecover_part_err1;
		}
		//д��flash
		if(sunxi_sprite_write(tmp_partstart_by_sector, onetime_read_sectors, down_buffer) != onetime_read_sectors)
		{
			printf("sunxi sprite error: download rawdata error %s, start 0x%x, sectors 0x%x\n", part_info->dl_filename, tmp_partstart_by_sector, onetime_read_sectors);

			goto __download_sysrecover_part_err1;
		}
		tmp_imgfile_start       += onetime_read_sectors;
		tmp_partdata_by_bytes   -= onetime_read_sectors*512;
		tmp_partstart_by_sector += onetime_read_sectors;
	}
	if(tmp_partdata_by_bytes > 0)
	{
		uint rest_sectors = (tmp_partdata_by_bytes + 511)/512;
		//���������̼��еķ������ݣ���СΪbuffer�ֽ���
		if(sunxi_flash_read(tmp_imgfile_start, rest_sectors, down_buffer) != rest_sectors)
		{
			printf("sunxi sprite error : read sdcard block %d, total %d failed\n", tmp_imgfile_start, rest_sectors);

			goto __download_sysrecover_part_err1;
		}
		//д��flash
		if(sunxi_sprite_write(tmp_partstart_by_sector, rest_sectors, down_buffer) != rest_sectors)
		{
			printf("sunxi sprite error: download rawdata error %s, start 0x%x, sectors 0x%x\n", part_info->dl_filename, tmp_partstart_by_sector, rest_sectors);

			goto __download_sysrecover_part_err1;
		}
	}
    ret = 0;

__download_sysrecover_part_err1:
	if(imgitemhd)
    {
    	Img_CloseItem(imghd, imgitemhd);
    	imgitemhd = NULL;
    }

    return ret;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int sunxi_sprite_deal_part(sunxi_download_info *dl_map)
{
    dl_one_part_info  	*part_info;
	int 				ret  = -1;
	int 				ret1;
	int 				  i  = 0;
    uchar *down_buff         = NULL;
    int					rate;

	if(!dl_map->download_count)
	{
		printf("sunxi sprite: no part need to write\n");

		return 0;
	}
	rate = (70-10)/dl_map->download_count;
	//��ʼ��flash��nand����mmc
	if(sunxi_sprite_init(1))
	{
		printf("sunxi sprite err: init flash err\n");

		return -1;
	}
 	//�����ڴ�
    down_buff = (uchar *)malloc(SPRITE_CARD_ONCE_DATA_DEAL + SPRITE_CARD_HEAD_BUFF);
    if(!down_buff)
    {
    	printf("sunxi sprite err: unable to malloc memory for sunxi_sprite_deal_part\n");

    	goto __sunxi_sprite_deal_part_err1;
    }
    for(part_info = dl_map->one_part_info, i = 0; i < dl_map->download_count; i++, part_info++)
    {
    	tick_printf("begin to download part %s\n", part_info->name);
    	if(!strncmp("UDISK", (char*)part_info->name, strlen("UDISK")))
		{
			ret1 = __download_udisk(part_info, down_buff);
			if(ret1 < 0)
			{
				printf("sunxi sprite err: sunxi_sprite_deal_part, download_udisk failed\n");

				goto __sunxi_sprite_deal_part_err2;
			}
			else if(ret1 > 0)
			{
				printf("do NOT need download UDISK\n");
			}
		}//�����sysrecovery��������¼������������
		else if(!strncmp("sysrecovery", (char*)part_info->name, strlen("sysrecovery")))
		{
			ret1 = __download_sysrecover_part(part_info, down_buff);
			if(ret1 != 0)
			{
				printf("sunxi sprite err: sunxi_sprite_deal_part, download sysrecovery failed\n");

				goto __sunxi_sprite_deal_part_err2;
			}
		}//�����private����������Ƿ���Ҫ��¼
		else if(!strncmp("private", (char*)part_info->name, strlen("private")))
		{
			if(1)
			{
				//��Ҫ��¼�˷���
				printf("NEED down private part\n");
				ret1 = __download_normal_part(part_info, down_buff);
				if(ret1 != 0)
				{
					printf("sunxi sprite err: sunxi_sprite_deal_part, download private failed\n");

					goto __sunxi_sprite_deal_part_err2;
				}
			}
			else
			{
				printf("IGNORE private part\n");
			}
		}
		else
		{
			ret1 = __download_normal_part(part_info, down_buff);
			if(ret1 != 0)
			{
				printf("sunxi sprite err: sunxi_sprite_deal_part, download normal failed\n");

				goto __sunxi_sprite_deal_part_err2;
			}
		}
		sprite_cartoon_upgrade(10 + rate * (i+1));
		tick_printf("successed in download part %s\n", part_info->name);
	}

	ret = 0;

__sunxi_sprite_deal_part_err1:
	sunxi_sprite_exit(1);

__sunxi_sprite_deal_part_err2:

    if(down_buff)
    {
    	free(down_buff);
    }

    return ret;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int sunxi_sprite_deal_uboot(int production_media)
{
	char buffer[4 * 1024 * 1024];
	uint item_original_size;
	if(!gd->securemode)
	{
		imgitemhd = Img_OpenItem(imghd, "12345678", "UBOOT_0000000000");
	}
	else
	{
		imgitemhd = Img_OpenItem(imghd, "12345678", "TOC1_00000000000");
	}

    if(!imgitemhd)
    {
        printf("sprite update error: fail to open uboot item\n");
        return -1;
    }
    //uboot����
    item_original_size = Img_GetItemSize(imghd, imgitemhd);
    if(!item_original_size)
    {
        printf("sprite update error: fail to get uboot item size\n");
        return -1;
    }
    /*��ȡuboot������*/
    if(!Img_ReadItem(imghd, imgitemhd, (void *)buffer, 4 * 1024 * 1024))
    {
        printf("update error: fail to read data from for uboot\n");
        return -1;
    }
    Img_CloseItem(imghd, imgitemhd);
    imgitemhd = NULL;

    if(sunxi_sprite_download_uboot(buffer, production_media, 0))
    {
    	printf("update error: fail to write uboot\n");
        return -1;
    }
    printf("sunxi_sprite_deal_uboot ok\n");

    return 0;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int sunxi_sprite_deal_boot0(int production_media)
{
	char buffer[1 *1024 * 1024];
	uint item_original_size;

	if(!gd->securemode)
	{
		if(production_media == 0)
		{
			imgitemhd = Img_OpenItem(imghd, "BOOT    ", "BOOT0_0000000000");
		}
		else
		{
			imgitemhd = Img_OpenItem(imghd, "12345678", "1234567890BOOT_0");
		}
	}
	else
	{
		imgitemhd = Img_OpenItem(imghd, "12345678", "TOC0_00000000000");
	}

    if(!imgitemhd)
    {
        printf("sprite update error: fail to open boot0 item\n");
        return -1;
    }
    //boot0����
    item_original_size = Img_GetItemSize(imghd, imgitemhd);
    if(!item_original_size)
    {
        printf("sprite update error: fail to get boot0 item size\n");
        return -1;
    }

    /*��ȡboot0������*/
    if(!Img_ReadItem(imghd, imgitemhd, (void *)buffer, 1 * 1024 * 1024))
    {
        printf("update error: fail to read data from for boot0\n");
        return -1;
    }
    Img_CloseItem(imghd, imgitemhd);
    imgitemhd = NULL;

    if(sunxi_sprite_download_boot0(buffer, production_media))
    {
    	printf("update error: fail to write boot0\n");
        return -1;
    }

    return 0;
}

/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int card_download_uboot(uint length, void *buffer)
{
	int ret;
	ret = sunxi_sprite_phywrite(UBOOT_START_SECTOR_IN_SDMMC, length/512, buffer);
	if(!ret)
	{
		return -1;
	}

#ifdef CONFIG_TOC1_BACKUP_MODE

	ret = sunxi_sprite_phywrite(UBOOT_START_SECTOR_BACKUP_IN_SDMMC, length/512, buffer);
	if(!ret)
	{
		return -1;
	}
#endif

#ifdef CONFIG_UBOOT_BACKUP_MODE
        ret = sunxi_sprite_phywrite(UBOOT_START_BACKUP_IN_SDMMC,length/512 ,buffer);
        if(!ret)
        {
                return -1;
        }
#endif
	return 0;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int card_download_boot0(uint length, void *buffer)
{
	int ret;

	ret = sunxi_sprite_phywrite(BOOT0_SDMMC_START_ADDR, length/512, buffer);
	if(!ret)
	{
		return -1;
	}

	return 0;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int card_upload_boot0(uint length, void *buffer)
{
	int ret;

	ret = sunxi_sprite_phyread(BOOT0_SDMMC_START_ADDR, (length+511)/512, buffer);
	if(!ret)
	{
		return -1;
	}

	return 0;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
//static void buffer_dump(void *buffer, int len)
//{
//	int i;
//	char *data = (char *)buffer;
//
//	for(i=0;i<len;i++)
//	{
//		printf("%02x", data[i]);
//		if((i & 0x07) == 7)
//		{
//			printf("\n");
//		}
//		else
//		{
//			puts("  ");
//		}
//	}
//}


int card_download_standard_gpt(void *buffer)
{
    legacy_mbr   *remain_mbr;
    sunxi_mbr_t  *sunxi_mbr = (sunxi_mbr_t *)buffer;
    gpt_header   *gpt_head;
    gpt_entry    *pgpt_entry = NULL;
    char         legacy_mbr_buf[512];
    char         gpt_head_buf[512];
    char         gpt_entry_buf[512];
    char         gpt_entry_bkup_buf[20*128];
    int          i,id,k;
    int          j = 0;
    int          total_sectors;
    int          next_sector = 0;
    u64          sectors = 0;

    unsigned char name_unicode[72];
    unsigned char guid[16] = {0x87,0x3c,0x95,0xc5,0x25,0x42,0x60,0x49,0xa9,0xd0,0x41,0x7f,0x23,0x2f,0x81,0xb8};
    unsigned char part_guid[16] = {0x46,0x55,0x08,0xa0,0x66,0x41,0x4a,0x74,0xa3,0x53,0xfc,0xa9,0x27,0x2b,0x8e,0x45};


    /* 1. LBA0: write legacy mbr,part type must be 0xee */
    memset(legacy_mbr_buf, 0, 512);
    remain_mbr = (legacy_mbr *)legacy_mbr_buf;
    remain_mbr->partition_record[0].part_type = 0xee;
    remain_mbr->partition_record[0].start_sect = 1UL;
    remain_mbr->end_flag = 0xaa55;
    if(!sunxi_sprite_phywrite(0, 1, legacy_mbr_buf))
    {
	    printf("write legacy mbr 0 failed\n");
	    return -1;
    }

    /* 2. LBA1: fill primary gpt header */
    memset(gpt_head_buf, 0, 512);
    gpt_head = (gpt_header *)gpt_head_buf;
    strcpy((char *)gpt_head->signature,GPT_HEADER_SIGNATURE);

    gpt_head->revision[0] = 0x00;
    gpt_head->revision[1] = 0x00;
    gpt_head->revision[2] = 0x01;
    gpt_head->revision[3] = 0x00;

    gpt_head->header_size = 0x5c;
    gpt_head->header_crc32 = 0x00;

    memset(gpt_head->reserved1, 0, sizeof(gpt_head->reserved1));

    //��һ��������ͷ��λ�ã�����Ϊ��λ
    gpt_head->my_lba = 0x01;

    //���ݷ�����ͷ��λ�ã�����Ϊ��λ
    total_sectors = sunxi_sprite_size();
    gpt_head->alternate_lba = total_sectors - 1;

    //��һ�������ڷ�����LBA��������������һ��LBA + 1��
    gpt_head->first_usable_lba = sunxi_mbr->array[0].addrlo + (20<<20)/512;
    printf("the first partition offset sector = %d\n",sunxi_mbr->array[0].addrlo);

    //���һ�������ڷ�����LBA�����ݷ�����ĵ�һ��LBA - 1��
    gpt_head->last_usable_lba = total_sectors - 16;

    memcpy(gpt_head->disk_guid.b,guid,16);

    //�����������λ�ã�����Ϊ��λ
    gpt_head->partition_entry_lba = 0x02;

    //���������������
    gpt_head->num_partition_entries = sunxi_mbr->PartCount;

    //һ����������Ĵ�С���ֽ�Ϊ��λ
    gpt_head->sizeof_partition_entry = 0x80;

    /* ���������crc��Ҫ��ȡʵ�ʷ�����������ݲſɼ��㣬������Ҫ��д�����������ټ��� */

    /* 3. LBA2~LBAn: fill gpt entry */
    memset(gpt_entry_buf, 0, 512);
	for(i=0;i<sunxi_mbr->PartCount;i++)
	{
        pgpt_entry = (gpt_entry *)(gpt_entry_buf+j*128);
        //printf("i = %d, pgpt_entry = 0x%x\n",i,(unsigned int)pgpt_entry);

        sectors += sunxi_mbr->array[i].lenlo;
        //printf("SUNXI_MBR:%-12s: %-12x  %-12x\n", sunxi_mbr->array[i].name, sunxi_mbr->array[i].addrlo, sunxi_mbr->array[i].lenlo);

        pgpt_entry->partition_type_guid = PARTITION_BASIC_DATA_GUID;

        memcpy(pgpt_entry->unique_partition_guid.b,part_guid,16);
        for(id=0;id<16;id++)
            part_guid[id] = part_guid[id]+1;

        pgpt_entry->starting_lba = ((u64)sunxi_mbr->array[i].addrhi<<32) + sunxi_mbr->array[i].addrlo + (20<<20)/512;
        pgpt_entry->ending_lba = pgpt_entry->starting_lba \
             +((u64)sunxi_mbr->array[i].lenhi<<32)  \
             + sunxi_mbr->array[i].lenlo;

        //UDISK partition
        if(i == sunxi_mbr->PartCount-1)
        {
            pgpt_entry->ending_lba = total_sectors - 16;
        }

        //printf("GPT:%-12s: %-12llx  %-12llx\n", sunxi_mbr->array[i].name, pgpt_entry->starting_lba, pgpt_entry->ending_lba);
        if(sunxi_mbr->array[i].ro == 1)
        {
            pgpt_entry->attributes.type_guid_specific = 0x6000;
        }
        else
        {
            pgpt_entry->attributes.type_guid_specific = 0x8000;
        }

        //ASCII to unicode
        memset(name_unicode, 0,72);
		for(k=0;k < strlen((const char *)sunxi_mbr->array[i].name);k++ )
		{
			name_unicode[k*2] = sunxi_mbr->array[i].name[k];
		}
	    for(k =0;k < 72;k++)
		{
			pgpt_entry->partition_name[k] = name_unicode[k];
		}

        j++;
        if((pgpt_entry == (gpt_entry *)(gpt_entry_buf+384)) || (i == sunxi_mbr->PartCount-1))
        {
            j = 0;
            if(!sunxi_sprite_phywrite(2+next_sector, 1, (u8*)gpt_entry_buf))
            {
                printf("fail to write gpt entrys to the %dth sectors\n", 2+next_sector);
                return -1;
            }else{
                printf("success to write gpt entrys to the %dth sectors\n", 2+next_sector);

                //backup gpt entrys,use for calc gpt entrys crc value
                memcpy(gpt_entry_bkup_buf + next_sector*512 ,gpt_entry_buf,512);

                //clean gpt_entry_buf
                memset(gpt_entry_buf, 0, 512);
                next_sector++;
            }
        }

	}

    //�������GPTͷ����Ϣ
    pgpt_entry = (gpt_entry *)gpt_entry_bkup_buf;
    gpt_head->partition_entry_array_crc32 = crc32(0, (unsigned char const *)pgpt_entry,
                                                         (gpt_head->num_partition_entries)*(gpt_head->sizeof_partition_entry));

    //printf("gpt_head->partition_entry_array_crc32 = %d\n",gpt_head->partition_entry_array_crc32);

    memset(gpt_head->reserved2, 0, sizeof(gpt_head->reserved2));

    gpt_head->header_crc32 = crc32(0,(const unsigned char *)gpt_head,sizeof(gpt_header)- GPT_RESERVED_SIZE);
    //printf("gpt_head->header_crc32 = %d\n",gpt_head->header_crc32);

    if(!sunxi_sprite_phywrite(1, 1, gpt_head_buf))
    {
	    printf("write gpt header 1 failed\n");
	    return -1;
    }

    /* 4. LBA-1: the last sector fill backup gpt header */

	return 0;
}


int card_download_standard_mbr(void *buffer)
{
	mbr_stand   *mbrst;
	sunxi_mbr_t *mbr = (sunxi_mbr_t *)buffer;
	char         mbr_bufst[512];
	int          i;
	int          sectors;
	int          unusd_sectors;

	sectors = 0;
	for(i=1;i<mbr->PartCount-1;i++)
	{
		memset(mbr_bufst, 0, 512);
		mbrst = (mbr_stand *)mbr_bufst;

		sectors += mbr->array[i].lenlo;

		mbrst->part_info[0].part_type  	   = 0x83;
		mbrst->part_info[0].start_sectorl  = ((mbr->array[i].addrlo - i + 20 * 1024 * 1024/512 ) & 0x0000ffff) >> 0;
		mbrst->part_info[0].start_sectorh  = ((mbr->array[i].addrlo - i + 20 * 1024 * 1024/512 ) & 0xffff0000) >> 16;
		mbrst->part_info[0].total_sectorsl = ( mbr->array[i].lenlo & 0x0000ffff) >> 0;
		mbrst->part_info[0].total_sectorsh = ( mbr->array[i].lenlo & 0xffff0000) >> 16;

		if(i != mbr->PartCount-2)
		{
			mbrst->part_info[1].part_type      = 0x05;
			mbrst->part_info[1].start_sectorl  = i;
			mbrst->part_info[1].start_sectorh  = 0;
			mbrst->part_info[1].total_sectorsl = (mbr->array[i].lenlo  & 0x0000ffff) >> 0;
			mbrst->part_info[1].total_sectorsh = (mbr->array[i].lenlo  & 0xffff0000) >> 16;
		}

		mbrst->end_flag = 0xAA55;
		if(!sunxi_sprite_phywrite(i, 1, mbr_bufst))
		{
			printf("write standard mbr %d failed\n", i);

			return -1;
		}
	}
	memset(mbr_bufst, 0, 512);
	mbrst = (mbr_stand *)mbr_bufst;

	unusd_sectors = sunxi_sprite_size() - 20 * 1024 * 1024/512 - sectors;
	mbrst->part_info[0].indicator = 0x80;
	mbrst->part_info[0].part_type = 0x0B;
	mbrst->part_info[0].start_sectorl  = ((mbr->array[mbr->PartCount-1].addrlo + 20 * 1024 * 1024/512 ) & 0x0000ffff) >> 0;
	mbrst->part_info[0].start_sectorh  = ((mbr->array[mbr->PartCount-1].addrlo + 20 * 1024 * 1024/512 ) & 0xffff0000) >> 16;
	mbrst->part_info[0].total_sectorsl = ( unusd_sectors & 0x0000ffff) >> 0;
	mbrst->part_info[0].total_sectorsh = ( unusd_sectors & 0xffff0000) >> 16;

	mbrst->part_info[1].part_type = 0x06;
	mbrst->part_info[1].start_sectorl  = ((mbr->array[0].addrlo + 20 * 1024 * 1024/512) & 0x0000ffff) >> 0;
	mbrst->part_info[1].start_sectorh  = ((mbr->array[0].addrlo + 20 * 1024 * 1024/512) & 0xffff0000) >> 16;
	mbrst->part_info[1].total_sectorsl = (mbr->array[0].lenlo  & 0x0000ffff) >> 0;
	mbrst->part_info[1].total_sectorsh = (mbr->array[0].lenlo  & 0xffff0000) >> 16;

	mbrst->part_info[2].part_type = 0x05;
	mbrst->part_info[2].start_sectorl  = 1;
	mbrst->part_info[2].start_sectorh  = 0;
	mbrst->part_info[2].total_sectorsl = (sectors & 0x0000ffff) >> 0;
	mbrst->part_info[2].total_sectorsh = (sectors & 0xffff0000) >> 16;

	mbrst->end_flag = 0xAA55;
	if(!sunxi_sprite_phywrite(0, 1, mbr_bufst))
	{
		printf("write standard mbr 0 failed\n");

		return -1;
	}

	return 0;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
#define CARD_ERASE_BLOCK_BYTES    (8 * 1024 * 1024)
#define CARD_ERASE_BLOCK_SECTORS  (CARD_ERASE_BLOCK_BYTES/512)


int card_erase(int erase, void *mbr_buffer)
{
	char *erase_buffer;
	sunxi_mbr_t *mbr = (sunxi_mbr_t *)mbr_buffer;
	unsigned int erase_head_sectors;
	unsigned int erase_head_addr;
	unsigned int erase_tail_sectors;
	unsigned int erase_tail_addr;
	unsigned int skip_space[1+2*2]={0};
	unsigned int from, nr;
	int k, ret = 0;
	int i;

	//tick_printf("erase all part start\n");
	if(!erase)
	{
		return 0;
	}
	erase_buffer = (char *)malloc(CARD_ERASE_BLOCK_BYTES);
	if(!erase_buffer)
	{
		printf("card erase fail: unable to malloc memory for card erase\n");

		return -1;
	}
	memset(erase_buffer, 0, CARD_ERASE_BLOCK_BYTES);

	//erase boot0,write 0x00
	card_download_boot0(32 * 1024, erase_buffer);
	printf("erase boot0, size:32k, write 0x00\n");

	for(i=1;i<mbr->PartCount;i++)
	{
		printf("erase %s part\n", mbr->array[i].name);
		if (mbr->array[i].lenlo > CARD_ERASE_BLOCK_SECTORS * 2)  // part > 16M
		{
			erase_head_sectors = CARD_ERASE_BLOCK_SECTORS;
			erase_head_addr = mbr->array[i].addrlo;
			//erase_tail_sectors = CARD_ERASE_BLOCK_SECTORS;
			erase_tail_sectors = 2 * 1024 * 1024 / 512;
			erase_tail_addr = mbr->array[i].addrlo + mbr->array[i].lenlo - CARD_ERASE_BLOCK_SECTORS;
		}
		else if (mbr->array[i].lenlo > CARD_ERASE_BLOCK_SECTORS) // 8M < part <= 16M
		{
			erase_head_sectors = CARD_ERASE_BLOCK_SECTORS;
			erase_head_addr = mbr->array[i].addrlo;
			//erase_tail_sectors = mbr->array[i].lenlo - CARD_ERASE_BLOCK_SECTORS;
			erase_tail_sectors = 2 * 1024 * 1024 / 512;
			erase_tail_addr = mbr->array[i].addrlo + mbr->array[i].lenlo - erase_tail_sectors;
		}
		else if (mbr->array[i].lenlo > 0)   										// 0 < part <= 8M
		{
			erase_head_sectors = mbr->array[i].lenlo;
			erase_head_addr = mbr->array[i].addrlo;
			erase_tail_sectors = 0;
			erase_tail_addr = mbr->array[i].addrlo;
		}
		else {
			//printf("don't deal prat's length is 0 (%s) \n", mbr->array[i].name);
			//break;
			erase_head_sectors = CARD_ERASE_BLOCK_SECTORS;
			erase_head_addr = mbr->array[i].addrlo;
			erase_tail_sectors = 0;
			erase_tail_addr = mbr->array[i].addrlo;
		}

		from = mbr->array[i].addrlo + CONFIG_MMC_LOGICAL_OFFSET;
		nr = mbr->array[i].lenlo;
		ret = sunxi_sprite_mmc_phyerase(from, nr, skip_space);
		if (ret == 0)
		{
			//printf("erase part from sector 0x%x to 0x%x ok\n", from, (from+nr-1));
		}
		else if (ret == 1)
		{
			for (k=0; k<2; k++)
			{
				if (skip_space[0] & (1<<k)) {
					printf("write zeros-%d: from 0x%x to 0x%x\n", k, skip_space[2*k+1],
						(skip_space[2*k+1]+skip_space[2*k+2]-1));
					from = skip_space[2*k+1];
					nr = skip_space[2*k+2];
					if(!sunxi_sprite_mmc_phywrite(from, nr, erase_buffer))
					{
						printf("card erase fail in erasing part %s\n", mbr->array[i].name);
						free(erase_buffer);
						return -1;
					}
				}
			}
		}
		else if (ret == -1)
		{
			// erase head for partition
			if(!sunxi_sprite_write(erase_head_addr, erase_head_sectors, erase_buffer))
			{
				printf("card erase fail in erasing part %s\n", mbr->array[i].name);
				free(erase_buffer);
				return -1;
			}
			printf("erase prat's head from sector 0x%x to 0x%x\n", erase_head_addr, erase_head_addr + erase_head_sectors);

			// erase tail for partition
			if (erase_tail_sectors)
			{
				if(!sunxi_sprite_write(erase_tail_addr, erase_tail_sectors, erase_buffer))
				{
					printf("card erase fail in erasing part %s\n", mbr->array[i].name);
					free(erase_buffer);
					return -1;
				}
				printf("erase part's tail from sector 0x%x to 0x%x\n", erase_tail_addr, erase_tail_addr + erase_tail_sectors);
			}
		}
	}
	printf("card erase all\n");
	free(erase_buffer);

	//while((*(volatile unsigned int *)0) != 1);
	//tick_printf("erase all part end\n");
	return 0;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
#if defined(CONFIG_ARCH_SUN8IW1P1)

#define  BOOT0_MAX_SIZE   (32 * 1024)

int sunxi_card_probe_mmc0_boot(void)
{
	char  buffer[BOOT0_MAX_SIZE];
	boot0_file_head_t  *boot0_head;
	uint src_sum, cal_sum;
	struct mmc *mmc0;
	char  debug_info[1024];

	puts("probe mmc0 if exist\n");
	memset(debug_info, 0, 1024);
	board_mmc_pre_init(0);
	mmc0 = find_mmc_device(0);
	if(!mmc0)
	{
		strcpy(debug_info, "fail to find mmc0");

		goto __sunxi_card_probe_mmc0_boot_exit;
	}
	debug("try to init mmc0\n");
	if (mmc_init(mmc0))
	{
		strcpy(debug_info, "MMC0 init failed");

		goto __sunxi_card_probe_mmc0_boot_exit;
	}
	memset(buffer, 0, BOOT0_MAX_SIZE);

	if(mmc0->block_dev.block_read_mass_pro(mmc0->block_dev.dev, 16, BOOT0_MAX_SIZE/512, buffer) != BOOT0_MAX_SIZE/512)
	{
		strcpy(debug_info, "read mmc boot0 failed");

		goto __sunxi_card_probe_mmc0_boot_exit;
	}
	//reset uart gpio
	gpio_request_simple("uart_para", NULL);
	//reset jtag
	gpio_request_simple("jtag_para", NULL);
	//compare data
	boot0_head = (boot0_file_head_t *)buffer;
	printf("boot0 magic = %s\n", boot0_head->boot_head.magic);
	if(strncmp((const char *)boot0_head->boot_head.magic, BOOT0_MAGIC, 8))
	{
		puts("boot0 magic invalid\n");

		return 0;
	}

	src_sum = boot0_head->boot_head.check_sum;
	boot0_head->boot_head.check_sum = STAMP_VALUE;
	cal_sum = add_sum(buffer, boot0_head->boot_head.length);
	if(src_sum != cal_sum)
	{
		puts("boot0 addsum error\n");

		return 0;
	}

	sunxi_board_run_fel();

	return 0;

__sunxi_card_probe_mmc0_boot_exit:
	//reset uart gpio
	gpio_request_simple("uart_para", NULL);
	//reset jtag
	gpio_request_simple("jtag_para", NULL);

	printf("%s\n", debug_info);

	return 0;
}
#endif

#define  BOOT0_MAX_SIZE   (32 * 1024)
int sunxi_card_fill_boot0_magic(void)
{
	uchar  buffer[BOOT0_MAX_SIZE];
	boot0_file_head_t  *boot0_head;
	uint src_sum, cal_sum;
	struct mmc *mmc0;
	char  debug_info[1024];
	int ret = -1;

	puts("probe mmc0 if exist\n");
	memset(debug_info, 0, 1024);
	board_mmc_pre_init(0);
	mmc0 = find_mmc_device(0);
	if(!mmc0)
	{
		strcpy(debug_info, "fail to find mmc0");

		goto __sunxi_card_fill_boot0_magic_exit;
	}
	printf("try to init mmc0\n");
	if (mmc_init(mmc0))
	{
		strcpy(debug_info, "MMC0 init failed");

		goto __sunxi_card_fill_boot0_magic_exit;
	}
	memset(buffer, 0, BOOT0_MAX_SIZE);

	if(mmc0->block_dev.block_read_mass_pro(mmc0->block_dev.dev, 16, BOOT0_MAX_SIZE/512, buffer) != BOOT0_MAX_SIZE/512)
	{
		strcpy(debug_info, "read mmc boot0 failed");

		goto __sunxi_card_fill_boot0_magic_exit;
	}
#ifdef CONFIG_ARCH_SUN8IW3P1
	//reset uart gpio
	gpio_request_simple("uart_para", NULL);
	//reset jtag
	gpio_request_simple("jtag_para", NULL);
#endif
    //compare data
	boot0_head = (boot0_file_head_t *)buffer;
	//fill boot0 magic
	memcpy((char *)boot0_head->boot_head.magic, BOOT0_MAGIC,8);
    printf("boot0_head->boot_head.magic   == %s \n",(char*)boot0_head->boot_head.magic);
	src_sum = boot0_head->boot_head.check_sum;
    printf("src_sum = %x \n" ,src_sum);
    //boot0_head->boot_head.check_sum = STAMP_VALUE;
    printf("boot0_head->boot_head.length  =  %d \n",boot0_head->boot_head.length);
	boot0_head->boot_head.check_sum = STAMP_VALUE;
    cal_sum = add_sum(buffer, boot0_head->boot_head.length);
	if(src_sum != cal_sum)
	{
		puts("boot0 addsum error\n");

		return ret;
	}

	boot0_head->boot_head.check_sum = src_sum;
    flush_cache((uint)buffer,BOOT0_MAX_SIZE);
	if(mmc0->block_dev.block_write_mass_pro(mmc0->block_dev.dev, 16, BOOT0_MAX_SIZE/512, buffer) != BOOT0_MAX_SIZE/512)
	{
		strcpy(debug_info, "write mmc boot0 failed");

		goto __sunxi_card_fill_boot0_magic_exit;
	}

	ret = 0;
	return ret ;

__sunxi_card_fill_boot0_magic_exit:
#ifdef CONFIG_ARCH_SUN8IW3P1
	//reset uart gpio
	gpio_request_simple("uart_para", NULL);
	//reset jtag
	gpio_request_simple("jtag_para", NULL);
#endif
	printf("%s\n", debug_info);

	return ret ;
}

/*
************************************************************************************************************
*
*                                             function
*
*    name          :  һ���ָ���д����
*
*    parmeters     :
*
*    return        :
*
*    note          :	yanjianbo@allwinnertech.com
*
*
************************************************************************************************************
*/
int sunxi_sprite_deal_part_from_sysrevoery(sunxi_download_info *dl_map)
{
    dl_one_part_info  	*part_info;
	int 				ret  = -1;
	int 				ret1;
	int 				  i  = 0;
    uchar *down_buff         = NULL;
    int					rate;

	if(!dl_map->download_count)
	{
		printf("sunxi sprite: no part need to write\n");

		return 0;
	}
	rate = (80)/dl_map->download_count;
	//��ʼ��flash��nand����mmc
	//
/*
	if(sunxi_sprite_init(1))
	{
		printf("sunxi sprite err: init flash err\n");

		return -1;
	}
*/
 	//�����ڴ�
    down_buff = (uchar *)malloc(SPRITE_CARD_ONCE_DATA_DEAL + SPRITE_CARD_HEAD_BUFF);
    if(!down_buff)
    {
    	printf("sunxi sprite err: unable to malloc memory for sunxi_sprite_deal_part\n");

    	goto __sunxi_sprite_deal_part_err1;
    }

    for(part_info = dl_map->one_part_info, i = 0; i < dl_map->download_count; i++, part_info++)
    {
    	tick_printf("begin to download part %s\n", part_info->name);
		if (!strcmp("env", (const char *)part_info->name))
	    {
	    	printf("env part do not need to rewrite\n");
			sprite_cartoon_upgrade(20 + rate * (i+1));
	    	continue;
	    }
		else if (!strcmp("sysrecovery", (const char *)part_info->name))
	    {
	    	printf("THIS_IMG_SELF_00 do not need to rewrite\n");
			sprite_cartoon_upgrade(20 + rate * (i+1));
	    	continue;
	    }
		else if (!strcmp("UDISK", (const char *)part_info->name))
	    {
	    	printf("UDISK do not need to rewrite\n");
			sprite_cartoon_upgrade(20 + rate * (i+1));
	    	continue;
	    }
		else if (!strcmp("private", (const char *)part_info->name))
	    {
	    	printf("private do not need to rewrite\n");
			sprite_cartoon_upgrade(20 + rate * (i+1));
	    	continue;
	    }
		else
		{
			ret1 = __download_normal_part(part_info, down_buff);
			if(ret1 != 0)
			{
				printf("sunxi sprite err: sunxi_sprite_deal_part, download normal failed\n");
				goto __sunxi_sprite_deal_part_err2;
			}
		}
		sprite_cartoon_upgrade(20 + rate * (i+1));
		tick_printf("successed in download part %s\n", part_info->name);
	}

	ret = 0;

__sunxi_sprite_deal_part_err1:
	sunxi_sprite_exit(1);

__sunxi_sprite_deal_part_err2:

    if(down_buff)
    {
    	free(down_buff);
    }

    return ret;
}


/*
************************************************************************************************************
*
*                                             function
*
*    name          :  ���������Ҫ��ҪΪ����sprite�ĺ����ӿ�
*
*    parmeters     :
*
*    return        :
*
*    note          :  yanjianbo@allwinnertech.com
*
*
************************************************************************************************************
*/
int __imagehd(HIMAGE tmp_himage)
{
	imghd = tmp_himage;
	if (imghd)
	{
		return 0;
	}
	return -1;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :  ���������Ҫ��ҪΪ����sprite�ĺ����ӿ�
*
*    parmeters     :
*
*    return        :
*
*    note          :  guoyingyang@allwinnertech.com
*
*
************************************************************************************************************
*/
#ifdef CONFIG_SUNXI_SPINOR
extern int sunxi_sprite_setdata_finish(void);
static int __download_fullimg_part(uchar *source_buff)
{
    uint tmp_partstart_by_sector;

    s64  partdata_by_byte;                  //D����a????��?��???��y?Y(��??���̣�??)
    s64  tmp_partdata_by_bytes;

    uint onetime_read_sectors;              //��?��??��D���?����??��y
    uint first_write_bytes;

    uint imgfile_start;                     //��???��y?Y?��?����?����??
    uint tmp_imgfile_start;

    u8 *down_buffer           = source_buff + SPRITE_CARD_HEAD_BUFF;

    int  ret = -1;
    tmp_partstart_by_sector = 0;
    imgitemhd = Img_OpenItem(imghd, "12345678", "FULLIMG_00000000");
    if(!imgitemhd)
    {
        printf("sunxi sprite error: open part FULLIMG failed\n");
        return -1;
    }
    partdata_by_byte = Img_GetItemSize(imghd, imgitemhd);
    if (partdata_by_byte <= 0)
    {
		printf("sunxi sprite error: fetch part len FULLIMG failed\n");
        goto __download_fullimg_part_err1;
    }
    printf("partdata hi 0x%x\n", (uint)(partdata_by_byte>>32));
    printf("partdata lo 0x%x\n", (uint)partdata_by_byte);
    tmp_partdata_by_bytes = partdata_by_byte;
    if(tmp_partdata_by_bytes >= SPRITE_CARD_ONCE_DATA_DEAL)
    {
        onetime_read_sectors = SPRITE_CARD_ONCE_SECTOR_DEAL;
        first_write_bytes    = SPRITE_CARD_ONCE_DATA_DEAL;
    }
    else
    {
        onetime_read_sectors = (tmp_partdata_by_bytes + 511)>>9;
        first_write_bytes    = (uint)tmp_partdata_by_bytes;
    }
    imgfile_start = Img_GetItemStart(imghd, imgitemhd);
    if(!imgfile_start)
    {
        printf("sunxi sprite err : cant get part data imgfile_start FULLIMG\n");

        goto __download_fullimg_part_err1;
    }
    tmp_imgfile_start = imgfile_start;
    if(sunxi_flash_read(tmp_imgfile_start, onetime_read_sectors, down_buffer) != onetime_read_sectors)
    {
        printf("sunxi sprite error : read sdcard block %d, total %d failed\n", tmp_imgfile_start, onetime_read_sectors);
		goto __download_fullimg_part_err1;
    }
    tmp_imgfile_start += onetime_read_sectors;
    if(sunxi_sprite_write(tmp_partstart_by_sector, onetime_read_sectors, down_buffer) != onetime_read_sectors)
    {
		printf("sunxi sprite error: download rawdata error FULLIMG\n");
        goto __download_fullimg_part_err1;
	}
    tmp_partdata_by_bytes   -= first_write_bytes;
    tmp_partstart_by_sector  += onetime_read_sectors;

    while(tmp_partdata_by_bytes >= SPRITE_CARD_ONCE_DATA_DEAL)
    {
        if(sunxi_flash_read(tmp_imgfile_start, SPRITE_CARD_ONCE_SECTOR_DEAL, down_buffer) != SPRITE_CARD_ONCE_SECTOR_DEAL)
        {
            printf("sunxi sprite error : read sdcard block %d, total %d failed\n", tmp_imgfile_start, SPRITE_CARD_ONCE_SECTOR_DEAL);

            goto __download_fullimg_part_err1;
        }
        //D�䨨?flash
        if(sunxi_sprite_write(tmp_partstart_by_sector, SPRITE_CARD_ONCE_SECTOR_DEAL, down_buffer) != SPRITE_CARD_ONCE_SECTOR_DEAL)
        {
			printf("sunxi sprite error: download rawdata error FULLIMG, start 0x%x, sectors 0x%x\n",  tmp_partstart_by_sector, SPRITE_CARD_ONCE_SECTOR_DEAL);
			goto __download_fullimg_part_err1;
        }
        tmp_imgfile_start       += SPRITE_CARD_ONCE_SECTOR_DEAL;
        tmp_partdata_by_bytes   -= SPRITE_CARD_ONCE_DATA_DEAL;
        tmp_partstart_by_sector += SPRITE_CARD_ONCE_SECTOR_DEAL;
    }
    if(tmp_partdata_by_bytes > 0)
    {
        uint rest_sectors = (tmp_partdata_by_bytes + 511)>>9;
        if(sunxi_flash_read(tmp_imgfile_start, rest_sectors, down_buffer) != rest_sectors)
        {
            printf("sunxi sprite error : read sdcard block %d, total %d failed\n", tmp_imgfile_start, rest_sectors);

            goto __download_fullimg_part_err1;
        }
        if(sunxi_sprite_write(tmp_partstart_by_sector, rest_sectors, down_buffer) != rest_sectors)
        {
            printf("sunxi sprite error: download rawdata error FULLIMG, start 0x%x, sectors 0x%x\n",  tmp_partstart_by_sector, rest_sectors);

            goto __download_fullimg_part_err1;
        }
    }
    printf("successed in writting part FULLIMG\n");
    ret = 0;

__download_fullimg_part_err1:
    if(imgitemhd)
    {
        Img_CloseItem(imghd, imgitemhd);
        imgitemhd = NULL;
    }
    return ret;
}

int sunxi_sprite_deal_fullimg(void)
{
    int  ret  = -1;
    int  ret1;
    uchar  *down_buff         = NULL;

    if(sunxi_sprite_init(1))
    {
        printf("sunxi sprite err: init flash err\n");
        return -1;
    }
    down_buff = (uchar *)malloc_noncache(SPRITE_CARD_ONCE_DATA_DEAL + SPRITE_CARD_HEAD_BUFF);
    if(!down_buff)
    {
        printf("sunxi sprite err: unable to malloc memory for sunxi_sprite_deal_part\n");

        goto __sunxi_sprite_deal_fullimg_err1;
    }

    ret1 = __download_fullimg_part(down_buff);
    if(ret1 != 0)
    {
        printf("sunxi sprite err: sunxi_sprite_deal_part, download normal failed\n");

        goto __sunxi_sprite_deal_fullimg_err2;
    }
	//while(*(volatile uint *)0 != 0x12);
    sunxi_sprite_setdata_finish();

    printf("sunxi card sprite trans finish\n");

    ret = 0;

__sunxi_sprite_deal_fullimg_err1:
	sunxi_sprite_exit(1);
__sunxi_sprite_deal_fullimg_err2:
    if(down_buff)
    {
        free_noncache(down_buff);
    }
    return ret;
}
#endif



