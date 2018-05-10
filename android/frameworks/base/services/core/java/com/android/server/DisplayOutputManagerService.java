/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.server;


import android.content.Context;
import android.content.Intent;
import android.os.IDisplayOutputManager;
import android.os.SystemProperties;
import android.util.Slog;
import android.util.Log;
import java.util.ArrayList;
import android.widget.Toast;

/**
 * @hide
 */

public class DisplayOutputManagerService extends IDisplayOutputManager.Stub {
    private static final String TAG = "DisplayOutputManagerService";
    private static final boolean DBG = false;
    private static final String DISPLAYD_TAG = "DisplaydConnector";
    private Context mContext = null;
    private NativeDaemonConnector mConnector;

    class DisplaydResponseCode {
        public static final int InterfaceListResult = 110;
        public static final int ModeListResult      = 111;
        public static final int CommandOkay         = 200;
        public static final int OperationFailed     = 400;
        public static final int InterfaceChange     = 600;
    }

    public DisplayOutputManagerService(Context context){
        mContext = context;
        mConnector = new NativeDaemonConnector(
                            new DisplaydCallbackReceiver(), "displayd", 10, DISPLAYD_TAG, 160, null);
        Thread thread = new Thread(mConnector, DISPLAYD_TAG);
        thread.start();
        Slog.d(TAG,"DisplayOutputManagerService init");
    }

    class DisplaydCallbackReceiver implements INativeDaemonConnectorCallbacks {
        private Toast hotplugToast;

        public void onDaemonConnected() {
            Slog.d(TAG, "DisplayOutputManagerService NativeDaemonConnector connected");
        }
        public boolean onCheckHoldWakeLock(int code) {
            return false;
        }
        public boolean onEvent(int code, String raw, String[] cooked) {
            /* TODO */
            Slog.d(TAG, "event form daemon: " + raw);

            if (cooked[1].equals("hotplug")) {
                if (cooked.length < 4 || (!cooked[2].equals("hdmi") && !cooked[3].equals("cvbs")))
                    Slog.e(TAG, "Invalid message from daemon: " + raw);

                String toastText = String.format("%s plug %s !", cooked[2].toUpperCase(), cooked[3].equals("1") ? "in" : "out");
                if (hotplugToast == null) {
                    hotplugToast = Toast.makeText(mContext, "hotplugToast", Toast.LENGTH_LONG);
                }
                hotplugToast.setText(toastText);
                hotplugToast.show();
            }
            return true;
        }
    }

    private int executeGetRequest(String cmd, Object... args) {
        final NativeDaemonEvent event;
        try {
            event = mConnector.execute(cmd, args);
        } catch (NativeDaemonConnectorException e) {
            String exception = String.format("Exception: cmd(%s) return code(%d)", e.getCmd(), e.getCode());
            Slog.d(TAG, exception);
            return 0;
        }
        if ((event != null)
                && (event.getCode() == DisplaydResponseCode.CommandOkay))
            return Integer.parseInt(event.getMessage());
        return 0;
    }

    private String executeGetRequestRaw(String cmd, Object... args) {
        final NativeDaemonEvent event;
        try {
            event = mConnector.execute(cmd, args);
        } catch (NativeDaemonConnectorException e) {
            String exception = String.format("Exception: cmd(%s) return code(%d)", e.getCmd(), e.getCode());
            Slog.d(TAG, exception);
            return "unknow error";
        }
        if ((event != null)
                && (event.getCode() == DisplaydResponseCode.CommandOkay))
            return event.getMessage();
        return "unknow error";
    }

    private String[] executeGetRequestList(String cmd, Object... args) {
        try {
            return NativeDaemonEvent.filterMessageList(
                        mConnector.executeForList(cmd, args), DisplaydResponseCode.ModeListResult);
        } catch (NativeDaemonConnectorException e) {
            String exception = String.format("Exception: cmd(%s) return code(%d)", e.getCmd(), e.getCode());
            Slog.d(TAG, exception);
            return null;
        }
    }

