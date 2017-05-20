LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    MediaResourceArbitrator.cpp

LOCAL_SHARED_LIBRARIES := \
    libutils \
    libcutils \
    libexpat \
    libdl \


LOCAL_C_INCLUDES := \
    $(TARGET_OUT_HEADERS)/khronos/openmax \
    $(call include-path-for, frameworks-native)/media/openmax


LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libmrm_arbitrator

#LOCAL_CFLAGS += -Werror

include $(BUILD_SHARED_LIBRARY)
