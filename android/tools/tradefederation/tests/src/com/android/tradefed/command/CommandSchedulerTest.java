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

import com.android.tradefed.command.CommandFileParser.CommandLine;
import com.android.tradefed.command.CommandScheduler.CommandTracker;
import com.android.tradefed.command.CommandScheduler.CommandTrackerIdComparator;
import com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.IGlobalConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockDeviceManager;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.log.ITerribleFailureHandler;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Assert;

import java.io.File;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Unit tests for {@link CommandScheduler}.
 */
public class CommandSchedulerTest extends TestCase {

    private CommandScheduler mScheduler;
    private ITestInvocation mMockInvocation;
    private MockDeviceManager mMockManager;
    private IConfigurationFactory mMockConfigFactory;
    private IConfiguration mMockConfiguration;
    private CommandOptions mCommandOptions;
    private DeviceSelectionOptions mDeviceOptions;
    private CommandFileParser mMockCmdFileParser;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockInvocation = EasyMock.createMock(ITestInvocation.class);
        mMockManager = new MockDeviceManager(0);
        mMockConfigFactory = EasyMock.createMock(IConfigurationFactory.class);
        mMockConfiguration = EasyMock.createMock(IConfiguration.class);
        mCommandOptions = new CommandOptions();
        mDeviceOptions = new DeviceSelectionOptions();

        mScheduler = new CommandScheduler() {

            @Override
            ITestInvocation createRunInstance() {
                return mMockInvocation;
            }

            @Override
            IDeviceManager getDeviceManager() {
                return mMockManager;
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

            @Override
            void checkInvocations() {
                // ignore
            }

            @Override
            CommandFileParser createCommandFileParser() {
                return mMockCmdFileParser;
            }
        };
        // not starting the CommandScheduler yet because test methods need to setup mocks first
    }

    @Override
    protected void tearDown() throws Exception {
        if (mScheduler != null) {
            mScheduler.shutdown();
        }
        super.tearDown();
    }

    /**
     * Switch all mock objects to replay mode
     */
    private void replayMocks(Object... additionalMocks) {
        EasyMock.replay(mMockConfigFactory, mMockConfiguration, mMockInvocation);
        for (Object mock : additionalMocks) {
            EasyMock.replay(mock);
        }
    }

    /**
     * Verify all mock objects
     */
    private void verifyMocks(Object... additionalMocks) {
        EasyMock.verify(mMockConfigFactory, mMockConfiguration, mMockInvocation);
        for (Object mock : additionalMocks) {
            EasyMock.verify(mock);
        }
        mMockManager.assertDevicesFreed();
    }

    /**
     * Test {@link CommandScheduler#run()} when no configs have been added
     */
    public void testRun_empty() throws InterruptedException {
        mMockManager.setNumDevices(1);
        replayMocks();
        mScheduler.start();
        while (!mScheduler.isAlive()) {
            Thread.sleep(10);
        }
        mScheduler.shutdown();
        // expect run not to block
        mScheduler.join();
        verifyMocks();
    }

    /**
     * Test {@link CommandScheduler#addCommand(String[])} when help mode is specified
     */
    public void testAddConfig_configHelp() throws ConfigurationException {
        String[] args = new String[] {};
        mCommandOptions.setHelpMode(true);
        setCreateConfigExpectations(args, 1);
        // expect
        mMockConfigFactory.printHelpForConfig(EasyMock.aryEq(args), EasyMock.eq(true),
                EasyMock.eq(System.out));
        replayMocks();
        mScheduler.start();
        mScheduler.addCommand(args);
        verifyMocks();
    }

    /**
     * Test {@link CommandScheduler#addCommand(String[])} when json help mode is specified
     */
    public void testAddConfig_configJsonHelp() throws ConfigurationException, JSONException {
        String[] args = new String[] {};
        mCommandOptions.setJsonHelpMode(true);
        setCreateConfigExpectations(args, 1);
        // expect
        EasyMock.expect(mMockConfiguration.getJsonCommandUsage()).andReturn(new JSONArray());
        replayMocks();
        mScheduler.start();
        mScheduler.addCommand(args);
        verifyMocks();
    }

