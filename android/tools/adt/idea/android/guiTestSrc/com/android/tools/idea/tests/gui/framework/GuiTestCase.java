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
package com.android.tools.idea.tests.gui.framework;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.util.SystemProperties;
import com.intellij.util.net.HttpConfigurable;
import org.fest.swing.core.BasicRobot;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Condition;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.android.AndroidPlugin.GuiTestSuiteState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.tests.gui.framework.GuiTestRunner.canRunGuiTests;
import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.intellij.ide.impl.ProjectUtil.closeAndDispose;
import static com.intellij.openapi.project.ProjectCoreUtil.DIRECTORY_BASED_PROJECT_DIR;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.io.FileUtilRt.delete;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.util.Strings.quote;
import static org.jetbrains.android.AndroidPlugin.getGuiTestSuiteState;
import static org.junit.Assert.assertTrue;

@RunWith(GuiTestRunner.class)
public abstract class GuiTestCase {
  protected Robot myRobot;

  @SuppressWarnings("UnusedDeclaration") // This field is set via reflection.
  private String myTestName;

  /**
   * @return the name of the test method being executed.
   */
  protected String getTestName() {
    return myTestName;
  }

  @Before
  public void setUp() throws Exception {
    if (!canRunGuiTests()) {
      // We currently do not support running UI tests in headless environments.
      return;
    }

    Application application = ApplicationManager.getApplication();
    assertNotNull(application); // verify that we are using the IDE's ClassLoader.

    setUpDefaultProjectCreationLocationPath();

    myRobot = BasicRobot.robotWithCurrentAwtHierarchy();
    myRobot.settings().delayBetweenEvents(30);

    setIdeSettings();
  }

  private static void setIdeSettings() {
    GradleExperimentalSettings.getInstance().SELECT_MODULES_ON_PROJECT_IMPORT = false;

    // Clear HTTP proxy settings, in case a test changed them.
    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = false;
    ideSettings.PROXY_HOST = "";
    ideSettings.PROXY_PORT = 80;

    GuiTestSuiteState state = getGuiTestSuiteState();
    state.setSkipSdkMerge(false);
    state.setUseCachedGradleModelOnly(false);
  }

  @After
  public void tearDown() {
    if (myRobot != null) {
      myRobot.cleanUpWithoutDisposingWindows();
    }
  }

  @AfterClass
  public static void tearDownPerClass() {
    boolean inSuite = SystemProperties.getBooleanProperty(GUI_TESTS_RUNNING_IN_SUITE_PROPERTY, false);
    if (!inSuite) {
      IdeTestApplication.disposeInstance();
    }
  }

  @NotNull
  protected WelcomeFrameFixture findWelcomeFrame() {
    return WelcomeFrameFixture.find(myRobot);
  }

  @NotNull
  protected NewProjectWizardFixture findNewProjectWizard() {
    return NewProjectWizardFixture.find(myRobot);
  }

  @NotNull
  protected IdeFrameFixture findIdeFrame(@NotNull String projectName, @NotNull File projectPath) {
    return IdeFrameFixture.find(myRobot, projectPath, projectName);
  }

