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


static tv_para_t g_tv_para[]=
{
    {8, DISP_TV_MOD_NTSC,             720,    480, 60},
    {8, DISP_TV_MOD_PAL,              720,    576, 60},
    
    {5, DISP_TV_MOD_480I,             720,    480, 60},
    {5, DISP_TV_MOD_576I,             720,    576, 60},
    {5, DISP_TV_MOD_480P,             720,    480, 60},
    {5, DISP_TV_MOD_576P,             720,    576, 60},
    {5, DISP_TV_MOD_720P_50HZ,        1280,   720, 50},
    {5, DISP_TV_MOD_720P_60HZ,        1280,   720, 60},

    {1, DISP_TV_MOD_1080P_24HZ,       1920,   1080, 24},
    {5, DISP_TV_MOD_1080P_50HZ,       1920,   1080, 50},
    {5, DISP_TV_MOD_1080P_60HZ,       1920,   1080, 60},
    {5, DISP_TV_MOD_1080I_50HZ,       1920,   1080, 50},
    {5, DISP_TV_MOD_1080I_60HZ,       1920,   1080, 60},

	
	{5, DISP_TV_MOD_3840_2160P_25HZ,  3840,   2160, 25},
	{5, DISP_TV_MOD_3840_2160P_24HZ,  3840,   2160, 24},
    {5, DISP_TV_MOD_3840_2160P_30HZ,  3840,   2160, 30},
    
    {1, DISP_TV_MOD_1080P_24HZ_3D_FP, 1920,   1080, 24},
    {1, DISP_TV_MOD_720P_50HZ_3D_FP,  1280,   720, 50},
    {1, DISP_TV_MOD_720P_60HZ_3D_FP,  1280,   720, 60},

};

static void inttoa(int valule, char* store, int step )
{
    char tmp[32];
    char *tmpstor = tmp;
    int i = 0;
    while(valule)
    {
        *(tmpstor++) = valule%step +48;
        valule /= 10;
        i++;
    }
    *(store+i) = '\0';
    tmpstor = tmp;
    while(i)
    {
        *(store+(--i)) = *(tmpstor++);
    }
}

int aw_set_default_hdmi(int hdmi_settings)
{
    char property[PROPERTY_VALUE_MAX];
    inttoa(hdmi_settings,property,10);

	if (property_set("persist.sys.hdmimode", property) >= 0)
	{
        return 0;
	}

	return -1;
}

int aw_get_hdmi_setting(int *HdmiMode)
{
	int hdmi_settings = -1;
	char property[PROPERTY_VALUE_MAX];
	if (property_get("persist.sys.hdmimode", property, NULL) >= 0)
	{

	    hdmi_settings = atoi(property);
        
	}
    if(hdmi_settings>= DISP_TV_MOD_480I && hdmi_settings < DISP_TV_MODE_NUM)
    {
        *HdmiMode = hdmi_settings;
        return 1;
    }
	return 0;
}


int GetInfoOfMode(int mode,MODEINFO info)
{
    unsigned int i = 0;
    
    for(i=0; i<sizeof(g_tv_para)/sizeof(tv_para_t); i++)
    {
        if(g_tv_para[i].mode == mode)
        {
            return *(((int *)(g_tv_para+i))+info);
        }
    }
    return -1;
}

disp_tv_mode get_suitable_hdmi_mode(int select)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
	unsigned long arg[4]={0};
    arg[0] =select;
    int HdmiMode;
    int ret = -1;

    if(Globctx->HDMIMode >= DISP_TV_MOD_480I && Globctx->HDMIMode < DISP_TV_MODE_NUM)
    {
        //arg[1] = Globctx->HDMIMode;
        //ret = ioctl(Globctx->DisplayFd, DISP_CMD_HDMI_SUPPORT_MODE, arg);
	    //if(ret > 0)
	    //{
		        return  (disp_tv_mode)Globctx->HDMIMode;
	    //}

	}
    int i,j = -1;
    i = sizeof(g_tv_para)/sizeof(g_tv_para[0]); 
    while(i > 0)
    {
        i--;
        if(g_tv_para[i].mode == DISP_TV_MOD_1080P_60HZ)
        {
            j = i;
        }
        if(j != -1)
        {
	        arg[1] = g_tv_para[i].mode;
            ret = ioctl(Globctx->DisplayFd, DISP_CMD_HDMI_SUPPORT_MODE, arg);
	        if(ret > 0)
	        {
		        return g_tv_para[i].mode;
	        }
        }
        
    }
    return DISP_TV_MOD_720P_50HZ;    
}


