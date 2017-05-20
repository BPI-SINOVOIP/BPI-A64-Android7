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

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := CtsLibcoreTestCases

LOCAL_STATIC_JAVA_LIBRARIES := \
    apache-harmony-tests \
    conscrypt-tests \
    core-tests \
    cts-core-test-runner \
    jsr166-tests \
    mockito-target \
    tzdata_update-tests

# Don't include this package in any target
LOCAL_MODULE_TAGS := tests

# When built, explicitly put it in the data partition.
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_DEX_PREOPT := false
LOCAL_JACK_FLAGS := --multi-dex native

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_JNI_SHARED_LIBRARIES := libjavacoretests libsqlite_jni libnativehelper_compat_libc++ libc++

# Include both the 32 and 64 bit versions of libjavacoretests,
# where applicable.
LOCAL_MULTILIB := both

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts

LOCAL_JAVA_RESOURCE_DIRS := resources

LOCAL_JAVA_RESOURCE_FILES := \
    libcore/expectations/brokentests.txt \
    libcore/expectations/icebox.txt \
    libcore/expectations/knownfailures.txt \
    libcore/expectations/taggedtests.txt

LOCAL_JAVA_LANGUAGE_VERSION := 1.8

include $(BUILD_CTS_SUPPORT_PACKAGE)
