package com.android.settings.pipSettings;

import android.content.Context;
import android.graphics.Rect;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;

import static android.app.ActivityManager.StackId.PINNED_STACK_ID;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.ActivityManager.StackInfo;
import android.os.SystemProperties;

public class PipSetUtils {

    private static final String TAG = PipSetUtils.class.getSimpleName();
    private static PipSetUtils mPipSetUtils=null;
    private Context mContext=null;
    private IActivityManager mActivityManager=null;

    private static final String PIP_SETTINGS_BOUNDS_LEFT = "persist.sys.pip.bounds.left";
    private static final String PIP_SETTINGS_BOUNDS_RIGHT = "persist.sys.pip.bounds.right";
    private static final String PIP_SETTINGS_BOUNDS_TOP = "persist.sys.pip.bounds.top";
    private static final String PIP_SETTINGS_BOUNDS_BOTTOM = "persist.sys.pip.bounds.bottom";
    public static final String PIP_SETTINGS_BOUNDS_POS = "persist.sys.pip.bounds.pos";

    private static final int PIP_LEFT_TOP_POS_FLAG = 0;
    private static final int PIP_RIGHT_TOP_POS_FLAG = 1;
    private static final int PIP_MIDDLE_CENTER_POS_FLAG = 2;
    private static final int PIP_LEFT_BOTTOM_POS_FLAG = 3;
    private static final int PIP_RIGHT_BOTTOM_POS_FLAG = 4;

    private static int nPIPBoundsWidth = 0; //画中画中的屏幕宽
    private static int nPIPBoundsHeight = 0;//画中画中的屏幕高
    private static  int nScreenWidth = 0;  //屏幕大小
    private static  int nScreenHeight = 0;

    private static  int nPIPBoundsLeft = 0; //PIP设置的各边参数
    private static int nPIPBoundsRight = 0;
    private static  int nPIPBoundsTop = 0;
    private static  int nPIPBoundsBottom = 0;


    public static final int DEFAULT_PIP_BOUNDS_LEFT = 1328; //默认PIP位置在右上角
    public static final int DEFAULT_PIP_BOUNDS_RIGHT = 1808;
    public static final int DEFAULT_PIP_BOUNDS_TOP = 54;
    public static final int DEFAULT_PIP_BOUNDS_BOTTOM = 324;
    public static final int DEFAULT_PIP_BOUNDS_WIDTH = DEFAULT_PIP_BOUNDS_RIGHT - DEFAULT_PIP_BOUNDS_LEFT;
    public static final int DEFAULT_PIP_BOUNDS_HEIGHT = DEFAULT_PIP_BOUNDS_BOTTOM - DEFAULT_PIP_BOUNDS_TOP;

    private PipSetUtils()
    {

    }

    public static PipSetUtils newInstance( )
    {
        if (null == mPipSetUtils)
        {
            synchronized (PipSetUtils.class)
            {
                if (null == mPipSetUtils)
                {
                    mPipSetUtils = new PipSetUtils();
                }
            }
        }
        return mPipSetUtils;
    }


    public int GetScreenWidth()
    {
        return this.nScreenWidth;
    }

    public int GetScreenHeight()
    {
        return this.nScreenHeight;
    }

    public void SetScreenWidth(final int nScreenWidth)
    {
         this.nScreenWidth = nScreenWidth;
    }

    public void SetScreenHeight(final int nScreenHeight)
    {
         this.nScreenHeight = nScreenHeight;
    }

    public int GetPipBoundsWidth()
    {
        return this.nPIPBoundsWidth;
    }

    public void SetPipBoundsWidth(final int nPIPBoundsWidth)
    {
        this.nPIPBoundsWidth = nPIPBoundsWidth;
    }

    public int GetPipBoundsHeight()
    {
        return this.nPIPBoundsHeight;
    }

    public void SetPipBoundsHeight(final int nPIPBoundsHeight)
    {
         this.nPIPBoundsHeight = nPIPBoundsHeight;
    }


    public void initPipData()
    {

            Log.d(TAG,"nScreenWidth:"+nScreenWidth+",nScreenHeight:"+nScreenHeight);
            Rect mPipRect = getPipParameters();
              //PIP大小设置，默认焦点位置 画中画中宽和高
            nPIPBoundsLeft= mPipRect.left;
            nPIPBoundsTop = mPipRect.top;
            nPIPBoundsRight = mPipRect.right;
            nPIPBoundsBottom = mPipRect.bottom;

             nPIPBoundsWidth = nPIPBoundsRight - nPIPBoundsLeft; //宽高
             nPIPBoundsHeight = nPIPBoundsBottom - nPIPBoundsTop;

             Log.d(TAG,"=YTR=INIT=nPIPBoundsLeft:"+nPIPBoundsLeft+",nPIPBoundsTop:"+nPIPBoundsTop+",nPIPBoundsRight:"+nPIPBoundsRight+",nPIPBoundsBottom:"+nPIPBoundsBottom);
             Log.d(TAG,"===YTR==INIT==nPIPBoundsWidth:"+nPIPBoundsWidth+"===nPIPBoundsHeight:"+nPIPBoundsHeight);


    }


    public void savePipParameters(final int nIndex)
    {
        SystemProperties.set(PIP_SETTINGS_BOUNDS_LEFT,String.valueOf(nPIPBoundsLeft));
        SystemProperties.set(PIP_SETTINGS_BOUNDS_RIGHT,String.valueOf(nPIPBoundsRight));
        SystemProperties.set(PIP_SETTINGS_BOUNDS_TOP,String.valueOf(nPIPBoundsTop));
        SystemProperties.set(PIP_SETTINGS_BOUNDS_BOTTOM,String.valueOf(nPIPBoundsBottom));
        SystemProperties.set(PIP_SETTINGS_BOUNDS_POS,nIndex+"");
    }



