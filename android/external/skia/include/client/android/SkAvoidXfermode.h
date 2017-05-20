/*
 * Copyright 2006 The Android Open Source Project
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef SkAvoidXfermode_DEFINED
#define SkAvoidXfermode_DEFINED

#include "SkColor.h"
#include "SkTypes.h"
#include "SkXfermode.h"

/** \class AvoidXfermode

    This xfermode will draw the src everywhere except on top of the specified
    color.
*/
class SkAvoidXfermode : public SkXfermode {
public:
    enum Mode {
        kAvoidColor_Mode,   //!< draw everywhere except on the opColor
        kTargetColor_Mode   //!< draw only on top of the opColor
    };

    /** This xfermode draws, or doesn't draw, based on the destination's
        distance from an op-color.

        There are two modes, and each mode interprets a tolerance value.

        Avoid: In this mode, drawing is allowed only on destination pixels that
               are different from the op-color.
               Tolerance near 0: avoid any colors even remotely similar to the op-color
               Tolerance near 255: avoid only colors nearly identical to the op-color

        Target: In this mode, drawing only occurs on destination pixels that
                are similar to the op-color
                Tolerance near 0: draw only on colors that are nearly identical to the op-color
                Tolerance near 255: draw on any colors even remotely similar to the op-color
     */
    static SkAvoidXfermode* Create(SkColor opColor, U8CPU tolerance, Mode mode) {
        return new SkAvoidXfermode(opColor, tolerance, mode);
    }

    // overrides from SkXfermode
    void xfer32(SkPMColor dst[], const SkPMColor src[], int count,
                const SkAlpha aa[]) const override;
    void xfer16(uint16_t dst[], const SkPMColor src[], int count,
                const SkAlpha aa[]) const override;
    void xferA8(SkAlpha dst[], const SkPMColor src[], int count,
                const SkAlpha aa[]) const override;

#if SK_SUPPORT_GPU
    const GrFragmentProcessor* getFragmentProcessorForImageFilter(
                                                    const GrFragmentProcessor* dst) const override;
    GrXPFactory* asXPFactory() const override;
#endif

    SK_TO_STRING_OVERRIDE()
    SK_DECLARE_PUBLIC_FLATTENABLE_DESERIALIZATION_PROCS(AvoidXfermode)

protected:
    SkAvoidXfermode(SkColor opColor, U8CPU tolerance, Mode mode);
    void flatten(SkWriteBuffer&) const override;

private:
    SkColor     fOpColor;
    uint32_t    fDistMul;   // x.14 cached from fTolerance
    uint8_t     fTolerance;
    Mode        fMode;

    typedef SkXfermode INHERITED;
};

#endif
