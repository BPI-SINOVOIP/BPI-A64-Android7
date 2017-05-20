
/*
 * Copyright 2011 Google Inc.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include "SkColorPriv.h"
#include "SkReadBuffer.h"
#include "SkWriteBuffer.h"
#include "SkPixelRef.h"
#include "SkErrorInternals.h"
#include "SkShader.h"
#include "SkBitmapProcShader.h"
#include <stdlib.h>

extern void ClampX_ClampY_nofilter_scale_neon(const SkBitmapProcState& s,
                                uint32_t xy[], int count, int x, int y);
extern void S32_opaque_D32_nofilter_DX_neon(const SkBitmapProcState& s,
                             const uint32_t* SK_RESTRICT xy,
                             int count, SkPMColor* SK_RESTRICT colors);
extern void ClampX_ClampY_nofilter_scale(const SkBitmapProcState& s,
                             uint32_t xy[], int count, int x, int y);
extern void S32_opaque_D32_nofilter_DX(const SkBitmapProcState& s,
                             const uint32_t* SK_RESTRICT xy,
                             int count, SkPMColor* SK_RESTRICT colors);


bool SkShader::Context::shadeSpanRect(int x, int y, SkPMColor dstC[],
                             int rb, int count, int height) {
    return false;
}

bool SkShader::Context::shadeSpanRect_D565(int x, int y, uint16_t* dst,
                                       int rb, int count, int height, SkBlitRow::Proc16 blit_proc) {
    return false;
}

bool SkBitmapProcShader::BitmapProcShaderContext::shadeSpanRect(int x, int y, SkPMColor dstC[],
                                                              int rb, int count, int height) {
    const SkBitmapProcState& state = *fState;
    SkBitmapProcState::MatrixProc	 mproc = state.getMatrixProc();
    SkBitmapProcState::SampleProc32 sproc = state.getSampleProc32();

#if defined(__ARM_HAVE_NEON_COMMON)
    if ((ClampX_ClampY_nofilter_scale_neon != mproc) || (S32_opaque_D32_nofilter_DX_neon != sproc)) {
        return false;
    }
#else
        return false;
#endif

    uint32_t *buffer = (uint32_t*)malloc((count/2+2) * sizeof(uint32_t));
    if (buffer == NULL) {
        SkDebugf("insufficient memory in %s", __func__);
        return false;
    }

    SkASSERT(state.fBitmap->getPixels());
    SkASSERT(state.fBitmap->pixelRef() == NULL ||
            state.fBitmap->pixelRef()->isLocked());

    SkFixed fy;
    const unsigned maxY = state.fBitmap->height() - 1;

    SkPoint pt;
    state.fInvProc(state.fInvMatrix, SkIntToScalar(x) + SK_ScalarHalf,
                SkIntToScalar(y) + SK_ScalarHalf, &pt);
    fy = SkScalarToFixed(pt.fY);
    fy = SkClampMax((fy) >> 16, maxY);


    SkFixed dy = state.fInvKy;
    mproc(state, buffer, count, x, y);
    while (height > 0) {
        sproc(state, buffer, count, dstC);
        y++;
        state.fInvProc(state.fInvMatrix, SkIntToScalar(x) + SK_ScalarHalf,
                        SkIntToScalar(y) + SK_ScalarHalf, &pt);
        fy = SkScalarToFixed(pt.fY);
        buffer[0] = SkClampMax((fy) >> 16, maxY);
        dstC = (SkPMColor*)((char*)dstC + rb);
        height--;
    }

    free(buffer);
    return true;
}

bool SkBitmapProcShader::BitmapProcShaderContext::shadeSpanRect_D565(int x, int y, uint16_t* dst,
                                                                 int rb, int count, int height, SkBlitRow::Proc16 blit_proc) {
    const SkBitmapProcState& state = *fState;
    SkBitmapProcState::MatrixProc   mproc = state.getMatrixProc();
    SkBitmapProcState::SampleProc32 sproc = state.getSampleProc32();
    SkASSERT(NULL != blit_proc);

#if defined(__ARM_HAVE_NEON_COMMON)
    if ((ClampX_ClampY_nofilter_scale_neon != mproc) || (S32_opaque_D32_nofilter_DX_neon != sproc)) {
        return false;
    }
#else
    return false;
#endif

    uint32_t *buffer = (uint32_t*)malloc((count/2+2) * sizeof(uint32_t));
    if (buffer == NULL) {
        SkDebugf("insufficient memory in %s", __func__);
        return false;
    }

    SkPMColor *dstBuffer = (SkPMColor *)malloc(count * sizeof(SkPMColor));
    if (dstBuffer == NULL) {
        SkDebugf("insufficient memory in %s", __func__);
        return false;
    }

    SkASSERT(state.fBitmap->getPixels());
    SkASSERT(state.fBitmap->pixelRef() == NULL ||
        state.fBitmap->pixelRef()->isLocked());

    const unsigned maxY = state.fBitmap->height() - 1;
    mproc(state, buffer, count, x, y);
    while (height > 0) {
        sproc(state, buffer, count, dstBuffer);

        blit_proc(dst, dstBuffer, count, 0xFF, x, y);	/* blend S32 pixel to D565 dst memory */

        y++;
        SkPoint pt;
        state.fInvProc(state.fInvMatrix, SkIntToScalar(x) + SK_ScalarHalf,
                SkIntToScalar(y) + SK_ScalarHalf, &pt);
        SkFractionalInt fy = SkScalarToFractionalInt(pt.fY);
        buffer[0] = SkClampMax((SkFractionalIntToFixed(fy) >> 16), maxY);

        dst = (uint16_t *)((char*)dst + rb);
        height--;
    }

    free(buffer);
    free(dstBuffer);

    return true;
}



