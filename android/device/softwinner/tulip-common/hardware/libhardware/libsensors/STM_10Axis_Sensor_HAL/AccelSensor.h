/*
 * Copyright (C) 2013 STMicroelectronics
 * Matteo Dameno, Ciocca Denis, Alberto Marinoni - Motion MEMS Product Div.
 * Copyright (C) 2008 The Android Open Source Project
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

#include "configuration.h"
#if (SENSORS_ACCELEROMETER_ENABLE == 1)

#ifndef ANDROID_ACC_SENSOR_H
#define ANDROID_ACC_SENSOR_H

#include <stdint.h>
#include <errno.h>
#include <sys/cdefs.h>
#include <sys/types.h>

#include "sensors.h"
#include "SensorBase.h"
#include "InputEventReader.h"

#if ACC_CALIBRATION_ENABLE_FILE
#include "StoreCalibration.h"
#endif

/*****************************************************************************/
#define TRUE "true"
#define FALSE "false"
#define GSENSOR_NAME "gsensor_name"
#define GSENSOR_DIRECTX "gsensor_direct_x"
#define GSENSOR_DIRECTY "gsensor_direct_y"
#define GSENSOR_DIRECTZ "gsensor_direct_z"
#define GSENSOR_XY "gsensor_xy_revert"
#define GSENSOR_CONFIG_PATH    "/system/usr/gsensor.cfg"
#define LINE_LENGTH  (128)
struct input_event;

class AccelSensor : public SensorBase {
	enum {
		Acceleration = 0,
		SignificantMotion,
		iNemoAcceleration,
		MagCalAcceleration,
		GeoMagRotVectAcceleration,
		Orientation,
		Gravity_Accel,
		Linear_Accel,
		VirtualGyro,
		numSensors
	};
	static int mEnabled;
	static int64_t delayms;
	static int current_fullscale;
	InputEventCircularReader mInputReader;
	uint32_t mPendingMask;
	sensors_event_t mPendingEvents[numSensors];
	bool mHasPendingEvent;
	int setInitialState();

private:
	static sensors_vec_t  dataBuffer;
	static int64_t setDelayBuffer[numSensors];
	static int DecimationBuffer[numSensors];
	static int Acc_decimation_count;
	virtual bool setBufferData(sensors_vec_t *value);
	float data_raw[3];
	float data_rot[3];
	static pthread_mutex_t dataMutex;
	float direct_x;
    float direct_y;
    float direct_z;
    int direct_xy;

#if ACC_CALIBRATION_ENABLE_FILE
	StoreCalibration *pStoreCalibration;
#endif

public:
	AccelSensor();
	virtual ~AccelSensor();
	virtual int readEvents(sensors_event_t *data, int count);
	virtual bool hasPendingEvents() const;
	virtual int setDelay(int32_t handle, int64_t ns);
	virtual int setFullScale(int32_t handle, int value);
	virtual int enable(int32_t handle, int enabled, int type);
	
	static bool getBufferData(sensors_vec_t *lastBufferedValues);
private:
char* get_cfg_value(char *buf);
int gsensor_cfg();
};

#endif  // ANDROID_ACCEL_SENSOR_H

#endif /* SENSORS_ACCELEROMETER_ENABLE */
