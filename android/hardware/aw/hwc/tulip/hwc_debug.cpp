/*-------------------------------------------------------------------------
    
-------------------------------------------------------------------------*/
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

#include <sys/resource.h>
#include <sys/time.h>

#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <time.h>

char dump_src[40] = "/data/dump_layer";

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
    AssignDUETO(D_MEM_CTRL)
#undef AssignDUETO
		default:
			return "Unknown reason";
	}
    
}

void show_displays(HwcDisContext_t *Localctx)
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
	if(Globctx->hwcdebug && Globctx->show_layer)
	{
        if(PsDisplayInfo == NULL)
        {
            return;
        }
        ALOGD("\n\n+Disp info:\n"
              "+ Fram Num|DP|Vsy|STP|CA(U)|Ad|IST|FPS (20,40,60)|MAX_LMTD|Cur_LMTD|TR_LMTD |All_Used|DSP_Used|CHNNEL_0|CHNNEL_1|CHNNEL_2|CHNNEL_3|timestamp\n"
              "+---------+--+---+---+-----+--+---+--------------+--------+--------+--------+--------+--------+--------+--------+--------+--------+----------+");
        ALOGD("+%9d| %d|%3s|%3s|%-2d(%d)|%2d|%3s|%02.1f,%02.1f,%02.1f|%8d|%8d|%8d|%8d|%8d|%8d|%8d|%8d|%8d|%lld\n"
            ,Globctx->HWCFramecount-1
            ,PsDisplayInfo->VirtualToHWDisplay
            ,PsDisplayInfo->VsyncEnable?"Yes":"No"
            ,Globctx->ForceGPUComp[0] ? "Yes":"No"
            ,Globctx->NumManagemax
            ,Globctx->NumManageUsed
            ,Globctx->AbandonCount
            ,Globctx->in_stabilization ? "Yes":"No"
            ,Globctx->fps[0]
            ,Globctx->fps[1]
            ,Globctx->fps[2]
            ,Globctx->max_mem_limit
            ,Globctx->memlimit
            ,Globctx->tr_mem_limit
            ,Globctx->currentmem
            ,Localctx->cur_de_thruput
            ,Localctx->ChannelInfo[0].memthruput
            ,Localctx->ChannelInfo[1].memthruput
            ,Localctx->ChannelInfo[2].memthruput
            ,Localctx->ChannelInfo[3].memthruput
            ,PsDisplayInfo->mytimestamp);
        
        ALOGD("+---------+--+---+---+-----+--+---+--------------+--------+--------+--------+--------+--------+--------+--------+--------+--------+----------+");
        ALOGD("+\n"
              "+Type|CH|V|SC H|SC W|PL|S|       Handle    |      Phyaddr    |  Usage  |  Flags  |Tr|Bld| Format  |        Source Crop      |         Frame Crop      | Reason\n"
              "+----+--+-+----+----+--+-+-----------------+-----------------+---------+---------+--+---+---------+-------------------------+-------------------------+-------------\n");
        if(AllLayers == NULL)
        {
            return ;
        }
        for(i = 0; i < Localctx->numberofLayer; i++)
		{
            sunxiLayers = AllLayers+i;
            hwc_layer_1_t *l = sunxiLayers->psLayer;
            struct private_handle_t *handle = (private_handle_t*)l->handle;
            ALOGD("+%4s|%2d|%s|%1.2f|%1.2f|%02x|%d| %016x| %016x| %08x| %08x|%02x|%3x| %08x|[%5d,%5d,%5d,%5d]|[%5d,%5d,%5d,%5d]|%s\n",
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
                    handle == 0 ? 0 :
                        ((handle->flags & private_handle_t::PRIV_FLAGS_USES_CONFIG) ? ion_get_addr_fromfd(handle->share_fd):0),
                    handle == 0 ? 0 : handle->usage,
                    l->flags,
                    l->transform,
                    l->blending,
                    handle==0?0:handle->format,
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
        ALOGD("+----+--+-+----+----+--+-+-----------------+-----------------+---------+---------+--+---+---------+-------------------------+-------------------------+-------------\n");
    }
}

static void hwc_show_fps(SUNXI_hwcdev_context_t *psCtx)
{

	double fCurrentTime = 0.0;
	timeval tv = { 0, 0 };
	gettimeofday(&tv, NULL);
	fCurrentTime = tv.tv_sec + tv.tv_usec / 1.0e6;

	if(fCurrentTime - psCtx->fBeginTime >= 1)
	{
        if(psCtx->hwcdebug && psCtx->fps_layer)
	    {
	        ALOGD(">>>fps:: %d\n",
                    (int)((psCtx->HWCFramecount - psCtx->uiBeginFrame) * 1.0f 
                        / (fCurrentTime - psCtx->fBeginTime)));
	    }
        psCtx->uiBeginFrame = psCtx->HWCFramecount;
	    psCtx->fBeginTime = fCurrentTime;
	}
}

bool hwc_cmp(const char *s1, const char *s2)
{
    while (*s1 == *s2 && *s1++ != 0 && *s2++ != 0)
    {
    }
    if (*s1 == 0)
	    return 0;
    return 1;
}

void hwc_set_debug(SUNXI_hwcdev_context_t *psCtx)
{
    char property[PROPERTY_VALUE_MAX];
    char *ps_fix = property;
    if (property_get("debug.hwc.showfps", property, NULL) >= 0)
	{
	    if(!hwc_cmp("debug", ps_fix))
	    {
            if(psCtx->hwcdebug == 0)
            {
                ALOGD("####hwc open debug mode####");
            }
            psCtx->hwcdebug = 1;
            ps_fix += 6;
            if(!hwc_cmp("fps", ps_fix) && psCtx->fps_layer == 0)
            {
                psCtx->fps_layer = 1;
                ALOGD("hwc open show fps mode");
            }
            if(!hwc_cmp("dump", ps_fix) && psCtx->dump_layer == 0)
            {
                psCtx->dump_layer = 1;
                ALOGD("hwc open dump layer mode");
            }
            if(!hwc_cmp("show", ps_fix) && psCtx->show_layer == 0)
            {
                psCtx->show_layer = 1;
                ALOGD("hwc open show layer mode");
            }
            if(!hwc_cmp("close", ps_fix) && psCtx->close_layer == 0)
            {
                psCtx->close_layer = 1;
                ALOGD("hwc open close layer mode");
            }
            if(!hwc_cmp("pause", ps_fix) && psCtx->pause == 0)
            {
                psCtx->pause = 1;
                ALOGD("hwc open pause layer mode");
            }
	    }
        if((!strcmp("clear", property) || !strcmp("0", ps_fix) )
            && psCtx->hwcdebug == 1)
        {
            psCtx->hwcdebug = 0;
            psCtx->close_layer = 0;
            psCtx->dump_layer = 0;
            psCtx->fps_layer = 0;
            psCtx->disp_st = -1;
            psCtx->layer_st = -1;
            psCtx->show_layer = 0;
            psCtx->channel_st = -1;
            psCtx->pause = 0;
            ALOGD("####hwc close debug mode####");
        }
        if(!strcmp("1", ps_fix) && psCtx->fps_layer == 0)
        {
            psCtx->hwcdebug = 1;
            psCtx->fps_layer = 1;
            ALOGD("hwc open show fps mode");
        }
        if(!strcmp("2", ps_fix) && psCtx->show_layer == 0)
        {
            psCtx->hwcdebug = 1;
            psCtx->show_layer = 1;
            ALOGD("hwc open show layer mode");
        }
    }else{
        psCtx->hwcdebug = 0;
	}
    hwc_show_fps(psCtx);
}

void hwc_debug_close_layer(SUNXI_hwcdev_context_t *psCtx, hwc_commit_data_t *commit_data)
{
    if(psCtx->hwcdebug && psCtx->close_layer)
    {
        disp_layer_config *fix_layer = NULL; 
        hwc_commit_layer_t *hwc_layer_info = NULL;
        int lyr = -1;
        char property[PROPERTY_VALUE_MAX];
        char *ps_fix = property;
        bool  change = 0;
        if (property_get("debug.hwc.showfps", property, NULL) >= 0)
	    {
            if(!hwc_cmp("set", ps_fix))
	        {
                ps_fix += 4;
	            if(!hwc_cmp("d", ps_fix))
	            {
                    ps_fix++;
                    if(psCtx->disp_st != ((*ps_fix) - 48))
                    {
                        psCtx->disp_st = (*ps_fix) - 48;
                        change = 1;
                    }
	            }
                ps_fix++;
                if(!hwc_cmp("c", ps_fix))
                {
                    ps_fix++;
                    if(psCtx->channel_st != ((*ps_fix) - 48))
                    {
                        psCtx->channel_st = (*ps_fix) - 48;
                        change = 1;
                    }
                }
                ps_fix++;
                if(!hwc_cmp("l", ps_fix))
                {
                    ps_fix++;
                    if(psCtx->layer_st != ((*ps_fix) - 48))
                    {
                        psCtx->layer_st = (*ps_fix) - 48;
                        change = 1;
                    }
                }
                if(change)
                {
                    ALOGD("###will to close the disp[%d]--channel[%d]--layer[%d]###",
                            psCtx->disp_st, psCtx->channel_st, psCtx->layer_st);
                }
            }
	    }
        if(psCtx->disp_st >= 0 && psCtx->disp_st < NUMBEROFDISPLAY)
        {
            if(psCtx->layer_st > -1
                && (psCtx->layer_st
                     < psCtx->SunxiDisplay[psCtx->disp_st].LayerNumofCH))
            {
                if(psCtx->channel_st > -1
                    && (psCtx->channel_st
                        < psCtx->SunxiDisplay[psCtx->disp_st].HwChannelNum))
                {
                    lyr = psCtx->channel_st
                         * psCtx->SunxiDisplay[psCtx->disp_st].LayerNumofCH 
                         + psCtx->layer_st;
                    fix_layer = &commit_data->hwc_layer_info[psCtx->disp_st][lyr];
                }
            }
        }
        if(fix_layer != NULL)
        {
            fix_layer->enable = 0;
            ALOGD("###close the disp[%d]--channel[%d]--layer[%d]###",
                    psCtx->disp_st, psCtx->channel_st, psCtx->layer_st);
        }
    }

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

void hwc_debug_dump_layer(SUNXI_hwcdev_context_t *psCtx,
        hwc_commit_layer_t *hwc_layer_info, int disp, int sync_count)
{
    if(psCtx->hwcdebug && psCtx->dump_layer)
    {
        char property[PROPERTY_VALUE_MAX];
        char *ps_fix = property;
        if (property_get("debug.hwc.showfps", property, NULL) >= 0)
	    {
            bool change = 0;
            if(!hwc_cmp("set", ps_fix))
	        {
                ps_fix += 4;
            	if(!hwc_cmp("d", ps_fix))
                {
                    ps_fix++;
                    if(psCtx->disp_st != ((*ps_fix) - 48))
                    {
                        psCtx->disp_st = (*ps_fix) - 48;
                        change = 1;
                    }
                }
                ps_fix++;
                if(!hwc_cmp("c", ps_fix))
                {
                    ps_fix++;
                    if(psCtx->channel_st != ((*ps_fix) - 48))
                    {
                        psCtx->channel_st = (*ps_fix) - 48;
                        change = 1;
                    }
                }
                ps_fix++;
                if(!hwc_cmp("l", ps_fix))
                {
                    ps_fix++;
                    if(psCtx->layer_st != ((*ps_fix) - 48))
                    {
                        psCtx->layer_st = (*ps_fix) - 48;
                        change = 1;
                    }
                }
                if(change)
                {
                    ALOGD("###dump the disp[%d]--channel[%d]--layer[%d]###",
                            psCtx->disp_st, psCtx->channel_st, psCtx->layer_st);
                }
            }
        }

        if(psCtx->disp_st == disp
            && psCtx->channel_st == (int)hwc_layer_info->hwc_layer_info.channel
            && psCtx->layer_st == (int)hwc_layer_info->hwc_layer_info.layer_id)
        {
            void *addr_0 = NULL;
            int size = 0;
            int fd = 0;
            int ret = -1;
            unsigned int width;
            unsigned int height;
            disp_pixel_format format = hwc_layer_info->hwc_layer_info.info.fb.format;
            width = hwc_layer_info->hwc_layer_info.info.fb.size[0].width;
            height = hwc_layer_info->hwc_layer_info.info.fb.size[0].height;
            sprintf(dump_src, "/data/dump_%d_%02x", sync_count, hwc_layer_info->tr);
            fd = open(dump_src, O_RDWR|O_CREAT,0644);
            if(fd < 0)
            {
                ALOGD("open %s %d", dump_src, fd);
                return ;
            }
            size = width * height * hwc_disp_pixel_bytes(format) / 8;
            ALOGD("### Width:%d Height:%d Size:%d at frame:%d###",
                    width, height, size, sync_count);
            addr_0 = mmap(NULL, size, PROT_READ|PROT_WRITE, MAP_SHARED,
                            hwc_layer_info->share_fd, 0);
            ret = ::write(fd, addr_0, size);
            if(ret != size)
            {
                ALOGD("write %s err %d", ret, dump_src);
            }
            munmap(addr_0,size);
            close(fd);
            ALOGD("###dump the disp[%d]--channel[%d]--layer[%d]--frame[%d]###",
                psCtx->disp_st, psCtx->channel_st, psCtx->layer_st, sync_count);
        }
    }

}

bool hwc_debug_pause(SUNXI_hwcdev_context_t *psCtx, int sync_count, bool paused)
{
    if(psCtx->hwcdebug && psCtx->pause && !paused)
    {
        char property[PROPERTY_VALUE_MAX];
        char *ps_fix = property;
        if (property_get("debug.hwc.showfps", property, NULL) >= 0)
	    {
            bool change = 0;
            if(!hwc_cmp("set", ps_fix))
	        {
                ALOGD("###Pause at frame:%d ###", sync_count-1);
                return 1;
            }
        }
    }
    return paused && psCtx->pause;
}

