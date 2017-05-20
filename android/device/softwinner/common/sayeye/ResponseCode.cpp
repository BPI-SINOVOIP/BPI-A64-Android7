/*
 * Copyright (C) 2008 The Android Open Source Project
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

#include <stdio.h>
#include <errno.h>
#include <string.h>

#define LOG_TAG "Sayeye"

#include <cutils/log.h>

#include "ResponseCode.h"

int ResponseCode::convertFromErrno() {
    if (errno == ENODEV) {
        return 0;
    } else if (errno == ENODATA) {
        return 0;
    } else if (errno == EIO) {
        return 0;
    } else if (errno == EBUSY) {
        return 0;
    } else if (errno == ENOENT) {
        return 0;
    }

    SLOGW("Returning OperationFailed - no handler for errno %d", errno);
    return(ResponseCode::OperationFailed);
}
