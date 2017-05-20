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

SUNXI_hwcdev_context_t gSunxiHwcDevice;


static char merg_fence[40];

static mem_speed_limit_t mem_speed_limit[3] =
{
    {672000, 37324800},
    {552000, 29030400},
    {432000, 20736000},
};

static inline bool check_video(int format);

static bool inline hwc_format_info(format_info *format_info, int format);

static const char *hwc_print_info(AssignDUETO_T eError)
{
    switch(eError)
	{

#define AssignDUETO(x) \
		          case x: \
			            return #x;
    AssignDUETO(I_OVERLAY)
    AssignDUETO(D_NULL_BUF)
    AssignDUETO(D_CONTIG_MEM)
	AssignDUETO(D_VIDEO_PD)
	AssignDUETO(D_CANNT_SCALE)
	AssignDUETO(D_SKIP_LAYER)
    AssignDUETO(D_NO_FORMAT)
    AssignDUETO(D_BACKGROUND)
    AssignDUETO(D_TR_N_0)
    AssignDUETO(D_ALPHA)
    AssignDUETO(D_X_FB)
    AssignDUETO(D_SW_OFTEN)
    AssignDUETO(D_SCALE_OUT)
    AssignDUETO(D_STOP_HWC)
    AssignDUETO(D_NO_PIPE)
    AssignDUETO(D_NO_MEM)
#undef AssignDUETO
		default:
			return "Unknown reason";
	}
    
}

static void dump_displays(HwcDisContext_t *Localctx)
{
	int i;
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    layer_info  *AllLayers = Localctx->psAllLayer;
    layer_info  *sunxiLayers = NULL;
    const DisplayInfo *PsDisplayInfo = Localctx->psDisplayInfo;
    static char const* compositionTypeName[] = {
                            "GLES",
                            "HWC",
                            "BKGD",
                            "FB",
                            "SIDE",
                            "CURS",
                            "NULL"};
	if(Globctx->hwcdebug & LAYER_DUMP)
	{
        if(PsDisplayInfo == NULL)
        {
            return;
        }
        ALOGD("\n\n+Disp info:\n"
              "+ Fram Num  |Disp|Vsync|FCGPU|Cache(Used)|Abandon|Total Mem | Cur used |Disp limit|Disp Total|Channel[0]|Channel[1]|Channel[2]|Channel[3]|timestamp\n"
              "+-----------+----+-----+-----+-----------+-------+----------+----------+----------+----------+----------+----------+----------+----------+----------+");
        ALOGD("+%10d |  %d |%-5s|%-5s|%5d(%-4d)|%4d   |%10d|%10d|%10d|%10d|%10d|%10d|%10d|%10d|%lld\n"
            ,Globctx->HWCFramecount - 1
            ,PsDisplayInfo->VirtualToHWDisplay
            ,PsDisplayInfo->VsyncEnable?"yes":"no"
            ,Globctx->ForceGPUComp ? "yes":"no"
            ,Globctx->NumManagemax
            ,Globctx->NumManageUsed
            ,Globctx->AbandonCount
            ,Globctx->memlimit
            ,Globctx->currentmem
            ,Localctx->psDisplayInfo->max_thruput
            ,Localctx->current_thruput
            ,Localctx->ChannelInfo[0].memthruput
            ,Localctx->ChannelInfo[1].memthruput
            ,Localctx->ChannelInfo[2].memthruput
            ,Localctx->ChannelInfo[3].memthruput
            ,PsDisplayInfo->mytimestamp);
        
        ALOGD("+-----------+----+-----+-----+-----------+-------+----------+----------+----------+----------+----------+----------+----------+----------+----------+");
        ALOGD("+\n"
              "+Type|CH|V|SC H|SC W|PL|S|  Handle | Phyaddr |  Usage  |  Flags  |Tr|Bld| Format  |        Source Crop      |         Frame Crop      | Reason\n"
              "+----+--+-+----+----+--+-+---------+---------+---------+---------+--+---+---------+-------------------------+-------------------------+-------------\n");
        if(AllLayers == NULL)
        {
            return ;
        }
        for(i = 0; i < Localctx->numberofLayer ; i++)
		{
            sunxiLayers = AllLayers+i;
            hwc_layer_1_t *l = sunxiLayers->psLayer;
            IMG_native_handle_t* handle = (IMG_native_handle_t*)l->handle;
            ALOGD("+%4s|%2d|%s|%1.2f|%1.2f|%02x|%d| %08x| %08x| %08x| %08x|%02x|%3x| %08x|[%5d,%5d,%5d,%5d]|[%5d,%5d,%5d,%5d]|%s\n",
                    compositionTypeName[l->compositionType],
                    sunxiLayers->hwchannel,
                    sunxiLayers->virchannel >= 0 ?
                        (Localctx->ChannelInfo[sunxiLayers->virchannel].hasVideo ? "Y" : "N"):"N",
                    sunxiLayers->virchannel >= 0 ?
                        Localctx->ChannelInfo[sunxiLayers->virchannel].HTScaleFactor :0,
                    sunxiLayers->virchannel >= 0 ? 
                        Localctx->ChannelInfo[sunxiLayers->virchannel].WTScaleFactor :0,
                    sunxiLayers->virchannel >= 0 ? 
                        Localctx->ChannelInfo[sunxiLayers->virchannel].planeAlpha : 0xff,
                    sunxiLayers->need_sync,
                    l->handle,
                    handle == 0 ? 0 : ((handle->uiFlags & PVRSRV_MEM_CONTIGUOUS) ? ion_get_addr_fromfd(handle->fd[0]) : 0),
                    handle == 0 ? 0 : handle->usage,
                    l->flags, 
                    l->transform, 
                    l->blending, 
                    handle==0?0:handle->iFormat,
#if !defined(HWC_1_3)
                    l->sourceCrop.left,
                    l->sourceCrop.top,
                    l->sourceCrop.right,
                    l->sourceCrop.bottom,
#else
                    (int)ceilf(l->sourceCropf.left),
                    (int)ceilf(l->sourceCropf.top),
                    (int)ceilf(l->sourceCropf.right),
                    (int)ceilf(l->sourceCropf.bottom),

#endif
                    l->displayFrame.left,
                    l->displayFrame.top,
                    l->displayFrame.right,
                    l->displayFrame.bottom,
                   sunxiLayers->info != DEFAULT? hwc_print_info(sunxiLayers->info): "NOT_ASSIGNED");
        }
        ALOGD("+----+--+-+--------+--------+--+---------+---------+---------+---------+--+-----+---------+-------------------------+-------------------------+-------------\n");

    }
}
bool inline hwc_support_rotate(unsigned int transform)
{
    switch(transform)
    {
        case 0:
        case HAL_TRANSFORM_ROT_90:
        case HAL_TRANSFORM_ROT_180:
        case HAL_TRANSFORM_ROT_270:
            return 1;
        default:
            return 0;
    }
}

bool inline hwc_rotate_mem(IMG_native_handle_t *handle)
{
    return (handle->iWidth * handle->iHeight) < 2400000;
}

static inline bool check_swap_w_h(unsigned int mode)
{
    return  (mode & HAL_TRANSFORM_ROT_90) == HAL_TRANSFORM_ROT_90  
            || (mode & HAL_TRANSFORM_ROT_270) == HAL_TRANSFORM_ROT_270;
}

static inline void reset_layer_type(HwcDisContext_t *Localctx,
        hwc_display_contents_1_t *list, int hwctype) 
{
    unsigned int j = 0;
    IMG_native_handle_t *handle = NULL;
    format_info format_info;
    layer_info *psAllLayer = Localctx->psAllLayer;
    hwc_layer_1_t *psLayer = NULL;

    if (list && list->numHwLayers >= 1) 
    {
        for(j = 0; j < list->numHwLayers; j++) 
        {
            psLayer = &list->hwLayers[j];
            if(psLayer->compositionType != HWC_FRAMEBUFFER_TARGET)
            {
               psLayer->compositionType = hwctype;
            }
            handle = (IMG_native_handle_t *)psLayer->handle;
            if(handle != NULL && check_video(handle->iFormat))
            {
                hwc_format_info(&format_info, handle->iFormat);
#ifdef HWC_1_3
                Localctx->prememvideo += (psLayer->sourceCropf.right - psLayer->sourceCropf.left) 
                        * (psLayer->sourceCropf.bottom - psLayer->sourceCropf.top) 
                        * format_info.bpp / 8;
#else
                Localctx->prememvideo += (psLayer->sourceCrop.right - psLayer->sourceCrop.left) 
                        * (psLayer->sourceCrop.bottom - psLayer->sourceCrop.top)
                        * format_info.bpp / 8;
#endif
                Localctx->unasignedVideo++; 
            }
            if(psAllLayer != NULL)
            {
                psAllLayer[j].assigned = ASSIGN_INIT;
                psAllLayer[j].hwchannel = -2;
                psAllLayer[j].virchannel = -2;
                psAllLayer[j].HwzOrder  = -1;
                psAllLayer[j].OrigOrder = -1;
                psAllLayer[j].isvideo= 0;
                psAllLayer[j].is3D = 0;
                psAllLayer[j].is_cursor = 0;
                psAllLayer[j].need_sync = 0;
                psAllLayer[j].shared_fd = -1;
                psAllLayer[j].info = DEFAULT;
                psAllLayer[j].psLayer = psLayer;
            }
        }
    }
}

static void inline calculate_factor(DisplayInfo *PsDisplayInfo,
        float *XWidthFactor, float *XHighetfactor)
{
    
    float WidthFactor = (float)PsDisplayInfo->PersentWidth / 100;
    float Highetfactor = (float)PsDisplayInfo->PersentHeight / 100;
    if(PsDisplayInfo->InitDisplayWidth && PsDisplayInfo->InitDisplayHeight)
    {
        WidthFactor = (float)(((float)PsDisplayInfo->VarDisplayWidth)
                    / PsDisplayInfo->InitDisplayWidth * PsDisplayInfo->PersentWidth / 100);
        Highetfactor = (float)(((float)PsDisplayInfo->VarDisplayHeight)
                    / PsDisplayInfo->InitDisplayHeight * PsDisplayInfo->PersentHeight / 100);
    }
    *XWidthFactor = WidthFactor;
    *XHighetfactor = Highetfactor;
}

static bool reset_local(DisplayInfo *psDisplayInfo, HwcDisContext_t *Localctx, 
        hwc_display_contents_1_t *psDisplay)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int i = 4, j = 0;
    bool ret = 0;

    Localctx->use_wb = 0;
    Localctx->fb_has_alpha = 0;
    Localctx->wb_tr = 0;
    Localctx->has_tr = 0;
    Localctx->hasVideo = 0;
    Localctx->HwCHUsedCnt = -1;
    Localctx->VideoCHCnt = -1;
    Localctx->unasignedVideo = 0;
    Localctx->prememvideo = 0;
    Localctx->countofhwlayer = 0;
    Localctx->numberofLayer = psDisplay->numHwLayers;
    Localctx->psDisplayInfo = psDisplayInfo;
    Globctx->currentmem -= Localctx->current_thruput;
    Localctx->current_thruput = 0;
    Globctx->has_tr_count -= Localctx->has_tr;

    if(Localctx->psAllLayer == NULL
        || (Localctx->malloc_layer - psDisplay->numHwLayers) > 2)
    {
        if(Localctx->psAllLayer != NULL)
        {
            free(Localctx->psAllLayer);
        }
        Localctx->malloc_layer = 0;
        Localctx->psAllLayer = (layer_info*)calloc(psDisplay->numHwLayers, sizeof(layer_info));
        if(Localctx->psAllLayer == NULL)
        {
            ret = 1;
        }else{
            Localctx->malloc_layer = psDisplay->numHwLayers;
        }
    }
    if(Localctx->psAllLayer != NULL)
    {
        memset(Localctx->psAllLayer, 0, sizeof(layer_info) * Localctx->malloc_layer);
    }
    calculate_factor(psDisplayInfo, &Localctx->WidthScaleFactor, &Localctx->HighetScaleFactor);

    reset_layer_type(Localctx, psDisplay, HWC_FRAMEBUFFER);
    if(!Localctx->unasignedVideo)
    {
        Globctx->stop_rotate_hw = 0;//here maybe a bug
    }
    while(i--)
    {
        j = 4;
        Localctx->ChannelInfo[i].hasBlend = 0;
        Localctx->ChannelInfo[i].hasVideo= 0;
        Localctx->ChannelInfo[i].WTScaleFactor = 1.0;
        Localctx->ChannelInfo[i].HTScaleFactor = 1.0;
        Localctx->ChannelInfo[i].iCHFormat = 0;
        Localctx->ChannelInfo[i].planeAlpha = 0xff;
        Localctx->ChannelInfo[i].HwLayerCnt = 0;
        Localctx->ChannelInfo[i].memthruput = 0;
        while(j--)
        {
            Localctx->ChannelInfo[i].HwLayer[j] = NULL;
        } 
    }
    return ret;
}

