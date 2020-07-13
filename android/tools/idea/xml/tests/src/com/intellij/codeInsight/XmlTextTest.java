/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author mike
 */
public class XmlTextTest extends LightCodeInsightTestCase {
  public void testInsertAtOffset() throws Exception {
    new WriteCommandAction(getProject()) {

      @Override
      protected void run(final Result result) throws Throwable {
        String xml = "<root>0123456789</root>";
        XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject())
          .createFileFromText("foo.xml", StdFileTypes.XML, xml, (long)1, true, false);
        //System.out.println(DebugUtil.psiToString(file, false));
        XmlTag root = file.getDocument().getRootTag();
        final XmlText text1 = root.getValue().getTextElements()[0];

        assertFalse(CodeEditUtil.isNodeGenerated(root.getNode()));
        final XmlText text = text1;

        final XmlElement element = text.insertAtOffset(XmlElementFactory.getInstance(getProject()).createTagFromText("<bar/>"), 5);
        assertNotNull(element);
        assertTrue(element instanceof XmlText);
        assertEquals("01234", element.getText());
        assertEquals("<root>01234<bar/>56789</root>", text.getContainingFile().getText());
      }
    }.execute();
  }

  public void testPhysicalToDisplayIfHasGaps2() throws Exception {
    String xml = "<div>&amp;abc</div>";
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("foo.xml", xml);
    XmlTag root = file.getDocument().getRootTag();
    final XmlText text = root.getValue().getTextElements()[0];

    assertEquals("&abc", text.getValue());
    assertEquals(0, text.physicalToDisplay(0));
    assertEquals(1, text.physicalToDisplay(5));
    assertEquals(2, text.physicalToDisplay(6));
    assertEquals(3, text.physicalToDisplay(7));
    assertEquals(4, text.physicalToDisplay(8));
  }

  public void testDisplayToPhysical() throws Exception {
    String xml = "<div>&amp;abc</div>";
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("foo.xml", xml);
    XmlTag root = file.getDocument().getRootTag();
    final XmlText text = root.getValue().getTextElements()[0];
                                                              
    assertEquals("&abc", text.getValue());
    assertEquals(0, text.displayToPhysical(0));
    assertEquals(5, text.displayToPhysical(1));
    assertEquals(6, text.displayToPhysical(2));
    assertEquals(7, text.displayToPhysical(3));
    assertEquals(8, text.displayToPhysical(4));
  }

  public void testDisplayToPhysical2() throws Exception {
    String xml = "<div><![CDATA[ ]]></div>";
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("foo.xml", xml);
    XmlTag root = file.getDocument().getRootTag();
    final XmlText text = root.getValue().getTextElements()[0];

    assertEquals(" ", text.getValue());
    assertEquals(9, text.displayToPhysical(0));
    assertEquals(13, text.displayToPhysical(1));
  }

}