    /**
     * Test {@link CommandScheduler#run()} when one config has been added
     */
    public void testRun_oneConfig() throws Throwable {
        String[] args = new String[] {};
        mMockManager.setNumDevices(2);
        setCreateConfigExpectations(args, 1);
        setExpectedInvokeCalls(1);
        mMockConfiguration.validateOptions();
        replayMocks();
        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verifyMocks();
    }

    /**
     * Test {@link CommandScheduler#removeAllCommands()} for idle case, where command is waiting for
     * device.
     */
    public void testRemoveAllCommands() throws Throwable {
        String[] args = new String[] {};
        mMockManager.setNumDevices(0);
        setCreateConfigExpectations(args, 1);
        mMockConfiguration.validateOptions();
        replayMocks();
        mScheduler.start();
        mScheduler.addCommand(args);
        assertEquals(1, mScheduler.getAllCommandsSize());
        mScheduler.removeAllCommands();
        assertEquals(0, mScheduler.getAllCommandsSize());
        verifyMocks();
    }

    /**
     * Test {@link CommandScheduler#run()} when one config has been added in dry-run mode
     */
    public void testRun_dryRun() throws Throwable {
        String[] dryRunArgs = new String[] {"--dry-run"};
        mCommandOptions.setDryRunMode(true);
        mMockManager.setNumDevices(2);
        setCreateConfigExpectations(dryRunArgs, 1);

        // add a second command, to verify the first dry-run command did not get added
        String[] args2 = new String[] {};
        setCreateConfigExpectations(args2, 1);
        setExpectedInvokeCalls(1);
        mMockConfiguration.validateOptions();
        EasyMock.expectLastCall().times(2);

        replayMocks();
        mScheduler.start();
        assertFalse(mScheduler.addCommand(dryRunArgs));
        // the same config object is being used, so clear its state
        mCommandOptions.setDryRunMode(false);
        assertTrue(mScheduler.addCommand(args2));
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verifyMocks();
    }

    /**
     * Test simple case for {@link CommandScheduler#execCommand()}
     */
    public void testExecCommand() throws Throwable {
        String[] args = new String[] {
            "foo"
        };
        setCreateConfigExpectations(args, 1);
        setExpectedInvokeCalls(1);
        mMockConfiguration.validateOptions();

        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("serial");
        EasyMock.expect(mockDevice.getDeviceState()).andStubReturn(TestDeviceState.ONLINE);
        IScheduledInvocationListener mockListener = EasyMock
                .createMock(IScheduledInvocationListener.class);
        mockListener.invocationComplete(mockDevice, FreeDeviceState.AVAILABLE);
        replayMocks(mockDevice, mockListener);
        mScheduler.start();
        mScheduler.execCommand(mockListener, mockDevice, args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join(2*1000);
        verifyMocks(mockListener);
    }

    /**
     * Sets the number of expected
     * {@link ITestInvocation#invoke(ITestDevice, IConfiguration, IRescheduler)} calls
     *
     * @param times
     */
    private void setExpectedInvokeCalls(int times) throws Throwable {
        mMockInvocation.invoke((ITestDevice)EasyMock.anyObject(),
                (IConfiguration)EasyMock.anyObject(), (IRescheduler)EasyMock.anyObject(),
                (ITestInvocationListener)EasyMock.anyObject());
        EasyMock.expectLastCall().times(times);
    }

    /**
     * Sets up a object that will notify when the expected number of
     * {@link ITestInvocation#invoke(ITestDevice, IConfiguration, IRescheduler)} calls occurs
     *
     * @param times
     */
    private Object waitForExpectedInvokeCalls(final int times) throws Throwable {
        IAnswer<Object> blockResult = new IAnswer<Object>() {
            private int mCalls = 0;
            @Override
            public Object answer() throws Throwable {
                synchronized(this) {
                    mCalls++;
                    if (times == mCalls) {
                        notifyAll();
                    }
                }
                return null;
            }
        };
        mMockInvocation.invoke((ITestDevice)EasyMock.anyObject(),
                (IConfiguration)EasyMock.anyObject(), (IRescheduler)EasyMock.anyObject(),
                (ITestInvocationListener)EasyMock.anyObject());
        EasyMock.expectLastCall().andAnswer(blockResult);
        EasyMock.expectLastCall().andAnswer(blockResult);
        return blockResult;
    }

    /**
     * Test {@link CommandScheduler#run()} when one config has been added in a loop
     */
    public void testRun_oneConfigLoop() throws Throwable {
        String[] args = new String[] {};
        // track if exception occurs on scheduler thread
        UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        try {
            ExceptionTracker tracker = new ExceptionTracker();
            Thread.setDefaultUncaughtExceptionHandler(tracker);
            mMockManager.setNumDevices(1);
            // config should only be created three times
            setCreateConfigExpectations(args, 3);
            mCommandOptions.setLoopMode(true);
            mCommandOptions.setMinLoopTime(50);
            Object notifier = waitForExpectedInvokeCalls(2);
            mMockConfiguration.validateOptions();
            replayMocks();
            mScheduler.start();
            mScheduler.addCommand(args);
            synchronized (notifier) {
                notifier.wait(1 * 1000);
            }
            mScheduler.shutdown();
            mScheduler.join();
            verifyMocks();
            assertNull("exception occurred on background thread!", tracker.mThrowable);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(defaultHandler);
        }
    }

    class ExceptionTracker implements UncaughtExceptionHandler {

        private Throwable mThrowable = null;

        /**
         * {@inheritDoc}
         */
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            e.printStackTrace();
            mThrowable  = e;
        }
    }

