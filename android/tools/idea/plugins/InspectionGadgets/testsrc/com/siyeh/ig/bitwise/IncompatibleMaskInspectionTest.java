package com.siyeh.ig.bitwise;

import com.siyeh.ig.IGInspectionTestCase;

public class IncompatibleMaskInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bitwise/incompatible_mask",
           new IncompatibleMaskInspection());
  }
}