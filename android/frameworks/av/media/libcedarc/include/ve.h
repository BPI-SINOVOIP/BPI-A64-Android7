
/*
* Copyright (c) 2008-2016 Allwinner Technology Co. Ltd.
* All rights reserved.
*
* File : ve.h
* Description :
* History :
*   Author  : xyliu <xyliu@allwinnertech.com>
*   Date    : 2016/04/13
*   Comment :
*
*
*/

#ifndef VE_H
#define VE_H
#include <stdint.h>
#include <pthread.h>

#ifdef __cplusplus
extern "C" {
#endif


enum DRAMTYPE
{
    DDRTYPE_DDR1_16BITS = 0,
    DDRTYPE_DDR1_32BITS = 1,
    DDRTYPE_DDR2_16BITS = 2,
    DDRTYPE_DDR2_32BITS = 3,
    DDRTYPE_DDR3_16BITS = 4,
    DDRTYPE_DDR3_32BITS = 5,
    DDRTYPE_DDR3_64BITS = 6,

    DDRTYPE_MIN = DDRTYPE_DDR1_16BITS,
    DDRTYPE_MAX = DDRTYPE_DDR3_64BITS,
};

enum CHIPID
{
    CHIP_UNKNOWN = 0,
    CHIP_H3s = 1,
    CHIP_H3 = 2,
    CHIP_H2 = 3,
    CHIP_H2PLUS = 4,
};

enum RESET_VE_MODE
{
    RESET_VE_NORMAL = 0,
    RESET_VE_SPECIAL = 1,  // for dtmb, we should reset ve not reset decode
};

typedef struct VETOP_REG_MODE_SELECT
{
    volatile unsigned int mode                        :4;
    //* 0: mpeg/jpeg, 1:h264/avs, 2:vc1;
    volatile unsigned int reserved0                    :1;
    volatile unsigned int jpg_dec_en                 :1;
    //* add for 1681.
    volatile unsigned int enc_isp_enable             :1;
    //* add for 1633.
    volatile unsigned int enc_enable                 :1;
    //* Jpeg/H264 encoder enable.
    volatile unsigned int read_counter_sel            :1;
    volatile unsigned int write_counter_sel            :1;

    volatile unsigned int decclkgen                    :1;
    volatile unsigned int encclkgen                    :1;
    volatile unsigned int reserved1                    :1;
    volatile unsigned int rabvline_spu_dis            :1;
    volatile unsigned int deblk_spu_dis                :1;
    volatile unsigned int mc_spu_dis                 :1;
    volatile unsigned int ddr_mode                    :2;
    //* 00: 16-DDR1, 01: 32-DDR1 or DDR2, 10: 32-DDR2 or 16-DDR3, 11: 32-DDR3

    //* the following 14 bits are for 1623, ...
    volatile unsigned int reserved2                    :1;
    volatile unsigned int mbcntsel                    :1;
    volatile unsigned int rec_wr_mode                :1;
    volatile unsigned int pic_width_more_2048        :1;
    //* for 1633
    volatile unsigned int pic_width_is_4096            :1;
    volatile unsigned int reserved3                    :9;
}vetop_reg_mode_sel_t;


//* 0x04
typedef struct VETOP_REG_RESET
{
    volatile unsigned int reset                     :1;
    //* 1633 do not use this bit.
    volatile unsigned int reserved0                 :3;
    volatile unsigned int mem_sync_mask             :1;
    //* 1633 do not use this bit.
    //* for 1633
    volatile unsigned int wdram_clr                 :1;
    //* add for 1633.
    volatile unsigned int reserved1                 :2;
    volatile unsigned int write_dram_finish         :1;
    volatile unsigned int ve_sync_idle              :1;
    //* this bit can be used to check the status of sync module before rest.
    volatile unsigned int reserved2                 :6;
    volatile unsigned int decoder_reset             :1;
    //* 1: reset assert, 0: reset de-assert.
    volatile unsigned int dec_req_mask_enable       :1;
    //* 1: mask, 0: pass.
   // volatile unsigned int reserved3                    :6;
    volatile unsigned int  dec_vebk_reset            :1;
    //* 1: reset assert, 0: reset de-assert. used in decoder.
    volatile unsigned int  reserved3                :5;

    volatile unsigned int encoder_reset             :1;
    //* 1. reset assert, 0: reset de-assert.
    volatile unsigned int enc_req_mask_enable       :1;
    //* 1: mask, 0: pass.
    volatile unsigned int reserved4                 :6;
}vetop_reg_reset_t;


//* 0x2c      for 1681 jpeg decode
typedef struct VETOP_REG_JPG_RESET
{
    volatile unsigned int jpg_dec_reset                :1;
    //* 1. reset assert, 0: reset de-assert.
    volatile unsigned int jpg_dec_req_mask_enable    :1;
    //* 1: mask, 0: pass.
    volatile unsigned int reserved0                    :30;
}vetop_reg_jpg_reset_t;

typedef struct VE_PARAM
{
    unsigned int ic_version;

#if defined(CONFIG_VE_IPC_ENABLE)
    uint32_t lock_vdec;
    uint32_t lock_venc;
    uint32_t lock_jdec;
#else
    pthread_mutex_t *locks[3];
    
    pthread_mutex_t *lock_vdec;
    pthread_mutex_t *lock_venc;
    pthread_mutex_t *lock_jdec;
#endif

    int ddr_mode;
    uint32_t phy_offset;
    int need_check_idle;
}ve_param_t;

int   VeInitialize(void);

void  VeRelease(void);

int   VeLock(void);

int  VeUnLock(void);

int VeEncoderLock(void);

int VeEncoderUnLock(void);

void VeSetDramType();

void  VeReset(void);

int   VeWaitInterrupt(void);

int VeWaitEncoderInterrupt(void);

void* VeGetRegisterBaseAddress(void);

unsigned int VeGetIcVersion();

int   VeGetDramType(void);

uint32_t VeGetPhyOffset(void);

int   VeSetSpeed(int nSpeedMHz);

void VeEnableEncoder();

void VeDisableEncoder();

void VeEnableDecoder(int nDecoderMode);

void VeDisableDecoder();

void VeDecoderWidthMode(int nWidth);

void VeResetDecoder(int resetVeMode);

void VeResetEncoder();

void VeInitEncoderPerformance(int nMode);

void VeUninitEncoderPerformance(int nMode);


//for 1681 jpeg decode
int VeWaitJpegDecodeInterrupt(void);
void VeEnableJpegDecoder();
void VeDisableJpegDecoder();
void VeResetJpegDecoder();
int VeJpegDeLock(void);
int VeJpegDeUnLock(void);
int VeChipId(void);

void VeSetDdrMode(int ddr_mode);

#ifdef __cplusplus
}
#endif


#endif

