package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.checks.UselessViewDetector;
import com.android.tools.lint.detector.api.Issue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class RemoveUselessViewQuickFix implements AndroidLintQuickFix {
  private final Issue myIssue;

  public RemoveUselessViewQuickFix(@NotNull Issue issue) {
    myIssue = issue;
  }

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

    if (myIssue.getId().equals(UselessViewDetector.USELESS_LEAF.getId())) {
      tag.delete();
    }
    else {
      assert false;
      // todo: implement
    }
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
    return AndroidBundle.message("android.lint.fix.remove.unnecessary.view");
  }
}