static bool inline recount_present(DisplayInfo *PsDisplayInfo)
{

    if(PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_HDMI)
    {
        if(PsDisplayInfo->SetPersentWidth >= 90 && PsDisplayInfo->SetPersentWidth <= 100
            && PsDisplayInfo->DisplayMode != DISP_TV_MOD_3840_2160P_25HZ
            && PsDisplayInfo->DisplayMode != DISP_TV_MOD_3840_2160P_24HZ
            && PsDisplayInfo->DisplayMode != DISP_TV_MOD_3840_2160P_30HZ
            && PsDisplayInfo->Current3DMode == DISPLAY_2D_ORIGINAL )
        {
            PsDisplayInfo->PersentWidth = PsDisplayInfo->SetPersentWidth;
        }else{
            PsDisplayInfo->PersentWidth = 100;
        }

        if(PsDisplayInfo->SetPersentHeight >= 90 && PsDisplayInfo->SetPersentHeight <= 100
            && PsDisplayInfo->DisplayMode != DISP_TV_MOD_3840_2160P_25HZ
            && PsDisplayInfo->DisplayMode != DISP_TV_MOD_3840_2160P_24HZ
            && PsDisplayInfo->DisplayMode != DISP_TV_MOD_3840_2160P_30HZ
            && PsDisplayInfo->Current3DMode == DISPLAY_2D_ORIGINAL )
        {
            PsDisplayInfo->PersentHeight= PsDisplayInfo->SetPersentHeight;
        }else{
            PsDisplayInfo->PersentHeight= 100;
        }

        if(PsDisplayInfo->PersentHeight != 100 || PsDisplayInfo->PersentWidth != 100)
        {
            return 1;
        }
    }else{
        PsDisplayInfo->PersentHeight = 100;
        PsDisplayInfo->PersentWidth = 100;
    }
    return 0;
}

static void reset_globle(SUNXI_hwcdev_context_t *Globctx)
{
    int i;
    Globctx->currentmem = 0;
    Globctx->has_tr_count = 0;
    int tmp_mem_thruput0 = 0;

    Globctx->SunxiDisplay[0].max_thruput = Globctx->memlimit;
	for (i = 0; i < Globctx->NumberofDisp; i++)
	{
        if(Globctx->SunxiDisplay[i].VirtualToHWDisplay != -EINVAL)
        {
            if(i > 0)
            {
                Globctx->SunxiDisplay[0].max_thruput -= Globctx->SunxiDisplay[i].fb_thruput;
            }
            recount_present(&Globctx->SunxiDisplay[i]);
        }
        Globctx->DisContext[i].UsedFB = 0;
        Globctx->DisContext[i].current_thruput = 0;
	}
    tmp_mem_thruput0 = Globctx->SunxiDisplay[0].max_thruput;

    for (i = 1; i < Globctx->NumberofDisp; i++)
	{
        if(Globctx->SunxiDisplay[i].VirtualToHWDisplay != -EINVAL)
        {
            tmp_mem_thruput0 += Globctx->SunxiDisplay[i].fb_thruput;
            Globctx->SunxiDisplay[i].max_thruput = tmp_mem_thruput0;
        }
	}
}

static inline int check_usage_sw_read(IMG_native_handle_t *psHandle)
{
	return (psHandle->usage & GRALLOC_USAGE_SW_READ_MASK);
}

static inline int check_stop_hwc(IMG_native_handle_t *psHandle)
{
	return psHandle->usage & STOP_HWC;
}

static inline int check_usage_sw_write(IMG_native_handle_t *psHandle)
{
	return (psHandle->usage & GRALLOC_USAGE_SW_WRITE_MASK);
}

static inline int check_usage_protected(IMG_native_handle_t *psHandle)
{
	return psHandle->usage & GRALLOC_USAGE_PROTECTED;
}

static inline bool cursor_flags(hwc_layer_1_t *layer)
{
    return layer->flags & HWC_IS_CURSOR_LAYER;
}
static inline int check_cursor_format(int format)
{
    switch(format) 
    {
        case HAL_PIXEL_FORMAT_RGBA_8888:
        case HAL_PIXEL_FORMAT_BGRA_8888:
            return 1;
    }
    return 0;
}

static inline bool check_cursor(hwc_layer_1_t *layer, int order, int count)
{
    IMG_native_handle_t *handle = (IMG_native_handle_t *)layer->handle;
    return (layer->flags & HWC_IS_CURSOR_LAYER) && (count-order == 2) && check_cursor_format(handle->iFormat);
}

static inline int check_valid_format(int format)
{
    switch(format) 
    {
    case HAL_PIXEL_FORMAT_RGBA_8888:
    case HAL_PIXEL_FORMAT_RGBX_8888:
    case HAL_PIXEL_FORMAT_RGB_888:
    case HAL_PIXEL_FORMAT_RGB_565:
    case HAL_PIXEL_FORMAT_BGRA_8888:
    // case HAL_PIXEL_FORMAT_sRGB_A_8888:
    // case HAL_PIXEL_FORMAT_sRGB_X_8888:
    case HAL_PIXEL_FORMAT_YV12:
	case HAL_PIXEL_FORMAT_YCrCb_420_SP:
    case HAL_PIXEL_FORMAT_BGRX_8888:
        return 1;
    default:
        return 0;
    }
}

static inline int check_scale_format(int format)
{
    switch(format)
    {
        case HAL_PIXEL_FORMAT_YV12:
	    case HAL_PIXEL_FORMAT_YCrCb_420_SP:
        case HAL_PIXEL_FORMAT_AW_NV12:
            return 1;
#if defined(UI_SCALE)
        case HAL_PIXEL_FORMAT_BGRA_8888:
        case HAL_PIXEL_FORMAT_BGRX_8888:
        case HAL_PIXEL_FORMAT_RGBX_8888:
        case HAL_PIXEL_FORMAT_RGBA_8888:
        case HAL_PIXEL_FORMAT_RGB_565:
        case HAL_PIXEL_FORMAT_RGB_888:
            return 2;
#endif
        default:
            return 0;
    }
}

static inline bool check_video(int format)
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

static bool check_3d_video(const DisplayInfo *PsDisplayInfo, hwc_layer_1_t *psLayer)
{
    IMG_native_handle_t *handle = (IMG_native_handle_t *)psLayer->handle;
    if(!check_video(handle->iFormat))
    {
        goto no_3d;
    }
    switch(PsDisplayInfo->Current3DMode)
    {
        case DISPLAY_2D_LEFT:
        case DISPLAY_2D_TOP:
        case DISPLAY_3D_LEFT_RIGHT_HDMI:
        case DISPLAY_3D_TOP_BOTTOM_HDMI:
            return 1;
        default:
            return 0;
    }

 no_3d:
    return 0;
}

static inline bool check_support_blending(int format)
{
    switch(format) 
    {
        case HAL_PIXEL_FORMAT_RGBA_8888:
        case HAL_PIXEL_FORMAT_RGBX_8888:
        case HAL_PIXEL_FORMAT_RGB_888:
        case HAL_PIXEL_FORMAT_RGB_565:
        case HAL_PIXEL_FORMAT_BGRA_8888:
        // case HAL_PIXEL_FORMAT_sRGB_A_8888:
        // case HAL_PIXEL_FORMAT_sRGB_X_8888:
        case HAL_PIXEL_FORMAT_BGRX_8888:
            return 1;
        default:
            return 0;
    }
}

static inline int check_is_blending(hwc_layer_1_t *psLayer)
{
	return (psLayer->blending != HWC_BLENDING_NONE);
}

static inline int check_is_premult(hwc_layer_1_t *psLayer)
{
    return (psLayer->blending == HWC_BLENDING_PREMULT);
}

static bool inline check_same_scale(float SRWscaleFac, float SRHscaleFac,
        float DTWscalFac, float DTHscaleFac )
{
    return (((SRWscaleFac - DTWscalFac) > -0.001) && ((SRWscaleFac - DTWscalFac) < 0.001))
           &&(((SRHscaleFac - DTHscaleFac) > -0.001) && ((SRHscaleFac - DTHscaleFac) < 0.001));
}

static bool check_is_scale(const DisplayInfo *PsDisplayInfo, hwc_layer_1_t *layer,
        float *WScaleFactor, float *HScaleFactor, bool isvideo)
{
    bool ret = 0;

#ifdef HWC_1_3
    float w = layer->sourceCropf.right - layer->sourceCropf.left;
    float h = layer->sourceCropf.bottom - layer->sourceCropf.top;
    ret = ((layer->displayFrame.right - layer->displayFrame.left) != int(ceilf(w)))
            || ((layer->displayFrame.bottom - layer->displayFrame.top) != int(ceilf(h)));
#else
    int w = layer->sourceCrop.right - layer->sourceCrop.left;
    int h = layer->sourceCrop.bottom - layer->sourceCrop.top;
    if(check_swap_w_h(layer->transform))
    {
        int swap = w ;
        w = h;
        h = swap;
    }
    if(isvideo
        && (PsDisplayInfo->Current3DMode == DISPLAY_2D_LEFT
            || PsDisplayInfo->Current3DMode == DISPLAY_3D_LEFT_RIGHT_HDMI))
    {
        w /= 2;
    }
    if(isvideo
        && (PsDisplayInfo->Current3DMode == DISPLAY_2D_TOP
            || PsDisplayInfo->Current3DMode == DISPLAY_3D_TOP_BOTTOM_HDMI))
    {
        h /= 2;
    }
    ret = ((layer->displayFrame.right - layer->displayFrame.left) != w)
            || ((layer->displayFrame.bottom - layer->displayFrame.top) != h);
#endif
    if(ret)
    {
        *WScaleFactor = float(layer->displayFrame.right - layer->displayFrame.left) / float(w);
        *HScaleFactor = float(layer->displayFrame.bottom - layer->displayFrame.top) / float(h);
    }
    return ret;
}

static AssignDUETO_T check_valid_layer(hwc_layer_1_t *layer, bool is_cursor)
{
    IMG_native_handle_t *handle = (IMG_native_handle_t *)layer->handle;
    
    if ((layer->flags & HWC_SKIP_LAYER))
    {
        return D_SKIP_LAYER;
    }
    
    if (!check_valid_format(handle->iFormat))
    {
        return D_NO_FORMAT;
    }
   
    if (layer->compositionType == HWC_BACKGROUND)
    {
        return D_BACKGROUND;
    }

    if(layer->transform && !is_cursor
#if defined(HAS_HW_ROT)
        && !(check_video(handle->iFormat) 
            && hwc_support_rotate(layer->transform)
            && hwc_rotate_mem(handle))
#endif
        )
    {
        return D_TR_N_0;
    }

    return I_OVERLAY;
}

