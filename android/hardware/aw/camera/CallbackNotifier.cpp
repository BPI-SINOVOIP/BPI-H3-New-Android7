/*
 * Copyright (c) 2008-2015 Allwinner Technology Co. Ltd.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "CameraDebug.h"
#if DBG_CALLBACK
#define LOG_NDEBUG 0
#endif
#define LOG_TAG "CallbackNotifier"
#include <cutils/log.h>

#include <cutils/properties.h>
#include <HardwareAPI.h>

#include "V4L2CameraDevice2.h"
#include "CallbackNotifier.h"
#include "vencoder.h"
#include "MetadataBufferType.h"

extern "C" int scaler(unsigned char * psrc,
                      unsigned char * pdst,
                      int src_w,
                      int src_h,
                      int dst_w,
                      int dst_h,
                      int fmt,
                      int align);

namespace android {

/* String representation of camera messages. */
static const char* lCameraMessages[] =
{
    "CAMERA_MSG_ERROR",
    "CAMERA_MSG_SHUTTER",
    "CAMERA_MSG_FOCUS",
    "CAMERA_MSG_ZOOM",
    "CAMERA_MSG_PREVIEW_FRAME",
    "CAMERA_MSG_VIDEO_FRAME",
    "CAMERA_MSG_POSTVIEW_FRAME",
    "CAMERA_MSG_RAW_IMAGE",
    "CAMERA_MSG_COMPRESSED_IMAGE",
    "CAMERA_MSG_RAW_IMAGE_NOTIFY",
    "CAMERA_MSG_PREVIEW_METADATA",
    "CAMERA_MSG_CONTINUOUSSNAP",
    "CAMERA_MSG_SNAP"
    "CAMERA_MSG_SNAP_THUMB"
    "CAMERA_MSG_SNAP_FD"
};
static const int lCameraMessagesNum = sizeof(lCameraMessages) / sizeof(char*);

/* Builds an array of strings for the given set of messages.
 * Param:
 *  msg - Messages to get strings for,
 *  strings - Array where to save strings
 *  max - Maximum number of entries in the array.
 * Return:
 *  Number of strings saved into the 'strings' array.
 */
static int GetMessageStrings(uint32_t msg, const char** strings, int max)
{
    int index = 0;
    int out = 0;
    while (msg != 0 && out < max && index < lCameraMessagesNum) {
        while ((msg & 0x1) == 0 && index < lCameraMessagesNum) {
            msg >>= 1;
            index++;
        }
        if ((msg & 0x1) != 0 && index < lCameraMessagesNum) {
            strings[out] = lCameraMessages[index];
            out++;
            msg >>= 1;
            index++;
        }
    }

    return out;
}

/* Logs messages, enabled by the mask. */
#if DEBUG_MSG
static void PrintMessages(uint32_t msg)
{
    const char* strs[lCameraMessagesNum];
    const int translated = GetMessageStrings(msg, strs, lCameraMessagesNum);
    for (int n = 0; n < translated; n++) {
        LOGV("    %s", strs[n]);
    }
}
#else
#define PrintMessages(msg)   (void(0))
#endif

/*static void formatToNV21(void *dst,
                       void *src,
                       int width,
                       int height,
                       size_t stride,
                       uint32_t offset,
                       unsigned int bytesPerPixel,
                       size_t length,
                       int pixelFormat)
{
    unsigned int alignedRow, row;
    unsigned char *bufferDst, *bufferSrc;
    unsigned char *bufferDstEnd, *bufferSrcEnd;
    uint16_t *bufferSrc_UV;

    unsigned int y_uv[2];
    y_uv[0] = (unsigned int)src;
    y_uv[1] = (unsigned int)src + width*height;

    // NV12 -> NV21
    if (pixelFormat == V4L2_PIX_FMT_NV12) {
        bytesPerPixel = 1;
        bufferDst = ( unsigned char * ) dst;
        bufferDstEnd = ( unsigned char * ) dst + width*height*bytesPerPixel;
        bufferSrc = ( unsigned char * ) y_uv[0] + offset;
        bufferSrcEnd = ( unsigned char * ) ( ( size_t ) y_uv[0] + length + offset);
        row = width*bytesPerPixel;
        alignedRow = stride-width;
        int stride_bytes = stride / 8;
        uint32_t xOff = offset % stride;
        uint32_t yOff = offset / stride;

        // going to convert from NV12 here and return
        // Step 1: Y plane: iterate through each row and copy
        for ( int i = 0 ; i < height ; i++) {
            memcpy(bufferDst, bufferSrc, row);
            bufferSrc += stride;
            bufferDst += row;
            if ( ( bufferSrc > bufferSrcEnd ) || ( bufferDst > bufferDstEnd ) ) {
                break;
            }
        }

        bufferSrc_UV = ( uint16_t * ) ((uint8_t*)y_uv[1] + (stride/2)*yOff + xOff);

        uint16_t *bufferDst_UV;

        // Step 2: UV plane: convert NV12 to NV21 by swapping U & V
        bufferDst_UV = (uint16_t *) (((uint8_t*)dst)+row*height);

        for (int i = 0 ; i < height/2 ; i++, bufferSrc_UV += alignedRow/2) {
            int n = width;
            asm volatile (
            "   pld [%[src], %[src_stride], lsl #2]                         \n\t"
            "   cmp %[n], #32                                               \n\t"
            "   blt 1f                                                      \n\t"
            "0: @ 32 byte swap                                              \n\t"
            "   sub %[n], %[n], #32                                         \n\t"
            "   vld2.8  {q0, q1} , [%[src]]!                                \n\t"
            "   vswp q0, q1                                                 \n\t"
            "   cmp %[n], #32                                               \n\t"
            "   vst2.8  {q0,q1},[%[dst]]!                                   \n\t"
            "   bge 0b                                                      \n\t"
            "1: @ Is there enough data?                                     \n\t"
            "   cmp %[n], #16                                               \n\t"
            "   blt 3f                                                      \n\t"
            "2: @ 16 byte swap                                              \n\t"
            "   sub %[n], %[n], #16                                         \n\t"
            "   vld2.8  {d0, d1} , [%[src]]!                                \n\t"
            "   vswp d0, d1                                                 \n\t"
            "   cmp %[n], #16                                               \n\t"
            "   vst2.8  {d0,d1},[%[dst]]!                                   \n\t"
            "   bge 2b                                                      \n\t"
            "3: @ Is there enough data?                                     \n\t"
            "   cmp %[n], #8                                                \n\t"
            "   blt 5f                                                      \n\t"
            "4: @ 8 byte swap                                               \n\t"
            "   sub %[n], %[n], #8                                          \n\t"
            "   vld2.8  {d0, d1} , [%[src]]!                                \n\t"
            "   vswp d0, d1                                                 \n\t"
            "   cmp %[n], #8                                                \n\t"
            "   vst2.8  {d0[0],d1[0]},[%[dst]]!                             \n\t"
            "   bge 4b                                                      \n\t"
            "5: @ end                                                       \n\t"
#ifdef NEEDS_ARM_ERRATA_754319_754320
            "   vmov s0,s0  @ add noop for errata item                      \n\t"
#endif
            : [dst] "+r" (bufferDst_UV), [src] "+r" (bufferSrc_UV), [n] "+r" (n)
            : [src_stride] "r" (stride_bytes)
            : "cc", "memory", "q0", "q1"
            );
        }
    }
}*/

