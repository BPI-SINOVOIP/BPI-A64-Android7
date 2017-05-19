/*
 * Copyright (C) 2012 Invensense, Inc.
 *
 * This software is licensed under the terms of the GNU General Public
 * License version 2, as published by the Free Software Foundation, and
 * may be copied, distributed, and modified under those terms.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
#define pr_fmt(fmt) "inv_mpu: " fmt
#include "inv_mpu_iio.h"
#define  ALLWINNER 1
#ifdef ALLWINNER
s64 get_time_ns(void)
{
	struct timespec ts;
	ktime_get_ts(&ts);
	return timespec_to_ns(&ts);
}
#else
#ifndef CONFIG_INV_MPU_DRAGON_IIO
#include <linux/android_alarm.h>
#endif
#ifdef CONFIG_INV_MPU_DRAGON_IIO
s64 get_time_ns(void)
{
	struct timespec ts;

	get_monotonic_boottime(&ts);

	/* Workaround for some platform on which monotonic clock and
	 * Android SystemClock has a gap.
	 * Use ktime_to_timespec(alarm_get_elapsed_realtime()) instead of
	 * get_monotonic_boottime() for these platform
	 */
	return timespec_to_ns(&ts);
}
#else
s64 get_time_ns(void)
{
	struct timespec ts;

	/* get_monotonic_boottime(&ts); */

	/* Workaround for some platform on which monotonic clock and
	 * Android SystemClock has a gap.
	 * Use ktime_to_timespec(alarm_get_elapsed_realtime()) instead of
	 * get_monotonic_boottime() for these platform
	 */

	ts = ktime_to_timespec(alarm_get_elapsed_realtime());

	return timespec_to_ns(&ts);
}
#endif
#endif


int inv_q30_mult(int a, int b)
{
#define DMP_MULTI_SHIFT                 30
	u64 temp;
	int result;

	temp = ((u64)a) * b;
	result = (int)(temp >> DMP_MULTI_SHIFT);

	return result;
}
#if defined(CONFIG_INV_MPU_IIO_ICM20648) || \
					defined(CONFIG_INV_MPU_IIO_ICM20690)
/* inv_read_secondary(): set secondary registers for reading.
   The chip must be set as bank 3 before calling.
 */
int inv_read_secondary(struct inv_mpu_state *st, int ind, int addr,
		       int reg, int len)
{
	int result;

	result = inv_plat_single_write(st, st->slv_reg[ind].addr,
				       INV_MPU_BIT_I2C_READ | addr);
	if (result)
		return result;
	result = inv_plat_single_write(st, st->slv_reg[ind].reg, reg);
	if (result)
		return result;
	result = inv_plat_single_write(st, st->slv_reg[ind].ctrl,
				       INV_MPU_BIT_SLV_EN | len);

	return result;
}

int inv_execute_read_secondary(struct inv_mpu_state *st, int ind, int addr,
			       int reg, int len, u8 *d)
{
	int result;

	inv_set_bank(st, BANK_SEL_3);
	result = inv_read_secondary(st, ind, addr, reg, len);
	if (result)
		return result;
	inv_set_bank(st, BANK_SEL_0);
	result = inv_plat_single_write(st, REG_USER_CTRL, st->i2c_dis |
				       BIT_I2C_MST_EN);
	msleep(SECONDARY_INIT_WAIT);
	result = inv_plat_single_write(st, REG_USER_CTRL, st->i2c_dis);
	if (result)
		return result;
	result = inv_plat_read(st, REG_EXT_SLV_SENS_DATA_00, len, d);

	return result;
}

/* inv_write_secondary(): set secondary registers for writing.
   The chip must be set as bank 3 before calling.
 */
int inv_write_secondary(struct inv_mpu_state *st, int ind, int addr,
			int reg, int v)
{
	int result;

	result = inv_plat_single_write(st, st->slv_reg[ind].addr, addr);
	if (result)
		return result;
	result = inv_plat_single_write(st, st->slv_reg[ind].reg, reg);
	if (result)
		return result;
	result = inv_plat_single_write(st, st->slv_reg[ind].ctrl,
				       INV_MPU_BIT_SLV_EN | 1);

	result = inv_plat_single_write(st, st->slv_reg[ind].d0, v);

	return result;
}

