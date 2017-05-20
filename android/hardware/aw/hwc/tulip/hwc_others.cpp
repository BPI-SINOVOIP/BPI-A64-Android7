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

static int _hwc_device_set_3d_mode_per_display(int disp, __display_3d_mode new_mode)
{    
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[disp];
    __display_3d_mode old_mode = PsDisplayInfo->Current3DMode;
	static int last2dMode;
    unsigned long arg[4]={0};
    disp_tv_mode current_mode;
    if(old_mode == new_mode)
    {
        return 0;
    }
    if(PsDisplayInfo->VirtualToHWDisplay != -EINVAL && PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_HDMI)
    {
        if(new_mode == DISPLAY_2D_ORIGINAL)
        {
            Globctx->CanForceGPUCom = 1;
            current_mode = (disp_tv_mode)last2dMode;
        }else{
			if(PsDisplayInfo->DisplayMode != DISP_TV_MOD_1080P_24HZ_3D_FP)
				last2dMode = PsDisplayInfo->DisplayMode;
            Globctx->CanForceGPUCom = 0;
            current_mode = DISP_TV_MOD_1080P_24HZ_3D_FP;
        }
        
        PsDisplayInfo->Current3DMode = new_mode;
        int disp = PsDisplayInfo->VirtualToHWDisplay;
        hwc_hotplug_switch(disp,1,current_mode);
    }    
    return 0;
}

int _hwc_device_set_3d_mode(int disp, __display_3d_mode mode)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int ret = 0;
    int i = 0;

    HWC_UNREFERENCED_PARAMETER(disp);
    
    for( i = 0; i < Globctx->NumberofDisp; i++)
    {
        ret = _hwc_device_set_3d_mode_per_display(i, mode);
    }
    return 0;
}
                                                                                         

int _hwc_device_set_backlight(int disp, int on_off, bool half)               
{                                                                                            
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long arg[4]={0};
    disp_rect window;
    if(disp == HWC_DISPLAY_PRIMARY && Globctx->SunxiDisplay[disp].DisplayType != DISP_OUTPUT_TYPE_HDMI)
    { 
        arg[0] = Globctx->SunxiDisplay[disp].VirtualToHWDisplay;
        window.x = 0;
        window.y = 0;
        window.width = ioctl(Globctx->DisplayFd, DISP_GET_SCN_WIDTH, arg);
        window.height = ioctl(Globctx->DisplayFd, DISP_GET_SCN_HEIGHT, arg);
        arg[1] = (unsigned long)&window;
        if(half)
        { 
            if(on_off)
            {
                window.width > window.height ? window.width/= 2 : window.height /= 2;
            }
            ioctl(Globctx->DisplayFd, DISP_SMBL_SET_WINDOW, arg);
            return 0;
        }
        if(on_off == 1 )
        {
            ioctl(Globctx->DisplayFd, DISP_SMBL_ENABLE, arg);  
        }else{
            ioctl(Globctx->DisplayFd, DISP_SMBL_DISABLE, arg);
        }

    }
    return 0;
}
                                                                                             
int _hwc_device_set_enhancemode(int disp, bool on_off, bool half)      
{ 
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    unsigned long arg[4]={0};
    enum tag_DISP_CMD ioctl_arg;
    if(disp == HWC_DISPLAY_PRIMARY)
    {
       arg[0] = 0;
       if(on_off)
       {
            if(half)
            {
                ioctl_arg = DISP_ENHANCE_DEMO_ENABLE;
            }else{
                ioctl_arg = DISP_ENHANCE_ENABLE;
            }
       }else{
            if(half)
            {
                ioctl_arg = DISP_ENHANCE_DEMO_DISABLE;
            }else{
                ioctl_arg = DISP_ENHANCE_DISABLE;
            }
       }
       ioctl(Globctx->DisplayFd, ioctl_arg, arg); 
    }                               

    return 0;                                                                                           
} 

int _hwc_device_set_output_mode(int disp, int out_type, int out_mode)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[disp];
    int disp_t;

    HWC_UNREFERENCED_PARAMETER(out_type);

    if (PsDisplayInfo->VirtualToHWDisplay != -EINVAL && PsDisplayInfo->DisplayMode != out_mode && PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_HDMI) 
    {  
        PsDisplayInfo->DisplayMode = (disp_tv_mode)out_mode;
        disp_t = PsDisplayInfo->VirtualToHWDisplay;
        hwc_hotplug_switch(disp_t, 0, (disp_tv_mode)out_mode);
        hwc_hotplug_switch(disp_t, 1, (disp_tv_mode)out_mode);          
    }
    return 0;
}
int _hwc_set_persent(int disp,int para0, int para1)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo = NULL;
    
    PsDisplayInfo = &Globctx->SunxiDisplay[disp];
    if(PsDisplayInfo->VirtualToHWDisplay != -1 && PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_HDMI)
    {
        if(para0 >= 90 && para0 <= 100)
        {
            PsDisplayInfo->SetPersentWidth= para0;
        }else{
            PsDisplayInfo->SetPersentWidth= 100;
        }
        if(para1 >= 90 && para1 <= 100)
        {
            PsDisplayInfo->SetPersentHeight= para1;
        }else{
            PsDisplayInfo->SetPersentHeight = PsDisplayInfo->SetPersentWidth;
        }
    }
    return 0;   
}

