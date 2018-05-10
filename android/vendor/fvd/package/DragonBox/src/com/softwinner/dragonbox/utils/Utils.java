package com.softwinner.dragonbox.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

public class Utils {

	public static boolean checkHasFile(Context context, String fileName) {
		String filePath = getFileAbsolutePath(context, fileName);
		return filePath != null && !"".equals(filePath);
	}

	public static String getFileAbsolutePath(Context context, String fileName) {
		StorageManager storageManager = (StorageManager) context
				.getSystemService(Context.STORAGE_SERVICE);
		StorageVolume[] volumes = storageManager.getVolumeList();
		for (StorageVolume volume : volumes) {
			String state = storageManager.getVolumeState(volume.getPath());
			if (Environment.MEDIA_MOUNTED.equals(state)) {
				String path = volume.getPath() + fileName;
				File file = new File(path);
				if (file.exists() && file.isFile()) {
					return path;
				}
			}
		}
		return null;
	}

	public String formatSize(long size) {
		String suffix = null;
		float fSize = 0;

		if (size >= 1024) {
			suffix = "KB";
			fSize = size / 1024;
			if (fSize >= 1024) {
				suffix = "MB";
				fSize /= 1024;
			}
			if (fSize >= 1024) {
				suffix = "GB";
				fSize /= 1024;
			}
		} else {
			fSize = size;
		}
		java.text.DecimalFormat df = new java.text.DecimalFormat("#0.00");
		StringBuilder resultBuffer = new StringBuilder(df.format(fSize));
		if (suffix != null)
			resultBuffer.append(suffix);
		return resultBuffer.toString();
	}

	/*
	 * args[0] : shell 命令 如"ls" 或"ls -1"; args[1] : 命令执行路径 如"/" ;
	 */
	public static String execute(String[] cmmand, String directory) throws IOException {
		String result = "";
		try {
			ProcessBuilder builder = new ProcessBuilder(cmmand);

			if (directory != null)
				builder.directory(new File(directory));
			builder.redirectErrorStream(true);
			Process process = builder.start();

			// 得到命令执行后的结果
			InputStream is = process.getInputStream();
			byte[] buffer = new byte[1024];
			while (is.read(buffer) != -1) {
				result = result + new String(buffer);
			}
			is.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
}
