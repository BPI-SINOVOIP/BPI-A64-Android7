/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Importers for different project types, e.g. ADT, Gradle.
 */
public abstract class ModuleImporter {
  private static final Key<ModuleImporter[]> KEY_IMPORTERS = new Key<ModuleImporter[]>("com.android.tools.importers");
  private static final Key<ModuleImporter> KEY_CURRENT_IMPORTER = new Key<ModuleImporter>("com.android.tools.currentImporter");
  private static final ModuleImporter NONE = new ModuleImporter() {
    @Override
    public boolean isStepVisible(ModuleWizardStep step) {
      return false;
    }

    @Override
    public List<? extends ModuleWizardStep> createWizardSteps() {
      return Collections.emptyList();
    }

    @Override
    public void importProjects(@Nullable Map<String, VirtualFile> projects) {
      LOG.error("Unsupported import kind");
    }

    @Override
    public boolean isValid() {
      return false;
    }

    @Override
    public boolean canImport(VirtualFile importSource) {
      return false;
    }

    @Override
    public Set<ModuleToImport> findModules(VirtualFile importSource) {
      return Collections.emptySet();
    }
  };
  private static Logger LOG = Logger.getInstance(ModuleImporter.class);

  /**
   * Importers live in the wizard context. This method lazily creates importers if they are
   * not already there.
   */
  @NotNull
  public static synchronized ModuleImporter[] getAllImporters(@NotNull WizardContext context) {
    ModuleImporter[] importers = context.getUserData(KEY_IMPORTERS);
    if (importers == null) {
      importers = createImporters(context);
    }
    return importers;
  }

  /**
   * Create supported importers.
   */
  // TODO: Consider creating an extension point
  @NotNull
  private static ModuleImporter[] createImporters(WizardContext context) {
    ModuleImporter[] importers = {new AdtModuleImporter(context), new GradleModuleImporter(context)};
    context.putUserData(KEY_IMPORTERS, importers);
    return importers;
  }

  /**
   * @return "headless" importers - that don't always need UI.
   */
  public static ModuleImporter[] getAllImporters(Project destinationProject) {
    return new ModuleImporter[] {new GradleModuleImporter(destinationProject)};
  }

  public static ModuleImporter getImporter(WizardContext context) {
    ModuleImporter importer = context.getUserData(KEY_CURRENT_IMPORTER);
    if (importer != null) {
      return importer;
    }
    else {
      return NONE;
    }
  }

  @NotNull
  public static ModuleImporter importerForLocation(WizardContext context, VirtualFile importSource) {
    for (ModuleImporter importer : getAllImporters(context)) {
      if (importer.canImport(importSource)) {
        return importer;
      }
    }
    return NONE;
  }

  public static void setImporter(WizardContext context, @Nullable ModuleImporter importer) {
    context.putUserData(KEY_CURRENT_IMPORTER, importer);
  }

  public abstract boolean isStepVisible(ModuleWizardStep step);

  public abstract List<? extends ModuleWizardStep> createWizardSteps();

  public abstract void importProjects(@Nullable Map<String, VirtualFile> projects);

  public abstract boolean isValid();

  public abstract boolean canImport(VirtualFile importSource);

  public abstract Set<ModuleToImport> findModules(VirtualFile importSource) throws IOException;
}
