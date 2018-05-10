package com.softwinner.dragonbox.testcase;

import org.xmlpull.v1.XmlPullParser;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.TextView;

import com.softwinner.dragonbox.R;
import com.softwinner.dragonbox.entity.VersionInfo;
import com.softwinner.dragonbox.entity.VersionInfoMore;
import com.softwinner.dragonbox.manager.VersionManager;

public class CaseVersion extends IBaseCase {
	public VersionManager versiomManager;

	private TextView mMinFireware;
	private TextView mMinDisplay;
	private TextView mMinModel;
	private TextView mMinWifiMac;
	private TextView mMinEthMac;
	private TextView mMinNand;
	private TextView mMinDDR;

	private TextView mMaxFireWare;
	private TextView mMaxFireWareConf;
	private TextView mMaxModel;
	private TextView mMaxModelConf;
	private TextView mMaxDisplay;
	private TextView mMaxDisplayConf;

	private TextView mMaxWifiMac;
	private TextView mMaxEthMac;
	private TextView mMaxNand;
	private TextView mMaxDDR;

	private CaseVersion(Context context) {
		super(context, R.string.case_version_name, R.layout.case_version_max,
				R.layout.case_version_min, TYPE_MODE_AUTO);
		versiomManager = new VersionManager(context);
		mMinFireware = (TextView) mMinView
				.findViewById(R.id.case_version_fireware);
		mMinDisplay = (TextView) mMinView
				.findViewById(R.id.case_version_display);
		mMinModel = (TextView) mMinView.findViewById(R.id.case_version_model);
		mMinWifiMac = (TextView) mMinView
				.findViewById(R.id.case_version_wifi_mac);
		mMinEthMac = (TextView) mMinView
				.findViewById(R.id.case_version_eth_mac);
		mMinNand = (TextView) mMinView.findViewById(R.id.case_version_nand);
		mMinDDR = (TextView) mMinView.findViewById(R.id.case_version_ddr);

		mMaxFireWare = (TextView) mMaxView
				.findViewById(R.id.case_version_fireware);
		mMaxFireWareConf = (TextView) mMaxView
				.findViewById(R.id.case_version_fireware_conf);
		mMaxModel = (TextView) mMaxView.findViewById(R.id.case_version_model);
		mMaxModelConf = (TextView) mMaxView
				.findViewById(R.id.case_version_model_conf);
		mMaxDisplay = (TextView) mMaxView
				.findViewById(R.id.case_version_display);
		mMaxDisplayConf = (TextView) mMaxView
				.findViewById(R.id.case_version_display_conf);

		mMaxWifiMac = (TextView) mMaxView
				.findViewById(R.id.case_version_wifi_mac);
		mMaxEthMac = (TextView) mMaxView
				.findViewById(R.id.case_version_eth_mac);
		mMaxNand = (TextView) mMaxView.findViewById(R.id.case_version_nand);
		mMaxDDR = (TextView) mMaxView.findViewById(R.id.case_version_ddr);
	}

	public CaseVersion(Context context, XmlPullParser xmlParser) {
		this(context);
		VersionInfo vInfo = new VersionInfo();
		vInfo.setFireware(xmlParser.getAttributeValue(null, "fireware"));
		vInfo.setDispaly(xmlParser.getAttributeValue(null, "display"));
		vInfo.setModel(xmlParser.getAttributeValue(null, "model"));
		versiomManager.setConfVersionInfo(vInfo);
	}

	@Override
	public void onStartCase() {
		updateView();
		setDialogPositiveButtonEnable(false);
	}

	private void updateView() {
		VersionInfo confInfo = versiomManager.getConfVersionInfo();
		VersionInfoMore sysInfo = versiomManager.getSysVersionInfo();

		// String versionInfo = mContext.getString(R.string.case_version_info,
		// vInfo.getFireware(), vInfo.getDispaly(), vInfo.getModle());
		// String curVersionInfo =
		// mContext.getString(R.string.case_version_info,
		// curVInfo.getFireware(), curVInfo.getDispaly(), curVInfo.getModle());

		mMinFireware.setText(mContext
				.getString(R.string.case_version_info_fireware)
				+ sysInfo.getFireware());
		mMinDisplay.setText(mContext
				.getString(R.string.case_version_info_display)
				+ sysInfo.getDispaly());
		mMinModel.setText(mContext.getString(R.string.case_version_info_model)
				+ sysInfo.getModel());
		mMinWifiMac
				.setText(mContext.getString(
						R.string.case_version_other_info_wifi_mac,
						sysInfo.getWifiMac()));
		mMinEthMac
				.setText(mContext.getString(
						R.string.case_version_other_info_eth_mac,
						sysInfo.getEth0Mac()));
		mMinNand.setText(mContext.getString(
				R.string.case_version_other_info_nand,
				Formatter.formatFileSize(mContext, sysInfo.getNand()[0])));
		mMinDDR.setText(mContext.getString(
				R.string.case_version_other_info_ddr, sysInfo.getDDR()));

		mMaxFireWare.setText(sysInfo.getFireware());
		mMaxFireWareConf.setText(confInfo.getFireware());
		mMaxModel.setText(sysInfo.getModel());
		mMaxModelConf.setText(confInfo.getModel());
		mMaxDisplay.setText(sysInfo.getDispaly());
		mMaxDisplayConf.setText(confInfo.getDispaly());

		mMaxWifiMac
				.setText(mContext.getString(
						R.string.case_version_other_info_wifi_mac,
						sysInfo.getWifiMac()));
		mMaxEthMac
				.setText(mContext.getString(
						R.string.case_version_other_info_eth_mac,
						sysInfo.getEth0Mac()));
		mMaxNand.setText(mContext.getString(
				R.string.case_version_other_info_nand,
				Formatter.formatFileSize(mContext, sysInfo.getNand()[0])));
		mMaxDDR.setText(mContext.getString(
				R.string.case_version_other_info_ddr, sysInfo.getDDR()));
		boolean result = confInfo.equals(sysInfo);
		stopCase();
		setCaseResult(result);
		setDialogPositiveButtonEnable(result);
	}

	@Override
	public void onStopCase() {

	}

	@Override
	public void reset() {
		super.reset();
	}
}
