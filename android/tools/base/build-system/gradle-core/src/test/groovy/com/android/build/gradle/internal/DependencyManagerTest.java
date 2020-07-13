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

package com.android.build.gradle.internal;

import static org.junit.Assert.*;

import com.android.utils.ILogger;
import com.android.utils.StdLogger;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.junit.Test;

public class DependencyManagerTest {

    @Test
    public void testNormalize() throws Exception {
        ModuleVersionIdentifier moduleVersionIdentifier = new DefaultModuleVersionIdentifier(
                "group", "name", "1.2");
        ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
        assertEquals("app", DependencyManager.normalize(logger, moduleVersionIdentifier, "app"));
        assertEquals(".app", DependencyManager.normalize(logger, moduleVersionIdentifier, ".app"));
        assertEquals("app@", DependencyManager.normalize(logger, moduleVersionIdentifier, "app."));
        assertEquals("app@", DependencyManager.normalize(logger, moduleVersionIdentifier, "app "));
        assertEquals("app@@@", DependencyManager.normalize(logger, moduleVersionIdentifier, "app..."));
        assertEquals("app@@@", DependencyManager.normalize(logger, moduleVersionIdentifier, "app. ."));
        assertEquals("app@@@@", DependencyManager.normalize(logger, moduleVersionIdentifier, "app. . "));
        assertEquals("a@", DependencyManager.normalize(logger, moduleVersionIdentifier, "a."));
        assertEquals("a@", DependencyManager.normalize(logger, moduleVersionIdentifier, "a "));
        assertEquals("a@@@", DependencyManager.normalize(logger, moduleVersionIdentifier, "a..."));
        assertEquals(".app@@", DependencyManager.normalize(logger, moduleVersionIdentifier, ".app%%"));
        assertEquals("app.txt", DependencyManager.normalize(logger, moduleVersionIdentifier, "app.txt"));
        assertEquals("app@@@txt", DependencyManager.normalize(logger, moduleVersionIdentifier, "app%*?txt"));
        assertEquals("", DependencyManager.normalize(logger, moduleVersionIdentifier, ""));
        assertEquals("a", DependencyManager.normalize(logger, moduleVersionIdentifier, "a"));
        assertEquals("1", DependencyManager.normalize(logger, moduleVersionIdentifier, "1"));
        assertNull(DependencyManager.normalize(logger, moduleVersionIdentifier, null));

        // those will generate an exception and return the original value
        assertEquals(".", DependencyManager.normalize(logger, moduleVersionIdentifier, "."));
        assertEquals("..", DependencyManager.normalize(logger, moduleVersionIdentifier, ".."));
        assertEquals("...", DependencyManager.normalize(logger, moduleVersionIdentifier, "..."));
    }
}