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

int inv_get_pedometer_steps(struct inv_mpu_state *st, int *ped)
{
	int r;

	r = read_be32_from_mem(st, ped, PEDSTD_STEPCTR);

	return r;
}

int inv_get_pedometer_time(struct inv_mpu_state *st, int *ped)
{
	int r;

	r = read_be32_from_mem(st, ped, PEDSTD_TIMECTR);

	return r;
}

int inv_read_pedometer_counter(struct inv_mpu_state *st)
{
	int result;
	u32 last_step_counter, curr_counter;
	u64 counter;

	result = read_be32_from_mem(st, &last_step_counter, STPDET_TIMESTAMP);
	if (result)
		return result;
	if (0 != last_step_counter) {
		result = read_be32_from_mem(st, &curr_counter, DMPRATE_CNTR);
		if (result)
			return result;
		counter = inv_get_cntr_diff(curr_counter, last_step_counter);
		st->ped.last_step_time = get_time_ns() - counter *
		    st->eng_info[ENGINE_ACCEL].dur;
	}

	return 0;
}
#if defined(CONFIG_INV_MPU_IIO_ICM20602) || defined(CONFIG_INV_MPU_IIO_ICM20690)
int inv_check_sensor_rate(struct inv_mpu_state *st)
{
	st->sensor_l[SENSOR_L_GESTURE_ACCEL].rate = GESTURE_ACCEL_RATE;

	return 0;
}

static void inv_check_wake_non_wake(struct inv_mpu_state *st,
			enum SENSOR_L wake, enum SENSOR_L non_wake)
{
	int tmp_rate;

	tmp_rate = MPU_INIT_SENSOR_RATE;
	if (st->sensor_l[wake].on)
		tmp_rate = st->sensor_l[wake].rate;
	if (st->sensor_l[non_wake].on)
		tmp_rate = max(tmp_rate, st->sensor_l[non_wake].rate);
	st->sensor_l[wake].rate = tmp_rate;
	st->sensor_l[non_wake].rate = tmp_rate;
}

static void inv_check_wake_non_wake_divider(struct inv_mpu_state *st,
			enum SENSOR_L wake, enum SENSOR_L non_wake)
{
	if (st->sensor_l[wake].on && st->sensor_l[non_wake].on)
		st->sensor_l[non_wake].div = 0xffff;

}

