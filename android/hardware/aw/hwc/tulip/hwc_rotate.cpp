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

int hwc_rotate_query(unsigned long tr_handle)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long arg[4] = {0};
    int ret = -1;
    arg[0] = tr_handle;
    ret = ioctl(Globctx->tr_fd, TR_QUERY, (unsigned long)arg);
    return ret;
}

bool hwc_rotate_request(void)
{
	SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long arg[4] = {0};
    unsigned long   tr;
    int ret;
    arg[0] = (unsigned long)&tr;
    ret = ioctl(Globctx->tr_fd, TR_REQUEST, (unsigned long)&arg);
    if(ret < 0)
    {
        Globctx->tr_handle = 0;
        ALOGD("request ratate module err");
        return 0;
    }
    Globctx->tr_handle = tr;
    return 1;
}

bool hwc_rotate_commit(tr_info *tr_info)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long arg[4] = {0};
    int ret = -1;
    arg[0] = Globctx->tr_handle;
    arg[1] = (unsigned long)tr_info;
    ret = ioctl(Globctx->tr_fd, TR_COMMIT, (unsigned long)arg);
    if(ret < 0)
    {
        ALOGD("commit rotate err");
        return 0;
    }
    return !ret;
}

void hwc_rotate_release(void)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long arg[4] = {0};
    int ret = -1;
    arg[0] = Globctx->tr_handle;
    ret = ioctl(Globctx->tr_fd, TR_RELEASE, (unsigned long)arg);
    if(ret < 0)
    {
        ALOGD("release rotate err");
    }
    Globctx->tr_handle = 0;
}

bool hwc_rotate_settimeout(unsigned long ms_time)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long arg[4] = {0};
    int ret = -1;
    arg[0] = Globctx->tr_handle;
    arg[1] = ms_time;
    ret = ioctl(Globctx->tr_fd, TR_SET_TIMEOUT, (unsigned long)arg);
    return !ret;
}

int culate_timeout(disp_rectsz disp_src)
{
    unsigned int dst = disp_src.width * disp_src.height;
    if(dst > 2073600)
    {
        return 100;
    }
    if(dst > 1024000)
    {
        return 50;
    }
    return 32;
}

static rotate_cache_t *hwc_ratate_cache_manage(SUNXI_hwcdev_context_t *Globctx, int cnt_st)
{
    int i = 0;
    rotate_cache_t *ratate_cache = NULL;
    list_head_t *head = &Globctx->rotate_cache_list;
    if(Globctx->rotate_hold_cnt >= cnt_st)
    {
        for(i=0; i<cnt_st; i++)
        {
            head = head->next;
        }
        ratate_cache = (rotate_cache_t *)head;
    }else{
        ratate_cache = (rotate_cache_t *)calloc(1, sizeof(rotate_cache_t));
        if(ratate_cache == NULL)
        {
            return NULL;
        }
        memset(ratate_cache, 0, sizeof(rotate_cache_t));
        for(i = 0; i < ROTATE_CACHE_COUNT; i++)
        {
            ratate_cache->rotate_cache[i].fd = -1;
            ratate_cache->rotate_cache[i].share_fd = -1;
        }
        hwc_list_put(&Globctx->rotate_cache_list, &ratate_cache->head);
        Globctx->rotate_hold_cnt++;
        ALOGD("alloc rotate_cache_t[%d]",Globctx->rotate_hold_cnt);
    }
    return ratate_cache;
}