int inv_execute_write_secondary(struct inv_mpu_state *st, int ind, int addr,
				int reg, int v)
{
	int result;

	inv_set_bank(st, BANK_SEL_3);
	result = inv_write_secondary(st, ind, addr, reg, v);
	if (result)
		return result;
	inv_set_bank(st, BANK_SEL_0);
	result = inv_plat_single_write(st, REG_USER_CTRL, st->i2c_dis |
				       BIT_I2C_MST_EN);
	msleep(SECONDARY_INIT_WAIT);
	result = inv_plat_single_write(st, REG_USER_CTRL, st->i2c_dis);

	return result;
}

int inv_set_bank(struct inv_mpu_state *st, u8 bank)
{
#ifdef CONFIG_INV_MPU_IIO_ICM20648
	int r;

	r = inv_plat_single_write(st, REG_BANK_SEL, bank);

	return r;
#else
	return 0;
#endif
}
#endif

#ifdef CONFIG_INV_MPU_IIO_ICM20648
/**
 *  inv_write_cntl() - Write control word to designated address.
 *  @st:	Device driver instance.
 *  @wd:        control word.
 *  @en:	enable/disable.
 *  @cntl:	control address to be written.
 */
int inv_write_cntl(struct inv_mpu_state *st, u16 wd, bool en, int cntl)
{
	int result;
	u8 reg[2], d_out[2];

	result = mem_r(cntl, 2, d_out);
	if (result)
		return result;
	reg[0] = ((wd >> 8) & 0xff);
	reg[1] = (wd & 0xff);
	if (!en) {
		d_out[0] &= ~reg[0];
		d_out[1] &= ~reg[1];
	} else {
		d_out[0] |= reg[0];
		d_out[1] |= reg[1];
	}
	result = mem_w(cntl, 2, d_out);

	return result;
}
#endif

int inv_set_power(struct inv_mpu_state *st, bool power_on)
{
	u8 d;
	int r;

	if ((!power_on) == st->chip_config.is_asleep)
		return 0;

	d = BIT_CLK_PLL;
	if (!power_on)
		d |= BIT_SLEEP;

	r = inv_plat_single_write(st, REG_PWR_MGMT_1, d);
	if (r)
		return r;

	if (power_on)
		usleep_range(REG_UP_TIME_USEC, REG_UP_TIME_USEC);

	st->chip_config.is_asleep = !power_on;

	return 0;
}

int inv_stop_dmp(struct inv_mpu_state *st)
{
	return inv_plat_single_write(st, REG_USER_CTRL, st->i2c_dis);
}