DisplayInfo* ManageDisplay(DisplayInfo *HWDisplayInfo, int DispInfo,ManageDisp mode)
{

    DisplayInfo* PsDisplayInfo = NULL;
    DisplayInfo* TmpDisplayInfo =NULL;
    int find=0;
    int disp;
    for(disp = 0; disp < NUMBEROFDISPLAY; disp++)
    {
        PsDisplayInfo = HWDisplayInfo++;
        switch(mode)
        {
            case FIND_HWDISPNUM:
                if(PsDisplayInfo->VirtualToHWDisplay == DispInfo)
                {
                    return PsDisplayInfo;
                }
                break;
                
            case FIND_HWTYPE:
                if(PsDisplayInfo->VirtualToHWDisplay != -1 && PsDisplayInfo->DisplayType == DispInfo)
                {
                    return PsDisplayInfo;
                }
                break;
                
            case NULL_DISPLAY:
                if(PsDisplayInfo->VirtualToHWDisplay != -1 )
                {
                    return PsDisplayInfo;
                }
                break;
                
            case SET_DISP:
                if(PsDisplayInfo->VirtualToHWDisplay == DispInfo)
                {
                    return PsDisplayInfo;
                }
                 
                if(PsDisplayInfo->VirtualToHWDisplay == -1 && TmpDisplayInfo == NULL)
                {
                    TmpDisplayInfo = PsDisplayInfo;
                }
                if(disp == NUMBEROFDISPLAY-1)
                {
                    TmpDisplayInfo->VirtualToHWDisplay = DispInfo;
                    return TmpDisplayInfo;
                }
                break;
                
            case FREE_DISP:
                if(PsDisplayInfo->VirtualToHWDisplay == DispInfo)
                {
                    PsDisplayInfo->VirtualToHWDisplay = -1;
                    return PsDisplayInfo;
                }
                break;
                
            default:
                ALOGD("Error  usage in ManageDisplay");
        }
    }
    
    PsDisplayInfo = NULL;
    return PsDisplayInfo;
    
}

int hwc_hdmi_switch(int DisplayNum, bool plug,bool update)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int disp=0;
    DisplayInfo   *PsDisplayInfo = NULL;

    unsigned long arg[4] = {0};
    bool AllreadyPlugin=0;
        
    if(plug)
    {
        PsDisplayInfo = ManageDisplay(Globctx->SunxiDisplay, DisplayNum, FIND_HWDISPNUM);
        if (PsDisplayInfo != NULL)
        {
            ALOGD("###AllreadyPlugin####");
            AllreadyPlugin = 1;
            
        }else{
        
            PsDisplayInfo = ManageDisplay(Globctx->SunxiDisplay, DisplayNum, SET_DISP);
        }
        if(update)
        {
            disp_tv_mode ExtDisplayMode;
            if(PsDisplayInfo->Current3DMode == DISPLAY_3D_LEFT_RIGHT_HDMI || PsDisplayInfo->Current3DMode == DISPLAY_3D_TOP_BOTTOM_HDMI)
            {
                ExtDisplayMode = DISP_TV_MOD_1080P_24HZ_3D_FP;
            }else{
                ExtDisplayMode = get_suitable_hdmi_mode(DisplayNum);
            }
            
            PsDisplayInfo->VarDisplayWidth = GetInfoOfMode(ExtDisplayMode,WIDTH);
            PsDisplayInfo->VarDisplayHeight = GetInfoOfMode(ExtDisplayMode,HEIGHT);
            PsDisplayInfo->DisplayType = DISP_OUTPUT_TYPE_HDMI;
            PsDisplayInfo->DisplayMode = ExtDisplayMode;
            PsDisplayInfo->DiplayDPI_X = 213000;
            PsDisplayInfo->DiplayDPI_Y = 213000;
            PsDisplayInfo->DisplayVsyncP = 1000000000/GetInfoOfMode(ExtDisplayMode, REFRESHRAE);
            PsDisplayInfo->HwLayerNum = DISPLAY_MAX_LAYER_NUM;
            PsDisplayInfo->HwPipeNum = NUMBEROFPIPE;

            
                
            arg[0] = DisplayNum;
            ioctl(Globctx->DisplayFd, DISP_CMD_HDMI_DISABLE, (unsigned long)arg);

            arg[0] = DisplayNum;
            arg[1] = ExtDisplayMode;
            ioctl(Globctx->DisplayFd, DISP_CMD_HDMI_SET_MODE, (unsigned long)arg);
            ioctl(Globctx->DisplayFd, DISP_CMD_HDMI_ENABLE, (unsigned long)arg);

        }else{
        
            ALOGD("###HDMI[%d] already plugin",DisplayNum);
            return 1;
        }
        PsDisplayInfo->DisplayEnable = 1;
        Globctx->DetectError = 0;
        
        ALOGD( "###hdmi plug in, Type:%d, Mode:0x%08x###", PsDisplayInfo->DisplayType,PsDisplayInfo->DisplayMode);
        
    }else{
    
        arg[0] = DisplayNum;
        ioctl(Globctx->DisplayFd, DISP_CMD_HDMI_DISABLE, (unsigned long)arg);
        
        if(Globctx->SunxiDisplay[0].VirtualToHWDisplay == DisplayNum )
        {
            Globctx->SunxiDisplay[0].DisplayEnable = 0;
        }else{
            ManageDisplay(Globctx->SunxiDisplay, DisplayNum ,FREE_DISP);
        }
        
        if(ManageDisplay(Globctx->SunxiDisplay, 0 ,NULL_DISPLAY) == NULL)
        {
            
            Globctx->DetectError = 0;
            ALOGD( "###ALL Display has plug out###");
        }
            ALOGD( "###hdmi plug out###");
            
    }
    
    if(Globctx->psHwcProcs && Globctx->psHwcProcs->hotplug)
    {
        if(Globctx->SunxiDisplay[0].VirtualToHWDisplay != DisplayNum && AllreadyPlugin == 0)
        {
            ALOGD("hotplug report plug[%d]",plug);
            Globctx->psHwcProcs->hotplug(Globctx->psHwcProcs, HWC_DISPLAY_EXTERNAL, plug);
        }
        
        return 1;
    }
    ALOGD("###psHwcProcs  No register.###");
    
    return 0;
}

