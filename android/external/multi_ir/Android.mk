LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES:= multiir.c virtual_input.c virtual_input.h multi_ir.h uevent_utils.c
LOCAL_MODULE := multi_ir
LOCAL_MODULE_TAGS := optional
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_STATIC_LIBRARIES := libcutils libc liblog
LOCAL_CFLAGS:= -DLOG_TAG=\"multiir\"
LOCAL_INIT_RC := multi_ir.rc
include $(BUILD_EXECUTABLE)

