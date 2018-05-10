/*************************************************************************
    > File Name: SysRecoveryReceiver.java
    > Author: chenjuncong
    > Mail: chenjuncong@allwinnertech.com
    > Created Time: 2017年09月12日 星期二 15时30分06秒
 ************************************************************************/

package com.android.server;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RecoverySystem;
import android.util.Log;
import android.util.Slog;
import java.io.IOException;
import java.lang.InterruptedException;
import android.app.ActivityManager;

/*
 *This BroadcastReceiver receives android.media.MASTER_MUTE_CHANGED_ACTION,
 when this broadcast has sent, Apks like Youtube will start which will effect the
 performance of the system,so after Youtube started, try to kill it.
 * */

public class MuteApksReceiver extends BroadcastReceiver {
    private static final String TAG = "MuteApksReceiver";
    private static final String YouTubePkg = "com.google.android.youtube";
    private boolean shouldKillApks = false;
    private static Thread thr = null;
    @Override
    public void onReceive(final Context context, final Intent intent) {
        if(CheckAPKHelper.isAppRunningOnForeGround(context, YouTubePkg)){
            //should not kill apks
            Log.d(TAG, "should not kill Youtube");
            return;
        }
        else{
            //should kill apks
            Log.d(TAG, "should kill Youtube");
            shouldKillApks = true;
        }
        if(shouldKillApks){
            if(thr == null){
                Thread thr = new Thread() {
                    int count = 10;
                    @Override
                    public void run() {
                        synchronized(this){
                            while(count-- > 0){
                                if(CheckAPKHelper.isAppRunning(context, YouTubePkg)){
                                    Log.d(TAG, "stop Youtube for mute key in broadcast receiver");
                                    ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                                    am.forceStopPackage(YouTubePkg);
                                    return;
                                }
                                else{
                                    Log.d(TAG, "wait stop Youtube for mute key in broadcast receiver");
                                }
                                try{
                                    Thread.sleep(200);
                                }
                                catch(InterruptedException e){
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                };
                thr.start();
            }
        }
    }
}
