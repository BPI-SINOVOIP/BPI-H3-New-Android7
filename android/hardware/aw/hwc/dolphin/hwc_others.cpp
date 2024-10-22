/*
 * Copyright (C) 2007-2014 The Android Open Source Project
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

#include "hwc.h"

#define MARGIN_MIN_PERCENT 50
#define MARGIN_MAX_PERCENT 100

int is3DMode(__display_3d_mode mode)
{
    switch(mode)
    {
    case DISPLAY_3D_LEFT_RIGHT_HDMI:
    case DISPLAY_3D_TOP_BOTTOM_HDMI:
    case DISPLAY_3D_DUAL_STREAM:
    case DISPLAY_3D_LEFT_RIGHT_ALL:
    case DISPLAY_3D_TOP_BOTTOM_ALL:
        return 1;
    default:
        return 0;
    }
}

static int _hwc_device_set_3d_mode_per_display(int disp, __display_3d_mode new_mode)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo *PsDisplayInfo = &Globctx->SunxiDisplay[disp];
    __display_3d_mode old_mode = PsDisplayInfo->Current3DMode;

    if(old_mode == new_mode) {
        return 0;
    }

    if(PsDisplayInfo->VirtualToHWDisplay != EVALID_HWDISP_ID) {
        if(new_mode == DISPLAY_2D_ORIGINAL) {
            Globctx->CanForceGPUCom = 1;
        } else {
            Globctx->CanForceGPUCom = 0;
        }

        PsDisplayInfo->Current3DMode = new_mode;
        if ((PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_HDMI) &&
                (is3DMode(old_mode) != is3DMode(new_mode))) {

            ALOGD("###set 3D mode: old_mode[%d] --> new_mode[%d]###", old_mode, new_mode);
            int ret = 0;
            Globctx->outputMask = 1 << PsDisplayInfo->VirtualToHWDisplay;
            ret = hwcOutputSwitch(PsDisplayInfo, DISP_OUTPUT_TYPE_HDMI,
                    is3DMode(new_mode) ? DISP_TV_MOD_1080P_24HZ_3D_FP : PsDisplayInfo->setDispMode);
            if (ret) {
                PsDisplayInfo->Current3DMode = old_mode;
                ALOGE("switch mode return errror");
                return ret;
            }

            /* Wait here untill 3d output stable */
            if (is3DMode(PsDisplayInfo->Current3DMode))
                wait3dOuputStableAsync(4500);
            else
                wait3dOuputStableAsync(2000);
        }

        if (PsDisplayInfo->DisplayType == DISP_OUTPUT_TYPE_TV) {
            if (DISPLAY_3D_LEFT_RIGHT_HDMI == new_mode) {
                PsDisplayInfo->Current3DMode = DISPLAY_2D_LEFT;
            } else if (DISPLAY_3D_TOP_BOTTOM_HDMI == new_mode) {
                PsDisplayInfo->Current3DMode = DISPLAY_2D_TOP;
            } else if (DISPLAY_3D_DUAL_STREAM == new_mode) {
                PsDisplayInfo->Current3DMode = DISPLAY_2D_DUAL_STREAM;
            }
        }
    }
    return 0;
}

int _hwc_device_set_3d_mode(int disp, __display_3d_mode mode)
{
    disp;
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    int ret = 0;
    int i = 0;
    for( i = 0; i < Globctx->NumberofDisp; i++)
    {
        ret = _hwc_device_set_3d_mode_per_display(i, mode);
    }
    return 0;
}


int _hwc_device_set_backlight(int disp, int on_off,bool half)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long arg[4]={0};
    disp_rect window;
    if(disp == HWC_DISPLAY_PRIMARY
        && Globctx->SunxiDisplay[disp].DisplayType != DISP_OUTPUT_TYPE_HDMI)
    {
        arg[0] = Globctx->SunxiDisplay[disp].VirtualToHWDisplay;
        window.x = 0;
        window.y = 0;
        window.width = ioctl(Globctx->DisplayFd,DISP_GET_SCN_WIDTH,arg);
        window.height = ioctl(Globctx->DisplayFd,DISP_GET_SCN_HEIGHT,arg);
        arg[1] = (unsigned long)&window;
        if(half)
        {
            if(on_off)
            {
                window.width > window.height ? window.width/= 2 : window.height /= 2;
            }
            ioctl(Globctx->DisplayFd, DISP_SMBL_SET_WINDOW,arg);
            return 0;
        }
        if(on_off == 1 )
        {
            ioctl(Globctx->DisplayFd, DISP_SMBL_ENABLE,arg);
        }else{
            ioctl(Globctx->DisplayFd, DISP_SMBL_DISABLE,arg);
        }

    }
    return 0;
}

