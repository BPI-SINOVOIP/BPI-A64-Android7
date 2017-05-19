#
# Makefile for Invensense inv-mpu-iio/inv-mpu-iio-spi device.
#

obj-$(CONFIG_INV_MPU20602_IIO) += inv-mpu-iio-spi.o
#obj-$(CONFIG_INV_MPU_IIO_SPI) += inv-mpu-iio-spi.o

inv-mpu-iio-objs := inv_mpu_i2c.o
inv-mpu-iio-objs += inv_mpu_ring.o
inv-mpu-iio-objs += inv_mpu_misc.o
inv-mpu-iio-objs += inv_mpu_common.o
inv-mpu-iio-objs += inv_mpu_timestamp.o

inv-mpu-iio-spi-objs := inv_mpu_spi.o
inv-mpu-iio-spi-objs += inv_mpu_ring.o
inv-mpu-iio-spi-objs += inv_mpu_misc.o
inv-mpu-iio-spi-objs += inv_mpu_common.o
inv-mpu-iio-spi-objs += inv_mpu_timestamp.o

# compile Invensense MPU IIO driver as DTS
ifeq ($(CONFIG_DTS_INV_MPU_IIO), y)
inv-mpu-iio-objs += inv_mpu_dts.o
endif

ifeq ($(CONFIG_INV_MPU_IIO_ICM20648), y)
inv-mpu-iio-objs += inv_mpu_init.o
inv-mpu-iio-objs += inv_mpu_core.o
inv-mpu-iio-objs += inv_mpu_parsing.o
inv-mpu-iio-objs += inv_mpu_setup.o
inv-mpu-iio-objs += inv_slave_compass.o
inv-mpu-iio-objs += inv_slave_pressure.o
inv-mpu-iio-objs += inv_slave_als.o
inv-mpu-iio-objs += inv_mpu_load_dmp.o
inv-mpu-iio-objs += inv_mpu_selftest.o

inv-mpu-iio-spi-objs += inv_mpu_init.o
inv-mpu-iio-spi-objs += inv_mpu_core.o
inv-mpu-iio-spi-objs += inv_mpu_parsing.o
inv-mpu-iio-spi-objs += inv_mpu_setup.o
inv-mpu-iio-spi-objs += inv_slave_compass.o
inv-mpu-iio-spi-objs += inv_slave_pressure.o
inv-mpu-iio-spi-objs += inv_slave_als.o
inv-mpu-iio-spi-objs += inv_mpu_load_dmp.o
inv-mpu-iio-spi-objs += inv_mpu_selftest.o
endif

ifeq ($(CONFIG_INV_MPU_IIO_ICM20690), y)
inv-mpu-iio-objs += inv_mpu_init_20690.o
inv-mpu-iio-objs += inv_mpu_core_20690.o
inv-mpu-iio-objs += inv_mpu_parsing_2060X.o
inv-mpu-iio-objs += inv_mpu_setup_no_dmp.o
inv-mpu-iio-objs += inv_mpu_selftest_20690.o
inv-mpu-iio-objs += inv_slave_compass.o

inv-mpu-iio-spi-objs += inv_mpu_init_20690.o
inv-mpu-iio-spi-objs += inv_mpu_core_20690.o
inv-mpu-iio-spi-objs += inv_mpu_parsing_2060X.o
inv-mpu-iio-spi-objs += inv_mpu_setup_no_dmp.o
inv-mpu-iio-spi-objs += inv_mpu_selftest_20690.o
inv-mpu-iio-spi-objs += inv_slave_compass.o
endif

ifeq ($(CONFIG_INV_MPU_IIO_ICM20602), y)
inv-mpu-iio-objs += inv_mpu_init_2060X.o
inv-mpu-iio-objs += inv_mpu_core_2060X.o
inv-mpu-iio-objs += inv_mpu_parsing_2060X.o
inv-mpu-iio-objs += inv_mpu_setup_no_dmp.o
inv-mpu-iio-objs += inv_mpu_selftest_2060X.o

inv-mpu-iio-spi-objs += inv_mpu_init_2060X.o
inv-mpu-iio-spi-objs += inv_mpu_core_2060X.o
inv-mpu-iio-spi-objs += inv_mpu_parsing_2060X.o
inv-mpu-iio-spi-objs += inv_mpu_setup_no_dmp.o
inv-mpu-iio-spi-objs += inv_mpu_selftest_2060X.o
endif

ifeq ($(CONFIG_INV_MPU_IIO_ICM20608D), y)
inv-mpu-iio-objs += inv_mpu_init_2060X.o
inv-mpu-iio-objs += inv_mpu_core_2060X.o
inv-mpu-iio-objs += inv_mpu_parsing_2060X.o
inv-mpu-iio-objs += inv_mpu_setup_20608D.o
inv-mpu-iio-objs += inv_mpu_load_dmp.o
inv-mpu-iio-objs += inv_mpu_selftest_2060X.o

inv-mpu-iio-spi-objs += inv_mpu_init_2060X.o
inv-mpu-iio-spi-objs += inv_mpu_core_2060X.o
inv-mpu-iio-spi-objs += inv_mpu_parsing_2060X.o
inv-mpu-iio-spi-objs += inv_mpu_setup_20608D.o
inv-mpu-iio-spi-objs += inv_mpu_load_dmp.o
inv-mpu-iio-spi-objs += inv_mpu_selftest_2060X.o
endif
ccflags-y := -Idrivers/staging/iio/