    /**
     * Verify that scheduler goes into shutdown mode when a {@link FatalHostError} is thrown.
     */
    public void testRun_fatalError() throws Throwable {
        mMockInvocation.invoke((ITestDevice)EasyMock.anyObject(),
                (IConfiguration)EasyMock.anyObject(), (IRescheduler)EasyMock.anyObject(),
                (ITestInvocationListener)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(new FatalHostError("error"));
        // set up a mock global config and wtfhandler to handle CLog.wtf when FatalHostError occurs
        IGlobalConfiguration mockGc = EasyMock.createMock(IGlobalConfiguration.class);
        CLog.setGlobalConfigInstance(mockGc);
        try {
            ITerribleFailureHandler mockWtf = EasyMock.createMock(ITerribleFailureHandler.class);
            EasyMock.expect(mockGc.getWtfHandler()).andReturn(mockWtf).anyTimes();
            EasyMock.expect(mockWtf.onTerribleFailure((String)EasyMock.anyObject(),
                    (Throwable)EasyMock.anyObject())).andReturn(Boolean.TRUE);
            String[] args = new String[] {};
            mMockManager.setNumDevices(2);
            setCreateConfigExpectations(args, 1);
            mMockConfiguration.validateOptions();
            replayMocks(mockGc, mockWtf);
            mScheduler.start();
            mScheduler.addCommand(args);
            // no need to call shutdown explicitly - scheduler should shutdown by itself
            mScheduler.join(2*1000);
            verifyMocks(mockGc, mockWtf);
        } finally {
            // reset global config to null, which means 'not overloaded/use default'
            CLog.setGlobalConfigInstance(null);
        }
    }

    /**
     * Test{@link CommandScheduler#run()} when config is matched to a specific device serial number
     * <p/>
     * Adds two configs to run, and verify they both run on one device
     */
    public void testRun_configSerial() throws Throwable {
        String[] args = new String[] {};
        mMockManager.setNumDevices(2);
        setCreateConfigExpectations(args, 2);
        // allocate and free a device to get its serial
        ITestDevice dev = mMockManager.allocateDevice();
        mDeviceOptions.addSerial(dev.getSerialNumber());
        setExpectedInvokeCalls(1);
        mMockConfiguration.validateOptions();
        mMockConfiguration.validateOptions();
        replayMocks();
        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.addCommand(args);
        mMockManager.freeDevice(dev, FreeDeviceState.AVAILABLE);

        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verifyMocks();
    }

    /**
     * Test{@link CommandScheduler#run()} when config is matched to a exclude specific device serial
     * number.
     * <p/>
     * Adds two configs to run, and verify they both run on the other device
     */
    public void testRun_configExcludeSerial() throws Throwable {
        String[] args = new String[] {};
        mMockManager.setNumDevices(2);
        setCreateConfigExpectations(args, 2);
        // allocate and free a device to get its serial
        ITestDevice dev = mMockManager.allocateDevice();
        mDeviceOptions.addExcludeSerial(dev.getSerialNumber());
        ITestDevice expectedDevice = mMockManager.allocateDevice();
        setExpectedInvokeCalls(1);
        mMockConfiguration.validateOptions();
        mMockConfiguration.validateOptions();
        replayMocks();
        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.addCommand(args);
        mMockManager.freeDevice(dev, FreeDeviceState.AVAILABLE);
        mMockManager.freeDevice(expectedDevice, FreeDeviceState.AVAILABLE);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verifyMocks();
    }

    /**
     * Test {@link CommandScheduler#run()} when one config has been rescheduled
     */
    @SuppressWarnings("unchecked")
    public void testRun_rescheduled() throws Throwable {
        String[] args = new String[] {};
        mMockManager.setNumDevices(2);
        setCreateConfigExpectations(args, 1);
        mMockConfiguration.validateOptions();
        final IConfiguration rescheduledConfig = EasyMock.createMock(IConfiguration.class);
        EasyMock.expect(rescheduledConfig.getCommandOptions()).andStubReturn(mCommandOptions);
        EasyMock.expect(rescheduledConfig.getDeviceRequirements()).andStubReturn(
                mDeviceOptions);

        // an ITestInvocationn#invoke response for calling reschedule
        IAnswer<Object> rescheduleAndThrowAnswer = new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                IRescheduler rescheduler =  (IRescheduler) EasyMock.getCurrentArguments()[2];
                rescheduler.scheduleConfig(rescheduledConfig);
                throw new DeviceNotAvailableException("not avail");
            }
        };

