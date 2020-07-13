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
package com.android.tools.idea.uibuilder.surface;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.ddms.screenshot.DeviceArtPainter;
import com.android.tools.idea.rendering.RenderErrorPanel;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.actions.SelectAllAction;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.palette.ScalableDesignSurface;
import com.google.common.collect.Lists;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.Magnificator;
import com.intellij.util.Alarm;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.Insets;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.*;

/**
 * The design surface in the layout editor, which contains the full background, rulers, one
 * or more device renderings, etc
 */
public class DesignSurface extends JPanel implements Disposable, ScalableDesignSurface {
  private static final Logger LOG = Logger.getInstance(DesignSurface.class);
  public static final boolean SIZE_ERROR_PANEL_DYNAMICALLY = true;
  private static final Integer LAYER_PROGRESS = JLayeredPane.POPUP_LAYER + 100;
  private final Project myProject;

  public enum ScreenMode {
    SCREEN_ONLY, BLUEPRINT_ONLY, BOTH;

    @NonNull
    public ScreenMode next() {
      ScreenMode[] values = values();
      return values[(ordinal() + 1) % values.length];
    }
  }

  @NonNull private ScreenMode myScreenMode = ScreenMode.SCREEN_ONLY;
  @Nullable private ScreenView myScreenView;
  @Nullable private ScreenView myBlueprintView;
  @SwingCoordinate private int myScreenX = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_X;
  @SwingCoordinate private int myScreenY = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_Y;

  private double myScale = 1;
  @NonNull private final JScrollPane myScrollPane;
  private final MyLayeredPane myLayeredPane;
  private boolean myDeviceFrames = false;
  private final List<Layer> myLayers = Lists.newArrayList();
  private final InteractionManager myInteractionManager;
  private final GlassPane myGlassPane;
  private final RenderErrorPanel myErrorPanel;
  private int myErrorPanelHeight = -1;
  private List<DesignSurfaceListener> myListeners;
  private boolean myCentered;

  public DesignSurface(@NonNull Project project) {
    super(new BorderLayout());
    myProject = project;

    setOpaque(true);
    setFocusable(true);
    setRequestFocusEnabled(true);
    setBackground(UIUtil.TRANSPARENT_COLOR);

    myInteractionManager = new InteractionManager(this);

    myLayeredPane = new MyLayeredPane();
    myLayeredPane.setBounds(0, 0, 100, 100);
    myGlassPane = new GlassPane();
    myLayeredPane.add(myGlassPane, JLayeredPane.DRAG_LAYER);

    myProgressPanel = new MyProgressPanel();
    myLayeredPane.add(myProgressPanel, LAYER_PROGRESS);

    myScrollPane = new MyScrollPane();
    myScrollPane.setViewportView(myLayeredPane);
    myScrollPane.setBorder(null);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);

    add(myScrollPane, BorderLayout.CENTER);

    myErrorPanel = new RenderErrorPanel();
    myErrorPanel.setVisible(false);
    myLayeredPane.add(myErrorPanel, JLayeredPane.POPUP_LAYER);

    // TODO: Do this as part of the layout/validate operation instead
    addComponentListener(new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent componentEvent) {
        updateScrolledAreaSize();
        if (isShowing() && getWidth() > 0 && getHeight() > 0) {
          zoomToFit();
        }
      }

      @Override
      public void componentMoved(ComponentEvent componentEvent) {
      }

      @Override
      public void componentShown(ComponentEvent componentEvent) {
      }

