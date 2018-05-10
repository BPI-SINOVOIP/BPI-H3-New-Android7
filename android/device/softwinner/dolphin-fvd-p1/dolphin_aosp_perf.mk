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
$(call inherit-product-if-exists, vendor/dolby/ds1/dolby-product/dolby_cheepth_udc.mk)
PRODUCT_COPY_FILES += \
    device/softwinner/dolphin-fvd-p1/fstab.sun8iw7p1:root/fstab.sun8iw7p1 \
    device/softwinner/dolphin-fvd-p1/init.sun8iw7p1.rc:root/init.sun8iw7p1.rc

# Copy Camera cfg files
PRODUCT_COPY_FILES += \
	device/softwinner/dolphin-fvd-p1/configs/camera.cfg:system/etc/camera.cfg \
	device/softwinner/dolphin-fvd-p1/configs/cameralist.cfg:system/etc/cameralist.cfg \
	device/softwinner/dolphin-fvd-p1/configs/media_profiles.xml:system/etc/media_profiles.xml

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.freeform_window_management.xml:system/etc/permissions/android.software.freeform_window_management.xml \
    frameworks/native/data/etc/android.software.picture_in_picture.xml:system/etc/permissions/android.software.picture_in_picture.xml \
    frameworks/native/data/etc/android.hardware.type.television.xml:system/etc/permissions/android.hardware.type.television.xml \
    frameworks/native/data/etc/android.hardware.camera.xml:system/etc/permissions/android.hardware.camera.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.camera.autofocus.xml:system/etc/permissions/android.hardware.camera.autofocus.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:system/etc/permissions/android.hardware.bluetooth_le.xml

$(call inherit-product, build/target/product/full_base.mk)
$(call inherit-product-if-exists, vendor/fvd/products/fvd.mk)
$(call inherit-product-if-exists, vendor/fvd/products/products.mk)
$(call inherit-product, device/softwinner/dolphin-common/dolphin-common.mk)
$(call inherit-product-if-exists, device/softwinner/dolphin-fvd-p1/modules/modules.mk)
$(call inherit-product, device/softwinner/dolphin-fvd-p1/device.mk)
$(call inherit-product-if-exists, device/softwinner/common/rtkbt/rtkbt.mk)

PRODUCT_PACKAGE_OVERLAYS := device/softwinner/dolphin-fvd-p1/overlay \
                           $(DEVICE_PACKAGE_OVERLAYS)

# ########## DISPLAY CONFIGS BEGIN #############

#disable rotation
PRODUCT_PROPERTY_OVERRIDES += \
    ro.sf.disablerotation = 1
#support write external storage permission for third-party application 
PRODUCT_PROPERTY_OVERRIDES += \
	ro.config.writeexternalstorage=1


PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.usb.config=mtp,adb \
    ro.adb.secure=0 \
    config.disable_bluetooth=false


PRODUCT_PROPERTY_OVERRIDES += \
    ro.sf.lcd_density=320 \
    persist.sys.disp_density=320 \
    ro.hwc.sysrsl=9 \
    persist.sys.disp_enhance_mode=1 \
    persist.sys.disp_policy=3 \
    persist.sys.disp_init_exit=0 \
    persist.sys.disp_dev0=404 \
    persist.sys.disp_dev1=20B \
    persist.sys.hdmi_hpd=1 \
    persist.sys.hdmi_rvthpd=0 \
    persist.sys.cvbs_hpd=1 \
    persist.sys.cvbs_rvthpd=0 \
    persist.sys.disp_rsl_fex=0 \
    persist.sys.disp_margin_fex=0 \
    persist.sys.tv_vdid_fex=0

PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.timezone=Asia/Shanghai \
    persist.sys.country=CN \
    persist.sys.language=zh

#DISPLAY_INIT_POLICY is used in init_disp.c to choose display policy.
DISPLAY_INIT_POLICY := 3
DISP_DE := 20
DISP_DEV0 := 4
DISP_DEV1 := 2
HDMI_CHANNEL := 0
HDMI_DEFAULT_MODE := 4
CVBS_CHANNEL := 1
CVBS_DEFAULT_MODE := 11
#SHOW_INITLOGO := true
MAX_INIT_DISP_WIDTH := 1920
MAX_INIT_DISP_HEIGHT := 1080
SAVE_DISP_CONFIGS_TO_PROPERTY := true

PRODUCT_AAPT_CONFIG := large xlarge mdpi hdpi xhdpi

# ########## DISPLAY CONFIGS END ##############

PRODUCT_BRAND := Allwinner
PRODUCT_NAME := dolphin_aosp_perf
PRODUCT_DEVICE := dolphin-fvd-p1
PRODUCT_MODEL := dolphin
PRODUCT_MANUFACTURER := Allwinner

