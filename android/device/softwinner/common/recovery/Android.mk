LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_CLANG := true
LOCAL_CFLAGS += -Wall
ifneq ($(filter tulip%,$(TARGET_BOARD_PLATFORM)),)
LOCAL_CFLAGS += -D__A64__
endif
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := librecovery_updater_common
LOCAL_SRC_FILES := recovery_updater.cpp \
	BurnBoot.cpp \
	BurnNandBoot.cpp \
	BurnSdBoot.cpp \
	Utils.cpp
LOCAL_C_INCLUDES += bootable/recovery
LOCAL_C_INCLUDES += \
	external/zlib \
	external/safe-iop/include \
	external/selinux/libselinux/include

include $(BUILD_STATIC_LIBRARY)
