$(call inherit-product, device/softwinner/tulip-common/tulip_64_bit.mk)
$(call inherit-product, build/target/product/full_base.mk)
$(call inherit-product, device/softwinner/tulip-common/tulip-common.mk)
$(call inherit-product-if-exists, device/softwinner/bpi-m64-lcd5/modules/modules.mk)

BOARD_WIDEVINE_OEMCRYPTO_LEVEL := 3
PRODUCT_PROPERTY_OVERRIDES += \
    drm.service.enabled=true
DEVICE_PACKAGE_OVERLAYS := device/softwinner/bpi-m64-lcd5/overlay \
    $(DEVICE_PACKAGE_OVERLAYS)

PRODUCT_PACKAGES += Launcher3

#BPI-M64 Porting
PRODUCT_PACKAGES += \
    ESFileExplorer \
    VideoPlayer \
    Bluetooth 

	
#widevine
PRODUCT_PACKAGES += \
    com.google.widevine.software.drm.xml \
    com.google.widevine.software.drm \
    libdrmwvmplugin \
    libwvm \
    libWVStreamControlAPI_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL) \
    libwvdrm_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL) \
    libdrmdecrypt \
    libwvdrmengine

ifeq ($(BOARD_WIDEVINE_OEMCRYPTO_LEVEL), 1)
PRODUCT_PACKAGES += \
    liboemcrypto \
    libtee_client
endif

#   PartnerChromeCustomizationsProvider

PRODUCT_COPY_FILES += \
    device/softwinner/bpi-m64-lcd5/kernel:kernel \
    device/softwinner/bpi-m64-lcd5/fstab.sun50iw1p1:root/fstab.sun50iw1p1 \
    device/softwinner/bpi-m64-lcd5/init.sun50iw1p1.rc:root/init.sun50iw1p1.rc \
    device/softwinner/bpi-m64-lcd5/init.recovery.sun50iw1p1.rc:root/init.recovery.sun50iw1p1.rc \
    device/softwinner/bpi-m64-lcd5/ueventd.sun50iw1p1.rc:root/ueventd.sun50iw1p1.rc \
    device/softwinner/common/verity/rsa_key/verity_key:root/verity_key \
    device/softwinner/bpi-m64-lcd5/modules/modules/sunxi_tr.ko:root/sunxi_tr.ko \
    device/softwinner/bpi-m64-lcd5/modules/modules/gt9xxnew_ts.ko:recovery/root/gt9xxnew_ts.ko \

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.camera.xml:system/etc/permissions/android.hardware.camera.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.software.verified_boot.xml:system/etc/permissions/android.software.verified_boot.xml \
    frameworks/native/data/etc/android.hardware.ethernet.xml:system/etc/permissions/android.hardware.ethernet.xml

PRODUCT_COPY_FILES += \
    device/softwinner/bpi-m64-lcd5/configs/camera.cfg:system/etc/camera.cfg \
    device/softwinner/bpi-m64-lcd5/configs/gsensor.cfg:system/usr/gsensor.cfg \
    device/softwinner/bpi-m64-lcd5/configs/media_profiles.xml:system/etc/media_profiles.xml \
    device/softwinner/bpi-m64-lcd5/configs/sunxi-keyboard.kl:system/usr/keylayout/sunxi-keyboard.kl \
    device/softwinner/bpi-m64-lcd5/configs/gt9xxnew_ts.kl:system/usr/keylayout/gt9xxnew_ts.kl \
    device/softwinner/bpi-m64-lcd5/configs/axp81x-supplyer.kl:system/usr/keylayout/axp81x-supplyer.kl \
    device/softwinner/bpi-m64-lcd5/configs/sunxi_ir_recv.kl:system/usr/keylayout/sunxi_ir_recv.kl \
    device/softwinner/bpi-m64-lcd5/configs/tp.idc:system/usr/idc/tp.idc

PRODUCT_COPY_FILES += \
    device/softwinner/bpi-m64-lcd5/hawkview/sensor_list_cfg.ini:system/etc/hawkview/sensor_list_cfg.ini

# bootanimation
PRODUCT_COPY_FILES += \
    device/softwinner/bpi-m64-lcd5/media/bootanimation.zip:system/media/bootanimation.zip

# Radio Packages and Configuration Flie
$(call inherit-product, device/softwinner/common/rild/radio_common.mk)
#$(call inherit-product, device/softwinner/common/ril_modem/huawei/mu509/huawei_mu509.mk)
#$(call inherit-product, device/softwinner/common/ril_modem/Oviphone/em55/oviphone_em55.mk)

# Realtek wifi efuse map
#PRODUCT_COPY_FILES += \
#    device/softwinner/bpi-m64-lcd5/configs/wifi/wifi_efuse_8723bs-vq0.map:system/etc/wifi/wifi_efuse_8723bs-vq0.map


PRODUCT_PROPERTY_OVERRIDES += \
    ro.frp.pst=/dev/block/by-name/frp

# limit dex2oat threads to improve thermals
#PRODUCT_PROPERTY_OVERRIDES += \
#    dalvik.vm.boot-dex2oat-threads=4 \
#    dalvik.vm.dex2oat-threads=3 \
#    dalvik.vm.image-dex2oat-threads=4

#PRODUCT_PROPERTY_OVERRIDES += \
#    dalvik.vm.dex2oat-flags=--no-watch-dog \
#    dalvik.vm.jit.codecachesize=0 \
#    ro.am.reschedule_service=true

PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.usb.config=mtp,adb \
    ro.adb.secure=0 \
    rw.logger=0 \

PRODUCT_PROPERTY_OVERRIDES += \
    ro.product.first_api_level=24

PRODUCT_PROPERTY_OVERRIDES += \
    dalvik.vm.heapsize=384m \
    dalvik.vm.heapstartsize=8m \
    dalvik.vm.heapgrowthlimit=80m \
    dalvik.vm.heaptargetutilization=0.75 \
    dalvik.vm.heapminfree=512k \
    dalvik.vm.heapmaxfree=8m \
    ro.zygote.disable_gl_preload=false

# scense_control
PRODUCT_PROPERTY_OVERRIDES += \
    sys.p_bootcomplete= true \
    sys.p_debug=false

PRODUCT_PROPERTY_OVERRIDES += \
    ro.sf.lcd_density=213

PRODUCT_PROPERTY_OVERRIDES += \
    ro.spk_dul.used=false

#BPI-M64 Porting
PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.timezone=Asia/Taipei \
    persist.sys.language=EN \
    persist.sys.country=US

# stoarge
PRODUCT_PROPERTY_OVERRIDES += \
    persist.fw.force_adoptable=true

PRODUCT_CHARACTERISTICS := tablet

PRODUCT_AAPT_CONFIG := tvdpi xlarge hdpi xhdpi large
PRODUCT_AAPT_PREF_CONFIG := tvdpi

$(call inherit-product-if-exists, vendor/google/products/gms_base.mk)

PRODUCT_BRAND := Allwinner
PRODUCT_NAME := bpi_m64_lcd5
PRODUCT_DEVICE := bpi-m64-lcd5
PRODUCT_MODEL := BPI M64
PRODUCT_MANUFACTURER := Sinovoip
