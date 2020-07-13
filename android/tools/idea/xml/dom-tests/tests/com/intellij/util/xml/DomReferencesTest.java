/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.xml.impl.GenericDomValueReference;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author peter
 */
public class DomReferencesTest extends DomHardCoreTestCase {

  public void testMetaData() throws Throwable {
    final MyElement element = createElement("");
    element.getName().setValue("A");
    final XmlTag tag = element.getXmlTag();
    final DomMetaData metaData = assertInstanceOf(tag.getMetaData(), DomMetaData.class);
    assertEquals(tag, metaData.getDeclaration());
    assertOrderedEquals(metaData.getDependences(), DomUtil.getFileElement(element), tag);
    assertEquals("A", metaData.getName());
    assertEquals("A", metaData.getName(null));

    metaData.setName("B");
    assertEquals("B", element.getName().getValue());
  }

  public void testNameReference() throws Throwable {
    final MyElement element = createElement("<a><name>abc</name></a>");
    final DomTarget target = DomTarget.getTarget(element);
    assertNotNull(target);
    final XmlTag tag = element.getName().getXmlTag();
    assertNull(tag.getContainingFile().findReferenceAt(tag.getValue().getTextRange().getStartOffset()));
  }

  public void testProcessingInstruction() throws Throwable {
    createElement("<a><?xml version=\"1.0\"?></a>").getXmlTag().accept(new PsiRecursiveElementVisitor() {
      @Override public void visitElement(PsiElement element) {
        super.visitElement(element);
        for (final PsiReference reference : element.getReferences()) {
          assertFalse(reference instanceof GenericDomValueReference);
        }
      }
    });
  }

  public void testBooleanReference() throws Throwable {
    final MyElement element = createElement("<a><boolean>true</boolean></a>");
    assertVariants(assertReference(element.getBoolean()), "false", "true");
  }

  public void testBooleanAttributeReference() throws Throwable {
    final MyElement element = createElement("<a boolean-attribute=\"true\"/>");
    final PsiReference reference = getReference(element.getBooleanAttribute());
    assertVariants(reference, "false", "true");

    final XmlAttributeValue xmlAttributeValue = element.getBooleanAttribute().getXmlAttributeValue();
    final PsiElement psiElement = reference.getElement();
    assertEquals(xmlAttributeValue, psiElement);

    assertEquals(new TextRange(0, "true".length()).shiftRight(1), reference.getRangeInElement());
  }

  public void testEnumReference() throws Throwable {
    assertVariants(assertReference(createElement("<a><enum>239</enum></a>").getEnum(), null), "A", "B", "C");
    assertVariants(assertReference(createElement("<a><enum>A</enum></a>").getEnum()), "A", "B", "C");
  }

  public void testPsiClass() throws Throwable {
    final MyElement element = createElement("<a><psi-class>java.lang.String</psi-class></a>");
    assertReference(element.getPsiClass(), PsiType.getJavaLangString(getPsiManager(), GlobalSearchScope.allScope(getProject())).resolve(),
                    element.getPsiClass().getXmlTag().getValue().getTextRange().getEndOffset() - 1);
  }

  public void testPsiType() throws Throwable {
    final MyElement element = createElement("<a><psi-type>java.lang.String</psi-type></a>");
    assertReference(element.getPsiType(), PsiType.getJavaLangString(getPsiManager(), GlobalSearchScope.allScope(getProject())).resolve());
  }

  public void testIndentedPsiType() throws Throwable {
    final MyElement element = createElement("<a><psi-type>  java.lang.Strin   </psi-type></a>");
    final PsiReference psiReference = assertReference(element.getPsiType(), null);
    assertEquals(new TextRange(22, 22 + "Strin".length()), psiReference.getRangeInElement());
  }

  public void testPsiPrimitiveType() throws Throwable {
    final MyElement element = createElement("<a><psi-type>int</psi-type></a>");
    assertReference(element.getPsiType());
  }
  
  public void testPsiPrimitiveTypeArray() throws Throwable {
    final MyElement element = createElement("<a><psi-type>int[]</psi-type></a>");
    final GenericDomValue value = element.getPsiType();
    final XmlTagValue tagValue = value.getXmlTag().getValue();
    final int i = tagValue.getText().indexOf(value.getStringValue());
    assertReference(value, value.getXmlTag(), tagValue.getTextRange().getStartOffset() + i + "int".length());
  }

  public void testPsiUnknownType() throws Throwable {
    final MyElement element = createElement("<a><psi-type>#$^%*$</psi-type></a>");
    assertReference(element.getPsiType(), null);
  }

