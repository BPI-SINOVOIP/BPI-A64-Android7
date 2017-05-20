/*-------------------------------------------------------------------------
    
-------------------------------------------------------------------------*/
/*-------------------------------------------------------------------------
    
-------------------------------------------------------------------------*/
/*-------------------------------------------------------------------------
    
-------------------------------------------------------------------------*/
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
        name: "Sample hwcomposer module",
        author: "The Android Open Source Project",
        methods: &hwc_module_methods,
        dso:NULL,
        reserved:{0},
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

    IMG_native_handle_t* handle = (IMG_native_handle_t*)l->handle;
    ALOGD(" %10s |  % 2d  | %s | %08x | %08x | %08x | %02x | %05x | %08x | [%7d,%7d,%7d,%7d] | [%5d,%5d,%5d,%5d] \n",
            compositionTypeName[l->compositionType],pipe,usedfe?"Yes":"No ",(unsigned int)l->handle,l->hints, l->flags, l->transform, l->blending, handle==0?0:handle->iFormat ,
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
		for(disp = 0; disp < (int)numDisplays; disp++)
		{
		    hwc_display_contents_1_t *psDisplay = displays[disp];
            LayerTmp = (Layer_list_t*)Globctx->HwcLayerHead[disp].pre;
		    if(psDisplay)
		    {
		        ALOGD("\n\n\ndisp:%d  the framecount:%d \n    type    | pipe | fe  |  handle  |   hints  |   flags  | tr | blend |  format  |          source crop            |           frame            \n"
                                                         "------------+------+-----+----------+----------+----------+----+-------+----------+---------------------------------+--------------------------------\n", disp,Globctx->HWCFramecount);
                for(i = 0; i < (int)psDisplay->numHwLayers; i++)
                {
                    hwc_layer_1_t *psLayer = &psDisplay->hwLayers[i];
                    if(psLayer->compositionType == 1 || psLayer->compositionType == 3)
                    {
                        if(LayerTmp != (Layer_list_t*)&Globctx->HwcLayerHead[disp])
                        {
                            pipe = LayerTmp->pipe;
                            feused =LayerTmp->usedfe;
                            LayerTmp = (Layer_list_t*)LayerTmp->head.pre;
                        }
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
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
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
    return 0;
}

static inline void ResetPipeInfo(HwcDevCntContext_t * ctx,int start,int end)
{
    while(start < end)
     {
        ctx->PipeRegion[start].left = 10000;
        ctx->PipeRegion[start].top = 10000;
        ctx->PipeRegion[start].right = 0;
        ctx->PipeRegion[start].bottom = 0;
        start++;
     }
}

static bool inline ReCountPresent(DisplayInfo *PsDisplayInfo )
{

    if(PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_HDMI)
    {
        if(PsDisplayInfo->DisplayPersentWT >= 90 && PsDisplayInfo->DisplayPersentWT <= 100
            && PsDisplayInfo->DisplayMode != DISP_TV_MOD_3840_2160P_25HZ
            && PsDisplayInfo->DisplayMode != DISP_TV_MOD_3840_2160P_24HZ
            && PsDisplayInfo->DisplayMode != DISP_TV_MOD_3840_2160P_30HZ
            && PsDisplayInfo->Current3DMode == DISPLAY_2D_ORIGINAL )
        {
            PsDisplayInfo->DisplayPersentW = PsDisplayInfo->DisplayPersentWT;
            
        }else{
            PsDisplayInfo->DisplayPersentW = 100;
        }
        
        if(PsDisplayInfo->DisplayPersentHT >= 90 && PsDisplayInfo->DisplayPersentHT <= 100
            && PsDisplayInfo->DisplayMode != DISP_TV_MOD_3840_2160P_25HZ
            && PsDisplayInfo->DisplayMode != DISP_TV_MOD_3840_2160P_24HZ
            && PsDisplayInfo->DisplayMode != DISP_TV_MOD_3840_2160P_30HZ
            && PsDisplayInfo->Current3DMode == DISPLAY_2D_ORIGINAL )
        {
            PsDisplayInfo->DisplayPersentH = PsDisplayInfo->DisplayPersentHT;
            
        }else{
            PsDisplayInfo->DisplayPersentH = 100;
        }

        if(PsDisplayInfo->DisplayPersentW != 100 || PsDisplayInfo->DisplayPersentH != 100)
        {
            return 1;
        }
    }else{
        PsDisplayInfo->DisplayPersentW = 100;
        PsDisplayInfo->DisplayPersentH = 100; 
    }
    return 0;
}

static void
resetGlobDevice(SUNXI_hwcdev_context_t * Globctx)
{
    int i;
    setup_dispc_data_t* DisplayData=Globctx->pvPrivateData;
    Globctx->GloFEisUsedCnt = 0;
    
	for (i = 0; i < NUMBEROFDISPLAY; i++)
	{
		DisplayData->layer_num[i] = 0;
        memset(DisplayData->hConfigData,-1,12*sizeof(int));
        int j;
        for(j = 0; j < DISPLAY_MAX_LAYER_NUM; j++)
        {
            memset(&(DisplayData->layer_info[i][j]),0,sizeof(disp_layer_info));
            DisplayData->layer_info[i][j].mode = DISP_LAYER_WORK_MODE_NORMAL;
        }
        Globctx->HwcLayerHead[i].next = &(Globctx->HwcLayerHead[i]);
        Globctx->HwcLayerHead[i].pre = &(Globctx->HwcLayerHead[i]);
        if(Globctx->SunxiDisplay[i].VirtualToHWDisplay != -1)
        {
            if( ReCountPresent(&Globctx->SunxiDisplay[i]))
            {
                Globctx->GloFEisUsedCnt++;
            }
            if(Globctx->layer0usfe)
            {
                Globctx->GloFEisUsedCnt++;
            }
        }
	}
}

static void
resetLocalCnt(HwcDevCntContext_t * ctx,int usedfb)
{

    ResetPipeInfo(ctx,0,NUMBEROFPIPE);

    ctx->FEisUsedCnt = 0;
    ctx->HwLayerCnt = 0;
    ctx->HwPipeUsedCnt = 0;
    ctx->UsedFB = usedfb;
    ctx->FBLayerHead.next = &(ctx->FBLayerHead);
    ctx->FBLayerHead.pre = &(ctx->FBLayerHead);  
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

static inline  void ResetLayerInfo(disp_layer_info* LayerInfo)
{
    memset(LayerInfo,0,sizeof(disp_layer_info));
    LayerInfo->mode = DISP_LAYER_WORK_MODE_NORMAL;
}

static inline int CntOfLyaer(SUNXI_hwcdev_context_t * ctx)
{
    int i=0, NumOfLayer = 0;
    while(i<NUMBEROFDISPLAY)
    {
       NumOfLayer += ctx->pvPrivateData->layer_num[i];
       i++;
    }
    return NumOfLayer;
}

static unsigned int  CntOfLyaerMem(hwc_layer_1_t *psLayer)
{
    unsigned int hight,width;
    IMG_native_handle_t* handle = (IMG_native_handle_t*)psLayer->handle;
    if(handle->iFormat == HAL_PIXEL_FORMAT_YV12 || handle->iFormat == HAL_PIXEL_FORMAT_YCrCb_420_SP)
    {
       width = ALIGN(handle->iWidth, YV12_ALIGN);
    }else{
       width = ALIGN(handle->iWidth, HW_ALIGN);
    }
    
    return handle->uiBpp * width * handle->iHeight;
    
}

static void reset_layer_type(hwc_display_contents_1_t* displays) 
{
    unsigned int j = 0;
    hwc_display_contents_1_t *list = displays;
    if (list && list->numHwLayers > 1) 
    {
        for(j = 0; j < list->numHwLayers; j++) 
        {
            if(list->hwLayers[j].compositionType != HWC_FRAMEBUFFER_TARGET)
            {
               list->hwLayers[j].compositionType = HWC_FRAMEBUFFER;
            }
        }
    }
}

static
int hwc_prepare(hwc_composer_device_1_t *dev, size_t numDisplays,
					   hwc_display_contents_1_t **displays)
{
	int forceSoftwareRendering = 1;
	hwc_display_contents_1_t *psDisplay;
	size_t disp, i;
	SUNXI_hwcdev_context_t *globctx = &gSunxiHwcDevice;
    HwcDevCntContext_t CntInfo;
    HwcDevCntContext_t *Localctx = &CntInfo;
	int err = 0;
    resetGlobDevice(globctx);
    int ret = 0;
    unsigned long arg[4] = {0};
    
    for(disp = 0; disp < numDisplays; disp++)
    {
       
        psDisplay = displays[disp];
        reset_layer_type(psDisplay);
        resetLocalCnt(Localctx,0);
        
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
            if(globctx->SunxiDisplay[disp].VirtualToHWDisplay == -1)
            {
            	ALOGV("ctx->hdmi_hpd is NULL");
                continue;
            }
        }
    	if(psDisplay->numHwLayers < 2)
    	{
    		ALOGV("%s: display[%d] numHwLayer:%d less then 2",
    								__func__, disp, psDisplay->numHwLayers);
            forceSoftwareRendering = 1;
    	}
        for(i = 0; i < psDisplay->numHwLayers; i++)
	    {
	    	hwc_layer_1_t *psLayer = &psDisplay->hwLayers[i];
	    		
	    	if(psLayer->handle == NULL)
	    	{
	    		forceSoftwareRendering = 1;
	    		break;
	    	}
        }

        ret = ioctl(globctx->DisplayFd,DISP_CMD_HWC_GET_DISP_READY,(unsigned long)arg);
        if(ret == 0 || globctx->bDisplayReady == 0)
        {
            ALOGV("%s:Display is not ready yet!",__func__);
            forceSoftwareRendering = 1;
        }
        globctx->bDisplayReady = ret;

    	if (forceSoftwareRendering)
    	{
           hwc_layer_1_t *psLayer = &psDisplay->hwLayers[psDisplay->numHwLayers-1];
           if(HwcTrytoAssignLayer(Localctx, psLayer, disp, 0) != ASSIGN_OK)
           {
                ALOGE("Use GPU composite FB failed ");
                continue;
           }
    	}else{
            int theFBCnt = 0;
            int SetOrder = 0;
            unsigned int SizeOfMem = 0;
            HwcPipeAssignStatusType AssignStatus;
            int NeedReAssignedLayer = 0;
            hwc_layer_1_t *psLayer; 
            
ReAssignedLayer:
            
            reset_layer_type(psDisplay);
    	    for(i = 0; i < psDisplay->numHwLayers; i++)
    	    {
    		    psLayer = &psDisplay->hwLayers[i];
                if(i >= psDisplay->numHwLayers-1)
                {
                    if(Localctx->UsedFB || SizeOfMem > CntOfLyaerMem(&psDisplay->hwLayers[psDisplay->numHwLayers-1]))
                    {
                        if(globctx->ForceGPUComp)
                        {
                            reset_layer_type(psDisplay);
                            SetOrder = 0;
                            FreeLayerList(&Localctx->FBLayerHead);
                            FreeLayerList(&(globctx->HwcLayerHead[disp]));
                            resetLocalCnt(Localctx, ASSIGN_FB_PIPE);
                        }
                    }else{
                        break;
                    }
                }
                AssignStatus = HwcTrytoAssignLayer(Localctx,psLayer, disp, SetOrder);
                if(AssignStatus == ASSIGN_NO_DISP)
                {
                    continue;
                }
    			if (AssignStatus == ASSIGN_OK)
    			{
                    if(psLayer->compositionType == HWC_FRAMEBUFFER)
                    {
                        SetOrder++;
                        psLayer->compositionType = HWC_OVERLAY;
                        if(globctx->ForceGPUComp && !Localctx->UsedFB)
                        {
                            SizeOfMem += CntOfLyaerMem(psLayer);
                        }    
                    }
    			}else{
    			    if(NeedReAssignedLayer == 0)
    			    {
                        NeedReAssignedLayer++;
                        SetOrder = 0;
                        SizeOfMem = 0;
                        globctx->GloFEisUsedCnt -= Localctx->FEisUsedCnt;
                        FreeLayerList(&(globctx->HwcLayerHead[disp]));
                        FreeLayerList(&Localctx->FBLayerHead);
                        resetLocalCnt(Localctx, ASSIGN_FB_PIPE);
                        goto ReAssignedLayer;
    			    }
    		    }
    	    }
          FreeLayerList(&Localctx->FBLayerHead);  
        }
    }   
    dump_displays(numDisplays, displays);
err_out:
	return err;
}

static int hwc_set(hwc_composer_device_1_t *dev,
        size_t numDisplays, hwc_display_contents_1_t** displays)
{
    int ret = 0;
	unsigned long arg[4] = {0};
	int releaseFenceFd = -1;
	size_t disp, i;
	hwc_display_contents_1_t *psDisplay;
	SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    head_list_t *head,*next;
    int *Fds;
    for(disp = 0;disp < numDisplays;disp++)
    {   

        psDisplay = displays[disp];

    	if(!psDisplay)
    	{
    		ALOGV("%s: display[%d] was unexpectedly NULL",
    								__func__, disp);
    		continue;
    	}
        int FdCnt = 0;
        head = Globctx->HwcLayerHead[disp].pre;
        while(head != &(Globctx->HwcLayerHead[disp]))
        {
            hwc_layer_1_t *psLayer = ((Layer_list_t *)head)->pslayer;
            Fds = (int *)(Globctx->pvPrivateData->hConfigData);
            int pipe = ((Layer_list_t *)head)->pipe;
            int Order = ((Layer_list_t *)head)->Order;
            if(psLayer->acquireFenceFd >= 0)
            {
                *(Fds+FdCnt) = psLayer->acquireFenceFd;
                FdCnt++;
            }
            if(HwcSetupLayer(Globctx,psLayer,Order,disp,pipe) == -1)
            {
                break;
            }
            head = head->pre;
        }
        FreeLayerList(&(Globctx->HwcLayerHead[disp]));
           
    }
    if(Globctx->DetectError == 0)
    {
        arg[0] = 0;
        arg[1] = (unsigned int)(Globctx->pvPrivateData);
        releaseFenceFd = ioctl(Globctx->DisplayFd,DISP_CMD_HWC_COMMIT,(unsigned long)arg);
    }
    
	Globctx->HWCFramecount++;
	for(disp = 0; disp < numDisplays; disp++)
	{
		psDisplay = displays[disp];
		if(!psDisplay)
		{
			ALOGV("%s: display[%d] was unexpectedly NULL",
    			    					__func__, disp);
    		continue;
		}
           
		for(i=0 ; i<psDisplay->numHwLayers ; i++)
		{
            if(psDisplay->hwLayers[i].acquireFenceFd>=0)
            {
               close(psDisplay->hwLayers[i].acquireFenceFd);
               psDisplay->hwLayers[i].acquireFenceFd=-1;
             }
		    if((psDisplay->hwLayers[i].compositionType == HWC_OVERLAY) || ((psDisplay->hwLayers[i].compositionType == HWC_FRAMEBUFFER_TARGET)))
			{
				if(releaseFenceFd >= 0)
				{
					psDisplay->hwLayers[i].releaseFenceFd = dup(releaseFenceFd);
			    }else{
					    psDisplay->hwLayers[i].releaseFenceFd = -1;
				}
	        }else{
				psDisplay->hwLayers[i].releaseFenceFd = -1;
		    }
		}
    }
    if(releaseFenceFd >= 0)
    {
        close(releaseFenceFd);
	    releaseFenceFd = -1;
    }

    return ret;
}

static int hwc_eventControl(struct hwc_composer_device_1* dev, int disp,
            int event, int enabled)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long               arg[4]={0};
    if(disp==0)
    {
        DisplayInfo   *PsDisplayInfo=&Globctx->SunxiDisplay[disp];
        switch (event) 
        {
            case HWC_EVENT_VSYNC:
                arg[0] = PsDisplayInfo->VirtualToHWDisplay;
                arg[1] = !!enabled;
                ioctl(Globctx->DisplayFd, DISP_CMD_VSYNC_EVENT_EN,(unsigned long)arg);
                PsDisplayInfo->VsyncEnable = (!!enabled);
                ALOGV("hwc   vsync: %d ",PsDisplayInfo->VsyncEnable);
                return 0;
            default:
                return -EINVAL;
        }
    }

    return -EINVAL;
}

static void hwc_register_procs(struct hwc_composer_device_1* dev,
            hwc_procs_t const* procs)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    Globctx->psHwcProcs = const_cast<hwc_procs_t *>(procs);
}

static int hwc_getDisplayConfigs(struct hwc_composer_device_1 *dev,
        int disp, uint32_t *configs, size_t *numConfigs)
{

	int err = -EINVAL;
	SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo=&Globctx->SunxiDisplay[disp];

	if(disp == HWC_DISPLAY_PRIMARY)
    {
    	if(numConfigs)
    		*numConfigs = 1;

    	if(configs)
    		configs[0] = 0;
	}
	else if(disp == HWC_DISPLAY_EXTERNAL)
	{
	    if(PsDisplayInfo->VirtualToHWDisplay !=-1)
	    {
        	if(numConfigs)
        		*numConfigs = 1;

        	if(configs)
        		configs[0] = 0;
    	}
	    else
	    {
            *numConfigs = 0;
	        goto err_out;
	    }
	}else{
        goto err_out;
    }
    
	err = 0;
err_out:
	return err;
}

static int32_t GetHWAttribute(const uint32_t attribute,
         uint32_t disp)
{
	SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo=&Globctx->SunxiDisplay[disp];
        
    if(PsDisplayInfo->VirtualToHWDisplay!=-1)
    {
	    switch(attribute) {
        case HWC_DISPLAY_VSYNC_PERIOD: 
            return PsDisplayInfo->DisplayVsyncP;
            
        case HWC_DISPLAY_WIDTH:
            return PsDisplayInfo->VarDisplayWidth;

        case HWC_DISPLAY_HEIGHT:
            return PsDisplayInfo->VarDisplayHeight;

        case HWC_DISPLAY_DPI_X:
            return PsDisplayInfo->DiplayDPI_X;

        case HWC_DISPLAY_DPI_Y:
            return PsDisplayInfo->DiplayDPI_Y;
#if 0
	    case HWC_DISPLAY_IS_SECURE:
            if(PsDisplayInfo->DisplayType != DISP_OUTPUT_TYPE_HDMI)
            {   
                return 1;
            }
		    return 1;
#endif
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
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;

    for (int i = 0; attributes[i] != HWC_DISPLAY_NO_ATTRIBUTE; i++) 
    {
        if (disp <=2)
        {
            values[i]=GetHWAttribute(attributes[i],disp);
            
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
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    HwcDestroyDevice(Globctx);
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
    psHwcDevice->common.version  = HWC_DEVICE_API_VERSION_1_1;
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

	HwcCreateDevice();

    return err;
}


