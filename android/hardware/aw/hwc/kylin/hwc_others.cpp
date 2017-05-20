
#include "hwc.h"


int _hwcdev_layer_config_3d(int disp, disp_layer_info *layer_info)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[disp]; 
    __display_3d_mode cur_3d_mode = PsDisplayInfo->Current3DMode;

    if(layer_info->mode == DISP_LAYER_WORK_MODE_SCALER)
    {
        switch(cur_3d_mode)
        {
            case DISPLAY_2D_ORIGINAL:
                layer_info->fb.b_trd_src = 0;
                layer_info->b_trd_out = 0;
                break;
            case DISPLAY_2D_LEFT:
                layer_info->fb.b_trd_src = 1;
                layer_info->fb.trd_mode = DISP_3D_SRC_MODE_SSF;
                layer_info->b_trd_out = 0;
                break;
            case DISPLAY_2D_TOP:
                layer_info->fb.b_trd_src = 1;
                layer_info->fb.trd_mode = DISP_3D_SRC_MODE_TB;
                layer_info->b_trd_out = 0;
                break;
            case DISPLAY_3D_LEFT_RIGHT_HDMI:
                layer_info->fb.b_trd_src = 1;
                layer_info->fb.trd_mode = DISP_3D_SRC_MODE_SSF;
                layer_info->b_trd_out = 1;
                layer_info->out_trd_mode = DISP_3D_OUT_MODE_FP;
                break;
            case DISPLAY_3D_TOP_BOTTOM_HDMI:
                layer_info->fb.b_trd_src = 1;
                layer_info->fb.trd_mode = DISP_3D_SRC_MODE_TB;
                layer_info->b_trd_out = 1;
                layer_info->out_trd_mode = DISP_3D_OUT_MODE_FP;
                break;
            default:
                break;
        }

        if(cur_3d_mode == DISPLAY_3D_LEFT_RIGHT_HDMI || cur_3d_mode == DISPLAY_3D_TOP_BOTTOM_HDMI)
        {
            layer_info->screen_win.x = 0;
            layer_info->screen_win.y = 0;
            layer_info->screen_win.width = 1920;
            layer_info->screen_win.height = 1080 * 2;
        }
    }
    else
    {
        if(cur_3d_mode == DISPLAY_3D_LEFT_RIGHT_HDMI || cur_3d_mode == DISPLAY_3D_TOP_BOTTOM_HDMI)
        {
            layer_info->mode = DISP_LAYER_WORK_MODE_SCALER;
            layer_info->screen_win.x = 0;
            layer_info->screen_win.y = 0;
            layer_info->screen_win.width = 1920;
            layer_info->screen_win.height = 1080;
        }
    }

   return 0;
}

static int _hwc_device_set_3d_mode_per_display(int disp, __display_3d_mode new_mode)
{    
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[disp];
    __display_3d_mode old_mode = PsDisplayInfo->Current3DMode;
    unsigned long arg[4]={0};

    if(old_mode == new_mode)
    {
        return 0;
    }
  
    if(PsDisplayInfo->VirtualToHWDisplay != -1 && PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_HDMI)
    {
        if(new_mode == DISPLAY_2D_ORIGINAL)
        {
            Globctx->CanForceGPUCom = 1;
        }else{
            Globctx->CanForceGPUCom = 0;
        }
        
        PsDisplayInfo->Current3DMode = new_mode;
        if( (old_mode == DISPLAY_3D_LEFT_RIGHT_HDMI || old_mode == DISPLAY_3D_TOP_BOTTOM_HDMI)
            && (new_mode == DISPLAY_3D_LEFT_RIGHT_HDMI || new_mode ==  DISPLAY_3D_TOP_BOTTOM_HDMI ))
        {
            return 0;
        }
        int disp = PsDisplayInfo->VirtualToHWDisplay;
        hwc_hdmi_switch(disp,1,1);
    
    }
    
    return 0;
}

int _hwc_device_set_3d_mode(int disp, __display_3d_mode mode)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int ret = 0;
    int i = 0;
    for( i = 0; i < NUMBEROFDISPLAY; i++)
    {
        ret = _hwc_device_set_3d_mode_per_display(i, mode);
    }
    return 0;
}

int _hwc_device_set_backlight_mode(int disp, int mode)                   
{                                                                                            
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long arg[4]={0};

    if(Globctx->SunxiDisplay[0].DisplayType != DISP_OUTPUT_TYPE_HDMI)
    { 
        arg[0] = 0;  
        if(mode == 1)                                                                              
        {                                                                                          
            return  ioctl(Globctx->DisplayFd,DISP_CMD_DRC_ENABLE,arg);                                       
        }                                                                                          
        else                                                                                       
        {	                                                                                         
            return ioctl(Globctx->DisplayFd,DISP_CMD_DRC_DISABLE,arg);                                       
        } 
    }                               

    return 0;                                                                                
}                                                                                            

