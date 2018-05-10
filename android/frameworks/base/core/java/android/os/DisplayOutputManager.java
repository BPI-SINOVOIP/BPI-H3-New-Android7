/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.os;

import android.content.Context;
import android.os.IBinder;
import android.os.IDisplayOutputManager;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;
import android.view.IWindowManager;
import android.view.Display;
import android.graphics.Rect;

public class DisplayOutputManager {
    public static final int DISPLAY_2D_ORIGINAL                  = 0;
    public static final int DISPLAY_2D_LEFT                      = 1;
    public static final int DISPLAY_2D_TOP                       = 2;
    public static final int DISPLAY_3D_LEFT_RIGHT_HDMI           = 3;
    public static final int DISPLAY_3D_TOP_BOTTOM_HDMI           = 4;
    public static final int DISPLAY_2D_DUAL_STREAM               = 5;
    public static final int DISPLAY_3D_DUAL_STREAM               = 6;

    public static final int DISPLAY_OUTPUT_TYPE_NONE             = 0;
    public static final int DISPLAY_OUTPUT_TYPE_LCD              = 1;
    public static final int DISPLAY_OUTPUT_TYPE_TV               = 2;
    public static final int DISPLAY_OUTPUT_TYPE_HDMI             = 4;
    public static final int DISPLAY_OUTPUT_TYPE_VGA              = 8;

    public static final int DISPLAY_TVFORMAT_480I                = 0;
    public static final int DISPLAY_TVFORMAT_576I                = 1;
    public static final int DISPLAY_TVFORMAT_480P                = 2;
    public static final int DISPLAY_TVFORMAT_576P                = 3;
    public static final int DISPLAY_TVFORMAT_720P_50HZ           = 4;
    public static final int DISPLAY_TVFORMAT_720P_60HZ           = 5;
    public static final int DISPLAY_TVFORMAT_1080I_50HZ          = 6;
    public static final int DISPLAY_TVFORMAT_1080I_60HZ          = 7;
    public static final int DISPLAY_TVFORMAT_1080P_24HZ          = 8;
    public static final int DISPLAY_TVFORMAT_1080P_50HZ          = 9;
    public static final int DISPLAY_TVFORMAT_1080P_60HZ          = 0xa;
    public static final int DISPLAY_TVFORMAT_3840_2160P_30HZ     = 0x1c;
    public static final int DISPLAY_TVFORMAT_3840_2160P_25HZ     = 0x1d;
    public static final int DISPLAY_TVFORMAT_3840_2160P_24HZ     = 0x1e;
    public static final int DISPLAY_TVFORMAT_4096_2160P_24HZ		 = 0x1f;
    public static final int DISPLAY_TVFORMAT_4096_2160P_25HZ		 = 0x20;
    public static final int DISPLAY_TVFORMAT_4096_2160P_30HZ		 = 0x21;
    public static final int DISPLAY_TVFORMAT_3840_2160P_60HZ     = 0x22;
    public static final int DISPLAY_TVFORMAT_4096_2160P_60HZ		 = 0x23;

    public static final int DISPLAY_TVFORMAT_PAL                 = 0xb;
    public static final int DISPLAY_TVFORMAT_PAL_SVIDEO          = 0xc;
    public static final int DISPLAY_TVFORMAT_PAL_CVBS_SVIDEO     = 0xd;
    public static final int DISPLAY_TVFORMAT_NTSC                = 0xe;
    public static final int DISPLAY_TVFORMAT_NTSC_SVIDEO         = 0xf;
    public static final int DISPLAY_TVFORMAT_NTSC_CVBS_SVIDEO    = 0x10;
    public static final int DISPLAY_TVFORMAT_PAL_M               = 0x11;
    public static final int DISPLAY_TVFORMAT_PAL_M_SVIDEO        = 0x12;
    public static final int DISPLAY_TVFORMAT_PAL_M_CVBS_SVIDEO   = 0x13;
    public static final int DISPLAY_TVFORMAT_PAL_NC              = 0x14;
    public static final int DISPLAY_TVFORMAT_PAL_NC_SVIDEO       = 0x15;
    public static final int DISPLAY_TVFORMAT_PAL_NC_CVBS_SVIDEO  = 0x16;

    public static final int DISPLAY_VGA_FORMAT_640x480P_60HZ     = 0x0;
    public static final int DISPLAY_VGA_FORMAT_800x600P_60HZ     = 0x1;
    public static final int DISPLAY_VGA_FORMAT_1024x768P_60HZ    = 0x2;
    public static final int DISPLAY_VGA_FORMAT_1280x768P_60HZ    = 0x3;
    public static final int DISPLAY_VGA_FORMAT_1280x800P_60HZ    = 0x4;
    public static final int DISPLAY_VGA_FORMAT_1366x768P_60HZ = 0x5;
    public static final int DISPLAY_VGA_FORMAT_1440x900P_60HZ = 0x6;
    public static final int DISPLAY_VGA_FORMAT_1920x1080P_60HZ = 0x7;
    public static final int DISPLAY_VGA_FORMAT_1920x1200P_60HZ = 0x8;

    /*@hide*/
    public static final int DISPLAY_OUTPUT_TYPE_MASK = 0xff00;
    /*@hide*/
    public static final int DISPLAY_OUTPUT_MODE_MASK = 0xff;

    private static final String TAG = "DisplayOutputManager";
    private final boolean DBG = true;
    private final Context mContext;
    private final IDisplayOutputManager mService;

