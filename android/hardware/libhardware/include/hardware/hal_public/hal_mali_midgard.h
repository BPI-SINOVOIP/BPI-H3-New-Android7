/*
 * Copyright (C) 2010 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
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

/* DDK version: r14p0-01rel0 */

#ifndef GRALLOC_PRIV_H_
#define GRALLOC_PRIV_H_

#include <stdint.h>
#include <pthread.h>
#include <errno.h>
#include <linux/fb.h>
#include <sys/types.h>
#include <unistd.h>
#include <sys/mman.h>
#include <hardware/gralloc.h>
#include <cutils/native_handle.h>
#include <utils/Log.h>

#ifndef SUNXI_YUV_ALIGN
#define SUNXI_YUV_ALIGN 16
#endif

#ifndef SUNXI_MEM_CONTIGUOUS
#define SUNXI_MEM_CONTIGUOUS (1U<<3)
#endif

#define GRALLOC_SUNXI_METADATA_BUF 1
#define GRALLOC_ARM_UMP_MODULE 0
#define GRALLOC_ARM_DMA_BUF_MODULE 1
#if !defined(GRALLOC_OLD_ION_API)
/* new libion */
typedef int ion_user_handle_t;
#define ION_INVALID_HANDLE 0
#else
typedef struct ion_handle *ion_user_handle_t;
#define ION_INVALID_HANDLE NULL
#endif /* GRALLOC_OLD_ION_API */

/* NOTE:
 * If your framebuffer device driver is integrated with UMP, you will have to
 * change this IOCTL definition to reflect your integration with the framebuffer
 * device.
 * Expected return value is a UMP secure id backing your framebuffer device memory.
 */
#if GRALLOC_ARM_UMP_MODULE
#define IOCTL_GET_FB_UMP_SECURE_ID	_IOWR('m', 0xF8, __u32)
#endif

/* NOTE:
 * If your framebuffer device driver is integrated with dma_buf, you will have to
 * change this IOCTL definition to reflect your integration with the framebuffer
 * device.
 * Expected return value is a structure filled with a file descriptor
 * backing your framebuffer device memory.
 */
#if GRALLOC_ARM_DMA_BUF_MODULE
struct fb_dmabuf_export
{
	__u32 fd;
	__u32 flags;
};
#define FBIOGET_DMABUF	_IOR('F', 0x21, struct fb_dmabuf_export)
#endif

/* the max string size of GRALLOC_HARDWARE_GPU0 & GRALLOC_HARDWARE_FB0
 * 8 is big enough for "gpu0" & "fb0" currently
 */
#define MALI_GRALLOC_HARDWARE_MAX_STR_LEN 8
#define NUM_FB_BUFFERS 2

/* Define number of shared file descriptors */
#define GRALLOC_ARM_NUM_FDS 2

#define NUM_INTS_IN_PRIVATE_HANDLE ((sizeof(struct private_handle_t) - sizeof(native_handle)) / sizeof(int) - sNumFds)

enum
{
	/* Buffer won't be allocated as AFBC */
	GRALLOC_ARM_USAGE_NO_AFBC = GRALLOC_USAGE_PRIVATE_1 | GRALLOC_USAGE_PRIVATE_2
};

#if GRALLOC_ARM_UMP_MODULE
#include <ump/ump.h>
#endif

#define SZ_4K      0x00001000
#define SZ_2M      0x00200000

typedef enum
{
	MALI_YUV_NO_INFO,
	MALI_YUV_BT601_NARROW,
	MALI_YUV_BT601_WIDE,
	MALI_YUV_BT709_NARROW,
	MALI_YUV_BT709_WIDE
} mali_gralloc_yuv_info;

typedef enum
{
	MALI_DPY_TYPE_UNKNOWN = 0,
	MALI_DPY_TYPE_CLCD,
	MALI_DPY_TYPE_HDLCD
} mali_dpy_type;

struct private_handle_t;

