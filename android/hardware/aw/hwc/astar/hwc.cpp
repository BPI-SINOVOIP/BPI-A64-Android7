/*
 * Copyright (C) 2010 The Android Open Source Project
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
        name: "Sample hwcomposer module",
        author: "The Android Open Source Project",
        methods: &hwc_module_methods,
    }
};

/*****************************************************************************/

static void dump_layer(hwc_layer_1_t const* l,int pipe,bool usedfe) 
{
    static char const* compositionTypeName[] = {
                            "GLES",
                            "HWC",
                            "BACKGROUND",
                            "FB TARGET",
                            "UNKNOWN"};

    private_handle_t * handle = (private_handle_t *)l->handle;
    ALOGD(" %10s |  % 2d  | %s | %08x | %08x | %08x | %02x | %05x | %08x | [%7d,%7d,%7d,%7d] | [%5d,%5d,%5d,%5d] \n",
            compositionTypeName[l->compositionType],pipe,usedfe?"Yes":"No ",l->handle,l->hints, l->flags, l->transform, l->blending, handle==0?0:handle->format ,
            l->sourceCrop.left,
            l->sourceCrop.top,
            l->sourceCrop.right,
            l->sourceCrop.bottom,
            l->displayFrame.left,
            l->displayFrame.top,
            l->displayFrame.right,
            l->displayFrame.bottom);
}
static void dump_displays(size_t numDisplays,hwc_display_contents_1_t **displays)
{
	int disp, i,pipe;
    bool feused;
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    Layer_list_t* LayerTmp = NULL;
	if(Globctx->hwcdebug & LAYER_DUMP)
	{
		for(disp = 0; disp < numDisplays; disp++)
		{
		    hwc_display_contents_1_t *psDisplay = displays[disp];
        LayerTmp = (Layer_list_t*)Globctx->HwcLayerHead.next;    				
		    if(psDisplay)
		    {
		        ALOGD("\n\n\ndisp:%d  the framecount:%d \n    type    | pipe | fe  |  handle  |   hints  |   flags  | tr | blend |  format  |          source crop            |           frame            \n"
                                                         "------------+------+-----+----------+----------+----------+----+-------+----------+---------------------------------+--------------------------------\n", disp,Globctx->HWCFramecount);
                for(i = 0; i < psDisplay->numHwLayers; i++)
                {
                    hwc_layer_1_t *psLayer = &psDisplay->hwLayers[i];
                    if((psLayer->compositionType == 1 || psLayer->compositionType == 3) && (LayerTmp != (Layer_list_t*)(&(Globctx->HwcLayerHead))))
                    {
                        pipe = LayerTmp->pipe;
                        feused =LayerTmp->usedfe;
                        LayerTmp = (Layer_list_t*)LayerTmp->head.next;
                    }else{
                        feused = 0;
                        pipe = -1;
                    }
                    dump_layer(psLayer,pipe,feused);
                }
            }
        }
    }

}

static int hwc_blank(struct hwc_composer_device_1* dev, int disp, int blank)
{
    return 0;
}

static int hwc_setParameter(struct hwc_composer_device_1* dev, int cmd, int disp,
            int para0, int para1)
{
	int ret = 0;
	 switch(cmd)
	{
		case DISPLAY_CMD_SET3DMODE:
			ret = _hwc_device_set_3d_mode(disp, (__display_3d_mode)para0);
			break;
		case DISPLAY_CMD_SETBACKLIGHTMODE:
			ret = _hwc_device_set_backlight_mode(disp, para0);
			break;
		case DISPLAY_CMD_SETBACKLIGHTDEMOMODE:
			ret = _hwc_device_set_backlight_demomode(disp, para0);
			break;
		case DISPLAY_CMD_SETDISPLAYENHANCEMODE:
			ret = _hwc_device_set_enhancemode(disp, para0);
			break;
		case DISPLAY_CMD_SETDISPLAYENHANCEDEMOMODE:
			ret = _hwc_device_set_enhancedemomode(disp, para0);
			break;
		/*
		case DISPLAY_CMD_SETOUTPUTMODE:
			ret = _hwc_device_set_output_mode(disp, para0, para1, psPrivateData);
			break;
		*/
		default:
			break;
	}

	return ret;
}


static int hwc_getParameter(struct hwc_composer_device_1* dev, int cmd, int disp,
            int para0, int para1)
{
    return 0;
}

