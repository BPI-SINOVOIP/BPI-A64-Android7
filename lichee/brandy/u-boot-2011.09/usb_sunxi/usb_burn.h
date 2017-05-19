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

#ifndef  __USB_PBURN_H__
#define  __USB_PBURN_H__

#include <common.h>

#define  SUNXI_USB_PBURN_DEV_MAX       (4)

unsigned char  sunxi_usb_pbur_normal_LangID[8]        = {0x04, 0x03, 0x09, 0x04, '\0'};

unsigned char  sunxi_usb_pburn_iSerialNum0[32] = "20101201120001";

unsigned char  sunxi_usb_pburn_iManufacturer[32] = "AllWinner Technology";

unsigned char  sunxi_usb_pburn_iProduct[32] = "USB Mass Storage";

#define  SUNXI_USB_STRING_LANGIDS			 (0)
#define  SUNXI_USB_STRING_IMANUFACTURER	 	 (1)
#define  SUNXI_USB_STRING_IPRODUCT		 	 (2)
#define  SUNXI_USB_STRING_ISERIALNUMBER    	 (3)

unsigned char  *sunxi_usb_pburn_dev[SUNXI_USB_PBURN_DEV_MAX] = {sunxi_usb_pbur_normal_LangID, 		\
																sunxi_usb_pburn_iSerialNum0, 		\
																sunxi_usb_pburn_iManufacturer, 		\
																sunxi_usb_pburn_iProduct};


const unsigned char  pburn_InquiryData[40]  = {0x00, 0x80, 0x02, 0x02, 0x1f, 										\
										 0x00, 0x00, 0x00, 													\
										 'U',  'S',  'B',  '2',  '.',  '0',  0x00, 0x00, 					\
	                                     'U' , 'S',  'B',  ' ', 'S',  't',  'o' , 'r' , 'a' , 'g' , 'e',	\
	                                     0x00, 0x00, 0x00, 0x00, 0x00,
	                                     '0',  '1',  '0',  '0',  '\0' };

const unsigned char pburn_RequestSense[20] = {0x07,0x00,0x02,0x00,0x00,0x00,0x00,0x0a,0x00,0x00,0x00,0x00,0x3a,0x00,0x00,0x00,0x00,0x00};


#define  SUNXI_USB_PBURN_IDLE					 (0)
#define  SUNXI_USB_PBURN_SETUP					 (1)
#define  SUNXI_USB_PBURN_SEND_DATA				 (2)
#define  SUNXI_USB_PBURN_RECEIVE_DATA			 (3)
#define  SUNXI_USB_PBURN_STATUS					 (4)
#define  SUNXI_USB_PBURN_EXIT                    (5)
#define  SUNXI_USB_PBURN_RECEIVE_NULL			 (6)
#define  SUNXI_USB_PBURN_RECEIVE_PART_INFO		 (7)
#define  SUNXI_USB_PBURN_RECEIVE_PART_VERIFY	 (8)


typedef struct
{
	uchar *base_recv_buffer;		//��Ž��յ������ݵ��׵�ַ�������㹻��
	uint   act_recv_buffer;//
	uint   recv_size;
	uint   to_be_recved_size;
	uchar *base_send_buffer;		//��Ž�Ҫ�������ݵ��׵�ַ�������㹻��
	uint   act_send_buffer;//
	uint   send_size;		//��Ҫ�������ݵĳ���
	uint   flash_start;			//��ʼλ�ã��������ڴ棬Ҳ������flash����
	uint   flash_sectors;
}
pburn_trans_set_t;


typedef struct
{
	char  magic[16];       	//�����ַ������̶��� "usbhandshake"����������
	int   sizelo;			//�̷��ĵ�32λ����λ������
	int   sizehi;			//�̷��ĸ�32λ����λ������
	int   res1;
	int   res2;
}
__usb_handshake_t;

typedef struct
{
	char magic[16];				//�����ַ������̶��� "usbburnpart"����������
	char name[16];				//��д�ķ�������
	unsigned int lenhi;			//�������ݴ�С��32λ,��λ�ֽ�
	unsigned int lenlo;			//�������ݴ�С��32λ,��λ�ֽ�
	char reserved[1024-40]; 	//���ݹ�1024 byte
}__attribute__ ((packed))pburn_partition_set_t;

typedef struct
{
	char magic[16];					//�����ַ������̶��� "usbburnpart"����������
	char name[16];					//��д�ķ�������
	unsigned int check_sum;			//pc�˼���ķ�������У���
	char reserved[1024-36]; 		//���ݹ�1024 byte
}__attribute__ ((packed))pburn_verify_part_set_t;


typedef struct
{
	char  magic[32];       	//�����ַ������̶��� "usbhandshake"����������
}
__usb_handshake_sec_t;

typedef struct
{
	char  magic[64];   //�����ַ���
	int   err_no;
}
__usb_handshake_ext_t;

//������0��û�д���
//������1����������(key)��У�����
//������2��ָ��keyλ�ã��������ݣ��Ѿ�Ҫ�������ظ�
//������3������(key)д��ʧ��
//������4������������
//������5����������̫̫����Ϊ��
//������6����������У��ʧ��
//		7: ������ϢmagicУ��ʧ��
#define ERR_NO_SUCCESS					0
#define ERR_NO_KEY_VERIFY_ERR			1
#define ERR_NO_KEY_HASEXIST				2
#define ERR_NO_WRITE_ERR				3

#define ERR_NO_PART_NOEXIST				4
#define ERR_NO_PART_SIZE_ERR			5
#define ERR_NO_PART_VERIFY_ERR			6
#define ERR_NO_PART_MAGIC_ERR			7

#define ERR_NO_ERASE_KEY_FAILED			8
#define ERR_NO_KEY_NOEXIST				9

#define ERR_NO_READ_KEY_NOEXIST			10
#define ERR_NO_READ_KEY_FAILED			11

typedef struct
{
    //������Ϣ�ظ�����ʾÿ��key����Ϣ
    char  name[64];      //key������
    u32 type;          //0:aes   1:rsa    ������δ֪key
	u32 len;           //key���ݶε��ܳ���
	u32 if_burn;       //�Ƿ���Ҫ��¼��0������Ҫ��1����Ҫ
	u32 if_replace;    //�Ƿ������滻֮ǰ��key��0��������1������
	u32 if_crypt;     //�Ƿ���ҪС���˼��ܴ洢
    u8  *key_data[];   //����һ�����飬���key��ȫ����Ϣ�����ݳ�����lenָ��
}
sunxi_usb_burn_key_info_t;

typedef struct
{
	u8  magic[16];	//����ͷ��ʶ����������"key-group-db"
	u8  hash[256]; //hashֵ(����rootkey��˽Կ�����е�key��Ϣ���м���)
	u32 count;      //key�ĸ���
	u32 res[3];     //����

	sunxi_usb_burn_key_info_t  key_info;
}
sunxi_usb_burn_main_info_t;

#define   SUNXI_PBURN_RECV_MEM_SIZE   (512 * 1024)
#define   SUNXI_PBURN_SEND_MEM_SIZE   (512 * 1024)

#endif

