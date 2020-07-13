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

import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.command.CommandFileParser.CommandLine;
import com.android.tradefed.command.CommandFileWatcher.ICommandFileListener;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.command.remote.IRemoteClient;
import com.android.tradefed.command.remote.RemoteClient;
import com.android.tradefed.command.remote.RemoteException;
import com.android.tradefed.command.remote.RemoteManager;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NoDeviceException;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.TableFormatter;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * A scheduler for running TradeFederation commands across all available devices.
 * <p/>
 * Will attempt to prioritize commands to run based on a total running count of their execution
 * time. e.g. infrequent or fast running commands will get prioritized over long running commands.
 * <p/>
 * Runs forever in background until shutdown.
 */
public class CommandScheduler extends Thread implements ICommandScheduler, ICommandFileListener {

    /** the queue of commands ready to be executed. */
    private List<ExecutableCommand> mReadyCommands;

    /** the queue of commands sleeping. */
    private Set<ExecutableCommand> mSleepingCommands;

    /** the queue of commands current executing. */
    private Set<ExecutableCommand> mExecutingCommands;

    /** map of device to active invocation threads */
    private Map<ITestDevice, InvocationThread> mInvocationThreadMap;

    /** timer for scheduling commands to be re-queued for execution */
    private ScheduledThreadPoolExecutor mCommandTimer;

    private IRemoteClient mRemoteClient = null;
    private RemoteManager mRemoteManager = null;

    private CommandFileWatcher mCommandFileWatcher = null;

    /** latch used to notify other threads that this thread is running */
    private final CountDownLatch mRunLatch;

    /** maximum time to wait for handover initiation to complete */
    private static final long MAX_HANDOVER_INIT_TIME = 2 * 60 * 1000;

    /** used to assign unique ids to each CommandTracker created */
    private int mCurrentCommandId = 0;

    /** flag for instructing scheduler to exit when no commands are present */
    private boolean mShutdownOnEmpty = false;

    private boolean mStarted = false;

    // flag to indicate this scheduler is currently handing over control to another remote TF
    private boolean mPerformingHandover = false;

    private WaitObj mHandoverHandshake = new WaitObj();

    private WaitObj mCommandProcessWait = new WaitObj();

    @Option(name = "reload-cmdfiles", description =
            "Whether to enable the command file autoreload mechanism")
    // FIXME: enable this to be enabled or disabled on a per-cmdfile basis
    private boolean mReloadCmdfiles = false;

    @Option(name = "max-poll-time", description =
            "ms between forced command scheduler execution time")
    private long mPollTime = 30 * 1000; // 30 seconds

    @Option(name = "shutdown-on-cmdfile-error", description =
            "terminate TF session if a configuration exception on command file occurs")
    private boolean mShutdownOnCmdfileError = false;

    private enum CommandState {
        WAITING_FOR_DEVICE("Wait_for_device"),
        EXECUTING("Executing"),
        SLEEPING("Sleeping");

        private String mDisplayName;

        CommandState(String displayName) {
            mDisplayName = displayName;
        }

        public String getDisplayName() {
            return mDisplayName;
        }
    }

    /**
     * Represents one active command added to the scheduler. Will track total execution time of all
     * instances of this command
     */
     static class CommandTracker {
        private final int mId;
        private final String[] mArgs;
        private final String mCommandFilePath;

        /** the total amount of time this command was executing. Used to prioritize */
        private long mTotalExecTime = 0;

        CommandTracker(int id, String[] args, String commandFilePath) {
            mId = id;
            mArgs = args;
            mCommandFilePath = commandFilePath;
        }

        synchronized void incrementExecTime(long execTime) {
            mTotalExecTime += execTime;
        }

        /**
         * @return the total amount of execution time for this command.
         */
        synchronized long getTotalExecTime() {
            return mTotalExecTime;
        }

        /**
         * Get the full list of config arguments associated with this command.
         */
        String[] getArgs() {
            return mArgs;
        }

        int getId() {
            return mId;
        }

        /**
         * Return the path of the file this command is associated with. null if not applicable
         * @return
         */
        String getCommandFilePath() {
            return mCommandFilePath;
        }
    }

    /**
     * Represents one instance of a command to be executed.
     */
    private class ExecutableCommand {
        private final CommandTracker mCmdTracker;
        private final IConfiguration mConfig;
        private final boolean mRescheduled;
        private final long mCreationTime;
        private Long mSleepTime;

        private ExecutableCommand(CommandTracker tracker, IConfiguration config,
                boolean rescheduled) {
            mConfig = config;
            mCmdTracker = tracker;
            mRescheduled = rescheduled;
            mCreationTime = System.currentTimeMillis();
        }

        /**
         * Gets the {@link IConfiguration} for this command instance
         */
        public IConfiguration getConfiguration() {
            return mConfig;
        }

        /**
         * Gets the associated {@link CommandTracker}.
         */
        CommandTracker getCommandTracker() {
            return mCmdTracker;
        }

        /**
         * Callback to inform listener that command has started execution.
         */
        void commandStarted() {
            mSleepTime = null;
        }

        public void commandFinished(long elapsedTime) {
            getCommandTracker().incrementExecTime(elapsedTime);
            CLog.d("removing exec command for id %d", getCommandTracker().getId());
            synchronized (CommandScheduler.this) {
                mExecutingCommands.remove(this);
            }
            if (isShuttingDown()) {
                mCommandProcessWait.signalEventReceived();
            }
        }

        public boolean isRescheduled() {
            return mRescheduled;
        }

        public long getCreationTime() {
            return mCreationTime;
        }

        public boolean isLoopMode() {
            return mConfig.getCommandOptions().isLoopMode();
        }

        public Long getSleepTime() {
            return mSleepTime;
        }

        public String getCommandFilePath() {
            return mCmdTracker.getCommandFilePath();
        }
    }

    /**
     * struct for a command and its state
     */
    private static class ExecutableCommandState {
        final ExecutableCommand cmd;
        final CommandState state;

        ExecutableCommandState(ExecutableCommand cmd, CommandState state) {
            this.cmd = cmd;
            this.state = state;
        }
    }

    /**
     * A {@link IRescheduler} that will add a command back to the queue.
     */
    private class Rescheduler implements IRescheduler {

        private CommandTracker mCmdTracker;

