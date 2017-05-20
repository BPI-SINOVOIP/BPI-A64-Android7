
#include "hwc.h"


int _hwcdev_layer_config_3d(int disp, __disp_layer_info_t *layer_info)
{
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
    __display_3d_mode cur_3d_mode = ctx->cur_3d_mode[disp];

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

#ifdef __LECACY_USED__ 
            layer_info->scn_win.x = 0;
            layer_info->scn_win.y = 0;
            layer_info->scn_win.width = 1920;
            layer_info->scn_win.height = 1080 * 2;
#else
	    layer_info->screen_win.x = 0;
            layer_info->screen_win.y = 0;
            layer_info->screen_win.width = 1920;
            layer_info->screen_win.height = 1080 * 2;

#endif
        }
    }
    else
    {
        /*if(cur_3d_mode == DISPLAY_3D_LEFT_RIGHT_HDMI || cur_3d_mode == DISPLAY_3D_TOP_BOTTOM_HDMI)
        {
            layer_info->mode = DISP_LAYER_WORK_MODE_SCALER;
            layer_info->scn_win.x = 0;
            layer_info->scn_win.y = 0;
            layer_info->scn_win.width = 1920;
            layer_info->scn_win.height = 1080;
        }*/
    }

    return 0;
}

static int _hwc_device_set_3d_mode_per_display(int disp, __display_3d_mode new_mode)
{    
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
    __display_3d_mode old_mode = ctx->cur_3d_mode[disp];
    unsigned long arg[4]={0};

    if(old_mode == new_mode)
    {
        return 0;
    }

    if((disp == HWC_DISPLAY_PRIMARY) && (new_mode == DISPLAY_3D_LEFT_RIGHT_HDMI || new_mode == DISPLAY_3D_TOP_BOTTOM_HDMI))
    {
        ALOGD( "####%s: primary display do not support 3D!", __func__);
        return -1;
    }

    if((old_mode == DISPLAY_3D_LEFT_RIGHT_HDMI || old_mode == DISPLAY_3D_TOP_BOTTOM_HDMI) 
        && (new_mode != DISPLAY_3D_LEFT_RIGHT_HDMI && new_mode != DISPLAY_3D_TOP_BOTTOM_HDMI))//3d->2d
    {
        arg[0] = 1;

#ifdef __LECACY_USED__ 
        ioctl(ctx->disp_fp, DISP_CMD_HDMI_OFF, (unsigned long)arg);

        arg[0] = 1;
        arg[1] = get_suitable_hdmi_mode();
        ioctl(ctx->disp_fp, DISP_CMD_HDMI_SET_MODE, (unsigned long)arg);

        arg[0] = 1;
        ioctl(ctx->disp_fp, DISP_CMD_HDMI_ON, (unsigned long)arg);
#else
	 ioctl(ctx->disp_fp, DISP_CMD_HDMI_DISABLE, (unsigned long)arg);

        arg[0] = 1;
        arg[1] = get_suitable_hdmi_mode();
        ioctl(ctx->disp_fp, DISP_CMD_HDMI_SET_MODE, (unsigned long)arg);

        arg[0] = 1;
        ioctl(ctx->disp_fp, DISP_CMD_HDMI_ENABLE, (unsigned long)arg);

#endif
    }
    else if((old_mode != DISPLAY_3D_LEFT_RIGHT_HDMI && old_mode != DISPLAY_3D_TOP_BOTTOM_HDMI) 
        && (new_mode == DISPLAY_3D_LEFT_RIGHT_HDMI || new_mode == DISPLAY_3D_TOP_BOTTOM_HDMI))//2d->3d
    {

#ifdef __LECACY_USED__ 
        arg[0] = 1;
        ioctl(ctx->disp_fp, DISP_CMD_HDMI_OFF, (unsigned long)arg);

        arg[0] = 1;
        arg[1] = DISP_TV_MOD_1080P_24HZ_3D_FP;
        ioctl(ctx->disp_fp, DISP_CMD_HDMI_SET_MODE, (unsigned long)arg);

        arg[0] = 1;
        ioctl(ctx->disp_fp, DISP_CMD_HDMI_ON, (unsigned long)arg);
#else
	arg[0] = 1;
        ioctl(ctx->disp_fp, DISP_CMD_HDMI_DISABLE, (unsigned long)arg);

        arg[0] = 1;
        arg[1] = DISP_TV_MOD_1080P_24HZ_3D_FP;
        ioctl(ctx->disp_fp, DISP_CMD_HDMI_SET_MODE, (unsigned long)arg);

        arg[0] = 1;
        ioctl(ctx->disp_fp, DISP_CMD_HDMI_ENABLE, (unsigned long)arg);

#endif
    }

    ctx->cur_3d_mode[disp] = new_mode;

    return 0;
}

