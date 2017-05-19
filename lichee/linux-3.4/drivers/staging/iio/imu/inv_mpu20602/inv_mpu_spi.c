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
#include <linux/spi/spi.h>
#include <linux/err.h>
#include <linux/delay.h>
#include <linux/sysfs.h>
#include <linux/jiffies.h>
#include <linux/irq.h>
#include <linux/interrupt.h>
#include <linux/kfifo.h>
#include <linux/poll.h>
#include <linux/miscdevice.h>
#include <linux/spinlock.h>

#include "inv_mpu_iio.h"
#define ALL_WINNER 1

#ifdef ALL_WINNER
#include <linux/init-input.h>
#include <linux/gpio.h>


static struct sensor_config_info sensor_info = {
	.input_type = GSENSOR_TYPE,
	.int_number = 0,
	.ldo = NULL,
};

#ifdef CONFIG_ARCH_SUN8IW6P1
#include <mach/platform.h>
#define PG_EXT_INTERRUPT_REG_VADDR (SUNXI_PIO_VBASE + 0x238)
#define PG_EXT_INTERRUPT_CLK_24MHZ 0x01
#define PG_EXT_INTERRUPT_CLK_34KHZ 0x00

static int inv_host_initial(void)
{
       unsigned int value;
    /* modify pll cpu lock time */
    value = readl(PG_EXT_INTERRUPT_REG_VADDR);
    value &= ~(0xffff);
    value |= 0x01;
    writel(value, PG_EXT_INTERRUPT_REG_VADDR);
    return 0;
}

#endif

#endif


#ifdef CONFIG_DTS_INV_MPU_IIO
#include "inv_mpu_dts.h"
#endif
#define INV_SPI_READ 0x80

int inv_plat_single_write(struct inv_mpu_state *st, u8 reg, u8 data)
{
	struct spi_message msg;
	int res;
	u8 d[2];
	struct spi_transfer xfers = {
		.tx_buf = d,
		.bits_per_word = 8,
		.len = 2,
	};

	d[0] = reg;
	d[1] = data;
	spi_message_init(&msg);
	spi_message_add_tail(&xfers, &msg);
	res = spi_sync(to_spi_device(st->dev), &msg);

	return res;
}

int inv_plat_read(struct inv_mpu_state *st, u8 reg, int len, u8 *data)
{
	struct spi_message msg;
	int res;
	u8 d[2];
	struct spi_transfer xfers[] = {
		{
		 .tx_buf = d,
		 .bits_per_word = 8,
		 .len = 1,
		 },
		{
		 .rx_buf = data,
		 .bits_per_word = 8,
		 .len = len,
		 }
	};

	if (!data)
		return -EINVAL;

	d[0] = (reg | INV_SPI_READ);

	spi_message_init(&msg);
	spi_message_add_tail(&xfers[0], &msg);
	spi_message_add_tail(&xfers[1], &msg);
	res = spi_sync(to_spi_device(st->dev), &msg);

	return res;

}

int mpu_memory_write(struct inv_mpu_state *st, u8 mpu_addr, u16 mem_addr,
		     u32 len, u8 const *data)
{
	struct spi_message msg;
	u8 buf[258];
	int res;

	struct spi_transfer xfers = {
		.tx_buf = buf,
		.bits_per_word = 8,
		.len = len + 1,
	};

	if (!data || !st)
		return -EINVAL;

	if (len > (sizeof(buf) - 1))
		return -ENOMEM;

	inv_plat_single_write(st, REG_MEM_BANK_SEL, mem_addr >> 8);
	inv_plat_single_write(st, REG_MEM_START_ADDR, mem_addr & 0xFF);

	buf[0] = REG_MEM_R_W;
	memcpy(buf + 1, data, len);
	spi_message_init(&msg);
	spi_message_add_tail(&xfers, &msg);
	res = spi_sync(to_spi_device(st->dev), &msg);

	return res;
}

int mpu_memory_read(struct inv_mpu_state *st, u8 mpu_addr, u16 mem_addr,
		    u32 len, u8 *data)
{
	int res;

	if (!data || !st)
		return -EINVAL;

	if (len > 256)
		return -EINVAL;

	res = inv_plat_single_write(st, REG_MEM_BANK_SEL, mem_addr >> 8);
	res = inv_plat_single_write(st, REG_MEM_START_ADDR, mem_addr & 0xFF);
	res = inv_plat_read(st, REG_MEM_R_W, len, data);

	return res;
}

