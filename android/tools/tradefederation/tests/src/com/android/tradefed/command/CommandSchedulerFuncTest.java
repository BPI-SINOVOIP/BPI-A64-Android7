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

package com.android.tradefed.command;

import com.android.ddmlib.Log;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockDeviceManager;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.RunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Longer running test for {@link CommandScheduler}
 */
public class CommandSchedulerFuncTest extends TestCase {

    private static final String LOG_TAG = "CommandSchedulerFuncTest";
    /** the {@link CommandScheduler} under test, with all dependencies mocked out */
    private CommandScheduler mCommandScheduler;
    private MeasuredInvocation mMockTestInvoker;
    private IDeviceManager mMockDeviceManager;
    private IConfiguration mSlowConfig;
    private IConfiguration mFastConfig;
    private IConfigurationFactory mMockConfigFactory;
    private CommandOptions mCommandOptions;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSlowConfig = EasyMock.createNiceMock(IConfiguration.class);
        mFastConfig = EasyMock.createNiceMock(IConfiguration.class);
        mMockDeviceManager = new MockDeviceManager(1);
        mMockTestInvoker = new MeasuredInvocation();
        mMockConfigFactory = EasyMock.createMock(IConfigurationFactory.class);
        mCommandOptions = new CommandOptions();
        mCommandOptions.setLoopMode(true);
        mCommandOptions.setMinLoopTime(0);
        EasyMock.expect(mSlowConfig.getCommandOptions()).andStubReturn(mCommandOptions);
        EasyMock.expect(mFastConfig.getCommandOptions()).andStubReturn(mCommandOptions);
        EasyMock.expect(mSlowConfig.getDeviceRequirements()).andStubReturn(
                new DeviceSelectionOptions());
        EasyMock.expect(mFastConfig.getDeviceRequirements()).andStubReturn(
                new DeviceSelectionOptions());

        mCommandScheduler = new CommandScheduler() {
            @Override
            ITestInvocation createRunInstance() {
                return mMockTestInvoker;
            }

            @Override
            IDeviceManager getDeviceManager() {
                return mMockDeviceManager;
            }

            @Override
            IConfigurationFactory getConfigFactory() {
                return mMockConfigFactory;
            }

            @Override
            void initLogging() {
                // ignore
            }

            @Override
            void cleanUp() {
                // ignore
            }
        };
    }

    /**
     * Test config priority scheduling. Verifies that configs are prioritized according to their
     * total run time.
     * <p/>
     * This test continually executes two configs in loop mode. One config executes quickly (ie
     * "fast config"). The other config (ie "slow config") takes ~ 2 * fast config time to execute.
     * <p/>
     * The run is stopped after the slow config is executed 20 times. At the end of the test, it is
     * expected that "fast config" has executed roughly twice as much as the "slow config".
     */
    public void testRun_scheduling() throws Exception {
        String[] fastConfigArgs = new String[] {"fastConfig"};
        String[] slowConfigArgs = new String[] {"slowConfig"};

        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs(EasyMock.aryEq(fastConfigArgs)))
                .andReturn(mFastConfig).anyTimes();
        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs(EasyMock.aryEq(slowConfigArgs)))
                .andReturn(mSlowConfig).anyTimes();

        EasyMock.replay(mFastConfig, mSlowConfig, mMockConfigFactory);
        mCommandScheduler.start();
        mCommandScheduler.addCommand(fastConfigArgs);
        mCommandScheduler.addCommand(slowConfigArgs);

        synchronized (mMockTestInvoker) {
            mMockTestInvoker.wait();
        }
        mCommandScheduler.shutdown();
        mCommandScheduler.join();

        Log.i(LOG_TAG, String.format("fast times %d slow times %d",
                mMockTestInvoker.mFastCount, mMockTestInvoker.mSlowCount));
        // assert that fast config has executed roughly twice as much as slow config. Allow for
        // some variance since the execution time of each config (governed via Thread.sleep) will
        // not be 100% accurate
        assertEquals(mMockTestInvoker.mSlowCount * 2, mMockTestInvoker.mFastCount, 5);
    }

    private class MeasuredInvocation implements ITestInvocation {
        Integer mSlowCount = 0;
        Integer mFastCount = 0;
        Integer mSlowCountLimit = 40;

        @Override
        public void invoke(ITestDevice device, IConfiguration config, IRescheduler rescheduler,
                ITestInvocationListener... listeners)
                throws DeviceNotAvailableException {
            if (config.equals(mSlowConfig)) {
                // sleep for 2 * fast config time
                RunUtil.getDefault().sleep(200);
                synchronized (mSlowCount) {
                    mSlowCount++;
                }
                if (mSlowCount >= mSlowCountLimit) {
                    synchronized (this) {
                        notify();
                    }
                }
            } else if (config.equals(mFastConfig)) {
                RunUtil.getDefault().sleep(100);
                synchronized (mFastCount) {
                    mFastCount++;
                }
            } else {
                throw new IllegalArgumentException("unknown config");
            }
        }
   }
}
