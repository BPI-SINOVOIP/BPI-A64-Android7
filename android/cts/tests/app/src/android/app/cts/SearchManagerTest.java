/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import android.app.stubs.CTSActivityTestCaseBase;
import android.app.stubs.SearchManagerStubActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public class SearchManagerTest extends CTSActivityTestCaseBase {

    private void setupActivity(String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(getInstrumentation().getTargetContext(), SearchManagerStubActivity.class);
        getInstrumentation().getTargetContext().startActivity(intent);
    }

    public void testStopSearch() throws InterruptedException {
        if (isTelevision()) {
            return;
        }
        SearchManagerStubActivity.setCTSResult(this);
        setupActivity(SearchManagerStubActivity.TEST_STOP_SEARCH);
        waitForResult();
    }

    public void testSetOnDismissListener() throws InterruptedException {
        if (isTelevision()) {
            return;
        }
        SearchManagerStubActivity.setCTSResult(this);
        setupActivity(SearchManagerStubActivity.TEST_ON_DISMISSLISTENER);
        waitForResult();
    }

    public void testSetOnCancelListener() throws InterruptedException {
        if (isTelevision()) {
            return;
        }
        SearchManagerStubActivity.setCTSResult(this);
        setupActivity(SearchManagerStubActivity.TEST_ON_CANCELLISTENER);
        waitForResult();
    }

    private boolean isTelevision() {
        Context context = getInstrumentation().getTargetContext();
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
}