static bool inline hwc_format_info(format_info *m_format_info, int format)
{
    memset(m_format_info, 0, sizeof(format_info));
    m_format_info->bpp = 32;
    m_format_info->plannum = 1;
    m_format_info->plnbbp[0] = 32;
    m_format_info->planWscale[0] = 1;
    m_format_info->planHscale[0] = 1;
    m_format_info->align[0] = HW_ALIGN;
    m_format_info->swapUV = 0;
    switch(format)
    {
        case HAL_PIXEL_FORMAT_RGBA_8888:
            m_format_info->format = DISP_FORMAT_ABGR_8888;
        break;
        case HAL_PIXEL_FORMAT_RGBX_8888:
            m_format_info->format = DISP_FORMAT_XBGR_8888;
        break;
        case HAL_PIXEL_FORMAT_BGRA_8888:
            m_format_info->format = DISP_FORMAT_ARGB_8888;
        break;
	    case HAL_PIXEL_FORMAT_BGRX_8888:
	        m_format_info->format = DISP_FORMAT_XRGB_8888;
	    break;
        case HAL_PIXEL_FORMAT_RGB_888:
            m_format_info->format = DISP_FORMAT_BGR_888;
            m_format_info->plnbbp[0] = 24;
            m_format_info->bpp = 24;
            m_format_info->align[0] = HW_ALIGN;
        break;
        case HAL_PIXEL_FORMAT_RGB_565:
            m_format_info->format = DISP_FORMAT_RGB_565;
            m_format_info->plnbbp[0] = 16;
            m_format_info->bpp = 16;
            m_format_info->align[0] = HW_ALIGN;
        break;
        case HAL_PIXEL_FORMAT_YV12:
            m_format_info->format = DISP_FORMAT_YUV420_P;
            m_format_info->bpp = 12;
            m_format_info->plannum = 3;
            m_format_info->plnbbp[0] = 8;
            m_format_info->plnbbp[1] = 8;
            m_format_info->plnbbp[2] = 8;
            m_format_info->planWscale[1] = 2;
            m_format_info->planWscale[2] = 2;
            m_format_info->planHscale[1] = 2;
            m_format_info->planHscale[2] = 2;
            m_format_info->align[0] = YV12_ALIGN;
            m_format_info->align[1] = YV12_ALIGN / 2;
            m_format_info->align[2] = YV12_ALIGN / 2;
            m_format_info->swapUV = 1;
        break;
        case HAL_PIXEL_FORMAT_YCrCb_420_SP:
            m_format_info->format = DISP_FORMAT_YUV420_SP_VUVU;
            m_format_info->bpp = 12;
            m_format_info->plannum = 2;
            m_format_info->plnbbp[0] = 8;
            m_format_info->plnbbp[1] = 16;
            m_format_info->planWscale[0] = 1;
            m_format_info->planWscale[1] = 2;
            m_format_info->planHscale[0] = 1;
            m_format_info->planHscale[1] = 2;
            m_format_info->align[0] = YV12_ALIGN;
            m_format_info->align[1] = YV12_ALIGN / 2;
        break;
        case HAL_PIXEL_FORMAT_AW_NV12:
            m_format_info->format = DISP_FORMAT_YUV420_SP_UVUV;
            m_format_info->bpp = 12;
            m_format_info->plannum = 2;
            m_format_info->plnbbp[0] = 8;
            m_format_info->plnbbp[1] = 16;
            m_format_info->planWscale[1] = 2;
            m_format_info->planHscale[1] = 2;
            m_format_info->align[0] = YV12_ALIGN;
            m_format_info->align[1] = YV12_ALIGN / 2;
        break;
        default:
            ALOGE("Not support format 0x%x in %s", format, __FUNCTION__);
            return 1;
        }
    return 0;
}

static bool hwc_region_intersect(hwc_rect_t *rect0, hwc_rect_t *rect1, hwc_rect_t *rectx)
{
    hwc_rect_t tmprect;

    tmprect.left = rect0->left > rect1->left ? rect0->left : rect1->left;
    tmprect.right = rect0->right < rect1->right ? rect0->right : rect1->right;
    tmprect.top = rect0->top > rect1->top ? rect0->top : rect1->top;
    tmprect.bottom = rect0->bottom < rect1->bottom ? rect0->bottom : rect1->bottom;

    if((tmprect.left < tmprect.right) && (tmprect.top < tmprect.bottom))
    {
        if(rectx != NULL)
        {
            *rectx = tmprect;
        }
        return 1;//return 1 is intersect
    }
    if(rectx != NULL)
    {
        rectx->bottom = 0;
        rectx->left = 0;
        rectx->right = 0;
        rectx->top = 0;
    }
    return 0;
}

static int hwc_can_scale(HwcDisContext_t *Localctx, hwc_layer_1_t *psLayer, bool isvideo)
{
   int src_w, src_h, vsyncPeroid, lcd_h, lcd_w, dst_w;
   long long layer_line_peroid, lcd_line_peroid, de_freq;
#ifdef HWC_1_3
    src_w = int (ceilf(psLayer->sourceCropf.right - psLayer->sourceCropf.left));
    src_h = int (ceilf(psLayer->sourceCropf.bottom - psLayer->sourceCropf.top));
#else
    src_w = psLayer->sourceCrop.right - psLayer->sourceCrop.left;
    src_h = psLayer->sourceCrop.bottom - psLayer->sourceCrop.top;
#endif
    if(check_swap_w_h(psLayer->transform))
    {
        int swap = src_w;
        src_w = src_h;
        src_h = swap;
    }
    const DisplayInfo *PsDisplayInfo = Localctx->psDisplayInfo;
    lcd_w = PsDisplayInfo->VarDisplayWidth;
    lcd_h = PsDisplayInfo->VarDisplayHeight;
	dst_w = (int)(psLayer->displayFrame.right * Localctx->WidthScaleFactor)
                - (int)(psLayer->displayFrame.left * Localctx->WidthScaleFactor);
    vsyncPeroid =
        (PsDisplayInfo->DisplayVsyncP ? PsDisplayInfo->DisplayVsyncP : (1000000000/60));

    if(PsDisplayInfo->Current3DMode == DISPLAY_3D_LEFT_RIGHT_HDMI && isvideo)
    {
        dst_w /= 2;
    }
    if(PsDisplayInfo->Current3DMode != DISPLAY_2D_ORIGINAL)
    {
        vsyncPeroid /= 2;
    }
    de_freq = 254000000;
	lcd_line_peroid = vsyncPeroid / lcd_h;
    layer_line_peroid = (src_w > dst_w)
            ? (1000000*((long long)(lcd_w - dst_w + src_w))/(de_freq/1000))
                : (1000000*((long long)lcd_w)/(de_freq/1000));

    if((lcd_line_peroid *4/5) < layer_line_peroid)
        return 0; //can't
    else
        return 1;//can
}

static inline int find_channel_layer(ChannelInfo_t *ChannelInfo, bool last_or_frst)
{
   layer_info_t *layerinfo = NULL;
   if(ChannelInfo->HwLayerCnt == 0)
   {
        return -1;
   }
   if(!last_or_frst)
   {
        layerinfo = ChannelInfo->HwLayer[0];
   }else{
        layerinfo = ChannelInfo->HwLayer[ChannelInfo->HwLayerCnt-1];
   }
   return layerinfo == NULL ? -1 : layerinfo->OrigOrder;
}

static int match_nofull_channel(HwcDisContext_t *Localctx, int channel,
        bool isvideo, int emptynum, int format, float SRWscaleFac, float SRHscaleFac, unsigned char planealpha)
{
    ChannelInfo *CH = NULL;
    const DisplayInfo *PsDisplayInfo = Localctx->psDisplayInfo;
    int whilecnt, tmpch;
    (channel >= 0 && channel < PsDisplayInfo->HwChannelNum)
        ? (whilecnt = 1, tmpch = channel ): (whilecnt = Localctx->HwCHUsedCnt +1, tmpch = 0);

    while(whilecnt --)
    {
        CH = &Localctx->ChannelInfo[tmpch];
        if( CH->HwLayerCnt != 0
            ? ((CH->HwLayerCnt + emptynum) <= PsDisplayInfo->LayerNumofCH
                && (isvideo ? ((CH->hasVideo) && (CH->iCHFormat == format)) : (CH->hasVideo ? 0 : 1))
                && (CH->planeAlpha == planealpha)
                && check_same_scale(CH->WTScaleFactor, CH->HTScaleFactor, SRWscaleFac, SRHscaleFac))
            : 1 )
        {
            return tmpch;
        }
        tmpch++;
    }
    return -1;
}

int check_cross_list(hwc_layer_1_t *psLayer, HwcDisContext_t *Localctx, int from,
        int to, int channel, HwcAssignStatus type)
{
    const DisplayInfo *PsDisplayInfo = Localctx->psDisplayInfo;
    layer_info_t *dispinfo = Localctx->psAllLayer;
    dispinfo += from;
    while(from >= 0 && from <= to && to <= Localctx->numberofLayer)
    {
        if((dispinfo->psLayer != NULL)
            && ((channel >= -1 && channel < PsDisplayInfo->HwChannelNum) ? dispinfo->virchannel == channel : 1)
            && ((type == ASSIGN_GPU || type == ASSIGN_OVERLAY) ? dispinfo->assigned == type : 1)
            && hwc_region_intersect(&dispinfo->psLayer->displayFrame, &psLayer->displayFrame, NULL))
        {
            return 1;
        }
        dispinfo++;
        from++;
    }
    return 0;
}

AssignDUETO_T static calculate_memthruput(HwcDisContext_t *Localctx, hwc_layer_1_t *psLayer,
        float WscalFac, float HscaleFac, int channel, bool isFB, bool isvideo)
{
    int memoflayer = 0, whilecnt = 0;
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    format_info format_info;
    IMG_native_handle_t* handle = (IMG_native_handle_t*)psLayer->handle;
    ChannelInfo_t *channelinfo;
    hwc_rect_t rectx = {0,0,0,0};
    hwc_rect_t rectx2 = {0,0,0,0};
    hwc_rect_t *psdiplay = &psLayer->displayFrame;
    int prememvideo;
    prememvideo = Localctx->prememvideo;

    channelinfo = &Localctx->ChannelInfo[channel];
    if(isFB)
    {
        memoflayer = Localctx->psDisplayInfo->fb_thruput;
        if(Globctx->currentmem + memoflayer > Localctx->psDisplayInfo->max_thruput)
        {
            goto need_resigned;
        }
        goto fix_mem;
    }
    hwc_format_info(&format_info,handle->iFormat);
    memoflayer = (psdiplay->right - psdiplay->left) /WscalFac 
                * (psdiplay->bottom - psdiplay->top) / HscaleFac 
                * format_info.bpp / 8;
    /*here is a round count,the channel has a same scalefactor,so is ok*/
    if(check_3d_video(Localctx->psDisplayInfo, psLayer))
    {
        memoflayer *= 2;
    }
    if(isvideo)
    {
        prememvideo -= memoflayer;
        if(prememvideo <= 0)
        {
            prememvideo = 0;
        }
    }
    if(Localctx->unasignedVideo <= 0)
    {
        prememvideo = 0;
        Localctx->prememvideo = 0;
    }
    for(whilecnt = 0; whilecnt < channelinfo->HwLayerCnt; whilecnt++)
    {
        if(channelinfo->HwLayer[whilecnt] != NULL
            && hwc_region_intersect(psdiplay, &channelinfo->HwLayer[whilecnt]->psLayer->displayFrame, &rectx))
        {
            memoflayer -= (rectx.right - rectx.left) / WscalFac
                        * (rectx.bottom - rectx.top) / HscaleFac * format_info.bpp / 8;
            if(memoflayer <= 0)
            {
                memoflayer = 0;
            }
        }
    }
    if(channelinfo->HwLayerCnt > 1)
    {
        whilecnt = channelinfo->HwLayerCnt - 1;
        while(whilecnt)
        {
            if(hwc_region_intersect(psdiplay, &channelinfo->rectx[whilecnt - 1], &rectx2))
            {
                memoflayer += (rectx2.right - rectx2.left) / WscalFac
                            * (rectx2.bottom - rectx2.top) / HscaleFac
                            * format_info.bpp / 8;
            }
            whilecnt--;
        }
    }
    if(channelinfo->HwLayerCnt == 3)
    {
        if(hwc_region_intersect(&channelinfo->rectx[1], &channelinfo->rectx[0], &channelinfo->rectx[2]))
        {
            if(hwc_region_intersect(psdiplay, &channelinfo->rectx[2], &rectx2))
            {
                memoflayer -= (rectx2.right - rectx2.left) / WscalFac
                                * (rectx2.bottom - rectx2.top) / HscaleFac
                                * format_info.bpp / 8;
            }
        }
    }
    if(memoflayer <= 0)
    {
        memoflayer = 0;
    }

    if((Globctx->currentmem
            + memoflayer
            + prememvideo
            + (Localctx->UsedFB ? Localctx->psDisplayInfo->fb_thruput : 0)
        ) <= Localctx->psDisplayInfo->max_thruput)
    {
        if(channelinfo->HwLayerCnt > 0)
        {
            channelinfo->rectx[channelinfo->HwLayerCnt - 1] = rectx;
        }
        if(isvideo)
        {
            Localctx->prememvideo -= memoflayer;
            if(Localctx->prememvideo <= 0)
            {
                Localctx->prememvideo = 0;
            }
        }
    }else{
        goto overflow;
    }
fix_mem:
    Localctx->current_thruput += memoflayer;
    channelinfo->memthruput += memoflayer;
    Globctx->currentmem += memoflayer;
    return I_OVERLAY;

overflow:
    return D_NO_MEM;

need_resigned:
    return D_NO_PIPE;
}

