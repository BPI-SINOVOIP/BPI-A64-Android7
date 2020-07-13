LOCAL_PATH := $(call my-dir)

ifeq ($(BOARD_WIFI_VENDOR), xr_wlan)
	include $(call all-makefiles-under,$(LOCAL_PATH))
endif
