ifneq (,$(findstring $(TARGET_DEVICE),bpi-m64-lcd5))

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_CLANG := true
LOCAL_MODULE_TAGS := optional
LOCAL_C_INCLUDES += bootable/recovery
LOCAL_SRC_FILES := aw_ui.cpp

# should match TARGET_RECOVERY_UI_LIB set in BoardConfig.mk
LOCAL_MODULE := librecovery_ui_bpi_m64_lcd5

include $(BUILD_STATIC_LIBRARY)

endif