static bool check_same_diplay(hwc_display_contents_1_t **psDisplay,
        int num, int *psrotate)
{
    hwc_display_contents_1_t *priDisp = psDisplay[0];
    hwc_display_contents_1_t *cmpDisp = psDisplay[num];
    IMG_native_handle_t* handle = NULL;
    int rotate = -1;
    int cnt;
    bool sure = 0;
    if((cmpDisp != NULL) && (cmpDisp->numHwLayers != priDisp->numHwLayers))
    {
        goto unsame;
    }
    handle = (IMG_native_handle_t*)cmpDisp->outbuf;
    cnt = cmpDisp->numHwLayers - 1;
    while(cnt--)
    {
        if(cmpDisp->hwLayers[cnt].handle != priDisp->hwLayers[cnt].handle)
        {
            goto unsame;
        }
        if(rotate == -1 || !sure)
        {
            switch(priDisp->hwLayers[cnt].compositionType)
            {
                case 0:
                    sure = 1;
                    switch (priDisp->hwLayers[cnt].transform)
                    {
                        case 0:
                            rotate = cmpDisp->hwLayers[cnt].transform;
                        break;
                        case HAL_TRANSFORM_ROT_90:
                            rotate = HAL_TRANSFORM_ROT_270;
                        break;
                        case HAL_TRANSFORM_ROT_180:
                            sure = 0;
                            rotate = -1;
                        break;
                        case HAL_TRANSFORM_ROT_270:
                            rotate = HAL_TRANSFORM_ROT_90;
                        break;
                        default:
                            rotate = -1;
                    }
                break;
                case 1:
                    sure = 1;
                    rotate = cmpDisp->hwLayers[cnt].transform;
                break;
                default:
                    rotate = -1;
            }
        }
    }
    if(rotate == -1)
    {
        goto unsame;
    }
    if(psrotate != NULL)
    {
        *psrotate = rotate;
    }
    return 1;
unsame:
    return 0;
}

static inline long long recalc_coordinate(const float percent, const long long srcScreenMiddle,
            const long long dstScreenMiddle, const long long coordinate)
{
    long long diff = 0;
    diff = srcScreenMiddle - coordinate;
    diff *= percent;
    return  dstScreenMiddle - diff;
}

static void resize_crop(rect64 *fb_crop, hwc_layer_1_t *psLayer, int *buffer_w, int *buffer_h)
{
    hwc_rect_t sourceCrop;
    IMG_native_handle_t* handle = (IMG_native_handle_t*)psLayer->handle;
    int handle_w, handle_h;
    format_info format_info;
    int swap, s_left, s_right, s_top, s_bottom;
    hwc_format_info(&format_info,handle->iFormat);

    handle_w = ALIGN(handle->iWidth,format_info.align[0]);
    handle_h = handle->iHeight;
#ifdef HWC_1_3

    sourceCrop.left = int(ceilf(psLayer->sourceCropf.left)) < 0  ? 0 : int(ceilf(psLayer->sourceCropf.left));
    sourceCrop.right = int(floorf(psLayer->sourceCropf.right)) < 0 ? 0 : int(floorf(psLayer->sourceCropf.right));
    sourceCrop.top = int(ceilf(psLayer->sourceCropf.top)) < 0 ? 0 : int(ceilf(psLayer->sourceCropf.top));
    sourceCrop.bottom = int(floorf(psLayer->sourceCropf.bottom)) < 0 ? 0 : int(floorf(psLayer->sourceCropf.bottom));
#else
    sourceCrop.left = psLayer->sourceCrop.left < 0 ? 0 : psLayer->sourceCrop.left;
    sourceCrop.right = psLayer->sourceCrop.right < 0 ? 0 : psLayer->sourceCrop.right;
    sourceCrop.top = psLayer->sourceCrop.top < 0 ? 0 : psLayer->sourceCrop.top;
    sourceCrop.bottom = psLayer->sourceCrop.bottom < 0 ? 0 : psLayer->sourceCrop.bottom;
#endif
    s_left = sourceCrop.left;
    s_top = sourceCrop.top;
    s_right = sourceCrop.right;
    s_bottom = sourceCrop.bottom;
    /*180 == HAL_TRANSFORM_FLIP_V | HAL_TRANSFORM_FLIP_H
     HAL_TRANSFORM_ROT_270 = HAL_TRANSFORM_FLIP_V | HAL_TRANSFORM_FLIP_H | HAL_TRANSFORM_ROT_90*/
    if((psLayer->transform & HAL_TRANSFORM_FLIP_V) == HAL_TRANSFORM_FLIP_V)
    {
        s_top = (handle_h - sourceCrop.bottom)>0 ? (handle_h - sourceCrop.bottom) : 0;
        s_bottom = (handle_h - sourceCrop.top)>0 ? (handle_h - sourceCrop.top) : handle_h;
    }

    if((psLayer->transform & HAL_TRANSFORM_FLIP_H) == HAL_TRANSFORM_FLIP_H)
    {
        s_right = (handle_w - sourceCrop.left)>0 ? (handle_w  - sourceCrop.left) : handle_w;
        s_left =  (handle_w - sourceCrop.right)>0 ? (handle_w - sourceCrop.right) : 0;
    }

    if((psLayer->transform & HAL_TRANSFORM_ROT_90) == HAL_TRANSFORM_ROT_90)
    {
        swap = s_left;
        s_left = (handle_h - s_bottom)>0 ? (handle_h - s_bottom) : 0;
        s_bottom = s_right;
        s_right = (handle_h - s_top)>0 ? (handle_h - s_top) : handle_h;
        s_top =  swap;

        swap = handle_h;
        handle_h = handle_w;
        handle_w = swap;
    }
    fb_crop->left = (long long)(((long long)(s_left)) << 32);
    fb_crop->top = (long long)(((long long)(s_top)) << 32);
    fb_crop->right = (long long)(((long long)(s_right)) << 32);
    fb_crop->bottom = (long long)(((long long)(s_bottom)) << 32);
    *buffer_w = handle_w;
    *buffer_h = handle_h;
}

static bool resize_layer(HwcDisContext_t *Localctx,
        disp_layer_info *layer_info, layer_info_t *hwc_layer)
{
    bool isoutlayer = 0;
    long long src_cut = 0, dst_cut = 0, coordinate = 0;
    int handle_w, handle_h;
    hwc_layer_1_t *psLayer = hwc_layer->psLayer;
    float WScaleFactor = 1.0 , HScaleFactor = 1.0;
    IMG_native_handle_t* handle = (IMG_native_handle_t*)psLayer->handle;
    bool cut_left = 0 ,cut_right = 0 , cut_top = 0, cut_bottom = 0;
    const DisplayInfo *PsDisplayInfo = Localctx->psDisplayInfo;
    rect64  Layer_crop[4];
    rect64 *fb_crop = &Layer_crop[0];
    rect64 *screen_win = &Layer_crop[1];
    rect64 *Iscn_bound = &Layer_crop[2];
    rect64 *Vscn_bound = &Layer_crop[3];

    screen_win->left = (long long)(((long long)(psLayer->displayFrame.left)) << 32);
    screen_win->top = (long long)(((long long)(psLayer->displayFrame.top)) << 32);
    screen_win->right = (long long)(((long long)(psLayer->displayFrame.right)) << 32);
    screen_win->bottom = (long long)(((long long)(psLayer->displayFrame.bottom)) << 32);

    Iscn_bound->left = 0;
    Iscn_bound->top = 0;
    Iscn_bound->right = (long long)(((long long)(PsDisplayInfo->InitDisplayWidth)) << 32);
    Iscn_bound->bottom = (long long)(((long long)(PsDisplayInfo->InitDisplayHeight)) << 32);

    Vscn_bound->left = 0;
    Vscn_bound->top = 0;
    Vscn_bound->right = (long long)(((long long)(PsDisplayInfo->VarDisplayWidth)) << 32);
    Vscn_bound->bottom = (long long)(((long long)(PsDisplayInfo->VarDisplayHeight)) << 32);

    resize_crop(fb_crop, psLayer, &handle_w, &handle_h);

    if(check_is_scale(PsDisplayInfo, psLayer, &WScaleFactor, &HScaleFactor, hwc_layer->isvideo))
    {
        if( screen_win->left < 0)
        {
            dst_cut = 0 - screen_win->left;
            screen_win->left = 0;
            fb_crop->left += (long long)(dst_cut / WScaleFactor);
            cut_left = 1;
        }
        if(screen_win->right > Iscn_bound->right)
        {
            dst_cut = screen_win->right - Iscn_bound->right;
            screen_win->right = Iscn_bound->right;
            fb_crop->right -=  (long long)(dst_cut / WScaleFactor);
            cut_right = 1;
        }
        if(screen_win->top < 0)
        {
            dst_cut = 0 - screen_win->top;
            screen_win->top = 0;
            fb_crop->top += (long long)(dst_cut / HScaleFactor);
            cut_top = 1;
        }
        if(screen_win->bottom > Iscn_bound->bottom)
        {
            dst_cut = screen_win->bottom - Iscn_bound->bottom;
            screen_win->bottom = Iscn_bound->bottom;
            fb_crop->right -= (long long)(dst_cut / HScaleFactor);
            cut_bottom = 1;
        }
        if(fb_crop->right > ((long long)((long long)(handle_w)) << 32))
        {
            src_cut = fb_crop->left + fb_crop->right - ((long long) ((long long)(handle_w)) << 32);
            fb_crop->right -= src_cut;
            cut_left = 1;
        }
        if(fb_crop->bottom > ((long long)((long long)(handle_h)) << 32))
        {
            src_cut = fb_crop->top + fb_crop->bottom - ((long long) ((long long)(handle_h)) << 32);
            fb_crop->bottom -= src_cut;
            cut_bottom = 1;
        }
    }

    if(!check_same_scale(Localctx->WidthScaleFactor, Localctx->HighetScaleFactor, 1.0, 1.0) && !isoutlayer) //can >100
    {
        coordinate =
            recalc_coordinate(Localctx->WidthScaleFactor, Iscn_bound->right>>1,
                                            Vscn_bound->right>>1, screen_win->left);
        if(coordinate >= Vscn_bound->right)
        {
           isoutlayer = 1;
        }else if( coordinate < 0){
            dst_cut = 0 - coordinate;
            screen_win->left = 0;
            src_cut = (long long)(dst_cut / Localctx->WidthScaleFactor / WScaleFactor);
            fb_crop->left += src_cut;
            cut_left = 1;
        }else{
            screen_win->left = coordinate;
        }
        coordinate =
            recalc_coordinate(Localctx->WidthScaleFactor, Iscn_bound->right>>1,
                                        Vscn_bound->right>>1, screen_win->right);
        if(coordinate >= Vscn_bound->right)
        {
            dst_cut = coordinate - Vscn_bound->right;
            screen_win->right = Vscn_bound->right;
            src_cut = (long long)(dst_cut / Localctx->WidthScaleFactor / WScaleFactor);
            fb_crop->right -= src_cut;
            cut_right = 1;
        }else if(coordinate <= 0){
            isoutlayer = 1;
        }else{
            screen_win->right = coordinate;
        }
        coordinate =
            recalc_coordinate(Localctx->HighetScaleFactor, Iscn_bound->bottom>>1,
                                            Vscn_bound->bottom>>1, screen_win->top);
        if(coordinate >= Vscn_bound->bottom)
        {
            isoutlayer = 1;
        }else if(coordinate <= 0){
            dst_cut = 0 - coordinate;
            screen_win->top = 0;
            src_cut = (long long)(dst_cut / Localctx->HighetScaleFactor / HScaleFactor);
            fb_crop->top += src_cut;
            cut_top = 1;
        }else{
            screen_win->top = coordinate;
        }
        coordinate =
            recalc_coordinate(Localctx->HighetScaleFactor, Iscn_bound->bottom>>1,
                                        Vscn_bound->bottom>>1, screen_win->bottom);
        if(coordinate <= 0)
        {
            isoutlayer = 1;
        }else if(coordinate >= Vscn_bound->bottom){
            dst_cut =  coordinate - Vscn_bound->bottom;
            screen_win->bottom = Vscn_bound->bottom;
            src_cut = (long long)(dst_cut / Localctx->HighetScaleFactor / HScaleFactor);
            fb_crop->bottom -= src_cut;
            cut_right = 1;
        }else{
            screen_win->bottom= coordinate;
        }
    }
#if defined(HWC_DEBUG)
        ALOGD("\ndestation\n[%f,%f]#S[%lld,%lld,%lld,%lld] F[%lld,%lld,%lld,%lld]\n",
            Localctx->WidthScaleFactor, Localctx->HighetScaleFactor,
            fb_crop->left>>32, fb_crop->top>>32, fb_crop->right>>32,
            fb_crop->bottom>>32, screen_win->left>>32, screen_win->top>>32,
            screen_win->right>>32, screen_win->bottom>>32);
#endif
    if( screen_win->top >= screen_win->bottom
        || screen_win->right <= screen_win->left
        || fb_crop->top >= fb_crop->bottom
        || fb_crop->left >= fb_crop->right)
    {
        isoutlayer = 1;
    }
    if(!isoutlayer)
    {
        long long srcdiff, destdiff, cut_mod;
        srcdiff = fb_crop->right - fb_crop->left;
        destdiff = screen_win->right - screen_win->left;
        float step = (srcdiff << 18) / destdiff;
        layer_info->screen_win.x  = (!cut_right) ? (int)(screen_win->left >> 32)
                : ((screen_win->right >> 32) - (screen_win->left >> 32) - (destdiff >> 32)) > 0
                    ? (int)(screen_win->left>>32)+1 : (int)(screen_win->left >> 32);

        layer_info->screen_win.width = (int)(destdiff >> 32) ? (int)(destdiff >> 32) :1;
        cut_mod = (long long)(step*((float)(destdiff - (((long long)layer_info->screen_win.width) << 32))
                                            / (((long long)1) << 32))*(((long long)1) << 14));

        layer_info->fb.crop.x = fb_crop->left + ((cut_left == 1) ? cut_mod:0);
        layer_info->fb.crop.width = srcdiff - cut_mod;

        srcdiff = fb_crop->bottom - fb_crop->top;
        destdiff = screen_win->bottom - screen_win->top;
        step = (srcdiff << 18) /destdiff;

        layer_info->screen_win.y = (!cut_bottom) ? (int)(screen_win->top >> 32)
                    : ((screen_win->bottom >> 32) - (screen_win->top >> 32) - (destdiff >> 32)) > 0
                        ? ((int)(screen_win->top >> 32) + 1) :((int)(screen_win->top >> 32));

        layer_info->screen_win.height = (int)(destdiff >> 32) ? (int)(destdiff >> 32) : 1;

        cut_mod = (long long)(step * ((float)(destdiff - (((long long)layer_info->screen_win.height) << 32))
                        / (((long long)1) << 32)) * (((long long)1) << 14));
	    layer_info->fb.crop.y = fb_crop->top + ((cut_top == 1) ? cut_mod:0);
		layer_info->fb.crop.height = srcdiff - cut_mod;
    }
    if(layer_info->b_trd_out == 1)
    {
        switch(PsDisplayInfo->Current3DMode)
        {
            case DISPLAY_3D_LEFT_RIGHT_HDMI:
                layer_info->screen_win.x = 0;
                layer_info->screen_win.y = 0;
                layer_info->screen_win.width = 1920;
                layer_info->screen_win.height = 1080;
                layer_info->fb.flags = DISP_BF_STEREO_SSH;
                break;
            case DISPLAY_3D_TOP_BOTTOM_HDMI:
                layer_info->screen_win.x = 0;
                layer_info->screen_win.y = 0;
                layer_info->screen_win.width = 1920;
                layer_info->screen_win.height = 1080;
                layer_info->fb.flags = DISP_BF_STEREO_TB;
                break;
            default:
                    ALOGD("Not support 3D format");
            }
    }
    return isoutlayer;
}

