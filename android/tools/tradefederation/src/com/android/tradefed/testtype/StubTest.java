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

package com.android.tradefed.testtype;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * No-op empty test implementation.
 */
public class StubTest implements IShardableTest {

    @Option(
            name = "num-shards",
            description = "Shard this test into given number of separately runnable chunks")
    private int mNumShards = 1;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        CLog.i("nothing to test!");

    }

    @Override
    public Collection<IRemoteTest> split() {
        if (mNumShards > 1) {
            List<IRemoteTest> shards = new ArrayList<IRemoteTest>(mNumShards);
            for (int i=0; i < mNumShards; i++) {
                shards.add(new StubTest());
            }
            CLog.logAndDisplay(
                    LogLevel.INFO, "splitting into %d shards", mNumShards);
            return shards;
        }
        return null;
    }
}
