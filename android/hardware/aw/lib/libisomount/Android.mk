LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=               \
    IISOMountManagerService.cpp      \
    ISOMountManagerService.cpp


LOCAL_SHARED_LIBRARIES :=     		\
	libcutils             			\
	libutils              			\
	libbinder             			\
	libandroid_runtime

LOCAL_C_INCLUDES += \
	hardware/aw/include

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE:= libisomountmanagerservice

LOCAL_PRELINK_MODULE:= false

include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_SRC_FILES:= isomountmanager_test.cpp
LOCAL_SHARED_LIBRARIES:= libutils libbinder libisomountmanagerservice

LOCAL_C_INCLUDES += \
	hardware/aw/include

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE:= isomountmanager_test
include $(BUILD_EXECUTABLE)
