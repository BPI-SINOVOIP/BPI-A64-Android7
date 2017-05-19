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

#ifndef _SUNXI_CPU_H
#define _SUNXI_CPU_H


#define SUNXI_SRAM_A1_BASE			0X00000000
#define SUNXI_SRAM_A1_SIZE			(16 * 1024)		/* 16k */
#define SUNXI_SRAM_A2_BASE			0X00004000		/* 16k */
#define SUNXI_SRAM_A3_BASE			0X00008000		/* 13k */
#define SUNXI_SRAM_A4_BASE			0X0000B400		/* 3k */
#if 0
#define SUNXI_SRAM_NAND_BASE		0Xdeaddead		/* 2k(address not available on spec) */
#endif
#define SUNXI_SRAM_D_BASE			0X01C00000		/* 4k */
#define SUNXI_SRAM_B_BASE			0X00200000		/* 64k(secure) */
#define SUNXI_SRAMC_BASE			0X01C00000		/* 4k */
#define SUNXI_DRAMC_BASE			0X01C01000		/* 4k */
#define SUNXI_DMA_BASE				0X01C02000		/* 4k */
#define SUNXI_NFC_BASE				0X01C03000		/* 4k */
#define SUNXI_TS_BASE				0X01C04000		/* 4k */
#define SUNXI_SPI0_BASE				0X01C05000		/* 4k */
#define SUNXI_SPI1_BASE				0X01C06000		/* 4k */
#define SUNXI_MS_BASE				0X01C07000		/* 4k */
#define SUNXI_TVD_BASE				0X01C08000		/* 4k */
#define SUNXI_CSI0_BASE				0X01C09000		/* 4k */
#define SUNXI_TVE0_BASE				0X01C0A000		/* 4k */
#define SUNXI_EMAC_BASE				0X01C0B000		/* 4k */
#define SUNXI_LCD0_BASE				0X01C0C000		/* 4k */
#define SUNXI_LCD1_BASE				0X01C0D000		/* 4k */
#define SUNXI_VE_BASE				0X01C0E000		/* 4k */
#define SUNXI_MMC0_BASE				0X01C0F000		/* 4k */
#define SUNXI_MMC1_BASE				0X01C10000		/* 4k */
#define SUNXI_MMC2_BASE				0X01C11000		/* 4k */
#define SUNXI_MMC3_BASE				0X01C12000		/* 4k */
#define SUNXI_USBOTG_BASE			0X01C13000		/* 4k */
#define SUNXI_USBEHCI0_BASE			0X01C14000		/* 4k */

#define SUNXI_SS_BASE				0X01C15000		/* 4k */
#define SUNXI_HDMI_BASE				0X01C16000		/* 4k */
#define SUNXI_SPI2_BASE				0X01C17000		/* 4k */
#define SUNXI_SATA_BASE				0X01C18000		/* 4k */
#define SUNXI_PATA_BASE				0X01C19000		/* 4k */
#define SUNXI_ACE_BASE				0X01C1A000		/* 4k */
#define SUNXI_TVE1_BASE				0X01C1B000		/* 4k */
#define SUNXI_USB2_BASE				0X01C1C000		/* 4k */
#define SUNXI_CSI1_BASE				0X01C1D000		/* 4k */
													/* 4k */
#define SUNXI_SPI3_BASE				0X01C1F000		/* 4k */													
#define SUNXI_CCM_BASE				0X01C20000		/* 1k */
#define SUNXI_INTC_BASE				0X01C20400		/* 1k */
#define SUNXI_PIO_BASE				0X01C20800		/* 1k */
#define SUNXI_TIMER_BASE			0X01C20C00		/* 1k */
#define SUNXI_SPDIF_BASE			0X01C21000		/* 1k */
#define SUNXI_PWM_BASE				0X01C20c00
#define SUNXI_IR0_BASE				0X01C21800		/* 1k */
#define SUNXI_IR1_BASE				0X01C21C00		/* 1k */
#define SUNXI_IIS_BASE				0X01C22400		/* 1k */
#define SUNXI_LRADC_BASE			0X01C22800		/* 1k */
#define SUNXI_AD_DA_BASE			0X01C22C00		/* 1k */
#define SUNXI_KEYPAD_BASE			0X01C23000		/* 1k */
													/* 1k */
#define SUNXI_SID_BASE				0X01C23800		/* 1k */
#define SUNXI_SJTAG_BASE			0X01C23C00		/* 1k */
													/* 1k */
													/* 1k */
