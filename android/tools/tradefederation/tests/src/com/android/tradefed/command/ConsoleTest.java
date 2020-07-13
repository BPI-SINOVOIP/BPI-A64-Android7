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

import com.android.tradefed.command.Console.CaptureList;
import com.android.tradefed.util.RegexTrie;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link Console}.
 */
public class ConsoleTest extends TestCase {

    private ICommandScheduler mMockScheduler;
    private Console mConsole;
    private ProxyExceptionHandler mProxyExceptionHandler;
    private boolean mIsConsoleFunctional;

    private static class ProxyExceptionHandler implements Thread.UncaughtExceptionHandler {
        private Throwable mThrowable = null;

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            mThrowable = e;
        }

        public void verify() throws Throwable {
            if (mThrowable != null) {
                throw mThrowable;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockScheduler = EasyMock.createStrictMock(ICommandScheduler.class);
        mIsConsoleFunctional = false;
        /**
         * Note: Eclipse doesn't play nice with consoles allocated like {@code new ConsoleReader()}.
         * To make an actual ConsoleReader instance, you should likely use the four-arg
         * {@link jline.ConsoleReader} constructor and use {@link jline.UnsupportedTerminal} or
         * similar as the implementation.
         */
        mConsole = new Console(null) {
            @Override
            boolean isConsoleFunctional() {
                return mIsConsoleFunctional;
            }
        };
        mConsole.setCommandScheduler(mMockScheduler);
        mProxyExceptionHandler = new ProxyExceptionHandler();
        mConsole.setUncaughtExceptionHandler(mProxyExceptionHandler);
     }

    /**
     * Test normal Console run when system console is available
     */
    public void testRun_withConsole() throws Throwable {
        mIsConsoleFunctional = true;

        mMockScheduler.start();
        mMockScheduler.await();
        EasyMock.expectLastCall().anyTimes();
        mMockScheduler.shutdown();  // after we discover that we can't read console input

        EasyMock.replay(mMockScheduler);

        // non interactive mode needs some args to start
        mConsole.setArgs(Arrays.asList("help"));
        mConsole.start();
        mConsole.join();
        mProxyExceptionHandler.verify();
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Test normal Console run when system console is _not_ available
     */
    public void testRun_noConsole() throws Throwable {
        mIsConsoleFunctional = false;

        mMockScheduler.start();
        mMockScheduler.await();
        EasyMock.expectLastCall().anyTimes();
        mMockScheduler.shutdown();  // after we run the initial command and then immediately quit.

        EasyMock.replay(mMockScheduler);

        // non interactive mode needs some args to start
        mConsole.setArgs(Arrays.asList("help"));
        mConsole.start();
        mConsole.join();
        mProxyExceptionHandler.verify();
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure that "run command foo config.xml" works properly.
     */
    public void testRunCommand() throws Exception {
        String[] command = new String[] {"run", "command", "--arg", "value", "config.xml"};
        String[] expected = new String[] {"--arg", "value", "config.xml"};
        CaptureList captures = new CaptureList();
        RegexTrie<Runnable> trie = mConsole.getCommandTrie();

        EasyMock.expect(mMockScheduler.addCommand(EasyMock.aryEq(expected))).andReturn(
                Boolean.TRUE);
        EasyMock.replay(mMockScheduler);

        Runnable runnable = trie.retrieve(captures, command);
        assertNotNull(String.format("Console didn't match input %s", Arrays.toString(command)),
                runnable);
        mConsole.executeCmdRunnable(runnable, captures);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure that the "run foo config.xml" shortcut works properly.
     */
    public void testRunCommand_shortcut() throws Exception {
        String[] command = new String[] {"run", "--arg", "value", "config.xml"};
        String[] expected = new String[] {"--arg", "value", "config.xml"};
        CaptureList captures = new CaptureList();
        RegexTrie<Runnable> trie = mConsole.getCommandTrie();

        EasyMock.expect(mMockScheduler.addCommand(EasyMock.aryEq(expected))).andReturn(
                Boolean.TRUE);
        EasyMock.replay(mMockScheduler);

        Runnable runnable = trie.retrieve(captures, command);
        assertNotNull(String.format("Console didn't match input %s", Arrays.toString(command)),
                runnable);
        mConsole.executeCmdRunnable(runnable, captures);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure that the command "run command command foo config.xml" properly considers the second
     * "command" to be the first token of the command to be executed.
     */
    public void testRunCommand_startsWithCommand() throws Exception {
        String[] command = new String[] {"run", "command", "command", "--arg", "value",
                "config.xml"};
        String[] expected = new String[] {"command", "--arg", "value", "config.xml"};
        CaptureList captures = new CaptureList();
        RegexTrie<Runnable> trie = mConsole.getCommandTrie();

        EasyMock.expect(mMockScheduler.addCommand(EasyMock.aryEq(expected))).andReturn(
                Boolean.TRUE);
        EasyMock.replay(mMockScheduler);

        Runnable runnable = trie.retrieve(captures, command);
        assertNotNull(String.format("Console didn't match input %s", Arrays.toString(command)),
                runnable);
        mConsole.executeCmdRunnable(runnable, captures);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure that {@link Console#getFlatArgs} works as expected.
     */
    public void testFlatten() throws Exception {
        CaptureList cl = new CaptureList();
        cl.add(Arrays.asList("run", null));
        cl.add(Arrays.asList("alpha"));
        cl.add(Arrays.asList("beta"));
        List<String> flat = Console.getFlatArgs(1, cl);
        assertEquals(2, flat.size());
        assertEquals("alpha", flat.get(0));
        assertEquals("beta", flat.get(1));
    }

    /**
     * Make sure that {@link Console#getFlatArgs} throws an exception when argIdx is wrong.
     */
    public void testFlatten_wrongArgIdx() throws Exception {
        CaptureList cl = new CaptureList();
        cl.add(Arrays.asList("run", null));
        cl.add(Arrays.asList("alpha"));
        cl.add(Arrays.asList("beta"));
        // argIdx is 0, and element 0 has size 2
        try {
            Console.getFlatArgs(0, cl);
            fail("IllegalArgumentException not thrown!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Make sure that {@link Console#getFlatArgs} throws an exception when argIdx is OOB.
     */
    public void testFlatten_argIdxOOB() throws Exception {
        CaptureList cl = new CaptureList();
        cl.add(Arrays.asList("run", null));
        cl.add(Arrays.asList("alpha"));
        cl.add(Arrays.asList("beta"));
        try {
            Console.getFlatArgs(1 + cl.size(), cl);
            fail("IndexOutOfBoundsException not thrown!");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }
}