static void
resetDevice(SUNXI_hwcdev_context_t * ctx, size_t disp)
{
	size_t i = 0;
	for (i = 0; i < ctx->pipeCount[disp]; i++)
	{
		ctx->pipes[disp][i].pipeInUse = 0;
		ctx->pipes[disp][i].pipeType = 0;
		ctx->pipes[disp][i].assignedLayer = NULL;
		ctx->pipes[disp][i].assignedLayerZOrder = 0;
		ctx->pipes[disp][i].display_type = HWC_DISPLAY_PRIMARY;
	}
	hwcdev_reset_device(ctx, disp);
}

//Helper
static void reset_layer_type(int numDisplays, hwc_display_contents_1_t** displays) {
    int i = 0;
    size_t j = 0;
    
    for(i = 0; i < numDisplays; i++){
        hwc_display_contents_1_t *list = displays[i];
        // XXX:SurfaceFlinger no longer guarantees that this
        // value is reset on every prepare. However, for the layer
        // cache we need to reset it.
        // We can probably rethink that later on
        if (list && list->numHwLayers > 1) {
            for(j = 0; j < list->numHwLayers; j++) {
                if(list->hwLayers[j].compositionType != HWC_FRAMEBUFFER_TARGET)
                    list->hwLayers[j].compositionType = HWC_FRAMEBUFFER;
            }
        }
    }
}

int InitAddLayerTail(head_list_t* LayerHead,hwc_layer_1_t *psLayer, int Order,int pipe,bool feused)
{

	int i=3;
	Layer_list_t* LayerTmp = NULL;
	while((i--)&&(LayerTmp == NULL))
	{
		LayerTmp=(Layer_list_t* )calloc(1, sizeof(Layer_list_t));
		if(LayerTmp == NULL) {
	             ALOGE("InitAddLayerTail:calloc memory for LayerTmp fail !");
		     return 0;
		}
		memset(LayerTmp,0,sizeof(Layer_list_t));
	}
	
	LayerTmp->pslayer = psLayer;
	LayerTmp->Order = Order;
	LayerTmp->pipe = pipe;
	LayerTmp->usedfe= feused;
	head_list_t *tmp = LayerHead->next;
	while(tmp != LayerHead)
	{
		head_list_t *tmp2=tmp->next;
		if(Order > ((Layer_list_t *)tmp)->Order)
		{
			LayerTmp->head.next = tmp;
			LayerTmp->head.pre = tmp->pre;
			tmp->pre->next = &(LayerTmp->head);
			tmp->pre = &(LayerTmp->head);
			return 0;
		}
		tmp = tmp2;   
	}
	LayerTmp->head.next = tmp;
	LayerTmp->head.pre = tmp->pre;
	tmp->pre->next = &(LayerTmp->head);
	tmp->pre = &(LayerTmp->head);
	return 0;
}
int FreeLayerList(head_list_t * LayerHead)
{
	head_list_t *head,*next;
	head = LayerHead->next;
	while(head != LayerHead)
	{
		next = head->next;
		free(head);
		head = next;
	}
	LayerHead->next = LayerHead;
	LayerHead->pre = LayerHead;
	return 0;     
}

