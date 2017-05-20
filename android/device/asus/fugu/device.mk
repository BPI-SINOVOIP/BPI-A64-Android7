#
# Copyright 2013 The Android Open-Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

KERNEL_SRC_DIR ?= linux/kernel-fugu
KERNEL_CFG_NAME ?= fugu
TARGET_KERNEL_ARCH ?= x86_64


# Check for availability of kernel source
ifneq ($(wildcard $(KERNEL_SRC_DIR)/Makefile),)
  # Give precedence to TARGET_PREBUILT_KERNEL
  ifeq ($(TARGET_PREBUILT_KERNEL),)
    TARGET_KERNEL_BUILT_FROM_SOURCE := true
  endif
endif

ifneq ($(TARGET_KERNEL_BUILT_FROM_SOURCE), true)
# Use prebuilt kernel
ifeq ($(TARGET_PREBUILT_KERNEL),)
LOCAL_KERNEL := device/asus/fugu-kernel/bzImage
else
LOCAL_KERNEL := $(TARGET_PREBUILT_KERNEL)
endif

PRODUCT_COPY_FILES += \
    $(LOCAL_KERNEL):kernel

endif #TARGET_KERNEL_BUILT_FROM_SOURCE

# Need AppWidget permission to prevent from Launcher's crash.
# TODO(pattjin): Remove this when the TV Launcher is used, which does not support AppWidget.
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.app_widgets.xml:system/etc/permissions/android.software.app_widgets.xml

PRODUCT_AAPT_CONFIG := normal large xlarge hdpi xhdpi
PRODUCT_AAPT_PREF_CONFIG := xhdpi

# xhdpi, while we are hardcoding the 1080 resolution.
# when we start doing 720 as well, will need to stop hardcoding this.
PRODUCT_PROPERTY_OVERRIDES += \
    ro.sf.lcd_density=320

# There may be a cleaner way to do this.
PRODUCT_PROPERTY_OVERRIDES += \
    dalvik.vm.heapstartsize=8m \
    dalvik.vm.heapgrowthlimit=128m \
    dalvik.vm.heapsize=174m

$(call inherit-product-if-exists, frameworks/native/build/tablet-10in-xhdpi-2048-dalvik-heap.mk)

PRODUCT_CHARACTERISTICS := nosdcard,tv

DEVICE_PACKAGE_OVERLAYS := \
    device/asus/fugu/overlay

PRODUCT_COPY_FILES += \
    device/asus/fugu/fstab.fugu:root/fstab.fugu \
    device/asus/fugu/init.fugu.rc:root/init.fugu.rc \
    device/asus/fugu/init.fugu.usb.rc:root/init.fugu.usb.rc \
    device/asus/fugu/ueventd.fugu.rc:root/ueventd.fugu.rc \
    device/asus/fugu/init.recovery.fugu.rc:root/init.recovery.fugu.rc

# Audio
PRODUCT_PACKAGES += \
    libtinyalsa \
    audio.primary.fugu \
    audio.usb.default \
    audio.a2dp.default \
    audio.r_submix.default \
    libaudio-resampler

# http://b/15193147
# TODO(danalbert): Remove this once stlport is dead and gone.
PRODUCT_PACKAGES +=  libstlport

USE_CUSTOM_AUDIO_POLICY := 1

# specific management of audio_policy.conf
PRODUCT_COPY_FILES += \
    device/asus/fugu/audio_policy.conf:system/etc/audio_policy.conf

# Hdmi CEC: Fugu works as a playback device (4).
PRODUCT_PROPERTY_OVERRIDES += ro.hdmi.device_type=4

# Hdmi CEC: Disable 'Set Menu Language' feature.
PRODUCT_PROPERTY_OVERRIDES += ro.hdmi.set_menu_language=false

# Keep secure decoders in mediaserver process
PRODUCT_PROPERTY_OVERRIDES += media.stagefright.less-secure=true

# Boot Animation
PRODUCT_COPY_FILES += \
    device/asus/fugu/bootanimation-580-256col.zip:system/media/bootanimation.zip

# Bluetooth
PRODUCT_PACKAGES += \
    bt_bcm4354

PRODUCT_COPY_FILES += \
    device/asus/fugu/bt_vendor.conf:system/etc/bluetooth/bt_vendor.conf

# IMG graphics
PRODUCT_PACKAGES += \
    IMG_graphics \
    hwcomposer.moorefield

