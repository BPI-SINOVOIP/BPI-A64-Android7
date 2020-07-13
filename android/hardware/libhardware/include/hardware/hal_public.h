#ifndef  HAL_PUBLIC_H
#define  HAL_PUBLIC_H

#define astar	1
#define kylin	2
#define octopus 3

#if (TARGET_BOARD_PLATFORM == astar \
     || TARGET_BOARD_PLATFORM == tulip)
#include "hal_public/hal_mali_utgard.h"
#elif (TARGET_BOARD_PLATFORM == octopus)
#include "hal_public/hal_img_sgx544.h"
#elif (TARGET_BOARD_PLATFORM == kylin)
#include "hal_public/hal_img_rgx6230.h"
#else
#error "please select a platform\n"
#endif

#endif
