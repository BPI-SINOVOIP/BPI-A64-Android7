/*
 * (C) Copyright 2012
 *     wangflord@allwinnertech.com
 *
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program;
 *
 */
#include "types.h"
#include "arch.h"
/*******************************************************************************
*��������: set_pll
*����ԭ�ͣ�void set_pll( void )
*��������: Boot0����C���Ա�д�� ����CPUƵ��
*��ڲ���: void
*�� �� ֵ: void
*��    ע:
*******************************************************************************/
static void set_pll( void )
{
	__u32 reg_val, i;

	//�л���24M
	writel(0x00010010, CCMU_REG_CPUAHBAPB0_RATIO);

	//����PLL1��384M
	reg_val = (0x21005000) | (0x80000000);
	writel(reg_val, CCMU_REG_PLL1_CTRL);
	//��ʱ
	for(i=0;i<200;i++);
	//�л���PLL1
	reg_val = readl(CCMU_REG_CPUAHBAPB0_RATIO);
	reg_val &= ~(3 << 16);
	reg_val |=  (2 << 16);
	writel(reg_val, CCMU_REG_CPUAHBAPB0_RATIO);

	//��DMA
	reg_val = readl(CCMU_REG_AHB1_GATING0);
	reg_val |= 1<<6;
	writel(reg_val, CCMU_REG_AHB1_GATING0);
	//��GPIO
	reg_val = readl(CCMU_REG_APB0_GATING);
	reg_val |= 1<<5;
	writel(reg_val, CCMU_REG_APB0_GATING);

	return ;
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
void pll_reset( void )
{
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
void timer_init(void)
{
	writel(readl(CCMU_REG_AVS) | (1U << 31), CCMU_REG_AVS);

	writel(3, TMRC_AVS_CTRL);
	writel(0xC2EE0, TMRC_AVS_DIVISOR);
	writel(0, TMRC_AVS_COUNT0);
	writel(0, TMRC_AVS_COUNT1);
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
void __msdelay(__u32 ms)
{
	__u32 t1, t2;

	t1 = readl(TMRC_AVS_COUNT0);
	t2 = t1 + ms;
	do
	{
		t1 = readl(TMRC_AVS_COUNT0);
	}
	while(t2 >= t1);

	return ;
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
void __usdelay(__u32 us)
{
	__u32 t1, t2;

	t1 = readl(TMRC_AVS_COUNT1);
	t2 = t1 + us;
	do
	{
		t1 = readl(TMRC_AVS_COUNT1);
	}
	while(t2 >= t1);

	return ;
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
void timer_exit(void)
{
	writel(0, TMRC_AVS_CTRL);
	writel(0x2EE0, TMRC_AVS_DIVISOR);
	writel(0, TMRC_AVS_COUNT0);

	writel(readl(CCMU_REG_AVS) & (~(1U << 31)), CCMU_REG_AVS);
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
void bias_calibration(void)
{
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
void cpu_init_s(void)
{
	set_pll();
}

//---------for rtc --------
#define msg(fmt,args...)				UART_printf2(fmt ,##args)

unsigned int  get_fel_flag(void)
{
    unsigned int fel_flag;
    fel_flag = *(volatile unsigned int *)(0x01f00000 + 0x108);
    return fel_flag;
}

void show_rtc_reg(void)
{
    unsigned int reg_val;
    int index = 0;
	volatile unsigned int *reg_addr = 0;
	while(index < 0x18)
	{
		reg_addr = (volatile unsigned int *)(0x01f00000 + 0x100 + index);
		reg_val = *reg_addr;
		msg("reg_addr %x =%x\n", reg_addr, reg_val);
		index+=0x4;
	}
	
}

void  clear_fel_flag(void)
{
    //clear fel flag maked by uboot
    *(volatile unsigned int *)(0x01f00000 + 0x108) = 0;
}