static inline int cal_pre_channel(HwcDisContext_t *Localctx, bool isFB, bool isvideo)
{
    const DisplayInfo *psDisplayInfo = Localctx->psDisplayInfo;
    int prechnnl = 0;
    int prevideoch = 0;
    prechnnl += (isFB ? 0: Localctx->UsedFB);
    if((Localctx->unasignedVideo > 0) && (Localctx->VideoCHCnt < (psDisplayInfo->VideoCHNum - 1)))
    {
        if(Localctx->unasignedVideo
            > (psDisplayInfo->VideoCHNum
                -(Localctx->VideoCHCnt >= 0 ? (Localctx->VideoCHCnt+ 1):0)))
        {
            prevideoch = psDisplayInfo->VideoCHNum
                        - (Localctx->VideoCHCnt >= 0 ? (Localctx->VideoCHCnt+ 1):0)
                        - isvideo;
        }else{
            prevideoch = Localctx->unasignedVideo - isvideo;
        }
    }
    prechnnl += prevideoch;
    return prechnnl;
}

bool match_format(layer_info_t *psHwlayer_info, disp_layer_info *layer_info)
{
    IMG_native_handle_t *handle;
    handle = (IMG_native_handle_t *)( psHwlayer_info->psLayer->handle);
    bool err = 0;
    int i = 0, stride = 0;
    format_info format_info;

    layer_info->fb.addr[0] = ion_get_addr_fromfd(handle->fd[0]);
    if(layer_info->fb.addr[0] == 0)
    {
        err = 1;
        ALOGD("hwc get addr err...");
        goto ret_ok;
    }
    psHwlayer_info->shared_fd = handle->fd[0];
    err = hwc_format_info(&format_info, handle->iFormat);
    if(!err)
    {
        layer_info->fb.format = format_info.format;
        stride = ALIGN(handle->iWidth ,format_info.align[0]);
        i = 0;
        while(i < format_info.plannum)
        {
            layer_info->fb.size[i].width =
                ALIGN(stride / format_info.planWscale[i], format_info.align[i]);
            layer_info->fb.size[i].height = handle->iHeight / format_info.planHscale[i];
            layer_info->fb.align[i] = format_info.align[i];
            if(i > 0)
            {
                layer_info->fb.addr[i] = layer_info->fb.addr[i-1] +
                                      (layer_info->fb.size[i-1].width * layer_info->fb.size[i-1].height);
            }
            i++;
        }
        if(format_info.swapUV)
        {
            unsigned long long addr = layer_info->fb.addr[1];
            layer_info->fb.addr[1] = layer_info->fb.addr[2];
            layer_info->fb.addr[2] = addr;
        }
        if(format_info.format == DISP_FORMAT_YUV420_SP_VUVU)
        {
            layer_info->fb.addr[1] = ALIGN(layer_info->fb.addr[1], 4096);
        }
    }
    if(psHwlayer_info->is3D)
    {
        layer_info->b_trd_out = 1;
        layer_info->out_trd_mode = DISP_3D_OUT_MODE_FP;
    }
ret_ok:
    return err;

}

HwcAssignStatus hwc_try_assign_layer(HwcDisContext_t *Localctx, size_t singcout, int zOrder)
{

    bool needchannel = 1, isvideo = 0, isalpha = 0, isFB = 0, issecure = 0;
    bool is3D = 0, need_sync = 0, is_cursor = 0;
    float WscalFac = 1.0, HscaleFac = 1.0;
    int CH= -1, tmCnt1 = 0, tmCnt2 = 0, addLayerCnt = 1;
    int CHdiff = 0, tmpCH = 0, prechl = 0;
    AssignDUETO_T dueto = I_OVERLAY;
    unsigned char planealpha = 0xff;
    hwc_layer_1_t *psLayer = NULL;
    psLayer = Localctx->psAllLayer[singcout].psLayer;
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;

    const DisplayInfo  *PsDisplayInfo = Localctx->psDisplayInfo;
    IMG_native_handle_t* handle = (IMG_native_handle_t*)psLayer->handle;
    ChannelInfo_t *psCH = Localctx->ChannelInfo;
    if(psLayer->compositionType == HWC_FRAMEBUFFER_TARGET )
    {
        isFB = 1;
        isalpha = 1;
        goto needchannel;
    }
    isalpha = check_is_blending(psLayer);
    if(isalpha && singcout == 0)
    {
        psLayer->blending = HWC_BLENDING_NONE;
    }
    if(handle == NULL)
    {
        ALOGV("%s:Buffer handle is NULL", __func__);
        Localctx->UsedFB = 1;
        dueto = D_NULL_BUF;
	    goto assign_gpu;
    }
    isvideo = check_video(handle->iFormat);
    is3D = check_3d_video(PsDisplayInfo, psLayer);
    is_cursor = check_cursor(psLayer, singcout, Localctx->numberofLayer);
    if(is3D)
    {
        /*ont only the Video has 3D*/
        addLayerCnt = 2;
    }
    
    if(!(handle->uiFlags & PVRSRV_MEM_CONTIGUOUS))
    {
        ALOGV("%s:not continuous Memory", __func__);
        Localctx->UsedFB = 1;
        dueto = D_CONTIG_MEM;
	    goto assign_gpu;
    }
    
	if(check_usage_protected(handle))
	{
        if(!PsDisplayInfo->issecure)
        {
            ALOGV("%s:Video Protected", __func__);
            dueto = D_VIDEO_PD;
	        goto assign_gpu;
        }else{
            issecure = 1;
        }
	}
    
    dueto = check_valid_layer(psLayer, is_cursor);
    if(dueto != I_OVERLAY)
    {
        ALOGV("check_valid_layer:0x%08x", handle->iFormat);
        goto assign_gpu;
    }
    if(check_usage_sw_write(handle))
    {
        need_sync = 1;
    }
    if(check_stop_hwc(handle))
    {
        ALOGV("we need stop hwc");
        dueto = D_STOP_HWC;
        goto assign_gpu;
    }

    if(isvideo
        && psLayer->transform
        && (Globctx->has_tr_count >= Globctx->tr_count_limit || Globctx->stop_rotate_hw))
    {
        ALOGV("we have have a tr video% d", Globctx->stop_rotate_hw);
        dueto = D_TR_N_0;
        goto assign_gpu;
    }
    if(is_cursor)
    {
        goto needchannel;
    }
    if(isalpha)
    {
        planealpha = psLayer->planeAlpha;
        /*cann't distinguish only pixel alpha*/

        if(!check_support_blending(handle->iFormat))
        {
            ALOGV("not surpport alpha layer");
            dueto = D_ALPHA;
			goto assign_gpu;
        }
        if(Localctx->UsedFB)
        {
            if(check_cross_list(psLayer, Localctx, 0, singcout-1, -1, ASSIGN_GPU))
            {
                dueto = D_X_FB;
                goto assign_gpu;
            }
        }
    }

    if(check_is_scale(PsDisplayInfo, psLayer, &WscalFac, &HscaleFac, isvideo)
        || !check_same_scale(Localctx->WidthScaleFactor, Localctx->HighetScaleFactor, 1.0, 1.0))
    {
        switch(check_scale_format(handle->iFormat))
        {
            case 1:
			    if(!hwc_can_scale(Localctx, psLayer, isvideo))
			    {
                    ALOGV("Scale can not handle");
                    dueto = D_SCALE_OUT;
			        goto assign_gpu;
                }
            break;
            case 2:
                if(WscalFac * Localctx->WidthScaleFactor < 1
                    || WscalFac * Localctx->WidthScaleFactor > UI_FACTOR
                    || HscaleFac * Localctx->HighetScaleFactor < 1
                    || HscaleFac * Localctx->HighetScaleFactor > UI_FACTOR)
                {
                    ALOGV("Scale can not handle");
                    dueto = D_SCALE_OUT;
			        goto assign_gpu;
                }
            break;
            default:
                ALOGV("not support  scale layer");
                dueto = D_CANNT_SCALE;
			    goto assign_gpu;
        }
    }

    CH = Localctx->HwCHUsedCnt;
    tmpCH = match_nofull_channel(Localctx, -1, isvideo, addLayerCnt, handle->iFormat, WscalFac, HscaleFac, planealpha);

    while(tmpCH != -1 && CH !=-1 && CH >= tmpCH)
    {
        /*can assigned to the lowest Channel */
        tmCnt1 =  find_channel_layer(&Localctx->ChannelInfo[CH], 0);
        tmCnt2 =  find_channel_layer(&Localctx->ChannelInfo[CH], 1);
        if((CH == tmpCH) || (match_nofull_channel(Localctx, CH, isvideo, addLayerCnt, handle->iFormat, WscalFac, HscaleFac, planealpha) != -1))
        {
            if(check_cross_list(psLayer, Localctx, tmCnt1, tmCnt2, CH, ASSIGN_OVERLAY))
            {
                if(!isalpha)
                {
                    CHdiff = Localctx->HwCHUsedCnt - CH;
                    needchannel = 0;
                }
                CH = -1;
            }else{
                CHdiff = Localctx->HwCHUsedCnt - CH;
                needchannel = 0;
                CH--;
            }
        }else if(check_cross_list(psLayer, Localctx, tmCnt1, tmCnt2, CH, ASSIGN_OVERLAY))
        {
            CH = -1;
        }else{
            CH--;
        }
    }

needchannel:
    if(needchannel)
    {
        /* here is a fix  :UI has used video channel ,wether reasigend?  
           could add check video before hwc_try_assign_layer()
           but must check_cross_list() between the videos
        */
        prechl = cal_pre_channel(Localctx, isFB, isvideo);

        if((isvideo ? (Localctx->VideoCHCnt < PsDisplayInfo->VideoCHNum -1) : 1)
           && Localctx->HwCHUsedCnt < (PsDisplayInfo->HwChannelNum - prechl -1))
        {
            Localctx->HwCHUsedCnt++;
            isvideo ? Localctx->VideoCHCnt++ :0;
            isFB ? Localctx->UsedFB = 1 : 0;
        }else{
            goto assigned_need_resigne;
        }
    }
    /*check the mem thruput*/
    if(!is_cursor)
    {
        dueto = calculate_memthruput(Localctx, psLayer,
                    WscalFac, HscaleFac, Localctx->HwCHUsedCnt - CHdiff, isFB, isvideo);
        if(dueto != I_OVERLAY)
        {
            if(needchannel)
            {
                Localctx->HwCHUsedCnt--;
                isvideo ? Localctx->VideoCHCnt-- :0;
            }
            if(dueto == D_NO_PIPE)
            {
                goto assigned_need_resigne;
            }
            ALOGV("hardware mem hruput");
            goto assign_gpu;
        }
    }
    if(Localctx->UsedFB
        && !isalpha
        && !is_cursor
        && !isFB
        && check_cross_list(psLayer, Localctx, 0, singcout-1, -1, ASSIGN_GPU )
      )
    {
        psLayer->hints |= HWC_HINT_CLEAR_FB;
    }
    if(isalpha && is_cursor && Localctx->fb_has_alpha)
    {
        dueto = D_X_FB;
        goto assign_gpu;
    }
assign_overlay:

    Localctx->unasignedVideo -= isvideo;
    Localctx->countofhwlayer++;
    Localctx->has_tr += (isvideo && psLayer->transform);
    Globctx->has_tr_count += (isvideo && psLayer->transform);
    psCH[Localctx->HwCHUsedCnt - CHdiff].hasVideo = isvideo;
    psCH[Localctx->HwCHUsedCnt - CHdiff].iCHFormat =
                (handle == NULL ? HAL_PIXEL_FORMAT_BGRA_8888 : handle->iFormat);
    psCH[Localctx->HwCHUsedCnt - CHdiff].WTScaleFactor =
                WscalFac * Localctx->WidthScaleFactor;
    psCH[Localctx->HwCHUsedCnt - CHdiff].HTScaleFactor =
                HscaleFac * Localctx->HighetScaleFactor;
    psCH[Localctx->HwCHUsedCnt - CHdiff].planeAlpha = planealpha;
    psCH[Localctx->HwCHUsedCnt - CHdiff].HwLayer[psCH[Localctx->HwCHUsedCnt - CHdiff].HwLayerCnt]
            = &Localctx->psAllLayer[singcout];
    psCH[Localctx->HwCHUsedCnt - CHdiff].HwLayerCnt += addLayerCnt;
    psCH[Localctx->HwCHUsedCnt - CHdiff].isFB = isFB;
    psCH[Localctx->HwCHUsedCnt - CHdiff].hasBlend += isalpha;

    Localctx->psAllLayer[singcout].assigned = ASSIGN_OVERLAY;
    Localctx->psAllLayer[singcout].virchannel = Localctx->HwCHUsedCnt- CHdiff;
    Localctx->psAllLayer[singcout].HwzOrder = zOrder;
    Localctx->psAllLayer[singcout].OrigOrder = singcout;
    Localctx->psAllLayer[singcout].is3D = is3D;
    Localctx->psAllLayer[singcout].info = dueto;
    Localctx->psAllLayer[singcout].isvideo= isvideo;
    Localctx->psAllLayer[singcout].need_sync = issecure?0:need_sync;
    Localctx->psAllLayer[singcout].is_cursor = is_cursor;
    Localctx->psAllLayer[singcout].is_secure = issecure;
    if(is_cursor)
    {
        return ASSIGN_CURSOR;
    }
    return ASSIGN_OVERLAY;

assign_gpu:

    Localctx->unasignedVideo -= isvideo;
    Localctx->psAllLayer[singcout].assigned = ASSIGN_GPU;
    Localctx->psAllLayer[singcout].virchannel = -1;
    Localctx->psAllLayer[singcout].HwzOrder = -1;
    Localctx->psAllLayer[singcout].OrigOrder = singcout;
    Localctx->UsedFB = 1;
    Localctx->psAllLayer[singcout].info = dueto;
    Localctx->fb_has_alpha = isalpha;

    return ASSIGN_GPU;

assigned_need_resigne:
    if((!Localctx->UsedFB) || isFB)
    {
        Localctx->UsedFB = 1;
        return ASSIGN_NEEDREASSIGNED;
    }else{
        dueto = D_NO_PIPE;
        goto assign_gpu;
    }
}

