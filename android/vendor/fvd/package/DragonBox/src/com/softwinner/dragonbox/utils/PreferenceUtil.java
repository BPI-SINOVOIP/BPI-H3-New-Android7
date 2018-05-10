package com.softwinner.dragonbox.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

public class PreferenceUtil {
	public static final String FACTORY_SUCCESS = "1";
	public static final String FACTORY_FAIL = "0";
	
	private static final String PREF_FACTORY_FILE = "factory_pass";
	private static final String PREF_FACTORY_RESULT_KEY = "pass";
	
	public static void writeFactory(Context context, boolean success) {
		SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_FACTORY_FILE, Context.MODE_WORLD_READABLE);
		Editor editor = sharedPreferences.edit();
		editor.putString(PREF_FACTORY_RESULT_KEY, success ? FACTORY_SUCCESS : FACTORY_FAIL);
		editor.commit();
		Log.i("Main", "==============================" + sharedPreferences.getString("pass", "0"));
	}
	
	public static String readFactory(Context context){
		SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_FACTORY_FILE, Context.MODE_WORLD_READABLE);
		return sharedPreferences.getString(PREF_FACTORY_RESULT_KEY, FACTORY_FAIL);
	}
	
}
