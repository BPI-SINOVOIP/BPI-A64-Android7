/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.run;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.TimeoutException;
import com.intellij.execution.process.ProcessHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 28, 2009
 * Time: 1:40:49 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AndroidApplicationLauncher {
  public abstract LaunchResult launch(@NotNull AndroidRunningState state, @NotNull IDevice device)
    throws IOException, AdbCommandRejectedException, TimeoutException;

  public boolean isReadyForDebugging(@NotNull ClientData data, @Nullable ProcessHandler processHandler) {
    return data.getDebuggerConnectionStatus() == ClientData.DebuggerStatus.WAITING;
  }

  public enum LaunchResult {
    SUCCESS, STOP, NOTHING_TO_DO
  }
}
