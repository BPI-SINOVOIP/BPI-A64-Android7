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

static char iden[] = { 1, 0, 0, 0, 1, 0, 0, 0, 1 };
static char fsync_delay[] = {4, 5, 1, 2, 3};

#ifdef CONFIG_INV_MPU_IIO_ICM20608D
static int inv_push_accuracy(struct inv_mpu_state *st, int ind, u16 accur)
{
	struct iio_dev *indio_dev = iio_priv_to_dev(st);
	u8 buf[IIO_BUFFER_BYTES];
	u16 hdr;

	hdr = st->sensor_accuracy[ind].header;
	if (st->sensor_acurracy_flag[ind]) {
		if (!accur)
			accur = DEFAULT_ACCURACY;
		else
			st->sensor_acurracy_flag[ind] = 0;
	}
	memcpy(buf, &hdr, sizeof(hdr));
	memcpy(buf + sizeof(hdr), &accur, sizeof(accur));
#ifdef LINUX_KERNEL_3_10
	iio_push_to_buffers(indio_dev, buf);
#else
	iio_push_to_buffer(indio_dev->buffer, buf, 0);
#endif

	pr_debug("Accuracy for sensor [%d] is [%d]\n", ind, accur);

	return 0;
}

static int inv_process_gyro(struct inv_mpu_state *st, u8 *d, u64 t)
{
	s16 raw[3];
	s32 bias[3];
	s32 scaled_bias[3];
	s32 calib[3];
	int i;
#define BIAS_UNIT 2859

	for (i = 0; i < 3; i++)
		raw[i] = be16_to_cpup((__be16 *) (d + i * 2));
	for (i = 0; i < 3; i++)
		bias[i] = be32_to_int(d + 6 + i * 4);

	for (i = 0; i < 3; i++) {
		scaled_bias[i] = ((bias[i] << 14) / BIAS_UNIT) << 1;
		calib[i] = (raw[i] << 15) - scaled_bias[i];
	}

	if ((scaled_bias[0] != st->gyro_bias[0]) && scaled_bias[0]) {
		for (i = 0; i < 3; i++)
			st->gyro_bias[i] = scaled_bias[i];
		inv_push_accuracy(st, SENSOR_GYRO_ACCURACY, 3);
	}

	inv_push_gyro_data(st, raw, calib, t);

	return 0;
}
#else
static int inv_process_gyro(struct inv_mpu_state *st, u8 *d, u64 t)
{
	s16 raw[3];
	s32 calib[3];
	int i;
#define BIAS_UNIT 2859

	for (i = 0; i < 3; i++)
		raw[i] = be16_to_cpup((__be16 *) (d + i * 2));

	for (i = 0; i < 3; i++)
		calib[i] = (raw[i] << 15);

	inv_push_gyro_data(st, raw, calib, t);

	return 0;
}
#endif

static int inv_apply_soft_iron(struct inv_mpu_state *st, s16 *out_1, s32 *out_2)
{
	int *r, i, j;
	s64 tmp;

	r = st->final_compass_matrix;
	for (i = 0; i < THREE_AXES; i++) {
		tmp = 0;
		for (j = 0; j < THREE_AXES; j++)
			tmp  +=
			(s64)r[i * THREE_AXES + j] * (((int)out_1[j]) << 16);
		out_2[i] = (int)(tmp >> 30);
	}

	return 0;
}

#ifdef CONFIG_INV_MPU_IIO_ICM20602
static int inv_check_fsync(struct inv_mpu_state *st, u8 fsync_status)
{
	u8 data[1];

	if (!st->chip_config.eis_enable)
		return 0;
	inv_plat_read(st, REG_FSYNC_INT, 1, data);
	if (data[0] & BIT_FSYNC_INT) {
		pr_debug("fsync\n");
		st->eis.eis_triggered = true;
		st->eis.fsync_delay = 1;
		st->eis.prev_state = 1;
		st->eis.frame_count++;
		st->eis.eis_frame = true;
	}
	st->header_count--;

	return 0;
}

