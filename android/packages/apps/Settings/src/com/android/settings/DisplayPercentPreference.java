
package com.android.settings;

/*
 ************************************************************************************
 *                                    Android Settings

 *                       (c) Copyright 2006-2010, huanglong Allwinner 
 *                                 All Rights Reserved
 *
 * File       : SaturationPreference.java
 * By         : huanglong
 * Version    : v1.0
 * Date       : 2011-9-5 16:20:00
 * Description: Add the Saturation settings to Display.
 * Update     : date                author      version     notes
 *           
 ************************************************************************************
 */

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.DisplayOutputManager;
import android.view.Display;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import java.lang.Integer;
import android.os.SystemProperties;
import java.lang.Integer;
import java.lang.String;
import java.lang.Exception;
import com.android.settings.SeekBarDialogPreference;
import com.android.settings.SeekBarPreference;

public class DisplayPercentPreference extends SeekBarDialogPreference implements
        SeekBar.OnSeekBarChangeListener {

    private SeekBar mSeekBar;

    private int OldValue;

    private int MAXIMUM_VALUE = 100;
    private int MINIMUM_VALUE = 80;
    private String DISPLAY_AREA_RADIO = "display.area_radio";
    private String TAG = "DisplayPercentPreference";
    private DisplayOutputManager mDisplayManager;

    public DisplayPercentPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDisplayManager = (DisplayOutputManager)
                context.getSystemService(Context.DISPLAYOUTPUT_SERVICE);
        int dispType = mDisplayManager.getDisplayOutputType(Display.TYPE_BUILT_IN);
        if(dispType == DisplayOutputManager.DISPLAY_OUTPUT_TYPE_HDMI){
        	MAXIMUM_VALUE = 100;
        	MINIMUM_VALUE = 80;
        } else if(dispType == DisplayOutputManager.DISPLAY_OUTPUT_TYPE_TV){
        	MAXIMUM_VALUE = 100;
        	MINIMUM_VALUE = 80;
        }
        //setDialogLayoutResource(R.layout.preference_dialog_saturation);
        //setDialogIcon(R.drawable.ic_settings_saturation);
    }

    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mSeekBar = getSeekBar(view);
        mSeekBar.setMax(MAXIMUM_VALUE - MINIMUM_VALUE);
        OldValue = getDisplayPercent();
        mSeekBar.setProgress(OldValue - MINIMUM_VALUE);
        mSeekBar.setOnSeekBarChangeListener(this);
    }

    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromTouch) {
        setDisplayPercent(progress + MINIMUM_VALUE);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            setDisplayPercent(mSeekBar.getProgress() + MINIMUM_VALUE);
        } else {
            setDisplayPercent(OldValue);
        }
        super.onDialogClosed(positiveResult);
    }


    private void setDisplayPercent(int value) {
        mDisplayManager.setDisplayMargin(Display.DEFAULT_DISPLAY, value,value);
    }

    private int getDisplayPercent() {
        return mDisplayManager.getDisplayMargin(Display.DEFAULT_DISPLAY)[0];
    }

    /* implements method in SeekBar.OnSeekBarChangeListener */
    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
        // NA

    }

    /* implements method in SeekBar.OnSeekBarChangeListener */
    @Override
    public void onStopTrackingTouch(SeekBar arg0) {
        // NA

    }

}
