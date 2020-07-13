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

import com.android.tradefed.util.IRunUtil.IRunnableResult;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;

/**
 * Unit tests for {@link RunUtilTest}
 */
public class RunUtilTest extends TestCase {

    private RunUtil mRunUtil;
    private long mSleepTime = 0;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRunUtil = new RunUtil();
    }

    /**
     * Test success case for {@link RunUtil#runTimed(long, IRunnableResult, boolean)}.
     */
    public void testRunTimed() throws Exception {
        IRunUtil.IRunnableResult mockRunnable = EasyMock.createStrictMock(
                IRunUtil.IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andReturn(Boolean.TRUE);
        EasyMock.replay(mockRunnable);
        assertEquals(CommandStatus.SUCCESS, mRunUtil.runTimed(100, mockRunnable, true));
    }

    /**
     * Test failure case for {@link RunUtil#runTimed(long, IRunnableResult, boolean)}.
     */
    public void testRunTimed_failed() throws Exception {
        IRunUtil.IRunnableResult mockRunnable = EasyMock.createStrictMock(
                IRunUtil.IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andReturn(Boolean.FALSE);
        EasyMock.replay(mockRunnable);
        assertEquals(CommandStatus.FAILED, mRunUtil.runTimed(100, mockRunnable, true));
    }

    /**
     * Test exception case for {@link RunUtil#runTimed(long, IRunnableResult, boolean)}.
     */
    public void testRunTimed_exception() throws Exception {
        IRunUtil.IRunnableResult mockRunnable = EasyMock.createStrictMock(
                IRunUtil.IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andThrow(new RuntimeException());
        mockRunnable.cancel();
        EasyMock.replay(mockRunnable);
        assertEquals(CommandStatus.EXCEPTION, mRunUtil.runTimed(100, mockRunnable, true));
    }

    /**
     * Test that {@link RunUtil#runTimedCmd(long, String)} fails when given a garbage command.
     */
    public void testRunTimedCmd_failed() {
        CommandResult result = mRunUtil.runTimedCmd(1000, "blahggggwarggg");
        assertEquals(CommandStatus.EXCEPTION, result.getStatus());
        assertNull(result.getStdout());
        assertNull(result.getStderr());
    }

    /**
     * Test that {@link RunUtil#runTimedCmd(long, String)} fails when garbage times out.
     */
    public void testRunTimedCmd_timeout() {
        String[] command = {"sleep", "10000"};
        CommandResult result = mRunUtil.runTimedCmd(10, command);
        assertEquals(CommandStatus.TIMED_OUT, result.getStatus());
        assertEquals(result.getStdout(),"");
        assertEquals(result.getStderr(),"");
    }

    /**
     * Verify that calling {@link RunUtil#setWorkingDir()} is not allowed on default instance.
     */
    public void testSetWorkingDir_default() {
        try {
            RunUtil.getDefault().setWorkingDir(new File("foo"));
            fail("could set working dir on RunUtil.getDefault()");
        } catch (RuntimeException e) {
            // expected
        }
    }

    /**
     * Verify that calling {@link RunUtil#setEnvVariable(String, String)} is not allowed on default
     * instance.
     */
    public void testSetEnvVariable_default() {
        try {
            RunUtil.getDefault().setEnvVariable("foo", "bar");
            fail("could set env var on RunUtil.getDefault()");
        } catch (RuntimeException e) {
            // expected
        }
    }

    /**
     * Verify that calling {@link RunUtil#unsetEnvVariable(String)} is not allowed on default
     * instance.
     */
    public void testUnsetEnvVariable_default() {
        try {
            RunUtil.getDefault().unsetEnvVariable("foo");
            fail("could unset env var on RunUtil.getDefault()");
        } catch (Exception e) {
            // expected
        }
    }

    /**
     * Test that {@link RunUtil#runEscalatingTimedRetry()} fails when operation continually fails,
     * and that the maxTime variable is respected.
     */
    public void testRunEscalatingTimedRetry_timeout() throws Exception {
        // create a RunUtil fixture with methods mocked out for
        // fast execution

        RunUtil runUtil = new RunUtil() {
            @Override
            public void sleep(long time) {
                mSleepTime += time;
            }

            @Override
            long getCurrentTime() {
                return mSleepTime;
            }

            @Override
            public CommandStatus runTimed(long timeout, IRunUtil.IRunnableResult runnable,
                    boolean logErrors) {
                try {
                    // override parent with simple version that doesn't create a thread
                    return runnable.run() ? CommandStatus.SUCCESS : CommandStatus.FAILED;
                } catch (Exception e) {
                    return CommandStatus.EXCEPTION;
                }
            }
        };

        IRunUtil.IRunnableResult mockRunnable = EasyMock.createStrictMock(
                IRunUtil.IRunnableResult.class);
        // expect a call 4 times, at sleep time 0, 1, 4 and 10 ms
        EasyMock.expect(mockRunnable.run()).andReturn(Boolean.FALSE).times(4);
        EasyMock.replay(mockRunnable);
        long maxTime = 10;
        assertFalse(runUtil.runEscalatingTimedRetry(1, 1, 512, maxTime, mockRunnable));
        assertEquals(maxTime, mSleepTime);
        EasyMock.verify(mockRunnable);
    }

    /**
     * Test a success case for {@link RunUtil#interrupt}.
     */
    public void testInterrupt() {
        final String message = "it is alright now";
        mRunUtil.allowInterrupt(true);
        mRunUtil.interrupt(Thread.currentThread(), message);
        try{
            mRunUtil.sleep(1);
            fail("RunInterruptedException was expected, but not thrown.");
        } catch (final RunInterruptedException e) {
            assertEquals(message, e.getMessage());
        }
    }

    /**
     * Test whether a {@link RunUtil#interrupt} call is respected when called while interrupts are
     * not allowed.
     */
    public void testInterrupt_delayed() {
        final String message = "it is alright now";
        mRunUtil.allowInterrupt(false);
        mRunUtil.interrupt(Thread.currentThread(), message);
        mRunUtil.sleep(1);
        mRunUtil.allowInterrupt(true);
        try{
            mRunUtil.sleep(1);
            fail("RunInterruptedException was expected, but not thrown.");
        } catch (final RunInterruptedException e) {
            assertEquals(message, e.getMessage());
        }
    }

    /**
     * Test whether a {@link RunUtil#interrupt} call is respected when called multiple times.
     */
    public void testInterrupt_multiple() {
        final String message1 = "it is alright now";
        final String message2 = "without a fight";
        final String message3 = "rock this town";
        mRunUtil.allowInterrupt(true);
        mRunUtil.interrupt(Thread.currentThread(), message1);
        mRunUtil.interrupt(Thread.currentThread(), message2);
        mRunUtil.interrupt(Thread.currentThread(), message3);
        try{
            mRunUtil.sleep(1);
            fail("RunInterruptedException was expected, but not thrown.");
        } catch (final RunInterruptedException e) {
            assertEquals(message3, e.getMessage());
        }
    }
}