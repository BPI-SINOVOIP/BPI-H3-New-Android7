#ifndef _SUN50IW6P1_H_
#define _SUN50IW6P1_H_

#include "platform.h"
#include "sunxi_display2.h"

#define CHIP_UNKNOWN (0xFFFFF)
#define CHIP_H3      (0x00090)
#define CHIP_H3s     (0x00091)
#define CHIP_H2      (0x00092)
#define CHIP_H2PLUS  (0x00093)

const static struct display_mode_info _display_modes[] = {

	{"720p-50Hz" , DISP_TV_MOD_720P_50HZ       },
	{"720p-60Hz" , DISP_TV_MOD_720P_60HZ       },

	{"1080p-24Hz", DISP_TV_MOD_1080P_24HZ      },
	{"1080p-50Hz", DISP_TV_MOD_1080P_50HZ      },
	{"1080p-60Hz", DISP_TV_MOD_1080P_60HZ      },
	{"1080i-50Hz", DISP_TV_MOD_1080I_50HZ      },
	{"1080i-60Hz", DISP_TV_MOD_1080I_60HZ      },

	{"4K-25",      DISP_TV_MOD_3840_2160P_25HZ },
	{"4K-30",      DISP_TV_MOD_3840_2160P_30HZ },

	/* The last two modes are just for cvbs,
	 * They must store in the end of this array */
	{"NTSC"      , DISP_TV_MOD_NTSC            },
	{"PAL"       , DISP_TV_MOD_PAL             },
};

int getChipId();

#endif
