/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.deskclock.settings;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.android.deskclock.R;
import android.content.SharedPreferences;
import com.android.deskclock.data.DataModel;
import android.util.Log;
/**
 * Settings for Clock Daydream
 */
public final class ScreensaverSettingsActivity extends AppCompatActivity {
    public static final String KEY_CLOCK_STYLE = "screensaver_clock_style";
    public static final String KEY_NIGHT_MODE = "screensaver_night_mode";
    public static final String TAG_LOG = "ScreenSaver";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screensaver_settings);
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    public static class PrefsFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener{

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.dream_settings);
            final DataModel.ClockStyle clockStyle = DataModel.getDataModel().getScreensaverClockStyle();
            final ListPreference clockStylePref = (ListPreference) findPreference(KEY_CLOCK_STYLE);
            int index = 0;
            switch (clockStyle) {
                case ANALOG:
                    index = 0;
                    break;
                case DIGITAL:
                    index = 1;
                    break;
            }
            clockStylePref.setValueIndex(index);

            final CheckBoxPreference NightModePref = (CheckBoxPreference) findPreference(KEY_NIGHT_MODE);
            boolean nightstatus = DataModel.getDataModel().getScreensaverNightMode();
            if(nightstatus){
                NightModePref.setChecked(true);
            }
            else{
                NightModePref.setChecked(false);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            refresh();
        }

        @Override
        public boolean onPreferenceChange(Preference pref, Object newValue) {
            if (KEY_CLOCK_STYLE.equals(pref.getKey())) {
                final ListPreference clockStylePref = (ListPreference) pref;
                final int index = clockStylePref.findIndexOfValue((String) newValue);
                clockStylePref.setSummary(clockStylePref.getEntries()[index]);
                final DataModel dm = DataModel.getDataModel();
                if(index == 0){
                    dm.setScreensaverClockStyle(KEY_CLOCK_STYLE, "analog");
                }
                else if(index == 1){
                    dm.setScreensaverClockStyle(KEY_CLOCK_STYLE, "digital");
                }
            }
            return true;
        }

        @Override
        public boolean onPreferenceClick(Preference pref){
            final CheckBoxPreference NightModePref = (CheckBoxPreference) findPreference(KEY_NIGHT_MODE);
            if(KEY_NIGHT_MODE.equals(pref.getKey())){
                final DataModel dm = DataModel.getDataModel();
                if(NightModePref.isChecked()){
                    dm.setScreensaverNightMode(KEY_NIGHT_MODE, true);
                }
                else{
                    dm.setScreensaverNightMode(KEY_NIGHT_MODE, false);
                }
            }
            return true;
        }


        private void refresh() {
            final ListPreference clockStylePref = (ListPreference) findPreference(KEY_CLOCK_STYLE);
            clockStylePref.setSummary(clockStylePref.getEntry());
            clockStylePref.setOnPreferenceChangeListener(this);
            final CheckBoxPreference NightModePref = (CheckBoxPreference) findPreference(KEY_NIGHT_MODE);
            NightModePref.setOnPreferenceClickListener(this);
        }
    }
}
