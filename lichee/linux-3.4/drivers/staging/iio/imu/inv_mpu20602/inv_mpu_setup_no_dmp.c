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

static int inv_calc_engine_dur(struct inv_engine_info *ei)
{
	if (!ei->running_rate)
		return -EINVAL;
	ei->dur = ei->base_time / ei->orig_rate;
	ei->dur *= ei->divider;

	return 0;
}

static int inv_turn_on_fifo(struct inv_mpu_state *st)
{
	u8 int_en, fifo_en, mode, user;
	int r;

	r = inv_plat_single_write(st, REG_USER_CTRL, BIT_FIFO_RST);
	if (r)
		return r;
	fifo_en = 0;
	int_en = 0;

	if (st->gesture_only_on && (!st->batch.timeout)) {
		st->gesture_int_count = WOM_DELAY_THRESHOLD;
		int_en |= BIT_WOM_ALL_INT_EN;
	}
	if (st->batch.timeout) {
		if (!st->batch.fifo_wm_th)
			int_en = BIT_DATA_RDY_EN;
	} else {
		int_en = BIT_DATA_RDY_EN;
#ifdef CONFIG_INV_MPU_IIO_ICM20602
		if (st->chip_config.eis_enable)
			int_en |= BIT_FSYNC_INT_EN;
#endif
	}
	if (st->sensor[SENSOR_GYRO].on)
		fifo_en |= BITS_GYRO_FIFO_EN;

	if (st->sensor[SENSOR_ACCEL].on)
		fifo_en |= BIT_ACCEL_FIFO_EN;
#ifdef CONFIG_INV_MPU_IIO_ICM20690
	if (st->sensor[SENSOR_TEMP].on)
		fifo_en |= BITS_TEMP_FIFO_EN;
	if (st->sensor[SENSOR_COMPASS].on)
		fifo_en |= BIT_SLV_0_FIFO_EN;
#endif
	r = inv_plat_single_write(st, REG_FIFO_EN, fifo_en);
	if (r)
		return r;
	r = inv_plat_single_write(st, REG_INT_ENABLE, int_en);
	if (r)
		return r;
	if (st->gesture_only_on && (!st->batch.timeout)) {
		mode = BIT_ACCEL_INTEL_EN | BIT_ACCEL_INTEL_MODE;
	} else {
		mode = 0;
	}
#ifdef CONFIG_INV_MPU_IIO_ICM20690
	if (st->ois.en)
		mode |= BIT_ACCEL_FCHOICE_OIS_B;
#endif
	r = inv_plat_single_write(st, REG_ACCEL_INTEL_CTRL, mode);
#ifdef CONFIG_INV_MPU_IIO_ICM20690
	if (st->eng_info[ENGINE_GYRO].running_rate > MAX_COMPASS_RATE) {
		r = inv_plat_single_write(st, REG_I2C_SLV4_CTRL,
			st->eng_info[ENGINE_GYRO].running_rate /
			MAX_COMPASS_RATE);
		if (r)
			return r;
		r = inv_plat_single_write(st, REG_I2C_MST_DELAY_CTRL,
						BIT_I2C_SLV1_DELAY_EN |
						BIT_I2C_SLV0_DELAY_EN);
	} else {
		r = inv_plat_single_write(st, REG_I2C_SLV4_CTRL, 0);
	}
	if (r)
		return r;
#endif
	user = BIT_FIFO_EN;
#ifdef CONFIG_INV_MPU_IIO_ICM20690
	if (st->sensor[SENSOR_COMPASS].on)
		user |= BIT_I2C_MST_EN;
#endif
	r = inv_plat_single_write(st, REG_USER_CTRL, user | st->i2c_dis);

	return r;
}

/*
 *  inv_reset_fifo() - Reset FIFO related registers.
 */
int inv_reset_fifo(struct inv_mpu_state *st, bool turn_off)
{
	int r, i;

	st->last_run_time = get_time_ns();
	st->reset_ts = st->last_run_time;
	r = inv_turn_on_fifo(st);
	if (r)
		return r;

	st->last_temp_comp_time = st->last_run_time;
	st->left_over_size = 0;
	for (i = 0; i < SENSOR_NUM_MAX; i++) {
		st->sensor[i].calib_flag = 0;
		st->sensor[i].sample_calib = 0;
		st->sensor[i].time_calib = st->last_run_time;
	}

	st->time_calib_counter = 0;
	return 0;
}

