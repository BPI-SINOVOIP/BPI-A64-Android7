/*
 * Copyright (C) 2012 STMicroelectronics
 * Matteo Dameno, Denis Ciocca - Motion MEMS Product Div.
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

#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <math.h>
#include <poll.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/select.h>

#include <cutils/log.h>

#include <linux/input.h>

#include "SensorBase.h"
#include "configuration.h"
#include "sensors.h"

/*****************************************************************************/

SensorBase::SensorBase(const char* dev_name, const char* data_name)
	: dev_name(dev_name), data_name(data_name),
	dev_fd(-1), data_fd(-1)
{
	if(data_name)
		data_fd = openInput(data_name);
}

SensorBase::~SensorBase()
{
	if (data_fd >= 0)
		close(data_fd);

	if (dev_fd >= 0)
		close(dev_fd);
}

int SensorBase::open_device()
{
	if (dev_fd<0 && dev_name) {
		dev_fd = open(dev_name, O_RDONLY);
		STLOGE_IF(dev_fd < 0, "Couldn't open %s (%s)", dev_name, strerror(errno));
	}
	return 0;
}

int SensorBase::close_device()
{
	if (dev_fd >= 0) {
		close(dev_fd);
		dev_fd = -1;
	}
	return 0;
}

int SensorBase::getFd() const
{
	if (!data_name)
		return dev_fd;

	return data_fd;
}

int SensorBase::setDelay(int32_t handle, int64_t ns)
{
	return 0;
}

bool SensorBase::hasPendingEvents() const
{
	return false;
}

int64_t SensorBase::getTimestamp()
{
	struct timespec t;
	t.tv_sec = t.tv_nsec = 0;
	clock_gettime(CLOCK_MONOTONIC, &t);
	return int64_t(t.tv_sec)*1000000000LL + t.tv_nsec;
}

