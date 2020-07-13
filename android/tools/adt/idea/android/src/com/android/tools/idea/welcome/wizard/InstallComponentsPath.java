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

import com.android.SdkConstants;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SdkMerger;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.*;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.utils.NullLogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.*;
import com.google.common.base.Supplier;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.*;

/**
 * Wizard path that manages component installation flow. It will prompt the user
 * for the components to install and for install parameters. On wizard
 * completion it will download and unzip component archives and will
 * perform component setup.
 */
public class InstallComponentsPath extends DynamicWizardPath implements LongRunningOperationPath {
  public static final AndroidVersion LATEST_ANDROID_VERSION = new AndroidVersion(22, null);
  private static final ScopedStateStore.Key<String> KEY_SDK_INSTALL_LOCATION =
    ScopedStateStore.createKey("download.sdk.location", ScopedStateStore.Scope.PATH, String.class);
  private final ProgressStep myProgressStep;
  @NotNull private final FirstRunWizardMode myMode;
  @NotNull private final ComponentInstaller myComponentInstaller;
  @NotNull private final File mySdkLocation;
  private SdkComponentsStep mySdkComponentsStep;
  private ComponentTreeNode myComponentTree;
  @Nullable private final Multimap<PkgType, RemotePkgInfo> myRemotePackages;

  public InstallComponentsPath(@NotNull ProgressStep progressStep,
                               @NotNull FirstRunWizardMode mode,
                               @NotNull File sdkLocation,
                               @Nullable Multimap<PkgType, RemotePkgInfo> remotePackages) {
    myProgressStep = progressStep;
    myMode = mode;
    mySdkLocation = sdkLocation;
    myRemotePackages = remotePackages;
    myComponentInstaller = new ComponentInstaller(remotePackages);
  }

  private ComponentTreeNode createComponentTree(@NotNull FirstRunWizardMode reason, @NotNull ScopedStateStore stateStore, boolean createAvd) {
    List<ComponentTreeNode> components = Lists.newArrayList();
    components.add(new AndroidSdk(stateStore));
    ComponentTreeNode platforms = Platform.createSubtree(stateStore, myRemotePackages);
    if (platforms != null) {
      components.add(platforms);
    }
    if (Haxm.canRun() && reason == FirstRunWizardMode.NEW_INSTALL) {
      components.add(new Haxm(stateStore, FirstRunWizard.KEY_CUSTOM_INSTALL));
    }
    if (createAvd) {
      components.add(new AndroidVirtualDevice(stateStore, myRemotePackages));
    }
    return new ComponentCategory("Root", "Root node that is not supposed to appear in the UI", components);
  }

  private static File createTempDir() throws WizardException {
    File tempDirectory;
    try {
      tempDirectory = FileUtil.createTempDirectory("AndroidStudio", "FirstRun", true);
    }
    catch (IOException e) {
      throw new WizardException("Unable to create temporary folder: " + e.getMessage(), e);
    }
    return tempDirectory;
  }

