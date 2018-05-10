package com.softwinner.dragonbox;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.SystemProperties;
import java.io.IOException;

import com.softwinner.dragonbox.config.ConfigManager;

public class StorageReceiver extends BroadcastReceiver {
    private static final String TAG = "DragonBox-StorageReceiver";

	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		Log.w(TAG,"Receiver broadcast,its action is "+action);
		if((!action.equals("android.intent.action.BOOT_COMPLETED"))&&(!SystemProperties.get("sys.boot_completed").equals("1"))) {
			Log.e(TAG, "Android does not boot completed,donot start Dragonbox!");
			return;
		}
		if (!ConfigManager.startConfigAPK(context, ConfigManager.CONFIG_DRAGON_BOX, false)) {
			if (!ConfigManager.startConfigAPK(context, ConfigManager.CONFIG_DRAGON_SN, false)) {
				if (!ConfigManager.startConfigAPK(context, ConfigManager.CONFIG_DRAGON_AGING, false)) {
					Log.d(TAG,"config file not exist");
				}
			}
		}
		if (ConfigManager.isConfigFileExist(context, "/DragonBox/TimeCamera")) {
		    Intent it = new Intent();
		    it.setComponent(new ComponentName("com.ysten.app.camera", "com.ysten.app.camera.WelcomeActivity"));
		    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		    context.startActivity(it);
		}
		if(ConfigManager.isConfigFileExist(context, "/DragonBox/switch_usb")) {
			Log.d(TAG,"switch the USB0 to device mode");
			SystemProperties.set("sys.service.adbd.enable",  "1");
			try {
				Runtime runtime = Runtime.getRuntime();
				String[] cmd = {"qw", "-c", "cat /sys/devices/soc.0/usbc0.5/usb_device"};
				Process proc = runtime.exec(cmd);
				proc.waitFor();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			Log.d(TAG,"DragonBox Receiver Exit!!!");
		}
	}
}