#ifdef ALL_WINNER
static struct mpu_platform_data mpu_gyro_data = {
    .int_config  = 0x10,
    .orientation = {	0,  1,  0,
						1, 0,  0,
						0,  0,  -1},
    .level_shifter = 0,

};

#endif


/*
 *  inv_mpu_probe() - probe function.
 */
static int inv_mpu_probe(struct spi_device *spi)
{
	struct inv_mpu_state *st;
	struct iio_dev *indio_dev;
	int result;
	#ifdef ALL_WINNER
	script_item_u 	val;
	script_item_value_type_e  type;
	int irq_number = 0;
	#endif


#ifdef LINUX_KERNEL_3_10
	indio_dev = iio_device_alloc(sizeof(*st));
#else
	indio_dev = iio_allocate_device(sizeof(*st));
#endif
	if (indio_dev == NULL) {
		pr_err("memory allocation failed\n");
		result = -ENOMEM;
		goto out_no_free;
	}
	st = iio_priv(indio_dev);

#ifdef CONFIG_DTS_INV_MPU_IIO
	enable_irq_wake(spi->irq);
	result = invensense_mpu_parse_dt(&spi->dev, &st->plat_data);
	if (result)
		goto out_free;

	/*Power on device. */
	if (st->plat_data.power_on) {
		result = st->plat_data.power_on(&st->plat_data);
		if (result < 0) {
			dev_err(&spi->dev, "power_on failed: %d\n", result);
			return result;
		}
		pr_info("%s: power on here.\n", __func__);
	}
	pr_info("%s: power on.\n", __func__);

	msleep(100);
#else
	#ifdef ALL_WINNER
	st->plat_data = mpu_gyro_data;
	#else
	st->plat_data =
		*(struct mpu_platform_data *)dev_get_platdata(&spi->dev);
	#endif
#endif
	spi_set_drvdata(spi, indio_dev);
	indio_dev->dev.parent = &spi->dev;
	st->dev = &spi->dev;
	#ifdef ALL_WINNER
	type = script_get_item("gsensor_para", "gsensor_int1", &val);
	if (SCIRPT_ITEM_VALUE_TYPE_PIO != type) {
		printk("%s: type err int1 = %d. \n", __func__, val.gpio.gpio);
		return 0;
	}
	sensor_info.irq_gpio = val.gpio;
	sensor_info.int_number = val.gpio.gpio;
	/*register irq*/
	irq_number = gpio_to_irq(sensor_info.int_number);
	if (IS_ERR_VALUE(irq_number)) {
		printk("map gpio [%d] to virq failed, errno = %d\n",
			sensor_info.int_number, irq_number);
		return -EINVAL;
	}
	st->irq = irq_number;
	#else
	st->irq = spi->irq;
	#endif
	#ifdef CONFIG_INV_MPU_IIO_ICM20648
	st->i2c_dis = BIT_I2C_IF_DIS;
	#endif
	if (!strcmp(spi->modalias, "icm20648"))
		indio_dev->name = "icm20648";
	/* power is turned on inside check chip type */
	result = inv_check_chip_type(indio_dev, "icm20602");
	if (result)
		goto out_free;
	indio_dev->name = "icm20602";
	result = inv_mpu_configure_ring(indio_dev);
	if (result) {
		pr_err("configure ring buffer fail\n");
		goto out_free;
	}
	result = iio_buffer_register(indio_dev, indio_dev->channels,
				     indio_dev->num_channels);
	if (result) {
		pr_err("ring buffer register fail\n");
		goto out_unreg_ring;
	}

	result = iio_device_register(indio_dev);
	if (result) {
		pr_err("IIO device register fail\n");
		goto out_remove_ring;
	}

	result = inv_create_dmp_sysfs(indio_dev);
	if (result) {
		pr_err("create dmp sysfs failed\n");
		goto out_unreg_iio;
	}
	init_waitqueue_head(&st->wait_queue);
	st->resume_state = true;
	wake_lock_init(&st->wake_lock, WAKE_LOCK_SUSPEND, "inv_mpu");

	dev_info(&spi->dev, "%s is ready to go!\n", indio_dev->name);

	return 0;
out_unreg_iio:
	iio_device_unregister(indio_dev);
out_remove_ring:
	iio_buffer_unregister(indio_dev);
out_unreg_ring:
	inv_mpu_unconfigure_ring(indio_dev);
out_free:
#ifdef LINUX_KERNEL_3_10
	iio_device_free(indio_dev);
#else
	iio_free_device(indio_dev);
#endif
out_no_free:
	dev_err(&spi->master->dev, "%s failed %d\n", __func__, result);

	return -EIO;
}