        mMockInvocation.invoke(EasyMock.<ITestDevice>anyObject(),
                EasyMock.<IConfiguration>anyObject(), EasyMock.<IRescheduler>anyObject(),
                EasyMock.<ITestInvocationListener>anyObject());
        EasyMock.expectLastCall().andAnswer(rescheduleAndThrowAnswer);

        // expect one more success call
        setExpectedInvokeCalls(1);

        replayMocks(rescheduledConfig);
        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();

        EasyMock.verify(mMockConfigFactory, mMockConfiguration, mMockInvocation);
    }

    /**
     * Simple success case test for {@link CommandScheduler#addCommandFile(String, java.util.List)}
     * @throws ConfigurationException
     */
    public void testAddCommandFile() throws ConfigurationException {
        // set number of devices to 0 so we can verify command presence
        mMockManager.setNumDevices(0);
        List<String> extraArgs = Arrays.asList("--bar");
        setCreateConfigExpectations(new String[] {"foo", "--bar"}, 1);
        mMockConfiguration.validateOptions();
        final List<CommandLine> cmdFileContent = Arrays.asList(new CommandLine(
                Arrays.asList("foo"), null, 0));
        mMockCmdFileParser = new CommandFileParser() {
            @Override
            public List<CommandLine> parseFile(File cmdFile) {
                return cmdFileContent;
            }
        };
        replayMocks();

        mScheduler.start();
        mScheduler.addCommandFile("mycmd.txt", extraArgs);
        List<CommandTracker> cmds = mScheduler.getCommandTrackers();
        assertEquals(1, cmds.size());
        assertEquals("foo", cmds.get(0).getArgs()[0]);
        assertEquals("--bar", cmds.get(0).getArgs()[1]);
    }

    /**
     * Simple success case test for auto reloading a command file
     *
     * @throws ConfigurationException
     */
    public void testAddCommandFile_reload() throws ConfigurationException {
        // set number of devices to 0 so we can verify command presence
        mMockManager.setNumDevices(0);
        String[] addCommandArgs = new String[]{"fromcommand"};
        List<String> extraArgs = Arrays.asList("--bar");

        setCreateConfigExpectations(addCommandArgs, 1);
        String[] cmdFile1Args = new String[] {"fromFile1", "--bar"};
        setCreateConfigExpectations(cmdFile1Args, 1);
        String[] cmdFile2Args = new String[] {"fromFile2", "--bar"};
        setCreateConfigExpectations(cmdFile2Args, 1);

        mMockConfiguration.validateOptions();
        EasyMock.expectLastCall().times(3);

        final List<CommandLine> cmdFileContent1 = Arrays.asList(new CommandLine(
                Arrays.asList("fromFile1"), null, 0));
        final List<CommandLine> cmdFileContent2 = Arrays.asList(new CommandLine(
                Arrays.asList("fromFile2"), null, 0));
        mMockCmdFileParser = new CommandFileParser() {
            boolean firstCall = true;
            @Override
            public List<CommandLine> parseFile(File cmdFile) {
                if (firstCall) {
                    firstCall = false;
                    return cmdFileContent1;
                }
                return cmdFileContent2;
            }
        };
        replayMocks();
        mScheduler.start();
        mScheduler.setCommandFileReload(true);
        mScheduler.addCommand(addCommandArgs);
        mScheduler.addCommandFile("mycmd.txt", extraArgs);

        List<CommandTracker> cmds = mScheduler.getCommandTrackers();
        assertEquals(2, cmds.size());
        Collections.sort(cmds, new CommandTrackerIdComparator());
        Assert.assertArrayEquals(addCommandArgs, cmds.get(0).getArgs());
        Assert.assertArrayEquals(cmdFile1Args, cmds.get(1).getArgs());

        // now reload the command file
        mScheduler.notifyFileChanged(new File("mycmd.txt"), extraArgs);

        cmds = mScheduler.getCommandTrackers();
        assertEquals(2, cmds.size());
        Collections.sort(cmds, new CommandTrackerIdComparator());
        Assert.assertArrayEquals(addCommandArgs, cmds.get(0).getArgs());
        Assert.assertArrayEquals(cmdFile2Args, cmds.get(1).getArgs());
    }

    /**
     * Verify attempts to add the same commmand file in reload mode are rejected
     */
    public void testAddCommandFile_twice() throws ConfigurationException {
        // set number of devices to 0 so we can verify command presence
        mMockManager.setNumDevices(0);
        String[] cmdFile1Args = new String[] {"fromFile1"};
        setCreateConfigExpectations(cmdFile1Args, 1);
        setCreateConfigExpectations(cmdFile1Args, 1);
        mMockConfiguration.validateOptions();
        EasyMock.expectLastCall().times(2);

        final List<CommandLine> cmdFileContent1 = Arrays.asList(new CommandLine(
                Arrays.asList("fromFile1"), null, 0));
        mMockCmdFileParser = new CommandFileParser() {
            @Override
            public List<CommandLine> parseFile(File cmdFile) {
                return cmdFileContent1;
            }
        };
        replayMocks();
        mScheduler.start();
        mScheduler.setCommandFileReload(true);
        mScheduler.addCommandFile("mycmd.txt", Collections.<String>emptyList());

        List<CommandTracker> cmds = mScheduler.getCommandTrackers();
        assertEquals(1, cmds.size());
        Assert.assertArrayEquals(cmdFile1Args, cmds.get(0).getArgs());

        // now attempt to add the same command file
        mScheduler.addCommandFile("mycmd.txt", Collections.<String>emptyList());

        // expect reload
        // ensure same state as before
        cmds = mScheduler.getCommandTrackers();
        assertEquals(1, cmds.size());
        Assert.assertArrayEquals(cmdFile1Args, cmds.get(0).getArgs());
    }

    /**
     * Test {@link CommandScheduler#shutdown()} when no devices are available.
     */
    public void testShutdown() throws Exception {
        mMockManager.setNumDevices(0);
        mScheduler.start();
        while (!mScheduler.isAlive()) {
            Thread.sleep(10);
        }
        // hack - sleep a bit more to ensure allocateDevices is called
        Thread.sleep(50);
        mScheduler.shutdown();
        mScheduler.join();
        // test will hang if not successful
    }

    /**
     * Set EasyMock expectations for a create configuration call.
     */
    private void setCreateConfigExpectations(String[] args, int times)
            throws ConfigurationException {
        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs(EasyMock.aryEq(args)))
                .andReturn(mMockConfiguration)
                .times(times);
        EasyMock.expect(mMockConfiguration.getCommandOptions()).andStubReturn(mCommandOptions);
        EasyMock.expect(mMockConfiguration.getDeviceRequirements()).andStubReturn(
                mDeviceOptions);
    }
}