  @SuppressWarnings("UnusedDeclaration")
  // Called by GuiTestRunner via reflection.
  protected void closeAllProjects() {
    pause(new Condition("Close all projects") {
      @Override
      public boolean test() {
        final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        execute(new GuiTask() {
          @Override
          protected void executeInEDT() throws Throwable {
            for (Project project : openProjects) {
              assertTrue("Failed to close project " + quote(project.getName()), closeAndDispose(project));
            }
          }
        });
        return ProjectManager.getInstance().getOpenProjects().length == 0;
      }
    }, SHORT_TIMEOUT);

    //noinspection ConstantConditions
    boolean welcomeFrameShown = execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0) {
          WelcomeFrame.showNow();

          WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
          windowManager.disposeRootFrame();
          return true;
        }
        return false;
      }
    });

    if (welcomeFrameShown) {
      pause(new Condition("'Welcome' frame to show up") {
        @Override
        public boolean test() {
          for (Frame frame : Frame.getFrames()) {
            if (frame instanceof WelcomeFrame && frame.isShowing()) {
              return true;
            }
          }
          return false;
        }
      }, SHORT_TIMEOUT);
    }
  }

  @NotNull
  protected IdeFrameFixture importSimpleApplication() throws IOException {
    return importProjectAndWaitForProjectSyncToFinish("SimpleApplication");
  }

  @NotNull
  protected IdeFrameFixture importProjectAndWaitForProjectSyncToFinish(@NotNull String projectDirName) throws IOException {
    File projectPath = setUpProject(projectDirName, false, true, null);
    VirtualFile toSelect = findFileByIoFile(projectPath, true);
    assertNotNull(toSelect);

    GuiTestSuiteState testSuiteState = getGuiTestSuiteState();
    if (!testSuiteState.isImportProjectWizardAlreadyTested()) {
      testSuiteState.setImportProjectWizardAlreadyTested(true);

      findWelcomeFrame().clickImportProjectButton();

      FileChooserDialogFixture importProjectDialog = FileChooserDialogFixture.findImportProjectDialog(myRobot);
      return openProjectAndWaitUntilOpened(toSelect, importProjectDialog);
    }

    doImportProject(toSelect);

    IdeFrameFixture projectFrame = findIdeFrame(projectPath);
    projectFrame.waitForGradleProjectSyncToFinish();

    return projectFrame;
  }

  @NotNull
  protected File importProject(@NotNull String projectDirName) throws IOException {
    File projectPath = setUpProject(projectDirName, false, true, null);
    VirtualFile toSelect = findFileByIoFile(projectPath, true);
    assertNotNull(toSelect);
    doImportProject(toSelect);
    return projectPath;
  }

  private static void doImportProject(@NotNull final VirtualFile projectDir) {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        GradleProjectImporter.getInstance().importProject(projectDir);
      }
    });
  }

  @NotNull
  private IdeFrameFixture openProject(@NotNull String projectDirName) throws IOException {
    File projectPath = setUpProject(projectDirName, true, true, null);
    return openProject(projectPath);
  }

  @NotNull
  private IdeFrameFixture openProject(@NotNull final File projectPath) {
    VirtualFile toSelect = findFileByIoFile(projectPath, true);
    assertNotNull(toSelect);

    GuiTestSuiteState state = getGuiTestSuiteState();
    if (!state.isOpenProjectWizardAlreadyTested()) {
      state.setOpenProjectWizardAlreadyTested(true);

      findWelcomeFrame().clickOpenProjectButton();

      FileChooserDialogFixture openProjectDialog = FileChooserDialogFixture.findOpenProjectDialog(myRobot);
      return openProjectAndWaitUntilOpened(toSelect, openProjectDialog);
    }

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
        projectManager.loadAndOpenProject(projectPath.getPath());
      }
    });

    IdeFrameFixture projectFrame = findIdeFrame(projectPath);
    projectFrame.waitForGradleProjectSyncToFinish();

    return projectFrame;
  }

  /**
   * Sets up a project before using it in a UI test:
   * <ul>
   * <li>Makes a copy of the project in testData/guiTests/newProjects (deletes any existing copy of the project first.) This copy is
   * the one the test will use.</li>
   * <li>Creates a Gradle wrapper for the test project.</li>
   * <li>Updates the version of the Android Gradle plug-in used by the project, if applicable</li>
   * <li>Creates a local.properties file pointing to the Android SDK path specified by the system property (or environment variable)
   * 'ADT_TEST_SDK_PATH'</li>
   * <li>Copies over missing files to the .idea directory (if the project will be opened, instead of imported.)</li>
   * <li>Deletes .idea directory, .iml files and build directories, if the project will be imported.</li>
   * <p/>
   * </ul>
   *
   * @param projectDirName the name of the project's root directory. Tests are located in testData/guiTests.
   * @param forOpen indicates whether the project will be opened by the IDE, or imported.
   * @param updateAndroidPluginVersion indicates if the latest supported version of the Android Gradle plug-in should be set in the
   *                                   project.
   * @param gradleVersion the Gradle version to use in the wrapper. If {@code null} is passed, this method will use the latest supported
   *                      version of Gradle.
   * @return the path of project's root directory (the copy of the project, not the original one.)
   * @throws IOException if an unexpected I/O error occurs.
   */
  @NotNull
  private File setUpProject(@NotNull String projectDirName,
                            boolean forOpen,
                            boolean updateAndroidPluginVersion,
                            @Nullable String gradleVersion) throws IOException {
    File projectPath = copyProjectBeforeOpening(projectDirName);

    File gradlePropertiesFilePath = new File(projectPath, SdkConstants.FN_GRADLE_PROPERTIES);
    if (gradlePropertiesFilePath.isFile()) {
      delete(gradlePropertiesFilePath);
    }

    if (gradleVersion == null) {
      createGradleWrapper(projectPath, GRADLE_LATEST_VERSION);
    }
    else {
      createGradleWrapper(projectPath, gradleVersion);
    }

    if (updateAndroidPluginVersion) {
      updateGradleVersions(projectPath);
    }

    updateLocalProperties(projectPath);

    if (forOpen) {
      File toDotIdea = new File(projectPath, DIRECTORY_BASED_PROJECT_DIR);
      ensureExists(toDotIdea);

      File fromDotIdea = new File(getTestProjectsRootDirPath(), join("commonFiles", DIRECTORY_BASED_PROJECT_DIR));
      assertThat(fromDotIdea).isDirectory();

      for (File from : notNullize(fromDotIdea.listFiles())) {
        if (from.isDirectory()) {
          File destination = new File(toDotIdea, from.getName());
          if (!destination.isDirectory()) {
            copyDirContent(from, destination);
          }
          continue;
        }
        File to = new File(toDotIdea, from.getName());
        if (!to.isFile()) {
          copy(from, to);
        }
      }
    }
    else {
      cleanUpProjectForImport(projectPath);
    }

    return projectPath;
  }

  @NotNull
  protected File copyProjectBeforeOpening(@NotNull String projectDirName) throws IOException {
    File masterProjectPath = getMasterProjectDirPath(projectDirName);

    File projectPath = getTestProjectDirPath(projectDirName);
    delete(projectPath);
    copyDir(masterProjectPath, projectPath);
    return projectPath;
  }

  protected boolean createGradleWrapper(@NotNull File projectDirPath, @NotNull String gradleVersion) throws IOException {
    return GradleUtil.createGradleWrapper(projectDirPath, gradleVersion);
  }

  protected void updateLocalProperties(File projectPath) throws IOException {
    File androidHomePath = IdeSdks.getAndroidSdkPath();
    assertNotNull(androidHomePath);

    LocalProperties localProperties = new LocalProperties(projectPath);
    localProperties.setAndroidSdkPath(androidHomePath);
    localProperties.save();
  }

  protected void updateGradleVersions(@NotNull File projectPath) throws IOException {
    AndroidGradleTestCase.updateGradleVersions(projectPath);
  }

  @NotNull
  protected File getMasterProjectDirPath(@NotNull String projectDirName) {
    return new File(getTestProjectsRootDirPath(), projectDirName);
  }

  @NotNull
  protected File getTestProjectDirPath(@NotNull String projectDirName) {
    return new File(getProjectCreationDirPath(), projectDirName);
  }

  protected void cleanUpProjectForImport(@NotNull File projectPath) {
    File dotIdeaFolderPath = new File(projectPath, DIRECTORY_BASED_PROJECT_DIR);
    if (dotIdeaFolderPath.isDirectory()) {
      File modulesXmlFilePath = new File(dotIdeaFolderPath, "modules.xml");
      if (modulesXmlFilePath.isFile()) {
        SAXBuilder saxBuilder = new SAXBuilder();
        try {
          Document document = saxBuilder.build(modulesXmlFilePath);
          XPath xpath = XPath.newInstance("//*[@fileurl]");
          //noinspection unchecked
          List<Element> modules = xpath.selectNodes(document);
          int urlPrefixSize = "file://$PROJECT_DIR$/".length();
          for (Element module : modules) {
            String fileUrl = module.getAttributeValue("fileurl");
            if (!StringUtil.isEmpty(fileUrl)) {
              String relativePath = toSystemDependentName(fileUrl.substring(urlPrefixSize));
              File imlFilePath = new File(projectPath, relativePath);
              if (imlFilePath.isFile()) {
                delete(imlFilePath);
              }
              // It is likely that each module has a "build" folder. Delete it as well.
              File buildFilePath = new File(imlFilePath.getParentFile(), "build");
              if (buildFilePath.isDirectory()) {
                delete(buildFilePath);
              }
            }
          }
        }
        catch (Throwable ignored) {
          // if something goes wrong, just ignore. Most likely it won't affect project import in any way.
        }
      }
      delete(dotIdeaFolderPath);
    }
  }

  @NotNull
  protected IdeFrameFixture openProjectAndWaitUntilOpened(@NotNull VirtualFile projectDir,
                                                          @NotNull FileChooserDialogFixture fileChooserDialog) {
    fileChooserDialog.select(projectDir).clickOk();

    File projectPath = virtualToIoFile(projectDir);
    IdeFrameFixture projectFrame = findIdeFrame(projectPath);
    projectFrame.waitForGradleProjectSyncToFinish();
    return projectFrame;
  }

  @NotNull
  protected IdeFrameFixture findIdeFrame(@NotNull File projectPath) {
    return IdeFrameFixture.find(myRobot, projectPath, null);
  }
}