static void inv_mpu_shutdown(struct spi_device *spi)
{
	struct iio_dev *indio_dev = spi_get_drvdata(spi);
	struct inv_mpu_state *st = iio_priv(indio_dev);
	int result;

	mutex_lock(&indio_dev->mlock);
	inv_switch_power_in_lp(st, true);
	dev_dbg(&spi->dev, "Shutting down %s...\n", st->hw->name);

	/* reset to make sure previous state are not there */
	result = inv_plat_single_write(st, REG_PWR_MGMT_1, BIT_H_RESET);
	if (result)
		dev_err(&spi->dev, "Failed to reset %s\n", st->hw->name);
	msleep(POWER_UP_TIME);
	/* turn off power to ensure gyro engine is off */
	result = inv_set_power(st, false);
	if (result)
		dev_err(&spi->dev, "Failed to turn off %s\n", st->hw->name);
	inv_switch_power_in_lp(st, false);
	mutex_unlock(&indio_dev->mlock);
}

/*
 *  inv_mpu_remove() - remove function.
 */
static int inv_mpu_remove(struct spi_device *spi)
{
	struct iio_dev *indio_dev = spi_get_drvdata(spi);

	iio_device_unregister(indio_dev);
	iio_buffer_unregister(indio_dev);
	inv_mpu_unconfigure_ring(indio_dev);
#ifdef LINUX_KERNEL_3_10
	iio_device_free(indio_dev);
#else
	iio_free_device(indio_dev);
#endif
	dev_info(&spi->dev, "inv-mpu-iio module removed.\n");

	return 0;
}
#ifdef ALL_WINNER

/*
 * inv_mpu_suspend(): suspend method for this driver.
 *    This method can be modified according to the request of different
 *    customers. If customer want some events, such as SMD to wake up the CPU,
 *    then data interrupt should be disabled in this interrupt to avoid
 *    unnecessary interrupts. If customer want pedometer running while CPU is
 *    asleep, then pedometer should be turned on while pedometer interrupt
 *    should be turned off.
 */
static int inv_mpu_suspend(struct spi_device *spi, pm_message_t mesg)
{

	/* add code according to different request Start */

	return 0;

}

/*
 * inv_mpu_complete(): complete method for this driver.
 *    This method can be modified according to the request of different
 *    customers. It basically undo everything suspend is doing
 *    and recover the chip to what it was before suspend. We use complete to
 *    make sure that alarm clock resume is finished. If we use resume, the
 *    alarm clock may not resume yet and get incorrect clock reading.
 */
static int inv_mpu_resume(struct spi_device *spi)
{
	struct iio_dev *indio_dev = spi_get_drvdata(spi);
	struct inv_mpu_state *st = iio_priv(indio_dev);
	inv_mpu_initialize(st);
	pr_info("%s inv_mpu_complete\n", st->hw->name);

	return 1;
}


#else
#ifdef CONFIG_PM
/*
 * inv_mpu_suspend(): suspend method for this driver.
 *    This method can be modified according to the request of different
 *    customers. If customer want some events, such as SMD to wake up the CPU,
 *    then data interrupt should be disabled in this interrupt to avoid
 *    unnecessary interrupts. If customer want pedometer running while CPU is
 *    asleep, then pedometer should be turned on while pedometer interrupt
 *    should be turned off.
 */
static int inv_mpu_suspend(struct device *dev)
{
	struct iio_dev *indio_dev = spi_get_drvdata(to_spi_device(dev));
	struct inv_mpu_state *st = iio_priv(indio_dev);

	/* add code according to different request Start */
	pr_info("%s inv_mpu_suspend\n", st->hw->name);
	mutex_lock(&indio_dev->mlock);

	st->resume_state = false;
	if (st->chip_config.wake_on) {
		enable_irq_wake(st->irq);
	} else {
	#ifdef CONFIG_INV_MPU_IIO_ICM20648
		inv_plat_read(st, REG_INT_ENABLE, 1, &st->int_en);
		inv_plat_read(st, REG_INT_ENABLE_2, 1, &st->int_en_2);
		inv_plat_single_write(st, REG_INT_ENABLE, 0);
		inv_plat_single_write(st, REG_INT_ENABLE_2, 0);
	#endif
	#ifdef CONFIG_INV_MPU_IIO_ICM20602
		inv_plat_single_write(st, REG_INT_ENABLE, 0);
		inv_plat_single_write(st, REG_FIFO_WM_TH2, 0);
		inv_plat_single_write(st, REG_FIFO_WM_TH1, 0);
	#endif
		disable_irq_wake(st->irq);
	}
	mutex_unlock(&indio_dev->mlock);

	return 0;

}

