/*
 * Copyright (C) Allwinner Tech All Rights Reserved
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
#include "hwc.h"

#include <sys/resource.h>
#include <sys/time.h>

#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <time.h>

static tv_para_t g_tv_para[]=
{
    {8, DISP_TV_MOD_NTSC,             720,    480, 60,0},
    {8, DISP_TV_MOD_PAL,              720,    576, 60,0},
    
    {5, DISP_TV_MOD_480I,             720,    480, 60,0},
    {5, DISP_TV_MOD_576I,             720,    576, 60,0},
    {5, DISP_TV_MOD_480P,             720,    480, 60,0},
    {5, DISP_TV_MOD_576P,             720,    576, 60,0},
    {5, DISP_TV_MOD_720P_50HZ,        1280,   720, 50,0},
    {5, DISP_TV_MOD_720P_60HZ,        1280,   720, 60,0},

    {1, DISP_TV_MOD_1080P_24HZ,       1920,   1080, 24,0},
    {5, DISP_TV_MOD_1080P_50HZ,       1920,   1080, 50,0},
    {5, DISP_TV_MOD_1080P_60HZ,       1920,   1080, 60,0},
    {5, DISP_TV_MOD_1080I_50HZ,       1920,   1080, 50,0},
    {5, DISP_TV_MOD_1080I_60HZ,       1920,   1080, 60,0},

	
	{5, DISP_TV_MOD_3840_2160P_25HZ,  3840,   2160, 25,0xff},
	{5, DISP_TV_MOD_3840_2160P_24HZ,  3840,   2160, 24,0xff},
    {5, DISP_TV_MOD_3840_2160P_30HZ,  3840,   2160, 30,0xff},
    
    {1, DISP_TV_MOD_1080P_24HZ_3D_FP, 1920,   1080, 24,0},
    {1, DISP_TV_MOD_720P_50HZ_3D_FP,  1280,   720, 50,0},
    {1, DISP_TV_MOD_720P_60HZ_3D_FP,  1280,   720, 60,0},
    {1, DISP_TV_MODE_NUM,  0,   0, 0,0},
};

int get_info_mode(int mode,MODEINFO info)
{
    unsigned int i = 0;
    
    for(i=0; i<sizeof(g_tv_para) / sizeof(tv_para_t); i++)
    {
        if(g_tv_para[i].mode == mode)
        {
            return *(((int *)(g_tv_para+i))+info);
        }
    }
    return -1;
}

int hwc_manage_display(DisplayInfo **retDisplayInfo, int DispInfo, ManageDisp mode)
{

    DisplayInfo* PsDisplayInfo = NULL;
    DisplayInfo* TmpDisplayInfo = NULL;
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int find = -1;
    int disp;
    for(disp = 0; disp < (Globctx->NumberofDisp); disp++)
    {
        PsDisplayInfo = &Globctx->SunxiDisplay[disp];
        switch(mode)
        {
            case FIND_HWDISPNUM:
                if(PsDisplayInfo->VirtualToHWDisplay == DispInfo)
                {
                    *retDisplayInfo = PsDisplayInfo;
                    return disp;
                }
                break;
            case NULL_DISPLAY:
                if(PsDisplayInfo->VirtualToHWDisplay != -EINVAL )
                {
                    return disp;
                }
                break;
            case SET_DISP:
                if(PsDisplayInfo->VirtualToHWDisplay == DispInfo)
                {
                    *retDisplayInfo = PsDisplayInfo;
                    return disp;
                }
                if(disp == HWC_DISPLAY_PRIMARY && !PsDisplayInfo->active)
                {
                    PsDisplayInfo->VirtualToHWDisplay = DispInfo;
                    *retDisplayInfo = PsDisplayInfo;
                    PsDisplayInfo->active = 1;
                    return disp;
                }
                if(PsDisplayInfo->VirtualToHWDisplay == -EINVAL && TmpDisplayInfo == NULL)
                {
                    TmpDisplayInfo = PsDisplayInfo;
                    find = disp;
                }
                if(disp == Globctx->NumberofDisp-1 && TmpDisplayInfo != NULL)
                {
                    TmpDisplayInfo->VirtualToHWDisplay = DispInfo;
                    *retDisplayInfo = TmpDisplayInfo;
                    (*retDisplayInfo)->active = 1;
                    return find;
                }
                break;
            case FREE_DISP:
                if(PsDisplayInfo->VirtualToHWDisplay == DispInfo)
                {
                    if(disp != HWC_DISPLAY_PRIMARY)
                    {
                        PsDisplayInfo->VirtualToHWDisplay = -EINVAL;
                    }
                    PsDisplayInfo->active = 0;
                    if(PsDisplayInfo->Current3DMode == DISPLAY_3D_LEFT_RIGHT_HDMI
                        || PsDisplayInfo->Current3DMode == DISPLAY_3D_TOP_BOTTOM_HDMI)
                    {
                        PsDisplayInfo->Current3DMode = DISPLAY_2D_ORIGINAL;
						PsDisplayInfo->DisplayMode = DISP_TV_MODE_NUM;
                    }
                    PsDisplayInfo->max_thruput = 0;
                    return disp;
                }
                break;
            default:
                ALOGD("Error  usage in ManageDisplay");
        }
    }

    return -1;
    
}

disp_tv_mode get_suitable_hdmi_mode(int select, disp_tv_mode lastmode)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
	unsigned long arg[4]={0};
    arg[0] = select;
    int ret, i, j = -1;
    disp_tv_mode theMostMode = DISP_TV_MODE_NUM;
    i = sizeof(g_tv_para) / sizeof(g_tv_para[0]);
    if(lastmode < DISP_TV_MODE_NUM)
    {
            arg[1] = DISP_OUTPUT_TYPE_HDMI;
	        arg[2] = lastmode;
            ret = ioctl(Globctx->DisplayFd, DISP_DEVICE_SWITCH, arg);
	        if(ret >= 0)
	        {
                return lastmode;
	        }
    }
    while(i > 0)
    {
        i--;
        if(g_tv_para[i].mode == DISP_TV_MOD_1080P_60HZ)
        {
            j = i;
        }
        if(j != -1)
        {
            arg[1] = DISP_OUTPUT_TYPE_HDMI;
	        arg[2] = g_tv_para[i].mode;
            ret = ioctl(Globctx->DisplayFd, DISP_DEVICE_SWITCH, arg);
	        if(ret >= 0)
	        {
                if(theMostMode == DISP_TV_MODE_NUM)
                {
                    g_tv_para[sizeof(g_tv_para) / sizeof(g_tv_para[0])-1].support = 1<<select;
                    theMostMode = g_tv_para[i].mode;
                }
                g_tv_para[i].support |= 1<<select;
	        }else{
	            g_tv_para[i].support &= ~(1<<select);
	        }
        }
    }
    if(theMostMode != DISP_TV_MODE_NUM)
    {
        return theMostMode;
    }else{
        return DISP_TV_MOD_1080P_60HZ;
    }
}

int hwc_hotplug_switch(int DisplayNum, bool plug, disp_tv_mode set_mode)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int vir_disp = -1;
    DisplayInfo   *PsDisplayInfo = NULL;

    unsigned long arg[4] = {0};
    bool AllreadyPlugin = 0;
    vir_disp = hwc_manage_display(&PsDisplayInfo, DisplayNum, FIND_HWDISPNUM);
    if (PsDisplayInfo != NULL)
    {
        ALOGD("###AllreadyPlugin:vir(%d) ture(%d)####", vir_disp, DisplayNum);
        AllreadyPlugin = 1;
    }

    if(plug)
    {
        if(PsDisplayInfo == NULL)
        {
            vir_disp = hwc_manage_display(&PsDisplayInfo, DisplayNum, SET_DISP);
        }

        if(set_mode >= DISP_TV_MODE_NUM)
        {
            set_mode = get_suitable_hdmi_mode(DisplayNum,PsDisplayInfo->DisplayMode);
        }
        if(set_mode != DISP_TV_MODE_NUM)
        {
            PsDisplayInfo->VarDisplayWidth = get_info_mode(set_mode,WIDTH);
            PsDisplayInfo->VarDisplayHeight = get_info_mode(set_mode,HEIGHT);
            PsDisplayInfo->DisplayType = DISP_OUTPUT_TYPE_HDMI;
            PsDisplayInfo->DisplayMode = set_mode;
            PsDisplayInfo->DiplayDPI_X = 213000;
            PsDisplayInfo->DiplayDPI_Y = 213000;
            PsDisplayInfo->DisplayVsyncP = 1000000000/get_info_mode(set_mode, REFRESHRAE);
            PsDisplayInfo->HwChannelNum = DisplayNum?2:4;
            PsDisplayInfo->LayerNumofCH = NUMLAYEROFCHANNEL;
            PsDisplayInfo->VideoCHNum =1;
            if(Globctx->SunxiDisplay[0].VirtualToHWDisplay != DisplayNum && !AllreadyPlugin)
            {
                PsDisplayInfo->InitDisplayHeight = PsDisplayInfo->VarDisplayHeight;
                PsDisplayInfo->InitDisplayWidth = PsDisplayInfo->VarDisplayWidth;
            }
            PsDisplayInfo->fb_thruput = PsDisplayInfo->InitDisplayHeight * PsDisplayInfo->InitDisplayWidth 
                    * 4 * (1000000000/PsDisplayInfo->DisplayVsyncP/60);
            arg[0] = DisplayNum;
            arg[1] = DISP_OUTPUT_TYPE_HDMI;
            arg[2] = set_mode;
            ioctl(Globctx->DisplayFd, DISP_DEVICE_SWITCH, (unsigned long)arg);
            arg[0] = DisplayNum;
            arg[1] = 1;
            ioctl(Globctx->DisplayFd, DISP_VSYNC_EVENT_EN,(unsigned long)arg);
        }else{

            ALOGD("###has no fix HDMI Mode###");
            return 0;
        }       
        ALOGD( "###hdmi plug in, Type:%d, Mode:0x%08x###",
                PsDisplayInfo->DisplayType, PsDisplayInfo->DisplayMode);
        
    }else{
        hwc_manage_display(NULL, DisplayNum ,FREE_DISP);
    }
    if(Globctx->psHwcProcs && Globctx->psHwcProcs->hotplug)
    {
        if((vir_disp == HWC_DISPLAY_EXTERNAL)
            && ((!AllreadyPlugin && plug )||(AllreadyPlugin && !plug)))
        {
            ALOGD("hotplug report plug[%d][%d]",DisplayNum,plug);
            Globctx->psHwcProcs->hotplug(Globctx->psHwcProcs, HWC_DISPLAY_EXTERNAL, plug);
        }
    }else{
        ALOGD("###psHwcProcs  No register.###");
    }
    if(!plug)
    {
        arg[0] = DisplayNum;
        arg[1] = DISP_OUTPUT_TYPE_NONE; 
        sleep(1);
        ioctl(Globctx->DisplayFd, DISP_DEVICE_SWITCH, (unsigned long)arg);
        arg[0] = DisplayNum;
        arg[1] = 0;
        ioctl(Globctx->DisplayFd, DISP_VSYNC_EVENT_EN,(unsigned long)arg);

        if(hwc_manage_display(NULL, 0 ,NULL_DISPLAY) == Globctx->NumberofDisp)
        {
            ALOGD( "###ALL Display has plug out###");
        }
            ALOGD( "###hdmi plug out###");       
    }
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
		if((show_fps_settings & FPS_SHOW) != (psCtx->hwcdebug & FPS_SHOW))
		{	
		    ALOGD("###### %s hwc fps print ######",
                    (show_fps_settings & FPS_SHOW) != 0 ? "Enable" : "Disable");
		}
        psCtx->hwcdebug = show_fps_settings & SHOW_ALL;
        if(psCtx->hwcdebug & 1)
	    {
	        ALOGD(">>>fps:: %d\n",
                    (int)((psCtx->HWCFramecount - psCtx->uiBeginFrame) * 1.0f 
                        / (fCurrentTime - psCtx->fBeginTime)));
	    }
        psCtx->uiBeginFrame = psCtx->HWCFramecount;
	    psCtx->fBeginTime = fCurrentTime;
	}
}

static inline bool check_stop()
{
	char property[PROPERTY_VALUE_MAX];
	int  stop_hwc = 0;	    
	if (property_get("debug.hwc.forcegpu", property, NULL) >= 0)
	{
        stop_hwc = atoi(property);        
	}
    return !!stop_hwc;
}

static int hwc_uevent(void)
{
	struct sockaddr_nl snl;
	const int buffersize = 32*1024;
    char *buf = NULL;
    struct pollfd fds;
    bool stop_hwc = 0;
    int count, IsVsync, IsHdmi, hotplug_sock, retval, display_id = -1;
    unsigned int cout = 0, new_hdmi_hpd = 0;
    uint64_t timestamp = 0;
    const char *s = NULL;
	SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;

    buf = (char *)calloc(buffersize, sizeof(char));
    if(buf == NULL)
    {
        ALOGD("calloc err.");
        return -1;
    }

	memset(&snl, 0x0, sizeof(snl));
	snl.nl_family = AF_NETLINK;
	snl.nl_pid = 0;
	snl.nl_groups = 0xffffffff;
	hotplug_sock = socket(PF_NETLINK, SOCK_DGRAM, NETLINK_KOBJECT_UEVENT);
	if (hotplug_sock == -1)
    {
		ALOGE("####socket is failed in %s error:%d %s###",
                __FUNCTION__, errno, strerror(errno));
		free(buf);
		return -1;
	}

	setsockopt(hotplug_sock, SOL_SOCKET, SO_RCVBUFFORCE, &buffersize, sizeof(buffersize));
	retval = bind(hotplug_sock, (struct sockaddr *)&snl, sizeof(struct sockaddr_nl));
	if (retval < 0)
    {
		ALOGE("####bind is failed in %s error:%d %s###",
                __FUNCTION__, errno, strerror(errno));
		close(hotplug_sock);
		free(buf);
		return -1;
	}

	timeval tv = { 0, 0 };
    double starttime = 0.0;
    gettimeofday(&tv, NULL);
    starttime = tv.tv_sec * 1000 + tv.tv_usec / 1.0e3;
    double fCurrentTime = 0.0;
    ALOGD("######hwc uevent Thread(%d)%p.#######", gettid(), &snl);

	while(1) 
	{
        fds.fd = hotplug_sock;
        fds.events = POLLIN;
        fds.revents = 0;
        stop_hwc = check_stop();
        gettimeofday(&tv, NULL);
	    fCurrentTime = tv.tv_sec * 1000 + tv.tv_usec / 1.0e3;
        if(fCurrentTime - starttime >= 500)
        {
            if(Globctx->HWCFramecount - cout > 2)
            {
                cout = Globctx->HWCFramecount;
                Globctx->ForceGPUComp = stop_hwc;
            }else{
                 if(Globctx->ForceGPUComp == 0 && Globctx->CanForceGPUCom)
				{
                    if(Globctx->psHwcProcs != NULL 
                        && Globctx->psHwcProcs->invalidate != NULL)
					{
                		Globctx->psHwcProcs->invalidate(Globctx->psHwcProcs);
						Globctx->ForceGPUComp = 1;
					}
				}
            }
            starttime = fCurrentTime;
        }
        retval = poll(&fds, 1, 1000);
        if(Globctx->psHwcProcs == NULL || Globctx->psHwcProcs->vsync == NULL)
		{
             continue;
		}
        if(retval > 0 && (fds.revents & POLLIN))
        {
    		count = ::recv(hotplug_sock, buf, buffersize, 0);
            s = buf;
    		if(count > 0)
    		{
                IsVsync = !strcmp(s, "change@/devices/platform/disp");
                IsHdmi = !strcmp(s, "change@/devices/virtual/switch/hdmi");
                s += strlen(s) + 1;
                if(IsVsync)
                {
                    timestamp = 0;
                    while(s)
                    {
                        display_id = -1;
                        if (!strncmp(s, "VSYNC0=", strlen("VSYNC0=")))
                        {
                            timestamp = strtoull(s + strlen("VSYNC0="), NULL, 0);
                            display_id = 0;
                        }else if(!strncmp(s, "VSYNC1=", strlen("VSYNC1="))){
                            timestamp = strtoull(s + strlen("VSYNC1="), NULL, 0);
                            display_id = 1;
                        }
                        if(Globctx->SunxiDisplay[0].VirtualToHWDisplay == display_id)
                        {
                            Globctx->SunxiDisplay[0].mytimestamp = timestamp;
                            if(Globctx->SunxiDisplay[0].VsyncEnable == 1)
                            {
                                Globctx->psHwcProcs->vsync(Globctx->psHwcProcs, 0, timestamp);
                            }
                        } 
                        s += strlen(s) + 1;
                        if(s - buf >= count || s - buf >= buffersize)
                        {
                            break;
                        }
                    }
                }

                if(IsHdmi)
                {
                    while(s)
                    {
                        if (!strncmp(s, "SWITCH_STATE=", strlen("SWITCH_STATE=")))
                        {
                            new_hdmi_hpd = strtoull(s + strlen("SWITCH_STATE="), NULL, 0);
                            ALOGD( "#### disp[%d]   hotplug[%d]###", HDMI_USED, !!new_hdmi_hpd);
                            hwc_hotplug_switch(1, !!new_hdmi_hpd, DISP_TV_MODE_NUM);
                        }
                        s += strlen(s) + 1;
                        if(s - buf >= count || s - buf >= buffersize)
                        {
                            break;
                        }
                    }
                }
            }
        }
	    updateFps(Globctx);
    }

	return 0;
}

void *vsync_thread_wrapper(void *priv)
{
    HWC_UNREFERENCED_PARAMETER(priv);
    
	setpriority(PRIO_PROCESS, 0, HAL_PRIORITY_URGENT_DISPLAY);

	hwc_uevent();

	return NULL;
}

