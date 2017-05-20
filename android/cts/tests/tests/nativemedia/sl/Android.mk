# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Build the unit tests.

LOCAL_PATH:= $(call my-dir)

test_executable := CtsNativeMediaSlTestCases
list_executable := $(test_executable)_list

include $(CLEAR_VARS)
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk

LOCAL_MODULE := $(test_executable)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA)/nativetest
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := $(LOCAL_MODULE)32
LOCAL_MODULE_STEM_64 := $(LOCAL_MODULE)64

LOCAL_C_INCLUDES := \
    external/gtest/include \
    $(call include-path-for, wilhelm) \
    $(call include-path-for, wilhelm-ut)

LOCAL_SRC_FILES := \
    src/SLObjectCreationTest.cpp

LOCAL_SHARED_LIBRARIES := \
    libutils \
    liblog \
    libOpenSLES \

LOCAL_STATIC_LIBRARIES := \
    libOpenSLESUT \
    libgtest

LOCAL_CTS_TEST_PACKAGE := android.nativemedia.sl

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts

LOCAL_CFLAGS := -Werror -Wall

include $(BUILD_CTS_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk

LOCAL_MODULE := $(list_executable)
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    src/SLObjectCreationTest.cpp

LOCAL_CFLAGS := \
    -DBUILD_ONLY \
    -Werror -Wall

LOCAL_SHARED_LIBRARIES := \
    liblog \

include $(BUILD_HOST_NATIVE_TEST)
