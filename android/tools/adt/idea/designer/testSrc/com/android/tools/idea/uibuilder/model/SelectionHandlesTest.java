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
package com.android.tools.idea.uibuilder.model;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import junit.framework.TestCase;

import java.util.List;

import static org.mockito.Mockito.mock;

public class SelectionHandlesTest extends TestCase {
  public void test() {
    NlComponent component = mock(NlComponent.class);
    component.x = 100;
    component.y = 110;
    component.w = 500;
    component.h = 400;

    SelectionHandles handles = new SelectionHandles(component);
    List<SelectionHandle> handleList = Lists.newArrayList();
    Iterators.addAll(handleList, handles.iterator());
    assertEquals(8, handleList.size());

    SelectionHandle handle = handles.findHandle(100, 110, 2);
    assertNotNull(handle);
    assertEquals(SelectionHandle.Position.TOP_LEFT, handle.getPosition());

    handle = handles.findHandle(100 + 500 / 2, 110, 2);
    assertNotNull(handle);
    assertEquals(SelectionHandle.Position.TOP_MIDDLE, handle.getPosition());

    handle = handles.findHandle(100 + 500, 110, 2);
    assertNotNull(handle);
    assertEquals(SelectionHandle.Position.TOP_RIGHT, handle.getPosition());

    handle = handles.findHandle(100 + 500, 110 + 400 / 2, 2);
    assertNotNull(handle);
    assertEquals(SelectionHandle.Position.RIGHT_MIDDLE, handle.getPosition());

    handle = handles.findHandle(100 + 500, 110 + 400, 2);
    assertNotNull(handle);
    assertEquals(SelectionHandle.Position.BOTTOM_RIGHT, handle.getPosition());

    handle = handles.findHandle(100 + 500 / 2, 110 + 400, 2);
    assertNotNull(handle);
    assertEquals(SelectionHandle.Position.BOTTOM_MIDDLE, handle.getPosition());

    handle = handles.findHandle(100, 110 + 400, 2);
    assertNotNull(handle);
    assertEquals(SelectionHandle.Position.BOTTOM_LEFT, handle.getPosition());

    handle = handles.findHandle(100, 110 + 400 / 2, 2);
    assertNotNull(handle);
    assertEquals(SelectionHandle.Position.LEFT_MIDDLE, handle.getPosition());

    handle = handles.findHandle(300, 300, 2);
    assertNull(handle);
  }
}