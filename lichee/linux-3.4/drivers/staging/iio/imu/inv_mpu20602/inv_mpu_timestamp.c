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

#include <linux/module.h>
#include <linux/init.h>
#include <linux/slab.h>
#include <linux/err.h>
#include <linux/delay.h>
#include <linux/sysfs.h>
#include <linux/jiffies.h>
#include <linux/irq.h>
#include <linux/interrupt.h>
#include <linux/kfifo.h>
#include <linux/poll.h>
#include <linux/miscdevice.h>
#include <linux/math64.h>

#include "inv_mpu_iio.h"

int inv_update_dmp_ts(struct inv_mpu_state *st, int ind)
{
	int i;
	u32 counter;
	u64 ts;
	enum INV_ENGINE en_ind;

	ts = st->last_run_time - st->sensor[ind].time_calib;
	counter = st->sensor[ind].sample_calib;
	en_ind = st->sensor[ind].engine_base;
	if (en_ind != st->clock_base)
		return 0;
	/* we average over 2 seconds period to do the timestamp calculation */
	if (ts < 2 * NSEC_PER_SEC)
		return 0;
	/* this is the first time we do timestamp averaging, return */
	/* after resume from suspend, the clock of linux has up to 1 seconds
	   drift. We should start from the resume clock instead of using clock
	   before resume */
	if ((!st->sensor[ind].calib_flag) || st->resume_flag) {
		st->sensor[ind].sample_calib = 0;
		st->sensor[ind].time_calib = st->last_run_time;
		st->sensor[ind].calib_flag = 1;
		st->resume_flag = false;

		return 0;
	}
	/* if the sample number in current FIFO is not zero and between now and
		last update time is more than 2 seconds, we do calculation */
	if ((counter > 0) &&
	    (st->last_run_time - st->eng_info[en_ind].last_update_time >
	     2 * NSEC_PER_SEC)) {
		/* duration for each sensor */
		st->sensor[ind].dur = (u32) div_u64(ts, counter);
		/* engine duration derived from each sensor */
		if (st->sensor[ind].div)
			st->eng_info[en_ind].dur = st->sensor[ind].dur /
							st->sensor[ind].div;
		else
			pr_err("sensor %d divider zero!\n", ind);
		/* update base time for each sensor */
		if (st->eng_info[en_ind].divider)
			st->eng_info[en_ind].base_time =
					(st->eng_info[en_ind].dur /
					st->eng_info[en_ind].divider) *
					st->eng_info[en_ind].orig_rate;
		else
			pr_err("engine %d divider zero!\n", en_ind);
		st->eng_info[en_ind].last_update_time = st->last_run_time;
		/* update all the sensors duration based on the same engine */
		for (i = 0; i < SENSOR_NUM_MAX; i++) {
			if (st->sensor[i].on &&
			    (st->sensor[i].engine_base == en_ind))
				st->sensor[i].dur = st->sensor[i].div *
				    st->eng_info[en_ind].dur;
		}

	}
	st->sensor[ind].sample_calib = 0;
	st->sensor[ind].time_calib = st->last_run_time;

	return 0;
}

int inv_get_dmp_ts(struct inv_mpu_state *st, int i)
{
	st->sensor[i].ts += (st->sensor[i].dur + st->sensor[i].ts_adj);
	if (st->header_count == 1)
		inv_update_dmp_ts(st, i);

	return 0;
}

int inv_process_step_det(struct inv_mpu_state *st, u8 *dptr)
{
	u32 tmp;
	u64 t;
	s16 s[3];

	tmp = be32_to_int(dptr);
	tmp = inv_get_cntr_diff(st->start_dmp_counter, tmp);
	t = st->last_run_time - (u64) tmp * st->eng_info[ENGINE_ACCEL].dur;
	if (st->curr_steps > st->step_det_count)
		tmp = st->curr_steps - st->step_det_count;
	else
		tmp = 0;
	pr_debug("Step detect, batch %s\n", (st->batch.on) ? "On" : "Off");
	if (st->batch.on)
		inv_send_steps(st, tmp, t);
	if (st->step_detector_l_on)
		inv_push_8bytes_buffer(st, STEP_DETECTOR_HDR, t, s);
	if (st->step_detector_wake_l_on)
		inv_push_8bytes_buffer(st, STEP_DETECTOR_WAKE_HDR, t, s);

	return 0;
}

