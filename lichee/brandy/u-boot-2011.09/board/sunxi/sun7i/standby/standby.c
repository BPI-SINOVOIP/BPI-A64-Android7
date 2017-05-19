/*
 * (C) Copyright 2007-2013
 * Allwinner Technology Co., Ltd. <www.allwinnertech.com>
 * Martin zheng <zhengjiewen@allwinnertech.com>
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
#include <common.h>
#include "standby.h"

#include <boot_type.h>
DECLARE_GLOBAL_DATA_PTR;
static int boot_enter_standby(void);
static int boot_exit_standby(void);
static int boot_standby_detect(void);
static int boot_mod_enter_standby(void);
static int boot_mod_exit_standby(void);
static int boot_early_standby_mode(void);

static int standby_flag = 0;
static int status;
static boot_dram_para_t standby_dram_para;

extern __s32 boot_power_get_key(void);
extern __s32  boot_power_get_dcin_battery_exist(__u32 *dcin_exist, __u32 *battery_exist);
extern __s32 boot_power_battery_charge_status(void);
extern __s32 boot_config_charge_current(int start_time);
extern __s32 boot_power_int_enable(void);
extern __s32 boot_power_set_dcdc2(int set_vol);
extern __s32 boot_power_int_disable(void);
extern __s32 boot_power_int_query(__u8 *int_status);

void standby_get_dram_para(void)
{
    memcpy((void *)&standby_dram_para, uboot_spare_head.boot_data.dram_para, sizeof(boot_dram_para_t));
}
/*
 ************************************************************************************************************
 *
 *                                             function
 *
 *    �������ƣ�
 *
 *    �����б�
 *
 *    ����ֵ  ��
 *
 *    ˵��    ��
 *
 *
 ************************************************************************************************************
 */
int boot_standby_mode(void)
{
   	int status;

	//axp_set_suspend_chgcur();
	boot_store_sp();
	boot_set_sp();

	status = boot_early_standby_mode();
	//standby_serial_putc('[');

	boot_restore_sp();
	//standby_serial_putc('{');

	return status;
}
/*
 ************************************************************************************************************
 *
 *                                             function
 *
 *    �������ƣ�
 *
 *    �����б�
 *
 *    ����ֵ  ��
 *
 *    ˵��    ��
 *
 *
 ************************************************************************************************************
 */
#define   DCDC2_STANDBY_VOL      (1250)
#define   DCDC3_STANDBY_VOL		 (1250)

//#define STANDBY_CHECK_CRC
#ifdef STANDBY_CHECK_CRC
static unsigned int crc_before_standby[8];
static unsigned int test_rang_begin=0x40000000;
static unsigned int test_rang_last =0x43000000;
void standby_before_check_crc(void)
{
    int i, j;
    int *tmp = (int *)test_rang_begin;
    int crc = 0;

    for(i = 0; i < 8; i++)
    {
        crc = 0;
        for(j = 0; j < 128 * 1024 * 1024; j+=32)
        {
            crc += *tmp;
            tmp += 8;
        }
        crc_before_standby[i]=crc;
        //     if (crc != standby_get_from_rtc(8 + i))
        //  {
        //        printf("%d M dram crc err!\n", i*128);
        //    }
        //    else
        //    {
        //        printf("%d M dram crc ok!\n", i*128);
        //    }
    }
}


void standby_after_check_crc(void)
{
    int i, j;
    int *tmp = (int *)test_rang_begin;
    int crc = 0;
    int result =1;
    //printf("check crc!\n");
    printf("test_rang_begin=%x\n",test_rang_begin);
    for(i = 0; i < 8; i++)
    {
        crc = 0;
        for(j = 0; j < 128 * 1024 * 1024; j+=32)
        {
            crc += *tmp;
            tmp += 8;
        }
        if (crc != crc_before_standby[i])
        {   

            printf("%d M dram crc err!\n", i*128);

            //    result =0;
        }
        else
        {
            printf("%d M dram crc ok!\n", i*128);
            // result =1;
        }


    }
    //  if(result)
    //  {
    //       test_rang_begin = ((test_rang_begin-0x40000000)>>1);
    //  }else
    //  {
    //       test_rang_begin +=((test_rang_last-test_rang_begin)>>1);
    //  }
}
#endif

