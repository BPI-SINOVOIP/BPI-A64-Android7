/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.util.ProjectBuilder;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.google.common.collect.Lists;
import com.intellij.ProjectTopics;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer;
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer;
import org.jetbrains.plugins.gradle.execution.test.runner.TestMethodGradleConfigurationProducer;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.intellij.openapi.externalSystem.util.ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY;
import static com.intellij.openapi.util.text.StringUtil.join;

public class AndroidGradleProjectComponent extends AbstractProjectComponent {
  @NonNls private static final String SHOW_MIGRATE_TO_GRADLE_POPUP = "show.migrate.to.gradle.popup";

  @Nullable private Disposable myDisposable;

  @NotNull
  public static AndroidGradleProjectComponent getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidGradleProjectComponent.class);
  }

  public AndroidGradleProjectComponent(@NotNull final Project project) {
    super(project);
    // Register a task that will be executed after project build (e.g. make, rebuild, generate sources) with JPS.
    ProjectBuilder.getInstance(project).addAfterProjectBuildTask(new ProjectBuilder.AfterProjectBuildTask() {
      @Override
      public void execute(@NotNull GradleInvocationResult result) {
        PostProjectBuildTasksExecutor.getInstance(project).onBuildCompletion(result);
      }

      @Override
      public boolean execute(CompileContext context) {
        PostProjectBuildTasksExecutor.getInstance(project).onBuildCompletion(context);
        return true;
      }
    });
  }

  /**
   * This method is called when a project is created and when it is opened.
   */
  @Override
  public void projectOpened() {
    checkForSupportedModules();
    GradleSyncState syncState = GradleSyncState.getInstance(myProject);
    if (syncState.isSyncInProgress()) {
      // when opening a new project, the UI was not updated when sync started. Updating UI ("Build Variants" tool window, "Sync" toolbar
      // button and editor notifications.
      syncState.notifyUser();
    }
    if (shouldShowMigrateToGradleNotification()
        && AndroidStudioSpecificInitializer.isAndroidStudio()
        && Projects.isIdeaAndroidProject(myProject)) {
      // Suggest that Android Studio users use Gradle instead of IDEA project builder.
      showMigrateToGradleWarning();
      return;
    }

    boolean isGradleProject = isBuildWithGradle(myProject);
    if (isGradleProject) {
      configureGradleProject();
    }
  }

  private boolean shouldShowMigrateToGradleNotification() {
    return PropertiesComponent.getInstance(myProject).getBoolean(SHOW_MIGRATE_TO_GRADLE_POPUP, true);
  }

  private void showMigrateToGradleWarning() {
    String errMsg = "This project does not use the Gradle build system. We recommend that you migrate to using the Gradle build system.";
    NotificationHyperlink moreInfoHyperlink = new OpenMigrationToGradleUrlHyperlink();
    NotificationHyperlink doNotShowAgainHyperlink = new NotificationHyperlink("do.not.show", "Don't show this message again.") {
      @Override
      protected void execute(@NotNull Project project) {
        PropertiesComponent.getInstance(myProject).setValue(SHOW_MIGRATE_TO_GRADLE_POPUP, Boolean.FALSE.toString());
      }
    };

    AndroidGradleNotification notification = AndroidGradleNotification.getInstance(myProject);
    notification.showBalloon("Migrate Project to Gradle?", errMsg, NotificationType.WARNING, moreInfoHyperlink, doNotShowAgainHyperlink);
  }

  public void configureGradleProject() {
    if (myDisposable != null) {
      return;
    }
    myDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };

    // Prevent IDEA from refreshing project. We will do it ourselves in AndroidGradleProjectStartupActivity.
    myProject.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, Boolean.TRUE);

    listenForProjectChanges(myProject, myDisposable);

    Projects.enforceExternalBuild(myProject);

    // Make sure the gradle test configurations are ignored in this project, since they don't work in Android gradle projects. This
    // will modify .idea/runConfigurations.xml
    RunConfigurationProducerService runConfigurationProducerManager = RunConfigurationProducerService.getInstance(myProject);
    runConfigurationProducerManager.addIgnoredProducer(AllInPackageGradleConfigurationProducer.class);
    runConfigurationProducerManager.addIgnoredProducer(TestMethodGradleConfigurationProducer.class);
    runConfigurationProducerManager.addIgnoredProducer(TestClassGradleConfigurationProducer.class);
  }

  private static void listenForProjectChanges(@NotNull Project project, @NotNull Disposable disposable) {
    GradleBuildFileUpdater buildFileUpdater = new GradleBuildFileUpdater(project);

    GradleModuleListener moduleListener = new GradleModuleListener();
    moduleListener.addModuleListener(buildFileUpdater);

    MessageBusConnection connection = project.getMessageBus().connect(disposable);
    connection.subscribe(ProjectTopics.MODULES, moduleListener);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, buildFileUpdater);
  }

  @Override
  public void projectClosed() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
    }
  }

  /**
   * Verifies that the project, if it is an Android Gradle project, does not have any modules that are not known by Gradle. For example,
   * when adding a plain IDEA Java module.
   * Do not call this method from {@link ModuleListener#moduleAdded(Project, Module)} because the settings that this method look for are
   * not present when importing a valid Gradle-aware module, resulting in false positives.
   */
  public void checkForSupportedModules() {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    if (modules.length == 0 || !isBuildWithGradle(myProject)) {
      return;
    }
    final List<Module> unsupportedModules = new ArrayList<Module>();

    for (Module module : modules) {
      final ModuleType moduleType = ModuleType.get(module);

      if (moduleType instanceof JavaModuleType) {
        final String externalSystemId = module.getOptionValue(EXTERNAL_SYSTEM_ID_KEY);

        if (!GRADLE_SYSTEM_ID.getId().equals(externalSystemId)) {
          unsupportedModules.add(module);
        }
      }
    }

    if (unsupportedModules.size() == 0) {
      return;
    }
    final String s = join(unsupportedModules, new Function<Module, String>() {
      @Override
      public String fun(Module module) {
        return module.getName();
      }
    }, ", ");
    AndroidGradleNotification.getInstance(myProject).showBalloon(
      "Unsupported Modules Detected",
      "Compilation is not supported for following modules: " + s +
      ". Unfortunately you can't have non-Gradle Java modules and Android-Gradle modules in one project.",
      NotificationType.ERROR);
  }

  private static class GradleModuleListener implements ModuleListener {
    @NotNull private final List<ModuleListener> additionalListeners = Lists.newArrayList();

    @Override
    public void moduleAdded(Project project, Module module) {
      updateBuildVariantView(project);
      for (ModuleListener listener : additionalListeners) {
        listener.moduleAdded(project, module);
      }
    }

    @Override
    public void beforeModuleRemoved(Project project, Module module) {
      for (ModuleListener listener : additionalListeners) {
        listener.beforeModuleRemoved(project, module);
      }
    }

    @Override
    public void modulesRenamed(Project project, List<Module> modules, Function<Module, String> oldNameProvider) {
      updateBuildVariantView(project);
      for (ModuleListener listener : additionalListeners) {
        listener.modulesRenamed(project, modules, oldNameProvider);
      }
    }

    @Override
    public void moduleRemoved(Project project, Module module) {
      updateBuildVariantView(project);
      for (ModuleListener listener : additionalListeners) {
        listener.moduleRemoved(project, module);
      }
    }

    private static void updateBuildVariantView(@NotNull Project project) {
      BuildVariantView.getInstance(project).updateContents();
    }

    void addModuleListener(@NotNull ModuleListener listener) {
      additionalListeners.add(listener);
    }
  }
}
