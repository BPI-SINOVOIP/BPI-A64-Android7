# Copyright (C) 2010 The Android Open Source Project
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

$(warning $(TARGET_BOARD_PLATFORM))

MY_LOCAL_PATH := $(call my-dir)

# audio effects lib
include $(MY_LOCAL_PATH)/effects/Android.mk

# audio primary module
include $(MY_LOCAL_PATH)/$(TARGET_BOARD_PLATFORM)/Android.mk

# audio a2dp module
include $(MY_LOCAL_PATH)/audio_a2dp_hw/Android.mk

include $(CLEAR_VARS)

