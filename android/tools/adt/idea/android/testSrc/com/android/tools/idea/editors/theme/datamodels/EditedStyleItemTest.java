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
package com.android.tools.idea.editors.theme.datamodels;

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeResolver;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.junit.Test;

import java.util.List;

public class EditedStyleItemTest extends AndroidTestCase {
  @Test
  public void testValueSetters() {
    // Do a simple instantiation and check that the setters update the right value

    VirtualFile myLayout = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml");
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myLayout);

    // We just get a theme that we use as fake source to pass to the ConfiguredItemResourceValue constructor
    ThemeEditorStyle fakeTheme = ResolutionUtils.getStyle(configuration, "@android:style/Theme", null);
    assertNotNull(fakeTheme);

    //noinspection ConstantConditions
    List<ConfiguredItemResourceValue> items = ImmutableList.of(
      new ConfiguredItemResourceValue(FolderConfiguration.getConfigForFolder("values-v21"),
                                                      new ItemResourceValue("attribute", false, "otherValue", false), fakeTheme)
    );

    EditedStyleItem editedStyleItem = new EditedStyleItem(
      new ConfiguredItemResourceValue(new FolderConfiguration(), new ItemResourceValue("attribute", false, "selectedValue", false), fakeTheme),
      items, fakeTheme);

    assertEquals("selectedValue", editedStyleItem.getValue());
    assertEquals("selectedValue", editedStyleItem.getSelectedValue().getValue());
    assertEquals(1, editedStyleItem.getNonSelectedItemResourceValues().size());
    ConfiguredItemResourceValue notSelectedItem = editedStyleItem.getNonSelectedItemResourceValues().iterator().next();
    assertEquals("otherValue", notSelectedItem.myValue.getValue());
  }
}