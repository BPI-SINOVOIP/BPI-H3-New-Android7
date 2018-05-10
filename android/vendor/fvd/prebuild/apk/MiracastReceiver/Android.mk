#
# Copyright (C) 2008 Google Inc.
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

###############################################################################
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TARGET_ARCH := arm

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED

LOCAL_MODULE := MiracastReceiver

LOCAL_SRC_FILES := $(LOCAL_MODULE).apk

LOCAL_PREBUILT_JNI_LIBS := \
    lib/arm/libjni_WFDisplay.so \
    lib/arm/libwfdmanager.so \
    lib/arm/libwfdrtsp.so \
    lib/arm/libwfdplayer.so \
    lib/arm/libwfdutils.so

include $(BUILD_PREBUILT)