int _hwc_device_set_enhance_mode(int disp, int mode)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

	if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
		ALOGD("invalid disp(%d)", disp);
	}
	psDisplayInfo = &(Globctx->SunxiDisplay[disp]);

	char const *fileName = ENHANCE_MODE_ATTR;
	char values[4];
	sprintf(values, "%d", mode);
    int ret = writeStringToAttrFile(fileName, values, strlen(values));
	if (!ret) {
		char const *name = ENHANCE_MODE_PROPERTY;
		if (property_set(name, values) != 0) {
			ALOGD("set %s failed", name);
		}
	}
	return ret;
}

int _hwc_device_get_enhance_mode(int disp)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

    if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
        ALOGD("invalid disp(%d)", disp);
    }
    psDisplayInfo = &(Globctx->SunxiDisplay[disp]);
    if (HW_CHANNEL_PRIMARY == psDisplayInfo->VirtualToHWDisplay) {
        char const *fileName = ENHANCE_MODE_ATTR;
        char values[8] = {0};
        int len = readStringFromAttrFile(fileName, values, sizeof(values) / sizeof(values[0]));
        if (0 < len) {
            return atoi(values);
        }
    }

	return 0;
}

static int handle_unconnect_external_display(int /* disp */, int out_type, int out_mode)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo  *psDisplayInfo0 = &Globctx->SunxiDisplay[HWC_DISPLAY_PRIMARY];
    DisplayInfo  *psDisplayInfo1 = &Globctx->SunxiDisplay[HWC_DISPLAY_EXTERNAL];
    unsigned long arg[4] = {0};
    int ret = -1;

    if (0 == out_type) {
        ALOGD("allready closed external display");
        return 0;
    }

    ALOGD("init and open external display: output[%d,%d]", out_type, out_mode);
    int needExchange = 0;

    arg[0] = getHwDispByType(out_type);
    arg[1] = out_type;
    arg[2] = out_mode;
    ioctl(Globctx->DisplayFd, DISP_DEVICE_SWITCH, (unsigned long)arg);
    saveDispModeToFile(out_type, out_mode);

    disp_output dispOutput;
    dispOutput.type = out_type;
    dispOutput.mode = out_mode;
    initDisplayDevice(HWC_DISPLAY_EXTERNAL, arg[0], &dispOutput);
    if(arg[0] < (unsigned int)(psDisplayInfo0->VirtualToHWDisplay)) {
        ALOGD("need to exchange...");
        needExchange = 1;
        psDisplayInfo1->InitDisplayWidth =
                get_info_mode(psDisplayInfo0->DisplayMode, WIDTH);
        psDisplayInfo1->InitDisplayHeight =
                get_info_mode(psDisplayInfo0->DisplayMode, HEIGHT);
    } else {
        psDisplayInfo1->setblank = 0;
        psDisplayInfo1->SetPersentWidth  = psDisplayInfo0->SetPersentWidth;
        psDisplayInfo1->SetPersentHeight = psDisplayInfo0->SetPersentHeight;
    }
    Globctx->psHwcProcs->hotplug(Globctx->psHwcProcs, HWC_DISPLAY_EXTERNAL, 1);
    if (1 == needExchange) {
        ALOGD("need exchange output[%d,%d]<-->output[%d,%d]",
                psDisplayInfo0->DisplayType, psDisplayInfo0->DisplayMode,
                psDisplayInfo1->DisplayType, psDisplayInfo1->DisplayMode);
        hwcOutputExchange();// exchange output device between primary and external
        psDisplayInfo0->setblank = 0;
    }
    return 0;
}