  private static boolean hasPlatformsDir(@Nullable File[] files) {
    if (files == null) {
      return false;
    }
    for (File file : files) {
      if (isPlatformsDir(file)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isPlatformsDir(File file) {
    return file.isDirectory() && file.getName().equalsIgnoreCase(SdkConstants.FD_PLATFORMS);
  }

  /**
   * This is an attempt to isolate from SDK packaging peculiarities.
   */
  @NotNull
  private static File getSdkRoot(@NotNull File expandedLocation) {
    File[] files = expandedLocation.listFiles();
    // Breadth-first scan - to lower chance of false positive
    if (hasPlatformsDir(files)) {
      return expandedLocation;
    }
    // Only scan one level down (no recursion) - avoid false positives
    if (files != null) {
      for (File file : files) {
        if (hasPlatformsDir(file.listFiles())) {
          return file;
        }
      }
    }
    return expandedLocation;
  }

  /**
   * @return null if the user cancels from the UI
   */
  @NotNull
  @VisibleForTesting
  static InstallOperation<File, File> downloadAndUnzipSdkSeed(@NotNull InstallContext context,
                                                              @NotNull final File destination,
                                                              double progressShare) {
    final double DOWNLOAD_OPERATION_PROGRESS_SHARE = progressShare * 0.8;
    final double UNZIP_OPERATION_PROGRESS_SHARE = progressShare * 0.15;
    final double MOVE_OPERATION_PROGRESS_SHARE = progressShare - DOWNLOAD_OPERATION_PROGRESS_SHARE - UNZIP_OPERATION_PROGRESS_SHARE;

    DownloadOperation download =
      new DownloadOperation(context, FirstRunWizardDefaults.getSdkDownloadUrl(), DOWNLOAD_OPERATION_PROGRESS_SHARE);
    UnpackOperation unpack = new UnpackOperation(context, UNZIP_OPERATION_PROGRESS_SHARE);
    MoveSdkOperation move = new MoveSdkOperation(context, destination, MOVE_OPERATION_PROGRESS_SHARE);

    return download.then(unpack).then(move);
  }

  private static boolean existsAndIsVisible(DynamicWizardStep step) {
    return step != null && step.isStepVisible();
  }

  @Nullable
  private File getHandoffAndroidSdkSource() {
    File androidSrc = myMode.getAndroidSrc();
    if (androidSrc != null) {
      File[] files = androidSrc.listFiles();
      if (androidSrc.isDirectory() && files != null && files.length > 0) {
        return androidSrc;
      }
    }
    return null;
  }

  /**
   * <p>Creates an operation that will prepare SDK so the components can be installed.</p>
   * <p>Supported scenarios:</p>
   * <ol>
   * <li>Install wizard leaves SDK repository to merge - merge will happen whether destination exists or not.</li>
   * <li>Valid SDK at destination - do nothing, the wizard will update components later</li>
   * <li>No handoff, no valid SDK at destination - SDK "seed" will be downloaded and unpacked</li>
   * </ol>
   *
   * @return install operation object that will perform the setup
   */
  private InstallOperation<File, File> createInitSdkOperation(InstallContext installContext, File destination, double progressRatio) {
    File handoffSource = getHandoffAndroidSdkSource();
    if (handoffSource != null) {
      return new MergeOperation(handoffSource, installContext, progressRatio);
    }
    if (isNonEmptyDirectory(destination)) {
      SdkManager manager = SdkManager.createManager(destination.getAbsolutePath(), new NullLogger());
      if (manager != null) {
        // We have SDK, first operation simply passes path through
        return InstallOperation.wrap(installContext, new ReturnValue(), 0);
      }
    }
    return downloadAndUnzipSdkSeed(installContext, destination, progressRatio);
  }

  private static boolean isNonEmptyDirectory(File file) {
    String[] contents = !file.isDirectory() ? null : file.list(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return !(name.equalsIgnoreCase(".DS_Store") || name.equalsIgnoreCase("thumbs.db") || name.equalsIgnoreCase("desktop.ini"));
      }
    });
    return contents != null && contents.length > 0;
  }

  @Override
  protected void init() {
    boolean createAvd = myMode.shouldCreateAvd();
    String pathString = mySdkLocation.getAbsolutePath();
    myState.put(KEY_SDK_INSTALL_LOCATION, pathString);

    myComponentTree = createComponentTree(myMode, myState, createAvd);
    myComponentTree.init(myProgressStep);
    mySdkComponentsStep = new SdkComponentsStep(myComponentTree, FirstRunWizard.KEY_CUSTOM_INSTALL, KEY_SDK_INSTALL_LOCATION, myMode);
    addStep(mySdkComponentsStep);

    SdkManager manager = SdkManager.createManager(pathString, new NullLogger());
    myComponentTree.init(myProgressStep);
    myComponentTree.updateState(manager);
    for (DynamicWizardStep step : myComponentTree.createSteps()) {
      addStep(step);
    }
    if (myMode != FirstRunWizardMode.INSTALL_HANDOFF) {
      addStep(new InstallSummaryStep(FirstRunWizard.KEY_CUSTOM_INSTALL, KEY_SDK_INSTALL_LOCATION, new Supplier<Collection<RemotePkgInfo>>() {
        @Override
        public Collection<RemotePkgInfo> get() {
          return myComponentInstaller.getPackagesToInstallInfos(myState.get(KEY_SDK_INSTALL_LOCATION), myComponentTree.getChildrenToInstall());
        }
      }));
    }
  }


  @Override
  public void deriveValues(Set<ScopedStateStore.Key> modified) {
    super.deriveValues(modified);
    if (modified.contains(KEY_SDK_INSTALL_LOCATION)) {
      String sdkPath = myState.get(KEY_SDK_INSTALL_LOCATION);
      SdkManager manager = null;
      if (sdkPath != null) {
        manager = SdkManager.createManager(sdkPath, new NullLogger());
      }
      myComponentTree.updateState(manager);
    }
    if (modified.contains(FirstRunWizard.KEY_CUSTOM_INSTALL) || modified.contains(KEY_SDK_INSTALL_LOCATION) ||
        myComponentTree.componentStateChanged(modified)) {
      myState.put(WizardConstants.INSTALL_REQUESTS_KEY, getPackageDescriptions());
    }
  }

  private List<IPkgDesc> getPackageDescriptions() {
    Collection<RemotePkgInfo> installIds =
      myComponentInstaller.getPackagesToInstallInfos(myState.get(KEY_SDK_INSTALL_LOCATION), myComponentTree.getChildrenToInstall());
    ImmutableList.Builder<IPkgDesc> packages = ImmutableList.builder();
    for (RemotePkgInfo remotePackage : installIds) {
      packages.add(remotePackage.getPkgDesc());
    }
    return packages.build();
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Setup Android Studio Components";
  }

  @Override
  public void runLongOperation() throws WizardException {
    final double INIT_SDK_OPERATION_PROGRESS_SHARE = 0.3;
    final double INSTALL_COMPONENTS_OPERATION_PROGRESS_SHARE = 1.0 - INIT_SDK_OPERATION_PROGRESS_SHARE;

    final InstallContext installContext = new InstallContext(createTempDir(), myProgressStep);
    final File destination = getDestination();
    final InstallOperation<File, File> initialize = createInitSdkOperation(installContext, destination, INIT_SDK_OPERATION_PROGRESS_SHARE);

    final Collection<? extends InstallableComponent> selectedComponents = myComponentTree.getChildrenToInstall();
    CheckSdkOperation checkSdk = new CheckSdkOperation(installContext);
    InstallComponentsOperation install =
      new InstallComponentsOperation(installContext, selectedComponents, myComponentInstaller, INSTALL_COMPONENTS_OPERATION_PROGRESS_SHARE);

    SetPreference setPreference = new SetPreference(myMode.getInstallerTimestamp());
    try {
      initialize.then(checkSdk).then(install).then(setPreference).then(new ConfigureComponents(installContext, selectedComponents))
        .execute(destination);
    }
    catch (InstallationCancelledException e) {
      installContext.print("Android Studio setup was canceled", ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  public static RemotePkgInfo findLatestPlatform(Multimap<PkgType, RemotePkgInfo> remotePackages, boolean preview) {
    List<RemotePkgInfo> packages = Lists.newArrayList(remotePackages.get(PkgType.PKG_PLATFORM));
    Collections.sort(packages);
    Collections.reverse(packages);
    RemotePkgInfo latest = null;
    for (RemotePkgInfo pkg : packages) {
      boolean isPreview = pkg.getPkgDesc().getAndroidVersion().isPreview();
      if (preview) {
        if (isPreview) {
          latest = pkg;
        }
        // if it's not a preview, there isn't a preview more recent than the latest non-preview. return null.
        break;
      }
      else if (!isPreview) {
        latest = pkg;
        break;
      }
    }
    return latest;
  }

  @NotNull
  private File getDestination() throws WizardException {
    String destinationPath = myState.get(KEY_SDK_INSTALL_LOCATION);
    assert destinationPath != null;

    final File destination = new File(destinationPath);
    if (destination.isFile()) {
      throw new WizardException(String.format("Path %s does not point to a directory", destination));
    }
    return destination;
  }

  @Override
  public boolean performFinishingActions() {
    // Everything happens after wizard completion
    return true;
  }

  @Override
  public boolean isPathVisible() {
    return true;
  }

  public boolean showsStep() {
    return isPathVisible() && (existsAndIsVisible(mySdkComponentsStep) || myMode == FirstRunWizardMode.NEW_INSTALL);
  }

  private static class MergeOperation extends InstallOperation<File, File> {
    private final File myRepo;
    private final InstallContext myContext;
    private boolean myRepoWasMerged = false;

    public MergeOperation(File repo, InstallContext context, double progressRatio) {
      super(context, progressRatio);
      myRepo = repo;
      myContext = context;
    }

    @NotNull
    @Override
    protected File perform(@NotNull ProgressIndicator indicator, @NotNull File destination) throws WizardException {
      indicator.setText("Installing Android SDK");
      try {
        FileUtil.ensureExists(destination);
        if (!FileUtil.filesEqual(destination.getCanonicalFile(), myRepo.getCanonicalFile())) {
          SdkMerger.mergeSdks(myRepo, destination, indicator);
          myRepoWasMerged = true;
        }
        myContext.print(String.format("Android SDK was installed to %1$s\n", destination), ConsoleViewContentType.SYSTEM_OUTPUT);
        return destination;
      }
      catch (IOException e) {
        throw new WizardException(e.getMessage(), e);
      }
      finally {
        indicator.stop();
      }
    }

    @Override
    public void cleanup(@NotNull File result) {
      if (myRepoWasMerged && myRepo.exists()) {
        FileUtil.delete(myRepo);
      }
    }
  }

  private static class MoveSdkOperation extends InstallOperation<File, File> {
    @NotNull private final File myDestination;

    public MoveSdkOperation(@NotNull InstallContext context, @NotNull File destination, double progressShare) {
      super(context, progressShare);
      myDestination = destination;
    }

    @NotNull
    @Override
    protected File perform(@NotNull ProgressIndicator indicator, @NotNull File file) throws WizardException {
      indicator.setText("Moving downloaded SDK");
      indicator.start();
      try {
        File root = getSdkRoot(file);
        if (!root.renameTo(myDestination)) {
          FileUtil.copyDir(root, myDestination);
          FileUtil.delete(root); // Failure to delete it is not critical, the source is in temp folder.
          // No need to abort installation.
        }
        return myDestination;
      }
      catch (IOException e) {
        throw new WizardException("Unable to move Android SDK", e);
      }
      finally {
        indicator.setFraction(1.0);
        indicator.stop();
      }
    }


    @Override
    public void cleanup(@NotNull File result) {
      // Do nothing
    }
  }

  private static class ReturnValue implements Function<File, File> {
    @Override
    public File apply(@Nullable File input) {
      assert input != null;
      return input;
    }
  }

  private static class SetPreference implements Function<File, File> {
    @Nullable private final String myInstallerTimestamp;

    public SetPreference(@Nullable String installerTimestamp) {
      myInstallerTimestamp = installerTimestamp;
    }

    @Override
    public File apply(@Nullable final File input) {
      assert input != null;
      final Application application = ApplicationManager.getApplication();
      // SDK can only be set from write action, write action can only be started from UI thread
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          application.runWriteAction(new Runnable() {
            @Override
            public void run() {
              IdeSdks.setAndroidSdkPath(input, null);
              AndroidFirstRunPersistentData.getInstance().markSdkUpToDate(myInstallerTimestamp);
            }
          });
        }
      }, application.getAnyModalityState());
      return input;
    }
  }

  private static class ConfigureComponents implements Function<File, File> {
    private final InstallContext myInstallContext;
    private final Collection<? extends InstallableComponent> mySelectedComponents;

    public ConfigureComponents(InstallContext installContext, Collection<? extends InstallableComponent> selectedComponents) {
      myInstallContext = installContext;
      mySelectedComponents = selectedComponents;
    }

    @Override
    public File apply(@Nullable File input) {
      assert input != null;
      for (InstallableComponent component : mySelectedComponents) {
        component.configure(myInstallContext, input);
      }
      return input;
    }
  }
}
