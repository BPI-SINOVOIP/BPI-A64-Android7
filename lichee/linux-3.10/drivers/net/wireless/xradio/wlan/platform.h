/*
 * platform interfaces for XRadio drivers
 *
 * Copyright (c) 2013
 * Allwinner Technology Co., Ltd. <www.allwinnertech.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 */

#ifndef XRADIO_PLAT_H_INCLUDED
#define XRADIO_PLAT_H_INCLUDED

#include <linux/version.h>
#include <linux/kernel.h>
#include <linux/mmc/host.h>


#if (LINUX_VERSION_CODE >= KERNEL_VERSION(3, 4, 0))
#define PLAT_ALLWINNER_SUNXI
#define MCI_RESCAN_CARD(id, ins)  sunxi_mmc_rescan_card(id)
#define MCI_CHECK_READY(h, t)     sunxi_mmc_check_r1_ready(h, t)

extern void sunxi_mmc_rescan_card(unsigned id);
extern int sunxi_mmc_check_r1_ready(struct mmc_host *mmc, unsigned ms);
#endif

/* platform interfaces */
int xradio_plat_init(void);
void xradio_plat_deinit(void);
int xradio_sdio_detect(int enable);
int  xradio_request_gpio_irq(struct device *dev, void *sbus_priv);
void xradio_free_gpio_irq(struct device *dev, void *sbus_priv);
int xradio_wlan_power(int on);

#if (LINUX_VERSION_CODE >= KERNEL_VERSION(3,10,0))
extern void sunxi_wlan_set_power(int on); // enable wifi_power和 wifi_IO_regulator的电，以及将,wlan_regon gpio拉高
extern int sunxi_wlan_get_bus_index(void);//获得sdio 编号
extern int sunxi_wlan_get_oob_irq(void);//获取中断号
//extern int sunxi_wlan_get_oob_irq_flags(void); //获取中断参数
#endif

#endif /* XRADIO_PLAT_H_INCLUDED */
