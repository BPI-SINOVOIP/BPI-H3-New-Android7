

#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <cutils/log.h>
#include "debug.h"
#include "platform-sun8iw7p1.h"


int getChipId()
{
    int ret = 0;
    int dev = 0;
    char buf[16]={0};

    dev = open("/dev/sunxi_soc_info", O_RDONLY);
    if (dev < 0) {
        log_error("cannot open /dev/sunxi_soc_info\n");
        return  CHIP_UNKNOWN;
    }

    ret = ioctl(dev, 3, buf);
    close(dev);
    if(ret < 0) {
        log_error("ioctl err!\n");
        return  CHIP_UNKNOWN;
    }

    log_error("%s\n", buf);

    if (!strncmp(buf, "00000000", 8) ||
            !strncmp(buf, "00000081", 8)) {
        ret = CHIP_H3;
    }
    else if (!strncmp(buf, "00000042", 8) ||
            !strncmp(buf, "00000083", 8)) {
        ret = CHIP_H2PLUS;
    }
    else if (!strcmp(buf, "H2")) {
        ret = CHIP_H2;
    }
    else if (!strcmp(buf, "H3s")) {
        ret = CHIP_H3s;
    } else {
        ret = CHIP_UNKNOWN;
    }
    return ret;
}