#else
int inv_check_fsync(struct inv_mpu_state *st, u8 fsync_status)
{
	if (!st->chip_config.eis_enable)
		return 0;
	if ((fsync_status & INV_FSYNC_TEMP_BIT) && (st->eis.prev_state == 0)) {
		pr_debug("fsync\n");
		st->eis.eis_triggered = true;
		st->eis.fsync_delay = 1;
		st->eis.prev_state = 1;
		st->eis.frame_count++;
		st->eis.eis_frame = true;
	} else if (fsync_status & INV_FSYNC_TEMP_BIT) {
		st->eis.prev_state = 1;
	} else {
		st->eis.prev_state = 0;
	}

	return 0;
}
#endif

static int inv_push_sensor(struct inv_mpu_state *st, int ind, u64 t, u8 *d)
{
	int res, i;
	s16 out_1[3];
	s32 out_2[3];

	res = 0;
	switch (ind) {
	case SENSOR_ACCEL:
		inv_convert_and_push_8bytes(st, ind, d, t, iden);
		break;
	case SENSOR_TEMP:
		inv_check_fsync(st, d[1]);
		break;
	case SENSOR_GYRO:
		inv_process_gyro(st, d, t);
		break;
#ifdef CONFIG_INV_MPU_IIO_ICM20608D
	case SENSOR_SIXQ:
		inv_convert_and_push_16bytes(st, ind, d + 2, t, iden);
		break;
	case SENSOR_PEDQ:
		inv_convert_and_push_8bytes(st, ind, d, t, iden);
		break;
#endif
	case SENSOR_COMPASS:
		if (d[0] != 1) {
			pr_info("Bad compass data= %x\n", d[0]);
			return -EINVAL;
		}

		for (i = 0; i < 3; i++)
			out_1[i] = be16_to_cpup((__be16 *) (d + i * 2 + 2));
		inv_apply_soft_iron(st, out_1, out_2);
		inv_push_16bytes_buffer(st, ind, t, out_2, 0);
		break;
	default:
		break;
	}

	return res;
}

int inv_get_packet_size(struct inv_mpu_state *st, u16 hdr,
			       u32 *pk_size, u8 *dptr)
{
	int i, size;

	size = HEADER_SZ;
	for (i = 0; i < SENSOR_NUM_MAX; i++) {
		if (hdr == st->sensor[i].output) {
			if (st->sensor[i].on)
				size += st->sensor[i].sample_size;
			else
				return -EINVAL;
		}
	}

	if (hdr == PED_STEPDET_SET) {
		if (st->chip_config.step_detector_on) {
			size += HEADER_SZ;
			size += PED_STEPDET_TIMESTAMP_SZ;
		} else {
			pr_err("ERROR: step detector should not be here\n");
			return -EINVAL;
		}
	}
	if (hdr == FSYNC_HDR) {
		if (st->chip_config.eis_enable) {
			size += FSYNC_PK_SZ;
		} else {
			pr_err("ERROR: eis packet should not be here\n");
			return -EINVAL;
		}
	}

	*pk_size = size;

	return 0;
}

int inv_parse_packet(struct inv_mpu_state *st, u16 hdr, u8 *dptr)
{
	int i;
	u64 t;
	bool data_header;
	u16 delay;

	t = 0;
	pr_debug("hdr= %x\n", hdr);
	data_header = false;
	for (i = 0; i < SENSOR_NUM_MAX; i++) {
		if (hdr == st->sensor[i].output) {
			inv_get_dmp_ts(st, i);
			st->sensor[i].sample_calib++;
			inv_push_sensor(st, i, st->sensor[i].ts, dptr);
			dptr += st->sensor[i].sample_size;
			t = st->sensor[i].ts;
			data_header = true;
		}
	}
	if (data_header)
		st->header_count--;
	if (hdr == PED_STEPDET_SET) {
		dptr += HEADER_SZ;
		inv_process_step_det(st, dptr);
		dptr += PED_STEPDET_TIMESTAMP_SZ;
		st->step_det_count--;
	}

	if (hdr & PED_STEPIND_SET)
		inv_push_step_indicator(st, t);
	if (hdr == FSYNC_HDR) {
		st->eis.current_sync = true;
		st->eis.eis_triggered = true;
		st->eis.frame_count++;
		st->eis.eis_frame = true;
		delay = be16_to_cpup((__be16 *) (dptr));
		delay ^= 0x7bcf;
		if (delay > 4) {
			pr_info("ERROR FSYNC value= %d\n", delay);
			delay = 4;
		}
		st->eis.fsync_delay = fsync_delay[delay];
		dptr += FSYNC_PK_SZ;
	}


	return 0;
}

