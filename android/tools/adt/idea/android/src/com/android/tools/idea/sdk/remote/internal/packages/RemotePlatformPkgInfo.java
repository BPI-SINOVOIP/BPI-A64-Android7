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

package com.android.tools.idea.sdk.remote.internal.packages;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.remote.internal.sources.SdkRepoConstants;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * Represents a platform XML node in an SDK repository.
 */
public class RemotePlatformPkgInfo extends RemoteMinToolsPkgInfo implements IAndroidVersionProvider {

  /**
   * The version, a string, for platform packages.
   */
  private final String mVersionName;

  /**
   * The helper handling the layoutlib version.
   */
  private final LayoutlibVersionMixin mLayoutlibVersion;

  /**
   * Creates a new platform package from the attributes and elements of the given XML node.
   * This constructor should throw an exception if the package cannot be created.
   *
   * @param source      The {@link SdkSource} where this is loaded from.
   * @param packageNode The XML element being parsed.
   * @param nsUri       The namespace URI of the originating XML document, to be able to deal with
   *                    parameters that vary according to the originating XML schema.
   * @param licenses    The licenses loaded from the XML originating document.
   */
  public RemotePlatformPkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    mVersionName = RemotePackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_VERSION);

    int apiLevel = RemotePackageParserUtils.getXmlInt(packageNode, SdkRepoConstants.NODE_API_LEVEL, 0);
    String codeName = RemotePackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_CODENAME);
    if (codeName.length() == 0) {
      codeName = null;
    }
    AndroidVersion version = new AndroidVersion(apiLevel, codeName);

    mLayoutlibVersion = new LayoutlibVersionMixin(packageNode);

    PkgDesc.Builder pkgDescBuilder = PkgDesc.Builder.newPlatform(version, new MajorRevision(getRevision()), getMinToolsRevision());
    pkgDescBuilder
      .setDescriptionShort(createShortDescription(mListDisplay, getRevision(), mVersionName, version, isObsolete()));
    pkgDescBuilder.setDescriptionUrl(getDescUrl());
    pkgDescBuilder.setListDisplay(createListDescription(mListDisplay, mVersionName, version, isObsolete()));
    pkgDescBuilder.setIsObsolete(isObsolete());
    pkgDescBuilder.setLicense(getLicense());

    mPkgDesc = pkgDescBuilder.create();
  }

  /**
   * Save the properties of the current packages in the given {@link Properties} object.
   * These properties will later be given to a constructor that takes a {@link Properties} object.
   */
  @Override
  public void saveProperties(Properties props) {
    super.saveProperties(props);

    getAndroidVersion().saveProperties(props);
    mLayoutlibVersion.saveProperties(props);

    if (mVersionName != null) {
      props.setProperty(PkgProps.PLATFORM_VERSION, mVersionName);
    }
  }

  /**
   * Returns the version, a string, for platform packages.
   */
  public String getVersionName() {
    return mVersionName;
  }

  /**
   * Returns the package version, for platform, add-on and doc packages.
   */
  @Override
  @NonNull
  public AndroidVersion getAndroidVersion() {
    return getPkgDesc().getAndroidVersion();
  }

  /**
   * Returns a string identifier to install this package from the command line.
   * For platforms, we use "android-N" where N is the API or the preview codename.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public String installId() {
    return AndroidTargetHash.getPlatformHashString(getAndroidVersion());
  }

  /**
   * Returns a description of this package that is suitable for a list display.
   * <p/>
   * {@inheritDoc}
   */
  private static String createListDescription(String listDisplay, String versionName, AndroidVersion version, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s%2$s", listDisplay, obsolete ? " (Obsolete)" : "");
    }

    String s;
    if (version.isPreview()) {
      s = String.format("SDK Platform Android %1$s Preview%2$s", versionName, obsolete ? " (Obsolete)" : "");  //$NON-NLS-2$
    }
    else {
      s = String.format("SDK Platform Android %1$s%2$s", versionName, obsolete ? " (Obsolete)" : "");      //$NON-NLS-2$
    }

    return s;
  }

  /**
   * Returns a short description for an {@link IDescription}.
   */
  private static String createShortDescription(String listDisplay,
                                               FullRevision revision,
                                               String versionName,
                                               AndroidVersion version,
                                               boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s, revision %2$s%3$s", listDisplay, revision.toShortString(), obsolete ? " (Obsolete)" : "");
    }

    String s;
    if (version.isPreview()) {
      s = String.format("SDK Platform Android %1$s Preview, revision %2$s%3$s", versionName, revision.toShortString(),
                        obsolete ? " (Obsolete)" : "");  //$NON-NLS-2$
    }
    else {
      s = String
        .format("SDK Platform Android %1$s, API %2$d, revision %3$s%4$s", versionName, version.getApiLevel(), revision.toShortString(),
                obsolete ? " (Obsolete)" : "");      //$NON-NLS-2$
    }

    return s;
  }

  /**
   * Computes a potential installation folder if an archive of this package were
   * to be installed right away in the given SDK root.
   * <p/>
   * A platform package is typically installed in SDK/platforms/android-"version".
   * However if we can find a different directory under SDK/platform that already
   * has this platform version installed, we'll use that one.
   *
   * @param osSdkRoot  The OS path of the SDK root folder.
   * @param sdkManager An existing SDK manager to list current platforms and addons.
   * @return A new {@link File} corresponding to the directory to use to install this package.
   */
  @Override
  public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {

    // First find if this platform is already installed. If so, reuse the same directory.
    for (IAndroidTarget target : sdkManager.getTargets()) {
      if (target.isPlatform() && target.getVersion().equals(getAndroidVersion())) {
        return new File(target.getLocation());
      }
    }

    File platforms = new File(osSdkRoot, SdkConstants.FD_PLATFORMS);
    File folder = new File(platforms, String.format("android-%s", getAndroidVersion().getApiString())); //$NON-NLS-1$

    return folder;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((mLayoutlibVersion == null) ? 0 : mLayoutlibVersion.hashCode());
    result = prime * result + (getPkgDesc().hasAndroidVersion() ? 0 : getPkgDesc().getAndroidVersion().hashCode());
    result = prime * result + ((mVersionName == null) ? 0 : mVersionName.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (!(obj instanceof RemotePlatformPkgInfo)) {
      return false;
    }
    RemotePlatformPkgInfo other = (RemotePlatformPkgInfo)obj;
    if (mLayoutlibVersion == null) {
      if (other.mLayoutlibVersion != null) {
        return false;
      }
    }
    else if (!mLayoutlibVersion.equals(other.mLayoutlibVersion)) {
      return false;
    }
    if (mVersionName == null) {
      if (other.mVersionName != null) {
        return false;
      }
    }
    else if (!mVersionName.equals(other.mVersionName)) {
      return false;
    }
    return true;
  }
}
