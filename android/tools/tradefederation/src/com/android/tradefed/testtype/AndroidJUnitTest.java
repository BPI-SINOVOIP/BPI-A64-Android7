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

package com.android.tradefed.testtype;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * A Test that runs an instrumentation test package on given device using the
 * android.support.test.runner.AndroidJUnitRunner.
 */
public class AndroidJUnitTest extends InstrumentationTest implements IRuntimeHintProvider,
        ITestFilterReceiver {

    private static final String AJUR = "android.support.test.runner.AndroidJUnitRunner";

    /** instrumentation test runner argument key used for including a class/test */
    private static final String INCLUDE_CLASS_INST_ARGS_KEY = "class";
    /** instrumentation test runner argument key used for excluding a class/test */
    private static final String EXCLUDE_CLASS_INST_ARGS_KEY = "notClass";
    /** instrumentation test runner argument key used for including a package */
    private static final String INCLUDE_PACKAGE_INST_ARGS_KEY = "package";
    /** instrumentation test runner argument key used for excluding a package */
    private static final String EXCLUDE_PACKAGE_INST_ARGS_KEY = "notPackage";

    @Option(name = "runtime-hint",
            isTimeVal=true,
            description="The hint about the test's runtime.")
    private long mRuntimeHint = 60000;// 1 minute

    @Option(name = "include-filter",
            description="The include filters of the test name to run.")
    private List<String> mIncludeFilters = new ArrayList<>();

    @Option(name = "exclude-filter",
            description="The exclude filters of the test name to run.")
    private List<String> mExcludeFilters = new ArrayList<>();

    public AndroidJUnitTest() {
        super();
        // Set the runner to AJUR, this can still be overwritten by the optionsetter/optioncopier
        setRunnerName(AJUR);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRuntimeHint() {
        return mRuntimeHint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllIncludeFilters(List<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllExcludeFilters(List<String> filters) {
        mExcludeFilters.addAll(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setRunnerArgs(IRemoteAndroidTestRunner runner) {
        super.setRunnerArgs(runner);
        // Split filters into class, notClass, package and notPackage
        List<String> classArg = new ArrayList<String>();
        List<String> notClassArg = new ArrayList<String>();
        List<String> packageArg = new ArrayList<String>();
        List<String> notPackageArg = new ArrayList<String>();
        for (String test : mIncludeFilters) {
            if (isClassOrMethod(test)) {
                classArg.add(test);
            } else {
                packageArg.add(test);
            }
        }
        for (String test : mExcludeFilters) {
            if (isClassOrMethod(test)) {
                notClassArg.add(test);
            } else {
                notPackageArg.add(test);
            }
        }
        if (!classArg.isEmpty()) {
            runner.addInstrumentationArg(INCLUDE_CLASS_INST_ARGS_KEY,
                    ArrayUtil.join(",", classArg));
        }
        if (!notClassArg.isEmpty()) {
            runner.addInstrumentationArg(EXCLUDE_CLASS_INST_ARGS_KEY,
                    ArrayUtil.join(",", notClassArg));
        }
        if (!packageArg.isEmpty()) {
            runner.addInstrumentationArg(INCLUDE_PACKAGE_INST_ARGS_KEY,
                    ArrayUtil.join(",", packageArg));
        }
        if (!notPackageArg.isEmpty()) {
            runner.addInstrumentationArg(EXCLUDE_PACKAGE_INST_ARGS_KEY,
                    ArrayUtil.join(",", notPackageArg));
        }
    }

    /**
     * @VisibleForTesting
     */
    public boolean isClassOrMethod(String filter) {
        if (filter.contains("#")) {
            return true;
        }
        String[] parts = filter.split("\\.");
        if (parts.length > 0) {
            // FIXME Assume java package names starts with lowercase and class names start with
            // uppercase.
            // Return true iff the first character of the last word is uppercase
            // com.android.foobar.Test
            return Character.isUpperCase(parts[parts.length - 1].charAt(0));
        }
        return false;
    }
}
