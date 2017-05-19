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

#ifndef __GPIO_H__
#define __GPIO_H__

#include "type_def.h"
//ͨ�õģ���GPIO��ص����ݽṹ
typedef struct _normal_gpio_cfg
{
    unsigned char      port;                       //�˿ں�
    unsigned char      port_num;                   //�˿��ڱ��
    char               mul_sel;                    //���ܱ��
    char               pull;                       //����״̬
    char               drv_level;                  //������������
    char               data;                       //�����ƽ
    unsigned char      reserved[2];                //����λ����֤����
}
normal_gpio_cfg;

typedef struct _special_gpio_cfg
{
	unsigned char		port;				//�˿ں�
	unsigned char		port_num;			//�˿��ڱ��
	char				mul_sel;			//���ܱ��
	char				data;				//�����ƽ
}special_gpio_cfg;

#endif    /*  #ifndef __GPIO_H__  */