static int boot_enter_standby(void)
{
    volatile int i;
    //	__u8  power_int_status[5];
    //����standby������
    standby_get_dram_para();
    boot_config_charge_current(1);
    //����VBUS��粻����
    //	boot_power_vbus_cur_limit();
    //	//���power���ж�pending
    //	boot_power_int_query(power_int_status);
    //�����ж�
    //__debug("standby int init\n");
    standby_int_init();
    //����clock
    standby_clock_store();
#ifdef STANDBY_CHECK_CRC
    standby_before_check_crc();
#endif
#ifndef CONFIG_AW_FPGA_PLATFORM
    //����dram��֮�������ٷ���dram
    dram_power_save_process(&standby_dram_para);
    //��ֹdrampll���ʹ��
    standby_clock_drampll_ouput(0);
#endif
    //__debug("after standby_clock_plldisable\n");
    //boot_power_set_dcdc3(DCDC3_STANDBY_VOL);
    //ʹ�ܵ�Դ�жϣ��ȴ�����
    boot_power_int_enable();
    //�л���24M
    standby_clock_to_source(24000000);
    //__debug("after standby_clock_to_source\n");
    //�ر�����pll���
    standby_clock_plldisable();
    // __debug("after standby_clock_plldisable\n");

    //���͵�Դ��ѹ���
    boot_power_set_dcdc2(DCDC2_STANDBY_VOL);
	////ʹ�ܵ�Դ�жϣ��ȴ�����
    //boot_power_int_enable();	
    //�л���Ƶ��ȫΪ0
    standby_clock_divsetto0();
    //__debug("after standby_clock_divsetto0 fail\n");
    //�л�apb1��32k
    standby_clock_apb1_to_source(32000);
    //�л�ʱ�ӣ��ر�ʱ��
    for(i=0;i<2000;i++);
    standby_clock_to_source(32000);
    //�ر�24M����
    standby_clock_24m_op(0);
    return 0;
}
/*
 ************************************************************************************************************
 *
 *                                             function
 *
 *    �������ƣ�
 *
 *    �����б�
 *
 *    ����ֵ  ��
 *
 *    ˵��    ��
 *
 *
 ************************************************************************************************************
 */
static int boot_exit_standby(void)
{
    volatile int i;

    ////�رյ�Դ�ж�
    //boot_power_int_disable();
    //���õ�ѹ
    boot_power_set_dcdc2(1400);
    //	boot_power_set_dcdc3(-1);
    //��ԭ����pll��ԭ���򿪵���򿪣�ԭ���رյĲ�����
    standby_clock_restore();
    for(i=0;i<80000;i++);//0x100000
    //�л�ʱ�ӵ�PLL1
    standby_clock_to_source(0);
#ifndef CONFIG_AW_FPGA_PLATFORM
    //��dram���ʹ��
    standby_clock_drampll_ouput(1);
    //����dram
    standby_tmr_enable_watchdog();
    dram_power_up_process(&standby_dram_para);
    standby_tmr_disable_watchdog();
#endif
#ifdef STANDBY_CHECK_CRC
    standby_after_check_crc();
#endif
    //�رյ�Դ�ж�
    boot_power_int_disable();
    //��ԭ�ж�״̬
    standby_int_exit();
    //��ԭ������
    boot_config_charge_current(0);
    //��ԭ���е�����ģ��
    //boot_timer_delay(50);

    return 0;
}
/*
 ************************************************************************************************************
 *
 *                                             function
 *
 *    �������ƣ�
 *
 *    �����б�
 *
 *    ����ֵ  ��
 *
 *    ˵��    ��
 *
 *
 ************************************************************************************************************
 */
static int boot_standby_detect(void)
{
    volatile int i;
    __u32 dcin_exist, battery_exist;
    __u8  power_int_status[5];

    //����24M����
    standby_clock_24m_op(1);
    //�л�apb1��24M
    standby_clock_apb1_to_source(24000000);
    //����Ƶ�ʵ�24M
    standby_clock_to_source(24000000);
    standby_clock_divsetback();
    for(i=0;i<2000;i++);
    //����жϴ���
    boot_power_int_query(power_int_status);
    //����жϿ�������pending
    //	standby_int_query();
    //	if(boot_key_get_status() == 1)			//��ͨADC��������
    //	{
    //		return 1;
    //	}
    //	boot_power_vbus_unlimit();
    if(power_int_status[2] & 0x02)			//��Դ�����̰�
    {
        return 2;
    }
    if(power_int_status[2] & 0x01)			//��Դ��������
    {
        return 3;
    }
    dcin_exist = 100;
    battery_exist = 100;
    boot_power_get_dcin_battery_exist(&dcin_exist, &battery_exist);
    if(!dcin_exist)							//�ⲿ��Դ�Ƴ�
    {
        return 4;
    }
    //	if((power_int_status[1] & 0x04) && (battery_exist==1))			//������
    //	{
    //		return 5;
    //	}
    if(power_int_status[0] & 0x08)			//usb��ţ����
    {
        return 8;
    }
    //if(power_int_status[0] & 0x04)			//usb��ţ�Ƴ�
    //{
    //    return 9;
    //}
    //��ԭ��32K
    standby_clock_divsetto0();
    standby_clock_apb1_to_source(32000);
    standby_clock_to_source(32000);
    standby_clock_24m_op(0);

    return 0;
}
extern int drv_disp_standby(uint cmd, void *pArg);