  public void testPsiArrayType() throws Throwable {
    final MyElement element = createElement("<a><psi-type>java.lang.String[]</psi-type></a>");
    final XmlTag tag = element.getPsiType().getXmlTag();
    final TextRange valueRange = tag.getValue().getTextRange();
    final PsiReference reference = tag.getContainingFile().findReferenceAt(valueRange.getStartOffset() + "java.lang.".length());
    assertNotNull(reference);
    assertEquals(PsiType.getJavaLangString(getPsiManager(), GlobalSearchScope.allScope(getProject())).resolve(), reference.resolve());
    assertEquals("<psi-type>java.lang.".length(), reference.getRangeInElement().getStartOffset());
    assertEquals("String".length(), reference.getRangeInElement().getLength());
  }

  public void testJvmArrayType() throws Throwable {
    final MyElement element = createElement("<a><jvm-psi-type>[Ljava.lang.String;</jvm-psi-type></a>");
    final XmlTag tag = element.getJvmPsiType().getXmlTag();
    final TextRange valueRange = tag.getValue().getTextRange();
    final PsiReference reference = tag.getContainingFile().findReferenceAt(valueRange.getEndOffset() - 1);
    assertNotNull(reference);
    assertEquals(PsiType.getJavaLangString(getPsiManager(), GlobalSearchScope.allScope(getProject())).resolve(), reference.resolve());
    assertEquals("<jvm-psi-type>[Ljava.lang.".length(), reference.getRangeInElement().getStartOffset());
    assertEquals("String".length(), reference.getRangeInElement().getLength());
  }

  public void testCustomResolving() throws Throwable {
    final MyElement element = createElement("<a><string-buffer>239</string-buffer></a>");
    assertVariants(assertReference(element.getStringBuffer()), "239", "42", "foo", "zzz");
  }

  public void testAdditionalValues() throws Throwable {
    final MyElement element = createElement("<a><string-buffer>zzz</string-buffer></a>");
    final XmlTag tag = element.getStringBuffer().getXmlTag();
    assertTrue(tag.getContainingFile().findReferenceAt(tag.getValue().getTextRange().getStartOffset()).isSoft());
  }

  public interface MyElement extends DomElement {
    GenericDomValue<Boolean> getBoolean();

    GenericAttributeValue<Boolean> getBooleanAttribute();

    @Convert(MyStringConverter.class)
    GenericDomValue<String> getConvertedString();

    GenericDomValue<MyEnum> getEnum();

    @NameValue GenericDomValue<String> getName();

    GenericDomValue<PsiClass> getPsiClass();

    GenericDomValue<PsiType> getPsiType();

    @Convert(JvmPsiTypeConverter.class)
    GenericDomValue<PsiType> getJvmPsiType();

    List<GenericDomValue<MyEnum>> getEnumChildren();

    @Convert(MyStringBufferConverter.class)
    GenericDomValue<StringBuffer> getStringBuffer();

    MyAbstractElement getChild();

    MyElement getRecursiveChild();

    List<MyGenericValue> getMyGenericValues();

    MyGenericValue getMyAnotherGenericValue();
  }

  @Convert(MyStringConverter.class)
  public interface MyGenericValue extends GenericDomValue<String> {

  }

  public interface MySomeInterface {
    GenericValue<PsiType> getFoo();
  }

  public interface MyAbstractElement extends DomElement {
    GenericAttributeValue<String> getFubar239();
    GenericAttributeValue<Runnable> getFubar();
  }

  public interface MyFooElement extends MyAbstractElement, MySomeInterface {
    @Override
    GenericDomValue<PsiType> getFoo();

    GenericAttributeValue<Set> getFubar2();
  }

  public interface MyBarElement extends MyAbstractElement {
    GenericDomValue<StringBuffer> getBar();
  }


  public enum MyEnum {
    A,B,C
  }

  public static class MyStringConverter extends ResolvingConverter<String> {

    @Override
    @NotNull
    public Collection<? extends String> getVariants(final ConvertContext context) {
      return Collections.emptyList();
    }

    @Override
    public String fromString(final String s, final ConvertContext context) {
      return s;
    }

    @Override
    public String toString(final String s, final ConvertContext context) {
      return s;
    }
  }

  public static class MyStringBufferConverter extends ResolvingConverter<StringBuffer> {

    @Override
    public StringBuffer fromString(final String s, final ConvertContext context) {
      return s == null ? null : new StringBuffer(s);
    }

    @Override
    public String toString(final StringBuffer t, final ConvertContext context) {
      return t == null ? null : t.toString();
    }

    @Override
    public Collection<? extends StringBuffer> getVariants(final ConvertContext context) {
      return Arrays.asList(new StringBuffer("239"), new StringBuffer("42"), new StringBuffer("foo"));
    }

    @Override
    public Set<String> getAdditionalVariants() {
      return Collections.singleton("zzz");
    }
  }
}