int _hwc_device_set_3d_mode(int disp, __display_3d_mode mode)
{
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
    int ret = 0;

    if(disp == HWC_DISPLAY_PRIMARY)
    {   
        ret = _hwc_device_set_3d_mode_per_display(0, mode);
        if((ret == 0) && ctx->hdmi_hpd)
        {
            ret = _hwc_device_set_3d_mode_per_display(1, mode);
        }
    }
    else if(disp == HWC_DISPLAY_EXTERNAL)
    {
        if(ctx->hdmi_hpd)
        {
            ret = _hwc_device_set_3d_mode_per_display(1, mode);
        }
        else
        {
            ALOGD( "####%s: extern display is plug out, can not set to 3d!", __func__);
            return -1;
        }
    }

    return ret;
}

int _hwc_device_set_backlight_mode(int disp, int mode)                   
{                                                                                            
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
    unsigned long arg[4]={0};

    if(disp == HWC_DISPLAY_PRIMARY)
    { 
        arg[0] = 0;  
#ifdef __LECACY_USED__ 
        if(mode == 1)                                                                              
        {                                                                                          
            return  ioctl(ctx->disp_fp,DISP_CMD_DRC_ON,arg);                                       
        }                                                                                          
        else                                                                                       
        {	                                                                                         
            return ioctl(ctx->disp_fp,DISP_CMD_DRC_OFF,arg);                                       
        } 
#else
	if(mode == 1)                                                                              
        {                                                                                          
            return  ioctl(ctx->disp_fp,DISP_CMD_DRC_ENABLE,arg);                                       
        }                                                                                          
        else                                                                                       
        {	                                                                                         
            return ioctl(ctx->disp_fp,DISP_CMD_DRC_DISABLE,arg);                                       
        }
#endif
    }                               

    return 0;                                                                                
}                                                                                            

int _hwc_device_set_backlight_demomode(int disp, int mode)               
{                                                                                            
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
    unsigned long arg[4]={0};

#ifdef __LECACY_USED__ 
    __disp_rect_t window;                                                                     
#else
    disp_window window;                                                                     
#endif

    if(disp == HWC_DISPLAY_PRIMARY)
    { 
        arg[0] = 0; 
        window.x = 0;                                                                             
        window.y = 0;                                                                             

#ifdef __LECACY_USED__ 
        window.width = ioctl(ctx->disp_fp,DISP_CMD_SCN_GET_WIDTH,arg);                          
        window.height = ioctl(ctx->disp_fp,DISP_CMD_SCN_GET_HEIGHT,arg);  
#else
	window.width = ioctl(ctx->disp_fp,DISP_CMD_GET_SCN_WIDTH,arg);                          
        window.height = ioctl(ctx->disp_fp,DISP_CMD_GET_SCN_HEIGHT,arg);  
#endif
        if(mode == 1)                                                                              
        {                                                                                          
            window.width /= 2;                                                                      
            arg[1] = (unsigned long)&window;                                                       
            return ioctl(ctx->disp_fp, DISP_CMD_DRC_SET_WINDOW,arg);                                       
        }                                                                                          
        else                                                                                       
        {	                                                                                         
            arg[1] = (unsigned long)&window;                                                       
            return ioctl(ctx->disp_fp, DISP_CMD_DRC_SET_WINDOW,arg);                                       
        } 
    }                                                                                                                      
    return 0;                                                                                 
}                                                                                            

