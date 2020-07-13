/*
 * Copyright (C) 2010 The Android Open Source Project
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
#define LOG_TAG "paramopt"

#include "log.h"
#include "cutils/properties.h"
#include <string.h>
#include <stdlib.h>

#include "ParamOpt.h"

static const char* s_ScreenSizeConfig[] = {
"SCREEN_SMALL",
"SCREEN_NORMAL",
"SCREEN_LARGE",
"SCREEN_XLARGE"
};

static int get_dram_size(void) {
    FILE *fd;
    char data[128], *tmp;
    int dram_size = 1024;

    fd = fopen(MEMINFO_NODE, "r");
    if (fd == NULL) {
        ERROR("cannot open %s, return default 1G\n", MEMINFO_NODE);
        goto end;
    }

    while (fgets(data, sizeof(data), fd)) {
        if (strstr(data, "MemTotal")) {
            tmp = strchr(data, ':') + 1;
            dram_size = atoi(tmp) >> 10; /* convert to MBytes */
            break;
        }
    }

    fclose(fd);
end:
    INFO("%s: return %d\n", __func__, dram_size);
    return dram_size;
}

inline void trim(char *buf) {
    char *temp;
    int i = 0;

    if (!buf || *buf == 0)
        return;

    /* trim tail */
    while ((temp = buf + strlen(buf) - 1) && *temp != 0) {
        if (*temp==' ' || *temp=='\t'
                || *temp=='\n' || *temp=='\r')
            *temp = 0;
        else
            break;
    }

    if (*buf == 0)
        return;

    /* trim head */
    while (i < (int)strlen(buf)) {
        if (buf[i]==' ' || buf[i]=='\t'
                || buf[i]=='\n' || buf[i]=='\r') {
            i++;
            continue;
        } else if (buf[i] != 0) {
            strcpy(buf, &buf[i]);
            break;
        } else {
            buf[0] = 0;
            break;
        }
    }
}

void config_item(char *buf) {
    char data[1024], key[256], value[256];
    bool find = false;
    FILE *fd;
    int len;

    fd = fopen(CONFIG_MEM_FILE, "r");
    if (fd == NULL) {
        ERROR("cannot open %s\n", CONFIG_MEM_FILE);
        return;
    }

    while (!feof(fd)) {
        if (!fgets(data, sizeof(data), fd)) /* eof or read error */
            continue;

        if (strlen(data) >= sizeof(data) - 1) {
            ERROR("%s err: line too long!\n", __func__);
            goto end;
        }

        trim(data);

        if (data[0]=='#' || data[0]==0) /* comment or blank line */
            continue;

        if (!find) {
            if (data[0]=='[' && strstr(data, buf)) {
                find = true;
                continue;
            }
        } else {
            if (data[0]=='[') {
                break; /* NEXT item, so break */
            } else if (!strstr(data, "=") || data[strlen(data)-1] == '=') {
                continue; /* not key=value style, or has no value field */
            }

            len = strlen(data) - strlen(strstr(data, "="));
            strncpy(key, data, len);
            key[len] = '\0';
            trim(key);

            strcpy(value, strstr(data, "=") + 1);
            trim(value);

            INFO("%s: get key->value %s %s\n", __func__, key, value);
            if (key[0] == '/')  {
                /* file node, as: /sys/class/adj=12 */
                sprintf(data, "echo %s > %s", value, key);
                system(data);
            } else {
                /* property node, as: dalvik.vm.heapsize=184m */
                property_set(key, value);
            }
        }
    }

end:
    fclose(fd);
}

static bool get_lcd_resolution(int *width, int *height) {
   char buf[PROP_VALUE_MAX] = {0};
   if (property_get("ro.boot.lcd_x", buf, "")) {
        *width = atoi(buf);
        if (*width <= 0) {
            ERROR("%s: ro.boot.lcd_x wrong value: %d(convert from %s) set, \
                    disable adaptive memory function!\n", __func__, *width, buf);
            return false;
        }
    } else {
        ERROR("%s: ro.boot.lcd_x not set, disable adaptive memory function!\n", __func__);
        return false;
    }

    if (property_get("ro.boot.lcd_y", buf, "")) {
        *height = atoi(buf);
        if (*height <= 0) {
            ERROR("%s: ro.boot.lcd_y wrong value: %d(convert from %s) set, \
                    disable adaptive memory function!\n", __func__, *height, buf);
            return false;
        }
    } else {
        ERROR("%s: ro.boot.lcd_y not set, disable adaptive memory function!\n", __func__);
        return false;
    }
    return true;
}