    /*** @hide */ 
    public DisplayOutputManager(Context context,IDisplayOutputManager service){
	mContext = context;
        mService = service;
    }

    public int getDisplayOutput(int display){
        int retval = 0;
        try {
          retval = mService.getDisplayOutput(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
        
    }
    public int setDisplayOutput(int display, int format){
	int retval = 0;
        try {
          retval = mService.setDisplayOutput(display,format);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int getDisplayOutputType(int display){
        int retval = 0;
        try {
          retval = mService.getDisplayOutputType(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int getDisplayOutputMode(int display){
        int retval = 0;
        try {
          retval = mService.getDisplayOutputMode(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int setDisplayOutputMode(int display, int mode){
        int retval = 0;
        try {
          retval = mService.setDisplayOutputMode(display,mode);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int getDisplayOutputPixelFormat(int display) {
        int retval = 0;
        try {
          retval = mService.getDisplayOutputPixelFormat(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
	}

    public int setDisplayOutputPixelFormat(int display, int format) {
        int retval = 0;
        try {
          retval = mService.setDisplayOutputPixelFormat(display, format);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int getDisplayOutputCurDataspaceMode(int display) {
        int retval = 0;
        try {
          retval = mService.getDisplayOutputCurDataspaceMode(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
	}

    public int getDisplayOutputDataspaceMode(int display) {
        int retval = 0;
        try {
          retval = mService.getDisplayOutputDataspaceMode(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
	}

    public int setDisplayOutputDataspaceMode(int display, int mode) {
        int retval = 0;
        try {
          retval = mService.setDisplayOutputDataspaceMode(display, mode);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public boolean isSupportHdmiMode(int display, int mode){
        boolean retval = false;
        try {
          retval = mService.isSupportHdmiMode(display,mode);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int[] getSupportModes(int display, int type){
        int[] retval = null;
        try {
          retval = mService.getSupportModes(display,type);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

     /* interface for 3D mode setting */
    public int getDisplaySupport3DMode(int display){
        int retval = 0;
        try {
          retval = mService.getDisplaySupport3DMode(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int setDisplay3DMode(int display, int mode){
        int retval = 0;
        try {
          retval = mService.setDisplay3DMode(display, mode, 0);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int setDisplay3DMode(int display, int mode, int videoCropHeight){
        int retval = 0;
        try {
          retval = mService.setDisplay3DMode(display, mode, videoCropHeight);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int setDisplay3DLayerOffset(int display, int offset){
        int retval = 0;
        try {
          retval = mService.setDisplay3DLayerOffset(display,offset);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

     /* interface for screen margin/offset setting */
    public int[] getDisplayMargin(int display){
        int[] retval = null;
        try {
          retval = mService.getDisplayMargin(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int[] getDisplayOffset(int display){
        int[] retval = null;
        try {
          retval = mService.getDisplayOffset(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int setDisplayMargin(int display, int margin_x, int margin_y){
        int retval = 0;
        try {
          retval = mService.setDisplayMargin(display,margin_x,margin_y);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int setDisplayOffset(int display, int offset_x, int offset_y){
        int retval = 0;
        try {
          retval = mService.setDisplayOffset(display,offset_x,offset_y);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

     /* interface for display enhance effect */
    public int getDisplayEdge(int display){
        int retval = 0;
        try {
          retval = mService.getDisplayEdge(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int setDisplayEdge(int display, int edge){
        int retval = 0;
        try {
          retval = mService.setDisplayEdge(display,edge);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int getDisplayDetail(int display){
        int retval = 0;
        try {
          retval = mService.getDisplayDetail(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int setDisplayDetail(int display, int detail){
        int retval = 0;
        try {
          retval = mService.setDisplayDetail(display,detail);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int getDisplayBright(int display){
        int retval = 0;
        try {
          retval = mService.getDisplayBright(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int setDisplayBright(int display, int bright){
        int retval = 0;
        try {
          retval = mService.setDisplayBright(display,bright);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int getDisplayDenoise(int display){
        int retval = 0;
        try {
          retval = mService.getDisplayDenoise(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;       
    }

    public int setDisplayDenoise(int display, int denoise){
        int retval = 0;
        try {
          retval = mService.setDisplayDenoise(display,denoise);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int getDisplaySaturation(int display){
        int retval = 0;
        try {
          retval = mService.getDisplaySaturation(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int setDisplaySaturation(int display, int saturation){
        int retval = 0;
        try {
          retval = mService.setDisplaySaturation(display,saturation);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int getDisplayContrast(int display){
        int retval = 0;
        try {
          retval = mService.getDisplayContrast(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int setDisplayContrast(int display, int contrast){
        int retval = 0;
        try {
          retval = mService.setDisplayContrast(display,contrast);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int getDisplayEnhanceMode(int display){
        int retval = 0;
        try {
          retval = mService.getDisplayEnhanceMode(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    public int setDisplayEnhanceMode(int display, int mode){
        int retval = 0;
        try {
          retval = mService.setDisplayEnhanceMode(display, mode);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return retval;
    }

    static public int getDisplayModeFromFormat(int format) {
        return format & DISPLAY_OUTPUT_MODE_MASK;
    }

    static public int getDisplayTypeFromFormat(int format) {
        return (format & DISPLAY_OUTPUT_TYPE_MASK) >> 8;
    }

    public int makeDisplayFormat(int type, int mode) {
        return ((type << 8) & DISPLAY_OUTPUT_TYPE_MASK) | (mode & DISPLAY_OUTPUT_MODE_MASK);
    } 
}

