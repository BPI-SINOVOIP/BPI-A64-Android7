LOCAL_PATH:= $(call my-dir)

ifeq ($(SECURE_OS_OPTEE), yes)
	include $(LOCAL_PATH)/adapt_optee/widevine_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL)/Android.mk
else
	include $(LOCAL_PATH)/adapt_semelis/widevine_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL)/Android.mk
endif
