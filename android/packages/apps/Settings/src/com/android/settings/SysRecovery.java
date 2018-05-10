/*************************************************************************
    > File Name: SysRecovery.java
    > Author: chenjuncong
    > Mail: chenjuncong@allwinnertech.com
    > Created Time: 2017年09月12日 星期二 10时21分32秒
 ************************************************************************/



package com.android.settings;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.android.internal.logging.MetricsProto.MetricsEvent;

public class SysRecovery extends OptionsMenuFragment {
    private static final String TAG = "SysRecovery";
    private View mContentView;
    private Button mInitiateButton;

    public SysRecovery(){
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState){
        mContentView = inflater.inflate(R.layout.sysrecovery, null);
        establishInitialiState();
        return mContentView;
    }

    @Override
    protected int getMetricsCategory(){
        return MetricsEvent.ACTION_SYSRECOVERY;
    }

    private void establishInitialiState(){
        mInitiateButton = (Button) mContentView.findViewById(R.id.initiate_sysrecovery);
        mInitiateButton.setOnClickListener(mInitiateListener);
    }

    private final Button.OnClickListener mInitiateListener = new Button.OnClickListener(){
        public void onClick(View v){
            showFinalConfirmation();
        }
    };

    private void showFinalConfirmation(){
        ((SettingsActivity) getActivity()).startPreferencePanel(SysRecoveryConfirm.class.getName(),
            null, R.string.sysrecovery_confirm_title, null, null, 0);
    }
}
