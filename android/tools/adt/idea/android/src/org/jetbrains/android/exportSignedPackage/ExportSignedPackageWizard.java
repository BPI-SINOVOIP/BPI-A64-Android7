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

package org.jetbrains.android.exportSignedPackage;

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.sdklib.BuildToolInfo;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidCommonBundle;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class ExportSignedPackageWizard extends AbstractWizard<ExportSignedPackageWizardStep> {
  private static final Logger LOG = Logger.getInstance(ExportSignedPackageWizard.class);

  private static final String NOTIFICATION_TITLE = "Generate signed APK";
  private static final String NOTIFICATION_GROUPID = "Android";

  private final Project myProject;

  private AndroidFacet myFacet;
  private PrivateKey myPrivateKey;
  private X509Certificate myCertificate;

  private boolean mySigned;
  private CompileScope myCompileScope;
  private String myApkPath;

  // build type, list of flavors and gradle signing info are valid only for Gradle projects
  private String myBuildType;
  private List<String> myFlavors;
  private GradleSigningInfo myGradleSigningInfo;

  public ExportSignedPackageWizard(Project project, List<AndroidFacet> facets, boolean signed) {
    super(AndroidBundle.message("android.export.package.wizard.title"), project);
    myProject = project;
    mySigned = signed;
    assert facets.size() > 0;
    if (facets.size() > 1 ||
        SystemInfo.isMac /* wizards with only step are shown incorrectly on mac */) {
      addStep(new ChooseModuleStep(this, facets));
    }
    else {
      myFacet = facets.get(0);
    }
    boolean useGradleToSign = facets.get(0).requiresAndroidModel();

    if (signed) {
      addStep(new KeystoreStep(this, useGradleToSign));
    }

    if (useGradleToSign) {
      addStep(new GradleSignStep(this));
    } else {
      addStep(new ApkStep(this));
    }
    init();
  }

  public boolean isSigned() {
    return mySigned;
  }

  @Override
  protected void doOKAction() {
    if (!commitCurrentStep()) return;
    super.doOKAction();

    assert myFacet != null;
    if (myFacet.requiresAndroidModel()) {
      buildAndSignGradleProject();
    } else {
      buildAndSignIntellijProject();
    }
  }

  private void buildAndSignIntellijProject() {
    CompilerManager.getInstance(myProject).make(myCompileScope, new CompileStatusNotification() {
      @Override
      public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        if (aborted || errors != 0) {
          return;
        }

        final String title = AndroidBundle.message("android.extract.package.task.title");
        ProgressManager.getInstance().run(new Task.Backgroundable(myProject, title, true, null) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            createAndAlignApk(myApkPath);
          }
        });
      }
    });
  }

  private void buildAndSignGradleProject() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Generating signed APKs", false, null) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(myFacet.getModule());
        if (gradleFacet == null) {
          LOG.error("Unable to get gradle project information for module: " + myFacet.getModule().getName());
          return;
        }
        String gradleProjectPath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;

        IdeaAndroidProject androidModel = myFacet.getAndroidModel();
        if (androidModel == null) {
          LOG.error("Unable to obtain Android project model. Did the last Gradle sync complete successfully?");
          return;
        }

        List<String> assembleTasks = getAssembleTasks(gradleProjectPath, androidModel.getAndroidProject(), myBuildType, myFlavors);

        List<String> projectProperties = Lists.newArrayList();
        projectProperties.add(createProperty(AndroidProject.PROPERTY_SIGNING_STORE_FILE, myGradleSigningInfo.keyStoreFilePath));
        projectProperties
          .add(createProperty(AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD, new String(myGradleSigningInfo.keyStorePassword)));
        projectProperties.add(createProperty(AndroidProject.PROPERTY_SIGNING_KEY_ALIAS, myGradleSigningInfo.keyAlias));
        projectProperties.add(createProperty(AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD, new String(myGradleSigningInfo.keyPassword)));
        projectProperties.add(createProperty(AndroidProject.PROPERTY_APK_LOCATION, myApkPath));

        final GradleInvoker gradleInvoker = GradleInvoker.getInstance(myProject);

        final GradleInvoker.AfterGradleInvocationTask afterTask = new GradleInvoker.AfterGradleInvocationTask() {
          @Override
          public void execute(@NotNull GradleInvocationResult result) {
            if (result.isBuildSuccessful()) {
              if (ShowFilePathAction.isSupported()) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    if (Messages.showOkCancelDialog(myProject, "Signed APKs generated successfully.", NOTIFICATION_TITLE,
                                                    RevealFileAction.getActionName(), IdeBundle.message("action.close"),
                                                    Messages.getInformationIcon()) == Messages.OK) {
                      ShowFilePathAction.openDirectory(new File(myApkPath));
                    }
                  }
                });
              }
              else {
                Notifications.Bus.notify(new Notification(NOTIFICATION_GROUPID, NOTIFICATION_TITLE, "Signed APKs are in: " + myApkPath,
                                                          NotificationType.INFORMATION));
              }
            }
            else {
              Notifications.Bus.notify(new Notification(NOTIFICATION_GROUPID, NOTIFICATION_TITLE,
                                                        "Errors while building apk, see messages tool window for list of errors.",
                                                        NotificationType.ERROR));
            }
            gradleInvoker.removeAfterGradleInvocationTask(this);
          }
        };

        gradleInvoker.addAfterGradleInvocationTask(afterTask);
        gradleInvoker.executeTasks(assembleTasks, projectProperties);

        LOG.info("Export APK command: " +
                 Joiner.on(',').join(assembleTasks) +
                 ", destination: " +
                 createProperty(AndroidProject.PROPERTY_APK_LOCATION, myApkPath));
      }

      private String createProperty(@NotNull String name, @NotNull String value) {
        return AndroidGradleSettings.createProjectProperty(name, value);
      }
    });
  }

  @VisibleForTesting
  public static List<String> getAssembleTasks(String gradleProjectPath,
                                               AndroidProject androidProject,
                                               String buildType,
                                               List<String> flavors) {
    Map<String,Variant> variantsByFlavor = Maps.newHashMapWithExpectedSize(flavors.size());
    for (Variant v : androidProject.getVariants()) {
      if (!v.getBuildType().equals(buildType)) {
        continue;
      }

      variantsByFlavor.put(getMergedFlavorName(v), v);
    }

    if (flavors.isEmpty()) {
      // if there are no flavors defined, then the default merged flavor name is empty..
      Variant v = variantsByFlavor.get("");
      if (v != null) {
        String taskName = v.getMainArtifact().getAssembleTaskName();
        return Collections.singletonList(GradleInvoker.createBuildTask(gradleProjectPath, taskName));
      } else {
        LOG.error("Unable to find default variant");
        return Collections.emptyList();
      }
    }

    List<String> assembleTasks = Lists.newArrayListWithExpectedSize(flavors.size());
    for (String flavor : flavors) {
      Variant v = variantsByFlavor.get(flavor);
      if (v != null) {
        String taskName = v.getMainArtifact().getAssembleTaskName();
        assembleTasks.add(GradleInvoker.createBuildTask(gradleProjectPath, taskName));
      }
    }

    return assembleTasks;
  }

  public static String getMergedFlavorName(Variant variant) {
    return Joiner.on('-').join(variant.getProductFlavors());
  }

  @Override
  protected void doNextAction() {
    if (!commitCurrentStep()) return;
    super.doNextAction();
  }

  private boolean commitCurrentStep() {
    try {
      mySteps.get(myCurrentStep).commitForNext();
    }
    catch (CommitStepException e) {
      Messages.showErrorDialog(getContentPane(), e.getMessage());
      return false;
    }
    return true;
  }

  @Override
  protected int getNextStep(int stepIndex) {
    int result = super.getNextStep(stepIndex);
    if (result != myCurrentStep) {
      mySteps.get(result).setPreviousStepIndex(myCurrentStep);
    }
    return result;
  }

  @Override
  protected int getPreviousStep(int stepIndex) {
    ExportSignedPackageWizardStep step = mySteps.get(stepIndex);
    int prevStepIndex = step.getPreviousStepIndex();
    assert prevStepIndex >= 0;
    return prevStepIndex;
  }

  @Override
  protected void updateStep() {
    final int step = getCurrentStep();
    final ExportSignedPackageWizardStep currentStep = mySteps.get(step);
    getFinishButton().setEnabled(currentStep.canFinish());

    super.updateStep();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        getRootPane().setDefaultButton(getNextButton());

        final JComponent component = currentStep.getPreferredFocusedComponent();
        if (component != null) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              component.requestFocus();
            }
          });
        }
      }
    });
  }

  @Override
  protected String getHelpID() {
    ExportSignedPackageWizardStep step = getCurrentStepObject();
    if (step != null) {
      return step.getHelpId();
    }
    return null;
  }

  public Project getProject() {
    return myProject;
  }

  public void setFacet(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  public void setPrivateKey(@NotNull PrivateKey privateKey) {
    myPrivateKey = privateKey;
  }

  public void setCertificate(@NotNull X509Certificate certificate) {
    myCertificate = certificate;
  }

  public PrivateKey getPrivateKey() {
    return myPrivateKey;
  }

  public X509Certificate getCertificate() {
    return myCertificate;
  }

  public void setCompileScope(@NotNull CompileScope compileScope) {
    myCompileScope = compileScope;
  }

  public void setApkPath(@NotNull String apkPath) {
    myApkPath = apkPath;
  }

  public void setGradleOptions(String buildType, List<String> flavors) {
    myBuildType = buildType;
    myFlavors = flavors;
  }

  private void createAndAlignApk(final String apkPath) {
    AndroidPlatform platform = getFacet().getConfiguration().getAndroidPlatform();
    assert platform != null;
    final String sdkPath = platform.getSdkData().getPath();
    String zipAlignPath = AndroidCommonUtils.getZipAlign(sdkPath, platform.getTarget());
    File zipalign = new File(zipAlignPath);
    if (!zipalign.isFile()) {
      BuildToolInfo buildTool = platform.getTarget().getBuildToolInfo();
      if (buildTool != null) {
        zipAlignPath = buildTool.getPath(BuildToolInfo.PathId.ZIP_ALIGN);
        zipalign = new File(zipAlignPath);
      }
    }
    final boolean runZipAlign = zipalign.isFile();
    File destFile = null;
    try {
      destFile = runZipAlign ? FileUtil.createTempFile("android", ".apk") : new File(apkPath);
      createApk(destFile);
    }
    catch (Exception e) {
      showErrorInDispatchThread(e.getMessage());
    }
    if (destFile == null) return;

    if (runZipAlign) {
      File realDestFile = new File(apkPath);
      final String message = AndroidCommonUtils.executeZipAlign(zipAlignPath, destFile, realDestFile);
      if (message != null) {
        showErrorInDispatchThread(message);
        return;
      }
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        String title = AndroidBundle.message("android.export.package.wizard.title");
        final Project project = getProject();
        final File apkFile = new File(apkPath);

        final VirtualFile vApkFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(apkFile);
        if (vApkFile != null) {
          vApkFile.refresh(true, false);
        }

        if (!runZipAlign) {
          Messages.showWarningDialog(project, AndroidCommonBundle.message(
            "android.artifact.building.cannot.find.zip.align.error"), title);
        }

        if (ShowFilePathAction.isSupported()) {
          if (Messages.showOkCancelDialog(project, AndroidBundle.message("android.export.package.success.message", apkFile.getName()),
                                          title, RevealFileAction.getActionName(), IdeBundle.message("action.close"),
                                          Messages.getInformationIcon()) == Messages.OK) {
            ShowFilePathAction.openFile(apkFile);
          }
        }
        else {
          Messages.showInfoMessage(project, AndroidBundle.message("android.export.package.success.message", apkFile), title);
        }
      }
    }, ModalityState.NON_MODAL);
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  private void createApk(File destFile) throws IOException, GeneralSecurityException {
    final String srcApkPath = AndroidCompileUtil.getUnsignedApkPath(getFacet());
    final File srcApk = new File(FileUtil.toSystemDependentName(srcApkPath));

    if (isSigned()) {
      AndroidCommonUtils.signApk(srcApk, destFile, getPrivateKey(), getCertificate());
    }
    else {
      FileUtil.copy(srcApk, destFile);
    }
  }

  private void showErrorInDispatchThread(final String message) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(getProject(), "Error: " + message, CommonBundle.getErrorTitle());
      }
    }, ModalityState.NON_MODAL);
  }

  public void setGradleSigningInfo(GradleSigningInfo gradleSigningInfo) {
    myGradleSigningInfo = gradleSigningInfo;
  }
}
