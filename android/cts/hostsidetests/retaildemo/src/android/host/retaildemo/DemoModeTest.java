/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.host.retaildemo;

import static junit.framework.Assert.assertTrue;

public class DemoModeTest extends BaseTestCase {
    private static final String RETAIL_DEMO_TEST_APK = "CtsRetailDemoApp.apk";

    public void testIsDemoUser_inPrimaryUser() throws Exception {
        assertTrue(runDeviceTestsAsUser(
                ".DemoUserTest", "testIsDemoUser_failure", getDevice().getPrimaryUserId()));
    }

    public void testIsDemoUser_inDemoUser() throws Exception {
        final int demoUserId = createDemoUser();
        getDevice().startUser(demoUserId);
        installAppAsUser(RETAIL_DEMO_TEST_APK, demoUserId);
        assertTrue(runDeviceTestsAsUser(
                ".DemoUserTest", "testIsDemoUser_success", demoUserId));
    }
}
