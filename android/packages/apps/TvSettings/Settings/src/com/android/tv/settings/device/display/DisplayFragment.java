package com.android.tv.settings.device.display;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.IDisplayOutputManager;
import android.os.DisplayOutputManager;
import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.TwoStatePreference;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.text.TextUtils;
import android.util.Log;
import android.app.AlertDialog;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface;
import android.view.Display;
import android.os.AsyncTask;

import java.util.Arrays;

import com.android.tv.settings.R;

public class DisplayFragment extends LeanbackPreferenceFragment implements Preference.OnPreferenceChangeListener {
    private static final int FALLBACK_DISPLAY_MODE_TIMEOUT = 10;
    private static final String TAG = "DisplayFragment";
    private static final String KEY_HDMI_OUTPUTMODE = "hdmi_outputmode";
    private static final String KEY_SCREEN_MARGIN = "screen_margin";
    private DisplayOutputManager mDisplayOutputManager = null;
    private int nowResolution;//当前显示分辨率
    private String[] hdmiSupportModeEVArray;//当前支持的分辨率,EntryValue
    private String[] hdmiSupportModeEArray;//当前支持的分辨率，便于human-readable
    ListPreference hdmiOutputMode = null;
    Preference screenMarginPref = null;

    public  final static String[] hdmiOutputInfos = {
            "480I",
            "576I",
            "480P",
            "576P",
            "720P 50Hz",
            "720P 60Hz" ,
            "1080I 50Hz" ,
            "1080I 60Hz",
            "1080P 24Hz",
            "1080P 50Hz",
            "1080P 60Hz",
            "unknow",
            "unknow",
            "unknow",
            "unknow",
            "unknow",
            "unknow",
            "unknow",
            "unknow",
            "unknow",
            "unknow",
            "unknow",
            "unknow",
            "1080P 24Hz (3D FP)",
            "720P 50Hz (3D FP)",
            "720P 60Hz (3D FP)",
            "1080P 25Hz",
            "1080P 30Hz",
            "3840x2160P 30Hz",
            "3840x2160P 25Hz",
            "3840x2160P 24Hz",
            "4096x2160P 24Hz",
            "4096x2160P 25Hz",
            "4096x2160P 30Hz",
            "3840x2160P 60Hz",
            "4096x2160P 60Hz",
            "3840x2160P 50Hz",};