    private int executeSetRequest(String cmd, Object... args) {
        final NativeDaemonEvent event;
        try {
            event = mConnector.execute(cmd, args);
        } catch (NativeDaemonConnectorException e) {
            String exception = String.format("Exception: cmd(%s) return code(%d)", e.getCmd(), e.getCode());
            Slog.d(TAG, exception);
            return -1;
        }
        if ((event != null) &&
                (event.getCode() == DisplaydResponseCode.CommandOkay))
            return 0;
        return -1;
    }

    /* interface for output mode/type setting */
    public int getDisplayOutput(int display){
        if(DBG)
       	Slog.d(TAG,"getDisplayOutput" );
        int format = executeGetRequest("interface", "GetFormat", display);
        return format;
    }
    public int setDisplayOutput(int display, int format){
        if(DBG)
	Slog.d(TAG,"setDisplayOutput" );
	return executeSetRequest("interface", "SetFormat", display, format);
    }

    public int getDisplayOutputType(int display){
        if(DBG)
	Slog.d(TAG,"getDisplayOutputType" );
        int type = executeGetRequest("interface", "GetType", display);
	return type;
    }

    public int getDisplayOutputMode(int display){
        if(DBG)
	Slog.d(TAG,"getDisplayOutputMode" );
        int mode = executeGetRequest("interface", "GetMode", display);
	return mode;
    }

    public int setDisplayOutputMode(int display, int mode){
        if(DBG)
	Slog.d(TAG,"setDisplayOutputMode" );
        return executeSetRequest("interface", "SetMode", display, mode);
    }

    public int getDisplayOutputPixelFormat(int display) {
        if(DBG)
            Slog.d(TAG, "getDisplayOutputPixelFormat");
        int format = executeGetRequest("interface", "GetPixelFormat", display);
        return format;
    }

    public int setDisplayOutputPixelFormat(int display, int format) {
        if(DBG)
            Slog.d(TAG, "setDisplayOutputPixelFormat");
        return executeSetRequest("interface", "SetPixelFormat", display, format);
    }

    public int getDisplayOutputCurDataspaceMode(int display) {
        if(DBG)
            Slog.d(TAG, "getDisplayCurOutputDataspaceMode");
        int mode = executeGetRequest("interface", "GetCurDataspace", display);
        return mode;
    }

    public int getDisplayOutputDataspaceMode(int display) {
        if(DBG)
            Slog.d(TAG, "getDisplayOutputDataspaceMode");
        int mode = executeGetRequest("interface", "GetDataspace", display);
        return mode;
    }

    public int setDisplayOutputDataspaceMode(int display, int mode) {
        if(DBG)
            Slog.d(TAG, "setDisplayOutputDataspaceMode");
        return executeSetRequest("interface", "SetDataspace", display, mode);
    }

    public boolean isSupportHdmiMode(int display, int mode){
        if(DBG)
	Slog.d(TAG,"isSupportHdmiMode" );
        String support = executeGetRequestRaw("interface", "CheckMode", display, mode);
        return support.equals("true");
    }

    public int[] getSupportModes(int display, int type){
        if(DBG)
	Slog.d(TAG,"getSupportModes" );
        String modes[] = executeGetRequestList("interface", "ListMode", display, type);
        int[] result = new int[modes.length];
        for (int i = 0; i < modes.length; i++) {
            result[i] = Integer.parseInt(modes[i]);
        }
        return result;
    }

     /* interface for 3D mode setting */
    public int getDisplaySupport3DMode(int display){
        if(DBG)
	Slog.d(TAG,"getDisplaySupport3DMode" );
        int support = executeGetRequest("utils", "GetSupport3DMode", display);
        return support;
    }

    public int setDisplay3DMode(int display, int mode, int videoCropHeight) {
        if(DBG)
            Slog.d(TAG, "setDisplay3DMode: mode="
                + mode + ", videoCropHeight=" + videoCropHeight);
        return executeSetRequest("utils", "Set3DLayerMode", display, mode, videoCropHeight);
    }

    public int setDisplay3DLayerOffset(int display, int offset){
        if(DBG)
	Slog.d(TAG,"setDisplay3DLayerOffset" );
        return executeSetRequest("utils", "Set3DLayerOffset", display, offset);
    }

