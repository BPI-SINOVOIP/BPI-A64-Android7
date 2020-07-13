package org.jetbrains.jps.android;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.local.LocalSdk;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.library.sdk.JpsSdk;

public class AndroidPlatform {
  private final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> mySdk;
  private final IAndroidTarget myTarget;
  private final int myPlatformToolsRevision;
  private final int mySdkToolsRevision;
  private final LocalSdk myLocalSdk;

  public AndroidPlatform(@NotNull JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> sdk,
                         @NotNull IAndroidTarget target,
                         @NotNull LocalSdk localSdk) {
    mySdk = sdk;
    myTarget = target;
    final String homePath = sdk.getHomePath();
    myPlatformToolsRevision = AndroidCommonUtils.parsePackageRevision(homePath, SdkConstants.FD_PLATFORM_TOOLS);
    mySdkToolsRevision = AndroidCommonUtils.parsePackageRevision(homePath, SdkConstants.FD_TOOLS);
    myLocalSdk = localSdk;
  }

  @NotNull
  public JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> getSdk() {
    return mySdk;
  }

  @NotNull
  public IAndroidTarget getTarget() {
    return myTarget;
  }

  public int getPlatformToolsRevision() {
    return myPlatformToolsRevision;
  }

  public int getSdkToolsRevision() {
    return mySdkToolsRevision;
  }

  public boolean needToAddAnnotationsJarToClasspath() {
    return myTarget.getVersion().getApiLevel() <= 15;
  }

  @NotNull
  public LocalSdk getLocalSdk() {
    return myLocalSdk;
  }
}
