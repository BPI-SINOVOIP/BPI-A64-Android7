#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <dirent.h>

#define LOG_TAG "RadioMonitor"
#include <cutils/log.h>

#include "UEventFramework.h"

static char usb_vid[0x10],usb_pid[0x10];

static void do_coldboot(DIR *d, int lvl)
{
    struct dirent *de;
    int dfd, fd;

    dfd = dirfd(d);

    fd = openat(dfd, "uevent", O_WRONLY);
    if(fd >= 0) {
        write(fd, "add\n", 4);
        close(fd);
    }

    while((de = readdir(d))) {
        DIR *d2;

        if (de->d_name[0] == '.')
            continue;

        if (de->d_type != DT_DIR && lvl > 0)
            continue;

        fd = openat(dfd, de->d_name, O_RDONLY | O_DIRECTORY);
        if(fd < 0)
            continue;

        d2 = fdopendir(fd);
        if(d2 == 0)
            close(fd);
        else {
            do_coldboot(d2, lvl + 1);
            closedir(d2);
        }
    }
}

static void coldboot(const char *path)
{
    DIR *d = opendir(path);
    if(d) {
        do_coldboot(d, 0);
        closedir(d);
    }
}

static int read_vid_pid(char * path)
{
	int fd,size;
	char usb_path[0x60] = {0};

	memset(usb_vid,0,sizeof(usb_vid));
	memset(usb_pid,0,sizeof(usb_pid));

	//read Vid
	memset(usb_path,0,0x60);
	strcat(usb_path,path);
	strcat(usb_path,"/idVendor");
	fd=open(usb_path,O_RDONLY);
	size=read(fd,usb_vid,sizeof(usb_vid));
	close(fd);
	//RLOGI("VID :size %d,vid_path '%s',VID  '%s'.\n",size,usb_path,usb_vid);
	if(size<=0)
	{
		RLOGE("Vid :err\n");
		return -1;
	}
	//���һ���ַ��ǻ��з��ţ���Ҫȥ��
	usb_vid[size-1] = 0;

	//read Pid
	memset(usb_path,0,0x60);
	strcat(usb_path,path);
	strcat(usb_path,"/idProduct");
	fd=open(usb_path,O_RDONLY);
	size=read(fd,usb_pid,sizeof(usb_pid));
	close(fd);

	//RLOGI("PID :size %d,Pid_path '%s',PID  '%s'.\n",size,usb_path,usb_pid);
	if(size<=0)
	{
		RLOGE("Pid :err\n");
		return -1;
	}
	//���һ���ַ��ǻ��з��ţ���Ҫȥ��
	usb_pid[size-1] = 0;

	return 0;
}

static void handleUsbEvent(struct uevent *evt)
{
    const char *devtype = evt->devtype;
    char *p,*cmd = NULL, path[0x60] = {0};
    char *argv_rc[] =
	{
		NULL,
		NULL,
		NULL
	};
    int ret,status;
    char buffer[256];
	char file[256];

    //�����ж��豸���ͣ����Ƿ�Ϊaddģʽ�� ������Ӧ����
    if(!strcmp(evt->action, "add") && !strcmp(devtype, "usb_device")) {
        /*call usb mode switch function*/
/*
		RLOGI("event { '%s', '%s', '%s', '%s', %d, %d }\n", evt->action, evt->path, evt->subsystem,
                    evt->firmware, evt->major, evt->minor);
*/
        p = strstr(evt->path,"usb");
        if(p == NULL)
        {
        	return;
        }
        p += sizeof("usb");
        /*�����usb���������ϱ���������path��  /devices/platform/sw-ehci.1/usb*
          ���������������ϱ���������path��   /devices/platform/sw-ehci.1/usb1/1-1/1-1.7
        */
        p = strchr(p,'-');
        if(p == NULL)
        {
        	return;
        }

        strcat(path,"/sys");
        strcat(path,evt->path);
        //RLOGI("path : '%s'\n",path);
        ret = read_vid_pid(path);
        if(ret < 0)
        {
        	return;
        }

		sprintf(file, "/etc/usb_modeswitch.d/%s_%s", usb_vid, usb_pid);
		if(access(file, 0) == 0){
			//wait for usb device ready, zoomdata,StrongRising 3g dongle
	        if(!strncmp(usb_vid,"8888",4)&& !strncmp(usb_pid, "6500",4)){
	        	sleep(8);
			}

            //send usb_modeswitch command
			asprintf(&cmd, "source /system/xbin/usb_modeswitch.sh %s &", file);
			RLOGI("cmd: %s", cmd);

			ret = system(cmd);
			free(cmd);
			RLOGI("err: excute command faild, ret=%d, err=%s\n",ret, strerror(errno));
		}
    }

    return;
}

/* uevent callback function */
static void on_uevent(struct uevent *event)
{
	const char *subsys = event->subsystem;

	//RLOGI("3g monitor on_uevent: action=%s, path=%s, subsystem=%s\n",  event->action, event->path, event->subsystem);

	if (!strcmp(subsys, "usb")) {
    	handleUsbEvent(event);	//�˺�����Ҫ�� Event�������
    }

}

static void *radio_monitor_thread(void *param)
{
	RLOGI("radio_monitor_thread run");

	uevent_init();
	//coldboot("/sys/devices");
	coldboot("/sys/bus/usb/devices");
	RLOGI("change coldboot to /sys/bus/usb/devices");
	uevent_next_event(on_uevent);

	return param;
}

int radio_monitor(void)
{
	int ret;
	pthread_t pid;
    pthread_attr_t attr;

    ret = pthread_attr_init (&attr);
    if (ret != 0) {
        ALOGE("err: pthread_attr_init failed err=%s", strerror(ret));
        goto pthread_attr_init_failed;
    }

    ret = pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    if (ret != 0) {
        ALOGE("err: pthread_attr_setdetachstate failed err=%s", strerror(ret));
        goto pthread_attr_setdetachstate_failed;
    }

	ret = pthread_create(&pid, &attr, radio_monitor_thread, NULL);
	if (ret) {
		ALOGE("err: pthread_create failed, ret=%d\n", ret);
		goto pthread_create_failed;
	}

	return 0;

pthread_create_failed:
pthread_attr_setdetachstate_failed:
pthread_attr_init_failed:
	return -1;
}

