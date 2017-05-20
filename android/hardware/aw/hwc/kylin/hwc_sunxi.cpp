/*-------------------------------------------------------------------------
    
-------------------------------------------------------------------------*/
/*-------------------------------------------------------------------------
    
-------------------------------------------------------------------------*/
/*************************************************************************/ /*!
@Copyright      Copyright (c) Imagination Technologies Ltd. All Rights Reserved
@License        Strictly Confidential.
*/ /**************************************************************************/

#include "hwc.h"


SUNXI_hwcdev_context_t gSunxiHwcDevice;

static inline int HwcUsageSW(IMG_native_handle_t *psHandle)
{
	return psHandle->usage & (GRALLOC_USAGE_SW_READ_OFTEN |
							  GRALLOC_USAGE_SW_WRITE_OFTEN);
}

static inline int HwcUsageSWwrite(IMG_native_handle_t *psHandle)
{
	return psHandle->usage & GRALLOC_USAGE_SW_WRITE_OFTEN;
}

static inline int HwcUsageProtected(IMG_native_handle_t *psHandle)
{
	return psHandle->usage & GRALLOC_USAGE_PROTECTED;
}


static inline int HwcValidFormat(int format)
{
    switch(format) 
    {
    case HAL_PIXEL_FORMAT_RGBA_8888:
    case HAL_PIXEL_FORMAT_RGBX_8888:
    case HAL_PIXEL_FORMAT_RGB_888:
    case HAL_PIXEL_FORMAT_RGB_565:
    case HAL_PIXEL_FORMAT_BGRA_8888:
    case HAL_PIXEL_FORMAT_sRGB_A_8888:
    case HAL_PIXEL_FORMAT_sRGB_X_8888:
    case HAL_PIXEL_FORMAT_YV12:
	case HAL_PIXEL_FORMAT_YCrCb_420_SP:
    case HAL_PIXEL_FORMAT_BGRX_8888:
        return 1;
    default:
        return 0;
    }
}

static inline int HwcisBlended(hwc_layer_1_t* psLayer)
{
	return (psLayer->blending != HWC_BLENDING_NONE);
}

static inline int HwcisPremult(hwc_layer_1_t* psLayer)
{
    return (psLayer->blending == HWC_BLENDING_PREMULT);
}

static void inline CalculateFactor(DisplayInfo *PsDisplayInfo,float *XWidthFactor, float *XHighetfactor)
{
    
    float WidthFactor = (float)PsDisplayInfo->DisplayPersentW / 100; 
    float Highetfactor = (float)PsDisplayInfo->DisplayPersentH / 100;
    if(PsDisplayInfo->InitDisplayWidth && PsDisplayInfo->InitDisplayHeight)
    {
        WidthFactor = (float)PsDisplayInfo->VarDisplayWidth / PsDisplayInfo->InitDisplayWidth * PsDisplayInfo->DisplayPersentW / 100;
        Highetfactor = (float)PsDisplayInfo->VarDisplayHeight / PsDisplayInfo->InitDisplayHeight * PsDisplayInfo->DisplayPersentH / 100;
    }
    
    *XWidthFactor = WidthFactor;
    *XHighetfactor = Highetfactor;
}


static int HwcisScaled(DisplayInfo   *PsDisplayInfo, hwc_layer_1_t *layer)
{
    float XWidthFactor = 1; 
    float XHighetfactor = 1;
    
    CalculateFactor(PsDisplayInfo, &XWidthFactor, &XHighetfactor);
  
    int w = layer->sourceCrop.right - layer->sourceCrop.left;
    int h = layer->sourceCrop.bottom - layer->sourceCrop.top;

    if (layer->transform & HWC_TRANSFORM_ROT_90)
    {
        int tmp = w;
        w = h;
        h = tmp;
    }

    return ((layer->displayFrame.right - layer->displayFrame.left) * XWidthFactor != w)
        || ((layer->displayFrame.bottom - layer->displayFrame.top) * XHighetfactor != h);
}

