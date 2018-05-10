package com.softwinner.dragonbox;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.Thread;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import android.content.SharedPreferences; 
import android.content.SharedPreferences.Editor;
import android.app.AlertDialog.Builder;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.softwinner.dragonbox.config.ConfigManager;
import com.softwinner.dragonbox.testcase.CaseEthernet;
import com.softwinner.dragonbox.testcase.CasePerformance;
import com.softwinner.dragonbox.testcase.CaseWifi;
import com.softwinner.dragonbox.testcase.CaseYSTWan;
import com.softwinner.dragonbox.testcase.CaseYSTWifi;
import com.softwinner.dragonbox.testcase.IBaseCase;
import com.softwinner.dragonbox.utils.PreferenceUtil;

public class DragonBoxMain extends Activity implements IBaseCase.onResultChangeListener{

	// public ViewGroup fullContainer;
	public LinearLayout caseContainner;
	private List<IBaseCase> mAllCases = new ArrayList<IBaseCase>();
	private List<IBaseCase> mAllAutoCases = new ArrayList<IBaseCase>();
	private List<IBaseCase> mAllManualCases = new ArrayList<IBaseCase>();

	private int backPressTimes = 0;
	private static final int BACK_PRESS_MAX_TIMES = 3;
	private int prePressNum = -1;
	private static final int WHAT_HANDLE_RESTART = 0;
	private static final int WHAT_HANDLE_KEY = 1;
	Handler mHandler = new Handler(){
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case WHAT_HANDLE_RESTART:
				restartAllTest();
				break;
			case WHAT_HANDLE_KEY:
				int positionNum = msg.arg1;
				if (positionNum > mAllCases.size() && positionNum >= 0){
					Toast.makeText(DragonBoxMain.this, R.string.case_main_alert_item_not_support, Toast.LENGTH_SHORT).show();
				} else {
					mAllCases.get(positionNum - 1).mActionView.performClick();
				}
				prePressNum = -1;
				break;
			}
		};
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
        new Thread(){
            public void run(){
                //add flag file to tell others 'I'm running'
                try{
                    String filePath = getCacheDir().getPath() + File.separator + "state";
                    File runningStat = new File(filePath);
                    if(!runningStat.exists()){
                        runningStat.createNewFile();
                    }
                    FileOutputStream fileOutputStream = new FileOutputStream(runningStat,false);
                    fileOutputStream.write("0".getBytes());
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    Thread.sleep(2000);
                    fileOutputStream = new FileOutputStream(runningStat,false);
                    fileOutputStream.write("1".getBytes());
                    fileOutputStream.flush();
                    fileOutputStream.close();
                }catch(Exception e){
                    e.printStackTrace();
                }
            };
        }.start();
		caseContainner = (LinearLayout) findViewById(R.id.main_case_container);
		try {
			List<IBaseCase> cases = ConfigManager.getInstence(this)
					.parseConfig();
			mAllCases = cases;
			for (int i = 0; i < cases.size(); i++) {
				IBaseCase curCase = cases.get(i);
				curCase.addRusultChangeListener(this);
				TextView numTV = (TextView) curCase.mMinView.findViewById(R.id.case_min_num);
				if (numTV != null) {
					numTV.setText((i + 1) + "");
				}
				caseContainner.addView(curCase.mMinView);
				if (curCase.getType() == IBaseCase.TYPE_MODE_AUTO) {
					//curCase.startCaseforAuto();
					mAllAutoCases.add(curCase);
				} else if (curCase.getType() == IBaseCase.TYPE_MODE_MANUAL) {
					mAllManualCases.add(curCase);
				}
			}
		} catch (Exception e) {
			Toast.makeText(this, "ERROR,Please check config files", Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}

		mHandler.sendEmptyMessageDelayed(WHAT_HANDLE_RESTART, 100);

	}

	private void generateLinkCases(List<IBaseCase> cases) {
		if (cases == null || cases.size() == 0) {
			return;
		}
		int size = cases.size();
		for (int i = 0; i < size - 1; i++) {
			cases.get(i).setNextCase(cases.get(i + 1));
		}
		cases.get(0).mActionView.performClick();
	}

	interface onFullWindowAct {
		void onFullWindowShow();
		void onFullWindowHide();
	}

	public void cancelAllTest(){
		for (IBaseCase baseCase : mAllCases) {
			baseCase.stopCase();
			baseCase.cancel();
		}
	}
	
	public void restartAllTest() {
		boolean haveEthCase = false;
		IBaseCase caseYSTWifi = null;
		IBaseCase caseYSTWan = null;
		for (IBaseCase baseCase : mAllCases) {
			if (baseCase instanceof CaseEthernet || baseCase instanceof CaseYSTWan) {
				haveEthCase = true;
			}
			if (baseCase instanceof CaseYSTWifi) {
				caseYSTWifi = baseCase;
			}
			if (baseCase instanceof CaseYSTWan) {
				caseYSTWan = baseCase;
			}
			baseCase.stopCase();
			baseCase.reset();
		}
		
		for (IBaseCase baseCase : mAllAutoCases) {
			
			if (baseCase instanceof CaseWifi && haveEthCase) {
				baseCase.startCaseDelay(6000);
			}else if (baseCase instanceof CaseYSTWifi) {
				baseCase.startCastAfterCase(caseYSTWan);
			}else if (baseCase instanceof CaseYSTWan) {
				baseCase.startCastAfterCase(caseYSTWifi);
			} else {
				baseCase.startCase();
			}
		}
		generateLinkCases(mAllManualCases);
	}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                backPressTimes++;
                if (backPressTimes >= BACK_PRESS_MAX_TIMES) {
                    try{
                        String filePath = getCacheDir().getPath() + File.separator + "state";
                        File runningStat = new File(filePath);
                        if(!runningStat.exists()){
                            runningStat.createNewFile();
                        }
                        FileOutputStream fileOutputStream = new FileOutputStream(runningStat,false);
                        fileOutputStream.write("0".getBytes());
                        fileOutputStream.flush();
                        fileOutputStream.close();
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                    finish();
                    Runtime.getRuntime().exit(0);
                } else {
                    Toast.makeText(this, "再按两次返回键退出应用", Toast.LENGTH_SHORT).show();
                }
                return true;
            case KeyEvent.KEYCODE_0:
            case KeyEvent.KEYCODE_1:
            case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_3:
		case KeyEvent.KEYCODE_4:
		case KeyEvent.KEYCODE_5:
		case KeyEvent.KEYCODE_6:
		case KeyEvent.KEYCODE_7:
		case KeyEvent.KEYCODE_8:
		case KeyEvent.KEYCODE_9:
			mHandler.removeMessages(WHAT_HANDLE_KEY);
			Message msg = mHandler.obtainMessage();
			msg.what = WHAT_HANDLE_KEY;
			if (prePressNum == -1) {
				prePressNum = keyCode - KeyEvent.KEYCODE_0;
				msg.arg1 = prePressNum;
				if (keyCode == KeyEvent.KEYCODE_0 || keyCode == KeyEvent.KEYCODE_1) {
					mHandler.sendMessageDelayed(msg, 1000);					
				} else {
					mHandler.sendMessage(msg);
				}
			} else {
				msg.arg1 = prePressNum * 10 + (keyCode - KeyEvent.KEYCODE_0);
				mHandler.sendMessage(msg);
			}
			backPressTimes = 0;
			return true;
        case KeyEvent.KEYCODE_MENU:
            showMenuDialog();
            //return true; 暂时不return true,还未查明onCreateOptionsMenu为什么没有执行.
		default:
			break;
		}
        
		backPressTimes = 0;
		return false;
	}
    
    public void showMenuDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请选择:");
        String[] operations = {"重新测试","启动DragonSN","启动DragonAging"};
        builder.setSingleChoiceItems(operations,1,new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog,int which){
                dialog.dismiss();
                switch (which) {
                    case 0:
                        restartAllTest();
                        break;
                    case 1:
                        ConfigManager.startConfigAPK(DragonBoxMain.this, ConfigManager.CONFIG_DRAGON_SN, true);
                        break;
                    case 2:
                        ConfigManager.startConfigAPK(DragonBoxMain.this, ConfigManager.CONFIG_DRAGON_AGING, true);
                        break;
                }
            }
        });
        builder.show();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
		case R.id.menu_retest:
			restartAllTest();
			break;
		case R.id.menu_go_dragonsn:
			ConfigManager.startConfigAPK(this, ConfigManager.CONFIG_DRAGON_SN, true);
			break;
		case R.id.menu_go_dragonaging:
			ConfigManager.startConfigAPK(this, ConfigManager.CONFIG_DRAGON_AGING, true);
			break;
		}
		return true;
	}

	@Override
	public void onResultChange(IBaseCase baseCase, boolean caseResult) {
		for (IBaseCase cases : mAllCases) {
			if (!cases.getCaseResult()){
				if (PreferenceUtil.readFactory(this) != PreferenceUtil.FACTORY_FAIL) {
					PreferenceUtil.writeFactory(this,false);					
				}
				return;
			}
		}
		PreferenceUtil.writeFactory(this,true);
		ConfigManager.startConfigAPK(this, ConfigManager.CONFIG_DRAGON_SN, false);
	}

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
	
	/*
	 * void showFullWindow(View view){ fullContainer.removeAllViews();
	 * fullContainer.addView(((IBaseCase) view.getTag()).mMaxView);
	 * fullContainer.setVisibility(View.VISIBLE); }
	 * 
	 * private void hideFullWindow(){ if (fullContainer.getChildCount() > 0) {
	 * ((IBaseCase) fullContainer.getChildAt(0).getTag()).stopCase(); }
	 * fullContainer.removeAllViews(); fullContainer.setVisibility(View.GONE); }
	 */
	/*
	 * @Override public void onClick(View view) { showFullWindow(view);
	 * ((IBaseCase) view.getTag()).startCaseforAuto(); }
	 */

}
