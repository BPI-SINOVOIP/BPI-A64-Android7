/*************************************************************************/ /*!
@Copyright      Copyright (c) Imagination Technologies Ltd. All Rights Reserved
@License        Strictly Confidential.
*/ /**************************************************************************/

#include "hwc.h"


SUNXI_hwcdev_context_t gSunxiHwcDevice;

static int _hwcdev_usage_sw(private_handle_t *psHandle)
{
	return psHandle->usage & (GRALLOC_USAGE_SW_READ_OFTEN |
							  GRALLOC_USAGE_SW_WRITE_OFTEN);
}

static int _hwcdev_usage_sw_write(private_handle_t *psHandle)
{
	return psHandle->usage & GRALLOC_USAGE_SW_WRITE_OFTEN;
}

static int _hwcdev_usage_sw_write_often(private_handle_t *psHandle)
{
	return (psHandle->usage & GRALLOC_USAGE_SW_WRITE_MASK) == GRALLOC_USAGE_SW_WRITE_OFTEN;
}

static int _hwcdev_usage_protected(private_handle_t *psHandle)
{
	return psHandle->usage & GRALLOC_USAGE_PROTECTED;
}


static int _hwcdev_is_valid_format(int format)
{
    switch(format) 
    {
    case HAL_PIXEL_FORMAT_RGBA_8888:
    case HAL_PIXEL_FORMAT_RGBX_8888:
    case HAL_PIXEL_FORMAT_RGB_888:
    case HAL_PIXEL_FORMAT_RGB_565:
    case HAL_PIXEL_FORMAT_BGRA_8888:
    //case HAL_PIXEL_FORMAT_sRGB_A_8888:
    //case HAL_PIXEL_FORMAT_sRGB_X_8888:
    case HAL_PIXEL_FORMAT_YV12:
	  case HAL_PIXEL_FORMAT_YCrCb_420_SP:
        return 1;
    default:
        return 0;
    }
}

static int _hwcdev_is_blended(hwc_layer_1_t* psLayer)
{
	//return ((psLayer->blending != HWC_BLENDING_NONE) || (psLayer->reserved[0]<0XFF));
	return (psLayer->blending != HWC_BLENDING_NONE);
}

static int _hwcdev_is_premult(hwc_layer_1_t* psLayer)
{
    return (psLayer->blending == HWC_BLENDING_PREMULT);
}

static int _hwcdev_is_scaled(hwc_layer_1_t *layer)
{
    int w = layer->sourceCrop.right - layer->sourceCrop.left;
    int h = layer->sourceCrop.bottom - layer->sourceCrop.top;

    if (layer->transform & HWC_TRANSFORM_ROT_90)
    {
        int tmp = w;
        w = h;
        h = tmp;
    }

    return ((layer->displayFrame.right - layer->displayFrame.left) != w) || ((layer->displayFrame.bottom - layer->displayFrame.top)!= h);
}

static int _hwcdev_is_valid_layer(hwc_layer_1_t *layer)
{
    private_handle_t *handle = (private_handle_t *)layer->handle;
    
    /* Skip layers are handled by SF */
    if ((layer->flags & HWC_SKIP_LAYER) || !handle)
    {
        return 0;
    }
    
    if (!_hwcdev_is_valid_format(handle->format))
    {
        return 0;
    }
    
    if(_hwcdev_is_scaled(layer) && ((handle->format != HAL_PIXEL_FORMAT_YV12) && (handle->format != HAL_PIXEL_FORMAT_YCrCb_420_SP)) )
    {
        return 0;
    }

    if(layer->transform)
    {
        return 0;
    }

    return 1;
}

static int _hwcdev_is_two_region_intersect(hwc_rect_t *rect0, hwc_rect_t *rect1)
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
        return 1;
    }

    return 0;
}

static int _hwcdev_region_merge(hwc_rect_t *rect_from, hwc_rect_t *rect1_to, int bound_width, int bound_height)
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

