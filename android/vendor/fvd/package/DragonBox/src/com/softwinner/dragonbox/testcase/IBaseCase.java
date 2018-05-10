package com.softwinner.dragonbox.testcase;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.DialogInterface.OnShowListener;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.softwinner.dragonbox.DragonBoxMain;
import com.softwinner.dragonbox.R;

public abstract class IBaseCase {
	public View mActionView;
	private TextView mResultTV;

	public View mMinView;
	public View mMaxView;

	public Context mContext;
	/**
	 * the MaxViewDialog,showing the detail.use the
	 * IBaseCase.setDialog***Listener instead of
	 * mMaxViewDialog.set***listener(). because it had use Proxy instead.
	 */
	public AlertDialog mMaxViewDialog;
	private boolean mShowDialogButton = true;

	private OnClickListener mPositiveButtonListener;
	private OnClickListener mNegativeButtonListener;
	private OnDismissListener mDismissListener;
	private OnShowListener mOnShowListener;

	private boolean mIsTesting = false;

	/**
	 * which type the Case is
	 */
	private int mType = TYPE_MODE_MANUAL;
	// man must click the result success or not
	public static final int TYPE_MODE_MANUAL = 1;
	// can judge automatic
	public static final int TYPE_MODE_AUTO = 2;

	/**
	 * while text loop ,it indicate that which is the next test.
	 */
	private IBaseCase mNextCase;

	private ArrayList<onResultChangeListener> mResultChangeListeners = new ArrayList<onResultChangeListener>();
	private boolean mCaseReult = false;

	//与某项同时测试会有冲突的case
	private IBaseCase mAfterCase = null;
	
