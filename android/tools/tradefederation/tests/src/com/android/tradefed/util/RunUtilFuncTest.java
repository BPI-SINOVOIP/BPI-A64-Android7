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
import com.android.tradefed.util.IRunUtil.IRunnableResult;

import junit.framework.TestCase;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * Longer running tests for {@link RunUtilFuncTest}
 */
public class RunUtilFuncTest extends TestCase {

    private abstract class MyRunnable implements IRunUtil.IRunnableResult {
        boolean mCanceled = false;

        @Override
        public void cancel() {
            mCanceled = true;
        }
    }

    /**
     * Test timeout case for {@link RunUtil#runTimed(long, IRunnableResult)}.
     */
    public void testRunTimed_timeout() {
        final long timeout = 200;
        MyRunnable mockRunnable = new MyRunnable() {
            @Override
            public boolean run() {
                try {
                    Thread.sleep(timeout*5);
                } catch (InterruptedException e) {
                    // ignore
                }
                return true;
            }
        };
        assertEquals(CommandStatus.TIMED_OUT, RunUtil.getDefault().runTimed(timeout,
                mockRunnable, true));
        assertTrue(mockRunnable.mCanceled);
    }

    /**
     * Test method for {@link RunUtil#runTimedRetry(long, long, , int, IRunnableResult)}.
     * Verify that multiple attempts are made.
     */
    public void testRunTimedRetry() {
        final int maxAttempts = 5;
        final long pollTime = 200;
        IRunUtil.IRunnableResult mockRunnable = new IRunUtil.IRunnableResult() {
            int attempts = 0;
            @Override
            public boolean run() {
                attempts++;
                return attempts == maxAttempts;
            }
            @Override
            public void cancel() {
                // ignore
            }
        };
        final long startTime = System.currentTimeMillis();
        assertTrue(RunUtil.getDefault().runTimedRetry(100, pollTime, maxAttempts, mockRunnable));
        final long actualTime = System.currentTimeMillis() - startTime;
        // assert that time actually taken is at least, and no more than twice expected
        final long expectedPollTime = pollTime * (maxAttempts-1);
        assertTrue(String.format("Expected poll time %d, got %d", expectedPollTime, actualTime),
                expectedPollTime <= actualTime && actualTime <= (2 * expectedPollTime));
    }

    /**
     * Test timeout case for {@link RunUtil#runTimed(long, IRunnableResult)} and ensure we
     * consistently get the right stdout for a fast running command.
     */
    public void testRunTimed_repeatedOutput() {
        for (int i=0; i < 1000; i++) {
            final long timeOut = 200;
            CommandResult result = RunUtil.getDefault().runTimedCmd(timeOut, "echo", "hello");
            assertTrue(result.getStatus() == CommandStatus.SUCCESS);
            CLog.d(result.getStdout());
            assertTrue(result.getStdout().trim().equals("hello"));
        }
    }

    /**
     * Test case for {@link RunUtil#runTimed(long, IRunnableResult)} for a command that produces
     * a large amount of output
     * @throws IOException
     */
    public void testRunTimed_largeOutput() throws IOException {
        // 1M  chars
        int dataSize = 1000000;
        File f = FileUtil.createTempFile("foo", ".txt");
        Writer s = null;
        try {
            s = new BufferedWriter(new FileWriter(f));
            for (int i=0; i < dataSize; i++) {
                s.write('a');
            }
            s.close();

            final long timeOut = 5000;
            // FIXME: this test case is not ideal, as it will only work on platforms that support
            // cat command.
            CommandResult result = RunUtil.getDefault().runTimedCmd(timeOut, "cat",
                    f.getAbsolutePath());
            assertTrue(result.getStatus() == CommandStatus.SUCCESS);
            assertTrue(result.getStdout().length() == dataSize);
        } finally {
            f.delete();
            StreamUtil.close(s);
        }
    }

    /**
     * Test case for {@link RunUtil#unsetEnvVariable(String key)}
     */
    public void testUnsetEnvVariable() {
        long timeout = 200;
        RunUtil runUtil = new RunUtil();
        runUtil.setEnvVariable("bar", "foo");
        // FIXME: this test case is not ideal, as it will only work on platforms that support
        // printenv
        CommandResult result = runUtil.runTimedCmd(timeout, "printenv", "bar");
        assertTrue(result.getStatus() == CommandStatus.SUCCESS);
        assertTrue("foo".equals(result.getStdout().trim()));

        // remove env variable
        runUtil.unsetEnvVariable("bar");
        // printenv with non-exist variable will fail
        result = runUtil.runTimedCmd(timeout, "printenv", "bar");
        assertTrue(result.getStatus() == CommandStatus.FAILED);
        assertTrue("".equals(result.getStdout().trim()));
    }
}