static int handle_connected_external_display(int /* disp */, int out_type, int out_mode)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo  *psDisplayInfo0 = &Globctx->SunxiDisplay[HWC_DISPLAY_PRIMARY];
    DisplayInfo  *psDisplayInfo1 = &Globctx->SunxiDisplay[HWC_DISPLAY_EXTERNAL];
    unsigned long arg[4] = {0};
    int ret = -1;

    if (0 == out_type) {
        arg[0] = psDisplayInfo1->VirtualToHWDisplay;
        arg[1] = HWC_SET_POWER_MODE;
        arg[2] = HWC_K_POWER_MODE_OFF;
        ALOGD("close external display.");
        Globctx->psHwcProcs->hotplug(Globctx->psHwcProcs, HWC_DISPLAY_EXTERNAL, 0);
        hwcOutputSwitch(psDisplayInfo1, 0, 0);
        ioctl(Globctx->DisplayFd, DISP_HWC_COMMIT, (unsigned long)arg);
        return 0;
    }
    if (psDisplayInfo1->DisplayMode != out_mode) {
        if(DISP_TV_MOD_1080P_24HZ_3D_FP != out_mode
            && is3DMode(psDisplayInfo1->Current3DMode)) {
            psDisplayInfo1->Current3DMode = DISPLAY_2D_ORIGINAL;
        }
        ALOGD("set external display mode.");
        ret = hwcOutputSwitch(psDisplayInfo1, out_type, out_mode);
        if(ret) {
            ALOGD("set external display mode failed.");
            hwcOutputSwitch(psDisplayInfo1, 0, 0);
            return ret;
        } else {
            psDisplayInfo1->setDispMode = out_mode;
        }
    }
    return 0;
}