static void NV12ToYVU420(void* psrc, void* pdst, int width, int height)
{
    uint8_t* psrc_y = (uint8_t*)psrc;
    uint8_t* pdst_y = (uint8_t*)pdst;
    uint8_t* psrc_uv = (uint8_t*)psrc + width * height;
    uint8_t* pdst_uv = (uint8_t*)pdst + width * height;

    for(int i = 0; i < height; i++)
    {
        for (int j = 0; j < width; j++)
        {
            *(uint8_t*)(pdst_y + i * width + j) = *(uint8_t*)(psrc_y + i * width + j);
        }
    }

    for(int i = 0; i < height / 2; i++)
    {
        for (int j = 0; j < width / 2; j++)
        {
            *(uint8_t*)(pdst_uv + i * width / 2 + j) =
                *(uint8_t*)(psrc_uv + i * width + j * 2);
            *(uint8_t*)(pdst_uv + (width / 2) * (height / 2) + i * width / 2 + j) =
                *(uint8_t*)(psrc_uv + i * width + j * 2 + 1);
        }
    }

}

static void NV21ToYVU420(void* psrc, void* pdst, int width, int height)
{
    uint8_t* psrc_y = (uint8_t*)psrc;
    uint8_t* pdst_y = (uint8_t*)pdst;
    uint8_t* psrc_uv = (uint8_t*)psrc + width * height;
    uint8_t* pdst_uv = (uint8_t*)pdst + width * height;

    for(int i = 0; i < height; i++)
    {
        for (int j = 0; j < width; j++)
        {
            *(uint8_t*)(pdst_y + i * width + j) = *(uint8_t*)(psrc_y + i * width + j);
        }
    }
    for(int i = 0; i < height / 2; i++)
    {
        for (int j = 0; j < width / 2; j++)
        {
            *(uint8_t*)(pdst_uv + i * width / 2 + j) =
                *(uint8_t*)(psrc_uv + i * width + j * 2 + 1);
            *(uint8_t*)(pdst_uv + (width / 2) * (height / 2) + i * width / 2 + j) =
                *(uint8_t*)(psrc_uv + i * width + j * 2);
        }
    }
}

static void YVU420ToNV21(void* psrc, void* pdst, int width, int height)
{
    uint8_t* psrc_y = (uint8_t*)psrc;
    uint8_t* pdst_y = (uint8_t*)pdst;
    uint8_t* psrc_uv = (uint8_t*)psrc + width * height;
    uint8_t* pdst_uv = (uint8_t*)pdst + width * height;

    for(int i = 0; i < height; i++)
    {
        for (int j = 0; j < width; j++)
        {
            *(uint8_t*)(pdst_y + i * width + j) = *(uint8_t*)(psrc_y + i * width + j);
        }
    }

    for(int i = 0; i < height / 2; i++)
    {
        for (int j = 0; j < width / 2; j++)
        {
            *(uint8_t*)(pdst_uv + i * width + j * 2 + 1) =
                *(uint8_t*)(psrc_uv + i * width / 2 + j);
            *(uint8_t*)(pdst_uv + i * width + j * 2) =
                *(uint8_t*)(psrc_uv + (width / 2) * (height / 2) + i * width / 2 + j);
        }
    }

}

static bool yuv420spDownScale(void* psrc,
                              void* pdst,
                              int src_w,
                              int src_h,
                              int dst_w,
                              int dst_h)
{
    char * psrc_y = (char *)psrc;
    char * pdst_y = (char *)pdst;
    char * psrc_uv = (char *)psrc + src_w * src_h;
    char * pdst_uv = (char *)pdst + dst_w * dst_h;

    int scale = 1;
    int scale_w = src_w / dst_w;
    int scale_h = src_h / dst_h;
    int h, w;

    if (dst_w > src_w
        || dst_h > src_h)
    {
        LOGE("error size, %dx%d -> %dx%d\n", src_w, src_h, dst_w, dst_h);
        return false;
    }

    if (scale_w == scale_h)
    {
        scale = scale_w;
    }

    LOGV("scale = %d\n", scale);

    if (scale == 1)
    {
        if ((src_w == dst_w)
            && (src_h == dst_h))
        {
            memcpy((char*)pdst, (char*)psrc, dst_w * dst_h * 3/2);
        }
        else
        {
            // crop
            for (h = 0; h < dst_h; h++)
            {
                memcpy((char*)pdst + h * dst_w, (char*)psrc + h * src_w, dst_w);
            }
            for (h = 0; h < dst_h / 2; h++)
            {
                memcpy((char*)pdst_uv + h * dst_w, (char*)psrc_uv + h * src_w, dst_w);
            }
        }

        return true;
    }

    for (h = 0; h < dst_h; h++)
    {
        for (w = 0; w < dst_w; w++)
        {
            *(pdst_y + h * dst_w + w) = *(psrc_y + h * scale * src_w + w * scale);
        }
    }
    for (h = 0; h < dst_h / 2; h++)
    {
        for (w = 0; w < dst_w; w+=2)
        {
            *((short*)(pdst_uv + h * dst_w + w)) =
                *((short*)(psrc_uv + h * scale * src_w + w * scale));
        }
    }

    return true;
}

CallbackNotifier::CallbackNotifier()
    : mNotifyCB(NULL),
      mDataCB(NULL),
      mDataCBTimestamp(NULL),
      mGetMemoryCB(NULL),
      mCallbackCookie(NULL),
      mMessageEnabler(0),
      mVideoRecEnabled(false),
      mSavePictureCnt(0),
      mSavePictureMax(0),
      mJpegQuality(90),
      mJpegRotate(0),
      mPictureWidth(640),
      mPictureHeight(480),
      mThumbWidth(0),
      mThumbHeight(0),
      mGpsLatitude(0.0),
      mGpsLongitude(0.0),
      mGpsAltitude(0),
      mGpsTimestamp(0),
      mFocalLength(0.0),
      mWhiteBalance(0),
      mFd(0),
      mCBWidth(0),
      mCBHeight(0),
      mBufferList(NULL),
      mSaveThreadExited(false),
      mIsSinglePicture(true),
      mContinuousFdIndex(0)
{
    LOGV("CallbackNotifier construct");

    memset(mGpsMethod, 0, sizeof(mGpsMethod));
    memset(mCallingProcessName, 0, sizeof(mCallingProcessName));

    strcpy(mExifMake, "MID MAKE");        // default
    strcpy(mExifModel, "MID MODEL");    // default
    memset(mDateTime, 0, sizeof(mDateTime));
    strcpy(mDateTime, "2012:10:24 17:30:00");

    memset(mFolderPath, 0, sizeof(mFolderPath));
    memset(mSnapPath, 0, sizeof(mSnapPath));
    memset(mContinuousFd, 0, sizeof(mContinuousFd));
}

