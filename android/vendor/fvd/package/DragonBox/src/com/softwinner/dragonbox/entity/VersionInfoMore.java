package com.softwinner.dragonbox.entity;

import android.os.Build;
import android.os.SystemProperties;

public class VersionInfoMore extends VersionInfo{
	private String mWifiMac;
	private String mEth0Mac;
	private String mDDR;
	private long[] mNand;
	private String mSN;
	
	public VersionInfoMore() {
		super(SystemProperties.get("ro.product.firmware","null"), Build.MODEL, Build.DISPLAY, "");
	}
	
	public String getWifiMac() {
		return mWifiMac;
	}
	public void setWifiMac(String wifiMac) {
		mWifiMac = wifiMac;
	}
	public String getEth0Mac() {
		return mEth0Mac;
	}
	public void setEth0Mac(String eth0Mac) {
		mEth0Mac = eth0Mac;
	}
	public String getDDR() {
		return mDDR;
	}
	public void setDDR(String dDR) {
		mDDR = dDR;
	}

	public long[] getNand() {
		return mNand;
	}

	public void setNand(long[] nand) {
		this.mNand = nand;
	}

	public String getSN() {
		return mSN;
	}
	public void setSN(String sN) {
		mSN = sN;
	}
	
	

}
