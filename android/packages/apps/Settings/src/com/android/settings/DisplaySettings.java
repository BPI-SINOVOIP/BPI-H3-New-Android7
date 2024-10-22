/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.UiModeManager;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.os.AsyncTask;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.hardware.display.DisplayManager;
import android.os.DisplayOutputManager;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.DropDownPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import android.view.IWindowManager;
import android.view.Display;
import android.os.ServiceManager;
import android.util.AndroidException;
import android.os.RemoteException;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.view.RotationPolicy;
import com.android.settings.accessibility.ToggleFontSizePreferenceFragment;
import com.android.settings.dashboard.SummaryLoader;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedPreference;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static android.provider.Settings.Secure.CAMERA_DOUBLE_TAP_POWER_GESTURE_DISABLED;
import static android.provider.Settings.Secure.CAMERA_GESTURE_DISABLED;
import static android.provider.Settings.Secure.DOUBLE_TAP_TO_WAKE;
import static android.provider.Settings.Secure.DOZE_ENABLED;
import static android.provider.Settings.Secure.WAKE_GESTURE_ENABLED;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import static com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, Indexable {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;
		private static final int FALLBACK_DISPLAY_MODE_TIMEOUT = 10;
    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_SCREEN_SAVER = "screensaver";
    private static final String KEY_LIFT_TO_WAKE = "lift_to_wake";
    private static final String KEY_DOZE = "doze";
    private static final String KEY_TAP_TO_WAKE = "tap_to_wake";
    private static final String KEY_AUTO_BRIGHTNESS = "auto_brightness";
    private static final String KEY_AUTO_ROTATE = "auto_rotate";
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_CAMERA_GESTURE = "camera_gesture";
    private static final String KEY_CAMERA_DOUBLE_TAP_POWER_GESTURE
            = "camera_double_tap_power_gesture";
    private static final String KEY_WALLPAPER = "wallpaper";
    private static final String KEY_VR_DISPLAY_PREF = "vr_display_pref";
    private DisplayOutputManager mDisplayManager;
    private static final String KEY_TV_OUTPUT_MODE = "display_output_mode";
    private static final String KEY_HDMI_OUTPUT_MODE = "hdmi_output_mode";
    private static final String KEY_HDMI_OUTPUT_MODE_720P = "hdmi_output_mode_720p";
    private static final String KEY_COLOR_SETTING_CATE = "display_color_setting_cate";
    private static final String KEY_DISPLAY_OUTPUT_MODE_CATE = "display_output_mode_cate";
    private static final String PLATFORM_DIST_PATH = "/sys/class/sunxi_info/sys_info";
    private static final String H2_1 = "00000083";
    private static final String H2_2 = "00000042";

    //yangtingrui add begin
    private static final String KEY_PIP_SCREEN_SET = "pip_screen_set";
    //yangtingrui add end

    private static final int DLG_GLOBAL_CHANGE_WARNING = 1;
    private static final int DLG_RESOLUTION_CHANGE_WARNING = 11;
    private Preference mFontSizePref;
    private ListPreference mOutputMode;
    private ListPreference mHdmiOutputModePreference;

    private TimeoutListPreference mScreenTimeoutPreference;
    private ListPreference mNightModePreference;
    private Preference mScreenSaverPreference;
    private SwitchPreference mLiftToWakePreference;
    private SwitchPreference mDozePreference;
    private SwitchPreference mTapToWakePreference;
    private SwitchPreference mAutoBrightnessPreference;
    private SwitchPreference mCameraGesturePreference;
    private SwitchPreference mCameraDoubleTapPowerGesturePreference;

    // yangtingrui add begin
    private Preference mPipSetPreference=null;
    // yangtingrui add end

    private boolean isSupport = false;
    private boolean isSameMode = false;

    private String oldValue;
    private String newValue;
    private int format = 0;
    private boolean dialogTipsFlag;
    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.DISPLAY;
    }
    
    private boolean bIsH2Platform() {
    	try {
    		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(PLATFORM_DIST_PATH)));
        	String line = null;
        	while((line = br.readLine())!=null) {
        		if(line.contains("sunxi_chiptype"))
        			break;
        	}
        	if(line.contains(H2_1)||line.contains(H2_2)) {
        		return true;
        	}else {
        		return false;
        	}
		} catch (Exception e) {
			return false;
		}
    	
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Activity activity = getActivity();
        final ContentResolver resolver = activity.getContentResolver();

        addPreferencesFromResource(R.xml.display_settings);

        mScreenSaverPreference = findPreference(KEY_SCREEN_SAVER);
        if (mScreenSaverPreference != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_dreamsSupported) == false) {
            getPreferenceScreen().removePreference(mScreenSaverPreference);
        }

        mScreenTimeoutPreference = (TimeoutListPreference) findPreference(KEY_SCREEN_TIMEOUT);

        mFontSizePref = findPreference(KEY_FONT_SIZE);
        mHdmiOutputModePreference = (ListPreference)findPreference(KEY_HDMI_OUTPUT_MODE);
        if(bIsH2Platform()) {
        	mHdmiOutputModePreference.setEntries(Resources.getSystem().getIdentifier("support_tv_format_entries_hdmi_h2", "array", "android"));
        	mHdmiOutputModePreference.setEntryValues(Resources.getSystem().getIdentifier("support_tv_format_values_hdmi_h2", "array", "android"));
        }
        mOutputMode = (ListPreference)findPreference(KEY_SCREEN_TIMEOUT);

        if (isAutomaticBrightnessAvailable(getResources())) {
            mAutoBrightnessPreference = (SwitchPreference) findPreference(KEY_AUTO_BRIGHTNESS);
            mAutoBrightnessPreference.setOnPreferenceChangeListener(this);
        } else {
            removePreference(KEY_AUTO_BRIGHTNESS);
        }

        if (isLiftToWakeAvailable(activity)) {
            mLiftToWakePreference = (SwitchPreference) findPreference(KEY_LIFT_TO_WAKE);
            mLiftToWakePreference.setOnPreferenceChangeListener(this);
        } else {
            removePreference(KEY_LIFT_TO_WAKE);
        }

        if (isDozeAvailable(activity)) {
            mDozePreference = (SwitchPreference) findPreference(KEY_DOZE);
            mDozePreference.setOnPreferenceChangeListener(this);
        } else {
            removePreference(KEY_DOZE);
        }

        if (isTapToWakeAvailable(getResources())) {
            mTapToWakePreference = (SwitchPreference) findPreference(KEY_TAP_TO_WAKE);
            mTapToWakePreference.setOnPreferenceChangeListener(this);
        } else {
            removePreference(KEY_TAP_TO_WAKE);
        }

        if (isCameraGestureAvailable(getResources())) {
            mCameraGesturePreference = (SwitchPreference) findPreference(KEY_CAMERA_GESTURE);
            mCameraGesturePreference.setOnPreferenceChangeListener(this);
        } else {
            removePreference(KEY_CAMERA_GESTURE);
        }

        if (isCameraDoubleTapPowerGestureAvailable(getResources())) {
            mCameraDoubleTapPowerGesturePreference
                    = (SwitchPreference) findPreference(KEY_CAMERA_DOUBLE_TAP_POWER_GESTURE);
            mCameraDoubleTapPowerGesturePreference.setOnPreferenceChangeListener(this);
        } else {
            removePreference(KEY_CAMERA_DOUBLE_TAP_POWER_GESTURE);
        }
	removePreference(KEY_CAMERA_DOUBLE_TAP_POWER_GESTURE);

        if (RotationPolicy.isRotationLockToggleVisible(activity)) {
            DropDownPreference rotatePreference =
                    (DropDownPreference) findPreference(KEY_AUTO_ROTATE);
            int rotateLockedResourceId;
            // The following block sets the string used when rotation is locked.
            // If the device locks specifically to portrait or landscape (rather than current
            // rotation), then we use a different string to include this information.
            if (allowAllRotations(activity)) {
                rotateLockedResourceId = R.string.display_auto_rotate_stay_in_current;
            } else {
                if (RotationPolicy.getRotationLockOrientation(activity)
                        == Configuration.ORIENTATION_PORTRAIT) {
                    rotateLockedResourceId =
                            R.string.display_auto_rotate_stay_in_portrait;
                } else {
                    rotateLockedResourceId =
                            R.string.display_auto_rotate_stay_in_landscape;
                }
            }
            rotatePreference.setEntries(new CharSequence[] {
                    activity.getString(R.string.display_auto_rotate_rotate),
                    activity.getString(rotateLockedResourceId),
            });
            rotatePreference.setEntryValues(new CharSequence[] { "0", "1" });
            rotatePreference.setValueIndex(RotationPolicy.isRotationLocked(activity) ?
                    1 : 0);
            rotatePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final boolean locked = Integer.parseInt((String) newValue) != 0;
                    MetricsLogger.action(getActivity(), MetricsEvent.ACTION_ROTATION_LOCK,
                            locked);
                    RotationPolicy.setRotationLock(activity, locked);
                    return true;
                }
            });
        } else {
            removePreference(KEY_AUTO_ROTATE);
        }
        mDisplayManager = (DisplayOutputManager)getActivity().getSystemService(
                Context.DISPLAYOUTPUT_SERVICE);
        mOutputMode = (ListPreference) findPreference(KEY_TV_OUTPUT_MODE);
	if(mOutputMode!=null){
		format = mDisplayManager.getDisplayOutput(android.view.Display.TYPE_BUILT_IN);
		Log.d(TAG,"format = " + format);
		mOutputMode.setValue(Integer.toHexString(format));
	}
        if (isVrDisplayModeAvailable(activity)) {
            DropDownPreference vrDisplayPref =
                    (DropDownPreference) findPreference(KEY_VR_DISPLAY_PREF);
            vrDisplayPref.setEntries(new CharSequence[] {
                    activity.getString(R.string.display_vr_pref_low_persistence),
                    activity.getString(R.string.display_vr_pref_off),
            });
            vrDisplayPref.setEntryValues(new CharSequence[] { "0", "1" });

            final Context c = activity;
            int currentUser = ActivityManager.getCurrentUser();
            int current = Settings.Secure.getIntForUser(c.getContentResolver(),
                            Settings.Secure.VR_DISPLAY_MODE,
                            /*default*/Settings.Secure.VR_DISPLAY_MODE_LOW_PERSISTENCE,
                            currentUser);
            vrDisplayPref.setValueIndex(current);
            vrDisplayPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int i = Integer.parseInt((String) newValue);
                    int u = ActivityManager.getCurrentUser();
                    if (!Settings.Secure.putIntForUser(c.getContentResolver(),
                            Settings.Secure.VR_DISPLAY_MODE,
                            i, u)) {
                        Log.e(TAG, "Could not change setting for " +
                                Settings.Secure.VR_DISPLAY_MODE);
                    }
                    return true;
                }
            });
        } else {
            removePreference(KEY_VR_DISPLAY_PREF);
        }

        mNightModePreference = (ListPreference) findPreference(KEY_NIGHT_MODE);
        if (mNightModePreference != null) {
            final UiModeManager uiManager = (UiModeManager) getSystemService(
                    Context.UI_MODE_SERVICE);
            final int currentNightMode = uiManager.getNightMode();
            mNightModePreference.setValue(String.valueOf(currentNightMode));
            mNightModePreference.setOnPreferenceChangeListener(this);
        }


        mPipSetPreference =  findPreference(KEY_PIP_SCREEN_SET);
        if (null != mPipSetPreference)
        {
            mPipSetPreference.setOnPreferenceChangeListener(this);

        }

    }

    private static boolean allowAllRotations(Context context) {
        return Resources.getSystem().getBoolean(
                com.android.internal.R.bool.config_allowAllRotations);
    }

    private static boolean isLiftToWakeAvailable(Context context) {
        SensorManager sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return sensors != null && sensors.getDefaultSensor(Sensor.TYPE_WAKE_GESTURE) != null;
    }

    private static boolean isDozeAvailable(Context context) {
        String name = Build.IS_DEBUGGABLE ? SystemProperties.get("debug.doze.component") : null;
        if (TextUtils.isEmpty(name)) {
            name = context.getResources().getString(
                    com.android.internal.R.string.config_dozeComponent);
        }
        return !TextUtils.isEmpty(name);
    }

    private static boolean isTapToWakeAvailable(Resources res) {
        return res.getBoolean(com.android.internal.R.bool.config_supportDoubleTapWake);
    }

    private static boolean isAutomaticBrightnessAvailable(Resources res) {
        return res.getBoolean(com.android.internal.R.bool.config_automatic_brightness_available);
    }

    private static boolean isCameraGestureAvailable(Resources res) {
        boolean configSet = res.getInteger(
                com.android.internal.R.integer.config_cameraLaunchGestureSensorType) != -1;
        return configSet &&
                !SystemProperties.getBoolean("gesture.disable_camera_launch", false);
    }

    private static boolean isCameraDoubleTapPowerGestureAvailable(Resources res) {
        return res.getBoolean(
                com.android.internal.R.bool.config_cameraDoubleTapPowerGestureEnabled);
    }

    private static boolean isVrDisplayModeAvailable(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE);
    }

    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        TimeoutListPreference preference = mScreenTimeoutPreference;
        String summary;
        if (preference.isDisabledByAdmin()) {
            summary = getString(R.string.disabled_by_policy_title);
        } else if (currentTimeout < 0) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            if (entries == null || entries.length == 0) {
                summary = "";
            } else {
                int best = 0;
                for (int i = 0; i < values.length; i++) {
                    long timeout = Long.parseLong(values[i].toString());
                    if (currentTimeout == timeout) {
                        best = i;
                    }
                }
                summary = getString(R.string.screen_timeout_summary, entries[best]);
            }
        }
        preference.setSummary(summary);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateState();

        final long currentTimeout = Settings.System.getLong(getActivity().getContentResolver(),
                SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE);
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);

        mHdmiOutputModePreference.setOnPreferenceChangeListener(this);
        mOutputMode.setOnPreferenceChangeListener(this);
        int dipslayOutput = mDisplayManager.getDisplayOutput(android.view.Display.TYPE_BUILT_IN);
	Log.d(TAG,"displayOutput = " + Integer.toHexString(dipslayOutput));
        mHdmiOutputModePreference.setValue(Integer.toHexString(dipslayOutput));
        mOutputMode.setValue(Integer.toHexString(dipslayOutput));

        final DevicePolicyManager dpm = (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) {
            final EnforcedAdmin admin = RestrictedLockUtils.checkIfMaximumTimeToLockIsSet(
                    getActivity());
            final long maxTimeout = dpm
                    .getMaximumTimeToLockForUserAndProfiles(UserHandle.myUserId());
            mScreenTimeoutPreference.removeUnusableTimeouts(maxTimeout, admin);
        }
        updateTimeoutPreferenceDescription(currentTimeout);

        disablePreferenceIfManaged(KEY_WALLPAPER, UserManager.DISALLOW_SET_WALLPAPER);
    }

    private void updateState() {
        updateFontSizeSummary();
        updateScreenSaverSummary();

        // Update auto brightness if it is available.
        if (mAutoBrightnessPreference != null) {
            int brightnessMode = Settings.System.getInt(getContentResolver(),
                    SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL);
            mAutoBrightnessPreference.setChecked(brightnessMode != SCREEN_BRIGHTNESS_MODE_MANUAL);
        }

        // Update lift-to-wake if it is available.
        if (mLiftToWakePreference != null) {
            int value = Settings.Secure.getInt(getContentResolver(), WAKE_GESTURE_ENABLED, 0);
            mLiftToWakePreference.setChecked(value != 0);
        }

        // Update doze if it is available.
        if (mDozePreference != null) {
            int value = Settings.Secure.getInt(getContentResolver(), DOZE_ENABLED, 1);
            mDozePreference.setChecked(value != 0);
        }

        // Update tap to wake if it is available.
        if (mTapToWakePreference != null) {
            int value = Settings.Secure.getInt(getContentResolver(), DOUBLE_TAP_TO_WAKE, 0);
            mTapToWakePreference.setChecked(value != 0);
        }

        // Update camera gesture #1 if it is available.
        if (mCameraGesturePreference != null) {
            int value = Settings.Secure.getInt(getContentResolver(), CAMERA_GESTURE_DISABLED, 0);
            mCameraGesturePreference.setChecked(value == 0);
        }

        // Update camera gesture #2 if it is available.
        if (mCameraDoubleTapPowerGesturePreference != null) {
            int value = Settings.Secure.getInt(
                    getContentResolver(), CAMERA_DOUBLE_TAP_POWER_GESTURE_DISABLED, 0);
            mCameraDoubleTapPowerGesturePreference.setChecked(value == 0);
        }
    }

    private void updateScreenSaverSummary() {
        if (mScreenSaverPreference != null) {
            mScreenSaverPreference.setSummary(
                    DreamSettings.getSummaryTextWithDreamName(getActivity()));
        }
    }

    private void updateFontSizeSummary() {
        final Context context = mFontSizePref.getContext();
        final float currentScale = Settings.System.getFloat(context.getContentResolver(),
                Settings.System.FONT_SCALE, 1.0f);
        final Resources res = context.getResources();
        final String[] entries = res.getStringArray(R.array.entries_font_size);
        final String[] strEntryValues = res.getStringArray(R.array.entryvalues_font_size);
        final int index = ToggleFontSizePreferenceFragment.fontSizeValueToIndex(currentScale,
                strEntryValues);
        mFontSizePref.setSummary(entries[index]);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            try {
                int value = Integer.parseInt((String) objValue);
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        }
        if (preference == mAutoBrightnessPreference) {
            boolean auto = (Boolean) objValue;
            Settings.System.putInt(getContentResolver(), SCREEN_BRIGHTNESS_MODE,
                    auto ? SCREEN_BRIGHTNESS_MODE_AUTOMATIC : SCREEN_BRIGHTNESS_MODE_MANUAL);
        }
        if (preference == mLiftToWakePreference) {
            boolean value = (Boolean) objValue;
            Settings.Secure.putInt(getContentResolver(), WAKE_GESTURE_ENABLED, value ? 1 : 0);
        }
        if (preference == mDozePreference) {
            boolean value = (Boolean) objValue;
            Settings.Secure.putInt(getContentResolver(), DOZE_ENABLED, value ? 1 : 0);
        }
        if (preference == mTapToWakePreference) {
            boolean value = (Boolean) objValue;
            Settings.Secure.putInt(getContentResolver(), DOUBLE_TAP_TO_WAKE, value ? 1 : 0);
        }
        Log.d("Settings","key  = " + key);
        if (KEY_TV_OUTPUT_MODE.equals(key) || KEY_HDMI_OUTPUT_MODE.equals(key)) {
	    format  = mDisplayManager.getDisplayOutput(android.view.Display.TYPE_BUILT_IN);
	    oldValue = Integer.toHexString(format);
            newValue = (String)objValue;
            isSameMode = oldValue.equals(newValue);
	    Log.d("Settings","new value = " + newValue);
            if(isSameMode)
                return true;
            // do not switch to hdmi output when it's plug out!
            if (!canSwitchToNewOutputType(Integer.parseInt(oldValue, 16), Integer.parseInt(newValue, 16)))
                return false;
            dialogTipsFlag = true;
            switchDispFormat(newValue);
            if(dialogTipsFlag){
                showDialog(DLG_RESOLUTION_CHANGE_WARNING);
            }

	}
        if (preference == mCameraGesturePreference) {
            boolean value = (Boolean) objValue;
            Settings.Secure.putInt(getContentResolver(), CAMERA_GESTURE_DISABLED,
                    value ? 0 : 1 /* Backwards because setting is for disabling */);
        }
        if (preference == mCameraDoubleTapPowerGesturePreference) {
            boolean value = (Boolean) objValue;
            Settings.Secure.putInt(getContentResolver(), CAMERA_DOUBLE_TAP_POWER_GESTURE_DISABLED,
                    value ? 0 : 1 /* Backwards because setting is for disabling */);
        }
        if (preference == mNightModePreference) {
            try {
                final int value = Integer.parseInt((String) objValue);
                final UiModeManager uiManager = (UiModeManager) getSystemService(
                        Context.UI_MODE_SERVICE);
                uiManager.setNightMode(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist night mode setting", e);
            }
        }
        return true;
    }

    private boolean canSwitchToNewOutputType(int oldFormat, int newFormat) {
        int newType = (newFormat & 0xff00) >> 8;
        int mCurType = mDisplayManager.getDisplayOutputType(android.view.Display.TYPE_BUILT_IN);

        Log.e(TAG, "current output type: " + mCurType + " new ouput type: " + newType);
        if (mCurType != newType)
            return false;

        return true;
    }
    private void switchDispFormat(String value) {
	    Log.d(TAG,"switchDispFormat value = " + value);
	    try {
	        format = Integer.parseInt(value, 16);
            int dispformat = mDisplayManager.getDisplayModeFromFormat(format);
            int mCurType = mDisplayManager.getDisplayOutputType(android.view.Display.TYPE_BUILT_IN);
            IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.checkService(
                        Context.WINDOW_SERVICE));
            int setDispOutputOk = 0;
            int w = 0;
            int h = 0;
            int density = 0;

            isSupport = true;
            if(isSupport){
                Log.w(TAG, "dm set output format:"+format);
                setDispOutputOk = mDisplayManager.setDisplayOutput(android.view.Display.TYPE_BUILT_IN, format);
                Log.w(TAG, "setDisplayOutput return "+setDispOutputOk);
                if(setDispOutputOk == -1 && dialogTipsFlag){
                    dialogTipsFlag = !dialogTipsFlag;
                    Dialog alertDialog = new AlertDialog.Builder(this.getActivity())
                        .setTitle(this.getResources().getString(R.string.display_support_type_title))
                        .setMessage(this.getResources().getString(R.string.display_support_type))
                        .setPositiveButton(
                                this.getResources().getString(R.string.assistant_security_warning_agree),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                        int which) {
                                        switchDispFormat(oldValue);
                                        return ;
                                    }
                                }).create();
                    alertDialog.show();
                }

                mOutputMode.setValue(value);
		if(mHdmiOutputModePreference!=null)
		mHdmiOutputModePreference.setValue(value);
                if(0xFF == setDispOutputOk) {
                    switch(dispformat) {
                    case DisplayOutputManager.DISPLAY_TVFORMAT_3840_2160P_30HZ:
                    case DisplayOutputManager.DISPLAY_TVFORMAT_3840_2160P_25HZ:
                    case DisplayOutputManager.DISPLAY_TVFORMAT_3840_2160P_24HZ:
                    case DisplayOutputManager.DISPLAY_TVFORMAT_1080P_50HZ:
                    case DisplayOutputManager.DISPLAY_TVFORMAT_1080P_60HZ:
                    case DisplayOutputManager.DISPLAY_TVFORMAT_1080I_60HZ:
                    case DisplayOutputManager.DISPLAY_TVFORMAT_1080I_50HZ:
                    case DisplayOutputManager.DISPLAY_TVFORMAT_1080P_24HZ:
                        w = 1920;
                        h = 1080;
                        density = 240;
                        break;
                    case DisplayOutputManager.DISPLAY_TVFORMAT_720P_50HZ:
                    case DisplayOutputManager.DISPLAY_TVFORMAT_720P_60HZ:
                        w = 1280;
                        h = 720;
                        density = 160;
                        break;
                    case DisplayOutputManager.DISPLAY_TVFORMAT_576P:
                    case DisplayOutputManager.DISPLAY_TVFORMAT_576I:
                        w = 720;
                        h = 576;
                        density = 128;
                        break;
                    case DisplayOutputManager.DISPLAY_TVFORMAT_480P:
                    case DisplayOutputManager.DISPLAY_TVFORMAT_480I:
                        w = 720;
                        h = 480;
                        density = 106;
                        break;
                    default:
                        setDispOutputOk = 1;
                    }
                }
                if(0xFF == setDispOutputOk) {
                    if (wm == null) {
                        Log.e(TAG,"wm is null!");
                    }
                    try{
                        wm.setForcedDisplaySize(android.view.Display.DEFAULT_DISPLAY, w, h);
                        wm.setForcedDisplayDensity(android.view.Display.DEFAULT_DISPLAY, density);
                    }catch(RemoteException e){
                        Log.d(TAG,"call setForcedDisplaySize error");
                    }
                }
            }else {
                Toast.makeText(getActivity(), com.android.settings.R.string.display_mode_unsupport,Toast.LENGTH_LONG).show();
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid display output format!");
        }
    }    
    @Override
    protected int getHelpResource() {
        return R.string.help_uri_display;
    }
    
    @Override
    public Dialog onCreateDialog(int dialogId) {
        if (DLG_RESOLUTION_CHANGE_WARNING == dialogId) {
            OnClickListener listener = new OnClickListener() {
                @Override
                    public void onClick(DialogInterface dialog, int btn) {
                        if (btn == AlertDialog.BUTTON_POSITIVE) {
                            switchDispFormat(newValue);
                        } else if (btn == AlertDialog.BUTTON_NEGATIVE) {
                            switchDispFormat(oldValue);
                        }
                        dialog.dismiss();
                    }
            };

            String str = getString(com.android.settings.R.string.display_mode_time_out_desc);
            final AlertDialog dialog = new AlertDialog.Builder(this.getActivity())
				.setTitle(com.android.settings.R.string.display_mode_time_out_title)
				.setMessage(String.format(str, Integer.toString(FALLBACK_DISPLAY_MODE_TIMEOUT)))
				.setPositiveButton(com.android.internal.R.string.ok, listener)
				.setNegativeButton(com.android.internal.R.string.cancel, listener)
				.create();
            dialog.show();

            new AsyncTask(){
                @Override
                    protected Object doInBackground(Object... arg0) {
                        int time = FALLBACK_DISPLAY_MODE_TIMEOUT;
                        while(time >= 0 && dialog.isShowing()){
                            publishProgress(time);
                            try{
                                Thread.sleep(1000);
                            }catch(Exception e){}
                            time--;
                        }
                        return null;
                    }
                @Override
                    protected void onPostExecute(Object result) {
                        super.onPostExecute(result);
                        if (dialog.isShowing()) {
                            Log.d(TAG,"oldValue = " + oldValue);
                            switchDispFormat(oldValue);
                            dialog.dismiss();
                        }
                    }
                @Override
                    protected void onProgressUpdate(Object... values) {
                        super.onProgressUpdate(values);
                        int time = (Integer)values[0];
                        String str = getString(com.android.settings.R.string.display_mode_time_out_desc);
                        dialog.setMessage(String.format(str, Integer.toString(time)));
                    }
            }.execute();
            return dialog;
        }
        return null;
    }
    
    private void disablePreferenceIfManaged(String key, String restriction) {
        final RestrictedPreference pref = (RestrictedPreference) findPreference(key);
        if (pref != null) {
            pref.setDisabledByAdmin(null);
            if (RestrictedLockUtils.hasBaseUserRestriction(getActivity(), restriction,
                    UserHandle.myUserId())) {
                pref.setEnabled(false);
            } else {
                pref.checkRestrictionAndSetDisabled(restriction);
            }
        }
    }

    private static class SummaryProvider implements SummaryLoader.SummaryProvider {
        private final Context mContext;
        private final SummaryLoader mLoader;

        private SummaryProvider(Context context, SummaryLoader loader) {
            mContext = context;
            mLoader = loader;
        }

        @Override
        public void setListening(boolean listening) {
            if (listening) {
                updateSummary();
            }
        }

        private void updateSummary() {
            boolean auto = Settings.System.getInt(mContext.getContentResolver(),
                    SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                    == SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
            mLoader.setSummary(this, mContext.getString(auto ? R.string.display_summary_on
                    : R.string.display_summary_off));
        }
    }

    public static final SummaryLoader.SummaryProviderFactory SUMMARY_PROVIDER_FACTORY
            = new SummaryLoader.SummaryProviderFactory() {
        @Override
        public SummaryLoader.SummaryProvider createSummaryProvider(Activity activity,
                                                                   SummaryLoader summaryLoader) {
            return new SummaryProvider(activity, summaryLoader);
        }
    };

    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();

                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.display_settings;
                    result.add(sir);

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    ArrayList<String> result = new ArrayList<String>();
                    if (!context.getResources().getBoolean(
                            com.android.internal.R.bool.config_dreamsSupported)) {
                        result.add(KEY_SCREEN_SAVER);
                    }
                    if (!isAutomaticBrightnessAvailable(context.getResources())) {
                        result.add(KEY_AUTO_BRIGHTNESS);
                    }
                    if (!isLiftToWakeAvailable(context)) {
                        result.add(KEY_LIFT_TO_WAKE);
                    }
                    if (!isDozeAvailable(context)) {
                        result.add(KEY_DOZE);
                    }
                    if (!RotationPolicy.isRotationLockToggleVisible(context)) {
                        result.add(KEY_AUTO_ROTATE);
                    }
                    if (!isTapToWakeAvailable(context.getResources())) {
                        result.add(KEY_TAP_TO_WAKE);
                    }
                    if (!isCameraGestureAvailable(context.getResources())) {
                        result.add(KEY_CAMERA_GESTURE);
                    }
                    if (!isCameraDoubleTapPowerGestureAvailable(context.getResources())) {
                        result.add(KEY_CAMERA_DOUBLE_TAP_POWER_GESTURE);
                    }
                    if (!isVrDisplayModeAvailable(context)) {
                        result.add(KEY_VR_DISPLAY_PREF);
                    }
                    return result;
                }
            };
}
