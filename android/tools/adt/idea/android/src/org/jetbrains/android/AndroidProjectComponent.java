/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.compiler.AndroidAutogeneratorMode;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.compiler.AndroidPrecompileTask;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidResourceFilesListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidProjectComponent extends AbstractProjectComponent {
  private Disposable myDisposable;

  protected AndroidProjectComponent(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    final CompilerManager manager = CompilerManager.getInstance(myProject);
    manager.addBeforeTask(new AndroidPrecompileTask());

    myDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };

    if (!ApplicationManager.getApplication().isUnitTestMode() &&
        !ApplicationManager.getApplication().isHeadlessEnvironment()) {

      if (ProjectFacetManager.getInstance(myProject).hasFacets(AndroidFacet.ID)) {
        createAndroidSpecificComponents();
      }
      else {
        final MessageBusConnection connection = myProject.getMessageBus().connect(myDisposable);

        connection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
          @Override
          public void facetAdded(@NotNull Facet facet) {
            if (facet instanceof AndroidFacet) {
              createAndroidSpecificComponents();
              connection.disconnect();
            }
          }
        });
      }
    }
  }

  @Override
  public void projectClosed() {
    Disposer.dispose(myDisposable);
  }

  private void createAndroidSpecificComponents() {
    final AndroidResourceFilesListener listener = new AndroidResourceFilesListener(myProject);
    Disposer.register(myDisposable, listener);

    createAlarmForAutogeneration();
  }

  private void createAlarmForAutogeneration() {
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);
    alarm.addRequest(new Runnable() {
      @Override
      public void run() {
          final Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> facetsToProcess =
            new HashMap<AndroidFacet, Collection<AndroidAutogeneratorMode>>();

        final Module[] modules = ModuleManager.getInstance(myProject).getModules();
        final Module[] modulesCopy = Arrays.copyOf(modules, modules.length);

        for (Module module : modulesCopy) {
            final AndroidFacet facet = AndroidFacet.getInstance(module);

            if (facet != null && facet.isAutogenerationEnabled()) {
              final Set<AndroidAutogeneratorMode> modes = EnumSet.noneOf(AndroidAutogeneratorMode.class);

              for (AndroidAutogeneratorMode mode : AndroidAutogeneratorMode.values()) {
                if (facet.cleanRegeneratingState(mode) || facet.isGeneratedFileRemoved(mode)) {
                  modes.add(mode);
                }
              }

              if (modes.size() > 0) {
                facetsToProcess.put(facet, modes);
              }
            }
          }

          if (facetsToProcess.size() > 0) {
            generate(facetsToProcess);
        }
        if (!alarm.isDisposed()) {
          alarm.addRequest(this, 2000);
        }
      }
    }, 2000);
  }

  private void generate(final Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> facetsToProcess) {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        AndroidCompileUtil.createGenModulesAndSourceRoots(myProject, facetsToProcess.keySet());
      }
    }, ModalityState.defaultModalityState());

    for (Map.Entry<AndroidFacet, Collection<AndroidAutogeneratorMode>> entry : facetsToProcess.entrySet()) {
      final AndroidFacet facet = entry.getKey();
      final Collection<AndroidAutogeneratorMode> modes = entry.getValue();

      for (AndroidAutogeneratorMode mode : modes) {
        AndroidCompileUtil.doGenerate(facet, mode);
      }
    }
  }
}