static hwc_cache_t *hwc_tr_cache_get(rotate_cache_t *rotate_cache, int size, int fd, unsigned int sync_count, bool is_secure)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    hwc_cache_t *tr_cache = NULL;
    int i = 0, litte = 0, ret = -1;

    unsigned int litte_sync = rotate_cache->rotate_cache[0].sync_cnt;

    for(i = 0; i < ROTATE_CACHE_COUNT; i++)
    {
        tr_cache = &rotate_cache->rotate_cache[i];
        if(tr_cache->sync_cnt == 0)
        {
            litte = i;
            break;
        }
        if(litte_sync > tr_cache->sync_cnt)
        {
            litte = i;
            litte_sync = tr_cache->sync_cnt;
        }
    }
    tr_cache = &rotate_cache->rotate_cache[litte];
    /*Never go there*/
    if(tr_cache->fd >= 0)
    {
        if(sync_wait(tr_cache->fd, 300) < 0)
        {
            ALOGD("wait relesefence fd:%d,sync:%d(%d),size:%d",tr_cache->fd,
                tr_cache->sync_cnt, Globctx->HWCFramecount, tr_cache->size_buffer);
        }
        close(tr_cache->fd);
        tr_cache->fd = -1;
    }
    if(tr_cache->share_fd >= 0 && size <= tr_cache->size_buffer && tr_cache->is_secure == is_secure)
    {
        if(tr_cache->size_buffer - size > 4096)
        {
            close(tr_cache->share_fd);
            tr_cache->share_fd = -1;
            tr_cache->size_buffer = 0;
        }
    }else{
        if(tr_cache->share_fd >= 0)
        {
            close(tr_cache->share_fd);
        }
        tr_cache->share_fd = -1;
        tr_cache->size_buffer = 0;
    }
    if(tr_cache->share_fd == -1 || tr_cache->size_buffer == 0)
    {
		if(is_secure){
			ret = ion_alloc_fd(Globctx->IonFd, size,
                4096,  ION_HEAP_SECURE_MASK, 0, &tr_cache->share_fd);
            if(ret < 0)
            {
                 ALOGD("alloc err from ION_HEAP_SECURE_MASK");
                 return NULL;
            }
            tr_cache->is_secure = 1;
		}else{
			ret = ion_alloc_fd(Globctx->IonFd, size,
                4096, ION_HEAP_TYPE_DMA_MASK, 0, &tr_cache->share_fd);
	        if(ret < 0)
	        {
	            ALOGD("alloc err from ION_HEAP_TYPE_DMA_MASK");
	            ret =  ion_alloc_fd(Globctx->IonFd, size,
	                    4096, ION_HEAP_SYSTEM_CONTIG_MASK, 0, &tr_cache->share_fd);
	            if(ret < 0)
	            {
	                ALOGD("alloc err from ION_HEAP_SYSTEM_CONTIG_MASK");
	                tr_cache->share_fd = -1;
	                tr_cache->size_buffer = 0;
	                return NULL;
	            }
	        }
		}
        if(!is_secure){
        	ion_sync_fd(Globctx->IonFd, tr_cache->share_fd);
		}
        tr_cache->size_buffer = size;
    }
    tr_cache->sync_cnt = sync_count;
    tr_cache->valid = 0;
    tr_cache->fd = dup(fd);
    return tr_cache;
}

static hwc_cache_t *hwc_cache_get_last(rotate_cache_t *rotate_cache, unsigned int sync_count)
{
    hwc_cache_t *tr_cache = NULL;
    int i = 0, biggest = -1, ret = -1;
    unsigned int big_sync = 0;

    for(i = 0; i < ROTATE_CACHE_COUNT; i++)
    {
        tr_cache = &rotate_cache->rotate_cache[i];
        if(big_sync < tr_cache->sync_cnt
            && tr_cache->sync_cnt != sync_count
            && tr_cache->valid)
        {
            big_sync = tr_cache->sync_cnt;
            biggest = i;
        }
    }
    if(biggest >= 0)
    {
        tr_cache = &rotate_cache->rotate_cache[biggest];
        if(tr_cache->share_fd >= 0)
        {
            return tr_cache;
        }
    }
    return NULL;
}

void hwc_rotate_cache_free(void)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    hwc_cache_t *tr_cache = NULL;
    rotate_cache_t *rotate_cache = NULL;
    int i = 0, j = 0;
    list_head_t *head = NULL;
    ALOGD("hwc_rotate_cache_free[%d]",Globctx->rotate_hold_cnt);
    while(NULL != (head = hwc_list_get(&Globctx->rotate_cache_list)))
    {
        rotate_cache = (rotate_cache_t *)head;
        for(i = 0; i < ROTATE_CACHE_COUNT; i++)
        {
            tr_cache = &rotate_cache->rotate_cache[i];
            if(tr_cache->share_fd >= 0)
            {
                close(tr_cache->share_fd);
            }
            if(tr_cache->fd >= 0)
            {
                close(tr_cache->fd);
            }
            tr_cache->fd = -1;
            tr_cache->share_fd = -1;
            tr_cache->size_buffer = 0;
            tr_cache->sync_cnt = 0;
        }
        Globctx->rotate_hold_cnt--;
    }
    Globctx->rotate_hold_cnt = 0;
}

