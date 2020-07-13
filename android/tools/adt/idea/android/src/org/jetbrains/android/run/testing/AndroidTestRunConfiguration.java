/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.run.testing;

import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Variant;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.google.common.base.Predicate;

import com.android.tools.idea.gradle.util.Projects;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.android.dom.manifest.Instrumentation;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.run.*;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * User: Eugene.Kudelevsky
 * Date: Aug 27, 2009
 * Time: 2:23:56 PM
 */
public class AndroidTestRunConfiguration extends AndroidRunConfigurationBase implements RefactoringListenerProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.testing.AndroidTestRunConfiguration");

  public static final int TEST_ALL_IN_MODULE = 0;
  public static final int TEST_ALL_IN_PACKAGE = 1;
  public static final int TEST_CLASS = 2;
  public static final int TEST_METHOD = 3;

  public int TESTING_TYPE = TEST_ALL_IN_MODULE;
  public String INSTRUMENTATION_RUNNER_CLASS = "";

  public String METHOD_NAME = "";
  public String CLASS_NAME = "";
  public String PACKAGE_NAME = "";

  public AndroidTestRunConfiguration(final Project project, final ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  protected Pair<Boolean, String> supportsRunningLibraryProjects(@NotNull AndroidFacet facet) {
    if (!facet.requiresAndroidModel()) {
      // Non Gradle projects always require an application
      return Pair.create(Boolean.FALSE, AndroidBundle.message("android.cannot.run.library.project.error"));
    }

    IdeaAndroidProject androidModel = facet.getAndroidModel();
    if (androidModel == null) {
      return Pair.create(Boolean.FALSE, AndroidBundle.message("android.cannot.run.library.project.error"));
    }

    // Gradle only supports testing against a single build type (which could be anything, but is "debug" build type by default)
    // Currently, the only information the model exports that we can use to detect whether the current build type
    // is testable is by looking at the test task name and checking whether it is null.
    BaseArtifact testArtifact = androidModel.findSelectedTestArtifactInSelectedVariant();
    String testTask = testArtifact != null ? testArtifact.getAssembleTaskName() : null;
    return new Pair<Boolean, String>(testTask != null, AndroidBundle.message("android.cannot.run.library.project.in.this.buildtype"));
  }

  @Override
  public boolean isGeneratedName() {
    final String name = getName();

    if ((TESTING_TYPE == TEST_CLASS || TESTING_TYPE == TEST_METHOD) &&
        (CLASS_NAME == null || CLASS_NAME.length() == 0)) {
      return JavaExecutionUtil.isNewName(name);
    }
    if (TESTING_TYPE == TEST_METHOD &&
        (METHOD_NAME == null || METHOD_NAME.length() == 0)) {
      return JavaExecutionUtil.isNewName(name);
    }
    return Comparing.equal(name, suggestedName());
  }

  @Override
  public String suggestedName() {
    if (TESTING_TYPE == TEST_ALL_IN_PACKAGE) {
      return ExecutionBundle.message("test.in.scope.presentable.text", PACKAGE_NAME);
    }
    else if (TESTING_TYPE == TEST_CLASS) {
      return ProgramRunnerUtil.shortenName(JavaExecutionUtil.getShortClassName(CLASS_NAME), 0);
    }
    else if (TESTING_TYPE == TEST_METHOD) {
      return ProgramRunnerUtil.shortenName(METHOD_NAME, 2) + "()";
    }
    return ExecutionBundle.message("all.tests.scope.presentable.text");
  }

  @Override
  public void checkConfiguration(@NotNull AndroidFacet facet) throws RuntimeConfigurationException {
    if (getTargetSelectionMode() == TargetSelectionMode.CLOUD_MATRIX_TEST && !IS_VALID_CLOUD_MATRIX_SELECTION) {
      throw new RuntimeConfigurationError(INVALID_CLOUD_MATRIX_SELECTION_ERROR);
    }
    if (getTargetSelectionMode() == TargetSelectionMode.CLOUD_DEVICE_LAUNCH && !IS_VALID_CLOUD_DEVICE_SELECTION) {
      throw new RuntimeConfigurationError(INVALID_CLOUD_DEVICE_SELECTION_ERROR);
    }

    Module module = facet.getModule();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    switch (TESTING_TYPE) {
      case TEST_ALL_IN_PACKAGE:
        final PsiPackage testPackage = facade.findPackage(PACKAGE_NAME);
        if (testPackage == null) {
          throw new RuntimeConfigurationWarning(ExecutionBundle.message("package.does.not.exist.error.message", PACKAGE_NAME));
        }
        break;
      case TEST_CLASS:
        final PsiClass testClass =
          getConfigurationModule().checkModuleAndClassName(CLASS_NAME, ExecutionBundle.message("no.test.class.specified.error.text"));
        if (!JUnitUtil.isTestClass(testClass)) {
          throw new RuntimeConfigurationWarning(ExecutionBundle.message("class.isnt.test.class.error.message", CLASS_NAME));
        }
        break;
      case TEST_METHOD:
        checkTestMethod();
        break;
    }
    if (INSTRUMENTATION_RUNNER_CLASS.length() > 0) {
      if (facade.findClass(INSTRUMENTATION_RUNNER_CLASS, module.getModuleWithDependenciesAndLibrariesScope(true)) == null) {
        throw new RuntimeConfigurationError(AndroidBundle.message("instrumentation.runner.class.not.specified.error"));
      }
    }
  }

  @Override
  public AndroidRunningState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final AndroidRunningState state = super.getState(executor, env);

    if (state == null) {
      return null;
    }

    final AndroidFacet facet = state.getFacet();
    final AndroidFacetConfiguration configuration = facet.getConfiguration();

    if (!facet.requiresAndroidModel() && !configuration.getState().PACK_TEST_CODE) {
      final Module module = facet.getModule();
      final int count = getTestSourceRootCount(module);
      
      if (count > 0) {
        final String message = "Code and resources under test source " + (count > 1 ? "roots" : "root") +
                               " aren't included into debug APK.\nWould you like to include them and recompile " +
                               module.getName() + " module?" + "\n(You may change this option in Android facet settings later)";
        final int result =
          Messages.showYesNoCancelDialog(getProject(), message, "Test code not included into APK", Messages.getQuestionIcon());
        
        if (result == Messages.YES) {
          configuration.getState().PACK_TEST_CODE = true;
        }
        else if (result == Messages.CANCEL) {
          return null;
        }
      }
    }
    return state;
  }

  @Override
  @NotNull
  protected ApkProvider getApkProvider() {
    Module module = getConfigurationModule().getModule();
    assert module != null;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    if (facet.getAndroidModel() != null && Projects.isBuildWithGradle(module)) {
      return new GradleApkProvider(facet, true);
    }
    return new NonGradleApkProvider(facet, null);
  }
  
  private static int getTestSourceRootCount(@NotNull Module module) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    return manager.getSourceRoots(true).length - manager.getSourceRoots(false).length;
  }

  private void checkTestMethod() throws RuntimeConfigurationException {
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    final PsiClass testClass =
      configurationModule.checkModuleAndClassName(CLASS_NAME, ExecutionBundle.message("no.test.class.specified.error.text"));
    if (!JUnitUtil.isTestClass(testClass)) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("class.isnt.test.class.error.message", CLASS_NAME));
    }
    if (METHOD_NAME == null || METHOD_NAME.trim().length() == 0) {
      throw new RuntimeConfigurationError(ExecutionBundle.message("method.name.not.specified.error.message"));
    }
    final JUnitUtil.TestMethodFilter filter = new JUnitUtil.TestMethodFilter(testClass);
    boolean found = false;
    boolean testAnnotated = false;
    for (final PsiMethod method : testClass.findMethodsByName(METHOD_NAME, true)) {
      if (filter.value(method)) found = true;
      if (JUnitUtil.isTestAnnotated(method)) testAnnotated = true;
    }
    if (!found) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("test.method.doesnt.exist.error.message", METHOD_NAME));
    }

    if (!AnnotationUtil.isAnnotated(testClass, JUnitUtil.RUN_WITH, true) && !testAnnotated) {
      try {
        final PsiClass testCaseClass = JUnitUtil.getTestCaseClass(configurationModule.getModule());
        if (!testClass.isInheritor(testCaseClass, true)) {
          throw new RuntimeConfigurationError(ExecutionBundle.message("class.isnt.inheritor.of.testcase.error.message", CLASS_NAME));
        }
      }
      catch (JUnitUtil.NoJUnitException e) {
        throw new RuntimeConfigurationWarning(ExecutionBundle.message(AndroidBundle.message("cannot.find.testcase.error")));
      }
    }
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    Project project = getProject();
    AndroidRunConfigurationEditor<AndroidTestRunConfiguration> editor =
      new AndroidRunConfigurationEditor<AndroidTestRunConfiguration>(project, new Predicate<AndroidFacet>() {
        @Override
        public boolean apply(@Nullable AndroidFacet facet) {
          return facet != null && supportsRunningLibraryProjects(facet).getFirst();
        }
      });
    editor.setConfigurationSpecificEditor(new TestRunParameters(project, editor.getModuleSelector()));
    return editor;
  }

  @NotNull
  @Override
  protected ConsoleView attachConsole(AndroidRunningState state, Executor executor) throws ExecutionException {
    final AndroidTestConsoleProperties properties = new AndroidTestConsoleProperties(this, executor);

    //TODO[for android guys]: attach stactrace filter for "Open Source at Exception" feature:
    // properties.addStackTraceFilter(new ...);
    
    BaseTestsOutputConsoleView consoleView = SMTestRunnerConnectionUtil
      .createAndAttachConsole("Android", state.getProcessHandler(), properties, state.getEnvironment()
      );
    Disposer.register(state.getFacet().getModule().getProject(), consoleView);
    return consoleView;
  }

  @Override
  protected boolean supportMultipleDevices() {
    return false;
  }

  @NotNull
  @Override
  protected AndroidApplicationLauncher getApplicationLauncher(AndroidFacet facet) {
    String runner = StringUtil.isEmpty(INSTRUMENTATION_RUNNER_CLASS) ? findInstrumentationRunner(facet) : INSTRUMENTATION_RUNNER_CLASS;
    return new MyApplicationLauncher(runner);
  }

  @Nullable
  public static String findInstrumentationRunner(@NotNull AndroidFacet facet) {
    String runner = getRunnerFromManifest(facet);

    IdeaAndroidProject androidModel = facet.getAndroidModel();
    if (runner == null && androidModel != null) {
      Variant selectedVariant = androidModel.getSelectedVariant();
      String testRunner = selectedVariant.getMergedFlavor().getTestInstrumentationRunner();
      if (testRunner != null) {
        runner = testRunner;
      }
    }

    return runner;
  }

  @Nullable
  private static String getRunnerFromManifest(@NotNull AndroidFacet facet) {
    Manifest manifest = facet.getManifest();
    if (manifest != null) {
      for (Instrumentation instrumentation : manifest.getInstrumentations()) {
        if (instrumentation != null) {
          PsiClass instrumentationClass = instrumentation.getInstrumentationClass().getValue();
          if (instrumentationClass != null) {
            return instrumentationClass.getQualifiedName();
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns a refactoring listener that listens to changes in either the package, class or method names
   * depending on the current {@link #TESTING_TYPE}.
   */
  @Nullable
  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (element instanceof PsiPackage) {
      String pkgName = ((PsiPackage)element).getQualifiedName();
      if (TESTING_TYPE == TEST_ALL_IN_PACKAGE && !StringUtil.equals(pkgName, PACKAGE_NAME)) {
        // testing package, but the refactored package does not match our package
        return null;
      } else if (TESTING_TYPE != TEST_ALL_IN_PACKAGE && !StringUtil.equals(pkgName, StringUtil.getPackageName(CLASS_NAME))) {
        // testing a class or a method, but the refactored package doesn't match our containing package
        return null;
      }

      return new RefactoringElementAdapter() {
        @Override
        protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          if (newElement instanceof PsiPackage) {
            String newPkgName = ((PsiPackage)newElement).getQualifiedName();
            if (TESTING_TYPE == TEST_ALL_IN_PACKAGE) {
              PACKAGE_NAME = newPkgName;
            } else {
              CLASS_NAME = CLASS_NAME.replace(StringUtil.getPackageName(CLASS_NAME), newPkgName);
            }
          }
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          if (newElement instanceof PsiPackage) {
            if (TESTING_TYPE == TEST_ALL_IN_PACKAGE) {
              PACKAGE_NAME = oldQualifiedName;
            } else {
              CLASS_NAME = CLASS_NAME.replace(StringUtil.getPackageName(CLASS_NAME), oldQualifiedName);
            }
          }
        }
      };
    } else if ((TESTING_TYPE == TEST_CLASS || TESTING_TYPE == TEST_METHOD) && element instanceof PsiClass) {
      if (!StringUtil.equals(JavaExecutionUtil.getRuntimeQualifiedName((PsiClass)element), CLASS_NAME)) {
        return null;
      }

      return new RefactoringElementAdapter() {
        @Override
        protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          if (newElement instanceof PsiClass) {
            CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName((PsiClass)newElement);
          }
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          if (newElement instanceof PsiClass) {
            CLASS_NAME = oldQualifiedName;
          }
        }
      };
    } else if (TESTING_TYPE == TEST_METHOD && element instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)element;
      if (!StringUtil.equals(psiMethod.getName(), METHOD_NAME)) {
        return null;
      }

      PsiClass psiClass = psiMethod.getContainingClass();
      if (psiClass == null) {
        return null;
      }

      String fqName = psiClass.getQualifiedName();
      if (fqName != null && !StringUtil.equals(fqName, CLASS_NAME)) {
        return null;
      }

      return new RefactoringElementAdapter() {
        @Override
        protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          if (newElement instanceof PsiMethod) {
            METHOD_NAME = ((PsiMethod)newElement).getName();
          }
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          if (newElement instanceof PsiMethod) {
            METHOD_NAME = oldQualifiedName;
          }
        }
      };
    }
    return null;
  }

  private class MyApplicationLauncher extends AndroidApplicationLauncher {
    private final String myInstrumentationTestRunner;

    private MyApplicationLauncher(@Nullable String instrumentationTestRunner) {
      myInstrumentationTestRunner = instrumentationTestRunner;
    }

    @Override
    public LaunchResult launch(@NotNull AndroidRunningState state, @NotNull IDevice device)
      throws IOException, AdbCommandRejectedException, TimeoutException {
      state.getProcessHandler().notifyTextAvailable("Running tests\n", ProcessOutputTypes.STDOUT);
      RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(state.getTestPackageName(), myInstrumentationTestRunner, device);
      switch (TESTING_TYPE) {
        case TEST_ALL_IN_PACKAGE:
          runner.setTestPackageName(PACKAGE_NAME);
          break;
        case TEST_CLASS:
          runner.setClassName(CLASS_NAME);
          break;
        case TEST_METHOD:
          runner.setMethodName(CLASS_NAME, METHOD_NAME);
          break;
      }
      runner.setDebug(state.isDebugMode());
      try {
        runner.run(new AndroidTestListener(state));
      }
      catch (ShellCommandUnresponsiveException e) {
        LOG.info(e);
        state.getProcessHandler().notifyTextAvailable("Error: time out", ProcessOutputTypes.STDERR);
      }
      return LaunchResult.SUCCESS;
    }
  }
}
