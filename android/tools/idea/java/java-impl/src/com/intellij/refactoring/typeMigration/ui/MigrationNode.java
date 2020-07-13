/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.ui.DuplicateNodeRenderer;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author anna
 * Date: 25-Mar-2008
 */
public class MigrationNode extends AbstractTreeNode<TypeMigrationUsageInfo> implements DuplicateNodeRenderer.DuplicatableNode<MigrationNode> {
  private final TypeMigrationUsageInfo myInfo;
  private MigrationNode myDuplicatedNode;
  private List<MigrationNode> myCachedChildren;
  private final TypeMigrationLabeler myLabeler;
  private final PsiType myMigrationType;
  private final TypeMigrationTreeBuilder myBuilder;
  private final HashMap<TypeMigrationUsageInfo, Set<MigrationNode>> myProcessed;
  private final HashSet<TypeMigrationUsageInfo> myParents;

  public MigrationNode(final Project project,
                       final TypeMigrationUsageInfo info,
                       final PsiType migrationType,
                       final TypeMigrationLabeler labeler,
                       final TypeMigrationTreeBuilder builder,
                       final HashSet<TypeMigrationUsageInfo> parents,
                       final HashMap<TypeMigrationUsageInfo, Set<MigrationNode>> processed) {
    super(project, info);
    myLabeler = labeler;
    myMigrationType = migrationType;
    myBuilder = builder;
    myProcessed = processed;
    myParents = parents;

    Set<MigrationNode> alreadyAdded = myProcessed.get(info);
    if (alreadyAdded == null) {
      alreadyAdded = new HashSet<MigrationNode>();
      myProcessed.put(info, alreadyAdded);
      myInfo = info;
    }
    else {
      final MigrationNode duplicate = alreadyAdded.iterator().next();
      myInfo = duplicate.getInfo();
      myDuplicatedNode = duplicate;
    }
    alreadyAdded.add(this);
  }

  public TypeMigrationUsageInfo getInfo() {
    return myInfo;
  }

  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    if (myCachedChildren == null) {
      myCachedChildren = new ArrayList<MigrationNode>();
      
      final PsiElement element = myInfo.getElement();
      if (element != null) {
        myLabeler.setCurrentRoot(myInfo);

        try {
          myLabeler.migrateRoot(element, myMigrationType, myLabeler.markRootUsages(element, myMigrationType));
        }
        catch (TypeMigrationLabeler.MigrateException e) {
          //skip warning
        }

        final HashSet<Pair<TypeMigrationUsageInfo, PsiType>> roots = myLabeler.getRootsTree().get(myInfo);
        if (roots != null) {
          for (Pair<TypeMigrationUsageInfo, PsiType> root : roots) {

            final TypeMigrationUsageInfo info = root.getFirst();

            if (myParents.contains(info)) continue;
            final HashSet<TypeMigrationUsageInfo> parents = new HashSet<TypeMigrationUsageInfo>(myParents);
            parents.add(info);

            final MigrationNode migrationNode =
                new MigrationNode(getProject(), info, root.getSecond(), myLabeler, myBuilder, parents, myProcessed);

            if (myInfo.isExcluded()) {
              info.setExcluded(true);
            }

            myCachedChildren.add(migrationNode);
          }
        }
      }
    }
    return myCachedChildren;
  }

  public boolean areChildrenInitialized() {
    return myCachedChildren != null;
  }

  protected void update(final PresentationData presentation) {

  }

  public MigrationNode getDuplicate() {
    return myDuplicatedNode;
  }

  public boolean hasDuplicate() {
    return myDuplicatedNode != null;
  }
}
