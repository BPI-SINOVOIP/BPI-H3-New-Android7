package com.softwinner.dragonbox.testcase;

import java.util.List;
import java.util.Stack;

import org.xmlpull.v1.XmlPullParser;

import android.app.AlertDialog;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.softwinner.dragonbox.R;
import com.softwinner.dragonbox.entity.WifiConnInfo;
import com.softwinner.dragonbox.manager.WifiConnManager;
import com.softwinner.dragonbox.manager.WifiConnManager.OnWifiConnChangeListener;
import com.softwinner.dragonbox.utils.WifiUtil;
import com.softwinner.dragonbox.view.WifiConnListAdapter;
import com.softwinner.dragonbox.view.WifiScanListAdapter;

public class CaseWifi extends IBaseCase implements OnWifiConnChangeListener {
	private static final String TAG = "CaseWifi";
	private WifiConnManager mWifiConnManager;

	private WifiManager mWifiManager;

	private ListView mMaxScanResultLV;
	private ListView mMaxConnResultLV;
	private WifiScanListAdapter mScanListAdapter;
	private WifiConnListAdapter mConnListAdapter;

	private TextView mMinWifiStatusTV;
	private TextView mMinConnNameTV;
	private TextView mMinIpAddrTV;
	private TextView mMinConnStrengthTV;

	private TextView mMaxEfuseResultTV;
	private TextView mMinEfuseResultTV;

	private boolean mNeedTestEfuse = false;
	public boolean mWifiEfuseTestResult = false;
	
	public CaseWifi(Context context) {
		super(context, R.string.case_wifi_name, R.layout.case_wifi_max,
				R.layout.case_wifi_min, TYPE_MODE_AUTO);
		mWifiManager = (WifiManager) mContext
				.getSystemService(Context.WIFI_SERVICE);
		mMaxScanResultLV = (ListView) mMaxView
				.findViewById(R.id.case_wifi_scan_list);
		mMaxConnResultLV = (ListView) mMaxView
				.findViewById(R.id.case_wifi_conn_list);

		mMinWifiStatusTV = (TextView) mMinView
				.findViewById(R.id.case_wifi_status_text);
		mMinConnNameTV = (TextView) mMinView
				.findViewById(R.id.case_wifi_conn_name);
		mMinIpAddrTV = (TextView) mMinView.findViewById(R.id.case_wifi_ip_addr);
		mMinConnStrengthTV = (TextView) mMinView
				.findViewById(R.id.case_wifi_conn_strength);

		mMaxEfuseResultTV = (TextView) mMaxView
				.findViewById(R.id.case_wifi_efuse_status_text);
		mMinEfuseResultTV = (TextView) mMinView
				.findViewById(R.id.case_wifi_efuse_status_text);

		mScanListAdapter = new WifiScanListAdapter(mContext);
		mConnListAdapter = new WifiConnListAdapter(mContext);
		mMaxScanResultLV.setAdapter(mScanListAdapter);
		mMaxConnResultLV.setAdapter(mConnListAdapter);
	}
	
	

	public CaseWifi(Context context, XmlPullParser xmlParser) {
		this(context);
		WifiConnInfo info = new WifiConnInfo();
		info.mMaxRSSI = Integer.parseInt(xmlParser.getAttributeValue(null,
				"maxRSSI"));
		info.ssid = xmlParser.getAttributeValue(null, "wifiSSID");
		info.mWifiPWD = xmlParser.getAttributeValue(null, "wifiPWD");
		info.isConfig = true;
		mWifiConnManager = new WifiConnManager(context, info);
		mWifiConnManager.setOnWifiConnChangeListener(this);
		mNeedTestEfuse = mWifiConnManager.isNeedTestEfuse();
	}

	public CaseWifi(Context context, int maxRSSI, String ssid, String wifiPWD) {
		this(context);
		WifiConnInfo info = new WifiConnInfo();
		info.mMaxRSSI = maxRSSI;
		info.ssid = ssid;
		info.mWifiPWD = wifiPWD;
		info.isConfig = true;
		mWifiConnManager = new WifiConnManager(context, info);
		mWifiConnManager.setOnWifiConnChangeListener(this);
		mNeedTestEfuse = mWifiConnManager.isNeedTestEfuse();
	}

