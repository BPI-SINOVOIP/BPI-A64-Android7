LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := samples tests

# Only compile source java files in this apk.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4

LOCAL_PACKAGE_NAME := Support4Demos

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 4

LOCAL_DEX_PREOPT := false

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
