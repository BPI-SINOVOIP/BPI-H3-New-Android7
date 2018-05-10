package com.softwinner.dragonbox.testcase;

import org.xmlpull.v1.XmlPullParser;

import android.content.Context;
import android.widget.TextView;
import com.softwinner.Gpio;
import android.app.Activity;

import com.softwinner.dragonbox.R;
import com.softwinner.dragonbox.manager.LedManager;

public class CaseResetKey extends IBaseCase {

    private static final char RESET_KEY_GROUP = 'L';
    private static final int RESET_KEY_NUM = 4;
    private boolean mTerminate = false;
    private int ret = 1;
    private TextView mResultText;
    private Context mContext;
    private Runnable mDetectRunnable = new Runnable(){
        @Override
        public void run (){
            while(true){
                if(mTerminate){
                    ret = Gpio.readGpio(RESET_KEY_GROUP, RESET_KEY_NUM);
                    ((Activity)mContext).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setCaseResult(ret==0);
                            setDialogPositiveButtonEnable(ret==0);
                            mTerminate = ret!=0;
                        }
                    });
                    //System.out.println("ret = " + ret);
                }
                try {
                    Thread.sleep(50);
                }catch(Exception e) {
                }
            }
        }
    };

    public CaseResetKey(Context context) {
        super(context , R.string.case_reset_name, 
                R.layout.case_reset_max, R.layout.case_reset_min,
                TYPE_MODE_AUTO);
        mResultText = (TextView)mMinView.findViewById(R.id.case_reset_status);
        mContext = context;
    }

    public CaseResetKey(Context context, XmlPullParser xmlParser) {
        this(context);
    }

    Thread  mThread = null;
    @Override
    public void onStartCase() {
        mThread = new Thread(mDetectRunnable);
        mThread.start();
        mTerminate = true;
    }

    @Override
    public void onStopCase() {
        mResultText.setText(R.string.case_reset_status_success_text);
    }

    @Override
    public void reset() {
        super.reset();
        mResultText.setText(R.string.case_reset_status_text);
        mTerminate = true;
    }

}
