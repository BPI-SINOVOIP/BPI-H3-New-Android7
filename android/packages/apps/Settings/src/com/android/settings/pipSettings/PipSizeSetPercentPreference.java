/*
 ************************************************************************************
 *                           Android Settings

 *               c) Copyright 2006-2010, yangtingrui Allwinner
 *                                 All Rights Reserved
 *
 * File       : PipSizeSetPercentPreference.java
 * By         : yangtingrui
 * Version    : v1.0
 * Date       : 2017-3-9 16:20:00
 * Description: Add the Pip Size settings to Display.
 * Update     : date                author      version     notes
 *
 ************************************************************************************
 */

package com.android.settings.pipSettings;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.DisplayOutputManager;
import android.os.RemoteException;
import android.view.Display;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
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
import android.util.DisplayMetrics;
import android.content.res.Resources;
import static android.app.ActivityManager.StackId.PINNED_STACK_ID;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.ActivityManager.StackInfo;
import android.os.SystemProperties;
import android.graphics.Rect;

public class PipSizeSetPercentPreference extends SeekBarDialogPreference implements
        SeekBar.OnSeekBarChangeListener {

    private SeekBar mSeekBar;
    private int MAXIMUM_VALUE = 100;
    private int MINIMUM_VALUE =0;
    private String DISPLAY_AREA_RADIO = "display.area_radio";
    private final static String TAG = PipSizeSetPercentPreference.class.getSimpleName();

    private static final int DEFAULT_MAX_PIP_BOUNDS_WIDTH=960;
    private static final int DEFAULT_MAX_PIP_BOUNDS_HEIGHT=540;
    private int nPreProgress=0;
    private int nPrePipBoundsWidth =0;
    private int nPrePipBoundsHeight= 0;
    private int nPosIndex = 0;
    private PipSetUtils mPipSetUtils=null;

    public PipSizeSetPercentPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mPipSetUtils = PipSetUtils.newInstance();
        mPipSetUtils.initPipData();
        nPrePipBoundsWidth = mPipSetUtils.GetPipBoundsWidth();
        nPrePipBoundsHeight = mPipSetUtils.GetPipBoundsHeight();
        nPosIndex = SystemProperties.getInt(PipSetUtils.PIP_SETTINGS_BOUNDS_POS,0);
        mSeekBar = getSeekBar(view);
        mSeekBar.setMax(MAXIMUM_VALUE);
        int nPrePreogress = (nPrePipBoundsWidth-PipSetUtils.DEFAULT_PIP_BOUNDS_WIDTH)*100/ (DEFAULT_MAX_PIP_BOUNDS_WIDTH-PipSetUtils.DEFAULT_PIP_BOUNDS_WIDTH);
        Log.d(TAG,"nPrePreogress:"+nPrePreogress);
        mSeekBar.setProgress(nPrePreogress);
        mSeekBar.setOnSeekBarChangeListener(this);

    }


    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean bTouch) {
        Log.d(TAG,"===YTR===progress:"+progress+"bTouch:"+bTouch);
        setPipSizeIndex(progress);
        mPipSetUtils. setPipBounds(nPosIndex);
        mPipSetUtils.savePipParameters(nPosIndex);
    }


    @Override
    protected void onDialogClosed(boolean bResult)
    {
        if (!bResult) 
        {
            mPipSetUtils.SetPipBoundsWidth(nPrePipBoundsWidth);
            mPipSetUtils.SetPipBoundsHeight(nPrePipBoundsHeight);
            mPipSetUtils. setPipBounds(nPosIndex);
          //  mPipSetUtils.savePipParameters(nPosIndex);
        }
        super.onDialogClosed(bResult);
    }



    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
        // NA

    }

    /* implements method in SeekBar.OnSeekBarChangeListener */
    @Override
    public void onStopTrackingTouch(SeekBar arg0) {
        // NA

    }

    private void setPipSizeIndex(final int nProgress)
    {
        double nUpdateWidth = (DEFAULT_MAX_PIP_BOUNDS_WIDTH-PipSetUtils.DEFAULT_PIP_BOUNDS_WIDTH) *( nProgress*1.0/100);
        double nUpdateHeight = (DEFAULT_MAX_PIP_BOUNDS_HEIGHT-PipSetUtils.DEFAULT_PIP_BOUNDS_HEIGHT) *( nProgress*1.0/100);
        int  nPIPBoundsWidth = (int) (PipSetUtils.DEFAULT_PIP_BOUNDS_WIDTH+nUpdateWidth);
        int  nPIPBoundsHeight = (int) (PipSetUtils.DEFAULT_PIP_BOUNDS_HEIGHT+nUpdateHeight);
        Log.d(TAG,"==nPIPBoundsWidth:"+nPIPBoundsWidth+"nPIPBoundsHeight:"+nPIPBoundsHeight);

         mPipSetUtils.SetPipBoundsWidth(nPIPBoundsWidth);
         mPipSetUtils.SetPipBoundsHeight(nPIPBoundsHeight);
    }


}