static int _hwcdev_fe_can_use(SUNXI_hwcdev_context_t *ctx, int fe_src_w, int fe_src_h, int fe_out_w, int fe_out_h)
{
	double fe_clk = 234000000;
	int fe_pro_w=0;
	int fe_pro_h=0;
	int m = 3;
	int n = 2;
	
	if(fe_src_w >= fe_out_w)
		fe_pro_w = fe_src_w;
	else
		fe_pro_w = fe_out_w;
	
	if(fe_src_h >= fe_out_h)
		fe_pro_h = fe_src_h;
	else
		fe_pro_h = fe_out_h;

	double tmp = (fe_pro_w * fe_pro_h * 60)/(fe_out_w * fe_out_h) * (ctx->display_width[0] * ctx->display_height[0]);
	double required_fe_clk = tmp * m/n;

	if(required_fe_clk > fe_clk) {
		return 0;//cant
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
		ALOGV("Rect A: adr = 0x%x [%d,%d,%d,%d]",tmplayer,
						tmplayer->displayFrame.left,
            tmplayer->displayFrame.top,
            tmplayer->displayFrame.right,
            tmplayer->displayFrame.bottom);
		ALOGV("Rect B: adr = 0x%x [%d,%d,%d,%d]",psLayer,
						psLayer->displayFrame.left,
            psLayer->displayFrame.top,
            psLayer->displayFrame.right,
            psLayer->displayFrame.bottom);
		if(_hwcdev_is_two_region_intersect(&tmplayer->displayFrame, &psLayer->displayFrame))
		{
			ALOGV("found intersect\n");
			return 1;
		}   
		head = next;
	}
	return 0;   
}
IMG_hwcdev_pipe_assign_status_t
hwcdev_try_to_assign_pipe(SUNXI_hwcdev_context_t *ctx, SUNXI_hwcdev_pipe_t *psPipe, hwc_layer_1_t *psLayer, int zOrder, int disp)
{
    private_handle_t* handle = (private_handle_t*)psLayer->handle;
    int add_pipe_needed = 0;
    char property[PROPERTY_VALUE_MAX];
    ctx->used_fe = 0;
    
	if (psPipe->pipeInUse == 1)
	{
		ALOGV("%s: pipe in use type:%d handle:%p", __func__, psLayer->compositionType, psLayer->handle);
	        goto assign_failed;
	}
	
	if (psLayer->handle == NULL)
	{
		ALOGV("%s: handle is null", __func__);
	        goto assign_failed;
	}

    if(!(handle->flags&private_handle_t::PRIV_FLAGS_USES_CONFIG))
	{
	    ALOGV("hwcdev_try_to_assign_pipe: We use not config memory");
	        goto assign_failed;
	}

	if (_hwcdev_usage_sw_write_often((private_handle_t*)psLayer->handle))
	{
	    if(psLayer->compositionType != HWC_FRAMEBUFFER_TARGET)
	    {
	        ALOGV("hwcdev_try_to_assign_pipe: sw write often usage(%x) buffer!", handle->usage);
	        goto assign_failed;
	    }

	}
		
	if (psLayer->compositionType == HWC_BACKGROUND)
	{
	        goto assign_failed;
	}
	
	if (psLayer->compositionType == HWC_FRAMEBUFFER_TARGET)
	{
		ctx->use_fb[disp] = 1;
		add_pipe_needed = 1;
	}
	else
	{
	    if(ctx->force_sgx[disp])
	    {
	        ALOGV("%s:type force_sgx:%x", __func__, ctx->force_sgx[disp]);
	        goto assign_failed;
	    }
	    if (property_get("sys.forcegles", property, NULL) > 0)
			{
					
					if(atoi(property))
					{
							goto assign_failed;
					}
			}
	    
        if(!_hwcdev_is_valid_layer(psLayer))
        {
            ALOGV("%s:_hwcdev_is_valid_layer", __func__);
	    			goto assign_failed;
        }

        if(zOrder == 0)
        {
            add_pipe_needed = 1;
        }
        else if(_hwcdev_is_blended(psLayer) || _hwcdev_is_premult(psLayer))
        {
		
            if(ctx->use_fb[disp])
            {
			if(Check_X_FB(psLayer,&ctx->FBLayerHead))
			{
				ALOGV("%s:_hwcdev_is_blended and use fb", __func__);
				goto assign_failed;

			}   
			else
			{
				ALOGV("%s: use fb to hwc", __func__);
			}
            }
	    
            
            if(_hwcdev_is_two_region_intersect(&ctx->de_pipe_region[disp][ctx->de_pipe_used[disp]-1], &psLayer->displayFrame))
            {
                if(ctx->de_pipe_used[disp] < 2)
                {
                    add_pipe_needed = 1;
                }
                else
                {
		   
                    ALOGV("%s:not enough de pipe", __func__);
		   goto assign_failed;
                }
            }
        }
        else if(ctx->use_fb[disp] && ctx->have_ovl[disp] == 0)
        {
            add_pipe_needed = 1;
        }

		if(_hwcdev_is_scaled(psLayer) || handle->format==HAL_PIXEL_FORMAT_YV12 ||  handle->format==HAL_PIXEL_FORMAT_YCrCb_420_SP)
		{
		    if(ctx->de_fe_used[disp] < 1)
		    {
			int src_w, src_h, dst_w, dst_h;
			
			src_w = psLayer->sourceCrop.right - psLayer->sourceCrop.left;
			src_h = psLayer->sourceCrop.bottom - psLayer->sourceCrop.top;
			dst_w = psLayer->displayFrame.right - psLayer->displayFrame.left;
			dst_h = psLayer->displayFrame.bottom - psLayer->displayFrame.top;
			if(_hwcdev_fe_can_use(ctx, src_w, src_h, dst_w, dst_h))
			{
			    ctx->de_fe_used[disp]++;
			    ctx->used_fe = 1;
			}
			else
			{
			    ALOGV("%s:fe can not used", __func__);
			   goto assign_failed;
			}
		    }
		    else
		    {
			ALOGV("%s:not enough de fe", __func__);
			goto assign_failed;
		    }
		}

        ctx->have_ovl[disp] = 1;
        
        //if (ctx->use_fb[disp] && !_hwcdev_is_blended(psLayer))
        if (ctx->use_fb[disp] && Check_X_FB(psLayer, &ctx->FBLayerHead) )
        {
            psLayer->hints |= HWC_HINT_CLEAR_FB;
        }
    }
    
    if(add_pipe_needed)
    {
        ctx->de_pipe_used[disp]++;
    }

    _hwcdev_region_merge(&psLayer->displayFrame, &ctx->de_pipe_region[disp][ctx->de_pipe_used[disp]-1], ctx->display_width[disp], ctx->display_height[disp]);
    
    psPipe->pipeType = ctx->de_pipe_used[disp]-1;
	psPipe->pipeInUse = 1;
	psPipe->assignedLayer = psLayer;
	psPipe->assignedLayerZOrder = zOrder;
	psPipe->display_type = HWC_DISPLAY_PRIMARY;
  
	return ASSIGN_OK;
assign_failed:
	    
	return ASSIGN_FAILED;
}


