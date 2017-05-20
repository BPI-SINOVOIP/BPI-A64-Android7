LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../effects/audio_3d_surround

LOCAL_SRC_FILES := \
	audio_a2dp_hw.c \
	../effects/audio_3d_surround/audio_3d_surround.c


LOCAL_CFLAGS += -std=c99 $(bdroid_CFLAGS)

LOCAL_MODULE := audio.a2dp.$(TARGET_BOARD_PLATFORM)
LOCAL_MODULE_RELATIVE_PATH := hw

LOCAL_STATIC_LIBRARIES += libaw_audio3dsur

LOCAL_SHARED_LIBRARIES := libcutils liblog

LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)
