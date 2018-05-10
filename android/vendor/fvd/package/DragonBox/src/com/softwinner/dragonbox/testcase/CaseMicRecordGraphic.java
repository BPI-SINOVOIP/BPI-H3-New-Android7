package com.softwinner.dragonbox.testcase;

import org.xmlpull.v1.XmlPullParser;
import java.io.File;
import java.util.LinkedList;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;

import com.softwinner.dragonbox.R;

public class CaseMicRecordGraphic extends IBaseCase implements SurfaceHolder.Callback{
	private static String TAG = "CaseMicRecordGraphic";
    private static int SLEEP_TIME = 25;

    private Paint circlePaint;
    private Paint center;
    private Paint paintLine;
    private Paint mPaint;
    private int mChannelCount = 0;
    private int mChannel = AudioFormat.CHANNEL_IN_STEREO;
    private int mSource = MediaRecorder.AudioSource.MIC;
    private int mFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int mSampleRate = 44100;

    private int mRateX = 3;//

    private int mXStep = 10;
    private int mPadding = 60;
    private final Object mSurfaceLock = new Object();
    private DrawThread mThread;
    private RecordThread mRecordThread;
    private AudioRecord ar;
    
    private TextView mTvMicRecordResult;

    private LinkedList[] mPcmBuffers ;//两个链表，分别对应 左右声道
	
	SurfaceView mMaxSurfaceV;//显示声音波形
	
	public CaseMicRecordGraphic(Context context) {
		super(context, R.string.case_micrecord_name, R.layout.case_micrecord_graphic_max,
				R.layout.case_micrecord_min, TYPE_MODE_MANUAL);
		mMaxSurfaceV = (SurfaceView) mMaxView.findViewById(R.id.case_mic_surface);
		mMaxSurfaceV.setZOrderOnTop(true);
		LayoutParams lp = mMaxSurfaceV.getLayoutParams();
		lp.width = (int)context.getResources().getDimension(R.dimen.camera_size_width);
		lp.height = (int)context.getResources().getDimension(R.dimen.camera_size_height);
		Log.e("mylog", "width = "+lp.width+"height = "+lp.height);
		mMaxSurfaceV.setLayoutParams(lp);
		mTvMicRecordResult = (TextView) mMinView.findViewById(R.id.case_micrecord_status);
	}

	public void setParameter(int iChannel){
        /*mChannel = AudioFormat.CHANNEL_IN_STEREO;//左右声道；立体声
        switch (mChannel){
            case AudioFormat.CHANNEL_IN_MONO:
                mChannelCount = 1;
                break;
            case AudioFormat.CHANNEL_IN_STEREO:
                mChannelCount = 2;
                break;
            default:
                mChannelCount = 2;
        }*/
		mChannelCount = iChannel;
        mSource = MediaRecorder.AudioSource.MIC;
        mFormat = AudioFormat.ENCODING_PCM_16BIT;
        mPcmBuffers =  new LinkedList[mChannelCount];
        for (int i = 0; i < mChannelCount; i++){
            mPcmBuffers[i] = new LinkedList();//生成空链表，初始化。
        }
    }
	
	private void init(){
        circlePaint = new Paint();//画圆
        circlePaint.setColor(Color.rgb(246, 131, 126));//设置上圆的颜色
        center = new Paint();
        center.setColor(Color.rgb(39, 199, 175));// 画笔为color
        center.setStrokeWidth(1);// 设置画笔粗细
        center.setAntiAlias(true);
        center.setFilterBitmap(true);
        center.setStyle(Paint.Style.FILL);
        paintLine =new Paint();
        paintLine.setColor(Color.rgb(169, 169, 169));
        mPaint = new Paint();
        mPaint.setColor(Color.rgb(39, 199, 175));// 画笔为color
        mPaint.setStrokeWidth(1);// 设置画笔粗细
        mPaint.setAntiAlias(true);
        mPaint.setFilterBitmap(true);
        mPaint.setStyle(Paint.Style.FILL);
    }
	
	public CaseMicRecordGraphic(Context context, XmlPullParser xmlParser) {
		this(context);
		String sChannel = xmlParser.getAttributeValue(null, "channel");
		int iChannel;//mic数
		try{
			iChannel = Integer.parseInt(sChannel);
		} catch (Exception e) {
			e.printStackTrace();
			iChannel = 1;
		}
		setParameter(iChannel);
	    init();
	}

	@Override
	public void onStartCase() {
		SurfaceHolder surfaceHolder;
		surfaceHolder = mMaxSurfaceV.getHolder();
		//surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		surfaceHolder.addCallback(this);
		setDialogPositiveButtonEnable(true);
	}

	@Override
	public void onStopCase() {
		try{
			synchronized (mSurfaceLock) {
		        mThread.setRun(false);
		        mRecordThread.setRun(false);
				if(ar.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
					ar.stop();
				}
				if(ar != null) {
					ar.release();
				}
			}
        } catch (Exception e) {
            e.printStackTrace();
        }
		mTvMicRecordResult.setText(getCaseResult() ? R.string.case_micrecord_status_success_text
				: R.string.case_micrecord_status_fail_text);	
	}

