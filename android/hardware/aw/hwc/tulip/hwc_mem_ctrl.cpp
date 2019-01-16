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

AssignDUETO_T calculate_memthruput(HwcDisContext_t *Localctx, layer_info_t *hwLayer,
        float WscalFac, float HscaleFac, int channel, bool isFB, bool isvideo)
{
    int memoflayer = 0, whilecnt = 0, fb_mem = 0, need_mem = 0;
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    hwc_layer_1_t *psLayer = hwLayer->psLayer;
    struct private_handle_t *handle = (struct private_handle_t*)psLayer->handle;
    ChannelInfo_t *channelinfo;
    hwc_rect_t rectx = {0,0,0,0};
    hwc_rect_t rectx2 = {0,0,0,0};
    hwc_rect_t *psdiplay = &psLayer->displayFrame;
    int prememvideo;
    prememvideo = Localctx->prememvideo;
    bool re_cal_mem = 1;

    channelinfo = &Localctx->ChannelInfo[channel];
    if(check_swap_w_h(psLayer->transform))
    {
        float tmp = WscalFac;
        WscalFac = HscaleFac;
        HscaleFac = tmp;
    }
    memoflayer = (psdiplay->right - psdiplay->left) /WscalFac
                * (psdiplay->bottom - psdiplay->top) / HscaleFac
                * hwLayer->form_info.bpp / 8;
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
    if(Localctx->unasignedVideo <= 0 || isFB)
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
                        * (rectx.bottom - rectx.top) / HscaleFac * hwLayer->form_info.bpp / 8;
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
                            * hwLayer->form_info.bpp / 8;
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
                                * hwLayer->form_info.bpp / 8;
            }
        }
    }
    if(memoflayer <= 0)
    {
        memoflayer = 0;
    }
    if(psLayer->transform != 0)
    {
        memoflayer += 2 * cal_layer_mem(hwLayer);
    }
    if(!isFB && Localctx->UsedFB)
    {
        fb_mem = Localctx->cur_fb_thruput;
    }

cal_mem:
    need_mem = Globctx->currentmem + memoflayer + prememvideo + fb_mem
                + Globctx->fb_pre_mem - Globctx->memlimit;
    if(need_mem <= 0)
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
    Localctx->cur_de_thruput += memoflayer;
    channelinfo->memthruput += memoflayer;
    Globctx->currentmem += memoflayer;
    return I_OVERLAY;

overflow:
    return D_NO_MEM;
}

void hwc_down_limit(SUNXI_hwcdev_context_t *Globctx, int local_mem[NUMBEROFDISPLAY], bool force_gpu)
{
    HWC_UNREFERENCED_PARAMETER(local_mem);
    int i = 0, tmp_mem_thruput0 = 0;
    if(Globctx->has_secure != 0)
    {
        Globctx->memlimit = Globctx->max_mem_limit;
        return;
    }
    if(Globctx->ForceGPUComp[0] == 0 && Globctx->CanForceGPUCom && force_gpu)
    {
        if(Globctx->psHwcProcs != NULL
            && Globctx->psHwcProcs->invalidate != NULL)
        {
            Globctx->ForceGPUComp[0] = 1;
            Globctx->ForceGPUComp[1] = 1;
            Globctx->psHwcProcs->invalidate(Globctx->psHwcProcs);
        }
    }
    for (i = 0; i < Globctx->NumberofDisp; i++)
    {
        if(Globctx->SunxiDisplay[i].VirtualToHWDisplay != -EINVAL)
        {
            tmp_mem_thruput0 += Globctx->SunxiDisplay[i].InitDisplayHeight
                                * Globctx->SunxiDisplay[i].InitDisplayWidth
                                * 4;
        }
    }
    Globctx->memlimit = tmp_mem_thruput0;
    Globctx->tr_mem_limit = 0;
}

void mem_adjust_moment(SUNXI_hwcdev_context_t *Globctx,
        int diff_all, int lower_limit, int high_limit, bool updata)
{
    static int mem_cnt_cap;
    int max = Globctx->max_mem_limit;
    int memlimit_val = 0, mem_lock_tmp = 0;
    int try_cnt = 2;
    int low = 0, i = 0;

    while(i < Globctx->NumberofDisp)
    {
        if(Globctx->SunxiDisplay[i].VirtualToHWDisplay != -EINVAL)
        {
            low += Globctx->SunxiDisplay[i].InitDisplayHeight
                    * Globctx->SunxiDisplay[i].InitDisplayWidth
                    * 4;
        }
        i++;
    }
    if(high_limit < low)
    {
        goto updata;
    }
try_agaign:
    mem_lock_tmp = Globctx->memlimit;
    if(low > lower_limit)
    {
        lower_limit = low;
    }
    if((mem_lock_tmp + diff_all) < lower_limit)
    {
        memlimit_val = lower_limit;
    }
    if(high_limit < max)
    {
        max = high_limit;
    }
    if((mem_lock_tmp + diff_all) > max)
    {
        memlimit_val = max;
    }
    /* down the probability, but not avoid mutex, not importance */
    if(mem_lock_tmp == Globctx->memlimit)
    {
        if(memlimit_val == 0)
        {
            Globctx->memlimit += diff_all;
        }else{
            Globctx->memlimit = memlimit_val;
        }
    }else{
        if(--try_cnt)
        {
            goto try_agaign;
        }
    }
updata:   
    if(!updata)
    {
        return;
    }
    if(mem_cnt_cap != Globctx->updata_mem_cnt)
    {
        Globctx->cur_all_diff = 0;
        mem_cnt_cap = Globctx->updata_mem_cnt;
    }else{
        Globctx->cur_all_diff += diff_all;
    }
}

