/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.tradefed.result;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.util.IEmail;
import com.android.tradefed.util.IEmail.Message;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.IOException;

/**
 * Unit tests for {@link EmailResultReporter}.
 */
public class EmailResultReporterTest extends TestCase {
    private IEmail mMockMailer;
    private EmailResultReporter mEmailReporter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockMailer = EasyMock.createMock(IEmail.class);
        mEmailReporter = new EmailResultReporter(mMockMailer);
    }

    /**
     * Test normal success case for {@link EmailResultReporter#invocationEnded(long)}.
     * @throws IOException
     */
    public void testInvocationEnded() throws IllegalArgumentException, IOException {
        mMockMailer.send(EasyMock.<Message>anyObject());
        EasyMock.replay(mMockMailer);
        mEmailReporter.invocationStarted(new BuildInfo("888", "mytest", "mybuild"));
        mEmailReporter.addDestination("foo");
        mEmailReporter.invocationEnded(0);
        EasyMock.verify(mMockMailer);

        assertEquals("Tradefed result for mytest  on build 888: SUCCESS",
                mEmailReporter.generateEmailSubject());
    }

    /**
     * Make sure that we don't include the string "null" in a generated email subject
     */
    public void testNullFlavorAndBranch() throws Exception {
        mMockMailer.send(EasyMock.<Message>anyObject());
        EasyMock.replay(mMockMailer);

        mEmailReporter.invocationStarted(new BuildInfo("888", null, null));
        mEmailReporter.addDestination("foo");
        mEmailReporter.invocationEnded(0);

        EasyMock.verify(mMockMailer);

        assertEquals("Tradefed result for (unknown suite) on build 888: SUCCESS",
                mEmailReporter.generateEmailSubject());
    }
}
