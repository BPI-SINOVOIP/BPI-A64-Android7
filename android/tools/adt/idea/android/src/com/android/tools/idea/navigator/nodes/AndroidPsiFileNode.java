/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.navigator.nodes;

import com.android.SdkConstants;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.Nullable;

public class AndroidPsiFileNode extends PsiFileNode {
  private final IdeaSourceProvider mySourceProvider;

  public AndroidPsiFileNode(Project project, PsiFile file, ViewSettings settings, IdeaSourceProvider sourceProvider) {
    super(project, file, settings);
    mySourceProvider = sourceProvider;
  }

  @Override
  protected void updateImpl(PresentationData data) {
    super.updateImpl(data);
    if (mySourceProvider != null && !SdkConstants.FD_MAIN.equals(mySourceProvider.getName())) {
      data.addText(data.getPresentableText(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      data.addText(" (" + mySourceProvider.getName() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    String sourceProviderName = mySourceProvider == null ? "" : mySourceProvider.getName();
    return getQualifiedNameSortKey() + "-" + (SdkConstants.FD_MAIN.equals(sourceProviderName) ? "" : sourceProviderName);
  }

  @Override
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return AndroidPsiDirectoryNode.toTestString(getValue().getName(), mySourceProvider);
  }
}
