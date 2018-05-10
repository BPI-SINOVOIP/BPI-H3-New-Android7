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

package android.support.v17.leanback.widget;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.ColorInt;
import android.support.v17.leanback.R;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ImageView;

import android.support.v17.leanback.util.DeviceStateMonitor;

/**
 * <p>A widget that draws a search affordance, represented by a round background and an icon.</p>
 *
 * The background color and icon can be customized.
 */
public class StatusBarView extends LinearLayout {
	
		private Context mContext;
		private View mRootView;
		private ImageView mNetStatView;
		private ImageView mBtStatView;
		private ImageView mUdiskView;
		private DeviceStateMonitor mDeviceStateMonitor;
		
    public StatusBarView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.statusBarViewStyle);
    }
    
    public StatusBarView(Context context, AttributeSet attrs, int defStyleAttr) {
    	super(context);
			mContext = context;
      final Resources res = context.getResources();
      LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			mRootView = inflater.inflate(R.layout.lb_status_bar, this, true);
			mNetStatView = (ImageView)mRootView.findViewById(R.id.net_status);
			mBtStatView = (ImageView)mRootView.findViewById(R.id.bt_status);
			mUdiskView = (ImageView)mRootView.findViewById(R.id.udisk_status);
			mNetStatView.setImageResource(R.drawable.lb_eth);
			mBtStatView.setImageResource(R.drawable.lb_bluetooth);
			mUdiskView.setImageResource(R.drawable.lb_udisk);
			mNetStatView.setVisibility(View.GONE);
			mBtStatView.setVisibility(View.GONE);
			mUdiskView.setVisibility(View.GONE);
			mDeviceStateMonitor = new DeviceStateMonitor(context,this);
    }
    
    
    public void setEthStats(boolean status){
    	if(status){
    		mNetStatView.setImageResource(R.drawable.lb_eth);
    		mBtStatView.setVisibility(View.VISIBLE);
    	}else{
    		mBtStatView.setVisibility(View.GONE);
    	}
    }
    public void setWifiStats(boolean status,int rssi){
    	if(status){
    		if(rssi<=50){
    			mNetStatView.setImageResource(R.drawable.lb_wifi_level4);
    		}else if(rssi>50 && rssi<=55 ){
    			mNetStatView.setImageResource(R.drawable.lb_wifi_level3);
    		}else if(rssi>55 && rssi<=70){
    			mNetStatView.setImageResource(R.drawable.lb_wifi_level2);
    		}else if(rssi>70){
    			mNetStatView.setImageResource(R.drawable.lb_wifi_level3);
    		}
    		mNetStatView.setVisibility(View.VISIBLE);
    	}else{
    		mNetStatView.setVisibility(View.GONE);
    	}

    }
    public void setBluetoothStat(boolean status){
    	if(status){
    		mBtStatView.setVisibility(View.VISIBLE);
    	}else{
    		mBtStatView.setVisibility(View.GONE);
    	}
    }
    public void setUdiskStat(boolean status){
    	if(status){
    		mUdiskView.setVisibility(View.VISIBLE);
    	}else{
    		mUdiskView.setVisibility(View.GONE);
    	}
    }

}
