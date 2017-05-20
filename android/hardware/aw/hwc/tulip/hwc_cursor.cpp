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

hwc_cursor_async_t *find_little_cache(hwc_cursor_async_t *cache_array, int count)
{
    int i = 0, fix = 0;
    unsigned int little = cache_array->sync_count;
    for(i=0; i<count; i++)
    {
        if(!(cache_array+i)->vaild)
        {
            fix = i;
            break;
        }
        if(little > ((cache_array+i)->sync_count))
        {
            little = (cache_array+i)->sync_count;
            fix = i; 
        }
    }
    return cache_array+fix;
}

hwc_cursor_async_t *find_fix_cache(hwc_cursor_async_t *cache_array, int count, unsigned int fix)
{
    int i = 0;
    for(i=0; i<count; i++)
    {
        if(fix == ((cache_array+i)->sync_count) && (cache_array+i)->vaild)
        {
            return cache_array+i; 
        }
    }
    return NULL;
}

int hwc_set_cursor_async(struct hwc_composer_device_1 *dev, int disp, int x_pos, int y_pos)
{
    HWC_UNREFERENCED_PARAMETER(dev);
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    hwc_cursor_async_t *fix_cache = NULL;
    if(disp > HWC_DISPLAY_EXTERNAL)
    {
        return -ENODEV;
    }
    fix_cache = find_little_cache(Globctx->SunxiDisplay[disp].cursor_cache, CURSOR_CACHE_COUNT);
    fix_cache->write_read_lock++;
    fix_cache->x_pos = x_pos;
    fix_cache->y_pos = y_pos;
    fix_cache->ture_disp = Globctx->SunxiDisplay[disp].VirtualToHWDisplay;
    fix_cache->sync_count = Globctx->HWCFramecount - 1;
    fix_cache->vaild = 1;
    Globctx->cursor_sync = Globctx->HWCFramecount - 1;
    Globctx->CommitCondition.broadcast();

    return 0;
}

bool cursor_set_disp(SUNXI_hwcdev_context_t *Globctx,
        disp_layer_config  *hwc_layer_info, hwc_cursor_async_t *set_cursor)
{
    unsigned long arg[4] = {0};
    unsigned int write_read_lock =  set_cursor->write_read_lock;
    hwc_layer_info->info.screen_win.x = set_cursor->x_pos;
    hwc_layer_info->info.screen_win.y = set_cursor->y_pos;
    set_cursor->vaild = 0;
    if(write_read_lock == set_cursor->write_read_lock)
    {
        arg[0] = set_cursor->ture_disp;
        arg[2] = 1;
        arg[1] = (unsigned long)hwc_layer_info;
        ioctl(Globctx->DisplayFd, DISP_LAYER_SET_CONFIG, (unsigned long)arg);

        return 1;
    }
    return 0;
}

bool rotate_mem(void *src_mem_a, void *dst_mem_a, unsigned int tr, disp_rectsz *size)
{
    unsigned int width, height;
    unsigned int i = 0, j =0;
    width = size->width;
    height = size->height;
    unsigned int *t_mem = NULL;
    unsigned int *src_mem = (unsigned int *)src_mem_a;
    unsigned int *dst_mem = (unsigned int *)dst_mem_a;
    switch(tr)
    {
        case  HAL_TRANSFORM_ROT_90:
            src_mem += (height * (width-1));
            for(j = 0; j < width; j++, src_mem++)
            {
                t_mem = src_mem;
                for(i = 0; i < height; i++, dst_mem++, t_mem -= width)
                {
                    *dst_mem = *t_mem;
                }
            }
            size->width = height;
            size->height = width;
        break;
        case  HAL_TRANSFORM_ROT_180:
            src_mem += (width * height-1);
            for(j = 0; j < height; j++, src_mem -= width)
            {
                t_mem = src_mem;
                for(i = 0; i < width; i++, dst_mem++, t_mem--)
                {
                    *dst_mem = *t_mem;
                }
            }
        break;
        case  HAL_TRANSFORM_ROT_270:
            src_mem += (width-1);
            for(j = 0; j < width; j++, src_mem--)
            {
                t_mem = src_mem;
                for(i = 0; i < height; i++, dst_mem++, t_mem += (width))
                {
                    *(unsigned int*)dst_mem = *(unsigned int*)t_mem;
                }
            }
            size->width = height;
            size->height = width;
        break;
        default:
            return 1;
    } 
    return 0;
}

