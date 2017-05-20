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

wb_data_t *wb_handle_capture(buffer_handle_t *handle,disp_capture_info *data)
{
    HWC_UNREFERENCED_PARAMETER(data);
    HWC_UNREFERENCED_PARAMETER(handle);
    return 0;
}

hwc_cache_t *wb_ion_buffer_capture(disp_capture_info *data)
{
    hwc_cache_t* hwc_cache = NULL;
    HWC_UNREFERENCED_PARAMETER(data);
    return  hwc_cache;
}

void hwc_wb_manage(hwc_dispc_data_t *DisplayData, hwc_commit_data_t *commit_data, disp_capture_info *data)
{
    HWC_UNREFERENCED_PARAMETER(commit_data);
    HWC_UNREFERENCED_PARAMETER(DisplayData);
    HWC_UNREFERENCED_PARAMETER(data);
    hwc_cache_t* hwc_cache = NULL;
}
