     /* interface for screen margin/offset setting */
    public int[] getDisplayMargin(int display){
        if(DBG)
	Slog.d(TAG,"getDisplayMargin" );
        String[] margin = executeGetRequestRaw("overscan", "GetMargin", display).split(" ", 2);
        int[] result = new int[margin.length];
        for (int i = 0; i < margin.length; i++) {
            result[i] = Integer.parseInt(margin[i]);
        }
        return result;
    }

    public int[] getDisplayOffset(int display){
        if(DBG)
        Slog.d(TAG,"getDisplayOffset" );
        String[] offset = executeGetRequestRaw("overscan", "GetOffset", display).split(" ", 2);
        int[] result = new int[offset.length];
        for (int i = 0; i < offset.length; i++) {
            result[i] = Integer.parseInt(offset[i]);
        }
        return result;
    }

    public int setDisplayMargin(int display, int margin_x, int margin_y){
        if(DBG)
	Slog.d(TAG,"setDisplayMargin" );
        return executeSetRequest("overscan", "SetMargin", display, margin_x, margin_y);
    }

    public int setDisplayOffset(int display, int offset_x, int offset_y){
        if(DBG)
	Slog.d(TAG,"setDisplayOffset" );
        return executeSetRequest("overscan", "SetOffset", display, offset_x, offset_y);
    }

     /* interface for display enhance effect */
    public int getDisplayEdge(int display){
        if(DBG) Slog.d(TAG,"getDisplayEdge" );
        int edge = executeGetRequest("enhance", "GetEdge", display);
        return edge;
    }

    public int setDisplayEdge(int display, int edge){
        if(DBG) Slog.d(TAG,"setDisplayEdge" );
        return executeSetRequest("enhance", "SetEdge", display, edge);
    }

    public int getDisplayDetail(int display){
        if(DBG) Slog.d(TAG,"getDisplayDetail" );
        int detail = executeGetRequest("enhance", "GetDetail", display);
        return detail;
    }

    public int setDisplayDetail(int display, int detail){
        if(DBG) Slog.d(TAG,"setDisplayDetail" );
        return executeSetRequest("enhance", "SetDetail", display, detail);
    }

    public int getDisplayBright(int display){
        if(DBG) Slog.d(TAG,"getDisplayBright" );
        int bright = executeGetRequest("enhance", "GetBright", display);
        return bright;
    }

    public int setDisplayBright(int display, int bright){
        if(DBG) Slog.d(TAG,"setDisplayBright" );
        return executeSetRequest("enhance", "SetBright", display, bright);
    }

    public int getDisplayDenoise(int display){
        if(DBG) Slog.d(TAG,"getDisplayDenoise" );
        int denoise = executeGetRequest("enhance", "GetDenoise", display);
        return denoise;
    }

    public int setDisplayDenoise(int display, int denoise){
        if(DBG) Slog.d(TAG,"setDisplayDenoise" );
        return executeSetRequest("enhance", "SetDenoise", display, denoise);
    }

    public int getDisplaySaturation(int display){
        if(DBG) Slog.d(TAG,"getDisplaySaturation" );
        int saturation = executeGetRequest("enhance", "GetSaturation", display);
        return saturation;
    }

    public int setDisplaySaturation(int display, int saturation){
        if(DBG) Slog.d(TAG,"setDisplaySaturation" );
        return executeSetRequest("enhance", "SetSaturation", display, saturation);
    }

    public int getDisplayContrast(int display){
        if(DBG) Slog.d(TAG,"getDisplayContrast" );
        int contrast = executeGetRequest("enhance", "GetContrast", display);
        return contrast;
    }

    public int setDisplayContrast(int display, int contrast){
        if(DBG) Slog.d(TAG,"setDisplayContrast" );
        return executeSetRequest("enhance", "SetContrast", display, contrast);
    }

    public int getDisplayEnhanceMode(int display){
        if(DBG) Slog.d(TAG,"getDisplayEnhanceMode" );
        int mode = executeGetRequest("enhance", "GetMode", display);
        return mode;
    }

    public int setDisplayEnhanceMode(int display, int mode){
        if(DBG) Slog.d(TAG,"setDisplayEnhanceMode" );
        return executeSetRequest("enhance", "SetMode", display, mode);
    }
}