int inv_pre_parse_packet(struct inv_mpu_state *st, u16 hdr, u8 *dptr)
{
	int i;
	bool data_header;

	data_header = false;
	for (i = 0; i < SENSOR_NUM_MAX; i++) {
		if (hdr == st->sensor[i].output) {
			st->sensor[i].count++;
			dptr += st->sensor[i].sample_size;
			data_header = true;
		}
	}
	if (data_header)
		st->header_count++;
	if (hdr == PED_STEPDET_SET) {
		st->step_det_count++;
		dptr += HEADER_SZ;
		dptr += PED_STEPDET_TIMESTAMP_SZ;
	}
	if (hdr == FSYNC_HDR)
		dptr += FSYNC_PK_SZ;

	return 0;
}

static int inv_process_dmp_interrupt(struct inv_mpu_state *st)
{
#ifndef CONFIG_INV_MPU_IIO_ICM20602
	struct iio_dev *indio_dev = iio_priv_to_dev(st);
	u8 d[1];
	int result, step;

#define DMP_INT_SMD		0x04
#define DMP_INT_PED		0x08

	if ((!st->smd.on) && (!st->ped.on))
		return 0;

	result = inv_plat_read(st, REG_DMP_INT_STATUS, 1, d);
	if (result) {
		pr_info("REG_DMP_INT_STATUS result [%d]\n", result);
		return result;
	}

	if (d[0] & DMP_INT_SMD) {
		pr_info("Sinificant motion detected\n");
		sysfs_notify(&indio_dev->dev.kobj, NULL, "poll_smd");
		st->smd.on = false;
		st->trigger_state = EVENT_TRIGGER;
		set_inv_enable(indio_dev);
		st->wake_sensor_received = true;
	}

	step = -1;
	if (st->ped.on && (!st->batch.on)) {
		inv_switch_power_in_lp(st, true);
		if (st->ped.int_on) {
			if (d[0] & DMP_INT_PED) {
				pr_info("PEDO!!\n");
				sysfs_notify(&indio_dev->dev.kobj, NULL,
					     "poll_pedometer");
				inv_get_pedometer_steps(st, &step);
			}
		} else {
			inv_get_pedometer_steps(st, &step);
		}

		inv_switch_power_in_lp(st, false);
		if ((step != -1) && (step != st->prev_steps)) {
			inv_send_steps(st, step, st->last_run_time);
			st->prev_steps = step;
		}
	}
#endif
	return 0;
}
static int inv_push_2060X_data(struct inv_mpu_state *st, u8 *d)
{
	u8 *dptr;
	int i;

	dptr = d;

	for (i = 0; i < SENSOR_NUM_MAX; i++) {
		if (st->sensor[i].on) {
			inv_get_dmp_ts(st, i);
			st->sensor[i].sample_calib++;
			inv_push_sensor(st, i, st->sensor[i].ts, dptr);
			dptr += st->sensor[i].sample_size;
		}
	}
	/* inv_check_fsync1(st, fsync_status); */
	st->header_count--;

	return 0;
}

