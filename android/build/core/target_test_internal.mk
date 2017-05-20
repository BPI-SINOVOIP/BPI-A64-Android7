#######################################################
## Shared definitions for all target test compilations.
#######################################################

LOCAL_CFLAGS += -DGTEST_OS_LINUX_ANDROID -DGTEST_HAS_STD_STRING

LOCAL_C_INCLUDES += external/gtest/include

ifndef LOCAL_SDK_VERSION
LOCAL_STATIC_LIBRARIES += libgtest_main libgtest
else
LOCAL_STATIC_LIBRARIES += libgtest_main_ndk libgtest_ndk
endif

ifdef LOCAL_MODULE_PATH
$(error $(LOCAL_PATH): Do not set LOCAL_MODULE_PATH when building test $(LOCAL_MODULE))
endif

ifdef LOCAL_MODULE_PATH_32
$(error $(LOCAL_PATH): Do not set LOCAL_MODULE_PATH_32 when building test $(LOCAL_MODULE))
endif

ifdef LOCAL_MODULE_PATH_64
$(error $(LOCAL_PATH): Do not set LOCAL_MODULE_PATH_64 when building test $(LOCAL_MODULE))
endif

LOCAL_MODULE_PATH_64 := $(TARGET_OUT_DATA_NATIVE_TESTS)/$(LOCAL_MODULE)
LOCAL_MODULE_PATH_32 := $($(TARGET_2ND_ARCH_VAR_PREFIX)TARGET_OUT_DATA_NATIVE_TESTS)/$(LOCAL_MODULE)