#Video
PRODUCT_COPY_FILES += \
    device/asus/fugu/media_profiles.xml:system/etc/media_profiles.xml \
    device/asus/fugu/wrs_omxil_components.list:system/etc/wrs_omxil_components.list \
    frameworks/av/media/libstagefright/data/media_codecs_google_audio.xml:system/etc/media_codecs_google_audio.xml \
    frameworks/av/media/libstagefright/data/media_codecs_google_tv.xml:system/etc/media_codecs_google_tv.xml \
    frameworks/av/media/libstagefright/data/media_codecs_google_video.xml:system/etc/media_codecs_google_video.xml \
    device/asus/fugu/media_codecs.xml:system/etc/media_codecs.xml \
    device/asus/fugu/media_codecs_performance.xml:system/etc/media_codecs_performance.xml \
    device/asus/fugu/mfx_omxil_core.conf:system/etc/mfx_omxil_core.conf \
    device/asus/fugu/video_isv_profile.xml:system/etc/video_isv_profile.xml \
    device/asus/fugu/codec_resources_limitation.xml:system/etc/codec_resources_limitation.xml


# psb video
PRODUCT_PACKAGES += \
    pvr_drv_video

# Media SDK and OMX IL components
PRODUCT_PACKAGES += \
    libmfxsw32 \
    libmfx_omx_core \
    libmfx_omx_components_sw \
    libgabi++-mfx \
    libstlport-mfx

#video firmware
PRODUCT_PACKAGES += \
    msvdx.bin.0008.0000.0000 \
    msvdx.bin.0008.0000.0001 \
    msvdx.bin.0008.0002.0001 \
    msvdx.bin.0008.0000.0002 \
    msvdx.bin.000c.0001.0001 \
    topaz.bin.0008.0000.0000 \
    topaz.bin.0008.0000.0001 \
    topaz.bin.0008.0000.0002 \
    topaz.bin.0008.0002.0001 \
    topaz.bin.000c.0001.0001 \
    vsp.bin.0008.0000.0000 \
    vsp.bin.0008.0000.0001 \
    vsp.bin.0008.0000.0002 \
    vsp.bin.0008.0002.0001 \
    vsp.bin.000c.0001.0001
# libva
PRODUCT_PACKAGES += \
    libva \
    libva-android \
    libva-tpi \
    vainfo

#libstagefrighthw
PRODUCT_PACKAGES += \
    libstagefrighthw

# libmix
PRODUCT_PACKAGES += \
    libmixvbp_mpeg4 \
    libmixvbp_h264 \
    libmixvbp_h264secure \
    libmixvbp_vc1 \
    libmixvbp_vp8 \
    libmixvbp_mpeg2 \
    libmixvbp \
    libva_videodecoder \
    libva_videoencoder

PRODUCT_PACKAGES += \
    libwrs_omxil_common \
    libwrs_omxil_core_pvwrapped \
    libOMXVideoDecoderAVC \
    libOMXVideoDecoderH263 \
    libOMXVideoDecoderMPEG4 \
    libOMXVideoDecoderWMV \
    libOMXVideoDecoderVP8 \
    libOMXVideoDecoderMPEG2 \
    libOMXVideoDecoderVP9HWR \
    libOMXVideoDecoderVP9Hybrid \
    libOMXVideoEncoderAVC \
    libOMXVideoEncoderH263 \
    libOMXVideoEncoderMPEG4 \
    libOMXVideoEncoderVP8

#libISV
PRODUCT_PACKAGES += libisv_omx_core

# pvr
PRODUCT_PACKAGES += \
    libpvr2d

# libdrm
PRODUCT_PACKAGES += \
    libdrm \
    dristat \
    drmstat

# Wifi
PRODUCT_PACKAGES += \
    libwpa_client \
    lib_driver_cmd_bcmdhd \
    hostapd \
    wpa_supplicant \
    bcmdhd.cal \
    bcmdhd_sr2.cal

PRODUCT_COPY_FILES += \
    device/asus/fugu/wpa_supplicant.conf:/system/etc/wifi/wpa_supplicant.conf

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.hardware.wifi.direct.xml:system/etc/permissions/android.hardware.wifi.direct.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:system/etc/permissions/android.hardware.bluetooth_le.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.usb.host.xml:system/etc/permissions/android.hardware.usb.host.xml \
    frameworks/native/data/etc/android.hardware.hdmi.cec.xml:system/etc/permissions/android.hardware.hdmi.cec.xml \
    frameworks/native/data/etc/android.software.midi.xml:system/etc/permissions/android.software.midi.xml