typedef int (* boot_mod_standby)(uint cmd, void *pArg);

static int boot_mod_enter_standby(void)
{
	uint addr;
	boot_mod_standby  mod_func;

	addr = (uint)drv_disp_standby + gd->reloc_off;
	mod_func = (boot_mod_standby)addr;

	mod_func(MOD_ENTER_STANDBY, 0);

	return 0;
}
/*
************************************************************************************************************
*
*                                             function
*
*    �������ƣ�
*
*    �����б�
*
*    ����ֵ  ��
*
*    ˵��    ��
*
*
************************************************************************************************************
*/
static int boot_mod_exit_standby(void)
{
	uint addr;
	boot_mod_standby  mod_func;

	addr = (uint)drv_disp_standby + gd->reloc_off;
	mod_func = (boot_mod_standby)addr;

	mod_func(MOD_EXIT_STANDBY, 0);

	return 0;
}


/*
************************************************************************************************************
*
*                                             function
*
*    �������ƣ�
*
*    �����б�
*
*    ����ֵ  ��
*
*    ˵��    ��
*
*
************************************************************************************************************
*/
static int boot_early_standby_mode(void)
{
	__u32 dcin_exist, battery_exist;//, charge_status;
	__s32 key_status, usb_status;
    status = -1;	
    //����Ƿ��а�������
    key_status = boot_power_get_key();
    if(key_status & 0x01)			//������������£����ܵ�Դ�Ƿ��Ƴ���ֱ�ӽ���ϵͳ
    {
        //�˳����е�����ģ��
        if(standby_flag)
        {
            boot_mod_exit_standby();
        }
        return 3;
    }
    //����ⲿ��Դ�Ƿ����
    dcin_exist = 100;
    battery_exist = 100;
    boot_power_get_dcin_battery_exist(&dcin_exist, &battery_exist);
    if(!dcin_exist)							//�ⲿ��Դ�Ƴ�
    {
        //�˳����е�����ģ��
        if(standby_flag)
        {
            boot_mod_exit_standby();
        }
        return 4;
    }
   	//charge_status = boot_power_battery_charge_status();		//������
  	//	if((charge_status > 0) && (battery_exist == 1))
   	//{
   	//    //�˳����е�����ģ��
   	//	if(standby_flag)
   	//	{
   	//		boot_mod_exit_standby();
   	//	}
   	//	return 5;
   	//}

    if(key_status & 0x02)			//���ⲿ��Դ���ڣ�����standby
    {
        //�˳����е�����ģ��
        if(standby_flag)
        {
            boot_mod_exit_standby();
        }
        return 2;
    }
    if(!standby_flag)
    {
        boot_mod_enter_standby();
    }

	//����Ƿ���USB��Դ����
	usb_status = standby_axp_probe_usb();

	if(usb_status > 0)
	{
		return 8;
	}
	status = -1;
	boot_enter_standby();
    //���ڣ�clock������32k��
    do
    {
        //��ʼѭ������Ƿ�ʼ����
        boot_halt();
        status = boot_standby_detect();
    }
    while(status <= 0);
    //������Ҫ���ѣ��˳�standby
    boot_exit_standby();
    //�˳�ģ���standby
    if((status != 8) && (status != 9))
    {
        boot_mod_exit_standby();
        standby_flag = 0;
    }
    else
    {
        standby_flag = 1;
    }  
    return status;
}




/*
 ************************************************************************************************************
 *
 *                                             function
 *
 *    �������ƣ�
 *
 *    �����б�
 *
 *    ����ֵ  ��
 *
 *    ˵��    ��
 *
 *
 ************************************************************************************************************
 */
/*
static int boot_mod_enter_standby(void)
{
    int i;

    for(i=0;i<EMOD_COUNT_MAX;i++)
    {
        boot_driver_standby(i, MOD_ENTER_STANDBY, 0);
    }

    return 0;
}
*/
/*
 ************************************************************************************************************
 *
 *                                             function
 *
 *    �������ƣ�
 *
 *    �����б�
 *
 *    ����ֵ  ��
 *
 *    ˵��    ��
 *
 *
 ************************************************************************************************************
 */
/*
static int boot_mod_exit_standby(void)
{
    int i;

    for(i=0;i<EMOD_COUNT_MAX;i++)
    {
        boot_driver_standby(i, MOD_EXIT_STANDBY, 0);
    }

    return 0;
}
*/
