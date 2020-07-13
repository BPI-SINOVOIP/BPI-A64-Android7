/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.hierarchyviewerlib.models;

import com.android.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * This class manages the resource data that was dumped from a View's Theme.
 */
public class ThemeModel {

    private List<ThemeModelData> data;

    public ThemeModel() {
        data = new ArrayList<ThemeModelData>();
    }

    public void add(@NonNull String name, @NonNull String value) {
        data.add(new ThemeModelData(name, value));
    }

    public List<ThemeModelData> getData() {
        return data;
    }

    public class ThemeModelData {
        private String mName;
        private String mValue;

        public ThemeModelData(@NonNull String name, @NonNull String value) {
            mName = name;
            mValue = value;
        }

        public String getName() {
            return mName;
        }

        public String getValue() {
            return mValue;
        }
    }
}