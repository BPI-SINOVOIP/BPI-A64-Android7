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

package com.android.tools.idea.ddms;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

public class DevicePropertyUtil {
  private static final Set<String> ourManufacturerNameIsAcronym =
    ImmutableSet.of("ASUS", "HTC", "LG", "LGE", "ZTE");

  @VisibleForTesting
  static String fixManufacturerName(@NotNull String manufacturer) {
    String allCaps = manufacturer.toUpperCase(Locale.US);
    return ourManufacturerNameIsAcronym.contains(allCaps) ?
           allCaps : StringUtil.capitalizeWords(manufacturer, true);
  }

  public static String getManufacturer(IDevice d, String unknown) {
    String m = d.getProperty(IDevice.PROP_DEVICE_MANUFACTURER);
    return m != null ? fixManufacturerName(m) : unknown;
  }

  public static String getModel(IDevice d, String unknown) {
    String m = d.getProperty(IDevice.PROP_DEVICE_MODEL);
    return m != null ? StringUtil.capitalizeWords(m, true) : unknown;
  }

  public static String getBuild(IDevice d) {
    StringBuilder sb = new StringBuilder(20);
    String v = d.getProperty(IDevice.PROP_BUILD_VERSION);
    if (v != null) {
      sb.append("Android ");
      sb.append(v);
    }

    v = d.getProperty(IDevice.PROP_BUILD_API_LEVEL);
    if (v != null) {
      sb.append(String.format(" (API %1$s)", v));
    }

    return sb.toString();
  }

  /**
   * Retrieves the version of Android running on the device by reading its system properties.
   * Returns {@link com.android.sdklib.AndroidVersion#DEFAULT} if there are any issues while reading the properties.
   */
  @NotNull
  public static AndroidVersion getDeviceVersion(@NotNull IDevice device) {
    try {
      String apiLevel = device.getProperty(IDevice.PROP_BUILD_API_LEVEL);
      if (apiLevel == null) {
        return AndroidVersion.DEFAULT;
      }

      return new AndroidVersion(Integer.parseInt(apiLevel), device.getProperty(IDevice.PROP_BUILD_CODENAME));
    }
    catch (Exception e) {
      return AndroidVersion.DEFAULT;
    }
  }
}
