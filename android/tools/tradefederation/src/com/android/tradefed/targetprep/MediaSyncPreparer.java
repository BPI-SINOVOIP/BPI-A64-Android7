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

package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.concurrent.TimeUnit;

public class MediaSyncPreparer implements ITargetPreparer {
    @Option(name = "command-timeout", description = "Custom timeout for media sync command")
    private int mCommandTimeout = 2 * 60 * 1000;

    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final String MEDIA_RESCAN_INTENT =
            "am broadcast -a android.intent.action.MEDIA_MOUNTED -d file://sdcard";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {

        // trigger media rescan
        CLog.d("About to broadcast media rescan intent on device %s", device.getSerialNumber());
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        device.executeShellCommand(MEDIA_RESCAN_INTENT, receiver,
                mCommandTimeout, TimeUnit.MILLISECONDS, MAX_RETRY_ATTEMPTS);
        String output = receiver.getOutput();
        CLog.v("Media rescan intent on %s returned %s", MEDIA_RESCAN_INTENT,
                device.getSerialNumber(), output);
    }
}