static int inv_turn_on_engine(struct inv_mpu_state *st)
{
	u8 w;
	int r;

	if (st->chip_config.gyro_enable | st->chip_config.accel_enable) {
		w = 0;
		if (!st->chip_config.gyro_enable)
			w |= BIT_PWR_GYRO_STBY;
		if (!st->chip_config.accel_enable)
			w |= BIT_PWR_ACCEL_STBY;
	} else if (st->chip_config.compass_enable) {
		w = BIT_PWR_GYRO_STBY;
	} else {
		w = (BIT_PWR_GYRO_STBY | BIT_PWR_ACCEL_STBY);
	}
	r = inv_plat_single_write(st, REG_PWR_MGMT_2, w);
	if (r)
		return r;

	if (st->chip_config.has_compass) {
		if (st->chip_config.compass_enable)
			r = st->slave_compass->resume(st);
		else
			r = st->slave_compass->suspend(st);
		if (r)
			return r;
	}

	return 0;
}

static int inv_setup_dmp_rate(struct inv_mpu_state *st)
{
	int i;

	for (i = 0; i < SENSOR_NUM_MAX; i++) {
		if (st->sensor[i].on) {
			st->cntl |= st->sensor[i].output;
			st->sensor[i].dur =
				st->eng_info[st->sensor[i].engine_base].dur;
			st->sensor[i].div = 1;
		}
	}

	return 0;
}

/*
 *  inv_set_lpf() - set low pass filer based on fifo rate.
 */
static int inv_set_lpf(struct inv_mpu_state *st, int rate)
{
	const short hz[] = {188, 98, 42, 20, 10, 5};
	const int   d[] = {INV_FILTER_188HZ, INV_FILTER_98HZ,
			INV_FILTER_42HZ, INV_FILTER_20HZ,
			INV_FILTER_10HZ, INV_FILTER_5HZ};
	int i, h, data, result;

	if (st->chip_config.eis_enable || (st->ois.en)) {
		h = (rate >> 1);
		i = 0;
		while ((h < hz[i]) && (i < ARRAY_SIZE(d) - 1))
			i++;
		data = d[i];
#ifdef CONFIG_INV_MPU_IIO_ICM20690
		data |= (EXT_SYNC_SET | BIT_FIFO_COUNT_REC);
#else
		data |= (EXT_SYNC_SET);
#endif
		result = inv_plat_single_write(st, REG_CONFIG, data);
		if (result)
			return result;

		st->chip_config.lpf = data;
		result = inv_plat_single_write(st, REG_LP_MODE_CTRL, 0);
		if (result)
			return result;
	} else {
		result = inv_plat_single_write(st, REG_LP_MODE_CTRL,
							BIT_GYRO_CYCLE_EN);
		if (result)
			return result;
#ifdef CONFIG_INV_MPU_IIO_ICM20690
		data = (BIT_FIFO_COUNT_REC);
		result = inv_plat_single_write(st, REG_CONFIG, data | 3);
#endif
	}

	return 0;
}

static int inv_set_div(struct inv_mpu_state *st, int a_d, int g_d)
{
	int result, div;

	if (st->chip_config.gyro_enable)
		div = g_d;
	else
		div = a_d;
	if (st->chip_config.eis_enable)
		div = 0;

	pr_debug("div= %d\n", div);
	result = inv_plat_single_write(st, REG_SAMPLE_RATE_DIV, div);
	msleep(10);

	return result;
}

