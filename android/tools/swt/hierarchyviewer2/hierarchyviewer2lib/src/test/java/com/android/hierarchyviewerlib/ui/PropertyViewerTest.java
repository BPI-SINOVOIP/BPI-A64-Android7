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

package com.android.hierarchyviewerlib.ui;

import junit.framework.TestCase;

public class PropertyViewerTest extends TestCase {

    public void testExistingCases() {
        assertEquals("alpha", PropertyViewer.parseColumnTextName("drawing:getAlpha()"));
        assertEquals("x", PropertyViewer.parseColumnTextName("drawing:getX()"));
    }

    public void testEdgeCases() {
        assertEquals("alpha", PropertyViewer.parseColumnTextName("foo:alpha"));
        assertEquals("x", PropertyViewer.parseColumnTextName("foo:x"));

        assertEquals("get", PropertyViewer.parseColumnTextName("foo:get"));
        assertEquals("", PropertyViewer.parseColumnTextName("foo:()"));
        assertEquals("", PropertyViewer.parseColumnTextName("foo:"));

        assertEquals("getter", PropertyViewer.parseColumnTextName("foo:getter"));
        assertEquals("together", PropertyViewer.parseColumnTextName("foo:together"));
        assertEquals("together", PropertyViewer.parseColumnTextName("foo:getTogether"));
        assertEquals("()get", PropertyViewer.parseColumnTextName("foo:()get"));
    }
}
