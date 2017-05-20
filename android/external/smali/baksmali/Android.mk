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

LOCAL_PATH := $(call my-dir)

# build baksmali jar
# ============================================================

include $(CLEAR_VARS)

LOCAL_MODULE := baksmalilib

LOCAL_MODULE_TAGS := optional

#LOCAL_MODULE_CLASS and LOCAL_IS_HOST_MODULE must be defined before calling $(local-intermediates-dir)
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_IS_HOST_MODULE := true

intermediates := $(call local-intermediates-dir,COMMON)

LOCAL_SRC_FILES := \
	$(call all-java-files-under, src/main/java) \
	$(call all-java-files-under, ../util/src/main/java)

LOCAL_JAR_MANIFEST := manifest.txt

LOCAL_STATIC_JAVA_LIBRARIES := \
	dexlib2

#read in the version number
BAKSMALI_VERSION := $(shell cat $(LOCAL_PATH)/../build.gradle | \
    grep -o -e "^version = '\(.*\)'" | grep -o -e "[0-9.]\+")

BAKSMALI_VERSION := $(BAKSMALI_VERSION)-aosp

#create a new baksmali.properties file using the correct version
$(intermediates)/resources/baksmali.properties:
	$(hide) mkdir -p $(dir $@)
	$(hide) echo "application.version=$(BAKSMALI_VERSION)" > $@

LOCAL_JAVA_RESOURCE_FILES := $(intermediates)/resources/baksmali.properties

include $(BUILD_HOST_JAVA_LIBRARY)



# copy baksmali script
# ============================================================

include $(CLEAR_VARS)
LOCAL_IS_HOST_MODULE := true
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_MODULE := baksmali
LOCAL_SRC_FILES := ../scripts/baksmali
LOCAL_REQUIRED_MODULES := baksmalilib
include $(BUILD_PREBUILT)
