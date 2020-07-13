/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.support.v4.view;

import android.app.Activity;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.Display;
import android.view.View;
import android.support.v4.BaseInstrumentationTestCase;
import android.support.compat.test.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;


@RunWith(AndroidJUnit4.class)
@MediumTest
public class ViewCompatTest extends BaseInstrumentationTestCase<ViewCompatActivity> {

    private View mView;

    public ViewCompatTest() {
        super(ViewCompatActivity.class);
    }

    @Before
    public void setUp() {
        final Activity activity = mActivityTestRule.getActivity();
        mView = activity.findViewById(R.id.view);
    }

    @Test
    public void testConstants() {
        // Compat constants must match core constants since they can be used interchangeably
        // in various support lib calls.
        assertEquals("LTR constants", View.LAYOUT_DIRECTION_LTR, ViewCompat.LAYOUT_DIRECTION_LTR);
        assertEquals("RTL constants", View.LAYOUT_DIRECTION_RTL, ViewCompat.LAYOUT_DIRECTION_RTL);
    }

    @Test
    public void testGetDisplay() {
        final Display display = ViewCompat.getDisplay(mView);
        assertNotNull(display);
    }

    @Test
    public void testGetDisplay_returnsNullForUnattachedView() {
        final View view = new View(mActivityTestRule.getActivity());
        final Display display = ViewCompat.getDisplay(view);
        assertNull(display);
    }

}
