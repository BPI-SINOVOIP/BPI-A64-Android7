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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.intellij.CommonBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper.DoNotAskOption;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.startup.AndroidStudioSpecificInitializer.isAndroidStudio;
import static com.intellij.openapi.ui.Messages.*;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;

final class PreSyncChecks {
  private static final Logger LOG = Logger.getInstance(PreSyncChecks.class);
  private static final String GRADLE_SYNC_MSG_TITLE = "Gradle Sync";
  private static final String PROJECT_SYNCING_ERROR_GROUP = "Project syncing error";

  @NonNls private static final String SHOW_DO_NOT_ASK_TO_COPY_PROXY_SETTINGS_PROPERTY_NAME =
    "show.do.not.copy.http.proxy.settings.to.gradle";

  private PreSyncChecks() {
  }

  @NotNull
  static PreSyncCheckResult canSync(@NotNull Project project) {
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      // Unlikely to happen because it would mean this is the default project.
      return PreSyncCheckResult.success();
    }

    if (isAndroidStudio()) {
      // We only check proxy settings for Studio, because Studio does not pass the IDE's proxy settings to Gradle.
      // See https://code.google.com/p/android/issues/detail?id=169743
      checkHttpProxySettings(project);
    }

    ProjectSyncMessages syncMessages = ProjectSyncMessages.getInstance(project);
    syncMessages.removeMessages(PROJECT_SYNCING_ERROR_GROUP);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      attemptToUseEmbeddedGradle(project);
    }

    GradleProjectSettings gradleSettings = getGradleProjectSettings(project);
    File wrapperPropertiesFile = findWrapperPropertiesFile(project);

    DistributionType distributionType = gradleSettings != null ? gradleSettings.getDistributionType() : null;
    boolean usingWrapper = (distributionType == null || distributionType == DEFAULT_WRAPPED) && wrapperPropertiesFile != null;
    if (usingWrapper && gradleSettings != null) {
      // Do this just to ensure that the right distribution type is set. If this is not set, build.gradle editor will not have code
      // completion (see BuildClasspathModuleGradleDataService, line 119).
      gradleSettings.setDistributionType(DEFAULT_WRAPPED);
    }
    else if (!ApplicationManager.getApplication().isUnitTestMode()) {
      if (wrapperPropertiesFile == null && gradleSettings != null) {
        createWrapperIfNecessary(project, gradleSettings, distributionType);
      }
    }

    return PreSyncCheckResult.success();
  }

  // If the IDE is configured to use proxies, we ask the user if she would like to have those settings copied to gradle.properties, if such
  // files does not include them already.
  // Gradle may need those settings to access the Internet to download dependencies.
  // See https://code.google.com/p/android/issues/detail?id=65325
  private static void checkHttpProxySettings(@NotNull Project project) {
    boolean performCheck = PropertiesComponent.getInstance().getBoolean(SHOW_DO_NOT_ASK_TO_COPY_PROXY_SETTINGS_PROPERTY_NAME, true);
    if (!performCheck) {
      // User already checked the "do not ask me" option.
      return;
    }

    HttpConfigurable ideProxySettings = HttpConfigurable.getInstance();
    if (ideProxySettings.USE_HTTP_PROXY && isNotEmpty(ideProxySettings.PROXY_HOST)) {
      GradleProperties properties;
      try {
        properties = new GradleProperties(project);
      }
      catch (IOException e) {
        LOG.info("Failed to read gradle.properties file", e);
        // Let sync continue, even though it may fail.
        return;
      }
      GradleProperties.ProxySettings proxySettings = properties.getProxySettings();
      if (!ideProxySettings.PROXY_HOST.equals(proxySettings.getHost())) {
        String msg = "Android Studio is configured to use a HTTP proxy. " +
                     "Gradle may need these proxy settings to access the Internet (e.g. for downloading dependencies.)\n\n" +
                     "Would you like to have the IDE's proxy configuration be set in the project's gradle.properties file?";
        DoNotAskOption doNotAskOption = new PropertyDoNotAskOption(SHOW_DO_NOT_ASK_TO_COPY_PROXY_SETTINGS_PROPERTY_NAME);
        int result = Messages.showYesNoDialog(project, msg, "Proxy Settings", Messages.getQuestionIcon(), doNotAskOption);
        if (result == YES) {
          proxySettings.copyFrom(ideProxySettings);
          properties.setProxySettings(proxySettings);
          try {
            properties.save();
          }
          catch (IOException e) {
            //noinspection ThrowableResultOfMethodCallIgnored
            Throwable root = getRootCause(e);

            String cause = root.getMessage();
            String errMsg = "Failed to save changes to gradle.properties file.";
            if (isNotEmpty(cause)) {
              if (!cause.endsWith(".")) {
                cause += ".";
              }
              errMsg += String.format("\nCause: %1$s", cause);
            }
            AndroidGradleNotification notification = AndroidGradleNotification.getInstance(project);
            notification.showBalloon("Proxy Settings", errMsg, NotificationType.ERROR);

            LOG.info("Failed to save changes to gradle.properties file", e);
          }
        }
      }
    }
  }

  private static boolean createWrapperIfNecessary(@NotNull Project project,
                                                  @NotNull GradleProjectSettings gradleSettings,
                                                  @Nullable DistributionType distributionType) {
    boolean createWrapper = false;
    boolean chooseLocalGradleHome = false;

    if (distributionType == null) {
      String msg = createUseWrapperQuestion("Gradle settings for this project are not configured yet.");
      int answer = showOkCancelDialog(project, msg, GRADLE_SYNC_MSG_TITLE, getQuestionIcon());
      createWrapper = answer == Messages.OK;
    }
    else if (distributionType == DEFAULT_WRAPPED) {
      createWrapper = true;
    }
    else if (distributionType == LOCAL) {
      String gradleHome = gradleSettings.getGradleHome();
      String msg = null;
      if (isEmpty(gradleHome)) {
        msg = createUseWrapperQuestion("The path of the local Gradle distribution to use is not set.");
      }
      else {
        File gradleHomePath = new File(toSystemDependentName(gradleHome));
        if (!gradleHomePath.isDirectory()) {
          String reason = String.format("The path\n'%1$s'\n, set as a local Gradle distribution, does not belong to an existing directory.",
                                        gradleHomePath.getPath());
          msg = createUseWrapperQuestion(reason);
        }
        else {
          FullRevision gradleVersion = getGradleVersion(gradleHomePath);
          if (gradleVersion == null) {
            String reason = String.format("The path\n'%1$s'\n, does not belong to a Gradle distribution.", gradleHomePath.getPath());
            msg = createUseWrapperQuestion(reason);
          }
          else if (!isSupportedGradleVersion(gradleVersion)) {
            String reason = String.format("Gradle version %1$s is not supported.", gradleHomePath.getPath());
            msg = createUseWrapperQuestion(reason);
          }
        }
      }
      if (msg != null) {
        int answer = showOkCancelDialog(project, msg, GRADLE_SYNC_MSG_TITLE, getQuestionIcon());
        createWrapper = answer == Messages.OK;
        chooseLocalGradleHome = !createWrapper;
      }
    }

    if (createWrapper) {
      File projectDirPath = getBaseDirPath(project);

      // attempt to delete the whole gradle wrapper folder.
      File gradleDirPath = new File(projectDirPath, SdkConstants.FD_GRADLE);
      if (!delete(gradleDirPath)) {
        // deletion failed. Let sync continue.
        return true;
      }

      try {
        createGradleWrapper(projectDirPath);
        if (distributionType == null) {
          gradleSettings.setDistributionType(DEFAULT_WRAPPED);
        }
        return true;
      }
      catch (IOException e) {
        LOG.info("Failed to create Gradle wrapper for project '" + project.getName() + "'", e);
      }
    }
    else if (distributionType == null || chooseLocalGradleHome) {
      ChooseGradleHomeDialog dialog = new ChooseGradleHomeDialog();
      if (dialog.showAndGet()) {
        String enteredGradleHomePath = dialog.getEnteredGradleHomePath();
        gradleSettings.setGradleHome(enteredGradleHomePath);
        gradleSettings.setDistributionType(LOCAL);
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static String createUseWrapperQuestion(@NotNull String reason) {
    return reason + "\n\n" +
           "Would you like the project to use the Gradle wrapper?\n" +
           "(The wrapper will automatically download the latest supported Gradle version).\n\n" +
           "Click 'OK' to use the Gradle wrapper, or 'Cancel' to manually set the path of a local Gradle distribution.";
  }

  static class PreSyncCheckResult {
    private final boolean mySuccess;
    @Nullable private final String myFailureCause;

    @NotNull
    private static PreSyncCheckResult success() {
      return new PreSyncCheckResult(true, null);
    }

    @NotNull
    private static PreSyncCheckResult failure(@NotNull String cause) {
      return new PreSyncCheckResult(false, cause);
    }

    private PreSyncCheckResult(boolean success, @Nullable String failureCause) {
      mySuccess = success;
      myFailureCause = failureCause;
    }

    public boolean isSuccess() {
      return mySuccess;
    }

    @Nullable
    public String getFailureCause() {
      return myFailureCause;
    }
  }

  /**
   * Implementation of "Do not show this dialog in the future" option. This option is displayed as a checkbox in a {@code Messages} dialog.
   * The state of such checkbox is stored in the IDE's {@code PropertiesComponent} under the name passed in the constructor.
   */
  private static class PropertyDoNotAskOption implements DoNotAskOption {
    /** The name of the property storing the value of the "Do not show this dialog in the future" option.  */
    @NotNull private final String myProperty;

    PropertyDoNotAskOption(@NotNull String property) {
      myProperty = property;
    }

    @Override
    public boolean isToBeShown() {
      // Read the stored value. If none is found, return "true" to display the checkbox the first time.
      return PropertiesComponent.getInstance().getBoolean(myProperty, true);
    }

    @Override
    public void setToBeShown(boolean toBeShown, int exitCode) {
      // Stores the state of the checkbox into the property.
      PropertiesComponent.getInstance().setValue(myProperty, String.valueOf(toBeShown));
    }

    @Override
    public boolean canBeHidden() {
      // By returning "true", the Messages dialog can hide the checkbox if the user previously set the checkbox as "selected".
      return true;
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      // We always want to save the value of the checkbox, regardless of the button pressed in the Messages dialog.
      return true;
    }

    @NotNull
    @Override
    public String getDoNotShowMessage() {
      // This is the text to set in the checkbox.
      return CommonBundle.message("dialog.options.do.not.show");
    }
  }
}
