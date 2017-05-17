/* Sunxi Remote Controller
 *
 * keymap imported from ir-keymaps.c
 *
 * Copyright (c) 2014 by allwinnertech
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

#include <media/rc-map.h>
#include "sunxi-ir-rx.h"

static u32 match_num = 0;
static struct ir_key_table match_addr[MAX_ADDR_NUM];

/* bpi, define keycode here and using scan in rc_map_list */
static struct rc_map_table sunxi_nec_scan[] = {
	{ 0x17c601, KEY_UP},
	{ 0x17c619, KEY_LEFT },
	{ 0x17c611, KEY_RIGHT },
	{ 0x17c609, KEY_DOWN },
	{ 0x17c60f, KEY_HOME },
	{ 0x17c60d, KEY_BACK },
	{ 0x17c61c, KEY_PAUSE },
	{ 0x17c67f, KEY_MENU },
	{ 0x17c640, KEY_ENTER },
	{ 0x17c612, KEY_POWER },
};

/* bpi, define keycode in sys_cofig and using mapping 
 * instead of scan in rc_map_list 
 */
static u32 sunxi_key_mapping(u32 code)
{
	u32 keycode;
	int mid;
	int start = 0;
	int end = match_num - 1;
	
	while (start <= end) {
		mid = (start + end) / 2;
		if (match_addr[mid].ir_key_addr < code)
			start = mid + 1;
		else if (match_addr[mid].ir_key_addr > code)
			end = mid - 1;
		else
			break;
			
	}

	keycode = mid < match_num ? match_addr[mid].ir_key_code : KEY_RESERVED;

	pr_info("%s, ir code = %x, mapping keycode = %d\n", __func__, code, keycode);

	return keycode;
}

static struct rc_map_list sunxi_map = {
	.map = {
		.scan    = sunxi_nec_scan,
		.size    = ARRAY_SIZE(sunxi_nec_scan),
		.mapping = sunxi_key_mapping,
		.rc_type = RC_TYPE_NEC,	/* Legacy IR type */
		.name    = RC_MAP_SUNXI,
	}
};

int init_rc_addr(struct ir_key_table *addr_key, u32 ir_keycount)
{
	int i, j;
	struct ir_key_table temp_key_table;

	if(!addr_key){
		pr_err("addr_key is null\n");
		return 0;
	}

	for(i = 0; i < ir_keycount; i++){
		match_addr[i].ir_key_addr = addr_key[i].ir_key_addr;
		match_addr[i].ir_key_code = addr_key[i].ir_key_code;
	}
	match_num = ir_keycount;

	/* sort */
	for(i = 0; i < ir_keycount-1; i++) {
		for(j = 0; j < ir_keycount-i-1; j++) {
			if(match_addr[j].ir_key_addr > match_addr[j+1].ir_key_addr)
			{
				temp_key_table = match_addr[j];
				match_addr[j] = match_addr[j+1];
				match_addr[j+1] = temp_key_table;
			}
		}
	}

	return 0;
}

int init_rc_map_sunxi(void)
{
	return rc_map_register(&sunxi_map);
}

void exit_rc_map_sunxi(void)
{
	rc_map_unregister(&sunxi_map);
}

