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
 * limitations under the License
 */

package com.android.incallui;

import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.incallui.InCallPresenter.InCallState;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@MediumTest
public class ProximitySensorTest extends InstrumentationTestCase {
    @Mock private AccelerometerListener mAccelerometerListener;
    private MockCallListWrapper mCallList;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        System.setProperty("dexmaker.dexcache",
                getInstrumentation().getTargetContext().getCacheDir().getPath());
        MockitoAnnotations.initMocks(this);
        mCallList = new MockCallListWrapper();
    }

    public void testAccelerometerBehaviorOnDisplayChange() {
        final ProximitySensor proximitySensor =
                new ProximitySensor(
                        getInstrumentation().getContext(),
                        new AudioModeProvider(),
                        mAccelerometerListener);
        verify(mAccelerometerListener, never()).enable(anyBoolean());
        proximitySensor.onStateChange(null, InCallState.OUTGOING, mCallList.getCallList());
        verify(mAccelerometerListener).enable(true);
        verify(mAccelerometerListener, never()).enable(false);

        proximitySensor.onDisplayStateChanged(false);
        verify(mAccelerometerListener).enable(true);
        verify(mAccelerometerListener).enable(false);

        proximitySensor.onDisplayStateChanged(true);
        verify(mAccelerometerListener, times(2)).enable(true);
        verify(mAccelerometerListener).enable(false);
    }
}