static int inline hwc_disp_pixel_bytes(disp_pixel_format format)
{
    switch(format) 
    {
        case DISP_FORMAT_YUV420_P:
	    case DISP_FORMAT_YUV420_SP_VUVU:
        case DISP_FORMAT_YUV420_SP_UVUV:
            return 12;

        case DISP_FORMAT_ABGR_8888:
        case DISP_FORMAT_XBGR_8888:
        case DISP_FORMAT_ARGB_8888:
	    case DISP_FORMAT_XRGB_8888:
	        return 32;
        case DISP_FORMAT_BGR_888:
            return 24;
        case DISP_FORMAT_RGB_565:
            return 16;
        default:
            return 0;
    } 
    return 0;
}

static inline int hwc_disp_layer_plan(disp_pixel_format format)
{
    switch(format) 
    {
        case DISP_FORMAT_YUV420_P:
            return 3;

	    case DISP_FORMAT_YUV420_SP_VUVU:
        case DISP_FORMAT_YUV420_SP_UVUV:
            return 2;

        case DISP_FORMAT_ABGR_8888:
        case DISP_FORMAT_XBGR_8888:
        case DISP_FORMAT_ARGB_8888:
	    case DISP_FORMAT_XRGB_8888:
        case DISP_FORMAT_RGB_565:
            return 1;
        default:
            return 1;
    } 
}

static inline disp_pixel_format tr_to_disp(tr_pixel_format tr_format)
{
    switch(tr_format)
    {
        case TR_FORMAT_YUV420_SP_VUVU:
            return DISP_FORMAT_YUV420_SP_VUVU;
        case TR_FORMAT_YUV420_P:
            return DISP_FORMAT_YUV420_P;
        case TR_FORMAT_ABGR_8888:
            return DISP_FORMAT_ABGR_8888;
        case TR_FORMAT_XBGR_8888:
            return DISP_FORMAT_XBGR_8888;
        case TR_FORMAT_ARGB_8888:
            return DISP_FORMAT_ARGB_8888;
	    case TR_FORMAT_XRGB_8888:
            return DISP_FORMAT_XRGB_8888;
        case TR_FORMAT_RGB_565:
            return DISP_FORMAT_RGB_565;
        default :
            return DISP_FORMAT_YUV420_P;
    }
}

static inline tr_pixel_format disp_to_tr(disp_pixel_format disp_format)
{
    switch(disp_format)
    {
        /* Hardware TR only support YV12 output, sync with TR driver */
        case DISP_FORMAT_YUV420_SP_VUVU:
            return TR_FORMAT_YUV420_SP_VUVU;
        case DISP_FORMAT_YUV420_P:
            return TR_FORMAT_YUV420_P;

        case DISP_FORMAT_ABGR_8888:
            //return TR_FORMAT_ABGR_8888;
        case DISP_FORMAT_XBGR_8888:
            //return TR_FORMAT_XBGR_8888;
	    case DISP_FORMAT_XRGB_8888:
            //return TR_FORMAT_XRGB_8888;
        case DISP_FORMAT_ARGB_8888:
            return TR_FORMAT_ARGB_8888;
        case DISP_FORMAT_RGB_565:
            return TR_FORMAT_RGB_565;
        default :
            return TR_FORMAT_YUV420_P;
    }
}

static inline tr_mode rotate_switch(unsigned int mode)
{
    tr_mode ret = TR_ROT_0;
    switch(mode)
    {
        case HAL_TRANSFORM_FLIP_H:
            ret = TR_HFLIP;
        break;
        case HAL_TRANSFORM_FLIP_V:
            ret = TR_VFLIP;
        break;
        case HAL_TRANSFORM_ROT_90:
            ret = TR_ROT_90;
        break;
        case HAL_TRANSFORM_ROT_180:
            ret = TR_ROT_180;
        break;
        case HAL_TRANSFORM_ROT_270:
            ret = TR_ROT_270;
        break;
        case (HAL_TRANSFORM_FLIP_H | HAL_TRANSFORM_ROT_90):
            ret = TR_VFLIP_ROT_90;
        break;
        case (HAL_TRANSFORM_FLIP_V | HAL_TRANSFORM_ROT_90):
            ret = TR_HFLIP_ROT_90;
        break;
        default:
            ret = TR_ROT_0;
    }
    return ret;
}

