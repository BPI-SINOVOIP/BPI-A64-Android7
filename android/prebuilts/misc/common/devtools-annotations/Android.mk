LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := devtools-annotations-prebuilt
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := devtools-annotations-prebuilt.jar
LOCAL_IS_HOST_MODULE := true
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_BUILT_MODULE_STEM := javalib.jar
LOCAL_MODULE_SUFFIX := $(COMMON_JAVA_PACKAGE_SUFFIX)

include $(BUILD_PREBUILT)
