/*
 * \file        gsensertester.c
 * \brief       
 *
 * \version     1.0.0
 * \date        2012年07月10日
 * \author      zhengjiewen  <zhengjiewen@allwinnertech.com>
 *
 * Copyright (c) 2012 Allwinner Technology. All Rights Reserved.
 *
 */


#include <linux/input.h>

#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include  <dirent.h>
#include "dragonboard_inc.h"

//#define DEBUG_SENSOR 1

#define CONVERT                         (GRAVITY_EARTH / 256)

#ifdef GSENSOR_DIRECT_X
#define CONVERT_X                       (CONVERT)
#else
#define CONVERT_X                       (-CONVERT)
#endif
#ifdef GSENSOR_DIRECT_Y
#define CONVERT_Y                       (CONVERT)
#else
#define CONVERT_Y                       (-CONVERT)
#endif 
#ifdef GSENSOR_DIRECT_Z
#define CONVERT_Z                       (CONVERT)
#else
#define CONVERT_Z                       (-CONVERT)
#endif

#define INPUT_DIR                       "/dev/input"

//add lsm9ds0 
#define LSM9DS0_ACC                     "lsm9ds0_acc"
#define LSM9DS0_ACC_PATH                "device/accelerometer"
static int lsm9ds0_acc_flag = 0;
static char * enable_name = "enable";



static int set_sysfs_input_attr(char *class_path,
				const char *attr, char *value, int len)
{
	char path[256];
	int fd;
	if (class_path == NULL || *class_path == '\0'
	    || attr == NULL || value == NULL || len < 1) {
		return -EINVAL;
	}
	snprintf(path, sizeof(path), "%s/%s", class_path, attr);
	path[sizeof(path) - 1] = '\0';
	db_msg("path is %s \n",path);
	fd = open(path, O_RDWR);
	if (fd < 0) {
		return -errno;
	}
	if (write(fd, value, len) < 0) {
		close(fd);
		return -errno;
	}
	close(fd);

	return 0;
}

//let it active

/*
static int poll__setDelay(struct sensors_poll_device_t *device,
        int handle, int64_t ns) {

	sensors_poll_context_t *dev = (sensors_poll_context_t *)device;
	char buffer[20];
	int ms=ns/1000000;
	int bytes = sprintf(buffer, "%d\n", ms);

	set_sysfs_input_attr(dev->class_path,"delay",buffer,bytes);

	return 0;

}
*/

static int sensor_get_class_path(char *class_path,char * sensor_name)
{
	char *dirname = "/sys/class/input";
	char buf[256];
	int res;
	DIR *dir;
	struct dirent *de;
	int fd = -1;
	int found = 0;

	dir = opendir(dirname);
	if (dir == NULL)
		return -1;

	while((de = readdir(dir))) {
		if (strncmp(de->d_name, "input", strlen("input")) != 0) {
		    continue;
        	}

		sprintf(class_path, "%s/%s", dirname, de->d_name);
		snprintf(buf, sizeof(buf), "%s/name", class_path);

		fd = open(buf, O_RDONLY);
		if (fd < 0) {
		    continue;
		}
		if ((res = read(fd, buf, sizeof(buf))) < 0) {
		    close(fd);
		    continue;
		}
		buf[res - 1] = '\0';
		if (strcmp(buf, sensor_name) == 0) {
		    found = 1;
		    db_msg("sensor:find sensor %s\n",sensor_name);
			if(strcmp(LSM9DS0_ACC,sensor_name) == 0){
				lsm9ds0_acc_flag = 1;
				sprintf(class_path,"%s/%s",class_path,LSM9DS0_ACC_PATH);
				db_msg("class_path = %s\n",class_path);
			}
		    close(fd);
		    break;
		}

		close(fd);
		fd = -1;
	}
	closedir(dir);

	if (found) {
		return 0;
	}else {
		class_path = '\0';
		return -1;
	}

}

//打开sensor设备操作节点，
//返回值：正确时为节点的fd,错误时为-1.

static int open_input_device(char* sensor_name)
{
	char *filename;
	int fd;
	DIR *dir;
	struct dirent *de;
	char name[80];
	char devname[256];
	dir = opendir(INPUT_DIR);
	if (dir == NULL)
		return -1;

	strcpy(devname, INPUT_DIR);
	filename = devname + strlen(devname);
	*filename++ = '/';

	while ((de = readdir(dir))) {
		if (de->d_name[0] == '.' &&
		    (de->d_name[1] == '\0' ||
		     (de->d_name[1] == '.' && de->d_name[2] == '\0')))
			continue;
		strcpy(filename, de->d_name);
		fd = open(devname, O_RDONLY);
		if (fd < 0) {
			continue;
		}


		if (ioctl(fd, EVIOCGNAME(sizeof(name) - 1), &name) < 1) {
			name[0] = '\0';
		}

		if (!strcmp(name, sensor_name)) {
#ifdef DEBUG_SENSOR
		db_msg("devname is %s \n",devname);
#endif
		} else {
			close(fd);
			continue;
		}
		closedir(dir);

		return fd;

	}
	closedir(dir);

	return -1;
}

int main(int argc, char *argv[])
{
    char dev_path[64];
    int fd;
    int x=0;
    int y=0;
    int z=0;
    struct input_event event;
    char buf[64];
    char class_path[256];
    int ret;
    
    INIT_CMD_PIPE();

	if(sensor_get_class_path(class_path,argv[4]) < 0) {
		db_error("can't get the sensor class path\n");
        goto err;	
	}

	if(lsm9ds0_acc_flag){
		enable_name = "enable_device";
	}
	ret=sprintf(buf,"%d",1);
	if(0!=set_sysfs_input_attr(class_path,enable_name,buf,ret)){
	  	db_warn("can't set sensor enable!!! (%s)\n", strerror(errno));
	}
	  
    fd=open_input_device(argv[4]);
    if (fd== -1) {
            db_error("can't open %s(%s)\n",argv[4], strerror(errno));
            goto err;
    }
    /*
    strncpy(dev_path, "/dev/input/event2", 64);

        fd= open(dev_path, O_RDONLY);
        if (fd== -1) {
            db_error("can't open %s(%s)\n", dev_path, strerror(errno));
            goto err;
        }
    */    
        while(1){
        	ret = read(fd, &event, sizeof(event));
        	if(ret==-1){
        		db_error("can't read %s(%s)\n", dev_path, strerror(errno));
        		goto err;
        		}
       if (event.type == EV_ABS) {

			  	switch (event.code) {
		 	   	case ABS_X:
				    x =event.value;
             	  //db_msg("senser data is: x=%d\n",x);
                         break;
				case ABS_Y:
					y =event.value;
                  // db_msg("senser date is: y=%d\n",y);
					break;				
				case ABS_Z:
					z =event.value;
                 //   db_msg("senser date is:z=%d\n",z);
					break;		
					}
				sprintf(buf,"(%d,%d,%d)",x,y,z);
			    //db_msg("senser data is:x=%d,y=%d,z=%d\n",x,y,z);
				SEND_CMD_PIPE_OK_EX(buf);
                }
        	
       sleep(1);

    }
     close(fd);
err:
    SEND_CMD_PIPE_FAIL();
    EXIT_CMD_PIPE();
    return 0;
}


