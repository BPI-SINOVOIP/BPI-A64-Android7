/*
************************************************************************************************************************
*                                                     suni define
*                            suni CPU hardware registers, memory, interrupts, ... define
*
*                             Copyright(C), 2009-2010, uLive Microelectronic Co., Ltd.
*											       All Rights Reserved
*
* File Name : sunii.h
*
* Author : kevin.z
*
* Version : 1.1.0
*
* Date : 2009-9-7 10:53
*
* Description : This file provides some defination of suni's hardware registers, memory, interrupt
*             and so on. This file is very similar to file "sunii.inc"; the two files should be
*             modified at the same time to keep coherence of information.
*
* Others : None at present.
*
*
* History :
*
*  <Author>        <time>       <version>      <description>
*
* kevin.z      2009-9-7 10:53    1.0.0        build the file
*
************************************************************************************************************************
*/

#ifndef  __HD_SCRIPT_H_
#define  __HD_SCRIPT_H_

#define readl(addr)    				( *((volatile unsigned int *)(addr)))
#define writel(value, addr)			((*((volatile unsigned int *)(addr))) = (value))
/*
*********************************************************************************************************
*   hardware registers base define
*********************************************************************************************************
*/
#define REGS_AHB0_BASE	   0x01C00000		//�Ĵ��������ַ

#define REGS1_BASE	   0x01C00000		//�Ĵ��������ַ

// �����ַ
#define SSC_REGS_BASE          ( REGS_AHB0_BASE + 0x02000 )    //SS controller
#define NFC0_REGS_BASE         ( REGS_AHB0_BASE + 0x03000 )    //nand flash controller0
#define NFC1_REGS_BASE         ( REGS_AHB0_BASE + 0x04000 )    //nand flash controller1

#define SDMC0_REGS_BASE        ( REGS_AHB0_BASE + 0x0f000 )    //sdmmc0 controller
#define SDMC1_REGS_BASE        ( REGS_AHB0_BASE + 0x10000 )    //sdmmc1 controller
#define SDMC2_REGS_BASE        ( REGS_AHB0_BASE + 0x11000 )    //sdmmc2 controller
#define SDMC3_REGS_BASE        ( REGS_AHB0_BASE + 0x12000 )    //sdmmc3 controller
#define MMC_REG_COMM_BASE      ( REGS_AHB0_BASE + 0x13000 )    //sdmmc common controller

#define SPI0_REGS_BASE         ( REGS_AHB0_BASE + 0x1A000 )    //spi0 controller
#define SPI1_REGS_BASE         ( REGS_AHB0_BASE + 0x1B000 )    //spi1 controller
#define SPI2_REGS_BASE         ( REGS_AHB0_BASE + 0x1C000 )    //spi2 controller
#define SPI3_REGS_BASE         ( REGS_AHB0_BASE + 0x1D000 )    //spi3 controller
/*
*********************************************************************************************************
*   hardware registers base define
*********************************************************************************************************
*/
#define REGS_AHB1_BASE	   0x00800000		//�Ĵ��������ַ

#define DMAC_REGS_BASE         ( REGS_AHB1_BASE + 0x02000 )    //dma controller0
#define USBC_REGS_BASE         ( REGS_AHB1_BASE + 0x10000 )    //dma controller0

/*
*********************************************************************************************************
*   hardware registers base define
*********************************************************************************************************
*/
#define REGS_APB0_BASE	   0x06000000		//�Ĵ��������ַ

#define CCMC_REGS_BASE         ( REGS_APB0_BASE + 0x00000 )    //ccm pll controller0
#define CCMM_REGS_BASE         ( REGS_APB0_BASE + 0x00400 )    //ccm module controller0
#define PIOC_REGS_BASE         ( REGS_APB0_BASE + 0x00800 )    //pio controller0
#define TMRC_REGS_BASE		   ( REGS_APB0_BASE + 0x00C00 )    //tmr controller0

/*
*********************************************************************************************************
*   hardware registers base define
*********************************************************************************************************
*/
#define REGS_APB1_BASE	   0x07000000		//�Ĵ��������ַ

#define UART0_REGS_BASE        ( REGS_APB1_BASE + 0x00000 )    //uart0 controller0
#define UART1_REGS_BASE        ( REGS_APB1_BASE + 0x00400 )    //uart1 controller0
#define UART2_REGS_BASE        ( REGS_APB1_BASE + 0x00800 )    //uart2 controller0
#define UART3_REGS_BASE        ( REGS_APB1_BASE + 0x00C00 )    //uart3 controller0
#define UART4_REGS_BASE        ( REGS_APB1_BASE + 0x01000 )    //uart4 controller0
#define UART5_REGS_BASE        ( REGS_APB1_BASE + 0x01400 )    //uart5 controller0

#define TWI0_REGS_BASE         ( REGS_APB1_BASE + 0x02800 )    //twi0 controller0
#define TWI1_REGS_BASE         ( REGS_APB1_BASE + 0x02C00 )    //twi1 controller0
#define TWI2_REGS_BASE         ( REGS_APB1_BASE + 0x03000 )    //twi2 controller0
#define TWI3_REGS_BASE         ( REGS_APB1_BASE + 0x03400 )    //twi3 controller0
#define TWI4_REGS_BASE         ( REGS_APB1_BASE + 0x03800 )    //twi4 controller0

#define REGS_RCPUS_BASE         0x08000000    //RCPU�Ĵ��������ַ
#define SUNXI_RPRCM_BASE       ( REGS_RCPUS_BASE + 0x1400)

#endif // end of #ifndef __HD_SCRIPT_H_
