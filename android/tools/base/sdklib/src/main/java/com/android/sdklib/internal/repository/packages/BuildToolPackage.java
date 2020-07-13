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

package com.android.sdklib.internal.repository.packages;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.sources.SdkSource;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.FullRevision.PreviewComparison;
import com.android.sdklib.repository.IDescription;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.PreciseRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;

import org.w3c.dom.Node;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Represents a build-tool XML node in an SDK repository.
 *
 * @deprecated
 * com.android.sdklib.internal.repository has moved into Studio as
 * com.android.tools.idea.sdk.remote.internal.
 */
@Deprecated
public class BuildToolPackage extends FullRevisionPackage {

    /** The base value returned by {@link BuildToolPackage#installId()}. */
    private static final String INSTALL_ID_BASE = SdkConstants.FD_BUILD_TOOLS + '-';

    private final IPkgDesc mPkgDesc;

    /**
     * Creates a new build-tool package from the attributes and elements of the given XML node.
     * This constructor should throw an exception if the package cannot be created.
     *
     * @param source The {@link SdkSource} where this is loaded from.
     * @param packageNode The XML element being parsed.
     * @param nsUri The namespace URI of the originating XML document, to be able to deal with
     *          parameters that vary according to the originating XML schema.
     * @param licenses The licenses loaded from the XML originating document.
     */
    public BuildToolPackage(
            SdkSource source,
            Node packageNode,
            String nsUri,
            Map<String,String> licenses) {
        super(source, packageNode, nsUri, licenses);

        mPkgDesc = setDescriptions(PkgDesc.Builder
                        .newBuildTool(getRevision())
        )
                .create();
    }

    /**
     * Creates either a valid {@link BuildToolPackage} or a {@link BrokenPackage}.
     * <p/>
     * If the build-tool directory contains valid properties,
     * this creates a new {@link BuildToolPackage} with the reversion listed in the properties.
     * Otherwise returns a new {@link BrokenPackage} with some explanation on what failed.
     * <p/>
     * Note that the folder name is not enforced. A build-tool directory must have a
     * a source.props with a revision property and a few expected binaries inside to be
     * valid.
     *
     * @param buildToolDir The SDK/build-tool/revision folder
     * @param props The properties located in {@code buildToolDir} or null if not found.
     * @return A new {@link BuildToolPackage} or a new {@link BrokenPackage}.
     */
    public static Package create(File buildToolDir, Properties props) {
        String error = null;

        // Try to load the reversion from the sources.props.
        // If we don't find them, the package is broken.
        if (props == null) {
            error = String.format("Missing file %1$s in build-tool/%2$s",
                    SdkConstants.FN_SOURCE_PROP,
                    buildToolDir.getName());
        }

        // Check we can find the revision in the source properties
        FullRevision rev = null;
        if (error == null) {
            String revStr = getProperty(props, PkgProps.PKG_REVISION, null);

            if (revStr != null) {
                try {
                    rev = FullRevision.parseRevision(revStr);
                } catch (NumberFormatException ignore) {}
            }

            if (rev == null) {
                error = String.format("Missing revision property in %1$s",
                        SdkConstants.FN_SOURCE_PROP);
            }
        }

        if (error == null) {
            // Check the directory contains the expected binaries.

            if (!buildToolDir.isDirectory()) {
                error = String.format("build-tool/%1$s folder is missing",
                                       buildToolDir.getName());
            } else {
                File[] files = buildToolDir.listFiles();
                if (files == null || files.length == 0) {
                    error = String.format("build-tool/%1$s folder is empty",
                                           buildToolDir.getName());
                } else {
                    Set<String> names = new HashSet<String>();
                    for (File file : files) {
                        names.add(file.getName());
                    }
                    for (String name : new String[] { SdkConstants.FN_AAPT,
                                                      SdkConstants.FN_AIDL,
                                                      SdkConstants.FN_DX } ) {
                        if (!names.contains(name)) {
                            if (error == null) {
                                error = String.format("build-tool/%1$s folder is missing ",
                                                       buildToolDir.getName());
                            } else {
                                error += ", ";
                            }
                            error += name;
                        }
                    }
                }
            }
        }

        if (error == null && rev != null) {
            BuildToolPackage pkg = new BuildToolPackage(
                    null,                       //source
                    props,
                    0,                          //revision (extracted from props)
                    null,                       //license
                    null,                       //description
                    null,                       //descUrl
                    buildToolDir.getAbsolutePath());

            if (pkg.hasCompatibleArchive()) {
                return pkg;
            } else {
                error = "Package is not compatible with current OS";
            }
        }


        StringBuilder sb = new StringBuilder("Broken Build-Tools Package");
        if (rev != null) {
            sb.append(String.format(", revision %1$s", rev.toShortString()));
        }

        String shortDesc = sb.toString();

        if (error != null) {
            sb.append('\n').append(error);
        }

        String longDesc = sb.toString();

        IPkgDesc desc = PkgDesc.Builder
                .newBuildTool(rev != null ? rev : new FullRevision(FullRevision.MISSING_MAJOR_REV))
                .setDescriptionShort(shortDesc)
                .create();

        return new BrokenPackage(props, shortDesc, longDesc,
                IMinApiLevelDependency.MIN_API_LEVEL_NOT_SPECIFIED,
                IExactApiLevelDependency.API_LEVEL_INVALID,
                buildToolDir.getAbsolutePath(),
                desc);
    }

    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected BuildToolPackage(
                SdkSource source,
                Properties props,
                int revision,
                String license,
                String description,
                String descUrl,
                String archiveOsPath) {
        super(source,
                props,
                revision,
                license,
                description,
                descUrl,
                archiveOsPath);

        mPkgDesc = setDescriptions(PkgDesc.Builder.newBuildTool(getRevision())).create();
    }

