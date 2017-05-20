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
#ifndef PLATF_BUFFER_MANAGER_H
#define PLATF_BUFFER_MANAGER_H

#include <BufferManager.h>

namespace android {
namespace intel {

class PlatfBufferManager : public BufferManager {
public:
    PlatfBufferManager();
    virtual ~PlatfBufferManager();

public:
    bool initialize();
    void deinitialize();

protected:
    DataBuffer* createDataBuffer(buffer_handle_t handle);
    BufferMapper* createBufferMapper(DataBuffer& buffer);
    bool blit(buffer_handle_t srcHandle, buffer_handle_t destHandle,
              const crop_t& destRect, bool filter, bool async);
};

}
}
#endif /* PLATF_BUFFER_MANAGER_H */
