/*
**********************************************************************************************************************
*
*						           the Embedded Secure Bootloader System
*
*
*						       Copyright(C), 2006-2014, Allwinnertech Co., Ltd.
*                                           All Rights Reserved
*
* File    :
*
* By      :
*
* Version : V2.00
*
* Date	  :
*
* Descript:
**********************************************************************************************************************
*/
#include "common.h"
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
__s32 check_magic( void *mem_base, const char *magic )
{
	standard_boot_file_head_t *bfh = (standard_boot_file_head_t *)mem_base;

	if(!memcmp(magic, (const char *)bfh->magic, strlen(magic)))
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
__s32 check_sum( void *mem_base, __u32 size )
{
	__u32 *buf;
	__u32 count;
	__u32 src_sum;
	__u32 sum;
	standard_boot_file_head_t  *bfh;

	bfh = (standard_boot_file_head_t *)mem_base;
	/* ����У��� */
	src_sum = bfh->check_sum;                  // ��Boot_file_head�еġ�check_sum���ֶ�ȡ��У���
	bfh->check_sum = STAMP_VALUE;              // ��STAMP_VALUEд��Boot_file_head�еġ�check_sum���ֶ�
	count = size >> 2;                         // �� �֣�4bytes��Ϊ��λ����
	sum = 0;
	buf = (__u32 *)mem_base;
	do
	{
		sum += *buf++;                         // �����ۼӣ����У���
		sum += *buf++;                         // �����ۼӣ����У���
		sum += *buf++;                         // �����ۼӣ����У���
		sum += *buf++;                         // �����ۼӣ����У���
	}while( ( count -= 4 ) > (4-1) );
	while( count-- > 0 )
		sum += *buf++;

	bfh->check_sum = src_sum;                  // �ָ�Boot_file_head�еġ�check_sum���ֶε�ֵ
	if( sum == src_sum )
		return 0;               // У��ɹ�
	else
	{
		return -1;                 // У��ʧ��
	}
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
__s32 gen_check_sum( void *boot_buf )
{
	standard_boot_file_head_t  *head_p;
	__u32           length;
	__u32           *buf;
	__u32            loop;
	__u32            i;
	__u32            sum;

	head_p = (standard_boot_file_head_t *)boot_buf;
	length = head_p->length;
	if( ( length & 0x3 ) != 0 )                   // must 4-byte-aligned
		return -1;
	buf = (__u32 *)boot_buf;
	head_p->check_sum = STAMP_VALUE;              // fill stamp
	loop = length >> 2;
    /* ���㵱ǰ�ļ����ݵġ�У��͡�*/
    for( i = 0, sum = 0;  i < loop;  i++ )
    	sum += buf[i];

    /* write back check sum */
    head_p->check_sum = sum;

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
__s32 gen_check_sum_toc0( void *boot_buf )
{
	sbrom_toc0_head_info_t  *head_p;
	__u32           length;
	__u32           *buf;
	__u32            loop;
	__u32            i;
	__u32            sum;

	head_p = (sbrom_toc0_head_info_t *)boot_buf;
	length = head_p->valid_len;
	if( ( length & 0x3 ) != 0 )                   // must 4-byte-aligned
		return -1;
	buf = (__u32 *)boot_buf;
	head_p->add_sum = STAMP_VALUE;              // fill stamp
	loop = length >> 2;
    /* ���㵱ǰ�ļ����ݵġ�У��͡�*/
    for( i = 0, sum = 0;  i < loop;  i++ )
    	sum += buf[i];

    /* write back check sum */
    head_p->add_sum = sum;

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
uint gen_general_checksum(void *buff, uint length)
{
	uint             *buf;
	uint            loop;
	uint            i;
	uint            sum = 0;

	buf = (__u32 *)buff;
	loop = length >> 2;
    /* ���㵱ǰ�ļ����ݵġ�У��͡�*/
    for( i = 0, sum = 0;  i < loop;  i++ )
    	sum += buf[i];

	return sum;
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
uint sunxi_sprite_generate_checksum(void *buffer, uint length, uint src_sum)
{
#ifndef CONFIG_USE_NEON_SIMD
	uint *buf;
	uint count;
	uint sum;

	/* ����У��� */
	count = length >> 2;                       // �� �֣�4bytes��Ϊ��λ����
	sum = 0;
	buf = (__u32 *)buffer;
	do
	{
		sum += *buf++;                         // �����ۼӣ����У���
		sum += *buf++;                         // �����ۼӣ����У���
		sum += *buf++;                         // �����ۼӣ����У���
		sum += *buf++;                         // �����ۼӣ����У���
	}while( ( count -= 4 ) > (4-1) );

	while( count-- > 0 )
		sum += *buf++;
#else
	uint sum;

	sum = add_sum_neon(buffer, length);
#endif
	sum = sum - src_sum + STAMP_VALUE;

    return sum;
}
