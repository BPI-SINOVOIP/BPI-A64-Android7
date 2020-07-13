/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.actions;

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.inferNullity.InferNullityAnnotationsAction;
import com.intellij.codeInspection.inferNullity.NullityInferrer;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.pluralize;

/**
 * AndroidInferNullityAnnotationAction gives the user the option of adding the correct
 * component library to the gradle build file.
 * This file has excerpts of Intellij code.
 */
public class AndroidInferNullityAnnotationAction extends InferNullityAnnotationsAction {
  private static final Logger LOG = Logger.getInstance(AndroidInferNullityAnnotationAction.class);
  private static final String INFER_NULLITY_ANNOTATIONS = "Infer Nullity Annotations";
  private static final String ADD_DEPENDENCY = "Add Support Dependency";
  private static final int MIN_SDK_WITH_NULLABLE = 19;

  @Override
  protected void analyze(@NotNull Project project, @NotNull AnalysisScope scope) {
    if (!Projects.isBuildWithGradle(project)) {
      super.analyze(project, scope);
      return;
    }
    int[] fileCount = new int[] {0};
    Map<Module, PsiFile> modules = findModulesInScope(project, scope, fileCount);
    if (modules == null) {
      return;
    }
    if (!checkModules(project, scope, modules)) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final UsageInfo[] usageInfos = findUsages(project, scope, fileCount[0]);
    if (usageInfos == null) return;

    if (usageInfos.length < 5) {
      SwingUtilities.invokeLater(applyRunnable(project, new Computable<UsageInfo[]>() {
        @Override
        public UsageInfo[] compute() {
          return usageInfos;
        }
      }));
    }
    else {
      showUsageView(project, usageInfos, scope);
    }
  }

