/*
**********************************************************************************************************************
*											        eGon
*						           the Embedded GO-ON Bootloader System
*									       eGON arch sub-system
*
*						  Copyright(C), 2006-2010, SoftWinners Microelectronic Co., Ltd.
*                                           All Rights Reserved
*
* File    : arch.h
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
#ifndef __ARCH_H_
#define __ARCH_H_	1

//#define  A39_FPGA_PLATFORM
#undef   A39_FPGA_PLATFORM
#define  CONFIG_ARCH_SUN9IW1P1

#include "types.h"


#include "arch/ccmu.h"
#include "arch/timer.h"
#include "arch/uart.h"
#include "arch/pio.h"

#endif  /*#ifndef __ARCH_H_*/


