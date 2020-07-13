/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package test;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: vnikolaenko
 * Date: 25.09.2008
 * Time: 15:10:10
 */
public class RegExpCompletionTest extends CodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    PlatformTestCase.initPlatformLangPrefix();
    super.setUp();
  }

  // util methods
    private static String getInputDataFileName(String testName) {
        return Character.toUpperCase(testName.charAt(0)) + testName.substring(1) + ".regexp";
    }

    private static String getExpectedResultFileName(String testName) {
        return Character.toUpperCase(testName.charAt(0)) + testName.substring(1) + "Expected" + ".regexp";
    }

    public void testBackSlashVariants() throws Throwable {
        doBackSlashVariantsTest();
    }
  
    public void testBackSlashVariants2() throws Throwable {
        doBackSlashVariantsTest();
    }

    private void doBackSlashVariantsTest() throws Throwable {
        java.util.List<String> nameList = new ArrayList<String>(Arrays.asList("d", "D", "s", "S", "w", "W", "b", "B", "A", "G", "Z", "z", "Q", "E",
                "t", "n", "r", "f", "a", "e"));
        for (String[] stringArray : DefaultRegExpPropertiesProvider.getInstance().getAllKnownProperties()) {
            nameList.add("p{" + stringArray[0] + "}");
        }
        myFixture.testCompletionVariants(getInputDataFileName(getTestName(true)), ArrayUtil.toStringArray(nameList));
    }

  public void testPropertyVariants() throws Throwable {
        java.util.List<String> nameList = new ArrayList<String>();
        for (String[] stringArray : DefaultRegExpPropertiesProvider.getInstance().getAllKnownProperties()) {
            nameList.add("{" + stringArray[0] + "}");
        }
        myFixture.testCompletionVariants(getInputDataFileName(getTestName(true)), ArrayUtil.toStringArray(nameList));
    }

    public void testPropertyAlpha() throws Throwable {
        doTest();
    }

    public void doTest() throws Throwable {
        String inputDataFileName = getInputDataFileName(getTestName(true));
        String expectedResultFileName = getExpectedResultFileName(getTestName(true));
        myFixture.testCompletion(inputDataFileName, expectedResultFileName);
    }

    @Override
    protected String getBasePath() {
      String homePath = PathManager.getHomePath();
      File candidate = new File(homePath, "community/RegExpSupport");
      if (candidate.isDirectory()) {
        return "/community/RegExpSupport/testData/completion";
      }
      return "/RegExpSupport/testData/completion";
    }
}
