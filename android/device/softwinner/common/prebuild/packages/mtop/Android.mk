LOCAL_PATH:=$(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE:=mtop
LOCAL_MODULE_TAGS:=option
LOCAL_SRC_FILES:=\
	mtop.c
LOCAL_SHARED_LIBRARIES := libcutils

include $(BUILD_EXECUTABLE)
