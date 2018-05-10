/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings.device.display;

import android.content.Context;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.util.Log;
import android.widget.TextView;
import android.os.ServiceManager;
import android.view.Display;
import android.os.IDisplayOutputManager;
import android.os.DisplayOutputManager;

import com.android.tv.settings.R;

import java.util.List;

public class ScreenMarginActivity extends Activity {

    private static final String TAG = "ScreenMarginActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            GuidedStepFragment.addAsRoot(this, ScreenMarginFragment.newInstance(), android.R.id.content);
        }
    }

    public static class ScreenMarginFragment extends GuidedStepFragment {

        private static final int MAX_SCREEN_MARGIN = 100;
        private static final int MIN_SCREEN_MARGIN = 80;

        private DisplayOutputManager mDisplayOutputManager = null;
        private int currentMargin = MAX_SCREEN_MARGIN;

        public static ScreenMarginFragment newInstance() {

            Bundle args = new Bundle();

            ScreenMarginFragment fragment = new ScreenMarginFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {

            IDisplayOutputManager service = IDisplayOutputManager.Stub.asInterface(ServiceManager.getService(Context.DISPLAYOUTPUT_SERVICE));
            mDisplayOutputManager = new DisplayOutputManager(getActivity(), service);
            currentMargin = mDisplayOutputManager.getDisplayMargin(Display.DEFAULT_DISPLAY)[0];

            String title = String.format("%s: %d%%",
                    getString(R.string.screen_margin), currentMargin);
            return new GuidanceStylist.Guidance(
                    title,
                    getString(R.string.screen_margin_description),
                    null,
                    getContext().getDrawable(R.drawable.ic_settings_backup_restore_132dp));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder(getContext())
                    .clickAction(GuidedAction.ACTION_ID_CANCEL)
                    .title(getString(R.string.margin_scaleup))
                    .build());
            actions.add(new GuidedAction.Builder(getContext())
                    .clickAction(GuidedAction.ACTION_ID_OK)
                    .title(getString(R.string.margin_scaledown))
                    .build());
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            int newMargin = currentMargin;
            if (action.getId() == GuidedAction.ACTION_ID_OK) {
                // scale down
                if (currentMargin > MIN_SCREEN_MARGIN)
                    newMargin = currentMargin - 1;
            } else if (action.getId() == GuidedAction.ACTION_ID_CANCEL) {
                // scale up
                if (currentMargin < MAX_SCREEN_MARGIN)
                    newMargin = currentMargin + 1;
            } else {
                Log.wtf(TAG, "Unknown action clicked");
            }

            if (newMargin != currentMargin) {
                currentMargin = newMargin;
                TextView title = getGuidanceStylist().getTitleView();
                title.setText(String.format("%s: %d%%", getString(R.string.screen_margin), currentMargin));
                mDisplayOutputManager.setDisplayMargin(Display.DEFAULT_DISPLAY, currentMargin, currentMargin);
            }
        }
    }
}
