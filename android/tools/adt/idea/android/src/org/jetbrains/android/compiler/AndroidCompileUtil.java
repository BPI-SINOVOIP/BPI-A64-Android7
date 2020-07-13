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
package org.jetbrains.android.compiler;

import com.android.tools.idea.fileTypes.AndroidRenderscriptFileType;
import com.intellij.CommonBundle;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.impl.CompileContextImpl;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.artifact.*;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import com.android.tools.idea.lang.aidl.AidlFileType;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;

/**
 * @author yole
 */
public class AndroidCompileUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidCompileUtil");

  private static final Key<Boolean> RELEASE_BUILD_KEY = new Key<Boolean>(AndroidCommonUtils.RELEASE_BUILD_OPTION);
  @NonNls private static final String RESOURCES_CACHE_DIR_NAME = "res-cache";
  @NonNls private static final String GEN_MODULE_PREFIX = "~generated_";

  @NonNls public static final String OLD_PROGUARD_CFG_FILE_NAME = "proguard.cfg";
  public static final String UNSIGNED_SUFFIX = ".unsigned";
  public static Key<String> PROGUARD_CFG_PATHS_KEY = Key.create(AndroidCommonUtils.PROGUARD_CFG_PATHS_OPTION);

  private AndroidCompileUtil() {
  }

  @NotNull
  public static <T> Map<CompilerMessageCategory, T> toCompilerMessageCategoryKeys(@NotNull Map<AndroidCompilerMessageKind, T> map) {
    final Map<CompilerMessageCategory, T> result = new HashMap<CompilerMessageCategory, T>();

    for (Map.Entry<AndroidCompilerMessageKind, T> entry : map.entrySet()) {
      final AndroidCompilerMessageKind key = entry.getKey();
      final T value = entry.getValue();

      switch (key) {
        case ERROR:
          result.put(CompilerMessageCategory.ERROR, value);
          break;
        case INFORMATION:
          result.put(CompilerMessageCategory.INFORMATION, value);
          break;
        case WARNING:
          result.put(CompilerMessageCategory.WARNING, value);
          break;
      }
    }
    return result;
  }

  @Nullable
  public static Pair<VirtualFile, Boolean> getDefaultProguardConfigFile(@NotNull AndroidFacet facet) {
    VirtualFile root = AndroidRootUtil.getMainContentRoot(facet);
    if (root == null) {
      return null;
    }
    final VirtualFile proguardCfg = root.findChild(AndroidCommonUtils.PROGUARD_CFG_FILE_NAME);
    if (proguardCfg != null) {
      return new Pair<VirtualFile, Boolean>(proguardCfg, true);
    }

    final VirtualFile oldProguardCfg = root.findChild(OLD_PROGUARD_CFG_FILE_NAME);
    if (oldProguardCfg != null) {
      return new Pair<VirtualFile, Boolean>(oldProguardCfg, false);
    }
    return null;
  }

  public static void addMessages(final CompileContext context, final Map<CompilerMessageCategory, List<String>> messages, @Nullable Module module) {
    addMessages(context, messages, null, module);
  }

  static void addMessages(final CompileContext context,
                          final Map<CompilerMessageCategory, List<String>> messages,
                          @Nullable final Map<VirtualFile, VirtualFile> presentableFilesMap,
                          @Nullable final Module module) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (context.getProject().isDisposed()) return;
        for (CompilerMessageCategory category : messages.keySet()) {
          List<String> messageList = messages.get(category);
          for (String message : messageList) {
            String url = null;
            int line = -1;
            Matcher matcher = AndroidCommonUtils.COMPILER_MESSAGE_PATTERN.matcher(message);
            if (matcher.matches()) {
              String fileName = matcher.group(1);
              if (new File(fileName).exists()) {
                url = getPresentableFile("file://" + fileName, presentableFilesMap);
                line = Integer.parseInt(matcher.group(2));
              }
            }
            context.addMessage(category, (module != null ? '[' + module.getName() + "] " : "") + message, url, line, -1);
          }
        }
      }
    });
  }

  @NotNull
  private static String getPresentableFile(@NotNull String url, @Nullable Map<VirtualFile, VirtualFile> presentableFilesMap) {
    final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file == null) {
      return url;
    }

    if (presentableFilesMap == null) {
      return url;
    }

    for (Map.Entry<VirtualFile, VirtualFile> entry : presentableFilesMap.entrySet()) {
      if (Comparing.equal(file, entry.getValue())) {
        return entry.getKey().getUrl();
      }
    }
    return url;
  }

  private static void collectChildrenRecursively(@NotNull VirtualFile root,
                                                 @NotNull VirtualFile anchor,
                                                 @NotNull Collection<VirtualFile> result) {
    if (Comparing.equal(root, anchor)) {
      return;
    }

    VirtualFile parent = anchor.getParent();
    if (parent == null) {
      return;
    }
    for (VirtualFile child : parent.getChildren()) {
      if (!Comparing.equal(child, anchor)) {
        result.add(child);
      }
    }
    if (!Comparing.equal(parent, root)) {
      collectChildrenRecursively(root, parent, result);
    }
  }

  private static void unexcludeRootIfNecessary(@NotNull VirtualFile root,
                                               @NotNull ModifiableRootModel model,
                                               @NotNull Ref<Boolean> modelChangedFlag) {
    Set<VirtualFile> excludedRoots = new HashSet<VirtualFile>(Arrays.asList(model.getExcludeRoots()));
    VirtualFile excludedRoot = root;
    while (excludedRoot != null && !excludedRoots.contains(excludedRoot)) {
      excludedRoot = excludedRoot.getParent();
    }
    if (excludedRoot == null) {
      return;
    }
    Set<VirtualFile> rootsToExclude = new HashSet<VirtualFile>();
    collectChildrenRecursively(excludedRoot, root, rootsToExclude);
    ContentEntry contentEntry = findContentEntryForRoot(model, excludedRoot);
    if (contentEntry != null) {
      if (contentEntry.removeExcludeFolder(excludedRoot.getUrl())) {
        modelChangedFlag.set(true);
      }
      for (VirtualFile rootToExclude : rootsToExclude) {
        if (!excludedRoots.contains(rootToExclude)) {
          contentEntry.addExcludeFolder(rootToExclude);
          modelChangedFlag.set(true);
        }
      }
    }
  }

  @NotNull
  private static String getGenModuleName(@NotNull Module module) {
    return GEN_MODULE_PREFIX + module.getName();
  }

  @Nullable
  public static VirtualFile createSourceRootIfNotExist(@NotNull final String path,
                                                       @NotNull final ModifiableRootModel model,
                                                       @NotNull Ref<Boolean> modelChangedFlag) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final File rootFile = new File(path);
    final boolean created;
    if (!rootFile.exists()) {
      if (!rootFile.mkdirs()) return null;
      created = true;
    }
    else {
      created = false;
    }
    final Module module = model.getModule();
    final Project project = module.getProject();

    if (project.isDisposed() || module.isDisposed()) {
      return null;
    }

    final VirtualFile root;
    if (created) {
      root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(rootFile);
    }
    else {
      root = LocalFileSystem.getInstance().findFileByIoFile(rootFile);
    }
    if (root != null) {
      unexcludeRootIfNecessary(root, model, modelChangedFlag);

      boolean markedAsSource = false;

      for (VirtualFile existingRoot : model.getSourceRoots()) {
        if (Comparing.equal(existingRoot, root)) {
          markedAsSource = true;
        }
      }

      if (markedAsSource) {
        markRootAsGenerated(model, root, modelChangedFlag);
      }
      else {
        addSourceRoot(model, root);
        modelChangedFlag.set(true);
      }
    }
    return root;
  }

  private static void markRootAsGenerated(ModifiableRootModel model, VirtualFile root, Ref<Boolean> modelChangedFlag) {
    final ContentEntry contentEntry = findContentEntryForRoot(model, root);

    if (contentEntry == null) {
      return;
    }
    for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
      if (root.equals(sourceFolder.getFile())) {
        final JavaSourceRootProperties props = sourceFolder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
        if (props != null) {
          props.setForGeneratedSources(true);
          modelChangedFlag.set(true);
          break;
        }
      }
    }
  }

  private static void excludeFromCompilation(@NotNull Project project, @NotNull VirtualFile sourceRoot, @NotNull String aPackage) {
    final String buildConfigPath = sourceRoot.getPath() + '/' + aPackage.replace('.', '/') + "/BuildConfig.java";
    String url = VfsUtilCore.pathToUrl(buildConfigPath);
    final ExcludesConfiguration configuration =
      CompilerConfiguration.getInstance(project).getExcludedEntriesConfiguration();

    for (ExcludeEntryDescription description : configuration.getExcludeEntryDescriptions()) {
      if (Comparing.equal(description.getUrl(), url)) {
        return;
      }
    }
    configuration.addExcludeEntryDescription(new ExcludeEntryDescription(url, true, false, project));
  }

  private static void excludeFromCompilation(@NotNull Project project, @NotNull VirtualFile dir) {
    final ExcludesConfiguration configuration =
      CompilerConfiguration.getInstance(project).getExcludedEntriesConfiguration();

    for (ExcludeEntryDescription description : configuration.getExcludeEntryDescriptions()) {
      if (Comparing.equal(description.getVirtualFile(), dir)) {
        return;
      }
    }
    configuration.addExcludeEntryDescription(new ExcludeEntryDescription(dir, true, false, project));
  }

  private static void removeGenModule(@NotNull final ModifiableRootModel model, @NotNull Ref<Boolean> modelChangedFlag) {
    final String genModuleName = getGenModuleName(model.getModule());
    final Project project = model.getProject();
    final ModuleManager moduleManager = ModuleManager.getInstance(project);

    final Module genModule = moduleManager.findModuleByName(genModuleName);
    if (genModule == null) {
      return;
    }

    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry &&
          genModuleName.equals(((ModuleOrderEntry)entry).getModuleName())) {
        model.removeOrderEntry(entry);
        modelChangedFlag.set(true);
      }
    }
    final VirtualFile moduleFile = genModule.getModuleFile();
    moduleManager.disposeModule(genModule);

    if (moduleFile != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                moduleFile.delete(project);
              }
              catch (IOException e) {
                LOG.error(e);
              }
            }
          });
        }
      });
    }
  }

  @Nullable
  public static SourceFolder addSourceRoot(final ModifiableRootModel model, @NotNull final VirtualFile root) {
    ContentEntry contentEntry = findContentEntryForRoot(model, root);

    if (contentEntry == null) {
      final Project project = model.getProject();
      final String message = "Cannot mark directory '" + FileUtil.toSystemDependentName(root.getPath()) +
                             "' as source root, because it is not located under content root of module '" +
                             model.getModule().getName() + "'\n<a href='fix'>Open Project Structure</a>";
      final Notification notification = new Notification(
        AndroidBundle.message("android.autogeneration.notification.group"),
        "Autogeneration Error", message, NotificationType.ERROR,
        new NotificationListener.Adapter() {
          @Override
          protected void hyperlinkActivated(@NotNull Notification notification,
                                            @NotNull HyperlinkEvent e) {
            notification.expire();
            final ProjectStructureConfigurable configurable =
              ProjectStructureConfigurable.getInstance(project);

            ShowSettingsUtil.getInstance()
              .editConfigurable(project, configurable, new Runnable() {
                @Override
                public void run() {
                  final Module module = model.getModule();
                  final AndroidFacet facet = AndroidFacet.getInstance(module);

                  if (facet != null) {
                    configurable.select(facet, true);
                  }
                }
              });
          }
        });
      Notifications.Bus.notify(notification, project);
      LOG.debug(message);
      return null;
    }
    else {
      return contentEntry.addSourceFolder(root, JavaSourceRootType.SOURCE,
                                          JpsJavaExtensionService.getInstance().createSourceRootProperties("", true));
    }
  }

  @Nullable
  public static ContentEntry findContentEntryForRoot(@NotNull ModifiableRootModel model, @NotNull VirtualFile root) {
    ContentEntry contentEntry = null;
    for (ContentEntry candidate : model.getContentEntries()) {
      VirtualFile contentRoot = candidate.getFile();
      if (contentRoot != null && VfsUtilCore.isAncestor(contentRoot, root, false)) {
        contentEntry = candidate;
      }
    }
    return contentEntry;
  }

  public static void generate(final Module module, final AndroidAutogeneratorMode mode) {
    final AndroidFacet facet = AndroidFacet.getInstance(module);

    if (facet != null) {
      facet.scheduleSourceRegenerating(mode);
    }
  }

  public static boolean doGenerate(AndroidFacet facet, final AndroidAutogeneratorMode mode) {
    assert !ApplicationManager.getApplication().isDispatchThread();
    final CompileContext[] contextWrapper = new CompileContext[1];
    final Module module = facet.getModule();
    final Project project = module.getProject();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) return;
        CompilerTask task = new CompilerTask(project, "Android auto-generation", true, false, true, true);
        CompileScope scope = new ModuleCompileScope(module, false);
        contextWrapper[0] = new CompileContextImpl(project, task, scope, false, false);
      }
    });
    CompileContext context = contextWrapper[0];
    if (context == null) {
      return false;
    }
    generate(facet, mode, context, false);
    return context.getMessages(CompilerMessageCategory.ERROR).length == 0;
  }

  public static boolean isModuleAffected(CompileContext context, Module module) {
    return ArrayUtil.find(context.getCompileScope().getAffectedModules(), module) >= 0;
  }

  public static void generate(AndroidFacet facet,
                              AndroidAutogeneratorMode mode,
                              final CompileContext context,
                              boolean force) {
    if (context == null) {
      return;
    }
    AndroidAutogenerator.run(mode, facet, context, force);
  }

  // must be invoked in a read action!

  public static void removeDuplicatingClasses(final Module module, @NotNull final String packageName, @NotNull String className,
                                              @Nullable File classFile, String sourceRootPath) {
    if (sourceRootPath == null) {
      return;
    }
    VirtualFile sourceRoot = LocalFileSystem.getInstance().findFileByPath(sourceRootPath);
    if (sourceRoot == null) {
      return;
    }
    final Project project = module.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final String interfaceQualifiedName = packageName + '.' + className;
    PsiClass[] classes = facade.findClasses(interfaceQualifiedName, GlobalSearchScope.moduleScope(module));
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiClass c : classes) {
      PsiFile psiFile = c.getContainingFile();
      if (className.equals(FileUtil.getNameWithoutExtension(psiFile.getName()))) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null && Comparing.equal(projectFileIndex.getSourceRootForFile(virtualFile), sourceRoot)) {
          final String path = virtualFile.getPath();
          final File f = new File(path);

          if (!FileUtil.filesEqual(f, classFile) && f.exists()) {
            if (f.delete()) {
              virtualFile.refresh(true, false);
            }
            else {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  Messages.showErrorDialog(project, "Can't delete file " + path, CommonBundle.getErrorTitle());
                }
              }, project.getDisposed());
            }
          }
        }
      }
    }
  }

  @Nullable
  public static String findResourcesCacheDirectory(@NotNull Module module, boolean createIfNotFound, @Nullable CompileContext context) {
    final Project project = module.getProject();

    final CompilerProjectExtension extension = CompilerProjectExtension.getInstance(project);
    if (extension == null) {
      if (context != null) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot get compiler settings for project " + project.getName(), null, -1, -1);
      }
      return null;
    }

    final String projectOutputDirUrl = extension.getCompilerOutputUrl();
    if (projectOutputDirUrl == null) {
      if (context != null) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot find output directory for project " + project.getName(), null, -1, -1);
      }
      return null;
    }

    final String pngCacheDirPath = VfsUtil.urlToPath(projectOutputDirUrl) + '/' + RESOURCES_CACHE_DIR_NAME + '/' + module.getName();
    final String pngCacheDirOsPath = FileUtil.toSystemDependentName(pngCacheDirPath);

    final File pngCacheDir = new File(pngCacheDirOsPath);
    if (pngCacheDir.exists()) {
      if (pngCacheDir.isDirectory()) {
        return pngCacheDirOsPath;
      }
      else {
        if (context != null) {
          context.addMessage(CompilerMessageCategory.ERROR, "Cannot create directory " + pngCacheDirOsPath + " because file already exists",
                             null, -1, -1);
        }
        return null;
      }
    }

    if (!createIfNotFound) {
      return null;
    }

    if (!pngCacheDir.mkdirs()) {
      if (context != null) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot create directory " + pngCacheDirOsPath, null, -1, -1);
      }
      return null;
    }

    return pngCacheDirOsPath;
  }

  public static boolean isFullBuild(@NotNull CompileContext context) {
    return isFullBuild(context.getCompileScope());
  }

  public static boolean isFullBuild(@NotNull CompileScope scope) {
    final RunConfiguration c = CompileStepBeforeRun.getRunConfiguration(scope);
    return c == null || !AndroidCommonUtils.isTestConfiguration(c.getType().getId());
  }

  public static boolean isReleaseBuild(@NotNull CompileContext context) {
    final Boolean value = context.getCompileScope().getUserData(RELEASE_BUILD_KEY);
    if (value != null && value.booleanValue()) {
      return true;
    }
    final Project project = context.getProject();
    final Set<Artifact> artifacts = ArtifactCompileScope.getArtifactsToBuild(project, context.getCompileScope(), false);

    if (artifacts != null) {
      for (Artifact artifact : artifacts) {
        final ArtifactProperties<?> properties = artifact.getProperties(AndroidArtifactPropertiesProvider.getInstance());
        if (properties instanceof AndroidApplicationArtifactProperties) {
          final AndroidArtifactSigningMode signingMode = ((AndroidApplicationArtifactProperties)properties).getSigningMode();

          if (signingMode != AndroidArtifactSigningMode.DEBUG &&
              signingMode != AndroidArtifactSigningMode.DEBUG_WITH_CUSTOM_CERTIFICATE) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static void setReleaseBuild(@NotNull CompileScope compileScope) {
    compileScope.putUserData(RELEASE_BUILD_KEY, Boolean.TRUE);
  }

  public static boolean createGenModulesAndSourceRoots(@NotNull AndroidFacet facet, @NotNull ModifiableRootModel model) {
    if (facet.requiresAndroidModel() || !facet.getProperties().ENABLE_SOURCES_AUTOGENERATION) {
      return false;
    }
    final Module module = facet.getModule();
    final GlobalSearchScope moduleScope = facet.getModule().getModuleScope();
    final Ref<Boolean> modelChangedFlag = Ref.create(false);

    if (facet.isLibraryProject()) {
      removeGenModule(model, modelChangedFlag);
    }
    final Set<String> genRootsToCreate = new HashSet<String>();
    final Set<String> genRootsToInit = new HashSet<String>();

    final String buildConfigGenRootPath = AndroidRootUtil.getBuildconfigGenSourceRootPath(facet);

    if (buildConfigGenRootPath != null) {
      genRootsToCreate.add(buildConfigGenRootPath);
    }
    final String renderscriptGenRootPath = AndroidRootUtil.getRenderscriptGenSourceRootPath(facet);

    if (renderscriptGenRootPath != null) {
      final boolean createIfNotExist = FileTypeIndex.getFiles(AndroidRenderscriptFileType.INSTANCE, moduleScope).size() > 0;
      (createIfNotExist ? genRootsToCreate : genRootsToInit).add(renderscriptGenRootPath);
    }

    final String aptGenRootPath = AndroidRootUtil.getAptGenSourceRootPath(facet);

    if (aptGenRootPath != null) {
      genRootsToCreate.add(aptGenRootPath);
    }
    final String aidlGenRootPath = AndroidRootUtil.getAidlGenSourceRootPath(facet);

    if (aidlGenRootPath != null) {
      final boolean createIfNotExist = FileTypeIndex.getFiles(AidlFileType.INSTANCE, moduleScope).size() > 0;
      (createIfNotExist ? genRootsToCreate : genRootsToInit).add(aidlGenRootPath);
    }
    genRootsToInit.addAll(genRootsToCreate);

    for (String genRootPath : genRootsToInit) {
      initializeGenSourceRoot(model, genRootPath, genRootsToCreate.contains(genRootPath), true, modelChangedFlag);
    }
    return modelChangedFlag.get();
  }

  private static void excludeAllBuildConfigsFromCompilation(AndroidFacet facet, VirtualFile sourceRoot) {
    final Module module = facet.getModule();
    final Project project = module.getProject();
    final Set<String> packages = new HashSet<String>();

    final Manifest manifest = facet.getManifest();
    final String aPackage = manifest != null ? manifest.getPackage().getStringValue() : null;

    if (aPackage != null) {
      packages.add(aPackage);
    }
    packages.addAll(AndroidUtils.getDepLibsPackages(module));

    for (String p : packages) {
      excludeFromCompilation(project, sourceRoot, p);
    }
  }

  private static void includeAaptGenSourceRootToCompilation(AndroidFacet facet) {
    final Project project = facet.getModule().getProject();
    final ExcludesConfiguration configuration =
      ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(project)).getExcludedEntriesConfiguration();
    final ExcludeEntryDescription[] descriptions = configuration.getExcludeEntryDescriptions();

    configuration.removeAllExcludeEntryDescriptions();

    for (ExcludeEntryDescription description : descriptions) {
      final VirtualFile vFile = description.getVirtualFile();
      if (!Comparing.equal(vFile, AndroidRootUtil.getAaptGenDir(facet))) {
        configuration.addExcludeEntryDescription(description);
      }
    }
  }

  @Nullable
  private static VirtualFile initializeGenSourceRoot(@NotNull ModifiableRootModel model,
                                                     @Nullable String sourceRootPath,
                                                     boolean createIfNotExist,
                                                     boolean excludeInNonExternalMode,
                                                     @NotNull Ref<Boolean> modelChangedFlag) {
    if (sourceRootPath == null) {
      return null;
    }
    VirtualFile sourceRoot = null;

    if (createIfNotExist) {
      final VirtualFile root = createSourceRootIfNotExist(sourceRootPath, model, modelChangedFlag);
      if (root != null) {
        sourceRoot = root;
      }
    }
    if (sourceRoot == null) {
      sourceRoot = LocalFileSystem.getInstance().findFileByPath(sourceRootPath);
    }
    if (sourceRoot != null && excludeInNonExternalMode) {
      final Module module = model.getModule();
      final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(module.getProject());
    }
    return sourceRoot;
  }

  @NotNull
  public static String[] toOsPaths(@NotNull VirtualFile[] classFilesDirs) {
    final String[] classFilesDirOsPaths = new String[classFilesDirs.length];

    for (int i = 0; i < classFilesDirs.length; i++) {
      classFilesDirOsPaths[i] = FileUtil.toSystemDependentName(classFilesDirs[i].getPath());
    }
    return classFilesDirOsPaths;
  }

  // can't be invoked from dispatch thread
  @NotNull
  public static Map<CompilerMessageCategory, List<String>> execute(String... argv) throws IOException {
    assert !ApplicationManager.getApplication().isDispatchThread();
    final Map<AndroidCompilerMessageKind, List<String>> messages = AndroidExecutionUtil.doExecute(argv);
    return toCompilerMessageCategoryKeys(messages);
  }

  public static String getApkName(Module module) {
    return module.getName() + ".apk";
  }

  @Nullable
  public static String getOutputPackage(@NotNull Module module) {
    VirtualFile compilerOutput = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
    if (compilerOutput == null) return null;
    return new File(compilerOutput.getPath(), getApkName(module)).getPath();
  }

  public static boolean isExcludedFromCompilation(@NotNull File file, @Nullable Project project) {
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    return vFile != null && isExcludedFromCompilation(vFile, project);
  }

  public static boolean isExcludedFromCompilation(VirtualFile child, @Nullable Project project) {
    final CompilerManager compilerManager = project != null ? CompilerManager.getInstance(project) : null;

    if (compilerManager == null) {
      return false;
    }

    if (!compilerManager.isExcludedFromCompilation(child)) {
      return false;
    }

    final Module module = ModuleUtil.findModuleForFile(child, project);
    if (module == null) {
      return true;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || !facet.isLibraryProject()) {
      return true;
    }

    final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
    if (platform == null) {
      return true;
    }

    // we exclude sources of library modules automatically for tools r7 or previous
    return platform.getSdkData().getPlatformToolsRevision() > 7;
  }

  @Nullable
  public static ProguardRunningOptions getProguardConfigFilePathIfShouldRun(@NotNull AndroidFacet facet, CompileContext context) {
    // wizard
    String pathsStr = context.getCompileScope().getUserData(PROGUARD_CFG_PATHS_KEY);
    if (pathsStr != null) {
      final String[] paths = pathsStr.split(File.pathSeparator);

      if (paths.length > 0) {
        return new ProguardRunningOptions(Arrays.asList(paths));
      }
    }
    final AndroidPlatform platform = AndroidPlatform.getInstance(facet.getModule());
    final String sdkHomePath = platform != null ? FileUtil.toCanonicalPath(platform.getSdkData().getPath()) : null;

    // artifact
    final Project project = context.getProject();
    final Set<Artifact> artifacts = ArtifactCompileScope.getArtifactsToBuild(project, context.getCompileScope(), false);

    for (Artifact artifact : artifacts) {
      if (artifact.getArtifactType() instanceof AndroidApplicationArtifactType &&
          facet.equals(AndroidArtifactUtil.getPackagedFacet(project, artifact))) {
        final ArtifactProperties<?> properties = artifact.getProperties(AndroidArtifactPropertiesProvider.getInstance());

        if (properties instanceof AndroidApplicationArtifactProperties) {
          final AndroidApplicationArtifactProperties p = (AndroidApplicationArtifactProperties)properties;

          if (p.isRunProGuard()) {
            final List<String> paths = AndroidUtils.urlsToOsPaths(p.getProGuardCfgFiles(), sdkHomePath);
            return new ProguardRunningOptions(paths);
          }
        }
      }
    }

    // facet
    final AndroidFacetConfiguration configuration = facet.getConfiguration();
    final JpsAndroidModuleProperties properties = configuration.getState();
    if (properties != null && properties.RUN_PROGUARD) {
      final List<String> urls = properties.myProGuardCfgFiles;
      final List<String> paths = AndroidUtils.urlsToOsPaths(urls, sdkHomePath);
      return new ProguardRunningOptions(paths);
    }
    return null;
  }

  @Nullable
  public static Module findCircularDependencyOnLibraryWithSamePackage(@NotNull AndroidFacet facet) {
    final Manifest manifest = facet.getManifest();
    final String aPackage = manifest != null ? manifest.getPackage().getValue() : null;
    if (aPackage == null) {
      return null;
    }

    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(facet.getModule(), true)) {
      final Manifest depManifest = depFacet.getManifest();
      final String depPackage = depManifest != null ? depManifest.getPackage().getValue() : null;
      if (aPackage.equals(depPackage)) {
        final List<AndroidFacet> depDependencies = AndroidUtils.getAllAndroidDependencies(depFacet.getModule(), false);

        if (depDependencies.contains(facet)) {
          // circular dependency on library with the same package
          return depFacet.getModule();
        }
      }
    }
    return null;
  }

  @NotNull
  public static String[] getLibPackages(@NotNull Module module, @NotNull String packageName) {
    final Set<String> packageSet = new HashSet<String>();
    packageSet.add(packageName);

    final List<String> result = new ArrayList<String>();

    for (String libPackage : AndroidUtils.getDepLibsPackages(module)) {
      if (packageSet.add(libPackage)) {
        result.add(libPackage);
      }
    }

    return ArrayUtil.toStringArray(result);
  }

  // support for lib<->lib and app<->lib circular dependencies
  // see IDEA-79737 for details
  public static boolean isLibraryWithBadCircularDependency(@NotNull AndroidFacet facet) {
    if (!facet.isLibraryProject()) {
      return false;
    }

    final List<AndroidFacet> dependencies = AndroidUtils.getAllAndroidDependencies(facet.getModule(), false);

    final Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return false;
    }

    final String aPackage = manifest.getPackage().getValue();
    if (aPackage == null || aPackage.length() == 0) {
      return false;
    }

    for (AndroidFacet depFacet : dependencies) {
      final List<AndroidFacet> depDependencies = AndroidUtils.getAllAndroidDependencies(depFacet.getModule(), true);

      if (depDependencies.contains(facet) &&
          dependencies.contains(depFacet) &&
          (depFacet.getModule().getName().compareTo(facet.getModule().getName()) < 0 ||
           !depFacet.isLibraryProject())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static String getUnsignedApkPath(@NotNull AndroidFacet facet) {
    return AndroidRootUtil.getApkPath(facet);
  }

  public static void reportException(@NotNull CompileContext context, @NotNull String messagePrefix, @NotNull Exception e) {
    context.addMessage(CompilerMessageCategory.ERROR, messagePrefix + e.getClass().getSimpleName() + ": " + e.getMessage(), null, -1, -1);
  }

  @Nullable
  public static String getAaptManifestPackage(@NotNull AndroidFacet facet) {
    if (facet.getProperties().USE_CUSTOM_MANIFEST_PACKAGE) {
      return facet.getProperties().CUSTOM_MANIFEST_PACKAGE;
    }
    final Manifest manifest = facet.getManifest();

    return manifest != null
           ? manifest.getPackage().getStringValue()
           : null;
  }

  public static void createGenModulesAndSourceRoots(@NotNull Project project, @NotNull final Collection<AndroidFacet> facets) {
    if (project.isDisposed()) {
      return;
    }
    final ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final List<ModifiableRootModel> modelsToCommit = new ArrayList<ModifiableRootModel>();

        for (final AndroidFacet facet : facets) {
          if (facet.requiresAndroidModel()) {
            continue;
          }
          final Module module = facet.getModule();

          if (module.isDisposed()) {
            continue;
          }
          final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();

          if (createGenModulesAndSourceRoots(facet, model)) {
            modelsToCommit.add(model);
          }
          else {
            model.dispose();
          }
        }
        if (modelsToCommit.size() == 0) {
          return;
        }

        ModifiableModelCommitter.multiCommit(modelsToCommit.toArray(new ModifiableRootModel[modelsToCommit.size()]), moduleModel);
      }
    });
  }
}