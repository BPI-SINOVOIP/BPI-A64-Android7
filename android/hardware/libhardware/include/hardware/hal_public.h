#ifndef  HAL_PUBLIC_H
#define  HAL_PUBLIC_H

#define astar	1
#define kylin	2
#define octopus 3

#if (TARGET_BOARD_PLATFORM == astar)
#include "hal_public_mali.h"
#elif (TARGET_BOARD_PLATFORM == tulip)
#include "hal_public_mali400.h"
#elif (TARGET_BOARD_PLATFORM == kylin)
#include "hal_public_6230.h"
#elif (TARGET_BOARD_PLATFORM == octopus)
#include "hal_public_544.h"
#else
#error "please select a platform\n"
#endif

#endif
