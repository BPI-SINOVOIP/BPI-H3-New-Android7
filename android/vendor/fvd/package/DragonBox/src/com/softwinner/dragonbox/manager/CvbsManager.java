package com.softwinner.dragonbox.manager;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManagerEx;
import android.os.Handler;

import com.softwinner.dragonbox.platform.DisplayManagerPlatform;
import com.softwinner.dragonbox.platform.IDisplayManagerPlatform;
import com.softwinner.dragonbox.utils.AudioChannelUtil;
import com.softwinner.dragonbox.utils.MediaPlayerUtil;
import com.softwinner.dragonbox.utils.MusicerUtil;

public class CvbsManager {

	private Context mContext;
	private IDisplayManagerPlatform mDisplayManagerPlatform;
	//private MusicerUtil mMusicUtil;
	private MediaPlayerUtil mMediaPlayerUtil;
	
	private boolean mLeftPlaySuccess = false;
	private boolean mRightPlaySuccess = false;
	private Handler mHandler = new Handler();
	public CvbsManager(Context context) {
		mContext = context;
		mDisplayManagerPlatform = new DisplayManagerPlatform(mContext);
		//mMusicUtil = MusicerUtil.getInstance(context);
		mMediaPlayerUtil = MediaPlayerUtil.getInstance(context);
	}

	public void changeToCvbs() {
		mDisplayManagerPlatform.changeToCVBS();	
	}
	
	public void changeToHdmi() {
		mDisplayManagerPlatform.changeToHDMI();
	}

	public void playLeft(){
		//mMusicUtil.playMusic(MusicerUtil.PLAY_LEFT);
		mMediaPlayerUtil.playMusic(MediaPlayerUtil.PLAY_LEFT);
	}
	
	public void playRight(){
		/*stopPlaying();
		mHandler.postDelayed(new Runnable(){
			public void run(){
				mMusicUtil.playMusic(MusicerUtil.PLAY_RIGHT);				
			}
		}, 300);
		//mMusicUtil.playMusic(MusicerUtil.PLAY_RIGHT);*/
		mMediaPlayerUtil.stop();
		mMediaPlayerUtil.release();
		mMediaPlayerUtil.playMusic(MediaPlayerUtil.PLAY_RIGHT);
	}
	
	public void stopPlaying(){
		//mMusicUtil.pause();
		mMediaPlayerUtil.release();
	}

	public boolean isLeftPlaySuccess() {
		return mLeftPlaySuccess;
	}

	public void setLeftPlaySuccess(boolean leftPlaySuccess) {
		this.mLeftPlaySuccess = leftPlaySuccess;
	}

	public boolean isRightPlaySuccess() {
		return mRightPlaySuccess;
	}

	public void setRightPlaySuccess(boolean rightPlaySuccess) {
		this.mRightPlaySuccess = rightPlaySuccess;
	}

	public boolean getResult () {
		return isLeftPlaySuccess() && isRightPlaySuccess();
	}
	public boolean isCvbsStatusConn(){
		return mDisplayManagerPlatform.getTvHotPlugStatus();
	}
	
}
