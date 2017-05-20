LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
include $(LOCAL_PATH)/Makefile.sources

LOCAL_SRC_FILES := $(filter-out %.h,$(MODETEST_FILES))

LOCAL_MODULE := modetest

LOCAL_SHARED_LIBRARIES := libdrm
LOCAL_STATIC_LIBRARIES := libdrm_util

include $(BUILD_EXECUTABLE)
