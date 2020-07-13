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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.NdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.model.NativeLibraryFactory;
import com.android.build.gradle.internal.model.NativeLibraryImpl;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.ndk.internal.BinaryToolHelper;
import com.android.builder.model.NativeLibrary;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.gradle.nativeplatform.NativeLibraryBinarySpec;
import org.gradle.platform.base.BinaryContainer;

import java.io.File;
import java.util.Collections;

/**
 * Implementation of NativeLibraryFactory from in the component model plugin.
 *
 * The library extract information directly from the binaries.
 */
public class ComponentNativeLibraryFactory implements NativeLibraryFactory {

    BinaryContainer binaries;

    NdkHandler ndkHandler;

    public ComponentNativeLibraryFactory(BinaryContainer binaries,
            NdkHandler ndkHandler) {
        this.binaries = binaries;
        this.ndkHandler = ndkHandler;
    }

    @NonNull
    @Override
    public Optional<NativeLibrary> create(
            @NonNull VariantScope scope,
            @NonNull String toolchainName,
            @NonNull final Abi abi) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();

        DefaultAndroidBinary androidBinary =
                (DefaultAndroidBinary) binaries.findByName(variantData.getName());

        if (androidBinary == null) {
            // Binaries are not created for test variants.
            return Optional.absent();
        }

        @SuppressWarnings("ConstantConditions")
        Optional<NativeLibraryBinarySpec> nativeBinary =
                Iterables.tryFind(androidBinary.getNativeBinaries(),
                        new Predicate<NativeLibraryBinarySpec>() {
                            @Override
                            public boolean apply(NativeLibraryBinarySpec binary) {
                                return binary.getTargetPlatform().getName().equals(abi.getName());
                            }
                        });

        if (!nativeBinary.isPresent()) {
            // We don't have native binaries.
            return Optional.absent();
        }

        CoreNdkOptions ndkConfig = variantData.getVariantConfiguration().getNdkConfig();
        // The DSL currently do not support all options available in the model such as the
        // include dirs and the defines.  Therefore, just pass an empty collection for now.
        return Optional.<NativeLibrary>of(new NativeLibraryImpl(
                ndkConfig.getModuleName(),
                toolchainName,
                abi.getName(),
                Collections.<File>emptyList(),  /*cIncludeDirs*/
                Collections.<File>emptyList(),  /*cppIncludeDirs*/
                Collections.<File>emptyList(),  /*cSystemIncludeDirs*/
                ndkHandler.getStlIncludes(ndkConfig.getStl(), abi),
                Collections.<String>emptyList(),  /*cDefines*/
                Collections.<String>emptyList(),  /*cppDefines*/
                BinaryToolHelper.getCCompiler(nativeBinary.get()).getArgs(),
                BinaryToolHelper.getCppCompiler(nativeBinary.get()).getArgs(),
                ImmutableList.of(variantData.getScope().getNdkDebuggableLibraryFolders(abi))));
    }
}
