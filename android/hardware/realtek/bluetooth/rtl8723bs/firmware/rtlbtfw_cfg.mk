#
# Copyright (C) 2008 The Android Open Source Project
#
-include hardware/realtek/wlan/config/config.mk

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rtl8723b_fw:system/etc/firmware/rtl8723b_fw \
    $(LOCAL_PATH)/rtl8723b_config:system/etc/firmware/rtl8723b_config
########################
