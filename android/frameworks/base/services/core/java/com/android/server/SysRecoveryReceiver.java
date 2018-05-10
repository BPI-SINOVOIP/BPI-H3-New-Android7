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


public class SysRecoveryReceiver extends BroadcastReceiver {
    private static final String TAG = "SysRecovery";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Thread thr = new Thread("Reboot") {
            @Override
            public void run() {
                try {
                    RecoverySystem.rebootSysRecovery(context);
                } catch (IOException e) {
                    Slog.e(TAG, "Can't perform sysrecovery", e);
                } catch (SecurityException e) {
                    Slog.e(TAG, "Can't perform sysrecovery", e);
                }
            }
        };
        thr.start();
    }
}