struct private_module_t
{
	gralloc_module_t base;

	struct private_handle_t* framebuffer;
	uint32_t flags;
	uint32_t numBuffers;
	uint32_t bufferMask;
	pthread_mutex_t lock;
	buffer_handle_t currentBuffer;
	int ion_client;
	mali_dpy_type dpy_type;

	struct fb_var_screeninfo info;
	struct fb_fix_screeninfo finfo;
	float xdpi;
	float ydpi;
	float fps;
	int swapInterval;

	enum
	{
		// flag to indicate we'll post this buffer
		PRIV_USAGE_LOCKED_FOR_POST = 0x80000000
	};

#ifdef __cplusplus
	/* default constructor */
	private_module_t();
#endif
};

#ifdef __cplusplus
struct private_handle_t : public native_handle
{
#else
struct private_handle_t
{
	struct native_handle nativeHandle;
#endif

	enum
	{
		PRIV_FLAGS_FRAMEBUFFER       = 0x00000001,
		PRIV_FLAGS_USES_UMP          = 0x00000002,
		PRIV_FLAGS_USES_ION          = 0x00000004,
		PRIV_FLAGS_USES_CONFIG       = 0x00000008
	};

	enum
	{
		LOCK_STATE_WRITE     =   1<<31,
		LOCK_STATE_MAPPED    =   1<<30,
		LOCK_STATE_READ_MASK =   0x3FFFFFFF
	};

#if GRALLOC_ARM_DMA_BUF_MODULE
	/*
	 * Shared file descriptor for dma_buf sharing. This must be the first element in the
	 * structure so that binder knows where it is and can properly share it between
	 * processes.
	 * DO NOT MOVE THIS ELEMENT!
	 */
	int     share_fd;
#endif

	union {
		int share_attr_fd;
		int metadata_fd;
	};

#if GRALLOC_ARM_DMA_BUF_MODULE
	ion_user_handle_t ion_hnd;
#endif

	ion_user_handle_t ion_metadata_hnd;
	int ion_metadata_size;
	unsigned int ion_metadata_flag;

	long long aw_buf_id;

	// ints
	int        magic;
	union {
		int req_format;
		int format;
	};
	uint64_t   internal_format;
	int        byte_stride;
	int        flags;
	int        usage;
	int        size;
	int        width;
	int        height;
	int        internalWidth;
	int        internalHeight;
	int        stride;
	union {
		void*    base;
		uint64_t padding;
	};
	int        lockState;
	int        writeOwner;
	int        pid;

	// locally mapped shared attribute area
	union {
		void*    attr_base;
		uint64_t padding3;
	};

	mali_gralloc_yuv_info yuv_info;

	// Following members is for framebuffer only
	int   fd;
	union {
		off_t    offset;
		uint64_t padding4;
	};

	// Following members are for UMP memory only
#if GRALLOC_ARM_UMP_MODULE
	ump_secure_id ump_id;
	union {
		void*    ump_mem_handle;
		uint64_t padding5;
	};
#endif
	/*
	 * min_pgsz denotes minimum phys_page size used by this buffer.
	 * if buffer memory is physical contiguous set min_pgsz to buff->size
	 * if not sure buff's real phys_page size, you can use SZ_4K for safe.
	 */
	int min_pgsz;
#ifdef __cplusplus
	/*
	 * We track the number of integers in the structure. There are 16 unconditional
	 * integers (magic - pid, yuv_info, fd and offset). Note that the fd element is
	 * considered an int not an fd because it is not intended to be used outside the
	 * surface flinger process. The GRALLOC_ARM_NUM_INTS variable is used to track the
	 * number of integers that are conditionally included. Similar considerations apply
	 * to the number of fds.
	 */
	static const int sNumFds = GRALLOC_ARM_NUM_FDS;
	static const int sMagic = 0x3141592;

#if GRALLOC_ARM_UMP_MODULE
	private_handle_t(int flags, int usage, int size, void *base, int lock_state, ump_secure_id secure_id, ump_handle handle):
		share_attr_fd(-1),
		magic(sMagic),
		flags(flags),
		usage(usage),
		size(size),
		width(0),
		height(0),
		stride(0),
		base(base),
		lockState(lock_state),
		writeOwner(0),
		pid(getpid()),
		attr_base(MAP_FAILED),
		yuv_info(MALI_YUV_NO_INFO),
		fd(0),
		offset(0),
		ump_id(secure_id),
		ump_mem_handle(handle)

