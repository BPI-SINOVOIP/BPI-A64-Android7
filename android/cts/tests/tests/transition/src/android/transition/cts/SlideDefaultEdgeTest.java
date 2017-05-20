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
package android.transition.cts;

import android.support.test.rule.ActivityTestRule;
import android.test.suitebuilder.annotation.SmallTest;
import android.transition.Slide;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SlideDefaultEdgeTest {
    @Test
    @SmallTest
    public void testDefaultSide() {
        // default to bottom
        Slide slide = new Slide();
        assertEquals(Gravity.BOTTOM, slide.getSlideEdge());
    }
}

