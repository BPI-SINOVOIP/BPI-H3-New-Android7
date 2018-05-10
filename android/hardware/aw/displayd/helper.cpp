
#include <stdlib.h>
#include <string.h>
#include <vector>
#include <cutils/log.h>
#include "helper.h"
#include "platform.h"
#include "debug.h"

int stringToMode(char *mode) {
	int size = sizeof(_display_modes) / sizeof(_display_modes[0]);
	int i;

	for (i = 0; i < size; i++) {
		if (!strcmp(mode, _display_modes[i].identify)) {
			return _display_modes[i].code;
		}
	}
	return -1;
}

const char *modeToString(int mode) {
	int size = sizeof(_display_modes) / sizeof(_display_modes[0]);
	int i;

	for (i = 0; i < size; i++) {
		if (_display_modes[i].code == mode)
			return _display_modes[i].identify;
	}
	return "unknow";
}

struct display_interface_info {
	const char *identify;
	int interface;
};

const static struct display_interface_info _display_interfaces[] = {
	{"HDMI", DISP_OUTPUT_TYPE_HDMI},
	{"CVBS", DISP_OUTPUT_TYPE_TV},
	{"LCD" , DISP_OUTPUT_TYPE_LCD },
	{"VGA" , DISP_OUTPUT_TYPE_VGA },
};

int stringToInterface(char *interface) {
	int size = sizeof(_display_interfaces) / sizeof(_display_interfaces[0]);
	int i;

	for (i = 0; i < size; i++) {
		if (!strcmp(interface, _display_interfaces[i].identify))
			return _display_interfaces[i].interface;
	}
	return -1;
}

const char *interfaceToString(int interface) {
	int size = sizeof(_display_interfaces) / sizeof(_display_interfaces[0]);
	int i;

	for (i = 0; i < size; i++) {
		if (interface == _display_interfaces[i].interface)
			return _display_interfaces[i].identify;
	}
	return "unknow";
}

void getHdmiModes(std::vector<int>* modes) {
#ifdef PLATFORM_SUN8IW7P1
	modes->clear();
	int size = sizeof(_display_modes) / sizeof(_display_modes[0]) - 2;
    int soc = getChipId();
	for (int i = 0; i < size; i++) {
        if ((soc == CHIP_H2) || (soc == CHIP_H2PLUS)) {
            if ((_display_modes[i].code == DISP_TV_MOD_3840_2160P_25HZ) ||
                    (_display_modes[i].code == DISP_TV_MOD_3840_2160P_30HZ)) {
                log_error("skip 4k resolution for H2/H2+");
                continue;
            }
        }
		modes->push_back(_display_modes[i].code);
    }
#else
	modes->clear();
	int size = sizeof(_display_modes) / sizeof(_display_modes[0]) - 2;
	for (int i = 0; i < size; i++) {
		modes->push_back(_display_modes[i].code);
    }
#endif
}

void getCvbsModes(std::vector<int>* modes) {
	modes->clear();
	modes->push_back(DISP_TV_MOD_NTSC);
	modes->push_back(DISP_TV_MOD_PAL);
}


