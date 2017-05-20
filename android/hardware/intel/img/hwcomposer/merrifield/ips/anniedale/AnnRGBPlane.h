/*
// Copyright (c) 2014 Intel Corporation 
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/
#ifndef ANN_RGB_PLANE_H
#define ANN_RGB_PLANE_H

#include <utils/KeyedVector.h>
#include <hal_public.h>
#include <Hwcomposer.h>
#include <BufferCache.h>
#include <DisplayPlane.h>

#include <linux/psb_drm.h>

namespace android {
namespace intel {

class AnnRGBPlane : public DisplayPlane {
public:
    AnnRGBPlane(int index, int type, int disp);
    virtual ~AnnRGBPlane();
public:
    // hardware operations
    bool enable();
    bool disable();
    bool isDisabled();
    void postFlip();

    void* getContext() const;
    void setZOrderConfig(ZOrderConfig& config, void *nativeConfig);

    bool setDataBuffer(buffer_handle_t handle);
protected:
    bool setDataBuffer(BufferMapper& mapper);
    bool enablePlane(bool enabled);
private:
    void setFramebufferTarget(buffer_handle_t handle);
protected:
    struct intel_dc_plane_ctx mContext;
};

} // namespace intel
} // namespace android

#endif /* ANN_RGB_PLANE_H */
