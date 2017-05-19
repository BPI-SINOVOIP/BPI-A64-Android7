/*
************************************************************************************************************************
*                                                         eGON
*                                         the Embedded GO-ON Bootloader System
*
*                             Copyright(C), 2006-2009, SoftWinners Microelectronic Co., Ltd.
*											       All Rights Reserved
*
* File Name   : load_boot1_from_spinor.h
*
* Author      : Gary.Wang
*
* Version     : 1.1.0
*
* Date        : 2009.12.08
*
* Description :
*
* Others      : None at present.
*
*
* History     :
*
*  <Author>        <time>       <version>      <description>
*
* Gary.Wang      2009.12.08       1.1.0        build the file
*
************************************************************************************************************************
*/
#ifndef  __load_boot1_from_sdmmc_h
#define  __load_boot1_from_sdmmc_h

//SD��������ݽṹ
typedef struct _boot_sdcard_info_t
{
	__s32               card_ctrl_num;                //�ܹ��Ŀ��ĸ���
	__s32				boot_offset;                  //ָ��������֮���߼�����������Ĺ���
	__s32 				card_no[4];                   //��ǰ�����Ŀ���, 16-31:GPIO��ţ�0-15:ʵ�ʿ����������
	__s32 				speed_mode[4];                //�����ٶ�ģʽ��0�����٣�����������
	__s32				line_sel[4];                  //�������ƣ�0: 1�ߣ�������4��
	__s32				line_count[4];                //��ʹ���ߵĸ���
}
boot_sdcard_info_t;
/*******************************************************************************
*��������: load_boot1_from_spinor
*����ԭ�ͣ�int32 load_boot1_from_spinor( void )
*��������: ��һ�ݺõ�Boot1��spi nor flash�����뵽SRAM�С�
*��ڲ���: void
*�� �� ֵ: OK                         ���벢У��ɹ�
*          ERROR                      ���벢У��ʧ��
*��    ע:
*******************************************************************************/
extern __s32 load_boot1_from_sdmmc( char *buf);



#endif     //  ifndef __load_boot1_from_spi_nor_h

/* end of load_boot1_from_spinor.h */
