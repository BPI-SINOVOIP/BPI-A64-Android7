#
# Copyright (C) 2012 The Android Open Source Project
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
#

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(call all-subdir-cpp-files)

#$(info $(LOCAL_SRC_FILES))
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lib/include $(LOCAL_PATH)/../lib/src external/gtest/include \
    external/tinyalsa/include/  libcore/include
LOCAL_STATIC_LIBRARIES := libutils libgtest_host libgtest_main_host  liblog libcutils libtinyalsa \
    libtinyxml
# need to keep everything in libcts_.. Otherwise, linker will drop some
# functions and linker error happens
LOCAL_WHOLE_STATIC_LIBRARIES := libcts_audio_quality
LOCAL_CFLAGS:= -g -fno-exceptions
LOCAL_LDFLAGS:= -g -lrt -ldl -lm -fno-exceptions -lpthread
LOCAL_MODULE_HOST_OS := linux
LOCAL_MODULE:= cts_audio_quality_test
include $(BUILD_HOST_EXECUTABLE)
