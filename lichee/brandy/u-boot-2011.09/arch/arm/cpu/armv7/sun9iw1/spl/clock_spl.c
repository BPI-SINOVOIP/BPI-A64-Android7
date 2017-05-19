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
#include "asm/io.h"
#include "asm/armv7.h"
#include "asm/arch/cpu.h"
#include "asm/arch/ccmu.h"
#include "asm/arch/timer.h"


static void set_pll_bias(void)
{
    __u32 reg_val = 0 ;
    __u32 i = 0 ;
    __u32 reg_addr = CCM_PLL_C0CPUX_BIAS;
    for(i = 0;i<12;i++)
    {
        reg_val = readl(reg_addr + (i<<2));
        reg_val &= ~(0x1f<<16);
        reg_val |= 0x04<<16;
        writel(reg_val,reg_addr + (i<<2));
    }
}
/*******************************************************************************
*��������: gtbus
*����ԭ�ͣ�void gtbus( void )
*��������: Boot0����C���Ա�д�� ������ʾ�����ȼ�
*��ڲ���: void
*�� �� ֵ: void
*��    ע:
*******************************************************************************/
static void gtbus_init(void)
{
    writel(0xff0     ,GT_MST_READ_PROI_CFG_REG0);
    writel(0x3fffffff,GT_MST_CFG_REG(4));
    writel(0x3fffffff,GT_MST_CFG_REG(5));
    writel(0x3fffffff,GT_MST_CFG_REG(6));
    writel(0x3fffffff,GT_MST_CFG_REG(7));
    writel(0x3fffffff,GT_MST_CFG_REG(8));
    writel(0x3fffffff,GT_MST_CFG_REG(9));
    writel(0x3fffffff,GT_MST_CFG_REG(10));
    writel(0x3fffffff,GT_MST_CFG_REG(11));
}
/*******************************************************************************
*��������: set_pll
*����ԭ�ͣ�void set_pll( void )
*��������: ����CPUƵ��
*��ڲ���: void
*�� �� ֵ: void
*��    ע:
*******************************************************************************/
void set_pll( void )
{
	__u32 reg_val;

	//�л���24M
	reg_val = readl(CCM_CPU_SOURCECTRL);
	reg_val &= ~1;
	writel(reg_val, CCM_CPU_SOURCECTRL);

	__usdelay(10);
	CP15DMB;
	CP15ISB;
	//����PLL_P1=1200
	reg_val = readl(CCM_PLL12_PERP1_CTRL);
	if(!(reg_val & 0x80000000))
	{
		writel(0x00003200, CCM_PLL12_PERP1_CTRL);
		do
		{
			__usdelay(10);
		}
		while(readl(CCM_PLL12_PERP1_CTRL) != 0x00003200);
		writel(0x80003200, CCM_PLL12_PERP1_CTRL);
	}

	//����PLL_C0CPUX��408M
	writel(0x02001100, CCM_PLL1_C0_CTRL);
	do
	{
		__usdelay(10);
	}
	while(readl(CCM_PLL1_C0_CTRL) != 0x02001100);
	writel(0x82001100, CCM_PLL1_C0_CTRL);

	//����PLL_C1CPUX��408M
	writel(0x02001100, CCM_PLL2_C1_CTRL);
	do
	{
		__usdelay(10);
	}
	while(readl(CCM_PLL2_C1_CTRL) != 0x02001100);
	writel(0x82001100, CCM_PLL2_C1_CTRL);

	//����PLL_P0=960
	reg_val = readl(CCM_PLL4_PERP0_CTRL);
	if(!(reg_val & 0x80000000))
	{
		writel(0x00002800, CCM_PLL4_PERP0_CTRL);
		do
		{
			__usdelay(10);
		}
		while(readl(CCM_PLL4_PERP0_CTRL) != 0x00002800);
		writel(0x80002800, CCM_PLL4_PERP0_CTRL);
	}
	else
	{
		writel(0x80002800, CCM_PLL4_PERP0_CTRL);
	}
	__usdelay(5000);

	CP15DMB;
	CP15ISB;
	//����AXI0 & ATB0 ��Ƶ 2:2
	writel(0x101, CCM_CLUSTER0_AXI_RATIO);
	__usdelay(50);
	CP15DMB;
	CP15ISB;
	//����AHB0��ʱ��ԴΪPLL_P0����Ƶ=2����AHB0=960/8=120
	writel(0x00000003, CCM_AHB0_RATIO_CTRL);
	do
	{
		__usdelay(10);
	}
	while(readl(CCM_AHB0_RATIO_CTRL) != 0x00000003);
	writel(0x01000003, CCM_AHB0_RATIO_CTRL);
	//����AHB1��ʱ��ԴΪPLL_P0����Ƶ=2����AHB1=960/4=240
	writel(0x00000002, CCM_AHB1_RATIO_CTRL);
	do
	{
		__usdelay(10);
	}
	while(readl(CCM_AHB1_RATIO_CTRL) != 0x00000002);
	writel(0x01000002, CCM_AHB1_RATIO_CTRL);
//	����AHB2��ʱ��ԴΪPLL_P0����Ƶ=2����GTBUS=960/8=240
	writel(0x00000003, CCM_AHB2_RATIO_CTRL);
	do
	{
		__usdelay(10);
	}
	while(readl(CCM_AHB2_RATIO_CTRL) != 0x00000003);
	writel(0x01000003, CCM_AHB2_RATIO_CTRL);
//	����APB0��ʱ��ԴΪPLL_P0����Ƶ=3����GTBUS=960/8=120
	writel(0x00000003, CCM_APB0_RATIO_CTRL);
	do
	{
		__usdelay(10);
	}
	while(readl(CCM_APB0_RATIO_CTRL) != 0x00000003);
	writel(0x01000003, CCM_APB0_RATIO_CTRL);
	//����GTBUS��ʱ��ԴΪPLL_P1����Ƶ=2����GTBUS=1200/3=400
	writel(0x00000002, CCM_GTCLK_RATIO_CTRL);
	do
	{
		__usdelay(10);
	}
	while(readl(CCM_GTCLK_RATIO_CTRL) != 0x00000002);
	writel(0x02000002, CCM_GTCLK_RATIO_CTRL);
//	����CCI400��ʱ��ԴΪPLL_P0����Ƶ=1����CCI400=960/2=480
	__usdelay(100);
	CP15DMB;
	CP15ISB;
	reg_val = readl(CCM_CCI400_CTRL);
	if(!(reg_val & (0x3<<24)))
	{
	    writel(0x1, CCM_CCI400_CTRL);
	    __usdelay(50);
	}
	writel(0x01000001, CCM_CCI400_CTRL);

	__usdelay(100);
	CP15DMB;
	CP15ISB;
	//����Cluster0ʱ��ԴΪPLL_C0CPUX
	reg_val = readl(CCM_CPU_SOURCECTRL);
	reg_val |= 1;
	writel(reg_val, CCM_CPU_SOURCECTRL);

	//����Cluster1ʱ��ԴΪPLL_C1CPUX
	reg_val = readl(CCM_CPU_SOURCECTRL);
	reg_val |= 1 << 8;
	writel(reg_val, CCM_CPU_SOURCECTRL);

	__usdelay(1000);
	CP15DMB;
	CP15ISB;
	//��GPIO
	writel(readl(CCM_APB0_GATE0_CTRL)   | (1 << 5), CCM_APB0_GATE0_CTRL);
	writel(readl(0x08001400 + 0x28)       | 0x01,   0x08001400 + 0x28);
	//��DMA
	writel(readl(CCM_AHB1_RST_REG0)   | (1 << 24), CCM_AHB1_RST_REG0);
	writel(readl(CCM_AHB1_GATE0_CTRL) | (1 << 24), CCM_AHB1_GATE0_CTRL);
	//�ر�dma autoclock gating
	//writel(3, DMAC_REGS_BASE + 0x28);
	//��TIMESTAMP
	writel(1, 0x01720000);
    set_pll_bias();
    gtbus_init();

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
void reset_pll( void )
{
    //�л�CPUʱ��ԴΪ24M
    writel(0x00000000, CCM_CPU_SOURCECTRL);
    //��ԭPLL_C0CPUXΪĬ��ֵ
	writel(0x02001100, CCM_PLL1_C0_CTRL);

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
void set_gpio_gate(void)
{
	writel(readl(CCM_APB0_GATE0_CTRL)   | (1 << 5), CCM_APB0_GATE0_CTRL);
	writel(readl(0x08001400 + 0x28)       | 0x01,   0x08001400 + 0x28);
}

