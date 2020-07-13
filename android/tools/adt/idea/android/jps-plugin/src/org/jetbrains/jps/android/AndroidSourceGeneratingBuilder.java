package org.jetbrains.jps.android;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.build.BuildConfigGenerator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.android.compiler.artifact.AndroidArtifactSigningMode;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.compiler.tools.AndroidIdl;
import org.jetbrains.android.compiler.tools.AndroidRenderscript;
import org.jetbrains.android.util.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.android.model.AndroidApplicationArtifactType;
import org.jetbrains.jps.android.model.JpsAndroidApplicationArtifactProperties;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.java.FormsParsing;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.*;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSourceGeneratingBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.android.AndroidSourceGeneratingBuilder");

  @NonNls private static final String ANDROID_VALIDATOR = "android-validator";
  @NonNls private static final String ANDROID_IDL_COMPILER = "android-idl-compiler";
  @NonNls private static final String ANDROID_RENDERSCRIPT_COMPILER = "android-renderscript-compiler";
  @NonNls private static final String ANDROID_BUILD_CONFIG_GENERATOR = "android-buildconfig-generator";
  @NonNls private static final String ANDROID_APT_COMPILER = "android-apt-compiler";
  @NonNls private static final String ANDROID_GENERATED_SOURCES_PROCESSOR = "android-generated-sources-processor";
  @NonNls private static final String BUILDER_NAME = "Android Source Generator";

  @NonNls private static final String AIDL_EXTENSION = "aidl";
  @NonNls private static final String RENDERSCRIPT_EXTENSION = "rs";
  @NonNls private static final String PERMISSION_TAG = "permission";
  @NonNls private static final String PERMISSION_GROUP_TAG = "permission-group";
  @NonNls private static final String NAME_ATTRIBUTE = "name";

  private static final int MIN_PLATFORM_TOOLS_REVISION = 11;
  private static final int MIN_SDK_TOOLS_REVISION = 19;

  public static final Key<Boolean> IS_ENABLED = Key.create("_android_source_generator_enabled_");

  @NonNls private static final String R_TXT_OUTPUT_DIR_NAME = "r_txt";

  public AndroidSourceGeneratingBuilder() {
    super(BuilderCategory.SOURCE_GENERATOR);
  }

  @Override
  public void buildStarted(CompileContext context) {
    IS_ENABLED.set(context, true);
  }

  @Override
  public void buildFinished(CompileContext context) {
    AndroidBuildDataCache.clean();
  }

  @Override
  public ModuleLevelBuilder.ExitCode build(CompileContext context,
                                           ModuleChunk chunk,
                                           DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                           OutputConsumer outputConsumer) throws ProjectBuildException {
    if (!IS_ENABLED.get(context, Boolean.TRUE) || chunk.containsTests() || !AndroidJpsUtil.isAndroidProjectWithoutGradleFacet(chunk)) {
      return ExitCode.NOTHING_DONE;
    }

    try {
      return doBuild(context, chunk, dirtyFilesHolder);
    }
    catch (Exception e) {
      return AndroidJpsUtil.handleException(context, e, BUILDER_NAME, LOG);
    }
  }

  @Override
  public List<String> getCompilableFileExtensions() {
    return Arrays.asList(AIDL_EXTENSION, RENDERSCRIPT_EXTENSION);
  }

  private static ModuleLevelBuilder.ExitCode doBuild(CompileContext context,
                                                     ModuleChunk chunk,
                                                     DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder)
    throws IOException {
    final Map<JpsModule, MyModuleData> moduleDataMap = computeModuleDatas(chunk.getModules(), context);
    if (moduleDataMap == null || moduleDataMap.size() == 0) {
      return ExitCode.ABORT;
    }

    if (!checkVersions(moduleDataMap, context)) {
      return ExitCode.ABORT;
    }
    checkAndroidDependencies(moduleDataMap, context);

    if (!checkArtifacts(context)) {
      return ExitCode.ABORT;
    }

    if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)) {
      if (!clearAndroidStorages(context, chunk.getModules())) {
        return ExitCode.ABORT;
      }
    }

    final Map<File, ModuleBuildTarget> idlFilesToCompile = new HashMap<File, ModuleBuildTarget>();
    final Map<File, ModuleBuildTarget> rsFilesToCompile = new HashMap<File, ModuleBuildTarget>();

    dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
      @Override
      public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor sourceRoot) throws IOException {
        final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(target.getModule());

        if (extension == null) {
          return true;
        }
        String fileName = file.getName();

        if (FileUtilRt.extensionEquals(fileName, AIDL_EXTENSION)) {
          idlFilesToCompile.put(file, target);
        }
        else if (FileUtilRt.extensionEquals(fileName, RENDERSCRIPT_EXTENSION)) {
          rsFilesToCompile.put(file, target);
        }

        return true;
      }
    });
    boolean success = true;

    final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)) {
      for (JpsModule module : moduleDataMap.keySet()) {
        final File generatedSourcesStorage = AndroidJpsUtil.getGeneratedSourcesStorage(module, dataManager);
        if (generatedSourcesStorage.exists() &&
            !deleteAndMarkRecursively(generatedSourcesStorage, context, BUILDER_NAME)) {
          success = false;
        }

        final File generatedResourcesStorage = AndroidJpsUtil.getGeneratedResourcesStorage(module, dataManager);
        if (generatedResourcesStorage.exists() &&
            !deleteAndMarkRecursively(generatedResourcesStorage, context, BUILDER_NAME)) {
          success = false;
        }
      }
    }

    if (!success) {
      return ExitCode.ABORT;
    }
    boolean didSomething = false;

    if (idlFilesToCompile.size() > 0) {
      if (!runAidlCompiler(context, idlFilesToCompile, moduleDataMap)) {
        success = false;
      }
      didSomething = true;
    }

    if (rsFilesToCompile.size() > 0) {
      if (!runRenderscriptCompiler(context, rsFilesToCompile, moduleDataMap)) {
        success = false;
      }
      didSomething = true;
    }
    MyExitStatus status = runAaptCompiler(context, moduleDataMap);

    if (status == MyExitStatus.FAIL) {
      success = false;
    }
    else if (status == MyExitStatus.OK) {
      didSomething = true;
    }
    status = runBuildConfigGeneration(context, moduleDataMap);

    if (status == MyExitStatus.FAIL) {
      success = false;
    }
    else if (status == MyExitStatus.OK) {
      didSomething = true;
    }

    if (!success) {
      return ExitCode.ABORT;
    }
    status = copyGeneratedSources(moduleDataMap, dataManager, context);
    if (status == MyExitStatus.FAIL) {
      return ExitCode.ABORT;
    }
    else if (status == MyExitStatus.OK) {
      didSomething = true;
    }

    if (didSomething) {
      return ExitCode.OK;
    }
    return ExitCode.NOTHING_DONE;
  }

  @NotNull
  private static List<String> filterExcludedByOtherProviders(@NotNull JpsModule module, @NotNull Collection<String> genRoots) {
    final Set<String> genRootPaths = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);

    for (String genRoot : genRoots) {
      genRootPaths.add(FileUtil.toSystemIndependentName(genRoot));
    }
    final List<String> result = new ArrayList<String>();
    final List<JpsModuleSourceRoot> genSourceRoots = new ArrayList<JpsModuleSourceRoot>();

    for (JpsModuleSourceRoot root : module.getSourceRoots()) {
      if (genRootPaths.contains(FileUtil.toSystemIndependentName(root.getFile().getPath()))) {
        genSourceRoots.add(root);
      }
    }
    final Iterable<ExcludedJavaSourceRootProvider> excludedRootProviders = JpsServiceManager.
      getInstance().getExtensions(ExcludedJavaSourceRootProvider.class);

    for (JpsModuleSourceRoot genSourceRoot : genSourceRoots) {
      boolean excluded = false;

      for (ExcludedJavaSourceRootProvider provider : excludedRootProviders) {
        if (!(provider instanceof AndroidExcludedJavaSourceRootProvider) &&
            provider.isExcludedFromCompilation(module, genSourceRoot)) {
          excluded = true;
          break;
        }
      }
      final String genRootFilePath = genSourceRoot.getFile().getPath();

      if (!excluded) {
        result.add(genRootFilePath);
      }
    }
    return result;
  }

  @NotNull
  private static MyExitStatus copyGeneratedSources(@NotNull Map<JpsModule, MyModuleData> moduleDataMap,
                                                   @NotNull BuildDataManager dataManager,
                                                   @NotNull final CompileContext context)
    throws IOException {
    final Ref<Boolean> didSomething = Ref.create(false);
    final Ref<Boolean> success = Ref.create(true);

    for (Map.Entry<JpsModule, MyModuleData> entry : moduleDataMap.entrySet()) {
      final JpsModule module = entry.getKey();
      final MyModuleData data = entry.getValue();

      if (!data.getAndroidExtension().isCopyCustomGeneratedSources()) {
        continue;
      }
      final ModuleBuildTarget moduleTarget = new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION);
      final AndroidGenSourcesCopyingStorage storage = context.getProjectDescriptor().dataManager.getStorage(
        moduleTarget, AndroidGenSourcesCopyingStorage.PROVIDER);

      final Set<String> genDirs = AndroidJpsUtil.getGenDirs(data.getAndroidExtension());
      final List<String> filteredGenDirs = filterExcludedByOtherProviders(module, genDirs);

      final Set<String> forciblyExcludedDirs = new HashSet<String>(genDirs);
      forciblyExcludedDirs.removeAll(filteredGenDirs);
      warnUserAboutForciblyExcludedRoots(forciblyExcludedDirs, context);

      final AndroidFileSetState savedState = storage.read();

      final AndroidFileSetState currentState = new AndroidFileSetState(filteredGenDirs, new Condition<File>() {
        @Override
        public boolean value(File file) {
          try {
            return shouldBeCopied(file);
          }
          catch (IOException e) {
            return false;
          }
        }
      }, true);

      if (currentState.equalsTo(savedState)) {
        continue;
      }
      final File outDir = AndroidJpsUtil.getCopiedSourcesStorage(module, dataManager.getDataPaths());
      clearDirectoryIfNotEmpty(outDir, context, ANDROID_GENERATED_SOURCES_PROCESSOR);
      final List<Pair<String, String>> copiedFiles = new ArrayList<Pair<String, String>>();

      for (String path : filteredGenDirs) {
        final File dir = new File(path);

        if (dir.isDirectory()) {
          FileUtil.processFilesRecursively(dir, new Processor<File>() {
            @Override
            public boolean process(File file) {
              try {
                if (!shouldBeCopied(file)) {
                  return true;
                }
                final String relPath = FileUtil.getRelativePath(dir, file);
                final File dstFile = new File(outDir.getPath() + "/" + relPath);
                final File dstDir = dstFile.getParentFile();

                if (!dstDir.exists() && !dstDir.mkdirs()) {
                  context.processMessage(new CompilerMessage(
                    ANDROID_GENERATED_SOURCES_PROCESSOR, BuildMessage.Kind.ERROR, AndroidJpsBundle.message(
                    "android.jps.cannot.create.directory", dstDir.getPath())));
                  return true;
                }
                FileUtil.copy(file, dstFile);
                copiedFiles.add(Pair.create(file.getPath(), dstFile.getPath()));
                didSomething.set(true);
              }
              catch (IOException e) {
                AndroidJpsUtil.reportExceptionError(context, null, e, ANDROID_GENERATED_SOURCES_PROCESSOR);
                success.set(false);
                return true;
              }
              return true;
            }
          });
        }
      }
      final File generatedSourcesDir = AndroidJpsUtil.getGeneratedSourcesStorage(
        module, dataManager.getDataPaths());
      final List<String> deletedFiles = new ArrayList<String>();

      if (!removeCopiedFilesDuplicatingGeneratedFiles(context, outDir, generatedSourcesDir, deletedFiles)) {
        success.set(false);
        continue;
      }
      final AndroidBuildTestingManager testingManager = AndroidBuildTestingManager.getTestingManager();

      if (testingManager != null) {
        logGeneratedSourcesProcessing(testingManager, copiedFiles, deletedFiles);
      }
      markDirtyRecursively(outDir, context, ANDROID_GENERATED_SOURCES_PROCESSOR, false);
      storage.saveState(currentState);
    }
    if (didSomething.get()) {
      return success.get() ? MyExitStatus.OK : MyExitStatus.FAIL;
    }
    else {
      return MyExitStatus.NOTHING_CHANGED;
    }
  }

  private static void warnUserAboutForciblyExcludedRoots(@NotNull Set<String> paths, @NotNull CompileContext context) {
    for (String dir : paths) {
      final boolean hasFileToCopy = !FileUtil.processFilesRecursively(new File(dir), new Processor<File>() {
        @Override
        public boolean process(File file) {
          try {
            return !shouldBeCopied(file);
          }
          catch (IOException e) {
            return false;
          }
        }
      });

      if (hasFileToCopy) {
        context.processMessage(new CompilerMessage(ANDROID_GENERATED_SOURCES_PROCESSOR, BuildMessage.Kind.WARNING,
                                                   "Source root " + FileUtil.toSystemDependentName(dir) +
                                                   " was forcibly excluded by the IDE, so custom generated files won't be compiled"));
      }
    }
  }

  private static boolean shouldBeCopied(@NotNull File file) throws IOException {
    return file.isFile() &&
           (!FileUtilRt.extensionEquals(file.getName(), "java") ||
           !isGeneratedByIdea(file));
  }

  private static boolean removeCopiedFilesDuplicatingGeneratedFiles(final CompileContext context,
                                                                    final File copiedFilesDir,
                                                                    final File generatedSourcesDir,
                                                                    final List<String> deletedFiles) {
    if (!generatedSourcesDir.isDirectory()) {
      return true;
    }
    final File[] genRoots = generatedSourcesDir.listFiles();

    if (genRoots == null || genRoots.length == 0) {
      return true;
    }
    final Ref<Boolean> success = Ref.create(true);

    FileUtil.processFilesRecursively(copiedFilesDir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (!file.isFile()) {
          return true;
        }
        final String relPath = FileUtil.getRelativePath(copiedFilesDir, file);

        if (relPath != null) {
          boolean toDelete = false;

          for (File genRoot : genRoots) {
            final File genFile = new File(genRoot.getPath() + "/" + relPath);

            if (genFile.exists()) {
              LOG.debug("File " + file.getPath() + " duplicates generated file " + genFile.getPath() + ", so it'll be deleted");
              toDelete = true;
              break;
            }
          }

          if (toDelete) {
            if (!FileUtil.delete(file)) {
              context.processMessage(new CompilerMessage(ANDROID_GENERATED_SOURCES_PROCESSOR, BuildMessage.Kind.ERROR,
                                                         "Cannot remove file " + file.getPath()));
              success.set(false);
            }
            else {
              deletedFiles.add(file.getPath());
            }
          }
        }
        return true;
      }
    });
    return success.get();
  }

  private static void logGeneratedSourcesProcessing(AndroidBuildTestingManager manager,
                                                    List<Pair<String, String>> copiedFiles,
                                                    List<String> deletedFiles) {
    if (copiedFiles.size() == 0 && deletedFiles.size() == 0) {
      return;
    }
    final StringBuilder message = new StringBuilder(ANDROID_GENERATED_SOURCES_PROCESSOR);
    message.append("\n");

    if (copiedFiles.size() > 0) {
      Collections.sort(copiedFiles, new Comparator<Pair<String, String>>() {
        @Override
        public int compare(Pair<String, String> o1, Pair<String, String> o2) {
          return (o1.getFirst() + o1.getSecond()).compareTo(o2.getFirst() + o2.getSecond());
        }
      });
      message.append("Copied files\n");

      for (Pair<String, String> pair : copiedFiles) {
        message.append(pair.getFirst()).append('\n').append(pair.getSecond()).append('\n');
      }
    }

    if (deletedFiles.size() > 0) {
      Collections.sort(deletedFiles);
      message.append("Deleted files\n");

      for (String path : deletedFiles) {
        message.append(path).append('\n');
      }
    }
    manager.getCommandExecutor().log(message.toString());
  }

  private static boolean isGeneratedByIdea(File file) throws IOException {
    final String text = FileUtil.loadFile(file);
    return text.startsWith(AndroidCommonUtils.AUTOGENERATED_JAVA_FILE_HEADER);
  }

  private static boolean clearAndroidStorages(@NotNull CompileContext context, @NotNull Collection<JpsModule> modules) {
    for (JpsModule module : modules) {
      final File dir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, module);
      if (dir.exists() && !FileUtil.delete(dir)) {
        context.processMessage(
          new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle.message("android.jps.cannot.delete", dir.getPath())));
        return false;
      }
    }
    return true;
  }

  private static boolean checkVersions(@NotNull Map<JpsModule, MyModuleData> dataMap, @NotNull CompileContext context) {
    for (Map.Entry<JpsModule, MyModuleData> entry : dataMap.entrySet()) {
      final JpsModule module = entry.getKey();
      final AndroidPlatform platform = entry.getValue().getPlatform();

      boolean success = true;

      final int platformToolsRevision = platform.getPlatformToolsRevision();
      if (platformToolsRevision >= 0 && platformToolsRevision < MIN_PLATFORM_TOOLS_REVISION) {
        final String message = '[' +
                               module.getName() +
                               "] Incompatible version of Android SDK Platform-tools package. Min version is " +
                               MIN_PLATFORM_TOOLS_REVISION +
                               ". Please, update it though SDK manager";
        context.processMessage(new CompilerMessage(ANDROID_VALIDATOR, BuildMessage.Kind.ERROR, message));
        success = false;
      }

      final int sdkToolsRevision = platform.getSdkToolsRevision();
      if (sdkToolsRevision >= 0 && sdkToolsRevision < MIN_SDK_TOOLS_REVISION) {
        final String message = '[' + module.getName() + "] Incompatible version " +
                               sdkToolsRevision + " of Android SDK Tools package. Min version is " +
                               MIN_SDK_TOOLS_REVISION + ". Please, update it though SDK manager";
        context.processMessage(new CompilerMessage(ANDROID_VALIDATOR, BuildMessage.Kind.ERROR, message));
        success = false;
      }

      // show error message only for first module, because all modules usualy have the same sdk specified
      if (!success) {
        return false;
      }
    }
    return true;
  }

  private static void checkAndroidDependencies(@NotNull Map<JpsModule, MyModuleData> moduleDataMap, @NotNull CompileContext context) {
    for (Map.Entry<JpsModule, MyModuleData> entry : moduleDataMap.entrySet()) {
      final JpsModule module = entry.getKey();
      final MyModuleData moduleData = entry.getValue();
      final JpsAndroidModuleExtension extension = moduleData.getAndroidExtension();

      if (extension.isLibrary()) {
        continue;
      }

      for (JpsDependencyElement item : JpsJavaExtensionService.getInstance()
        .getDependencies(module, JpsJavaClasspathKind.PRODUCTION_RUNTIME, false)) {
        if (item instanceof JpsModuleDependency) {
          final JpsModule depModule = ((JpsModuleDependency)item).getModule();
          if (depModule != null) {
            final JpsAndroidModuleExtension depExtension = AndroidJpsUtil.getExtension(depModule);

            if (depExtension != null && !depExtension.isLibrary()) {
              String message = "Suspicious module dependency " + module.getName() + " -> " + depModule.getName() +
                               ": Android application module depends on other application module. Possibly, you should " +
                               "change type of module '" + depModule.getName() +
                               "' to 'Library' or change the dependency scope to 'Provided'.";
              context.processMessage(new CompilerMessage(ANDROID_VALIDATOR, BuildMessage.Kind.WARNING, message));
            }
          }
        }
      }
    }
  }

  private static MyExitStatus runBuildConfigGeneration(@NotNull CompileContext context,
                                                       @NotNull Map<JpsModule, MyModuleData> moduleDataMap) throws IOException {
    boolean success = true;
    boolean didSomething = false;

    for (Map.Entry<JpsModule, MyModuleData> entry : moduleDataMap.entrySet()) {
      final JpsModule module = entry.getKey();
      final ModuleBuildTarget moduleTarget = new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION);
      final AndroidBuildConfigStateStorage storage =
        context.getProjectDescriptor().dataManager.getStorage(
          moduleTarget, AndroidBuildConfigStateStorage.PROVIDER);

      final MyModuleData moduleData = entry.getValue();
      final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);

      final File generatedSourcesDir = AndroidJpsUtil.getGeneratedSourcesStorage(module, context.getProjectDescriptor().dataManager);
      final File outputDirectory = new File(generatedSourcesDir, AndroidJpsUtil.BUILD_CONFIG_GENERATED_SOURCE_ROOT_NAME);

      try {
        if (extension == null || isLibraryWithBadCircularDependency(extension)) {
          if (!clearDirectoryIfNotEmpty(outputDirectory, context, ANDROID_BUILD_CONFIG_GENERATOR)) {
            success = false;
          }
          continue;
        }
        final String packageName = moduleData.getPackage();
        final boolean debug = !AndroidJpsUtil.isReleaseBuild(context);
        final Set<String> libPackages = new HashSet<String>(getDepLibPackages(module).values());
        libPackages.remove(packageName);

        final AndroidBuildConfigState newState = new AndroidBuildConfigState(packageName, libPackages, debug);

        final AndroidBuildConfigState oldState = storage.getState(module.getName());
        if (newState.equalsTo(oldState)) {
          continue;
        }
        didSomething = true;
        context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.build.config", module.getName())));

        // clear directory, because it may contain obsolete files (ex. if package name was changed)
        if (!clearDirectory(outputDirectory, context, ANDROID_BUILD_CONFIG_GENERATOR)) {
          success = false;
          continue;
        }

        if (doBuildConfigGeneration(packageName, libPackages, debug, outputDirectory, context)) {
          storage.update(module.getName(), newState);
          markDirtyRecursively(outputDirectory, context, ANDROID_BUILD_CONFIG_GENERATOR, true);
        }
        else {
          storage.update(module.getName(), null);
          success = false;
        }
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, null, e, ANDROID_BUILD_CONFIG_GENERATOR);
        success = false;
      }
    }

    if (!success) {
      return MyExitStatus.FAIL;
    }
    else if (didSomething) {
      return MyExitStatus.OK;
    }
    return MyExitStatus.NOTHING_CHANGED;
  }

  private static boolean doBuildConfigGeneration(@NotNull String packageName,
                                                 @NotNull Collection<String> libPackages,
                                                 boolean debug,
                                                 @NotNull File outputDirectory,
                                                 @NotNull CompileContext context) {
    if (!doBuildConfigGeneration(packageName, debug, outputDirectory.getPath(), context)) {
      return false;
    }

    for (String libPackage : libPackages) {
      if (!doBuildConfigGeneration(libPackage, debug, outputDirectory.getPath(), context)) {
        return false;
      }
    }
    return true;
  }

  private static boolean doBuildConfigGeneration(@NotNull String packageName,
                                                 boolean debug,
                                                 @NotNull String outputDirOsPath,
                                                 @NotNull CompileContext context) {
    final BuildConfigGenerator generator = new BuildConfigGenerator(outputDirOsPath, packageName, debug);
    try {
      generator.generate();
      return true;
    }
    catch (IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, ANDROID_BUILD_CONFIG_GENERATOR);
      return false;
    }
  }

  private static boolean runAidlCompiler(@NotNull final CompileContext context,
                                         @NotNull Map<File, ModuleBuildTarget> files,
                                         @NotNull Map<JpsModule, MyModuleData> moduleDataMap) {
    if (files.size() > 0) {
      context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.aidl")));
    }

    boolean success = true;

    for (Map.Entry<File, ModuleBuildTarget> entry : files.entrySet()) {
      final File file = entry.getKey();
      final ModuleBuildTarget buildTarget = entry.getValue();
      final String filePath = file.getPath();

      final MyModuleData moduleData = moduleDataMap.get(buildTarget.getModule());

      if (!LOG.assertTrue(moduleData != null)) {
        context.processMessage(
          new CompilerMessage(ANDROID_IDL_COMPILER, BuildMessage.Kind.ERROR, AndroidJpsBundle.message("android.jps.internal.error")));
        success = false;
        continue;
      }
      final File generatedSourcesDir =
        AndroidJpsUtil.getGeneratedSourcesStorage(buildTarget.getModule(), context.getProjectDescriptor().dataManager);
      final File aidlOutputDirectory = new File(generatedSourcesDir, AndroidJpsUtil.AIDL_GENERATED_SOURCE_ROOT_NAME);

      if (!aidlOutputDirectory.exists() && !aidlOutputDirectory.mkdirs()) {
        context.processMessage(
          new CompilerMessage(ANDROID_IDL_COMPILER, BuildMessage.Kind.ERROR,
                              AndroidJpsBundle.message("android.jps.cannot.create.directory", aidlOutputDirectory.getPath())));
        success = false;
        continue;
      }

      final IAndroidTarget target = moduleData.getPlatform().getTarget();

      try {
        final File[] sourceRoots = AndroidJpsUtil.getSourceRootsForModuleAndDependencies(buildTarget.getModule());
        final String[] sourceRootPaths = AndroidJpsUtil.toPaths(sourceRoots);
        final String packageName = computePackageForFile(context, file);

        if (packageName == null) {
          context.processMessage(new CompilerMessage(ANDROID_IDL_COMPILER, BuildMessage.Kind.ERROR,
                                                     AndroidJpsBundle.message("android.jps.errors.cannot.compute.package", filePath)));
          success = false;
          continue;
        }

        final File outputFile = new File(aidlOutputDirectory, packageName.replace('.', File.separatorChar) +
                                                              File.separator + FileUtil.getNameWithoutExtension(file) + ".java");
        final String outputFilePath = outputFile.getPath();
        final Map<AndroidCompilerMessageKind, List<String>> messages =
          AndroidIdl.execute(target, filePath, outputFilePath, sourceRootPaths);

        addMessages(context, messages, filePath, ANDROID_IDL_COMPILER);

        if (messages.get(AndroidCompilerMessageKind.ERROR).size() > 0) {
          success = false;
        }
        else if (outputFile.exists()) {
          final SourceToOutputMapping sourceToOutputMap = context.getProjectDescriptor().dataManager.getSourceToOutputMap(buildTarget);
          sourceToOutputMap.setOutput(filePath, outputFilePath);
          FSOperations.markDirty(context, CompilationRound.CURRENT, outputFile);
        }
      }
      catch (final IOException e) {
        AndroidJpsUtil.reportExceptionError(context, filePath, e, ANDROID_IDL_COMPILER);
        success = false;
      }
    }
    return success;
  }

  private static boolean runRenderscriptCompiler(@NotNull final CompileContext context,
                                                 @NotNull Map<File, ModuleBuildTarget> files,
                                                 @NotNull Map<JpsModule, MyModuleData> moduleDataMap) {
    if (files.size() > 0) {
      context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.renderscript")));
    }

    boolean success = true;

    for (Map.Entry<File, ModuleBuildTarget> entry : files.entrySet()) {
      final File file = entry.getKey();
      final ModuleBuildTarget buildTarget = entry.getValue();

      final MyModuleData moduleData = moduleDataMap.get(buildTarget.getModule());
      if (!LOG.assertTrue(moduleData != null)) {
        context.processMessage(new CompilerMessage(ANDROID_RENDERSCRIPT_COMPILER, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.internal.error")));
        success = false;
        continue;
      }

      final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
      final File generatedSourcesDir = AndroidJpsUtil.getGeneratedSourcesStorage(buildTarget.getModule(), dataManager);
      final File rsOutputDirectory = new File(generatedSourcesDir, AndroidJpsUtil.RENDERSCRIPT_GENERATED_SOURCE_ROOT_NAME);
      if (!rsOutputDirectory.exists() && !rsOutputDirectory.mkdirs()) {
        context.processMessage(new CompilerMessage(ANDROID_RENDERSCRIPT_COMPILER, BuildMessage.Kind.ERROR, AndroidJpsBundle
          .message("android.jps.cannot.create.directory", rsOutputDirectory.getPath())));
        success = false;
        continue;
      }

      final File generatedResourcesDir = AndroidJpsUtil.getGeneratedResourcesStorage(buildTarget.getModule(), dataManager);
      final File rawDir = new File(generatedResourcesDir, "raw");

      if (!rawDir.exists() && !rawDir.mkdirs()) {
        context.processMessage(new CompilerMessage(ANDROID_RENDERSCRIPT_COMPILER, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.cannot.create.directory", rawDir.getPath())));
        success = false;
        continue;
      }

      final AndroidPlatform platform = moduleData.getPlatform();
      final IAndroidTarget target = platform.getTarget();
      final String sdkLocation = platform.getSdk().getHomePath();
      final String filePath = file.getPath();

      File tmpOutputDirectory = null;

      try {
        tmpOutputDirectory = FileUtil.createTempDirectory("generated-rs-temp", null);
        final String depFolderPath = getDependencyFolder(context, file, tmpOutputDirectory);

        final Map<AndroidCompilerMessageKind, List<String>> messages =
          AndroidRenderscript.execute(sdkLocation, target, filePath, tmpOutputDirectory.getPath(), depFolderPath, rawDir.getPath());

        addMessages(context, messages, filePath, ANDROID_RENDERSCRIPT_COMPILER);

        if (messages.get(AndroidCompilerMessageKind.ERROR).size() > 0) {
          success = false;
        }
        else {
          final List<File> newFiles = new ArrayList<File>();
          AndroidCommonUtils.moveAllFiles(tmpOutputDirectory, rsOutputDirectory, newFiles);

          final File bcFile = new File(rawDir, FileUtil.getNameWithoutExtension(file) + ".bc");
          if (bcFile.exists()) {
            newFiles.add(bcFile);
          }
          final List<String> newFilePaths = Arrays.asList(AndroidJpsUtil.toPaths(newFiles.toArray(new File[newFiles.size()])));

          final SourceToOutputMapping sourceToOutputMap = dataManager.getSourceToOutputMap(buildTarget);
          sourceToOutputMap.setOutputs(filePath, newFilePaths);

          for (File newFile : newFiles) {
            FSOperations.markDirty(context, CompilationRound.CURRENT, newFile);
          }
        }
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, filePath, e, ANDROID_RENDERSCRIPT_COMPILER);
        success = false;
      }
      finally {
        if (tmpOutputDirectory != null) {
          FileUtil.delete(tmpOutputDirectory);
        }
      }
    }
    return success;
  }

  private static MyExitStatus runAaptCompiler(@NotNull final CompileContext context,
                                              @NotNull Map<JpsModule, MyModuleData> moduleDataMap)
    throws IOException {
    boolean success = true;
    boolean didSomething = false;

    for (Map.Entry<JpsModule, MyModuleData> entry : moduleDataMap.entrySet()) {
      final JpsModule module = entry.getKey();
      final ModuleBuildTarget moduleTarget = new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION);
      final AndroidAptStateStorage storage =
        context.getProjectDescriptor().dataManager.getStorage(
          moduleTarget, AndroidAptStateStorage.PROVIDER);

      final MyModuleData moduleData = entry.getValue();
      final JpsAndroidModuleExtension extension = moduleData.getAndroidExtension();

      final File generatedSourcesDir = AndroidJpsUtil.getGeneratedSourcesStorage(module, context.getProjectDescriptor().dataManager);
      final File aptOutputDirectory = new File(generatedSourcesDir, AndroidJpsUtil.AAPT_GENERATED_SOURCE_ROOT_NAME);
      final IAndroidTarget target = moduleData.getPlatform().getTarget();

      try {
        final String[] resPaths = AndroidJpsUtil.collectResourceDirsForCompilation(extension, false, context, true);
        if (resPaths.length == 0) {
          // there is no resources in the module
          if (!clearDirectoryIfNotEmpty(aptOutputDirectory, context, ANDROID_APT_COMPILER)) {
            success = false;
          }
          continue;
        }
        final String packageName = moduleData.getPackage();
        final File manifestFile;

        if (extension.isLibrary() || !extension.isManifestMergingEnabled()) {
          manifestFile = moduleData.getManifestFileForCompiler();
        }
        else {
          manifestFile = new File(AndroidJpsUtil.getPreprocessedManifestDirectory(module, context.
            getProjectDescriptor().dataManager.getDataPaths()), SdkConstants.FN_ANDROID_MANIFEST_XML);
        }

        if (isLibraryWithBadCircularDependency(extension)) {
          if (!clearDirectoryIfNotEmpty(aptOutputDirectory, context, ANDROID_APT_COMPILER)) {
            success = false;
          }
          continue;
        }
        final Map<JpsModule, String> packageMap = getDepLibPackages(module);
        packageMap.put(module, packageName);

        final JpsModule circularDepLibWithSamePackage = findCircularDependencyOnLibraryWithSamePackage(extension, packageMap);
        if (circularDepLibWithSamePackage != null && !extension.isLibrary()) {
          final String message = "Generated fields in " +
                                 packageName +
                                 ".R class in module '" +
                                 module.getName() +
                                 "' won't be final, because of circular dependency on module '" +
                                 circularDepLibWithSamePackage.getName() +
                                 "'";
          context.processMessage(new CompilerMessage(ANDROID_APT_COMPILER, BuildMessage.Kind.WARNING, message));
        }
        final boolean generateNonFinalFields = extension.isLibrary() || circularDepLibWithSamePackage != null;

        AndroidAptValidityState oldState;

        try {
          oldState = storage.getState(module.getName());
        }
        catch (IOException e) {
          LOG.info(e);
          oldState = null;
        }
        final Map<String, ResourceFileData> resources = new HashMap<String, ResourceFileData>();
        final TObjectLongHashMap<String> valueResFilesTimestamps = new TObjectLongHashMap<String>();
        collectResources(resPaths, resources, valueResFilesTimestamps, oldState);

        final List<ResourceEntry> manifestElements = collectManifestElements(manifestFile);
        final List<Pair<String, String>> libRTextFilesAndPackages = new ArrayList<Pair<String, String>>(packageMap.size());

        for (Map.Entry<JpsModule, String> entry1 : packageMap.entrySet()) {
          final String libPackage = entry1.getValue();

          if (!packageName.equals(libPackage)) {
            final String libRTxtFilePath = new File(new File(AndroidJpsUtil.getDirectoryForIntermediateArtifacts(
              context, entry1.getKey()), R_TXT_OUTPUT_DIR_NAME), SdkConstants.FN_RESOURCE_TEXT).getPath();
            libRTextFilesAndPackages.add(Pair.create(libRTxtFilePath, libPackage));
          }
        }
        AndroidJpsUtil.collectRTextFilesFromAarDeps(module, libRTextFilesAndPackages);

        final File outputDirForArtifacts = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, module);
        final String proguardOutputCfgFilePath;

        if (AndroidJpsUtil.getProGuardConfigIfShouldRun(context, extension) != null) {
          if (AndroidJpsUtil.createDirIfNotExist(outputDirForArtifacts, context, BUILDER_NAME) == null) {
            success = false;
            continue;
          }
          proguardOutputCfgFilePath = new File(outputDirForArtifacts, AndroidCommonUtils.PROGUARD_CFG_OUTPUT_FILE_NAME).getPath();
        }
        else {
          proguardOutputCfgFilePath = null;
        }
        String rTxtOutDirOsPath = null;

        if (extension.isLibrary() || libRTextFilesAndPackages.size() > 0) {
          final File rTxtOutDir = new File(outputDirForArtifacts, R_TXT_OUTPUT_DIR_NAME);

          if (AndroidJpsUtil.createDirIfNotExist(rTxtOutDir, context, BUILDER_NAME) == null) {
            success = false;
            continue;
          }
          rTxtOutDirOsPath = rTxtOutDir.getPath();
        }
        final AndroidAptValidityState newState =
          new AndroidAptValidityState(resources, valueResFilesTimestamps, manifestElements, libRTextFilesAndPackages,
                                      packageName, proguardOutputCfgFilePath, rTxtOutDirOsPath, extension.isLibrary());

        if (newState.equalsTo(oldState)) {
          // we need to update state, because it also contains myValueResFilesTimestamps not taking into account by equalsTo()
          storage.update(module.getName(), newState);
          continue;
        }
        didSomething = true;
        context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.aapt", module.getName())));

        File tmpOutputDir = null;
        try {
          tmpOutputDir = FileUtil.createTempDirectory("android_apt_output", "tmp");
          final Map<AndroidCompilerMessageKind, List<String>> messages = AndroidApt.compile(
            target, -1, manifestFile.getPath(), packageName, tmpOutputDir.getPath(), resPaths, libRTextFilesAndPackages,
            generateNonFinalFields, proguardOutputCfgFilePath, rTxtOutDirOsPath, !extension.isLibrary());

          AndroidJpsUtil.addMessages(context, messages, ANDROID_APT_COMPILER, module.getName());

          if (messages.get(AndroidCompilerMessageKind.ERROR).size() > 0) {
            success = false;
            storage.update(module.getName(), null);
          }
          else {
            if (!AndroidCommonUtils.directoriesContainSameContent(tmpOutputDir, aptOutputDirectory, JavaFilesFilter.INSTANCE)) {
              if (!deleteAndMarkRecursively(aptOutputDirectory, context, ANDROID_APT_COMPILER)) {
                success = false;
                continue;
              }
              final File parent = aptOutputDirectory.getParentFile();
              if (parent != null && !parent.exists() && !parent.mkdirs()) {
                context.processMessage(new CompilerMessage(ANDROID_APT_COMPILER, BuildMessage.Kind.ERROR, AndroidJpsBundle.message(
                  "android.jps.cannot.create.directory", parent.getPath())));
                success = false;
                continue;
              }
              // we use copyDir instead of moveDirWithContent here, because tmp directory may be located on other disk and
              // moveDirWithContent doesn't work for such case
              FileUtil.copyDir(tmpOutputDir, aptOutputDirectory);
              markDirtyRecursively(aptOutputDirectory, context, ANDROID_APT_COMPILER, true);
            }
            storage.update(module.getName(), newState);
          }
        }
        finally {
          if (tmpOutputDir != null) {
            FileUtil.delete(tmpOutputDir);
          }
        }
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, null, e, ANDROID_APT_COMPILER);
        success = false;
      }
    }
    if (!success) {
      return MyExitStatus.FAIL;
    }
    else if (didSomething) {
      return MyExitStatus.OK;
    }
    return MyExitStatus.NOTHING_CHANGED;
  }

  private static boolean clearDirectory(File dir, CompileContext context, String compilerName) throws IOException {
    if (!deleteAndMarkRecursively(dir, context, compilerName)) {
      return false;
    }

    if (!dir.mkdirs()) {
      context.processMessage(new CompilerMessage(compilerName, BuildMessage.Kind.ERROR,
                                                 AndroidJpsBundle.message("android.jps.cannot.create.directory", dir.getPath())));
      return false;
    }
    return true;
  }

  private static boolean clearDirectoryIfNotEmpty(@NotNull File dir, @NotNull CompileContext context, String compilerName)
    throws IOException {
    if (dir.isDirectory()) {
      final String[] list = dir.list();
      if (list != null && list.length > 0) {
        return clearDirectory(dir, context, compilerName);
      }
    }
    return true;
  }

  private static boolean deleteAndMarkRecursively(@NotNull File dir, @NotNull CompileContext context, @NotNull String compilerName)
    throws IOException {
    if (dir.exists()) {
      final List<File> filesToDelete = collectJavaFilesRecursively(dir);
      if (!FileUtil.delete(dir)) {
        context.processMessage(
          new CompilerMessage(compilerName, BuildMessage.Kind.ERROR, AndroidJpsBundle.message("android.jps.cannot.delete", dir.getPath())));
        return false;
      }

      for (File file : filesToDelete) {
        FSOperations.markDeleted(context, file);
      }
    }
    return true;
  }

  private static boolean markDirtyRecursively(@NotNull File dir,
                                              @NotNull final CompileContext context,
                                              @NotNull final String compilerName,
                                              final boolean javaFilesOnly) {
    final Ref<Boolean> success = Ref.create(true);

    FileUtil.processFilesRecursively(dir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.isFile() && (!javaFilesOnly || FileUtilRt.extensionEquals(file.getName(), "java"))) {
          try {
            FSOperations.markDirty(context, CompilationRound.CURRENT, file);
          }
          catch (IOException e) {
            AndroidJpsUtil.reportExceptionError(context, null, e, compilerName);
            success.set(false);
            return false;
          }
        }
        return true;
      }
    });
    return success.get();
  }

  @NotNull
  private static List<File> collectJavaFilesRecursively(@NotNull File dir) {
    final List<File> result = new ArrayList<File>();

    FileUtil.processFilesRecursively(dir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.isFile() && FileUtilRt.extensionEquals(file.getName(), "java")) {
          result.add(file);
        }
        return true;
      }
    });
    return result;
  }

  @NotNull
  private static Map<JpsModule, String> getDepLibPackages(@NotNull JpsModule module) throws IOException {
    final Map<JpsModule, String> result = new HashMap<JpsModule, String>();

    for (JpsAndroidModuleExtension depExtension : AndroidJpsUtil.getAllAndroidDependencies(module, true)) {
      final File depManifestFile = AndroidJpsUtil.getManifestFileForCompilationPath(depExtension);

      if (depManifestFile != null && depManifestFile.exists()) {
        final String packageName = AndroidJpsUtil.parsePackageNameFromManifestFile(depManifestFile);

        if (packageName != null) {
          result.put(depExtension.getModule(), packageName);
        }
      }
    }
    return result;
  }

  @NotNull
  private static Map<String, ResourceFileData> collectResources(@NotNull String[] resPaths,
                                                                @NotNull Map<String, ResourceFileData> resDataMap,
                                                                @NotNull TObjectLongHashMap<String> valueResFilesTimestamps,
                                                                @Nullable AndroidAptValidityState oldState)
    throws IOException {

    for (String resDirPath : resPaths) {
      final File[] resSubdirs = new File(resDirPath).listFiles();

      if (resSubdirs != null) {
        for (File resSubdir : resSubdirs) {
          final String resType = AndroidCommonUtils.getResourceTypeByDirName(resSubdir.getName());

          if (resType != null) {
            final File[] resFiles = resSubdir.listFiles();

            if (resFiles != null) {
              for (File resFile : resFiles) {
                collectResources(resFile, resType, resDataMap, valueResFilesTimestamps, oldState);
              }
            }
          }
        }
      }
    }
    return resDataMap;
  }

  private static void collectResources(@NotNull File resFile,
                                       @NotNull String resType,
                                       @NotNull Map<String, ResourceFileData> resDataMap,
                                       @NotNull TObjectLongHashMap<String> valueResFilesTimestamps,
                                       @Nullable AndroidAptValidityState oldState)
    throws IOException {
    final String resFilePath = FileUtil.toSystemIndependentName(resFile.getPath());
    final long resFileTimestamp = resFile.lastModified();

    if (ResourceFolderType.VALUES.getName().equals(resType) && FileUtilRt.extensionEquals(resFile.getName(), "xml")) {
      ResourceFileData dataToReuse = null;

      if (oldState != null) {
        final long oldTimestamp = oldState.getValueResourceFilesTimestamps().get(resFilePath);

        if (resFileTimestamp == oldTimestamp) {
          dataToReuse = oldState.getResources().get(resFilePath);
        }
      }

      if (dataToReuse != null) {
        resDataMap.put(resFilePath, dataToReuse);
      }
      else {
        final List<ResourceEntry> entries = AndroidBuildDataCache.getInstance().getParsedValueResourceFile(resFile);
        resDataMap.put(resFilePath, new ResourceFileData(entries, 0));
      }
      valueResFilesTimestamps.put(resFilePath, resFileTimestamp);
    }
    else {
      final ResourceType resTypeObj = ResourceType.getEnum(resType);
      final boolean idProvidingType =
        resTypeObj != null && ArrayUtil.find(AndroidCommonUtils.ID_PROVIDING_RESOURCE_TYPES, resTypeObj) >= 0;
      final ResourceFileData data =
        new ResourceFileData(Collections.<ResourceEntry>emptyList(), idProvidingType ? resFileTimestamp : 0);
      resDataMap.put(resFilePath, data);
    }
  }

  @NotNull
  private static List<ResourceEntry> collectManifestElements(@NotNull File manifestFile) throws IOException {
    final InputStream inputStream = new BufferedInputStream(new FileInputStream(manifestFile));
    try {
      final List<ResourceEntry> result = new ArrayList<ResourceEntry>();

      FormsParsing.parse(inputStream, new FormsParsing.IXMLBuilderAdapter() {
        String myLastName;

        @Override
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
          throws Exception {
          myLastName = null;
        }

        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type)
          throws Exception {
          if (value != null && NAME_ATTRIBUTE.equals(key)) {
            myLastName = value;
          }
        }

        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
          if (myLastName != null && PERMISSION_TAG.equals(name) || PERMISSION_GROUP_TAG.equals(name)) {
            assert myLastName != null;
            result.add(new ResourceEntry(name, myLastName, ""));
          }
        }
      });

      return result;
    }
    finally {
      inputStream.close();
    }
  }

  @Nullable
  private static String getDependencyFolder(@NotNull CompileContext context, @NotNull File sourceFile, @NotNull File genFolder) {
    final JavaSourceRootDescriptor descriptor = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context,
                                                                                                                          sourceFile);
    if (descriptor == null) {
      return null;
    }
    final File sourceRoot = descriptor.root;

    final File parent = FileUtilRt.getParentFile(sourceFile);
    if (parent == null) {
      return null;
    }

    if (FileUtil.filesEqual(parent, sourceRoot)) {
      return genFolder.getPath();
    }
    final String relativePath = FileUtil.getRelativePath(sourceRoot, parent);
    assert relativePath != null;
    return genFolder.getPath() + '/' + relativePath;
  }

  @Nullable
  private static Map<JpsModule, MyModuleData> computeModuleDatas(@NotNull Collection<JpsModule> modules, @NotNull CompileContext context)
    throws IOException {
    final Map<JpsModule, MyModuleData> moduleDataMap = new HashMap<JpsModule, MyModuleData>();

    boolean success = true;

    for (JpsModule module : modules) {
      final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
      if (extension == null) {
        continue;
      }

      final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
      if (platform == null) {
        success = false;
        continue;
      }

      final File manifestFile = AndroidJpsUtil.getManifestFileForCompilationPath(extension);
      if (manifestFile == null || !manifestFile.exists()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.errors.manifest.not.found", module.getName())));
        success = false;
        continue;
      }

      final String packageName = AndroidJpsUtil.parsePackageNameFromManifestFile(manifestFile);
      if (packageName == null || packageName.length() == 0) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
          .message("android.jps.errors.package.not.specified", module.getName())));
        success = false;
        continue;
      }

      if (!AndroidCommonUtils.contains2Identifiers(packageName)) {
        context
          .processMessage(new CompilerMessage(BUILDER_NAME, extension.isLibrary() ? BuildMessage.Kind.WARNING : BuildMessage.Kind.ERROR,
                                              AndroidJpsBundle
                                                .message("android.jps.errors.incorrect.package.name", module.getName())));
        success = false;
        continue;
      }

      moduleDataMap.put(module, new MyModuleData(platform, extension, manifestFile, packageName));
    }

    return success ? moduleDataMap : null;
  }

  @Nullable
  private static String computePackageForFile(@NotNull CompileContext context, @NotNull File file) throws IOException {
    final JavaSourceRootDescriptor descriptor = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file);
    if (descriptor == null) {
      return null;
    }

    final String relPath = FileUtil.getRelativePath(descriptor.root, FileUtilRt.getParentFile(file));
    if (relPath == null) {
      return null;
    }

    return FileUtil.toSystemIndependentName(relPath).replace('/', '.');
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }

  // support for lib<->lib and app<->lib circular dependencies
  // see IDEA-79737 for details
  private static boolean isLibraryWithBadCircularDependency(@NotNull JpsAndroidModuleExtension extension)
    throws IOException {
    if (!extension.isLibrary()) {
      return false;
    }
    final List<JpsAndroidModuleExtension> dependencies = AndroidJpsUtil.getAllAndroidDependencies(extension.getModule(), false);

    for (JpsAndroidModuleExtension depExtension : dependencies) {
      final List<JpsAndroidModuleExtension> depDependencies = AndroidJpsUtil.getAllAndroidDependencies(depExtension.getModule(), true);

      if (depDependencies.contains(extension) &&
          dependencies.contains(depExtension) &&
          (depExtension.getModule().getName().compareTo(extension.getModule().getName()) < 0 || !depExtension.isLibrary())) {
        return true;
      }
    }
    return false;
  }

  private static void addMessages(@NotNull CompileContext context,
                                  @NotNull Map<AndroidCompilerMessageKind, List<String>> messages,
                                  @NotNull String sourcePath,
                                  @NotNull String builderName) {
    for (Map.Entry<AndroidCompilerMessageKind, List<String>> entry : messages.entrySet()) {
      final AndroidCompilerMessageKind kind = entry.getKey();
      final BuildMessage.Kind buildMessageKind = AndroidJpsUtil.toBuildMessageKind(kind);

      if (buildMessageKind == null) {
        continue;
      }

      for (String message : entry.getValue()) {
        context.processMessage(new CompilerMessage(builderName, buildMessageKind, message, sourcePath));
      }
    }
  }

  @Nullable
  public static JpsModule findCircularDependencyOnLibraryWithSamePackage(@NotNull JpsAndroidModuleExtension extension,
                                                                         @NotNull Map<JpsModule, String> packageMap) {
    final String aPackage = packageMap.get(extension.getModule());
    if (aPackage == null || aPackage.length() == 0) {
      return null;
    }

    for (JpsAndroidModuleExtension depExtension : AndroidJpsUtil.getAllAndroidDependencies(extension.getModule(), true)) {
      if (aPackage.equals(packageMap.get(depExtension.getModule()))) {
        final List<JpsAndroidModuleExtension> depDependencies = AndroidJpsUtil.getAllAndroidDependencies(depExtension.getModule(), false);

        if (depDependencies.contains(extension)) {
          // circular dependency on library with the same package
          return depExtension.getModule();
        }
      }
    }
    return null;
  }

  private static boolean checkUnambiguousAndRecursiveArtifacts(CompileContext context, List<JpsArtifact> artifacts) {
    boolean success = true;

    for (JpsArtifact artifact : artifacts) {
      if (artifact.getArtifactType() instanceof AndroidApplicationArtifactType) {
        final List<JpsAndroidModuleExtension> facets = AndroidJpsUtil.getAllPackagedFacets(artifact);

        if (facets.size() > 1) {
          context.processMessage(new CompilerMessage(
            ANDROID_VALIDATOR, BuildMessage.Kind.ERROR, "Cannot build artifact '" + artifact.getName() +
                                                        "' because it contains more than one Android package"));
          success = false;
          continue;
        }
        final String artifactOutputPath = artifact.getOutputFilePath();

        if (artifactOutputPath != null && facets.size() > 0) {
          final JpsAndroidModuleExtension facet = facets.get(0);
          final String apkPath = AndroidFinalPackageElementBuilder.getApkPath(facet);

          if (FileUtil.pathsEqual(apkPath, artifactOutputPath)) {
            context.processMessage(new CompilerMessage(
              ANDROID_VALIDATOR, BuildMessage.Kind.ERROR,
              "Incorrect output path for artifact '" + artifact.getName() + "': " + FileUtil.toSystemDependentName(apkPath)));
            success = false;
          }
        }
      }
    }
    return success;
  }

  private static boolean checkArtifacts(@NotNull CompileContext context) {
    final List<JpsArtifact> artifacts = AndroidJpsUtil.getAndroidArtifactsToBuild(context);

    if (!checkUnambiguousAndRecursiveArtifacts(context, artifacts)) {
      return false;
    }

    final Set<JpsArtifact> debugArtifacts = new HashSet<JpsArtifact>();
    final Set<JpsArtifact> releaseArtifacts = new HashSet<JpsArtifact>();
    final Map<String, List<JpsArtifact>> moduleName2Artifact = new HashMap<String, List<JpsArtifact>>();

    for (JpsArtifact artifact : artifacts) {
      final JpsElement properties = artifact.getProperties();

      if (!(properties instanceof JpsAndroidApplicationArtifactProperties)) {
        continue;
      }

      final AndroidArtifactSigningMode mode = ((JpsAndroidApplicationArtifactProperties)properties).getSigningMode();

      if (mode == AndroidArtifactSigningMode.DEBUG || mode == AndroidArtifactSigningMode.DEBUG_WITH_CUSTOM_CERTIFICATE) {
        debugArtifacts.add(artifact);
      }
      else {
        releaseArtifacts.add(artifact);
      }
      final JpsAndroidModuleExtension facet = AndroidJpsUtil.getPackagedFacet(artifact);

      if (facet != null) {
        final String moduleName = facet.getModule().getName();
        List<JpsArtifact> list = moduleName2Artifact.get(moduleName);

        if (list == null) {
          list = new ArrayList<JpsArtifact>();
          moduleName2Artifact.put(moduleName, list);
        }
        list.add(artifact);
      }
    }
    boolean success = true;

    if (debugArtifacts.size() > 0 && releaseArtifacts.size() > 0) {
      final String message = "Cannot build debug and release Android artifacts in the same session\n" +
                             "Debug artifacts: " + artifactsToString(debugArtifacts) + "\n" +
                             "Release artifacts: " + artifactsToString(releaseArtifacts);
      context.processMessage(new CompilerMessage(ANDROID_VALIDATOR, BuildMessage.Kind.ERROR, message));
      success = false;
    }

    if (releaseArtifacts.size() > 0 &&
        AndroidJpsUtil.getRunConfigurationTypeId(context) != null) {
      final String message = "Cannot build release Android artifacts in the 'make before run' session\n" +
                             "Release artifacts: " + artifactsToString(releaseArtifacts);
      context.processMessage(new CompilerMessage(ANDROID_VALIDATOR, BuildMessage.Kind.ERROR, message));
      success = false;
    }

    for (Map.Entry<String, List<JpsArtifact>> entry : moduleName2Artifact.entrySet()) {
      final List<JpsArtifact> list = entry.getValue();
      final String moduleName = entry.getKey();

      if (list.size() > 1) {
        final JpsArtifact firstArtifact = list.get(0);
        final Object[] firstArtifactProGuardOptions = getProGuardOptions(firstArtifact);

        for (int i = 1; i < list.size(); i++) {
          final JpsArtifact artifact = list.get(i);
          if (!Arrays.equals(getProGuardOptions(artifact), firstArtifactProGuardOptions)) {
            context.processMessage(new CompilerMessage(
              ANDROID_VALIDATOR, BuildMessage.Kind.ERROR, "Artifacts related to the same module '" +
                                                          moduleName +
                                                          "' have different ProGuard options: " +
                                                          firstArtifact.getName() +
                                                          ", " +
                                                          artifact.getName()));
            success = false;
            break;
          }
        }
      }
    }

    return success;
  }

  @NotNull
  private static Object[] getProGuardOptions(@NotNull JpsArtifact artifact) {
    final JpsElement properties = artifact.getProperties();

    if (properties instanceof JpsAndroidApplicationArtifactProperties) {
      final JpsAndroidApplicationArtifactProperties p = (JpsAndroidApplicationArtifactProperties)properties;
      final boolean runProGuard = p.isRunProGuard();

      return runProGuard
             ? new Object[]{runProGuard, p.getProGuardCfgFiles()}
             : new Object[]{runProGuard};
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @NotNull
  private static String artifactsToString(Collection<JpsArtifact> artifacts) {
    final StringBuilder result = new StringBuilder();

    for (JpsArtifact artifact : artifacts) {
      if (result.length() > 0) {
        result.append(", ");
      }
      result.append(artifact.getName());
    }
    return result.toString();
  }

  private static class MyModuleData {
    private final AndroidPlatform myPlatform;
    private final JpsAndroidModuleExtension myAndroidExtension;
    private final File myManifestFileForCompiler;
    private final String myPackage;

    private MyModuleData(@NotNull AndroidPlatform platform,
                         @NotNull JpsAndroidModuleExtension extension,
                         @NotNull File manifestFileForCompiler,
                         @NotNull String aPackage) {
      myPlatform = platform;
      myAndroidExtension = extension;
      myManifestFileForCompiler = manifestFileForCompiler;
      myPackage = aPackage;
    }

    @NotNull
    public AndroidPlatform getPlatform() {
      return myPlatform;
    }

    @NotNull
    public JpsAndroidModuleExtension getAndroidExtension() {
      return myAndroidExtension;
    }

    @NotNull
    public File getManifestFileForCompiler() {
      return myManifestFileForCompiler;
    }

    @NotNull
    public String getPackage() {
      return myPackage;
    }
  }

  private static enum MyExitStatus {
    OK, FAIL, NOTHING_CHANGED
  }
}
