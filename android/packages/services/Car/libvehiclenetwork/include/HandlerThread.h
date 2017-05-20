/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef CAR_HANDLER_THREAD_H_
#define CAR_HANDLER_THREAD_H_

#include <utils/Looper.h>
#include <utils/threads.h>

namespace android {

/**
 * Native HandlerThread implementation looking similar to Java version.
 */
class HandlerThread : public Thread {
public:
    HandlerThread();
    virtual ~HandlerThread();

    sp<Looper> getLooper();
    status_t start(const char* name = 0, int32_t priority = PRIORITY_DEFAULT, size_t stack = 0);
    void quit();

private:
    bool threadLoop();

private:
    sp<Looper> mLooper;
    mutable Mutex mLock;
    bool mShouldQuit;
    Condition mLooperWait;
};

};

#endif /* CAR_HANDLER_THREAD_H_ */