#ifdef __LECACY_USED__ 
static int _hwcdev_setup_layer(SUNXI_hwcdev_context_t *ctx, __disp_layer_info_t *layer_info, SUNXI_hwcdev_pipe_t *psPipe, size_t disp)
{
    hwc_layer_1_t *layer = psPipe->assignedLayer;
    private_handle_t *handle = (private_handle_t *)layer->handle;

    memset(layer_info, 0, sizeof(__disp_layer_info_t));
    
    switch(handle->format)
    {

        case HAL_PIXEL_FORMAT_RGBA_8888:
            layer_info->fb.mode = DISP_MOD_INTERLEAVED;
            layer_info->fb.format = DISP_FORMAT_ARGB8888;
            layer_info->fb.seq = DISP_SEQ_ARGB;
            layer_info->fb.br_swap = 1;
            break;
        case HAL_PIXEL_FORMAT_RGBX_8888:
            layer_info->fb.mode = DISP_MOD_INTERLEAVED;
            layer_info->fb.format = DISP_FORMAT_ARGB8888;
            layer_info->fb.seq = DISP_SEQ_ARGB;
            layer_info->fb.br_swap = 1;
            layer_info->alpha_en = 1;
            layer_info->alpha_val = 0xff;
            break;
        case HAL_PIXEL_FORMAT_RGB_888:
            layer_info->fb.mode = DISP_MOD_INTERLEAVED;
            layer_info->fb.format = DISP_FORMAT_RGB888;
            layer_info->fb.seq = DISP_SEQ_ARGB;
            layer_info->fb.br_swap = 1;
            break;
        case HAL_PIXEL_FORMAT_RGB_565:
            layer_info->fb.mode = DISP_MOD_INTERLEAVED;
            layer_info->fb.format = DISP_FORMAT_RGB565;
            layer_info->fb.seq = DISP_SEQ_ARGB;
            layer_info->fb.br_swap = 0;
            break;
        case HAL_PIXEL_FORMAT_BGRA_8888:
            layer_info->fb.mode = DISP_MOD_INTERLEAVED;
            layer_info->fb.format = DISP_FORMAT_ARGB8888;
            layer_info->fb.seq = DISP_SEQ_ARGB;
            layer_info->fb.br_swap = 0;
            break;
        case HAL_PIXEL_FORMAT_YV12:
            layer_info->fb.mode = DISP_MOD_NON_MB_PLANAR;
            layer_info->fb.format = DISP_FORMAT_YUV420;
            layer_info->fb.seq = DISP_SEQ_P3210;
            layer_info->fb.br_swap = 0;
            break;
	case HAL_PIXEL_FORMAT_YCrCb_420_SP:
            layer_info->fb.mode = DISP_MOD_NON_MB_UV_COMBINED;
            layer_info->fb.format = DISP_FORMAT_YUV420;
            layer_info->fb.seq = DISP_SEQ_VUVU;
            layer_info->fb.br_swap = 0;
	    break;
        default:
            ALOGE("not support format 0x%x in %s", handle->format, __FUNCTION__);
            break;
    }

    //if(layer->reserved[0] < 0xff)
    //{
    //    layer_info->alpha_en = 1;
    //    layer_info->alpha_val = 0;//layer->reserved[0];
    //}
    //else 
    if(psPipe->assignedLayerZOrder == 0 || _hwcdev_is_blended(layer)==0)
    {
        layer_info->alpha_en = 1;
        layer_info->alpha_val = 0xff;
    }

    if(layer->blending == HWC_BLENDING_PREMULT)
    {
        layer_info->fb.pre_multiply = 1;
    }

    layer_info->fb.addr[0] = IonGetAddr(handle);
    ALOGV("fb.addr = 0x%x\n",layer_info->fb.addr[0]);
    layer_info->fb.size.width = handle->stride;
    layer_info->fb.size.height = handle->height;

    layer_info->src_win.x = layer->sourceCrop.left;
    layer_info->src_win.y = layer->sourceCrop.top;
    layer_info->src_win.width = layer->sourceCrop.right - layer->sourceCrop.left;
    layer_info->src_win.height = layer->sourceCrop.bottom - layer->sourceCrop.top;

    layer_info->scn_win.x = layer->displayFrame.left + (ctx->display_width[disp] * (100 - ctx->display_persent[disp]) / ctx->display_persent[disp] / 2);
    layer_info->scn_win.y = layer->displayFrame.top + (ctx->display_height[disp] * (100 - ctx->display_persent[disp]) / ctx->display_persent[disp] / 2);
    layer_info->scn_win.width = layer->displayFrame.right - layer->displayFrame.left;
    layer_info->scn_win.height = layer->displayFrame.bottom - layer->displayFrame.top;

    if(_hwcdev_is_scaled(layer) || handle->format==HAL_PIXEL_FORMAT_YV12 || handle->format==HAL_PIXEL_FORMAT_YCrCb_420_SP )
    {
        int cut_size_scn, cut_size_src;
        hwc_rect_t scn_bound;

        layer_info->mode = DISP_LAYER_WORK_MODE_SCALER;

        scn_bound.left = ctx->display_width[disp] * (100 - ctx->display_persent[disp]) / ctx->display_persent[disp] / 2;
        scn_bound.top = ctx->display_height[disp] * (100 - ctx->display_persent[disp]) / ctx->display_persent[disp] / 2;

        if(disp == 0)
        {
            scn_bound.right = scn_bound.left + (ctx->display_width[disp] * ctx->display_persent[disp]) / 100;
            scn_bound.bottom = scn_bound.top + (ctx->display_height[disp] * ctx->display_persent[disp]) / 100;
        }
        else
        {
            scn_bound.left += ctx->frame[disp].left;
            scn_bound.top += ctx->frame[disp].top;
            scn_bound.right = scn_bound.left + ctx->frame[disp].right - ctx->frame[disp].left;
            scn_bound.bottom = scn_bound.top + ctx->frame[disp].bottom - ctx->frame[disp].top;
        }
        
        if(layer_info->src_win.x < 0)
        {
            cut_size_src = (0 - layer_info->src_win.x);

            layer_info->src_win.x += cut_size_src;
            layer_info->src_win.width -= cut_size_src;
        }
        if((layer_info->src_win.x + layer_info->src_win.width) > handle->width)
        {
            cut_size_src = (layer_info->src_win.x + layer_info->src_win.width) - handle->width;
            layer_info->src_win.width -= cut_size_src;
        }
        if(layer_info->src_win.y < 0)
        {
            cut_size_src = (0 - layer_info->src_win.y);

            layer_info->src_win.y += cut_size_src;
            layer_info->src_win.height -= cut_size_src;
        }
        if((layer_info->src_win.y + layer_info->src_win.height) > handle->height)
        {
            cut_size_src = (layer_info->src_win.x + layer_info->src_win.height) - handle->height;
            layer_info->src_win.height -= cut_size_src;
        }
        
        if(layer_info->scn_win.x < scn_bound.left)
        {
            cut_size_scn = (scn_bound.left - layer_info->scn_win.x);
            cut_size_src = cut_size_scn * layer_info->src_win.width / layer_info->scn_win.width;

            layer_info->src_win.x += cut_size_src;
            layer_info->src_win.width -= cut_size_src;

            layer_info->scn_win.x += cut_size_scn;
            layer_info->scn_win.width -= cut_size_scn;
        }
        if((layer_info->scn_win.x + layer_info->scn_win.width) > scn_bound.right)
        {
            cut_size_scn = (layer_info->scn_win.x + layer_info->scn_win.width) - scn_bound.right;
            cut_size_src = cut_size_scn * layer_info->src_win.width / layer_info->scn_win.width;
            
            layer_info->src_win.width -= cut_size_src;

            layer_info->scn_win.width -= cut_size_scn;
        }
        if(layer_info->scn_win.y < scn_bound.top)
        {
            cut_size_scn = (scn_bound.top - layer_info->scn_win.y);
            cut_size_src = cut_size_scn * layer_info->src_win.height / layer_info->scn_win.height;
            
            layer_info->src_win.y += cut_size_src;
            layer_info->src_win.height -= cut_size_src;
            
            layer_info->scn_win.y += cut_size_scn;
            layer_info->scn_win.height -= cut_size_scn;
        }
        if((layer_info->scn_win.y + layer_info->scn_win.height) > scn_bound.bottom)
        {
            cut_size_scn = (layer_info->scn_win.y + layer_info->scn_win.height) - scn_bound.bottom;
            cut_size_src = cut_size_scn * layer_info->src_win.height / layer_info->scn_win.height;
            
            layer_info->src_win.height -= cut_size_src;

            layer_info->scn_win.height -= cut_size_scn;
        }
    }
    else
    {
    	if(disp == 1)
    	{
    		layer_info->scn_win.x = 0;
    	}
        layer_info->mode = DISP_LAYER_WORK_MODE_NORMAL;
    }
    layer_info->pipe = psPipe->pipeType;
    layer_info->prio = psPipe->assignedLayerZOrder;

    _hwcdev_layer_config_3d(disp, layer_info);
    
    return 1;
}   
#else
static int _hwcdev_setup_layer(SUNXI_hwcdev_context_t *ctx, __disp_layer_info_t *layer_info, SUNXI_hwcdev_pipe_t *psPipe, size_t disp)
{
    hwc_layer_1_t *layer = psPipe->assignedLayer;
    private_handle_t *handle = (private_handle_t *)layer->handle;

    memset(layer_info, 0, sizeof(__disp_layer_info_t));
    
    switch(handle->format)
    {
	 case HAL_PIXEL_FORMAT_RGBA_8888:
            layer_info->fb.format = DISP_FORMAT_ABGR_8888;
            break;
        case HAL_PIXEL_FORMAT_RGBX_8888:
            layer_info->fb.format = DISP_FORMAT_XBGR_8888;
            layer_info->alpha_mode = 1;
            layer_info->alpha_value = 0xff;
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
	//	case HAL_PIXEL_FORMAT_BGRX_8888:
	//		layer_info->fb.format = DISP_FORMAT_XRGB_8888;
	//		layer_info->alpha_mode = 1;
	//		layer_info->alpha_value = 0xff;
    //        break;
        case HAL_PIXEL_FORMAT_YV12:
            layer_info->fb.format = DISP_FORMAT_YUV420_P;
            break;
	case HAL_PIXEL_FORMAT_YCrCb_420_SP:
            layer_info->fb.format = DISP_FORMAT_YUV420_SP_VUVU;
	    break;
        default:
            ALOGE("not support format 0x%x in %s", handle->format, __FUNCTION__);
            break;
    }

    //if(layer->reserved[0] < 0xff)
    //{
    //    layer_info->alpha_mode = 1;
    //    layer_info->alpha_value = 0;//layer->reserved[0];
    //}
    //else 
    if(psPipe->assignedLayerZOrder == 0 || _hwcdev_is_blended(layer)==0)
    {
        layer_info->alpha_mode = 1;
        layer_info->alpha_value = 0xff;
    }

    if(layer->blending == HWC_BLENDING_PREMULT)
    {
        layer_info->fb.pre_multiply = 1;
    }

    layer_info->fb.addr[0] = IonGetAddr(handle);
    ALOGV("fb.addr = 0x%x\n",layer_info->fb.addr[0]);
    layer_info->fb.size.width = handle->stride;
    layer_info->fb.size.height = handle->height;

    layer_info->fb.src_win.x = layer->sourceCrop.left;
    layer_info->fb.src_win.y = layer->sourceCrop.top;
    layer_info->fb.src_win.width = layer->sourceCrop.right - layer->sourceCrop.left;
    layer_info->fb.src_win.height = layer->sourceCrop.bottom - layer->sourceCrop.top;

    layer_info->screen_win.x = layer->displayFrame.left + (ctx->display_width[disp] * (100 - ctx->display_persent[disp]) / ctx->display_persent[disp] / 2);
    layer_info->screen_win.y = layer->displayFrame.top + (ctx->display_height[disp] * (100 - ctx->display_persent[disp]) / ctx->display_persent[disp] / 2);
    layer_info->screen_win.width = layer->displayFrame.right - layer->displayFrame.left;
    layer_info->screen_win.height = layer->displayFrame.bottom - layer->displayFrame.top;

    if(_hwcdev_is_scaled(layer) || handle->format==HAL_PIXEL_FORMAT_YV12 || handle->format== HAL_PIXEL_FORMAT_YCrCb_420_SP)
    {
        int cut_size_scn, cut_size_src;
        hwc_rect_t scn_bound;

        layer_info->mode = DISP_LAYER_WORK_MODE_SCALER;

        scn_bound.left = ctx->display_width[disp] * (100 - ctx->display_persent[disp]) / ctx->display_persent[disp] / 2;
        scn_bound.top = ctx->display_height[disp] * (100 - ctx->display_persent[disp]) / ctx->display_persent[disp] / 2;

        if(disp == 0)
        {
            scn_bound.right = scn_bound.left + (ctx->display_width[disp] * ctx->display_persent[disp]) / 100;
            scn_bound.bottom = scn_bound.top + (ctx->display_height[disp] * ctx->display_persent[disp]) / 100;
        }
        else
        {
            scn_bound.left += ctx->frame[disp].left;
            scn_bound.top += ctx->frame[disp].top;
            scn_bound.right = scn_bound.left + ctx->frame[disp].right - ctx->frame[disp].left;
            scn_bound.bottom = scn_bound.top + ctx->frame[disp].bottom - ctx->frame[disp].top;
        }
        
        if(layer_info->fb.src_win.x < 0)
        {
            cut_size_src = (0 - layer_info->fb.src_win.x);

            layer_info->fb.src_win.x += cut_size_src;
            layer_info->fb.src_win.width -= cut_size_src;
        }
        if((layer_info->fb.src_win.x + layer_info->fb.src_win.width) > handle->width)
        {
            cut_size_src = (layer_info->fb.src_win.x + layer_info->fb.src_win.width) - handle->width;
            layer_info->fb.src_win.width -= cut_size_src;
        }
        if(layer_info->fb.src_win.y < 0)
        {
            cut_size_src = (0 - layer_info->fb.src_win.y);

            layer_info->fb.src_win.y += cut_size_src;
            layer_info->fb.src_win.height -= cut_size_src;
        }
        if((layer_info->fb.src_win.y + layer_info->fb.src_win.height) > handle->height)
        {
            cut_size_src = (layer_info->fb.src_win.x + layer_info->fb.src_win.height) - handle->height;
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
        if((layer_info->screen_win.x + layer_info->screen_win.width) > scn_bound.right)
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
        if((layer_info->screen_win.y + layer_info->screen_win.height) > scn_bound.bottom)
        {
            cut_size_scn = (layer_info->screen_win.y + layer_info->screen_win.height) - scn_bound.bottom;
            cut_size_src = cut_size_scn * layer_info->fb.src_win.height / layer_info->screen_win.height;
            
            layer_info->fb.src_win.height -= cut_size_src;

            layer_info->screen_win.height -= cut_size_scn;
        }
    }
    else
    {
    	if(disp == 1)
    	{
    		layer_info->screen_win.x = 0;
    	}
        layer_info->mode = DISP_LAYER_WORK_MODE_NORMAL;
    }
    layer_info->pipe = psPipe->pipeType;
    layer_info->zorder = psPipe->assignedLayerZOrder;

    _hwcdev_layer_config_3d(disp, layer_info);
    
    return 1;
}
#endif

int hwcdev_generate_private_data(SUNXI_hwcdev_context_t *ctx)
{
	setup_dispc_data_t *psDsscomp = (setup_dispc_data_t *)calloc(1, sizeof(setup_dispc_data_t));
	size_t i, j, disp;
	int usedPipes = 0;

    memset(psDsscomp, 0, sizeof(setup_dispc_data_t));

    for(disp = 0; disp < 2; disp++)
    {
    	/* First we need to 'compress' android-supplied Z orders to the range 0..3 */
    	int pipeCompressedZValue[DISPLAY_MAX_LAYER_NUM];

    	for (i = 0; i < ctx->pipeCount[disp]; i++)
    	{
    		pipeCompressedZValue[i] = 0;

    		if (!ctx->pipes[disp][i].pipeInUse)
    			continue;

    		/* The number of layers with Z values less than this layer should
    		 * be the correct z */
    		for (j = 0; j < ctx->pipeCount[disp]; j++)
    		{
    			if (ctx->pipes[disp][j].pipeInUse)
    			{
    				if (ctx->pipes[disp][j].assignedLayerZOrder < ctx->pipes[disp][i].assignedLayerZOrder)
    				{
    					pipeCompressedZValue[i]++;
    				}
    			}
    		}
    	}

    	for (i = 0; i < ctx->pipeCount[disp]; i++)
    	{
    		if (ctx->pipes[disp][i].pipeInUse)
    		{
    			/* Replace Z value with the 'compressed' z */
    			ctx->pipes[disp][i].assignedLayerZOrder = pipeCompressedZValue[i];
    			if(!_hwcdev_setup_layer(ctx, &psDsscomp->layer_info[usedPipes], &ctx->pipes[disp][i], disp))
    			{
					free(psDsscomp);
    				return 0;
    			}
    			psDsscomp->acquireFenceFd[usedPipes] = ctx->pipes[disp][i].assignedLayer->acquireFenceFd;
    			IonHandleAddRef(ctx->pipes[disp][i].assignedLayer);
    			usedPipes++;
    		}
    	}

    	psDsscomp->show_black[disp] = ctx->show_black[disp];
    	
    	if(disp == 0)
    	{
    	    psDsscomp->primary_display_layer_num = usedPipes;
    	}
	}
	psDsscomp->post2_layers = usedPipes;
	psDsscomp->time_stamp = ctx->time_stamp++;
	ctx->pvPrivateData = psDsscomp;
	ctx->uiPrivateDataSize = sizeof(setup_dispc_data_t);

	ALOGV("####hwcdev_generate_private_data %d %d %d", psDsscomp->time_stamp, psDsscomp->post2_layers, psDsscomp->primary_display_layer_num);
	return 1;
}

int hwcdev_free_private_data(SUNXI_hwcdev_context_t *ctx)
{
    free(ctx->pvPrivateData);
	return 1;
}

SUNXI_hwcdev_context_t* hwcdev_create_device(void)
{
    SUNXI_hwcdev_context_t *ctx = &gSunxiHwcDevice;
    size_t i,disp;
    int sw_fd;
    __disp_tv_mode_t hdmi_mode;
    unsigned int ui32Default;
    void *pvHintState;
    unsigned int ui32HALHdmiMode;
    FILE *srcFp;
    struct fb_var_screeninfo info;

    memset(ctx, 0, sizeof(SUNXI_hwcdev_context_t));

    for(disp = 0; disp < 2; disp++)
    {
    	ctx->pipes[disp] = (SUNXI_hwcdev_pipe_t *)calloc(DISPLAY_MAX_LAYER_NUM, sizeof(SUNXI_hwcdev_pipe_t));
    	ctx->pipeCount[disp] = DISPLAY_MAX_LAYER_NUM;
    	for (i = 0; i < ctx->pipeCount[disp]; i++)
    	{
    		ctx->pipes[disp][i].pipeNo = i;
    	}
	}

    ctx->disp_fp = open("/dev/disp", O_RDWR);
    if (ctx->disp_fp < 0)
    {
        ALOGD( "Failed to open disp device, ret:%d, errno: %d\n", ctx->disp_fp, errno);
    }
    
    ctx->fb_fp[0] = open("/dev/graphics/fb0", O_RDWR);
    if (ctx->fb_fp[0] < 0)
    {
        ALOGD( "Failed to open fb0 device, ret:%d, errno:%d\n", ctx->fb_fp[0], errno);
    }
    
    sw_fd = open("/sys/class/switch/hdmi/state", O_RDONLY);
    if (sw_fd) 
    {
        char val;
        if (read(sw_fd, &val, 1) == 1 && val == '1') 
        {
            ctx->hdmi_hpd = 1;
            ALOGD( "####init hdmi_plug:%d", ctx->hdmi_hpd);
        }
    }
    
    ctx->hint_hdmi_mode = 255;//4;

    if (ioctl(ctx->fb_fp[0], FBIOGET_VSCREENINFO, &info) == -1) {
        ALOGE("FBIOGET_VSCREENINFO ioctl failed: %s", strerror(errno));
    }

    ctx->display_width[0] = info.xres;
    ctx->display_height[0] = info.yres;

    ctx->out_type[0] = DISP_OUTPUT_TYPE_LCD;
    ctx->out_mode[0] = 0;
    ctx->display_persent[0] = 100;
    
    hdmi_mode = get_suitable_hdmi_mode();
    ctx->display_width[1] = get_width_from_mode(hdmi_mode);
    ctx->display_height[1] = get_height_from_mode(hdmi_mode);
    ctx->out_type[1] = DISP_OUTPUT_TYPE_HDMI;
    ctx->out_mode[1] = hdmi_mode;
    ctx->display_persent[1] = 96;

    ctx->cur_3d_mode[0] = DISPLAY_2D_ORIGINAL;
    ctx->cur_3d_mode[1] = DISPLAY_2D_ORIGINAL;
    
    ctx->ion_fd = ion_open();
    
    pthread_mutex_init(&ctx->IonRefLock, NULL);

    pthread_create(&ctx->sVsyncThread, NULL, VsyncThreadWrapper, ctx);

#ifdef HWC_FPS_REPORT
        ctx->bReportFpsEnabled = 0; 
#endif /* HWC_FPS_REPORT */	
	return (SUNXI_hwcdev_context_t*)ctx;
}

int hwcdev_destroy_device(SUNXI_hwcdev_context_t *psDevice)
{
	SUNXI_hwcdev_context_t *ctx = (SUNXI_hwcdev_context_t*)psDevice;

	pthread_mutex_destroy(&ctx->IonRefLock);

	close(ctx->disp_fp);
	close(ctx->fb_fp[0]);
	close(ctx->fb_fp[1]);	
	free(ctx->pipes[0]);
	free(ctx->pipes[1]);
	
	return 1;
}

int hwcdev_reset_device(SUNXI_hwcdev_context_t *psDevice, size_t disp)
{
    SUNXI_hwcdev_context_t *ctx = (SUNXI_hwcdev_context_t*)psDevice;

    ctx->use_fb[disp] = 0;
    ctx->have_ovl[disp] = 0;
    ctx->de_pipe_used[disp] = 0;
    ctx->de_pipe_region[disp][0].left = 10000;
    ctx->de_pipe_region[disp][0].right = 0;
    ctx->de_pipe_region[disp][0].top = 10000;
    ctx->de_pipe_region[disp][0].bottom = 0;
    ctx->de_pipe_region[disp][1].left = 10000;
    ctx->de_pipe_region[disp][1].right = 0;
    ctx->de_pipe_region[disp][1].top = 10000;
    ctx->de_pipe_region[disp][1].bottom = 0;
    ctx->de_fe_used[disp] = 0;
    return 1;
}
unsigned int IonGetAddr(void *handle)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    struct ion_handle* tmp2;
    struct ion_custom_data custom_data;
	sunxi_phys_data phys_data;
    ion_handle_data freedata;
    struct ion_fd_data data ;
	private_handle_t* hnd = (private_handle_t*)handle;
    int ret = -1;

    data.fd = hnd->share_fd;
    ret = ioctl(Globctx->ion_fd, ION_IOC_IMPORT, &data);
    if (ret < 0)
    {
        ALOGE("#######ion_import  error#######");
        return 0;
    }
    custom_data.cmd = ION_IOC_SUNXI_PHYS_ADDR;
	phys_data.handle = data.handle;
	custom_data.arg = (unsigned long)&phys_data;
	ret = ioctl(Globctx->ion_fd, ION_IOC_CUSTOM,&custom_data);
	if(ret < 0){
        ALOGE("ION_IOC_CUSTOM(err=%d)",ret);
        return 0;
    }
    freedata.handle = data.handle;
    ret = ioctl(Globctx->ion_fd, ION_IOC_FREE, &freedata);
    if(ret < 0){
        ALOGE("ION_IOC_FREE(err=%d)",ret);
        return 0;
    }
    
    return phys_data.phys_addr;  
}

