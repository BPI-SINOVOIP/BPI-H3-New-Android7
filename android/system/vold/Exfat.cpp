#include <stdio.h>
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

#include "Exfat.h"
#include "VoldUtil.h"

static char FSCK_EXFAT_PATH[] = "/system/bin/fsck.exfat";
static char MK_EXFAT_PATH[] = "/system/bin/mkfs.exfat";
static char MOUNT_EXFAT_PATH[]="/system/bin/mount.exfat";

extern "C" int mount(const char *, const char *, const char *, unsigned long, const void *);

int Exfat::check(const char *fsPath) {
	SLOGI("Exfat::check");
	if (access(FSCK_EXFAT_PATH, X_OK)) {
        SLOGW("Skipping fs checks\n");
        return 0;
    }

    int pass = 1;
    int rc = 0;
    do {
        const char *args[4];
        int status;
        args[0] = FSCK_EXFAT_PATH;
        args[1] = fsPath;

        rc = android_fork_execvp(2, (char **)args, &status,
                false, true);
        if (rc != 0) {
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
            SLOGE("Filesystem check failed (not a EXFAT filesystem)");
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

        default:
            SLOGE("Filesystem check failed (unknown exit code %d)", status);
            errno = EIO;
            return -1;
        }
    } while (0);

	return 0;
}

int Exfat::doMount(const char *fsPath, const char *mountPoint,
                       bool ro, bool remount, bool executable,
                       int ownerUid, int ownerGid, int permMask, bool createLost) {
    int rc;
    const char *args[11];
    char mountData[255];
    int status;

    SLOGE("Exfat::doMount");
    sprintf(mountData,
            "locale=utf8,uid=%d,gid=%d,fmask=%o,dmask=%o,noatime,nodiratime",
            ownerUid, ownerGid, permMask, permMask);

    args[0] = MOUNT_EXFAT_PATH;
    args[1] = fsPath;
    args[2] = mountPoint;
    args[3] = "-o";
    args[4] = mountData;
    rc = android_fork_execvp(5, (char **)args, &status,
            false, true);
	if( rc !=0 ) {
		SLOGE("Exfat::doMount error : %s", strerror(errno));
	}

    return rc;
}

int Exfat::format(const char *fsPath, unsigned int numSectors) {
	SLOGW("donnot support exfat format");
	return 0;
}