static int inv_process_206XX_data(struct inv_mpu_state *st)
{
	int total_bytes, tmp, res, fifo_count, pk_size, i;
	u8 *dptr, *d;
	u8 data[2];
	bool done_flag;
#if defined(CONFIG_INV_MPU_IIO_ICM20602) || defined(CONFIG_INV_MPU_IIO_ICM20690)
	u8 v;
#endif
#if defined(CONFIG_INV_MPU_IIO_ICM20602) || defined(CONFIG_INV_MPU_IIO_ICM20690)
	if (st->gesture_only_on && (!st->batch.timeout)) {
		res = inv_plat_read(st, REG_INT_STATUS, 1, data);
		if (res)
			return res;
		pr_debug("ges cnt=%d, statu=%x\n",
						st->gesture_int_count, data[0]);
		if (data[0] & (BIT_WOM_ALL_INT_EN)) {
			if (!st->gesture_int_count) {
				inv_switch_power_in_lp(st, true);
				res = inv_plat_single_write(st, REG_INT_ENABLE,
					BIT_WOM_ALL_INT_EN | BIT_DATA_RDY_EN);
				if (res)
					return res;
				v = 0;
				if (st->chip_config.gyro_enable)
					v |= BITS_GYRO_FIFO_EN;

				if (st->chip_config.accel_enable)
					v |= BIT_ACCEL_FIFO_EN;
				res = inv_plat_single_write(st, REG_FIFO_EN, v);
				inv_switch_power_in_lp(st, false);
				if (res)
					return res;
			}
			st->gesture_int_count = WOM_DELAY_THRESHOLD;
		} else {
			if (!st->gesture_int_count) {
				inv_switch_power_in_lp(st, true);
				res = inv_plat_single_write(st, REG_FIFO_EN, 0);
				res = inv_plat_single_write(st, REG_INT_ENABLE,
					BIT_WOM_ALL_INT_EN);
				inv_switch_power_in_lp(st, false);
				if (res)
					return res;
				return 0;
			}
			st->gesture_int_count--;
		}
	}
#endif
#ifdef CONFIG_INV_MPU_IIO_ICM20690
	if (st->batch.timeout) {
		res = inv_plat_read(st, REG_DMP_INT_STATUS, 1, data);
		if (res)
			return res;
	}
#endif
	st->last_run_time = get_time_ns();
	res = inv_plat_read(st, REG_FIFO_COUNT_H, FIFO_COUNT_BYTE, data);
	if (res) {
		pr_info("read REG_FIFO_COUNT_H failed= %d\n", res);
		return res;
	}
	fifo_count = be16_to_cpup((__be16 *) (data));
	pr_debug("fifc= %d\n", fifo_count);
	if (!fifo_count) {
		pr_debug("REG_FIFO_COUNT_H size is 0\n");
		return 0;
	}
#ifdef CONFIG_INV_MPU_IIO_ICM20690
	fifo_count *= st->batch.pk_size;
#endif
	st->fifo_count = fifo_count;
	d = st->fifo_data_store;

	if (st->left_over_size > LEFT_OVER_BYTES) {
		st->left_over_size = 0;
		pr_info("Size error\n");
		return -EINVAL;
	}

	if (st->left_over_size > 0)
		memcpy(d, st->left_over, st->left_over_size);

	dptr = d + st->left_over_size;
	total_bytes = fifo_count;
	pk_size = st->batch.pk_size;
	if (!pk_size)
		return -EINVAL;

	while (total_bytes > 0) {
		if (total_bytes < MAX_FIFO_READ_SIZE)
			tmp = total_bytes;
		else
			tmp = MAX_FIFO_READ_SIZE;
		res = inv_plat_read(st, REG_FIFO_R_W, tmp, dptr);
		if (res < 0) {
			pr_err("read REG_FIFO_R_W is failed\n");
			return res;
		}
		dptr += tmp;
		total_bytes -= tmp;
	}
	dptr = d;
	pr_debug("dd: %x, %x, %x, %x, %x, %x, %x, %x\n", d[0], d[1], d[2],
						d[3], d[4], d[5], d[6], d[7]);
	pr_debug("dd2: %x, %x, %x, %x, %x, %x, %x, %x\n", d[8], d[9], d[10],
					d[11], d[12], d[13], d[14], d[15]);
	total_bytes = fifo_count + st->left_over_size;

	for (i = 0; i < SENSOR_NUM_MAX; i++) {
		if (st->sensor[i].on) {
			st->sensor[i].count =  total_bytes / pk_size;
		}
	}

	st->header_count = max(st->sensor[SENSOR_GYRO].count,
					st->sensor[SENSOR_ACCEL].count);
	st->header_count = max(st->header_count,
					st->sensor[SENSOR_COMPASS].count);

	st->time_calib_counter++;
	inv_bound_timestamp(st);

	dptr = d;
	done_flag = false;

	while (!done_flag) {
		if (total_bytes >= pk_size) {
			res = inv_push_2060X_data(st, dptr);
			if (res)
				return res;
			total_bytes -= pk_size;
			dptr += pk_size;
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

/*
 *  inv_read_fifo() - Transfer data from FIFO to ring buffer.
 */
irqreturn_t inv_read_fifo(int irq, void *dev_id)
{

	struct inv_mpu_state *st = (struct inv_mpu_state *)dev_id;
	struct iio_dev *indio_dev = iio_priv_to_dev(st);
	int result;

	result = wait_event_interruptible_timeout(st->wait_queue,
					st->resume_state, msecs_to_jiffies(300));
	if (result <= 0)
		return IRQ_HANDLED;
	mutex_lock(&indio_dev->mlock);

	if ((st->chip_type == ICM20602) || (st->chip_type == ICM20690)) {
		result = inv_process_206XX_data(st);
		if (result)
			goto err_reset_fifo;
		mutex_unlock(&indio_dev->mlock);

		if (st->wake_sensor_received)
			wake_lock_timeout(&st->wake_lock, msecs_to_jiffies(200));
		return IRQ_HANDLED;
	}
	st->last_run_time = get_time_ns();
	st->wake_sensor_received = false;

	if (st->chip_config.is_asleep)
		goto end_read_fifo;

	inv_switch_power_in_lp(st, true);

	st->activity_size = 0;
	result = inv_process_dmp_interrupt(st);

	if (result)
		goto end_read_fifo;

	result = inv_process_dmp_data(st);

	if (st->activity_size > 0)
		sysfs_notify(&indio_dev->dev.kobj, NULL, "poll_activity");
	if (result)
		goto err_reset_fifo;

end_read_fifo:
	inv_switch_power_in_lp(st, false);
	mutex_unlock(&indio_dev->mlock);

	if (st->wake_sensor_received)
		wake_lock_timeout(&st->wake_lock, msecs_to_jiffies(200));

	return IRQ_HANDLED;

err_reset_fifo:
	if ((!st->chip_config.gyro_enable) &&
	    (!st->chip_config.accel_enable) &&
	    (!st->chip_config.slave_enable) &&
	    (!st->chip_config.pressure_enable)) {
		inv_switch_power_in_lp(st, false);
		mutex_unlock(&indio_dev->mlock);

		return IRQ_HANDLED;
	}

	pr_err("error to reset fifo\n");
	inv_reset_fifo(st, true);
	inv_switch_power_in_lp(st, false);
	mutex_unlock(&indio_dev->mlock);

	return IRQ_HANDLED;

}

int inv_flush_batch_data(struct iio_dev *indio_dev, int data)
{

	struct inv_mpu_state *st = iio_priv(indio_dev);

	inv_switch_power_in_lp(st, true);
	st->wake_sensor_received = 0;
	if ((st->chip_type == ICM20602) || (st->chip_type == ICM20690)) {
		inv_process_206XX_data(st);
	} else {
		if (inv_process_dmp_data(st))
			pr_err("error on batch.. need reset fifo\n");
	}

	if (st->wake_sensor_received)
		wake_lock_timeout(&st->wake_lock, msecs_to_jiffies(200));

	inv_switch_power_in_lp(st, false);
	inv_push_marker_to_buffer(st, END_MARKER, data);

	return 0;
}