    public static DisplayFragment newInstance() {
        return new DisplayFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        IDisplayOutputManager service = IDisplayOutputManager.Stub.asInterface(ServiceManager.getService(Context.DISPLAYOUTPUT_SERVICE));
        mDisplayOutputManager = new DisplayOutputManager(getActivity(), service);
        nowResolution = mDisplayOutputManager.getDisplayOutput(Display.DEFAULT_DISPLAY);
        //减掉输出模式0x400,剩余的格式与getSupportModes对应
        nowResolution = nowResolution - 0x400;
        //获取当前HDMI支持分辨率
        int[] tempArray = mDisplayOutputManager.getSupportModes(Display.DEFAULT_DISPLAY,DisplayOutputManager.DISPLAY_OUTPUT_TYPE_HDMI);
        Arrays.sort(tempArray);
        hdmiSupportModeEArray = new String[tempArray.length];
        hdmiSupportModeEVArray = new String[tempArray.length];
        for(int i=0;i<tempArray.length;i++) {
            hdmiSupportModeEVArray[i] = String.valueOf(tempArray[i]);
            hdmiSupportModeEArray[i] = String.valueOf(hdmiOutputInfos[tempArray[i]]);
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.display, null);

        // init hdmi output mode summary
        hdmiOutputMode =(ListPreference) findPreference(KEY_HDMI_OUTPUTMODE);
        hdmiOutputMode.setEntries(hdmiSupportModeEArray);
        hdmiOutputMode.setEntryValues(hdmiSupportModeEVArray);
        String temp = String.valueOf(nowResolution);
        for(int i=0;i<hdmiSupportModeEVArray.length;i++) {
            if(temp.equals(hdmiSupportModeEVArray[i])) {
                hdmiOutputMode.setValue(hdmiSupportModeEVArray[i]);
                hdmiOutputMode.setSummary(hdmiSupportModeEArray[i]);
            }
        }
        hdmiOutputMode.setOnPreferenceChangeListener(this);

        // inti screen margin
        screenMarginPref = findPreference(KEY_SCREEN_MARGIN);
        screenMarginPref.setSummary(" ");
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {

        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
        case KEY_HDMI_OUTPUTMODE:
            String sNewResulution = (String)newValue;
            int iNewResolution = Integer.parseInt(sNewResulution);
            int resolutionBeforeSwitch = mDisplayOutputManager.getDisplayOutputMode(Display.DEFAULT_DISPLAY);
            int ret = mDisplayOutputManager.setDisplayOutputMode(Display.DEFAULT_DISPLAY,iNewResolution);
            if (ret == 0) {
                for(int i=0;i<hdmiSupportModeEVArray.length;i++) {
                    if(hdmiSupportModeEVArray[i].equals(sNewResulution)) {
                        hdmiOutputMode.setSummary(hdmiSupportModeEArray[i]);
                    }
                }
            } else {
                //TODO: error message. (toast)
            }

            if (iNewResolution != resolutionBeforeSwitch && ret == 0) {
                // alert dialog
                OnClickListener listener = new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int btn) {
                        if (btn == AlertDialog.BUTTON_POSITIVE) {
                        } else if (btn == AlertDialog.BUTTON_NEGATIVE) {
                            mDisplayOutputManager.setDisplayOutputMode(
                                    Display.DEFAULT_DISPLAY, resolutionBeforeSwitch);

                            // update summary
                            String temp = String.valueOf(resolutionBeforeSwitch);
                            for(int i=0;i<hdmiSupportModeEVArray.length;i++) {
                                if(temp.equals(hdmiSupportModeEVArray[i])) {
                                    hdmiOutputMode.setValue(hdmiSupportModeEVArray[i]);
                                    hdmiOutputMode.setSummary(hdmiSupportModeEArray[i]);
                                }
                            }
                        }
                        dialog.dismiss();
                    }
                };

                String title = String.format("%s: %s",
                                    this.getResources().getString(R.string.swithmod_warning),
                                    String.valueOf(hdmiOutputInfos[iNewResolution]));
                String alertMessage = this.getResources().getString(R.string.swithmod_message);
                final AlertDialog warnDialog = new AlertDialog.Builder(this.getActivity())
                            .setTitle(title)
                            .setMessage(String.format("%s %s", Integer.toString(10), alertMessage))
                            .setPositiveButton(this.getResources().getString(R.string.swithmod_agree), listener)
                            .setNegativeButton(this.getResources().getString(R.string.swithmod_cancel), listener)
                            .create();
                warnDialog.show();

                new AsyncTask() {
                    @Override
                    protected Object doInBackground(Object... arg0) {
                        int time = FALLBACK_DISPLAY_MODE_TIMEOUT;
                        while(time >= 0 && warnDialog.isShowing()){
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
                        if (warnDialog.isShowing()) {
                            mDisplayOutputManager.setDisplayOutputMode(
                                    Display.DEFAULT_DISPLAY, resolutionBeforeSwitch);
                            warnDialog.dismiss();

                            // update summary
                            String temp = String.valueOf(resolutionBeforeSwitch);
                            for(int i=0;i<hdmiSupportModeEVArray.length;i++) {
                                if(temp.equals(hdmiSupportModeEVArray[i])) {
                                    hdmiOutputMode.setValue(hdmiSupportModeEVArray[i]);
                                    hdmiOutputMode.setSummary(hdmiSupportModeEArray[i]);
                                }
                            }
                        }
                    }
                    @Override
                    protected void onProgressUpdate(Object... values) {
                        super.onProgressUpdate(values);
                        int time = (Integer)values[0];
                        String message = String.format("%s %s", Integer.toString(time), alertMessage);
                        warnDialog.setMessage(message);
                    }

                }.execute();
            }
            break;
        case KEY_SCREEN_MARGIN:
            GuidedStepFragment.add(getFragmentManager(), null);
        default:
            break;
        }
        return true;
    }
}
