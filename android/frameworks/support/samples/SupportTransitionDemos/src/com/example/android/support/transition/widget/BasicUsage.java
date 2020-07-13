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

package com.example.android.support.transition.widget;

import com.example.android.support.transition.R;

import android.support.transition.Scene;
import android.support.transition.TransitionManager;
import android.view.ViewGroup;

/**
 * This demonstrates basic usage of the Transition API.
 */
public class BasicUsage extends TransitionUsageBase {

    @Override
    Scene[] setUpScenes(ViewGroup root) {
        return new Scene[]{
                Scene.getSceneForLayout(root, R.layout.scene0, this),
                Scene.getSceneForLayout(root, R.layout.scene1, this),
        };
    }

    @Override
    void go(Scene scene) {
        TransitionManager.go(scene);
    }

}