int getDensityFromString2Int(char *sDensity) {
    if ((NULL == sDensity) || 0 == *sDensity) {
        return 0;
    }

    if (!strcmp(sDensity, "DENSITY_LOW")) {
        return DENSITY_LOW;
    } else if (!strcmp(sDensity, "DENSITY_MEDIUM")) {
        return DENSITY_MEDIUM;
    } else if (!strcmp(sDensity, "DENSITY_TV")) {
        return DENSITY_TV;
    } else if (!strcmp(sDensity, "DENSITY_HIGH")) {
        return DENSITY_HIGH;
    } else if (!strcmp(sDensity, "DENSITY_280")) {
        return DENSITY_280;
    } else if (!strcmp(sDensity, "DENSITY_XHIGH")) {
        return DENSITY_XHIGH;
    } else if (!strcmp(sDensity, "DENSITY_360")) {
        return DENSITY_360;
    } else if (!strcmp(sDensity, "DENSITY_400")) {
        return DENSITY_400;
    } else if (!strcmp(sDensity, "DENSITY_420")) {
        return DENSITY_420;
    } else if (!strcmp(sDensity, "DENSITY_XXHIGH")) {
        return DENSITY_XXHIGH;
    } else if (!strcmp(sDensity, "DENSITY_560")) {
        return DENSITY_560;
    } else if (!strcmp(sDensity, "DENSITY_XXXHIGH")) {
        return DENSITY_XXXHIGH;
    } else {
        return 0;
    }
}

int getMinimumMemory(int screen_size, int density, bool is64Bit) {
    char data[1024];
    bool find = false;
    FILE *fd;
    char *sScreenSizeRead    = NULL;
    char *sDensityRead = NULL;
    char *sCmpRead     = NULL;
    char *sMemRead     = NULL;
    int iDensityRead = 0;
    int iMemRead      = 0;

    fd = fopen(CONFIG_MEM_FILE, "r");
    if (fd == NULL) {
        ERROR("cannot open %s\n", CONFIG_MEM_FILE);
        return 0;
    }

    while (!feof(fd)) {
        if (!fgets(data, sizeof(data), fd)) /* eof or read error */
            continue;

        if (strlen(data) >= sizeof(data) - 1) {
            ERROR("%s err: line too long!\n", __func__);
            goto end;
        }

        trim(data);

        if (data[0]=='#' || data[0]==0) /* comment or blank line */
            continue;

        if (!find) {
            if (data[0]=='[' && strstr(data, "least_memory")) {
                find = true;
                continue;
            }
        } else {
            if (data[0]=='[')
                break; /* NEXT item, so break */

            sScreenSizeRead = strtok(data , " ");
            if ((NULL == sScreenSizeRead) || (0 == *sScreenSizeRead) || \
                (strcmp(sScreenSizeRead, s_ScreenSizeConfig[screen_size]))) {
                continue;
            }

            sDensityRead = strtok(NULL , " ");
            if ((NULL == sDensityRead) || (0 == *sDensityRead)) {
                continue;
            }
            iDensityRead = getDensityFromString2Int(sDensityRead);
            if (iDensityRead <= 0) {
                continue;
            }

            sCmpRead = strtok(NULL , " ");
            if ((NULL == sCmpRead) || (0 == *sCmpRead)) {
                continue;
            }
            if ((!strcmp(sCmpRead, "<=") && (density > iDensityRead)) || \
                (!strcmp(sCmpRead, ">=") && (density < iDensityRead))) {
                continue;
            }

            sMemRead = strtok(NULL , " ");
            if ((NULL == sMemRead) || (0 == *sMemRead)) {
                break;
            }
            if (!is64Bit) {
                iMemRead = atoi(sMemRead);
                break;
            }

            sMemRead = strtok(NULL , " ");
            if ((NULL == sMemRead) || (0 == *sMemRead)) {
                break;
            }
            iMemRead = atoi(sMemRead);
            break;
        }
    }

end:
    fclose(fd);
    return iMemRead;
}

void config_low_ram_property(int screen_size, int density, bool is64Bit, int totalmem) {
    int minMem = getMinimumMemory(screen_size, density, is64Bit);
    /*if total memory is less than 1.5 * least memory requested by cdd,
       then it's a low memory device */
    int lowMemoryThreathHold = minMem + (minMem >> 1);

    if (totalmem <= lowMemoryThreathHold) {
        property_set("ro.config.low_ram", "true");
    } else {
        property_set("ro.config.low_ram", "false");
    }

    if (totalmem <= 512) {
        property_set("ro.config.512m", "true");
    }
}