  // Intellij code from InferNullityAnnotationsAction.
  private static Map<Module, PsiFile> findModulesInScope(@NotNull final Project project,
                                                         @NotNull final AnalysisScope scope,
                                                         @NotNull final int[] fileCount) {
    final ProgressManager progressManager = ProgressManager.getInstance();
    final Map<Module, PsiFile> modules = new HashMap<Module, PsiFile>();
    boolean completed = progressManager.runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        scope.accept(new PsiElementVisitor() {
          @Override
          public void visitFile(PsiFile file) {
            fileCount[0]++;
            final ProgressIndicator progressIndicator = progressManager.getProgressIndicator();
            if (progressIndicator != null) {
              final VirtualFile virtualFile = file.getVirtualFile();
              if (virtualFile != null) {
                progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
              }
              progressIndicator.setText(AnalysisScopeBundle.message("scanning.scope.progress.title"));
            }
            final Module module = ModuleUtilCore.findModuleForPsiElement(file);
            if (module != null && !modules.containsKey(module)) {
              modules.put(module, file);
            }
          }
        });
      }
    }, "Check applicability...", true, project);
    return completed ? modules : null;
  }

  // Intellij code from InferNullityAnnotationsAction.
  private static UsageInfo[] findUsages(@NotNull final Project project,
                                        @NotNull final AnalysisScope scope,
                                        final int fileCount) {
    final NullityInferrer inferrer = new NullityInferrer(false, project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    final Runnable searchForUsages = new Runnable() {
      @Override
      public void run() {
        scope.accept(new PsiElementVisitor() {
          int myFileCount = 0;

          @Override
          public void visitFile(final PsiFile file) {
            myFileCount++;
            final VirtualFile virtualFile = file.getVirtualFile();
            final FileViewProvider viewProvider = psiManager.findViewProvider(virtualFile);
            final Document document = viewProvider == null ? null : viewProvider.getDocument();
            if (document == null || virtualFile.getFileType().isBinary()) return; //do not inspect binary files
            final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
            if (progressIndicator != null) {
              progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
              progressIndicator.setFraction(((double)myFileCount) / fileCount);
            }
            if (file instanceof PsiJavaFile) {
              inferrer.collect(file);
            }
          }
        });
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(searchForUsages, INFER_NULLITY_ANNOTATIONS, true, project)) {
        return null;
      }
    } else {
      searchForUsages.run();
    }

    final List<UsageInfo> usages = new ArrayList<UsageInfo>();
    inferrer.collect(usages);
    return usages.toArray(new UsageInfo[usages.size()]);
  }

  // For Android we need to check SDK version and possibly update the gradle project file
  protected boolean checkModules(@NotNull final Project project,
                                 @NotNull final AnalysisScope scope,
                                 @NotNull Map<Module, PsiFile> modules) {
    RepositoryUrlManager manager = RepositoryUrlManager.get();
    final String libraryCoordinate = manager.getLibraryCoordinate(RepositoryUrlManager.SUPPORT_ANNOTATIONS);

    final Set<Module> modulesWithoutAnnotations = new HashSet<Module>();
    final Set<Module> modulesWithLowVersion = new HashSet<Module>();
    for (Module module : modules.keySet()) {
      AndroidModuleInfo info = AndroidModuleInfo.get(module);
      if (info != null && info.getBuildSdkVersion() != null && info.getBuildSdkVersion().getFeatureLevel() <  MIN_SDK_WITH_NULLABLE) {
        modulesWithLowVersion.add(module);
      }
      GradleBuildFile gradleBuildFile = GradleBuildFile.get(module);
      if (gradleBuildFile == null) {
        LOG.warn("Unable to find Gradle build file for module " + module.getModuleFilePath());
        continue;
      }
      boolean dependencyFound = false;
      for (BuildFileStatement entry : gradleBuildFile.getDependencies()) {
        if (entry instanceof Dependency) {
          Dependency dependency = (Dependency)entry;
          if (dependency.scope == Dependency.Scope.COMPILE &&
              dependency.type == Dependency.Type.EXTERNAL &&
              dependency.getValueAsString().equals(libraryCoordinate)) {
            dependencyFound = true;
            break;
          }
        }
      }
      if (!dependencyFound) {
        modulesWithoutAnnotations.add(module);
      }
    }

    if (!modulesWithLowVersion.isEmpty()) {
      Messages.showErrorDialog(
        project,
        String.format("Infer Nullity Annotations requires the project sdk level be set to %1$d or greater.", MIN_SDK_WITH_NULLABLE),
        "Infer Nullity Annotations");
      return false;
    }
    if (modulesWithoutAnnotations.isEmpty()) {
      return true;
    }
    String moduleNames = StringUtil.join(modulesWithoutAnnotations, new Function<Module, String>() {
      @Override
      public String fun(Module module) {
        return module.getName();
      }
    }, ", ");
    int count = modulesWithoutAnnotations.size();
    String message = String.format("The %1$s %2$s %3$sn't refer to the existing '%4$s' library with Android nullity annotations. \n\n" +
                                   "Would you like to add the %5$s now?",
                                   pluralize("module", count),
                                   moduleNames,
                                   count > 1 ? "do" : "does",
                                   RepositoryUrlManager.SUPPORT_ANNOTATIONS,
                                   pluralize("dependency", count));
    if (Messages.showOkCancelDialog(project, message, "Infer Nullity Annotations", Messages.getErrorIcon()) == Messages.OK) {
      final LocalHistoryAction action = LocalHistory.getInstance().startAction(ADD_DEPENDENCY);
      try {
        new WriteCommandAction(project, ADD_DEPENDENCY) {
          @Override
          protected void run(@NotNull final Result result) throws Throwable {
            for (Module module : modulesWithoutAnnotations) {
              addDependency(module, libraryCoordinate);
            }
            GradleProjectImporter.getInstance().requestProjectSync(project, false /* do not generate sources */, null);
          }
        }.execute();
        restartAnalysis(project, scope);
      }
      finally {
        action.finish();
      }
    }
    return true;
  }

  // Intellij code from InferNullityAnnotationsAction.
  private static Runnable applyRunnable(final Project project, final Computable<UsageInfo[]> computable) {
    return new Runnable() {
      @Override
      public void run() {
        final LocalHistoryAction action = LocalHistory.getInstance().startAction(INFER_NULLITY_ANNOTATIONS);
        try {
          new WriteCommandAction(project, INFER_NULLITY_ANNOTATIONS) {
            @Override
            protected void run(@NotNull Result result) throws Throwable {
              final UsageInfo[] infos = computable.compute();
              if (infos.length > 0) {

                final Set<PsiElement> elements = new LinkedHashSet<PsiElement>();
                for (UsageInfo info : infos) {
                  final PsiElement element = info.getElement();
                  if (element != null) {
                    ContainerUtil.addIfNotNull(elements, element.getContainingFile());
                  }
                }
                if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) return;

                final SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, INFER_NULLITY_ANNOTATIONS, false);
                progressTask.setMinIterationTime(200);
                progressTask.setTask(new AnnotateTask(project, progressTask, infos));
                ProgressManager.getInstance().run(progressTask);
              } else {
                NullityInferrer.nothingFoundMessage(project);
              }
            }
          }.execute();
        }
        finally {
          action.finish();
        }
      }
    };
  }

  // Intellij code from InferNullityAnnotationsAction.
  protected void restartAnalysis(final Project project, final AnalysisScope scope) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        analyze(project, scope);
      }
    });
  }

  // Intellij code from InferNullityAnnotationsAction.
  private void showUsageView(@NotNull Project project, final UsageInfo[] usageInfos, @NotNull AnalysisScope scope) {
    final UsageTarget[] targets = UsageTarget.EMPTY_ARRAY;
    final Ref<Usage[]> convertUsagesRef = new Ref<Usage[]>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            convertUsagesRef.set(UsageInfo2UsageAdapter.convert(usageInfos));
          }
        });
      }
    }, "Preprocess usages", true, project)) return;

    if (convertUsagesRef.isNull()) return;
    final Usage[] usages = convertUsagesRef.get();

    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText("Infer Nullity Preview");
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setUsagesString(RefactoringBundle.message("usageView.usagesText"));

    final UsageView usageView = UsageViewManager.getInstance(project).showUsages(targets, usages, presentation, rerunFactory(project, scope));

    final Runnable refactoringRunnable = applyRunnable(project, new Computable<UsageInfo[]>() {
      @Override
      public UsageInfo[] compute() {
        final Set<UsageInfo> infos = UsageViewUtil.getNotExcludedUsageInfos(usageView);
        return infos.toArray(new UsageInfo[infos.size()]);
      }
    });

    String canNotMakeString = "Cannot perform operation.\nThere were changes in code after usages have been found.\nPlease perform operation search again.";

    usageView.addPerformOperationAction(refactoringRunnable, INFER_NULLITY_ANNOTATIONS, canNotMakeString, INFER_NULLITY_ANNOTATIONS, false);
  }

  // Intellij code from InferNullityAnnotationsAction.
  @NotNull
  private static Factory<UsageSearcher> rerunFactory(@NotNull final Project project, @NotNull final AnalysisScope scope) {
    return new Factory<UsageSearcher>() {
      @Override
      public UsageSearcher create() {
        return new UsageInfoSearcherAdapter() {
          @Override
          protected UsageInfo[] findUsages() {
            return AndroidInferNullityAnnotationAction.findUsages(project, scope, scope.getFileCount());
          }

          @Override
          public void generate(@NotNull Processor<Usage> processor) {
            processUsages(processor, project);
          }
        };
      }
    };
  }

  private static void addDependency(final Module module, final String libraryCoordinate) {
    ModuleRootModificationUtil.updateModel(module, new Consumer<ModifiableRootModel>() {
      @Override
      public void consume(ModifiableRootModel model) {
        GradleBuildFile gradleBuildFile = GradleBuildFile.get(module);
        if (gradleBuildFile != null) {
          List<BuildFileStatement> dependencies = gradleBuildFile.getDependencies();
          dependencies.add(new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, libraryCoordinate));
          gradleBuildFile.setValue(BuildFileKey.DEPENDENCIES, dependencies);
        }
      }
    });
  }

  /* Android nullable annotations do not support annotations on local variables. */
  @Override
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog) {
    if (!Projects.isBuildWithGradle(project)) {
      return super.getAdditionalActionSettings(project, dialog);
    }
    return null;
  }
}
