# Copyright (C) 2011 The Android Open Source Project
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

#variables for NFC_NXP_CHIP_TYPE
PN547C2 := 1
PN548C2 := 2
PN551   := 3
NQ110 := $PN547C2
NQ120 := $PN547C2
NQ210 := $PN548C2
NQ220 := $PN548C2
#NXP PN547 Enable
ifeq ($(PN547C2),1)
D_CFLAGS += -DPN547C2=1
endif
ifeq ($(PN548C2),2)
D_CFLAGS += -DPN548C2=2
endif
ifeq ($(PN551),3)
D_CFLAGS += -DPN551=3
endif

#### Select the CHIP ####
ifeq ($(NXP_CHIP_TYPE),$(PN547C2))
D_CFLAGS += -DNFC_NXP_CHIP_TYPE=PN547C2
else ifeq ($(NXP_CHIP_TYPE),$(PN548C2))
D_CFLAGS += -DNFC_NXP_CHIP_TYPE=PN548C2
else ifeq ($(NXP_CHIP_TYPE),$(PN551))
D_CFLAGS += -DNFC_NXP_CHIP_TYPE=PN551
else
D_CFLAGS += -DNFC_NXP_CHIP_TYPE=PN551
endif
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := nfc_nci.$(TARGET_DEVICE)
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_SRC_FILES := $(call all-subdir-c-files)  $(call all-subdir-cpp-files)
LOCAL_SHARED_LIBRARIES := liblog libcutils libhardware_legacy libdl libhardware

LOCAL_CFLAGS := $(D_CFLAGS)
LOCAL_C_INCLUDES += \
    $(LOCAL_PATH)/utils \
    $(LOCAL_PATH)/inc \
    $(LOCAL_PATH)/common \
    $(LOCAL_PATH)/dnld \
    $(LOCAL_PATH)/hal \
    $(LOCAL_PATH)/log \
    $(LOCAL_PATH)/tml \
    $(LOCAL_PATH)/self-test

LOCAL_CFLAGS += -DANDROID \
        -DNXP_UICC_ENABLE -DNXP_HW_SELF_TEST
LOCAL_CFLAGS += -DNFC_NXP_HFO_SETTINGS=FALSE
#LOCAL_CFLAGS += -DFELICA_CLT_ENABLE

include $(BUILD_SHARED_LIBRARY)