    @Override
    @NonNull
    public IPkgDesc getPkgDesc() {
        return mPkgDesc;
    }

    /**
     * Returns a string identifier to install this package from the command line.
     * For build-tools, we use "build-tools-" followed by the full revision string
     * where spaces are changed to underscore to be more script-friendly.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public String installId() {
        return getPkgDesc().getInstallId();
    }

    /**
     * Returns a description of this package that is suitable for a list display.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public String getListDescription() {
        String ld = getListDisplay();
        if (!ld.isEmpty()) {
            return String.format("%1$s%2$s", ld, isObsolete() ? " (Obsolete)" : "");
        }

        return String.format("Android SDK Build-tools%1$s",
                isObsolete() ? " (Obsolete)" : "");
    }

    /**
     * Returns a short description for an {@link IDescription}.
     */
    @Override
    public String getShortDescription() {
        String ld = getListDisplay();
        if (!ld.isEmpty()) {
            return String.format("%1$s, revision %2$s%3$s",
                    ld,
                    getRevision().toShortString(),
                    isObsolete() ? " (Obsolete)" : "");
        }

        return String.format("Android SDK Build-tools, revision %1$s%2$s",
                getRevision().toShortString(),
                isObsolete() ? " (Obsolete)" : "");
    }

    /** Returns a long description for an {@link IDescription}. */
    @Override
    public String getLongDescription() {
        String s = getDescription();
        if (s == null || s.isEmpty()) {
            s = getShortDescription();
        }

        if (s.indexOf("revision") == -1) {
            s += String.format("\nRevision %1$s%2$s",
                    getRevision().toShortString(),
                    isObsolete() ? " (Obsolete)" : "");
        }

        return s;
    }

    /**
     * Computes a potential installation folder if an archive of this package were
     * to be installed right away in the given SDK root.
     * <p/>
     * A build-tool package is typically installed in SDK/build-tools/revision.
     * Revision spaces are replaced by underscores for ease of use in command-line.
     *
     * @param osSdkRoot The OS path of the SDK root folder.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     * @return A new {@link File} corresponding to the directory to use to install this package.
     */
    @Override
    public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
        File folder = new File(osSdkRoot, SdkConstants.FD_BUILD_TOOLS);
        StringBuilder sb = new StringBuilder();

        PreciseRevision revision = getPkgDesc().getPreciseRevision();
        int[] version = revision.toIntArray(false);
        for (int i = 0; i < version.length; i++) {
            sb.append(version[i]);
            if (i != version.length - 1) {
                sb.append('.');
            }
        }
        if (getPkgDesc().getPreciseRevision().isPreview()) {
            sb.append(PkgDesc.PREVIEW_SUFFIX);
        }

        folder = new File(folder, sb.toString());
        return folder;
    }

    /**
     * Check whether 2 platform-tool packages are the same <em>and</em> have the
     * same preview bit.
     */
    @Override
    public boolean sameItemAs(Package pkg) {
        // Implementation note: here we don't want to care about the preview number
        // so we ignore the preview when calling sameItemAs(); however we do care
        // about both packages being either previews or not previews (i.e. the type
        // must match but the preview number doesn't need to.)
        // The end result is that a package such as "1.2 rc 4" will be an update for "1.2 rc 3".
        return sameItemAs(pkg, PreviewComparison.COMPARE_TYPE);
    }

    @Override
    public boolean sameItemAs(Package pkg, PreviewComparison comparePreview) {
        // Contrary to other package types, build-tools do not "update themselves"
        // so 2 build tools with 2 different revisions are not the same item.
        if (pkg instanceof BuildToolPackage) {
            BuildToolPackage rhs = (BuildToolPackage) pkg;
            return rhs.getRevision().compareTo(getRevision(), comparePreview) == 0;
        }
        return false;
    }

    /**
     * For build-tool package use their revision number like version numbers and
     * we want them sorted from higher to lower. To do that, insert a fake revision
     * number using 9999-value into the sorting key.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    protected String comparisonKey() {
        String s = super.comparisonKey();
        int pos = s.indexOf("|r:");         //$NON-NLS-1$
        assert pos > 0;

        FullRevision rev = getRevision();
        String reverseSort = String.format("|rr:%1$04d.%2$04d.%3$04d.",         //$NON-NLS-1$
                                            9999 - rev.getMajor(),
                                            9999 - rev.getMinor(),
                                            9999 - rev.getMicro());

        s = s.substring(0, pos) + reverseSort + s.substring(pos);
        return s;
    }

}