static inline bool hwc_swap_w_h(tr_mode mode)
{
    switch(mode)
    {
        case TR_ROT_90:
        case TR_ROT_270:
        case TR_HFLIP_ROT_90:
        case TR_VFLIP_ROT_90:
            return 1;
        break;
        default:
            return 0;
    }
}

bool hwc_layer_to_tr(disp_fb_info* hw_layer, tr_info *tr_info, hwc_cache_t *tr_buffer, unsigned int tr)
{

    unsigned long addr = 0;
    int i = 0;
    int cnt = hwc_disp_layer_plan(hw_layer->format);
    tr_info->src_frame.fmt = disp_to_tr(hw_layer->format);
    unsigned int w_stride, h_stride;
    tr_info->mode = rotate_switch(tr);
    i = 0;
    while(i < cnt)
    {
        tr_info->src_frame.haddr[i] = 0;
        tr_info->src_frame.laddr[i] = hw_layer->addr[i];
        tr_info->src_frame.pitch[i] = hw_layer->size[i].width;
        tr_info->src_frame.height[i] = hw_layer->size[i].height;
        i++;
    }
    tr_info->src_rect.x = 0;
    tr_info->src_rect.y = 0;
    tr_info->src_rect.w = hw_layer->size[0].width;
    tr_info->src_rect.h = hw_layer->size[0].height;
    addr = ion_get_addr_fromfd(tr_buffer->share_fd);
    if(addr == 0)
    {
        ALOGD("get ion addr err....");
        return 0;
    }
    addr = ALIGN(addr, 4);

    tr_info->dst_frame.fmt = disp_to_tr(hw_layer->format);
    if(cnt != 1)
    {
    	tr_info->dst_frame.fmt = TR_FORMAT_YUV420_P;
    }
    tr_info->dst_rect.x = 0;
    tr_info->dst_rect.y = 0;
    if(hwc_swap_w_h(tr_info->mode))
    {
        w_stride = ALIGN(hw_layer->size[0].height, ROTATE_ALIGN);
        h_stride = ALIGN(hw_layer->size[0].width, ROTATE_ALIGN);
    }else{
        w_stride = ALIGN(hw_layer->size[0].width, ROTATE_ALIGN);
        h_stride = ALIGN(hw_layer->size[0].height, ROTATE_ALIGN);
    }
    tr_info->dst_frame.pitch[0] = w_stride;
    tr_info->dst_frame.height[0] = h_stride;
    if(cnt != 1)
    {
        tr_info->dst_frame.pitch[1] = w_stride/2;
        tr_info->dst_frame.height[1] = h_stride/2;
        tr_info->dst_frame.pitch[2] = w_stride/2;
        tr_info->dst_frame.height[2] = h_stride/2;
    }
    tr_info->dst_rect.w = w_stride;
    tr_info->dst_rect.h = h_stride;

    tr_info->dst_frame.haddr[0] = 0;
    tr_info->dst_frame.haddr[1] = 0;
    tr_info->dst_frame.haddr[2] = 0;
    tr_info->dst_frame.laddr[0] = addr;
    /*YUV format we only support YV12  --> TR_FORMAT_YUV420_P*/
    /*tr_info->dst_frame.laddr[0]  --> Y*/
    /*tr_info->dst_frame.laddr[1]  --> U*/
    /*tr_info->dst_frame.laddr[2]  --> V*/
    if(cnt != 1)// is YUV Format
    {
        tr_info->dst_frame.laddr[2] = tr_info->dst_frame.laddr[0] + 
                tr_info->dst_frame.pitch[0] * tr_info->dst_frame.height[0];
        tr_info->dst_frame.laddr[1] = tr_info->dst_frame.laddr[2] + 
                tr_info->dst_frame.pitch[2] * tr_info->dst_frame.height[2];
    }
    return 1;
}

