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

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <string.h>
#include <limits.h>
#include <linux/fs.h>
#include <time.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>

#define LOG_TAG "nand_trim"

#include "cutils/log.h"
#include "cutils/properties.h"

#define NT_LOGI(...) \
	do { \
			printf(__VA_ARGS__); \
			SLOGI(__VA_ARGS__); \
	} while (0)

#define NT_LOGE(...) \
	do { \
			printf(__VA_ARGS__); \
			SLOGE(__VA_ARGS__); \
	} while (0)

struct mount_info {
	char *mount_dev;
    char *mount_point;
    char *fs_type;
    void *next;
};

static unsigned long long get_boot_time_ms(void)
{
    struct timespec t;
    unsigned long long time_ms;

    t.tv_sec = 0;
    t.tv_nsec = 0;
    clock_gettime(CLOCK_BOOTTIME, &t);
    time_ms = (t.tv_sec * 1000LL) + (t.tv_nsec / 1000000);

    return time_ms;
}

static int do_fstrim_filesystem(char *mount_point)
{
    int fd;
    int ret = 0;
    //struct fstrim_range range = { 0 };
	struct fstrim_range range;
	unsigned long long start_time;
	unsigned long long used_time;

    fd = open(mount_point, O_RDONLY);
    if (fd < 0) {
        NT_LOGE("Cannot open \"%s\" for FITRIM\n", mount_point);
        return -1;
    }

    memset(&range, 0, sizeof(range));
    range.len = ULLONG_MAX;
    NT_LOGI("Invoking %s ioctl on %s\n", "FITRIM", mount_point);
	start_time = get_boot_time_ms();

    ret = ioctl(fd, FITRIM, &range);
    if (ret) {
        NT_LOGE("FITRIM ioctl failed on %s (error %d/%s)\n", mount_point, errno, strerror(errno));
        return -1;
    } else {
    	used_time = get_boot_time_ms() - start_time;
        NT_LOGI("Trimmed %llu bytes on %s (%llu ms)\n", range.len, mount_point, used_time);
    }
    close(fd);
	return 0;
}

static struct mount_info *get_mount_info(int *is_nand_dev)
{
    FILE *f;
    char mount_dev[256];
    char mount_dir[256];
    char mount_type[256];
    char mount_opts[256];
    int mount_freq;
    int mount_passno;
    int match;
	struct mount_info *mnt_list = NULL;
	struct mount_info *last_mnt;

    f = fopen("/proc/mounts", "r");
    if (! f) {
    	NT_LOGE("Can't open /proc/mounts!\n");
        /* If we can't read /proc/mounts, just give up */
        return 0;
    }

    do {
        match = fscanf(f, "%255s %255s %255s %255s %d %d\n",
                       mount_dev, mount_dir, mount_type,
                       mount_opts, &mount_freq, &mount_passno);
        mount_dev[255] = 0;
        mount_dir[255] = 0;
        mount_type[255] = 0;
        mount_opts[255] = 0;

        if (match == 6) {
        	int n;
        	char path[256];
        	n = readlink(mount_dev, path, sizeof(path) - 1);
			if (n <= 0) {
				n = strlen(mount_dev);
				strcpy(path, mount_dev);
			}else{
		        path[n] = 0;
		    }

        	if(!strncmp(path, "/dev/block/nand", 14)){
        		*is_nand_dev = 1;

        		if(!strncmp(mount_opts, "ro", 2) || !strcmp(mount_dir, "/system"))
        			continue;

        		if(!strncmp(mount_type, "ext4", 4)){
        			struct mount_info *mnt = calloc(1, sizeof(struct mount_info));
        			mnt->mount_point = strdup(mount_dir);
        			mnt->mount_dev = strdup(path);
        			mnt->fs_type = strdup(mount_type);

        			if(mnt_list == NULL){
        				mnt_list = last_mnt = mnt;
        			}else{
        				last_mnt->next = mnt;
        				last_mnt = mnt;
        			}
        		}
        	}
        }
    } while (match != EOF);

    fclose(f);

    return mnt_list;
}


void usage()
{
	printf("\t nand_trim <INTERVAL>\n");
	printf("\t\tINTERVAL:s\n");
}

static const char *self = "nand_trim";

int main(int argc, char *argv[])
{
	int sleep_t = -1;
	int ret;
	struct mount_info *mnt_list;
	int is_nand_dev;

	if(argc > 2){
		usage();
		return -1;
	}

	if(argc == 2){
		ret = sscanf(argv[1], "%d", &sleep_t);

		if(ret != 1){
			usage();
			return -1;
		}
	}

	is_nand_dev = 0;
	mnt_list = get_mount_info(&is_nand_dev);

	if(mnt_list == NULL){
		if(!is_nand_dev)
			NT_LOGI("Not nand dev, do nothing.\n");
		else
			NT_LOGI("Not ext4 filesystem for nand dev, do nothing.\n");
		property_set("ctl.stop", self);
		while(1){
			sleep(1000);
		}
	}

	while(1){
		struct mount_info *mnt;
		for(mnt = mnt_list; mnt != NULL; mnt = mnt->next){
			NT_LOGI("\"%s\" mount on \"%s\" with \"%s\".\n",
					mnt->mount_dev, mnt->mount_point, mnt->fs_type);
			do_fstrim_filesystem(mnt->mount_point);
		}

		if(sleep_t < 0){
			property_set("ctl.stop", self);
			sleep(5);
			break;
		}
		sleep(sleep_t);
	}

    return 0;
}
