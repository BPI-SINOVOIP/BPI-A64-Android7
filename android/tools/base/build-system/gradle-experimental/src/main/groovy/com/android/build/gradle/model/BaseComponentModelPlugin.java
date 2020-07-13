/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.model;

import static com.android.build.gradle.model.AndroidComponentModelPlugin.COMPONENT_NAME;
import static com.android.build.gradle.model.ModelConstants.ANDROID_BUILDER;
import static com.android.build.gradle.model.ModelConstants.ANDROID_CONFIG_ADAPTOR;
import static com.android.build.gradle.model.ModelConstants.EXTRA_MODEL_INFO;
import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.AndroidConfigHelper;
import com.android.build.gradle.internal.ExecutionConfigurationUtil;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LibraryCache;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.NdkOptionsHelper;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.coverage.JacocoPlugin;
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.profile.RecordingBuildListener;
import com.android.build.gradle.internal.tasks.DependencyReportTask;
import com.android.build.gradle.internal.tasks.SigningReportTask;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.managed.AndroidConfig;
import com.android.build.gradle.managed.BuildType;
import com.android.build.gradle.managed.ClassField;
import com.android.build.gradle.managed.NdkConfig;
import com.android.build.gradle.managed.NdkOptions;
import com.android.build.gradle.managed.ProductFlavor;
import com.android.build.gradle.managed.SigningConfig;
import com.android.build.gradle.managed.adaptor.AndroidConfigAdaptor;
import com.android.build.gradle.managed.adaptor.BuildTypeAdaptor;
import com.android.build.gradle.managed.adaptor.ProductFlavorAdaptor;
import com.android.build.gradle.tasks.JillTask;
import com.android.build.gradle.tasks.PreDex;
import com.android.builder.Version;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.compiler.JackConversionCache;
import com.android.builder.internal.compiler.PreDexCache;
import com.android.builder.profile.ProcessRecorderFactory;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.signing.DefaultSigningConfig;
import com.android.ide.common.internal.ExecutorSingleton;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.prefs.AndroidLocation;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.Defaults;
import org.gradle.model.Model;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.ModelCreators;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.util.List;

import javax.inject.Inject;

import groovy.lang.Closure;

public class BaseComponentModelPlugin implements Plugin<Project> {

    private ToolingModelBuilderRegistry toolingRegistry;

    private ModelRegistry modelRegistry;

    @Inject
    protected BaseComponentModelPlugin(ToolingModelBuilderRegistry toolingRegistry,
            ModelRegistry modelRegistry) {
        this.toolingRegistry = toolingRegistry;
        this.modelRegistry = modelRegistry;
    }

    /**
     * Replace BasePlugin's apply method for component model.
     */
    @Override
    public void apply(Project project) {
        ExecutionConfigurationUtil.setThreadPoolSize(project);
        try {
            List<Recorder.Property> propertyList = Lists.newArrayList(
                    new Recorder.Property("plugin_version", Version.ANDROID_GRADLE_PLUGIN_VERSION),
                    new Recorder.Property("next_gen_plugin", "true"),
                    new Recorder.Property("gradle_version", project.getGradle().getGradleVersion())
            );
            String benchmarkName = AndroidGradleOptions.getBenchmarkName(project);
            if (benchmarkName != null) {
                propertyList.add(new Recorder.Property("benchmark_name", benchmarkName));
            }
            String benchmarkMode = AndroidGradleOptions.getBenchmarkMode(project);
            if (benchmarkMode != null) {
                propertyList.add(new Recorder.Property("benchmark_mode", benchmarkMode));
            }

            ProcessRecorderFactory.initialize(
                    new LoggerWrapper(project.getLogger()),
                    project.getRootProject()
                            .file("profiler" + System.currentTimeMillis() + ".json"),
                    propertyList);
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialize ProcessRecorderFactory");
        }
        project.getGradle().addListener(new RecordingBuildListener(ThreadRecorder.get()));

        project.getPlugins().apply(AndroidComponentModelPlugin.class);
        project.getPlugins().apply(JavaBasePlugin.class);
        project.getPlugins().apply(JacocoPlugin.class);

        // TODO: Create configurations for build types and flavors, or migrate to new dependency
        // management if it's ready.
        ConfigurationContainer configurations = project.getConfigurations();
        createConfiguration(configurations, "compile", "Classpath for default sources.");
        createConfiguration(configurations, "default-metadata", "Metadata for published APKs");
        createConfiguration(configurations, "default-mapping", "Metadata for published APKs");

        project.getPlugins().apply(NdkComponentModelPlugin.class);

        // Remove this when our models no longer depends on Project.
        modelRegistry.create(ModelCreators
                .bridgedInstance(ModelReference.of("projectModel", Project.class), project)
                .descriptor("Model of project.").build());

        toolingRegistry.register(new ComponentModelBuilder(modelRegistry));

        // Inserting the ToolingModelBuilderRegistry into the model so that it can be use to create
        // TaskManager in child classes.
        modelRegistry.create(ModelCreators.bridgedInstance(
                ModelReference.of("toolingRegistry", ToolingModelBuilderRegistry.class),
                toolingRegistry).descriptor("Tooling model builder model registry.").build());
    }

