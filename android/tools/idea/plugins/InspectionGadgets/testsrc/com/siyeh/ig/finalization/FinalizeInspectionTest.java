package com.siyeh.ig.finalization;

import com.siyeh.ig.IGInspectionTestCase;

public class FinalizeInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/finalization/finalize", new FinalizeInspection());
  }
}