	@Override
	public void onStartCase() {
		mWifiEfuseTestResult = false;
		mWifiConnManager.startWifiTest();
		setDialogPositiveButtonEnable(false);
		updateWifiEfuseView(mNeedTestEfuse, mWifiEfuseTestResult);
	}

	@Override
	public void onStopCase() {
		mWifiConnManager.stopScanWifi();
		updateWifiEfuseView(mNeedTestEfuse, mWifiEfuseTestResult);
	}

	@Override
	public void onWifiStateChange(int state) {

	}

	@Override
	public void onSearching(List<WifiConnInfo> wifiConnInfos) {
		mScanListAdapter.setWifiConnInfos(wifiConnInfos);
		mMinWifiStatusTV.setText(R.string.case_wifi_status_searching);
		if (mNeedTestEfuse && !mWifiEfuseTestResult) {
			mWifiEfuseTestResult = mWifiConnManager.getWifiEfuseTest();
		}
		updateWifiEfuseView(mNeedTestEfuse, mWifiEfuseTestResult);
		Log.d(TAG, "mWifiEfuseTestResult=" + mWifiEfuseTestResult + " mNeedTestEfuse=" + mNeedTestEfuse);
	}

	@Override
	public void onSearchEnd() {
		mMinWifiStatusTV.setText(R.string.case_wifi_status_search_end);
	}

	@Override
	public void onConnWifi(List<WifiInfo> wifiInfos, WifiConnInfo connInfo) {
		
		boolean wifiConnResult = true;
		for (WifiInfo wifiinfo : wifiInfos) {
			if (wifiinfo == null || wifiinfo.getSSID() == null
					|| !wifiinfo.getSSID().contains(connInfo.ssid)
					|| wifiinfo.getIpAddress() == 0
					|| wifiInfos.size() < WifiConnManager.MAX_SIZE
					|| wifiinfo.getRssi() <= connInfo.mMaxRSSI) {
				Log.d(TAG, "WifiInfo:" + wifiinfo);
				Log.d(TAG, "ipaddr=" + wifiinfo.getIpAddress() + ", size="
						+ wifiInfos.size() + ", rssi" + wifiinfo.getRssi());
				wifiConnResult = false;
				break;
			}
		}
		mMinConnNameTV.setText(mContext.getString(R.string.case_wifi_conn_name)
				+ "[" + wifiInfos.get(0).getSSID() + "]");
		mMinIpAddrTV
				.setText(mContext.getString(R.string.case_wifi_ip_addr) + "["
						+ WifiUtil.intToIp(wifiInfos.get(0).getIpAddress())
						+ "]");
		mMinConnStrengthTV.setText(mContext
				.getString(R.string.case_wifi_conn_strength)
				+ "["
				+ wifiInfos.get(0).getRssi() + "DB]");
		if (wifiConnResult) {
			stopCase();
		}
		mMinWifiStatusTV.setText(wifiConnResult ? R.string.case_wifi_status_success : R.string.case_wifi_status_fail);
		if (mNeedTestEfuse) {
			setDialogPositiveButtonEnable(wifiConnResult & mWifiEfuseTestResult);
			setCaseResult(wifiConnResult & mWifiEfuseTestResult);	
		} else {
			setDialogPositiveButtonEnable(wifiConnResult);
			setCaseResult(wifiConnResult);
		}
		
		mConnListAdapter.setWifiConnInfos(wifiInfos);
	}

	@Override
	public void reset() {
		super.reset();
		mMinWifiStatusTV.setText(R.string.case_wifi_status_text);
		mMinConnNameTV.setText(R.string.case_wifi_conn_name);
		mMinIpAddrTV.setText(R.string.case_wifi_ip_addr);
		mMinConnStrengthTV.setText(R.string.case_wifi_conn_strength);
	}

	private void updateWifiEfuseView(boolean needTest, boolean success){
		if (needTest) {
			mMinEfuseResultTV.setVisibility(View.VISIBLE);
			mMaxEfuseResultTV.setVisibility(View.VISIBLE);
			mMinEfuseResultTV.setText(success ? R.string.case_wifi_efuse_status_success_text : R.string.case_wifi_efuse_status_fail_text);
			mMaxEfuseResultTV.setText(success ? R.string.case_wifi_efuse_status_success_text : R.string.case_wifi_efuse_status_fail_text);
		} else {
			mMinEfuseResultTV.setVisibility(View.GONE);
			mMaxEfuseResultTV.setVisibility(View.GONE);
		}
	}
}