int hwc_setup_layer(hwc_dispc_data_t *DisplayData, HwcDisContext_t *Localctx)
{
    int CHCnt = 0, LCnt = 0, zOrder = 0, VideoCnt=0, UiCnt = 0, ture_disp = -EINVAL;
    disp_layer_info *layer_info = NULL;
    hwc_layer_1_t *psLayer = NULL;
    layer_info_t *psHwlayer_info = NULL;
    disp_layer_config *psDisconfig = NULL;
    hwc_commit_layer_t *hw_layer_config = NULL;
    const DisplayInfo *PsDisplayInfo = Localctx->psDisplayInfo;
    ChannelInfo_t *psChannelInfo = Localctx->ChannelInfo;
    IMG_native_handle_t *handle = NULL;
    ture_disp = PsDisplayInfo->VirtualToHWDisplay;
    if(ture_disp < 0 || ture_disp >= NUMBEROFDISPLAY)
    {
        return -1;
    }
    if(DisplayData->force_flip[ture_disp])
    {
        ALOGD("hwc force_flip");
        return 1;
    }
    DisplayData->layer_num_inused[ture_disp] = Localctx->countofhwlayer;
    UiCnt = Localctx->VideoCHCnt + 1;
    while(CHCnt < PsDisplayInfo->HwChannelNum)
    {
        LCnt = 0;
        while(LCnt < PsDisplayInfo->LayerNumofCH)
        {
            psHwlayer_info = psChannelInfo[CHCnt].HwLayer[LCnt];
            hw_layer_config = &(DisplayData->hwc_layer_info[ture_disp][zOrder]);
            psDisconfig = &(hw_layer_config->hwc_layer_info);
            if(psHwlayer_info != NULL)
            {
                psLayer = psHwlayer_info->psLayer;
            }else{
                LCnt++;
                continue;
            }
            layer_info = &psDisconfig->info;
            if(psLayer == NULL)
            {
                LCnt++;
                continue;
            }
            if(match_format(psHwlayer_info, layer_info)
                || resize_layer(Localctx, layer_info, psHwlayer_info))
            {
                DisplayData->layer_num_inused[ture_disp]--;
                LCnt++;
                continue;
            }
            if(psLayer->acquireFenceFd >= 0)
            {
                hw_layer_config->aquirefencefd = dup(psLayer->acquireFenceFd);
            }else{
                hw_layer_config->aquirefencefd = -1;
            }
            hw_layer_config->needsync = psHwlayer_info->need_sync;
            hw_layer_config->share_fd = dup(psHwlayer_info->shared_fd);
            hw_layer_config->is_secure = psHwlayer_info->is_secure;
            if(check_is_blending(psLayer))
            {
                layer_info->alpha_mode = 2;
            }else{
                layer_info->alpha_mode = 1;
            }

            if(psLayer->blending == HWC_BLENDING_PREMULT)
            {
                layer_info->fb.pre_multiply = 1;
            }

            hw_layer_config->tr = psLayer->transform;
            if(hw_layer_config->tr != 0 && psHwlayer_info->isvideo)
            {
                DisplayData->tr_layer = zOrder;
                DisplayData->tr_in_disp = ture_disp;
            }
            layer_info->zorder = zOrder;
            layer_info->alpha_value = psChannelInfo[CHCnt].planeAlpha;

            psDisconfig->enable = 1;
            psDisconfig->layer_id = LCnt;
            psDisconfig->channel = psChannelInfo[CHCnt].hasVideo ? VideoCnt : UiCnt;
            psHwlayer_info->hwchannel = psDisconfig->channel;
            LCnt++;
            hw_layer_config->iscursor = psHwlayer_info->is_cursor;
            if(psHwlayer_info->is_cursor && !Localctx->fb_has_alpha)
            {
                /* if cursor,we must set it to the the biggest z-order,
                   if there is nothing alpha in fb, higher than fb
                */
                DisplayData->cursor_in_disp[ture_disp] = zOrder;
                layer_info->zorder = Localctx->countofhwlayer-1;
            }
            if(Localctx->UsedFB
                && (DisplayData->cursor_in_disp[ture_disp] != -1)
                && (zOrder == Localctx->countofhwlayer - 1)
                && psChannelInfo[CHCnt].isFB
                && !Localctx->fb_has_alpha
              )
            {
                layer_info->zorder = Localctx->countofhwlayer - 2;
            }
            zOrder++;
        }
        psChannelInfo[CHCnt].hasVideo ? VideoCnt++ :UiCnt++;
        CHCnt++;
    }
    return 1;
}

bool sunxi_prepare(hwc_display_contents_1_t **displays, size_t NumofDisp)
{
    bool forceSoftwareRendering = 0;
	size_t disp = 0, i = 0;
    HwcAssignStatus AssignStatus;
    int NeedReAssignedLayer = 0, SetOrder = 0, ture_disp = -EINVAL;
    hwc_layer_1_t *psLayer = NULL;
    HwcDisContext_t *Localctx = NULL;
    DisplayInfo   *PsDisplayInfo = NULL;
    hwc_display_contents_1_t *psDisplay = NULL;
	SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    /*we must use the de0 is the HWC_DISPLAY_PRIMARY*/
    reset_globle(Globctx);
    for(disp = 0; disp < NumofDisp; disp++)
    {
        NeedReAssignedLayer = 0;
        SetOrder = 0;
        psDisplay = displays[disp];
        PsDisplayInfo = &Globctx->SunxiDisplay[disp >= NUMBEROFDISPLAY ? 0 : disp];
        ture_disp = PsDisplayInfo->VirtualToHWDisplay;
        if( !psDisplay
            || psDisplay->numHwLayers <= 0
            || ture_disp == -EINVAL
#if !defined(HWC_1_3)
            || disp >= HWC_DISPLAY_VIRTUAL
#endif
           )
        {
            continue;
        }
        Localctx = &Globctx->DisContext[ture_disp];
        if(reset_local(PsDisplayInfo, Localctx, psDisplay))
        {
            continue;
        }

#if defined (HWC_1_3) || defined (SECEND_COULD_WB)
        if(disp == HWC_DISPLAY_VIRTUAL)
        {
            int tr;
            bool samedisp = check_same_diplay(displays, disp, &tr);
            if( samedisp
                && (tr? (Globctx->has_tr_count < Globctx->tr_count_limit) : 1)
              )
            {
                if(tr)
                {
                    Globctx->has_tr_count++;
                }
                Localctx->wb_tr = tr;
                Localctx->use_wb = 1;
                reset_layer_type(Localctx, psDisplay, HWC_OVERLAY);
                continue;
            }else{
                if(disp == HWC_DISPLAY_VIRTUAL)
                {
                    reset_layer_type(Localctx, psDisplay, HWC_FRAMEBUFFER);
                    continue;
                }
            }
        }
#endif
ReAssignedLayer:

    	for(i = (forceSoftwareRendering ? psDisplay->numHwLayers-1 : 0);
            i < psDisplay->numHwLayers;
            i++
            )
    	{
    		psLayer = &psDisplay->hwLayers[i];
            if(psLayer->compositionType == HWC_FRAMEBUFFER_TARGET)
            {
                if( forceSoftwareRendering
                    || Localctx->UsedFB
                    || psDisplay->numHwLayers == 1
                    || Globctx->ForceGPUComp)
                {
                    if( forceSoftwareRendering
                        || psDisplay->numHwLayers == 1
                        || (Globctx->ForceGPUComp
                            && Localctx->VideoCHCnt == -1
                            && (Localctx->current_thruput > PsDisplayInfo->fb_thruput)
                            )
                       )
                    {
                        SetOrder = 0;
                        Localctx->UsedFB = 1;
                        reset_local(PsDisplayInfo, Localctx, psDisplay);
                    }
                    if(!Localctx->UsedFB)
                    {
                        break;
                    }
                }else{
                    break;
                }
            }
            AssignStatus = hwc_try_assign_layer(Localctx, i, SetOrder);
    	    switch (AssignStatus)
    		{
                case ASSIGN_OVERLAY:
                    if(psLayer->compositionType == HWC_FRAMEBUFFER)
                    {
                        psLayer->compositionType = HWC_OVERLAY;
                    }
                    SetOrder++;
                break;
    			case ASSIGN_CURSOR:
                    if(psLayer->compositionType == HWC_FRAMEBUFFER)
                    {
                        psLayer->compositionType = HWC_CURSOR_OVERLAY;
                        Globctx->has_cursor = 1;
                    }
                    SetOrder++;
                break;
                case ASSIGN_GPU:

                break;
                case ASSIGN_FAILED:
                    ALOGD("Use GPU composite FB failed [%d]", disp);
                    reset_local(PsDisplayInfo, Localctx, psDisplay);
                break;
                case ASSIGN_NEEDREASSIGNED:
                    reset_local(PsDisplayInfo, Localctx, psDisplay);
    			    if(NeedReAssignedLayer < 2)
    			    {
                        NeedReAssignedLayer++;
                        SetOrder = 0;
                        goto ReAssignedLayer;
    			    }else{
    			        forceSoftwareRendering = 1;
                        ALOGD("We force forceSoftwareRendering,must have some wrong");
                        goto ReAssignedLayer;
    			    }
                break;
                default:
                    ALOGE("No choice in assign layers");
    		}
    	}
    }
    return 0;
}

