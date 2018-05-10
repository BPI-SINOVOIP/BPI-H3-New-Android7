package com.softwinner.dragonbox.testcase;

import org.xmlpull.v1.XmlPullParser;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.media.AudioManager;
import android.media.AudioManagerEx;

import com.softwinner.dragonbox.utils.AudioChannelUtil;
import com.softwinner.dragonbox.R;
import com.softwinner.dragonbox.manager.CvbsManager;
import com.softwinner.dragonbox.testcase.IBaseCase.onResultChangeListener;

public class CaseCvbs extends IBaseCase implements OnClickListener,
		onResultChangeListener {

	Button leftButtonPass;
	Button leftButtonFail;
	Button rightButtonPass;
	Button rightButtonFail;
	ViewGroup leftContainer;
	ViewGroup rightContainer;
	TextView mShowAlertTV;

	TextView mMinLeftResultTV;
	TextView mMinRightResultTV;
	TextView mMinStatusTV;

	private TextView mMaxCvbsStatusTV;
	private TextView mMinCvbsStatusTV;

	CvbsManager cvbsManager;

	private static final int WHAT_CHECK_CVBS_STATUS = 1;
	private Handler mHandler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case WHAT_CHECK_CVBS_STATUS:
				int resId;
				boolean isConn = cvbsManager.isCvbsStatusConn();
				if (isConn) {
					resId = R.string.case_cvbs_status_success_text;
				} else {
					resId = R.string.case_cvbs_status_fail_text;
					mHandler.sendEmptyMessageDelayed(WHAT_CHECK_CVBS_STATUS,
							1000);
				}
				mMaxViewDialog.getButton(Dialog.BUTTON_POSITIVE).setEnabled(
						isConn);
				mMaxCvbsStatusTV.setText(resId);
				mMinCvbsStatusTV.setText(resId);
				break;
			}
		};
	};

	public CaseCvbs(Context context) {
		super(context, R.string.case_cvbs_name, R.layout.case_cvbs_max,
				R.layout.case_cvbs_min, TYPE_MODE_MANUAL);
		cvbsManager = new CvbsManager(mContext);
		leftButtonPass = (Button) mMaxView
				.findViewById(R.id.case_cvbs_left_button_passed);
		leftButtonFail = (Button) mMaxView
				.findViewById(R.id.case_cvbs_left_button_fail);
		rightButtonPass = (Button) mMaxView
				.findViewById(R.id.case_cvbs_right_button_passed);
		rightButtonFail = (Button) mMaxView
				.findViewById(R.id.case_cvbs_right_button_fail);
		leftContainer = (ViewGroup) mMaxView
				.findViewById(R.id.case_cvbs_left_container);
		rightContainer = (ViewGroup) mMaxView
				.findViewById(R.id.case_cvbs_right_container);

		leftButtonPass.setOnClickListener(this);
		leftButtonFail.setOnClickListener(this);
		leftButtonPass
				.setNextFocusForwardId(R.id.case_cvbs_right_button_passed);
		leftButtonFail
				.setNextFocusForwardId(R.id.case_cvbs_right_button_passed);
		rightButtonPass.setOnClickListener(this);
		rightButtonFail.setOnClickListener(this);
		mShowAlertTV = (TextView) mMaxView.findViewById(R.id.case_cvbs_alert);

		mMinLeftResultTV = (TextView) mMinView
				.findViewById(R.id.case_cvbs_left);
		mMinRightResultTV = (TextView) mMinView
				.findViewById(R.id.case_cvbs_right);

		mMaxCvbsStatusTV = (TextView) mMaxView
				.findViewById(R.id.case_cvbs_status);
		mMinCvbsStatusTV = (TextView) mMinView
				.findViewById(R.id.case_cvbs_status_text);

		setShowDialogButton(false);
	}

	public CaseCvbs(Context context, XmlPullParser xmlParser) {
		this(context);
	}

	/**
	 * change show item,left for left sound
	 * 
	 * @param isLeft
	 *            ture for show left ,false for show right
	 */
	private void resetTest(boolean isLeft) {
		if (isLeft) {
			leftContainer.setVisibility(View.VISIBLE);
			rightContainer.setVisibility(View.GONE);

			mMaxViewDialog.setTitle(R.string.case_cvbs_left_title);
			mShowAlertTV.setText(R.string.case_cvbs_left_alert);
			cvbsManager.playLeft();
		} else {
			leftContainer.setVisibility(View.GONE);
			rightContainer.setVisibility(View.VISIBLE);

			mMaxViewDialog.setTitle(R.string.case_cvbs_right_title);
			mShowAlertTV.setText(R.string.case_cvbs_right_alert);
			cvbsManager.playRight();
			rightButtonPass.requestFocus();
		}
	}

	@Override
	public void onStartCase() {
		cvbsManager.changeToCvbs();//when supporting hdmi and cvbs output at the same time ,should delete this row.
        AudioChannelUtil.setOuputChannels(mContext, true,AudioManagerEx.AUDIO_NAME_CODEC);
		mHandler.sendEmptyMessage(WHAT_CHECK_CVBS_STATUS);
		resetTest(true);
	}

	@Override
	public void onStopCase() {
		cvbsManager.changeToHdmi();
		cvbsManager.stopPlaying();
		setCaseResult(cvbsManager.getResult());
        AudioChannelUtil.setOuputChannels(mContext, false,AudioManagerEx.AUDIO_NAME_CODEC);
		mMinLeftResultTV
				.setText(cvbsManager.isLeftPlaySuccess() ? R.string.case_cvbs_left_success
						: R.string.case_cvbs_left_fail);
		mMinRightResultTV
				.setText(cvbsManager.isRightPlaySuccess() ? R.string.case_cvbs_right_success
						: R.string.case_cvbs_right_fail);
	}

	@Override
	public void reset() {
		super.reset();
		mMinLeftResultTV.setText(R.string.case_cvbs_left);
		mMinCvbsStatusTV.setText(R.string.case_cvbs_status_text);
		mMinRightResultTV.setText(R.string.case_cvbs_right);
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.case_cvbs_left_button_passed:
			resetTest(false);
			cvbsManager.setLeftPlaySuccess(true);
			break;
		case R.id.case_cvbs_left_button_fail:
			resetTest(false);
			cvbsManager.setLeftPlaySuccess(false);
			break;
		case R.id.case_cvbs_right_button_passed:
			cvbsManager.setRightPlaySuccess(true);
			mMaxViewDialog.dismiss();
			break;
		case R.id.case_cvbs_right_button_fail:
			cvbsManager.setRightPlaySuccess(false);
			mMaxViewDialog.dismiss();
			break;

		default:
			break;
		}
	}

	@Override
	public void onResultChange(IBaseCase baseCase, boolean caseResult) {
		mMinLeftResultTV
				.setText(cvbsManager.isLeftPlaySuccess() ? R.string.case_cvbs_left_success
						: R.string.case_cvbs_left_fail);
		mMinRightResultTV
				.setText(cvbsManager.isRightPlaySuccess() ? R.string.case_cvbs_right_success
						: R.string.case_cvbs_right_fail);
	}

}