static void
resetHeadList(SUNXI_hwcdev_context_t *ctx)
{
	ctx->FBLayerHead.next = &(ctx->FBLayerHead);
	ctx->FBLayerHead.pre = &(ctx->FBLayerHead);
	ctx->HwcLayerHead.next = &(ctx->HwcLayerHead);
	ctx->HwcLayerHead.pre = &(ctx->HwcLayerHead);
}


					   
static
int hwc_prepare(hwc_composer_device_1_t *dev, size_t numDisplays,
					   hwc_display_contents_1_t **displays)
{
	int forceSoftwareRendering = 0, needFramebufferTargetLayer = 0;
	hwc_display_contents_1_t *psDisplay;
	size_t disp, i;
	SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
	int err = 0;//-EINVAL;

    reset_layer_type(numDisplays, displays);
    ctx->frame_num++;
    resetDevice(ctx, 0);
    resetDevice(ctx, 1);
    resetHeadList(ctx);
    ctx->force_sgx[0] &= (~HWC_FORCE_SGX_REASON_OTHERS);
    ctx->force_sgx[1] &= (~HWC_FORCE_SGX_REASON_OTHERS);
    
    for(disp = 0; disp < numDisplays; disp++)
    {        
        ctx->show_black[disp] = 0;
        needFramebufferTargetLayer = 0;
        psDisplay = displays[disp];

    	if(!psDisplay)
    	{
    		ALOGV("%s: display[%d] was unexpectedly NULL",
    								__func__, disp);
    		continue;
    	}

        if(psDisplay->outbuf != NULL || psDisplay->outbufAcquireFenceFd != 0)
        {
            if (psDisplay->retireFenceFd >= 0) {
                close(psDisplay->retireFenceFd);
                psDisplay->retireFenceFd = -1;
            }
            if (psDisplay->outbuf != NULL) {
                psDisplay->outbuf = NULL;
            }
            if (psDisplay->outbufAcquireFenceFd >= 0) {
                close(psDisplay->outbufAcquireFenceFd);
                psDisplay->outbufAcquireFenceFd = -1;
            }
            ALOGV("%s: Virtual displays are not supported",
    								__func__);
        }

        if(disp > 1)
        {
            break;
        }
				
        if(disp == 1)
        {
            if(!ctx->hdmi_hpd)
            {
                continue;
            }
            if(ctx->frame[disp].right <= 0 || ctx->frame[disp].bottom <= 0)
            {
                continue;
            }
        }

    	if(psDisplay->numHwLayers < 2)
    	{
    		ALOGV("%s: display[%d] numHwLayer:%d less then 2",
    								__func__, disp, psDisplay->numHwLayers);

            ctx->show_black[disp] = 1;
            forceSoftwareRendering = 1;
    	}

    	for(i = 0; i < psDisplay->numHwLayers-1; i++)
    	{
    		hwc_layer_1_t *psLayer = &psDisplay->hwLayers[i];
    		
    		if(psLayer->handle == NULL)
    		{
    		    forceSoftwareRendering = 1;
    		    break;
    		}
        }

        if(disp == HWC_DISPLAY_EXTERNAL && (ctx->cur_3d_mode[disp] == DISPLAY_3D_LEFT_RIGHT_HDMI || ctx->cur_3d_mode[disp] == DISPLAY_3D_TOP_BOTTOM_HDMI))
        {
            //ctx->use_fb[0] = 1;
            //forceSoftwareRendering = 1;
        }

    	if (forceSoftwareRendering)
    	{
    		needFramebufferTargetLayer = 1;
    		ctx->force_sgx[disp] = HWC_FORCE_SGX_REASON_OTHERS;
    	}
    	else
    	{
    	    needFramebufferTargetLayer = 0;
    	}

    recalculate_pipe_assignments:
			resetDevice(ctx, 0);
			resetDevice(ctx, 1);
			FreeLayerList(&ctx->FBLayerHead);
			FreeLayerList(&ctx->HwcLayerHead); 
    	if (needFramebufferTargetLayer == 1)
    	{
    		hwc_layer_1_t *psFramebufferTargetLayer;
    		size_t pipeNo = 0;
    		size_t assignmentSucceeded = 0;
    		ALOGV("%s: Allocating framebuffer_target", __func__);

    		psFramebufferTargetLayer = &psDisplay->hwLayers[psDisplay->numHwLayers-1];
    		if (psFramebufferTargetLayer->compositionType != HWC_FRAMEBUFFER_TARGET)
    		{
    			ALOGD("%s: display[%d] FRAMEBUFFER_TARGET layer not last layer",
    			                        __func__, disp);
    			continue;
    		}
    		
    		for (pipeNo = 0; pipeNo < ctx->pipeCount[disp]; pipeNo++)
    		{
    			if (hwcdev_try_to_assign_pipe(ctx, &ctx->pipes[disp][pipeNo], psFramebufferTargetLayer, psDisplay->numHwLayers-1, disp)
    				== ASSIGN_OK)
    			{
    				assignmentSucceeded = 1;
    				break;
    			}
    		}
    		if (assignmentSucceeded == 0)
    		{
    			ALOGD("%s: display[%d] FRAMEBUFFER_TARGET layer failed to be assigned to pipe",
    			                        __func__, disp);
    			/* FIXME: In some cases the handle of the FRAMEBUFFER_TARGET layer is null
    			 * (Seems to be caused by android not expecting there to be a software layer
    			 * required, e.g. boot animation when forcing SW rendering) which causes the
    			 * assignment to fail. In these cases, don't do anything, but don't set the
    			 * error as that causes everything to fail */
    			continue;
    		}
    	}

	FreeLayerList(&ctx->FBLayerHead);
    	for(i = 0; i < psDisplay->numHwLayers-1; i++)
    	{
    		hwc_layer_1_t *psLayer = &psDisplay->hwLayers[i];
    		size_t pipeNo;
    		size_t assignmentSucceeded = 0;

    		switch(psLayer->compositionType)
    		{
    			case HWC_BACKGROUND:
    			case HWC_FRAMEBUFFER:
    			case HWC_OVERLAY:
    			{
    				if (forceSoftwareRendering)
    				{
    					psLayer->compositionType = HWC_FRAMEBUFFER;
    					break;
    				}
    				for (pipeNo = 0; pipeNo < ctx->pipeCount[disp]; pipeNo++)
    				{
    					if (hwcdev_try_to_assign_pipe(ctx, &ctx->pipes[disp][pipeNo], psLayer, i, disp)
    						== ASSIGN_OK)
    					{
    						psLayer->compositionType = HWC_OVERLAY;
    						assignmentSucceeded = 1;
    						InitAddLayerTail(&ctx->HwcLayerHead, psLayer,i, ctx->de_pipe_used[disp]-1,ctx->used_fe);
    						break;
    					}
    				}

    				if (assignmentSucceeded == 0)
    				{
							InitAddLayerTail(&ctx->FBLayerHead, psLayer,i, 0,0); 
    					if (needFramebufferTargetLayer == 0)
    					{
								FreeLayerList(&ctx->FBLayerHead);
    						needFramebufferTargetLayer = 1;
    						goto recalculate_pipe_assignments;
    					}
    					psLayer->compositionType = HWC_FRAMEBUFFER;
    				}

    				break;
    			}
    			default:
    				ALOGD("%s: Saw unsupported "
    										"compositionType=%d", __func__,
    										psLayer->compositionType);
    				continue;
    		}
    	}
    }
    dump_displays(numDisplays, displays);
    
    FreeLayerList(&ctx->FBLayerHead);  
    FreeLayerList(&ctx->HwcLayerHead);  
err_out:
	return err;
}

