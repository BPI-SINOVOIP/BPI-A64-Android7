package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class TrivialStringConcatenationInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/performance/trivial_string_concatenation", new TrivialStringConcatenationInspection());
  }
}