int IonHandleAddRef(hwc_layer_1_t *layer)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    private_handle_t *handle = (private_handle_t *)layer->handle;
    ion_list * item;
    ion_list * head = Globctx->IonHandleHead;
    struct ion_handle *handle_ion;
    int ret = -1;
    
    pthread_mutex_lock(&Globctx->IonRefLock);

    if(handle == NULL)
    {
    		pthread_mutex_unlock(&Globctx->IonRefLock);
        return -1;
    }
    
    ret = ion_import(Globctx->ion_fd, handle->share_fd, (ion_user_handle_t*)&handle_ion);
    if (ret < 0)
    {
        ALOGE("%s ION_IOC_IMPORT error", __func__);
        pthread_mutex_unlock(&Globctx->IonRefLock);
        return -1;
    }

    ALOGV("########add %d %p",Globctx->HWCFramecount, handle_ion);

    item = (ion_list *)malloc(sizeof(ion_list));
    item->frame_index = Globctx->HWCFramecount;
    item->fd = handle->share_fd;
    item->handle = handle_ion;
    if(head == NULL)
    {
        head = item;
        item->next = item;
        item->prev= item;
    }
    else
    {
        item->next = head;
        item->prev = head->prev;
        head->prev->next = item;
        head->prev = item;
        head = item;
    }
    Globctx->IonHandleHead = head;
    
    pthread_mutex_unlock(&Globctx->IonRefLock);
    
    return 0;  
}

