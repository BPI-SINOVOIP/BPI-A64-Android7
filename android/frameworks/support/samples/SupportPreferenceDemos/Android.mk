# Copyright (C) 2016 The Android Open Source Project
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

LOCAL_PATH:= $(call my-dir)

# Build the samples.
# We need to add some special AAPT flags to generate R classes
# for resources that are included from the libraries.
include $(CLEAR_VARS)
LOCAL_PACKAGE_NAME := SupportPreferenceDemos
LOCAL_MODULE_TAGS := samples

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 14

LOCAL_DEX_PREOPT := false

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES += \
    android-support-v4 \
    android-support-v7-appcompat \
    android-support-v7-preference \
    android-support-v7-recyclerview \
    android-support-v14-preference \
    android-support-v17-leanback \
    android-support-v17-preference-leanback \

LOCAL_RESOURCE_DIR = \
    $(LOCAL_PATH)/res \
    frameworks/support/v7/appcompat/res \
    frameworks/support/v7/preference/res \
    frameworks/support/v7/recyclerview/res \
    frameworks/support/v14/preference/res \
    frameworks/support/v17/leanback/res \
    frameworks/support/v17/preference-leanback/res

LOCAL_AAPT_FLAGS := \
        --auto-add-overlay \
        --extra-packages android.support.v7.appcompat \
        --extra-packages android.support.v7.preference \
        --extra-packages android.support.v7.recyclerview \
        --extra-packages android.support.v14.preference \
        --extra-packages android.support.v17.leanback \
        --extra-packages android.support.v17.preference \
        --no-version-vectors

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)