static int hwc_set(hwc_composer_device_1_t *dev,
        size_t numDisplays, hwc_display_contents_1_t** displays)
{
    int ret = 0;
	unsigned long arg[4]={0};
	int releaseFenceFd;

	SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;

	hwcdev_generate_private_data(ctx);

    arg[0] = 0;
    arg[1] = (unsigned int)(ctx->pvPrivateData);
    releaseFenceFd = ioctl(ctx->disp_fp,DISP_CMD_HWC_COMMIT,(unsigned long)arg);
    ctx->HWCFramecount++;
    
    for (size_t i=0 ; i<displays[0]->numHwLayers ; i++) 
    {
		 if(displays[0]->hwLayers[i].acquireFenceFd >= 0)
    	{

	    	//ALOGD("layerAcqrureFd[%d] = %d\n",i,displays[0]->hwLayers[i].acquireFenceFd);
            close(displays[0]->hwLayers[i].acquireFenceFd);
            displays[0]->hwLayers[i].acquireFenceFd = -1;
		}
		if((displays[0]->hwLayers[i].compositionType == HWC_OVERLAY) || ((displays[0]->hwLayers[i].compositionType == HWC_FRAMEBUFFER_TARGET)))
		{
			
			if(releaseFenceFd >= 0)
			{
				displays[0]->hwLayers[i].releaseFenceFd =dup(releaseFenceFd);
			}
			else
			{

				displays[0]->hwLayers[i].releaseFenceFd = -1;
			}
			
		}	
		else
		{

			displays[0]->hwLayers[i].releaseFenceFd = -1;
		}
        
    }
    if(releaseFenceFd >= 0)
    {
        close(releaseFenceFd);
		releaseFenceFd = -1;
    }
//FIX:need free the private data
     hwcdev_free_private_data(ctx);

	if(ctx->force_sgx[0] & HWC_FORCE_SGX_REASON_STILL0)
   {
	   ctx->force_sgx[0] &= (~HWC_FORCE_SGX_REASON_STILL0);
   }
   else if(ctx->force_sgx[0] & HWC_FORCE_SGX_REASON_STILL1)
   {
	   ctx->force_sgx[0] &= (~HWC_FORCE_SGX_REASON_STILL1);
   }

    return ret;
}

static int hwc_eventControl(struct hwc_composer_device_1* dev, int disp,
            int event, int enabled)
{
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
    unsigned long               arg[4]={0};

    switch (event) {
    case HWC_EVENT_VSYNC:
        arg[0] = 0;
        arg[1] = 1;//enabled;
        ioctl(ctx->disp_fp, DISP_CMD_VSYNC_EVENT_EN,(unsigned long)arg);
        ctx->vsync_en = enabled;
        return 0;
    }

    return -EINVAL;
}

