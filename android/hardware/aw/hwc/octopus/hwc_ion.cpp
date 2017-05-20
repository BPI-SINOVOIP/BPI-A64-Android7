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

ion_user_handle_t ion_alloc_buffer(int iAllocBytes, unsigned int heap_mask)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int ret = -1;

	struct ion_allocation_data sAllocInfo =
	{
		.len		= (size_t)ALIGN(iAllocBytes, 4096),
		.align		= 4096,
		.heap_id_mask	= heap_mask,
		.flags		= 0,
		.handle     = 0,
	};
	ret = ioctl(Globctx->IonFd, ION_IOC_ALLOC, &sAllocInfo);
	if(ret < 0)
	{
	    ALOGW("%s: ION_IOC_ALLOC failed (ret=%d)", __func__,  ret);
	    return (ion_user_handle_t) -1;
	}
	
	return sAllocInfo.handle;
}

int ion_free_buffer(ion_user_handle_t handle)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    ion_handle_data freedata;
    int ret = -1;

    freedata.handle = handle;
    ret = ioctl(Globctx->IonFd, ION_IOC_FREE, &freedata);
    if(ret < 0)
    {
        ALOGE("%s: ION_IOC_FREE failed (ret=%d)", __func__, ret);
    }

	return ret;
}

unsigned int ion_get_addr_fromfd(int sharefd)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int ret = -1;
    struct ion_custom_data custom_data;
	sunxi_phys_data phys_data;
    ion_handle_data freedata;
    struct ion_fd_data data ;
    
    data.fd = sharefd;
    ret = ioctl(Globctx->IonFd, ION_IOC_IMPORT, &data);
    if (ret < 0)
    {
        ALOGE("%s: ION_IOC_IMPORT failed(ret=%d)", __func__, ret);
        return 0;
    }
    custom_data.cmd = ION_IOC_SUNXI_PHYS_ADDR;
	phys_data.handle = data.handle;
	custom_data.arg = (unsigned long)&phys_data;
	ret = ioctl(Globctx->IonFd, ION_IOC_CUSTOM, &custom_data);
	if(ret < 0)
    {
        ALOGE("%s: ION_IOC_CUSTOM failed(ret=%d)", __func__, ret);
        return 0;
    }
    freedata.handle = data.handle;
    ret = ioctl(Globctx->IonFd, ION_IOC_FREE, &freedata);
    if(ret < 0)
    {
        ALOGE("%s: ION_IOC_FREE failed(ret=%d)", __func__, ret);
        return 0;
    }
    return phys_data.phys_addr;  
}

unsigned long ion_get_addr_from_handle(ion_user_handle_t handle)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    struct ion_custom_data custom_data;
	sunxi_phys_data phys_data;
    int ret = -1;

	phys_data.handle = handle;
    custom_data.cmd = ION_IOC_SUNXI_PHYS_ADDR;
	custom_data.arg = (unsigned long)&phys_data;
	ret = ioctl(Globctx->IonFd, ION_IOC_CUSTOM, &custom_data);
	if(ret < 0)
    {
        ALOGE("%s: ION_IOC_CUSTOM failed (ret=%d)", __func__, ret);
        return 0;
    }
	return phys_data.phys_addr;
}

ion_user_handle_t ion_handle_add_ref(int sharefd)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    struct ion_fd_data data;
    data.fd = sharefd;
    int ret;
    ret = ioctl(Globctx->IonFd, ION_IOC_IMPORT, &data);
    if (ret < 0)
    {
        ALOGE("%s: ION_IOC_IMPORT failed(ret=%d)", __func__, ret);
        return -1;
    }
    return data.handle;  
}

void ion_handle_dec_ref(ion_user_handle_t handle_id)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    ion_handle_data freedata;
    freedata.handle = handle_id;
    int ret;
    ret = ioctl(Globctx->IonFd, ION_IOC_FREE, &freedata);
    if(ret < 0)
    {
        ALOGE("%s: ION_IOC_FREE failed(ret=%d)", __func__, ret);
    }
}

static inline hwc_ion_hold_t *ion_find_least(void)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int i, cnt = 0;
    hwc_ion_hold_t  *ion_cache = NULL;
    unsigned int little_sync = Globctx->HWCFramecount;

    for(i = 0; i < ION_HOLD_CNT; i++)
    {
        ion_cache = &Globctx->ion_hold[i];
        if(little_sync > ion_cache->sync_count)
        {
            little_sync = ion_cache->sync_count;
            cnt = i;
            if(little_sync == 0)
            {
                break;
            }
        }
    }
    return &Globctx->ion_hold[cnt];
}

void ion_free_cache(hwc_ion_hold_t *ion_cache)
{
    int i;
    int *handle_id = NULL;
    handle_id = ion_cache->handle_array;
    for(i = 0; (i < ion_cache->num_array) && (handle_id != NULL); i++, handle_id++)
    {
        if(*handle_id >= 0)
        {
            close(*handle_id);
        }
        *handle_id = -1;
    }
    ion_cache->num_used = 0;
}

bool hwc_manage_ref_cache(bool cache, hwc_dispc_data_t *dispc_data)
{
    hwc_ion_hold_t  *ion_cache = NULL;
    int i, little_sync, cnt = 0, size = 0; 
    hwc_commit_layer_t *commit_layer = NULL;
    int *_array = NULL;

    if(!cache)
    {
        for(i = 0; i < NUMBEROFDISPLAY; i++)
        {
            for(cnt = 0; cnt < dispc_data->layer_num_inused[i]; cnt++)
            {
                commit_layer = &dispc_data->hwc_layer_info[i][cnt];
                if(commit_layer->share_fd >= 0)
                {
                    close(commit_layer->share_fd);
                    commit_layer->share_fd = -1;
                }
            }
        }
        goto manage_ok;
    }

    ion_cache = ion_find_least();
    ion_free_cache(ion_cache);

    cnt = NUMBEROFDISPLAY;
    size = 0;
    while(cnt--)
    {
        size += dispc_data->layer_num_inused[cnt];
    }
    if((ion_cache->handle_array == NULL) || (ion_cache->num_array < size))
    {
        if(ion_cache->handle_array != NULL)
        {
            free(ion_cache->handle_array);
            ion_cache->handle_array = NULL;
        }
        ion_cache->handle_array = (int* )calloc(size, sizeof(int));
        if(ion_cache->handle_array == NULL)
        {
            ALOGD("calloc ion_user_handle_t err");
            return 0;
        }
        memset(ion_cache->handle_array, -1, size * sizeof(int));
        ion_cache->num_array = size;
    }
    _array = ion_cache->handle_array;
    for(i = 0; i < NUMBEROFDISPLAY; i++)
    {
        commit_layer = dispc_data->hwc_layer_info[i];
        for(cnt = 0; cnt < dispc_data->layer_num_inused[i]; cnt++, _array++, commit_layer++)
        {
            *_array = commit_layer->share_fd;
        }
    }
    ion_cache->num_used = size;
    ion_cache->sync_count = dispc_data->sync_count;
    
manage_ok:
    return  1;
}

