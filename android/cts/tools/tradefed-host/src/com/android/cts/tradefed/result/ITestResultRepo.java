/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.cts.tradefed.result;

import java.io.File;
import java.util.List;

/**
 * Repository for CTS results.
 */
public interface ITestResultRepo {

    /**
     * @return the list of {@link ITestSummary}s. The index of the {@link ITestSummary} in the
     * list is its session id
     */
    public List<ITestSummary> getSummaries();

    /**
     * Get the {@link TestResults} for given session id.
     *
     * @param sessionId the session id
     * @return the {@link TestResults} or <code>null</null> if the result with that session id
     * cannot be retrieved
     */
    public TestResults getResult(int sessionId);

    /**
     * Get the report directory for given result
     * @param sessionId
     * @return A {@link File} representing the report directory for the given sessionId
     */
    public File getReportDir(int sessionId);

}
