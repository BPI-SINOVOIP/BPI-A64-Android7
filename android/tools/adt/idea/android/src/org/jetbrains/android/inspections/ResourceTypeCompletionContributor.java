/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.inspections;

import com.android.resources.ResourceType;
import com.android.tools.idea.model.ManifestInfo;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * A custom version of the IntelliJ
 * {@link com.intellij.codeInspection.magicConstant.MagicCompletionContributor},
 * almost identical, except
 * <p>
 * The main changes are:
 * <li>
 *   it calls {@link ResourceTypeInspection}
 *    instead of {@link com.intellij.codeInspection.magicConstant.MagicConstantInspection}
 *    to produce the set of values it will offer
 * </li>
 * <li>
 *   it can compute resource type suggestions ({@code R.string}, {@code R.drawable}, etc) when
 *   completing parameters or return types that have been annotated with one of the resource
 *   type annotations: {@code @android.support.annotation.StringRes},
 *   {@code @android.support.annotation.DrawableRes}, ...
 * </li>
 */
public class ResourceTypeCompletionContributor extends CompletionContributor {
  private static final ElementPattern<PsiElement> IN_METHOD_CALL_ARGUMENT =
    psiElement().withParent(psiElement(PsiReferenceExpression.class).inside(psiElement(PsiExpressionList.class).withParent(PsiCall.class)));
  private static final ElementPattern<PsiElement> IN_BINARY_COMPARISON =
    psiElement().withParent(psiElement(PsiReferenceExpression.class).inside(psiElement(PsiBinaryExpression.class)));
  private static final ElementPattern<PsiElement> IN_ASSIGNMENT =
    psiElement().withParent(psiElement(PsiReferenceExpression.class).inside(psiElement(PsiAssignmentExpression.class)));
  private static final ElementPattern<PsiElement> IN_RETURN =
    psiElement().withParent(psiElement(PsiReferenceExpression.class).inside(psiElement(PsiReturnStatement.class)));
  private static final ElementPattern<PsiElement> IN_ANNOTATION_INITIALIZER =
    psiElement().afterLeaf("=").withParent(PsiReferenceExpression.class).withSuperParent(2,PsiNameValuePair.class).withSuperParent(3,PsiAnnotationParameterList.class).withSuperParent(4,PsiAnnotation.class);
  private static final int PRIORITY = 100;

  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet result) {
    //if (parameters.getCompletionType() != CompletionType.SMART) return;
    PsiElement pos = parameters.getPosition();

    if (JavaCompletionData.AFTER_DOT.accepts(pos)) {
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(pos);
    if (facet == null) {
      return;
    }

    ResourceTypeInspection.Constraints allowedValues = null;
    if (IN_METHOD_CALL_ARGUMENT.accepts(pos)) {
      PsiCall call = PsiTreeUtil.getParentOfType(pos, PsiCall.class);
      if (!(call instanceof PsiExpression)) return;
      PsiType type = ((PsiExpression)call).getType();

      PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(call.getProject()).getResolveHelper();
      JavaResolveResult[] methods = call instanceof PsiMethodCallExpression
                                    ? ((PsiMethodCallExpression)call).getMethodExpression().multiResolve(true)
                                    : call instanceof PsiNewExpression && type instanceof PsiClassType
                                      ? resolveHelper.multiResolveConstructor((PsiClassType)type, call.getArgumentList(), call)
                                      : JavaResolveResult.EMPTY_ARRAY;
      for (JavaResolveResult resolveResult : methods) {
        PsiElement element = resolveResult.getElement();
        if (!(element instanceof PsiMethod)) return;
        PsiMethod method = (PsiMethod)element;
        if (!resolveHelper.isAccessible(method, call, null)) continue;
        PsiElement argument = pos;
        while (!(argument.getContext() instanceof PsiExpressionList)) argument = argument.getContext();
        PsiExpressionList list = (PsiExpressionList)argument.getContext();
        int i = ArrayUtil.indexOf(list.getExpressions(), argument);
        if (i == -1) continue;
        PsiParameter[] params = method.getParameterList().getParameters();
        if (i >= params.length) continue;
        PsiParameter parameter = params[i];
        ResourceTypeInspection.Constraints values =
          parameter == null ? null : ResourceTypeInspection.getAllowedValues(parameter, parameter.getType(), null);
        if (values == null) continue;
        if (allowedValues == null) {
          allowedValues = values;
          continue;
        }
        if (!allowedValues.equals(values)) return;
      }
    }
    else if (IN_BINARY_COMPARISON.accepts(pos)) {
      PsiBinaryExpression exp = PsiTreeUtil.getParentOfType(pos, PsiBinaryExpression.class);
      if (exp != null && (exp.getOperationTokenType() == JavaTokenType.EQEQ || exp.getOperationTokenType() == JavaTokenType.NE)) {
        PsiExpression l = exp.getLOperand();
        PsiElement resolved;
        if (l instanceof PsiReferenceExpression && (resolved = ((PsiReferenceExpression)l).resolve()) instanceof PsiModifierListOwner) {
          allowedValues = ResourceTypeInspection.getAllowedValues((PsiModifierListOwner)resolved, l.getType(), null);
        }
        PsiExpression r = exp.getROperand();
        if (allowedValues == null && r instanceof PsiReferenceExpression && (resolved = ((PsiReferenceExpression)r).resolve()) instanceof PsiModifierListOwner) {
          allowedValues = ResourceTypeInspection.getAllowedValues((PsiModifierListOwner)resolved, r.getType(), null);
        }
      }
    }
    else if (IN_ASSIGNMENT.accepts(pos)) {
      PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(pos, PsiAssignmentExpression.class);
      PsiElement resolved;
      PsiExpression l = assignment == null ? null : assignment.getLExpression();
      if (assignment != null && PsiTreeUtil.isAncestor(assignment.getRExpression(), pos, false) && l instanceof PsiReferenceExpression && (resolved = ((PsiReferenceExpression)l).resolve()) instanceof PsiModifierListOwner) {
        allowedValues = ResourceTypeInspection.getAllowedValues((PsiModifierListOwner)resolved, l.getType(), null);
      }
    }
    else if (IN_RETURN.accepts(pos)) {
      PsiReturnStatement statement = PsiTreeUtil.getParentOfType(pos, PsiReturnStatement.class);
      PsiExpression l = statement == null ? null : statement.getReturnValue();
      PsiMethod method = PsiTreeUtil.getParentOfType(l, PsiMethod.class);
      if (method != null) {
        allowedValues = ResourceTypeInspection.getAllowedValues(method, method.getReturnType(), null);
      }
    }
    else if (IN_ANNOTATION_INITIALIZER.accepts(pos)) {
      PsiNameValuePair pair = (PsiNameValuePair)pos.getParent().getParent();
      PsiAnnotationMemberValue value = pair.getValue();
      if (!(value instanceof PsiExpression)) return;
      PsiReference ref = pair.getReference();
      if (ref == null) return;
      PsiMethod method = (PsiMethod)ref.resolve();
      if (method == null) return;
      allowedValues = ResourceTypeInspection.getAllowedValues(method, method.getReturnType(), null);
    }
    if (allowedValues == null) return;

    final Set<PsiElement> allowed = new THashSet<PsiElement>(new TObjectHashingStrategy<PsiElement>() {
      @Override
      public int computeHashCode(PsiElement object) {
        return 0;
      }

      @Override
      public boolean equals(PsiElement o1, PsiElement o2) {
        return parameters.getOriginalFile().getManager().areElementsEquivalent(o1, o2);
      }
    });

    // Suggest resource types
    if (allowedValues instanceof ResourceTypeInspection.ResourceTypeAllowedValues) {
      for (ResourceType resourceType : ((ResourceTypeInspection.ResourceTypeAllowedValues)allowedValues).types) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(pos.getProject());
        String code = "R." + resourceType.getName();
        // Look up the fully qualified name of the application package
        String fqcn = ManifestInfo.get(facet.getModule(), false).getPackage();
        String qualifiedCode = fqcn + "." + code;
        Project project = facet.getModule().getProject();
        PsiClass cls = JavaPsiFacade.getInstance(project).findClass(qualifiedCode, GlobalSearchScope.allScope(project));
        if (cls != null) {
          result.addElement(new JavaPsiClassReferenceElement(cls));
        } else {
          PsiExpression type = factory.createExpressionFromText(code, pos);
          result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(type, code), PRIORITY - 1));
          allowed.add(type);
        }
      }
    } else if (allowedValues instanceof ResourceTypeInspection.AllowedValues) {
       ResourceTypeInspection.AllowedValues a = (ResourceTypeInspection.AllowedValues)allowedValues;
      if (a.canBeOred) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(pos.getProject());
        PsiExpression zero = factory.createExpressionFromText("0", pos);
        result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(zero, "0"), PRIORITY - 1));
        PsiExpression minusOne = factory.createExpressionFromText("-1", pos);
        result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(minusOne, "-1"), PRIORITY - 1));
        allowed.add(zero);
        allowed.add(minusOne);
      }
      List<ExpectedTypeInfo> types = Arrays.asList(JavaSmartCompletionContributor.getExpectedTypes(parameters));
      for (PsiAnnotationMemberValue value : a.values) {
        if (value instanceof PsiReference) {
          PsiElement resolved = ((PsiReference)value).resolve();
          if (resolved instanceof PsiNamedElement) {

            LookupElement lookupElement = LookupItemUtil.objectToLookupItem(resolved);
            if (lookupElement instanceof VariableLookupItem) {
              ((VariableLookupItem)lookupElement).setSubstitutor(PsiSubstitutor.EMPTY);
            }
            LookupElement element = PrioritizedLookupElement.withPriority(lookupElement, PRIORITY);
            element = decorate(parameters, types, element);
            result.addElement(element);
            allowed.add(resolved);
            continue;
          }
        }
        LookupElement element = LookupElementBuilder.create(value, value.getText());
        element = decorate(parameters, types, element);
        result.addElement(element);
        allowed.add(value);
      }
    }

    result.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
      @Override
      public void consume(CompletionResult completionResult) {
        LookupElement element = completionResult.getLookupElement();
        Object object = element.getObject();
        if (object instanceof PsiElement && allowed.contains(object)) {
          return;
        }
        result.passResult(completionResult);
      }
    });
  }

  private static LookupElement decorate(CompletionParameters parameters, List<ExpectedTypeInfo> types, LookupElement element) {
    if (!types.isEmpty() && parameters.getCompletionType() == CompletionType.SMART) {
      element = JavaSmartCompletionContributor.decorate(element, types);
    }
    return element;
  }
}
  