int inv_check_sensor_on(struct inv_mpu_state *st)
{
	int i, max_rate;

	for (i = 0; i < SENSOR_NUM_MAX; i++)
		st->sensor[i].on = false;
	for (i = 0; i < SENSOR_NUM_MAX; i++)
		st->sensor[i].rate = MPU_INIT_SENSOR_RATE;

	if ((st->step_detector_l_on
			|| st->step_detector_wake_l_on
			|| st->step_counter_l_on
			|| st->step_counter_wake_l_on
			|| st->chip_config.pick_up_enable
			|| st->chip_config.tilt_enable)
			&& (!st->sensor_l[SENSOR_L_ACCEL].on)
			&& (!st->sensor_l[SENSOR_L_ACCEL_WAKE].on))
		st->sensor_l[SENSOR_L_GESTURE_ACCEL].on = true;
	else
		st->sensor_l[SENSOR_L_GESTURE_ACCEL].on = false;


	st->chip_config.wake_on = false;
	for (i = 0; i < SENSOR_L_NUM_MAX; i++) {
		if (st->sensor_l[i].on && st->sensor_l[i].rate) {
			st->sensor[st->sensor_l[i].base].on = true;
			st->chip_config.wake_on |= st->sensor_l[i].wake_on;
		}
	}
	if (st->sensor_l[SENSOR_L_GESTURE_ACCEL].on &&
				(!st->sensor[SENSOR_GYRO].on) &&
				(!st->sensor[SENSOR_COMPASS].on))
		st->gesture_only_on = true;
	else
		st->gesture_only_on = false;

	for (i = 0; i < SENSOR_L_NUM_MAX; i++) {
		if (st->sensor_l[i].on) {
			st->sensor[st->sensor_l[i].base].rate =
			    max(st->sensor[st->sensor_l[i].base].rate,
							st->sensor_l[i].rate);
		}
	}
	max_rate = MPU_INIT_SENSOR_RATE;
	if (st->chip_config.eis_enable) {
		max_rate = BASE_SAMPLE_RATE;
		st->sensor_l[SENSOR_L_EIS_GYRO].rate = BASE_SAMPLE_RATE;
	}

	for (i = 0; i < SENSOR_NUM_MAX; i++) {
		if (st->sensor[i].on) {
			max_rate = max(max_rate, st->sensor[i].rate);
		}
	}
	for (i = 0; i < SENSOR_NUM_MAX; i++) {
		if (st->sensor[i].on) {
			st->sensor[i].rate = max_rate;
		}
	}
	inv_check_wake_non_wake(st, SENSOR_L_GYRO_WAKE, SENSOR_L_GYRO);
	inv_check_wake_non_wake(st, SENSOR_L_ACCEL_WAKE, SENSOR_L_ACCEL);
	inv_check_wake_non_wake(st, SENSOR_L_MAG_WAKE, SENSOR_L_MAG);

	for (i = 0; i < SENSOR_L_NUM_MAX; i++) {
		if (st->sensor_l[i].on) {
			if (st->sensor_l[i].rate)
				st->sensor_l[i].div =
				    st->sensor[st->sensor_l[i].base].rate
							/ st->sensor_l[i].rate;
			else
				st->sensor_l[i].div = 0xffff;
			pr_debug("sensor= %d, div= %d\n",
						i, st->sensor_l[i].div);
		}
	}

	inv_check_wake_non_wake_divider(st, SENSOR_L_GYRO_WAKE, SENSOR_L_GYRO);
	inv_check_wake_non_wake_divider(st, SENSOR_L_ACCEL_WAKE,
							SENSOR_L_ACCEL);
	inv_check_wake_non_wake_divider(st, SENSOR_L_MAG_WAKE, SENSOR_L_MAG);

	if (st->step_detector_wake_l_on ||
			st->step_counter_wake_l_on ||
			st->chip_config.pick_up_enable ||
			st->chip_config.tilt_enable)
		st->chip_config.wake_on = true;

	return 0;
}
#else
int inv_check_sensor_on(struct inv_mpu_state *st)
{
	int i;


	for (i = 0; i < SENSOR_NUM_MAX; i++)
		st->sensor[i].on = false;
	st->chip_config.wake_on = false;
	for (i = 0; i < SENSOR_L_NUM_MAX; i++) {
		if (st->sensor_l[i].on && st->sensor_l[i].rate) {
			st->sensor[st->sensor_l[i].base].on = true;
			st->chip_config.wake_on |= st->sensor_l[i].wake_on;
		}
	}

	if (st->step_detector_wake_l_on ||
			st->step_counter_wake_l_on ||
			st->chip_config.pick_up_enable ||
			st->chip_config.tilt_enable ||
			st->smd.on)
		st->chip_config.wake_on = true;

	return 0;
}

int inv_check_sensor_rate(struct inv_mpu_state *st)
{
	int i;

	for (i = 0; i < SENSOR_NUM_MAX; i++)
		st->sensor[i].rate = MPU_INIT_SENSOR_RATE;
	for (i = 0; i < SENSOR_L_NUM_MAX; i++) {
		if (st->sensor_l[i].on) {
			st->sensor[st->sensor_l[i].base].rate =
			    max(st->sensor[st->sensor_l[i].base].rate,
				st->sensor_l[i].rate);
		}
	}
	for (i = 0; i < SENSOR_L_NUM_MAX; i++) {
		if (st->sensor_l[i].on) {
			if (st->sensor_l[i].rate)
				st->sensor_l[i].div =
				    st->sensor[st->sensor_l[i].base].rate
				    / st->sensor_l[i].rate;
			else
				st->sensor_l[i].div = 0xffff;
		}
	}

	return 0;
}
#endif
