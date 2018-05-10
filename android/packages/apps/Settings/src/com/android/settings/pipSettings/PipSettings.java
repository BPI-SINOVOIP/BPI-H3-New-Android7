/*
 ************************************************************************************
 *      Android Settings

 *     c) Copyright 2006-2010, yangtingrui Allwinner
 *                                 All Rights Reserved
 *
 * File       : PipSettings.java
 * By         : yangtingrui
 * Version    : v1.0
 * Date       : 2017-3-9 16:20:00
 * Description: Add the Pip Size settings to Display.
 * Update     : date                author      version     notes
 *
 ************************************************************************************
 */

package com.android.settings.pipSettings;
import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v7.preference.ListPreference;
import android.provider.Settings;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.webkit.WebView.FindListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.SettingsPreferenceFragment;
import java.util.List;
import static android.app.ActivityManager.StackId.PINNED_STACK_ID;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.ActivityManager.StackInfo;
import android.os.SystemProperties;
import com.android.settings.R;
public class PipSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = PipSettings.class.getSimpleName();
    static final boolean DEBUG = false;
    private static final int DIALOG_WHEN_TO_DREAM = 1;
    private static final String PACKAGE_SCHEME = "package";

    private Context mContext;
    private ListPreference mPipPosSetPreference=null;
    private IActivityManager mActivityManager=null;

   private  static final String KEY_PIP_POSTION_MODE = "pip_screen_postion_set";
   private static final String PIP_SETTINGS_BOUNDS_POS = "persist.sys.pip.bounds.pos";
 
    private PipSetUtils mPipSetUtils=null;
    @Override
    public int getHelpResource() {
        return R.string.help_url_dreams;
    }

    @Override
    public void onAttach(Activity activity) {
        logd("onAttach(%s)", activity.getClass().getSimpleName());
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.DREAM;
    }

    @Override
    public void onCreate(Bundle icicle) 
    {
        logd("onCreate(%s)", icicle);
        super.onCreate(icicle);
        initPipSetData();
      }

    @Override
    public void onStart() {
        logd("onStart()");
        super.onStart();
    }

    @Override
    public void onDestroyView() {
        logd("onDestroyView()");
        super.onDestroyView();

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        logd("onActivityCreated(%s)", savedInstanceState);
        super.onActivityCreated(savedInstanceState);

    }

    @Override
    public Dialog onCreateDialog(int dialogId) {

        return super.onCreateDialog(dialogId);
    }


    @Override
    public void onPause()
    {
        logd("onPause()");
        super.onPause();
      }

    @Override
    public void onResume()
    {
        logd("onResume()");
        super.onResume();
      }

    public static int getSummaryResource(Context context)
    {
        return  R.string.screensaver_settings_summary_off;
    }

    private void initPipSetData()
   {
        logd("initPipUI()");
        addPreferencesFromResource(R.xml.pip_settings);
        mPipPosSetPreference = (ListPreference)findPreference(KEY_PIP_POSTION_MODE);
        mPipPosSetPreference.setOnPreferenceChangeListener(this);

        int nIndex =SystemProperties.getInt(PIP_SETTINGS_BOUNDS_POS, 1);
        Log.d(TAG,"YTR====index:"+nIndex);
        mPipPosSetPreference.setValueIndex(nIndex);

        //获取屏幕显示尺寸
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        int   nScreenWidth = dm.widthPixels;  //1920*1080
        int  nScreenHeight = dm.heightPixels;

        if (null == mPipSetUtils)
        {
            mPipSetUtils = PipSetUtils.newInstance();
        }
        mPipSetUtils.SetScreenWidth(nScreenWidth);
        mPipSetUtils.SetScreenHeight(nScreenHeight);

    }


    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue)
    {
        mPipSetUtils.initPipData();
         final String key = preference.getKey();
         Log.d(TAG,"======key:"+key);
         if (KEY_PIP_POSTION_MODE.equals(key)) {
             try {
                 int nIndex = Integer.parseInt((String) objValue);
                Log.d(TAG,"======value:"+nIndex);
                mPipSetUtils.setPipBounds(nIndex);
                mPipSetUtils.savePipParameters(nIndex);

             } catch (NumberFormatException e) {
                 Log.e(TAG, "could not persist screen timeout setting", e);
             }
         }

         return true;
    }




    private static void logd(String msg, Object... args)
    {
        if (DEBUG) Log.d(TAG, args == null || args.length == 0 ? msg : String.format(msg, args));
    }

}