void hwc_resize_crop(disp_fb_info *hw_layer, int w_original, int h_original, tr_mode mode)
{
    int w_diff, h_diff;
    switch(mode)
    {
        case TR_HFLIP:
            w_diff = hw_layer->size[0].width - w_original; 
            hw_layer->crop.x + (long long)(((long long)w_diff)<<32);
        break;
        case TR_VFLIP:
            h_diff = hw_layer->size[0].height - h_original;
            hw_layer->crop.y + (long long)(((long long)h_diff)<<32);
        break;
        case TR_ROT_90:
            w_diff = hw_layer->size[0].width - h_original; 
            hw_layer->crop.x + (long long)(((long long)w_diff)<<32);
            h_diff = hw_layer->size[0].height - w_original;
            hw_layer->crop.y + (long long)(((long long)h_diff)<<32);
        break;
        case TR_ROT_180:
            h_diff = hw_layer->size[0].height - h_original;
            hw_layer->crop.y + (long long)(((long long)h_diff)<<32);
            w_diff = hw_layer->size[0].width - w_original; 
            hw_layer->crop.x + (long long)(((long long)w_diff)<<32);
        break;
        case TR_ROT_270:
        break;
        case TR_HFLIP_ROT_90:
        break;
        case TR_VFLIP_ROT_90:
        break;
        default:
            ALOGD("give a bad tr");
    }
}

bool hwc_need_check_format(unsigned char fmt)
{
    switch(fmt)
    {
        case TR_FORMAT_YUV420_P:
        case TR_FORMAT_YUV420_SP_VUVU:
            return 1;
        default:
            return 0;
    }
    return 0;
}

bool hwc_tr_to_layer(disp_fb_info *hw_layer, tr_info *tr_info, hwc_cache_t *tr_buffer, bool last)
{
    int i = 0;
    unsigned int swap_w_h = 0;
    unsigned long addr = 0;
    unsigned int w_stride, h_stride;
    int w_change, h_change, w_original, h_original;

    if(hwc_need_check_format(tr_info->dst_frame.fmt))
    {
        hw_layer->format = tr_to_disp((tr_pixel_format)tr_info->dst_frame.fmt);
    }
    w_original = hw_layer->size[0].width;
    h_original = hw_layer->size[0].height;
    if(!last)
    {
        hw_layer->addr[0] = tr_info->dst_frame.laddr[0];
        hw_layer->addr[1] = tr_info->dst_frame.laddr[1];
        hw_layer->addr[2] = tr_info->dst_frame.laddr[2];

        hw_layer->size[0].width = tr_info->dst_frame.pitch[0];
        hw_layer->size[1].width = tr_info->dst_frame.pitch[1];
        hw_layer->size[2].width = tr_info->dst_frame.pitch[2];

        hw_layer->size[0].height = tr_info->dst_frame.height[0];
        hw_layer->size[1].height = tr_info->dst_frame.height[1];
        hw_layer->size[2].height = tr_info->dst_frame.height[2];

        hw_layer->align[0] = ROTATE_ALIGN;
        hw_layer->align[1] = ROTATE_ALIGN/2;
        hw_layer->align[2] = ROTATE_ALIGN/2;
        goto tr_ok;
    }else{
        addr = ion_get_addr_fromfd(tr_buffer->share_fd);
        if(addr == 0)
        {
            return 0;
        }
        addr = ALIGN(addr, 4);
        if(hwc_swap_w_h(tr_info->mode))
        {
            w_stride = ALIGN(hw_layer->size[0].height, ROTATE_ALIGN);
            h_stride = ALIGN(hw_layer->size[0].width, ROTATE_ALIGN);
        }else{
            w_stride = ALIGN(hw_layer->size[0].width, ROTATE_ALIGN);
            h_stride = ALIGN(hw_layer->size[0].height, ROTATE_ALIGN);
        }
        hw_layer->addr[0] = addr;
        if(hw_layer->format == DISP_FORMAT_YUV420_P)
        {
            hw_layer->addr[2] = hw_layer->addr[0] 
                + w_stride * h_stride ;
            hw_layer->addr[1] = hw_layer->addr[2]
                + w_stride * h_stride / 4;
            hw_layer->align[0] = ROTATE_ALIGN;
            hw_layer->align[1] = ROTATE_ALIGN/2;
            hw_layer->align[2] = ROTATE_ALIGN/2;
        }
    }
 tr_ok:
    //hwc_resize_crop(hw_layer,w_original,h_original,tr_info->mode);
    return 1;
}

