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
package com.android.tradefed.result;

import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.util.Email;
import com.android.tradefed.util.IEmail;
import com.android.tradefed.util.IEmail.Message;
import com.android.tradefed.util.StreamUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A simple result reporter base class that sends emails for test results.<br>
 * Subclasses should determine whether an email needs to be sent, and can
 * override other behavior.
 */
@OptionClass(alias = "email")
public class EmailResultReporter extends CollectingTestListener implements
        ITestSummaryListener {
    private static final String DEFAULT_SUBJECT_TAG = "Tradefed";

    @Option(name = "sender", description = "The envelope-sender address to use for the messages.",
            importance = Importance.IF_UNSET)
    private String mSender = null;

    @Option(name = "destination", description = "One or more destination addresses.",
            importance = Importance.IF_UNSET)
    private Collection<String> mDestinations = new HashSet<String>();

    @Option(name = "subject-tag",
            description = "The tag to be added to the beginning of the email subject.")
    private String mSubjectTag = DEFAULT_SUBJECT_TAG;

    private List<TestSummary> mSummaries = null;
    private Throwable mInvocationThrowable = null;
    private IEmail mMailer;
    private boolean mHtml;

    /**
     * Create a {@link EmailResultReporter}
     */
    public EmailResultReporter() {
        this(new Email());
    }

    /**
     * Create a {@link EmailResultReporter} with a custom {@link IEmail} instance to use.
     * <p/>
     * Exposed for unit testing.
     *
     * @param mailer the {@link IEmail} instance to use.
     */
    protected EmailResultReporter(IEmail mailer) {
        mMailer = mailer;
    }

    /**
     * Adds an email destination address.
     *
     * @param dest
     */
    public void addDestination(String dest) {
        mDestinations.add(dest);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void putSummary(List<TestSummary> summaries) {
        mSummaries = summaries;
    }

    /**
     * Allow subclasses to get at the summaries we've received
     */
    protected List<TestSummary> fetchSummaries() {
        return mSummaries;
    }

    /**
     * A method, meant to be overridden, which should do whatever filtering is decided and determine
     * whether a notification email should be sent for the test results.  Presumably, would consider
     * how many (if any) tests failed, prior failures of the same tests, etc.
     *
     * @return {@code true} if a notification email should be sent, {@code false} if not
     */
    protected boolean shouldSendMessage() {
        return true;
    }

    /**
     * A method to generate the subject for email reports. Will not be called if
     * {@link #shouldSendMessage()} returns {@code false}.
     * <p />
     * Sample email subjects:
     * <ul>
     *   <li>"Tradefed result for powerChromeFullSitesLocal on mantaray-user git_jb-mr1.1-release
     *       JDQ39: FAILED"</li>
     *   <li>"Tradefed result for Monkey on build 25: FAILED"</li>
     * </ul>
     *
     * @return A {@link String} containing the subject to use for an email
     *         report
     */
    protected String generateEmailSubject() {
        final IBuildInfo build = getBuildInfo();  // for convenience
        final StringBuilder subj = new StringBuilder(mSubjectTag);

        subj.append(" result for ");

        if (!appendUnlessNull(subj, build.getTestTag())) {
            subj.append("(unknown suite)");
        }

        subj.append(" on ");
        appendUnlessNull(subj, build.getBuildFlavor());
        appendUnlessNull(subj, build.getBuildBranch());
        if (!appendUnlessNull(subj, build.getBuildAttributes().get("build_alias"))) {
            subj.append("build ");
            subj.append(build.getBuildId());
        }

        subj.append(": ");
        subj.append(getInvocationStatus());
        return subj.toString();
    }

    /**
     * Appends {@code str + " "} to {@code builder} IFF {@code str} is not null.
     * @return {@code true} if str is not null, {@code false} if str is null.
     */
    private boolean appendUnlessNull(StringBuilder builder, String str) {
        if (str == null) {
            return false;
        } else {
            builder.append(str);
            builder.append(" ");
            return true;
        }
    }

    /**
     * Returns the {@link InvocationStatus}
     */
    protected InvocationStatus getInvocationStatus() {
        if (mInvocationThrowable == null) {
            return InvocationStatus.SUCCESS;
        } else if (mInvocationThrowable instanceof BuildError) {
            return InvocationStatus.BUILD_ERROR;
        } else {
            return InvocationStatus.FAILED;
        }
    }

    /**
     * Returns the {@link Throwable} passed via {@link #invocationFailed(Throwable)}.
     */
    protected Throwable getInvocationException() {
        return mInvocationThrowable;
    }

    /**
     * A method to generate the body for email reports.  Will not be called if
     * {@link #shouldSendMessage()} returns {@code false}.
     *
     * @return A {@link String} containing the body to use for an email report
     */
    protected String generateEmailBody() {
        StringBuilder bodyBuilder = new StringBuilder();

        for (Map.Entry<String, String> buildAttr : getBuildInfo().getBuildAttributes().entrySet()) {
            bodyBuilder.append(buildAttr.getKey());
            bodyBuilder.append(": ");
            bodyBuilder.append(buildAttr.getValue());
            bodyBuilder.append("\n");
        }
        bodyBuilder.append("host: ");
        try {
            bodyBuilder.append(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            bodyBuilder.append("unknown");
            CLog.e(e);
        }
        bodyBuilder.append("\n\n");

        if (mInvocationThrowable != null) {
            bodyBuilder.append("Invocation failed: ");
            bodyBuilder.append(StreamUtil.getStackTrace(mInvocationThrowable));
            bodyBuilder.append("\n");
        }
        bodyBuilder.append(String.format("Test results:  %d passed, %d failed\n\n",
                getNumTestsInState(TestStatus.PASSED), getNumAllFailedTests()));
        for (TestRunResult result : getRunResults()) {
            if (!result.getRunMetrics().isEmpty()) {
                bodyBuilder.append(String.format("'%s' test run metrics: %s\n", result.getName(),
                        result.getRunMetrics()));
            }
        }
        bodyBuilder.append("\n");

        if (mSummaries != null) {
            for (TestSummary summary : mSummaries) {
                bodyBuilder.append("Invocation summary report: ");
                bodyBuilder.append(summary.getSummary().getString());
                if (!summary.getKvEntries().isEmpty()) {
                    bodyBuilder.append("\".\nSummary key-value dump:\n");
                    bodyBuilder.append(summary.getKvEntries().toString());
                }
            }
        }
        return bodyBuilder.toString();
    }

    /**
     * A method to set a flag indicating that the email body is in HTML rather than plain text
     *
     * This method must be called before the email body is generated
     *
     * @param html true if the body is html
     */
    protected void setHtml(boolean html) {
        mHtml = html;
    }

    protected boolean isHtml() {
        return mHtml;
    }

    @Override
    public void invocationFailed(Throwable t) {
        mInvocationThrowable = t;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);
        if (!shouldSendMessage()) {
            return;
        }

        if (mDestinations.isEmpty()) {
            CLog.e("Failed to send email because no destination addresses were set.");
            return;
        }

        Message msg = new Message();
        msg.setSender(mSender);
        msg.setSubject(generateEmailSubject());
        msg.setBody(generateEmailBody());
        msg.setHtml(isHtml());
        Iterator<String> toAddress = mDestinations.iterator();
        while (toAddress.hasNext()) {
            msg.addTo(toAddress.next());
        }

        try {
            mMailer.send(msg);
        } catch (IllegalArgumentException e) {
            CLog.e("Failed to send email");
            CLog.e(e);
        } catch (IOException e) {
            CLog.e("Failed to send email");
            CLog.e(e);
        }
    }
}