/* inv_bound_timestamp (struct inv_mpu_state *st)
	The purpose this function is to give a generic bound to each
	sensor timestamp. The timestamp cannot exceed current time.
	The timestamp cannot backwards one sample time either, otherwise, there
	would be another sample in between. Using this principle, we can bound
	the sensor samples */
int inv_bound_timestamp(struct inv_mpu_state *st)
{
	s64 elaps_time, thresh1, thresh2;
	int i;

#define INV_TIME_CALIB_THRESHOLD_1 2
#define JITTER_THRESH (0)
#define DELAY_THRESH  (0)

	for (i = 0; i < SENSOR_NUM_MAX; i++) {
		if (st->sensor[i].on) {
			if (st->sensor[i].count) {
				elaps_time = ((u64) (st->sensor[i].dur)) *
				    st->sensor[i].count;
				thresh1 = st->last_run_time - elaps_time -
				    JITTER_THRESH;
				thresh2 = thresh1 - st->sensor[i].dur;
				if (thresh1 < 0)
					thresh1 = 0;
				if (thresh2 < 0)
					thresh2 = 0;
				st->sensor[i].ts_adj = 0;
				if ((st->time_calib_counter >=
					INV_TIME_CALIB_THRESHOLD_1) &&
							(!st->resume_flag)) {
					if (st->sensor[i].ts < thresh2)
						st->sensor[i].ts_adj =
						    thresh2 - st->sensor[i].ts;
				} else if ((st->time_calib_counter >=
					INV_TIME_CALIB_THRESHOLD_1) &&
							st->resume_flag) {
					if (st->sensor[i].ts < thresh2)
						st->sensor[i].ts =
							st->last_run_time -
							elaps_time -
							DELAY_THRESH;
				} else {
					st->sensor[i].ts = st->last_run_time -
						elaps_time - DELAY_THRESH;
				}

				if (st->sensor[i].ts > thresh1)
					st->sensor[i].ts_adj =
						thresh1 - st->sensor[i].ts;
				pr_debug("adj= %lld\n", st->sensor[i].ts_adj);
				pr_debug("dur= %d count= %d last= %lld\n",
							st->sensor[i].dur,
							st->sensor[i].count,
							st->last_run_time);
				if (st->sensor[i].ts_adj &&
						(st->sensor[i].count > 1))
					st->sensor[i].ts_adj =
					div_s64(st->sensor[i].ts_adj,
							st->sensor[i].count);
			} else if (st->time_calib_counter <
				   INV_TIME_CALIB_THRESHOLD_1) {
				st->sensor[i].ts = st->reset_ts;
			}
		}
	}

	return 0;
}

static int inv_get_step_params(struct inv_mpu_state *st)
{
	int result;

	result = inv_switch_power_in_lp(st, true);
	result |= read_be32_from_mem(st, &st->curr_steps, PEDSTD_STEPCTR);
	result |= read_be32_from_mem(st, &st->start_dmp_counter, DMPRATE_CNTR);
	result |= inv_switch_power_in_lp(st, false);

	return result;
}
/* static int inv_prescan_data(struct inv_mpu_state *st, u8 * dptr, int len)
	prescan data to know what type of data and how many samples of data
	in current FIFO reading.
*/
static int inv_prescan_data(struct inv_mpu_state *st, u8 *dptr, int len)
{
	int res, pk_size, i;
	bool done_flag;
	u16 hdr;

	done_flag = false;
	st->header_count = 0;
	st->step_det_count = 0;
	st->time_calib_counter++;
	for (i = 0; i < SENSOR_NUM_MAX; i++)
		st->sensor[i].count = 0;
	while (!done_flag) {
		if (len > HEADER_SZ) {
			hdr = (u16) be16_to_cpup((__be16 *) (dptr));
			if (!hdr) {
				pr_err("error header zero\n");
				st->left_over_size = 0;
				return -EINVAL;
			}
			res = inv_get_packet_size(st, hdr, &pk_size, dptr);
			if (res) {
				if (!st->chip_config.is_asleep)
					pr_err
				("prescan error in header parsing=%x size=%d\n",
				hdr, len);
				st->left_over_size = 0;

				return -EINVAL;
			}
			if (len >= pk_size) {
				inv_pre_parse_packet(st, hdr, dptr + HEADER_SZ);
				len -= pk_size;
				dptr += pk_size;
			} else {
				done_flag = true;
			}
		} else {
			done_flag = true;
		}
	}
	if (st->step_det_count)
		inv_get_step_params(st);
	inv_bound_timestamp(st);

	return 0;
}

