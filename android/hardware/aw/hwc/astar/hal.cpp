/*************************************************************************/ /*!
@Copyright      Copyright (c) Imagination Technologies Ltd. All Rights Reserved
@License        Strictly Confidential.
*/ /**************************************************************************/

#include "hwc.h"

#include <sys/resource.h>
#include <sys/time.h>

#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#include <utils/Timers.h>


#ifdef __LECACY_USED__ 
static tv_para_t g_tv_para[]=
{
    {8, DISP_TV_MOD_NTSC,             720,    480},
    {8, DISP_TV_MOD_PAL,              720,    576},
    
    {5, DISP_TV_MOD_480I,             720,    480},
    {5, DISP_TV_MOD_576I,             720,    576},
    {5, DISP_TV_MOD_480P,             720,    480},
    {5, DISP_TV_MOD_576P,             720,    576},
    {5, DISP_TV_MOD_720P_50HZ,        1280,   720},
    {5, DISP_TV_MOD_720P_60HZ,        1280,   720},
    {5, DISP_TV_MOD_1080I_50HZ,       1920,   1080},
    {5, DISP_TV_MOD_1080I_60HZ,       1920,   1080},
    {1, DISP_TV_MOD_1080P_24HZ,       1920,   1080},
    {5, DISP_TV_MOD_1080P_50HZ,       1920,   1080},
    {5, DISP_TV_MOD_1080P_60HZ,       1920,   1080},
    
    {1, DISP_TV_MOD_1080P_24HZ_3D_FP, 1920,   1080},
    {1, DISP_TV_MOD_720P_50HZ_3D_FP,  1280,   720},
    {1, DISP_TV_MOD_720P_60HZ_3D_FP,  1280,   720},
    
    {2, DISP_VGA_H1680_V1050,         1668,   1050},
    {2, DISP_VGA_H1440_V900,          1440,   900},
    {2, DISP_VGA_H1360_V768,          1360,   768},
    {2, DISP_VGA_H1280_V1024,         1280,   1024},
    {2, DISP_VGA_H1024_V768,          1024,   768},
    {2, DISP_VGA_H800_V600,           800,    600},
    {2, DISP_VGA_H640_V480,           640,    480},
    {2, DISP_VGA_H1440_V900_RB,       1440,   900},
    {2, DISP_VGA_H1920_V1080,         1920,   1080},
    {2, DISP_VGA_H1280_V720,          1280,   720},
};
#else
static tv_para_t g_tv_para[]=
{
    {8, DISP_TV_MOD_NTSC,             720,    480},
    {8, DISP_TV_MOD_PAL,              720,    576},
    
    {5, DISP_TV_MOD_480I,             720,    480},
    {5, DISP_TV_MOD_576I,             720,    576},
    {5, DISP_TV_MOD_480P,             720,    480},
    {5, DISP_TV_MOD_576P,             720,    576},
    {5, DISP_TV_MOD_720P_50HZ,        1280,   720},
    {5, DISP_TV_MOD_720P_60HZ,        1280,   720},
    {5, DISP_TV_MOD_1080I_50HZ,       1920,   1080},
    {5, DISP_TV_MOD_1080I_60HZ,       1920,   1080},
    {1, DISP_TV_MOD_1080P_24HZ,       1920,   1080},
    {5, DISP_TV_MOD_1080P_50HZ,       1920,   1080},
    {5, DISP_TV_MOD_1080P_60HZ,       1920,   1080},
    
    {1, DISP_TV_MOD_1080P_24HZ_3D_FP, 1920,   1080},
    {1, DISP_TV_MOD_720P_50HZ_3D_FP,  1280,   720},
    {1, DISP_TV_MOD_720P_60HZ_3D_FP,  1280,   720},
};

#endif

int  get_width_from_mode(int mode)
{
    unsigned int i = 0;
    
    for(i=0; i<sizeof(g_tv_para)/sizeof(tv_para_t); i++)
    {
        if(g_tv_para[i].mode == mode)
        {
            return g_tv_para[i].width;
        }
    }

    return -1;
}

int  get_height_from_mode(int mode)
{
    unsigned int i = 0;
    
    for(i=0; i<sizeof(g_tv_para)/sizeof(tv_para_t); i++)
    {
        if(g_tv_para[i].mode == mode)
        {
            return g_tv_para[i].height;
        }
    }
    
    return -1;
}

