package com.siyeh.ig.numeric;

import com.siyeh.ig.IGInspectionTestCase;

public class ComparisonToNanInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/numeric/comparison_to_nan", new ComparisonToNaNInspection());
  }
}