int _hwc_device_set_output_mode(int disp, int out_type, int out_mode)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo  *psDisplayInfo0 = &Globctx->SunxiDisplay[HWC_DISPLAY_PRIMARY];
    DisplayInfo  *psDisplayInfo1 = &Globctx->SunxiDisplay[HWC_DISPLAY_EXTERNAL];
    unsigned long arg[4] = {0};
    int ret = -1;

    if((0 != Globctx->ban4k)
      && (DISP_OUTPUT_TYPE_HDMI == out_type)
      && (DISP_TV_MOD_3840_2160P_30HZ <= out_mode)
      && (DISP_TV_MOD_4096_2160P_24HZ >= out_mode))
    {
        return -1;
    }

    if(HWC_DISPLAY_PRIMARY == disp)
    {
        int dispExternalOpened = psDisplayInfo1->VirtualToHWDisplay;
        int is_adapt_mode = (0xFF == out_mode) ? 1 : 0;
        if(0 == out_type)
        {
            return -1;
        }
        out_mode = resetDispMode(HWC_DISPLAY_PRIMARY, out_type, out_mode);
        if(out_type == psDisplayInfo0->DisplayType)
        {
            if(out_mode != psDisplayInfo0->DisplayMode)
            {
                ALOGD("switch on primary disp.");
                if(DISP_TV_MOD_1080P_24HZ_3D_FP != out_mode
                    && is3DMode(psDisplayInfo0->Current3DMode))
                {
                    psDisplayInfo0->Current3DMode = DISPLAY_2D_ORIGINAL;
                }
                ret = hwcOutputSwitch(psDisplayInfo0, out_type, out_mode);
                if (!ret)
                    psDisplayInfo0->setDispMode = out_mode;
            } else {
                ret = 0;
            }
            return ret;
        } else if (!is_adapt_mode
            && (out_type == psDisplayInfo1->DisplayType)) {
            if (out_mode != psDisplayInfo1->DisplayMode) {
                ALOGD("switch output on external disp.");
                if (DISP_TV_MOD_1080P_24HZ_3D_FP != out_mode
                    && is3DMode(psDisplayInfo1->Current3DMode)) {
                    psDisplayInfo1->Current3DMode = DISPLAY_2D_ORIGINAL;
                }
                ret = hwcOutputSwitch(psDisplayInfo1, out_type, out_mode);
                if (!ret)
                    psDisplayInfo0->setDispMode = out_mode;
            }
            return ret;
        }
        if(out_type != psDisplayInfo1->DisplayType)//fixme: if external disp opened ?
        {
            ALOGD("open new device on other hwdisp");
            memcpy((void *)psDisplayInfo1, (void *)psDisplayInfo0, sizeof(DisplayInfo));
            psDisplayInfo1->setDispMode = out_mode;
            if(hwcOutputSwitch(psDisplayInfo1, out_type, out_mode))
            {
                ALOGD("cannot open this type[%d]-mode[%d]!", out_type, out_mode);
                return -1;
            }
            psDisplayInfo1->VsyncEnable = 0;
        }
        ALOGD("###exchange output[%d,%d]<-->output[%d,%d]",
            psDisplayInfo0->DisplayType, psDisplayInfo0->DisplayMode,
            psDisplayInfo1->DisplayType, psDisplayInfo1->DisplayMode);
        hwcOutputExchange();//exchange output device between primary and external
        if(EVALID_HWDISP_ID == dispExternalOpened)
        {
            ALOGD("close external hwdisp after exchange.");
            arg[0] = psDisplayInfo1->VirtualToHWDisplay;
            arg[1] = HWC_SET_POWER_MODE;
            arg[2] = HWC_K_POWER_MODE_OFF;
            hwcOutputSwitch(psDisplayInfo1, 0, 0);
            ioctl(Globctx->DisplayFd, DISP_HWC_COMMIT, (unsigned long)arg);
        }
    }
    else if(HWC_DISPLAY_EXTERNAL == disp)
    {

#ifndef SYSRSL_SWITCH_ON_EX

        if(out_type == psDisplayInfo0->DisplayType)
        {
            ALOGD("can not open the DisplayType[%d] which was opened on primary display.",
                out_type);
            return -1;
        }
        out_mode = resetDispMode(HWC_DISPLAY_EXTERNAL, out_type, out_mode);
        if(EVALID_HWDISP_ID == psDisplayInfo1->VirtualToHWDisplay)
        {
            handle_unconnect_external_display(disp, out_type, out_mode);
            return 0;
        }
        else
        {
            handle_connected_external_display(disp, out_type, out_mode);
            return 0;
        }

#else
//# here is defined SYSRSL_SWITCH_ON_EX begin
        int init_width = 0;
        int init_height = 0;
        int need_reset = 0;
        int dispP2P = 0;

        if((DISP_DUAL_POLICY == Globctx->dispPolicy)
            || (!(dispP2P = isDisplayP2P())))
        {
            ALOGD("dispP2P=%d, dispPolicy=%d", dispP2P, Globctx->dispPolicy);
            return 0;
        }
        init_width = get_info_mode(out_mode , WIDTH);
        init_height = get_info_mode(out_mode, HEIGHT);
        if((0 < MAX_INIT_DISP_WIDTH)
            && (init_width > MAX_INIT_DISP_WIDTH))
        {
            init_width = MAX_INIT_DISP_WIDTH;
        }
        if((0 < MAX_INIT_DISP_HEIGHT)
            && (init_height > MAX_INIT_DISP_HEIGHT))
        {
            init_height = MAX_INIT_DISP_HEIGHT;
        }
        out_mode = resetDispMode(HWC_DISPLAY_EXTERNAL, out_type, out_mode);
        if((EVALID_HWDISP_ID == psDisplayInfo1->VirtualToHWDisplay)
            || ((out_type == psDisplayInfo1->DisplayType)
            && (out_mode == psDisplayInfo1->DisplayMode)))
        {
            ALOGD("###FIXME: psDisplayInfo1 hwdisp=%d. type=%d, mode=%d",
                psDisplayInfo1->VirtualToHWDisplay,
                psDisplayInfo1->DisplayType,
                psDisplayInfo1->DisplayMode);
            return 0;
        }

        if((init_height != psDisplayInfo1->InitDisplayHeight)
            || (init_width != psDisplayInfo1->InitDisplayWidth))
        {
            ALOGD("need_reset");
            need_reset = 1;
            Globctx->psHwcProcs->hotplug(Globctx->psHwcProcs, HWC_DISPLAY_EXTERNAL, 0);
        }
        psDisplayInfo1->setDispMode = out_mode;
        ret = hwcOutputSwitch(psDisplayInfo1, out_type, out_mode);
        saveDispModeToFile(out_type, out_mode);
        if(1 == need_reset)
        {
            psDisplayInfo1->InitDisplayWidth = init_width;
            psDisplayInfo1->InitDisplayHeight = init_height;
            ALOGD("need reset: init[%d,%d]", psDisplayInfo1->InitDisplayWidth,
                psDisplayInfo1->InitDisplayHeight);
            Globctx->psHwcProcs->hotplug(Globctx->psHwcProcs, HWC_DISPLAY_EXTERNAL, 1);
        }
        Globctx->psHwcProcs->invalidate(Globctx->psHwcProcs);
        if(1 == need_reset)
            return 0xFF;
        else
            return 0;
#endif //# here is defined SYSRSL_SWITCH_ON_EX end
    }
    else
    {
        ALOGW("[ERR]disp = %d", disp);
        return -1;
    }
    return 0;
}

