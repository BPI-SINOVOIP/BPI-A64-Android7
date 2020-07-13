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
package com.android.tradefed.config;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;

import junit.framework.Assert;

/**
 * Empty implementation use by {@link ConfigurationFactoryTest#testPrintHelpForConfig_mandatory()}.
 */
public class StubMandatoryTest implements IRemoteTest {

    static final String MANDATORY_OPTION_NAME = "mandatory-option";
    static final String MANDATORY_OPTION_DESC = "this is a mandatory option.";

    @Option(name = MANDATORY_OPTION_NAME, description = MANDATORY_OPTION_DESC,
            mandatory = true)
    private String mMandatory = null;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mMandatory);
    }
}
