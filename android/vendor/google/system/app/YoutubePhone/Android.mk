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

#TODO: write a build template file to do this, and/or fold into multi_prebuilt.

LOCAL_PATH := $(my-dir)


###############################################################################
###############################################################################
# YoutubePhone.apk
my_archs := arm
my_src_arch := $(call get-prebuilt-src-arch, $(my_archs))
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := YoutubePhone
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_MODULE_TARGET_ARCH := arm
#LOCAL_OVERRIDES_PACKAGES :=
LOCAL_SRC_FILES := $(LOCAL_MODULE).apk
#LOCAL_REQUIRED_MODULES :=
LOCAL_PREBUILT_JNI_LIBS := \
    lib/arm/libambisonic_audio_renderer.so\
    lib/arm/libcronet.61.0.3142.0.so \
    lib/arm/libfilterframework_jni.so \
    lib/arm/libframesequence.so \
    lib/arm/libgvr.so \
    lib/arm/libluajit.so \
    lib/arm/libmoxie.so \
    lib/arm/libvpxJNI.so \
    lib/arm/libvpx.so \
    lib/arm/libwebp_android.so \
    lib/arm/libyoga.so
include $(BUILD_PREBUILT)
