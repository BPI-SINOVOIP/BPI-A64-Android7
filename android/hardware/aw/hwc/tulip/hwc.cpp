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
//#define LOG_NDEBUG 0

#include "hwc.h"
#include "cutils/properties.h"


static int Framecount = 0;
/*****************************************************************************/

static int hwc_device_open(const struct hw_module_t* module, const char* name,
        struct hw_device_t** device);

static struct hw_module_methods_t hwc_module_methods = {
    open: hwc_device_open
};

hwc_module_t HAL_MODULE_INFO_SYM = {
    common: {
        tag: HARDWARE_MODULE_TAG,
        version_major: 1,
        version_minor: 0,
        id: HWC_HARDWARE_MODULE_ID,
        name: "Sunxi hwcomposer module",
        author: "Allwinner Tech",
        methods: &hwc_module_methods,
        dso: 0,
        reserved: {0},
    }
};

/*****************************************************************************/



static int hwc_blank(struct hwc_composer_device_1* dev, int disp, int blank)
{
    HWC_UNREFERENCED_PARAMETER(dev);
    HWC_UNREFERENCED_PARAMETER(disp);
    HWC_UNREFERENCED_PARAMETER(blank);

	SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
	if(!blank)
	{
		Globctx->unblank_flag = 1;
		return 0;
	}
	unsigned long               arg[4]={0};
    DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[disp];
    if(PsDisplayInfo->VirtualToHWDisplay !=  -EINVAL)
    {
	    arg[0] = PsDisplayInfo->VirtualToHWDisplay;
	    arg[1] = blank;
	    if(ioctl(Globctx->DisplayFd, DISP_BLANK, (unsigned long)arg) != 0)
		    return -1;
    }
    return 0;
}

static int hwc_setParameter(struct hwc_composer_device_1* dev, int cmd, int disp,
            int para0, int para1)
{
	int ret = 0;
	HWC_UNREFERENCED_PARAMETER(dev);
	switch(cmd)
	{
		case DISPLAY_CMD_SET3DMODE:
			ret = _hwc_device_set_3d_mode(disp, (__display_3d_mode)para0);
			break;
		case DISPLAY_CMD_SETBACKLIGHTMODE:
			ret = _hwc_device_set_backlight(disp, para0, 0);
			break;
		case DISPLAY_CMD_SETBACKLIGHTDEMOMODE:
			ret = _hwc_device_set_backlight(disp, para0, 1);
			break;
		case DISPLAY_CMD_SETDISPLAYENHANCEMODE:
			ret = _hwc_device_set_enhancemode(disp, para0, 0);
			break;
		case DISPLAY_CMD_SETDISPLAYENHANCEDEMOMODE:
			ret = _hwc_device_set_enhancemode(disp, para0, 1);
			break;		    
		case DISPLAY_CMD_SETOUTPUTMODE:
			ret = _hwc_device_set_output_mode(disp, para0, para1);
			break;
        case DISPLAY_CMD_HDMIPERSENT:
            
            ret = _hwc_set_persent(disp, para0, para1);
            break;
		default:
			break;
	}

	return ret;
}


static int hwc_getParameter(struct hwc_composer_device_1* dev, int cmd, int disp,
            int para0, int para1)
{
    HWC_UNREFERENCED_PARAMETER(dev);
    HWC_UNREFERENCED_PARAMETER(cmd);
    HWC_UNREFERENCED_PARAMETER(disp);
    HWC_UNREFERENCED_PARAMETER(para0);
    HWC_UNREFERENCED_PARAMETER(para1);
    return 0;
}



static
int hwc_prepare(hwc_composer_device_1_t *dev, size_t numDisplays,
					   hwc_display_contents_1_t **displays)
{
    HWC_UNREFERENCED_PARAMETER(dev);
    sunxi_prepare(displays, numDisplays);

	return 0;
}

static int hwc_set(hwc_composer_device_1_t *dev,
        size_t numDisplays, hwc_display_contents_1_t** displays)
{
    int ret = 0;
    HWC_UNREFERENCED_PARAMETER(dev);

    ret = sunxi_set(displays, numDisplays);
    return ret;
}

static int hwc_eventControl(struct hwc_composer_device_1* dev, int disp,
            int event, int enabled)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long               arg[4]={0};

    HWC_UNREFERENCED_PARAMETER(dev);
    
    if(disp == 0)
    {
        DisplayInfo  *PsDisplayInfo = &Globctx->SunxiDisplay[disp];
        if(PsDisplayInfo->VirtualToHWDisplay != -EINVAL)
        {
            switch (event) 
            {
                case HWC_EVENT_VSYNC:
                    arg[0] = PsDisplayInfo->VirtualToHWDisplay;
                    arg[1] = !!enabled;
                    ioctl(Globctx->DisplayFd, DISP_VSYNC_EVENT_EN,(unsigned long)arg);
                    PsDisplayInfo->VsyncEnable = (!!enabled);
                    return 0;
                default:
                    return -EINVAL;
            }
        }
    }

    return -EINVAL;
}

