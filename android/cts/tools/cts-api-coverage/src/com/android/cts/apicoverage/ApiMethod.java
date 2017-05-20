/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.cts.apicoverage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Representation of a method in the API with parameters (arguments) and a return value. */
class ApiMethod implements Comparable<ApiMethod> {

    private final String mName;

    private final List<String> mParameterTypes;

    private final String mReturnType;

    private final boolean mDeprecated;

    private final String mVisibility;

    private final boolean mStaticMethod;

    private final boolean mFinalMethod;

    private final boolean mAbstractMethod;

    private boolean mIsCovered;

    ApiMethod(
            String name,
            List<String> parameterTypes,
            String returnType,
            boolean deprecated,
            String visibility,
            boolean staticMethod,
            boolean finalMethod,
            boolean abstractMethod) {
        mName = name;
        mParameterTypes = new ArrayList<String>(parameterTypes);
        mReturnType = returnType;
        mDeprecated = deprecated;
        mVisibility = visibility;
        mStaticMethod = staticMethod;
        mFinalMethod = finalMethod;
        mAbstractMethod = abstractMethod;
    }

    @Override
    public int compareTo(ApiMethod another) {
        return mName.compareTo(another.mName);
    }

    public String getName() {
        return mName;
    }

    public List<String> getParameterTypes() {
        return Collections.unmodifiableList(mParameterTypes);
    }

    public String getReturnType() {
        return mReturnType;
    }

    public boolean isDeprecated() {
        return mDeprecated;
    }

    public boolean isCovered() {
        return mIsCovered;
    }

    public String getVisibility() { return mVisibility; }

    public boolean isAbstractMethod() { return mAbstractMethod; }

    public boolean isStaticMethod() { return mStaticMethod; }

    public boolean isFinalMethod() { return mFinalMethod; }

    public void setCovered(boolean covered) {
        mIsCovered = covered;
    }
}