__disp_tv_mode_t get_suitable_hdmi_mode(void)
{
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
	unsigned long arg[4]={0};

    if(ctx->hint_hdmi_mode >= DISP_TV_MOD_480I && ctx->hint_hdmi_mode <= DISP_TV_MOD_1080P_30HZ)
    {
	    return (__disp_tv_mode_t)ctx->hint_hdmi_mode;
	}
	else if(ctx->hint_hdmi_mode != 255)
	{
	    return DISP_TV_MOD_720P_50HZ;
	}

	arg[0] = 0;
	arg[1] = DISP_TV_MOD_1080P_50HZ;
	if(ioctl(ctx->disp_fp,DISP_CMD_HDMI_SUPPORT_MODE,arg))
	{
		return DISP_TV_MOD_1080P_50HZ;
	}

	arg[1] = DISP_TV_MOD_1080P_60HZ;
	if(ioctl(ctx->disp_fp,DISP_CMD_HDMI_SUPPORT_MODE,arg))
	{
		return DISP_TV_MOD_1080P_60HZ;
	}

	arg[1] = DISP_TV_MOD_720P_50HZ;
	if(ioctl(ctx->disp_fp,DISP_CMD_HDMI_SUPPORT_MODE,arg))
	{
		return DISP_TV_MOD_720P_50HZ;
	}

	arg[1] = DISP_TV_MOD_720P_60HZ;
	if(ioctl(ctx->disp_fp,DISP_CMD_HDMI_SUPPORT_MODE,arg))
	{
		return DISP_TV_MOD_720P_60HZ;
	}

	arg[1] = DISP_TV_MOD_576P;
	if(ioctl(ctx->disp_fp,DISP_CMD_HDMI_SUPPORT_MODE,arg))
	{
		return DISP_TV_MOD_576P;
	}
	
	arg[1] = DISP_TV_MOD_480P;
	if(ioctl(ctx->disp_fp,DISP_CMD_HDMI_SUPPORT_MODE,arg))
	{
		return DISP_TV_MOD_480P;
	}

    return DISP_TV_MOD_720P_60HZ;    
}

static int hwc_hdmi_switch(void)
{
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
    
    if(ctx->psHwcProcs && ctx->psHwcProcs->hotplug)
    {
        unsigned long arg[4]={0};

        if(ctx->hdmi_hpd)
        {
            __disp_tv_mode_t hdmi_mode;
            
            hdmi_mode = get_suitable_hdmi_mode();

            ctx->display_width[1] = get_width_from_mode(hdmi_mode);
            ctx->display_height[1] = get_height_from_mode(hdmi_mode);
            ctx->out_type[1] = DISP_OUTPUT_TYPE_HDMI;
            ctx->out_mode[1] = hdmi_mode;
            
            arg[0] = 1;
            arg[1] = hdmi_mode;
            ioctl(ctx->disp_fp, DISP_CMD_HDMI_SET_MODE, (unsigned long)arg);

            arg[0] = 1;

#ifdef __LECACY_USED__ 
            ioctl(ctx->disp_fp, DISP_CMD_HDMI_ON, (unsigned long)arg);
#else

            ioctl(ctx->disp_fp, DISP_CMD_HDMI_ENABLE, (unsigned long)arg);
#endif

            ALOGD( "####hdmi plug in, out_type:%d, out_mode:%d", ctx->out_type[1],ctx->out_mode[1]);
        }
        else
        {
            arg[0] = 1;

#ifdef __LECACY_USED__ 
            ioctl(ctx->disp_fp, DISP_CMD_HDMI_OFF, (unsigned long)arg);
#else
            ioctl(ctx->disp_fp, DISP_CMD_HDMI_DISABLE, (unsigned long)arg);
#endif
            ALOGD( "####hdmi plug out");
        }

        ctx->psHwcProcs->hotplug(ctx->psHwcProcs, HWC_DISPLAY_EXTERNAL, ctx->hdmi_hpd);

        ctx->hdmi_hpd_active = ctx->hdmi_hpd;
        
        return 1;
    }

    return 0;
}