bool sunxi_set(hwc_display_contents_1_t **displays, size_t numDisplays)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    hwc_dispc_data_t *DisplayData = NULL;
    HwcDisContext_t *Localctx = NULL;
    DisplayInfo *PsDisplayInfo = NULL;
    hwc_display_contents_1_t *psDisplay = NULL;
    hwc_layer_1_t *fb_layer = NULL;
    int ret = -1, mergfd = -1, ture_disp = -EINVAL;
    size_t disp, i, j;
    unsigned long arg[4] = {0};
    /* 0 for hardware DISP0,1 for DISP1,2 for WB,3 for FB Cache;*/
    int returnfenceFd[CNOUTDISPSYNC] =
                {HWC_SYNC_INIT,HWC_SYNC_INIT,HWC_SYNC_INIT,HWC_SYNC_INIT};
    int releasefecefd = -1;
    bool needmergfd = 0;
    hwc_ioctl_arg hwc_cmd;
    DisplayData = hwc_layer_cache_get(Globctx, Globctx->NumberofDisp);
    Globctx->HWCFramecount++;
    if(DisplayData == NULL)
    {
        ALOGD("calloc hwc_dispc_data_t memery err.");
        goto deal_fence;
    }
    for(disp = 0; disp < numDisplays; disp++)
    {
        Localctx = NULL;
        psDisplay = displays[disp];
        PsDisplayInfo = &Globctx->SunxiDisplay[disp >= NUMBEROFDISPLAY ? 0 : disp];
        ture_disp = PsDisplayInfo->VirtualToHWDisplay;
        if(ture_disp >= 0 && ture_disp < NUMBEROFDISPLAY)
        {
            Localctx = &Globctx->DisContext[ture_disp];
        }
    	if(!psDisplay
           || psDisplay->numHwLayers <= 0
           || Localctx == NULL
#if !defined(HWC_1_3)
           || disp >= HWC_DISPLAY_VIRTUAL
#endif
          )
    	{
    		ALOGV("display[%d] has no display content...", disp);
            continue;
    	}
        fb_layer = &psDisplay->hwLayers[psDisplay->numHwLayers-1];
        if(disp == HWC_DISPLAY_EXTERNAL)
        {
            needmergfd = check_same_diplay(displays, HWC_DISPLAY_EXTERNAL, NULL);
        }
        returnfenceFd[ture_disp] = HWC_SYNC_NEED;
        if(Localctx->UsedFB && fb_layer->handle == NULL)
        {
            DisplayData->force_flip[ture_disp] = 1;
            ALOGD("force flip disp[%d] frame[%d]"
                ,ture_disp, Globctx->HWCFramecount);
            continue;
        }
        if(disp < HWC_DISPLAY_VIRTUAL)
        {
            if(hwc_setup_layer(DisplayData, Localctx) == -1)
            {
                ALOGD("hwc_setup_layer force flip disp[%d] frame[%d]"
                    ,ture_disp, Globctx->HWCFramecount);
                DisplayData->force_flip[ture_disp] = 1;
            }else{
                dump_displays(Localctx);
            }
        }else{
            /*for  miracast*/
        }
    }
    hwc_cmd.cmd = HWC_IOCTL_FENCEFD;
    hwc_cmd.arg = returnfenceFd;
    arg[0] = 0;
    arg[1] = (unsigned long)(&hwc_cmd);
    ret = ioctl(Globctx->DisplayFd, DISP_HWC_COMMIT, (unsigned long)arg);
    for(i = 0; i < CNOUTDISPSYNC; i++)
    {
        if(returnfenceFd[i] >= 0)
        {
            DisplayData->releasefencefd[i] = dup(returnfenceFd[i]);
        }else{
            DisplayData->releasefencefd[i] = -1;
        }
    }

    pthread_mutex_lock(&Globctx->HeadLock);
    hwc_list_put(&Globctx->CommitHead, &DisplayData->commit_head);
    pthread_mutex_unlock(&Globctx->HeadLock);
    Globctx->CommitCondition.broadcast();

    if(needmergfd)
    {
        if(returnfenceFd[0] >= 0 && returnfenceFd[1] >= 0)
        {
            sprintf(merg_fence, "sunxi_merg_%u", Globctx->HWCFramecount);
            mergfd = sync_merge(merg_fence, returnfenceFd[0], returnfenceFd[1]);
            close(returnfenceFd[0]);
            close(returnfenceFd[1]);
            returnfenceFd[0] = dup(mergfd);
            returnfenceFd[1] = dup(mergfd);
            close(mergfd);
        }
    }
    /* if miracast need tr  we need sw_sync and merge the fence fd  
       the disp0 releasefencefd is the wb over,so don't need wb timeline.
    */
deal_fence:
    for(disp = HWC_DISPLAY_PRIMARY; disp < numDisplays; disp++)
	{
		psDisplay = displays[disp];
        releasefecefd = -1;
        PsDisplayInfo = &Globctx->SunxiDisplay[disp >= NUMBEROFDISPLAY ? 0 : disp];
        ture_disp = PsDisplayInfo->VirtualToHWDisplay;
		if(!psDisplay)
		{
			ALOGV("%s: display[%d] was unexpectedly NULL", __func__, disp);
    		continue;
		}
        if(ture_disp < NUMBEROFDISPLAY
            && ture_disp >= 0
            && PsDisplayInfo->VirtualToHWDisplay >= 0)
        {
            releasefecefd = returnfenceFd[ture_disp];
        }else{
            ALOGW("has plugout the disp[%d]",disp);
            continue;
        }
		for(i = 0; i < psDisplay->numHwLayers; i++)
		{
            if(psDisplay->hwLayers[i].acquireFenceFd >= 0)
            {
               close(psDisplay->hwLayers[i].acquireFenceFd);
               psDisplay->hwLayers[i].acquireFenceFd = -1;
            }
		    if((psDisplay->hwLayers[i].compositionType == HWC_OVERLAY)
                || (psDisplay->hwLayers[i].compositionType == HWC_FRAMEBUFFER_TARGET)
                || (psDisplay->hwLayers[i].compositionType == HWC_CURSOR_OVERLAY))
			{
                if(psDisplay->hwLayers[i].releaseFenceFd >= 0)
                {
                    close(psDisplay->hwLayers[i].releaseFenceFd);
                }
                psDisplay->hwLayers[i].releaseFenceFd = -1;
				if(releasefecefd >= 0)
				{
					psDisplay->hwLayers[i].releaseFenceFd = dup(releasefecefd);
			    }
	        }else{
	            if(psDisplay->hwLayers[i].releaseFenceFd >= 0)
	            {
                    close(psDisplay->hwLayers[i].releaseFenceFd);
	            }
				psDisplay->hwLayers[i].releaseFenceFd = -1;
		    }
		}
        if(disp == HWC_DISPLAY_PRIMARY)
        {
            if(psDisplay->retireFenceFd >= 0)
            {
                close(psDisplay->retireFenceFd);
            }
            psDisplay->retireFenceFd = dup(releasefecefd);
        }
        if(disp >= HWC_DISPLAY_VIRTUAL)
        {
            if(psDisplay->outbufAcquireFenceFd >= 0)
            {
                close(psDisplay->outbufAcquireFenceFd);
                psDisplay->outbufAcquireFenceFd = -1;
            }
        }
        close(releasefecefd);
        returnfenceFd[ture_disp] = -1;
    }
    for(i = 0; i < CNOUTDISPSYNC; i++)
    {
        if(returnfenceFd[i] >= 0)
        {
            close(returnfenceFd[i]);
        }
    }
    return 0;
}

