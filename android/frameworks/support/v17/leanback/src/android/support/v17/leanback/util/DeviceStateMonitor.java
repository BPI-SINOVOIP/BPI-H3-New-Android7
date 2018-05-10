/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.support.v17.leanback.util;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.support.v17.leanback.R;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.content.BroadcastReceiver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothClass;



import android.support.v17.leanback.widget.StatusBarView;

public class DeviceStateMonitor {
    private Context mContext;
    private StatusBarView mStatusBar;

    private BroadcastReceiver mReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
	    if (action.equals(WifiManager.RSSI_CHANGED_ACTION)
                || action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)
                || action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                || action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                	Log.d("IKE","wifi or networkchanged");
            }else if(action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
            		Log.d("IKE","media mounted");
            		mStatusBar.setUdiskStat(true);
            }else if(action.equals(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action))){
                        Log.d("IKE","bluetooth state change");
                        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                        if(adapter!=null)
                            mStatusBar.setBluetoothStat(adapter.isEnabled());
            }else if(action.equals(Intent.ACTION_MEDIA_UNMOUNTED)
            		|| action.equals(Intent.ACTION_MEDIA_EJECT)){
            		Log.d("IKE","media unmount or eject");
            		//mStatusBar.setUdiskStat(false);
            }
        }
    };
  
    public DeviceStateMonitor(Context context,StatusBarView statusBar){
        mContext = context;
        mStatusBar = statusBar;
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mIntentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mIntentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        mIntentFilter.addDataScheme("file");
        //mContext.registerReceiver(mReceiver, mIntentFilter);
        Log.d("IKE","DeviceStateMonitor init add media");
    }
}
