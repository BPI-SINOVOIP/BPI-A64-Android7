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

package com.android.cts.tradefed.testtype;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.util.xml.AbstractXmlParser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

/**
 * Interface for accessing test plan data.
 */
public interface ITestPlan {

    /**
     * Populates the test plan data from given XML stream.
     *
     * @param xmlStream the {@link InputStream} that contains the test plan xml.
     */
    public void parse(InputStream xmlStream) throws ParseException;

    /**
     * Gets a sorted list of test ids contained in this plan.
     */
    public Collection<String> getTestIds();

    /**
     * Gets a sorted {@link List} of test names contained in this plan.
     */
    public List<String> getTestNames();

    /**
     * Gets the {@link TestFilter} that should be used to filter tests from given package.
     */
    public TestFilter getTestFilter(String id);

    /**
     * Add a package to this test plan
     * @param id
     */
    public void addPackage(String id);

    /**
     * Add a excluded test to this test plan
     *
     * @param id the package id
     * @param testToExclude the test to exclude for given package
     */
    public void addExcludedTest(String id, TestIdentifier testToExclude);

    /**
     * Adds the list of excluded tests for given package
     *
     * @param id
     * @param excludedTests
     */
    public void addExcludedTests(String id, Collection<TestIdentifier> excludedTests);

    /**
     * Serialize the contents of this test plan.
     *
     * @param xmlOutStream the {@link OutputStream} to serialize test plan contents to
     * @throws IOException
     */
    public void serialize(OutputStream xmlOutStream) throws IOException;

    /**
     * @return the test plan name
     */
    public String getName();

}
