$(call inherit-product, device/softwinner/tulip-common/tulip_64_bit.mk)
$(call inherit-product, build/target/product/full_base.mk)
$(call inherit-product, device/softwinner/tulip-common/tulip-common.mk)
$(call inherit-product-if-exists, device/softwinner/tulip-p3/modules/modules.mk)


PRODUCT_PROPERTY_OVERRIDES += \
    drm.service.enabled=true
DEVICE_PACKAGE_OVERLAYS := device/softwinner/tulip-p3/overlay \
                           $(DEVICE_PACKAGE_OVERLAYS)

PRODUCT_PACKAGES += Launcher3

PRODUCT_PACKAGES += \
    WallpaperPicker \
    ESFileExplorer \
    VideoPlayer \
    Saturn \
    Bluetooth \
    Update
	
#   PartnerChromeCustomizationsProvider

PRODUCT_COPY_FILES += \
    device/softwinner/tulip-p3/kernel:kernel \
    device/softwinner/tulip-p3/fstab.sun50iw1p1:root/fstab.sun50iw1p1 \
    device/softwinner/tulip-p3/init.sun50iw1p1.rc:root/init.sun50iw1p1.rc \
    device/softwinner/tulip-p3/init.recovery.sun50iw1p1.rc:root/init.recovery.sun50iw1p1.rc \
    device/softwinner/tulip-p3/ueventd.sun50iw1p1.rc:root/ueventd.sun50iw1p1.rc \
    device/softwinner/common/verity/rsa_key/verity_key:root/verity_key \
    device/softwinner/tulip-p3/modules/modules/nand.ko:root/nand.ko \
    device/softwinner/tulip-p3/modules/modules/sunxi_tr.ko:root/sunxi_tr.ko \
    device/softwinner/tulip-p3/modules/modules/disp.ko:root/disp.ko \
    device/softwinner/tulip-p3/modules/modules/sunxi-keyboard.ko:recovery/root/sunxi-keyboard.ko \
    device/softwinner/tulip-p3/modules/modules/sw-device.ko:recovery/root/sw-device.ko \
    device/softwinner/tulip-p3/modules/modules/gslX680new.ko:recovery/root/gslX680new.ko \

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.camera.xml:system/etc/permissions/android.hardware.camera.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.software.verified_boot.xml:system/etc/permissions/android.software.verified_boot.xml \
    frameworks/native/data/etc/android.hardware.ethernet.xml:system/etc/permissions/android.hardware.ethernet.xml

PRODUCT_COPY_FILES += \
    device/softwinner/tulip-p3/configs/camera.cfg:system/etc/camera.cfg \
    device/softwinner/tulip-p3/configs/gsensor.cfg:system/usr/gsensor.cfg \
    device/softwinner/tulip-p3/configs/media_profiles.xml:system/etc/media_profiles.xml \
    device/softwinner/tulip-p3/configs/sunxi-keyboard.kl:system/usr/keylayout/sunxi-keyboard.kl \
    device/softwinner/tulip-p3/configs/tp.idc:system/usr/idc/tp.idc

PRODUCT_COPY_FILES += \
    device/softwinner/tulip-p3/hawkview/sensor_list_cfg.ini:system/etc/hawkview/sensor_list_cfg.ini

# bootanimation
PRODUCT_COPY_FILES += \
    device/softwinner/tulip-p3/media/bootanimation.zip:system/media/bootanimation.zip

# Radio Packages and Configuration Flie
$(call inherit-product, device/softwinner/common/rild/radio_common.mk)
#$(call inherit-product, device/softwinner/common/ril_modem/huawei/mu509/huawei_mu509.mk)
#$(call inherit-product, device/softwinner/common/ril_modem/Oviphone/em55/oviphone_em55.mk)

# Realtek wifi efuse map
PRODUCT_COPY_FILES += \
    device/softwinner/tulip-p3/wifi_efuse_8723bs-vq0.map:system/etc/wifi/wifi_efuse_8723bs-vq0.map


PRODUCT_PROPERTY_OVERRIDES += \
	ro.frp.pst=/dev/block/by-name/frp

# limit dex2oat threads to improve thermals
PRODUCT_PROPERTY_OVERRIDES += \
    dalvik.vm.boot-dex2oat-threads=4 \
    dalvik.vm.dex2oat-threads=3 \
    dalvik.vm.image-dex2oat-threads=4

PRODUCT_PROPERTY_OVERRIDES += \
	dalvik.vm.dex2oat-flags=--no-watch-dog \
	dalvik.vm.jit.codecachesize=0 \
	ro.am.reschedule_service=true

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

PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.timezone=Asia/Shanghai \
    persist.sys.country=Hans-CN \
    persist.sys.language=zh

# stoarge
PRODUCT_PROPERTY_OVERRIDES += \
    persist.fw.force_adoptable=true

PRODUCT_CHARACTERISTICS := tablet

PRODUCT_AAPT_CONFIG := tvdpi xlarge hdpi xhdpi large
PRODUCT_AAPT_PREF_CONFIG := tvdpi

$(call inherit-product-if-exists, vendor/google/products/gms_base.mk)

PRODUCT_BRAND := Allwinner
PRODUCT_NAME := tulip_p3
PRODUCT_DEVICE := tulip-p3
PRODUCT_MODEL := QUAD-CORE A64 p3
PRODUCT_MANUFACTURER := Allwinner