	@Override
	public void reset() {
		super.reset();
	}

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
	}

	private class RecordThread extends Thread{
        private boolean mIsRun = false;
        RecordThread(){

        }

        public void setRun(boolean isRun) {
            this.mIsRun = isRun;
        }

        @Override
        public void run() {
            int bufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannel, mFormat);
            ar = new AudioRecord(mSource, mSampleRate, mChannel, mFormat, bufferSize);
            short[] buffer = new short[bufferSize];
            ar.startRecording();
            int size = 0;
            int pointsCount = (mMaxSurfaceV.getWidth() - mPadding * 2) / mXStep;//点的数量
            while (mIsRun && (size = ar.read(buffer,0, bufferSize)) != -1){
				synchronized (mSurfaceLock) {
					long[] sum = new long[mChannelCount];
					int[] value = new int[mChannelCount];
					for (int i = 0; i < size; i = i + (mFormat * mChannelCount) * mRateX) {
						for (int j = 0; j < mChannelCount; j++) {
							value[j] = buffer[i + j];
						}
						for (int j = 0; j < mChannelCount; j++) {
							sum[j] += Math.abs(value[j]);
						}
					}

					for (int i = 0; i < mChannelCount; i++) {
						while (mPcmBuffers[i].size() > pointsCount) {
							mPcmBuffers[i].poll();
						}
						float result = sum[i] / (size / (mFormat * mChannelCount)) / (20 + 30 * (mChannelCount - 1));// 界面布局兼容不同声道数
						mPcmBuffers[i].add(result);
					}
				}
                try {
                    Thread.sleep(33);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
	
	public void startShowRecord(){
        mRecordThread = new RecordThread();
        mRecordThread.setRun(true);
        mRecordThread.start();

    }
	
	private void doDraw(Canvas canvas){
        canvas.drawARGB(255, 239, 239, 239);


        int contentHeight = canvas.getHeight() - mPadding * 2;
        int contentWidth = canvas.getWidth() - mPadding * 2;
        float baseline[] = new float[mChannelCount];

        //canvas.drawCircle(0, line_off/4, line_off/4, circlePaint);// 上面小圆
        //canvas.drawCircle(0, canvas.getHeight()-line_off/4, line_off/4, circlePaint);// 下面小圆
        //canvas.drawLine(0, 0, 0, canvas.getHeight(), circlePaint);//垂直的线
        //canvas.drawLine(mPadding, mPadding, contentWidth + mPadding, mPadding, paintLine);//最上面的那根线

        for (int i = 0 ; i < baseline.length; i++){
            baseline[i] = contentHeight / baseline.length * (i + 1) ;
            Log.d(TAG, "doDraw: baseline=" + baseline[i]);
            canvas.drawLine(mPadding, baseline[i], contentWidth + mPadding, baseline[i], paintLine);

        }
        canvas.drawLine(mPadding, 0, mPadding, canvas.getHeight(), paintLine);
        for (int i = 0; i < mPcmBuffers.length; i++){
            float preX = mPadding;
            float preY = baseline[i];
            float curX = 0;
            float curY = 0;
            for (int j = 0; j < mPcmBuffers[i].size(); j++){
                //Log.d(TAG, "doDraw: " + j + " " + mPcmBuffers[i].get(j));
                curX = mXStep * j + mPadding;
                curY = (float) mPcmBuffers[i].get(j) * -1 + baseline[i];
                canvas.drawLine(preX, preY, curX , curY, mPaint);
                preX = curX;
                preY = curY;
            }
            canvas.drawLine(curX, curY, curX , baseline[i], mPaint);
        }
    }
	
	private class DrawThread extends Thread {
        private SurfaceHolder mHolder;
        private boolean mIsRun = false;

        public DrawThread(SurfaceHolder holder) {
            super(TAG);
            mHolder = holder;
        }
        @Override
        public void run() {
            while(true) {
                synchronized (mSurfaceLock) {
                    if (!mIsRun) {
                        return;
                    }
                    Canvas canvas = mHolder.lockCanvas();
                    if (canvas != null) {
                        doDraw(canvas);  //这里做真正绘制的事情
                        mHolder.unlockCanvasAndPost(canvas);
                    }
                }
                try {
                    Thread.sleep(SLEEP_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void setRun(boolean isRun) {
            this.mIsRun = isRun;
        }
    }
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		startShowRecord();
        mThread = new DrawThread(holder);
        mThread.setRun(true);
        mThread.start();
        Log.d(TAG, "surfaceCreated: ");
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		Log.d("CaseCamera", "surfaceDestroyed");
		 synchronized (mSurfaceLock) {
	            mThread.setRun(false);
	            mRecordThread.setRun(false);
	        }
	}
}