#define SUNXI_TP_BASE				0X01C25000		/* 1k */
#define SUNXI_PMU_BASE				0X01C25400		/* 1k */
//#define SUNXI_CPU_CFG_BASE		0X01C25400		/* 1k */
													/* 1k */
													/* 1k */
													/* 1k */
													/* 1k */
													/* 1k */
													/* 1k */
													/* 1k */
													/* 1k */
#define SUNXI_UART0_BASE			0X01C28000		/* 1k */
#define SUNXI_UART1_BASE			0X01C28400		/* 1k */
#define SUNXI_UART2_BASE			0X01C28800		/* 1k */
#define SUNXI_UART3_BASE			0X01C28C00		/* 1k */
#define SUNXI_UART4_BASE			0X01C29000		/* 1k */
#define SUNXI_UART5_BASE			0X01C29400		/* 1k */
#define SUNXI_UART6_BASE			0X01C29800		/* 1k */
#define SUNXI_UART7_BASE			0X01C29C00		/* 1k */
#define SUNXI_PS2_0_BASE			0X01C2A000		/* 1k */
#define SUNXI_PS2_1_BASE			0X01C2A400		/* 1k */
													/* 1k */
#define SUNXI_TWI0_BASE				0X01C2AC00		/* 1k */
#define SUNXI_TWI1_BASE				0X01C2B000		/* 1k */
#define SUNXI_TWI2_BASE				0X01C2B400		/* 1k */
#define SUNXI_TWI3_BASE				0X01C2B800		/* 1k */
#define SUNXI_CAN_BASE				0X01C2BC00		/* 1k */
#define SUNXI_TWI4_BASE				0X01C2C000		/* 1k */
#define SUNXI_SCR_BASE				0X01C2C400		/* 1k */
#define SUNXI_GPS_BASE				0X01C30000		/* 64k */
#define SUNXI_MALI400_BASE			0X01C40000		/* 64k */
#define SUNXI_GMAC_BASE				0X01C50000		/* 64k */
#define SUNXI_HSTIMER_BASE			0X01C60000		/* 4k */
#define SUNXI_GIC_BASE				0X01C80000		/* 32k */
#define SUNXI_HDMI1_BASE			0X01CE0000		/* 128 */												/* 128k */
#define SUNXI_CPUBIST_BASE			0X03F51000		/* 4k */
#define SUNXI_SRAM_C_BASE			0X01D00000		/* module sram */
//#define SUNXI_DRAMPHY1_BASE		    0X01C66000
#define SUNXI_DE_FE0_BASE			0X01E00000		/* 128k */
#define SUNXI_DE_FE1_BASE			0X01E20000		/* 128k */
#define SUNXI_DE_BE0_BASE			0X01E60000		/* 128k */
#define SUNXI_DE_BE1_BASE			0X01E40000		/* 128k */
//#define SUNXI_DE_DRC0_BASE    0x01e70000
//#define SUNXI_DE_DRC1_BASE    0x01e50000
//#define SUNXI_DE_DEU0_BASE    0x01eb0000
//#define SUNXI_DE_DEU1_BASE    0x01ea0000
//#define SUNXI_MIPI_DSI0_BASE       0x01ca0000

/* RCPUS Module */
//#define SUNXI_RPRCM_BASE            (REGS_RCPUS_BASE + 0x1400)
//#define SUNXI_MIPI_DSI0_DPHY_BASE  0x01ca1000
#define SUNXI_MP_BASE				0X01E80000
#define SUNXI_AVG_BASE				0X01EA0000		/* 128k */
#define SUNXI_CSDM_BASE				0X3F500000		/* CoreSight Debug Module*/
#define SUNXI_DDRII_DDRIII_BASE		0X40000000		/* 2G */
#define SUNXI_BROM_BASE				0XFFFF0000		/* 32k */

#define SUNXI_CPU_CFG              (SUNXI_TIMER_BASE + 0x13c)


#define SUNXI_RTC_BASE     0x01f00000
#ifndef __ASSEMBLY__
/* boot type */
typedef enum {
	SUNXI_BOOT_TYPE_NULL = -1,
	SUNXI_BOOT_TYPE_NAND = 0,
	SUNXI_BOOT_TYPE_MMC0 = 1,
	SUNXI_BOOT_TYPE_MMC2 = 2,
	SUNXI_BOOT_TYPE_SPI  = 3
} sunxi_boot_type_t;

sunxi_boot_type_t get_boot_type(void);
#endif /* __ASSEMBLY__ */

#define SUNXI_GET_BITS(value, start_bit, bits_num) ( (value >> start_bit) & \
													((1 << bits_num) - 1) )

#endif /* _CPU_H */
