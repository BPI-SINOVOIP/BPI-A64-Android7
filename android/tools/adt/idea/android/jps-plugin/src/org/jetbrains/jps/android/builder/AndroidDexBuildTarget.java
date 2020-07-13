package org.jetbrains.jps.android.builder;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.*;
import org.jetbrains.jps.android.model.JpsAndroidDexCompilerConfiguration;
import org.jetbrains.jps.android.model.JpsAndroidExtensionService;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDexBuildTarget extends AndroidBuildTarget {
  public AndroidDexBuildTarget(@NotNull JpsModule module) {
    super(MyTargetType.INSTANCE, module);
  }

  @Override
  public void writeConfiguration(ProjectDescriptor pd, PrintWriter out) {
    super.writeConfiguration(pd, out);

    final JpsAndroidDexCompilerConfiguration c = JpsAndroidExtensionService.
      getInstance().getDexCompilerConfiguration(getModule().getProject());

    if (c != null) {
      out.println(c.getVmOptions());
      out.println(c.getMaxHeapSize());
      out.println(c.isOptimize());
      out.println(c.isForceJumbo());
      out.println(c.isCoreLibrary());
      out.println(c.getProguardVmOptions());
    }
  }

  @NotNull
  @Override
  protected List<BuildRootDescriptor> doComputeRootDescriptors(JpsModel model,
                                                               ModuleExcludeIndex index,
                                                               IgnoredFileIndex ignoredFileIndex,
                                                               BuildDataPaths dataPaths) {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);
    assert extension != null;

    if (extension.isLibrary()) {
      return Collections.emptyList();
    }
    final Map<String, String> libPackage2ModuleName = new THashMap<String, String>(FileUtil.PATH_HASHING_STRATEGY);
    final Set<String> appClassesDirs = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    final Set<String> javaClassesDirs = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    final Set<String> libClassesDirs = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);

    final File moduleClassesDir = new ModuleBuildTarget(
      myModule, JavaModuleBuildTargetType.PRODUCTION).getOutputDir();

    if (moduleClassesDir != null) {
      appClassesDirs.add(moduleClassesDir.getPath());
    }

    AndroidJpsUtil.processClasspath(dataPaths, myModule, new AndroidDependencyProcessor() {
      @Override
      public void processAndroidLibraryPackage(@NotNull File file, @NotNull JpsModule depModule) {
        libPackage2ModuleName.put(file.getPath(), depModule.getName());
      }

      @Override
      public void processAndroidLibraryOutputDirectory(@NotNull File dir) {
        libClassesDirs.add(dir.getPath());
      }

      @Override
      public void processJavaModuleOutputDirectory(@NotNull File dir) {
        javaClassesDirs.add(dir.getPath());
      }

      @Override
      public boolean isToProcess(@NotNull AndroidDependencyType type) {
        return type == AndroidDependencyType.ANDROID_LIBRARY_PACKAGE ||
               type == AndroidDependencyType.ANDROID_LIBRARY_OUTPUT_DIRECTORY ||
               type == AndroidDependencyType.JAVA_MODULE_OUTPUT_DIR;
      }
    }, false, false);

    if (extension.isPackTestCode()) {
      final File testModuleClassesDir = new ModuleBuildTarget(
        myModule, JavaModuleBuildTargetType.TEST).getOutputDir();

      if (testModuleClassesDir != null) {
        appClassesDirs.add(testModuleClassesDir.getPath());
      }
    }
    final List<BuildRootDescriptor> result = new ArrayList<BuildRootDescriptor>();

    for (String classesDir : appClassesDirs) {
      result.add(new MyClassesDirBuildRootDescriptor(this, new File(classesDir), ClassesDirType.ANDROID_APP));
    }

    for (String classesDir : libClassesDirs) {
      result.add(new MyClassesDirBuildRootDescriptor(this, new File(classesDir), ClassesDirType.ANDROID_LIB));
    }

    for (String classesDir : javaClassesDirs) {
      result.add(new MyClassesDirBuildRootDescriptor(this, new File(classesDir), ClassesDirType.JAVA));
    }
    final File preDexOutputDir = AndroidPreDexBuildTarget.getOutputDir(dataPaths);

    for (Map.Entry<String, String> entry : libPackage2ModuleName.entrySet()) {
      final String libPackage = entry.getKey();
      final String moduleName = entry.getValue();
      final File libPackageJarFile = new File(libPackage);
      assert AndroidPreDexBuilder.canBePreDexed(libPackageJarFile);
      result.add(new MyJarBuildRootDescriptor(this, libPackageJarFile, true, false));
      result.add(new MyJarBuildRootDescriptor(this, new File(
        new File(preDexOutputDir, moduleName), libPackageJarFile.getName()), true, true));
    }
    final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(myModule, null, null);

    if (platform != null) {
      for (String jarOrLibDir : AndroidJpsUtil.getExternalLibraries(dataPaths, myModule, platform, false, false, true)) {
        File file = new File(jarOrLibDir);
        File preDexedFile = file;

        if (AndroidPreDexBuilder.canBePreDexed(file)) {
          final String preDexedFileName = AndroidPreDexBuilder.getOutputFileNameForExternalJar(file);

          if (preDexedFileName != null) {
            preDexedFile = new File(preDexOutputDir, preDexedFileName);
          }
        }
        result.add(new MyJarBuildRootDescriptor(this, file, false, false));
        result.add(new MyJarBuildRootDescriptor(this, preDexedFile, false, true));
      }
    }
    for (String path : AndroidJpsUtil.getProvidedLibraries(dataPaths, myModule)) {
      result.add(new MyProvidedJarBuildRootDescriptor(this, new File(path)));
    }
    return result;
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.singletonList(getOutputFile(context));
  }

  @NotNull
  public File getOutputFile(CompileContext context) {
    return getOutputFile(context.getProjectDescriptor().dataManager.getDataPaths(), myModule);
  }

  @NotNull
  public static File getOutputFile(@NotNull BuildDataPaths dataPaths, @NotNull JpsModule module) {
    final File dir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(dataPaths, module);
    return new File(dir, AndroidCommonUtils.CLASSES_FILE_NAME);
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry registry, TargetOutputIndex outputIndex) {
    final List<BuildTarget<?>> result = new ArrayList<BuildTarget<?>>(
      super.computeDependencies(registry, outputIndex));
    result.add(new AndroidAarDepsBuildTarget(myModule));
    result.add(new AndroidPreDexBuildTarget(myModule.getProject()));
    return result;
  }

  public static class MyTargetType extends AndroidBuildTargetType<AndroidDexBuildTarget> {
    public static final MyTargetType INSTANCE = new MyTargetType();

    private MyTargetType() {
      super(AndroidCommonUtils.DEX_BUILD_TARGET_TYPE_ID, "DEX");
    }

    @Override
    public AndroidDexBuildTarget createBuildTarget(@NotNull JpsAndroidModuleExtension extension) {
      return !extension.isLibrary() ? new AndroidDexBuildTarget(extension.getModule()) : null;
    }
  }

  public enum ClassesDirType {
    ANDROID_APP, ANDROID_LIB, JAVA
  }

  public static class MyClassesDirBuildRootDescriptor extends AndroidClassesDirBuildRootDescriptor {
    private final ClassesDirType myClassesDirType;

    public MyClassesDirBuildRootDescriptor(@NotNull BuildTarget target,
                                           @NotNull File root,
                                           @NotNull ClassesDirType classesDirType) {
      super(target, root);
      myClassesDirType = classesDirType;
    }

    @NotNull
    public ClassesDirType getClassesDirType() {
      return myClassesDirType;
    }
  }

  public static class MyProvidedJarBuildRootDescriptor extends AndroidFileBasedBuildRootDescriptor {
    public MyProvidedJarBuildRootDescriptor(@NotNull BuildTarget target, @NotNull File file) {
      super(target, file);
    }
  }

  public static class MyJarBuildRootDescriptor extends AndroidFileBasedBuildRootDescriptor {
    private final boolean myLibPackage;
    private final boolean myPreDexed;

    public MyJarBuildRootDescriptor(@NotNull BuildTarget target, @NotNull File file, boolean libPackage, boolean preDexed) {
      super(target, file);
      myLibPackage = libPackage;
      myPreDexed = preDexed;
    }

    public boolean isLibPackage() {
      return myLibPackage;
    }

    public boolean isPreDexed() {
      return myPreDexed;
    }
  }
}
