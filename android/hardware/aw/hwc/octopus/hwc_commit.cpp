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

inline void hwc_init_config(disp_layer_config *layer_config,int count)
{
    int i = 0, j = 0;
    for(i = 0; i<(count / NUMLAYEROFCHANNEL); i++)
    {
        for(j = 0; j < NUMLAYEROFCHANNEL; j++)
        {
            layer_config->channel = i;
            layer_config->layer_id = j;
            layer_config->enable = 0;
            layer_config++;
        }
    }
}

void hwc_head_commit(hwc_dispc_data_t *head, hwc_commit_data_t *commit_data)
{
    int i = 0, j = 0, lyr = 0;
    disp_layer_config *commit_layer = NULL;
    hwc_commit_layer_t *hwc_layer = NULL;

    hwc_init_config(commit_data->hwc_layer_info[0], 16);
    hwc_init_config(commit_data->hwc_layer_info[1], 8);
    for(i = 0; i < CNOUTDISPSYNC; i++)
    {
        if(i < NUMBEROFDISPLAY)
        {
            commit_data->force_flip[i] = head->force_flip[i];
            commit_layer = commit_data->hwc_layer_info[i];
            hwc_layer = head->hwc_layer_info[i];
            for(j = 0; j < head->layer_num_inused[i]; j++)
            {
                lyr = (hwc_layer->hwc_layer_info.channel * NUMLAYEROFCHANNEL)
                        + hwc_layer->hwc_layer_info.layer_id;
                commit_layer[lyr] = hwc_layer->hwc_layer_info;
                hwc_layer++;
            }
        }
        if(head->releasefencefd[i] >= 0)
        {
            commit_data->releasefencefd[i] = head->releasefencefd[i];
        }else{
            commit_data->releasefencefd[i] = -1;
        }
    }
    commit_data->data = NULL;
}

