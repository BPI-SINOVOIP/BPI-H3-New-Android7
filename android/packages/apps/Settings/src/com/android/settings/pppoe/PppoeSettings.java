/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.pppoe;

import com.android.settings.R;

import com.android.settings.pppoe.PppoeDialog;
import com.android.settings.RestrictedSettingsFragment;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Network;
import android.net.NetworkInfo.DetailedState;
import android.net.LinkProperties;
import android.net.LinkAddress;
import android.net.EthernetManager;
import android.net.PppoeManager;
import android.net.InterfaceConfiguration;
import android.net.StaticIpConfiguration;
import android.net.IpConfiguration;
import android.net.NetworkUtils;
import android.net.IpConfiguration.IpAssignment;
import android.os.INetworkManagementService;
import android.net.RouteInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ServiceManager;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.PreferenceCategory;
import android.support.v14.preference.SwitchPreference;
import android.util.Log;
import android.util.Slog;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.content.ContentResolver;
import android.text.TextUtils;
import android.provider.Settings;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.SettingsActivity;
import com.android.internal.logging.MetricsProto.MetricsEvent;

import java.net.Inet4Address;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import android.os.AsyncTask;
import android.widget.TextView;
import android.widget.Switch;
import android.app.ActionBar;
import android.view.Gravity;
import android.widget.Toast;
import java.net.InetAddress;
import java.lang.reflect.Field;

