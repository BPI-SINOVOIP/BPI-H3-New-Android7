package com.softwinner.dragonbox.testcase;

import org.xmlpull.v1.XmlPullParser;

import com.softwinner.dragonbox.R;
import com.softwinner.dragonbox.manager.HdmiManager;
import com.softwinner.dragonbox.utils.AudioChannelUtil;

import android.media.AudioManager;
import android.media.AudioManagerEx;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.widget.TextView;

public class CaseHdmi extends IBaseCase {

	private TextView mMaxStatusTV;
	private TextView mMinStatusTV;
	private TextView mMinSoundStatusTV;

	HdmiManager hm;

	private static final int WHAT_CHECK_HDMI_STATUS = 1;
	private Handler mHandler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case WHAT_CHECK_HDMI_STATUS:
				int resId;
				boolean isConn = hm.isHDMIStatusConn();
				if (isConn) {
					resId = R.string.case_hdmi_status_success_text;
				} else {
					resId = R.string.case_hdmi_status_fail_text;
					mHandler.sendEmptyMessageDelayed(WHAT_CHECK_HDMI_STATUS,1000);
				}
				setDialogPositiveButtonEnable(isConn);
				mMaxStatusTV.setText(resId);
				mMinStatusTV.setText(resId);
				break;
			}
		};
	};

	public CaseHdmi(Context context) {
		super(context, R.string.case_hdmi_name, R.layout.case_hdmi_max,
				R.layout.case_hdmi_min, TYPE_MODE_MANUAL);
		hm = new HdmiManager(mContext);
		mMaxStatusTV = (TextView) mMaxView.findViewById(R.id.case_hdmi_status);
		mMinStatusTV = (TextView) mMinView.findViewById(R.id.case_hdmi_status_text);
		mMinSoundStatusTV = (TextView) mMinView.findViewById(R.id.case_hdmi_sound_status_text);
	}

	public CaseHdmi(Context context, XmlPullParser xmlParser) {
		this(context);
	}

	@Override
	public void onStartCase() {
		hm.changeToHDMI();//when supporting hdmi and cvbs output at the same time ,should delete this row.
		AudioChannelUtil.setOuputChannels(mContext, true ,AudioManagerEx.AUDIO_NAME_HDMI);
		hm.playMusic();
		mHandler.sendEmptyMessage(WHAT_CHECK_HDMI_STATUS);
	}

	@Override
	public void onStopCase() {
		hm.stopMusic();
		AudioChannelUtil.setOuputChannels(mContext, false,AudioManagerEx.AUDIO_NAME_HDMI);
		mMinSoundStatusTV.setText(getCaseResult() ? R.string.case_hdmi_sound_status_success_text
						: R.string.case_hdmi_sound_status_fail_text);
	}

	@Override
	public void reset() {
		super.reset();
		mMinStatusTV.setText(R.string.case_hdmi_status_text);
		mMinSoundStatusTV.setText(R.string.case_hdmi_sound_status_text);
	}
}
