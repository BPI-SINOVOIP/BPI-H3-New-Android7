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


import android.preference.ListPreference;
import android.hardware.display.DisplayManager;
import android.os.DisplayOutputManager;
import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;

public class DisplayOutputModeListPreference extends ListPreference{
    private DisplayOutputManager mDm;

    public DisplayOutputModeListPreference(Context context) {
        super(context);
        if (mDm == null)
            mDm = (DisplayOutputManager)context.getSystemService(
                    Context.DISPLAYOUTPUT_SERVICE);
    }

    public DisplayOutputModeListPreference(Context context, AttributeSet attrs) {
        super(context,attrs);
        if (mDm == null)
            mDm = (DisplayOutputManager)context.getSystemService(
                    Context.DISPLAYOUTPUT_SERVICE);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        int value = mDm.getDisplayOutput(android.view.Display.TYPE_BUILT_IN);
        setValue(Integer.toHexString(value));
        super.onPrepareDialogBuilder(builder);
    }
}