public class PppoeSettings extends RestrictedSettingsFragment implements
        Preference.OnPreferenceChangeListener,
        DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

    private static final String TAG = "PppoeSettings";
    private static boolean DBG = true;
    private IpConfiguration mIpConfiguration;
    private static final String KEY_PPPOE_SETUP = "pppoe_setup";
    private static final String KEY_PPPOE_MODE = "pppoe_enable";
    private static final String KEY_PPPOE_LOCAL_ADDRESS = "local_address";
    private static final String KEY_PPPOE_REMOTE_ADDRESS = "remote_address";
    private static final String KEY_PPPOE_DNS = "dns_address";

    private SwitchPreference mPppoeMode;
    private Preference mPppoeSetup;
    private Preference mLocalIpPreference;
    private Preference mRemoteIpPreference;
    private Preference mDnsPreference;
    private String user;
    private String password;
    private String ifname;
    private String preIface;
    private int mInterfaceIndex;
    private String[] mInterfaceType;
    private final IntentFilter mFilter;
    private final BroadcastReceiver mEthStateReceiver;
    private PppoeManager mPppoeManager;
    private PppoeDialog mPppoeDialog;
    private TextView mEmptyView;

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.DISPLAY;
    }

    public PppoeSettings() {
        super(null);
        mFilter = new IntentFilter();
        mFilter.addAction(PppoeManager.PPPOE_STATE_CHANGED_ACTION);
        mEthStateReceiver = new BroadcastReceiver() {
           @Override
            public void onReceive(Context context, Intent intent) {
                handleEvent(context, intent);
            }
        };
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pppoe_settings);
        mPppoeMode = (SwitchPreference)findPreference(KEY_PPPOE_MODE);
        mPppoeSetup = findPreference(KEY_PPPOE_SETUP);
        mLocalIpPreference = findPreference(KEY_PPPOE_LOCAL_ADDRESS);
        mRemoteIpPreference = findPreference(KEY_PPPOE_REMOTE_ADDRESS);
        mDnsPreference = findPreference(KEY_PPPOE_DNS);
        mInterfaceType = getResources().getStringArray(R.array.pppoe_interface);

        /* first, we must get the Service. */
        mPppoeManager = (PppoeManager)getSystemService(Context.PPPOE_SERVICE);
        mIpConfiguration = new IpConfiguration();
        if (!mPppoeManager.isPppoeEnabled()) {
            mPppoeMode.setChecked(false);
        } else {
            mPppoeMode.setChecked(true);
        }
        ifname = mPppoeManager.getPppoeInterfaceName();
        preIface = ifname;
        List<String> login = mPppoeManager.getPppoeUserInfo(ifname);
        Log.d(TAG,"onCreate,login = "+login);
        if (login != null && (!login.isEmpty())) {
            user = login.get(0);
            password = login.get(1);
        } else {
            user = null;
            password = null;
        }
        if(DBG) Slog.d(TAG, "onCreate.");

    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mPppoeSetup) {
            int index = 0;
            for (int i = 0; i < mInterfaceType.length; i++) {
                if (!TextUtils.isEmpty(ifname) && ifname.equals(mInterfaceType[i])) {
                    index = i;
                }
            }
            mPppoeDialog = new PppoeDialog(getActivity(), this, user, password, index);
            mPppoeDialog.setOnDismissListener(this);
            mPppoeDialog.show();
        } else {
            if (!(TextUtils.isEmpty(user)) && (!TextUtils.isEmpty(password))) {
                mPppoeManager.setupPppoe(ifname, user, password);
            } else {
                Log.w(TAG," user or password should not be null");
            }

            if (mPppoeMode.isChecked()) {
                Log.w(TAG,"start connect pppoe");
                if (TextUtils.isEmpty(ifname) || TextUtils.isEmpty(user) || TextUtils.isEmpty(password)) {
                    String pppoeTips = getString(R.string.pppoe_tips);
                    displayToast(pppoeTips);
                    mPppoeMode.setChecked(false);
                    return false;
                }
                mPppoeMode.setEnabled(true);
                mPppoeMode.setChecked(true);
                Thread setConfigThread = new Thread(new Runnable() {
                    public void run() {
                        if (mPppoeManager.getPppoeState(ifname) == PppoeManager.PPPOE_STATE_CONNECTED) {
                            mPppoeManager.disconnectPppoe(ifname);
                        } else {
                            if (mPppoeManager.getPppoeState(preIface) != PppoeManager.PPPOE_STATE_DISCONNECTED) {
                                mPppoeManager.disconnectPppoe(preIface);
                            }
                            preIface = ifname;
	                    mPppoeManager.connectPppoe(ifname);
	                }
                   }
                });
                setConfigThread.start();
            } else {
                Log.w(TAG,"disconnect pppoe");
                mPppoeMode.setChecked(false);
                mPppoeManager.disconnectPppoe(ifname);
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onSaveInstanceState(Bundle savedState) {
    }

    @Override
    public void onStart() {
        super.onStart();
        final SettingsActivity activity = (SettingsActivity) getActivity();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(mEthStateReceiver, mFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo info) {
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return false;
    }
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue){
        return false;
    }

    private void displayToast(String str) {
        Toast.makeText((Context)getActivity(), str, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(DialogInterface dialog, int whichbutton) {
        /* get the information form dialog. */
        if (whichbutton == DialogInterface.BUTTON_POSITIVE) {
            user = mPppoeDialog.getUser() ;
            password = mPppoeDialog.getPassword();
            int index = mPppoeDialog.getInterfaceIndex();
            ifname = mInterfaceType[index];

            if ((user != null) && (password != null) && (ifname != null) && (!ifname.equals(preIface))) {
                Log.d(TAG,"onClick ,ifname = "+ifname+",preIface = "+preIface);
                if (mPppoeManager.isPppoeEnabled()) {
                    Log.d(TAG,"onClick,pppoe enabled");
                    Thread disConnectThread = new Thread(new Runnable() {
                        public void run() {
                            if (mPppoeManager.getPppoeState(preIface) != PppoeManager.PPPOE_STATE_DISCONNECTED) {
                                Log.d(TAG,"onClick,disconnectPppoe");
                                mPppoeManager.disconnectPppoe(preIface);
                            }
                        }
                });
                disConnectThread.start();
                mPppoeMode.setChecked(false);
                }
            }
       }
    }
    private void handleEvent(Context context, Intent intent) {
        String action = intent.getAction();
        if (PppoeManager.PPPOE_STATE_CHANGED_ACTION.equals(action)) {
            final int event = intent.getIntExtra(PppoeManager.EXTRA_PPPOE_STATE,
                     PppoeManager.PPPOE_EVENT_CONNECT_SUCCESSED);
            switch(event) {
                case PppoeManager.PPPOE_EVENT_CONNECT_SUCCESSED :
                    Log.w(TAG,"received pppoe connnected message!");
                    final LinkProperties linkProperties = mPppoeManager.getPppoelinkProperties();
                    for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) { //set ipaddress.
                        if (linkAddress.getAddress() instanceof Inet4Address) {
                            mLocalIpPreference.setSummary(linkAddress.getAddress().getHostAddress());
                            break;
                        }
                    }

                    String Route = null;
                    for (RouteInfo route:linkProperties.getRoutes()){
                        if (route.isIPv4Default()) {
                            Route = route.getGateway().toString();
                            break;
                        }
                    }
                    if (Route != null) {
                        String[] RouteStr = Route.split("/");
                        mRemoteIpPreference.setSummary(RouteStr[1]);
                    } else {
                        mRemoteIpPreference.setSummary("0.0.0.0");
                    }
                    String dns = null;
                    int dnsCount = 0;
                    for (InetAddress inetAddress:linkProperties.getDnsServers()) {
                        if (0 == dnsCount) {
                            if (inetAddress instanceof Inet4Address) {
                                dns = inetAddress.getHostAddress().toString();
                                break;
                            }
                        }
                        if (1 == dnsCount) {
                            if (inetAddress instanceof Inet4Address) {
                                dns = inetAddress.getHostAddress().toString();
                                break;
                            }
                        }
                            dnsCount++;
                        }
                    if (dns!=null) {
                        mDnsPreference.setSummary(dns);
                    } else {
                        mDnsPreference.setSummary("0.0.0.0");
                    }

                    break;
                case PppoeManager.PPPOE_EVENT_DISCONNECT_SUCCESSED :
                case PppoeManager.PPPOE_EVENT_CONNECT_FAILED:
                    mLocalIpPreference.setSummary("0.0.0.0");
                    mRemoteIpPreference.setSummary("0.0.0.0");
                    mDnsPreference.setSummary("0.0.0.0");
                    if ((event == PppoeManager.PPPOE_EVENT_CONNECT_FAILED) && (mPppoeManager.isPppoeEnabled())) {
                        String errorReason = intent.getStringExtra(PppoeManager.EXTRA_PPPOE_ERRMSG);
                        String errmsg = context.getString(R.string.pppoe_errmsg)+errorReason;
                        displayToast(errmsg);
                    }
                    break;
                case PppoeManager.PPPOE_EVENT_DISCONNECTING:
                    break;
                default:
                    break;
            }
        }
    }
}
