/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.server.policy;

import static android.app.ActivityManager.StackId.DOCKED_STACK_ID;
import static android.app.ActivityManager.StackId.FREEFORM_WORKSPACE_STACK_ID;
import static android.app.ActivityManager.StackId.HOME_STACK_ID;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.content.pm.PackageManager.FEATURE_TELEVISION;
import static android.content.pm.PackageManager.FEATURE_WATCH;
import static android.content.res.Configuration.EMPTY;
import static android.content.res.Configuration.UI_MODE_TYPE_CAR;
import static android.content.res.Configuration.UI_MODE_TYPE_MASK;
import static android.view.WindowManager.DOCKED_TOP;
import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_RIGHT;
import static android.view.WindowManager.TAKE_SCREENSHOT_FULLSCREEN;
import static android.view.WindowManager.TAKE_SCREENSHOT_SELECTED_REGION;
import static android.view.WindowManager.LayoutParams.*;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.CAMERA_LENS_COVERED;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.CAMERA_LENS_COVER_ABSENT;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.CAMERA_LENS_UNCOVERED;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_ABSENT;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_CLOSED;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_OPEN;

import android.app.ActivityManager;
import android.app.ActivityManager.StackId;
import android.app.ActivityManagerInternal;
import android.app.ActivityManagerInternal.SleepToken;
import android.app.ActivityManagerNative;
import android.app.AppOpsManager;
import android.app.IUiModeManager;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiPlaybackClient;
import android.hardware.hdmi.HdmiPlaybackClient.OneTouchPlayCallback;
import android.hardware.input.InputManagerInternal;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioManagerEx;
import android.media.AudioSystem;
import android.media.IAudioService;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.session.MediaSessionLegacyHelper;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.IBinder;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.service.dreams.DreamManagerInternal;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.speech.RecognizerIntent;
import android.telecom.TelecomManager;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.util.MutableBoolean;
import android.util.Slog;
import android.util.SparseArray;
import android.util.LongSparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.IApplicationToken;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.KeyCharacterMap;
import android.view.KeyCharacterMap.FallbackAction;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerInternal;
import android.view.WindowManagerPolicy;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import com.android.internal.R;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.policy.PhoneWindow;
import com.android.internal.policy.IShortcutService;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.ScreenShapeHelper;
import com.android.internal.widget.PointerLocationView;
import com.android.server.GestureLauncherService;
import com.android.server.LocalServices;
import com.android.server.policy.keyguard.KeyguardServiceDelegate;
import com.android.server.policy.keyguard.KeyguardServiceDelegate.DrawnListener;
import com.android.server.statusbar.StatusBarManagerInternal;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;

/* support the mouse mode */
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.widget.Toast;

import com.softwinner.Gpio;
import android.hardware.display.DisplayManagerPolicy2;
import java.lang.Runnable;

/**
 * WindowManagerPolicy implementation for the Android phone UI.  This
 * introduces a new method suffix, Lp, for an internal lock of the
 * PhoneWindowManager.  This is used to protect some internal state, and
 * can be acquired with either the Lw and Li lock held, so has the restrictions
 * of both of those when held.
 */
public class TvWindowManager extends PhoneWindowManager {
    private final String TAG = "TvWindowManager";
	private static final String FACTORY_TEST_APP= "com.softwinner.agingdragonbox";

