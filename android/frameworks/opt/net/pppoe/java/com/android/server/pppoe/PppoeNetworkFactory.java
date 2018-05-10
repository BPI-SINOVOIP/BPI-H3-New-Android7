package com.android.server.pppoe;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpResults;
import android.net.InterfaceConfiguration;
import android.net.NetworkUtils;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkRequest;
import android.net.EthernetManager;
import android.net.StaticIpConfiguration;
import android.os.Handler;
import android.os.UserHandle;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.Log;
import android.content.Intent;
import android.os.UserHandle;
import android.os.SystemProperties;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.net.BaseNetworkObserver;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.net.InetAddress;
import java.util.List;
import android.net.RouteInfo;
import android.net.PppoeManager;
import android.net.IPppoeManager;
import java.net.Inet4Address;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  track pppoe state
 *  pppoe connect & setup & disconnect
 *  @hide
**/

class PppoeNetworkFactory {
    private static final String NETWORK_TYPE = "PPPOE";
    private static final String TAG = "PppoeNetworkFactory";
    private static final int NETWORK_SCORE = 80;
    private static final boolean DBG = true;
    private static final boolean VDBG= true;

    private static void LOG(String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }
    private static void LOGD(String msg) {
        if (VDBG) {
            Log.d(TAG,msg);
        }
    }

    /** Tracks interface changes. Called from NetworkManagementService. */
    private InterfaceObserver mInterfaceObserver;

    /** For static IP configuration */
    private PppoeManager mPppoeManager;

    /** To set link state and configure IP addresses. */
    private INetworkManagementService mNMService;

    /* To communicate with ConnectivityManager */
    private NetworkCapabilities mNetworkCapabilities;
    private NetworkAgent mNetworkAgent;
    private LocalNetworkFactory mFactory;
    private Context mContext;
    private IpConfiguration mIpConfig;
    /** Product-dependent regular expression of interface names we track. */
    private static String mIfaceMatch = "ppp\\d";

    /** Data members. All accesses to these must be synchronized(this). */
    private static String mIface = "";
    private String mPhyIface = "eth0";
    private boolean everUpdateAgent = false;
    private String mHwAddr;
    private static boolean mLinkUp;
    private NetworkInfo mNetworkInfo;
    private LinkProperties mLinkProperties;
    private int mPppoeCurrentState;
    private String lcpState;
    private static boolean running = false;

    static {
        System.loadLibrary("pppoe-jni");
        registerNatives();
    }

    public int mPppoeState = PppoeManager.PPPOE_STATE_DISCONNECTED;