int _hwc_device_set_margin(int disp, int hpercent, int vpercent)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo   *PsDisplayInfo = &Globctx->SunxiDisplay[disp];
    if(PsDisplayInfo->VirtualToHWDisplay != EVALID_HWDISP_ID)
    {
        PsDisplayInfo->SetPersentWidth = (hpercent < MARGIN_MIN_PERCENT) ? MARGIN_MIN_PERCENT :
            ((hpercent > MARGIN_MAX_PERCENT) ? MARGIN_MAX_PERCENT : hpercent);
        PsDisplayInfo->SetPersentHeight = (vpercent < MARGIN_MIN_PERCENT) ? MARGIN_MIN_PERCENT :
            ((vpercent > MARGIN_MAX_PERCENT) ? MARGIN_MAX_PERCENT : vpercent);
        saveDispMarginToFile(PsDisplayInfo->SetPersentWidth, PsDisplayInfo->SetPersentHeight);
    }
    return 0;
}

int _hwc_device_is_support_hdmi_mode(int disp,int mode)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long arg[4] = {0};
    int ret;

    arg[0] = Globctx->SunxiDisplay[disp].VirtualToHWDisplay;
    arg[1] = mode;
    ret = ioctl(Globctx->DisplayFd, DISP_HDMI_SUPPORT_MODE, (unsigned long)arg);
    if(ret)
    {
        ALOGD("supported: args[%ld,%d]", arg[0], mode);
        return 1;
    }
    ALOGD("not supported: args[%ld,%d]", arg[0], mode);
    return 0;
}

int _hwc_device_get_current_mode(int hwdisp)
{
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    unsigned long arg[4] = {0};
    disp_output dispOutput;
    int ret;

    arg[0] = hwdisp;
    arg[1] = (unsigned long)&dispOutput;
    ret = ioctl(Globctx->DisplayFd, DISP_GET_OUTPUT, (unsigned long)arg);
    if (ret != 0) {
        return DISP_DEFAULT_HDMI_MODE;
    }
    return dispOutput.mode;
}

int _hwc_device_get_output_type(int disp)
{
    DisplayInfo   *psDisplayInfo = &(gSunxiHwcDevice.SunxiDisplay[disp]);
    return psDisplayInfo->DisplayType;
}

int _hwc_device_get_output_mode(int disp)
{
    DisplayInfo   *psDisplayInfo = &(gSunxiHwcDevice.SunxiDisplay[disp]);
    return psDisplayInfo->setDispMode;
}

int _hwc_device_set_screenradio(int disp, int radioType)
{
    disp;
    SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
    DisplayInfo *psDisplayInfo = NULL;
    switch(radioType)
    {
    case SCREEN_AUTO:
    case SCREEN_FULL:
        for(int i = 0; i < NUMBEROFDISPLAY; i++)
        {
            psDisplayInfo = &(Globctx->SunxiDisplay[i]);
            psDisplayInfo->screenRadio = radioType;
        }
        break;
    default:
        break;
    }
    if(Globctx->psHwcProcs != NULL){
        Globctx->psHwcProcs->invalidate(Globctx->psHwcProcs);
    }
    return 0;
}