int IonHandleDecRef(void)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    ion_list * head = Globctx->IonHandleHead;
    ion_list * item;
    
    pthread_mutex_lock(&Globctx->IonRefLock);
				
    if(head == NULL)
    {
    		pthread_mutex_unlock(&Globctx->IonRefLock);
        return 0;
    }
    
    item = head->prev;
    while(item && item->frame_index < Globctx->HWCFramecount-2)
    {
        int ret = -1;
				
        ret = ion_free(Globctx->ion_fd, (ion_user_handle_t)item->handle);
        if(ret < 0)
        {
            ALOGE("%s ION_IOC_FREE error, frame_index:%d ion_handle:%p", __func__,item->frame_index, item->handle);
            pthread_mutex_unlock(&Globctx->IonRefLock);
            return -1;
        }
        ALOGV("########remove %d %p",item->frame_index, item->handle);

        if(item == head)
        {
            free(item);
            item = NULL;
            head = NULL;
        }
        else
        {
            ion_list * item_tmp;
            
            item_tmp = item->prev;
            
            item->next->prev = item->prev;
            item->prev->next = item->next;
            free(item);
            
            item = item_tmp;
        }
        Globctx->IonHandleHead = head;
    }
    
    pthread_mutex_unlock(&Globctx->IonRefLock);

    return 0;  
}
