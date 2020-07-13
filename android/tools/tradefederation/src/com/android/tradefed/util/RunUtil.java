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

package com.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A collection of helper methods for executing operations.
 */
public class RunUtil implements IRunUtil {

    private static final int POLL_TIME_INCREASE_FACTOR = 4;
    private static final long THREAD_JOIN_POLL_INTERVAL = 30 * 1000;
    private static final long PROCESS_DESTROY_TIMEOUT_SEC = 2;
    private static IRunUtil sDefaultInstance = null;
    private File mWorkingDir = null;
    private Map<String, String> mEnvVariables = new HashMap<String, String>();
    private Set<String> mUnsetEnvVariables = new HashSet<String>();
    private ThreadLocal<Boolean> mIsInterruptAllowed = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };
    private Map<Long, String> mInterruptThreads = new HashMap<>();

    /**
     * Create a new {@link RunUtil} object to use.
     */
    public RunUtil() {
    }

    /**
     * Get a reference to the default {@link RunUtil} object.
     * <p/>
     * This is useful for callers who want to use IRunUtil without customization.
     * Its recommended that callers who do need a custom IRunUtil instance
     * (ie need to call either {@link #setEnvVariable(String, String)} or
     * {@link #setWorkingDir(File)} create their own copy.
     */
    public static IRunUtil getDefault() {
        if (sDefaultInstance == null) {
            sDefaultInstance = new RunUtil();
        }
        return sDefaultInstance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setWorkingDir(File dir) {
        if (this.equals(sDefaultInstance)) {
            throw new UnsupportedOperationException("Cannot setWorkingDir on default RunUtil");
        }
        mWorkingDir = dir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setEnvVariable(String name, String value) {
        if (this.equals(sDefaultInstance)) {
            throw new UnsupportedOperationException("Cannot setEnvVariable on default RunUtil");
        }
        mEnvVariables.put(name, value);
    }

    /**
     * {@inheritDoc}
     * Environment variables may inherit from the parent process, so we need to delete
     * the environment variable from {@link ProcessBuilder#environment()}
     *
     * @param key the variable name
     * @see {@link ProcessBuilder#environment()}
     */
    @Override
    public synchronized void unsetEnvVariable(String key) {
        if (this.equals(sDefaultInstance)) {
            throw new UnsupportedOperationException("Cannot unsetEnvVariable on default RunUtil");
        }
        mUnsetEnvVariables.add(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult runTimedCmd(final long timeout, final String... command) {
        final CommandResult result = new CommandResult();
        IRunUtil.IRunnableResult osRunnable = new RunnableResult(result, null,
                createProcessBuilder(command));
        CommandStatus status = runTimed(timeout, osRunnable, true);
        result.setStatus(status);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult runTimedCmdRetry(long timeout, long retryInterval, int attempts,
            String... command) {
        CommandResult result = null;
        int counter = 0;
        while (counter < attempts) {
            result = runTimedCmd(timeout, command);
            if (CommandStatus.SUCCESS.equals(result.getStatus())) {
                return result;
            }
            sleep(retryInterval);
            counter++;
        }
        return result;
    }

    private synchronized ProcessBuilder createProcessBuilder(String... command) {
        return createProcessBuilder(Arrays.asList(command));
    }

    private synchronized ProcessBuilder createProcessBuilder(List<String> commandList) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (mWorkingDir != null) {
            processBuilder.directory(mWorkingDir);
        }
        if (!mEnvVariables.isEmpty()) {
            processBuilder.environment().putAll(mEnvVariables);
        }
        if (!mUnsetEnvVariables.isEmpty()) {
            // in this implementation, the unsetEnv's priority is higher than set.
            processBuilder.environment().keySet().removeAll(mUnsetEnvVariables);
        }
        return processBuilder.command(commandList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult runTimedCmdWithInput(final long timeout, String input,
            final String... command) {
        return runTimedCmdWithInput(timeout, input, ArrayUtil.list(command));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult runTimedCmdWithInput(final long timeout, String input,
            final List<String> command) {
        final CommandResult result = new CommandResult();
        IRunUtil.IRunnableResult osRunnable = new RunnableResult(result, input,
                createProcessBuilder(command));
        CommandStatus status = runTimed(timeout, osRunnable, true);
        result.setStatus(status);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult runTimedCmdSilently(final long timeout, final String... command) {
        final CommandResult result = new CommandResult();
        IRunUtil.IRunnableResult osRunnable = new RunnableResult(result, null,
                createProcessBuilder(command));
        CommandStatus status = runTimed(timeout, osRunnable, false);
        result.setStatus(status);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult runTimedCmdSilentlyRetry(long timeout, long retryInterval, int attempts,
            String... command) {
        CommandResult result = null;
        int counter = 0;
        while (counter < attempts) {
            result = runTimedCmdSilently(timeout, command);
            if (CommandStatus.SUCCESS.equals(result.getStatus())) {
                return result;
            }
            sleep(retryInterval);
            counter++;
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Process runCmdInBackground(final String... command) throws IOException  {
        final String fullCmd = Arrays.toString(command);
        CLog.v("Running %s", fullCmd);
        return createProcessBuilder(command).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Process runCmdInBackground(final List<String> command) throws IOException  {
        CLog.v("Running %s", command);
        return createProcessBuilder(command).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Process runCmdInBackground(List<String> command, OutputStream output)
            throws IOException {
        CLog.v("Running %s", command);
        Process process = createProcessBuilder(command).start();
        inheritIO(process.getInputStream(), output);
        inheritIO(process.getErrorStream(), output);
        return process;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public CommandStatus runTimed(long timeout, IRunUtil.IRunnableResult runnable,
            boolean logErrors) {
        checkInterrupted();
        RunnableNotifier runThread = new RunnableNotifier(runnable, logErrors);
        CLog.d("Running command with timeout: %dms", timeout);
        runThread.start();
        long startTime = System.currentTimeMillis();
        long pollIterval = 0;
        if (timeout < THREAD_JOIN_POLL_INTERVAL) {
            pollIterval = timeout;
        } else {
            pollIterval = THREAD_JOIN_POLL_INTERVAL;
        }
        do {
            try {
                runThread.join(pollIterval);
            } catch (InterruptedException e) {
                if (mIsInterruptAllowed.get()) {
                    CLog.i("runTimed: interrupted while joining the runnable");
                    break;
                }
                else {
                    CLog.i("runTimed: received an interrupt but uninterruptible mode, ignoring");
                }
            }
        } while ((System.currentTimeMillis() - startTime) < timeout && runThread.isAlive());

        if (runThread.getStatus() == CommandStatus.TIMED_OUT
                || runThread.getStatus() == CommandStatus.EXCEPTION) {
            CLog.i("runTimed: Calling interrupt, status is %s", runThread.getStatus());
            runThread.interrupt();
        }
        checkInterrupted();
        return runThread.getStatus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean runTimedRetry(long opTimeout, long pollInterval, int attempts,
            IRunUtil.IRunnableResult runnable) {
        for (int i = 0; i < attempts; i++) {
            if (runTimed(opTimeout, runnable, true) == CommandStatus.SUCCESS) {
                return true;
            }
            CLog.d("operation failed, waiting for %d ms", pollInterval);
            sleep(pollInterval);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean runFixedTimedRetry(final long opTimeout, final long pollInterval,
            final long maxTime, final IRunUtil.IRunnableResult runnable) {
        final long initialTime = getCurrentTime();
        while (getCurrentTime() < (initialTime + maxTime)) {
            if (runTimed(opTimeout, runnable, true) == CommandStatus.SUCCESS) {
                return true;
            }
            CLog.d("operation failed, waiting for %d ms", pollInterval);
            sleep(pollInterval);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean runEscalatingTimedRetry(final long opTimeout,
            final long initialPollInterval, final long maxPollInterval, final long maxTime,
            final IRunUtil.IRunnableResult runnable) {
        // wait an initial time provided
        long pollInterval = initialPollInterval;
        final long initialTime = getCurrentTime();
        while (true) {
            if (runTimed(opTimeout, runnable, true) == CommandStatus.SUCCESS) {
                return true;
            }
            long remainingTime = maxTime - (getCurrentTime() - initialTime);
            if (remainingTime <= 0) {
                CLog.d("operation is still failing after retrying for %d ms", maxTime);
                return false;
            } else if (remainingTime < pollInterval) {
                // cap pollInterval to a max of remainingTime
                pollInterval = remainingTime;
            }
            CLog.d("operation failed, waiting for %d ms", pollInterval);
            sleep(pollInterval);
            // somewhat arbitrarily, increase the poll time by a factor of 4 for each attempt,
            // up to the previously decided maximum
            pollInterval *= POLL_TIME_INCREASE_FACTOR;
            if (pollInterval > maxPollInterval) {
                pollInterval = maxPollInterval;
            }
        }
    }

    /**
     * Retrieves the current system clock time.
     * <p/>
     * Exposed so it can be mocked for unit testing
     */
    long getCurrentTime() {
        return System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sleep(long time) {
        checkInterrupted();
        if (time <= 0) {
            return;
        }
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            // ignore
            CLog.d("sleep interrupted");
        }
        checkInterrupted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void allowInterrupt(boolean allow) {
        CLog.d("run interrupt allowed: %s", allow);
        mIsInterruptAllowed.set(allow);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void interrupt(Thread thread, String message) {
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null.");
        }
        mInterruptThreads.put(thread.getId(), message);
    }

    private synchronized void checkInterrupted() {
        final long threadId = Thread.currentThread().getId();
        if (mIsInterruptAllowed.get()) {
            final String message = mInterruptThreads.remove(threadId);
            if (message != null) {
                throw new RunInterruptedException(message);
            }
        }
    }

    /**
     * Helper thread that wraps a runnable, and notifies when done.
     */
    private static class RunnableNotifier extends Thread {

        private final IRunUtil.IRunnableResult mRunnable;
        private CommandStatus mStatus = CommandStatus.TIMED_OUT;
        private boolean mLogErrors = true;

        RunnableNotifier(IRunUtil.IRunnableResult runnable, boolean logErrors) {
            // Set this thread to be a daemon so that it does not prevent
            // TF from shutting down.
            setDaemon(true);
            mRunnable = runnable;
            mLogErrors = logErrors;
        }

        @Override
        public void run() {
            CommandStatus status;
            try {
                status = mRunnable.run() ? CommandStatus.SUCCESS : CommandStatus.FAILED;
            } catch (InterruptedException e) {
                CLog.i("runutil interrupted");
                status = CommandStatus.EXCEPTION;
            } catch (Exception e) {
                if (mLogErrors) {
                    CLog.e("Exception occurred when executing runnable");
                    CLog.e(e);
                }
                status = CommandStatus.EXCEPTION;
            }
            synchronized (this) {
                mStatus = status;
            }
        }

        @Override
        public void interrupt() {
            mRunnable.cancel();
        }

        synchronized CommandStatus getStatus() {
            return mStatus;
        }
    }

    private class RunnableResult implements IRunUtil.IRunnableResult {
        private final ProcessBuilder mProcessBuilder;
        private final CommandResult mCommandResult;
        private final String mInput;
        private Process mProcess = null;
        private CountDownLatch mCountDown = null;
        private Thread mExecutionThread;

        RunnableResult(final CommandResult result, final String input,
                final ProcessBuilder processBuilder) {
            mProcessBuilder = processBuilder;
            mInput = input;
            mCommandResult = result;
            mCountDown = new CountDownLatch(1);
        }

        @Override
        public boolean run() throws Exception {
            mExecutionThread = Thread.currentThread();
            CLog.d("Running %s", mProcessBuilder.command());
            mProcess = mProcessBuilder.start();
            if (mInput != null) {
                BufferedOutputStream processStdin = new BufferedOutputStream(
                        mProcess.getOutputStream());
                processStdin.write(mInput.getBytes("UTF-8"));
                processStdin.flush();
                processStdin.close();
            }
            // Redirect IO, so that the buffer for the spawn process does not fill up and cause
            // deadlock.
            ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
            ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
            Thread stdoutThread = inheritIO(mProcess.getInputStream(), stdOut);
            Thread stderrThread = inheritIO(mProcess.getErrorStream(), stdErr);
            // Wait for process to complete.
            int rc = 1;
            try {
                try {
                    rc = mProcess.waitFor();
                    // wait for stdout and stderr to be read
                    stdoutThread.join();
                    stderrThread.join();
                    // close the buffer that holds stdout/err content
                    stdOut.close();
                    stdErr.close();
                } finally {
                    // Write out the streams to the result.
                    mCommandResult.setStdout(stdOut.toString("UTF-8"));
                    mCommandResult.setStderr(stdErr.toString("UTF-8"));
                }
            } finally {
                mCountDown.countDown();
            }

            if (rc == 0) {
                return true;
            } else {
                CLog.i("%s command failed. return code %d", mProcessBuilder.command(), rc);
            }
            return false;
        }

        @Override
        public void cancel() {
            if (mProcess != null) {
                CLog.i("Cancelling the process execution");
                mProcess.destroy();
                mProcess = null;
                try {
                    // Only allow to continue if the Stdout has been read
                    // RunnableNotifier#Interrupt is the next call and will terminate the thread
                    if (!mCountDown.await(PROCESS_DESTROY_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                        CLog.i("Process still not terminated, interrupting the execution thread");
                        mExecutionThread.interrupt();
                        mCountDown.await();
                    }
                } catch (InterruptedException e) {
                    CLog.i("interrupted while waiting for process output to be saved");
                }
            }
        }
    }

    /**
     * Helper method to redirect input stream.
     * @param src {@link InputStream} to inherit/redirect from
     * @param dest {@link BufferedOutputStream} to inherit/redirect to
     */
    private static Thread inheritIO(final InputStream src, final OutputStream dest) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    StreamUtil.copyStreams(src, dest);
                } catch (IOException e) {
                    CLog.e("Failed to read input stream.");
                }
            }
        });
        t.start();
        return t;
    }
}