int _hwc_device_set_enhance_bright(int disp, int value)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

	if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
		ALOGD("invalid disp(%d)", disp);
	}
	psDisplayInfo = &(Globctx->SunxiDisplay[disp]);

	if (value < 0)
		value = 0;
	else if (value > 10)
		value = 10;

	char const *fileName = ENHANCE_BRIGHT_ATTR;
	char values[4];
	sprintf(values, "%d", value);
    int ret = writeStringToAttrFile(fileName, values, strlen(values));
	if (!ret) {
		char const *name = ENHANCE_BRIGHT_PROPERTY;
		if (property_set(name, values) != 0) {
			ALOGE("set %s failed", name);
		}
	} else {
		return -1;
	}
	return value;
}

int _hwc_device_get_enhance_bright(int disp)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

    if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
        ALOGD("invalid disp(%d)", disp);
    }
    psDisplayInfo = &(Globctx->SunxiDisplay[disp]);
    if (HW_CHANNEL_PRIMARY == psDisplayInfo->VirtualToHWDisplay) {
        char const *fileName = ENHANCE_BRIGHT_ATTR;
        char values[8] = {0};
        int len = readStringFromAttrFile(fileName, values, sizeof(values) / sizeof(values[0]));
        if (0 < len) {
            return atoi(values);
        }
    }

	return 0;
}

int _hwc_device_set_enhance_contrast(int disp, int value)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

	if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
		ALOGD("invalid disp(%d)", disp);
	}
	psDisplayInfo = &(Globctx->SunxiDisplay[disp]);

	if (value < 0)
		value = 0;
	else if (value > 10)
		value = 10;

	char const *fileName = ENHANCE_CONTRAST_ATTR;
	char values[4];
	sprintf(values, "%d", value);
    int ret = writeStringToAttrFile(fileName, values, strlen(values));
	if (!ret) {
		char const *name = ENHANCE_CONTRAST_PROPERTY;
		if (property_set(name, values) != 0) {
			ALOGE("set %s failed", name);
		}
	} else {
		return -1;
	}
	return value;
}

int _hwc_device_get_enhance_contrast(int disp)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

    if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
        ALOGD("invalid disp(%d)", disp);
    }
    psDisplayInfo = &(Globctx->SunxiDisplay[disp]);
    if (HW_CHANNEL_PRIMARY == psDisplayInfo->VirtualToHWDisplay) {
        char const *fileName = ENHANCE_CONTRAST_ATTR;
        char values[8] = {0};
        int len = readStringFromAttrFile(fileName, values, sizeof(values) / sizeof(values[0]));
        if (0 < len) {
            return atoi(values);
        }
    }

	return 0;
}

int _hwc_device_set_enhance_saturation(int disp, int value)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

	if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
		ALOGD("invalid disp(%d)", disp);
	}
	psDisplayInfo = &(Globctx->SunxiDisplay[disp]);

	if (value < 0)
		value = 0;
	else if (value > 10)
		value = 10;

	char const *fileName = ENHANCE_SATURATION_ATTR;
	char values[4];
	sprintf(values, "%d", value);
    int ret = writeStringToAttrFile(fileName, values, strlen(values));
	if (!ret) {
		char const *name = ENHANCE_SATURATION_PROPERTY;
		if (property_set(name, values) != 0) {
			ALOGE("set %s failed", name);
		}
	} else {
		return -1;
	}
	return value;
}

int _hwc_device_get_enhance_saturation(int disp)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

    if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
        ALOGD("invalid disp(%d)", disp);
    }
    psDisplayInfo = &(Globctx->SunxiDisplay[disp]);
    if (HW_CHANNEL_PRIMARY == psDisplayInfo->VirtualToHWDisplay) {
        char const *fileName = ENHANCE_SATURATION_ATTR;
        char values[8] = {0};
        int len = readStringFromAttrFile(fileName, values, sizeof(values) / sizeof(values[0]));
        if (0 < len) {
            return atoi(values);
        }
    }

	return 0;
}

