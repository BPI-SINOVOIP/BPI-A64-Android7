# Copyright (C) 2008 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# Uncomment to be able to use ALOG* with #include "cutils/log.h".
optional_android_logging_includes :=
optional_android_logging_libraries :=
#optional_android_logging_includes := system/core/include
#optional_android_logging_libraries := liblog

subdirs := $(addprefix $(LOCAL_PATH)/,$(addsuffix /Android.mk, \
		common   \
		i18n     \
		stubdata \
	))

include $(subdirs)