int SensorBase::openInput(const char* inputDeviceName)
{	 
	int fd = -1;
	int input_id = -1;
    const char *dirname = "/dev/input";
    const char *inputsysfs = "/sys/class/input";
	const char *devpatch = "/device/device/";
    char devname[PATH_MAX];
	char inputsysname[PATH_MAX];
	
    char *filename;
	char *inputname;
	char *inputdevname;
    DIR *dir;
    struct dirent *de;
	int checkflag = 0;

    ALOGD("openInput enter inputDeviceName = %s \n", inputDeviceName);

	 if(!strcmp("lsm9ds0_acc", inputDeviceName)) {
		 fd = open("/dev/input/event4", O_RDONLY);
		 if(fd >= 0){
		 	char name[80];   
              if (ioctl(fd, EVIOCGNAME(sizeof(name) - 1), &name) < 1) {
                      name[0] = '\0';
              }
			  ALOGD("acc name is %s \n", name);
			  if (strcmp(name, inputDeviceName)) {
			  	  checkflag = 1;
			  }else{
			      strcpy(sysfs_device_path,"/sys/class/input/event4/device/device/");
			  }
		 }else{  checkflag = 1;
		 }	
	}
	 if(!strcmp("lsm9ds0_mag", inputDeviceName)) {
		 fd = open("/dev/input/event5", O_RDONLY);
		 if(fd >= 0){
		 	char name[80];   
              if (ioctl(fd, EVIOCGNAME(sizeof(name) - 1), &name) < 1) {
                      name[0] = '\0';
              }
			  ALOGD("mag name is %s \n", name);
			  if (strcmp(name, inputDeviceName)) {
			  	  checkflag = 1;
			  }else{
			      strcpy(sysfs_device_path,"/sys/class/input/event5/device/device/");
			  }
		 }else{  checkflag = 1;
		 }	
	}
	if(!strcmp("lsm9ds0_gyr", inputDeviceName)) {
		 fd = open("/dev/input/event7", O_RDONLY);
		 if(fd >= 0){
		 	char name[80];   
              if (ioctl(fd, EVIOCGNAME(sizeof(name) - 1), &name) < 1) {
                      name[0] = '\0';
              }
			  ALOGD("gyr name is %s \n", name);
			  if (strcmp(name, inputDeviceName)) {
			  	  checkflag = 1;
			  }else{
			      strcpy(sysfs_device_path,"/sys/class/input/event7/device/device/");
			  }
		 }else{  checkflag = 1;
		 }	
	}
	if(!strcmp("jsa1127", inputDeviceName)) {
		  fd = open("/dev/input/event6", O_RDONLY);
		  if(fd >= 0){
		 	char name[80];   
              if (ioctl(fd, EVIOCGNAME(sizeof(name) - 1), &name) < 1) {
                      name[0] = '\0';
              }
			  ALOGD(" jsa name is %s \n", name);
			  if (strcmp(name, inputDeviceName)) {
			  	  checkflag = 1;
			  }else{
			      strcpy(sysfs_device_path,"/sys/class/input/event6/device/device/");
			  }
		 }else{  checkflag = 1;
		 }	
	}
	if(!checkflag){
	    sysfs_device_path_len = strlen(sysfs_device_path);
	    STLOGE_IF(fd < 0, "couldn't find sysfs path for device '%s' ", inputDeviceName);
	    return fd;
	}else{
     dir = opendir(dirname);
    if(dir == NULL)
         return -1;   
     strcpy(devname, dirname);
     filename = devname + strlen(devname);
     *filename++ = '/';
	 strcpy(inputsysname,inputsysfs);
	 inputname = inputsysname + strlen(inputsysname); 
	 *inputname++ = '/';  
     while((de = readdir(dir))) {
            if(de->d_name[0] == '.' &&
                (de->d_name[1] == '\0' ||
                (de->d_name[1] == '.' && de->d_name[2] == '\0')))
                   continue;             
         strcpy(filename, de->d_name);
		 fd = open(devname, O_RDONLY);
         
         if (fd>=0) {
              char name[80];
                
              if (ioctl(fd, EVIOCGNAME(sizeof(name) - 1), &name) < 1) {
                      name[0] = '\0';
              }
              ALOGD("name is %s \n", name);
		      ALOGD("inputName is %s \n", inputDeviceName);	
              if (!strcmp(name, inputDeviceName)) {
			  	   strcpy(inputname,de->d_name);
				   inputdevname = inputname + strlen(de->d_name);
				   strcpy(inputdevname,devpatch);
				   ALOGD("openInput inputsysname = %s,fd = %d",inputsysname,fd);
				   strcpy(sysfs_device_path,inputsysname);
                   break;
              } else {
                    close(fd);
                    fd = -1;
              }
        }
     }
        
     closedir(dir);
#ifdef DEBUG_IF
        ALOGE_IF(fd<0, "couldn't find '%s' input device",inputDeviceName );
#endif
     sysfs_device_path_len = strlen(sysfs_device_path);   
     return fd;
   }//end else

}
	


int SensorBase::getSysfsDevicePath(char* sysfs_path ,const char* inputDeviceName)
{
	int fd = -1;
	const char *dirname = "/dev/input";
	char devname[PATH_MAX];
	char *filename;
	DIR *dir;
	struct dirent *de;

	sysfs_path[0] ='\0';
	dir = opendir(dirname);

	if(dir == NULL)
		return -1;

	strcpy(devname, dirname);
	filename = devname + strlen(devname);
	*filename++ = '/';

	while((de = readdir(dir))) {
		if(de->d_name[0] == '.' && (de->d_name[1] == '\0' || (de->d_name[1] == '.' && de->d_name[2] == '\0')))
			continue;

		strcpy(filename, de->d_name);
		fd = open(devname, O_RDONLY);
		if (fd >= 0) {
			char name[80];
			if (ioctl(fd, EVIOCGNAME(sizeof(name) - 1), &name) < 1)
				name[0] = '\0';

			ALOGD("name is %s \n", name);
			ALOGD("inputName is %s \n", inputDeviceName);	
			if (!strcmp(name, inputDeviceName)) {
				strcpy(sysfs_path,"/sys/class/input/");
				strcat(sysfs_path,filename);
				strcat(sysfs_path,"/device/device/");
				break;
			} else {
				close(fd);
				fd = -1;
			}
		}
	}
	closedir(dir);
	STLOGE_IF(fd < 0, "couldn't find sysfs path for device '%s' ", inputDeviceName);
	return fd;
}