static int HwcisValidLayer(hwc_layer_1_t *layer)
{
    IMG_native_handle_t *handle = (IMG_native_handle_t *)layer->handle;
    
    if ((layer->flags & HWC_SKIP_LAYER))
    {
        return 0;
    }
    
    if (!HwcValidFormat(handle->iFormat))
    {
        return 0;
    }
   
    if (layer->compositionType == HWC_BACKGROUND)
    {
        return 0;
    }
    if(layer->transform)
    {
        return 0;
    }

    return 1;
}

 int HwcTwoRegionIntersect(hwc_rect_t *rect0, hwc_rect_t *rect1)
{
    int mid_x0, mid_y0, mid_x1, mid_y1;
    int mid_diff_x, mid_diff_y;
    int sum_width, sum_height;

    mid_x0 = (rect0->right + rect0->left)/2;
    mid_y0 = (rect0->bottom + rect0->top)/2;
    mid_x1 = (rect1->right + rect1->left)/2;
    mid_y1 = (rect1->bottom + rect1->top)/2;

    mid_diff_x = (mid_x0 >= mid_x1)? (mid_x0 - mid_x1):(mid_x1 - mid_x0);
    mid_diff_y = (mid_y0 >= mid_y1)? (mid_y0 - mid_y1):(mid_y1 - mid_y0);

    sum_width = (rect0->right - rect0->left) + (rect1->right - rect1->left);
    sum_height = (rect0->bottom - rect0->top) + (rect1->bottom - rect1->top);
    
    if(mid_diff_x < (sum_width/2) && mid_diff_y < (sum_height/2))
    {
        return 1;//return 1 is intersect
    }

    return 0;
}
static inline int  HwcInRegion(hwc_rect_t *RectUp, hwc_rect_t *RectDw)
{
    return ((RectDw->left<=RectUp->left)&&(RectDw->right>RectUp->right)&&(RectDw->top<RectUp->top)&&(RectDw->bottom>RectUp->bottom));
}

static int HwcRegionMerge(hwc_rect_t *rect_from, hwc_rect_t *rect1_to, int bound_width, int bound_height)
{
    if(rect_from->left < rect1_to->left) 
	{
		rect1_to->left = (rect_from->left<0)?0:rect_from->left;
	}
	if(rect_from->right > rect1_to->right) 
	{
		rect1_to->right = (rect_from->right>bound_width)?bound_width:rect_from->right;
	}
	if(rect_from->top < rect1_to->top) 
	{
		rect1_to->top = (rect_from->top<0)?0:rect_from->top;
	}
	if(rect_from->bottom > rect1_to->bottom) 
	{
		rect1_to->bottom = (rect_from->bottom>bound_height)?bound_height:rect_from->bottom;
	}

    return 1;
}

static int HwcFeUseAble(DisplayInfo   *PsDisplayInfo,hwc_layer_1_t * psLayer)
{
    float XWidthFactor = 1; 
    float XHighetfactor = 1;
    
    CalculateFactor(PsDisplayInfo, &XWidthFactor, &XHighetfactor);
    
	int src_w = psLayer->sourceCrop.right - psLayer->sourceCrop.left;
	int src_h = psLayer->sourceCrop.bottom - psLayer->sourceCrop.top;
	int dst_w = (int)(psLayer->displayFrame.right * XWidthFactor + 0.5) - (int)(psLayer->displayFrame.left * XWidthFactor + 0.5);
	int dst_h = (int)(psLayer->displayFrame.bottom * XHighetfactor +0.5) - (int)(psLayer->displayFrame.top *XHighetfactor +0.5);
	float efficience = 0.8;
	float fe_clk = 297000000;
	
	float scale_factor_w = src_w/dst_w ;
	float scale_factor_h = src_h/dst_h ;
	
	float fe_pro_w = (scale_factor_w >= 1)? src_w : dst_w;
	float fe_pro_h = (scale_factor_h >= 1)? src_h : dst_h;

	float required_fe_clk = (fe_pro_w * fe_pro_h)/(dst_w * dst_h)*(PsDisplayInfo->VarDisplayWidth * PsDisplayInfo->VarDisplayHeight * 60)/efficience;
    // must THK thether the small initdisplay  and  the biggest display  can use fe?(1280*720 ---> 3840 * 2160  ---622080000   3840 * 2160 --->1280 *720  cann't....  error,so just can surpport the 1080p screen )   
	if(required_fe_clk > fe_clk) {
		return 0;//cann't
	} else {
		return 1;//can
	}
}


int Check_X_FB(hwc_layer_1_t *psLayer,head_list_t *FBLayerHead)
{
    
    head_list_t *head,*next;
    head = FBLayerHead->next;
    hwc_layer_1_t *tmplayer;
    while(head != FBLayerHead)
    {
        next = head->next;
        tmplayer = ((Layer_list_t *)(head))->pslayer;
        if(HwcTwoRegionIntersect(&tmplayer->displayFrame, &psLayer->displayFrame))
        {
            return 1;
        }   
        head = next;
    }
    return 0;   
}

static bool CheckScaleFormat(int format)
{

    switch(format) 
    {
    case HAL_PIXEL_FORMAT_YV12:
	case HAL_PIXEL_FORMAT_YCrCb_420_SP:
    case HAL_PIXEL_FORMAT_AW_NV12:
        return 1;
    default:
        return 0;
    } 
}