	private Handler mHandler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			if (mAfterCase != null && mAfterCase.mIsTesting) {
				sendEmptyMessageDelayed(0, 500);
			} else {
				startCase();
			}
		};
	};

	/**
	 * 
	 * @param context
	 * @param resId
	 *            for the name about buttons,case need a name
	 * @param maxViewId
	 *            the dialog's view
	 * @param minViewId
	 *            the main mini view
	 * @param type
	 *            the type,TYPE_MODE_MANUAL or TYPE_MODE_AUTO
	 */
	public IBaseCase(Context context, final int resId, int maxViewId,
			int minViewId, int type) {
		mContext = context;

		setMinView(minViewId);
		setMaxView(maxViewId);
		mActionView = (View) mMinView.findViewById(R.id.cases_action_view);
		mResultTV = (TextView) mMinView.findViewById(R.id.case_result);
		if (mActionView == null) {
			mActionView = (Button) View.inflate(mContext, R.layout.leftbutton,
					null);
		}
		setButtonText(resId);
		mMaxViewDialog = new AlertDialog.Builder(mContext).setView(mMaxView)
				.setTitle(resId).create();
		mActionView.setTag(this);
		mActionView.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View view) {
				stopCase();
				if (mShowDialogButton) {
					mMaxViewDialog.setButton(AlertDialog.BUTTON_POSITIVE,
							mContext.getString(R.string.case_result_success),
							new OnClickListener() {

								@Override
								public void onClick(
										DialogInterface dialogInterface,
										int which) {
									if (mPositiveButtonListener != null) {
										mPositiveButtonListener.onClick(
												dialogInterface, which);
									}
									setCaseResult(true);
								}
							});
					mMaxViewDialog.setButton(AlertDialog.BUTTON_NEGATIVE,
							mContext.getString(R.string.case_result_fail),
							new OnClickListener() {

								@Override
								public void onClick(
										DialogInterface dialogInterface,
										int which) {
									if (mNegativeButtonListener != null) {
										mNegativeButtonListener.onClick(
												dialogInterface, which);
									}
									setCaseResult(false);
								}
							});
				}
				mMaxViewDialog.setOnDismissListener(new OnDismissListener() {

					@Override
					public void onDismiss(DialogInterface dialogInterface) {
						if (mDismissListener != null) {
							mDismissListener.onDismiss(dialogInterface);
						}
						stopCase();
						if (mNextCase != null) {
							mNextCase.mActionView.performClick();
							mNextCase = null;
						}
					}
				});
				mMaxViewDialog.setCancelable(false);
				mMaxViewDialog.setOnShowListener(mOnShowListener);

				mMaxViewDialog.setOnKeyListener(new OnKeyListener() {

					@Override
					public boolean onKey(DialogInterface arg0, int keycode,
							KeyEvent keyEvent) {
						if (keycode == KeyEvent.KEYCODE_MEDIA_STOP
								&& keyEvent.getAction() == KeyEvent.ACTION_UP) {
							((DragonBoxMain) mContext).cancelAllTest();
						}
						return false;
					}
				});

				mMaxViewDialog.show();
				// setDialogPositiveButtonEnable(false);
				startCase();
			}
		});
		mType = type;
	}

	private void setButtonText(int resId) {
		if (mActionView instanceof Button) {
			((Button) mActionView).setText(resId);
		}
	}

	private void setMinView(int resID) {
		mMinView = View.inflate(mContext, resID, null);
	}

	private void setMaxView(int resID) {
		mMaxView = View.inflate(mContext, resID, null);
		mMaxView.setTag(this);
	}

	public int getType() {
		return mType;
	}

	public void setCaseResult(boolean caseResult) {
		mCaseReult = caseResult;
		for (onResultChangeListener listener : mResultChangeListeners) {
			listener.onResultChange(this, mCaseReult);
		}
		// mMinView.setBackgroundResource(caseResult ? R.drawable.main_bg :
		// R.drawable.main_bg);
		if (mResultTV != null) {
			mResultTV.setText(caseResult ? R.string.case_result_success
					: R.string.case_result_fail);
			mResultTV
					.setBackgroundResource(caseResult ? R.drawable.result_success_bg
							: R.drawable.result_fail_bg);
		}
	}

	public boolean getCaseResult() {
		return mCaseReult;
	}

	public void addRusultChangeListener(onResultChangeListener listener) {
		mResultChangeListeners.add(listener);
	}

	public void setDialogPositiveBTListener(OnClickListener positiveListener) {
		mPositiveButtonListener = positiveListener;
	}

	public void setDialogNegativeBTListener(OnClickListener positiveListener) {
		mNegativeButtonListener = positiveListener;
	}

	public void setDialogShowListener(OnShowListener showListener) {
		mOnShowListener = showListener;
	}

	public void setDialogDiamissListener(OnDismissListener dismissListener) {
		mDismissListener = dismissListener;
	}

	public void setNextCase(IBaseCase baseCase) {
		this.mNextCase = baseCase;
	}

	public void setShowDialogButton(boolean showDialogButton) {
		this.mShowDialogButton = showDialogButton;
	}

	public void setDialogPositiveButtonEnable(boolean enable) {
		if (mMaxViewDialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
			mMaxViewDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
					enable);
		}
	}

	public boolean startCase() {
		if (mIsTesting) {
			return false;
		}
		mIsTesting = true;
		onStartCase();
		return true;
	}

	/**
	 * like startCase
	 * 
	 * @return
	 */
	public void startCaseDelay(int millisecond) {
		mHandler.sendEmptyMessageDelayed(0, millisecond);
	}
	
	public void startCastAfterCase(IBaseCase afterCase){
		mAfterCase = afterCase;
		mHandler.sendEmptyMessageDelayed(0, 0);
	}

	public boolean stopCase() {
		mHandler.removeMessages(0);
		if (!mIsTesting) {
			return false;
		}
		mIsTesting = false;
		onStopCase();
		return true;
	}

	/**
	 * the case will be auto start, logic may write here
	 */
	public abstract void onStartCase();

	/**
	 * when max dialog is dismissed, it will call this method when test is
	 * finished,manual stop the case,so it would not always running;
	 */
	public abstract void onStopCase();

	public void reset() {
		if (mResultTV != null) {
			mResultTV.setText(R.string.case_result);
			mResultTV.setBackgroundResource(R.drawable.main_bg);
		}
		// mMinView.setBackgroundResource(R.drawable.trans);
		mNextCase = null;
		if (mMaxViewDialog.isShowing()) {
			mMaxViewDialog.setOnDismissListener(null);
			mMaxViewDialog.dismiss();
		}
		setCaseResult(false);
		// stopCase();
	}

	public void cancel(){
		mNextCase = null;
		if (mMaxViewDialog.isShowing()) {
			mMaxViewDialog.setOnDismissListener(null);
			mMaxViewDialog.dismiss();
		}
	}
	
	public interface onResultChangeListener {
		public void onResultChange(IBaseCase baseCase, boolean caseResult);
	}
}