    private static void createConfiguration(@NonNull ConfigurationContainer configurations,
            @NonNull String configurationName, @NonNull String configurationDescription) {
        Configuration configuration = configurations.findByName(configurationName);
        if (configuration == null) {
            configuration = configurations.create(configurationName);
        }

        configuration.setVisible(false);
        configuration.setDescription(configurationDescription);
    }

    @SuppressWarnings("MethodMayBeStatic")
    public static class Rules extends RuleSource {

        @Defaults
        public void configureAndroidModel(
                AndroidConfig androidModel,
                ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            AndroidConfigHelper.configure(androidModel, instantiator);

            androidModel.getSigningConfigs().create(DEBUG, new Action<SigningConfig>() {
                @Override
                public void execute(SigningConfig signingConfig) {
                    try {
                        signingConfig.setStoreFile(KeystoreHelper.defaultDebugKeystoreLocation());
                        signingConfig.setStorePassword(DefaultSigningConfig.DEFAULT_PASSWORD);
                        signingConfig.setKeyAlias(DefaultSigningConfig.DEFAULT_ALIAS);
                        signingConfig.setKeyPassword(DefaultSigningConfig.DEFAULT_PASSWORD);
                        signingConfig.setStoreType(KeyStore.getDefaultType());
                    } catch (AndroidLocation.AndroidLocationException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        // com.android.build.gradle.AndroidConfig do not contain an NdkConfig.  Copy it to the
        // defaultConfig for now.
        @Defaults
        public void copyNdkConfig(
                @Path("android.defaultConfig.ndk") NdkOptions defaultNdkConfig,
                @Path("android.ndk") NdkConfig pluginNdkConfig) {
            NdkOptionsHelper.init(defaultNdkConfig);
            NdkOptionsHelper.merge(defaultNdkConfig, pluginNdkConfig);
        }

       // TODO: Remove code duplicated from BasePlugin.
        @Model(EXTRA_MODEL_INFO)
        public ExtraModelInfo createExtraModelInfo(
                Project project,
                @NonNull @Path("isApplication") Boolean isApplication) {
            return new ExtraModelInfo(project, isApplication);
        }

        @Model
        public SdkHandler createSdkHandler(final Project project) {
            final ILogger logger = new LoggerWrapper(project.getLogger());
            final SdkHandler sdkHandler = new SdkHandler(project, logger);

            // call back on execution. This is called after the whole build is done (not
            // after the current project is done).
            // This is will be called for each (android) projects though, so this should support
            // being called 2+ times.
            project.getGradle().buildFinished(new Closure<Object>(this, this) {
                public void doCall(Object it) {
                    ExecutorSingleton.shutdown();
                    sdkHandler.unload();
                    try {
                        PreDexCache.getCache().clear(project.getRootProject()
                                .file(String.valueOf(project.getRootProject().getBuildDir()) + "/"
                                        + FD_INTERMEDIATES + "/dex-cache/cache.xml"), logger);
                        JackConversionCache.getCache().clear(project.getRootProject()
                                .file(String.valueOf(project.getRootProject().getBuildDir()) + "/"
                                        + FD_INTERMEDIATES + "/jack-cache/cache.xml"), logger);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    LibraryCache.getCache().unload();
                }

                public void doCall() {
                    doCall(null);
                }
            });

            project.getGradle().getTaskGraph().whenReady(new Closure<Void>(this, this) {
                public void doCall(TaskExecutionGraph taskGraph) {
                    for (Task task : taskGraph.getAllTasks()) {
                        if (task instanceof PreDex) {
                            PreDexCache.getCache().load(project.getRootProject()
                                    .file(String.valueOf(project.getRootProject().getBuildDir())
                                            + "/" + FD_INTERMEDIATES + "/dex-cache/cache.xml"));
                            break;
                        } else if (task instanceof JillTask) {
                            JackConversionCache.getCache().load(project.getRootProject()
                                    .file(String.valueOf(project.getRootProject().getBuildDir())
                                            + "/" + FD_INTERMEDIATES + "/jack-cache/cache.xml"));
                            break;
                        }
                    }
                }
            });

            // setup SDK repositories.
            for (final File file : sdkHandler.getSdkLoader().getRepositories()) {
                project.getRepositories().maven(new Action<MavenArtifactRepository>() {
                    @Override
                    public void execute(MavenArtifactRepository repo) {
                        repo.setUrl(file.toURI());

                    }
                });
            }
            return sdkHandler;
        }

        @Model(ANDROID_BUILDER)
        public AndroidBuilder createAndroidBuilder(Project project, ExtraModelInfo extraModelInfo) {
            String creator = "Android Gradle";
            ILogger logger = new LoggerWrapper(project.getLogger());

            return new AndroidBuilder(project.equals(project.getRootProject()) ? project.getName()
                    : project.getPath(), creator, new GradleProcessExecutor(project),
                    new GradleJavaProcessExecutor(project),
                    extraModelInfo, logger, project.getLogger().isEnabled(LogLevel.INFO));

        }

        @Mutate
        public void initDebugBuildTypes(
                @Path("android.buildTypes") ModelMap<BuildType> buildTypes,
                @Path("android.signingConfigs") final ModelMap<SigningConfig> signingConfigs) {
            buildTypes.beforeEach(new Action<BuildType>() {
                @Override
                public void execute(BuildType buildType) {
                    initBuildType(buildType);
                }
            });

            buildTypes.named(DEBUG, new Action<BuildType>() {
                @Override
                public void execute(BuildType buildType) {
                    buildType.setSigningConfig(signingConfigs.get(DEBUG));
                }
            });
        }

        private static void initBuildType(@NonNull BuildType buildType) {
            buildType.setDebuggable(false);
            buildType.setTestCoverageEnabled(false);
            buildType.setPseudoLocalesEnabled(false);
            buildType.setRenderscriptDebuggable(false);
            buildType.setRenderscriptOptimLevel(3);
            buildType.setMinifyEnabled(false);
            buildType.setZipAlignEnabled(true);
            buildType.setEmbedMicroApp(true);
            buildType.setUseJack(false);
            buildType.setShrinkResources(false);
            buildType.setProguardFiles(Sets.<File>newHashSet());
            buildType.setConsumerProguardFiles(Sets.<File>newHashSet());
            buildType.setTestProguardFiles(Sets.<File>newHashSet());
        }

        @Mutate
        public void initDefaultConfig(@Path("android.defaultConfig") ProductFlavor defaultConfig) {
            initProductFlavor(defaultConfig);
        }

        @Mutate
        public void initProductFlavors(
                @Path("android.productFlavors") final ModelMap<ProductFlavor> productFlavors) {
            productFlavors.beforeEach(new Action<ProductFlavor>() {
                @Override
                public void execute(ProductFlavor productFlavor) {
                    initProductFlavor(productFlavor);
                }
            });
        }

        private void initProductFlavor(ProductFlavor productFlavor) {
            productFlavor.setProguardFiles(Sets.<File>newHashSet());
            productFlavor.setConsumerProguardFiles(Sets.<File>newHashSet());
            productFlavor.setTestProguardFiles(Sets.<File>newHashSet());
            productFlavor.setResourceConfigurations(Sets.<String>newHashSet());
            productFlavor.setJarJarRuleFiles(Lists.<File>newArrayList());
            productFlavor.getBuildConfigFields().beforeEach(new Action<ClassField>() {
                @Override
                public void execute(ClassField classField) {
                    classField.setAnnotations(Sets.<String>newHashSet());
                }
            });
            productFlavor.getResValues().beforeEach(new Action<ClassField>() {
                @Override
                public void execute(ClassField classField) {
                    classField.setAnnotations(Sets.<String>newHashSet());
                }
            });
        }

        @Mutate
        public void addDefaultAndroidSourceSet(
                @Path("android.sources") AndroidComponentModelSourceSet sources) {
            sources.addDefaultSourceSet("resources", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("java", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("manifest", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("res", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("assets", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("aidl", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("renderscript", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("jniLibs", AndroidLanguageSourceSet.class);

            sources.all(new Action<FunctionalSourceSet>() {
                @Override
                public void execute(FunctionalSourceSet functionalSourceSet) {
                    LanguageSourceSet manifest = functionalSourceSet.getByName("manifest");
                    manifest.getSource().setIncludes(ImmutableList.of("AndroidManifest.xml"));
                }
            });
        }

        @Model(ANDROID_CONFIG_ADAPTOR)
        public com.android.build.gradle.AndroidConfig createModelAdaptor(
                ServiceRegistry serviceRegistry,
                AndroidConfig androidExtension,
                Project project,
                @Path("isApplication") Boolean isApplication) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return new AndroidConfigAdaptor(androidExtension, AndroidConfigHelper
                    .createSourceSetsContainer(project, instantiator, !isApplication));
        }

        @Mutate
        public void createAndroidComponents(
                ComponentSpecContainer androidSpecs,
                ServiceRegistry serviceRegistry, AndroidConfig androidExtension,
                com.android.build.gradle.AndroidConfig adaptedModel,
                @Path("android.buildTypes") ModelMap<BuildType> buildTypes,
                @Path("android.productFlavors") ModelMap<ProductFlavor> productFlavors,
                @Path("android.signingConfigs") ModelMap<SigningConfig> signingConfigs,
                VariantFactory variantFactory,
                TaskManager taskManager,
                Project project,
                AndroidBuilder androidBuilder,
                SdkHandler sdkHandler,
                ExtraModelInfo extraModelInfo,
                @Path("isApplication") Boolean isApplication) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);

            // check if the target has been set.
            TargetInfo targetInfo = androidBuilder.getTargetInfo();
            if (targetInfo == null) {
                sdkHandler.initTarget(androidExtension.getCompileSdkVersion(),
                        androidExtension.getBuildToolsRevision(),
                        androidExtension.getLibraryRequests(), androidBuilder);
            }

            VariantManager variantManager = new VariantManager(project, androidBuilder,
                    adaptedModel, variantFactory, taskManager, instantiator);

            for (BuildType buildType : buildTypes.values()) {
                variantManager.addBuildType(new BuildTypeAdaptor(buildType));
            }

            for (ProductFlavor productFlavor : productFlavors.values()) {
                variantManager.addProductFlavor(new ProductFlavorAdaptor(productFlavor));
            }

            DefaultAndroidComponentSpec spec =
                    (DefaultAndroidComponentSpec) androidSpecs.get(COMPONENT_NAME);
            spec.setExtension(androidExtension);
            spec.setVariantManager(variantManager);
        }

        @Mutate
        public void createVariantData(
                ModelMap<AndroidBinary> binaries,
                ModelMap<AndroidComponentSpec> specs,
                TaskManager taskManager) {
            final VariantManager variantManager =
                    ((DefaultAndroidComponentSpec) specs.get(COMPONENT_NAME)).getVariantManager();
            binaries.afterEach(new Action<AndroidBinary>() {
                @Override
                public void execute(AndroidBinary androidBinary) {
                    DefaultAndroidBinary binary = (DefaultAndroidBinary) androidBinary;
                    List<ProductFlavorAdaptor> adaptedFlavors = Lists.newArrayList();
                    for (ProductFlavor flavor : binary.getProductFlavors()) {
                        adaptedFlavors.add(new ProductFlavorAdaptor(flavor));
                    }
                    binary.setVariantData(
                            variantManager.createVariantData(
                                    new BuildTypeAdaptor(binary.getBuildType()),
                                    adaptedFlavors));
                    variantManager.getVariantDataList().add(binary.getVariantData());
                }
            });
        }

        @Mutate
        public void createLifeCycleTasks(ModelMap<Task> tasks, TaskManager taskManager) {
            taskManager.createTasksBeforeEvaluate(new TaskModelMapAdaptor(tasks));
        }

        @Mutate
        public void createAndroidTasks(
                ModelMap<Task> tasks,
                ModelMap<AndroidComponentSpec> androidSpecs,
                TaskManager taskManager,
                SdkHandler sdkHandler,
                Project project, AndroidComponentModelSourceSet androidSources) {
            // setup SDK repositories.
            for (final File file : sdkHandler.getSdkLoader().getRepositories()) {
                project.getRepositories().maven(new Action<MavenArtifactRepository>() {
                    @Override
                    public void execute(MavenArtifactRepository repo) {
                        repo.setUrl(file.toURI());
                    }
                });
            }
            // TODO: determine how to provide functionalities of variant API objects.
        }

        // TODO: Use @BinaryTasks after figuring how to configure non-binary specific tasks.
        @Mutate
        public void createBinaryTasks(
                final ModelMap<Task> tasks,
                BinaryContainer binaries,
                ModelMap<AndroidComponentSpec> specs,
                TaskManager taskManager) {
            final VariantManager variantManager =
                    ((DefaultAndroidComponentSpec) specs.get(COMPONENT_NAME)).getVariantManager();
            binaries.withType(AndroidBinary.class, new Action<AndroidBinary>() {
                @Override
                public void execute(AndroidBinary androidBinary) {
                    DefaultAndroidBinary binary = (DefaultAndroidBinary) androidBinary;
                    variantManager.createTasksForVariantData(
                            new TaskModelMapAdaptor(tasks),
                            binary.getVariantData());
                }
            });
        }

        /**
         * Create tasks that must be created after other tasks for variants are created.
         */
        @Mutate
        public void createRemainingTasks(
                ModelMap<Task> tasks,
                TaskManager taskManager,
                ModelMap<AndroidComponentSpec> spec) {
            VariantManager variantManager =
                    ((DefaultAndroidComponentSpec)spec.get(COMPONENT_NAME)).getVariantManager();

            // create the test tasks.
            taskManager.createTopLevelTestTasks(new TaskModelMapAdaptor(tasks),
                    !variantManager.getProductFlavors().isEmpty());
        }

        @Mutate
        public void createReportTasks(
                ModelMap<Task> tasks,
                ModelMap<AndroidComponentSpec> specs) {
            final VariantManager variantManager =
                    ((DefaultAndroidComponentSpec)specs.get(COMPONENT_NAME)).getVariantManager();

            tasks.create("androidDependencies", DependencyReportTask.class,
                    new Action<DependencyReportTask>() {
                        @Override
                        public void execute(DependencyReportTask dependencyReportTask) {
                            dependencyReportTask.setDescription(
                                    "Displays the Android dependencies of the project");
                            dependencyReportTask.setVariants(variantManager.getVariantDataList());
                            dependencyReportTask.setGroup("Android");
                        }
                    });

            tasks.create("signingReport", SigningReportTask.class,
                    new Action<SigningReportTask>() {
                        @Override
                        public void execute(SigningReportTask signingReportTask) {
                            signingReportTask
                                    .setDescription("Displays the signing info for each variant");
                            signingReportTask.setVariants(variantManager.getVariantDataList());
                            signingReportTask.setGroup("Android");

                        }
                    });
        }

        @Mutate
        public void modifyAssembleTaskDescription(@Path("tasks.assemble") Task assembleTask) {
            assembleTask.setDescription(
                    "Assembles all variants of all applications and secondary packages.");
        }
    }
}
