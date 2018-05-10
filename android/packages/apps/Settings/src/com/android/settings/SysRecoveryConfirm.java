/*************************************************************************
    > File Name: SysRecoveryConfirm.java
    > Author: chenjuncong
    > Mail: chenjuncong@allwinnertech.com
    > Created Time: 2017年09月12日 星期二 14时25分51秒
 ************************************************************************/

package com.android.settings;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.Button;
import android.content.Intent;

import com.android.internal.logging.MetricsProto.MetricsEvent;

public class SysRecoveryConfirm extends OptionsMenuFragment{
    private View mContentView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstancesState){
        mContentView = inflater.inflate(R.layout.sysrecovery_confirm, null);
        establishFinalConfirmationState();
        return mContentView;
    }

    private void establishFinalConfirmationState(){
        mContentView.findViewById(R.id.execute_sysrecovery)
            .setOnClickListener(mFinalClickListener);
    }

    private Button.OnClickListener mFinalClickListener = new Button.OnClickListener(){
        public void onClick(View v){
            if(Utils.isMonkeyRunning()){
                return;
            }
            doSysRecovery();
        }
    };

    private void doSysRecovery(){
        Intent intent = new Intent(Intent.ACTION_SYSRECOVERY);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        getActivity().sendBroadcast(intent);
    }

    @Override
    protected int getMetricsCategory(){
        return MetricsEvent.ACTION_SYSRECOVERY;
    }
}
