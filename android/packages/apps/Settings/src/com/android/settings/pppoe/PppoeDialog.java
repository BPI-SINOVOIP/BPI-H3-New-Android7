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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.security.Credentials;
import android.security.KeyStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.EditText;
import android.net.InterfaceConfiguration;
import android.net.IpConfiguration;
import android.os.INetworkManagementService;
import android.os.ServiceManager;
import android.util.Log;
import android.text.TextUtils;
import android.content.Context;
import android.net.PppoeManager;
import android.net.NetworkUtils;
import java.net.InetAddress;
import java.util.List;
import android.content.res.Resources;

public class PppoeDialog extends AlertDialog implements TextWatcher,
        View.OnClickListener, AdapterView.OnItemSelectedListener {

    private static final String TAG = "PppoeDialog";
    private final DialogInterface.OnClickListener mListener;

    private View mView;

    private Spinner mInterface;
    private EditText mUser;
    private EditText mPassword;
    private PppoeManager mPppoeService;

    private String mUserStr;
    private String mPasswordStr;
    private int mInterfaceNameIndex;

    public PppoeDialog(Context context, DialogInterface.OnClickListener listener,
            String user, String password, int index) {
        super(context);
        mListener = listener;
        mUserStr = user;
        mPasswordStr = password;
        mInterfaceNameIndex = index;
    }

    @Override
    protected void onCreate(Bundle savedState) {
        mView = getLayoutInflater().inflate(R.layout.pppoe_dialog, null);
        setTitle(R.string.pppoe_configure);
        setView(mView);
        setInverseBackgroundForced(true);
        Context context = getContext();

        // First, find out all the fields.
        mInterface = (Spinner) mView.findViewById(R.id.pppoe_interface);
        mUser = (EditText) mView.findViewById(R.id.pppoe_user_edit);
        mPassword = (EditText) mView.findViewById(R.id.pppoe_password_edit);
        mPppoeService = (PppoeManager)context.getSystemService(Context.PPPOE_SERVICE);
        mUser.setText(mUserStr);
        mPassword.setText(mPasswordStr);
        if ((mUserStr != null) && (!TextUtils.isEmpty(mUserStr))) {
            mUser.setSelection(mUserStr.length());
        }
        if ((mPasswordStr != null) && (!TextUtils.isEmpty(mPasswordStr))) {
            mPassword.setSelection(mPasswordStr.length());
        }
        mInterface.setSelection(mInterfaceNameIndex);
        mInterface.setOnItemSelectedListener(this);
        mUser.addTextChangedListener(this);
        mPassword.addTextChangedListener(this);
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(R.string.eth_cancel), mListener);
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(R.string.eth_ok), mListener);
        super.onCreate(savedState);
        mView.findViewById(R.id.eth_conf_editor).setVisibility(View.VISIBLE);
        getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
    }

    @Override
    public void afterTextChanged(Editable field) {
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mInterfaceNameIndex = position;
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    public int getInterfaceIndex() {
        return mInterfaceNameIndex;
    }

    public String getUser() {
        return mUser.getText().toString();
    }

    public String getPassword() {
        return mPassword.getText().toString();
    }

    @Override
    public void onClick(View v) {
    }
}
