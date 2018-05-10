/*
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

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <sys/mount.h>
#include <sys/wait.h>

#include <linux/kdev_t.h>

#define LOG_TAG "Vold"

#include <cutils/log.h>
#include <cutils/properties.h>

#include <logwrap/logwrap.h>

#include "Ntfs.h"
#include "VoldUtil.h"

static char FSCK_NTFS3G_PATH[] 	="/system/bin/ntfs-3g.probe";
static char MOUNT_NTFS3G_PATH[] ="/system/bin/ntfs-3g";

extern "C" int mount(const char *, const char *, const char *, unsigned long, const void *);

int Ntfs::check(const char *fsPath) {
    SLOGI("Ntfs::check");
	if (access(FSCK_NTFS3G_PATH, X_OK)) {
        SLOGW("Skipping fs checks\n");
        return 0;
    }

    int pass = 1;
	int rc;
    do {
        const char *args[4];
        int status;
        /* we first mount it read and write*/
        args[0] = FSCK_NTFS3G_PATH;
        args[1] = "--readwrite";
        args[2] = fsPath;

        rc = android_fork_execvp(3, (char **)args, &status,
                false, true);
        if (rc != 0 && rc !=15) {
            SLOGE("Filesystem check failed due to logwrap error");
            errno = EIO;
            return -1;
        }

        if (!WIFEXITED(status)) {
            SLOGE("Filesystem check did not exit properly");
            errno = EIO;
            return -1;
        }

        status = WEXITSTATUS(status);

        switch(status) {
        case 0:
            SLOGI("Filesystem check completed OK");
            return 0;

        case 2:
            SLOGE("Filesystem check failed (not a NTFS filesystem)");
            errno = ENODATA;
            return -1;

        case 4:
            if (pass++ <= 3) {
                SLOGW("Filesystem modified - rechecking (pass %d)",
                        pass);
                continue;
            }
            SLOGE("Failing check after too many rechecks");
            errno = EIO;
            return -1;

        case 15:
            SLOGI("The disk contains an unclean file system (0, 0).");
            return 0;

        default:
            SLOGE("Filesystem check failed (unknown exit code %d)", status);
            errno = EIO;
            return -1;
        }
    } while (0);

    return 0;
}

int Ntfs::doMount(const char *fsPath, const char *mountPoint,
                 bool ro, bool remount, bool executable,
                 int ownerUid, int ownerGid, int permMask, bool createLost) {
#if 0
    int rc;
    unsigned long flags;
    char mountData[255];

 //  flags = MS_NODEV | MS_NOSUID | MS_DIRSYNC;
  flags = MS_NODEV | MS_NOSUID ;//| MS_DIRSYNC;
    flags |= (executable ? 0 : MS_NOEXEC);
    flags |= (ro ? MS_RDONLY : 0);
    flags |= (remount ? MS_REMOUNT : 0);

        permMask = 0;

    sprintf(mountData,
            "nls=utf8,uid=%d,gid=%d,fmask=%o,dmask=%o",
            ownerUid, ownerGid, permMask, permMask);

    SLOGE("[lkj]:mount flags %s, mountData %s\n",flags, mountData, );
    rc = mount(fsPath, mountPoint, "ntfs", flags, mountData);

    if (rc && errno == EROFS) {
        SLOGE("%s appears to be a read only filesystem - retrying mount RO", fsPath);
        flags |= MS_RDONLY;
        rc = mount(fsPath, mountPoint, "ntfs", flags, mountData);
    }
    return rc;
#else
    int rc;
    const char *args[11];
    char mountData[255];
    int status;

    sprintf(mountData,
			"locale=utf8,uid=%d,gid=%d,fmask=%o,dmask=%o,big_writes,async,noatime,nodiratime",
            ownerUid, ownerGid, permMask, permMask);

    args[0] = MOUNT_NTFS3G_PATH;
    args[1] = fsPath;
    args[2] = mountPoint;
    args[3] = "-o";
    args[4] = mountData;
    rc = android_fork_execvp(5, (char **)args, &status,
            false, true);

    return rc;
#endif

}

int Ntfs::format(const char *fsPath, unsigned int numSectors) {
    SLOGW("[lkj]:Skipping ntfs format\n");
    return 0;
}
