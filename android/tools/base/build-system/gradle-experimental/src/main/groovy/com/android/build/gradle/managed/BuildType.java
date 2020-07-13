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

package com.android.build.gradle.managed;

import com.android.builder.model.AndroidArtifact;

import org.gradle.api.Named;
import org.gradle.model.Managed;
import org.gradle.model.ModelSet;
import org.gradle.model.Unmanaged;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * A Managed build type.
 *
 * TODO: Convert Unmanaged Collection to Managed type when Gradle provides ModelSet for basic class.
 */
@Managed
public interface BuildType extends Named {

    /**
     * Map of Build Config Fields where the key is the field name.
     *
     * @return a non-null map of class fields (possibly empty).
     */
    ModelSet<ClassField> getBuildConfigFields();

    /**
     * Map of generated res values where the key is the res name.
     *
     * @return a non-null map of class fields (possibly empty).
     */
    ModelSet<ClassField> getResValues();

    /**
     * Returns the collection of proguard rule files.
     *
     * <p>These files are only applied to the production code.
     *
     * @return a non-null collection of files.
     * @see #getTestProguardFiles()
     */
    @Unmanaged
    Set<File> getProguardFiles();
    void setProguardFiles(Set<File> files);

    /**
     * Returns the collection of proguard rule files for consumers of the library to use.
     *
     * @return a non-null collection of files.
     */
    @Unmanaged
    Set<File> getConsumerProguardFiles();
    void setConsumerProguardFiles(Set<File> files);

    /**
     * Returns the collection of proguard rule files to use for the test APK.
     *
     * @return a non-null collection of files.
     */
    @Unmanaged
    Set<File> getTestProguardFiles();
    void setTestProguardFiles(Set<File> files);

    /**
     * Returns the map of key value pairs for placeholder substitution in the android manifest file.
     *
     * This map will be used by the manifest merger.
     * @return the map of key value pairs.
     */
    // TODO: Add the commented fields.
    //Map<String, Object> getManifestPlaceholders();

    /**
     * Returns whether multi-dex is enabled.
     *
     * This can be null if the flag is not set, in which case the default value is used.
     */
    Boolean getMultiDexEnabled();
    void setMultiDexEnabled(Boolean multiDexEnabled);

    String getMultiDexKeepFile();
    void setMultiDexKeepFile(String multiDexKeepFile);

    String getMultiDexKeepProguard();
    void setMultiDexKeepProguard(String multiDexKeepProguard);

    /**
     * Returns the optional jarjar rule files, or empty if jarjar should be skipped.
     *
     * <p>If more than one file is provided, the rule files will be merged in order with last one
     * win in case of rule redefinition.
     *
     * <p>Can only be used with Jack toolchain.
     *
     * @return the optional jarjar rule file.
     */
    @Unmanaged
    List<File> getJarJarRuleFiles();
    void setJarJarRuleFiles(List<File> jarJarRuleFiles);

    /**
     * Returns whether the build type is configured to generate a debuggable apk.
     *
     * @return true if the apk is debuggable
     */
    Boolean getDebuggable();
    void setDebuggable(Boolean isDebuggable);

    /**
     * Returns whether the build type is configured to be build with support for code coverage.
     *
     * @return true if code coverage is enabled.
     */
    Boolean getTestCoverageEnabled();
    void setTestCoverageEnabled(Boolean isTestCoverageEnabled);

    /**
     * Returns whether the build type is configured to be build with support for pseudolocales.
     *
     * @return true if code coverage is enabled.
     */
    Boolean getPseudoLocalesEnabled();
    void setPseudoLocalesEnabled(Boolean isPseudoLocalesEnabled);

    /**
     * Returns whether the build type is configured to generate an apk with debuggable
     * renderscript code.
     *
     * @return true if the apk is debuggable
     */
    Boolean getRenderscriptDebuggable();
    void setRenderscriptDebuggable(Boolean isRenderscriptDebuggable);

    /**
     * Returns the optimization level of the renderscript compilation.
     *
     * @return the optimization level.
     */
    Integer getRenderscriptOptimLevel();
    void setRenderscriptOptimLevel(Integer renderscriptOptimLevel);

    /**
     * Returns the application id suffix applied to this build type.
     * To get the final application id, use {@link AndroidArtifact#getApplicationId()}.
     *
     * @return the application id
     */
    String getApplicationIdSuffix();
    void setApplicationIdSuffix(String applicationIdSuffix);

    /**
     * Returns the version name suffix.
     *
     * @return the version name suffix.
     */
    String getVersionNameSuffix();
    void setVersionNameSuffix(String versionNameSuffix);

    /**
     * Returns whether minification is enabled for this build type.
     *
     * @return true if minification is enabled.
     */
    Boolean getMinifyEnabled();
    void setMinifyEnabled(Boolean isMinifyEnabled);

    /**
     * Return whether zipalign is enabled for this build type.
     *
     * @return true if zipalign is enabled.
     */
    Boolean getZipAlignEnabled();
    void setZipAlignEnabled(Boolean isZipAlignEnabled);

    /**
     * Returns whether the variant embeds the micro app.
     */
    Boolean getEmbedMicroApp();
    void setEmbedMicroApp(Boolean isEmbedMicroApp);

    /**
     * Returns the associated signing config or null if none are set on the build type.
     */
    SigningConfig getSigningConfig();
    void setSigningConfig(SigningConfig signingConfig);

    Boolean getUseJack();
    void setUseJack(Boolean useJack);

    Boolean getShrinkResources();
    void setShrinkResources(Boolean shrinkResources);

    NdkBuildType getNdk();
}