int _hwc_device_set_enhance_denoise(int disp, int value)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

	if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
		ALOGD("invalid disp(%d)", disp);
	}
	psDisplayInfo = &(Globctx->SunxiDisplay[disp]);

	if (value < 0)
		value = 0;
	else if (value > 10)
		value = 10;

	char const *fileName = ENHANCE_DENOISE_ATTR;
	char values[4];
	sprintf(values, "%d", value);
    int ret = writeStringToAttrFile(fileName, values, strlen(values));
	if (!ret) {
		char const *name = ENHANCE_DENOISE_PROPERTY;
		if (property_set(name, values) != 0) {
			ALOGE("set %s failed", name);
		}
	} else {
		return -1;
	}
	return value;
}

int _hwc_device_get_enhance_denoise(int disp)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

    if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
        ALOGD("invalid disp(%d)", disp);
    }
    psDisplayInfo = &(Globctx->SunxiDisplay[disp]);
    if (HW_CHANNEL_PRIMARY == psDisplayInfo->VirtualToHWDisplay) {
        char const *fileName = ENHANCE_DENOISE_ATTR;
        char values[8] = {0};
        int len = readStringFromAttrFile(fileName, values, sizeof(values) / sizeof(values[0]));
        if (0 < len) {
            return atoi(values);
        }
    }

	return 0;
}

int _hwc_device_set_enhance_detail(int disp, int value)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

	_hwc_device_set_enhance_edge(disp, value);
	if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
		ALOGD("invalid disp(%d)", disp);
	}
	psDisplayInfo = &(Globctx->SunxiDisplay[disp]);

	if (value < 0)
		value = 0;
	else if (value > 10)
		value = 10;

	char const *fileName = ENHANCE_DETAIL_ATTR;
	char values[4];
	sprintf(values, "%d", value);
    int ret = writeStringToAttrFile(fileName, values, strlen(values));
	if (!ret) {
		char const *name = ENHANCE_DETAIL_PROPERTY;
		if (property_set(name, values) != 0) {
			ALOGE("set %s failed", name);
		}
	} else {
		return -1;
	}
	return value;
}

int _hwc_device_get_enhance_detail(int disp)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

    if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
        ALOGD("invalid disp(%d)", disp);
    }
    psDisplayInfo = &(Globctx->SunxiDisplay[disp]);
    if (HW_CHANNEL_PRIMARY == psDisplayInfo->VirtualToHWDisplay) {
        char const *fileName = ENHANCE_DETAIL_ATTR;
        char values[8] = {0};
        int len = readStringFromAttrFile(fileName, values, sizeof(values) / sizeof(values[0]));
        if (0 < len) {
            return atoi(values);
        }
    }

	return 0;
}

int _hwc_device_set_enhance_edge(int disp, int value)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

	if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
		ALOGD("invalid disp(%d)", disp);
	}
	psDisplayInfo = &(Globctx->SunxiDisplay[disp]);

	if (value < 0)
		value = 0;
	else if (value > 10)
		value = 10;

	char const *fileName = ENHANCE_EDGE_ATTR;
	char values[4];
	sprintf(values, "%d", value);
    int ret = writeStringToAttrFile(fileName, values, strlen(values));
	if (!ret) {
	} else {
		return -1;
	}
	return value;
}

int _hwc_device_get_enhance_edge(int disp)
{
    SUNXI_hwcdev_context_t *Globctx= &gSunxiHwcDevice;
    DisplayInfo   *psDisplayInfo;

    if (disp >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
        ALOGD("invalid disp(%d)", disp);
    }
    psDisplayInfo = &(Globctx->SunxiDisplay[disp]);
    if (HW_CHANNEL_PRIMARY == psDisplayInfo->VirtualToHWDisplay) {
        char const *fileName = ENHANCE_EDGE_ATTR;
        char values[8] = {0};
        int len = readStringFromAttrFile(fileName, values, sizeof(values) / sizeof(values[0]));
        if (0 < len) {
            return atoi(values);
        }
    }

	return 0;
}
