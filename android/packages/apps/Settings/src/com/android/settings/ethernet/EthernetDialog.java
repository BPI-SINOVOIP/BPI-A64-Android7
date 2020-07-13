/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.ethernet;

import com.android.settings.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.util.Slog;
import android.view.inputmethod.InputMethodManager;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.os.Environment;
import android.util.SparseArray;
import android.net.StaticIpConfiguration;
import android.net.EthernetManager;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.util.Iterator;
import android.text.TextUtils;
import android.net.LinkAddress;
import android.net.NetworkUtils;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import com.android.settings.Utils;
import android.widget.Toast;
import android.net.EthernetManager;
import android.provider.Settings;

class EthernetDialog extends AlertDialog implements DialogInterface.OnClickListener, DialogInterface.OnShowListener,
        DialogInterface.OnDismissListener{
    private final String TAG = "EthConfDialog";
    private static final boolean localLOGV = false;

    /* This value comes from "wifi_ip_settings" resource array */
    private static final int DHCP = 0;
    private static final int STATIC_IP = 1;
    private IpAssignment mIpAssignment = IpAssignment.DHCP;
	private StaticIpConfiguration mStaticIpConfiguration = null;

    private View mView;
    private RadioButton mConTypeDhcp;
    private RadioButton mConTypeManual;
    private EditText mIpaddr;
    private EditText mDns;
    private EditText mGw;
    //private EditText mMask;
	private EditText mprefix;

    private Context mContext;
	private EthernetManager mEthManager;
	private ConnectivityManager mCM;
	
    public EthernetDialog(Context context,EthernetManager EthManager,ConnectivityManager cm) {
        super(context);
        mContext = context;
		mEthManager = EthManager;
		mCM = cm;
        buildDialogContent(context);
        setOnShowListener(this);
        setOnDismissListener(this);
    }

    public void onShow(DialogInterface dialog) {
        if (localLOGV) Slog.d(TAG, "onShow");
		UpdateInfo();
        // soft keyboard pops up on the disabled EditText. Hide it.
        InputMethodManager imm = (InputMethodManager)mContext.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    public void onDismiss(DialogInterface dialog) {
        if (localLOGV) Slog.d(TAG, "onDismiss");
    }

	public void UpdateInfo() {
		int enable = Settings.Global.getInt(mContext.getContentResolver(),Settings.Global.ETHERNET_ON,0);//add by hclydao
		if(enable == EthernetManager.ETH_STATE_ENABLED) {
		//if(mEthManager.isAvailable()) {
			IpConfiguration ipinfo = mEthManager.getConfiguration();
			if(ipinfo != null) {
				if(ipinfo.ipAssignment == IpAssignment.DHCP) {
					mConTypeDhcp.setChecked(true);
		            mIpaddr.setEnabled(false);
		            mDns.setEnabled(false);
		            mGw.setEnabled(false);
		            //mMask.setEnabled(true);
					mprefix.setEnabled(false);
					mDns.setText("");
					mGw.setText("");
					mprefix.setText("");
					mIpaddr.setText("");
					if(mCM != null) {
						LinkProperties lp  = mCM.getLinkProperties(ConnectivityManager.TYPE_ETHERNET);
					if(lp != null) {
							mIpaddr.setText(formatIpAddresses(lp));
						}
					}
				} else {
					mConTypeManual.setChecked(true);
		            mIpaddr.setEnabled(true);
		            mDns.setEnabled(true);
		            mGw.setEnabled(true);
		            //mMask.setEnabled(true);
					mprefix.setEnabled(true);
					StaticIpConfiguration staticConfig = ipinfo.getStaticIpConfiguration();
					if (staticConfig != null) {
						if (staticConfig.ipAddress != null) {
							mIpaddr.setText(staticConfig.ipAddress.getAddress().getHostAddress());
		                    mprefix.setText(Integer.toString(staticConfig.ipAddress.getNetworkPrefixLength()));
						}
		                if (staticConfig.gateway != null) {
		                    mGw.setText(staticConfig.gateway.getHostAddress());
		                }
		                Iterator<InetAddress> dnsIterator = staticConfig.dnsServers.iterator();
		                if (dnsIterator.hasNext()) {
		                    mDns.setText(dnsIterator.next().getHostAddress());
		                }
					}
				}
			}
		}
	}

    public int buildDialogContent(Context context) {
        this.setTitle(R.string.eth_config_title);
        this.setView(mView = getLayoutInflater().inflate(R.layout.eth_configure, null));
        mConTypeDhcp = (RadioButton) mView.findViewById(R.id.dhcp_radio);
        mConTypeManual = (RadioButton) mView.findViewById(R.id.manual_radio);
        mIpaddr = (EditText)mView.findViewById(R.id.ipaddr_edit);
		mprefix = (EditText)mView.findViewById(R.id.prefix_edit);
       // mMask = (EditText)mView.findViewById(R.id.netmask_edit);
        mDns = (EditText)mView.findViewById(R.id.eth_dns_edit);
        mGw = (EditText)mView.findViewById(R.id.eth_gw_edit);

        mConTypeDhcp.setChecked(true);
        mConTypeManual.setChecked(false);
        mIpaddr.setEnabled(false);
       // mMask.setEnabled(false);
		mprefix.setEnabled(false);
        mDns.setEnabled(false);
        mGw.setEnabled(false);

        mConTypeManual.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
                mIpaddr.setEnabled(true);
                mDns.setEnabled(true);
                mGw.setEnabled(true);
                //mMask.setEnabled(true);
				mprefix.setEnabled(true);
				mIpAssignment = IpAssignment.STATIC;
				if(TextUtils.isEmpty(mIpaddr.getText().toString()))
					mIpaddr.setText("192.168.1.15");
				if(TextUtils.isEmpty(mDns.getText().toString()))
					mDns.setText("192.168.1.1");
				if(TextUtils.isEmpty(mGw.getText().toString()))
					mGw.setText("192.168.1.1");
				if(TextUtils.isEmpty(mprefix.getText().toString()))
					mprefix.setText("24");
            }
        });

        mConTypeDhcp.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
                mIpaddr.setEnabled(false);
                mDns.setEnabled(false);
                mGw.setEnabled(false);
                //mMask.setEnabled(false);
				mprefix.setEnabled(false);
				mIpAssignment = IpAssignment.DHCP;
				mDns.setText("");
				mGw.setText("");
				mprefix.setText("");
				mIpaddr.setText("");
            }
        });

        this.setInverseBackgroundForced(true);
        this.setButton(BUTTON_POSITIVE, context.getText(R.string.menu_save), this);
        this.setButton(BUTTON_NEGATIVE, context.getText(R.string.menu_cancel), this);
		UpdateInfo();
		return 0;
    }

    private String formatIpAddresses(LinkProperties prop) {
        if (prop == null) return null;
        Iterator<InetAddress> iter = prop.getAllAddresses().iterator();
        // If there are no entries, return null
        if (!iter.hasNext()) return null;
        // Concatenate all available addresses, comma separated
        String addresses = "";
        while (iter.hasNext()) {
            addresses += iter.next().getHostAddress();
            if (iter.hasNext()) addresses += "\n";
        }
        return addresses;
    }

    private Inet4Address getIPv4Address(String text) {
        try {
            return (Inet4Address) NetworkUtils.numericToInetAddress(text);
        } catch (IllegalArgumentException|ClassCastException e) {
            return null;
        }
    }

    private int validateIpConfigFields(StaticIpConfiguration staticIpConfiguration) {

        String ipAddr = mIpaddr.getText().toString();

        Inet4Address inetAddr = getIPv4Address(ipAddr);
        if (inetAddr == null) {
            return 2;
        }
/*
		String netmask = mMask.getText().toString();
        if (TextUtils.isEmpty(netmask)) 
			return 11;
		Inet4Address netmas = getIPv4Address(netmask);
        if (netmas == null) {
            return 12;
        }
		int nmask = NetworkUtils.inetAddressToInt(netmas);
		int prefixlength = NetworkUtils.netmaskIntToPrefixLength(nmask);
*/
        int networkPrefixLength = -1;
        try {
            networkPrefixLength = Integer.parseInt(mprefix.getText().toString());
            if (networkPrefixLength < 0 || networkPrefixLength > 32) {
                return 3;
            }
            staticIpConfiguration.ipAddress = new LinkAddress(inetAddr, networkPrefixLength);
        } catch (NumberFormatException e) {
            // Set the hint as default after user types in ip address
        }

        String gateway = mGw.getText().toString();

        InetAddress gatewayAddr = getIPv4Address(gateway);
        if (gatewayAddr == null) {
            return 4;
        }
        staticIpConfiguration.gateway = gatewayAddr;

        String dns = mDns.getText().toString();
        InetAddress dnsAddr = null;

		dnsAddr = getIPv4Address(dns);
		if (dnsAddr == null) {
		    return 5;
		}

		staticIpConfiguration.dnsServers.add(dnsAddr);

        return 0;
    }

    private void handle_saveconf() {
        if (mConTypeDhcp.isChecked()) {
			Slog.i(TAG,"mode dhcp");
			mEthManager.setConfiguration(new IpConfiguration(mIpAssignment, ProxySettings.NONE,
                                null, null));
        } else {
            Slog.i(TAG,"mode static ip");
			if(isIpAddress(mIpaddr.getText().toString())
				&& isIpAddress(mGw.getText().toString())
				&& isIpAddress(mDns.getText().toString())) {
				
				if(TextUtils.isEmpty(mIpaddr.getText().toString())
					|| TextUtils.isEmpty(mprefix.getText().toString())
					|| TextUtils.isEmpty(mGw.getText().toString())
					|| TextUtils.isEmpty(mDns.getText().toString())) {
					Toast.makeText(mContext, R.string.eth_settings_empty, Toast.LENGTH_LONG).show();
		            return ;
				}

				mStaticIpConfiguration = new StaticIpConfiguration();
		        int result = validateIpConfigFields(mStaticIpConfiguration);
		        if (result != 0) {
					Toast.makeText(mContext, " error id is " + result, Toast.LENGTH_LONG).show();
		            return ;
		        } else {
					mEthManager.setConfiguration( new IpConfiguration(mIpAssignment, ProxySettings.NONE,
		                                mStaticIpConfiguration, null));
				}
			} else
				Toast.makeText(mContext, R.string.eth_settings_error, Toast.LENGTH_LONG).show();
		}
    }

    private boolean isIpAddress(String value) {
        int start = 0;
        int end = value.indexOf('.');
        int numBlocks = 0;

        while (start < value.length()) {
            if (end == -1) {
                end = value.length();
            }

            try {
                int block = Integer.parseInt(value.substring(start, end));
                if ((block > 255) || (block < 0)) {
                        return false;
                }
            } catch (NumberFormatException e) {
                    return false;
            }

            numBlocks++;

            start = end + 1;
            end = value.indexOf('.', start);
        }
        return numBlocks == 4;
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case BUTTON_POSITIVE:
                handle_saveconf();
                break;
            case BUTTON_NEGATIVE:
                //Don't need to do anything
                break;
            default:
        }
    }

}