CallbackNotifier::~CallbackNotifier()
{
    LOGV("CallbackNotifier disconstruct");
}

/****************************************************************************
 * Camera API
 ***************************************************************************/

void CallbackNotifier::setCallbacks(camera_notify_callback notify_cb,
                                    camera_data_callback data_cb,
                                    camera_data_timestamp_callback data_cb_timestamp,
                                    camera_request_memory get_memory,
                                    void* user)
{
    LOGV("%s: %p, %p, %p, %p (%p)",
         __FUNCTION__, notify_cb, data_cb, data_cb_timestamp, get_memory, user);

    Mutex::Autolock locker(&mObjectLock);
    mNotifyCB = notify_cb;
    mDataCB = data_cb;
    mDataCBTimestamp = data_cb_timestamp;
    mGetMemoryCB = get_memory;
    mCallbackCookie = user;

    mBufferList = new BufferListManager();
    if (mBufferList == NULL)
    {
        LOGE("create BufferListManager failed");
    }

    mSaveThreadExited = false;

    // init picture thread
    mSavePictureThread = new DoSavePictureThread(this);
    pthread_mutex_init(&mSavePictureMutex, NULL);
    pthread_cond_init(&mSavePictureCond, NULL);
    mSavePictureThread->startThread();

    pthread_mutex_init(&mPictureFdMutex, NULL);
    pthread_cond_init(&mPictureFdCond, NULL);
}

void CallbackNotifier::enableMessage(uint msg_type)
{
    LOGV("%s: msg_type = 0x%x", __FUNCTION__, msg_type);
    PrintMessages(msg_type);

    Mutex::Autolock locker(&mObjectLock);
    mMessageEnabler |= msg_type;
    LOGV("**** Currently enabled messages:");
    PrintMessages(mMessageEnabler);
}

void CallbackNotifier::disableMessage(uint msg_type)
{
    LOGV("%s: msg_type = 0x%x", __FUNCTION__, msg_type);
    PrintMessages(msg_type);

    Mutex::Autolock locker(&mObjectLock);
    mMessageEnabler &= ~msg_type;
    LOGV("**** Currently enabled messages:");
    PrintMessages(mMessageEnabler);
}

status_t CallbackNotifier::enableVideoRecording()
{
    F_LOG;

    Mutex::Autolock locker(&mObjectLock);
    mVideoRecEnabled = true;

    return NO_ERROR;
}

void CallbackNotifier::disableVideoRecording()
{
    F_LOG;

    Mutex::Autolock locker(&mObjectLock);
    mVideoRecEnabled = false;
}

status_t CallbackNotifier::storeMetaDataInBuffers(bool enable)
{
    /* Return INVALID_OPERATION means HAL does not support metadata. So HAL will
     * return actual frame data with CAMERA_MSG_VIDEO_FRAME. Return
     * INVALID_OPERATION to mean metadata is not supported. */

    ALOGV("storeMetaDataInBuffers SET %s", enable ? "enable" : "unable");

    return UNKNOWN_ERROR;
}

/****************************************************************************
 * Public API
 ***************************************************************************/

void CallbackNotifier::cleanupCBNotifier()
{
    if (mSavePictureThread != NULL)
    {
        mSavePictureThread->stopThread();
        pthread_cond_signal(&mSavePictureCond);
        pthread_cond_signal(&mPictureFdCond);
        pthread_mutex_lock(&mSavePictureMutex);
        if (!mSaveThreadExited)
        {
            pthread_cond_wait(&mSavePictureCond, &mSavePictureMutex);
        }
        pthread_mutex_unlock(&mSavePictureMutex);

        mSavePictureThread.clear();
        mSavePictureThread = 0;

        pthread_mutex_destroy(&mSavePictureMutex);
        pthread_cond_destroy(&mSavePictureCond);
    }

    pthread_mutex_destroy(&mPictureFdMutex);
    pthread_cond_destroy(&mPictureFdCond);

    Mutex::Autolock locker(&mObjectLock);
    mMessageEnabler = 0;
    mNotifyCB = NULL;
    mDataCB = NULL;
    mDataCBTimestamp = NULL;
    mGetMemoryCB = NULL;
    mCallbackCookie = NULL;
    mVideoRecEnabled = false;
    mJpegQuality = 90;
    mJpegRotate = 0;
    mPictureWidth = 640;
    mPictureHeight = 480;
    mThumbWidth = 0;
    mThumbHeight = 0;
    mGpsLatitude = 0.0;
    mGpsLongitude = 0.0;
    mGpsAltitude = 0;
    mGpsTimestamp = 0;
    mFocalLength = 0.0;
    mWhiteBalance = 0;

    if (mBufferList != NULL)
    {
        delete mBufferList;
        mBufferList = NULL;
    }
}
void CallbackNotifier::onNextFrameHW2(/*buffer_handle_t* */ buffer_handle_t* handlebuffer,const void* frame)
{

	VideoNativeHandleMetadata mVideoNativeMetadata;
	V4L2BUF_t * pbuf = (V4L2BUF_t*)frame;
        private_handle_t * test_handle_t;
	// typedef const native_handle_t* buffer_handle_t;

	//(native_handle_t *)mNativeHandle = (buffer_handle_t*) handlebuffer;

	mVideoNativeMetadata.pHandle = (native_handle_t *)(*handlebuffer);
	test_handle_t = (private_handle_t *)mVideoNativeMetadata.pHandle;
       LOGV("zje,test_handle_t->base = %p, test_handle_t = %p \n",test_handle_t->base,test_handle_t);
    /* if it is kMetadataBufferTypeNativeHandleSource,no need to callback buffer_type */
    mVideoNativeMetadata.eType = kMetadataBufferTypeNativeHandleSource;
    //mVideoNativeMetadata.pHandle = privatehandlebuffer;
    if (isMessageEnabled(CAMERA_MSG_VIDEO_FRAME) && isVideoRecordingEnabled())
    {
        camera_memory_t* cam_buff = mGetMemoryCB(-1, sizeof(VideoNativeHandleMetadata), 1, NULL);
        if (NULL != cam_buff && NULL != cam_buff->data)
        {
            LOGV("zjw,CallbackNotifier::onNextFrameHW2");
            pbuf->refCnt++; //how to handle it ?
            memcpy(cam_buff->data, &mVideoNativeMetadata, sizeof(VideoNativeHandleMetadata));

            mDataCBTimestamp(pbuf->timeStamp, CAMERA_MSG_VIDEO_FRAME,
                               cam_buff, 0, mCallbackCookie);
            cam_buff->release(cam_buff);
        }
        else
        {
            LOGE("%s: Memory failure in CAMERA_MSG_VIDEO_FRAME", __FUNCTION__);
        }
    }
    //LOGD("Video:%d",);
}
//end for nativehandle buffer API

