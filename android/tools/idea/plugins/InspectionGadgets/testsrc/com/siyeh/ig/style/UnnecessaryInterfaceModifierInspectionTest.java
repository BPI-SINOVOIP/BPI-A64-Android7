package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryInterfaceModifierInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/unnecessary_interface_modifier",
           new UnnecessaryInterfaceModifierInspection());
  }
}