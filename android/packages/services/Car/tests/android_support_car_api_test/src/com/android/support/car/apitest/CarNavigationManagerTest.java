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
package com.android.support.car.apitest;

import static android.support.car.CarAppContextManager.APP_CONTEXT_NAVIGATION;

import android.os.Looper;
import android.support.car.Car;
import android.support.car.CarAppContextManager;
import android.support.car.CarAppContextManager.AppContextChangeListener;
import android.support.car.CarAppContextManager.AppContextOwnershipChangeListener;
import android.support.car.navigation.CarNavigationInstrumentCluster;
import android.support.car.CarNotConnectedException;
import android.support.car.navigation.CarNavigationManager;
import android.support.car.navigation.CarNavigationManager.CarNavigationListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link android.support.car.navigation.CarNavigationStatusManager}
 */
public class CarNavigationManagerTest extends CarApiTestBase {

    private CarNavigationManager mCarNavigationManager;
    private CarAppContextManager mCarAppContextManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCarNavigationManager =
                (CarNavigationManager) getCar().getCarManager(Car.CAR_NAVIGATION_SERVICE);
        assertNotNull(mCarNavigationManager);
        mCarAppContextManager =
                (CarAppContextManager) getCar().getCarManager(Car.APP_CONTEXT_SERVICE);
        assertNotNull(mCarAppContextManager);
    }

    public void testStart() throws Exception {
        final CountDownLatch onStartLatch = new CountDownLatch(1);

        mCarNavigationManager.registerListener(new CarNavigationListener() {
            @Override
            public void onInstrumentClusterStart(CarNavigationInstrumentCluster instrumentCluster) {
                // TODO: we should use VehicleHalMock once we implement HAL support in
                // CarNavigationStatusService.
                assertFalse(instrumentCluster.supportsCustomImages());
                assertEquals(1000, instrumentCluster.getMinIntervalMs());
                onStartLatch.countDown();
            }

            @Override
            public void onInstrumentClusterStop() {
              // TODO
            }
        });

        assertTrue(onStartLatch.await(DEFAULT_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        try {
            mCarNavigationManager.sendNavigationStatus(1);
            fail();
        } catch (IllegalStateException expected) {
            // Expected. Client should acquire context ownership for APP_CONTEXT_NAVIGATION.
        }

        mCarAppContextManager.registerContextListener(new AppContextChangeListener() {
            @Override
            public void onAppContextChange(int activeContexts) {
                // Nothing to do here.
            }
        }, APP_CONTEXT_NAVIGATION);
        mCarAppContextManager.setActiveContexts(new AppContextOwnershipChangeListener() {
            @Override
            public void onAppContextOwnershipLoss(int context) {
                // Nothing to do here.
            }
        }, APP_CONTEXT_NAVIGATION);
        assertTrue(mCarAppContextManager.isOwningContext(APP_CONTEXT_NAVIGATION));

        // TODO: we should use mocked HAL to be able to verify this, right now just make sure that
        // it is not crashing and logcat has appropriate traces.
        mCarNavigationManager.sendNavigationStatus(1);
    }
}