HwcPipeAssignStatusType
HwcTrytoAssignLayer(HwcDevCntContext_t *Localctx,hwc_layer_1_t *psLayer, size_t disp,int zOrder)
{

    IMG_native_handle_t* handle = (IMG_native_handle_t*)psLayer->handle;
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[disp];
    bool feused = 0;
    if(PsDisplayInfo->VirtualToHWDisplay == -1)
    {
         ALOGE("Display[%d]  No Display",disp);
         return ASSIGN_NO_DISP;
    }

    if(psLayer->compositionType == HWC_FRAMEBUFFER_TARGET ) 
    {

        if(handle != NULL)
        {
            if(HwcTwoRegionIntersect(&Localctx->PipeRegion[Localctx->HwPipeUsedCnt], &psLayer->displayFrame))
            {
                Localctx->HwPipeUsedCnt++;
            }
            feused = HwcisScaled(PsDisplayInfo,psLayer);
            ALOGV("HwLayerCnt:%d   HwLayerNum:%d ",Localctx->HwLayerCnt,PsDisplayInfo->HwLayerNum );
            goto assign_ok;
        }else{
            ALOGV("%s:We have a framebuffer MULL  Handle",__func__);
            return ASSIGN_FAILED;
        }
    }

    if(handle->usage  & GRALLOC_USAGE_PRIVATE_3)
    {
        ALOGV("%s:not continuous Memory", __func__);
        Localctx->UsedFB = Localctx->UsedFB|ASSIGN_FAILED;
	    goto assign_failed; 
    }
    
	if(HwcUsageProtected(handle) && (PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_HDMI))
	{
        ALOGV("%s:HDMI Protected", __func__);
        Localctx->UsedFB = Localctx->UsedFB|ASSIGN_FAILED;
	    goto assign_failed;
	}
    
    if(!HwcisValidLayer(psLayer))
    {
        ALOGV("%s:HwcisValidLayer:0x%08x", __func__,handle->iFormat);
        Localctx->UsedFB = Localctx->UsedFB|ASSIGN_FAILED;
        goto assign_failed;
    }

    if(HwcUsageSW(handle) && !CheckScaleFormat(handle->iFormat))
    {
        ALOGV("not video  and   GRALLOC_USAGE_SW_WRITE_OFTEN");
        Localctx->UsedFB = Localctx->UsedFB|ASSIGN_FAILED;
        goto assign_failed;
    }
   
    if((Localctx->HwLayerCnt) >= PsDisplayInfo->HwLayerNum - !!Localctx->UsedFB)
    {
        ALOGV("Have too manly HwLayer:disp:%d HwLayerCnt:%d   HwLayerNum:%d ",disp,Localctx->HwLayerCnt,PsDisplayInfo->HwLayerNum);
        Localctx->UsedFB = Localctx->UsedFB|ASSIGN_NOHWCLAYER;
        goto assign_failed;  
    }
         
    if(HwcisBlended(psLayer) || HwcisPremult(psLayer))
    {
        if(HwcisScaled(PsDisplayInfo,psLayer))
	    {
            if(CheckScaleFormat(handle->iFormat))
            {
		        if(Globctx->GloFEisUsedCnt < NUMBEROFDISPLAY )
		        {
			        if(HwcFeUseAble(PsDisplayInfo,psLayer))
			        {
			            Localctx->FEisUsedCnt++;
                        Globctx->GloFEisUsedCnt++;
                        feused =1;
			        }else{
			            ALOGV("%s:have   fe can not used", __func__);
			            goto assign_failed; 
			        }
		        }else{
			        ALOGV("%s:No enough  fe", __func__);
			        goto assign_failed; 
		        }
            }else{
                    ALOGV("%s:not support alpha scale layer", __func__);
			        goto assign_failed; 
            }
        }
        
        if(Localctx->UsedFB)
        {
            if(Check_X_FB(psLayer,&Localctx->FBLayerHead))
            {
                if(feused)
                {
                    Localctx->FEisUsedCnt--;
                    Globctx->GloFEisUsedCnt--;
                }
                goto assign_failed; 
            }   
        }
        
        if(HwcTwoRegionIntersect(&Localctx->PipeRegion[Localctx->HwPipeUsedCnt], &psLayer->displayFrame))
        {   
            if(Localctx->HwPipeUsedCnt < PsDisplayInfo->HwPipeNum - !!Localctx->UsedFB -1)
            {
                Localctx->HwPipeUsedCnt++;
            }else{
                ALOGV("%s:No enough HwPipe", __func__);
                if(feused)
                {
                    Localctx->FEisUsedCnt--;
                    Globctx->GloFEisUsedCnt--;
                }
			    goto assign_failed;
            }
        }
    }else if(HwcisScaled(PsDisplayInfo,psLayer) || CheckScaleFormat(handle->iFormat))

    {
        if(CheckScaleFormat(handle->iFormat))
        {
		    if(Globctx->GloFEisUsedCnt < NUMBEROFDISPLAY )
		    {
			    if(HwcFeUseAble(PsDisplayInfo,psLayer))
			    {
			        Localctx->FEisUsedCnt++;
                    Globctx->GloFEisUsedCnt++;
                    feused = 1;
			    }else{
			        ALOGV("%s:fe can not used", __func__);
			        goto assign_failed; 
			    }
		    }else{
			    ALOGV("%s:not enough de fe", __func__);
			    goto assign_failed; 
		    }
        }else{
            ALOGV("%s:not support scale layer", __func__);
			goto assign_failed; 
        }
    }
    if(Localctx->HwPipeUsedCnt == PsDisplayInfo->HwPipeNum - 1 && Localctx->UsedFB)
    {
        if(feused)
        {
            Localctx->FEisUsedCnt--;
            Globctx->GloFEisUsedCnt--;
        }
        goto assign_failed;
    }
    HwcRegionMerge(&psLayer->displayFrame,&Localctx->PipeRegion[Localctx->HwPipeUsedCnt],PsDisplayInfo->VarDisplayWidth,PsDisplayInfo->VarDisplayHeight);

    if(Localctx->UsedFB && Check_X_FB(psLayer, &Localctx->FBLayerHead))
    {
        psLayer->hints |= HWC_HINT_CLEAR_FB;
    }

assign_ok:
    if(Localctx->HwLayerCnt == 0 && Globctx->layer0usfe)
    {
        feused = 1;
    }
    InitAddLayerTail(&(Globctx->HwcLayerHead[disp]),psLayer, zOrder,Localctx->HwPipeUsedCnt,feused);
    if(Localctx->HwLayerCnt == 0 && Globctx->layer0usfe)
    {
        Localctx->HwPipeUsedCnt++;
    } 
    Localctx->HwLayerCnt++;  
	return ASSIGN_OK;
    
assign_failed:
    
    InitAddLayerTail(&Localctx->FBLayerHead, psLayer, zOrder, 0,0); 
    return ASSIGN_FAILED;
}

