/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.launcher.device.apps;

import android.graphics.drawable.Drawable;
import android.util.Log;

import com.android.tv.launcher.BrowseInfoFactory;
import com.android.tv.launcher.R;
import com.android.tv.launcher.MenuActivity;

/**
 * Activity allowing the management of apps settings.
 */
public class AppsActivity extends MenuActivity {
	private static final String TAG = "AppsActivity";
	
    @Override
    protected String getBrowseTitle() {
        return getString(R.string.device_apps);
    }
    
    @Override
    protected Drawable getBadgeImage() {
        //Justin Porting 20180424 Start
        return getResources().getDrawable(R.drawable.bpi_logo);
        //Justin Porting 20180424 End
    }
    
    @Override
    protected BrowseInfoFactory getBrowseInfoFactory() {
        AppsBrowseInfo appsBrowseInfo = new AppsBrowseInfo(this);
        appsBrowseInfo.init();
        return appsBrowseInfo;
    }
}
