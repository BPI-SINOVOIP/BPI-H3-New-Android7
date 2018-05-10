package com.softwinner.agingdragonbox.engine.testcase;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.os.storage.StorageManager;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ScrollView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.softwinner.agingdragonbox.R;
import com.softwinner.agingdragonbox.ThreadVar;
import com.softwinner.agingdragonbox.engine.BaseCase;
import com.softwinner.agingdragonbox.xml.Node;

public class CaseMemory extends BaseCase {

    private static final String      TAG                   = CaseMemory.class.getSimpleName();
    public static final String       PASSABLE_MIN_CAP      = "minCap";
    private StorageManager           mStorageManager;
    private ArrayAdapter<String>     mAdapter;
    private int                      mMinCapInMB           = -1;
    // 用来返回结果的，直接加上一个变量
    private long                     mAvailSize            = 0;
    private static final String      CMD_PATH = "/system/bin/memtester";
       // = "/data/data/com.softwinner.agingdragonbox/cache/memtester";
    static final boolean             DEBUG                 = false;
    protected ViewGroup              mStage;
    private boolean                  mIsRunning            = false;
    private boolean                  mResult               = false;
    private ViewGroup                myViewGroup;
    private int                      memSize               = 128;
    private int                      repeat;
    private SharedPreferences        mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    private static final int         HANDLER_UPDATE_OUTPUT = 0;
    private static final int         HANDLER_FINISHED      = 1;
    private static final int         HANDLER_PROGRESS      = 2;
    private ProgressBar              mProgressBar;
    private TextView                 mResultTextView;
    private TextView                 outputWindow;
    private ScrollView               svMeminfo;   
    private boolean                  threadExitDdr;
    private boolean                  threadExit;
    StringBuilder                    sb                    = new StringBuilder();
    String                           cmd;
    private boolean                  isShow;
    private static final int         MAX_INFO_LINE         = 500;
    private int                      currentLine           = 0;

    private static int               ledTime               = 500;
    Thread                           browseThread          = null;
    ThreadVar                        threadVar            = new ThreadVar();