void CallbackNotifier::onNextFrameAvailable(buffer_handle_t* handlebuffer,const void* frame,
                                             bool hw)
{
    if (hw)
    {
        onNextFrameHW2(handlebuffer,frame);
    }
    else
    {
        onNextFrameSW(frame);
    }
}

void CallbackNotifier::onNextFrameHW(const void* frame)
{
    V4L2BUF_t * pbuf = (V4L2BUF_t*)frame;
    VencInputBuffer sInputBuffer;

    int buffer_type = kMetadataBufferTypeCameraSource;//matadataType
    memset(&sInputBuffer, 0, sizeof(VencInputBuffer));

    sInputBuffer.pAddrVirY = (unsigned char*)pbuf->addrVirY;
    sInputBuffer.pAddrVirC = (unsigned char*)pbuf->addrVirC;
    sInputBuffer.pAddrPhyY = (unsigned char*)pbuf->addrPhyY;
    sInputBuffer.pAddrPhyC = (unsigned char*)pbuf->addrPhyC;
    sInputBuffer.nID = pbuf->index;
    sInputBuffer.nPts = pbuf->timeStamp;

    if(pbuf->width == pbuf->crop_rect.width || pbuf->height== pbuf->crop_rect.height)
    {
        sInputBuffer.bEnableCorp = 0;
    }
    else
    {
        sInputBuffer.bEnableCorp = 1;
        sInputBuffer.sCropInfo.nLeft = pbuf->crop_rect.left;
        sInputBuffer.sCropInfo.nTop = pbuf->crop_rect.top;
        sInputBuffer.sCropInfo.nWidth  = pbuf->crop_rect.width;
        sInputBuffer.sCropInfo.nHeight = pbuf->crop_rect.height;
    }

    if (isMessageEnabled(CAMERA_MSG_VIDEO_FRAME) && isVideoRecordingEnabled())
    {
        camera_memory_t* cam_buff = mGetMemoryCB(-1, (sizeof(VencInputBuffer) + 4), 1, NULL);
        if (NULL != cam_buff && NULL != cam_buff->data)
        {
            pbuf->refCnt++;
            memcpy(cam_buff->data, &buffer_type, 4);
            memcpy((void*)(int(cam_buff->data) + 4), &sInputBuffer, sizeof(VencInputBuffer));

            mDataCBTimestamp(pbuf->timeStamp, CAMERA_MSG_VIDEO_FRAME,
                               cam_buff, 0, mCallbackCookie);
            cam_buff->release(cam_buff);
        }
        else
        {
            LOGE("%s: Memory failure in CAMERA_MSG_VIDEO_FRAME", __FUNCTION__);
        }
    }

    if (isMessageEnabled(CAMERA_MSG_PREVIEW_FRAME))
    {
        camera_memory_t* cam_buff = mGetMemoryCB(-1, (sizeof(VencInputBuffer) + 4), 1, NULL);
        if (NULL != cam_buff && NULL != cam_buff->data)
        {
            memcpy(cam_buff->data, &buffer_type, 4);
            memcpy((void*)(int(cam_buff->data) + 4), &sInputBuffer, sizeof(VencInputBuffer));
            mDataCB(CAMERA_MSG_PREVIEW_FRAME, cam_buff, 0, NULL, mCallbackCookie);
            cam_buff->release(cam_buff);
        }
        else
        {
            LOGE("%s: Memory failure in CAMERA_MSG_PREVIEW_FRAME", __FUNCTION__);
        }
    }
}