        Rescheduler(CommandTracker cmdTracker) {
            mCmdTracker = cmdTracker;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean scheduleConfig(IConfiguration config) {
            // force loop mode to off, otherwise each rescheduled config will be treated as
            // a new command and added back to queue
            config.getCommandOptions().setLoopMode(false);
            ExecutableCommand rescheduledCmd = createExecutableCommand(mCmdTracker, config, true);
            return addExecCommandToQueue(rescheduledCmd, 0);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean rescheduleCommand() {
            try {
                CLog.d("rescheduling for command %d", mCmdTracker.getId());
                IConfiguration config = getConfigFactory().createConfigurationFromArgs(
                        mCmdTracker.getArgs());
                ExecutableCommand execCmd = createExecutableCommand(mCmdTracker, config, true);
                return addExecCommandToQueue(execCmd, config.getCommandOptions().getLoopTime());
            } catch (ConfigurationException e) {
                // FIXME: do this with jline somehow for ANSI support
                // note: make sure not to log (aka record) this line, as (args) may contain
                // passwords.
                System.out.println(String.format("Error while processing args: %s",
                        Arrays.toString(mCmdTracker.getArgs())));
                System.out.println(e.getMessage());
                System.out.println();
                return false;
            }
        }
    }

    /**
     * Comparator for {@link ExecutableCommand}.
     * <p/>
     * Delegates to {@link CommandTrackerTimeComparator}.
     */
    private static class ExecutableCommandComparator implements Comparator<ExecutableCommand> {
        CommandTrackerTimeComparator mTrackerComparator = new CommandTrackerTimeComparator();

        /**
         * {@inheritDoc}
         */
        @Override
        public int compare(ExecutableCommand c1, ExecutableCommand c2) {
            return mTrackerComparator.compare(c1.getCommandTracker(), c2.getCommandTracker());
        }
    }

    /**
     * Comparator for {@link CommandTracker}.
     * <p/>
     * Compares by mTotalExecTime, prioritizing configs with lower execution time
     */
    private static class CommandTrackerTimeComparator implements Comparator<CommandTracker> {

        @Override
        public int compare(CommandTracker c1, CommandTracker c2) {
            if (c1.getTotalExecTime() == c2.getTotalExecTime()) {
                return 0;
            } else if (c1.getTotalExecTime() < c2.getTotalExecTime()) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    /**
     * Comparator for {@link CommandTracker}.
     * <p/>
     * Compares by id.
     */
    static class CommandTrackerIdComparator implements Comparator<CommandTracker> {

        @Override
        public int compare(CommandTracker c1, CommandTracker c2) {
            if (c1.getId() == c2.getId()) {
                return 0;
            } else if (c1.getId() < c2.getId()) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    /**
     * An {@link IScheduledInvocationListener} for locally scheduled commands added via addCommand.
     * <p/>
     * Returns device to device manager and remote handover server if applicable.
     */
    private class FreeDeviceHandler extends ResultForwarder implements
            IScheduledInvocationListener {

        private final IDeviceManager mDeviceManager;

        FreeDeviceHandler(IDeviceManager deviceManager,
                IScheduledInvocationListener... listeners) {
            super(listeners);
            mDeviceManager = deviceManager;
        }

        @Override
        public void invocationComplete(ITestDevice device, FreeDeviceState deviceState) {
            for (ITestInvocationListener listener : getListeners()) {
                ((IScheduledInvocationListener) listener).invocationComplete(device, deviceState);
            }

            mDeviceManager.freeDevice(device, deviceState);
            remoteFreeDevice(device);
        }
    }

    private class InvocationThread extends Thread {
        private final IScheduledInvocationListener[] mListeners;
        private final ITestDevice mDevice;
        private final ExecutableCommand mCmd;
        private final ITestInvocation mInvocation;
        private long mStartTime = -1;

        public InvocationThread(String name, ITestDevice device, ExecutableCommand command,
                IScheduledInvocationListener... listeners) {
            // create a thread group so LoggerRegistry can identify this as an invocationThread
            super(new ThreadGroup(name), name);
            mListeners = listeners;
            mDevice = device;
            mCmd = command;
            mInvocation = createRunInstance();
        }

        public long getStartTime() {
            return mStartTime;
        }

        @Override
        public void run() {
            FreeDeviceState deviceState = FreeDeviceState.AVAILABLE;
            mStartTime = System.currentTimeMillis();
            ITestInvocation instance = getInvocation();
            IConfiguration config = mCmd.getConfiguration();

            try {
                mCmd.commandStarted();
                instance.invoke(mDevice, config, new Rescheduler(mCmd.getCommandTracker()),
                        mListeners);
            } catch (DeviceUnresponsiveException e) {
                CLog.w("Device %s is unresponsive. Reason: %s", mDevice.getSerialNumber(),
                        e.getMessage());
                deviceState = FreeDeviceState.UNRESPONSIVE;
            } catch (DeviceNotAvailableException e) {
                CLog.w("Device %s is not available. Reason: %s", mDevice.getSerialNumber(),
                        e.getMessage());
                deviceState = FreeDeviceState.UNAVAILABLE;
            } catch (FatalHostError e) {
                CLog.wtf(String.format("Fatal error occurred: %s, shutting down", e.getMessage()),
                        e);
                shutdown();
            } catch (Throwable e) {
                CLog.e(e);
            } finally {
                long elapsedTime = System.currentTimeMillis() - mStartTime;
                CLog.i("Updating command %d with elapsed time %d ms",
                       mCmd.getCommandTracker().getId(), elapsedTime);
                // remove invocation thread first so another invocation can be started on device
                // when freed
                removeInvocationThread(this);
                if (!TestDeviceState.ONLINE.equals(mDevice.getDeviceState())) {
                    //If the device is offline at the end of the test
                    deviceState = FreeDeviceState.UNAVAILABLE;
                }
                for (final IScheduledInvocationListener listener : mListeners) {
                    listener.invocationComplete(mDevice, deviceState);
                }
                mCmd.commandFinished(elapsedTime);
            }
        }

        ITestInvocation getInvocation() {
            return mInvocation;
        }

        ITestDevice getDevice() {
            return mDevice;
        }

        /**
         * Stops a running invocation. {@link CommandScheduler#shutdownHard()} will stop
         * all running invocations.
         */
        public void stopInvocation(String message) {
            if (mDevice != null && mDevice.getIDevice().isOnline()) {
                // Kill all running processes on device.
                try {
                    mDevice.executeShellCommand("am kill-all");
                } catch (DeviceNotAvailableException e) {
                    CLog.w("failed to kill process on device %s: %s", mDevice.getSerialNumber(), e);
                }
            }
            RunUtil.getDefault().interrupt(this, message);
            super.interrupt();
        }

        /**
         * Checks whether the device battery level is above the required value to keep running the
         * invocation.
         */
        public void checkDeviceBatteryLevel() {
            if (mCmd.getConfiguration().getDeviceOptions() == null) {
                CLog.d("No deviceOptions in the configuration, cannot do Battery level check");
                return;
            }
            final Integer cutoffBattery = mCmd.getConfiguration().getDeviceOptions()
                    .getCutoffBattery();
            if (mDevice != null && cutoffBattery != null) {
                final IDevice device = mDevice.getIDevice();
                int batteryLevel = -1;
                try {
                    batteryLevel = device.getBattery(500, TimeUnit.MILLISECONDS).get();
                } catch (InterruptedException | ExecutionException e) {
                    // fall through
                }
                CLog.d("device %s: battey level=%d%%", device.getSerialNumber(), batteryLevel);
                // This logic is based on the assumption that batterLevel will be 0 or -1 if TF
                // fails to fetch a valid battery level or the device is not using a battery.
                if (0 < batteryLevel && batteryLevel < cutoffBattery) {
                    CLog.i("Stopping %s: battery too low (%d%% < %d%%)",
                            getName(), batteryLevel, cutoffBattery);
                    stopInvocation(String.format(
                            "battery too low (%d%% < %d%%)", batteryLevel, cutoffBattery));
                }
            }
        }
    }

    /**
     * A {@link IDeviceMonitor} that signals scheduler to process commands when an available device
     * is added.
     */
    private class AvailDeviceMonitor implements IDeviceMonitor {

        @Override
        public void run() {
           // ignore
        }

        @Override
        public void setDeviceLister(DeviceLister lister) {
            // ignore
        }

        @Override
        public void notifyDeviceStateChange(String serial, DeviceAllocationState oldState,
                DeviceAllocationState newState) {
            if (newState.equals(DeviceAllocationState.Available)) {
                // new avail device was added, wake up scheduler
                mCommandProcessWait.signalEventReceived();
            }
        }
    }

    /**
     * Creates a {@link CommandScheduler}.
     * <p />
     * Note: start must be called before use.
     */
    public CommandScheduler() {
        super("CommandScheduler");  // set the thread name
        mReadyCommands = new LinkedList<>();
        mSleepingCommands = new HashSet<>();
        mExecutingCommands = new HashSet<>();
        mInvocationThreadMap = new HashMap<ITestDevice, InvocationThread>();
        // use a ScheduledThreadPoolExecutorTimer as a single-threaded timer. This class
        // is used instead of a java.util.Timer because it offers advanced shutdown options
        mCommandTimer = new ScheduledThreadPoolExecutor(1);
        mRunLatch = new CountDownLatch(1);
    }

    /**
     * Starts the scheduler including setting up of logging, init of {@link DeviceManager} etc
     */
    @Override
    public void start() {
        synchronized (this) {
            if (mStarted) {
                throw new IllegalStateException("scheduler has already been started");
            }
            initLogging();

            initDeviceManager();

            mStarted = true;
        }
        super.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized CommandFileWatcher getCommandFileWatcher() {
        assertStarted();
        if (mCommandFileWatcher == null) {
            mCommandFileWatcher = new CommandFileWatcher(this);
            mCommandFileWatcher.start();
        }
        return mCommandFileWatcher;
    }

    /**
     * Initialize the device manager, optionally using a global device filter if specified.
     */
    void initDeviceManager() {
        getDeviceManager().init();
    }

    /**
     * Factory method for creating a {@link TestInvocation}.
     *
     * @return the {@link ITestInvocation} to use
     */
    ITestInvocation createRunInstance() {
        return new TestInvocation();
    }

    /**
     * Factory method for getting a reference to the {@link IDeviceManager}
     *
     * @return the {@link IDeviceManager} to use
     */
    IDeviceManager getDeviceManager() {
        return GlobalConfiguration.getDeviceManagerInstance();
    }

    /**
     * Factory method for getting a reference to the {@link IConfigurationFactory}
     *
     * @return the {@link IConfigurationFactory} to use
     */
    IConfigurationFactory getConfigFactory() {
        return ConfigurationFactory.getInstance();
    }

    /**
     * The main execution block of this thread.
     */
    @Override
    public void run() {
        assertStarted();
        try {
            IDeviceManager manager = getDeviceManager();

            startRemoteManager();

            // Notify other threads that we're running.
            mRunLatch.countDown();

            // add a listener that will wake up scheduler when a new avail device is added
            manager.addDeviceMonitor(new AvailDeviceMonitor());

            while (!isShutdown()) {
                // wait until processing is required again
                mCommandProcessWait.waitAndReset(mPollTime);
                checkInvocations();
                processReadyCommands(manager);
            }
            mCommandTimer.shutdown();
            CLog.i("Waiting for invocation threads to complete");
            List<InvocationThread> threadListCopy;
            synchronized (this) {
                threadListCopy = new ArrayList<InvocationThread>(mInvocationThreadMap.size());
                threadListCopy.addAll(mInvocationThreadMap.values());
            }
            for (Thread thread : threadListCopy) {
                waitForThread(thread);
            }
            closeRemoteClient();
            if (mRemoteManager != null) {
                mRemoteManager.cancelAndWait();
            }
            exit(manager);
            cleanUp();
            CLog.logAndDisplay(LogLevel.INFO, "All done");
        } finally {
            // Make sure that we don't quit with messages still in the buffers
            System.err.flush();
            System.out.flush();
        }
    }

    void checkInvocations() {
        CLog.d("Checking invocations...");
        final List<InvocationThread> copy;
        synchronized(this) {
            copy = new ArrayList<InvocationThread>(mInvocationThreadMap.values());
        }
        for (InvocationThread thread : copy) {
            thread.checkDeviceBatteryLevel();
        }
    }

    protected void processReadyCommands(IDeviceManager manager) {
        Map<ExecutableCommand, ITestDevice> scheduledCommandMap = new HashMap<>();
        // minimize length of synchronized block by just matching commands with device first,
        // then scheduling invocations/adding looping commands back to queue
        synchronized (this) {
            // sort ready commands by priority, so high priority commands are matched first
            Collections.sort(mReadyCommands, new ExecutableCommandComparator());
            Iterator<ExecutableCommand> cmdIter = mReadyCommands.iterator();
            while (cmdIter.hasNext()) {
                ExecutableCommand cmd = cmdIter.next();
                ITestDevice device = manager.allocateDevice(cmd.getConfiguration()
                        .getDeviceRequirements());
                if (device != null) {
                    cmdIter.remove();
                    mExecutingCommands.add(cmd);
                    // track command matched with device
                    scheduledCommandMap.put(cmd, device);
                }
            }
        }

        // now actually execute the commands
        for (Map.Entry<ExecutableCommand, ITestDevice> cmdDeviceEntry : scheduledCommandMap
                .entrySet()) {
            ExecutableCommand cmd = cmdDeviceEntry.getKey();
            startInvocation(cmdDeviceEntry.getValue(), cmd,
                    new FreeDeviceHandler(getDeviceManager()));
            if (cmd.isLoopMode()) {
                addNewExecCommandToQueue(cmd.getCommandTracker());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void await() throws InterruptedException {
        while (mRunLatch.getCount() > 0) {
            mRunLatch.await();
        }
    }

    private void closeRemoteClient() {
        if (mRemoteClient != null) {
            try {
                mRemoteClient.sendHandoverComplete();
                mRemoteClient.close();
            } catch (RemoteException e) {
                CLog.e(e);
            }
        }
    }

    private void waitForThread(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            // ignore
            waitForThread(thread);
        }
    }

    private void exit(IDeviceManager manager) {
        if (manager != null) {
            manager.terminate();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addCommand(String[] args) throws ConfigurationException {
        return addCommand(args, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addCommand(String[] args, long totalExecTime) throws ConfigurationException {
        return internalAddCommand(args, totalExecTime, null);
    }

    private boolean internalAddCommand(String[] args, long totalExecTime, String cmdFilePath)
            throws ConfigurationException {
        assertStarted();
        IConfiguration config = getConfigFactory().createConfigurationFromArgs(args);
        if (config.getCommandOptions().isHelpMode()) {
            getConfigFactory().printHelpForConfig(args, true, System.out);
        } else if (config.getCommandOptions().isFullHelpMode()) {
            getConfigFactory().printHelpForConfig(args, false, System.out);
        } else if (config.getCommandOptions().isJsonHelpMode()) {
            try {
                // Convert the JSON usage to a string (with 4 space indentation) and print to stdout
                System.out.println(config.getJsonCommandUsage().toString(4));
            } catch (JSONException e) {
                CLog.logAndDisplay(LogLevel.ERROR, "Failed to get json command usage: %s", e);
            }
        } else if (config.getCommandOptions().isDryRunMode()) {
            config.validateOptions();
            String cmdLine = QuotationAwareTokenizer.combineTokens(args);
            CLog.d("Dry run mode; skipping adding command: %s", cmdLine);
            if (config.getCommandOptions().isNoisyDryRunMode()) {
                System.out.println(cmdLine.replace("--noisy-dry-run", ""));
                System.out.println("");
            }
        } else {
            config.validateOptions();

            if (config.getCommandOptions().runOnAllDevices()) {
                addCommandForAllDevices(totalExecTime, args, cmdFilePath);
            } else {
                CommandTracker cmdTracker = createCommandTracker(args, cmdFilePath);
                cmdTracker.incrementExecTime(totalExecTime);
                ExecutableCommand cmdInstance = createExecutableCommand(cmdTracker, config, false);
                addExecCommandToQueue(cmdInstance, 0);
            }
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addCommandFile(String cmdFilePath, List<String> extraArgs)
            throws ConfigurationException {
        // verify we aren't already watching this command file, don't want to add it twice!
        File cmdFile = new File(cmdFilePath);
        if (mReloadCmdfiles && getCommandFileWatcher().isFileWatched(cmdFile)) {
            CLog.logAndDisplay(LogLevel.INFO,
                    "cmd file %s is already running and being watched for changes. Reloading",
                    cmdFilePath);
            removeCommandsFromFile(cmdFile);
        }
        internalAddCommandFile(cmdFile, extraArgs);
    }

    /**
     * Adds a command file without verifying if its already being watched
     */
    private void internalAddCommandFile(File cmdFile, List<String> extraArgs)
            throws ConfigurationException {
        try {
            CommandFileParser parser = createCommandFileParser();

            List<CommandLine> commands = parser.parseFile(cmdFile);
            if (mReloadCmdfiles) {
                // note always should re-register for command file, even if already listening,
                // since the dependent file list might have changed
                getCommandFileWatcher().addCmdFile(cmdFile, extraArgs, parser.getIncludedFiles());
            }
            for (CommandLine command : commands) {
                command.addAll(extraArgs);
                String[] arrayCommand = command.asArray();
                final String prettyCmdLine = QuotationAwareTokenizer.combineTokens(arrayCommand);
                CLog.d("Adding command %s", prettyCmdLine);

                try {
                    internalAddCommand(arrayCommand, 0, cmdFile.getAbsolutePath());
                } catch (ConfigurationException e) {
                    throw new ConfigurationException(String.format(
                            "Failed to add command '%s': %s", prettyCmdLine, e.getMessage()), e);
                }
            }
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read file " + cmdFile.getAbsolutePath(), e);
        }
    }

    /**
     * Factory method for creating a {@link CommandFileParser}.
     * <p/>
     * Exposed for unit testing.
     */
    CommandFileParser createCommandFileParser() {
        return new CommandFileParser();
    }

    /**
     * Creates a new command for each connected device, and adds each to the queue.
     * <p/>
     * Note this won't have the desired effect if user has specified other
     * conflicting {@link IConfiguration#getDeviceRequirements()}in the command.
     */
    private void addCommandForAllDevices(long totalExecTime, String[] args, String cmdFilePath)
            throws ConfigurationException {
        List<DeviceDescriptor> deviceDescs = getDeviceManager().listAllDevices();

        for (DeviceDescriptor deviceDesc : deviceDescs) {
            if (!deviceDesc.isStubDevice()) {
                String device = deviceDesc.getSerial();
                String[] argsWithDevice = Arrays.copyOf(args, args.length + 2);
                argsWithDevice[argsWithDevice.length - 2] = "-s";
                argsWithDevice[argsWithDevice.length - 1] = device;
                CommandTracker cmdTracker = createCommandTracker(argsWithDevice, cmdFilePath);
                cmdTracker.incrementExecTime(totalExecTime);
                IConfiguration config = getConfigFactory().createConfigurationFromArgs(
                        cmdTracker.getArgs());
                CLog.logAndDisplay(LogLevel.INFO, "Scheduling '%s' on '%s'", cmdTracker.getArgs()[0],
                        device);
                config.getDeviceRequirements().setSerial(device);
                ExecutableCommand execCmd = createExecutableCommand(cmdTracker, config, false);
                addExecCommandToQueue(execCmd, 0);
            }
        }
    }

    /**
     * Creates a new {@link CommandTracker} with a unique id.
     */
    private synchronized CommandTracker createCommandTracker(String[] args,
            String commandFilePath) {
        mCurrentCommandId++;
        CLog.d("Creating command tracker id %d for command args: '%s'", mCurrentCommandId,
                ArrayUtil.join(" ", (Object[])args));
        return new CommandTracker(mCurrentCommandId, args, commandFilePath);
    }

    /**
     * Creates a new {@link ExecutableCommand}.
     */
    private ExecutableCommand createExecutableCommand(CommandTracker cmdTracker,
            IConfiguration config, boolean rescheduled) {
        ExecutableCommand cmd = new ExecutableCommand(cmdTracker, config, rescheduled);
        CLog.d("creating exec command for id %d", cmdTracker.getId());
        return cmd;
    }

    /**
     * Creates a new {@link ExecutableCommand}, and adds it to queue
     *
     * @param commandTracker
     */
    private void addNewExecCommandToQueue(CommandTracker commandTracker) {
        try {
            IConfiguration config = getConfigFactory().createConfigurationFromArgs(
                    commandTracker.getArgs());
            ExecutableCommand execCmd = createExecutableCommand(commandTracker, config, false);
            addExecCommandToQueue(execCmd, config.getCommandOptions().getLoopTime());
        } catch (ConfigurationException e) {
            CLog.e(e);
        }
    }

    /**
     * Adds executable command instance to queue, with optional delay.
     *
     * @param cmd the {@link ExecutableCommand} to return to queue
     * @param delayTime the time in ms to delay before adding command to queue
     * @return <code>true</code> if command will be added to queue, <code>false</code> otherwise
     */
    private synchronized boolean addExecCommandToQueue(final ExecutableCommand cmd,
            long delayTime) {
        if (isShutdown()) {
            return false;
        }
        if (delayTime > 0) {
            mSleepingCommands.add(cmd);
            // delay before making command active
            Runnable delayCommand = new Runnable() {
                @Override
                public void run() {
                    synchronized (CommandScheduler.this) {
                        if (mSleepingCommands.remove(cmd)) {
                            mReadyCommands.add(cmd);
                            mCommandProcessWait.signalEventReceived();
                        }
                    }
                }
            };
            mCommandTimer.schedule(delayCommand, delayTime, TimeUnit.MILLISECONDS);
        } else {
            mReadyCommands.add(cmd);
            mCommandProcessWait.signalEventReceived();
        }
        return true;
    }

    /**
     * Helper method to return an array of {@link String} elements as a readable {@link String}
     *
     * @param args the {@link String}[] to use
     * @return a display friendly {@link String} of args contents
     */
    private String getArgString(String[] args) {
        return ArrayUtil.join(" ", (Object[])args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execCommand(IScheduledInvocationListener listener, String[] args)
            throws ConfigurationException, NoDeviceException {
        assertStarted();
        IDeviceManager manager = getDeviceManager();
        CommandTracker cmdTracker = createCommandTracker(args, null);
        IConfiguration config = getConfigFactory().createConfigurationFromArgs(
                cmdTracker.getArgs());
        config.validateOptions();

        ExecutableCommand execCmd = createExecutableCommand(cmdTracker, config, false);
        ITestDevice device;

        synchronized(this) {
            device = manager.allocateDevice(config.getDeviceRequirements());
            if (device == null) {
                throw new NoDeviceException("no device is available for command: " + args);
            }
            CLog.i("Executing '%s' on '%s'", cmdTracker.getArgs()[0], device.getSerialNumber());
            mExecutingCommands.add(execCmd);
        }

        startInvocation(device, execCmd, listener, new FreeDeviceHandler(manager));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execCommand(IScheduledInvocationListener listener, ITestDevice device, String[] args)
            throws ConfigurationException {
        assertStarted();
        CommandTracker cmdTracker = createCommandTracker(args, null);
        IConfiguration config = getConfigFactory().createConfigurationFromArgs(
                cmdTracker.getArgs());
        config.validateOptions();
        CLog.i("Executing '%s' on '%s'", cmdTracker.getArgs()[0], device.getSerialNumber());
        ExecutableCommand execCmd = createExecutableCommand(cmdTracker, config, false);

        synchronized(this) {
            mExecutingCommands.add(execCmd);
        }

        startInvocation(device, execCmd, listener);
    }

    /**
     * Spawns off thread to run invocation for given device.
     *
     * @param device the {@link ITestDevice}
     * @param cmd the {@link ExecutableCommand} to execute
     * @param listeners the {@link IScheduledInvocationLister}s to invoke when complete
     * @return the thread that will run the invocation
     */
    private void startInvocation(ITestDevice device, ExecutableCommand cmd,
            IScheduledInvocationListener... listeners) {
        if (hasInvocationThread(device)) {
            throw new IllegalStateException(
                    String.format("Attempting invocation on device %s when one is already running",
                            device.getSerialNumber()));
        }
        CLog.d("starting invocation for command id %d", cmd.getCommandTracker().getId());
        final String invocationName = String.format("Invocation-%s", device.getSerialNumber());
        InvocationThread invocationThread = new InvocationThread(invocationName, device, cmd,
                listeners);
        invocationThread.start();
        addInvocationThread(invocationThread);
    }

    /**
     * Removes a {@link InvocationThread} from the active list.
     */
    private synchronized void removeInvocationThread(InvocationThread invThread) {
        mInvocationThreadMap.remove(invThread.getDevice());
    }

    private synchronized boolean hasInvocationThread(ITestDevice device) {
        return mInvocationThreadMap.containsKey(device);
    }

    /**
     * Adds a {@link InvocationThread} to the active list.
     */
    private synchronized void addInvocationThread(InvocationThread invThread) {
        mInvocationThreadMap.put(invThread.getDevice(), invThread);
    }

    protected synchronized boolean isShutdown() {
        return mCommandTimer.isShutdown() || (mShutdownOnEmpty && getAllCommandsSize() == 0);
    }

    protected synchronized boolean isShuttingDown() {
        return mCommandTimer.isShutdown() || mShutdownOnEmpty;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void shutdown() {
        assertStarted();
        if (!isShuttingDown()) {
            CLog.d("initiating shutdown");
            removeAllCommands();
            if (mReloadCmdfiles) {
                mCommandFileWatcher.cancel();
            }
            if (mCommandTimer != null) {
                mCommandTimer.shutdownNow();
            }
            mCommandProcessWait.signalEventReceived();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void shutdownOnEmpty() {
        assertStarted();
        if (!isShuttingDown()) {
            CLog.d("initiating shutdown on empty");
            mShutdownOnEmpty = true;
            mCommandProcessWait.signalEventReceived();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void removeAllCommands() {
        assertStarted();
        CLog.d("removing all commands");
        if (mReloadCmdfiles) {
            getCommandFileWatcher().removeAllFiles();
        }
        if (mCommandTimer != null) {
            for (Runnable task : mCommandTimer.getQueue()) {
                mCommandTimer.remove(task);
            }
        }
        mReadyCommands.clear();
        mSleepingCommands.clear();
        if (isShuttingDown()) {
            mCommandProcessWait.signalEventReceived();
        }
    }

    /**
     * Remove commands originally added via the given command file
     * @param cmdFile
     */
    private synchronized void removeCommandsFromFile(File cmdFile) {
        Iterator<ExecutableCommand> cmdIter = mReadyCommands.iterator();
        while (cmdIter.hasNext()) {
            ExecutableCommand cmd = cmdIter.next();
            String path = cmd.getCommandFilePath();
            if (path != null &&
                    path.equals(cmdFile.getAbsolutePath())) {
                cmdIter.remove();
            }
        }
        cmdIter = mSleepingCommands.iterator();
        while (cmdIter.hasNext()) {
            ExecutableCommand cmd = cmdIter.next();
            String path = cmd.getCommandFilePath();
            if (path != null &&
                    path.equals(cmdFile.getAbsolutePath())) {
                cmdIter.remove();
            }
        }
        if (isShuttingDown()) {
            mCommandProcessWait.signalEventReceived();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean handoverShutdown(int handoverPort) {
        assertStarted();
        if (mRemoteClient != null || mPerformingHandover) {
            CLog.e("A handover has already been initiated");
            return false;
        }
        mPerformingHandover = true;
        try {
            mRemoteClient = RemoteClient.connect(handoverPort);
            CLog.d("Connected to remote manager at %d", handoverPort);
            handoverDevices(mRemoteClient);
            handoverCommands(mRemoteClient);
            mRemoteClient.sendHandoverInitComplete();
            shutdown();
            return true;
        } catch (RemoteException e) {
            CLog.e(e);
            // TODO: reset state and recover
        }
        return false;
    }

    /**
     * Informs remote manager of the devices we are still using
     */
    private void handoverDevices(IRemoteClient client) throws RemoteException {
        for (DeviceDescriptor deviceDesc : getDeviceManager().listAllDevices()) {
            if (deviceDesc.getState() == DeviceAllocationState.Allocated) {
                client.sendAllocateDevice(deviceDesc.getSerial());
                CLog.d("Sent filter device %s command", deviceDesc.getSerial());
            }
        }
    }

    /**
     * Pass the set of remote commands in use to remote client
     */
    private void handoverCommands(IRemoteClient client) throws RemoteException {
        // now send command info
        List<CommandTracker> cmdCopy = getCommandTrackers();
        // send commands as files if appropriate to do so
        handoverCmdFiles(client, cmdCopy);

        // now send remaining commands
        for (CommandTracker cmd : cmdCopy) {
            client.sendAddCommand(cmd.getTotalExecTime(), cmd.mArgs);
        }
    }

    /**
     * If appropriate, send remote commands in use as command files
     *
     * @param client
     * @param cmdCopy the list of commands. Will remove commands from this list as they are sent.
     * @throws RemoteException
     */
    private void handoverCmdFiles(IRemoteClient client, List<CommandTracker> cmdCopy)
            throws RemoteException {
        if (mReloadCmdfiles) {
            // keep track of files we've sent
            Set<String> cmdFilesSent = new HashSet<>();
            // only want to send commands in file form if reload is on, because otherwise
            // it is not guaranteed that commands currently running are same as in file
            Iterator<CommandTracker> cmdIter = cmdCopy.iterator();
            while (cmdIter.hasNext()) {
                CommandTracker cmd = cmdIter.next();
                String cmdPath = cmd.getCommandFilePath();
                if (cmdPath != null) {
                    cmdIter.remove();
                    if (!cmdFilesSent.contains(cmdPath)) {
                        List<String> extraArgs = getCommandFileWatcher().getExtraArgsForFile(
                                cmdPath);
                        client.sendAddCommandFile(cmdPath, extraArgs);
                        cmdFilesSent.add(cmdPath);
                    }
                }
            }
        }
    }

    /**
     * @return the list of active {@link CommandTracker}. 'Active' here means all commands added
     * to the scheduler that are either executing, waiting for a device to execute on, or looping.
     */
    List<CommandTracker> getCommandTrackers() {
        List<ExecutableCommandState> cmdCopy = getAllCommands();
        Set<CommandTracker> cmdTrackers = new LinkedHashSet<CommandTracker>();
        for (ExecutableCommandState cmdState : cmdCopy) {
            cmdTrackers.add(cmdState.cmd.getCommandTracker());
        }
        return new ArrayList<CommandTracker>(cmdTrackers);
    }

    /**
     * Inform the remote listener of the freed device. Has no effect if there is no remote listener.
     *
     * @param device the freed {@link ITestDevice}
     */
    private void remoteFreeDevice(ITestDevice device) {
        // TODO: send freed device state too
        if (mPerformingHandover && mRemoteClient != null) {
            try {
                mRemoteClient.sendFreeDevice(device.getSerialNumber());
            } catch (RemoteException e) {
                CLog.e("Failed to send unfilter device %s to remote manager",
                        device.getSerialNumber());
                CLog.e(e);
                // TODO: send handover failed op?
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void shutdownHard() {
        shutdown();

        CLog.logAndDisplay(LogLevel.WARN, "Stopping invocation threads...");
        for (InvocationThread thread : mInvocationThreadMap.values()) {
            thread.stopInvocation("TF is shutting down");
        }
        getDeviceManager().terminateHard();
    }

    /**
     * Initializes the ddmlib log.
     * <p />
     * Exposed so unit tests can mock.
     */
    @SuppressWarnings("deprecation")
    void initLogging() {
        DdmPreferences.setLogLevel(LogLevel.VERBOSE.getStringValue());
        Log.setLogOutput(LogRegistry.getLogRegistry());
    }

    /**
     * Closes the logs and does any other necessary cleanup before we quit.
     * <p />
     * Exposed so unit tests can mock.
     */
    void cleanUp() {
        LogRegistry.getLogRegistry().closeAndRemoveAllLogs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void displayInvocationsInfo(PrintWriter printWriter) {
        assertStarted();
        if (mInvocationThreadMap == null || mInvocationThreadMap.size() == 0) {
            return;
        }
        List<InvocationThread> copy = new ArrayList<InvocationThread>(mInvocationThreadMap.values());
        ArrayList<List<String>> displayRows = new ArrayList<List<String>>();
        displayRows.add(Arrays.asList("Command Id", "Exec Time", "Device", "State"));
        long curTime = System.currentTimeMillis();

        for (InvocationThread invThread : copy) {
            displayRows.add(Arrays.asList(
                    Integer.toString(invThread.mCmd.getCommandTracker().getId()),
                    getTimeString(curTime - invThread.getStartTime()),
                    invThread.getDevice().getSerialNumber(),
                    invThread.getInvocation().toString()));
        }
        new TableFormatter().displayTable(displayRows, printWriter);
    }

    private String getTimeString(long elapsedTime) {
        long duration = elapsedTime / 1000;
        long secs = duration % 60;
        long mins = (duration / 60) % 60;
        long hrs = duration / (60 * 60);
        String time = "unknown";
        if (hrs > 0) {
            time = String.format("%dh:%02d:%02d", hrs, mins, secs);
        } else {
            time = String.format("%dm:%02d", mins, secs);
        }
        return time;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean stopInvocation(ITestInvocation invocation) {
        for (InvocationThread thread : mInvocationThreadMap.values()) {
            if (thread.getInvocation() == invocation) {
                thread.interrupt();
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void displayCommandsInfo(PrintWriter printWriter, String regex) {
        assertStarted();
        Pattern regexPattern = null;
        if (regex != null) {
            regexPattern = Pattern.compile(regex);
        }

        List<CommandTracker> cmds = getCommandTrackers();
        Collections.sort(cmds, new CommandTrackerIdComparator());
        for (CommandTracker cmd : cmds) {
            String argString = getArgString(cmd.getArgs());
            if (regexPattern == null || regexPattern.matcher(argString).find()) {
                String cmdDesc = String.format("Command %d: [%s] %s", cmd.getId(),
                        getTimeString(cmd.getTotalExecTime()), argString);
                printWriter.println(cmdDesc);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dumpCommandsXml(PrintWriter printWriter, String regex) {
        assertStarted();
        Pattern regexPattern = null;
        if (regex != null) {
            regexPattern = Pattern.compile(regex);
        }

        List<ExecutableCommandState> cmdCopy = getAllCommands();
        for (ExecutableCommandState cmd : cmdCopy) {
            String[] args = cmd.cmd.getCommandTracker().getArgs();
            String argString = getArgString(args);
            if (regexPattern == null || regexPattern.matcher(argString).find()) {
                // Use the config name prefixed by config__ for the file path
                String xmlPrefix = "config__" + args[0].replace("/", "__") + "__";

                // If the command line contains --template:map test config, use that config for the
                // file path.  This is because in the template system, many tests will have same
                // base config and the distinguishing feature is the test included.
                boolean templateIncludeFound = false;
                boolean testFound = false;
                for (String arg : args) {
                    if ("--template:map".equals(arg)) {
                        templateIncludeFound = true;
                    } else if (templateIncludeFound && "test".equals(arg)) {
                        testFound = true;
                    } else {
                        if (templateIncludeFound && testFound) {
                            xmlPrefix = "config__" + arg.replace("/", "__") + "__";
                        }
                        templateIncludeFound = false;
                        testFound = false;
                    }
                }

                try {
                    File xmlFile = FileUtil.createTempFile(xmlPrefix, ".xml");
                    PrintWriter writer = new PrintWriter(xmlFile);
                    cmd.cmd.getConfiguration().dumpXml(writer);
                    printWriter.println(String.format("Saved command dump to %s",
                            xmlFile.getAbsolutePath()));
                } catch (IOException e) {
                    // Log exception and continue
                    CLog.e("Could not dump config xml");
                    CLog.e(e);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void displayCommandQueue(PrintWriter printWriter) {
        assertStarted();
        List<ExecutableCommandState> cmdCopy = getAllCommands();
        if (cmdCopy.size() == 0) {
            return;
        }
        ArrayList<List<String>> displayRows = new ArrayList<List<String>>();
        displayRows.add(Arrays.asList("Id", "Config", "Created", "Exec time", "State", "Sleep time",
                "Rescheduled", "Loop"));
        long curTime = System.currentTimeMillis();
        for (ExecutableCommandState cmd : cmdCopy) {
            dumpCommand(curTime, cmd, displayRows);
        }
        new TableFormatter().displayTable(displayRows, printWriter);
    }

    private void dumpCommand(long curTime, ExecutableCommandState cmdAndState,
            ArrayList<List<String>> displayRows) {
        ExecutableCommand cmd = cmdAndState.cmd;
        String sleepTime = cmd.getSleepTime() == null ? "N/A" : getTimeString(cmd.getSleepTime());
        displayRows.add(Arrays.asList(
                Integer.toString(cmd.getCommandTracker().getId()),
                cmd.getCommandTracker().getArgs()[0],
                getTimeString(curTime - cmd.getCreationTime()),
                getTimeString(cmd.mCmdTracker.getTotalExecTime()),
                cmdAndState.state.getDisplayName(),
                sleepTime,
                Boolean.toString(cmd.isRescheduled()),
                Boolean.toString(cmd.isLoopMode())));
    }

    /**
     * Starts remote manager to listen to remote commands.
     * <p/>
     * TODO: refactor to throw exception on failure
     */
    private void startRemoteManager() {
        if (mRemoteManager != null && !mRemoteManager.isCanceled()) {
            String error = String.format("A remote manager is already running at port %d",
                    mRemoteManager.getPort());
            throw new IllegalStateException(error);
        }
        mRemoteManager = new RemoteManager(getDeviceManager(), this);
        // Read the args that were set by the global config.
        boolean startRmtMgrOnBoot = mRemoteManager.getStartRemoteMgrOnBoot();
        int defaultRmtMgrPort = mRemoteManager.getRemoteManagerPort();
        boolean autoHandover = mRemoteManager.getAutoHandover();

        if (!startRmtMgrOnBoot) {
            mRemoteManager = null;
            return;
        }
        if (mRemoteManager.connect()) {
            mRemoteManager.start();
            CLog.logAndDisplay(LogLevel.INFO, "Started remote manager at port %d",
                    mRemoteManager.getPort());
            return;
        }
        CLog.logAndDisplay(LogLevel.INFO, "Failed to start remote manager at port %d",
                defaultRmtMgrPort);
        if (!autoHandover) {
           if (mRemoteManager.connectAnyPort()) {
               mRemoteManager.start();
               CLog.logAndDisplay(LogLevel.INFO,
                       "Started remote manager at port %d with no handover",
                       mRemoteManager.getPort());
               return;
           } else {
               CLog.logAndDisplay(LogLevel.ERROR, "Failed to auto start a remote manager on boot.");
               return;
           }
        }
        try {
            CLog.logAndDisplay(LogLevel.INFO, "Initiating handover with remote TF instance!");
            mHandoverHandshake.reset();
            initiateHandover(defaultRmtMgrPort);
            waitForHandoverHandshake();
            CLog.logAndDisplay(LogLevel.INFO, "Handover initiation complete.");
        } catch (RemoteException e) {
            CLog.e(e);
        }
    }

    private void waitForHandoverHandshake() {
        // block and wait to receive all the commands and 'device still in use' messages from remote
        mHandoverHandshake.waitForEvent(MAX_HANDOVER_INIT_TIME);
        // TODO: throw exception if not received
    }

    /**
     * Helper object for allowing multiple threads to synchronize on an event.
     * <p/>
     * Basically a modest wrapper around Object's wait and notify methods, that supports
     * remembering if a notify call was made.
     */
    private static class WaitObj {
        boolean mEventReceived = false;

        /**
         * Wait for signal for a max of given ms.
         * @return true if event received before time elapsed, false otherwise
         */
        public synchronized boolean waitForEvent(long maxWaitTime) {
            if (maxWaitTime == 0) {
                return waitForEvent();
            }
            long startTime = System.currentTimeMillis();
            long remainingTime = maxWaitTime;
            while (!mEventReceived && remainingTime > 0) {
                try {
                    wait(remainingTime);
                } catch (InterruptedException e) {
                    CLog.w("interrupted");
                }
                remainingTime = maxWaitTime - (System.currentTimeMillis() - startTime);
            }
            return mEventReceived;
        }

        /**
         * Wait for signal indefinitely or until interrupted.
         * @return true if event received, false otherwise
         */
        public synchronized boolean waitForEvent() {
            if (!mEventReceived) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    CLog.w("interrupted");
                }
            }
            return mEventReceived;
        }

        /**
         * Reset the event received flag.
         */
        public synchronized void reset() {
            mEventReceived = false;
        }

        /**
         * Wait for given ms for event to be received, and reset state back to 'no event received'
         * upon completion.
         */
        public synchronized void waitAndReset(long maxWaitTime) {
            waitForEvent(maxWaitTime);
            reset();
        }

        /**
         * Notify listeners that event was received.
         */
        public synchronized void signalEventReceived() {
            mEventReceived = true;
            notifyAll();
        }
    }

    @Override
    public void handoverInitiationComplete() {
        mHandoverHandshake.signalEventReceived();
    }

    @Override
    public void completeHandover() {
        CLog.logAndDisplay(LogLevel.INFO, "Completing handover.");
        if (mRemoteClient != null) {
            mRemoteClient.close();
            mRemoteClient = null;
        } else {
            CLog.e("invalid state: received handover complete when remote client is null");
        }

        if (mRemoteManager != null) {
            mRemoteManager.cancelAndWait();
            mRemoteManager = null;
        } else {
            CLog.e("invalid state: received handover complete when remote manager is null");
        }

        // Start a new remote manager and attempt to capture the original default port.
        mRemoteManager = new RemoteManager(getDeviceManager(), this);
        boolean success = false;
        for (int i=0; i < 10 && !success; i++) {
            try {
                sleep(2000);
            } catch (InterruptedException e) {
                CLog.e(e);
                return;
            }
            success = mRemoteManager.connect();
        }
        if (!success) {
            CLog.e("failed to connect to default remote manager port");
            return;
        }

        mRemoteManager.start();
        CLog.logAndDisplay(LogLevel.INFO,
                "Successfully started remote manager after handover on port %d",
                mRemoteManager.getPort());
    }

    private void initiateHandover(int port) throws RemoteException {
        mRemoteClient = RemoteClient.connect(port);
        CLog.i("Connecting local client with existing remote TF at %d - Attempting takeover", port);
        // Start up a temporary local remote manager for handover.
        if (mRemoteManager.connectAnyPort()) {
            mRemoteManager.start();
            CLog.logAndDisplay(LogLevel.INFO,
                    "Started local tmp remote manager for handover at port %d",
                    mRemoteManager.getPort());
            mRemoteClient.sendStartHandover(mRemoteManager.getPort());
        }
    }

    private synchronized void assertStarted() {
        if(!mStarted) {
            throw new IllegalStateException("start() must be called before this method");
        }
    }

    @Override
    public void notifyFileChanged(File cmdFile, List<String> extraArgs) {
        CLog.logAndDisplay(LogLevel.INFO, "Detected update for cmdfile '%s'. Reloading",
                cmdFile.getAbsolutePath());
        removeCommandsFromFile(cmdFile);
        try {
            // just add the file again, including re-registering for command file watcher
            // don't want to remove the registration here in case file fails to load
            internalAddCommandFile(cmdFile, extraArgs);
        } catch (ConfigurationException e) {
            CLog.wtf(String.format("Failed to automatically reload cmdfile %s",
                    cmdFile.getAbsolutePath()), e);
        }
    }

    /**
     * Set the command file reloading flag
     *
     * @VisibleForTesting
     */
    void setCommandFileReload(boolean b) {
        mReloadCmdfiles = b;
    }

    synchronized int getAllCommandsSize() {
        return mReadyCommands.size() + mExecutingCommands.size() + mSleepingCommands.size();
    }

    synchronized List<ExecutableCommandState> getAllCommands() {
        List<ExecutableCommandState> cmds = new ArrayList<>(getAllCommandsSize());
        for (ExecutableCommand cmd : mExecutingCommands) {
            cmds.add(new ExecutableCommandState(cmd, CommandState.EXECUTING));
        }
        for (ExecutableCommand cmd : mReadyCommands) {
            cmds.add(new ExecutableCommandState(cmd, CommandState.WAITING_FOR_DEVICE));
        }
        for (ExecutableCommand cmd : mSleepingCommands) {
            cmds.add(new ExecutableCommandState(cmd, CommandState.SLEEPING));
        }
        return cmds;
    }

    @Override
    public boolean shouldShutdownOnCmdfileError() {
        return mShutdownOnCmdfileError;
    }
}