void config_heap_growth_limit_property(int screen_size, int density) {
    char data[1024];
    bool find = false;
    FILE *fd;
    char *sDensityRead = NULL;
    char *sMemRead     = NULL;
    int iDensityRead   = 0;

    fd = fopen(CONFIG_MEM_FILE, "r");
    if (fd == NULL) {
        ERROR("cannot open %s\n", CONFIG_MEM_FILE);
        return;
    }

    while (!feof(fd)) {
        if (!fgets(data, sizeof(data), fd)) /* eof or read error */
            continue;

        if (strlen(data) >= sizeof(data) - 1) {
            ERROR("%s err: line too long!\n", __func__);
            goto end;
        }

        trim(data);

        if (data[0]=='#' || data[0]==0) /* comment or blank line */
            continue;

        if (!find) {
            if (data[0]=='[' && strstr(data, "heap_growth_limit")) {
                find = true;
                continue;
            }
        } else {
            if (data[0]=='[')
                break; /* NEXT item, so break */

            sDensityRead = strtok(data , " ");
            if ((NULL == sDensityRead) || (0 == *sDensityRead)) {
                continue;
            }
            iDensityRead = getDensityFromString2Int(sDensityRead);
            if ((iDensityRead <= 0) || (density > iDensityRead)) {
                continue;
            }

            switch (screen_size) {
                case SCREEN_XLARGE:
                    sMemRead = strtok(NULL , " ");
                case SCREEN_LARGE:
                    sMemRead = strtok(NULL , " ");
                case SCREEN_NORMAL:
                    sMemRead = strtok(NULL , " ");
                case SCREEN_SMALL:
                    sMemRead = strtok(NULL , " ");
                    if ((NULL != sMemRead) || (0 != *sMemRead)) {
                        property_set("dalvik.vm.heapgrowthlimit", sMemRead);
                    }
                    break;
                default :
                    break;
            }

            break;

        }
    }

end:
    fclose(fd);
    return;
}

int getScreenSize(int width, int height) {
    int max = width >= height ? width : height;
    int min = width <= height ? width : height;

    if ((min >= 720) && (max > 960)) {
        return SCREEN_XLARGE;
    } else if ((min >= 480) && (max > 640)) {
        return SCREEN_LARGE;
    } else if ((min >= 320) && (max > 480)) {
        return SCREEN_NORMAL;
    } else {
        return SCREEN_SMALL;
    }
}

int main() {
    char buf[PROP_VALUE_MAX] = {0};
    int totalMem = 0, densityDPI = 0, screenSize = 0;
    int width = 0, height = 0, width_dp = 0, height_dp = 0;
    bool bSupport64Bit = false;

    ERROR("ParamOpt start!\n");
    if(property_get("ro.memopt.disable", buf, "") && !strcmp(buf,"true")) {
        ERROR("disable adaptive memory function!\n");
        return 1;
    }

    totalMem = get_dram_size();

    if (property_get("ro.sf.lcd_density", buf, "")) {
        densityDPI = atoi(buf);
        if (densityDPI <= 0) {
            ERROR("ro.sf.lcd_density wrong value: %d(convert from %s) set, \
                    disable adaptive memory function!\n", densityDPI, buf);
            return 1;
        }
    } else {
        ERROR("ro.sf.lcd_density not set, disable adaptive memory function!\n");
        return 1;
    }

    if (property_get("ro.product.cpu.abilist64", buf, "")) {
        bSupport64Bit = true;
    } else  {
        bSupport64Bit = false;
    }

    /* dalvik heap para */
    if (totalMem <= 512) {
        strcpy(buf, "dalvik_512m");
    } else if (totalMem > 512 && totalMem <= 1024) {
        strcpy(buf, "dalvik_1024m");
    } else if (totalMem > 1024 && totalMem <= 2048) {
        strcpy(buf, "dalvik_2048m");
    } else {
        strcpy(buf, "dalvik_1024m");
    }
    config_item(buf);

    /* hwui para */
    if (!width || !height) {
        if (!get_lcd_resolution(&width, &height)) {
            ERROR("get lcd resolution failed!\n");
            return 1;
        }
    }
    sprintf(buf, "hwui_%d", (width > height ? width : height));
    config_item(buf);

    width_dp = width * DENSITY_MEDIUM / densityDPI;
    height_dp = height * DENSITY_MEDIUM / densityDPI;
    screenSize = getScreenSize(width_dp, height_dp);
    ERROR("width_dp = %d; height_dp = %d; screenSize = %d; bSupport64Bit = %d, g_total_mem = %d \n",\
            width_dp, height_dp, screenSize, bSupport64Bit, totalMem);

    /* low memory property*/
    if (!property_get("ro.config.low_ram", buf, "")) {
		config_low_ram_property(screenSize, densityDPI, bSupport64Bit, totalMem);
	}

    /* dalvik.vm.heapgrowthlimit property*/
    if (!property_get("dalvik.vm.heapgrowthlimit", buf, "")) {
        config_heap_growth_limit_property(screenSize, densityDPI);
    }

    system("echo 12000 > /sys/module/lowmemorykiller/parameters/minfree");
    ERROR("ParamOpt: end!\n");
    return 0;
}
