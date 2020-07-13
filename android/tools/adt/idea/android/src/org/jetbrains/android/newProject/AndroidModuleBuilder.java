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

package org.jetbrains.android.newProject;


import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.DocumentUtil;
import icons.AndroidIcons;
import org.jetbrains.android.AndroidFileTemplateProvider;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.importDependencies.ImportDependenciesUtil;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.run.TargetSelectionMode;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.android.run.testing.AndroidTestRunConfigurationType;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Properties;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static org.jetbrains.android.util.AndroidUtils.createChildDirectoryIfNotExist;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidModuleBuilder extends JavaModuleBuilder {
  public static final ProjectType ANDROID_PROJECT_TYPE = new ProjectType("Android");
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.newProject.AndroidModuleBuilder");
  private final AndroidProjectType myProjectType;
  private String myPackageName;
  private String myApplicationName;
  private String myActivityName;
  private Module myTestedModule;
  private TargetSelectionMode myTargetSelectionMode;
  private String myPreferredAvd;

  @SuppressWarnings("UnusedDeclaration")
  public AndroidModuleBuilder() {
    this(AndroidProjectType.APPLICATION);
  }

  public AndroidModuleBuilder(AndroidProjectType type) {
    myProjectType = type;
  }

  @NotNull
  private static String getAntProjectName(@NotNull String moduleName) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < moduleName.length(); i++) {
      char c = moduleName.charAt(i);
      if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || Character.isDigit(c))) {
        c = '_';
      }
      result.append(c);
    }
    return result.toString();
  }

  private static Sdk getAndroidSdkForModule(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).getSdk();
  }

  private static void copyGeneratedAndroidProject(File tempDir, VirtualFile contentRoot, VirtualFile sourceRoot) {
    final File[] children = tempDir.listFiles();
    if (children != null) {
      for (File child : children) {
        if (SdkConstants.FD_SOURCES.equals(child.getName())) {
          continue;
        }
        final File to = new File(contentRoot.getPath(), child.getName());

        try {
          if (child.isDirectory()) {
            FileUtil.copyDir(child, to);
          }
          else {
            FileUtil.copy(child, to);
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

    final File tempSourceRoot = new File(tempDir, SdkConstants.FD_SOURCES);
    if (tempSourceRoot.exists()) {
      final File to = new File(sourceRoot.getPath());

      try {
        FileUtil.copyDir(tempSourceRoot, to);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private static void configureManifest(@NotNull AndroidFacet facet, @NotNull IAndroidTarget target) {
    final Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return;
    }

    final XmlTag manifestTag = manifest.getXmlTag();
    if (manifestTag == null) {
      return;
    }

    final PsiFile manifestFile = manifestTag.getContainingFile();
    if (manifestFile == null) {
      return;
    }

    final VirtualFile vManifestFile = manifestFile.getVirtualFile();
    if (vManifestFile == null ||
        !ReadonlyStatusHandler.ensureFilesWritable(manifestFile.getProject(), vManifestFile)) {
      return;
    }
    XmlTag usesSdkTag = manifestTag.createChildTag("uses-sdk", "", null, false);
    if (usesSdkTag != null) {
      usesSdkTag = manifestTag.addSubTag(usesSdkTag, true);
      usesSdkTag.setAttribute("minSdkVersion", SdkConstants.NS_RESOURCES, target.getVersion().getApiString());
    }
    CodeStyleManager.getInstance(manifestFile.getProject()).reformat(manifestFile);
  }

  private static void createManifestFileAndAntFiles(Project project, VirtualFile contentRoot, Module module) {
    VirtualFile existingManifestFile = contentRoot.findChild(FN_ANDROID_MANIFEST_XML);
    if (existingManifestFile != null) {
      return;
    }
    try {
      AndroidFileTemplateProvider
        .createFromTemplate(project, contentRoot, AndroidFileTemplateProvider.ANDROID_MANIFEST_TEMPLATE, FN_ANDROID_MANIFEST_XML);

      Sdk sdk = getAndroidSdkForModule(module);
      if (sdk == null) return;
      AndroidPlatform platform = AndroidPlatform.parse(sdk);

      if (platform == null) {
        Messages.showErrorDialog(project, "Cannot parse Android SDK: '" + SdkConstants.FN_PROJECT_PROPERTIES + "' won't be generated",
                                 CommonBundle.getErrorTitle());
        return;
      }

      Properties properties = FileTemplateManager.getInstance(project).getDefaultProperties();
      properties.setProperty("TARGET", platform.getTarget().hashString());
      AndroidFileTemplateProvider.createFromTemplate(project, contentRoot, AndroidFileTemplateProvider.DEFAULT_PROPERTIES_TEMPLATE,
                                                     SdkConstants.FN_PROJECT_PROPERTIES, properties);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private static void addTestRunConfiguration(final AndroidFacet facet, @NotNull TargetSelectionMode mode, @Nullable String preferredAvd) {
    Project project = facet.getModule().getProject();
    RunManager runManager = RunManager.getInstance(project);
    Module module = facet.getModule();
    RunnerAndConfigurationSettings settings = runManager
      .createRunConfiguration(module.getName(), AndroidTestRunConfigurationType.getInstance().getFactory());

    AndroidTestRunConfiguration configuration = (AndroidTestRunConfiguration)settings.getConfiguration();
    configuration.setModule(module);
    configuration.setTargetSelectionMode(mode);
    if (preferredAvd != null) {
      configuration.PREFERRED_AVD = preferredAvd;
    }

    runManager.addConfiguration(settings, false);
    runManager.setSelectedConfiguration(settings);
  }

  @Nullable
  private static VirtualFile findSourceRoot(ModifiableRootModel model) {
    VirtualFile genSourceRoot = AndroidRootUtil.getStandardGenDir(model.getModule());
    for (VirtualFile root : model.getSourceRoots()) {
      if (!Comparing.equal(root, genSourceRoot)) {
        return root;
      }
    }
    return null;
  }

  @Nullable
  private static PsiDirectory createPackageIfPossible(final PsiDirectory sourceDir, String packageName) {
    if (sourceDir != null) {
      final String[] ids = packageName.split("\\.");
      return ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
        @Override
        public PsiDirectory compute() {
          PsiDirectory dir = sourceDir;
          for (String id : ids) {
            PsiDirectory child = dir.findSubdirectory(id);
            dir = child == null ? dir.createSubdirectory(id) : child;
          }
          return dir;
        }
      });
    }
    return null;
  }

  private static void createFileFromResource(Project project, VirtualFile drawableDir, String name, String resourceFilePath)
    throws IOException {
    if (drawableDir.findChild(name) != null) {
      return;
    }
    VirtualFile resFile = drawableDir.createChildData(project, name);
    InputStream stream = AndroidModuleBuilder.class.getResourceAsStream(resourceFilePath);
    try {
      byte[] bytes = FileUtil.adaptiveLoadBytes(stream);
      resFile.setBinaryContent(bytes);
    }
    finally {
      stream.close();
    }
  }

  private static Pair<String, Boolean> runAndroidTool(@NotNull GeneralCommandLine commandLine) {
    final StringBuildingOutputProcessor processor = new StringBuildingOutputProcessor();
    String result;
    boolean success = false;
    try {
      success = AndroidUtils.executeCommand(commandLine, processor, WaitingStrategies.WaitForever.getInstance()) == ExecutionStatus.SUCCESS;
      result = processor.getMessage();
    }
    catch (ExecutionException e) {
      result = e.getMessage();
    }
    if (result != null) {
      LOG.debug(result);
    }
    return Pair.create(result, success);
  }

  @Override
  public void setupRootModel(final ModifiableRootModel rootModel) throws ConfigurationException {
    super.setupRootModel(rootModel);

    final LanguageLevelModuleExtension moduleExt = rootModel.getModuleExtension(LanguageLevelModuleExtension.class);

    if (moduleExt != null) {
      LanguageLevel languageLevel = moduleExt.getLanguageLevel();
      if (languageLevel == null) {
        final LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(rootModel.getProject());
        if (projectExt != null) {
          languageLevel = projectExt.getLanguageLevel();
        }
      }
      if (languageLevel == LanguageLevel.JDK_1_3) {
        moduleExt.setLanguageLevel(LanguageLevel.JDK_1_5);
      }
    }

    VirtualFile[] files = rootModel.getContentRoots();
    if (files.length > 0) {
      final VirtualFile contentRoot = files[0];
      final AndroidFacet facet = AndroidUtils.addAndroidFacet(rootModel.getModule(), contentRoot, myProjectType == AndroidProjectType.LIBRARY);

      if (myProjectType == null) {
        ImportDependenciesUtil.importDependencies(rootModel.getModule(), true);
        return;
      }

      final Project project = rootModel.getProject();
      ProjectTypeService.setProjectType(project, new ProjectType("Android"));
      final VirtualFile sourceRoot = findSourceRoot(rootModel);

      if (myProjectType == AndroidProjectType.TEST) {
        assert myTestedModule != null;
        facet.getProperties().PACK_TEST_CODE = true;
        ModuleOrderEntry entry = rootModel.addModuleOrderEntry(myTestedModule);
        entry.setScope(DependencyScope.PROVIDED);
      }
      facet.getProperties().UPDATE_PROPERTY_FILES = Boolean.TRUE.toString();

      StartupManager.getInstance(project).runWhenProjectIsInitialized(new DumbAwareRunnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              DocumentUtil.writeInRunUndoTransparentAction(new Runnable() {
                @Override
                public void run() {
                  createProject(contentRoot, sourceRoot, facet);
                }
              });
            }
          });
        }
      });
    }
  }

  private void createProject(VirtualFile contentRoot, VirtualFile sourceRoot, AndroidFacet facet) {
    if (sourceRoot == null) {
      try {
        // user've chosen "do not create source root"
        sourceRoot = contentRoot.createChildDirectory(this, "src");
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    if (myProjectType == AndroidProjectType.APPLICATION) {
      createDirectoryStructure(contentRoot, sourceRoot, facet);
    }
    else {
      createProjectByAndroidTool(contentRoot, sourceRoot, facet);
    }
  }

  private void createDirectoryStructure(VirtualFile contentRoot, VirtualFile sourceRoot, AndroidFacet facet) {
    if (isHelloAndroid()) {
      if (createProjectByAndroidTool(contentRoot, sourceRoot, facet)) {
        return;
      }
    }
    Project project = facet.getModule().getProject();
    createManifestFileAndAntFiles(project, contentRoot, facet.getModule());
    createResourcesAndLibs(project, contentRoot);
    PsiDirectory sourceDir = sourceRoot != null ? PsiManager.getInstance(project).findDirectory(sourceRoot) : null;
    createActivityAndSetupManifest(facet, sourceDir);
    if (myTargetSelectionMode != null) {
      addRunConfiguration(facet, myTargetSelectionMode, myPreferredAvd);
    }
  }

  private boolean createProjectByAndroidTool(final VirtualFile contentRoot,
                                             final VirtualFile sourceRoot,
                                             final AndroidFacet facet) {
    final Module module = facet.getModule();

    Sdk sdk = getAndroidSdkForModule(module);
    if (sdk == null) {
      return true;
    }
    AndroidPlatform platform = AndroidPlatform.parse(sdk);

    if (platform == null) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        Messages.showErrorDialog(module.getProject(), "Cannot parse Android SDK", CommonBundle.getErrorTitle());
      }
      return true;
    }

    final IAndroidTarget target = platform.getTarget();

    final String androidToolPath =
      platform.getSdkData().getLocation() + File.separator + AndroidCommonUtils.toolPath(SdkConstants.androidCmdName());

    if (!new File(androidToolPath).exists()) {
      return false;
    }

    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(FileUtil.toSystemDependentName(androidToolPath));

    commandLine.addParameter("create");

    switch (myProjectType) {
      case APPLICATION:
        commandLine.addParameter("project");
        break;
      case LIBRARY:
        commandLine.addParameter("lib-project");
        break;
      case TEST:
        commandLine.addParameter("test-project");
        break;
    }

    commandLine.addParameters("--name");
    commandLine.addParameter(getAntProjectName(module.getName()));

    commandLine.addParameters("--path");

    File tempContentRoot;
    try {
      tempContentRoot = FileUtil.createTempDirectory("android_temp_content_root", "tmp");
    }
    catch (IOException e) {
      LOG.error(e);
      return false;
    }
    final String targetDirectoryPath = tempContentRoot.getPath();
    commandLine.addParameter(FileUtil.toSystemDependentName(targetDirectoryPath));

    if (myProjectType == AndroidProjectType.APPLICATION || myProjectType == AndroidProjectType.LIBRARY) {
      String apiLevel = target.hashString();
      commandLine.addParameter("--target");
      commandLine.addParameter(apiLevel);
      commandLine.addParameter("--package");
      commandLine.addParameter(myPackageName);
    }

    if (myProjectType == AndroidProjectType.APPLICATION) {
      commandLine.addParameter("--activity");
      commandLine.addParameter(myActivityName);
    }
    else if (myProjectType == AndroidProjectType.TEST) {
      final AndroidFacet testedFacet = AndroidFacet.getInstance(myTestedModule);
      final VirtualFile moduleDir = testedFacet != null
                                    ? AndroidRootUtil.getMainContentRoot(testedFacet)
                                    : null;
      if (moduleDir == null) {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          Messages.showErrorDialog(module.getProject(), AndroidBundle.message(
            "android.wizard.cannot.find.main.content.root.error",
            module.getName()), CommonBundle.getErrorTitle());
        }
        return true;
      }
      commandLine.addParameter("--main");
      commandLine.addParameter(FileUtil.toSystemDependentName(moduleDir.getPath()));
    }

    final File finalTempContentRoot = tempContentRoot;

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final Project project = module.getProject();
        final String androidToolOutput;
        final boolean androidToolSuccess;
        try {
          final Pair<String, Boolean> pair = runAndroidTool(commandLine);
          androidToolOutput = pair.getFirst();
          androidToolSuccess = pair.getSecond();
          copyGeneratedAndroidProject(finalTempContentRoot, contentRoot, sourceRoot);
        }
        finally {
          FileUtil.delete(finalTempContentRoot);
        }

        StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
          @Override
          public void run() {
            FileDocumentManager.getInstance().saveAllDocuments();
          }
        });
        VfsUtil.markDirtyAndRefresh(false, true, true, contentRoot);

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (project.isDisposed() || module.isDisposed()) {
              return;
            }
            final boolean manifestGenerated = contentRoot.findChild(FN_ANDROID_MANIFEST_XML) != null;
            final String projectNotGeneratedError = "The project wasn't generated by 'android' tool.";

            if (androidToolOutput != null &&
                androidToolOutput.length() > 0 &&
                (!manifestGenerated ||
                 !androidToolSuccess ||
                 androidToolOutput.trim().toLowerCase().startsWith("error:"))) {
              final ConsoleViewContentType contentType = androidToolSuccess
                                                         ? ConsoleViewContentType.NORMAL_OUTPUT
                                                         : ConsoleViewContentType.ERROR_OUTPUT;
              AndroidUtils.activateConsoleToolWindow(project, new Runnable() {
                @Override
                public void run() {
                  if (!manifestGenerated) {
                    AndroidUtils.printMessageToConsole(project, projectNotGeneratedError, ConsoleViewContentType.ERROR_OUTPUT);
                  }
                  AndroidUtils.printMessageToConsole(project, androidToolOutput, contentType);
                }
              });
            }
            else if (!manifestGenerated) {
              AndroidUtils.activateConsoleToolWindow(project, new Runnable() {
                @Override
                public void run() {
                  AndroidUtils.printMessageToConsole(project, projectNotGeneratedError, ConsoleViewContentType.ERROR_OUTPUT);
                }
              });
            }

            if (!manifestGenerated) {
              return;
            }
            DocumentUtil.writeInRunUndoTransparentAction(new Runnable() {
              @Override
              public void run() {
                try {
                  if (project.isDisposed()) {
                    return;
                  }
                  if (myProjectType == AndroidProjectType.APPLICATION) {
                    assignApplicationName(facet);
                    configureManifest(facet, target);
                    createChildDirectoryIfNotExist(project, contentRoot, SdkConstants.FD_ASSETS);
                    createChildDirectoryIfNotExist(project, contentRoot, SdkConstants.FD_NATIVE_LIBS);
                  }
                  else if (myProjectType == AndroidProjectType.LIBRARY && myPackageName != null) {
                    final String[] dirs = myPackageName.split("\\.");
                    VirtualFile file = sourceRoot;

                    for (String dir : dirs) {
                      if (file == null || dir.length() == 0) {
                        break;
                      }
                      final VirtualFile childDir = file.findChild(dir);
                      file = childDir != null
                             ? childDir
                             : file.createChildDirectory(project, dir);
                    }
                  }
                }
                catch (IOException e) {
                  LOG.error(e);
                }
              }
            });

            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
                if (project.isDisposed() || facet.getModule().isDisposed()) {
                  return;
                }

                if (myTargetSelectionMode != null) {
                  if (myProjectType == AndroidProjectType.APPLICATION) {
                    addRunConfiguration(facet, myTargetSelectionMode, myPreferredAvd);
                  }
                  else if (myProjectType == AndroidProjectType.TEST) {
                    addTestRunConfiguration(facet, myTargetSelectionMode, myPreferredAvd);
                  }
                }
              }
            });

            new ReformatCodeProcessor(project, module, false).run();
          }
        });
      }
    });
    return true;
  }

  private void assignApplicationName(AndroidFacet facet) {
    if (myApplicationName == null || myApplicationName.length() == 0) {
      return;
    }

    final LocalResourceManager manager = facet.getLocalResourceManager();
    ResourceElement appNameResElement = null;
    final String appNameResource = "app_name";

    for (ResourceElement resElement : manager.getValueResources(ResourceType.STRING.getName())) {
      if (appNameResource.equals(resElement.getName().getValue())) {
        appNameResElement = resElement;
      }
    }
    final String appName = myApplicationName.replace("\\", "\\\\");

    if (appNameResElement == null) {
      final String fileName = AndroidResourceUtil.getDefaultResourceFileName(ResourceType.STRING);
      assert fileName != null;
      AndroidResourceUtil.createValueResource(facet.getModule(), appNameResource, ResourceType.STRING, fileName, Collections
              .singletonList(SdkConstants.FD_RES_VALUES), appName);
    }
    else {
      final String normalizedAppName = AndroidResourceUtil.normalizeXmlResourceValue(appName);
      appNameResElement.setStringValue(normalizedAppName);
    }

    final Manifest manifest = facet.getManifest();

    if (manifest != null) {
      manifest.getApplication().getLabel().setValue(ResourceValue.referenceTo('@', null, ResourceType.STRING.getName(), appNameResource));
      }
    }

  private void addRunConfiguration(@NotNull AndroidFacet facet,
                                   @NotNull TargetSelectionMode targetSelectionMode,
                                   @Nullable String targetAvd) {
    String activityClass;
    if (isHelloAndroid()) {
      activityClass = myPackageName + '.' + myActivityName;
    }
    else {
      activityClass = null;
    }
    AndroidUtils.addRunConfiguration(facet, activityClass, false, targetSelectionMode, targetAvd);
  }

  private boolean isHelloAndroid() {
    return StringUtil.isNotEmpty(myActivityName);
  }

  private void createActivityAndSetupManifest(final AndroidFacet facet, final PsiDirectory sourceDir) {
    if (myPackageName != null) {
      CommandProcessor.getInstance().executeCommand(facet.getModule().getProject(), new ExternalChangeAction() {
        @Override
        public void run() {
          Runnable action = new Runnable() {
            @Override
            public void run() {
              PsiDirectory packageDir = createPackageIfPossible(sourceDir, myPackageName);
              if (packageDir == null) return;
              final Manifest manifest = facet.getManifest();
              if (manifest != null) {
                manifest.getPackage().setValue(myPackageName);
                final Module module = facet.getModule();
                final Project project = module.getProject();
                StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
                  @Override
                  public void run() {
                    FileDocumentManager.getInstance().saveAllDocuments();
                  }
                });

                assignApplicationName(facet);

                Sdk sdk = getAndroidSdkForModule(module);
                if (sdk != null) {
                  final AndroidPlatform platform = AndroidPlatform.parse(sdk);
                  if (platform != null) {
                    configureManifest(facet, platform.getTarget());
                  }
                }
              }
            }
          };
          ApplicationManager.getApplication().runWriteAction(action);
        }
      }, AndroidBundle.message("build.android.module.process.title"), null);
    }
  }

  private void createResourcesAndLibs(final Project project, final VirtualFile rootDir) {
    try {
      createChildDirectoryIfNotExist(project, rootDir, SdkConstants.FD_ASSETS);
      createChildDirectoryIfNotExist(project, rootDir, SdkConstants.FD_NATIVE_LIBS);
      VirtualFile resDir = createChildDirectoryIfNotExist(project, rootDir, SdkConstants.FD_RES);
      VirtualFile drawableDir = createChildDirectoryIfNotExist(project, resDir, SdkConstants.FD_RES_DRAWABLE);
      createFileFromResource(project, drawableDir, "icon.png", "/icons/androidLarge.png");
      if (isHelloAndroid()) {
        VirtualFile valuesDir = createChildDirectoryIfNotExist(project, resDir, SdkConstants.FD_RES_VALUES);
        createFileFromResource(project, valuesDir, "strings.xml", "res/values/strings.xml");
        VirtualFile layoutDir = createChildDirectoryIfNotExist(project, resDir, SdkConstants.FD_RES_LAYOUT);
        createFileFromResource(project, layoutDir, "main.xml", "res/layout/main.xml");
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public void setActivityName(String activityName) {
    myActivityName = activityName;
  }

  public String getApplicationName() {
    return myApplicationName;
  }

  public void setApplicationName(String applicationName) {
    myApplicationName = applicationName;
  }

  public void setPackageName(String packageName) {
    myPackageName = packageName;
  }

  @Override
  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  protected ProjectType getProjectType() {
    return ANDROID_PROJECT_TYPE;
  }

  @Nullable
  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull final SettingsStep settingsStep) {
    if (myProjectType == null) {
      return super.modifyProjectTypeStep(settingsStep);
    }
    switch (myProjectType) {

      case APPLICATION:
        return new AndroidApplicationModifiedSettingsStep(this, settingsStep);
      case LIBRARY:
        return new AndroidLibraryModifiedSettingsStep(this, settingsStep);
      case TEST:
        return new AndroidTestModifiedSettingsStep(this, settingsStep);
      default:
        LOG.error("Unknown project type " + myProjectType);
        return super.modifyProjectTypeStep(settingsStep);
    }
  }

  public void setTestedModule(Module module) {
    myTestedModule = module;
  }

  public void setTargetSelectionMode(TargetSelectionMode targetSelectionMode) {
    myTargetSelectionMode = targetSelectionMode;
  }

  public void setPreferredAvd(String preferredAvd) {
    myPreferredAvd = preferredAvd;
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return new ModuleWizardStep[] { new AndroidModuleWizardStep(this, modulesProvider, myProjectType) };
  }

  @Override
  public Icon getBigIcon() {
    return AndroidIcons.Android24;
  }

  @Override
  public Icon getNodeIcon() {
    return AndroidIcons.Android;
  }

  @Override
  public String getDescription() {
    return AndroidBundle.message("android.module.type.description");
  }

  @Override
  public String getPresentableName() {
    return AndroidBundle.message("android.module.type.name");
  }

  @Override
  public String getBuilderId() {
    return getClass().getName();
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdkType) {
    return AndroidSdkType.getInstance() == sdkType;
  }

  public static class Library extends AndroidModuleBuilder {
    public Library() {
      super(AndroidProjectType.LIBRARY);
    }

    @Override
    public String getBuilderId() {
      return "android.library";
    }

    @Override
    public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext,
                                                @NotNull ModulesProvider modulesProvider) {
      return ModuleWizardStep.EMPTY_ARRAY;
    }
  }

  public static class Test extends AndroidModuleBuilder {
    public Test() {
      super(AndroidProjectType.TEST);
    }

    @Override
    public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
      return ModuleWizardStep.EMPTY_ARRAY;
    }

    @Override
    public String getBuilderId() {
      return "android.test";
    }
  }
}