static void hwc_register_procs(struct hwc_composer_device_1* dev,
            hwc_procs_t const* procs)
{
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
    ctx->psHwcProcs = const_cast<hwc_procs_t *>(procs);
}


static int hwc_getDisplayConfigs(struct hwc_composer_device_1 *dev,
        int disp, uint32_t *configs, size_t *numConfigs)
{
    if(disp == HWC_DISPLAY_PRIMARY)
    {
        if (*numConfigs == 0)
            return 0;

        if (disp == HWC_DISPLAY_PRIMARY) {
            configs[0] = 0;
            *numConfigs = 1;
            return 0;
        } else if (disp == HWC_DISPLAY_EXTERNAL) {
            configs[0] = 0;
            *numConfigs = 1;
            return 0;
        }
    }

    return -EINVAL;
}

static int32_t hwc_attribute(struct hwc_composer_device_1 *pdev,
        const uint32_t attribute)
{
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
    int refreshRate, xdpi, ydpi, vsync_period;
    struct fb_var_screeninfo info;

    if (ioctl(ctx->fb_fp[0], FBIOGET_VSCREENINFO, &info) == -1) {
        ALOGE("FBIOGET_VSCREENINFO ioctl failed: %s", strerror(errno));
        return -1;
    }

    if(info.pixclock){

	    refreshRate = 1000000000000LLU /
		(
		 uint64_t( info.upper_margin + info.lower_margin + info.yres )
		 * ( info.left_margin  + info.right_margin + info.xres )
		 * info.pixclock
		);

   }
    else
	{
		ALOGW("invalid refresh rate, assuming 60 Hz");
		refreshRate = 60;
	}
	//Out GPU is not fully with 63 fps, but the parameter claim it is fps, so hardcode it
	refreshRate = 60;

    if(info.width == 0)
    {
        xdpi = 160000;
    }
    else
    {
        xdpi = 1000 * (info.xres * 25.4f) / info.width;
    }
    if(info.height == 0)
    {
        ydpi = 160000;
    }
    else
    {
        ydpi = 1000 * (info.yres * 25.4f) / info.height;
    }
    vsync_period  = 1000000000 / refreshRate;

    switch(attribute) {
    case HWC_DISPLAY_VSYNC_PERIOD:
        return vsync_period;

    case HWC_DISPLAY_WIDTH:
        return info.xres;

    case HWC_DISPLAY_HEIGHT:    
        return info.yres;

    case HWC_DISPLAY_DPI_X:
        return xdpi;

    case HWC_DISPLAY_DPI_Y:
        return ydpi;

    default:
        ALOGE("unknown display attribute %u", attribute);
        return -EINVAL;
    }
}

static int hwc_getDisplayAttributes(struct hwc_composer_device_1 *dev,
        int disp, uint32_t config, const uint32_t *attributes, int32_t *values)
{
    for (int i = 0; attributes[i] != HWC_DISPLAY_NO_ATTRIBUTE; i++) 
    {
        if (disp == HWC_DISPLAY_PRIMARY)
        {
            values[i] = hwc_attribute(dev, attributes[i]);
        }
        else 
        {
            ALOGE("unknown display type %u", disp);
            return -EINVAL;
        }
    }
    return 0;
}

static int hwc_device_close(struct hw_device_t *dev)
{
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
    psHwcDevice->common.version  = HWC_DEVICE_API_VERSION_1_1;  //0
    psHwcDevice->common.module   = const_cast<hw_module_t*>(module);
    psHwcDevice->common.close    = hwc_device_close;
    
    psHwcDevice->prepare         = hwc_prepare;
    psHwcDevice->set             = hwc_set;
    psHwcDevice->setParameter    = hwc_setParameter;
    psHwcDevice->getParameter    = hwc_getParameter;
    psHwcDevice->registerProcs   = hwc_register_procs;
    psHwcDevice->eventControl	= hwc_eventControl;
	psHwcDevice->blank			= hwc_blank;
	psHwcDevice->getDisplayConfigs = hwc_getDisplayConfigs;
	psHwcDevice->getDisplayAttributes = hwc_getDisplayAttributes;

    *device = psHwDevice;

	hwcdev_create_device();

    return err;
}