int HwcSetupLayer(SUNXI_hwcdev_context_t *Globctx, hwc_layer_1_t *layer,int zOrder, size_t disp,int pipe)
{ 
    disp_layer_info *layer_info;
    DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[disp];
    float XWidthFactor = 1; 
    float XHighetfactor = 1;
    
    CalculateFactor(PsDisplayInfo, &XWidthFactor, &XHighetfactor);
    if(PsDisplayInfo->VirtualToHWDisplay == -1)
    {
        ALOGE("Display[%d]  No Display",disp);
        return -1;
    }

    layer_info = &(Globctx->pvPrivateData->layer_info[PsDisplayInfo->VirtualToHWDisplay][zOrder]);
    Globctx->pvPrivateData->layer_num[PsDisplayInfo->VirtualToHWDisplay]++;
    IMG_native_handle_t *handle = (IMG_native_handle_t *)layer->handle;
    switch(handle->iFormat)
    {
        case HAL_PIXEL_FORMAT_RGBA_8888:
            layer_info->fb.format = DISP_FORMAT_ABGR_8888;
            break;
        case HAL_PIXEL_FORMAT_RGBX_8888:
            layer_info->fb.format = DISP_FORMAT_XBGR_8888;
            break;
        case HAL_PIXEL_FORMAT_RGB_888:
            layer_info->fb.format = DISP_FORMAT_BGR_888;
            break;
        case HAL_PIXEL_FORMAT_RGB_565:
            layer_info->fb.format = DISP_FORMAT_RGB_565;
            break;
        case HAL_PIXEL_FORMAT_BGRA_8888:
            layer_info->fb.format = DISP_FORMAT_ARGB_8888;
            break;
		case HAL_PIXEL_FORMAT_BGRX_8888:
			layer_info->fb.format = DISP_FORMAT_XRGB_8888;
			break;
        case HAL_PIXEL_FORMAT_YV12:
            layer_info->fb.format = DISP_FORMAT_YUV420_P;
            break;
        case HAL_PIXEL_FORMAT_YCrCb_420_SP:
            layer_info->fb.format = DISP_FORMAT_YUV420_SP_VUVU;
            break;
        case HAL_PIXEL_FORMAT_AW_NV12:
            layer_info->fb.format = DISP_FORMAT_YUV420_SP_UVUV;
            break;
        default:
            ALOGE("Not support format 0x%x in %s", handle->iFormat, __FUNCTION__);
            
            goto ERR;
    }
    if(HwcisBlended(layer))
    {
        layer_info->alpha_mode  = 2;
        layer_info->alpha_value = layer->planeAlpha;
    }else{
        layer_info->alpha_mode  = 1;
        layer_info->alpha_value = 0xff;
    }
    
    if(layer->blending == HWC_BLENDING_PREMULT)
    {
        layer_info->fb.pre_multiply = 1;
    }
	if((PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_HDMI) && (PsDisplayInfo->Current3DMode == DISPLAY_3D_LEFT_RIGHT_HDMI || PsDisplayInfo->Current3DMode == DISPLAY_3D_TOP_BOTTOM_HDMI) )
	{
		layer_info->ck_enable = 0;
	}else{
		layer_info->ck_enable = 0;
	}
    if(handle->iFormat==HAL_PIXEL_FORMAT_YV12||handle->iFormat==HAL_PIXEL_FORMAT_YCrCb_420_SP)
    {
        layer_info->fb.size.width = ALIGN(handle->iWidth, YV12_ALIGN);
    }else{
        layer_info->fb.size.width = ALIGN(handle->iWidth, HW_ALIGN);
    }
    layer_info->fb.size.height = handle->iHeight;
    
    layer_info->fb.addr[0] = IonGetAddr(handle->fd[0]);
    if(layer_info->fb.addr[0] == 0)
    {
         goto ERR;
    }

    if(layer_info->fb.format == DISP_FORMAT_YUV420_P)
    {
        layer_info->fb.addr[2] = layer_info->fb.addr[0] +
                                layer_info->fb.size.width * layer_info->fb.size.height;
        layer_info->fb.addr[1] = layer_info->fb.addr[2] +
                                (layer_info->fb.size.width * layer_info->fb.size.height)/4;
    }else if(layer_info->fb.format == DISP_FORMAT_YUV420_SP_VUVU
             ||layer_info->fb.format == DISP_FORMAT_YUV420_SP_UVUV)
    {
        layer_info->fb.addr[1] = layer_info->fb.addr[0] +
                                layer_info->fb.size.height * layer_info->fb.size.width;
    }
    layer_info->fb.src_win.x = layer->sourceCrop.left;
    layer_info->fb.src_win.y = layer->sourceCrop.top;
    layer_info->fb.src_win.width = layer->sourceCrop.right - layer->sourceCrop.left;
    layer_info->fb.src_win.height = layer->sourceCrop.bottom - layer->sourceCrop.top;

    layer_info->screen_win.x = (int)(layer->displayFrame.left * XWidthFactor + 0.5) + (PsDisplayInfo->VarDisplayWidth * (100 - PsDisplayInfo->DisplayPersentW) / 100 / 2);
    layer_info->screen_win.y = (int)(layer->displayFrame.top * XHighetfactor +0.5) + (PsDisplayInfo->VarDisplayHeight * (100 - PsDisplayInfo->DisplayPersentH) / 100 / 2);
    layer_info->screen_win.width = (int)((layer->displayFrame.right - layer->displayFrame.left ) * XWidthFactor + 0.5);
    layer_info->screen_win.height = (int)((layer->displayFrame.bottom - layer->displayFrame.top ) * XHighetfactor +0.5);
    
    if(HwcisScaled(PsDisplayInfo,layer) || CheckScaleFormat(handle->iFormat))
    {
        int cut_size_scn, cut_size_src;
        hwc_rect_t scn_bound;
        layer_info->mode = DISP_LAYER_WORK_MODE_SCALER;
        scn_bound.left = PsDisplayInfo->VarDisplayWidth * (100 - PsDisplayInfo->DisplayPersentW) / 100/ 2;
        scn_bound.top = PsDisplayInfo->VarDisplayHeight * (100 - PsDisplayInfo->DisplayPersentH) /100 / 2;
        scn_bound.right = scn_bound.left + (PsDisplayInfo->VarDisplayWidth * PsDisplayInfo->DisplayPersentW) / 100;
        scn_bound.bottom = scn_bound.top + (PsDisplayInfo->VarDisplayHeight * PsDisplayInfo->DisplayPersentH) / 100;

        if(layer_info->fb.src_win.x < 0)
        {
            cut_size_src = (0 - layer_info->fb.src_win.x);

            layer_info->fb.src_win.x += cut_size_src;
            layer_info->fb.src_win.width -= cut_size_src;
        }
        if((layer_info->fb.src_win.x + layer_info->fb.src_win.width) > (unsigned int)handle->iWidth)
        {
            cut_size_src = (layer_info->fb.src_win.x + layer_info->fb.src_win.width) - handle->iWidth;
            layer_info->fb.src_win.width -= cut_size_src;
        }
        if(layer_info->fb.src_win.y < 0)
        {
            cut_size_src = (0 - layer_info->fb.src_win.y);

            layer_info->fb.src_win.y += cut_size_src;
            layer_info->fb.src_win.height -= cut_size_src;
        }
        if((layer_info->fb.src_win.y + layer_info->fb.src_win.height) > (unsigned int)handle->iHeight)
        {
            cut_size_src = (layer_info->fb.src_win.x + layer_info->fb.src_win.height) - handle->iHeight;
            layer_info->fb.src_win.height -= cut_size_src;
        }
        
        if(layer_info->screen_win.x < scn_bound.left)
        {
            cut_size_scn = (scn_bound.left - layer_info->screen_win.x);
            cut_size_src = cut_size_scn * layer_info->fb.src_win.width / layer_info->screen_win.width;

            layer_info->fb.src_win.x += cut_size_src;
            layer_info->fb.src_win.width -= cut_size_src;

            layer_info->screen_win.x += cut_size_scn;
            layer_info->screen_win.width -= cut_size_scn;
        }
        if((layer_info->screen_win.x + layer_info->screen_win.width) > (unsigned int)scn_bound.right)
        {
            cut_size_scn = (layer_info->screen_win.x + layer_info->screen_win.width) - scn_bound.right;
            cut_size_src = cut_size_scn * layer_info->fb.src_win.width / layer_info->screen_win.width;
            
            layer_info->fb.src_win.width -= cut_size_src;
            layer_info->screen_win.width -= cut_size_scn;
        }
        if(layer_info->screen_win.y < scn_bound.top)
        {
            cut_size_scn = (scn_bound.top - layer_info->screen_win.y);
            cut_size_src = cut_size_scn * layer_info->fb.src_win.height / layer_info->screen_win.height;
            
            layer_info->fb.src_win.y += cut_size_src;
            layer_info->fb.src_win.height -= cut_size_src;
            
            layer_info->screen_win.y += cut_size_scn;
            layer_info->screen_win.height -= cut_size_scn;
        }
        if((layer_info->screen_win.y + layer_info->screen_win.height) > (unsigned int)scn_bound.bottom)
        {
            cut_size_scn = (layer_info->screen_win.y + layer_info->screen_win.height) - scn_bound.bottom;
            cut_size_src = cut_size_scn * layer_info->fb.src_win.height / layer_info->screen_win.height;
            
            layer_info->fb.src_win.height -= cut_size_src;
            layer_info->screen_win.height -= cut_size_scn;
        }	
    }
    else
    {
        layer_info->mode = DISP_LAYER_WORK_MODE_NORMAL;
    }
    if(zOrder == 0 && Globctx->layer0usfe)
    {
        layer_info->mode = DISP_LAYER_WORK_MODE_SCALER;
    }
    layer_info->pipe = pipe;
    layer_info->zorder = zOrder;
    _hwcdev_layer_config_3d(disp, layer_info);
    
    return 1;
ERR:
    
    Globctx->pvPrivateData->layer_num[PsDisplayInfo->VirtualToHWDisplay] = 0;
    memset(&(Globctx->pvPrivateData->layer_info[PsDisplayInfo->VirtualToHWDisplay]),0,sizeof(disp_layer_info)*4);
    return -1;   
}   

