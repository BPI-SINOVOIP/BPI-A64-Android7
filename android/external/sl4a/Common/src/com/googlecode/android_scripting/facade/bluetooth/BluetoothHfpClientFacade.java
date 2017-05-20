/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.android_scripting.facade.bluetooth;

import java.util.List;

import android.app.Service;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.os.ParcelUuid;

import com.googlecode.android_scripting.Log;
import com.googlecode.android_scripting.facade.FacadeManager;
import com.googlecode.android_scripting.jsonrpc.RpcReceiver;
import com.googlecode.android_scripting.rpc.Rpc;
import com.googlecode.android_scripting.rpc.RpcParameter;

public class BluetoothHfpClientFacade extends RpcReceiver {
  static final ParcelUuid[] UUIDS = {
    BluetoothUuid.Handsfree_AG,
  };

  private final Service mService;
  private final BluetoothAdapter mBluetoothAdapter;

  private static boolean sIsHfpClientReady = false;
  private static BluetoothHeadsetClient sHfpClientProfile = null;

  public BluetoothHfpClientFacade(FacadeManager manager) {
    super(manager);
    mService = manager.getService();
    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    mBluetoothAdapter.getProfileProxy(mService, new HfpClientServiceListener(),
        BluetoothProfile.HEADSET_CLIENT);
  }

  class HfpClientServiceListener implements BluetoothProfile.ServiceListener {
    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
      sHfpClientProfile = (BluetoothHeadsetClient) proxy;
      sIsHfpClientReady = true;
    }

    @Override
    public void onServiceDisconnected(int profile) {
      sIsHfpClientReady = false;
    }
  }

  public Boolean hfpClientConnect(BluetoothDevice device) {
    if (sHfpClientProfile == null) return false;
    return sHfpClientProfile.connect(device);
  }

  public Boolean hfpClientDisconnect(BluetoothDevice device) {
    if (sHfpClientProfile == null) return false;
    return sHfpClientProfile.disconnect(device);
  }

  @Rpc(description = "Is HfpClient profile ready.")
  public Boolean bluetoothHfpClientIsReady() {
    return sIsHfpClientReady;
  }

  @Rpc(description = "Connect to an HFP Client device.")
  public Boolean bluetoothHfpClientConnect(
      @RpcParameter(name = "device", description = "Name or MAC address of a bluetooth device.")
      String deviceStr)
      throws Exception {
    if (sHfpClientProfile == null) return false;
    try {
      BluetoothDevice device =
          BluetoothFacade.getDevice(BluetoothFacade.DiscoveredDevices, deviceStr);
      Log.d("Connecting to device " + device.getAliasName());
      return hfpClientConnect(device);
    } catch (Exception e) {
        Log.e("bluetoothHfpClientConnect failed on getDevice " + deviceStr + " with " + e);
        return false;
    }
  }

  @Rpc(description = "Disconnect an HFP Client device.")
  public Boolean bluetoothHfpClientDisconnect(
      @RpcParameter(name = "device", description = "Name or MAC address of a device.")
      String deviceStr) {
    if (sHfpClientProfile == null) return false;
    Log.d("Connected devices: " + sHfpClientProfile.getConnectedDevices());
    try {
        BluetoothDevice device =
            BluetoothFacade.getDevice(sHfpClientProfile.getConnectedDevices(), deviceStr);
        return hfpClientDisconnect(device);
    } catch (Exception e) {
        // Do nothing since it is disconnect and this function should force disconnect.
        Log.e("bluetoothHfpClientConnect getDevice failed " + e);
    }
    return false;
  }

  @Rpc(description = "Get all the devices connected through HFP Client.")
  public List<BluetoothDevice> bluetoothHfpClientGetConnectedDevices() {
    return sHfpClientProfile.getConnectedDevices();
  }

  @Rpc(description = "Get the connection status of a device.")
  public Integer bluetoothHfpClientGetConnectionStatus(
          @RpcParameter(name = "deviceID",
                        description = "Name or MAC address of a bluetooth device.")
          String deviceID) {
      if (sHfpClientProfile == null) {
          return BluetoothProfile.STATE_DISCONNECTED;
      }
      List<BluetoothDevice> deviceList = sHfpClientProfile.getConnectedDevices();
      BluetoothDevice device;
      try {
          device = BluetoothFacade.getDevice(deviceList, deviceID);
      } catch (Exception e) {
          Log.e(e);
          return BluetoothProfile.STATE_DISCONNECTED;
      }
      return sHfpClientProfile.getConnectionState(device);
  }

  @Override
  public void shutdown() {
  }
}