int _hwc_device_set_enhancemode(int disp, int mode)          
{                                                                                            
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
    unsigned long arg[4]={0};

    if(disp == HWC_DISPLAY_PRIMARY)
    { 
        arg[0] = 0;  

#ifdef __LECACY_USED__ 
        if(mode == 1)                                                                              
        {                                                                                          
	   ioctl(ctx->disp_fp,DISP_CMD_ENHANCE_OFF,arg);
	   arg[1] = DISP_ENHANCE_MODE_VIVID;
           ioctl(ctx->disp_fp,DISP_CMD_SET_ENHANCE_MODE ,arg);                                       
           return  ioctl(ctx->disp_fp,DISP_CMD_ENHANCE_ON,arg);                                       
        }                                                                                          
        else                                                                                       
        {	                                                                                         

	   ioctl(ctx->disp_fp,DISP_CMD_ENHANCE_OFF,arg);
	   arg[1] = DISP_ENHANCE_MODE_STANDARD;
           ioctl(ctx->disp_fp,DISP_CMD_SET_ENHANCE_MODE,arg);                                       
           return  ioctl(ctx->disp_fp,DISP_CMD_ENHANCE_ON,arg);                                       
        } 
#else
	if(mode == 1)                                                                              
        {                                                                                          
	   ioctl(ctx->disp_fp,DISP_CMD_ENHANCE_DISABLE,arg);
	   arg[1] = DISP_ENHANCE_MODE_VIVID;
           ioctl(ctx->disp_fp,DISP_CMD_SET_ENHANCE_MODE ,arg);                                       
           return  ioctl(ctx->disp_fp,DISP_CMD_ENHANCE_ENABLE,arg);                                       
        }                                                                                          
        else                                                                                       
        {	                                                                                         
           return  ioctl(ctx->disp_fp,DISP_CMD_ENHANCE_DISABLE,arg);
        }
#endif
    }                               

    return 0;
}                                                                                            
                                                                                             
int _hwc_device_set_enhancedemomode(int disp, int mode)      
{ 
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
    unsigned long arg[4]={0};
#ifdef __LECACY_USED__ 
    __disp_rect_t window;                                                                
#else
    disp_window window;                                                                
#endif
    if(disp == HWC_DISPLAY_PRIMARY)
    { 
        arg[0] = 0;                                                                    
        window.x = 0;                                                                          
        window.y = 0;                                                                          

#ifdef __LECACY_USED__ 
        window.width = ioctl(ctx->disp_fp,DISP_CMD_SCN_GET_WIDTH,arg);                       
        window.height = ioctl(ctx->disp_fp,DISP_CMD_SCN_GET_HEIGHT,arg);                     
#else
	window.width = ioctl(ctx->disp_fp,DISP_CMD_GET_SCN_WIDTH,arg);                       
        window.height = ioctl(ctx->disp_fp,DISP_CMD_GET_SCN_HEIGHT,arg);                     
#endif
        if(mode == 1)                                                                              
        {                                                                                          
            window.width /= 2;                                                                  
            arg[1] = (unsigned long)&window;                                                   
            return ioctl(ctx->disp_fp,DISP_CMD_SET_ENHANCE_WINDOW,arg);                                       
        }                                                                                          
        else                                                                                       
        {	                                                                                         
            arg[1] = (unsigned long)&window;                                                   
            return ioctl(ctx->disp_fp,DISP_CMD_SET_ENHANCE_WINDOW,arg);                                       
        } 
    }                               

    return 0;                                                                                           
} 

int _hwc_device_set_output_mode(int disp, int out_type, int out_mode)
{
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;

    if (ctx->out_type[1] == out_type && ctx->out_mode[1] == out_mode) {
        return 0;
    }

    if (disp == HWC_DISPLAY_PRIMARY && ctx->hint_hdmi_mode != out_mode) {
        ctx->hint_hdmi_mode = out_mode;
        if (ctx->hdmi_hpd_active != 0) {
            ctx->hdmi_hpd = 0;

            usleep(1000 * 500);
            ctx->hdmi_hpd = 1;
        }
    }

    return 0;
}
