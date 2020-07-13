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
package com.android.tools.idea.uibuilder.editor;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.palette.NlPalettePanel;
import com.android.tools.idea.uibuilder.palette.ScalableDesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;

public class NlPaletteManager extends NlAbstractWindowManager {
  private NlPalettePanel myPalette;

  public NlPaletteManager(@NonNull Project project, @NonNull FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
  }

  @NonNull
  public static NlPaletteManager get(@NonNull Project project) {
    return project.getComponent(NlPaletteManager.class);
  }

  @Override
  protected void initToolWindow() {
    initToolWindow("Nl-Palette", AllIcons.Toolwindows.ToolWindowPalette);
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanelFacade designer) {
    if (designer == null) {
      myToolWindow.setAvailable(false, null);
      if (myPalette != null) {
        myPalette.setDesignSurface(null);
      }
    }
    else {
      if (myPalette == null) {
        myPalette = new NlPalettePanel(designer);
        createWindowContent(myPalette, myPalette.getFocusedComponent(), myPalette.getActions());
      }
      myPalette.setDesignSurface(getDesignSurface(designer));
      myToolWindow.setAvailable(true, null);
      myToolWindow.show(null);
    }
  }

  @Override
  protected ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.LEFT;
  }

  @NonNull
  private static DesignSurface getDesignSurface(@NonNull DesignerEditorPanelFacade designer) {
    if (designer instanceof NlEditorPanel) {
      NlEditorPanel editor = (NlEditorPanel)designer;
      return editor.getSurface();
    } else if (designer instanceof NlPreviewForm) {
      NlPreviewForm form = (NlPreviewForm)designer;
      return form.getSurface();
    }

    // Unexpected facade
    throw new RuntimeException(designer.getClass().getName());
  }

  public String getVisibilityKeyName(@NonNull DesignerEditorPanelFacade designer) {
    return getComponentName()+ "-" + designer.getClass().getSimpleName();
  }

  public void setDesignSurface(LightToolWindow toolWindow, @Nullable ScalableDesignSurface designSurface) {
    NlPalettePanel palette = (NlPalettePanel)toolWindow.getContent();
    palette.setDesignSurface(designSurface);
  }

  @Override
  protected LightToolWindow createContent(@NonNull DesignerEditorPanelFacade designer) {
    LightToolWindow toolWindow = (LightToolWindow)designer.getClientProperty(getComponentName());
    if (toolWindow != null) {
      // Avoid memory leaks for palettes created for the preview form (the palette is shared for all files).
      return toolWindow;
    }

    NlPalettePanel palette = new NlPalettePanel(designer);
    palette.setDesignSurface(getDesignSurface(designer));

    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    // When LightToolWindowManager#getEditorMode() is public (or a constructor which lets
    // me not specify it) is available and upstreamed, replace the following with just
    // anchor = getEditorMode() :
    String value = propertiesComponent.getValue(myEditorModeKey);
    ToolWindowAnchor anchor;
    if (value == null) {
      anchor = getAnchor();
    } else {
      anchor = value.equals("ToolWindow") ? null : ToolWindowAnchor.fromText(value);
    }

    return new LightToolWindow(palette, "Palette", AllIcons.Toolwindows.ToolWindowPalette, palette, palette.getFocusedComponent(),
                               designer.getContentSplitter(), anchor, this, myProject, propertiesComponent,
                               getVisibilityKeyName(designer), 180, palette.getActions());
  }

  @Override
  public void disposeComponent() {
    if (myPalette != null) {
      myPalette.dispose();
    }
  }

  @NonNull
  @Override
  public String getComponentName() {
    return "NlPaletteManager";
  }
}
