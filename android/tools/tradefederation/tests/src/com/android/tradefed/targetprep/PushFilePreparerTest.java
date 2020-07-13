/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.tradefed.device.ITestDevice;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.util.Arrays;

/**
 * Unit tests for {@link PushFilePreparer}
 */
public class PushFilePreparerTest extends TestCase {

    private PushFilePreparer mPreparer = null;
    private ITestDevice mMockDevice = null;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createStrictMock(ITestDevice.class);
        mPreparer = new PushFilePreparer();
    }

    /**
     * When there's nothing to be done, expect no exception to be thrown
     */
    public void testNoop() throws Exception {
        EasyMock.replay(mMockDevice);
        mPreparer.setUp(mMockDevice, null);
    }

    public void testLocalNoExist() throws Exception {
        mPreparer.setPushSpecs(Arrays.asList("/noexist->/data/"));
        mPreparer.setPostPushCommands(Arrays.asList("ls /"));
        EasyMock.replay(mMockDevice);
        try {
            // Should throw TargetSetupError and _not_ run any post-push command
            mPreparer.setUp(mMockDevice, null);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    public void testRemoteNoExist() throws Exception {
        mPreparer.setPushSpecs(Arrays.asList("/bin/sh->/noexist/"));
        mPreparer.setPostPushCommands(Arrays.asList("ls /"));
        // expect a pushFile() call and return false (failed)
        EasyMock.expect(
                mMockDevice.pushFile((File)EasyMock.anyObject(), EasyMock.eq("/noexist/")))
                .andReturn(Boolean.FALSE);
        EasyMock.replay(mMockDevice);
        try {
            // Should throw TargetSetupError and _not_ run any post-push command
            mPreparer.setUp(mMockDevice, null);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    public void testWarnOnFailure() throws Exception {
        mPreparer.setPushSpecs(Arrays.asList("/noexist->/data/", "/bin/sh->/noexist/"));
        mPreparer.setPostPushCommands(Arrays.asList("ls /"));
        mPreparer.setAbortOnFailure(false);

        // expect a pushFile() call and return false (failed)
        EasyMock.expect(
                mMockDevice.pushFile((File)EasyMock.anyObject(), EasyMock.eq("/noexist/")))
                .andReturn(Boolean.FALSE);
        // Because we're only warning, the post-push command should be run despite the push failures
        EasyMock.expect(mMockDevice.executeShellCommand(EasyMock.eq("ls /"))).andReturn("");
        EasyMock.replay(mMockDevice);

        // Don't expect any exceptions to be thrown
        mPreparer.setUp(mMockDevice, null);
    }
}