static void hwc_register_procs(struct hwc_composer_device_1* dev,
        hwc_procs_t const* procs)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;

    HWC_UNREFERENCED_PARAMETER(dev);
    Globctx->psHwcProcs = const_cast<hwc_procs_t *>(procs);
}

static int hwc_getDisplayConfigs(struct hwc_composer_device_1 *dev,
        int disp, uint32_t *configs, size_t *numConfigs)
{

	SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[disp];

    HWC_UNREFERENCED_PARAMETER(dev);
    *numConfigs = 1;
    *configs = 0;
    return PsDisplayInfo->VirtualToHWDisplay == -EINVAL;
        
}

static int32_t hwc_get_attribute(const uint32_t attribute,
         uint32_t disp)
{
	SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[disp];

    if(PsDisplayInfo->VirtualToHWDisplay != -EINVAL)
    {
	    switch(attribute) {
        case HWC_DISPLAY_VSYNC_PERIOD: 
            return PsDisplayInfo->DisplayVsyncP;
            
        case HWC_DISPLAY_WIDTH:
#ifdef FORCE_SET_RESOLUTION
            return FORCE_RESOLUTION_WIDTH;
#else
            return PsDisplayInfo->VarDisplayWidth;
#endif
        case HWC_DISPLAY_HEIGHT:
#ifdef FORCE_SET_RESOLUTION
            return FORCE_RESOLUTION_HEIGHT;
#else
            return PsDisplayInfo->VarDisplayHeight;
#endif

        case HWC_DISPLAY_DPI_X:
            return PsDisplayInfo->DiplayDPI_X;

        case HWC_DISPLAY_DPI_Y:
            return PsDisplayInfo->DiplayDPI_Y;

//	    case HWC_DISPLAY_IS_SECURE:
//            if(PsDisplayInfo->DisplayType != DISP_OUTPUT_TYPE_HDMI)
//            {
//                return 1;
//            }
//		    return 0;

        default:
            ALOGE("unknown display attribute %u", attribute);
            return -EINVAL;
	    }
    }
    ALOGE("No hareware display ");
    return -EINVAL;
}

static int hwc_getDisplayAttributes(struct hwc_composer_device_1 *dev,
        int disp, uint32_t config, const uint32_t *attributes, int32_t *values)
{
    HWC_UNREFERENCED_PARAMETER(dev);
    HWC_UNREFERENCED_PARAMETER(config);
    
    for (int i = 0; attributes[i] != HWC_DISPLAY_NO_ATTRIBUTE; i++) 
    {
        if (disp <=2)
        {
            values[i]=hwc_get_attribute(attributes[i],disp);
            
        }else
        {
            ALOGE("unknown display type %u", disp);
            return -EINVAL;
        }
    }
    return 0;
}

static int hwc_device_close(struct hw_device_t *dev)
{
    HWC_UNREFERENCED_PARAMETER(dev);
    
    hwc_destroy_device();
    return 0;
}

/*****************************************************************************/

static int hwc_device_open(const struct hw_module_t* module, const char* name,
        struct hw_device_t** device)
{
	hwc_composer_device_1_t *psHwcDevice;
	hw_device_t *psHwDevice;
	int err = 0;
	
    if (strcmp(name, HWC_HARDWARE_COMPOSER)) 
    {
        return -EINVAL;
    }
    
	psHwcDevice = (hwc_composer_device_1_t *)malloc(sizeof(hwc_composer_device_1_t));
	if(!psHwcDevice)
	{
		ALOGD("%s: Failed to allocate memory", __func__);
		return -ENOMEM;
	}

	memset(psHwcDevice, 0, sizeof(hwc_composer_device_1_t));
    psHwDevice = (hw_device_t *)psHwcDevice;

    psHwcDevice->common.tag      = HARDWARE_DEVICE_TAG;
#ifdef HWC_1_3    
    psHwcDevice->common.version  = HWC_DEVICE_API_VERSION_1_3;
#else
    psHwcDevice->common.version  = HWC_DEVICE_API_VERSION_1_1;
#endif
    psHwcDevice->common.module   = const_cast<hw_module_t*>(module);
    psHwcDevice->common.close    = hwc_device_close;
    
    psHwcDevice->prepare         = hwc_prepare;
    psHwcDevice->set             = hwc_set;
    psHwcDevice->setParameter    = hwc_setParameter;
    psHwcDevice->getParameter    = hwc_getParameter;
    psHwcDevice->registerProcs   = hwc_register_procs;
    psHwcDevice->eventControl	 = hwc_eventControl;
	psHwcDevice->blank			 = hwc_blank;
	psHwcDevice->getDisplayConfigs = hwc_getDisplayConfigs;
	psHwcDevice->getDisplayAttributes = hwc_getDisplayAttributes;
    /*
        open the hardware cursor ,you must modify the setIsCursorLayerHint() in Hwcomposer.cpp for 
        HWC_DEVICE_API_VERSION_1_4 to HWC_DEVICE_API_VERSION_1_1
    */
    psHwcDevice->setCursorPositionAsync = hwc_set_cursor_async;
    *device = psHwDevice;

	hwc_create_device();

    return err;
}


