LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v13
#LOCAL_STATIC_JAVA_LIBRARIES := libsysapi
LOCAL_SRC_FILES := $(call all-java-files-under, src) \

#for A20
ifeq (A20, $(SW_CHIP_PLATFORM))
	LOCAL_SRC_FILES += $(call all-java-files-under, platform/A20)
endif
#for A10
ifeq (A10, $(SW_CHIP_PLATFORM))
	LOCAL_SRC_FILES += $(call all-java-files-under, platform/A10)
endif
#for A31
ifeq (A31, $(SW_CHIP_PLATFORM))
	LOCAL_SRC_FILES += $(call all-java-files-under, platform/A31)
endif
#for A80
ifeq (A80, $(SW_CHIP_PLATFORM))
	LOCAL_SRC_FILES += $(call all-java-files-under, platform/A80)
endif
#for H8
ifeq (H8, $(SW_CHIP_PLATFORM))
	LOCAL_SRC_FILES += $(call all-java-files-under, platform/H8)
endif

#for H3
ifeq (H3, $(SW_CHIP_PLATFORM))
	LOCAL_SRC_FILES += $(call all-java-files-under, platform/AndroidN)
endif

ifeq (H64, $(SW_CHIP_PLATFORM))
        LOCAL_SRC_FILES += $(call all-java-files-under, platform/H64)
endif

ifeq (H5, $(SW_CHIP_PLATFORM))
        LOCAL_SRC_FILES += $(call all-java-files-under, platform/H6)
endif

ifeq (H6, $(SW_CHIP_PLATFORM))
        LOCAL_SRC_FILES += $(call all-java-files-under, platform/H6)
endif

LOCAL_PACKAGE_NAME := DragonBox

# We mark this out until Mtp and MediaMetadataRetriever is unhidden.
#LOCAL_SDK_VERSION := current
LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_FLAG_FILES := proguard.cfg
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_JAVA_LIBRARIES:= org.apache.http.legacy.boot

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))

#include $(CLEAR_VARS)
#LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := libsysapi:libs/sysapi.jar
#include $(BUILD_MULTI_PREBUILT)
$(warning $(PLATFORM_HARDWARE))
$(warning $(PLATFORM_SDK_VERSION))
