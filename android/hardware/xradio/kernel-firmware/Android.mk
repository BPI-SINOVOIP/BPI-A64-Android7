LOCAL_PATH := $(call my-dir)
THIS_DIR := $(LOCAL_PATH)
THIS_MODULE_PATH := $(PRODUCT_OUT)/system/etc/firmware

include $(CLEAR_VARS)

include $(THIS_DIR)/Makefile.common

LOCAL_MODULE := kernel-firmware
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(THIS_MODULE_PATH)

THIS_INTERMEDIATES := $(call local-intermediates-dir)
$(shell mkdir -p $(THIS_INTERMEDIATES))
$(shell touch $(THIS_INTERMEDIATES)/$(LOCAL_MODULE))

include $(BUILD_PREBUILT)

$(LOCAL_BUILT_MODULE): $(addprefix $(THIS_MODULE_PATH)/, $(FILES))
$(THIS_MODULE_PATH)/%: $(THIS_DIR)/% | $(ACP)
	$(transform-prebuilt-to-target)

clean-force-kernel-firmware: clean-kernel-firmware
	@ rm -rf $(addprefix $(THIS_MODULE_PATH)/, $(FILES))
	@ rm -rf $(PRODUCT_OUT)/obj/ETC/kernel-firmware_intermediates

clean: clean-force-kernel-firmware
