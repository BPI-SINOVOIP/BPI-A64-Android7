/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include <hardware/hardware.h>
#include <hardware/power.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <cutils/properties.h>
#include <utils/Log.h>
#include "power.h"

char  propdebug[100]={0};

static void power_fwrite(const char *path, char *s) {
    char buf[64];
    int len;
    int fd = open(path, O_WRONLY);
    if (fd < 0) {
       strerror_r(errno, buf, sizeof(buf));
       ALOGE("Error opening %s: %s\n", path, buf);
       return;
    }
    len = write(fd, s, strlen(s));
    if (len < 0) {
       strerror_r(errno, buf, sizeof(buf));
       ALOGE("Error writing to %s: %s\n", path, buf);
    }
    close(fd);
}

int main(int argc, char *argv[])
{
    property_get("sys.p_debug",propdebug,NULL);
    if (!strcmp(propdebug,"true")) {
        ALOGI("scense  debug mode, cpu scense would not change automatically!");
        printf("==debug  fail == ");
        return -1;
    } else if (!strcmp(propdebug,"false")) {
        property_get("sys.p_bootcomplete",propdebug,  NULL);
        if(!strcmp(propdebug,"false")) {
            ALOGI("==BOOTCOMPLETE MODE DISABLED==");
            return -1;
        }
        ALOGI("========BOOTCOMPLETE MODE=======");
        power_fwrite(CPU0LOCK,"1");
        power_fwrite(CPU0GOV,CPU_GOVERNOR);
        power_fwrite(CPUHOT,"1");
    #if defined A64
        power_fwrite(DRAMPAUSE,"0");
    #endif
    }
    return 0;
}