	{
		version = sizeof(native_handle);
		numFds = sNumFds;
		numInts = NUM_INTS_IN_PRIVATE_HANDLE;
	}
#endif

	private_handle_t(int flags, int usage, int size, void *base, int lock_state):
#if GRALLOC_ARM_DMA_BUF_MODULE
		share_fd(-1),
#endif
		share_attr_fd(-1),
#if GRALLOC_ARM_DMA_BUF_MODULE
		ion_hnd(ION_INVALID_HANDLE),
#endif

		ion_metadata_hnd(ION_INVALID_HANDLE),
		ion_metadata_size(0),
		ion_metadata_flag(0),

		magic(sMagic),
		flags(flags),
		usage(usage),
		size(size),
		width(0),
		height(0),
		stride(0),
		base(base),
		lockState(lock_state),
		writeOwner(0),
		pid(getpid()),
		attr_base(MAP_FAILED),
		yuv_info(MALI_YUV_NO_INFO),
		fd(0),
		offset(0)
#if GRALLOC_ARM_UMP_MODULE
		,ump_id(UMP_INVALID_SECURE_ID),
		ump_mem_handle(UMP_INVALID_MEMORY_HANDLE)
#endif


	{
		version = sizeof(native_handle);
		numFds = sNumFds;
		numInts = NUM_INTS_IN_PRIVATE_HANDLE;
	}

	private_handle_t(int flags, int usage, int size, void *base, int lock_state, int fb_file, off_t fb_offset, int unused):
#if GRALLOC_ARM_DMA_BUF_MODULE
		share_fd(-1),
#endif
		share_attr_fd(-1),
#if GRALLOC_ARM_DMA_BUF_MODULE
		ion_hnd(ION_INVALID_HANDLE),
#endif
		magic(sMagic),
		flags(flags),
		usage(usage),
		size(size),
		width(0),
		height(0),
		stride(0),
		base(base),
		lockState(lock_state),
		writeOwner(0),
		pid(getpid()),
		attr_base(MAP_FAILED),
		yuv_info(MALI_YUV_NO_INFO),
		fd(fb_file),
		offset(fb_offset)

#if GRALLOC_ARM_UMP_MODULE
		,ump_id(UMP_INVALID_SECURE_ID),
		ump_mem_handle(UMP_INVALID_MEMORY_HANDLE)
#endif

	{
		version = sizeof(native_handle);
		numFds = sNumFds;
		numInts = NUM_INTS_IN_PRIVATE_HANDLE;
	}

	~private_handle_t()
	{
		magic = 0;
	}

	bool usesPhysicallyContiguousMemory()
	{
		return (flags & PRIV_FLAGS_FRAMEBUFFER) ? true : false;
	}

	static int validate(const native_handle* h)
	{
		const private_handle_t* hnd = (const private_handle_t*)h;
		if (!h ||
		    h->version != sizeof(native_handle) ||
		    h->numInts != NUM_INTS_IN_PRIVATE_HANDLE ||
		    h->numFds != sNumFds ||
		    hnd->magic != sMagic)
		{
			return -EINVAL;
		}
		return 0;
	}

	static private_handle_t* dynamicCast(const native_handle* in)
	{
		if (validate(in) == 0)
		{
			return (private_handle_t*) in;
		}
		return NULL;
	}
#endif
};

#endif /* GRALLOC_PRIV_H_ */