void hwc_updata_limit(SUNXI_hwcdev_context_t *Globctx,
        hwc_dispc_data_t *DisplayData, int local_mem[NUMBEROFDISPLAY], unsigned int start_count)
{
    timeval tv = {0, 0};
    double starttime = 0.0;
    unsigned int diff_sync_count;
    int i = 0;
    static double fps_time[3];//0 for 20st time, 1 for 40st time, 2 for 60st time
    diff_sync_count = DisplayData->sync_count - start_count;
    i = 0;
    static int mem_all_diff = 0, setup_cnt = 0, de_mem_all = 0;
    int lower_max_mem = 0, mem_setup = 0;
    bool dvfs_start = 0;
    while(i < NUMBEROFDISPLAY)
    {
        local_mem[i] = DisplayData->local_mem[i];
        de_mem_all += DisplayData->local_mem[i];
        i++;
    }
    /* ddr dvf interval 256ms */
    if(diff_sync_count < SETUP_TIMES)
    {
        /* open the memory source by setup */
        if(Globctx->tr_fd >= 0)
        {
            if(Globctx->tr_max_mem_limit > Globctx->tr_mem_limit)
            {
                Globctx->tr_mem_limit += SETUP_INTERVAL_TR;
            }else{
                Globctx->tr_mem_limit = Globctx->tr_max_mem_limit; 
            }
        }else{
            Globctx->tr_mem_limit = 0;
        }

        if(Globctx->memlimit < Globctx->max_mem_limit)
        {
            Globctx->memlimit += SETUP_INTERVAL_DE;
        }else{
            Globctx->memlimit = Globctx->max_mem_limit;
        }
        Globctx->in_stabilization = 0;
        Globctx->cur_all_diff = 0;
        de_mem_all = 0;
    }
    if(diff_sync_count > 4)
    {
        Globctx->ForceGPUComp[0] = Globctx->stop_hwc;
        Globctx->ForceGPUComp[1] = Globctx->stop_hwc;
    }
    if(diff_sync_count >= SETUP_TIMES)
    {
        Globctx->in_stabilization = 1;
        Globctx->tr_mem_limit = Globctx->tr_max_mem_limit;
        Globctx->memlimit = Globctx->max_mem_limit;
        if(diff_sync_count%60 == 0)
        {
            gettimeofday(&tv, NULL);
            starttime = tv.tv_sec + tv.tv_usec / 1.0e6;
            if(starttime - fps_time[1] > 0 && diff_sync_count  != SETUP_TIMES)
            {
                Globctx->fps[2] = 20.0/(starttime - fps_time[1]);
                Globctx->last_fps20 = Globctx->fps[2];
            }
            fps_time[2] = starttime;
        }
        if(diff_sync_count%60 == 20)
        {
            gettimeofday(&tv, NULL);
            starttime = tv.tv_sec + tv.tv_usec / 1.0e6;
            if(starttime - fps_time[2] > 0)
            {
                Globctx->fps[0] = 20.0/(starttime - fps_time[2]);
                Globctx->last_fps20 = Globctx->fps[0];
            }
            fps_time[0] = starttime;
        }
        if(diff_sync_count%60 == 40)
        {
            gettimeofday(&tv, NULL);
            starttime = tv.tv_sec + tv.tv_usec / 1.0e6;
            if(starttime - fps_time[0] > 0)
            {
                Globctx->fps[1] = 20.0/(starttime - fps_time[0]);
                Globctx->last_fps20 = Globctx->fps[1];
            }
            fps_time[1] = starttime;
        }
    }
}

bool mem_ctrl_power_policy(SUNXI_hwcdev_context_t *Globctx, HwcDisContext_t *localctx)
{
    long long gpu_mem_fps_power = 0;
    long long de_mem_60_power = 0;
    float power_factor = DE_GPU_PW_FACTOR; // for the de and gpu power_factor xchange
    if(Globctx->in_stabilization)
    {
        /* if GPU high usage,hwo to get the gpu usage,wait for AL5,
          and low memory thurput then use de composit */
        gpu_mem_fps_power = (double)Globctx->last_fps20 * localctx->cur_all_thruput * power_factor
                        + localctx->cur_fb_thruput * 60;
        de_mem_60_power = (double)Globctx->last_fps20 * localctx->cur_gpu_thruput * power_factor
                        + localctx->cur_de_thruput * 60;
        if(de_mem_60_power - gpu_mem_fps_power > 0)
        {
            goto gpu_composite;
        }
    }
de_composite:
    return 0;

gpu_composite:
    return 1;
}
