LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

include $(LOCAL_PATH)/../../config.mk
CEDARC = $(TOP)/frameworks/av/media/libcedarc/
CEDARX = $(LOCAL_PATH)/../../
################################################################################
## set flags for golobal compile and link setting.
################################################################################

CONFIG_FOR_COMPILE =
CONFIG_FOR_LINK =

LOCAL_CFLAGS += $(CONFIG_FOR_COMPILE)
LOCAL_MODULE_TAGS := optional

## set the include path for compile flags.
LOCAL_SRC_FILES:= $(notdir $(wildcard $(LOCAL_PATH)/*.c))

LOCAL_C_INCLUDES := $(SourcePath)                             \
                    $(LOCAL_PATH)/../../                      \
                    $(CEDARX)/libcore/base/include                   \
                    $(CEDARX)/libcore/include           \

LOCAL_SHARED_LIBRARIES := \
            libcutils       \
            libutils        \
            libcdx_base     \

#LOCAL_32_BIT_ONLY := true
LOCAL_MODULE:= demomessage

include $(BUILD_EXECUTABLE)