void CallbackNotifier::onNextFrameSW(const void* frame)
{
    V4L2BUF_t * pbuf = (V4L2BUF_t*)frame;
    int framesize =0;
    int src_format = 0;
    unsigned long src_addr_phy = 0;
    unsigned long src_addr_vir = 0;
    int src_width = 0;
    int src_height = 0;
    RECT_t src_crop;

    if ((pbuf->isThumbAvailable == 1)
        && (pbuf->thumbUsedForPreview == 1))
    {
        src_format            = pbuf->thumbFormat;
        src_addr_phy        = pbuf->thumbAddrPhyY;
        src_addr_vir        = pbuf->thumbAddrVirY;
        src_width            = pbuf->thumbWidth;
        src_height            = pbuf->thumbHeight;
        memcpy((void*)&src_crop, (void*)&pbuf->thumb_crop_rect, sizeof(RECT_t));
    }
    else
    {
        src_format            = pbuf->format;
        src_addr_phy        = pbuf->addrPhyY;
        src_addr_vir        = pbuf->addrVirY;
        src_width            = pbuf->width;
        src_height            = pbuf->height;
        memcpy((void*)&src_crop, (void*)&pbuf->crop_rect, sizeof(RECT_t));
    }

    framesize = ALIGN_16B(src_width) * src_height * 3/2;

    if (isMessageEnabled(CAMERA_MSG_VIDEO_FRAME) && isVideoRecordingEnabled())
    {
        camera_memory_t* cam_buff = mGetMemoryCB(-1, framesize, 1, NULL);
        if (NULL != cam_buff && NULL != cam_buff->data)
        {
            memcpy(cam_buff->data, (void *)src_addr_vir, framesize);
            mDataCBTimestamp(pbuf->timeStamp, CAMERA_MSG_VIDEO_FRAME,
                               cam_buff, 0, mCallbackCookie);
            cam_buff->release(cam_buff);        // star add
        } else {
            LOGE("%s: Memory failure in CAMERA_MSG_VIDEO_FRAME", __FUNCTION__);
        }
    }

    if (isMessageEnabled(CAMERA_MSG_PREVIEW_FRAME))
    {
        if (strcmp(mCallingProcessName, "com.android.facelock") == 0)
        {
             camera_memory_t* cam_buff = mGetMemoryCB(-1, 160 * 120 * 3 / 2, 1, NULL);
            if (NULL != cam_buff && NULL != cam_buff->data)
            {
                yuv420spDownScale((void*)src_addr_vir, cam_buff->data,
                                ALIGN_16B(src_width), src_height,
                                160, 120);
                mDataCB(CAMERA_MSG_PREVIEW_FRAME, cam_buff, 0, NULL, mCallbackCookie);
                cam_buff->release(cam_buff);
            }
            else
            {
                LOGE("%s: Memory failure in CAMERA_MSG_PREVIEW_FRAME", __FUNCTION__);
            }
        }
        #if 0
        else if(/*(src_width == 1920) || */(src_width == 1280) ||
                (src_width == 960) ||
                (src_width == 800))
        {
            // LOGD("src size: %dx%d, cb size: %dx%d,",
            //        src_width, src_height, mCBWidth, mCBHeight);
            framesize = mCBWidth * mCBHeight * 3/2;
            camera_memory_t* cam_buff = mGetMemoryCB(-1, framesize, 1, NULL);

            if (NULL == cam_buff
                || NULL == cam_buff->data)
            {
                LOGE("%s: Memory failure in CAMERA_MSG_PREVIEW_FRAME", __FUNCTION__);
                return;
            }
            if (src_format == V4L2_PIX_FMT_YVU420)
            {
                // it will be used in cts
                scaler((unsigned char*)src_addr_vir, (unsigned char*)cam_buff->data,
                                    ALIGN_16B(src_width), src_height,
                                    mCBWidth, mCBHeight, /*src_format*/0, 16);
            }
            else
            {
                framesize = ALIGN_16B(src_width) * src_height * 3/2;
                camera_memory_t* src_addr_vir_copy = mGetMemoryCB(-1, framesize, 1, NULL);
                if (NULL == src_addr_vir_copy
                || NULL == src_addr_vir_copy->data)
                {
                    LOGE("%s: Memory failure in CAMERA_MSG_PREVIEW_FRAME", __FUNCTION__);
                    return;
                }

                framesize = mCBWidth * mCBHeight * 3/2;
                camera_memory_t* cam_buff_copy = mGetMemoryCB(-1, framesize, 1, NULL);
                if (NULL == cam_buff_copy
                || NULL == cam_buff_copy->data)
                {
                    LOGE("%s: Memory failure in CAMERA_MSG_PREVIEW_FRAME", __FUNCTION__);
                    return;
                }

                if (src_format == V4L2_PIX_FMT_NV12)
                {
                    NV12ToYVU420((void*)src_addr_vir,
                                 (void*)src_addr_vir_copy->data,
                                 ALIGN_16B(src_width), src_height);
                }
                else if(src_format == V4L2_PIX_FMT_NV21)
                {
                    NV21ToYVU420((void*)src_addr_vir,
                                 (void*)src_addr_vir_copy->data,
                                 ALIGN_16B(src_width), src_height);
                }

                scaler((unsigned char*)src_addr_vir_copy->data,
                       (unsigned char*)cam_buff_copy->data,
                       ALIGN_16B(src_width),
                       src_height,
                       mCBWidth,
                       mCBHeight,
                       0,
                       16);
                YVU420ToNV21(cam_buff_copy->data, cam_buff->data, mCBWidth, mCBHeight);

                cam_buff_copy->release(cam_buff_copy);
                src_addr_vir_copy->release(src_addr_vir_copy);
            }

            mDataCB(CAMERA_MSG_PREVIEW_FRAME, cam_buff, 0, NULL, mCallbackCookie);
            cam_buff->release(cam_buff);
        }
        #endif
        else
        {
            camera_memory_t* cam_buff = mGetMemoryCB(-1, mCBWidth * mCBHeight * 3 / 2, 1, NULL);
            if (NULL != cam_buff && NULL != cam_buff->data)
            {
                yuv420spDownScale((void*)src_addr_vir, cam_buff->data,
                                ALIGN_16B(src_width), src_height,
                                mCBWidth, mCBHeight);
                if (src_format == V4L2_PIX_FMT_NV12)
                {
                    // NV12 <--> NV21
                    /*formatToNV21(cam_buff->data,
                    cam_buff->data,
                    mCBWidth,
                    mCBHeight,
                    ALIGN_16B(mCBWidth),
                    0,
                    2,
                    ALIGN_16B(mCBWidth) * mCBHeight * 3/2,
                    src_format);*/
                }
                mDataCB(CAMERA_MSG_PREVIEW_FRAME, cam_buff, 0, NULL, mCallbackCookie);
                cam_buff->release(cam_buff);
            }
            else
            {
                LOGE("%s: Memory failure in CAMERA_MSG_PREVIEW_FRAME", __FUNCTION__);
            }
        }
    }
}

status_t CallbackNotifier::autoFocusMsg(bool success)
{
    if (isMessageEnabled(CAMERA_MSG_FOCUS))
    {
        mNotifyCB(CAMERA_MSG_FOCUS, success, 0, mCallbackCookie);
    }
    return NO_ERROR;
}

status_t CallbackNotifier::autoFocusContinuousMsg(bool success)
{
    if (isMessageEnabled(CAMERA_MSG_FOCUS_MOVE))
    {
        // in continuous focus mode
        // true for starting focus, false for focus ok
        mNotifyCB(CAMERA_MSG_FOCUS_MOVE, success, 0, mCallbackCookie);
    }
    return NO_ERROR;
}

status_t CallbackNotifier::faceDetectionMsg(camera_frame_metadata_t *face)
{
    if (isMessageEnabled(CAMERA_MSG_PREVIEW_METADATA))
    {
        camera_memory_t *cam_buff = mGetMemoryCB(-1, 1, 1, NULL);
        mDataCB(CAMERA_MSG_PREVIEW_METADATA, cam_buff, 0, face, mCallbackCookie);
        cam_buff->release(cam_buff);
    }
    return NO_ERROR;
}

void CallbackNotifier::notifyPictureMsg(const void* frame)
{
    F_LOG;

    V4L2BUF_t * pbuf = (V4L2BUF_t*)frame;
    int framesize = pbuf->width * pbuf->height * 3/2;

    // shutter msg
    if (isMessageEnabled(CAMERA_MSG_SHUTTER))
    {
        F_LOG;
        mNotifyCB(CAMERA_MSG_SHUTTER, 0, 0, mCallbackCookie);
    }

    // raw image msg
    if (isMessageEnabled(CAMERA_MSG_RAW_IMAGE))
    {
        camera_memory_t *dummyRaw = mGetMemoryCB(-1, 1, 1, NULL);
        if ( NULL == dummyRaw )
        {
            LOGE("%s: Memory failure in CAMERA_MSG_PREVIEW_FRAME", __FUNCTION__);
            return;
        }
        mDataCB(CAMERA_MSG_RAW_IMAGE, dummyRaw, 0, NULL, mCallbackCookie);
        dummyRaw->release(dummyRaw);
    }
    else if (isMessageEnabled(CAMERA_MSG_RAW_IMAGE_NOTIFY))
    {
        mNotifyCB(CAMERA_MSG_RAW_IMAGE_NOTIFY, 0, 0, mCallbackCookie);
    }

    // postview msg
    if (0 && isMessageEnabled(CAMERA_MSG_POSTVIEW_FRAME) )
    {
        F_LOG;
        camera_memory_t* cam_buff = mGetMemoryCB(-1, framesize, 1, NULL);
        if (NULL != cam_buff && NULL != cam_buff->data)
        {
            memset(cam_buff->data, 0xff, framesize);
            mDataCB(CAMERA_MSG_POSTVIEW_FRAME, cam_buff, 0, NULL, mCallbackCookie);
            cam_buff->release(cam_buff);
        }
        else
        {
            LOGE("%s: Memory failure in CAMERA_MSG_PREVIEW_FRAME", __FUNCTION__);
            return;
        }
    }
}

