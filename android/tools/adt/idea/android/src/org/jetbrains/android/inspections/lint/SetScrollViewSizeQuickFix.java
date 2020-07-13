package org.jetbrains.android.inspections.lint;

import com.android.SdkConstants;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
class SetScrollViewSizeQuickFix implements AndroidLintQuickFix {
  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    if (tag == null) {
      return;
    }

    final XmlTag parentTag = tag.getParentTag();
    if (parentTag == null) {
      return;
    }

    final boolean isHorizontal = SdkConstants.HORIZONTAL_SCROLL_VIEW.equals(parentTag.getName());
    final String attributeName = isHorizontal
                                 ? AndroidLintUtil.ATTR_LAYOUT_WIDTH
                                 : AndroidLintUtil.ATTR_LAYOUT_HEIGHT;
    tag.setAttribute(attributeName, SdkConstants.NS_RESOURCES, AndroidLintUtil.ATTR_VALUE_WRAP_CONTENT);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    if (tag == null) {
      return false;
    }
    return tag.getParentTag() != null;
  }

  @NotNull
  @Override
  public String getName() {
    return AndroidBundle.message("android.lint.fix.set.to.wrap.content");
  }
}
