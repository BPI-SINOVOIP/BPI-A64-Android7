/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Map;

public class DevicePanel implements AndroidDebugBridge.IDeviceChangeListener, AndroidDebugBridge.IDebugBridgeChangeListener,
                                    AndroidDebugBridge.IClientChangeListener, Disposable {
  private JPanel myPanel;

  private final DeviceContext myDeviceContext;
  @Nullable private AndroidDebugBridge myBridge;

  @NotNull private final Project myProject;
  @NotNull private final Map<String, String> myPreferredClients;
  public boolean myIgnoreActionEvents;
  @NotNull private JComboBox myDeviceCombo;
  @NotNull private JComboBox myClientCombo;
  @Nullable private String myCandidateClientName;

  public DevicePanel(@NotNull Project project, @NotNull DeviceContext context) {
    myProject = project;
    myDeviceContext = context;
    myPreferredClients = Maps.newHashMap();
    myCandidateClientName = getApplicationName();
    Disposer.register(myProject, this);

    initializeDeviceCombo();
    initializeClientCombo();

    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addClientChangeListener(this);
    AndroidDebugBridge.addDebugBridgeChangeListener(this);

  }

  private void initializeDeviceCombo() {
    myDeviceCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (myIgnoreActionEvents) return;

        updateClientCombo();
        Object sel = myDeviceCombo.getSelectedItem();
        IDevice device = (sel instanceof IDevice) ? (IDevice)sel : null;
        myDeviceContext.fireDeviceSelected(device);
      }
    });

    myDeviceCombo.setRenderer(new DeviceRenderer.DeviceComboBoxRenderer("No Connected Devices"));
    Dimension size = myDeviceCombo.getMinimumSize();
    myDeviceCombo.setMinimumSize(new Dimension(200, size.height));
  }

  private void initializeClientCombo() {
    myClientCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (myIgnoreActionEvents) return;

        Client client = (Client)myClientCombo.getSelectedItem();
        if (client != null) {
          myPreferredClients.put(client.getDevice().getName(), client.getClientData().getClientDescription());
        }
        myDeviceContext.fireClientSelected(client);
      }
    });

    myClientCombo.setRenderer(new ClientCellRenderer("No Debuggable Applications"));
    Dimension size = myClientCombo.getMinimumSize();
    myClientCombo.setMinimumSize(new Dimension(250, size.height));
  }

  public void selectDevice(IDevice device) {
    myDeviceCombo.setSelectedItem(device);
  }

  public void selectClient(Client client) {
    myClientCombo.setSelectedItem(client);
  }

  @Nullable
  private String getApplicationName() {
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(module);
      if (moduleInfo != null) {
        String pkg = moduleInfo.getPackage();
        if (pkg != null) {
          return pkg;
        }
      }
    }
    return null;
  }

  @Override
  public void dispose() {
    if (myBridge != null) {
      AndroidDebugBridge.removeDeviceChangeListener(this);
      AndroidDebugBridge.removeClientChangeListener(this);
      AndroidDebugBridge.removeDebugBridgeChangeListener(this);

      myBridge = null;
    }
  }

  public JPanel getComponent() {
    return myPanel;
  }

  @Override
  public void bridgeChanged(final AndroidDebugBridge bridge) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myBridge = bridge;
        updateDeviceCombo();
      }
    });
  }

  @Override
  public void deviceConnected(final IDevice device) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        updateDeviceCombo();
      }
    });
  }

  @Override
  public void deviceDisconnected(final IDevice device) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        updateDeviceCombo();
      }
    });
  }

  @Override
  public void deviceChanged(final IDevice device, final int changeMask) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if ((changeMask & IDevice.CHANGE_CLIENT_LIST) != 0) {
          updateClientCombo();
        }
        else if ((changeMask & IDevice.CHANGE_STATE) != 0) {
          updateDeviceCombo();
        }
        if (device != null) {
          myDeviceContext.fireDeviceChanged(device, changeMask);
        }
      }
    });
  }

  @Override
  public void clientChanged(Client client, int changeMask) {
    if ((changeMask & Client.CHANGE_NAME) != 0) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          updateClientCombo();
        }
      });
    }
  }

  private void updateDeviceCombo() {
    myIgnoreActionEvents = true;

    boolean update = true;
    IDevice selected = (IDevice)myDeviceCombo.getSelectedItem();
    myDeviceCombo.removeAllItems();
    if (myBridge != null) {
      for (IDevice device : myBridge.getDevices()) {
        myDeviceCombo.addItem(device);
        if (selected == device) {
          myDeviceCombo.setSelectedItem(device);
          update = false;
        }
      }
    }

    if (update) {
      myDeviceContext.fireDeviceSelected((IDevice)myDeviceCombo.getSelectedItem());
      updateClientCombo();
    }

    myIgnoreActionEvents = false;
  }

  private void updateClientCombo() {
    myIgnoreActionEvents = true;

    IDevice device = (IDevice)myDeviceCombo.getSelectedItem();
    Client selected = (Client)myClientCombo.getSelectedItem();
    Client toSelect = selected;
    boolean update = true;
    myClientCombo.removeAllItems();
    if (device != null) {
      // Change the currently selected client if the user has a preference.
      String preferred = getPreferredClientForDevice(device.getName());
      if (preferred != null) {
        Client preferredClient = device.getClient(preferred);
        if (preferredClient != null) {
          toSelect = preferredClient;
        }
      }

      Client[] clients = device.getClients();
      // There's a chance we got this update because a client we were debugging
      // just crashed or was closed. We still want to keep it in the list
      // though so the user can look over any final error messages / profiling
      // states.
      boolean selectedClientDied = true;
      Arrays.sort(clients, new ClientCellRenderer.ClientComparator());
      for (Client client : clients) {
        myClientCombo.addItem(client);
        if (selected == client) {
          selectedClientDied = false;
        }
      }
      if (selectedClientDied) {
        myClientCombo.addItem(selected);
      }

      myClientCombo.setSelectedItem(toSelect);
      update = toSelect != selected;
    }

    myIgnoreActionEvents = false;

    if (update) {
      myDeviceContext.fireClientSelected((Client)myClientCombo.getSelectedItem());
    }
  }

  @Nullable
  private String getPreferredClientForDevice(String deviceName) {
    String client = myPreferredClients.get(deviceName);
    return client == null ? myCandidateClientName : client;
  }
}
