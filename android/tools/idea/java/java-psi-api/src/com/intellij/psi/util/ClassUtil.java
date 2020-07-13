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
package com.intellij.psi.util;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassUtil {
  private ClassUtil() {}

  public static String extractPackageName(String className) {
    if (className != null) {
      int i = className.lastIndexOf('.');
      return i == -1 ? "" : className.substring(0, i);                                

    }
    return null;
  }

  @NotNull
  public static String extractClassName(@NotNull String fqName) {
    int i = fqName.lastIndexOf('.');
    return i == -1 ? fqName : fqName.substring(i + 1);
  }

  public static String createNewClassQualifiedName(String qualifiedName, String className) {
    if (className == null){
      return null;
    }
    if (qualifiedName == null || qualifiedName.isEmpty()){
      return className;
    }
    return qualifiedName + "." + extractClassName(className);
  }

  public static PsiDirectory sourceRoot(PsiDirectory containingDirectory) {
    while (containingDirectory != null) {
      if (JavaDirectoryService.getInstance().isSourceRoot(containingDirectory)) {
        return containingDirectory;
      }
      containingDirectory = containingDirectory.getParentDirectory();
    }
    return null;
  }

  public static void formatClassName(@NotNull final PsiClass aClass, @NotNull StringBuilder buf) {
    final String qName = aClass.getQualifiedName();
    if (qName != null) {
      buf.append(qName);
    }
    else {
      final PsiClass parentClass = getContainerClass(aClass);
      if (parentClass != null) {
        formatClassName(parentClass, buf);
        buf.append("$");
        buf.append(getNonQualifiedClassIdx(aClass));
        final String name = aClass.getName();
        if (name != null) {
          buf.append(name);
        }
      }
    }
  }

  @Nullable
  private static PsiClass getContainerClass(@NotNull PsiClass aClass) {
    PsiElement parent = aClass.getContext();
    while (parent != null && !(parent instanceof PsiClass)) {
      parent = parent.getContext();
    }
    return (PsiClass)parent;
  }

  public static int getNonQualifiedClassIdx(@NotNull final PsiClass psiClass) {
    final int[] result = {-1};
    final PsiClass containingClass = getContainerClass(psiClass);
    if (containingClass != null) {
      containingClass.accept(new JavaRecursiveElementVisitor() {
        private int myCurrentIdx = 0;

        @Override public void visitElement(PsiElement element) {
          if (result[0] == -1) {
            super.visitElement(element);
          }
        }

        @Override public void visitClass(PsiClass aClass) {
          super.visitClass(aClass);
          if (aClass.getQualifiedName() == null) {
            myCurrentIdx++;
            if (psiClass == aClass) {
              result[0] = myCurrentIdx;
            }
          }
        }
      });
    }
    return result[0];
  }

  public static PsiClass findNonQualifiedClassByIndex(@NotNull String indexName, @NotNull final PsiClass containingClass) {
    return findNonQualifiedClassByIndex(indexName, containingClass, false);
  }

  public static PsiClass findNonQualifiedClassByIndex(@NotNull String indexName, @NotNull final PsiClass containingClass,
                                                      final boolean jvmCompatible) {
    String prefix = getDigitPrefix(indexName);
    final int idx = !prefix.isEmpty() ? Integer.parseInt(prefix) : -1;
    final String name = prefix.length() < indexName.length() ? indexName.substring(prefix.length()) : null;
    final PsiClass[] result = new PsiClass[1];
    containingClass.accept(new JavaRecursiveElementVisitor() {
      private int myCurrentIdx = 0;

      @Override public void visitElement(PsiElement element) {
        if (result[0] == null) {
          super.visitElement(element);
        }
      }

      @Override public void visitClass(PsiClass aClass) {
        if (!jvmCompatible) {
          super.visitClass(aClass);
          if (aClass.getQualifiedName() == null) {
            myCurrentIdx++;
            if (myCurrentIdx == idx && Comparing.strEqual(name, aClass.getName())) {
              result[0] = aClass;
            }
          }
          return;
        }
        if (aClass == containingClass) {
          super.visitClass(aClass);
          return;
        }
        if (Comparing.strEqual(name, aClass.getName())) {
          myCurrentIdx++;
          if (myCurrentIdx == idx || idx == -1) {
            result[0] = aClass;
          }
        }
      }

      @Override public void visitTypeParameter(final PsiTypeParameter classParameter) {
        if (!jvmCompatible) {
          super.visitTypeParameter(classParameter);
        }
        else {
          visitElement(classParameter);
        }
      }
    });
    return result[0];
  }

  @NotNull
  private static String getDigitPrefix(@NotNull String indexName) {
    int i;
    for (i = 0; i < indexName.length(); i++) {
      final char c = indexName.charAt(i);
      if (!Character.isDigit(c)) {
        break;
      }
    }
    return i == 0 ? "" : indexName.substring(0, i);
  }


  /**
   * Finds anonymous classes. Uses javac notation.
   * @param psiManager project to search
   * @param externalName class qualified name
   * @return found psiClass
   */
  @Nullable
  public static PsiClass findPsiClass(@NotNull PsiManager psiManager, @NotNull String externalName){
    return findPsiClass(psiManager, externalName, null, false);
  }

  @Nullable
  public static PsiClass findPsiClass(@NotNull PsiManager psiManager,
                                      @NotNull String externalName,
                                      PsiClass psiClass,
                                      boolean jvmCompatible) {
    return findPsiClass(psiManager, externalName, psiClass, jvmCompatible, GlobalSearchScope.allScope(psiManager.getProject()));
  }

  @Nullable
  public static PsiClass findPsiClass(@NotNull PsiManager psiManager,
                                      @NotNull String externalName,
                                      @Nullable PsiClass psiClass,
                                      boolean jvmCompatible, 
                                      @NotNull GlobalSearchScope scope) {
    for (int pos = 0; pos < externalName.length(); pos++) {
      if (externalName.charAt(pos) == '$') {
        PsiClass parentClass = psiClass;
        if (parentClass == null) {
          parentClass = JavaPsiFacade.getInstance(psiManager.getProject())
            .findClass(externalName.substring(0, pos), scope);
        }
        if (parentClass == null) continue;
        PsiClass res = findSubclass(psiManager, externalName.substring(pos + 1), parentClass, jvmCompatible);
        if (res != null) return res;
      }
    }
    return JavaPsiFacade.getInstance(psiManager.getProject()).findClass(externalName, scope);
  }

  @Nullable
  private static PsiClass findSubclass(@NotNull PsiManager psiManager,
                                       @NotNull String externalName,
                                       final PsiClass psiClass,
                                       final boolean jvmCompatible) {
    for (int pos = 0; pos < externalName.length(); pos++) {
      if (externalName.charAt(pos) == '$') {
        PsiClass anonymousClass = findNonQualifiedClassByIndex(externalName.substring(0, pos), psiClass, jvmCompatible);
        if (anonymousClass == null) return null;
        PsiClass res = findPsiClass(psiManager, externalName.substring(pos), anonymousClass, jvmCompatible);
        if (res != null) return res;
      }
    }
    return findNonQualifiedClassByIndex(externalName, psiClass, jvmCompatible);
  }

  @Nullable
  public static String getJVMClassName(@NotNull PsiClass aClass) {
    final PsiClass containingClass = aClass.getContainingClass();
    if (containingClass != null) {
      String parentName = getJVMClassName(containingClass);
      if (parentName == null) {
        return null;
      }
      
      return parentName + "$" + aClass.getName();
    }
    return aClass.getQualifiedName();
  }


  @Nullable
  public static PsiClass findPsiClassByJVMName(@NotNull PsiManager manager, @NotNull String jvmClassName) {
    return findPsiClass(manager, jvmClassName.replace('/', '.'), null, true);
  }
}