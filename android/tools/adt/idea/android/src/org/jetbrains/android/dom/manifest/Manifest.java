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
package org.jetbrains.android.dom.manifest;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DefinesXml;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.converters.AndroidPackageConverter;

import java.util.List;

/**
 * @author yole
 */
@DefinesXml
public interface Manifest extends ManifestElement {
  Application getApplication();

  CompatibleScreens getCompatibleScreens();

  @Convert(AndroidPackageConverter.class)
  GenericAttributeValue<String> getPackage();

  List<Instrumentation> getInstrumentations();

  List<Permission> getPermissions();

  List<PermissionGroup> getPermissionGroups();

  List<PermissionTree> getPermissionTrees();

  List<UsesPermission> getUsesPermissions();

  List<UsesSdk> getUsesSdks();

  List<UsesFeature> getUsesFeatures();
}
