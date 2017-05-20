LOCAL_PATH = hardware/realtek/bluetooth/firmware
PRODUCT_COPY_FILES += \
                $(LOCAL_PATH)/rtl8723a_fw:system/etc/firmware/rtl8723as_fw \
                $(LOCAL_PATH)/rtl8723a_config:system/etc/firmware/rtl8723as_config \
                $(LOCAL_PATH)/rtl8723b_fw:system/etc/firmware/rtl8723bs_fw \
                $(LOCAL_PATH)/rtl8723b_config:system/etc/firmware/rtl8723bs_config \
                $(LOCAL_PATH)/rtl8723b_VQ0_fw:system/etc/firmware/rtl8723bs_VQ0_fw \
                $(LOCAL_PATH)/rtl8723b_VQ0_config:system/etc/firmware/rtl8723bs_VQ0_config \
                $(LOCAL_PATH)/rtl8723cs_xx_fw:system/etc/firmware/rtl8723cs_xx_fw \
                $(LOCAL_PATH)/rtl8723cs_xx_config:system/etc/firmware/rtl8723cs_xx_config \
                $(LOCAL_PATH)/rtl8723cs_cg_fw:system/etc/firmware/rtl8723cs_cg_fw \
                $(LOCAL_PATH)/rtl8723cs_cg_config:system/etc/firmware/rtl8723cs_cg_config \
                $(LOCAL_PATH)/rtl8723cs_vf_fw:system/etc/firmware/rtl8723cs_vf_fw \
                $(LOCAL_PATH)/rtl8723cs_vf_config:system/etc/firmware/rtl8723cs_vf_config \
				$(LOCAL_PATH)/rtl8703b_fw:system/etc/firmware/rtl8703bs_fw \
                $(LOCAL_PATH)/rtl8703b_config:system/etc/firmware/rtl8703bs_config \
                $(TOP_DIR)device/softwinner/$(basename $(TARGET_DEVICE))/configs/bluetooth/rtkbt.conf:system/etc/bluetooth/rtkbt.conf \

