/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;

import java.io.OutputStream;

public class RemoteDebugProcessHandler extends ProcessHandler{
  private final Project myProject;

  public RemoteDebugProcessHandler(Project project) {
    myProject = project;
  }

  public void startNotify() {
    final DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    final DebugProcessAdapter listener = new DebugProcessAdapter() {
      //executed in manager thread
      public void processDetached(DebugProcess process, boolean closedByUser) {
        debugProcess.removeDebugProcessListener(this);
        notifyProcessDetached();
      }
    };
    debugProcess.addDebugProcessListener(listener);
    try {
      super.startNotify();
    }
    finally {
      // in case we added our listener too late, we may have lost processDetached notification,
      // so check here if process is detached
      if (debugProcess.isDetached()) {
        debugProcess.removeDebugProcessListener(listener);
        notifyProcessDetached();
      }
    }
  }

  protected void destroyProcessImpl() {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if(debugProcess != null) {
      debugProcess.stop(true);
    }
  }

  protected void detachProcessImpl() {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if(debugProcess != null) {
      debugProcess.stop(false);
    }
  }

  public boolean detachIsDefault() {
    return true;
  }

  public OutputStream getProcessInput() {
    return null;
  }
}