    public Rect getPipParameters()
    {
        Rect mPipPosRect= new Rect();
        mActivityManager =  ActivityManagerNative.getDefault();
        try
        {
            StackInfo mStackInfo = mActivityManager.getStackInfo(PINNED_STACK_ID);
            Log.d(TAG,"=YTR=111=mStackInfo:"+mStackInfo);
             if (null == mStackInfo)
             {
                 mPipPosRect.left = SystemProperties.getInt(PIP_SETTINGS_BOUNDS_LEFT, DEFAULT_PIP_BOUNDS_LEFT);
                 mPipPosRect.right=SystemProperties.getInt(PIP_SETTINGS_BOUNDS_RIGHT, DEFAULT_PIP_BOUNDS_LEFT);
                 mPipPosRect.top = SystemProperties.getInt(PIP_SETTINGS_BOUNDS_TOP, DEFAULT_PIP_BOUNDS_LEFT);
                 mPipPosRect.bottom = SystemProperties.getInt(PIP_SETTINGS_BOUNDS_BOTTOM, DEFAULT_PIP_BOUNDS_LEFT);
             }
             else
             {
                     mPipPosRect = mStackInfo.bounds;
             }
        }catch (RemoteException ex)
        {
            ex.printStackTrace();
        }



        return mPipPosRect;
    }


    public void setPipBounds(final int nIndex)
    {
        setPipShowByIndex(nIndex);

        try {
            StackInfo stackInfo = mActivityManager.getStackInfo(PINNED_STACK_ID);
            if (stackInfo == null) {  //暂时还没有进入PIP模式
                Log.w(TAG, "Cannot find pinned stack");
            }else{  //已经在PIP模式了，就直接设置进去
                try{
                    mActivityManager.resizeStack(PINNED_STACK_ID, new Rect(nPIPBoundsLeft,nPIPBoundsTop,nPIPBoundsRight,nPIPBoundsBottom),true, true, true, -1);//这个切换动作参考ActivityManagerNative
                }catch(RemoteException e){
                    Log.e(TAG, "setDisplayPIPPosition resizeStack failed", e);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "getStackInfo failed", e);
        }

   }


    private void setPipShowByIndex(final int nIndex)
    {
                Log.d(TAG,"nIndex:"+nIndex);
                switch(nIndex){
                    case PIP_LEFT_TOP_POS_FLAG: //左上角
                        nPIPBoundsLeft = nScreenWidth - DEFAULT_PIP_BOUNDS_RIGHT;
                        nPIPBoundsTop = DEFAULT_PIP_BOUNDS_TOP;
                        nPIPBoundsRight = nPIPBoundsLeft + nPIPBoundsWidth;
                        nPIPBoundsBottom = DEFAULT_PIP_BOUNDS_TOP + nPIPBoundsHeight;
                        break;
                    case PIP_RIGHT_TOP_POS_FLAG://右上角
                        nPIPBoundsTop = DEFAULT_PIP_BOUNDS_TOP;
                        nPIPBoundsRight = DEFAULT_PIP_BOUNDS_RIGHT;
                        nPIPBoundsLeft = DEFAULT_PIP_BOUNDS_RIGHT - nPIPBoundsWidth;
                        nPIPBoundsBottom = DEFAULT_PIP_BOUNDS_TOP + nPIPBoundsHeight;
                        break;
                    case PIP_MIDDLE_CENTER_POS_FLAG: //中间
                        nPIPBoundsTop = nScreenHeight/2-nPIPBoundsHeight/2;
                        nPIPBoundsLeft = nScreenWidth/2-nPIPBoundsWidth/2;
                        nPIPBoundsBottom = nPIPBoundsTop+nPIPBoundsHeight;
                        nPIPBoundsRight = nPIPBoundsLeft+nPIPBoundsWidth;
                        break;
                    case PIP_LEFT_BOTTOM_POS_FLAG: //左下角
                        nPIPBoundsLeft = nScreenWidth - DEFAULT_PIP_BOUNDS_RIGHT;
                        nPIPBoundsBottom = nScreenHeight - DEFAULT_PIP_BOUNDS_TOP;
                        nPIPBoundsRight = nPIPBoundsLeft + nPIPBoundsWidth;
                        nPIPBoundsTop = nPIPBoundsBottom - nPIPBoundsHeight;
                        break;
                    case PIP_RIGHT_BOTTOM_POS_FLAG: //右下角
                        nPIPBoundsRight = DEFAULT_PIP_BOUNDS_RIGHT;
                        nPIPBoundsBottom = nScreenHeight - DEFAULT_PIP_BOUNDS_TOP;
                        nPIPBoundsLeft = DEFAULT_PIP_BOUNDS_RIGHT - nPIPBoundsWidth;
                        nPIPBoundsTop = nPIPBoundsBottom - nPIPBoundsHeight;
                        break;
                      default:
                          break;

                }

                Log.d(TAG,"nPIPBoundsLeft:"+nPIPBoundsLeft+",nPIPBoundsTop:"+nPIPBoundsTop+",nPIPBoundsRight:"+nPIPBoundsRight+",nPIPBoundsBottom:"+nPIPBoundsBottom);

        }

}