    private void setPppoeStateAndSendBroadcast(int newState) {
        int preState = mPppoeState;
        mPppoeState = newState;
        final Intent intent = new Intent(PppoeManager.PPPOE_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT |Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(PppoeManager.EXTRA_PPPOE_STATE, newState);
        if (newState == PppoeManager.PPPOE_EVENT_CONNECT_FAILED) {
            String exitCode=SystemProperties.get("net.eth0-pppoe.ppp-exit");
            LOG("net.eth0-pppoe.ppp-exit"+exitCode);
            if (exitCode != null &&!exitCode.isEmpty()) {
                if (exitCode.equals("1")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "fatal error");
                } else if (exitCode.equals("2")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "option error");
                } else if (exitCode.equals("3")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "not root");
                } else if (exitCode.equals("4")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "no kernel support");
                } else if (exitCode.equals("5")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "user request");
                } else if (exitCode.equals("6")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "lock failed");
                } else if (exitCode.equals("7")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "open failed");
                } else if (exitCode.equals("8")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "connect failed");
                } else if (exitCode.equals("9")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "ptycmd failed");
                } else if (exitCode.equals("10")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "negotiation failed");
                } else if (exitCode.equals("11")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "peer auth failed");
                } else if (exitCode.equals("12")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "idle timeout");
                } else if (exitCode.equals("13")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "connect time");
                } else if (exitCode.equals("14")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "callback");
                } else if (exitCode.equals("15")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "peer dead");
                } else if (exitCode.equals("16")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "hung up");
                } else if (exitCode.equals("17")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "loop back");
                } else if (exitCode.equals("18")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "init failed");
                } else if (exitCode.equals("19")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "auth to peer failed");
                } else if (exitCode.equals("21")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "chid auth failed");
                } else if (exitCode.equals("22")) {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "link out");
                } else {
                    intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRMSG, "unkonw reason");
                }
            }
        }
        LOG("setPppoeStateAndSendBroadcast() : preState = " + preState +", curState = " + newState);
        mContext.sendStickyBroadcastAsUser(intent,UserHandle.ALL);
    }

    PppoeNetworkFactory() {
        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_PPPOE, 0, NETWORK_TYPE, "");
        mLinkProperties = new LinkProperties();
        initNetworkCapabilities();
    }

    private void initNetworkCapabilities() {

        mNetworkCapabilities = new NetworkCapabilities();
        mNetworkCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_PPPOE);
        mNetworkCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        mNetworkCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        // We have no useful data on bandwidth. Say 100M up and 100M down. :-(
        mNetworkCapabilities.setLinkUpstreamBandwidthKbps(100 * 1000);
        mNetworkCapabilities.setLinkDownstreamBandwidthKbps(100 * 1000);
    }

    private class LocalNetworkFactory extends NetworkFactory {
        LocalNetworkFactory(String name, Context context, Looper looper) {
            super(looper, context, name, new NetworkCapabilities());
        }

        protected void startNetwork() {
            onRequestNetwork();
        }
        protected void stopNetwork() {
        }
    }

    private class InterfaceObserver extends BaseNetworkObserver {
        @Override
        public void interfaceLinkStateChanged(String iface, boolean up) {
	    Log.w(TAG,"interfaceLinkStateChanged");
            updateInterfaceState(iface, up);
        }

        @Override
        public void interfaceAdded(String iface) {
	    Log.w(TAG,"interfaceAdded");
            maybeTrackInterface(iface);
        }

        @Override
        public void interfaceRemoved(String iface) {
            Log.w(TAG,"interfaceRemoved");
            stopTrackingInterface(iface);
            setPppoeStateAndSendBroadcast(PppoeManager.PPPOE_EVENT_DISCONNECT_SUCCESSED);
        }
    }
    public void updateInterfaceState(String iface, boolean up) {

        LOGD("updateInterface: mIface:" + mIface + " mPhyIface:"+ mPhyIface + " iface:" + iface + " link:" + (up ? "up" : "down"));

        if (!mIface.equals(iface) && !mPhyIface.equals(iface)) {
            LOGD("not tracker interface");
            return;
        }

        if (mPhyIface.equals(iface)) { //需要监所连接物理端口
            if(!up) {
                if (getPppoeState(iface) != PppoeManager.PPPOE_STATE_DISCONNECTED) {
                    stopPppoe(iface);                  //当物理网口断开时,断开pppoe链接
                    stopTrackingInterface(iface);
                    setPppoeStateAndSendBroadcast(PppoeManager.PPPOE_EVENT_CONNECT_FAILED);
                }
            } else {
                if (mPppoeManager.isPppoeEnabled() && (getPppoeState(iface) != PppoeManager.PPPOE_STATE_CONNECTED)) {
                    startPppoe(iface);
                }
            }
 	    return;
        }
    }

    public boolean maybeTrackInterface(String iface) {
        // If we don't already have an interface, and if this interface matches
        // our regex, start tracking it.
        if (!iface.matches(mIfaceMatch))
            return false;
        LOG("Started tracking interface " + iface);
        setInterfaceUp(iface);
        return true;
    }

    public void updateAgent() {
        LOG("updateAgent");
        synchronized (PppoeNetworkFactory.this) {
            if (mNetworkAgent == null) {
                LOG("mNetworkAgent is null");
                return;
            }
            if (DBG) {
                Log.i(TAG, "Updating mNetworkAgent with: " +
                      mNetworkCapabilities + ", " +
                      mNetworkInfo + ", " +
                      mLinkProperties);
            }

            mNetworkAgent.sendNetworkCapabilities(mNetworkCapabilities);
            mNetworkAgent.sendNetworkInfo(mNetworkInfo);
            mNetworkAgent.sendLinkProperties(mLinkProperties);
            mNetworkAgent.sendNetworkScore(mLinkUp? NETWORK_SCORE : 0);
        }
    }

    private void setInterfaceUp(String iface) {
        // Bring up the interface so we get link status indications.
        try {
            InterfaceConfiguration config = mNMService.getInterfaceConfig(iface); // get eth0/wlan0 mac address
            if (config == null) {
                Log.e(TAG, "Null iterface config for " + iface + ". Bailing out.");
                return;
            }
            synchronized (this) {
                mHwAddr = config.getHardwareAddress();
                mNetworkInfo.setExtraInfo(mHwAddr);
                Log.w(TAG,"setInterfaceUp ,mHwAddr = "+mHwAddr);
            }
        } catch (Exception e) {
                Log.e(TAG, "Error upping interface " + mIface + ": " + e);
        }
    }

    public void stopTrackingInterface(String iface) {
        LOG("stopTrackingInterface");
        if ((!mIface.equals("")) && (!iface.equals(mIface)))
            return;

        Log.d(TAG, "Stopped tracking interface " + iface);
        // TODO: Unify this codepath with stop().
        synchronized (this) {
            mIface = "";
            everUpdateAgent = false;
            mNetworkInfo.setExtraInfo(null);
            mLinkUp = false;
            mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, mHwAddr);
            updateAgent();
            mNetworkAgent = null;
            mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_PPPOE, 0, NETWORK_TYPE, "");
            mLinkProperties = new LinkProperties();
        }
    }
    public void onRequestNetwork() {
        LOG("onRequestNetwork");
    }


    public void connected(String iface) {
        LOG("connected");
        LinkProperties linkProperties;
        String lcpstate = SystemProperties.get("net."+iface+"-pppoe.lcpup");
        synchronized(PppoeNetworkFactory.this) {
            if ((mNetworkAgent != null) && ("no").equals(lcpstate)) {
                Log.e(TAG, "Already have a NetworkAgent - aborting new request");
                return;
            }
            mIface = SystemProperties.get("net."+iface+"-pppoe.interface");
            linkProperties = new LinkProperties();
            linkProperties.setInterfaceName(this.mIface);

            String[] dnses = new String[2];
	    String route;
            String ipaddr;

	    dnses[0] = SystemProperties.get("net."+iface+"-"+mIface+".dns1");
            dnses[1] = SystemProperties.get("net."+iface+"-"+mIface+".dns2");

            List<InetAddress>dnsServers = new ArrayList<InetAddress>();
            dnsServers.add(NetworkUtils.numericToInetAddress(dnses[0]));
            dnsServers.add(NetworkUtils.numericToInetAddress(dnses[1]));
	    linkProperties.setDnsServers(dnsServers);

	    route = SystemProperties.get("net."+iface+"-"+mIface+".remote-ip");
            linkProperties.addRoute(new RouteInfo(NetworkUtils.numericToInetAddress(route)));

            ipaddr = SystemProperties.get("net."+iface+"-"+mIface+".local-ip");
            linkProperties.addLinkAddress(new LinkAddress(NetworkUtils.numericToInetAddress(ipaddr),28));

            mLinkProperties = linkProperties;

            mNetworkInfo.setIsAvailable(true);
            mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, mHwAddr);
           // everUpdateAgent = true;
            // Create our NetworkAgent.
            setPppoeStateAndSendBroadcast(PppoeManager.PPPOE_EVENT_CONNECT_SUCCESSED);
            mNetworkAgent = null;
            mNetworkAgent = new NetworkAgent(mFactory.getLooper(), mContext,
                            NETWORK_TYPE, mNetworkInfo, mNetworkCapabilities, mLinkProperties,
                            NETWORK_SCORE) {
            public void unwanted() {
                synchronized(PppoeNetworkFactory.this) {
                    if (this == mNetworkAgent) {
                        LOG("unWanted");
                        mLinkProperties.clear();
                        mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null,mHwAddr);
                        updateAgent();
                        mNetworkAgent = null;
                    } else {
                        Log.d(TAG, "Ignoring unwanted as we have a more modern " +
                                            "instance");
                        mNetworkAgent = null;
                    }
                }
            };
            };
        }
    }

  
   
    /**
     * Begin monitoring connectivity
     */
    public synchronized void start(Context context, Handler target) {

        // The services we use.
        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        mNMService = INetworkManagementService.Stub.asInterface(b);
        mPppoeManager = (PppoeManager) context.getSystemService(Context.PPPOE_SERVICE);

        // Create and register our NetworkFactory.
        mFactory = new LocalNetworkFactory(NETWORK_TYPE, context, target.getLooper());
        mFactory.setCapabilityFilter(mNetworkCapabilities);
        mFactory.setScoreFilter(-1); // this set high when we have an iface
        mFactory.register();
        mContext = context;

        // Start tracking interface change events.
        mInterfaceObserver = new InterfaceObserver();
        try {
            mNMService.registerObserver(mInterfaceObserver);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not register InterfaceObserver " + e);
        }
        synchronized(this) {
            String iface = mPppoeManager.getPppoeInterfaceName();
            stopPppoe(iface);
            if (mPppoeManager.isPppoeEnabled() && (getPppoeState(iface) != PppoeManager.PPPOE_STATE_CONNECTED)) {
                LOGD("start:startPppoe");
                startPppoe(iface);
            }
        }
    }

    public int getPppoeState(String iface) {
        String state = SystemProperties.get("net."+iface+"-pppoe.status");
        if (state.equals("starting")) {
            return PppoeManager.PPPOE_STATE_CONNECTING;
        }
        else if (state.equals("started")) {
            return PppoeManager.PPPOE_STATE_CONNECTED;
        }
        else if (state.equals("stopping")) {
            return PppoeManager.PPPOE_STATE_DISCONNECTING;
        }
        else if (state.equals("stopped")) {
            return PppoeManager.PPPOE_STATE_DISCONNECTED;
        } else {
            return PppoeManager.PPPOE_STATE_DISCONNECTED;
        }

    }
    public boolean startPppoe(String iface) {  //add by zhaokai,2011.11.4
        LOG("startPppoe");
        int count;
        mPhyIface = iface;
        running = true;
        final Thread PppoeThread = new Thread(new Runnable() {
            public void run() {
                setPppoeStateAndSendBroadcast(PppoeManager.PPPOE_EVENT_CONNECTING);
                if (0!=startPppoeNative(iface)) {
                    Log.e(TAG,"startPppoe():failed to start pppoe!");
                    setPppoeStateAndSendBroadcast(PppoeManager.PPPOE_EVENT_CONNECT_FAILED);
                    return;
                } else {
                    connected(iface);
                    return;
                }
            }
        });
        PppoeThread.start();
        //MonitorThread used to monitor pppoe hangup.
        final Thread MonitorThread = new Thread(new Runnable() {
            public void run() {
                while(running) {
                    lcpState = SystemProperties.get("net."+iface+"-pppoe.lcpup");
                    if (("yes").equals(lcpState)) {
                        SystemProperties.set("net." + iface +"-pppoe.lcpup","no");
                        connected(iface);
                    }
                    try {
                        Thread.sleep(200);
                    } catch (Exception e) {
                        LOG("PPPoE MonitorThread error!");
                    }
                }
            }
        });
        MonitorThread.start();
        return true;
    }
    public boolean stopPppoe(String iface) {
        setPppoeStateAndSendBroadcast(PppoeManager.PPPOE_EVENT_DISCONNECTING);
        if (0!=stopPppoeNative(iface)) {
            Log.e(TAG,"stopPppoe():failed to stop pppoe!");
            setPppoeStateAndSendBroadcast(PppoeManager.PPPOE_EVENT_DISCONNECT_SUCCESSED);
            return false;
        } else {
            setPppoeStateAndSendBroadcast(PppoeManager.PPPOE_EVENT_DISCONNECT_SUCCESSED);
            running = false;
            return true;
        }
    }
    public LinkProperties getPppoelinkProperties() {
        return mLinkProperties;
    }
    public native static int startPppoeNative(String iface);
    public native static int stopPppoeNative(String iface);
    public native static int registerNatives();
}
