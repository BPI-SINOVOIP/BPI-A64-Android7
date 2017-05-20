LOCAL_PATH := $(call my-dir)

ifeq ($(BOARD_HAVE_BLUETOOTH_RTK),true)

include $(call all-subdir-makefiles)

endif