void CallbackNotifier::startContinuousPicture()
{
    F_LOG;

    //
    mSavePictureCnt = 0;
}

void CallbackNotifier::stopContinuousPicture()
{
    // do nothing
}

void CallbackNotifier::setContinuousPictureCnt(int cnt)
{
    mSavePictureMax = cnt;
}

bool CallbackNotifier::takePicture(const void* frame, void *memOpsS, bool is_continuous )
{
    buffer_node * pNode = NULL;
    V4L2BUF_t * pbuf = (V4L2BUF_t *)frame;
    void * pOutBuf = NULL;
    int bufSize = 0;

    int src_format = 0;
    unsigned long src_addr_phy = 0;
    unsigned long src_addr_vir = 0;
    //unsigned int src_addr_phy = 0;
    //unsigned int src_addr_vir = 0;
    int src_width = 0;
    int src_height = 0;
    RECT_t src_crop;

    DBG_TIME_BEGIN("CallbackNotifier taking picture", 0);

    if ((pbuf->isThumbAvailable == 1)
        && (pbuf->thumbUsedForPhoto == 1))
    {
        src_format            = pbuf->thumbFormat;
        src_addr_phy        = pbuf->thumbAddrPhyY;
        src_addr_vir        = pbuf->thumbAddrVirY;
        src_width            = pbuf->thumbWidth;
        src_height            = pbuf->thumbHeight;
        memcpy((void*)&src_crop, (void*)&pbuf->thumb_crop_rect, sizeof(RECT_t));
    }
    else
    {
        src_format            = pbuf->format;
        src_addr_phy        = pbuf->addrPhyY;
        src_addr_vir        = pbuf->addrVirY;
        src_width            = pbuf->width;
        src_height            = pbuf->height;
        memcpy((void*)&src_crop, (void*)&pbuf->crop_rect, sizeof(RECT_t));
    }

    JPEG_ENC_t jpeg_enc;
    memset(&jpeg_enc, 0, sizeof(jpeg_enc));
    jpeg_enc.addrY            = src_addr_phy;
    jpeg_enc.addrC            = src_addr_phy + ALIGN_16B(src_width) * src_height;
    jpeg_enc.src_w            = src_width;
    jpeg_enc.src_h            = src_height;
    jpeg_enc.pic_w            = mPictureWidth;
    jpeg_enc.pic_h            = mPictureHeight;

#ifdef USE_CSI_VIN_DRIVER
    jpeg_enc.colorFormat  = (src_format == V4L2_PIX_FMT_NV21M) ?
                              JPEG_COLOR_YUV420_NV21:
                              JPEG_COLOR_YUV420_NV12;
#else
    jpeg_enc.colorFormat    = (src_format == V4L2_PIX_FMT_NV21) ?
                              JPEG_COLOR_YUV420_NV21:
                              JPEG_COLOR_YUV420_NV12;
#endif

    jpeg_enc.quality        = mJpegQuality;
    jpeg_enc.rotate            = mJpegRotate;

    getCurrentDateTime();

    //
    strcpy(jpeg_enc.CameraMake, mExifMake);
    strcpy(jpeg_enc.CameraModel, mExifModel);
    strcpy(jpeg_enc.DateTime, mDateTime);

    jpeg_enc.thumbWidth        = mThumbWidth;
    jpeg_enc.thumbHeight    = mThumbHeight;
    jpeg_enc.whitebalance   = mWhiteBalance;
    jpeg_enc.focal_length    = mFocalLength;

    if (0 != strlen(mGpsMethod))
    {
        jpeg_enc.enable_gps            = 1;
        jpeg_enc.gps_latitude        = mGpsLatitude;
        jpeg_enc.gps_longitude        = mGpsLongitude;
        jpeg_enc.gps_altitude        = mGpsAltitude;
        jpeg_enc.gps_timestamp        = mGpsTimestamp;
        strcpy(jpeg_enc.gps_processing_method, mGpsMethod);
        memset(mGpsMethod, 0, sizeof(mGpsMethod));
    }
    else
    {
        jpeg_enc.enable_gps            = 0;
    }

    if ((src_crop.width != jpeg_enc.src_w)
        || (src_crop.height != jpeg_enc.src_h))
    {
        jpeg_enc.enable_crop        = 1;
        jpeg_enc.crop_x                = src_crop.left;
        jpeg_enc.crop_y                = src_crop.top;
        jpeg_enc.crop_w                = src_crop.width;
        jpeg_enc.crop_h                = src_crop.height;
    }
    else
    {
        jpeg_enc.enable_crop        = 0;
    }

    LOGV("addrY: %x, src: %dx%d, pic: %dx%d, quality: %d, rotate: %d, Gps method: %s, \
        thumbW: %d, thumbH: %d, thubmFactor: %d, crop: [%d, %d, %d, %d]",
        jpeg_enc.addrY,
        jpeg_enc.src_w, jpeg_enc.src_h,
        jpeg_enc.pic_w, jpeg_enc.pic_h,
        jpeg_enc.quality, jpeg_enc.rotate,
        jpeg_enc.gps_processing_method,
        jpeg_enc.thumbWidth,
        jpeg_enc.thumbHeight,
        jpeg_enc.scale_factor,
        jpeg_enc.crop_x,
        jpeg_enc.crop_y,
        jpeg_enc.crop_w,
        jpeg_enc.crop_h);

    pNode = mBufferList->allocBuffer(-1, mPictureWidth * mPictureHeight);
    if (pNode == NULL)
    {
        LOGE("malloc picture node failed");
        return false;
    }
    pOutBuf = pNode->data;
    if (pOutBuf == NULL)
    {
        LOGE("malloc picture memory failed");
        return false;
    }

    JpegEncInfo sjpegInfo;
    EXIFInfo   exifInfo;

    memset(&sjpegInfo, 0, sizeof(JpegEncInfo));
    memset(&exifInfo, 0, sizeof(EXIFInfo));

    sjpegInfo.sBaseInfo.nStride = (src_width);
    sjpegInfo.sBaseInfo.nInputWidth = (src_width);
    sjpegInfo.sBaseInfo.nInputHeight = (src_height);
    sjpegInfo.sBaseInfo.nDstWidth = mPictureWidth;
    sjpegInfo.sBaseInfo.nDstHeight = mPictureHeight;
    sjpegInfo.sBaseInfo.memops = (ScMemOpsS*)memOpsS;
    sjpegInfo.pAddrPhyY = (unsigned char*)src_addr_phy;
    sjpegInfo.pAddrPhyC = (unsigned char*)src_addr_phy + ALIGN_16B(src_width) * src_height;
//add by jiangwei
    sjpegInfo.nShareBufFd = pbuf->nShareBufFd;
    LOGD("zjw, sjpegInfo.nShareBufFd %d",sjpegInfo.nShareBufFd);

#ifdef USE_CSI_VIN_DRIVER
    sjpegInfo.sBaseInfo.eInputFormat  = (src_format == V4L2_PIX_FMT_NV21M) ?
	VENC_PIXEL_YVU420SP:
	VENC_PIXEL_YUV420SP;
#else
    sjpegInfo.sBaseInfo.eInputFormat    = (src_format == V4L2_PIX_FMT_NV21) ?
	VENC_PIXEL_YVU420SP:
	VENC_PIXEL_YUV420SP;

#endif

    sjpegInfo.quality        = mJpegQuality;
    exifInfo.Orientation    = mJpegRotate;
    sjpegInfo.bEnableCorp  = 0;

    sjpegInfo.sCropInfo.nLeft = 0;
    sjpegInfo.sCropInfo.nTop = 0;
    sjpegInfo.sCropInfo.nWidth = 320;
    sjpegInfo.sCropInfo.nHeight = 240;

    exifInfo.ThumbWidth = 320;
    exifInfo.ThumbHeight = 240;

    strcpy((char*)exifInfo.CameraMake, "allwinner make test");
    strcpy((char*)exifInfo.CameraModel, "allwinner model test");
    strcpy((char*)exifInfo.DateTime, "2014:02:21 10:54:05");
    strcpy((char*)exifInfo.gpsProcessingMethod, "allwinner gps");

    exifInfo.ExposureTime.num = 2;
    exifInfo.ExposureTime.den = 1000;

    exifInfo.FNumber.num = 20;
    exifInfo.FNumber.den = 10;
    exifInfo.ISOSpeed = 50;

    exifInfo.ExposureBiasValue.num= -4;
    exifInfo.ExposureBiasValue.den= 1;

    exifInfo.MeteringMode = 1;
    exifInfo.FlashUsed = 0;

    exifInfo.FocalLength.num = 1400;
    exifInfo.FocalLength.den = 100;

    exifInfo.DigitalZoomRatio.num = 4;
    exifInfo.DigitalZoomRatio.den = 1;

    exifInfo.WhiteBalance = 1;
    exifInfo.ExposureMode = 1;

    exifInfo.enableGpsInfo = 0;

    exifInfo.gps_latitude = 23.2368;
    exifInfo.gps_longitude = 24.3244;
    exifInfo.gps_altitude = 1234.5;
    exifInfo.gps_timestamp = (long)time(NULL);

    int ret = AWJpecEnc(&sjpegInfo,&exifInfo,pOutBuf,&bufSize);

    if (ret < 0)
    {
        LOGE("JpegEnc failed");
        return false;
    }
    //LOGV("hw enc time: %lld(ms), size: %d", (systemTime() - lasttime)/1000000, bufSize);

    DBG_TIME_DIFF("enc");

    if (is_continuous)
    {
        pNode->id = mSavePictureCnt;
        pNode->size = bufSize;
        mBufferList->push(pNode);

        // cb number of pictures
        if (isMessageEnabled(CAMERA_MSG_CONTINUOUSSNAP))
        {
            mNotifyCB(CAMERA_MSG_CONTINUOUSSNAP, mSavePictureCnt, 0, mCallbackCookie);
        }

        pthread_cond_signal(&mSavePictureCond);

        mSavePictureCnt++;
    }
    else
    {
        if ((strlen(mSnapPath) > 0))
        {
            camera_memory_t* cb_buff;
            strcpy(pNode->priv, mSnapPath);
            pNode->id = -1;
            pNode->size = bufSize;
            mBufferList->push(pNode);
            mNotifyCB(CAMERA_MSG_SNAP, 0, 0, mCallbackCookie);
            pthread_cond_signal(&mSavePictureCond);
        }
        else
        {
            camera_memory_t* jpeg_buff = mGetMemoryCB(-1, bufSize, 1, NULL);
            if (NULL != jpeg_buff && NULL != jpeg_buff->data)
            {
                memcpy(jpeg_buff->data, (uint8_t *)pOutBuf, bufSize);
                mDataCB(CAMERA_MSG_COMPRESSED_IMAGE, jpeg_buff, 0, NULL, mCallbackCookie);
                jpeg_buff->release(jpeg_buff);
            }
            else
            {
                LOGE("%s: Memory failure in CAMERA_MSG_COMPRESSED_IMAGE", __FUNCTION__);
            }

            mBufferList->releaseBuffer(pNode);
        }
    }

    DBG_TIME_DIFF("photo end");
    LOGV("taking photo end");
    return true;
}