void hwc_rotate_finish(int cnt_st)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    list_head_t *head = NULL;
    int j = 0;
    if(cnt_st < Globctx->rotate_hold_cnt)
    {
        head = Globctx->rotate_cache_list.prev;
        hwc_list_del(head);
        Globctx->rotate_hold_cnt--;
    }
}

bool hwc_rotate_layer_tr(hwc_dispc_data_t *hwc_layer,
            hwc_commit_data_t *commit_data, int disp, int layer, int cnt_st)
{

    disp_layer_config  *disp_layer = NULL;
    hwc_commit_layer_t *commit_layer = NULL;
    int lyr = 0, size = 0, i = 0, ret = -1, width = 0, height = 0;
    hwc_cache_t *tr_cache = NULL;
    tr_info tr_info;
    bool last = 0;
    static int continuous_failed = 0;
    rotate_cache_t *rotate_cache = NULL;
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;

    commit_layer = &hwc_layer->hwc_layer_info[disp][layer];
    lyr = NUMLAYEROFCHANNEL * commit_layer->hwc_layer_info.channel
            + commit_layer->hwc_layer_info.layer_id;
    disp_layer = &commit_data->hwc_layer_info[disp][lyr];
    disp_fb_info *fb_crop = &disp_layer->info.fb;

    size += ALIGN(fb_crop->size[0].width, ROTATE_ALIGN)
            * ALIGN(fb_crop->size[0].height, ROTATE_ALIGN)
            * hwc_disp_pixel_bytes(fb_crop->format) /8
            + 4;

    ret = culate_timeout(disp_layer->info.fb.size[0]);
    if(ret != Globctx->tr_time_out)
    {
        hwc_rotate_settimeout(ret);
        Globctx->tr_time_out = ret;
    }
    rotate_cache = hwc_ratate_cache_manage(Globctx, cnt_st);
    if(rotate_cache == NULL)
    {
        ALOGE("we got a NULL of rotate_cache_t");
        goto translat_err;
    }
    tr_cache = hwc_tr_cache_get(rotate_cache, size,
                commit_data->releasefencefd[disp], hwc_layer->sync_count,commit_layer->is_secure);
    if(tr_cache != NULL)
    {
        memset(&tr_info, 0, sizeof(tr_info));
        if(!hwc_layer_to_tr(&disp_layer->info.fb, &tr_info, tr_cache, commit_layer->tr)
            || 1 == hwc_rotate_query(Globctx->tr_handle))
        {
            tr_cache = hwc_cache_get_last(rotate_cache, hwc_layer->sync_count);
            continuous_failed++;
            if(tr_cache == NULL || continuous_failed >= 3)
            {
                goto translat_err;
            }else{
                last = 1;
                goto tr2layer;
            }
        }
        if(!hwc_rotate_commit(&tr_info))
        {
            goto translat_err;
        }
        ret = 1;
        i = (Globctx->tr_time_out * 1000/16);//video 30 fps
        while(ret != 0 && i)
        {
            ret = hwc_rotate_query(Globctx->tr_handle);
            if(ret == -1)
            {
                break;
            }
            usleep(16);
            i--;
        }
        if(ret)
        {
            continuous_failed++;
            tr_cache->sync_cnt = 0;
            if(tr_cache->fd >= 0)
            {
                close(tr_cache->fd);
                tr_cache->fd = -1;
            }
            tr_cache = hwc_cache_get_last(rotate_cache, hwc_layer->sync_count);

            ALOGD("rotate query err:%d continuous_failed:%d wait:%dms at frame:%d",
                ret, continuous_failed,Globctx->tr_time_out, hwc_layer->sync_count);

            last = 1;
            if(tr_cache == NULL || continuous_failed >= 3)
            {
                goto translat_err;
            }
        }else{
            tr_cache->valid = 1;
            continuous_failed = 0;
            last = 0;
        }
tr2layer:
        if(!hwc_tr_to_layer(&disp_layer->info.fb, &tr_info, tr_cache, last))
        {
            goto translat_err;
        }
    }else{
        goto translat_err;
    }
translat_ok:
    close(commit_layer->share_fd);
    commit_layer->share_fd = dup(tr_cache->share_fd);
    commit_layer->tr = 0;
    return 1;

translat_err:
    disp_layer->enable =0;
    ALOGE("######hwc get a rotate err#####");
    return 0;
    
}
 