int inv_process_dmp_data(struct inv_mpu_state *st)
{
	int total_bytes, tmp, res, fifo_count, pk_size;
	u8 *dptr, *d;
	u16 hdr;
	u8 data[2];
	bool done_flag;

	st->last_run_time = get_time_ns();
	res = inv_plat_read(st, REG_FIFO_COUNT_H, FIFO_COUNT_BYTE, data);
	if (res) {
		pr_debug("read REG_FIFO_COUNT_H failed\n");
		return res;
	}
	fifo_count = be16_to_cpup((__be16 *) (data));
	if (!fifo_count) {
		pr_debug("REG_FIFO_COUNT_H size is 0\n");
		return 0;
	}
	st->fifo_count = fifo_count;
	d = st->fifo_data_store;

	if (st->left_over_size > LEFT_OVER_BYTES) {
		st->left_over_size = 0;
		pr_debug("Size error\n");
		return -EINVAL;
	}

	if (st->left_over_size > 0)
		memcpy(d, st->left_over, st->left_over_size);

	dptr = d + st->left_over_size;
	total_bytes = fifo_count;
	while (total_bytes > 0) {
		if (total_bytes < MAX_FIFO_READ_SIZE)
			tmp = total_bytes;
		else
			tmp = MAX_FIFO_READ_SIZE;
		res = inv_plat_read(st, REG_FIFO_R_W, tmp, dptr);
		if (res < 0) {
			pr_debug("read REG_FIFO_R_W is failed\n");
			return res;
		}
		dptr += tmp;
		total_bytes -= tmp;
	}
	dptr = d;
	total_bytes = fifo_count + st->left_over_size;
	res = inv_prescan_data(st, dptr, total_bytes);
	if (res) {
		pr_info("prescan failed\n");
		return -EINVAL;
	}
	dptr = d;
	done_flag = false;
	pr_debug("dd: %x, %x, %x, %x, %x, %x, %x, %x\n", d[0], d[1], d[2],
						d[3], d[4], d[5], d[6], d[7]);
	pr_debug("dd2: %x, %x, %x, %x, %x, %x, %x, %x\n", d[8], d[9], d[10],
					d[11], d[12], d[13], d[14], d[15]);
	while (!done_flag) {
		if (total_bytes > HEADER_SZ) {
			hdr = (u16) be16_to_cpup((__be16 *) (dptr));
			res = inv_get_packet_size(st, hdr, &pk_size, dptr);
			if (res) {
				pr_err
		("processing error in header parsing=%x fifo_count= %d\n",
		hdr, fifo_count);
				st->left_over_size = 0;

				return -EINVAL;
			}
			if (total_bytes >= pk_size) {
				inv_parse_packet(st, hdr, dptr + HEADER_SZ);
				total_bytes -= pk_size;
				dptr += pk_size;
			} else {
				done_flag = true;
			}
		} else {
			done_flag = true;
		}
	}
	st->left_over_size = total_bytes;
	if (st->left_over_size > LEFT_OVER_BYTES) {
		st->left_over_size = 0;
		return -EINVAL;
	}

	if (st->left_over_size)
		memcpy(st->left_over, dptr, st->left_over_size);

	return 0;
}