      @Override
      public void componentHidden(ComponentEvent componentEvent) {
      }
    });

    myInteractionManager.registerListeners();

    AnAction selectAllAction = new SelectAllAction(this);
    registerAction(selectAllAction, "$SelectAll");

    Disposer.register(project, this);
  }

  public Project getProject() {
    return myProject;
  }

  public boolean isCentered() {
    return myCentered;
  }

  public void setCentered(boolean centered) {
    myCentered = centered;
  }

  @NonNull
  public ScreenMode getScreenMode() {
    return myScreenMode;
  }

  public void setScreenMode(@NonNull ScreenMode screenMode) {
    if (screenMode != myScreenMode) {
      // If we're going from 1 screens to 2 or back from 2 to 1, must adjust the zoom
      // to-fit the screen(s) in the surface
      boolean adjustZoom = screenMode == ScreenMode.BOTH || myScreenMode == ScreenMode.BOTH;
      myScreenMode = screenMode;

      if (myScreenView != null) {
        NlModel model = myScreenView.getModel();
        setModel(null);
        setModel(model);
        if (adjustZoom) {
          zoomToFit();
        }
      }
    }
  }

  public void setModel(@Nullable NlModel model) {
    if (model == null && myScreenView == null) {
      return;
    }

    List<NlComponent> selectionBefore = Collections.emptyList();
    List<NlComponent> selectionAfter = Collections.emptyList();

    if (myScreenView != null) {
      SelectionModel selectionModel = myScreenView.getSelectionModel();
      selectionBefore = selectionModel.getSelection();
      selectionModel.removeListener(mySelectionListener);
      myScreenView = null;
    }

    myLayers.clear();
    if (model != null) {
      myScreenView = new ScreenView(this, model);

      Dimension screenSize = myScreenView.getPreferredSize();
      myLayeredPane.setPreferredSize(screenSize);

      if (myScreenMode == ScreenMode.SCREEN_ONLY) {
        myLayers.add(new ScreenViewLayer(myScreenView));
        myLayers.add(new SelectionLayer(myScreenView));
        myLayers.add(new WarningLayer(myScreenView));
      } else if (myScreenMode == ScreenMode.BOTH) {
        myBlueprintView = new ScreenView(this, model);
        assert screenSize != null;
        myBlueprintView.setLocation(myScreenX + screenSize.width + 10, myScreenY);
        myLayers.add(new ScreenViewLayer(myScreenView));
        myLayers.add(new SelectionLayer(myScreenView));
        myLayers.add(new WarningLayer(myScreenView));
        myLayers.add(new BlueprintLayer(myBlueprintView));
        myLayers.add(new SelectionLayer(myBlueprintView));
      } else if (myScreenMode == ScreenMode.BLUEPRINT_ONLY) {
        myLayers.add(new BlueprintLayer(myScreenView));
        myLayers.add(new SelectionLayer(myScreenView));
      } else {
        assert false : myScreenMode;
      }

      positionScreens();
      SelectionModel selectionModel = model.getSelectionModel();
      selectionModel.addListener(mySelectionListener);
      selectionAfter = selectionModel.getSelection();
    } else {
      myScreenView = null;
      myBlueprintView = null;
    }
    repaint();

    if (!selectionBefore.equals(selectionAfter)) {
      notifySelectionListeners(selectionAfter);
    }
    notifyScreenViewChanged();
  }

  @Override
  public void dispose() {
  }

  private void registerAction(AnAction action, @NonNls String actionId) {
    action.registerCustomShortcutSet(
      ActionManager.getInstance().getAction(actionId).getShortcutSet(),
      myLayeredPane
    );
  }

  private void updateScrolledAreaSize() {
    if (myScreenView == null) {
      return;
    }
    Dimension size = myScreenView.getPreferredSize();
    if (size != null) {
      // TODO: Account for the size of the blueprint screen too? I should figure out if I can automatically make it jump
      // to the side or below based on the form factor and the available size
      int scaledWidth = (int)(myScale * size.width);
      int scaledHeight = (int)(myScale * size.height);
      Dimension dimension = new Dimension(scaledWidth + 2 * DEFAULT_SCREEN_OFFSET_X,
                                          scaledHeight + 2 * DEFAULT_SCREEN_OFFSET_Y);
      myLayeredPane.setBounds(0, 0, dimension.width, dimension.height);
      myLayeredPane.setPreferredSize(dimension);
      myScrollPane.revalidate();
      myProgressPanel.setBounds(myScreenX, myScreenY, scaledWidth, scaledHeight);
    } else {
      myProgressPanel.setBounds(0, 0, getWidth(), getHeight());
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myGlassPane;
  }

  @Override
  protected void paintChildren(Graphics graphics) {
    super.paintChildren(graphics);

    if (isFocusOwner()) {
      graphics.setColor(UIUtil.getFocusedBoundsColor());
      graphics.drawRect(getX(), getY(), getWidth() - 1, getHeight() - 1);
    }
  }

  @Nullable
  @Override
  public ScreenView getCurrentScreenView() {
    return myScreenView;
  }

  @Nullable
  public ScreenView getScreenView(@SwingCoordinate int x, @SwingCoordinate int y) {
    // Currently only a single screen view active in the canvas.
    if (myBlueprintView != null && x >= myBlueprintView.getX() && y >= myBlueprintView.getY()) {
      return myBlueprintView;
    }
    return myScreenView;
  }

  public void hover(@SwingCoordinate int x, @SwingCoordinate int y) {
    if (myErrorPanel.isVisible() && HighlightSeverity.ERROR.equals(myErrorPanel.getSeverity())) {
      // don't show any warnings on hover if there is already some errors that are being displayed
      // TODO: we should really move this logic into the error panel itself
      return;
    }

    // Currently, we use the hover action only to check whether we need to show a warning.
    for (Layer layer : myLayers) {
      String tooltip = layer.getTooltip(x, y);
      if (tooltip != null) {
        myErrorPanel.showWarning(tooltip);
        if (!myErrorPanel.isVisible()) {
          myErrorPanel.setVisible(true);
          revalidate();
        } else {
          repaint();
        }
        break;
      }
    }
  }

  public void resetHover() {
    // if we were showing some warnings, then close it.
    // TODO: similar to hover() method above, this logic of warning/error should be inside the error panel itself
    if (HighlightSeverity.WARNING.equals(myErrorPanel.getSeverity())) {
      myErrorPanel.setVisible(false);
    }
  }

  public void zoom(@NonNull ZoomType type) {
    switch (type) {
      case IN:
        setScale(myScale * 1.1);
        repaint();
        break;
      case OUT:
        setScale(myScale * (1/1.1));
        repaint();
        break;
      case ACTUAL:
        if (SystemInfo.isMac && UIUtil.isRetina()) {
          setScale(0.5);
        } else {
          setScale(1);
        }
        repaint();
        break;
      case FIT:
      case FIT_INTO:
        if (myScreenView == null) {
          return;
        }

        // Fit to zoom
        int availableWidth = myScrollPane.getWidth();
        int availableHeight = myScrollPane.getHeight();
        Dimension preferredSize = myScreenView.getPreferredSize();
        if (preferredSize != null) {
          int requiredWidth = preferredSize.width;
          int requiredHeight = preferredSize.height;
          availableWidth -= 2 * DEFAULT_SCREEN_OFFSET_X;
          availableHeight -= 2 * DEFAULT_SCREEN_OFFSET_Y;

          if (myScreenMode == ScreenMode.BOTH) {
            if (isVerticalScreenConfig(availableWidth, availableHeight, preferredSize)) {
              requiredHeight *= 2;
              requiredHeight += SCREEN_DELTA;
            } else {
              requiredWidth *= 2;
              requiredWidth += SCREEN_DELTA;
            }
          }

          double scaleX = (double)availableWidth / requiredWidth;
          double scaleY = (double)availableHeight / requiredHeight;
          double scale = Math.min(scaleX, scaleY);
          if (type == ZoomType.FIT_INTO) {
            scale = Math.min(1.0, scale);
          }
          setScale(scale);
          repaint();
        }

        break;
      default:
      case SCREEN:
        throw new UnsupportedOperationException("Not yet implemented: " + type);
    }
  }

  public void zoomActual() {
    zoom(ZoomType.ACTUAL);
  }

  public void zoomIn() {
    zoom(ZoomType.IN);
  }

  public void zoomOut() {
    zoom(ZoomType.OUT);
  }

  public void zoomToFit() {
    zoom(ZoomType.FIT);
  }

  /** Returns true if we want to arrange screens vertically instead of horizontally */
  private static boolean isVerticalScreenConfig(int availableWidth, int availableHeight, @NonNull Dimension preferredSize) {
    boolean stackVertically = preferredSize.width > preferredSize.height;
    if (availableWidth > 10 && availableHeight > 3 * availableWidth / 2) {
      stackVertically = true;
    }
    return stackVertically;
  }

  @Override
  public double getScale() {
    return myScale;
  }

  @Override
  public Configuration getConfiguration() {
    return myScreenView != null ? myScreenView.getConfiguration() : null;
  }

  private void setScale(double scale) {
    if (Math.abs(scale - 1) < 0.0001) {
      scale = 1;
    } else if (scale < 0.01) {
      scale = 0.01;
    } else if (scale > 10) {
      scale = 10;
    }
    myScale = scale;
    positionScreens();
    updateScrolledAreaSize();
  }

  private void positionScreens() {
    if (myScreenView == null) {
      return;
    }
    Dimension preferredSize = myScreenView.getPreferredSize();
    if (preferredSize == null) {
      return;
    }
    int scaledScreenWidth = (int)(myScale * preferredSize.width);
    int scaledScreenHeight = (int)(myScale * preferredSize.height);

    // Position primary screen

    int availableWidth = myScrollPane.getWidth();
    int availableHeight = myScrollPane.getHeight();
    boolean stackVertically = isVerticalScreenConfig(availableWidth, availableHeight, preferredSize);

    if (myCentered && availableWidth > 10 && availableHeight > 10) {
      int requiredWidth = scaledScreenWidth;
      if (myScreenMode == ScreenMode.BOTH && !stackVertically) {
        requiredWidth += SCREEN_DELTA;
        requiredWidth += scaledScreenWidth;
      }
      if (requiredWidth < availableWidth) {
        myScreenX = (availableWidth - requiredWidth) / 2;
      } else {
        myScreenX = 0;
      }

      int requiredHeight = scaledScreenHeight;
      if (myScreenMode == ScreenMode.BOTH && stackVertically) {
        requiredHeight += SCREEN_DELTA;
        requiredHeight += scaledScreenHeight;
      }
      if (requiredHeight < availableHeight) {
        myScreenY = (availableHeight - requiredHeight) / 2;
      } else {
        myScreenY = 0;
      }
    } else {
      if (myDeviceFrames) {
        myScreenX = RULER_SIZE_PX + 2 * DEFAULT_SCREEN_OFFSET_X;
        myScreenY = RULER_SIZE_PX + 2 * DEFAULT_SCREEN_OFFSET_Y;
      } else {
        myScreenX = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_X;
        myScreenY = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_Y;
      }
    }
    myScreenView.setLocation(myScreenX, myScreenY);

    // Position blueprint view
    if (myBlueprintView != null) {

      if (stackVertically) {
        // top/bottom stacking
        myBlueprintView.setLocation(myScreenX, myScreenY + scaledScreenHeight + SCREEN_DELTA);
      }
      else {
        // left/right ordering
        myBlueprintView.setLocation(myScreenX + scaledScreenWidth + SCREEN_DELTA, myScreenY);
      }
    }
  }

  public void toggleDeviceFrames() {
    myDeviceFrames = !myDeviceFrames;
    positionScreens();
    repaint();
  }

  @NonNull
  public JComponent getLayeredPane() {
    return myLayeredPane;
  }

  @VisibleForTesting
  @NonNull
  public InteractionManager getInteractionManager() {
    return myInteractionManager;
  }

  private void notifySelectionListeners(@NonNull List<NlComponent> newSelection) {
    if (myListeners != null) {
      List<DesignSurfaceListener> listeners = Lists.newArrayList(myListeners);
      for (DesignSurfaceListener listener : listeners) {
        listener.componentSelectionChanged(this, newSelection);
      }
    }
  }

  private void notifyScreenViewChanged() {
    ScreenView screenView = myScreenView;
    NlModel model = myScreenView != null ? myScreenView.getModel() : null;
    if (myListeners != null) {
      List<DesignSurfaceListener> listeners = Lists.newArrayList(myListeners);
      for (DesignSurfaceListener listener : listeners) {
        listener.modelChanged(this, model);
        listener.screenChanged(this, screenView);
      }
    }
  }

  public void addListener(@NonNull DesignSurfaceListener listener) {
    if (myListeners == null) {
      myListeners = Lists.newArrayList();
    } else {
      myListeners.remove(listener); // ensure single registration
    }
    myListeners.add(listener);
  }

  public void removeListener(@NonNull DesignSurfaceListener listener) {
    if (myListeners != null) {
      myListeners.remove(listener);
    }
  }

  private final SelectionListener mySelectionListener = new SelectionListener() {
    @Override
    public void selectionChanged(@NonNull SelectionModel model, @NonNull List<NlComponent> selection) {
      if (myScreenView != null) {
        notifySelectionListeners(selection);
      } else {
        notifySelectionListeners(Collections.<NlComponent>emptyList());
      }
    }
  };

  /** The editor has been activated */
  public void activate() {
    if (myScreenView != null) {
      myScreenView.getModel().activate();
    }
  }

  public void deactivate() {
    if (myScreenView != null) {
      myScreenView.getModel().deactivate();
    }
  }

  private void positionErrorPanel() {
    if (!myErrorPanel.isVisible()) {
      return;
    }
    int height = getHeight();
    int width = getWidth();
    int size;
    if (SIZE_ERROR_PANEL_DYNAMICALLY) { // TODO: Only do this when the error panel is showing
      boolean showingErrors = HighlightSeverity.ERROR.equals(myErrorPanel.getSeverity());
      size = computeErrorPanelHeight(showingErrors, height, myErrorPanel.getPreferredHeight(width) + 16);
    } else {
      size = height / 2;
    }

    myErrorPanel.setSize(width, size);
    myErrorPanel.setLocation(RULER_SIZE_PX, height - size);
  }

  private static int computeErrorPanelHeight(boolean showingErrors, int designerHeight, int preferredHeight) {
    int maxSize = designerHeight * 3/4; // error panel can take up to 3/4th of the designer
    int minSize = showingErrors ? designerHeight / 4 : 16; // but is at least 1/4th if errors are being shown
    if (preferredHeight < maxSize) {
      return Math.max(preferredHeight, minSize);
    }
    else {
      return maxSize;
    }
  }

  private static class MyScrollPane extends JBScrollPane {
    private MyScrollPane() {
      super(0);
      setOpaque(true);
      setBackground(UIUtil.TRANSPARENT_COLOR);
      setupCorners();
    }

    @NonNull
    @Override
    public JScrollBar createVerticalScrollBar() {
      return new MyScrollBar(Adjustable.VERTICAL);
    }

    @Override
    public JScrollBar createHorizontalScrollBar() {
      return new MyScrollBar(Adjustable.HORIZONTAL);
    }

    @Override
    protected boolean isOverlaidScrollbar(@Nullable JScrollBar scrollbar) {
      ScrollBarUI vsbUI = scrollbar == null ? null : scrollbar.getUI();
      return vsbUI instanceof ButtonlessScrollBarUI && !((ButtonlessScrollBarUI)vsbUI).alwaysShowTrack();
    }
  }

  private static final Field decrButtonField = ReflectionUtil.getDeclaredField(BasicScrollBarUI.class, "decrButton");
  private static final Field incrButtonField = ReflectionUtil.getDeclaredField(BasicScrollBarUI.class, "incrButton");

  private static class MyScrollBar extends JBScrollBar implements IdeGlassPane.TopComponent {
    @NonNls private static final String APPLE_LAF_AQUA_SCROLL_BAR_UI_CLASS = "apple.laf.AquaScrollBarUI";
    private ScrollBarUI myPersistentUI;

    private MyScrollBar(@JdkConstants.AdjustableOrientation int orientation) {
      super(orientation);
      setOpaque(false);
    }

    void setPersistentUI(ScrollBarUI ui) {
      myPersistentUI = ui;
      setUI(ui);
    }

    @Override
    public boolean canBePreprocessed(MouseEvent e) {
      return JBScrollPane.canBePreprocessed(e, this);
    }

    @Override
    public void setUI(ScrollBarUI ui) {
      if (myPersistentUI == null) myPersistentUI = ui;
      super.setUI(myPersistentUI);
      setOpaque(false);
    }

    /**
     * This is helper method. It returns h of the top (decrease) scroll bar
     * button. Please note, that it's possible to return real h only if scroll bar
     * is instance of BasicScrollBarUI. Otherwise it returns fake (but good enough :) )
     * value.
     */
    int getDecScrollButtonHeight() {
      ScrollBarUI barUI = getUI();
      Insets insets = getInsets();
      int top = Math.max(0, insets.top);
      if (barUI instanceof ButtonlessScrollBarUI) {
        return top + ((ButtonlessScrollBarUI)barUI).getDecrementButtonHeight();
      }
      if (barUI instanceof BasicScrollBarUI) {
        try {
          JButton decrButtonValue = (JButton)decrButtonField.get(barUI);
          LOG.assertTrue(decrButtonValue != null);
          return top + decrButtonValue.getHeight();
        }
        catch (Exception exc) {
          throw new IllegalStateException(exc);
        }
      }
      return top + 15;
    }

    /**
     * This is helper method. It returns h of the bottom (increase) scroll bar
     * button. Please note, that it's possible to return real h only if scroll bar
     * is instance of BasicScrollBarUI. Otherwise it returns fake (but good enough :) )
     * value.
     */
    int getIncScrollButtonHeight() {
      ScrollBarUI barUI = getUI();
      Insets insets = getInsets();
      if (barUI instanceof ButtonlessScrollBarUI) {
        return insets.top + ((ButtonlessScrollBarUI)barUI).getIncrementButtonHeight();
      }
      if (barUI instanceof BasicScrollBarUI) {
        try {
          JButton incrButtonValue = (JButton)incrButtonField.get(barUI);
          LOG.assertTrue(incrButtonValue != null);
          return insets.bottom + incrButtonValue.getHeight();
        }
        catch (Exception exc) {
          throw new IllegalStateException(exc);
        }
      }
      if (APPLE_LAF_AQUA_SCROLL_BAR_UI_CLASS.equals(barUI.getClass().getName())) {
        return insets.bottom + 30;
      }
      return insets.bottom + 15;
    }

    @Override
    public int getUnitIncrement(int direction) {
      return 5;
    }

    @Override
    public int getBlockIncrement(int direction) {
      return 1;
    }
  }

  private class MyLayeredPane extends JLayeredPane implements Magnificator {
    public MyLayeredPane() {
      setOpaque(true);
      setBackground(UIUtil.TRANSPARENT_COLOR);

      // Enable pinching to zoom
      putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, this);
    }

    // ---- Implements Magnificator ----

    @Override
    public Point magnify(double scale, Point at) {
      // Handle screen zooming.
      // Note: This only seems to work (be invoked) on Mac with the Apple JDK (1.6) currently
      setScale(scale * myScale);
      DesignSurface.this.repaint();
      return new Point((int)(at.x * scale), (int)(at.y * scale));
    }

    @Override
    protected void paintComponent(@NonNull Graphics graphics) {
      super.paintComponent(graphics);

      if (myScreenView == null) {
        return;
      }

      Graphics2D g2d = (Graphics2D)graphics;
      // (x,y) coordinates of the top left corner in the view port
      int tlx = myScrollPane.getHorizontalScrollBar().getValue();
      int tly = myScrollPane.getVerticalScrollBar().getValue();

      paintBackground(g2d, tlx, tly);

      Composite oldComposite = g2d.getComposite();

      RenderResult result = myScreenView.getResult();
      boolean paintedFrame = false;
      if (myDeviceFrames && result != null && result.getRenderedImage() != null) {
        Configuration configuration = myScreenView.getConfiguration();
        Device device = configuration.getDevice();
        State deviceState = configuration.getDeviceState();
        DeviceArtPainter painter = DeviceArtPainter.getInstance();
        if (device != null && painter.hasDeviceFrame(device) && deviceState != null) {
          paintedFrame = true;
          g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
          painter.paintFrame(g2d, device, deviceState.getOrientation(), true, myScreenX, myScreenY,
                             (int)(myScale * result.getRenderedImage().getHeight()));
        }
      }

      if (paintedFrame) {
        // Only use alpha on the ruler bar if overlaying the device art
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
      } else {
        // Only show bounds dashed lines when there's no device
        paintBoundsRectangle(g2d);
      }

      g2d.setComposite(oldComposite);

      for (Layer layer : myLayers) {
        if (!layer.isHidden()) {
          layer.paint(g2d);
        }
      }

      // Temporary overlays:
      List<Layer> layers = myInteractionManager.getLayers();
      if (layers != null) {
        for (Layer layer : layers) {
          if (!layer.isHidden()) {
            layer.paint(g2d);
          }
        }
      }
    }

    private void paintBackground(@NonNull Graphics2D graphics, int lx, int ly) {
      int width = myScrollPane.getWidth() - RULER_SIZE_PX;
      int height = myScrollPane.getHeight() - RULER_SIZE_PX;
      graphics.setColor(DESIGN_SURFACE_BG);
      graphics.fillRect(RULER_SIZE_PX + lx, RULER_SIZE_PX + ly, width, height);
    }

    private void paintRulers(@NonNull Graphics2D g, int lx, int ly) {
      final Graphics2D graphics = (Graphics2D)g.create();
      try {
        int width = myScrollPane.getWidth();
        int height = myScrollPane.getHeight();

        graphics.setColor(RULER_BG);
        graphics.fillRect(lx, ly, width, RULER_SIZE_PX);
        graphics.fillRect(lx, ly + RULER_SIZE_PX, RULER_SIZE_PX, height - RULER_SIZE_PX);

        graphics.setColor(RULER_TICK_COLOR);

        int x = myScreenX + lx - lx % 100;
        int px2 = x + 10 - 100;
        for (int i = 1; i < 10; i++, px2 += 10) {
          if (px2 < myScreenX + lx - 100) {
            continue;
          }
          graphics.drawLine(px2, ly, px2, ly + RULER_MINOR_TICK_PX);
        }
        // TODO: The rulers need to be updated to track the scale!!!

        for (int px = 0; px < width; px += 100, x += 100) {
          graphics.drawLine(x, ly, x, ly + RULER_MAJOR_TICK_PX);
          px2 = x + 10;
          for (int i = 1; i < 10; i++, px2 += 10) {
            graphics.drawLine(px2, ly, px2, ly + RULER_MINOR_TICK_PX);
          }
        }

        int y = myScreenY + ly - ly % 100;
        int py2 = y + 10 - 100;
        for (int i = 1; i < 10; i++, py2 += 10) {
          if (py2 < myScreenY + ly - 100) {
            continue;
          }
          graphics.drawLine(lx, py2, lx + RULER_MINOR_TICK_PX, py2);
        }
        for (int py = 0; py < height; py += 100, y += 100) {
          graphics.drawLine(lx, y, lx + RULER_MAJOR_TICK_PX, y);
          py2 = y + 10;
          for (int i = 1; i < 10; i++, py2 += 10) {
            graphics.drawLine(lx, py2, lx + RULER_MINOR_TICK_PX, py2);
          }
        }

        graphics.setColor(RULER_TEXT_COLOR);
        graphics.setFont(RULER_TEXT_FONT);
        int xDelta = lx - lx % 100;
        x = myScreenX + 2 + xDelta;
        for (int px = 0; px < width; px += 100, x += 100) {
          graphics.drawString(Integer.toString(px + xDelta), x, ly + RULER_MAJOR_TICK_PX);
        }

        graphics.rotate(-Math.PI / 2);
        int yDelta = ly - ly % 100;
        y = myScreenY - 2 + yDelta;
        for (int py = 0; py < height; py += 100, y += 100) {
          graphics.drawString(Integer.toString(py + yDelta), -y, lx + RULER_MAJOR_TICK_PX);
        }
      }
      finally {
        graphics.dispose();
      }
    }

    private void paintBoundsRectangle(Graphics2D g2d) {
      if (myScreenView == null) {
        return;
      }

      g2d.setColor(BOUNDS_RECT_COLOR);
      int x = myScreenX;
      int y = myScreenY;
      Dimension preferredSize = myScreenView.getPreferredSize();
      if (preferredSize == null) {
        return;
      }
      double scale = myScreenView.getScale();
      int width = (int)(scale * preferredSize.width);
      int height = (int)(scale * preferredSize.height);

      Stroke prevStroke = g2d.getStroke();
      g2d.setStroke(DASHED_STROKE);

      g2d.drawLine(x - 1, y - BOUNDS_RECT_DELTA, x - 1, y + height + BOUNDS_RECT_DELTA);
      g2d.drawLine(x - BOUNDS_RECT_DELTA, y - 1, x + width + BOUNDS_RECT_DELTA, y - 1);
      g2d.drawLine(x + width, y - BOUNDS_RECT_DELTA, x + width, y + height + BOUNDS_RECT_DELTA);
      g2d.drawLine(x - BOUNDS_RECT_DELTA, y + height, x + width + BOUNDS_RECT_DELTA, y + height);

      g2d.setStroke(prevStroke);
    }

    @Override
    protected void paintChildren(@NonNull Graphics graphics) {
      super.paintChildren(graphics); // paints the screen

      // Paint rulers on top of whatever is under the scroll panel

      Graphics2D g2d = (Graphics2D)graphics;
      // (x,y) coordinates of the top left corner in the view port
      int tlx = myScrollPane.getHorizontalScrollBar().getValue();
      int tly = myScrollPane.getVerticalScrollBar().getValue();
      paintRulers(g2d, tlx, tly);
    }

    @Override
    public void doLayout() {
      super.doLayout();
      positionErrorPanel();
    }
  }

  /**
   * Notifies the design surface that the given screenview (which must be showing in this design surface)
   * has been rendered (possibly with errors)
   */
  public void updateErrorDisplay(@NonNull ScreenView view, @Nullable final RenderResult result) {
    if (view == myScreenView) {
      getErrorQueue().cancelAllUpdates();
      boolean hasProblems = result != null && result.getLogger().hasProblems();
      if (hasProblems != myErrorPanel.isVisible()) {
        if (hasProblems) {
          myErrorPanelHeight = -1;
          updateErrors(result);
          myErrorPanel.showErrors(result);
        } else {
          myErrorPanel.setVisible(false);
          repaint();
        }
      }
    }
  }

  /** When we have render errors for a given result, kick off a background computation
   * of the error panel HTML, which when done will update the UI thread */
  private void updateErrors(@Nullable final RenderResult result) {
    assert result != null && result.getLogger().hasProblems();

    getErrorQueue().cancelAllUpdates();
    getErrorQueue().queue(new Update("errors") {
      @Override
      public void run() {
        // Look up *current* result; a newer one could be available
        final RenderResult result = myScreenView != null ? myScreenView.getResult() : null;
        boolean hasProblems = result != null && result.getLogger().hasProblems();
        final String html = hasProblems ? myErrorPanel.generateHtml(result, result.getLogger().getLinkManager()) : null;
        if (hasProblems) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myErrorPanel.showErrors(html, result, result.getLogger().getLinkManager());
              if (!myErrorPanel.isVisible()) {
                myErrorPanel.setVisible(true);
                revalidate();
              } else {
                repaint();
              }
            }
          });
        }
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  @NonNull
  private MergingUpdateQueue getErrorQueue() {
    synchronized (myErrorQueueLock) {
      if (myErrorQueue == null) {
        myErrorQueue = new MergingUpdateQueue("android.error.computation", 200, true, null, myProject, null,
                                                  Alarm.ThreadToUse.POOLED_THREAD);
      }
      return myErrorQueue;
    }
  }

  private final Object myErrorQueueLock = new Object();
  private MergingUpdateQueue myErrorQueue;

  private static class GlassPane extends JComponent {
    private static final long EVENT_FLAGS = AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK;

    public GlassPane() {
      enableEvents(EVENT_FLAGS);
    }

    @Override
    public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      if (enabled) {
        enableEvents(EVENT_FLAGS);
      }
      else {
        disableEvents(EVENT_FLAGS);
      }
    }

    @Override
    protected void processKeyEvent(KeyEvent event) {
      if (!event.isConsumed()) {
        super.processKeyEvent(event);
      }
    }

    @Override
    protected void processMouseEvent(MouseEvent event) {
      if (event.getID() == MouseEvent.MOUSE_PRESSED) {
        requestFocusInWindow();
      }

      super.processMouseEvent(event);
    }
  }

  private final List<ProgressIndicator> myProgressIndicators = new ArrayList<ProgressIndicator>();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private final MyProgressPanel myProgressPanel;

  public synchronized void registerIndicator(@NonNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.add(indicator);
      myProgressPanel.showProgressIcon();
    }
  }

  public void unregisterIndicator(@NonNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.remove(indicator);

      if (myProgressIndicators.size() == 0) {
        myProgressPanel.hideProgressIcon();
      }
    }
  }

  /**
   * Panel which displays the progress icon. The progress icon can either be a large icon in the
   * center, when there is no rendering showing, or a small icon in the upper right corner when there
   * is a rendering. This is necessary because even though the progress icon looks good on some
   * renderings, depending on the layout theme colors it is invisible in other cases.
   */
  private class MyProgressPanel extends JPanel {
    private AsyncProcessIcon mySmallProgressIcon;
    private AsyncProcessIcon myLargeProgressIcon;
    private boolean mySmall;
    private boolean myProgressVisible;

    private MyProgressPanel() {
      super(new BorderLayout());
      setOpaque(false);
    }

    /** The "small" icon mode isn't just for the icon size; it's for the layout position too; see {@link #doLayout} */
    private void setSmallIcon(boolean small) {
      if (small != mySmall) {
        if (myProgressVisible && getComponentCount() != 0) {
          AsyncProcessIcon oldIcon = getProgressIcon();
          oldIcon.suspend();
        }
        mySmall = true;
        removeAll();
        AsyncProcessIcon icon = getProgressIcon();
        add(icon, BorderLayout.CENTER);
        if (myProgressVisible) {
          icon.setVisible(true);
          icon.resume();
        }
      }
    }

    public void showProgressIcon() {
      if (!myProgressVisible) {
        boolean hasResult = myScreenView != null && myScreenView.getResult() != null;
        setSmallIcon(hasResult);
        myProgressVisible = true;
        setVisible(true);
        AsyncProcessIcon icon = getProgressIcon();
        if (getComponentCount() == 0) { // First time: haven't added icon yet?
          add(getProgressIcon(), BorderLayout.CENTER);
        } else {
          icon.setVisible(true);
        }
        icon.resume();
      }
    }

    public void hideProgressIcon() {
      if (myProgressVisible) {
        myProgressVisible = false;
        setVisible(false);
        AsyncProcessIcon icon = getProgressIcon();
        icon.setVisible(false);
        icon.suspend();
      }
    }

    @Override
    public void doLayout() {
      super.doLayout();
      setBackground(Color.RED);

      if (!myProgressVisible) {
        return;
      }

      // Place the progress icon in the center if there's no rendering, and in the
      // upper right corner if there's a rendering. The reason for this is that the icon color
      // will depend on whether we're in a light or dark IDE theme, and depending on the rendering
      // in the layout it will be invisible. For example, in Darcula the icon is white, and if the
      // layout is rendering a white screen, the progress is invisible.
      AsyncProcessIcon icon = getProgressIcon();
      Dimension size = icon.getPreferredSize();
      if (mySmall) {
        icon.setBounds(getWidth() - size.width - 1, 1, size.width, size.height);
      } else {
        icon.setBounds(getWidth() / 2 - size.width / 2, getHeight() / 2 - size.height / 2, size.width, size.height);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return getProgressIcon().getPreferredSize();
    }

    @NonNull
    private AsyncProcessIcon getProgressIcon() {
      return getProgressIcon(mySmall);
    }

    @NonNull
    private AsyncProcessIcon getProgressIcon(boolean small) {
      if (small) {
        if (mySmallProgressIcon == null) {
          mySmallProgressIcon = new AsyncProcessIcon("Android layout rendering");
          Disposer.register(myProject, mySmallProgressIcon);
        }
        return mySmallProgressIcon;
      }
      else {
        if (myLargeProgressIcon == null) {
          myLargeProgressIcon = new AsyncProcessIcon.Big("Android layout rendering");
          Disposer.register(myProject, myLargeProgressIcon);
        }
        return myLargeProgressIcon;
      }
    }
  }
}