static int hwc_init_display(void)
{

    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int refreshRate, xdpi, ydpi, vsync_period;
    bool needupdate;
    struct fb_var_screeninfo info;
    int arg[4] = {0};
    int DispCnt, outtype, permanentdisp, hasDispCnt = 0;

    if (ioctl(Globctx->FBFd, FBIOGET_VSCREENINFO, &info) == -1) 
    {
        ALOGE("FBIOGET_VSCREENINFO ioctl failed: %s", strerror(errno));
        return -1;
    }
    DisplayInfo *PsDisplayInfo = &Globctx->SunxiDisplay[0];
    for(DispCnt = 0, needupdate = 0, permanentdisp = 0;
        DispCnt < Globctx->NumberofDisp;
        DispCnt++)
    {
        if(needupdate == 1)
        {
            PsDisplayInfo++;
            needupdate = 0;
        }
        arg[0] = DispCnt;
        outtype = ioctl(Globctx->DisplayFd, DISP_GET_OUTPUT_TYPE, arg);
        switch(outtype)
        {
            case DISP_OUTPUT_TYPE_LCD:
            case DISP_OUTPUT_TYPE_TV:
            case DISP_OUTPUT_TYPE_VGA:
                permanentdisp++;
            case DISP_OUTPUT_TYPE_HDMI:
                PsDisplayInfo->VirtualToHWDisplay = DispCnt;
                PsDisplayInfo->DisplayType = outtype;
                needupdate = 1;
                hasDispCnt++;
                break;
            case DISP_OUTPUT_TYPE_NONE:
                needupdate = 0;
                break;
            default:
                ALOGD("get the display type err.");
        }
         ALOGD("############outtype:%d.", outtype);
    }

    PsDisplayInfo = &Globctx->SunxiDisplay[0];
    if(permanentdisp && PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_HDMI)
    {
        DispCnt = 0;
        int DispT = PsDisplayInfo->DisplayType, VtoH = PsDisplayInfo->VirtualToHWDisplay;
        while(DispCnt < hasDispCnt && Globctx->SunxiDisplay[0].DisplayType == DISP_OUTPUT_TYPE_HDMI)
        {
            DispCnt++;
            if(PsDisplayInfo[DispCnt].DisplayType != DISP_OUTPUT_TYPE_HDMI
                && PsDisplayInfo[DispCnt].DisplayType != DISP_OUTPUT_TYPE_NONE)
            {
                PsDisplayInfo->VirtualToHWDisplay = PsDisplayInfo[DispCnt].VirtualToHWDisplay;
                PsDisplayInfo->DisplayType = PsDisplayInfo[DispCnt].DisplayType;
                PsDisplayInfo[DispCnt].VirtualToHWDisplay = VtoH;
                PsDisplayInfo[DispCnt].DisplayType = DispT;
            }
        }
    }
    for(DispCnt = 0; DispCnt < hasDispCnt; DispCnt++)
    {
        DisplayInfo *PsDisplayInfo = &Globctx->SunxiDisplay[DispCnt];
        if(PsDisplayInfo->VirtualToHWDisplay != -EINVAL)
        {
            arg[0] = PsDisplayInfo->VirtualToHWDisplay;
            switch(PsDisplayInfo->DisplayType)
            {
                case DISP_OUTPUT_TYPE_LCD:
                case DISP_OUTPUT_TYPE_TV:
                case DISP_OUTPUT_TYPE_VGA:
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
                    }else{
                        PsDisplayInfo->DiplayDPI_X = 1000 * (info.xres * 25.4f) / info.width;
                    }
                    if(info.height == 0)
                    {
                        PsDisplayInfo->DiplayDPI_Y = 160000;
                    }else{
                         PsDisplayInfo->DiplayDPI_Y = 1000 * (info.yres * 25.4f) / info.height;
                    }

                    PsDisplayInfo->DisplayVsyncP = 1000000000 / refreshRate;
                    PsDisplayInfo->Current3DMode = DISPLAY_2D_ORIGINAL;

                    PsDisplayInfo->InitDisplayWidth = info.xres;
                    PsDisplayInfo->InitDisplayHeight = info.yres;
                    PsDisplayInfo->VarDisplayWidth = info.xres;
                    PsDisplayInfo->VarDisplayHeight = info.yres;

                    PsDisplayInfo->VarDisplayWidth = info.xres;
                    PsDisplayInfo->VarDisplayHeight = info.yres;

                    PsDisplayInfo->issecure = 1;
                    PsDisplayInfo->active = 1;
                    break;

                case DISP_OUTPUT_TYPE_HDMI:
                    arg[0] = PsDisplayInfo->VirtualToHWDisplay;
                    if(PsDisplayInfo == Globctx->SunxiDisplay)
                    {
                        PsDisplayInfo->DisplayMode =
                            get_suitable_hdmi_mode(PsDisplayInfo->VirtualToHWDisplay, DISP_TV_MODE_NUM);
                        PsDisplayInfo->InitDisplayWidth =
                            get_info_mode(PsDisplayInfo->DisplayMode, WIDTH);
                        PsDisplayInfo->InitDisplayHeight =
                            get_info_mode(PsDisplayInfo->DisplayMode, HEIGHT);
                        PsDisplayInfo->VarDisplayWidth =
                            PsDisplayInfo->InitDisplayWidth;
                        PsDisplayInfo->VarDisplayHeight =
                            PsDisplayInfo->InitDisplayHeight;
                        PsDisplayInfo->DiplayDPI_X = 213000;
                        PsDisplayInfo->DiplayDPI_Y = 213000;
                        PsDisplayInfo->DisplayVsyncP
                            = 1000000000 /get_info_mode(PsDisplayInfo->DisplayMode, REFRESHRAE);
                        PsDisplayInfo->Current3DMode = DISPLAY_2D_ORIGINAL;
                    }else{
                        hwc_hotplug_switch(PsDisplayInfo->VirtualToHWDisplay,
                                1, DISP_TV_MODE_NUM);
                    }
                    PsDisplayInfo->issecure = HAS_HDCP;
                    PsDisplayInfo->active = 1;
                    break;
                default:
                    ALOGD("not support type");
                continue;
                        
            }
#ifdef FORCE_SET_RESOLUTION
            PsDisplayInfo->VarDisplayWidth = FORCE_RESOLUTION_WIDTH;
            PsDisplayInfo->VarDisplayHeight = FORCE_RESOLUTION_HEIGHT;
#endif
     	    PsDisplayInfo->HwChannelNum = arg[0] ? 2 : NUMCHANNELOFDSP;
            PsDisplayInfo->LayerNumofCH = NUMLAYEROFCHANNEL;
            PsDisplayInfo->VideoCHNum = NUMCHANNELOFVIDEO;
            PsDisplayInfo->VsyncEnable = 1;
            PsDisplayInfo->fb_thruput = ((double)1000000000 /PsDisplayInfo->DisplayVsyncP /60)
                                        * PsDisplayInfo->InitDisplayHeight
                                        * PsDisplayInfo->InitDisplayWidth
                                        * 4;
        }
    }
    arg[0] = 0;
    arg[1] = 1;
    ioctl(Globctx->DisplayFd, DISP_VSYNC_EVENT_EN, (unsigned long)arg);
    return 0;
}

SUNXI_hwcdev_context_t* hwc_create_device(void)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long arg[4] = {0};
    int outtype;
    int open_fd;
    int DispCnt, j;
    disp_tv_mode hdmi_mode;

    Globctx->DisplayFd = open("/dev/disp", O_RDWR);
    if (Globctx->DisplayFd < 0)
    {
        ALOGE( "Failed to open disp device, ret:%d, errno:%d\n",
            Globctx->DisplayFd, errno);
    }
    Globctx->FBFd = open("/dev/graphics/fb0", O_RDWR);
    if (Globctx->FBFd < 0)
    {
        ALOGE( "Failed to open fb0 device, ret:%d, errno:%d\n",
            Globctx->FBFd, errno);
    }
    Globctx->IonFd = open("/dev/ion",O_RDWR);
    if(Globctx->IonFd < 0)
    {
        ALOGE( "Failed to open ion device, ret:%d, errno:%d\n",
            Globctx->IonFd, errno);
    }

    Globctx->tr_fd = open("/dev/transform",O_RDWR);
    if(Globctx->tr_fd < 0)
    {
        ALOGE( "Failed to open transform device, ret:%d, errno:%d\n",
            Globctx->tr_fd, errno);
    }
    if(Globctx->tr_fd >= 0)
    {
        Globctx->tr_count_limit = 1;
    }else{
        Globctx->tr_count_limit = 0;
    }

    Globctx->NumberofDisp = NUMBEROFDISPLAY;
    Globctx->SunxiDisplay =(DisplayInfo* )calloc(Globctx->NumberofDisp, sizeof(DisplayInfo));
    if(Globctx->SunxiDisplay == NULL)
    {
        ALOGE( "calloc DisplayInfo  err....\n");
    }
    memset(Globctx->SunxiDisplay, 0, Globctx->NumberofDisp * sizeof(DisplayInfo));
    for(DispCnt = 0; DispCnt < Globctx->NumberofDisp; DispCnt++)
    {
        Globctx->SunxiDisplay[DispCnt].VirtualToHWDisplay = -EINVAL;
        Globctx->SunxiDisplay[DispCnt].DisplayMode = DISP_TV_MODE_NUM;
    }

    Globctx->DisContext =(HwcDisContext_t* )calloc(Globctx->NumberofDisp, sizeof(HwcDisContext_t));
    if(Globctx->DisContext == NULL)
    {
        ALOGE( "calloc HwcDisContext_t  err....\n");
    }
    memset(Globctx->DisContext, 0, Globctx->NumberofDisp * sizeof(HwcDisContext_t));

    hwc_init_display();
    Globctx->psHwcProcs = NULL;
    Globctx->memlimit = 37324800;

    open_fd = open("/sys/class/switch/hdmi/state", O_RDONLY);
    if (open_fd >= 0)
    {
        char val;
        if (read(open_fd, &val, 1) == 1 && val == '1')
        {
            if(Globctx->SunxiDisplay[0].DisplayType != DISP_OUTPUT_TYPE_HDMI
                && Globctx->SunxiDisplay[1].VirtualToHWDisplay == -EINVAL)
            {
                hwc_hotplug_switch(1, 1, DISP_TV_MODE_NUM);
            }
            ALOGD( "### init hdmi_plug: IN ###");
        }else{
            ALOGD( "### init hdmi_plug: OUT ###");
        }
        close(open_fd);
    }else{
        ALOGD("###open /sys/class/switch/hdmi/state fail");
    }

	open_fd = open("/sys/class/disp/disp/attr/runtime_enable", O_WRONLY);
	if (open_fd >= 0)
	{
		char i = '1';
		ssize_t ret = 0;
		ret = write(open_fd, &i, 1);
		if (ret < 0)
			ALOGD("###write /sys/class/disp/disp/attr/runtime_enable fail");
		close(open_fd);
	}else{
		ALOGD("###open /sys/class/disp/disp/attr/runtime_enable fail");
	}

    open_fd = open("/sys/class/devfreq/sunxi-ddrfreq/max_freq", O_RDONLY);
    if(open_fd >= 0)
    {
        char val_ddr[10] = {0x0,};
        int ret = -1, i = 0, speed = 0;
        ret = read(open_fd, &val_ddr, 6);
	    ALOGD( "### the ddr speedis %s ###",val_ddr);
        close(open_fd);
        open_fd = -1;
        while(ret--)
        {
            speed *= 10;
            if( val_ddr[i] >= '0' && val_ddr[i] <= '9')
            {
                speed += val_ddr[i++] - 48;
            }else{
                speed = 552000;//defalt ddr max speed
                break;
            }
        }
        i = 0;
        ret = sizeof(mem_speed_limit)/sizeof(mem_speed_limit_t);
        for(i =0; i< ret; i++)
        {
            if(mem_speed_limit[i].speed <= speed)
            {
                break;
            }
        }
        if(i == ret)
        {
            i--;
        }
        Globctx->memlimit = mem_speed_limit[i].limit;
        close(open_fd);
    }else{
        ALOGD("open \"/sys/devices/platform/sunxi-ddrfreq/devfreq/sunxi-ddrfreq/max_freq\" err.");
    }

    //Globctx->memlimit = 16736000;//for test
    Globctx->NumManagemax = 0;
    Globctx->NumManageUsed = 0;
    Globctx->AbandonCount = 0;
    Globctx->CanForceGPUCom = 1;
    Globctx->ForceGPUComp = 0;
	Globctx->fBeginTime = 0.0;
    Globctx->uiBeginFrame = 0;
    Globctx->hwcdebug = 0;
    Globctx->stop_rotate_hw = 0;
    Globctx->unblank_flag = 0;
    Globctx->ManageLock = PTHREAD_MUTEX_INITIALIZER;
    Globctx->AbandonLock = PTHREAD_MUTEX_INITIALIZER;
    Globctx->HeadLock = PTHREAD_MUTEX_INITIALIZER;

    j = 0;
    while(j < VIDEO_ROTATE_COUNT)
    {
        Globctx->video_cache[j].fd = -1;
        Globctx->video_cache[j].share_fd = -1;
        Globctx->cursor_rotate_layer[j].share_fd = -1;
        Globctx->cursor_rotate_layer[j].needsync = 0;
        Globctx->cursor_rotate_layer[j].aquirefencefd = -1;
        j++;
    }

    j = 0;
    while(j < ION_HOLD_CNT)
    {
        Globctx->ion_hold[j].num_array = 0;
        Globctx->ion_hold[j].sync_count = 0;
        Globctx->ion_hold[j].handle_array = NULL;
        j++;
    }
    ALOGD( "Primary Display[%d]: Type:[%d] Mode:[%d] Width:[%d] Height:[%d]\n"
            "VsyncP[%d] fb_m:[%d] memlimit[%d] List[%d][%d][%d]"
        ,Globctx->SunxiDisplay[0].VirtualToHWDisplay
        ,Globctx->SunxiDisplay[0].DisplayType
        ,Globctx->SunxiDisplay[0].DisplayMode
        ,Globctx->SunxiDisplay[0].VarDisplayWidth
        ,Globctx->SunxiDisplay[0].VarDisplayHeight
        ,Globctx->SunxiDisplay[0].DisplayVsyncP
        ,Globctx->SunxiDisplay[0].fb_thruput
        ,Globctx->memlimit
        ,Globctx->NumManagemax
        ,Globctx->NumManageUsed
        ,Globctx->AbandonCount);

    pthread_create(&Globctx->CommitThread, NULL, commit_thread, Globctx);
    pthread_create(&Globctx->sVsyncThread, NULL, vsync_thread_wrapper, Globctx);

	return (SUNXI_hwcdev_context_t*)Globctx;
}

int hwc_destroy_device(void)
{
	SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    ALOGD("hwc_destroy_device");
	close(Globctx->DisplayFd);
	close(Globctx->FBFd);
    close(Globctx->IonFd);
    free(Globctx->SunxiDisplay);
	return 1;
}