    private Handler                  myHandler             = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLER_UPDATE_OUTPUT:
                    refeshOutputWindow();
                    break;
                default:
                    break;
            }
        }
    };

    public void initCMDFile() {

        File cmdFile = new File(CMD_PATH);

        if (!cmdFile.exists()) {
            Log.d(TAG, " create memtester file on cache.");
            // copy memtest from assets of app to cache directory.
            InputStream is = null;
            FileOutputStream fos = null;
            try {
                is = mContext.getAssets().open("memtester");
                fos = new FileOutputStream(cmdFile);

                byte[] buff = new byte[2048];
                int length = 0;
                while ((length = is.read(buff)) != -1) {
                    fos.write(buff, 0, length);
                }
                fos.flush();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        // change memtest in cache permission

        Process p = null;
        DataOutputStream dos = null;

        try {
            // p = Runtime.getRuntime().exec("su");
            //
            // dos = new DataOutputStream(p.getOutputStream());
            //
            // dos.writeBytes("chmod 777 " + CMD_PATH);
            // dos.flush();

            Runtime.getRuntime().exec("chmod 6755 " + CMD_PATH);
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
    }

    private void refeshOutputWindow() {
        outputWindow.setText(sb.toString());
        svMeminfo.fullScroll(ScrollView.FOCUS_DOWN);
        if (isShow) {
            myHandler.sendEmptyMessageDelayed(HANDLER_UPDATE_OUTPUT, 1000);
        }
    }

    class TestRunnable implements Runnable {

        @Override
        public void run() {
            if (memSize <= 0) {
                return;
            }
            initCMDFile();
            sb.setLength(0);
            myHandler.sendEmptyMessage(HANDLER_UPDATE_OUTPUT);
            BufferedWriter dos = null;
            BufferedReader dis = null;

            Process p = null;

            try {
                p = Runtime.getRuntime().exec("qw");

                cmd = String.format("%s %dm %d\n", CMD_PATH, 128, repeat);

                dos = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
                dis = new BufferedReader(new InputStreamReader(p.getInputStream()));

                Log.d(TAG, "ddr test start........");

                // sb.append(dis.readLine());
                Log.v("maizirong", "=========run cmd=========" + cmd);
                dos.write(cmd);
                dos.flush();
                Log.v("maizirong", cmd);
                sb.append("start memtest\n");
                String line = null;
                Log.d(TAG, "ddr test start11........");

                // Record start time of testing to log file.
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                String timeStr = sdf.format(new Date(System.currentTimeMillis()));
                String spliteLine = "-----------------------------------------------------\n";
                String testInfo = timeStr + "    " + cmd + "\n";

                while ((line = dis.readLine()) != null) {
                    Log.d(TAG, "ddr test start22........");
                    if (currentLine >= MAX_INFO_LINE) {
                        currentLine = 0;
                        sb.setLength(0);

                    }

                    if (line.length() > 15 && "Stuck Address".equals(line.substring(2, 15))) {
                        Log.d(TAG, "ddr test start33........");
                        if (line.endsWith("ok")) {
                            Log.d(TAG, "ddr test ok........");
                            sb.append("  Stuck Address        : ok");

                        } else {
                            threadVar.threadExitDdr = false;
                            sb.append("  Stuck Address        : failed");
                            Log.d(TAG, "ddr test ........Stuck Address        : failed");
                        }
                    } else if (line.length() > 14 && "Random Value".equals(line.substring(2, 14))) {
                        Log.d(TAG, "ddr test start44........");
                        if (line.endsWith("ok")) {

                            sb.append("  Random Value        : ok");
                        } else {
                            threadVar.threadExitDdr = false;
                            sb.append("  Random Value        : failed");
                            Log.d(TAG, "ddr test ........Random Value        : failed");
                        }
                    } else if (line.length() > 14 && "Checkerboard".equals(line.substring(2, 14))) {
                        Log.d(TAG, "ddr test start55........");
                        if (line.endsWith("ok")) {
                            sb.append("  Checkerboard        : ok");
                        } else {
                            threadVar.threadExitDdr = false;
                            sb.append("  Checkerboard        : failed");
                            Log.d(TAG, "ddr test ........Checkerboard        : failed");
                        }
                    } else if (line.length() > 12 && "Solid Bits".equals(line.substring(2, 12))) {
                        Log.d(TAG, "ddr test start66........");
                        if (line.endsWith("ok")) {
                            sb.append("  Solid Bits        : ok");
                        } else {
                            threadVar.threadExitDdr = false;
                            sb.append("  Solid Bits        : failed");
                            Log.d(TAG, "ddr test ........Solid Bits        : failed");
                        }
                    } else if (line.length() > 18
                            && "Block Sequential".equals(line.substring(2, 18))) {
                        Log.d(TAG, "ddr test start77........");
                        if (line.endsWith("ok")) {
                            sb.append("  Block Sequential        : ok");
                        } else {
                            threadVar.threadExitDdr = false;
                            sb.append("  Block Sequential        : failed");
                            Log.d(TAG, "ddr test ........Solid Bits        : failed");
                        }

                    } else if (line.length() > 12 && "Bit Spread".equals(line.substring(2, 12))) {
                        Log.d(TAG, "ddr test start88........");
                        if (line.endsWith("ok")) {
                            sb.append("  Bit Spread        : ok");
                        } else {
                            threadVar.threadExitDdr = false;
                            sb.append("  Bit Spread        : failed");
                            Log.d(TAG, "ddr test ........Bit Spread        : failed");
                        }

                    } else if (line.length() > 10 && "Bit Flip".equals(line.substring(2, 10))) {
                        Log.d(TAG, "ddr test start99........");
                        if (line.endsWith("ok")) {
                            sb.append("  Bit Flip        : ok");
                        } else {
                            threadVar.threadExitDdr = false;
                            sb.append("  Bit Flip        : failed");
                            Log.d(TAG, "ddr test ........Bit Flip        : failed");
                        }

                    } else if (line.length() > 14 && "Walking Ones".equals(line.substring(2, 14))) {
                        Log.d(TAG, "ddr test start100........");
                        if (line.endsWith("ok")) {
                            sb.append("  Walking Ones        : ok");
                        } else {
                            threadVar.threadExitDdr = false;
                            sb.append("  Walking Ones        : failed");
                            Log.d(TAG, "ddr test ........Walking Ones        : failed");
                        }
                    } else if (line.length() > 16
                            && "Walking Zeroes".equals(line.substring(2, 16))) {
                        Log.d(TAG, "ddr test start110........");
                        if (line.endsWith("ok")) {
                            sb.append("  Walking Zeroes        : ok");
                        } else {
                            threadVar.threadExitDdr = false;
                            sb.append("  Walking Zeroes        : failed");
                            Log.d(TAG, "ddr test ........Walking Zeroes        : failed");
                        }
                    } else {
                        sb.append(line);
                    }

                    sb.append("\n");
                    if (line.startsWith("Done")) {
                        break;
                    }
                    currentLine++;
                    Log.e(TAG, "current = " + currentLine);
                }
                Log.i(TAG, "ddr test end.");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (dos != null) {
                    try {

                        dos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (dis != null) {
                    try {
                        dis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (p != null) {
                    p.destroy();
                }

                // memSize = 0;
                repeat = 1;
                cmd = null;
            }
        }

    };

    @Override
    protected void onInitialize(Node attr) {
        setView(R.layout.memory_test);
        setName(R.string.case_memory_name);
        outputWindow = (TextView) getView().findViewById(R.id.output_window);
        svMeminfo = (ScrollView) getView().findViewById(R.id.sv_meminfo);
    }

    @Override
    protected boolean onCaseStarted() {
        Log.i(TAG, "start MemoryTest");
        mIsRunning = true;
        mResult = false;
        isShow = true;
        new Thread(new TestRunnable()).start();
        startCtrlLedThread();
        return false;
    }

    @Override
    protected void onCaseFinished() {
        // mContext.unregisterReceiver(mStorageReceiver);
    }

    @Override
    protected void onRelease() {

    }

    private void chanceLedStatus(int status) {
        if (status > 0) {
            com.softwinner.Gpio.setNormalLedOn(false);
            com.softwinner.Gpio.setStandbyLedOn(true);
        } else {
            com.softwinner.Gpio.setNormalLedOn(true);
            com.softwinner.Gpio.setStandbyLedOn(false);
        } 
    }

    private void startCtrlLedThread() {
        browseThread = new Thread() {
            public void run() {
                try {
                    threadExit = threadVar.threadExit;
                    threadExitDdr = threadVar.threadExitDdr;
                    while (threadExitDdr && threadExit) {
                        if (ledTime <= 0) {
                            ledTime = 500;
                        }
                        chanceLedStatus(0);
                        Thread.sleep(ledTime);
                        chanceLedStatus(1);
                        Thread.sleep(ledTime);
                        threadExit = threadVar.threadExit;
                        threadExitDdr = threadVar.threadExitDdr;
                    }
                    chanceLedStatus(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        };
        browseThread.start();
    }
}