/*
 * inv_mpu_complete(): complete method for this driver.
 *    This method can be modified according to the request of different
 *    customers. It basically undo everything suspend is doing
 *    and recover the chip to what it was before suspend. We use complete to
 *    make sure that alarm clock resume is finished. If we use resume, the
 *    alarm clock may not resume yet and get incorrect clock reading.
 */
static void inv_mpu_complete(struct device *dev)
{
	struct iio_dev *indio_dev = spi_get_drvdata(to_spi_device(dev));
	struct inv_mpu_state *st = iio_priv(indio_dev);

	pr_info("%s inv_mpu_complete\n", st->hw->name);
	mutex_lock(&indio_dev->mlock);
	if (!st->chip_config.wake_on) {
	#ifdef CONFIG_INV_MPU_IIO_ICM20648
		inv_plat_single_write(st, REG_INT_ENABLE, st->int_en);
		inv_plat_single_write(st, REG_INT_ENABLE_2, st->int_en_2);
	#endif
		enable_irq_wake(st->irq);
	}
	/* resume state is used to synchronize read_fifo such that it won't
	    proceed unless resume is finished. */
	st->resume_state = true;
	/* resume flag is indicating that current clock reading is from resume,
	   it has up to 1 second drift and should do proper processing */
	st->resume_flag  = true;
	mutex_unlock(&indio_dev->mlock);
	wake_up_interruptible(&st->wait_queue);

	return;
}

static const struct dev_pm_ops inv_mpu_pmops = {
	.suspend = inv_mpu_suspend,
	.complete = inv_mpu_complete,
};
#define INV_MPU_PMOPS (&inv_mpu_pmops)
#else
#define INV_MPU_PMOPS NULL
#endif /* CONFIG_PM */
#endif


static const struct spi_device_id inv_mpu_id[] = {
#ifdef CONFIG_INV_MPU_IIO_ICM20648
	{"icm20645", ICM20645},
	{"icm10340", ICM10340},
	{"icm20648", ICM20648},
#else
	{"icm20608d", ICM20608D},
	{"icm20690", ICM20690},
	{"icm20602", ICM20602},
#endif
	{}
};

MODULE_DEVICE_TABLE(spi, inv_mpu_id);

static struct spi_driver inv_mpu_driver_spi = {
	.driver = {
		   .owner = THIS_MODULE,
		   .name = "inv_mpu_iio_spi",
		   #ifndef ALL_WINNER
		   .pm = INV_MPU_PMOPS,
		   #endif
		   },
	#ifdef ALL_WINNER
	.suspend 		= inv_mpu_suspend,
	.resume         = inv_mpu_resume,
	#endif
	.probe = inv_mpu_probe,
	.remove = inv_mpu_remove,
	.id_table = inv_mpu_id,
	.shutdown = inv_mpu_shutdown,
};

static int __init inv_mpu_init(void)
{
	int result = 0;
	#ifdef ALL_WINNER
	if (input_fetch_sysconfig_para(&(sensor_info.input_type))) {
		pr_err("%s: ls_fetch_sysconfig_para err.\n", __func__);
		/*return 0;*/
	}
	if (sensor_info.sensor_used == 0) {
		pr_err("*** gsensor_used set to 0 !\n");
		pr_err("*** if use gsensor_sensor,please put the sys_config.fex gsensor_used set to 1. \n");
		return 0;
	}
#endif

	 result = spi_register_driver(&inv_mpu_driver_spi);

	if (result) {
		pr_err("failed\n");
		return result;
	}
	return 0;
}

static void __exit inv_mpu_exit(void)
{
	spi_unregister_driver(&inv_mpu_driver_spi);
	#ifdef ALL_WINNER
	input_free_platform_resource(&(sensor_info.input_type));
	#endif
}

module_init(inv_mpu_init);
module_exit(inv_mpu_exit);

MODULE_AUTHOR("Invensense Corporation");
MODULE_DESCRIPTION("Invensense device driver");
MODULE_LICENSE("GPL");
MODULE_ALIAS("inv-mpu-iio-spi");
