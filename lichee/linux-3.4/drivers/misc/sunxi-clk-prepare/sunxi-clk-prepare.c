/*
 * sunxi-clk-prepare.c clk prepare Interlace driver
 *
 * Copyright (C) 2014-2016 allwinner.
 *	Ming Li<liming@allwinnertech.com>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 */
#include <linux/module.h>
#include <linux/clk.h>
#include <linux/clk-private.h>
#include <linux/of.h>
#include <linux/of_device.h>
#include <mach/sys_config.h>
#include "sunxi-clk-prepare.h"

static u32 debug_mask = 0x0;

/**
 * sunxi_clk_enable_prepare - prapare something before enable clk
 * Input:
 * 	clk: clk point
 * return value: 0 : success
 *               -EIO :  i/o err.
 */
int sunxi_clk_enable_prepare(struct clk *clk)
{
	unsigned int i = 0;
	unsigned int mask_bit = 0;
	struct clk *clk_handle = NULL;

	if(NULL == clk) {
		printk(KERN_ERR "%s: clk is NULL!\n", __func__);
		return -1;
	}

	for (i=0; i<CLK_MAX_MODULE_VALUE; i++) {
		if (0 != clk_bitmap_name_mapping[i].mask_bit) {
			if (!strcmp(clk_bitmap_name_mapping[i].id_name, clk->name)) {
				mask_bit = clk_bitmap_name_mapping[i].mask_bit;
				break;
			}
		}
	}

	if (0 == mask_bit) {
		printk(KERN_ERR "%s: donot find related module\n", __func__);
		return 0;
	}

	for (i=0; i<CLK_MAX_ID_VALUE; i++) {
		if ((1<<i) & mask_bit) {
			clk_handle = clk_get(NULL, id_name[i]);
			if (IS_ERR_OR_NULL(clk_handle)) {
				printk(KERN_ERR "%s: try to get %s failed!\n", __func__, id_name[i]);
				continue;
			}
			if (clk_prepare_enable(clk_handle)) {
				printk(KERN_ERR "%s: try to enable %s failed!\n", __func__, id_name[i]);
				clk_put(clk_handle);
				continue;
			}
			clk_put(clk_handle);
			dprintk(DEBUG_INIT, "%s: %s enable success!\n", __func__, id_name[i]);
			clk_handle = NULL;
		}
	}

	return 0;
}
EXPORT_SYMBOL(sunxi_clk_enable_prepare);

/**
 * sunxi_clk_disable_prepare - prapare something before disable clk
 * Input:
 * 	clk point
 * return value: 0 : success
 *               -EIO :  i/o err.
 */
int sunxi_clk_disable_prepare(struct clk *clk)
{
	unsigned int i = 0;
	unsigned int mask_bit = 0;
	struct clk *clk_handle = NULL;

	if(NULL == clk) {
		printk(KERN_ERR "%s: clk is NULL!\n", __func__);
		return -1;
	}

	for (i=0; i<CLK_MAX_MODULE_VALUE; i++) {
		if (0 != clk_bitmap_name_mapping[i].mask_bit) {
			if (!strcmp(clk->name, clk_bitmap_name_mapping[i].id_name)) {
				mask_bit = clk_bitmap_name_mapping[i].mask_bit;
				break;
			}
		}
	}

	if (0 == mask_bit) {
		printk(KERN_ERR "%s: donot find related module\n", __func__);
		return 0;
	}

	for (i=0; i<CLK_MAX_ID_VALUE; i++) {
		if ((1<<i) & mask_bit) {
			clk_handle = clk_get(NULL, id_name[i]);
			if (IS_ERR_OR_NULL(clk_handle)) {
				printk(KERN_ERR "%s: try to get %s failed!\n", __func__, id_name[i]);
				continue;
			}
			clk_disable_unprepare(clk_handle);
			clk_put(clk_handle);
			dprintk(DEBUG_INIT, "%s: %s disable success!\n", __func__, id_name[i]);
			clk_handle = NULL;
		}
	}

	return 0;
}
EXPORT_SYMBOL(sunxi_clk_disable_prepare);

static unsigned int sunxi_clk_check_flags(unsigned int mask_bit)
{
	unsigned int i = 0;
	script_item_u used;
	script_item_value_type_e type;

	for (i=0; i<CLK_MAX_ID_VALUE; i++) {
		if ((1<<i) & mask_bit) {
			if (i <= 12) {
				type = script_get_item(module_id[i], "usb_used", &used);
				if (SCIRPT_ITEM_VALUE_TYPE_INT != type) {
					printk(KERN_ERR "%s get %s err!\n", __func__, module_id[i]);
					continue;
				}
				if (1 != used.val)
					mask_bit &= ~(1<<i);
			} else if (0 == strcmp("hdmi_para", module_id[i])) {
				type = script_get_item(module_id[i], "hdmi_used", &used);
				if (SCIRPT_ITEM_VALUE_TYPE_INT != type) {
					printk(KERN_ERR "%s get %s err!\n", __func__, module_id[i]);
					continue;
				}
				if (1 != used.val)
					mask_bit &= ~(1<<i);
			}
		}
	}

	dprintk(DEBUG_INIT, "%s: mask_bit = 0x%x\n", __func__, mask_bit);

	return mask_bit;
}

static int __init sunxi_clk_prepare_init(void)
{
	unsigned int i = 0;

	/* analytical dts to check mask bit */
	for (i=0; i<CLK_MAX_MODULE_VALUE; i++) {
		if (0 != clk_bitmap_name_mapping[i].mask_bit) {
			clk_bitmap_name_mapping[i].mask_bit = sunxi_clk_check_flags(clk_bitmap_name_mapping[i].mask_bit);
		}
	}

	return 0;
}

static void __exit sunxi_clk_prepare_exit(void)
{
	return ;
}

subsys_initcall(sunxi_clk_prepare_init);
module_exit(sunxi_clk_prepare_exit);
module_param_named(debug_mask, debug_mask, int, 0644);
MODULE_DESCRIPTION("clk-prepare driver");
MODULE_AUTHOR("Ming Li<liming@allwinnertech.com>");
MODULE_LICENSE("GPL");

