package org.jetbrains.android.inspections.lint;

import com.android.resources.Density;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
class ConvertToDpQuickFix implements AndroidLintQuickFix {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.inspections.lint.ConvertToDpQuickFix");
  private static final Pattern PX_ATTR_VALUE_PATTERN = Pattern.compile("(\\d+)px");

  private static int ourPrevDpi = Density.DEFAULT_DENSITY;

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    if (context instanceof AndroidQuickfixContexts.BatchContext) {
      return;
    }
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    final List<Density> densities = new ArrayList<Density>();

    for (Density density : Density.values()) {
      if (density.getDpiValue() > 0) {
        densities.add(density);
      }
    }

    final String[] densityPresentableNames = new String[densities.size()];

    String defaultValue = null;
    String initialValue = null;

    for (int i = 0; i < densities.size(); i++) {
      final Density density = densities.get(i);
      densityPresentableNames[i] = getLabelForDensity(density);

      final int dpi = density.getDpiValue();
      if (dpi == 0) {
        continue;
      }

      if (dpi == ourPrevDpi) {
        initialValue = densityPresentableNames[i];
      }
      else if (dpi == Density.DEFAULT_DENSITY) {
        defaultValue = densityPresentableNames[i];
      }
    }

    if (initialValue == null) {
      initialValue = defaultValue;
    }
    if (initialValue == null) {
      return;
    }

    final int dpi;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      dpi = Density.DEFAULT_DENSITY;
    }
    else {
      final int selectedIndex = Messages
        .showChooseDialog("What is the screen density the current px value works with?", "Choose density", densityPresentableNames,
                          initialValue, null);
      if (selectedIndex < 0) {
        return;
      }
      dpi = densities.get(selectedIndex).getDpiValue();
    }

    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourPrevDpi = dpi;

    for (XmlAttribute attribute : tag.getAttributes()) {
      final String value = attribute.getValue();

      if (value != null && value.endsWith("px")) {
        final String newValue = convertToDp(value, dpi);
        if (newValue != null) {
          attribute.setValue(newValue);
        }
      }
    }
    final XmlTagValue tagValueElement = tag.getValue();
    final String tagValue = tagValueElement.getText();

    if (tagValue.endsWith("px")) {
      final String newValue = convertToDp(tagValue, dpi);

      if (newValue != null) {
        tagValueElement.setText(newValue);
      }
    }
  }

  private static String convertToDp(String value, int dpi) {
    String newValue = null;
    final Matcher matcher = PX_ATTR_VALUE_PATTERN.matcher(value);

    if (matcher.matches()) {
      final String numberString = matcher.group(1);
      try {
        final int px = Integer.parseInt(numberString);
        final int dp = px * 160 / dpi;
        newValue = Integer.toString(dp) + "dp";
      }
      catch (NumberFormatException nufe) {
        LOG.error(nufe);
      }
    }
    return newValue;
  }

  @NotNull
  private static String getLabelForDensity(@NotNull Density density) {
    return String.format("%1$s (%2$d)", density.getShortDisplayValue(), density.getDpiValue());
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return contextType != AndroidQuickfixContexts.BatchContext.TYPE &&
           PsiTreeUtil.getParentOfType(startElement, XmlTag.class) != null;
  }

  @NotNull
  @Override
  public String getName() {
    return AndroidBundle.message("android.lint.fix.convert.to.dp");
  }
}