static void updateFps(SUNXI_hwcdev_context_t *psCtx)
{

	double fCurrentTime = 0.0;
	timeval tv = { 0, 0 };
	gettimeofday(&tv, NULL);
	fCurrentTime = tv.tv_sec + tv.tv_usec / 1.0e6;

	if(fCurrentTime - psCtx->fBeginTime >= 1)
	{
		char property[PROPERTY_VALUE_MAX];
		int  show_fps_settings = 0;	    
		if (property_get("debug.hwc.showfps", property, NULL) >= 0)
		{
			show_fps_settings = atoi(property);        
		}else{
		    ALOGD("No hwc debug attribute node.");
			return;
		}
		if((show_fps_settings&FPS_SHOW) != (psCtx->hwcdebug&FPS_SHOW))
		{	
		    ALOGD("###### %s hwc fps print ######",(show_fps_settings&1) != 0 ? "Enable":"Disable");
		}
        psCtx->hwcdebug = show_fps_settings&SHOW_ALL;
        if(psCtx->hwcdebug&1)
	    {
	        ALOGD(">>>fps %d\n", int((psCtx->HWCFramecount - psCtx->uiBeginFrame) * 1.0f 
				                      / (fCurrentTime - psCtx->fBeginTime)));
	    }
        psCtx->uiBeginFrame = psCtx->HWCFramecount;
	    psCtx->fBeginTime = fCurrentTime;
	}
}