int hwc_set_sysfs_input_attr(char *class_path,const char *attr, char *value, int len)
{
        char path[256];
        int fd;

        if (class_path == NULL || *class_path == '\0'
            || attr == NULL || value == NULL || len < 1) {
                return -EINVAL;
        }

        snprintf(path, sizeof(path), "%s/%s", class_path, attr);
        path[sizeof(path) - 1] = '\0';
        fd = open(path, O_RDWR);

        if (fd < 0) {
                ALOGD("Could not open (write-only) SysFs attribute \"%s\" (%s).", attr, strerror(errno));
                close(fd);
                return -errno;
        }

        if (write(fd, value, len) < 0) {
                ALOGD("path:%s", path);
                ALOGD("Could not write SysFs attribute \"%s\" (%s).", attr, strerror(errno));
                close(fd);
                return -errno;
        }

        close(fd);

        return 0;
}
#ifdef HWC_FPS_REPORT
static void updateFps(SUNXI_hwcdev_context_t *psCtx)
{
	static double fLastPrintTime      = 0.0;
	static double fLastUpdateAttrTime = 0.0;
	static const double d = 1.0 / 1.0e6;

	double fCurrentTime = 0.0;
	timeval tv = { 0, 0 };
	gettimeofday(&tv, NULL);
	fCurrentTime = tv.tv_sec + tv.tv_usec * d;

	if(fCurrentTime - fLastUpdateAttrTime >= 1)
	{
	    fLastUpdateAttrTime = fCurrentTime;

        //get the latest system attribute
		int  show_fps_settings = psCtx->hwcdebug & FPS_SHOW;	    
		
		if(show_fps_settings != psCtx->bReportFpsEnabled)
		{	
		    ALOGD("###### %s hwc fps print ######", show_fps_settings != 0 ? "Enable":"Disable");
			fLastPrintTime   = fCurrentTime;
			psCtx->uiBeginFrame = psCtx->HWCFramecount;
			psCtx->bReportFpsEnabled = show_fps_settings;
		}		
	}

	if(psCtx->bReportFpsEnabled != 0)
	{
	    if(fCurrentTime - fLastPrintTime >= 1)
	    {
				//calc fps and print it
				{
		            ALOGD(">>>fps %f\n", (psCtx->HWCFramecount - psCtx->uiBeginFrame) * 1.0f 
					                      / (fCurrentTime - fLastPrintTime));
				}
	
	      psCtx->uiBeginFrame = psCtx->HWCFramecount;
				fLastPrintTime = fCurrentTime;
	    }
	}
}
#endif /* HWC_FPS_REPORT */
static int g_Setfastboot_fg = 0;
static unsigned int g_force_gpu = 0;
static unsigned int g_hwcFramecount = 0;
static int hwc_uevent(void)
{
	struct sockaddr_nl snl;
	const int buffersize = 16*1024;
	int retval;
	int hotplug_sock;
	SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;

	memset(&snl, 0x0, sizeof(snl));
	snl.nl_family = AF_NETLINK;
	snl.nl_pid = getpid();
	snl.nl_groups = 0xffffffff;

	hotplug_sock = socket(PF_NETLINK, SOCK_DGRAM, NETLINK_KOBJECT_UEVENT);
	if (hotplug_sock == -1) {
		ALOGE("####socket is failed in %s error:%d %s\n", __FUNCTION__, errno, strerror(errno));
		return -1;
	}

	setsockopt(hotplug_sock, SOL_SOCKET, SO_RCVBUFFORCE, &buffersize, sizeof(buffersize));

	retval = bind(hotplug_sock, (struct sockaddr *)&snl, sizeof(struct sockaddr_nl));

	if (retval < 0) {
		ALOGE("####bind is failed in %s error:%d %s\n", __FUNCTION__, errno, strerror(errno));
		close(hotplug_sock);
		return -1;
	}

	while(1) 
	{
		char buf[4096*2] = {0};
        struct pollfd fds;
        int err;
    		char property[PROPERTY_VALUE_MAX];
        fds.fd = hotplug_sock;
        fds.events = POLLIN;
        fds.revents = 0;
 				if (property_get("debug.hwc.showfps", property, NULL) >= 0)
				{
					ctx->hwcdebug = atoi(property)&SHOW_ALL;
				}
				else
				{
				  ctx->hwcdebug = 0;
				}
#ifdef HWC_FPS_REPORT
        updateFps(ctx);
#endif /* HWC_FPS_REPORT */
       
        err = poll(&fds, 1, 256);
	   
	

//TODO
#if 1//ndef DEBUG
        if(err == 0)
        {
            if(!ctx->force_sgx[0] && ctx->HWCFramecount == g_hwcFramecount )
		g_force_gpu ++;		     
	    else
	    {
		g_hwcFramecount = ctx->HWCFramecount;
		g_force_gpu = 0;
	    }

	    if(g_force_gpu >= 3)
		{
		     if(ctx->psHwcProcs && ctx->psHwcProcs->invalidate)
			{
			    ctx->force_sgx[0] |= (HWC_FORCE_SGX_REASON_STILL0 | HWC_FORCE_SGX_REASON_STILL1);
			    ctx->psHwcProcs->invalidate(ctx->psHwcProcs);
			    ALOGV("####invalidate, ctx->force_sgx:%x", ctx->force_sgx[0]);
			}
			g_force_gpu = 0;
			g_hwcFramecount = ctx->HWCFramecount;
		}
        }
#endif
#if 1
			if (property_get("sys.fasboot_shutdown", property, NULL) > 0)
			{
					
					if(atoi(property))
					{
							if(g_Setfastboot_fg != 1)
							{
								char buffer[2];
								int bytes = sprintf(buffer, "%d", 1);
								hwc_set_sysfs_input_attr("/sys/class/disp/disp/attr","fastboot", buffer, bytes);
								g_Setfastboot_fg = 1;
							}
							
					}
					else
					{
							if(g_Setfastboot_fg != 0)
							{
							  char buffer[2];
								int bytes = sprintf(buffer, "%d", 0);
								hwc_set_sysfs_input_attr("/sys/class/disp/disp/attr","fastboot", buffer, bytes);
								g_Setfastboot_fg = 0;
							}
					}
			}
			
#endif
				
        if(ctx->hdmi_hpd_active != ctx->hdmi_hpd)
        {
            hwc_hdmi_switch();
        }
#if 1 
        if(err > 0 && fds.events == POLLIN)
        {
    		int count = recv(hotplug_sock, &buf, sizeof(buf),0);
    		if(count > 0)
    		{
    		    int IsVsync, IsHdmi;
    		    
        		ALOGV("####received %s", buf);

                IsVsync = !strcmp(buf, "change@/devices/platform/disp");
                IsHdmi = !strcmp(buf, "change@/devices/virtual/switch/hdmi");
                
                if(IsVsync)
                {
                    uint64_t timestamp = 0;
                    unsigned int display_id = -1;
                    const char *s = buf;
                
                    if(!ctx->psHwcProcs || !ctx->psHwcProcs->vsync)
                       return 0;

                    s += strlen(s) + 1;
                    while(s)
                    {
                        if (!strncmp(s, "VSYNC0=", strlen("VSYNC0=")))
                        {
                            timestamp = strtoull(s + strlen("VSYNC0="), NULL, 0);
                            ALOGV("#### %s display0 timestamp:%lld", s,timestamp);
                            display_id = 0;
                            IonHandleDecRef();
                        }
                        else if (!strncmp(s, "VSYNC1=", strlen("VSYNC1=")))
                        {
                            timestamp = strtoull(s + strlen("VSYNC1="), NULL, 0);
                            ALOGV("#### %s display1 timestamp:%lld", s,timestamp);
                            display_id = 1;
                        }

                        s += strlen(s) + 1;
                        if(s - buf >= count)
                        {
                            break;
                        }
                    }

                    if(display_id == 0 && ctx->vsync_en)//only handle display 0 vsync event now
                    {
                        ctx->psHwcProcs->vsync(ctx->psHwcProcs, display_id, timestamp);
                    }
                }

                if(IsHdmi)
                {
                    const char *s = buf;
                    int new_hdmi_hpd = 0;
                    
                    ALOGV("####received %s", buf);
                    
                    s += strlen(s) + 1;
                    while(s)
                    {
                        if (!strncmp(s, "SWITCH_STATE=", strlen("SWITCH_STATE=")))
                        {
                            new_hdmi_hpd = strtoull(s + strlen("SWITCH_STATE="), NULL, 0);
                            if(new_hdmi_hpd != ctx->hdmi_hpd)
                            {
                                ctx->hdmi_hpd = new_hdmi_hpd;
                                ALOGD( "####hdmi_hpd:%d", ctx->hdmi_hpd);
                                hwc_hdmi_switch();
                            }
                        }
                        
                        s += strlen(s) + 1;
                        if(s - buf >= count)
                        {
                            break;
                        }
                    }
                }
            }
        }
#else
	{
                    uint64_t timestamp = 0;
                    unsigned int display_id = -1;
                
                    if(!ctx->psHwcProcs || !ctx->psHwcProcs->vsync)
                       return 0;
		    timestamp = systemTime(CLOCK_MONOTONIC);
                    ALOGD("#### display0 timestamp:%lld", timestamp);
                    display_id = 0;
                    if(display_id == 0)//only handle display 0 vsync event now
                    {
                        ctx->psHwcProcs->vsync(ctx->psHwcProcs, display_id, timestamp);
                    }
        }

#endif
    }

	return 0;
}

void *VsyncThreadWrapper(void *priv)
{
	setpriority(PRIO_PROCESS, 0, HAL_PRIORITY_URGENT_DISPLAY);

	hwc_uevent();

	return NULL;
}