static int inv_set_batch(struct inv_mpu_state *st)
{
	int res = 0;
	u32 w;

	if (st->batch.timeout) {
		w = st->batch.timeout * st->eng_info[ENGINE_GYRO].running_rate
					* st->batch.pk_size / 1000;
		if (w > 1007)
			w = 1007;
	} else {
		w = 0;
	}
#ifdef CONFIG_INV_MPU_IIO_ICM20602
	st->batch.fifo_wm_th = w;
	res = inv_plat_single_write(st, REG_FIFO_WM_TH2, w & 0xff);
	if (res)
		return res;
	res = inv_plat_single_write(st, REG_FIFO_WM_TH1, (w >> 8) & 0xff);
#else
	/* this should be removed once we have two registers */
	if (st->batch.pk_size)
		w /= st->batch.pk_size;
	st->batch.fifo_wm_th = w;
	pr_debug("running= %d, pksize=%d, to=%d w=%d\n",
		st->eng_info[ENGINE_GYRO].running_rate,
		st->batch.pk_size, st->batch.timeout, w);
	res = inv_plat_single_write(st, REG_FIFO_WM_TH, w);
#endif
	return res;
}

static int inv_set_rate(struct inv_mpu_state *st)
{
	int g_d, a_d, result, i;

	result = inv_setup_dmp_rate(st);
	if (result)
		return result;

	g_d = st->eng_info[ENGINE_GYRO].divider - 1;
	a_d = st->eng_info[ENGINE_ACCEL].divider - 1;
	result = inv_set_div(st, a_d, g_d);
	if (result)
		return result;
	result = inv_set_lpf(st, st->eng_info[ENGINE_GYRO].running_rate);
	st->batch.pk_size = 0;
	for (i = 0; i < SENSOR_NUM_MAX; i++) {
		if (st->sensor[i].on)
			st->batch.pk_size +=  st->sensor[i].sample_size;
	}

	inv_set_batch(st);

	return result;
}