bool rotate_cusor(SUNXI_hwcdev_context_t *Globctx,
        hwc_commit_layer_t *rotate_layer, hwc_commit_layer_t *commit_layer)
{
    int size = 0;
    int shared_fd = -1;
    void *addr_0 = NULL, *addr_1 = NULL;
    size = commit_layer->hwc_layer_info.info.fb.size[0].width 
        * commit_layer->hwc_layer_info.info.fb.size[0].height * 4;//cursor only ARGB
    if( 0 > ion_alloc_fd(Globctx->IonFd, size,0, ION_HEAP_TYPE_DMA_MASK,0, &shared_fd))
    {
        ALOGD("alloc ion err....");
        goto rotate_err;
    }
    addr_0 = mmap(NULL, size, PROT_READ|PROT_WRITE, MAP_SHARED, shared_fd, 0);
    if(addr_0 == NULL)
    {
        ALOGD("mmap 0 err...");
        goto rotate_err;
    }

    addr_1 = mmap(NULL, size, PROT_READ|PROT_WRITE, MAP_SHARED, commit_layer->share_fd, 0);
    if(addr_1 == NULL)
    {
        ALOGD("mmap 1 err...");
        goto rotate_err;
    }
    if(rotate_mem(addr_1, addr_0, commit_layer->tr,
            &commit_layer->hwc_layer_info.info.fb.size[0]))
    {
        ALOGD("rotate mem err...");
        goto rotate_err;
    }
    rotate_layer->hwc_layer_info = commit_layer->hwc_layer_info;
    rotate_layer->share_fd = shared_fd;
    rotate_layer->aquirefencefd = -1;
    rotate_layer->iscursor = 1;
    rotate_layer->needsync = 0;
    rotate_layer->hwc_layer_info.info.fb.addr[0] = ion_get_addr_fromfd(shared_fd);
    rotate_layer->hwc_layer_info.info.fb.size[0] = commit_layer->hwc_layer_info.info.fb.size[0];
    munmap(addr_0,size);
    munmap(addr_1,size);

    return 0;
rotate_err:
    if(addr_0 != 0)
    {
        munmap(addr_0, size);
    }
    if(addr_1 != 0)
    {
        munmap(addr_1, size);
    }
    if(shared_fd >= 0)
    {
        close(shared_fd);
    }
    return 1;
}

bool get_rotate_cursor_layer(SUNXI_hwcdev_context_t *Globctx,
        hwc_commit_layer_t *commit_layer)
{
    int i = -1;
    hwc_commit_layer_t *rotate_layer = NULL;
    switch(commit_layer->tr)
    {
        case  HAL_TRANSFORM_ROT_90:
            i = 0;
        break;
        case  HAL_TRANSFORM_ROT_180:
            i = 1;
        break;
        case  HAL_TRANSFORM_ROT_270:
            i = 2;
        break;
        default:
            i = -1;
    }
    if(i != -1)
    {
        rotate_layer = &Globctx->cursor_rotate_layer[i];
        if(rotate_layer->share_fd < 0)
        {
            ion_sync_fd(Globctx->IonFd, commit_layer->share_fd);
            if(rotate_cusor(Globctx, rotate_layer, commit_layer))
            {
                return 1;
            }
            ion_sync_fd(Globctx->IonFd, rotate_layer->share_fd);
            rotate_layer->tr = commit_layer->tr;
        }
        commit_layer->needsync = 0;
        close(commit_layer->share_fd);
        commit_layer->share_fd = -1;
        commit_layer->hwc_layer_info.info.fb.addr[0] =
                        rotate_layer->hwc_layer_info.info.fb.addr[0];
        commit_layer->hwc_layer_info.info.fb.size[0] =
                        rotate_layer->hwc_layer_info.info.fb.size[0];
    }
    return 0;
}

void free_rotate_cursor_layer(SUNXI_hwcdev_context_t *Globctx)
{
    int i = 0;
    hwc_commit_layer_t *rotate_cursor_layer = NULL;
    for(i = 0; i<ROTATE_CACHE_COUNT; i++)
    {
        rotate_cursor_layer = &Globctx->cursor_rotate_layer[i];
        if(rotate_cursor_layer->share_fd >= 0)
        {
            close(rotate_cursor_layer->share_fd);
        }
        rotate_cursor_layer->share_fd = -1;
        rotate_cursor_layer->aquirefencefd = -1;
        rotate_cursor_layer->needsync = 0;
        rotate_cursor_layer->tr = 0;
        rotate_cursor_layer->iscursor = 0;
    }
}

bool update_cursor_layer(SUNXI_hwcdev_context_t *Globctx,
            hwc_commit_layer_t *cursor_layer, hwc_dispc_data_t *DisplayData)
{
    int i = 0;
    bool has_cursor = 0;
    for(i=0; i<NUMBEROFDISPLAY; i++)
    {
        if(DisplayData->cursor_in_disp[i] != -1)
        {
            memcpy(&cursor_layer[i],
                &DisplayData->hwc_layer_info[i][DisplayData->cursor_in_disp[i]], sizeof(hwc_commit_layer_t));
            has_cursor = 1;
            /*rotate cursor manage*/
            get_rotate_cursor_layer(Globctx, &cursor_layer[i]);
        }else{
            cursor_layer[i].iscursor = 0;
        }
    }
    if(!has_cursor && Globctx->has_cursor)
    {
        Globctx->has_cursor = 0;
        free_rotate_cursor_layer(Globctx);
    }
    return has_cursor;
}

void hwc_cursor_manage(SUNXI_hwcdev_context_t *Globctx,
        hwc_commit_layer_t *cursor_layer, unsigned int sync_count)
{
    int i = 0, disp = -1;
    DisplayInfo *cursor_disp = NULL;
    hwc_cursor_async_t *set_cursor = NULL;
    bool  has_cursor = 0;

    for(i = 0; i < Globctx->NumberofDisp; i++)
    {
        if(cursor_layer[i].iscursor)
        {
            disp = hwc_manage_display(&cursor_disp, i, FIND_HWDISPNUM);
            if(cursor_disp != NULL)
            {
                set_cursor = find_fix_cache(cursor_disp->cursor_cache, CURSOR_CACHE_COUNT, sync_count);
                if(set_cursor != NULL && set_cursor->ture_disp == i)
                {
                    cursor_set_disp(Globctx, &cursor_layer[i].hwc_layer_info, set_cursor);
                }
            }
        }
    }
}
