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

package android.view.cts;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.view.View;
import android.view.cts.util.ViewTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.concurrent.TimeoutException;

@MediumTest
public class TextureViewTest {

    @Rule
    public ActivityTestRule<TextureViewCtsActivity> mActivityRule = new ActivityTestRule<>(
            TextureViewCtsActivity.class);

    private TextureViewCtsActivity mActivity;
    private Instrumentation mInstrumentation;

    @Before
    public void setUp() throws Exception {
        mActivity = mActivityRule.getActivity();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        assertNotNull(mActivity);
        assertNotNull(mInstrumentation);
    }

    @Test
    public void testFirstFrames() throws Throwable {
        final Point center = new Point();
        mInstrumentation.waitForIdleSync();
        mInstrumentation.runOnMainSync(() -> {
            View content = mActivity.findViewById(android.R.id.content);
            int[] outLocation = new int[2];
            content.getLocationOnScreen(outLocation);
            center.x = outLocation[0] + (content.getWidth() / 2);
            center.y = outLocation[1] + (content.getHeight() / 2);
        });
        assertTrue(center.x > 0);
        assertTrue(center.y > 0);
        waitForColor(center, Color.WHITE);
        mActivity.waitForSurface();
        mActivity.initGl();
        int updatedCount;
        updatedCount = mActivity.waitForSurfaceUpdateCount(0);
        assertEquals(0, updatedCount);
        mActivity.drawColor(Color.GREEN);
        updatedCount = mActivity.waitForSurfaceUpdateCount(1);
        assertEquals(1, updatedCount);
        assertEquals(Color.WHITE, getPixel(center));
        ViewTestUtils.runOnMainAndDrawSync(mInstrumentation, mActivity,
                () -> mActivity.removeCover());

        int color;
        color = waitForChange(center, Color.WHITE);
        assertEquals(Color.GREEN, color);
        mActivity.drawColor(Color.BLUE);
        updatedCount = mActivity.waitForSurfaceUpdateCount(2);
        assertEquals(2, updatedCount);
        color = waitForChange(center, color);
        assertEquals(Color.BLUE, color);
    }

    private int getPixel(Point point) {
        Bitmap screenshot = mInstrumentation.getUiAutomation().takeScreenshot();
        int pixel = screenshot.getPixel(point.x, point.y);
        screenshot.recycle();
        return pixel;
    }

    private void waitForColor(Point point, int color)
            throws InterruptedException, TimeoutException {
        for (int i = 0; i < 20; i++) {
            int pixel = getPixel(point);
            if (pixel == color) {
                return;
            }
            Thread.sleep(16);
        }
        throw new TimeoutException();
    }

    private int waitForChange(Point point, int color)
            throws InterruptedException, TimeoutException {
        for (int i = 0; i < 20; i++) {
            int pixel = getPixel(point);
            if (pixel != color) {
                return pixel;
            }
            Thread.sleep(16);
        }
        throw new TimeoutException();
    }
}