unsigned int IonGetAddr(int sharefd)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int ret = -1;
    struct ion_handle* tmp2;
    struct ion_custom_data custom_data;
	sunxi_phys_data phys_data;
    ion_handle_data freedata;

    struct ion_fd_data data ;
    data.fd = sharefd;
    ret = ioctl(Globctx->IonFd, ION_IOC_IMPORT, &data);
    if (ret < 0)
    {
        ALOGE("#######ion_import  error#######");
        return 0;
    }
    custom_data.cmd = ION_IOC_SUNXI_PHYS_ADDR;
	phys_data.handle = data.handle;
	custom_data.arg = (unsigned long)&phys_data;
	ret = ioctl(Globctx->IonFd, ION_IOC_CUSTOM,&custom_data);
	if(ret < 0){
        ALOGE("ION_IOC_CUSTOM(err=%d)",ret);
        return 0;
    }
    freedata.handle = data.handle;
    ret = ioctl(Globctx->IonFd, ION_IOC_FREE, &freedata);
    if(ret < 0){
        ALOGE("ION_IOC_FREE(err=%d)",ret);
        return 0;
    }
    return phys_data.phys_addr;  
}

int aw_get_composer0_use_fe()
{
	int usefe = -1;
	char property[PROPERTY_VALUE_MAX];
	if (property_get("persist.sys.layer0usefe", property, NULL) >= 0)
	{

	    usefe = atoi(property);
        
	}
    if(usefe == 1)
    {
        return 1;
    }else{
	    return 0;
	}
}