static int inv_determine_engine(struct inv_mpu_state *st)
{
	int i;
	bool a_en, g_en, c_en;
	int compass_rate, accel_rate, gyro_rate;
	u32 base_time;

	a_en = false;
	g_en = false;
	c_en = false;
	compass_rate = MPU_INIT_SENSOR_RATE;
	gyro_rate = MPU_INIT_SENSOR_RATE;
	accel_rate = MPU_INIT_SENSOR_RATE;
	/* loop the streaming sensors to see which engine needs to be turned on
		*/
	for (i = 0; i < SENSOR_NUM_MAX; i++) {
		if (st->sensor[i].on) {
			a_en |= st->sensor[i].a_en;
			g_en |= st->sensor[i].g_en;
			c_en |= st->sensor[i].c_en;
			if (st->sensor[i].c_en)
				compass_rate =
				    max(compass_rate, st->sensor[i].rate);
		}
	}
	if (st->ois.en)
		g_en = true;

	if ((st->chip_type == ICM20602) || ((st->chip_type == ICM20690) &&
						st->chip_config.eis_enable))
		st->sensor[SENSOR_TEMP].on = true;
	else
		st->sensor[SENSOR_TEMP].on = false;

	if (st->chip_config.eis_enable) {
		g_en = true;
		st->eis.frame_count = 0;
		st->eis.fsync_delay = 0;
		st->eis.gyro_counter = 0;
		st->eis.voting_count = 0;
		st->eis.voting_count_sub = 0;
		gyro_rate = BASE_SAMPLE_RATE;
	} else {
		st->eis.eis_triggered = false;
		st->eis.prev_state = false;
	}

	if (compass_rate > MAX_COMPASS_RATE)
		compass_rate = MAX_COMPASS_RATE;
	st->chip_config.compass_rate = compass_rate;
	accel_rate = st->sensor[SENSOR_ACCEL].rate;
	gyro_rate  = max(gyro_rate, st->sensor[SENSOR_GYRO].rate);

	if (compass_rate < MIN_COMPASS_RATE)
		compass_rate = MIN_COMPASS_RATE;
	st->clock_base = ENGINE_ACCEL;
	if (c_en && (!g_en) && (!a_en)) {
		a_en = true;
		accel_rate = compass_rate;
	}
	if (g_en) {
		/* gyro engine needs to be fastest */
		if (a_en)
			gyro_rate = max(gyro_rate, accel_rate);
		if (c_en) {
			if (gyro_rate < compass_rate)
				gyro_rate = max(gyro_rate, compass_rate);
		}
		accel_rate = gyro_rate;
		compass_rate = gyro_rate;
		st->clock_base = ENGINE_GYRO;
		st->sensor[SENSOR_COMPASS].engine_base = ENGINE_GYRO;
	} else if (a_en) {
		/* accel engine needs to be fastest if gyro engine is off */
		if (c_en) {
			if (accel_rate < compass_rate)
				accel_rate = max(accel_rate, compass_rate);
		}
		compass_rate = accel_rate;
		gyro_rate = accel_rate;
		st->clock_base = ENGINE_ACCEL;
		st->sensor[SENSOR_COMPASS].engine_base = ENGINE_ACCEL;
	} else if (c_en) {
		gyro_rate = compass_rate;
		accel_rate = compass_rate;
	}

	st->eng_info[ENGINE_GYRO].running_rate = gyro_rate;
	st->eng_info[ENGINE_ACCEL].running_rate = accel_rate;
	st->eng_info[ENGINE_I2C].running_rate = compass_rate;
	/* engine divider for pressure and compass is set later */
	if (st->chip_config.eis_enable) {
		st->eng_info[ENGINE_GYRO].divider = 1;
		st->eng_info[ENGINE_ACCEL].divider = 1;
		st->eng_info[ENGINE_I2C].divider = 1;
	} else {
		st->eng_info[ENGINE_GYRO].divider =
			(BASE_SAMPLE_RATE / MPU_DEFAULT_DMP_FREQ) *
			(MPU_DEFAULT_DMP_FREQ /
			st->eng_info[ENGINE_GYRO].running_rate);
		st->eng_info[ENGINE_ACCEL].divider =
			(BASE_SAMPLE_RATE / MPU_DEFAULT_DMP_FREQ) *
			(MPU_DEFAULT_DMP_FREQ /
			st->eng_info[ENGINE_ACCEL].running_rate);
		st->eng_info[ENGINE_I2C].divider =
			(BASE_SAMPLE_RATE / MPU_DEFAULT_DMP_FREQ) *
				(MPU_DEFAULT_DMP_FREQ /
					st->eng_info[ENGINE_I2C].running_rate);
	}
	base_time = NSEC_PER_SEC;

	st->eng_info[ENGINE_GYRO].base_time = base_time;
	st->eng_info[ENGINE_ACCEL].base_time = base_time;

	inv_calc_engine_dur(&st->eng_info[ENGINE_GYRO]);
	inv_calc_engine_dur(&st->eng_info[ENGINE_ACCEL]);

	pr_debug("gen: %d aen: %d cen: %d grate: %d arate: %d\n",
				g_en, a_en, c_en, gyro_rate, accel_rate);

	st->chip_config.gyro_enable = g_en;
	st->chip_config.accel_enable = a_en;
	st->chip_config.compass_enable = c_en;

	if (c_en)
		st->chip_config.slave_enable = 1;
	else
		st->chip_config.slave_enable = 0;

	return 0;
}

/*
 *  set_inv_enable() - enable function.
 */
int set_inv_enable(struct iio_dev *indio_dev)
{
	int result;
	struct inv_mpu_state *st = iio_priv(indio_dev);

	result = inv_switch_power_in_lp(st, true);
	if (result)
		return result;
	inv_determine_engine(st);
	result = inv_set_rate(st);
	if (result) {
		pr_err("inv_set_rate error\n");
		return result;
	}
	result = inv_turn_on_engine(st);
	if (result) {
		pr_err("inv_turn_on_engine error\n");
		return result;
	}
	result = inv_reset_fifo(st, false);
	if (result)
		return result;
	result = inv_switch_power_in_lp(st, false);
	if ((!st->chip_config.gyro_enable) &&
		(!st->chip_config.accel_enable) &&
		(!st->chip_config.compass_enable) && (!st->ois.en)) {
		inv_set_power(st, false);
		return 0;
	}

	return result;
}
/* dummy function for 20608D */
int inv_enable_pedometer_interrupt(struct inv_mpu_state *st, bool en)
{
	return 0;
}
int inv_dmp_read(struct inv_mpu_state *st, int off, int size, u8 *buf)
{
	return 0;
}
int inv_firmware_load(struct inv_mpu_state *st)
{
	return 0;
}
