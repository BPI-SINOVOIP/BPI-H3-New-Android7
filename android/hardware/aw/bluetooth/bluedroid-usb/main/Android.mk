LOCAL_PATH:= $(call my-dir)

# Bluetooth main HW module / shared library for target
# ========================================================
include $(CLEAR_VARS)

# platform specific
LOCAL_SRC_FILES+= \
	bte_main.c \
	bte_init.c \
	bte_logmsg.c \
	bte_conf.c \
	stack_config.c


LOCAL_C_INCLUDES+= . \
	$(LOCAL_PATH)/../ \
	$(LOCAL_PATH)/../bta/include \
	$(LOCAL_PATH)/../bta/sys \
	$(LOCAL_PATH)/../bta/dm \
	$(LOCAL_PATH)/../btcore/include \
	$(LOCAL_PATH)/../include \
	$(LOCAL_PATH)/../stack/include \
	$(LOCAL_PATH)/../stack/l2cap \
	$(LOCAL_PATH)/../stack/a2dp \
	$(LOCAL_PATH)/../stack/btm \
	$(LOCAL_PATH)/../stack/avdt \
	$(LOCAL_PATH)/../hcis \
	$(LOCAL_PATH)/../hcis/include \
	$(LOCAL_PATH)/../hcis/patchram \
	$(LOCAL_PATH)/../udrv/include \
	$(LOCAL_PATH)/../btif/include \
	$(LOCAL_PATH)/../btif/co \
	$(LOCAL_PATH)/../hci/include\
	$(LOCAL_PATH)/../vnd/include \
	$(LOCAL_PATH)/../brcm/include \
	$(LOCAL_PATH)/../embdrv/sbc/encoder/include \
	$(LOCAL_PATH)/../embdrv/sbc/decoder/include \
	$(LOCAL_PATH)/../audio_a2dp_hw \
	$(LOCAL_PATH)/../utils/include \
	$(bluetooth_C_INCLUDES) \
	external/tinyxml2 \
	external/zlib

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libdl \
    liblog \
    libz \
    libpower \
    libprotobuf-cpp-full \
    libmedia \
    libutils \
    libchrome

LOCAL_STATIC_LIBRARIES := \
    libtinyxml2 \
    libbt-qcom_sbc_decoder-usb \
    libbt-qcom_sbc_encoder-usb

LOCAL_WHOLE_STATIC_LIBRARIES := \
    libbt-bta-usb \
    libbtdevice-usb \
    libbtif-usb \
    libbt-hci-usb \
    libbt-protos-usb \
    libbt-stack-usb \
    libbt-utils-usb \
    libbtcore-usb \
    libosi-usb \
    libudrv-usb \
    libusb

LOCAL_MODULE := bluetooth.usb.default
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := SHARED_LIBRARIES

#
# Shared library link options.
# References to global symbols and functions should bind to the library
# itself. This is to avoid issues with some of the unit/system tests
# that might link statically with some of the code in the library, and
# also dlopen(3) the shared library.
#
LOCAL_LDLIBS := -Wl,-Bsymbolic,-Bsymbolic-functions

LOCAL_REQUIRED_MODULES := \
    bt_did-usb.conf \
    bt_stack-usb.conf \
    libbt-hci-usb \
    libbt-vendor-usb \
    libusb

LOCAL_CFLAGS += $(bluetooth_CFLAGS) -DBUILDCFG
LOCAL_CONLYFLAGS += $(bluetooth_CONLYFLAGS)
LOCAL_CPPFLAGS += $(bluetooth_CPPFLAGS)

include $(BUILD_SHARED_LIBRARY)