    /* support the mouse mode */
    private int mLeftBtn  = -1;
    private int mMidBtn   = -1;
    private int mRightBtn = -1;
    private int mLeft     = -1;
    private int mRight    = -1;
    private int mTop      = -1;
    private int mBottom   = -1;
	/* add by Gary. start {{----------------------------------- */
    /* 2011-10-27 */
    /* support mute */
    boolean mIsMute = false;
    private static final int NF_ID_ENTER_KEY_MOUSE_MODE = 1;
    boolean mKeyEnterMouseMode = false;
    NotificationManager mNotifationManager = null;
    Notification mNotificationEnterKeyMouseMode = null;
    Toast        mMouseToast = null;
    Runnable     mPromptEnterMouseMode = new Runnable(){
        public void run() {
            if(mMouseToast == null){
                mMouseToast = Toast.makeText(mContext, com.android.internal.R.string.enter_key_mouse_mode, Toast.LENGTH_SHORT);
                if(mMouseToast == null){
                    Log.w(TAG, "Fail in creating toast.");
                }else {
                    mMouseToast.setGravity(Gravity.CENTER, 0, 0);
                }
            }
			if(mMouseToast != null){
				mMouseToast.setText(com.android.internal.R.string.enter_key_mouse_mode);
				mMouseToast.show();
			}
        }
    };
    Runnable     mPromptExitMouseMode = new Runnable(){
        public void run() {
            mMouseToast.setText(com.android.internal.R.string.exit_key_mouse_mode);
            mMouseToast.show();
        }
    };
    private UEventObserver mHDMIObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            boolean plugged = "1".equals(event.get("SWITCH_STATE"))
                        || event.get("STATE").startsWith("HDMI=1");
            Slog.w(TAG, "mHDMIObserver: " + plugged);
            setHdmiPlugged(plugged);
        }
    };

    @Override
    void initializeHdmiState() {
        boolean plugged = false;
        // watch for HDMI plug messages if the hdmi switch exists
        if (new File("/sys/devices/virtual/switch/hdmi/state").exists()) {
            mHDMIObserver.startObserving("DEVPATH=/devices/virtual/switch/hdmi");

            final String filename = "/sys/class/switch/hdmi/state";
            FileReader reader = null;
            try {
                reader = new FileReader(filename);
                char[] buf = new char[15];
                int n = reader.read(buf);
                if (n > 1) {
                    plugged = 0 != Integer.parseInt(new String(buf, 0, n-1));
                }
            } catch (IOException ex) {
                Slog.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
            } catch (NumberFormatException ex) {
                Slog.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ex) {
                    }
                }
            }
        } else if (new File("/sys/class/extcon/hdmi/state").exists()) {
            Slog.w(TAG, "Observe for kernel 4.4");
            mHDMIObserver.startObserving("DEVPATH=/devices/platform/soc/hdmi/extcon/hdmi");

            final String filename = "/sys/class/extcon/hdmi/state";
            FileReader reader = null;
            try {
                reader = new FileReader(filename);
                char[] buf = new char[15];
                int n = reader.read(buf);
                if (n > 1) {
                    plugged = 0 != Integer.parseInt(new String(buf, 5, 1));
                }
            } catch (IOException ex) {
                Slog.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
            } catch (NumberFormatException ex) {
                Slog.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ex) {
                    }
                }
            }
        }

        // This dance forces the code in setHdmiPlugged to run.
        // Always do this so the sticky intent is stuck (to false) if there is no hdmi.
        mHdmiPlugged = !plugged;
        setHdmiPlugged(!mHdmiPlugged);
    }

    private DisplayManagerPolicy2 mDmPolicy;
    Toast  mDisplayEnhanceToast;
    int mDisplayEnhance;
    boolean mDisplayEnhanceStay=true;
    String [] mDisplayEnhanceModeStrArray;
    static final int DISPALYMODE_SIZE = 3;

    Runnable mDisplayModeRunnable = new Runnable(){
        public void run() {
            int retEnhance;
            mHandler.removeCallbacks(mResetDisplayModeStay);
            mHandler.postDelayed(mResetDisplayModeStay,5000);
            if (mDisplayEnhanceStay)
            {
                mDisplayEnhanceStay=false;
                mDisplayEnhance = mDmPolicy.getDisplay2EnhanceMode(0);
                retEnhance = mDisplayEnhance;
                mDisplayEnhance = mDisplayEnhance % DISPALYMODE_SIZE;
            }
            else
            {
                mDisplayEnhance ++;
                mDisplayEnhance = mDisplayEnhance % DISPALYMODE_SIZE;
                retEnhance = mDmPolicy.setDisplay2EnhanceMode(0, mDisplayEnhance);
            }
            if(mDisplayEnhanceToast == null){
                mDisplayEnhanceToast = Toast.makeText(mContext, "", Toast.LENGTH_LONG);
            }
            if(0 > retEnhance) {
                mDisplayEnhanceToast.setText("Enhance mode failed");
            } else {
                mDisplayEnhanceToast.setText(mDisplayEnhanceModeStrArray[mDisplayEnhance]);
            }
            mDisplayEnhanceToast.show();
        }
    };

    Runnable mResetDisplayModeStay = new Runnable(){
        public void run() {
            mDisplayEnhanceStay = true;
        }
    };

    /** {@inheritDoc} */
    @Override
    public void init(Context context, IWindowManager windowManager,
            WindowManagerFuncs windowManagerFuncs) {
        super.init(context, windowManager, windowManagerFuncs);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(mFlickerIntentReceiver, filter);
        /* support the mouse mode */
        mLeftBtn  = SystemProperties.getInt("ro.softmouse.leftbtn.code", -1);
        mMidBtn   = SystemProperties.getInt("ro.softmouse.midbtn.code", -1);
        mRightBtn = SystemProperties.getInt("ro.softmouse.rightbtn.code", -1);
        mLeft     = SystemProperties.getInt("ro.softmouse.left.code", -1);
        mRight    = SystemProperties.getInt("ro.softmouse.right.code", -1);
        mTop      = SystemProperties.getInt("ro.softmouse.top.code", -1);
        mBottom   = SystemProperties.getInt("ro.softmouse.bottom.code", -1);
        Log.d(TAG, "mLeftBtn = " + mLeftBtn + ", mBottom = " + mBottom);
        mLedNormalFlicker = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_ledNormalFlicker);
        mLedStandbyAllOff = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_ledStandbyAllOff);
        mDisplayEnhanceModeStrArray = mContext.getResources().getStringArray(com.android.internal.R.array.display2_enhance_mode_names);
        mDmPolicy =  new DisplayManagerPolicy2(mContext);
    }

    /** {@inheritDoc} */
    @Override
    public long interceptKeyBeforeDispatching(WindowState win, KeyEvent event, int policyFlags) {
        super.interceptKeyBeforeDispatching(win, event, policyFlags);
        final boolean keyguardOn = keyguardOn();
        final int keyCode = event.getKeyCode();
        final int repeatCount = event.getRepeatCount();
        final int metaState = event.getMetaState();
        final int flags = event.getFlags();
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final boolean canceled = event.isCanceled();
        if(down && keyCode != KeyEvent.KEYCODE_POWER && keyCode != KeyEvent.KEYCODE_UNKNOWN){
            startFlicker();
        }
        /* support the mouse mode */
        if (keyCode == KeyEvent.KEYCODE_MOUSE) {
            Log.v(TAG, "it's KEYCODE_MOUSE key and down = " + down);
            if(!down){
                if(mNotifationManager == null){
                    mNotifationManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                    if(mNotifationManager == null){
                        Log.w(TAG, "Fail in get NotificationManager");
                        return -1;
                    }
                }
                if(mNotificationEnterKeyMouseMode == null){
                    mNotificationEnterKeyMouseMode = new Notification(com.android.internal.R.drawable.key_mouse,
                            mContext.getResources().getText(com.android.internal.R.string.enter_key_mouse_mode),
                            System.currentTimeMillis());
                    Intent intent = new Intent();
                    PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    mNotificationEnterKeyMouseMode.setLatestEventInfo(mContext, mContext.getResources().getText(com.android.internal.R.string.title_key_mouse_mode),
                            mContext.getResources().getText(com.android.internal.R.string.detail_key_mouse_mode), contentIntent);
                }
                if(mKeyEnterMouseMode)
                {
                    /* cancel notification */
                    mNotifationManager.cancel(NF_ID_ENTER_KEY_MOUSE_MODE);
                    /* show toast */
                    mHandler.removeCallbacks(mPromptExitMouseMode);
                    mHandler.post(mPromptExitMouseMode);

                    try
                    {
                        mWindowManager.keyExitMouseMode();

                        mKeyEnterMouseMode = false;
                    }
                    catch(RemoteException e)
                    {
                        Log.e(TAG,"key Exit Mouse Mode Failed!\n");
                    }
                }
                else
                {
                    /* send notification */
                    mNotifationManager.notify(NF_ID_ENTER_KEY_MOUSE_MODE, mNotificationEnterKeyMouseMode);
                    /* show toast */
                    mHandler.removeCallbacks(mPromptEnterMouseMode);
                    mHandler.post(mPromptEnterMouseMode);

                    try
                    {
                        mWindowManager.keyEnterMouseMode();

                        mWindowManager.keySetMouseDistance(Settings.System.getInt(mContext.getContentResolver(), Settings.System.MOUSE_ADVANCE, 30));

                        mWindowManager.keySetMouseBtnCode(mLeftBtn, mMidBtn, mRightBtn);

                        mWindowManager.keySetMouseMoveCode(mLeft, mRight, mTop, mBottom);

                        mKeyEnterMouseMode = true;
                    }
                    catch(RemoteException e)
                    {
                        Log.e(TAG,"key enterMouse Mode Failed!\n");
                    }
                }
            }
            return -1;
        }
        return 0;
    }

    private Handler mFlickerHandler = new Handler();
    private boolean mFlickerEnable  = true;
    private static final int FLICKER_INTERVAL = 50;
    private static final char portType = 'h';
    private static final int portNum = 20;
    private static final int on = 1;
    private static final int off = 0;
    private int mLedNormalFlicker;
    private boolean mLedStandbyAllOff;
    private BroadcastReceiver mFlickerIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Slog.d(TAG, "action = " + action);
            if (action.equals(Intent.ACTION_SCREEN_ON)) {
				Slog.d(TAG,"================================================== ACTION_SCREEN_ON");
                mFlickerEnable = true;
                mFlickerHandler.removeCallbacks(mStep1On);
                mFlickerHandler.removeCallbacks(mStep2Off);
                mFlickerHandler.removeCallbacks(mStep3On);
                Gpio.setNormalLedOn(true);
                Gpio.setStandbyLedOn(false);
            }else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                Slog.d(TAG,"================================================== ACTION_SCREEN_OFF");
                mFlickerEnable = false;
                mFlickerHandler.removeCallbacks(mStep1On);
                mFlickerHandler.removeCallbacks(mStep2Off);
                mFlickerHandler.removeCallbacks(mStep3On);
                if(mLedStandbyAllOff == false) {
                    Gpio.setNormalLedOn(false);
                    Gpio.setStandbyLedOn(true);
                } else {
                    Gpio.setNormalLedOn(false);
                    Gpio.setStandbyLedOn(false);
                }
            }
        }
    };

    private void startFlicker(){
        Slog.d("GpioService", "mFlickerEnable = " + mFlickerEnable);
        if(mFlickerEnable){
            //Gpio.setNormalLedOn(false);
			//Gpio.setStandbyLedOn(false);
            mFlickerHandler.removeCallbacks(mStep1On);
            mFlickerHandler.removeCallbacks(mStep2Off);
            mFlickerHandler.removeCallbacks(mStep3On);
            mFlickerHandler.postDelayed(mStep1On, FLICKER_INTERVAL);
        }
    }


    private Runnable mStep1On = new Runnable() {
        public void run(){
            if(mFlickerEnable){
                Gpio.setNormalLedOn(true);
                Gpio.setStandbyLedOn(false);
                mFlickerHandler.removeCallbacks(mStep2Off);
                mFlickerHandler.removeCallbacks(mStep3On);
                mFlickerHandler.postDelayed(mStep2Off, FLICKER_INTERVAL);
            }
        }
    };

    private Runnable mStep2Off = new Runnable() {
        public void run(){
            if(mFlickerEnable){
                if(mLedNormalFlicker == 0) {
                    Gpio.setNormalLedOn(true);
                    Gpio.setStandbyLedOn(false);
                } else if(mLedNormalFlicker == 1) {
                    Gpio.setNormalLedOn(true);
                    Gpio.setStandbyLedOn(true);
                } else if(mLedNormalFlicker == 2) {
                    Gpio.setNormalLedOn(false);
                    Gpio.setStandbyLedOn(false);
                } else if(mLedNormalFlicker == 3) {
                    Gpio.setNormalLedOn(false);
                    Gpio.setStandbyLedOn(true);
                }
                mFlickerHandler.removeCallbacks(mStep3On);
                mFlickerHandler.postDelayed(mStep3On, FLICKER_INTERVAL);
            }
        }
    };

    private Runnable mStep3On = new Runnable() {
        public void run(){
            if(mFlickerEnable){
                Gpio.setNormalLedOn(true);
                Gpio.setStandbyLedOn(false);
            }
        }
    };

    @Override
    public int interceptKeyBeforeQueueing(KeyEvent event, int policyFlags) {
        int keyCode = event.getKeyCode();
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
		Log.d(TAG,"key event key = " + KeyEvent.keyCodeToString(keyCode));
        if(down){
            switch(keyCode){
                case KeyEvent.KEYCODE_SETTINGS:
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                    break;
                case KeyEvent.KEYCODE_PROG_RED:
                    Log.d(TAG,"key event red");
                    mHandler.post(mDisplayModeRunnable);
                    break;
                case KeyEvent.KEYCODE_PROG_GREEN:
                    Log.d(TAG,"key event greed");
                    launchHomeFromHotKey();
                    break;
                case KeyEvent.KEYCODE_PROG_YELLOW:
                    Log.d(TAG,"key event yellow");
                    Intent intent1 = new Intent();
                    intent1.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings"));
                    intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent1);
                    break;
                case KeyEvent.KEYCODE_PROG_BLUE:
                    Log.d(TAG,"key event blue");
		    if(mFocusedWindow!=null&&!"com.softwinner.TvdFileManager".equals(mFocusedWindow.getOwningPackage())){
			Intent intent2 = new Intent();
			intent2.setComponent(new ComponentName("com.softwinner.TvdFileManager", "com.softwinner.TvdFileManager.MainUI"));
			intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			mContext.startActivity(intent2);
		    }
                    break;
                case KeyEvent.KEYCODE_MUTE:
                    Log.d(TAG, "key Mute");
                    handleMute(keyCode); //cmcc
                    break;
				case KeyEvent.KEYCODE_VOLUME_DOWN:
				case KeyEvent.KEYCODE_VOLUME_UP:
				case KeyEvent.KEYCODE_VOLUME_MUTE:
					if(mIsMute){
                        handleMute(keyCode);  //cmcc
                    }	
					break;	
		case KeyEvent.KEYCODE_MOVIE:
		    Log.v(TAG, "it's KEYCODE_MOVIE key and down = " + down);
		    if(mFocusedWindow!=null&&!"com.softwinner.TvdFileManager".equals(mFocusedWindow.getOwningPackage())){
			Intent intent3 = new Intent();
			intent3.setComponent(new ComponentName("com.softwinner.TvdFileManager", "com.softwinner.TvdFileManager.MainUI"));
			intent3.putExtra("media_type", "MEDIA_TYPE_VIDEO");
			intent3.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			mContext.startActivity(intent3);
		    }
                    break;
            }
        }else{//key up
            switch(keyCode){
                case KeyEvent.KEYCODE_HOME:
                    if(mFocusedWindow!=null){
                        String focusedWindowApp = mFocusedWindow.getOwningPackage();
                        Log.d(TAG,"the focus window is "+focusedWindowApp);
                        if(focusedWindowApp.equals(FACTORY_TEST_APP)){
                            return 0;
                        }
                    }else{
                        Log.v(TAG, "the mFocusedWindow is null");
                    }
                    break;
            }
        }
        return super.interceptKeyBeforeQueueing(event,policyFlags);
    }
    /* add by Gary. start {{----------------------------------- */
    /* 2011-10-27 */
    /* support mute */
	void handleMute(int keyCode){
        IAudioService audioService = getAudioService();
		final AudioManager mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioService == null) {
            return;
        }
            mBroadcastWakeLock.acquire();
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_MUTE:
                case KeyEvent.KEYCODE_MUTE:
					mAudioManager.setMasterMute((mIsMute = !mIsMute),AudioManager.FLAG_SHOW_UI);
                    break;
                default:
                    break;
            }
            //mute the ir key sound
            if (!mIsMute) {
                mAudioManager.loadSoundEffects();
            } else {
                mAudioManager.unloadSoundEffects();
            }
            Settings.System.putInt(mContext.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED,
                    !mIsMute ? 1 : 0);    
            mBroadcastWakeLock.release();
    }
	/* add by Gary. end   -----------------------------------}} */

    public void setInitialDisplaySize(Display display, int width, int height, int density){
        super.setInitialDisplaySize(display, width, height, density);
        mNavigationBarCanMove = false;
    }
}
