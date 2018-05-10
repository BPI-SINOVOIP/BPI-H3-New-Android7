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

package com.android.settings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.DisplayOutputManager;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.PreferenceCategory;
import android.support.v14.preference.SwitchPreference;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

public class HDRSettings extends RestrictedSettingsFragment implements
        Preference.OnPreferenceChangeListener{

    private static final String TAG = "AdvanceDisplaySettings";

    private static final String KEY_HDMI_COLOR_MODE = "hdmi_color_mode";
    private static final String KEY_HDR_MODE = "hdr_mode";

    private static final int DISPLAY_BUILD_IN = 0;

    private DisplayOutputManager mDisplayManager;

    private ListPreference mHdmiColorModePreference;
    private ListPreference mHdrModePreference;

    public HDRSettings (){
	super(null);
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.DISPLAY;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.hdr_settings);

        mDisplayManager = (DisplayOutputManager)getActivity().getSystemService(
            Context.DISPLAYOUTPUT_SERVICE);

        mHdmiColorModePreference = (ListPreference)findPreference(KEY_HDMI_COLOR_MODE);
        int value = mDisplayManager.getDisplayOutputPixelFormat(DISPLAY_BUILD_IN);
        mHdmiColorModePreference.setValue(Integer.toHexString(value));
        mHdmiColorModePreference.setOnPreferenceChangeListener(this);

        mHdrModePreference = (ListPreference)findPreference(KEY_HDR_MODE);
        value = mDisplayManager.getDisplayOutputDataspaceMode(DISPLAY_BUILD_IN);
        mHdrModePreference.setValue(Integer.toHexString(value));
        mHdrModePreference.setOnPreferenceChangeListener(this);

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        ListPreference listpreference = (ListPreference)preference;
        if (listpreference.getValue().equals((String)objValue)) {
            return true;
        }

        final String key = preference.getKey();
        String result = "";
        final int value = Integer.parseInt((String)objValue);
        int ret = 0;

        Log.d(TAG, "onPreferenceChange: key=" + key + ", value=" + value);
        if (KEY_HDMI_COLOR_MODE.equals(key)) {
            ret = mDisplayManager.setDisplayOutputPixelFormat(
                DISPLAY_BUILD_IN, value);
            if (0 > ret)
                result = "set hdmi color space and deep mode failed";
        } else if (KEY_HDR_MODE.equals(key)) {
            ret = mDisplayManager.setDisplayOutputDataspaceMode(
                DISPLAY_BUILD_IN, value);
            if (0 > ret)
                result = "set hdmi hdr mode failed";
        }

        if (0 > ret) {
            Toast.makeText(getActivity(), result, Toast.LENGTH_LONG).show();
            return false;
        } else
            return true;
    }

}
