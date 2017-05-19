/*
************************************************************************************************************************
*                                                         eGON
*                                         the Embedded GO-ON Bootloader System
*
*                             Copyright(C), 2006-2008, SoftWinners Microelectronic Co., Ltd.
*											       All Rights Reserved
*
* File Name : load_Boot1_from_nand.c
*
* Author : Gary.Wang
*
* Version : 1.1.0
*
* Date : 2007.10.14
*
* Description : This file provides a function "load_Boot1_from_nand" to load a good copy of Boot1
*             from outside nand flash chips to SRAM.
*
* Others : None at present.
*
*
* History :
*
*  <Author>        <time>       <version>      <description>
*
* Gary.Wang       2007.10.14      1.1.0        build the file
*
************************************************************************************************************************
*/
#include "load_Boot1_from_nand_i.h"
#include "spare_head.h"

/*******************************************************************************
*��������: load_Boot1_from_nand
*����ԭ�ͣ�int32 load_Boot1_from_nand( void )
*��������: ��һ�ݺõ�Boot1��nand flash�����뵽SRAM�С�
*��ڲ���: void
*�� �� ֵ: OK                         ���벢У��ɹ�
*          ERROR                      ���벢У��ʧ��
*��    ע:
*******************************************************************************/
__s32 load_Boot1_from_nand( void )
{
    __u32 i;
    __s32  status;
    __u32 length;
    __u32 read_blks;
	uboot_file_head  *bfh;


	if(NF_open( ) == NF_ERROR)                         // ��nand flash
	{
		msg("fail in opening nand flash\n");

		return ERROR;
	}
	//msg("Succeed in opening nand flash.\n");
	//msg("block from %d to %d\n", BOOT1_START_BLK_NUM, BOOT1_LAST_BLK_NUM);
    for( i = BOOT1_START_BLK_NUM;  i <= BOOT1_LAST_BLK_NUM;  i++ )
    {
    	if( NF_read_status( i ) == NF_BAD_BLOCK )		// �����ǰ���ǻ��飬�������һ��
    	{
    		msg("nand block %d is bad\n", i);
            continue;
		}
        /* ���뵱ǰ����ǰ��512�ֽڵ����ݵ�SRAM�У�Ŀ���ǻ�ȡ�ļ�ͷ */
        if( NF_read( i << ( NF_BLK_SZ_WIDTH - NF_SCT_SZ_WIDTH ), (void *)UBOOT_BASE, 1 )  == NF_OVERTIME_ERR )
        {
		    msg("the first data is error\n");
			continue;
		}
		//msg("Succeed in reading Boot1 file head.\n");

		/* �쿴�Ƿ����ļ�ͷ */
		if( check_magic( (__u32 *)UBOOT_BASE, UBOOT_MAGIC ) != CHECK_IS_CORRECT )
		{
			msg("ERROR! block %u doesn't store head of Boot1 copy.\n", i );			
			continue;
		}
		
					        	
		bfh = (uboot_file_head *) UBOOT_BASE;
        length =  bfh->boot_head.length;
        //msg("The size of Boot1 is %x.\n", length );
        //msg("The align size of Boot1 is %x.\n", NF_SECTOR_SIZE );
        if( ( length & ( NF_SECTOR_SIZE - 1 ) ) != 0 )     // length������NF_SECTOR_SIZE�����
        {
            msg("the boot1 is not aligned by %x\n", bfh->boot_head.align_size);
        	continue;
		}
		//msg("The size of Boot1 is %x.\n", length );
        if( length <=  NF_BLOCK_SIZE )
        {
        	/* ��һ����������Boot1�ı��� */
        	status = load_and_check_in_one_blk( i, (void *)UBOOT_BASE, length, NF_BLOCK_SIZE, UBOOT_MAGIC );
        	if( status == ADV_NF_OVERTIME_ERR )            // ��������
        		continue;
        	else if( status == ADV_NF_OK )
        	{
                //msg("Check is correct.\n");
                bfh->boot_data.storage_type = 0;
                NF_close( );                        // �ر�nand flash
                return OK;
            }
        }
        else
        {
        	/* �Ӷ����������һ��Boot1�ı��� */     	
        	status = load_in_many_blks( i, BOOT1_LAST_BLK_NUM, (void*)UBOOT_BASE,
        								length, NF_BLOCK_SIZE, &read_blks );
        	if( status == ADV_NF_LACK_BLKS )        // ��������
        	{
        		msg("ADV_NF_LACK_BLKS\n");
        		NF_close( );                        // �ر�nand flash
        		return ERROR;
        	}
        	else if( status == ADV_NF_OVERTIME_ERR )
        	{
        		msg("mult block ADV_NF_OVERTIME_ERR\n");
        		continue;
			}
            if( check_sum( (__u32 *)UBOOT_BASE, length ) == CHECK_IS_CORRECT )
            {
                msg("The file stored in start block %u is perfect.\n", i );
                bfh->boot_data.storage_type = 0;
                NF_close( );                        // �ر�nand flash
                return OK;
            }
        }
    }


	msg("Can't find a good Boot1 copy in nand.\n");
    NF_close( );                        // �ر�nand flash
    msg("Ready to quit \"load_Boot1_from_nand\".\n");
    return ERROR;
}