static int inv_lp_en_off_mode(struct inv_mpu_state *st, bool on)
{
	int r;

	if (!st->chip_config.is_asleep)
		return 0;

	r = inv_plat_single_write(st, REG_PWR_MGMT_1, BIT_CLK_PLL);
	st->chip_config.is_asleep = 0;

	return r;
}
#ifdef CONFIG_INV_MPU_IIO_ICM20648
static int inv_lp_en_on_mode(struct inv_mpu_state *st, bool on)
{
	int r = 0;
	u8 w;

	if ((!st->chip_config.is_asleep) &&
	    ((!on) == st->chip_config.lp_en_set))
		return 0;

	w = BIT_CLK_PLL;
	if ((!on) && (!st->eis.eis_triggered))
		w |= BIT_LP_EN;
	r = inv_plat_single_write(st, REG_PWR_MGMT_1, w);
	st->chip_config.is_asleep = 0;
	st->chip_config.lp_en_set = (!on);
	return r;
}
#endif
#if defined(CONFIG_INV_MPU_IIO_ICM20602) || defined(CONFIG_INV_MPU_IIO_ICM20690)
static int inv_set_accel_config2(struct inv_mpu_state *st, bool cycle_mode)
{
	int cycle_freq[] = {275, 192, 111, 59};
	int cont_freq[] = {192, 192, 110, 59, 30, 15, 8};
	int i, r, rate;
	u8 v;

	rate = (st->eng_info[ENGINE_ACCEL].running_rate << 1);
	v = 0;
#ifdef CONFIG_INV_MPU_IIO_ICM20690
	v |= BIT_FIFO_SIZE_1K;
#endif
	if (cycle_mode) {
		i = ARRAY_SIZE(cycle_freq) - 1;
		while (i > 0) {
			if (rate < cycle_freq[i]) {
				break;
			}
			i--;
		}
		r = inv_plat_single_write(st, REG_ACCEL_CONFIG_2, v |
								(i << 4) | 7);
		if (r)
			return r;
	} else {
		i = ARRAY_SIZE(cont_freq) - 1;
		while (i > 0) {
			if (rate < cont_freq[i]) {
				break;
			}
			i--;
		}
		r = inv_plat_single_write(st, REG_ACCEL_CONFIG_2, v | i);
		if (r)
			return r;
	}

	return 0;
}
static int inv_lp_en_on_mode(struct inv_mpu_state *st, bool on)
{
	int r = 0;
	u8 w;

	if ((!st->chip_config.is_asleep) &&
	    ((!on) == st->chip_config.lp_en_set))
		return 0;
	w = BIT_CLK_PLL;
	if ((!on) && (!st->eis.eis_triggered) && (!st->chip_config.gyro_enable)
			&& (!st->chip_config.compass_enable)
			&& (!st->ois.en)) {
		w |= BIT_LP_EN;
		inv_set_accel_config2(st, true);
	} else {
		inv_set_accel_config2(st, false);
	}
	r = inv_plat_single_write(st, REG_PWR_MGMT_1, w);
	st->chip_config.is_asleep = 0;
	st->chip_config.lp_en_set = (!on);

	return r;
}
#endif
#ifdef CONFIG_INV_MPU_IIO_ICM20608D
static int inv_lp_en_on_mode(struct inv_mpu_state *st, bool on)
{
	return 0;
}
#endif
int inv_switch_power_in_lp(struct inv_mpu_state *st, bool on)
{
	int r;

	if (st->chip_config.lp_en_mode_off)
		r = inv_lp_en_off_mode(st, on);
	else
		r = inv_lp_en_on_mode(st, on);

	return r;
}

int write_be16_to_mem(struct inv_mpu_state *st, u16 data, int addr)
{
	u8 d[2];

	d[0] = (data >> 8) & 0xff;
	d[1] = data & 0xff;

	return mem_w(addr, sizeof(d), d);
}

int write_be32_to_mem(struct inv_mpu_state *st, u32 data, int addr)
{
	cpu_to_be32s(&data);
	return mem_w(addr, sizeof(data), (u8 *)&data);
}

int read_be16_from_mem(struct inv_mpu_state *st, u16 *o, int addr)
{
	int result;
	u8 d[2];

	result = mem_r(addr, 2, (u8 *) &d);
	*o = d[0] << 8 | d[1];

	return result;
}

int read_be32_from_mem(struct inv_mpu_state *st, u32 *o, int addr)
{
	int result;
	u32 d;

	result = mem_r(addr, 4, (u8 *) &d);
	*o = be32_to_cpup((__be32 *)(&d));

	return result;
}

int be32_to_int(u8 *d)
{
	return (d[0] << 24) | (d[1] << 16) | (d[2] << 8) | d[3];
}

u32 inv_get_cntr_diff(u32 curr_counter, u32 prev)
{
	u32 diff;

	if (curr_counter > prev)
		diff = curr_counter - prev;
	else
		diff = 0xffffffff - prev + curr_counter + 1;

	return diff;
}

int inv_write_2bytes(struct inv_mpu_state *st, int addr, int data)
{
	u8 d[2];

	if (data < 0 || data > USHRT_MAX)
		return -EINVAL;

	d[0] = (u8) ((data >> 8) & 0xff);
	d[1] = (u8) (data & 0xff);

	return mem_w(addr, ARRAY_SIZE(d), d);
}
