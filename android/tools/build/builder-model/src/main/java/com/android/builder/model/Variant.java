/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.builder.model;

import com.android.annotations.NonNull;

import java.util.Collection;
import java.util.List;

/**
 * A build Variant.
 */
public interface Variant {

    /**
     * Returns the name of the variant. It is made up of the build type and flavors (if applicable)
     *
     * @return the name of the variant.
     */
    @NonNull
    String getName();

    /**
     * Returns a display name for the variant. It is made up of the build type and flavors
     * (if applicable)
     *
     * @return the name.
     */
    @NonNull
    String getDisplayName();

    /**
     * Returns the main artifact for this variant.
     *
     * @return the artifact.
     */
    @NonNull
    AndroidArtifact getMainArtifact();

    @NonNull
    Collection<AndroidArtifact> getExtraAndroidArtifacts();

    @NonNull
    Collection<JavaArtifact> getExtraJavaArtifacts();

    /**
     * Returns the build type. All variants have a build type, so this is never null.
     *
     * @return the name of the build type.
     */
    @NonNull
    String getBuildType();

    /**
     * Returns the flavors for this variants. This can be empty if no flavors are configured.
     *
     * @return a list of flavors which can be empty.
     */
    @NonNull
    List<String> getProductFlavors();

    /**
     * The result of the merge of all the flavors and of the main default config. If no flavors
     * are defined then this is the same as the default config.
     *
     * This is directly a ProductFlavor instance of a ProdutFlavorContainer since this a composite
     * of existing ProductFlavors.
     *
     * @return the merged flavors.
     *
     * @see AndroidProject#getDefaultConfig()
     */
    @NonNull
    ProductFlavor getMergedFlavor();
}
