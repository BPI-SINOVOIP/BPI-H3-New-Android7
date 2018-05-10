package com.softwinner.TvdVideo;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.input.InputManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.TrackInfo;
import android.media.TimedText;
import android.net.Uri;
import android.os.DisplayOutputManager;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DisplayController extends Dialog implements SeekBar.OnSeekBarChangeListener,View.OnClickListener {

    private final String TAG = "DisplayController";
    private Context mContext;
    private TextView mTextStatus;
    private ImageButton mPrev;
    private ImageButton mNext;
    private TextView mTextStatus1;
    private ImageButton mPrev1;
    private ImageButton mNext1;
    private SeekBar mBrightness;
    private SeekBar mContrast;
    private SeekBar mSaturation;
    private SeekBar mDenoise;
    private SeekBar mDetail;
    private SeekBar mEdgeSharp;
    private LinearLayout[] mLayout;
    private  String[] mModeEntries;
    private  String[] mColorEntries;
    private DisplayOutputManager mDisplayManager;

    private int mDisplayType = 1;

    private int mCtrlIndex;
    private final int MAX_CTRL = 7;

    private int mEnhanceModeId;  /* 0 disable;  1 enable ; 2 demo */
    private int mMaxEnhanceModeNum;
    private final int ENHANCE_DISABLE = 0;
    private final int ENHANCE_ENABLE = 1;
    private final int ENHANCE_DEMO = 2;

    private int mColorId;
    private int mMaxColorModeNum;
    private final int COLOR_STANDAR = 0;
    private final int COLOR_SOFT = 1;
    private final int COLOR_VIVID = 2;
    private final int COLOR_CUSTOM = 3;
    private final String STORE_NAME = "DisplayController";
    private final String EDITOR_COLOR_MODE = "enhance.colormode";
    private final String EDITOR_COLOR_CUSTOM_VALUES = "colormode.custom";
    private final SharedPreferences mSp;
    private final SharedPreferences.Editor mEditor;
    private final ColorMode mColorStandard = new ColorMode(5, 0, 4, 5, 2, 2);
    private final ColorMode mColorSoft = new ColorMode(4, 0, 3, 5, 0, 0);
    private final ColorMode mColorVivid = new ColorMode(5, 5, 5, 5, 5, 5);
    private ColorMode mColorCustom = new ColorMode(5, 0, 4, 5, 2, 2);

    private class ColorMode {
        private int mBright;
        private int mContrast;
        private int mSaturation;
        private int mDenoise;
        private int mDetail;
        private int mEdge;

        public ColorMode(int brigth, int contrast, int saturation,
            int denoise, int detail, int edge) {
            mBright = brigth;
            mContrast = contrast;
            mSaturation = saturation;
            mDenoise = denoise;
            mDetail = detail;
            mEdge = edge;
        };

        public int getBringt() { return mBright; }
        public int getContrast() { return mContrast; }
        public int getSaturation() { return mSaturation; }
        public int getDenoise() { return mDenoise; }
        public int getDetail() { return mDetail; }
        public int getEdge() { return mEdge; }

        public void setBringt(int value) { mBright = value; }
        public void setContrast(int value) { mContrast = value; }
        public void setSaturation(int value) { mSaturation = value; }
        public void setDenoise(int value) { mDenoise = value; }
        public void setDetail(int value) { mDetail = value; }
        public void setEdge(int value) { mEdge = value; }

    };

    public DisplayController(Context context) {
        super(context, R.style.menu);

        mContext = context;
        mModeEntries = context.getResources().getStringArray(R.array.display_settings_state);
        mColorEntries = context.getResources().getStringArray(R.array.color_settings_state);
        mMaxEnhanceModeNum = mModeEntries.length;
        LayoutInflater inflate = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View menuView = inflate.inflate(R.layout.dialog_dispsettings, null);
        this.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide
                                                                   // nav
                                                                   // bar
            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
            );
        Log.d(TAG, "DislapyController -/-/");

        mDisplayManager = (DisplayOutputManager)mContext.getSystemService(
			Context.DISPLAYOUTPUT_SERVICE);

        this.setContentView(menuView);

        mTextStatus = (TextView)findViewById(R.id.result0);
        mPrev = (ImageButton)findViewById(R.id.switch_prev0);
        mPrev.setOnClickListener(this);
        mNext = (ImageButton)findViewById(R.id.switch_next0);
        mNext.setOnClickListener(this);

        mTextStatus1 = (TextView)findViewById(R.id.result1);
        mPrev1 = (ImageButton)findViewById(R.id.switch_prev1);
        mPrev1.setOnClickListener(this);
        mNext1 = (ImageButton)findViewById(R.id.switch_next1);
        mNext1.setOnClickListener(this);

        mCtrlIndex = 0;

        mSp = mContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
        mEditor = mSp.edit();
        mColorId = getSavedColorMode() % mColorEntries.length;
        syncColorModeState();
        setupColorCustomValues();
        //setDisplayColorMode();

        mLayout = new LinearLayout[7];
        mLayout[0] = (LinearLayout)findViewById(R.id.display_effect_layout);
        mLayout[1] = (LinearLayout)findViewById(R.id.video_effect_layout);
        mLayout[2] = (LinearLayout)findViewById(R.id.brightness_layout);
        mLayout[3] = (LinearLayout)findViewById(R.id.contrast_layout);
        mLayout[4] = (LinearLayout)findViewById(R.id.saturation_layout);
        mLayout[5] = (LinearLayout)findViewById(R.id.de_noise_layout);
        mLayout[6] = (LinearLayout)findViewById(R.id.detail_enhance_layout);
        setLayoutSelected(mCtrlIndex);

        mBrightness = (SeekBar)findViewById(R.id.brightness_bar);
        mBrightness.setMax(10);
        mContrast = (SeekBar)findViewById(R.id.contrast_bar);
        mContrast.setMax(10);
        mSaturation = (SeekBar)findViewById(R.id.saturation_bar);
        mSaturation.setMax(10);
        mDenoise = (SeekBar)findViewById(R.id.de_noise_bar);
        mDenoise.setMax(10);
        mDetail = (SeekBar)findViewById(R.id.detail_enhance_bar);
        mDetail.setMax(10);
        mEdgeSharp = (SeekBar)findViewById(R.id.edge_sharpening_bar);
        mEdgeSharp.setMax(10);
        setAllSeekBarProgress();

        mBrightness.setOnSeekBarChangeListener(this);
        mContrast.setOnSeekBarChangeListener(this);
        mSaturation.setOnSeekBarChangeListener(this);
        mDenoise.setOnSeekBarChangeListener(this);
        mDetail.setOnSeekBarChangeListener(this);
        mEdgeSharp.setOnSeekBarChangeListener(this);
    }

    private void setLayoutSelected(int index) {
        for (int i = 0; i < MAX_CTRL; i++)
            mLayout[i].setBackgroundColor(0x3f666666);
        mLayout[index].setBackgroundColor(0x70ffffff);
    }

    private void setAllSeekBarState(boolean state){
        mBrightness.setEnabled(state);
        mContrast.setEnabled(state);
        mSaturation.setEnabled(state);
        mDenoise.setEnabled(state);
        mDetail.setEnabled(state);
        mEdgeSharp.setEnabled(state);
    }

    private void setAllSeekBarProgress() {

        int value = mDisplayManager.getDisplayBright(mDisplayType);
        if (0 <= value) {
            mBrightness.setProgress(value);
            if (COLOR_CUSTOM == mColorId)
                mColorCustom.setBringt(value);
        }

        value = mDisplayManager.getDisplayContrast(mDisplayType);
        if (0 <= value) {
            mContrast.setProgress(value);
            if (COLOR_CUSTOM == mColorId)
                mColorCustom.setContrast(value);
        }

        value = mDisplayManager.getDisplaySaturation(mDisplayType);
        if (0 <= value) {
            mSaturation.setProgress(value);
            if (COLOR_CUSTOM == mColorId)
                mColorCustom.setSaturation(value);
        }

        value = mDisplayManager.getDisplayDenoise(mDisplayType);
        if (0 <= value) {
            mDenoise.setProgress(value);
            if (COLOR_CUSTOM == mColorId)
                mColorCustom.setDenoise(value);
        }

        value = mDisplayManager.getDisplayDetail(mDisplayType);
        if (0 <= value) {
            mDetail.setProgress(value);
            if (COLOR_CUSTOM == mColorId)
                mColorCustom.setDetail(value);
        }

        value = mDisplayManager.getDisplayEdge(mDisplayType);
        if (0 <= value) {
            mEdgeSharp.setProgress(value);
            if (COLOR_CUSTOM == mColorId)
                mColorCustom.setEdge(value);
        }

        if (COLOR_CUSTOM == mColorId)
            saveColorCustomValues();
    }

    private void syncAllSeekBarState() {
        if (((mEnhanceModeId == ENHANCE_ENABLE)
            || (mEnhanceModeId == ENHANCE_DEMO))
            && (mColorId == COLOR_CUSTOM)){
            setAllSeekBarState(true);
        } else {
            setAllSeekBarState(false);
        }
    }

    private void syncEnhanceModeState() {
        mEnhanceModeId = mDisplayManager.getDisplayEnhanceMode(mDisplayType)
            % mMaxEnhanceModeNum;
        mTextStatus.setText(mModeEntries[mEnhanceModeId]);
    }

    private void syncColorModeState() {
        mTextStatus1.setText(mColorEntries[mColorId]);
    }

    private void syncControllerState() {
        syncEnhanceModeState();
        syncAllSeekBarState();
    }

    private int getSavedColorMode() {
        return mSp.getInt(EDITOR_COLOR_MODE, COLOR_STANDAR);
    }

    private void saveColorMode(int mode) {
        mEditor.putInt(EDITOR_COLOR_MODE, mode);
        mEditor.commit();
    }

    private void saveColorCustomValues() {
        String values = String.format("%d,%d,%d,%d,%d,%d",
            mColorCustom.getBringt(),
            mColorCustom.getContrast(),
            mColorCustom.getSaturation(),
            mColorCustom.getDenoise(),
            mColorCustom.getDetail(),
            mColorCustom.getEdge());
        mEditor.putString(EDITOR_COLOR_CUSTOM_VALUES, values);
        mEditor.commit();
    }

    private void setupColorCustomValues() {
        String values = mSp.getString(EDITOR_COLOR_CUSTOM_VALUES, null);
        if (null != values) {
            String[] valueArray = values.split(",");
            if (6 <= valueArray.length) {
                mColorCustom.setBringt(Integer.parseInt(valueArray[0]));
                mColorCustom.setContrast(Integer.parseInt(valueArray[1]));
                mColorCustom.setSaturation(Integer.parseInt(valueArray[2]));
                mColorCustom.setDenoise(Integer.parseInt(valueArray[3]));
                mColorCustom.setDetail(Integer.parseInt(valueArray[4]));
                mColorCustom.setEdge(Integer.parseInt(valueArray[5]));
            } else {
                Log.w(TAG, "valueArray.lenth=" + valueArray.length
                    + " is less than 6");
            }
        //} else {
        //    Log.d(TAG, "not find " + EDITOR_COLOR_CUSTOM_VALUES + " values");
        }
    };

    private void setDisplayColorMode(ColorMode colorMode) {
        mDisplayManager.setDisplayBright(mDisplayType, colorMode.getBringt());
        mDisplayManager.setDisplayContrast(mDisplayType, colorMode.getContrast());
        mDisplayManager.setDisplaySaturation(mDisplayType, colorMode.getSaturation());
        mDisplayManager.setDisplayDenoise(mDisplayType, colorMode.getDenoise());
        mDisplayManager.setDisplayDetail(mDisplayType, colorMode.getDetail());
        mDisplayManager.setDisplayEdge(mDisplayType, colorMode.getEdge());
    }

    private void setDisplayColorMode() {
        if (COLOR_STANDAR == mColorId)
            setDisplayColorMode(mColorStandard);
        else if (COLOR_SOFT == mColorId)
            setDisplayColorMode(mColorSoft);
        else if (COLOR_VIVID == mColorId)
            setDisplayColorMode(mColorVivid);
        else if (COLOR_CUSTOM == mColorId)
            setDisplayColorMode(mColorCustom);
        else
            Log.w(TAG, "unkown colorid(" + mColorId + ")");
    }

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        Log.d(TAG, "on seek bar id = " + seekBar.getId() +  " progress = " + progress );
        switch (seekBar.getId()) {
        case R.id.brightness_bar:
            Log.d(TAG, "on brightness_bar progress = " + progress);
            mDisplayManager.setDisplayBright(mDisplayType, progress);
            break;
        case R.id.contrast_bar:
            Log.d(TAG, "on contrast_bar progress = " + progress);
            mDisplayManager.setDisplayContrast(mDisplayType, progress);
            break;
        case R.id.saturation_bar:
            Log.d(TAG, "on saturation_bar progress = " + progress);
            mDisplayManager.setDisplaySaturation(mDisplayType, progress);
            break;
        case R.id.de_noise_bar:
            Log.d(TAG, "on de_noise_bar progress = " + progress);
            mDisplayManager.setDisplayDenoise(mDisplayType, progress);
            break;
        case R.id.detail_enhance_bar:
            Log.d(TAG, "on detail_enhance_bar progress = " + progress);
            mDisplayManager.setDisplayDetail(mDisplayType, progress);
            break;
        case R.id.edge_sharpening_bar:
            Log.d(TAG, "on edge_sharpening_bar progress = " + progress);
            mDisplayManager.setDisplayEdge(mDisplayType, progress);
            break;
        default:
            Log.d(TAG, "on unknown progress = " + progress);
            return;
        }
        setAllSeekBarProgress();
        syncControllerState();
    }

    @Override
    public void onStart(){
        Log.d(TAG, "onStart");
        syncControllerState();
    }

    @Override
    public void dismiss() {
        Log.d(TAG, "on dismiss");
        super.dismiss();
    }   

    public void onStopTrackingTouch(SeekBar seekBar) {}

    public void onStartTrackingTouch(SeekBar seekBar) {}

    public void onClick(View v) {
        Log.d(TAG, "on click id = " + v.getId());
        syncControllerState();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_UP:
            if (mCtrlIndex > 0)
                mCtrlIndex--;
            setLayoutSelected(mCtrlIndex);
            break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
            if (mCtrlIndex < MAX_CTRL - 1)
                mCtrlIndex++;
            setLayoutSelected(mCtrlIndex);
            break;
        case KeyEvent.KEYCODE_DPAD_LEFT:
            if (mCtrlIndex == 0) {
                int enhanceMode = (mEnhanceModeId + mMaxEnhanceModeNum - 1)
                    % mMaxEnhanceModeNum;
                if (0 != mDisplayManager.setDisplayEnhanceMode(
                    mDisplayType, enhanceMode)) {
                    Log.w(TAG, "#setDisplayEnhanceMode(" + enhanceMode + ") failed");
                }
                syncControllerState();
            } else if (mCtrlIndex == 1) {
                syncEnhanceModeState();
                if ((mEnhanceModeId == ENHANCE_DEMO)
                    || (mEnhanceModeId == ENHANCE_ENABLE)) {
                    mColorId = (mColorId == 0) ?
                    (mColorEntries.length - 1) : (mColorId - 1);
                    setDisplayColorMode();
                    setAllSeekBarProgress();
                    syncAllSeekBarState();
                    syncColorModeState();
                    saveColorMode(mColorId);
                }
            }
            break;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            if(mCtrlIndex == 0) {
                int enhanceMode = (mEnhanceModeId + 1) % mMaxEnhanceModeNum;
                if (0 != mDisplayManager.setDisplayEnhanceMode(
                    mDisplayType, enhanceMode)) {
                    Log.w(TAG, "&setDisplayEnhanceMode(" + enhanceMode + ") failed");
                }
                syncControllerState();
            } else if (mCtrlIndex == 1) {
                syncEnhanceModeState();
                if ((mEnhanceModeId == ENHANCE_DEMO)
                    || (mEnhanceModeId == ENHANCE_ENABLE)) {
                    mColorId = (mColorId == mColorEntries.length - 1) ?
                        0 : (mColorId + 1);
                    setDisplayColorMode();
                    setAllSeekBarProgress();
                    syncAllSeekBarState();
                    syncColorModeState();
                    saveColorMode(mColorId);
                }
            }
            break;
        case KeyEvent.KEYCODE_MENU:
            this.dismiss();
            break;
        }

        return super.onKeyDown(keyCode, event);
    }

}
