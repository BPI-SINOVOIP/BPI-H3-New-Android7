LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    com_android_bluetooth_btservice_AdapterService.cpp \
    com_android_bluetooth_hfp.cpp \
    com_android_bluetooth_hfpclient.cpp \
    com_android_bluetooth_a2dp.cpp \
    com_android_bluetooth_a2dp_sink.cpp \
    com_android_bluetooth_avrcp.cpp \
    com_android_bluetooth_avrcp_controller.cpp \
    com_android_bluetooth_hid.cpp \
    com_android_bluetooth_hdp.cpp \
    com_android_bluetooth_pan.cpp \
    com_android_bluetooth_gatt.cpp \
    com_android_bluetooth_sdp.cpp

ifeq ($(BOARD_HAVE_BLUETOOTH_RTK),true)
LOCAL_CFLAGS += \
    -DBLUETOOTH_RTK \
    -DBLUETOOTH_RTK_API
LOCAL_SRC_FILES += \
    com_android_bluetooth_rtkbt.cpp
endif

LOCAL_C_INCLUDES += \
    $(JNI_H_INCLUDE) \
    $(TOP)/hardware/aw/bluetooth/libaw/include

LOCAL_SHARED_LIBRARIES := \
    libandroid_runtime \
    libnativehelper \
    libcutils \
    libutils \
    liblog \
    libhardware

LOCAL_STATIC_LIBRARIES += libbt-aw libwifi_hardware_info

LOCAL_MULTILIB := 32

LOCAL_CFLAGS += -Wall -Wextra -Wno-unused-parameter

LOCAL_MODULE := libbluetooth_jni
LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)
