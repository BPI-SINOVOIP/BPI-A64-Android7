LOCAL_PATH := $(call my-dir)

ifneq ($(BOARD_HAVE_BLUETOOTH_RTK),)

include $(CLEAR_VARS)

BDROID_DIR := $(TOP_DIR)system/bt

LOCAL_SRC_FILES := \
        src/bt_vendor_rtk.c \
        src/hardware.c \
        src/userial_vendor.c \
        src/upio.c

LOCAL_C_INCLUDES += \
        $(LOCAL_PATH)/include \
        $(BDROID_DIR)/hci/include \
        $(BOARD_BLUETOOTH_BDROID_BUILDCFG_INCLUDE_DIR)/uart

LOCAL_SHARED_LIBRARIES := \
        libcutils

ifeq ($(BOARD_HAVE_BLUETOOTH_NAME), rtl8723bs)
LOCAL_CFLAGS += -DRTL_8723BS_BT_USED
endif

ifeq ($(BOARD_HAVE_BLUETOOTH_NAME), rtl8723bs_vq0)
LOCAL_CFLAGS += -DRTL_8723BS_VQ0_BT_USED
endif
LOCAL_MODULE := libbt-vendor_uart
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_OWNER := realtek
LOCAL_PROPRIETARY_MODULE := true

include $(BUILD_SHARED_LIBRARY)

endif # BOARD_HAVE_BLUETOOTH_RTK