int SensorBase::writeFullScale(int32_t handle, int value)
{
	int fd;
	int err;
	char buf[6];
	const char *className;

	switch(handle) {
#if (SENSORS_ACCELEROMETER_ENABLE == 1)
		case SENSORS_ACCELEROMETER_HANDLE:
			strcpy(&sysfs_device_path[sysfs_device_path_len], ACCEL_RANGE_FILE_NAME);
			className = "AccelSensor::setFullScale()";
			break;
#endif
#if (SENSORS_MAGNETIC_FIELD_ENABLE == 1)
		case SENSORS_MAGNETIC_FIELD_HANDLE:
		case SENSORS_UNCALIB_MAGNETIC_FIELD_HANDLE:
			strcpy(&sysfs_device_path[sysfs_device_path_len], MAGN_RANGE_FILE_NAME);
			className = "MagnSensor::setFullScale()";
			break;
#endif
#if (SENSORS_GYROSCOPE_ENABLE == 1)
		case SENSORS_GYROSCOPE_HANDLE:
			strcpy(&sysfs_device_path[sysfs_device_path_len], GYRO_RANGE_FILE_NAME);
			className = "Gyro::setFullScale()";
			break;
#endif
		default:
			return -1;
	}


	fd = open(sysfs_device_path, O_RDWR);
	sprintf(buf,"%d", value);
	err = write(fd, buf, sizeof(buf));
	close(fd);

	if(err >= 0) {
		STLOGI("%s Set new full-scale to %d", className, value);
		return 0;
	} else {
		STLOGE("%s Failed to set Full-scale: %d - %s", className, value, sysfs_device_path);
		return -1;
	}
}

int SensorBase::writeEnable(int32_t handle, int enable)
{
	int fd;
	int err;
	char buf[6];
	const char *className;

	switch(handle) {
#if (SENSORS_ACCELEROMETER_ENABLE == 1)
		case SENSORS_ACCELEROMETER_HANDLE:
			strcpy(&sysfs_device_path[sysfs_device_path_len], ACCEL_ENABLE_FILE_NAME);
			className = "AccelSensor::Enable(Accel)";
			break;
#endif
#if (SENSORS_SIGNIFICANT_MOTION_ENABLE == 1)
		case SENSORS_SIGNIFICANT_MOTION_HANDLE:
			strcpy(&sysfs_device_path[sysfs_device_path_len], SIGN_MOTION_ENABLE_FILE_NAME);
			className = "AccelSensor::Enable(SigMotion)";
			break;
#endif
#if (SENSORS_MAGNETIC_FIELD_ENABLE == 1)
		case SENSORS_MAGNETIC_FIELD_HANDLE:
		case SENSORS_UNCALIB_MAGNETIC_FIELD_HANDLE:
			strcpy(&sysfs_device_path[sysfs_device_path_len], MAGN_ENABLE_FILE_NAME);
			className = "MagnSensor::Enable()";
			break;
#endif
#if (SENSORS_GYROSCOPE_ENABLE == 1)
		case SENSORS_GYROSCOPE_HANDLE:
			strcpy(&sysfs_device_path[sysfs_device_path_len], GYRO_ENABLE_FILE_NAME);
			className = "GyroSensor::Enable()";
			break;
#endif
#if (SENSORS_PRESSURE_ENABLE == 1)
		case SENSORS_PRESSURE_HANDLE:
		case SENSORS_TEMPERATURE_HANDLE:
			strcpy(&sysfs_device_path[sysfs_device_path_len], PRESS_ENABLE_FILE_NAME);
			className = "PressTempSensor::Enable()";
			break;
#endif
		default:
			return -1;
	}

	fd = open(sysfs_device_path, O_RDWR);
	sprintf(buf,"%d", enable);
	err = write(fd, buf, sizeof(buf));
	close(fd);

	if(err > 0) {
		STLOGI("%s Set enable to %d", className, enable);
		return 0;
	} else {
		STLOGE("%s Failed to set enable: %d - %s", className, enable, sysfs_device_path);
		return -1;
	}
}