void CallbackNotifier::setFd(int fd)
{
    if (mIsSinglePicture)
    {
        pthread_mutex_lock(&mPictureFdMutex);
        mFd = fd;
        pthread_cond_signal(&mPictureFdCond);
        pthread_mutex_unlock(&mPictureFdMutex);
    }
    else
    {
        pthread_mutex_lock(&mPictureFdMutex);
        mContinuousFd[(mContinuousFdIndex++)%10] = fd;
        pthread_cond_signal(&mPictureFdCond);
        pthread_mutex_unlock(&mPictureFdMutex);
    }
}

bool CallbackNotifier::savePictureThread()
{
    int wait_cnt = 5;
    int wait_time = 3;        // 3s * 5 = 15s

    if (mIsSinglePicture)
    {
        pthread_mutex_lock(&mSavePictureMutex);
        if (mSavePictureThread->getThreadStatus() == THREAD_STATE_EXIT)
        {
            mSaveThreadExited = true;
            pthread_mutex_unlock(&mSavePictureMutex);

            pthread_cond_signal(&mSavePictureCond);

            LOGD("savePictureThread exit, line: %d", __LINE__);
            return false;
        }

        if (mBufferList->isListEmpty())
        {
            LOGV("wait for picture to save");
            pthread_cond_wait(&mSavePictureCond, &mSavePictureMutex);
            pthread_mutex_unlock(&mSavePictureMutex);
            return true;
        }
        pthread_mutex_unlock(&mSavePictureMutex);
    }
    else
    {
        if (mBufferList->isListEmpty())
        {
            pthread_mutex_lock(&mSavePictureMutex);

            if (mSavePictureThread->getThreadStatus() == THREAD_STATE_EXIT)
            {
                mSaveThreadExited = true;
                pthread_mutex_unlock(&mSavePictureMutex);

                pthread_cond_signal(&mSavePictureCond);

                LOGD("savePictureThread exit, line: %d", __LINE__);
                return false;
            }

            LOGV("wait for picture to save");
            pthread_cond_wait(&mSavePictureCond, &mSavePictureMutex);
            pthread_mutex_unlock(&mSavePictureMutex);
            return true;
        }
    }

    ALOGV("%d items left in the picture list.", mBufferList->getItemCnt());

    DBG_TIME_BEGIN("save picture", 0);

    char fname[128];
    FILE *pf = NULL;
    buffer_node * pNode = mBufferList->pop();
    if (pNode == NULL)
    {
        LOGE("list head is null");
        goto SAVE_PICTURE_END;
    }

    if (mIsSinglePicture)
    {
        // callback fd
        camera_memory_t* cb_buff;
        if (pNode->id >= 0)
        {
            sprintf(pNode->priv, "%s%03d.jpg", mFolderPath, pNode->id);
        }
        cb_buff = mGetMemoryCB(-1, strlen(pNode->priv), 1, NULL);
        if (NULL != cb_buff && NULL != cb_buff->data)
        {
            memcpy(cb_buff->data, (uint8_t *)pNode->priv, strlen(pNode->priv));
            mDataCB(CAMERA_MSG_SNAP_FD, cb_buff, 0, NULL, mCallbackCookie);
            cb_buff->release(cb_buff);
        }
        else
        {
            LOGE("%s: Memory failure in CAMERA_MSG_SNAP_FD", __FUNCTION__);
            goto SAVE_PICTURE_END;
        }

        // wait fd
        while(wait_cnt-- >= 0)
        {
            pthread_mutex_lock(&mPictureFdMutex);
            if (mFd <= 0)
            {
                struct timespec timeout;
                timeout.tv_sec=time(0) + wait_time;        // 3s timeout
                timeout.tv_nsec = 0;
                int ret = pthread_cond_timedwait(&mPictureFdCond, &mPictureFdMutex, &timeout);
                if (ret == ETIMEDOUT)
                {
                    LOGW("wait fd timeout");
                    pthread_mutex_unlock(&mPictureFdMutex);

                    pthread_mutex_lock(&mSavePictureMutex);
                    if (mSavePictureThread->getThreadStatus() == THREAD_STATE_EXIT)
                    {
                        mSaveThreadExited = true;
                        pthread_mutex_unlock(&mSavePictureMutex);

                        pthread_cond_signal(&mSavePictureCond);

                        LOGD("savePictureThread exit, line: %d", __LINE__);
                        return false;
                    }
                    pthread_mutex_unlock(&mSavePictureMutex);
                    continue;
                }
            }
            pNode->fd = mFd;
            mFd = 0;
            pthread_mutex_unlock(&mPictureFdMutex);
            break;
        }

        if (wait_cnt < 0 || pNode->fd <= 0)
        {
            LOGE("single picture wait fd failed");
            goto SAVE_PICTURE_END;
        }
    }
    else
    {
        // continuous picture wait fd
        pthread_mutex_lock(&mPictureFdMutex);
        if (mContinuousFd[pNode->id] <= 0)
        {
            struct timespec timeout;
            timeout.tv_sec=time(0) + 3;        // 3s timeout
            timeout.tv_nsec = 0;
            int ret = pthread_cond_timedwait(&mPictureFdCond, &mPictureFdMutex, &timeout);
            if (ret == ETIMEDOUT)
            {
                LOGW("wait fd timeout");
                pthread_mutex_unlock(&mPictureFdMutex);
                goto SAVE_PICTURE_END;
            }
        }
        pNode->fd = mContinuousFd[pNode->id];
        mContinuousFd[pNode->id] = 0;
        pthread_mutex_unlock(&mPictureFdMutex);

        if (pNode->fd <= 0)
        {
            LOGD("savePictureThread exit, line: %d", __LINE__);
            goto SAVE_PICTURE_END;
        }
    }

    // write
    if (pNode->id >= 0)
    {
        if(pNode->fd > 0)
        {
            int err = ::write(pNode->fd, (uint8_t *)pNode->data, pNode->size);
            if(err == -1)
            {
                LOGE("write failed: %s", strerror(errno));
                ::close(pNode->fd);
                goto SAVE_PICTURE_END;
            }
            ::close(pNode->fd);
            if (isMessageEnabled(CAMERA_MSG_CONTINUOUSSNAP))
            {
                mNotifyCB(CAMERA_MSG_CONTINUOUSSNAP, (pNode->id + 1000), 0, mCallbackCookie);
            }
        }
        else
        {
            sprintf(fname, "%s%03d.jpg", mFolderPath, pNode->id);
            pf = fopen(fname, "wb+");
            if (pf != NULL)
            {
                LOGV("open %s ok", fname);
                fwrite((uint8_t *)pNode->data, pNode->size, 1, pf);
                fflush(pf);
                fclose(pf);
            }
            else
            {
                LOGE("open %s failed, %s", fname, strerror(errno));
                goto SAVE_PICTURE_END;
            }
        }
    }
    else
    {
        if(pNode->fd > 0)
        {
            int err = ::write(pNode->fd, (uint8_t *)pNode->data, pNode->size);
            if(err == -1)
            {
                LOGE("write failed: %s", strerror(errno));
                ::close(pNode->fd);
                goto SAVE_PICTURE_END;
            }
            ::close(pNode->fd);
        }
        else
        {
            strcpy(fname, pNode->priv);
            pf = fopen(fname, "wb+");
            if (pf != NULL)
            {
                LOGV("open %s ok", fname);
                fwrite((uint8_t *)pNode->data, pNode->size, 1, pf);
                fflush(pf);
                fclose(pf);
            }
            else
            {
                LOGE("open %s failed, %s", fname, strerror(errno));
                goto SAVE_PICTURE_END;
            }
        }
    }

    DBG_TIME_DIFF("write file");

    // single snap callback thumb
    if (pNode->id < 0)
    {
        camera_memory_t* cb_buff;
        cb_buff = mGetMemoryCB(-1, strlen(pNode->priv), 1, NULL);
        if (NULL != cb_buff && NULL != cb_buff->data)
        {
            memcpy(cb_buff->data, (uint8_t *)pNode->priv, strlen(pNode->priv));
            mDataCB(CAMERA_MSG_SNAP_THUMB, cb_buff, 0, NULL, mCallbackCookie);
            cb_buff->release(cb_buff);
        }
        else
        {
            LOGE("%s: Memory failure in CAMERA_MSG_SNAP_THUMB", __FUNCTION__);
        }
    }

SAVE_PICTURE_END:
    if (pNode != NULL)
    {
        mBufferList->releaseBuffer(pNode);
    }

    return true;
}


void CallbackNotifier::getCurrentDateTime()
{
    time_t t;
    struct tm *tm_t;
    time(&t);
    tm_t = localtime(&t);
    sprintf(mDateTime, "%4d:%02d:%02d %02d:%02d:%02d",
        tm_t->tm_year+1900, tm_t->tm_mon+1, tm_t->tm_mday,
        tm_t->tm_hour, tm_t->tm_min, tm_t->tm_sec);
}

void CallbackNotifier::onCameraDeviceError(int err)
{
    if (isMessageEnabled(CAMERA_MSG_ERROR) && mNotifyCB != NULL) {
        mNotifyCB(CAMERA_MSG_ERROR, err, 0, mCallbackCookie);
    }
}

}; /* namespace android */
