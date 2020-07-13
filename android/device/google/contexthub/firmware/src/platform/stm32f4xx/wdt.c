/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <plat/inc/pwr.h>
#include <plat/inc/wdt.h>
#include <plat/inc/cmsis.h>

struct StmWwdg {
    volatile uint16_t CR;
    uint8_t unused0[2];
    volatile uint16_t CFR;
    uint8_t unused1[2];
    volatile uint16_t SR;
    uint8_t unused2[2];
};

#define WWDG ((struct StmWwdg*)WWDG_BASE)

/* WWDG bit definitions */
#define WWDG_CR_ENABLE 0x80

#define WWDG_TCNT_HIGH 0x40
#define WWDG_TCNT_MASK 0x3F

#define WWDG_CFR_DIV2 0x0080
#define WWDG_CFR_DIV4 0x0100
#define WWDG_CFR_DIV8 0x0180
#define WWDG_CFR_EWI  0x0200

/* WWDG parameters */
#define WWDG_WINDOW_SIZE 0x3F // 0 < x <= 0x3F

void WWDG_IRQHandler(void);
void __attribute__((naked)) WWDG_IRQHandler(void)
{
    asm volatile(
        "mov    r0, #2                    \n"
        "b      cpuCommonFaultCode        \n"
    );
}

void wdtEnableClk()
{
    pwrUnitClock(PERIPH_BUS_APB1, PERIPH_APB1_WWDG, true);
}

void wdtEnableIrq()
{
    NVIC_EnableIRQ(WWDG_IRQn);
}

void wdtDisableClk()
{
    pwrUnitClock(PERIPH_BUS_APB1, PERIPH_APB1_WWDG, false);
}

void wdtDisableIrq()
{
    NVIC_DisableIRQ(WWDG_IRQn);
}

void wdtInit()
{
    wdtEnableClk();
    WWDG->CFR = WWDG_CFR_EWI | WWDG_CFR_DIV8 | WWDG_TCNT_HIGH | (WWDG_WINDOW_SIZE & WWDG_TCNT_MASK);
    WWDG->CR  = WWDG_CR_ENABLE | WWDG_TCNT_HIGH | (WWDG_WINDOW_SIZE & WWDG_TCNT_MASK);
    // with 16Mhz APB1 clock, this is 256*DIV = 2,048 uS per WWDG tick with DIV=8, max 131,072 uS WWDG window
    wdtEnableIrq();
}

void wdtPing()
{
    WWDG->CR = (WWDG->CR & ~WWDG_TCNT_MASK) | WWDG_TCNT_HIGH | (WWDG_WINDOW_SIZE & WWDG_TCNT_MASK);
}