hwc_dispc_data_t* hwc_layer_cache_get(SUNXI_hwcdev_context_t *Globctx, int countdisp)
{
    int i = 0, cout_cut = 2;
    bool fix = 0;
    list_head_t *head_pos = NULL;
    list_head_t *head_bak = NULL;
    hwc_dispc_data_t *manage_cache = NULL;
    hwc_dispc_data_t *fixed_cache = NULL;
    long offset = 0;
    offset = container_of(hwc_dispc_data_t, manage_head);
    list_head_t *ManageHead = &Globctx->ManageHead;
    list_head_t *AbandonHead = &Globctx->AbandonHead;
    HwcDisContext_t *DisContext = Globctx->DisContext;

    for(head_pos = ManageHead->next, head_bak = head_pos->next;
            head_pos != ManageHead;
                head_pos = head_bak, head_bak = head_pos->next)
    {
        manage_cache = type_container_of(head_pos, hwc_dispc_data_t, offset);
        if(!manage_cache->in_used)
        {
            for(i = 0; i < countdisp; i++)
            {
                manage_cache->layer_num_inused[i] = 0;
                if( (manage_cache->layer_num_max[i] >= DisContext[i].countofhwlayer)
                    && (manage_cache->layer_num_max[i] - DisContext[i].countofhwlayer <= 2)
                    && !manage_cache->abandon)
        	    {
                    if(i != (countdisp-1))
                    {
                        continue;
                    }
                    if(fixed_cache == NULL)
                    {
                        fixed_cache = manage_cache;
                        continue;
                    }
                    if(((cout_cut--) == 0)
                        || (Globctx->NumManagemax - Globctx->NumManageUsed < MAX_FREE_CACHE ))
                    {
                        goto cache_ok;
                    }
        	    }
        	    manage_cache->abandon = 1;

                pthread_mutex_lock(&Globctx->ManageLock);
                hwc_list_del(&manage_cache->manage_head);
                Globctx->NumManagemax--;
                pthread_mutex_unlock(&Globctx->ManageLock);

                pthread_mutex_lock(&Globctx->AbandonLock);
  	            hwc_list_put(AbandonHead, &manage_cache->manage_head);
                Globctx->AbandonCount++;
                pthread_mutex_unlock(&Globctx->AbandonLock);
                break;
            }
        }
    }
    //calloc a new
    if(fixed_cache != NULL)
    {
        goto cache_ok;
    }
    fixed_cache = (hwc_dispc_data_t *)calloc(1, sizeof(hwc_dispc_data_t));
    if(fixed_cache == NULL)
    {
        goto cache_err;
    }
    memset(fixed_cache, 0, sizeof(hwc_dispc_data_t));
    for(i = 0; i < countdisp; i++)
	{
    	if(DisContext[i].countofhwlayer > 0)
		{
    		fixed_cache->hwc_layer_info[i] =
                (hwc_commit_layer_t* )calloc(DisContext[i].countofhwlayer, sizeof(hwc_commit_layer_t));
    		if(fixed_cache->hwc_layer_info[i] == NULL)
    		{
    			goto cache_err;
    		}
            fixed_cache->layer_num_inused[i] = 0;
    		fixed_cache->layer_num_max[i] = DisContext[i].countofhwlayer;
		}
	}

	hwc_list_init(&fixed_cache->manage_head);
	hwc_list_init(&fixed_cache->commit_head);
    pthread_mutex_lock(&Globctx->ManageLock);
  	hwc_list_put(ManageHead, &fixed_cache->manage_head);
    Globctx->NumManagemax++;
    pthread_mutex_unlock(&Globctx->ManageLock);

cache_ok:
    fixed_cache->in_used = 1;
    pthread_mutex_lock(&Globctx->ManageLock);
    Globctx->NumManageUsed++;
    pthread_mutex_unlock(&Globctx->ManageLock);

	for(i = 0; i < NUMBEROFDISPLAY; i++)
	{
    	fixed_cache->force_flip[i] = 0;
        fixed_cache->cursor_in_disp[i] = -1;
		if(fixed_cache->layer_num_max[i] > 0)
        {
            memset(fixed_cache->hwc_layer_info[i], 0,
                    sizeof(hwc_commit_layer_t) * fixed_cache->layer_num_max[i]);
        }
    }
    fixed_cache->tr_layer = -1;
    fixed_cache->tr_in_disp = -1;
    fixed_cache->first_disp = Globctx->SunxiDisplay[0].VirtualToHWDisplay;
    fixed_cache->sync_count = Globctx->HWCFramecount;
    fixed_cache->vir_wb_data.vir_handle = NULL;
    fixed_cache->vir_wb_data.outbufAcquireFenceFd = -1;
    fixed_cache->second_wb = 0;

    for(i = 0; i < CNOUTDISPSYNC; i++)
	{
    	fixed_cache->releasefencefd[i] = -1;
	}

    return fixed_cache;

cache_err:
    if(fixed_cache != NULL)
    {
        for(i = 0; i < NUMBEROFDISPLAY; i++)
		{
       	 	if(fixed_cache->hwc_layer_info[i] != NULL)
			{
				free(fixed_cache->hwc_layer_info[i]);
			}
        }
		free(fixed_cache);
    }
    ALOGD("hwc cache err.");
    return NULL;
}