int InitDisplayDeviceInfo()
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int refreshRate, xdpi, ydpi, vsync_period;
    struct fb_var_screeninfo info;
    int arg[4] = {0};
    int i;

    if (ioctl(Globctx->FBFd, FBIOGET_VSCREENINFO, &info) == -1) {
        ALOGE("FBIOGET_VSCREENINFO ioctl failed: %s", strerror(errno));
        return -1;
    }
    ALOGD("#########The PrimaryDisplay:%d ", Globctx->SunxiDisplay[0].VirtualToHWDisplay);

    for(i = 0; i < NUMBEROFDISPLAY; i++)
    {
        DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[i];
        if(PsDisplayInfo->VirtualToHWDisplay != -1)
        {
            switch(PsDisplayInfo->DisplayType)
            {
                case DISP_OUTPUT_TYPE_LCD:
                    arg[0] = PsDisplayInfo->VirtualToHWDisplay;
                    
                    refreshRate = 1000000000000LLU /
                                (
                                uint64_t( info.upper_margin + info.lower_margin + info.vsync_len + info.yres )
                                * ( info.left_margin  + info.right_margin + info.hsync_len + info.xres )
                                * info.pixclock
                                );
                    if (refreshRate == 0) 
                    {
                        ALOGW("invalid refresh rate, assuming 60 Hz");
                        refreshRate = 60;
                    }

                    if(info.width == 0)
                    {
                        PsDisplayInfo->DiplayDPI_X = 160000;
                    }
                    else
                    {
                        PsDisplayInfo->DiplayDPI_X = 1000 * (info.xres * 25.4f) / info.width;
                    }
                    if(info.height == 0)
                    {
                        PsDisplayInfo->DiplayDPI_Y = 160000;
                    }
                    else
                    {
                         PsDisplayInfo->DiplayDPI_Y = 1000 * (info.yres * 25.4f) / info.height;
                    }
                    
                    PsDisplayInfo->DisplayVsyncP = 1000000000 / refreshRate;
                    PsDisplayInfo->DisplayPersentHT = 100;
                    PsDisplayInfo->DisplayPersentWT = 100;
                    PsDisplayInfo->Current3DMode = DISPLAY_2D_ORIGINAL;
                   
                    PsDisplayInfo->InitDisplayWidth = info.xres;
                    PsDisplayInfo->InitDisplayHeight = info.yres;
                    PsDisplayInfo->VarDisplayWidth = info.xres;
                    PsDisplayInfo->VarDisplayHeight = info.yres;
                    PsDisplayInfo->HwLayerNum = DISPLAY_MAX_LAYER_NUM;
    	            PsDisplayInfo->HwPipeNum = NUMBEROFPIPE;
                    PsDisplayInfo->VsyncEnable = 1;
                    break; 
                    
                case DISP_OUTPUT_TYPE_HDMI:
                    arg[0] = PsDisplayInfo->VirtualToHWDisplay;
                    
                    PsDisplayInfo->DisplayType = DISP_OUTPUT_TYPE_HDMI;
                    PsDisplayInfo->DisplayMode = (disp_tv_mode)ioctl(Globctx->DisplayFd,DISP_CMD_HDMI_GET_MODE,arg);
                    PsDisplayInfo->InitDisplayWidth = GetInfoOfMode(PsDisplayInfo->DisplayMode,WIDTH);
                    PsDisplayInfo->InitDisplayHeight = GetInfoOfMode(PsDisplayInfo->DisplayMode,HEIGHT);
                    PsDisplayInfo->VarDisplayWidth = PsDisplayInfo->InitDisplayWidth;
                    PsDisplayInfo->VarDisplayHeight = PsDisplayInfo->InitDisplayHeight;
                    PsDisplayInfo->DiplayDPI_X = 213000;
                    PsDisplayInfo->DiplayDPI_Y = 213000;
                    PsDisplayInfo->DisplayVsyncP = 1000000000/GetInfoOfMode(PsDisplayInfo->DisplayMode,REFRESHRAE);
                    PsDisplayInfo->Current3DMode = DISPLAY_2D_ORIGINAL;

                    PsDisplayInfo->DisplayPersentHT = 100;
                    PsDisplayInfo->DisplayPersentWT = 100;
                    PsDisplayInfo->HwLayerNum = DISPLAY_MAX_LAYER_NUM;
    	            PsDisplayInfo->HwPipeNum = NUMBEROFPIPE;
                    PsDisplayInfo->VsyncEnable = 1;
                    PsDisplayInfo->DisplayEnable = 0;
                    break;

                case DISP_OUTPUT_TYPE_TV:
                case DISP_OUTPUT_TYPE_VGA:    
                    
                default:
                    ALOGD("not support type");
                    continue;
                        
            }
        }
    }
    return 0;
}


