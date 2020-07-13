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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NlPropertiesTest extends LayoutTestCase {
  private static final String[] VIEW_ATTRS = {"id", "padding", "visibility", "textAlignment", "translationZ", "elevation", "style"};
  private static final String[] TEXTVIEW_ATTRS = {"text", "hint", "textColor", "textSize"};

  private static final String[] FRAMELAYOUT_ATTRS = {"layout_gravity"};
  private static final String[] GRIDLAYOUT_ATTRS = {"layout_rowSpan", "layout_column"};
  private static final String[] LINEARLAYOUT_ATTRS = {"layout_weight"};
  private static final String[] RELATIVELAYOUT_ATTRS = {"layout_toLeftOf", "layout_above", "layout_alignTop"};

  public void testViewAttributes() {
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<View xmlns:android=\"http://schemas.android.com/apk/res/android\" />";
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", source);
    String tag = "View";

    List<NlProperty> properties = NlProperties.getInstance().getProperties(MockNlComponent.create(xmlFile.getRootTag()));
    assertTrue(properties.size() > 120); // at least 124 attributes (view + layouts) are available as of API 22

    // check that some of the View's attributes are there..
    assertPresent(tag, properties, VIEW_ATTRS);

    // check that non-existent properties haven't been added
    assertAbsent(tag, properties, TEXTVIEW_ATTRS);

    // Views that don't have a parent layout have all the layout attributes available to them..
    assertPresent(tag, properties, RELATIVELAYOUT_ATTRS);
    assertPresent(tag, properties, GRIDLAYOUT_ATTRS);
    assertPresent(tag, properties, FRAMELAYOUT_ATTRS);
  }

  public void testViewInRelativeLayout() {
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" >" +
                    "  <TextView />" +
                    "</RelativeLayout>";
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", source);
    String tag = "TextView";

    XmlTag[] subTags = xmlFile.getRootTag().getSubTags();
    assertEquals(1, subTags.length);

    List<NlProperty> properties = NlProperties.getInstance().getProperties(MockNlComponent.create(subTags[0]));
    assertTrue(properties.size() > 180); // at least 190 attributes are available as of API 22

    // A textview should have all of its attributes and the parent class's (View) attributes
    assertPresent(tag, properties, TEXTVIEW_ATTRS);
    assertPresent(tag, properties, VIEW_ATTRS);

    // Since it is embedded inside a relative layout, it should only have relative layout's layout attributes
    assertPresent(tag, properties, RELATIVELAYOUT_ATTRS);
    assertAbsent(tag, properties, GRIDLAYOUT_ATTRS);
    assertAbsent(tag, properties, FRAMELAYOUT_ATTRS);
  }

  public void testCustomViewAttributes() {
    XmlFile xmlFile = setupCustomViewProject();

    String tag = "p1.p2.PieChart";

    XmlTag[] subTags = xmlFile.getRootTag().getSubTags();
    assertEquals(1, subTags.length);

    List<NlProperty> properties = NlProperties.getInstance().getProperties(MockNlComponent.create(subTags[0]));
    assertTrue("# of properties lesser than expected: " + properties.size(), properties.size() > 90);

    assertPresent(tag, properties, VIEW_ATTRS);
    assertPresent(tag, properties, LINEARLAYOUT_ATTRS);
    assertAbsent(tag, properties, TEXTVIEW_ATTRS);
  }

  public void testPropertyNames() {
    XmlFile xmlFile = setupCustomViewProject();
    XmlTag[] subTags = xmlFile.getRootTag().getSubTags();
    assertEquals(1, subTags.length);

    List<NlProperty> properties = NlProperties.getInstance().getProperties(MockNlComponent.create(subTags[0]));

    NlProperty p = getPropertyByName(properties, "id");
    assertNotNull(p);

    assertEquals("id", p.getName());
    assertEquals("@android:id", p.getTooltipText());

    p = getPropertyByName(properties, "legend");
    assertNotNull(p);

    assertEquals("legend", p.getName());
    assertEquals("legend", p.getTooltipText());
  }

  private XmlFile setupCustomViewProject() {
    @Language("XML")
    String layoutSrc = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" >" +
                    "  <p1.p2.PieChart />" +
                    "</LinearLayout>";

    @Language("XML")
    String attrsSrc = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                      "<resources>\n" +
                      "    <declare-styleable name=\"PieChart\">\n" +
                      "        <attr name=\"legend\" format=\"boolean\" />\n" +
                      "        <attr name=\"labelPosition\" format=\"enum\">\n" +
                      "            <enum name=\"left\" value=\"0\"/>\n" +
                      "            <enum name=\"right\" value=\"1\"/>\n" +
                      "        </attr>\n" +
                      "    </declare-styleable>\n" +
                      "</resources>";

    @Language("JAVA")
    String javaSrc = "package p1.p2;\n" +
                      "\n" +
                      "import android.content.Context;\n" +
                      "import android.view.View;\n" +
                      "\n" +
                      "public class PieChart extends View {\n" +
                      "    public PieChart(Context context) {\n" +
                      "        super(context);\n" +
                      "    }\n" +
                      "}\n";

    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", layoutSrc);
    myFixture.addFileToProject("res/values/attrs.xml", attrsSrc);
    myFixture.addFileToProject("src/p1/p2/PieChart.java", javaSrc);
    return xmlFile;
  }

  public void testAppCompatIssues() {
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" >" +
                    "  <TextView />" +
                    "</RelativeLayout>";
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", source);

    @Language("XML")
    String attrsSrc = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                      "<resources>\n" +
                      "    <declare-styleable name=\"View\">\n" +
                      "        <attr name=\"android:focusable\" />\n" +
                      "        <attr name=\"theme\" format=\"reference\" />\n" +
                      "        <attr name=\"android:theme\" />\n" +
                      "        <attr name=\"custom\" />\n" +
                      "    </declare-styleable>\n" +
                      "</resources>";
    myFixture.addFileToProject("res/values/attrs.xml", attrsSrc);

    XmlTag[] subTags = xmlFile.getRootTag().getSubTags();
    assertEquals(1, subTags.length);

    List<NlProperty> properties = NlProperties.getInstance().getProperties(MockNlComponent.create(subTags[0]));
    assertTrue(properties.size() > 180); // at least 190 attributes are available as of API 22

    // The attrs.xml in appcompat-22.0.0 includes android:focusable, theme and android:theme.
    // The android:focusable refers to the platform attribute, and hence should not be duplicated..
    assertPresent("TextView", properties, "focusable", "theme", "custom");
    assertAbsent("TextView", properties, "android:focusable", "android:theme");
  }

  @Nullable
  public static NlProperty getPropertyByName(@NotNull List<NlProperty> properties, @NotNull String name) {
    for (NlProperty property : properties) {
      if (name.equals(property.getName())) {
        return property;
      }
    }

    return null;
  }

  private static void assertPresent(String tag, List<NlProperty> properties, String... names) {
    for (String n : names) {
      assertNotNull("Missing attribute " + n + " for " + tag, getPropertyByName(properties, n));
    }
  }

  private static void assertAbsent(String tag, List<NlProperty> properties, String... names) {
    for (String n : names) {
      assertNull("Attribute " + n + " not applicable for a " + tag, getPropertyByName(properties, n));
    }
  }
}
