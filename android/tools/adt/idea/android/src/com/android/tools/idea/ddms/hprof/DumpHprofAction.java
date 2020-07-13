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
package com.android.tools.idea.ddms.hprof;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.tools.chartlib.EventData;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.actions.AbstractClientAction;
import com.android.tools.idea.editors.hprof.HprofCaptureType;
import com.android.tools.idea.monitor.memory.MemoryMonitorView;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DumpHprofAction extends AbstractClientAction {
  @NotNull
  private final Project myProject;
  @NotNull
  private EventData myEvents;

  public DumpHprofAction(@NotNull Project project, @NotNull DeviceContext deviceContext, @NotNull EventData events) {
    super(deviceContext,
          AndroidBundle.message("android.ddms.actions.dump.hprof"),
          AndroidBundle.message("android.ddms.actions.dump.hprof.description"),
          AndroidIcons.Ddms.DumpHprof);
    myProject = project;
    myEvents = events;
  }

  @Override
  protected void performAction(@NotNull Client c) {
    ApplicationManager.getApplication().executeOnPooledThread(new HprofRequest(c, myEvents));
  }

  class HprofRequest implements Runnable, AndroidDebugBridge.IClientChangeListener {

    private final Client myClient;
    private CountDownLatch myResponse;
    private final EventData myEvents;
    private EventData.Event myEvent;

    public HprofRequest(Client client, EventData events) {
      myClient = client;
      myEvents = events;
      myResponse = new CountDownLatch(1);
    }

    @Override
    public void run() {
      AndroidDebugBridge.addClientChangeListener(this);

      myClient.dumpHprof();
      synchronized (myEvents) {
         myEvent = myEvents.start(System.currentTimeMillis(), MemoryMonitorView.EVENT_HPROF);
      }
      try {
        myResponse.await(1, TimeUnit.MINUTES);
        // TODO Handle cases where it fails or times out.
      }
      catch (InterruptedException e) {
        // Interrupted
      }
      // If the event had not finished, finish it now
      synchronized (myEvents) {
        if (myEvent != null) {
          myEvent.stop(System.currentTimeMillis());
        }
      }
      AndroidDebugBridge.removeClientChangeListener(this);
    }

    @Override
    public void clientChanged(Client client, int changeMask) {
      if (changeMask == Client.CHANGE_HPROF && client == myClient) {
        final ClientData.HprofData data = client.getClientData().getHprofData();
        if (data != null) {
          switch (data.type) {
            case FILE:
              // TODO: older devices don't stream back the heap data. Instead they save results on the sdcard.
              // We don't support this yet.
              Messages.showErrorDialog(AndroidBundle.message("android.ddms.actions.dump.hprof.error.unsupported"),
                                       AndroidBundle.message("android.ddms.actions.dump.hprof"));
              break;
            case DATA:
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                      try {
                        CaptureService service = CaptureService.getInstance(myProject);
                        service.createCapture(HprofCaptureType.class, data.data);
                      }
                      catch (IOException e) {
                        throw new RuntimeException(e);
                      }
                    }
                  });
                }
              });
              break;
          }
        } else {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog("Error obtaining Hprof data", AndroidBundle.message("android.ddms.actions.dump.hprof"));
            }
          });
        }
        myResponse.countDown();
      }
    }
  }
}