void hwc_manage_layer_cache(SUNXI_hwcdev_context_t *Globctx, hwc_dispc_data_t *commit_head)
{
    list_head_t *reference_commit = NULL;
    list_head_t *head_pos = NULL;
    list_head_t *head_bak = NULL;
    hwc_dispc_data_t *manage_cache = NULL;
    hwc_dispc_data_t *reference_cache = NULL;
    long offset = 0;
    int i = 0, j = 0;
    offset = container_of(hwc_dispc_data_t, manage_head);

    if(commit_head != NULL)
    {
        i = CNOUTDISPSYNC;
        while(i--)
        {
            if(commit_head->releasefencefd[i] >= 0)
            {
                close(commit_head->releasefencefd[i]);
                commit_head->releasefencefd[i] = -1;
            }
        }
        if(Globctx->NumManagemax - Globctx->NumManageUsed > MAX_FREE_CACHE)
        {
            commit_head->abandon = 1;
        }
        commit_head->in_used = 0;
        pthread_mutex_lock(&Globctx->ManageLock);
        Globctx->NumManageUsed--;
        pthread_mutex_unlock(&Globctx->ManageLock);
    }

    if(!hwc_list_empty(&Globctx->CommitHead))
    {
        pthread_mutex_lock(&Globctx->HeadLock);
        reference_commit = Globctx->CommitHead.prev;
        pthread_mutex_unlock(&Globctx->HeadLock);
        reference_cache = (hwc_dispc_data_t*)reference_commit;
    }

    while((reference_cache != NULL)
            && (Globctx->NumManagemax - Globctx->NumManageUsed < MIN_FREE_CACHE))
    {
        if(!hwc_list_empty(&Globctx->AbandonHead))
        {
            pthread_mutex_lock(&Globctx->AbandonLock);
            head_pos = hwc_list_get(&Globctx->AbandonHead);
            Globctx->AbandonCount--;
            pthread_mutex_unlock(&Globctx->AbandonLock);
            manage_cache = type_container_of(head_pos, hwc_dispc_data_t, offset);
            for(i = 0; i < NUMBEROFDISPLAY; i++)
		    {
                if((manage_cache->layer_num_max[i] - reference_cache->layer_num_inused[i] < 0)
                     || (manage_cache->layer_num_max[i] - reference_cache->layer_num_inused[i] > 2)
                     || (reference_cache->layer_num_inused[i] == 0))
			    {
                    if(manage_cache->hwc_layer_info[i] != NULL)
                    {
				        free(manage_cache->hwc_layer_info[i]);
                        manage_cache->hwc_layer_info[i] = NULL;
                    }
                    manage_cache->layer_num_max[i] = 0;
                    manage_cache->layer_num_inused[i] = 0;
                    if(reference_cache->layer_num_inused[i] > 0)
                    {
                        manage_cache->hwc_layer_info[i] =
                            (hwc_commit_layer_t* )calloc(reference_cache->layer_num_inused[i],
                                            sizeof(hwc_commit_layer_t));
                        if(manage_cache->hwc_layer_info[i] == NULL)
                        {
                            goto manage_err;
                        }
                        manage_cache->layer_num_max[i] = reference_cache->layer_num_inused[i];
                        memset(manage_cache->hwc_layer_info[i], 0,
                            sizeof(hwc_commit_layer_t) * manage_cache->layer_num_max[i]);
                    }
			    }
            }
            pthread_mutex_lock(&Globctx->ManageLock);
            hwc_list_put(&Globctx->ManageHead, &manage_cache->manage_head);
            Globctx->NumManagemax++;
            pthread_mutex_unlock(&Globctx->ManageLock);
        }else{
            break;
        }
    }

    while(Globctx->AbandonCount > MIN_FREE_CACHE)
    {
        pthread_mutex_lock(&Globctx->AbandonLock);
        head_pos = hwc_list_get(&Globctx->AbandonHead);
        Globctx->AbandonCount--;
        pthread_mutex_unlock(&Globctx->AbandonLock);
        if(head_pos != NULL)
        {
            manage_cache = type_container_of(head_pos, hwc_dispc_data_t, offset);
            for(i = 0; i < NUMBEROFDISPLAY; i++)
		    {
       	 	    if(manage_cache->hwc_layer_info[i] != NULL
                    && manage_cache->layer_num_max[i] > 0)
			    {
				    free(manage_cache->hwc_layer_info[i]);
			    }
            }
		    free(manage_cache);
        }
    }
    return;

manage_err:
    if(manage_cache != NULL)
    {
        for(i = 0; i < NUMBEROFDISPLAY; i++)
		{
       	 	if(manage_cache->hwc_layer_info[i] != NULL)
			{
				free(manage_cache->hwc_layer_info[i]);
			}
        }
		free(manage_cache);
    }
}

