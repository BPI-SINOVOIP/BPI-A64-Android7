/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.intellij.lang.regexp;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.ClassExtension;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpPyCondRef;
import org.intellij.lang.regexp.psi.RegExpQuantifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public final class RegExpLanguageHosts extends ClassExtension<RegExpLanguageHost> {
  private static final RegExpLanguageHosts INSTANCE = new RegExpLanguageHosts();
  private final DefaultRegExpPropertiesProvider myDefaultProvider;

  public static RegExpLanguageHosts getInstance() {
    return INSTANCE;
  }

  private RegExpLanguageHosts() {
    super("com.intellij.regExpLanguageHost");
    myDefaultProvider = DefaultRegExpPropertiesProvider.getInstance();
  }

  @Nullable
  private static RegExpLanguageHost findRegExpHost(@Nullable final PsiElement element) {
    if (element == null) {
      return null;
    }
    PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(element.getProject()).getInjectionHost(element);
    if (host instanceof RegExpLanguageHost) {
      return (RegExpLanguageHost)host;
    }
    if (host != null) {
      return INSTANCE.forClass(host.getClass());
    }
    return null;
  }

  public boolean isRedundantEscape(@NotNull final RegExpChar ch, @NotNull final String text) {
    if (text.length() <= 1) {
      return false;
    }
    final RegExpLanguageHost host = findRegExpHost(ch);
    if (host != null) {
      final char c = text.charAt(1);
      final boolean needsEscaping = host.characterNeedsEscaping(c);
      return !needsEscaping;
    }
    else {
      return !("\\]".equals(text) || "\\}".equals(text));
    }
  }

  public boolean supportsNamedGroupSyntax(@Nullable final RegExpGroup group) {
    final RegExpLanguageHost host = findRegExpHost(group);
    return host != null && host.supportsNamedGroupSyntax(group);
  }

  public boolean supportsPerl5EmbeddedComments(@Nullable final PsiComment comment) {
    final RegExpLanguageHost host = findRegExpHost(comment);
    return host != null && host.supportsPerl5EmbeddedComments();
  }

  public boolean supportsPythonConditionalRefs(@Nullable final RegExpPyCondRef condRef) {
    final RegExpLanguageHost host = findRegExpHost(condRef);
    return host != null && host.supportsPythonConditionalRefs();
  }

  public boolean supportsPossessiveQuantifiers(@Nullable final RegExpQuantifier quantifier) {
    final RegExpLanguageHost host = findRegExpHost(quantifier);
    return host == null || host.supportsPossessiveQuantifiers();
  }

  public boolean isValidCategory(@NotNull final PsiElement element, @NotNull String category) {
    final RegExpLanguageHost host = findRegExpHost(element);
    return host != null ? host.isValidCategory(category) : myDefaultProvider.isValidCategory(category);
  }

  @NotNull
  public String[][] getAllKnownProperties(@NotNull final PsiElement element) {
    final RegExpLanguageHost host = findRegExpHost(element);
    return host != null ? host.getAllKnownProperties() : myDefaultProvider.getAllKnownProperties();
  }

  @Nullable
  String getPropertyDescription(@NotNull final PsiElement element, @Nullable final String name) {
    final RegExpLanguageHost host = findRegExpHost(element);
    return host != null ?  host.getPropertyDescription(name) : myDefaultProvider.getPropertyDescription(name);
  }

  @NotNull
  String[][] getKnownCharacterClasses(@NotNull final PsiElement element) {
    final RegExpLanguageHost host = findRegExpHost(element);
    return host != null ? host.getKnownCharacterClasses() : myDefaultProvider.getKnownCharacterClasses();
  }
}
