package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class ChainedEqualityInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/chained_equality", new ChainedEqualityInspection());
  }
}