static int hwc_uevent(void)
{
	struct sockaddr_nl snl;
	const int buffersize = 32*1024;
	int retval;
	int hotplug_sock;
	SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;

	memset(&snl, 0x0, sizeof(snl));
	snl.nl_family = AF_NETLINK;
	snl.nl_pid = 0;
	snl.nl_groups = 0xffffffff;

	hotplug_sock = socket(PF_NETLINK, SOCK_DGRAM, NETLINK_KOBJECT_UEVENT);
	if (hotplug_sock == -1) {
		ALOGE("####socket is failed in %s error:%d %s###", __FUNCTION__, errno, strerror(errno));
		return -1;
	}

	setsockopt(hotplug_sock, SOL_SOCKET, SO_RCVBUFFORCE, &buffersize, sizeof(buffersize));

	retval = bind(hotplug_sock, (struct sockaddr *)&snl, sizeof(struct sockaddr_nl));

	if (retval < 0) {
		ALOGE("####bind is failed in %s error:%d %s###", __FUNCTION__, errno, strerror(errno));
		close(hotplug_sock);
		return -1;
	}   
	while(1) 
	{
		char buf[4096*2] = {0};
        struct pollfd fds;
        int err;
        unsigned int cout;
    
        fds.fd = hotplug_sock;
        fds.events = POLLIN;
        fds.revents = 0;
        cout = Globctx->HWCFramecount;
        err = poll(&fds, 1, 1000);
 
        if(err > 0 && fds.revents == POLLIN)
        {
    		int count = recv(hotplug_sock, &buf, sizeof(buf),0);
    		if(count > 0)
    		{
    		    int IsVsync, IsHdmi;
    		    
                IsVsync = !strcmp(buf, "change@/devices/platform/disp");
                IsHdmi = !strcmp(buf, "change@/devices/virtual/switch/hdmi");
                
                if(IsVsync)
                {
                    uint64_t timestamp = 0;
                    int display_id = -1;
                    const char *s = buf;
                
                    if(!Globctx->psHwcProcs || !Globctx->psHwcProcs->vsync)
                    {
                       ALOGD("####unable to call Globctx->psHwcProcs->vsync, should not happened");
                       continue;
                    }

                    s += strlen(s) + 1;
                    while(s)
                    {
                        if (!strncmp(s, "VSYNC0=", strlen("VSYNC0=")))
                        {
                            timestamp = strtoull(s + strlen("VSYNC0="), NULL, 0);
                            ALOGV("#### %s display0 timestamp:%lld###", s,timestamp);
                            display_id = AW_DIS_00;
                        }
                        else if (!strncmp(s, "VSYNC1=", strlen("VSYNC1=")))
                        {
                            timestamp = strtoull(s + strlen("VSYNC1="), NULL, 0);
                            ALOGV("#### %s display1 timestamp:%lld###", s,timestamp);
                            display_id = AW_DIS_01;
                        }else if(!strncmp(s, "VSYNC2=", strlen("VSYNC2="))){
                            timestamp = strtoull(s + strlen("VSYNC1="), NULL, 0);
                            ALOGV("#### %s display2 timestamp:%lld###", s,timestamp);
                            display_id = AW_DIS_02;
                        }

                        s += strlen(s) + 1;
                        if(s - buf >= count)
                        {
                            break;
                        }
                    }
                    if(display_id == Globctx->SunxiDisplay[0].VirtualToHWDisplay && Globctx->SunxiDisplay[0].VsyncEnable == 1)
                    {
                        display_id = 0;
                        Globctx->psHwcProcs->vsync(Globctx->psHwcProcs, display_id, timestamp);
                    }
                }

                if(IsHdmi)
                {
                    const char *s = buf;
                    unsigned int new_hdmi_hpd ;
                    s += strlen(s) + 1;
                    while(s)
                    {
                        if (!strncmp(s, "SWITCH_STATE=", strlen("SWITCH_STATE=")))
                        {
                            new_hdmi_hpd = strtoull(s + strlen("SWITCH_STATE="), NULL, 0);
                            
                            ALOGD( "#### disp[%d]   hotplug[%d]###",HDMI_USED ,!!new_hdmi_hpd);
                            hwc_hdmi_switch(HDMI_USED, !!new_hdmi_hpd,1);
                        }
                        
                        s += strlen(s) + 1;
                        if(s - buf >= count)
                        {
                            break;
                        }
                    }
                }
            }
            Globctx->ForceGPUComp = 0;
            
        }else if(err == 0) {
            if(Globctx->HWCFramecount == cout)
            {
	            if(Globctx->ForceGPUComp == 0 && Globctx->CanForceGPUCom)
		        {
                    Globctx->ForceGPUComp = 1;
                    Globctx->psHwcProcs->invalidate(Globctx->psHwcProcs);
		        }
            }else{
                if((Globctx->HWCFramecount > cout ? Globctx->HWCFramecount-cout : cout-Globctx->HWCFramecount) > 2)
		        {
                	Globctx->ForceGPUComp = 0;
		        }
            }
        }

	    updateFps(Globctx);
    }

	return 0;
}

void *VsyncThreadWrapper(void *priv)
{
	setpriority(PRIO_PROCESS, 0, HAL_PRIORITY_URGENT_DISPLAY);

	hwc_uevent();

	return NULL;
}