SUNXI_hwcdev_context_t* HwcCreateDevice(void)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;

    unsigned long arg[4] = {0};
    int outtype;
    int HDMI_fd;
    int DispCnt;
	int num = 0;
    disp_tv_mode hdmi_mode;
    DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[0];

    memset(Globctx, 0, sizeof(SUNXI_hwcdev_context_t));
    for(DispCnt = 0; DispCnt < NUMBEROFDISPLAY; DispCnt++)
    {
        Globctx->SunxiDisplay[DispCnt].VirtualToHWDisplay = -1;
    }
    Globctx->pvPrivateData =(setup_dispc_data_t* )calloc(1, sizeof(setup_dispc_data_t));
    memset(Globctx->pvPrivateData, 0, sizeof(setup_dispc_data_t));
    Globctx->pvPrivateData->hConfigData = calloc(DISPLAY_MAX_LAYER_NUM*NUMBEROFDISPLAY,sizeof(int));  
    Globctx->DisplayFd = open("/dev/disp", O_RDWR);
    if (Globctx->DisplayFd < 0)
    {
        ALOGE( "Failed to open disp device, ret:%d, errno: %d\n", Globctx->DisplayFd, errno);
    }
    
    Globctx->FBFd = open("/dev/graphics/fb0", O_RDWR);
    if (Globctx->FBFd < 0)
    {
        ALOGE( "Failed to open fb0 device, ret:%d, errno:%d\n", Globctx->FBFd, errno);
    }
    Globctx->IonFd = open("/dev/ion",O_RDWR);
    if(Globctx->IonFd < 0)
    {
        ALOGE( "Failed to open  ion device, ret:%d, errno:%d\n", Globctx->IonFd, errno);
    }
    
    PsDisplayInfo->VirtualToHWDisplay = AW_DIS_00;
    arg[0] = AW_DIS_00;
	outtype = ioctl(Globctx->DisplayFd, DISP_CMD_GET_OUTPUT_TYPE, arg);
	while(outtype == DISP_OUTPUT_TYPE_NONE) {
		usleep(50000);
    	outtype = ioctl(Globctx->DisplayFd, DISP_CMD_GET_OUTPUT_TYPE, arg);
		num++;
		if(num == 20)
		{
			ALOGE( "########NO LCD Display Screen#######");
			break;
		}
	}

    if(outtype==DISP_OUTPUT_TYPE_NONE)
    {
        arg[0] = AW_DIS_02;
        outtype = ioctl(Globctx->DisplayFd, DISP_CMD_GET_OUTPUT_TYPE, arg);
        if(outtype == DISP_OUTPUT_TYPE_NONE)
        {
            arg[0] = AW_DIS_01;
            outtype = ioctl(Globctx->DisplayFd, DISP_CMD_GET_OUTPUT_TYPE, arg);
            if(outtype == DISP_OUTPUT_TYPE_NONE)
            {
                 ALOGE( "########No Display Screen#######");
            }else{
            	PsDisplayInfo->VirtualToHWDisplay = AW_DIS_01;
	        }
        }else{
            PsDisplayInfo->VirtualToHWDisplay = AW_DIS_02;
        }
    }else{
        PsDisplayInfo->VirtualToHWDisplay = AW_DIS_00;
    }
    PsDisplayInfo->DisplayType = outtype;
    Globctx->CanForceGPUCom = 1;
    Globctx->ForceGPUComp = 0;
    Globctx->layer0usfe = aw_get_composer0_use_fe();
	if(PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_HDMI)
    {
    	Globctx->HDMIMode = (disp_tv_mode)ioctl(Globctx->DisplayFd,DISP_CMD_HDMI_GET_MODE,arg);   
    }else{
        Globctx->HDMIMode = DISP_TV_MOD_1080P_60HZ;
    }

    InitDisplayDeviceInfo();
    
    HDMI_fd = open("/sys/class/switch/hdmi/state", O_RDONLY);
    if (HDMI_fd) 
    {
        char val;
        if (read(HDMI_fd, &val, 1) == 1 && val == '1') 
        {
			if(PsDisplayInfo->DisplayType != DISP_OUTPUT_TYPE_HDMI)
			{ 
				hwc_hdmi_switch(HDMI_USED, 1,1);
			}
			ALOGD( "### init hdmi_plug: IN ###");
        }
        close(HDMI_fd);
    }
	Globctx->fBeginTime = 0.0;
    Globctx->uiBeginFrame = 0;
    Globctx->hwcdebug = 0; 

    ALOGD( "#### Type:%d  DisplayMode:%d PrimaryDisplay:%d  DisplayWidth:%d  DisplayHeight:%d ",Globctx->SunxiDisplay[0].DisplayType,Globctx->SunxiDisplay[0].DisplayMode,  Globctx->SunxiDisplay[0].VirtualToHWDisplay,Globctx->SunxiDisplay[0].VarDisplayWidth,Globctx->SunxiDisplay[0].VarDisplayHeight);
    pthread_create(&Globctx->sVsyncThread, NULL, VsyncThreadWrapper, Globctx);
    
	return (SUNXI_hwcdev_context_t*)Globctx;
}

int HwcDestroyDevice(SUNXI_hwcdev_context_t *psDevice)
{
	SUNXI_hwcdev_context_t *Globctx = (SUNXI_hwcdev_context_t*)psDevice;

	close(Globctx->DisplayFd);
	close(Globctx->FBFd);
    close(Globctx->IonFd);
    free(Globctx->pvPrivateData->hConfigData);
	free(Globctx->pvPrivateData);
	return 1;
}