void *commit_thread(void *priv)
{
    HWC_UNREFERENCED_PARAMETER(priv);
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    list_head_t *CommitHead = NULL;
    list_head_t *CommitList = NULL;
    hwc_dispc_data_t *DisplayData = NULL;
    int i = 0, j = 0, ret = -1, lyr = 0, rotatecall = 0;
    int primary_disp = 0, video_fence_fd = -1, share_fd = -1;
    int unblank_count = 0;
    unsigned long arg[4] = {0};
    unsigned int current_sync_count = 0, cusor_sync = 0;
    hwc_ioctl_arg hwc_cmd;
    hwc_cmd.cmd = HWC_IOCTL_COMMIT;
    hwc_commit_data_t commit_data;
    disp_capture_info wb_data;
    hwc_commit_layer_t cursor_layer[NUMBEROFDISPLAY];
    bool need_sync = 0;

    memset(cursor_layer, 0, sizeof(hwc_commit_layer_t) * NUMBEROFDISPLAY);
    setpriority(PRIO_PROCESS, 0, HAL_PRIORITY_URGENT_DISPLAY);

    commit_data.hwc_layer_info[0] =
            (disp_layer_config *)calloc(16, sizeof(disp_layer_config));
    commit_data.hwc_layer_info[1] =
            (disp_layer_config *)calloc(8, sizeof(disp_layer_config));
    if(commit_data.hwc_layer_info[0] == NULL 
        || commit_data.hwc_layer_info[1] == NULL)
    {
        ALOGD("hwc calloc commit err.");
        return NULL;
    }

    hwc_list_init(&Globctx->ManageHead);
    hwc_list_init(&Globctx->CommitHead);
    hwc_list_init(&Globctx->AbandonHead);
    CommitHead = &Globctx->CommitHead;
    ALOGD("######hwc Commit  Thread(%d)#######", gettid());
	while(1)
	{
        i = 0;
        ret = -1;
        j = 0;
        if(!hwc_list_empty(CommitHead) || (Globctx->has_cursor && (Globctx->cursor_sync >= current_sync_count)))
        {
            if(Globctx->has_cursor && (cusor_sync != current_sync_count))
            {
                /* hardware cursor management if you open the hw cursor */
                hwc_cursor_manage(Globctx, cursor_layer, current_sync_count);
                cusor_sync = current_sync_count;
            }
            if(!hwc_list_empty(CommitHead))
            {
                pthread_mutex_lock(&Globctx->HeadLock);
                CommitList = hwc_list_get(CommitHead);
                pthread_mutex_unlock(&Globctx->HeadLock);
                /* convert head to commit data */
                DisplayData = (hwc_dispc_data_t *)CommitList;
                hwc_head_commit(DisplayData, &commit_data);
                /* deal the tr video */
                if(DisplayData->tr_layer != -1
                    && !commit_data.force_flip[DisplayData->tr_in_disp])
                {
                    /* must THK lot the same display for tr*/
                    video_fence_fd = -1;
                    need_sync = 0;
                    share_fd = -1;
                    if(Globctx->tr_handle == 0)
                    {
                        if(!hwc_rotate_request())
                        {
                            goto deal_fence;
                        }
                        rotatecall++;
                        ALOGD("hwc request rotate context times[%d]", rotatecall);
                    }
                    video_fence_fd =
                        DisplayData->hwc_layer_info[DisplayData->tr_in_disp][DisplayData->tr_layer].aquirefencefd;
                    need_sync =
                        DisplayData->hwc_layer_info[DisplayData->tr_in_disp][DisplayData->tr_layer].needsync;
                    share_fd =
                        DisplayData->hwc_layer_info[DisplayData->tr_in_disp][DisplayData->tr_layer].share_fd;
                    if(video_fence_fd >= 0)
                    {
                        ret = sync_wait(video_fence_fd, 3000);
                        if(ret < 0)
                        {
                            ALOGD("wait video fence timeout(%d)", ret);
                            commit_data.force_flip[DisplayData->tr_in_disp] = 1;
                        }
                        close(video_fence_fd);
                        DisplayData->hwc_layer_info[DisplayData->tr_in_disp][DisplayData->tr_layer].aquirefencefd = -1;
                    }
                    if(need_sync)
                    {
                        ion_sync_fd(Globctx->IonFd, share_fd);
                        DisplayData->hwc_layer_info[DisplayData->tr_in_disp][DisplayData->tr_layer].needsync = 0;
                    }
                    if(!hwc_rotate_layer_video(DisplayData, &commit_data, DisplayData->tr_in_disp, DisplayData->tr_layer))
                    {
                        Globctx->stop_rotate_hw = 1;
                        //commit_data.force_flip[DisplayData->tr_in_disp] = 1;// normal when video start 1 frame
                    }
                }else{
                    if(Globctx->tr_handle != 0)
                    {
                        hwc_rotate_release();
                        hwc_rotate_cache_free();
                    }
                }
deal_fence:
                j = 0;
                /* wait for fence */
                while( j < NUMBEROFDISPLAY)
                {
                    for(i = 0; i < DisplayData->layer_num_inused[j]; i++)
                    {
                        if(!commit_data.force_flip[j])
                        {
                            if(DisplayData->hwc_layer_info[j][i].aquirefencefd >= 0)
                            {
                                ret = sync_wait(DisplayData->hwc_layer_info[j][i].aquirefencefd, 3000);
                                if(ret < 0)
                                {
                                    ALOGD("wait fence timeout (%d)disp(%d)layer(%d)",
                                        ret, j, i);
                                    commit_data.force_flip[j] = 1;
                                }
                            }

                            if(DisplayData->hwc_layer_info[j][i].iscursor)
                            {
                                get_rotate_cursor_layer(Globctx, &DisplayData->hwc_layer_info[j][i]);
                                /* change the new layer for the hardware cursor */
                                lyr = (DisplayData->hwc_layer_info[j][i].hwc_layer_info.channel*4)
                                    + DisplayData->hwc_layer_info[j][i].hwc_layer_info.layer_id;
                                commit_data.hwc_layer_info[j][lyr].info.fb.addr[0] =
                                    DisplayData->hwc_layer_info[j][i].hwc_layer_info.info.fb.addr[0];
                                commit_data.hwc_layer_info[j][lyr].info.fb.size[0] =
                                    DisplayData->hwc_layer_info[j][i].hwc_layer_info.info.fb.size[0];
                            }

                            if(DisplayData->hwc_layer_info[j][i].needsync)
                            {
                                ion_sync_fd(Globctx->IonFd, DisplayData->hwc_layer_info[j][i].share_fd);
                            }
                        }

                        if( DisplayData->hwc_layer_info[j][i].aquirefencefd >= 0)
                        {
                            close(DisplayData->hwc_layer_info[j][i].aquirefencefd);
                            DisplayData->hwc_layer_info[j][i].aquirefencefd = -1;
                        }
                    }
                    j++;
                }
                /* deal the write back data */
                //hwc_wb_manage(DisplayData, &commit_data, &wb_data);

                /* set primary disp, THK a lot before commit disp? */
                if(primary_disp != DisplayData->first_disp)
                {
                    primary_disp  = DisplayData->first_disp;
                    hwc_cmd.cmd = HWC_IOCTL_SETPRIDIP;
                    hwc_cmd.arg = &primary_disp;
                    arg[0] = 0;
                    arg[1] = (unsigned long)(&hwc_cmd);
                    ret = ioctl(Globctx->DisplayFd, DISP_HWC_COMMIT, (unsigned long)arg);
                }
                /* commit disp */
                hwc_cmd.cmd = HWC_IOCTL_COMMIT;
                hwc_cmd.arg = &commit_data;
                arg[0] = 0;
                arg[1] = (unsigned long)(&hwc_cmd);
                ret = ioctl(Globctx->DisplayFd, DISP_HWC_COMMIT, (unsigned long)arg);
                
                if(Globctx->unblank_flag)
                {
                		if(unblank_count == 3)
                       {
                           	unsigned long               arg[4]={0};
                                DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[DisplayData->first_disp];
                                
                                if(PsDisplayInfo->VirtualToHWDisplay !=  -EINVAL)
                                {
                                         arg[0] = PsDisplayInfo->VirtualToHWDisplay;
                                         arg[1] = 0;
                                         if(ioctl(Globctx->DisplayFd, DISP_BLANK, (unsigned long)arg) != 0)
                                                           ALOGE("##########unblank error!");
                                }
                                
                                Globctx->unblank_flag = 0;
                                unblank_count = 0;
                       }
                       
                       unblank_count++;
                }
                /* check wb and display to HDMI or miracast */

                /* update cursor disp data */
                if(update_cursor_layer(Globctx, cursor_layer, DisplayData))
                {
                    current_sync_count = DisplayData->sync_count;
                }else{
                    current_sync_count = 0;
                    cusor_sync = 0;
                }
                /* ion ref */
                hwc_manage_ref_cache(!(commit_data.force_flip[0] && commit_data.force_flip[1]), DisplayData);
                /* Commit List */
                hwc_manage_layer_cache(Globctx, DisplayData);
            }
        }else{
            hwc_manage_layer_cache(Globctx, NULL);
            Globctx->CommitCondition.waitRelative(Globctx->CommitLock, 16000000);
        }
	}

	return NULL;
}
