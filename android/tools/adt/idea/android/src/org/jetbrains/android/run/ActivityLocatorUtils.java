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
package org.jetbrains.android.run;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClass;
import com.intellij.util.xml.DomElement;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ActivityLocatorUtils {
  public static boolean shouldUseMergedManifest(@NotNull AndroidFacet facet) {
    return facet.requiresAndroidModel() || facet.getProperties().ENABLE_MANIFEST_MERGING;
  }

  public static boolean containsLauncherIntent(@NotNull List<IntentFilter> intentFilters) {
    for (IntentFilter filter : intentFilters) {
      if (AndroidDomUtil.containsAction(filter, AndroidUtils.LAUNCH_ACTION_NAME) &&
          (AndroidDomUtil.containsCategory(filter, AndroidUtils.LAUNCH_CATEGORY_NAME) ||
           AndroidDomUtil.containsCategory(filter, AndroidUtils.LEANBACK_LAUNCH_CATEGORY_NAME))) {
        return true;
      }
    }

    return false;
  }

  @Nullable
  public static String getQualifiedName(@NotNull ActivityAlias alias) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    String name = alias.getName().getStringValue();
    if (name == null) {
      return null;
    }

    int dotIndex = name.indexOf('.');
    if (dotIndex > 0) { // fully qualified
      return name;
    }

    // attempt to retrieve the package name from the manifest in which this alias was defined
    String pkg = null;
    DomElement parent = alias.getParent();
    if (parent instanceof Application) {
      parent = parent.getParent();
      if (parent instanceof Manifest) {
        Manifest manifest = (Manifest)parent;
        pkg = manifest.getPackage().getStringValue();
      }
    }

    // if we have a package name, prepend that to the activity alias
    return pkg == null ? name : pkg + (dotIndex == -1 ? "." : "") + name;
  }

  @Nullable
  public static String getQualifiedName(@NotNull Activity activity) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    PsiClass c = activity.getActivityClass().getValue();
    return c == null ? null : c.getQualifiedName();
  }
}