int SensorBase::writeDelay(int32_t handle, int64_t delay_ms)
{
	int fd;
	int err;
	char buf[8];
	const char *className;

	STLOGD( "SensorBase: setDelay handle = %d", handle);

	switch(handle) {
#if (SENSORS_ACCELEROMETER_ENABLE == 1)
		case SENSORS_ACCELEROMETER_HANDLE:
		case SENSORS_SIGNIFICANT_MOTION_HANDLE:
			strcpy(&sysfs_device_path[sysfs_device_path_len], ACCEL_DELAY_FILE_NAME);
			className = "AccelSensor::Delay()";
			break;
#endif
#if (SENSORS_MAGNETIC_FIELD_ENABLE == 1)
		case SENSORS_MAGNETIC_FIELD_HANDLE:
		case SENSORS_UNCALIB_MAGNETIC_FIELD_HANDLE:
			strcpy(&sysfs_device_path[sysfs_device_path_len], MAGN_DELAY_FILE_NAME);
			className = "MagnSensor::Delay()";
			break;
#endif
#if (SENSORS_GYROSCOPE_ENABLE == 1)
		case SENSORS_GYROSCOPE_HANDLE:
			strcpy(&sysfs_device_path[sysfs_device_path_len], GYRO_DELAY_FILE_NAME);
			className = "Gyro::Delay()";
			break;
#endif
		default:
			return -1;
	}

	fd = open(sysfs_device_path, O_RDWR);
	sprintf(buf,"%lld", delay_ms);
	err = write(fd, buf, sizeof(buf));
	close(fd);

	if(err > 0) {
		STLOGI("%s Set delay to %lld [ms]", className, delay_ms);
		return 0;
	} else {
		STLOGE("%s Failed to set delay: %lld [ms] - %s", className, delay_ms, sysfs_device_path);
		return -1;
	}
}

int SensorBase::writeSysfsCommand(int32_t handle, const char *sysfsFilename, const char *dataFormat, int64_t param)
{
	int fd;
	int err;
	char buf[8];
	const char *className;

	char formatstring1[50] = "%s Set %s to ";
	char formatstring2[100] = "%s Failed to set %s: ";

	switch(handle) {
#if (SENSORS_ACCELEROMETER_ENABLE == 1)
		case SENSORS_ACCELEROMETER_HANDLE:
		case SENSORS_SIGNIFICANT_MOTION_HANDLE:
			strcpy(&sysfs_device_path[sysfs_device_path_len], sysfsFilename);
			className = "AccelSensor::Command()";
			break;
#endif
		default:
			return -1;
	}

	fd = open(sysfs_device_path, O_RDWR);
	sprintf(buf, dataFormat, param);
	err = write(fd, buf, sizeof(buf));
	close(fd);


	strcat(formatstring1, dataFormat);
	strcat(formatstring2, dataFormat);
	strcat(formatstring2, " - %s");

	if(err > 0) {
		STLOGI(formatstring1, className, sysfsFilename, param);
		return 0;
	} else {
		STLOGE(formatstring2, className, sysfsFilename, param, sysfs_device_path);
		return -1;
	}
}
