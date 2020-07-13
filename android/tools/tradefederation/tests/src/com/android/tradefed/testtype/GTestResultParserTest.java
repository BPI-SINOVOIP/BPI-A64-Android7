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
package com.android.tradefed.testtype;

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Vector;


/**
 * Unit tests for {@link GTestResultParserTest}.
 */
public class GTestResultParserTest extends TestCase {
    private static final String TEST_TYPE_DIR = "testtype";
    private static final String TEST_MODULE_NAME = "module";
    private static final String GTEST_OUTPUT_FILE_1 = "gtest_output1.txt";
    private static final String GTEST_OUTPUT_FILE_2 = "gtest_output2.txt";
    private static final String GTEST_OUTPUT_FILE_3 = "gtest_output3.txt";
    private static final String GTEST_OUTPUT_FILE_4 = "gtest_output4.txt";
    private static final String GTEST_OUTPUT_FILE_5 = "gtest_output5.txt";
    private static final String GTEST_OUTPUT_FILE_6 = "gtest_output6.txt";
    private static final String GTEST_OUTPUT_FILE_7 = "gtest_output7.txt";
    private static final String LOG_TAG = "GTestResultParserTest";

    /**
     * Helper to read a file from the res/testtype directory and return its contents as a String[]
     *
     * @param filename the name of the file (without the extension) in the res/testtype directory
     * @return a String[] of the
     */
    private String[] readInFile(String filename) {
        Vector<String> fileContents = new Vector<String>();
        try {
            InputStream gtestResultStream1 = getClass().getResourceAsStream(File.separator +
                    TEST_TYPE_DIR + File.separator + filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(gtestResultStream1));
            String line = null;
            while ((line = reader.readLine()) != null) {
                fileContents.add(line);
            }
        }
        catch (NullPointerException e) {
            Log.e(LOG_TAG, "Gest output file does not exist: " + filename);
        }
        catch (IOException e) {
            Log.e(LOG_TAG, "Unable to read contents of gtest output file: " + filename);
        }
        return fileContents.toArray(new String[fileContents.size()]);
    }

    /**
     * Tests the parser for a simple test run output with 11 tests.
     */
    @SuppressWarnings("unchecked")
    public void testParseSimpleFile() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_1);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 11);
        // 11 passing test cases in this run
        for (int i=0; i<11; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        // TODO: validate param values
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
    }

    /**
     * Tests the parser for a simple test run output with 53 tests and no times.
     */
    @SuppressWarnings("unchecked")
    public void testParseSimpleFileNoTimes() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_2);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 53);
        // 53 passing test cases in this run
        for (int i=0; i<53; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        // TODO: validate param values
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
    }

    /**
     * Tests the parser for a simple test run output with 0 tests and no times.
     */
    @SuppressWarnings("unchecked")
    public void testParseNoTests() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_3);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 0);
        // TODO: validate param values
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
    }

    /**
     * Tests the parser for a run with 268 tests.
     */
    @SuppressWarnings("unchecked")
    public void testParseLargerFile() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_4);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 268);
        // 268 passing test cases in this run
        for (int i=0; i<268; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        // TODO: validate param values
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
    }

    /**
     * Tests the parser for a run with test failures.
     */
    @SuppressWarnings("unchecked")
    public void testParseWithFailures() throws Exception {
        String MESSAGE_OUTPUT =
                "This is some random text that should get captured by the parser.";
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_5);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        // 13 test cases in this run
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 13);
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        // test failure
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        // 4 passing tests
        for (int i=0; i<4; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        // 2 consecutive test failures
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());

        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), EasyMock.matches(MESSAGE_OUTPUT));
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());

        // 5 passing tests
        for (int i=0; i<5; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }

        // TODO: validate param values
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        EasyMock.verify(mockRunListener);
    }

    /**
     * Tests the parser for a run with test errors.
     */
    @SuppressWarnings("unchecked")
    public void testParseWithErrors() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_6);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        // 10 test cases in this run
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 10);
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        // test failure
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        // 5 passing tests
        for (int i=0; i<5; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        // another test error
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        // 2 passing tests
        for (int i=0; i<2; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }

        // TODO: validate param values
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        EasyMock.verify(mockRunListener);
    }

    /**
     * Tests the parser for a run with 11 tests.
     */
    @SuppressWarnings("unchecked")
    public void testParseNonAlignedTag() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_7);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 11);
        // 11 passing test cases in this run
        for (int i=0; i<11; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
    }
}
