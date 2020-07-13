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
package com.android.tools.idea.editors.strings;

import com.android.SdkConstants;
import com.android.ide.common.res2.ResourceItem;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.ModuleResourceRepository;
import com.google.common.base.Function;
import com.google.common.collect.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StringResourceDataTest extends AndroidTestCase {
  public void testSummarizeLocales() {
    assertEquals("", StringResourceData.summarizeLocales(Collections.<Locale>emptySet()));

    List<Locale> locales = Lists.newArrayList(Locale.create("fr"), Locale.create("en"));
    assertEquals("English (en) and French (fr)", StringResourceData.summarizeLocales(locales));

    locales = Lists.newArrayList(Locale.create("en"), Locale.create("fr"), Locale.create("hi"));
    assertEquals("English (en), French (fr) and Hindi (hi)", StringResourceData.summarizeLocales(locales));

    locales = Lists.newArrayList(Locale.create("en"), Locale.create("fr"), Locale.create("hi"), Locale.create("no"));
    assertEquals("English (en), French (fr), Hindi (hi) and 1 more", StringResourceData.summarizeLocales(locales));

    locales = Lists.newArrayList(Locale.create("en"), Locale.create("fr"), Locale.create("hi"), Locale.create("no"), Locale.create("ta"),
                                 Locale.create("es"), Locale.create("ro"));
    assertEquals("English (en), French (fr), Hindi (hi) and 4 more", StringResourceData.summarizeLocales(locales));
  }

  public void testParser() {
    VirtualFile res = myFixture.copyDirectoryToProject("stringsEditor/base/res", "res");
    ModuleResourceRepository repository = ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res));
    StringResourceData data = StringResourceParser.parse(myFacet, repository);

    Set<String> locales = Sets.newHashSet(Iterables.transform(data.getLocales(), new Function<Locale, String>() {
      @Override
      public String apply(Locale input) {
        return input.toLocaleId();
      }
    }));
    assertSameElements(locales, ImmutableSet.of("en", "en-GB", "en-IN", "fr", "hi"));

    Map<String, ResourceItem> defaultValues = data.getDefaultValues();
    assertEquals(5, defaultValues.size());
    assertContainsElements(defaultValues.keySet(), ImmutableSet.of("key1", "key2", "key3", "key5"));

    Set<String> untranslatableKeys = data.getUntranslatableKeys();
    assertSameElements(untranslatableKeys, Lists.newArrayList("key5", "key6"));

    Table<String, Locale, ResourceItem> translations = data.getTranslations();
    assertNull(translations.get("key1", Locale.create("hi")));
    assertEquals("Key 2 hi", StringResourceData.resourceToString(translations.get("key2", Locale.create("hi"))));
  }

  public void testValidation() {
    VirtualFile res = myFixture.copyDirectoryToProject("stringsEditor/base/res", "res");
    ModuleResourceRepository repository = ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res));
    StringResourceData data = StringResourceParser.parse(myFacet, repository);

    assertEquals("Key 'key1' has translations missing for locales French (fr) and Hindi (hi)", data.validateKey("key1"));
    assertNull(data.validateKey("key2"));
    assertNull(data.validateKey("key3"));
    assertEquals("Key 'key4' missing default value", data.validateKey("key4"));
    assertNull(data.validateKey("key5"));
    assertEquals("Key 'key6' is marked as non translatable, but is translated in locale French (fr)", data.validateKey("key6"));

    assertEquals("Key 'key1' is missing Hindi (hi) translation", data.validateTranslation("key1", Locale.create("hi")));
    assertNull(data.validateTranslation("key2", Locale.create("hi")));
    assertEquals("Key 'key6' is marked as non-localizable, and should not be translated to French (fr)",
                 data.validateTranslation("key6", Locale.create("fr")));

    assertNull(data.validateTranslation("key1", null));
    assertEquals("Key 'key4' is missing the default value", data.validateTranslation("key4", null));
  }

  public void testEditingDoNotTranslate() {
    VirtualFile res = myFixture.copyDirectoryToProject("stringsEditor/base/res", "res");
    ModuleResourceRepository repository = ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res));
    StringResourceData data = StringResourceParser.parse(myFacet, repository);

    final VirtualFile stringsFile = res.findFileByRelativePath("values/strings.xml");
    assertNotNull(stringsFile);

    assertFalse(data.getUntranslatableKeys().contains("key1"));
    XmlTag tag = getNthXmlTag(stringsFile, "string", 0);
    assertEquals("key1", tag.getAttributeValue(SdkConstants.ATTR_NAME));
    assertNull(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE));

    data.setDoNotTranslate("key1", true);

    assertTrue(data.getUntranslatableKeys().contains("key1"));
    tag = getNthXmlTag(stringsFile, "string", 0);
    assertEquals(SdkConstants.VALUE_FALSE, tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE));

    assertTrue(data.getUntranslatableKeys().contains("key5"));
    tag = getNthXmlTag(stringsFile, "string", 3);
    assertEquals("key5", tag.getAttributeValue(SdkConstants.ATTR_NAME));
    assertEquals(SdkConstants.VALUE_FALSE, tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE));

    data.setDoNotTranslate("key5", false);

    assertFalse(data.getUntranslatableKeys().contains("key5"));
    tag = getNthXmlTag(stringsFile, "string", 3);
    assertNull(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE));
  }

  public void testEditingCdata() {
    VirtualFile res = myFixture.copyDirectoryToProject("stringsEditor/base/res", "res");
    ModuleResourceRepository repository = ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res));
    StringResourceData data = StringResourceParser.parse(myFacet, repository);

    final Locale locale = Locale.create("en-rIN");
    final String key = "key1";

    String currentData = StringResourceData.resourceToString(data.getTranslations().get(key, locale));
    assertEquals("<![CDATA[\n" +
                 "        <b>Google I/O 2014</b><br>\n" +
                 "        Version %s<br><br>\n" +
                 "        <a href=\"http://www.google.com/policies/privacy/\">Privacy Policy</a>\n" +
                 "  ]]>", currentData);
    assertTrue(data.setTranslation(key, locale, currentData.replace("%s", "%1$s")));

    final String expected = "<![CDATA[\n" +
                            "        <b>Google I/O 2014</b><br>\n" +
                            "        Version %1$s<br><br>\n" +
                            "        <a href=\"http://www.google.com/policies/privacy/\">Privacy Policy</a>\n" +
                            "  ]]>";
    assertEquals(expected, StringResourceData.resourceToString(data.getTranslations().get(key, locale)));
    XmlTag tag = getNthXmlTag(res.findFileByRelativePath("values-en-rIN/strings.xml"), "string", 0);
    assertEquals("key1", tag.getAttributeValue(SdkConstants.ATTR_NAME));
    assertEquals(expected, tag.getValue().getText());
  }

  public void testAddingTranslation() {
    VirtualFile res = myFixture.copyDirectoryToProject("stringsEditor/base/res", "res");
    ModuleResourceRepository repository = ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res));
    StringResourceData data = StringResourceParser.parse(myFacet, repository);

    final Locale locale = Locale.create("en");
    final String key = "key4";
    assertNull(data.getTranslations().get(key, locale));

    assertTrue(data.setTranslation(key, locale, "Hello"));

    XmlTag tag = getNthXmlTag(res.findFileByRelativePath("values-en/strings.xml"), "string", 3);
    assertEquals("key4", tag.getAttributeValue(SdkConstants.ATTR_NAME));
    assertEquals("Hello", tag.getValue().getText());

    assertEquals("Hello", StringResourceData.resourceToString(data.getTranslations().get(key, locale)));
  }

  private XmlTag getNthXmlTag(@NotNull VirtualFile file, @NotNull String tag, int index) {
    PsiFile psiFile = PsiManager.getInstance(myFacet.getModule().getProject()).findFile(file);
    return ((XmlFile)psiFile).getRootTag().findSubTags(tag)[index];
  }
}
