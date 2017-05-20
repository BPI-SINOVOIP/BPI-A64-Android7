/*
 * Copyright (C) 2012 STMicroelectronics
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
#if (SENSORS_MAGNETIC_FIELD_ENABLE == 1)

#ifndef ANDROID_MAGN_SENSOR_H
#define ANDROID_MAGN_SENSOR_H

#include <stdint.h>
#include <errno.h>
#include <sys/cdefs.h>
#include <sys/types.h>

#include "configuration.h"
#include "sensors.h"
#include "SensorBase.h"
#include "InputEventReader.h"
#include "AccelSensor.h"

#if MAG_CALIBRATION_ENABLE_FILE
#include "StoreCalibration.h"
#endif

#if MAG_CALIBRATION_ENABLE == 1
extern "C"
{
	#include "sensors_compass_API.h"
};
#endif
#if (SENSORS_GEOMAG_ROTATION_VECTOR_ENABLE == 1)
extern "C"
{
	#include "iNemoEngineGeoMagAPI.h"
};
#endif
/*****************************************************************************/

struct input_event;


class MagnSensor : public SensorBase
{
	enum {
		MagneticField = 0,
		UncalibMagneticField,
		iNemoMagnetic,
		GeoMagRotVect_Magnetic,
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
	sensors_event_t mPendingEvent[numSensors];
	bool mHasPendingEvent;
	int data_read;
	int setInitialState();
	static int8_t status;
#if MAG_CALIBRATION_ENABLE == 1
	CalibFactor cf;
	int data_accelerometer[3];
#endif

private:
	static sensors_vec_t  dataBuffer;
	static int64_t setDelayBuffer[numSensors];
	static int DecimationBuffer[numSensors];
	static int Mag_decimation_count;
	static int MagUncalib_decimation_count;
	static int GeoMagnRotVect_decimation_count;
	static int Orientation_decimation_count;
	static int Gravity_Accel_decimation_count;
	static int Linear_Accel_decimation_count;
	sensors_vec_t mSensorsBufferedVectors[3];
	virtual bool setBufferData(sensors_vec_t *value);
#if (SENSORS_ACCELEROMETER_ENABLE == 1)
	static AccelSensor *acc;
#endif
#if (SENSORS_GEOMAG_ROTATION_VECTOR_ENABLE == 1)
	iNemoGeoMagSensorsData sData;
#endif
	float data_raw[3];
	float data_rot[3];
	static pthread_mutex_t dataMutex;

#if MAG_CALIBRATION_ENABLE_FILE
	StoreCalibration *pStoreCalibration;
#endif

public:
	MagnSensor();
	virtual ~MagnSensor();
	virtual int readEvents(sensors_event_t* data, int count);
	virtual bool hasPendingEvents() const;
	virtual int setDelay(int32_t handle, int64_t ns);
	virtual int setFullScale(int32_t handle, int value);
	virtual int enable(int32_t handle, int enabled, int type);
	int64_t getDelayms() {
		return delayms;
	};
	static bool getBufferData(sensors_vec_t *lastBufferedValues);
	static int count_call_calibration;
	static int freq;
};

#endif  /* ANDROID_MAGN_SENSOR_H */

#endif /* SENSORS_MAGNETIC_FIELD_ENABLE */
