package com.softwinner.dragonbox.utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import android.util.Log;

public class SUUtil {
	private static final String TAG = "SUUTILS";
	public static final boolean execute(String... commands) {
		boolean retval = false;
		try {
			if (null != commands && commands.length > 0) {
				Process process = Runtime.getRuntime().exec("qw");

				DataOutputStream os = new DataOutputStream(
						process.getOutputStream());

				for (String currCommand : commands) {
					os.writeBytes(currCommand + "\n");
					//
				}
				os.writeBytes("exit\n");
				os.flush();
				//os.writeBytes("exit\n");

				BufferedReader reader = new BufferedReader(
						new InputStreamReader(process.getInputStream()));
				int read;
				char[] buffer = new char[4096];
				StringBuffer output = new StringBuffer();
				while ((read = reader.read(buffer)) > 0) {
					output.append(buffer, 0, read);
				}
				reader.close();

				try {
					int suProcessRetval = process.waitFor();
					if (255 != suProcessRetval) {
						retval = true;
					} else {
						retval = false;
					}
					System.out.println("BBBB: " + output.toString());
				} catch (Exception ex) {
					// Log.e("Error executing root action", ex);
				}
			}
		} catch (IOException ex) {
			Log.w("ROOT", "Can't get root access", ex);
		} catch (SecurityException ex) {
			Log.w("ROOT", "Can't get root access", ex);
		} catch (Exception ex) {
			Log.w("ROOT", "Error executing internal operation", ex);
		}

		return retval;
	}

	public static boolean haveRoot() {
		int ret = execRootCmdSilent("echo test"); // 通过执行测试命令来检测
		if (ret != -1) {
			Log.i(TAG, "have root!");
		} else {
			Log.i(TAG, "not root!");
		}
		Log.i(TAG, "ret val=" + ret);
		return ret != -1;
	}
	
	// 执行命令但不关注结果输出 
    public static int execRootCmdSilent(String cmd) { 
        int result = -1; 
        DataOutputStream dos = null; 
         
        try { 
            Process p = Runtime.getRuntime().exec("qw"); 
            dos = new DataOutputStream(p.getOutputStream()); 
             
            Log.i(TAG, cmd); 
            dos.writeBytes(cmd + "\n"); 
            dos.flush(); 
            dos.writeBytes("exit\n"); 
            dos.flush(); 
            p.waitFor(); 
            result = p.exitValue(); 
        } catch (Exception e) { 
            e.printStackTrace(); 
        } finally { 
            if (dos != null) { 
                try { 
                    dos.close(); 
                } catch (IOException e) { 
                    e.printStackTrace(); 
                } 
            } 
        } 
        return result; 
    } 
}
