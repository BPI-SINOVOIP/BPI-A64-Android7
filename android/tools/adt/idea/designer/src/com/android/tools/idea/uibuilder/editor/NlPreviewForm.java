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
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightFillLayout;
import com.intellij.designer.LightToolWindow;
import com.intellij.designer.LightToolWindowManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class NlPreviewForm implements Disposable, CaretListener, DesignerEditorPanelFacade {
  private final NlPreviewManager myManager;
  private final DesignSurface mySurface;
  private final ThreeComponentsSplitter myContentSplitter;
  private boolean myUseInteractiveSelector = true;
  private boolean myIgnoreListener;
  private RenderResult myRenderResult;
  private XmlFile myFile;
  private TextEditor myEditor;
  private CaretModel myCaretModel;

  public NlPreviewForm(NlPreviewManager manager) {
    myManager = manager;
    mySurface = new DesignSurface(manager.getProject());
    mySurface.setCentered(true);
    mySurface.setScreenMode(DesignSurface.ScreenMode.SCREEN_ONLY);
    mySurface.addListener(new DesignSurfaceListener() {
      @Override
      public void componentSelectionChanged(@NonNull DesignSurface surface, @NonNull List<NlComponent> newSelection) {
        assert surface == mySurface; // We're maintaining the listener per surface
        // Allow only one component
        NlComponent component = newSelection.size() == 1 ? newSelection.get(0) : null;
        selectComponent(component);
      }

      @Override
      public void screenChanged(@NonNull DesignSurface surface, @Nullable ScreenView screenView) {
      }

      @Override
      public void modelChanged(@NonNull DesignSurface surface, @Nullable NlModel model) {
      }
    });

    myContentSplitter = new ThreeComponentsSplitter();

    // The {@link LightFillLayout} provides the UI for the minimized forms of the {@link LightToolWindow}
    // used for the palette and the structure/properties panes.
    JPanel contentPanel = new JPanel(new LightFillLayout());
    JComponent toolbar = NlEditorPanel.createToolbar(mySurface);
    contentPanel.add(toolbar);
    contentPanel.add(mySurface);

    myContentSplitter.setDividerWidth(0);
    myContentSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"));
    myContentSplitter.setInnerComponent(contentPanel);

    Project project = myManager.getProject();
    NlPaletteManager paletteManager = NlPaletteManager.get(project);
    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    // Keep in sync with LightToolWindow#myShowStateKey's logic
    String key = LightToolWindowManager.EDITOR_MODE + paletteManager.getVisibilityKeyName(this) + ".SHOW";
    boolean showing = properties.getBoolean(key, true);
    if (showing) {
      properties.setValue(key, Boolean.toString(false));
    }

    paletteManager.bind(this);
  }

  private void setEditor(@Nullable TextEditor editor) {
    if (editor != myEditor) {
      myEditor = editor;

      if (myCaretModel != null) {
        myCaretModel.removeCaretListener(this);
        myCaretModel = null;
      }

      if (editor != null) {
        myCaretModel = myEditor.getEditor().getCaretModel();
        myCaretModel.addCaretListener(this);
      }
    }
  }

  private void selectComponent(@Nullable NlComponent component) {
    ScreenView screenView = mySurface.getCurrentScreenView();
    if (screenView == null) {
      return;
    }

    if (myEditor != null && component != null && component.getTag().isValid() && myUseInteractiveSelector && !myIgnoreListener) {
      int offset = component.getTag().getTextOffset();
      if (offset != -1) {
        Editor editor = myEditor.getEditor();
        myIgnoreListener = true;
        try {
          screenView.getSelectionModel().setSelection(Collections.singletonList(component));
          editor.getCaretModel().moveToOffset(offset);
          editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        } finally {
          myIgnoreListener = false;
        }
      }
    }
  }
  private void updateCaret() {
    if (myCaretModel != null && !myIgnoreListener && myUseInteractiveSelector) {
      ScreenView screenView = mySurface.getCurrentScreenView();
      if (screenView != null) {
        int offset = myCaretModel.getOffset();
        if (offset != -1) {
          List<NlComponent> views = screenView.getModel().findByOffset(offset);
          if (views != null && views.size() == 1 && views.get(0).isRoot()) {
            views = null;
          }
          try {
            myIgnoreListener = true;
            SelectionModel selectionModel = screenView.getSelectionModel();
            selectionModel.setSelection(views != null ? views : Collections.<NlComponent>emptyList());
            mySurface.repaint();
          } finally {
            myIgnoreListener = false;
          }
        }
      }
    }
  }

  @Nullable
  public XmlFile getFile() {
    return myFile;
  }

  @NonNull
  public JPanel getContentPanel() {
    return myContentSplitter;
  }

  @Override
  public void dispose() {
  }

  public void setUseInteractiveSelector(boolean useInteractiveSelector) {
    this.myUseInteractiveSelector = useInteractiveSelector;
  }

  private class Pending implements ModelListener, Runnable {
    public final XmlFile file;
    public final NlModel model;
    public boolean valid = true;

    public Pending(XmlFile file, NlModel model) {
      this.file = file;
      this.model = model;
      model.addListener(this);
      model.requestRenderAsap(); // on file switches, render as soon as possible; the delay is for edits
    }

    @Override
    public void modelChanged(@NonNull NlModel model) {
    }

    @Override
    public void modelRendered(@NonNull NlModel model) {
      model.removeListener(this);
      if (valid) {
        valid = false;
        ApplicationManager.getApplication().invokeLater(this);
      }
    }

    public void invalidate() {
      valid = false;
    }

    @Override
    public void run() {
      // This method applies the given pending update to the UI thread; this must be done from a read thread
      ApplicationManager.getApplication().assertIsDispatchThread();
      setActiveModel(model);
    }
  }

  private Pending myPendingFile;

  public boolean setFile(@Nullable PsiFile file) {
    if (myPendingFile != null) {
      if (file == myPendingFile) {
        return false;
      }
      myPendingFile.invalidate();
    } else if (file == myFile) {
      return false;
    }

    AndroidFacet facet = file instanceof XmlFile ? AndroidFacet.getInstance(file) : null;
    if (facet == null || file.getVirtualFile() == null) {
      myPendingFile = null;
      myFile = null;
      setEditor(null);
    } else {
      XmlFile xmlFile = (XmlFile)file;
      NlModel model = NlModel.create(mySurface, xmlFile.getProject(), facet, xmlFile);
      model.setRenderDelay(800);
      myPendingFile = new Pending(xmlFile, model);
    }
    return true;
  }

  public void setActiveModel(@Nullable NlModel model) {
    ScreenView currentScreenView = mySurface.getCurrentScreenView();
    if (currentScreenView != null) {
      currentScreenView.getModel().deactivate();
    }

    if (model == null) {
      setEditor(null);
      myManager.setDesignSurface(null);
    } else {
      myFile = model.getFile();
      mySurface.setModel(model);
      mySurface.zoomToFit();
      setEditor(myManager.getActiveLayoutXmlEditor());
      model.activate();
      myManager.setDesignSurface(mySurface);
    }
  }

  @Nullable
  public RenderResult getRenderResult() {
    return myRenderResult;
  }

  public void setRenderResult(@NonNull RenderResult renderResult) {
    myRenderResult = renderResult;
  }

  @Nullable
  public Configuration getConfiguration() {
    ScreenView screenView = mySurface.getCurrentScreenView();
    if (screenView != null) {
      return screenView.getModel().getConfiguration();
    }
    return null;
  }

  @NonNull
  public DesignSurface getSurface() {
    return mySurface;
  }

  // ---- Implements CaretListener ----

  @Override
  public void caretPositionChanged(CaretEvent e) {
    if (!myIgnoreListener) {
      updateCaret();
      // TODO: implement
      //ActionBarHandler.showMenu(false, myContext, true);
    }
  }

  @Override
  public void caretAdded(CaretEvent e) {

  }

  @Override
  public void caretRemoved(CaretEvent e) {

  }

  /** Minimize the palette tool window, if possible */
  public void minimizePalette() {
    if (myToolWindow != null) {
      try {
        // When LightToolWindow#minimize() is added to the base platform and upstreamed,
        // replace this:
        LightToolWindow.class.getDeclaredMethod("minimize").invoke(myToolWindow);
        // with myToolWindow.minimize();
      } catch (Exception ignore) {
      }
    }
  }

  // ---- Implements DesignerEditorPanelFacade ----

  @Override
  public Object getClientProperty(Object key) {
    return myContentSplitter.getClientProperty(key);
  }

  private LightToolWindow myToolWindow;

  @Override
  public void putClientProperty(Object key, Object value) {
    Project project = myManager.getProject();
    NlPaletteManager paletteManager = NlPaletteManager.get(project);
    String paletteKey = paletteManager.getComponentName();
    myContentSplitter.putClientProperty(key, value);
    if (key.equals(paletteKey)) {
      myToolWindow = (LightToolWindow) value;
    }
  }

  @Override
  public ThreeComponentsSplitter getContentSplitter() {
    return myContentSplitter;
  }
}