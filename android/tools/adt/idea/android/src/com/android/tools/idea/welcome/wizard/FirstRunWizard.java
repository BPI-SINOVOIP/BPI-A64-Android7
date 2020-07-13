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
package com.android.tools.idea.welcome.wizard;

import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.idea.wizard.dynamic.SingleStepPath;
import com.android.utils.NullLogger;
import com.google.common.collect.Multimap;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wizard to setup Android Studio before the first run
 */
public class FirstRunWizard extends DynamicWizard {
  public static final String WIZARD_TITLE = "Android Studio Setup Wizard";
  public static final ScopedStateStore.Key<Boolean> KEY_CUSTOM_INSTALL =
    ScopedStateStore.createKey("custom.install", ScopedStateStore.Scope.WIZARD, Boolean.class);

  @NotNull private final FirstRunWizardMode myMode;
  @Nullable private final Multimap<PkgType, RemotePkgInfo> myRemotePackages;
  /**
   * On the first user click on finish button, we show progress step & perform setup.
   * Second attempt will close the wizard.
   */
  private final AtomicInteger myFinishClicks = new AtomicInteger(0);
  private final SetupJdkPath myJdkPath;
  private InstallComponentsPath myComponentsPath;

  public FirstRunWizard(@NotNull DynamicWizardHost host, @NotNull FirstRunWizardMode mode,
                        @Nullable Multimap<PkgType, RemotePkgInfo> remotePackages) {
    super(null, null, WIZARD_TITLE, host);
    myMode = mode;
    myJdkPath = new SetupJdkPath(mode);
    myRemotePackages = remotePackages;
    setTitle(WIZARD_TITLE);
  }

  @Override
  public void init() {
    File initialSdkLocation = FirstRunWizardDefaults.getInitialSdkLocation(myMode);
    ConsolidatedProgressStep progressStep = new FirstRunProgressStep();
    myComponentsPath = new InstallComponentsPath(progressStep, myMode, initialSdkLocation, myRemotePackages);
    if (myMode == FirstRunWizardMode.NEW_INSTALL) {
      boolean sdkExists = initialSdkLocation.isDirectory() &&
                          SdkManager.createManager(initialSdkLocation.getAbsolutePath(), new NullLogger()) != null;
      addPath(new SingleStepPath(new FirstRunWelcomeStep(sdkExists)));
    }
    addPath(myJdkPath);
    if (myMode == FirstRunWizardMode.NEW_INSTALL) {
      addPath(new SingleStepPath(new InstallationTypeWizardStep(KEY_CUSTOM_INSTALL)));
    }
    addPath(new SingleStepPath(new SelectThemeStep(KEY_CUSTOM_INSTALL)));

    addPath(myComponentsPath);
    if (SystemInfo.isLinux && myMode != FirstRunWizardMode.INSTALL_HANDOFF) {
      addPath(new SingleStepPath(new LinuxHaxmInfoStep()));
    }
    if (myMode != FirstRunWizardMode.INSTALL_HANDOFF) {
      addPath(new SingleStepPath(new LicenseAgreementStep(getDisposable())));
    }
    addPath(new SingleStepPath(progressStep));
    super.init();
  }

  @Override
  public void doCancelAction() {
    ConfirmFirstRunWizardCloseDialog.Result result = new ConfirmFirstRunWizardCloseDialog().open();
    switch (result) {
      case Skip:
        AndroidFirstRunPersistentData.getInstance().markSdkUpToDate(myMode.getInstallerTimestamp());
        // Fallthrough
      case Rerun:
        myHost.close(DynamicWizardHost.CloseAction.CANCEL);
        break;
      case DoNotClose:
        break; // Do nothing
    }

  }

  // We need to show progress page before proceeding closing the wizard.
  @Override
  public void doFinishAction() {
    if (myFinishClicks.incrementAndGet() == 1) {
      doNextAction();
    }
    else {
      assert myFinishClicks.get() <= 2; // Should not take more then 2 clicks
      super.doFinishAction();
    }
  }

  @NotNull
  @Override
  protected String getProgressTitle() {
    return "Finishing setup...";
  }

  @Override
  public void performFinishingActions() {
    // Nothing
  }

  @Override
  protected String getWizardActionDescription() {
    return "Android Studio Setup Wizard";
  }

  public class FirstRunProgressStep extends ConsolidatedProgressStep {
    public FirstRunProgressStep() {
      super(getDisposable(), myHost);
      setPaths(myPaths);
    }

    /**
     * The goal is not to show this step until the user completes the wizard. So this page is
     * only shown once, after the user clicks finish for the first time.
     */
    @Override
    public boolean isStepVisible() {
      return myFinishClicks.get() == 1 || (!(myJdkPath.showsStep() || myComponentsPath.showsStep()));
    }
  }
}