# Key layout files
PRODUCT_COPY_FILES += \
    device/asus/fugu/Nexus_Remote.idc:system/usr/idc/Nexus_Remote.idc \
    device/asus/fugu/gpio-keys.idc:system/usr/idc/gpio-keys.idc \
    device/asus/fugu/gpio-keys.kl:system/usr/keylayout/gpio-keys.kl \
    device/asus/fugu/gpio-keys.kcm:system/usr/keychars/gpio-keys.kcm \
    device/asus/fugu/Spike.kl:system/usr/keylayout/Spike.kl \
    device/asus/fugu/Nexus_Remote.kl:system/usr/keylayout/Nexus_Remote.kl

#GFX Config
PRODUCT_COPY_FILES += \
    device/asus/fugu/powervr.ini:system/etc/powervr.ini \
    frameworks/native/data/etc/android.hardware.vulkan.level-0.xml:system/etc/permissions/android.hardware.vulkan.level-0.xml \
    frameworks/native/data/etc/android.hardware.vulkan.version-1_0_3.xml:system/etc/permissions/android.hardware.vulkan.version-1_0_3.xml

# Thermal itux
ENABLE_ITUXD := true
PRODUCT_PACKAGES += \
    ituxd

# Power HAL
PRODUCT_PACKAGES += \
    power.fugu

# Debug rc files
ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
PRODUCT_COPY_FILES += \
    device/asus/fugu/init.fugu.diag.rc.userdebug:root/init.fugu.diag.rc
endif

# In userdebug, add minidebug info the the boot image and the system server to support
# diagnosing native crashes.
ifneq (,$(filter userdebug, $(TARGET_BUILD_VARIANT)))
    # Boot image.
    PRODUCT_DEX_PREOPT_BOOT_FLAGS += --generate-mini-debug-info
    # System server and some of its services. This is just here for completeness and consistency,
    # as we currently only compile the boot image.
    # Note: we cannot use PRODUCT_SYSTEM_SERVER_JARS, as it has not been expanded at this point.
    $(call add-product-dex-preopt-module-config,services,--generate-mini-debug-info)
    $(call add-product-dex-preopt-module-config,wifi-service,--generate-mini-debug-info)
endif

$(call inherit-product-if-exists, vendor/asus/fugu/device-vendor.mk)
$(call inherit-product-if-exists, vendor/intel/PRIVATE/fugu/device-vendor.mk)
$(call inherit-product-if-exists, vendor/intel/moorefield/prebuilts/houdini/houdini.mk)

# Add WiFi Firmware
$(call inherit-product-if-exists, hardware/broadcom/wlan/bcmdhd/firmware/bcm4354/device-bcm.mk)

# specific management of sep_policy.conf
PRODUCT_COPY_FILES += \
    device/asus/fugu/sep_policy.conf:system/etc/security/sep_policy.conf

#PRODUCT_CHARACTERISTICS := tablet

# Wifi country code
PRODUCT_COPY_FILES += \
    device/asus/fugu/init.fugu.countrycode.sh:system/bin/init.fugu.countrycode.sh

# Get rid of dex preoptimization to save space for the system.img
# Sorted by *.odex size
FUGU_DONT_DEXPREOPT_MODULES := \
    NoTouchAuthDelegate \
    ConfigUpdater \
    SecondScreenSetup \
    SecondScreenSetupAuthBridge \
    TvSettings \
    SetupWraith \
    GooglePackageInstaller \
    GoogleContactsSyncAdapter \
    BugReportSender \
    ContactsProvider \
    PrintSpooler \
    CalendarProvider \
    CanvasPackageInstaller \
    SettingsProvider \
    ituxd \
    StatementService \
    ExternalStorageProvider \
    FrameworkPackageStubs \
    CertInstaller \
    KeyChain \
    UserDictionaryProvider
$(call add-product-dex-preopt-module-config,$(FUGU_DONT_DEXPREOPT_MODULES),disable)

# Some CTS tests will be skipped based on what the initial API level that
# shipped on device was.
PRODUCT_PROPERTY_OVERRIDES += \
    ro.product.first_api_level=21
