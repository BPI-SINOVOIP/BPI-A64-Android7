#ifndef _POWER_H_
#define _POWER_H_

#define CPU_GOVERNOR "interactive"

#ifdef A83T
#include "SUN8IW6P1.h"
#elif defined A64
#include "SUN50IW1P1.h"
#elif defined A80
#include "SUN9IW1P1.h"
#endif

#endif