int _hwc_device_set_backlight_demomode(int disp, int mode)               
{                                                                                            
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;

    
 
    unsigned long arg[4]={0};
    disp_window window;                                                                     

    if(disp == HWC_DISPLAY_PRIMARY && Globctx->SunxiDisplay[disp].DisplayType != DISP_OUTPUT_TYPE_HDMI)
    { 
        arg[0] = Globctx->SunxiDisplay[disp].VirtualToHWDisplay; 
        window.x = 0;                                                                             
        window.y = 0;                                                                             
        window.width = ioctl(Globctx->DisplayFd,DISP_CMD_GET_SCN_WIDTH,arg);                          
        window.height = ioctl(Globctx->DisplayFd,DISP_CMD_GET_SCN_HEIGHT,arg);  
        if(mode == 1)                                                                              
        {                                                                                          
            window.width /= 2;                                                                      
            arg[1] = (unsigned long)&window;                                                       
            return ioctl(Globctx->DisplayFd, DISP_CMD_DRC_SET_WINDOW,arg);                                       
        }                                                                                          
        else                                                                                       
        {	                                                                                         
            arg[1] = (unsigned long)&window;                                                       
            return ioctl(Globctx->DisplayFd, DISP_CMD_DRC_SET_WINDOW,arg);                                       
        } 
    }                                                                                                                      
    return 0;                                                                                 
}                                                                                            

int _hwc_device_set_enhancemode(int disp, int mode)          
{                                                                                            
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long arg[4]={0};


    if(disp == HWC_DISPLAY_PRIMARY)
    { 
        arg[0] = Globctx->SunxiDisplay[disp].VirtualToHWDisplay;  
        if(mode == 1)                                                                              
        {                                                                                          
            return  ioctl(Globctx->DisplayFd,DISP_CMD_ENHANCE_ENABLE,arg);                                       
        }                                                                                          
        else                                                                                       
        {	                                                                                         
            return ioctl(Globctx->DisplayFd,DISP_CMD_ENHANCE_DISABLE,arg);                                       
        } 
    }                               

    return 0;
}                                                                                            
                                                                                             
int _hwc_device_set_enhancedemomode(int disp, int mode)      
{ 
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    unsigned long arg[4]={0};
    disp_window window;                                                                


    if(disp == HWC_DISPLAY_PRIMARY)
    { 
        arg[0] = Globctx->SunxiDisplay[disp].VirtualToHWDisplay;                                                                    
        window.x = 0;                                                                          
        window.y = 0;                                                                          
        window.width = ioctl(Globctx->DisplayFd,DISP_CMD_GET_SCN_WIDTH,arg);                       
        window.height = ioctl(Globctx->DisplayFd,DISP_CMD_GET_SCN_HEIGHT,arg);                     
        if(mode == 1)                                                                              
        {                                                                                          
            window.width /= 2;                                                                  
            arg[1] = (unsigned long)&window;                                                   
            return ioctl(Globctx->DisplayFd,DISP_CMD_SET_ENHANCE_WINDOW,arg);                                       
        }                                                                                          
        else                                                                                       
        {	                                                                                         
            arg[1] = (unsigned long)&window;                                                   
            return ioctl(Globctx->DisplayFd,DISP_CMD_SET_ENHANCE_WINDOW,arg);                                       
        } 
    }                               

    return 0;                                                                                           
} 

int _hwc_device_set_output_mode(int disp, int out_type, int out_mode)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo = NULL;
    

    if (Globctx->HDMIMode != out_mode) 
    {
        Globctx->HDMIMode = out_mode;
        aw_set_default_hdmi(out_mode);
        PsDisplayInfo = ManageDisplay(Globctx->SunxiDisplay, DISP_OUTPUT_TYPE_HDMI, FIND_HWTYPE);
        if (PsDisplayInfo != NULL) 
        {
            int disp = PsDisplayInfo->VirtualToHWDisplay;
            unsigned char w = PsDisplayInfo->DisplayPersentWT;
            unsigned char h = PsDisplayInfo->DisplayPersentHT;       
            hwc_hdmi_switch(disp, 0,1);
            hwc_hdmi_switch(disp, 1,1);
            PsDisplayInfo->DisplayPersentWT = w; 
            PsDisplayInfo->DisplayPersentHT = h;
           
        }
    }

    return 0;
}

int  _hwc_set_persent(int disp,int para0, int para1)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo = NULL;
    
    PsDisplayInfo = &Globctx->SunxiDisplay[disp];
    if(PsDisplayInfo->VirtualToHWDisplay != -1 && PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_HDMI)
    {
        if(para0 >= 90 && para0 <= 100)
        {
            PsDisplayInfo->DisplayPersentWT = para0;
        }else{
            PsDisplayInfo->DisplayPersentWT = 100;
        }

        if(para1 >= 90 && para1 <= 100)
        {
            PsDisplayInfo->DisplayPersentHT = para1;
        }else{
            PsDisplayInfo->DisplayPersentHT = PsDisplayInfo->DisplayPersentWT;
        }
    }
    return 0;   
}

