/*
**********************************************************************************************************************
*											        eGon
*						           the Embedded GO-ON Bootloader System
*									       eGON arm boot sub-system
*
*						  Copyright(C), 2006-2010, SoftWinners Microelectronic Co., Ltd.
*                                           All Rights Reserved
*
* File    : pin_ops.c
*
* By      : Jerry
*
* Version : V2.00
*
* Date	  :
*
* Descript:
**********************************************************************************************************************
*/
#include "types.h"
#include "arch.h"
#include "egon_def.h"


/*
************************************************************************************************************
*
*                                             normal_gpio_cfg
*
*    �������ƣ�
*
*    �����б�
*
*
*
*    ����ֵ  ��
*
*    ˵��    ��
*
*
************************************************************************************************************
*/
__s32 boot_set_gpio(void  *user_gpio_list, __u32 group_count_max, __s32 set_gpio)
{
#if 1
	normal_gpio_cfg    *tmp_user_gpio_data, *gpio_list;
	__u32				first_port;                      //����������Ч��GPIO�ĸ���
	__u32               tmp_group_func_data = 0;
	__u32               tmp_group_pull_data = 0;
	__u32               tmp_group_dlevel_data = 0;
	__u32               tmp_group_data_data = 0;
	__u32               data_change = 0;
	__u32			   *tmp_group_port_addr = NULL;
	__u32     		   *tmp_group_func_addr = NULL,   *tmp_group_pull_addr = NULL;
	__u32     		   *tmp_group_dlevel_addr = NULL, *tmp_group_data_addr = NULL;
	__u32  				port, port_num, port_num_func, port_num_pull;
	__u32  				pre_port = 0, pre_port_num_func = 0;
	__u32  				pre_port_num_pull = 0;
	__s32               i, tmp_val;

    //׼����һ��GPIO����
    gpio_list = user_gpio_list;

    for(first_port = 0; first_port < group_count_max; first_port++)
    {
        tmp_user_gpio_data = gpio_list + first_port;
        port     = tmp_user_gpio_data->port;                         //�����˿���ֵ
	    port_num = tmp_user_gpio_data->port_num;                     //�����˿��е�ĳһ��GPIO
	    if(!port)
	    {
	    	continue;
	    }
	    port_num_func = (port_num >> 3);
        port_num_pull = (port_num >> 4);

		tmp_group_port_addr    = PIO_REG_BASE(port);

		tmp_group_func_addr    = tmp_group_port_addr + port_num_func;						  //���¹��ܼĴ�����ַ
		tmp_group_pull_addr    = tmp_group_port_addr + (PIOC_REG_o_PUL0>>2) + port_num_pull;  //����pull�Ĵ���
		tmp_group_dlevel_addr  = tmp_group_port_addr + (PIOC_REG_o_DRV0>>2) + port_num_pull;  //����driver level�Ĵ���
		tmp_group_data_addr    = tmp_group_port_addr + (PIOC_REG_o_DATA>>2); 				  //����data�Ĵ���

        tmp_group_func_data    = readl(tmp_group_func_addr);
        tmp_group_pull_data    = readl(tmp_group_pull_addr);
        tmp_group_dlevel_data  = readl(tmp_group_dlevel_addr);
        tmp_group_data_data    = readl(tmp_group_data_addr);

        pre_port          = port;
        pre_port_num_func = port_num_func;
        pre_port_num_pull = port_num_pull;
        //���¹��ܼĴ���
        tmp_val = (port_num - (port_num_func << 3)) << 2;
        tmp_group_func_data &= ~(                              0x07  << tmp_val);
        if(set_gpio)
        {
        	tmp_group_func_data |=  (tmp_user_gpio_data->mul_sel & 0x07) << tmp_val;
        }
        //����pull��ֵ�����Ƿ����pull�Ĵ���
        tmp_val              =  (port_num - (port_num_pull << 4)) << 1;
        if(tmp_user_gpio_data->pull >= 0)
        {
        	tmp_group_pull_data &= ~(                           0x03  << tmp_val);
        	tmp_group_pull_data |=  (tmp_user_gpio_data->pull & 0x03) << tmp_val;
        }
        //����driver level��ֵ�����Ƿ����driver level�Ĵ���
        if(tmp_user_gpio_data->drv_level >= 0)
        {
        	tmp_group_dlevel_data &= ~(                                0x03  << tmp_val);
        	tmp_group_dlevel_data |=  (tmp_user_gpio_data->drv_level & 0x03) << tmp_val;
        }
        //�����û����룬�Լ����ܷ�������Ƿ����data�Ĵ���
        if(tmp_user_gpio_data->mul_sel == 1)
        {
            if(tmp_user_gpio_data->data >= 0)
            {
            	tmp_val = tmp_user_gpio_data->data & 1;
                tmp_group_data_data &= ~(1 << port_num);
                tmp_group_data_data |= tmp_val << port_num;
                data_change = 1;
            }
        }

        break;
	}
	//����Ƿ������ݴ���
	if(first_port >= group_count_max)
	{
	    return -1;
	}
	//�����û�����
	for(i = first_port + 1; i < group_count_max; i++)
	{
		tmp_user_gpio_data = gpio_list + i;                 //gpio_set����ָ���û���ÿ��GPIO�����Ա
	    port     = tmp_user_gpio_data->port;                //�����˿���ֵ
	    port_num = tmp_user_gpio_data->port_num;            //�����˿��е�ĳһ��GPIO
	    if(!port)
	    {
	    	break;
	    }
        port_num_func = (port_num >> 3);
        port_num_pull = (port_num >> 4);

        if((port_num_pull != pre_port_num_pull) || (port != pre_port))    //������ֵ�ǰ���ŵĶ˿ڲ�һ�£��������ڵ�pull�Ĵ�����һ��
        {
            writel(tmp_group_func_data, tmp_group_func_addr);     //��д���ܼĴ���
            writel(tmp_group_pull_data, tmp_group_pull_addr);     //��дpull�Ĵ���
            writel(tmp_group_dlevel_data, tmp_group_dlevel_addr); //��дdriver level�Ĵ���
            if(data_change)
            {
                data_change = 0;
                writel(tmp_group_data_data, tmp_group_data_addr); //��дdata�Ĵ���
            }

			tmp_group_port_addr    = PIO_REG_BASE(port);

			tmp_group_func_addr    = tmp_group_port_addr + port_num_func;						  //���¹��ܼĴ�����ַ
			tmp_group_pull_addr    = tmp_group_port_addr + (PIOC_REG_o_PUL0>>2) + port_num_pull;  //����pull�Ĵ���
			tmp_group_dlevel_addr  = tmp_group_port_addr + (PIOC_REG_o_DRV0>>2) + port_num_pull;  //����driver level�Ĵ���
			tmp_group_data_addr    = tmp_group_port_addr + (PIOC_REG_o_DATA>>2); 				  //����data�Ĵ���

            tmp_group_func_data    = readl(tmp_group_func_addr);
            tmp_group_pull_data    = readl(tmp_group_pull_addr);
            tmp_group_dlevel_data  = readl(tmp_group_dlevel_addr);
            tmp_group_data_data    = readl(tmp_group_data_addr);
        }
        else if(pre_port_num_func != port_num_func)                       //������ֵ�ǰ���ŵĹ��ܼĴ�����һ��
        {
            writel(tmp_group_func_data, tmp_group_func_addr);    //��ֻ��д���ܼĴ���
            tmp_group_func_addr    = PIO_REG_CFG(port, port_num_func);   //���¹��ܼĴ�����ַ

            tmp_group_func_data    = readl(tmp_group_func_addr);
        }
		//���浱ǰӲ���Ĵ�������
        pre_port_num_pull = port_num_pull;                      //���õ�ǰGPIO��Ϊǰһ��GPIO
        pre_port_num_func = port_num_func;
        pre_port          = port;

        //���¹��ܼĴ���
        tmp_val = (port_num - (port_num_func << 3)) << 2;
        if(tmp_user_gpio_data->mul_sel)
        {
        	tmp_group_func_data &= ~(                              0x07  << tmp_val);
        	if(set_gpio)
        	{
        		tmp_group_func_data |=  (tmp_user_gpio_data->mul_sel & 0x07) << tmp_val;
        	}
        }
        //����pull��ֵ�����Ƿ����pull�Ĵ���
        tmp_val              =  (port_num - (port_num_pull << 4)) << 1;
        if(tmp_user_gpio_data->pull >= 0)
        {
        	tmp_group_pull_data &= ~(                           0x03  << tmp_val);
        	tmp_group_pull_data |=  (tmp_user_gpio_data->pull & 0x03) << tmp_val;
        }
        //����driver level��ֵ�����Ƿ����driver level�Ĵ���
        if(tmp_user_gpio_data->drv_level >= 0)
        {
        	tmp_group_dlevel_data &= ~(                                0x03  << tmp_val);
        	tmp_group_dlevel_data |=  (tmp_user_gpio_data->drv_level & 0x03) << tmp_val;
        }
        //�����û����룬�Լ����ܷ�������Ƿ����data�Ĵ���
        if(tmp_user_gpio_data->mul_sel == 1)
        {
            if(tmp_user_gpio_data->data >= 0)
            {
            	tmp_val = tmp_user_gpio_data->data & 1;
                tmp_group_data_data &= ~(1 << port_num);
                tmp_group_data_data |= tmp_val << port_num;
                data_change = 1;
            }
        }
    }
    //forѭ��������������ڻ�û�л�д�ļĴ���������д�ص�Ӳ������
    if(tmp_group_func_addr)                         //ֻҪ���¹��Ĵ�����ַ���Ϳ��Զ�Ӳ����ֵ
    {                                               //��ô�����е�ֵȫ����д��Ӳ���Ĵ���
        writel(tmp_group_func_data, tmp_group_func_addr);   //��д���ܼĴ���
        writel(tmp_group_pull_data, tmp_group_pull_addr);   //��дpull�Ĵ���
        writel(tmp_group_dlevel_data, tmp_group_dlevel_addr); //��дdriver level�Ĵ���
        if(data_change)
        {
            writel(tmp_group_data_data, tmp_group_data_addr); //��дdata�Ĵ���
        }
    }
#endif
    return 0;
}




