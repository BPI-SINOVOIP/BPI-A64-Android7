/*
************************************************************************************************************************
*                                                         eGON
*                                         the Embedded GO-ON Bootloader System
*
*                             Copyright(C), 2006-2008, SoftWinners Microelectronic Co., Ltd.
*											       All Rights Reserved
*
* File Name : Boot0_head.c
*
* Author : Gary.Wang
*
* Version : 1.1.0
*
* Date : 2007.11.06
*
* Description : This file defines the file head part of Boot0, which contains some important
*             infomations such as magic, platform infomation and so on, and MUST be allocted in the
*             head of Boot0.
*
* Others : None at present.
*
*
* History :
*
*  <Author>        <time>       <version>      <description>
*
* Gary.Wang       2007.11.06      1.1.0        build the file
*
************************************************************************************************************************
*/
#include "types.h"
#include "boot0_v2.h"

#pragma  arm section  rodata="fes1_head"

#define   DDR3_USED

const boot0_file_head_t  fes1_head = {
	                                  {
	      /* jump_instruction */          ( 0xEA000000 | ( ( ( sizeof( boot0_file_head_t ) + sizeof( int ) - 1 ) / sizeof( int ) - 2 ) & 0x00FFFFFF ) ),
							   		      BOOT0_MAGIC,
							   		      STAMP_VALUE,
							   		      32,
							   		      sizeof( boot_file_head_t ),
							   		      BOOT_PUB_HEAD_VERSION,
							   		      FES1_DRAM_RET_ADDR,
							   		      FES1_RUN_ADDR,
							   		      EGON_VERSION,
							   		      {
							   		      	0, 0, '2','.','0','.','0',0
							   		      },
							 	      },
#ifdef  DDR3_USED
							 	      {
							 	          sizeof( boot0_private_head_t ),
							 	          { 0 },
							 	          { 0 },
							 	          0,
										  {
										  	{ 2, 22, 4, 1, 1, 0, 0, 0},
										  	{ 2, 23, 4, 1, 1, 0, 0, 0}
										  },
							 	          0,
							 	          { 0 },
							 	          { 0 },
							 	          { 0 }
							 	      }
#else
							 	      {
							 	          sizeof( boot0_private_head_t ),
							 	          BOOT0_PRVT_HEAD_VERSION,
							 	          { 0x40000000,
							 	            1024,
							 	            180,
							 	            1,
							 	            1,
							 	            0,
							 	            (__dram_type_e)1,
							 	            16,
							 	            10,
							 	            14,
							 	            4,
							 	            3,
							 	            0,
							 	            16,
							 	            1024
							 	          },
							 	          0,
										  {
										  	{ 2, 22, 4, 1, 1, 0, 0, 0},
										  	{ 2, 23, 4, 1, 1, 0, 0, 0}
										  },
							 	          0,
							 	          { 0 },
							 	          { 0 },
							 	          { 0 }
							 	      }
#endif
							 	  };



/*******************************************************************************
*
*                  ����Boot_file_head�е�jump_instruction�ֶ�
*
*  jump_instruction�ֶδ�ŵ���һ����תָ�( B  BACK_OF_Boot_file_head )������
*תָ�ִ�к󣬳�����ת��Boot_file_head�����һ��ָ�
*
*  ARMָ���е�Bָ��������£�
*          +--------+---------+------------------------------+
*          | 31--28 | 27--24  |            23--0             |
*          +--------+---------+------------------------------+
*          |  cond  | 1 0 1 0 |        signed_immed_24       |
*          +--------+---------+------------------------------+
*  ��ARM Architecture Reference Manual�����ڴ�ָ�������½��ͣ�
*  Syntax :
*  B{<cond>}  <target_address>
*    <cond>    Is the condition under which the instruction is executed. If the
*              <cond> is ommitted, the AL(always,its code is 0b1110 )is used.
*    <target_address>
*              Specified the address to branch to. The branch target address is
*              calculated by:
*              1.  Sign-extending the 24-bit signed(wro's complement)immediate
*                  to 32 bits.
*              2.  Shifting the result left two bits.
*              3.  Adding to the contents of the PC, which contains the address
*                  of the branch instruction plus 8.
*
*  �ɴ˿�֪����ָ���������8λΪ��0b11101010����24λ����Boot_file_head�Ĵ�С��
*̬���ɣ�����ָ�����װ�������£�
*  ( sizeof( boot_file_head_t ) + sizeof( int ) - 1 ) / sizeof( int )
*                                              ����ļ�ͷռ�õġ��֡��ĸ���
*  - 2                                         ��ȥPCԤȡ��ָ������
*  & 0x00FFFFFF                                ���signed-immed-24
*  | 0xEA000000                                ��װ��Bָ��
*
*******************************************************************************/

