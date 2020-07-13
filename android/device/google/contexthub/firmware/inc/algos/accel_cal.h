/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef ACCEL_CAL_H_
#define ACCEL_CAL_H_
#include <algos/mat.h>
#include <algos/mag_cal.h>
#include <stdint.h>
#include <sys/types.h>
#ifdef __cplusplus
extern "C" {
#endif
#define ACCEL_CAL_NUM_TEMP_WINDOWS 2
#ifdef ACCEL_CAL_DBG_ENABLED
#define DGB_HISTORY 10
#define TEMP_HISTOGRAM 25
#endif
/* This module estimates the accelerometer offsets using the KASA sphere fit.
 * The algorithm senses stillness and classifies the data into seven sphere caps
 * (nx,nxb,ny,nyb,nz,nzb,nle). Once the buckets are full the data is used to
 * fit the sphere calculating the offsets and the radius. This can be done,
 * because when the accelerometer is still it sees only gravity and hence all
 * the vectors should end onto a sphere. Furthermore the offset values are
 * subtracted from the accelerometer data calibrating the sensor.
 */

// Data struct for the accel stillness detection.
struct accelStillDet_t {

  // Save accumulate variables to calc. mean and var.
  float acc_x, acc_y, acc_z;
  float acc_xx, acc_yy, acc_zz;

  // Mean and var.
  float mean_x, mean_y, mean_z;
  float var_x, var_y, var_z;

  // # of samples used in the stillness detector.
  uint32_t nsamples;

  // Start timer for a new still detection (in ns).
  uint64_t start_time;

  // Controling the Stillness algo with T0 and Th
  // time the sensor must be still to trigger still detection.
  uint32_t min_batch_window;
  uint32_t max_batch_window;

  // Need a minimum amount of samples, filters out low sample rates.
  uint32_t min_batch_size;

  // Setting Th to var_th.
  float var_th;

  // Total number of stillness.
  uint32_t n_still;
};

/* Struct for good data function.
 * Counts the vectors that fall into the 7
 * Sphere caps.
 */
struct accelGoodData_t {

  // Bucket counters.
  uint32_t nx, nxb, ny, nyb, nz, nzb, nle;

  // Bucket full values.
  uint32_t nfx, nfxb, nfy, nfyb, nfz, nfzb, nfle;

  // Temp check (in degree C).
  float acc_t, acc_tt;
  float var_t, mean_t;

  // Eigen Values.
  float e_x,e_y,e_z;
};
#ifdef ACCEL_CAL_DBG_ENABLED
// Struct for stats and debug.
struct accelStatsMem_t {

  // Temp (in degree C).
  uint32_t t_hist[TEMP_HISTOGRAM];
  uint64_t start_time;

  // Offset update counter.
  uint32_t noff;
  uint32_t noff_max;

  // Offset history.
  float var_t[DGB_HISTORY];
  float mean_t[DGB_HISTORY];
  float x_o[DGB_HISTORY];
  float y_o[DGB_HISTORY];
  float z_o[DGB_HISTORY];
  float e_x[DGB_HISTORY];
  float e_y[DGB_HISTORY];
  float e_z[DGB_HISTORY];
  float rad[DGB_HISTORY];

  uint8_t n_o;
  uint64_t cal_time[DGB_HISTORY];

  // Total Buckets counter.
  uint32_t ntx, ntxb, nty, ntyb, ntz, ntzb, ntle;
};
#endif
// Struct for an accel calibration for a single temperature window.
struct accelCalAlgo_t  {
  struct accelGoodData_t agd;
  struct MagCal amoc;
};

// Complete accel calibration struct.
struct accelCal_t {
  struct accelCalAlgo_t ac1[ACCEL_CAL_NUM_TEMP_WINDOWS];
  struct accelStillDet_t asd;
#ifdef ACCEL_CAL_DBG_ENABLED
  struct accelStatsMem_t adf;
#endif
  // Offsets are only updated while the accelerometer is not running. Hence need to store a new offset,
  // which gets updated during a power down event.
  float x_bias_new, y_bias_new, z_bias_new;

  // Offset values that get subtracted from live data
  float x_bias, y_bias, z_bias;
};

/* This function runs the accel calibration algorithm.
 * sample_time_nsec -> is the  sensor timestamp in ns and
 *                     is used to check the stillness time.
 * x,y,z            -> is the sensor data (m/s^2) for the three axes.
 *                     Data is converted to g’s inside the function.
 * temp             -> is the temperature of the IMU (degree C).
 */
void accelCalRun(struct accelCal_t *acc, uint64_t sample_time_nsec,
                   float x, float y, float z, float temp);

/* This function initializes the accCalRun data struct.
 * t0     -> Sets the time how long the accel has to be still in ns.
 * n_s    -> Defines the minimum number of samples for the stillness.
 * th     -> Sets the threshold for the stillness VAR in (g rms)^2.
 * fx,fxb,fy,fyb,fz,fzb,fle -> Defines how many counts of data in the
 *                             sphere cap (Bucket) is needed to reach full.
 */
void accelCalInit(struct accelCal_t *acc, uint32_t t0, uint32_t n_s,float th,
                    uint32_t fx, uint32_t fxb, uint32_t fy, uint32_t fyb,
                    uint32_t fz, uint32_t fzb, uint32_t fle);

void accelCalDestroy(struct accelCal_t *acc);

// Ensures that the offset is only updated during Sensor power down.
bool accelCalUpdateBias(struct accelCal_t *acc,
                        float *x, float *y, float *z);

void accelCalBiasSet(struct accelCal_t *acc,
                     float x, float y, float z);

void accelCalBiasRemove(struct accelCal_t *acc,
                        float *x, float *y, float *z);

#ifdef ACCEL_CAL_DBG_ENABLED
void accelCalDebPrint(struct accelCal_t *acc,float temp);
#endif
#ifdef __cplusplus
}
#endif

#endif  // ACCEL_CAL_H_
