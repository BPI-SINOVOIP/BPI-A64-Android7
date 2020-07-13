/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.android.uipreview;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ColorPickerListener;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.HintHint;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;

import java.awt.AWTException;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.LinearGradientPaint;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.MemoryImageSource;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * This is just a copy of {@link com.intellij.ui.ColorPicker}, but with support
 * for ARGB. There is nothing Android specific about this, so these changes
 * have been submitted as a patch for the base ColorPicker in
 * https://youtrack.jetbrains.com/issue/IDEA-123498
 * and if that's applied we can get rid of this class and use the base platform
 * one instead.
 */
public class ColorPicker extends JPanel implements ColorListener, DocumentListener {
  private static final String COLOR_CHOOSER_COLORS_KEY = "ColorChooser.RecentColors";
  private static final String HSB_PROPERTY = "color.picker.is.hsb";

  private Color myColor;
  private ColorPreviewComponent myPreviewComponent;
  private final ColorSelectionPanel myColorSelectionPanel;
  private final JTextField myAlpha;
  private final JTextField myRed;
  private final JTextField myGreen;
  private final JTextField myBlue;
  private final JTextField myHex;
  private final Alarm myUpdateQueue;
  private final ColorPickerListener[] myExternalListeners;

  private final boolean myOpacityInPercent;

  private RecentColorsComponent myRecentColorsComponent;
  private final ColorPipette myPicker;
  private final JLabel myA = new JLabel("A:");
  private final JLabel myR = new JLabel("R:");
  private final JLabel myG = new JLabel("G:");
  private final JLabel myB = new JLabel("B:");
  private final JLabel myR_after = new JLabel("");
  private final JLabel myG_after = new JLabel("");
  private final JLabel myB_after = new JLabel("");
  private final JLabel myHexLabel = new JLabel("#");
  private final JComboBox myFormat = new JComboBox() {
    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      UIManager.LookAndFeelInfo info = LafManager.getInstance().getCurrentLookAndFeel();
      if (info != null && info.getName().contains("Windows"))
        size.width += 10;
      return size;
    }
  };

  public ColorPicker(@NotNull Disposable parent, @Nullable Color color, boolean enableOpacity, ColorPickerListener... listeners) {
    this(parent, color, true, enableOpacity, listeners, false);
  }

  private ColorPicker(Disposable parent,
                      @Nullable Color color,
                      boolean restoreColors, boolean enableOpacity,
                      ColorPickerListener[] listeners, boolean opacityInPercent) {
    myUpdateQueue = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parent);
    myAlpha = createColorField(false);
    myRed = createColorField(false);
    myGreen = createColorField(false);
    myBlue = createColorField(false);
    myHex = createColorField(true);
    myOpacityInPercent = opacityInPercent;
    myA.setLabelFor(myAlpha);
    myR.setLabelFor(myRed);
    myG.setLabelFor(myGreen);
    myB.setLabelFor(myBlue);
    myHexLabel.setLabelFor(myHex);
    setLayout(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));

    DefaultComboBoxModel model = new DefaultComboBoxModel(new String[]{"RGB", "HSB"});
    if (enableOpacity) {
      model.addElement("ARGB");
    }
    myFormat.setModel(model);

    myColorSelectionPanel = new ColorSelectionPanel(this, enableOpacity, myOpacityInPercent);

    myExternalListeners = listeners;
    myFormat.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PropertiesComponent.getInstance().setValue(HSB_PROPERTY, String.valueOf(!isRGBMode()));
        myA.setVisible(isARGBMode());
        myAlpha.setVisible(isARGBMode());
        myR.setText(isRGBMode() ? "R:" : "H:");
        myG.setText(isRGBMode() ? "G:" : "S:");
        myR_after.setText(isRGBMode() ? "" : "\u00B0");
        myG.setText(isRGBMode() ? "G:" : "S:");
        myG_after.setText(isRGBMode() ? "" : "%");
        myB_after.setText(isRGBMode() ? "" : "%");
        applyColor(myColor);
        applyColorToHEX(myColor);
      }
    });

    myPicker = new ColorPipette(this, getColor());
    myPicker.setListener(new ColorListener() {
      @Override
      public void colorChanged(Color color, Object source) {
        setColor(color, source);
      }
    });
    try {
      add(buildTopPanel(true), BorderLayout.NORTH);
      add(myColorSelectionPanel, BorderLayout.CENTER);

      myRecentColorsComponent = new RecentColorsComponent(new ColorListener() {
        @Override
        public void colorChanged(Color color, Object source) {
          setColor(color, source);
        }
      }, restoreColors);

      add(myRecentColorsComponent, BorderLayout.SOUTH);
    }
    catch (ParseException ignore) {
    }

    Color c = color == null ? myRecentColorsComponent.getMostRecentColor() : color;
    if (c == null) {
      c = Color.WHITE;
    }
    setColor(c, this);

    setSize(300, 350);

    final boolean hsb = PropertiesComponent.getInstance().getBoolean(HSB_PROPERTY, false);
    if (hsb) {
      myFormat.setSelectedIndex(1);
    }
  }

  /** RGB or ARGB mode */
  private boolean isRGBMode() {
    return myFormat.getSelectedIndex() == 0 || isARGBMode();
  }

  private boolean isARGBMode() {
    return myFormat.getSelectedIndex() == 2;
  }

  /** Pick colors in RGB mode */
  public void pickRGB() {
    myFormat.setSelectedIndex(0);
  }

  /** Pick colors in HSB mode */
  public void pickHSB() {
    myFormat.setSelectedIndex(1);
  }

  /** Pick colors in ARGB mode. Only valid if the color picker was constructed with enableOpacity=true. */
  public void pickARGB() {
    myFormat.setSelectedIndex(2);
  }

  private JTextField createColorField(boolean hex) {
    final NumberDocument doc = new NumberDocument(hex);
    int lafFix = UIUtil.isUnderWindowsLookAndFeel() || UIUtil.isUnderDarcula() ? 1 : 0;
    UIManager.LookAndFeelInfo info = LafManager.getInstance().getCurrentLookAndFeel();
    if (info != null && (info.getName().startsWith("IDEA") || info.getName().equals("Windows Classic")))
      lafFix = 1;
    final JTextField field = new JTextField(doc, "", (hex ? 5:2) + lafFix);
    field.setSize(50, -1);
    doc.setSource(field);
    field.getDocument().addDocumentListener(this);
    field.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(final FocusEvent e) {
        field.selectAll();
      }
    });
    return field;
  }

  public JComponent getPreferredFocusedComponent() {
    return myHex;
  }

  private void setColor(Color color, Object src) {
    colorChanged(color, src);
    myColorSelectionPanel.setColor(color, src);
  }

  public void appendRecentColor() {
    myRecentColorsComponent.appendColor(myColor);
  }

  public void saveRecentColors() {
    myRecentColorsComponent.saveColors();
  }

  public Color getColor() {
    return myColor;
  }

  @Override
  public void insertUpdate(DocumentEvent e) {
    update(((NumberDocument)e.getDocument()).mySrc);
  }

  private void update(final JTextField src) {
    myUpdateQueue.cancelAllRequests();
    myUpdateQueue.addRequest(new Runnable() {
      @Override
      public void run() {
        validateAndUpdatePreview(src);
      }
    }, 300);
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    update(((NumberDocument)e.getDocument()).mySrc);
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    // ignore
  }

  private void validateAndUpdatePreview(JTextField src) {
    Color color;
    if (myHex.hasFocus()) {
      if (isARGBMode()) { // Read alpha from the text string itself
        // ColorUtil.fromHex only handles opaque colors
        String text = myHex.getText();
        int rgbIndex = Math.max(0, text.length() - 6);
        String rgb = text.substring(rgbIndex);
        String alphaText = text.substring(0, rgbIndex);
        int alpha = alphaText.isEmpty() ? 255 : Integer.parseInt(alphaText, 16);
        Color c = ColorUtil.fromHex(rgb, null);
        color = c != null ? ColorUtil.toAlpha(c, alpha) : null;
      } else {
        Color c = ColorUtil.fromHex(myHex.getText(), null);
        color = c != null ? ColorUtil.toAlpha(c, myColorSelectionPanel.mySaturationBrightnessComponent.myOpacity) : null;
      }
    } else {
      color = gatherRGB();
    }
    if (color != null) {
      myColorSelectionPanel.myOpacityComponent.setColor(color);
      if (myAlpha.hasFocus()) {
        myColorSelectionPanel.myOpacityComponent.setValue(color.getAlpha());
      } else if (myColorSelectionPanel.myOpacityComponent != null && !isARGBMode()) {
        color = ColorUtil.toAlpha(color, myColorSelectionPanel.myOpacityComponent.getValue());
      }
      myColorSelectionPanel.myOpacityComponent.repaint();
      updatePreview(color, src == myHex);
    }
  }

  private void updatePreview(Color color, boolean fromHex) {
    if (color != null && !color.equals(myColor)) {
      myColor = color;
      myPreviewComponent.setColor(color);
      myColorSelectionPanel.setColor(color, fromHex ? myHex : null);


      if (fromHex) {
        applyColor(color);
      } else {
        applyColorToHEX(color);
      }

      fireColorChanged(color);
    }
  }

  @Override
  public void colorChanged(Color color, Object source) {
    if (color != null && !color.equals(myColor)) {
      myColor = color;

      applyColor(color);

      if (source != myHex) {
        applyColorToHEX(color);
      }
      myPreviewComponent.setColor(color);
      fireColorChanged(color);
    }
  }

  private void fireColorChanged(Color color) {
    if (myExternalListeners == null) return;
    for (ColorPickerListener listener : myExternalListeners) {
      listener.colorChanged(color);
    }
  }

  private void fireClosed(@Nullable Color color) {
    if (myExternalListeners == null) return;
    for (ColorPickerListener listener : myExternalListeners) {
      listener.closed(color);
    }
  }

  @SuppressWarnings("UseJBColor")
  @Nullable
  private Color gatherRGB() {
    try {
      final int r = Integer.parseInt(myRed.getText());
      final int g = Integer.parseInt(myGreen.getText());
      final int b = Integer.parseInt(myBlue.getText());
      final int a = Integer.parseInt(myAlpha.getText());

      return isRGBMode() ? new Color(r, g, b, a) : new Color(Color.HSBtoRGB(((float)r) / 360f, ((float)g) / 100f, ((float)b) / 100f));
    } catch (Exception ignore) {
    }
    return null;
  }

  private void applyColorToHEX(final Color c) {
    if (isARGBMode()) {
      myHex.setText(String.format("%08X", c.getRGB()));
    } else {
      myHex.setText(String.format("%06X", (0xFFFFFF & c.getRGB())));
    }
  }

  private void applyColorToRGB(final Color color) {
    myAlpha.setText(String.valueOf(color.getAlpha()));
    myRed.setText(String.valueOf(color.getRed()));
    myGreen.setText(String.valueOf(color.getGreen()));
    myBlue.setText(String.valueOf(color.getBlue()));
  }

  private void applyColorToHSB(final Color c) {
    myAlpha.setText(String.valueOf(c.getAlpha()));
    final float[] hbs = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    myRed.setText(String.valueOf(((int)(360f * hbs[0]))));
    myGreen.setText(String.valueOf(((int)(100f * hbs[1]))));
    myBlue.setText(String.valueOf(((int)(100f * hbs[2]))));
  }

  private void applyColor(final Color color) {
    if (isRGBMode()) {
      applyColorToRGB(color);
    } else {
      applyColorToHSB(color);
    }
  }

  @Nullable
  public static Color showDialog(Component parent,
                                 String caption,
                                 @Nullable Color preselectedColor,
                                 boolean enableOpacity,
                                 @Nullable ColorPickerListener[] listeners,
                                 boolean opacityInPercent) {
    final ColorPickerDialog dialog = new ColorPickerDialog(parent, caption, preselectedColor, enableOpacity, listeners, opacityInPercent);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      return dialog.getColor();
    }

    return null;
  }

  private JComponent buildTopPanel(boolean enablePipette) throws ParseException {
    final JPanel result = new JPanel(new BorderLayout());

    final JPanel previewPanel = new JPanel(new BorderLayout());
    if (enablePipette && ColorPipette.isAvailable()) {
      final JButton pipette = new JButton();
      pipette.setUI(new BasicButtonUI());
      pipette.setRolloverEnabled(true);
      pipette.setIcon(AllIcons.Ide.Pipette);
      pipette.setBorder(IdeBorderFactory.createEmptyBorder());
      pipette.setRolloverIcon(AllIcons.Ide.Pipette_rollover);
      pipette.setFocusable(false);
      pipette.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myPicker.myOldColor = getColor();
          myPicker.pick();
          //JBPopupFactory.getInstance().createBalloonBuilder(new JLabel("Press ESC button to close pipette"))
          //  .setAnimationCycle(2000)
          //  .setSmallVariant(true)
          //  .createBalloon().show(new RelativePoint(pipette, new Point(pipette.getWidth() / 2, 0)), Balloon.Position.above);
        }
      });
      previewPanel.add(pipette, BorderLayout.WEST);
    }

    myPreviewComponent = new ColorPreviewComponent();
    previewPanel.add(myPreviewComponent, BorderLayout.CENTER);

    result.add(previewPanel, BorderLayout.NORTH);

    final JPanel rgbPanel = new JPanel();
    rgbPanel.setLayout(new BoxLayout(rgbPanel, BoxLayout.X_AXIS));
    if (!UIUtil.isUnderAquaLookAndFeel()) {
      myR_after.setPreferredSize(new Dimension(14, -1));
      myG_after.setPreferredSize(new Dimension(14, -1));
      myB_after.setPreferredSize(new Dimension(14, -1));
    }
    rgbPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    rgbPanel.add(myA);
    rgbPanel.add(myAlpha);
    myA.setVisible(isARGBMode());
    myAlpha.setVisible(isARGBMode());
    if (!UIUtil.isUnderAquaLookAndFeel()) rgbPanel.add(myR_after);
    rgbPanel.add(Box.createHorizontalStrut(2));
    rgbPanel.add(myR);
    rgbPanel.add(myRed);
    if (!UIUtil.isUnderAquaLookAndFeel()) rgbPanel.add(myR_after);
    rgbPanel.add(Box.createHorizontalStrut(2));
    rgbPanel.add(myG);
    rgbPanel.add(myGreen);
    if (!UIUtil.isUnderAquaLookAndFeel()) rgbPanel.add(myG_after);
    rgbPanel.add(Box.createHorizontalStrut(2));
    rgbPanel.add(myB);
    rgbPanel.add(myBlue);
    if (!UIUtil.isUnderAquaLookAndFeel()) rgbPanel.add(myB_after);
    rgbPanel.add(Box.createHorizontalStrut(2));
    rgbPanel.add(myFormat);

    result.add(rgbPanel, BorderLayout.WEST);

    final JPanel hexPanel = new JPanel();
    hexPanel.setLayout(new BoxLayout(hexPanel, BoxLayout.X_AXIS));
    hexPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    hexPanel.add(myHexLabel);
    hexPanel.add(myHex);

    result.add(hexPanel, BorderLayout.EAST);

    return result;
  }

  private static class ColorSelectionPanel extends JPanel {
    private SaturationBrightnessComponent mySaturationBrightnessComponent;
    private HueSlideComponent myHueComponent;
    private SlideComponent myOpacityComponent = null;

    private ColorSelectionPanel(ColorListener listener, boolean enableOpacity, boolean opacityInPercent) {
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
      setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

      mySaturationBrightnessComponent = new SaturationBrightnessComponent();
      add(mySaturationBrightnessComponent);

      mySaturationBrightnessComponent.addListener(listener);

      myHueComponent = new HueSlideComponent("Hue");
      myHueComponent.setToolTipText("Hue");
      myHueComponent.addListener(new Consumer<Integer>() {
        @Override
        public void consume(Integer value) {
          mySaturationBrightnessComponent.setHue(value.intValue() / 255.0f);
          mySaturationBrightnessComponent.repaint();
        }
      });

      add(myHueComponent);

      if (enableOpacity) {
        myOpacityComponent = new SlideComponent("Opacity", false);
        myOpacityComponent.setUnits(opacityInPercent ? SlideComponent.Unit.PERCENT : SlideComponent.Unit.LEVEL);
        myOpacityComponent.setToolTipText("Opacity");
        myOpacityComponent.addListener(new Consumer<Integer>() {
          @Override
          public void consume(Integer integer) {
            mySaturationBrightnessComponent.setOpacity(integer.intValue());
            mySaturationBrightnessComponent.repaint();
          }
        });

        add(myOpacityComponent);
      }
    }

    public void setColor(Color color, Object source) {
      float[] hsb = new float[3];
      Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);

      myHueComponent.setValue((int)(hsb[0] * 255));
      myHueComponent.repaint();

      mySaturationBrightnessComponent.dropImage();
      if (myOpacityComponent != null) {
        myOpacityComponent.setColor(color);
        if (source instanceof ColorPicker) {
          myOpacityComponent.setValue(color.getAlpha());
          myOpacityComponent.repaint();
        }
      }
      mySaturationBrightnessComponent.setColor(color, source);
    }
  }

  static class SaturationBrightnessComponent extends JComponent {
    private static final int BORDER_SIZE = 5;
    private float myBrightness = 1f;
    private float myHue = 1f;
    private float mySaturation = 0f;

    private Image myImage;
    private Rectangle myComponent;

    private Color myColor;

    private final List<ColorListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
    private int myOpacity;

    protected SaturationBrightnessComponent() {
      setOpaque(true);

      addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
          final Dimension size = getSize();
          final int x = Math.max(Math.min(e.getX(), size.width - BORDER_SIZE), BORDER_SIZE) - BORDER_SIZE;
          final int y = Math.max(Math.min(e.getY(), size.height - BORDER_SIZE), BORDER_SIZE) - BORDER_SIZE;

          float saturation = ((float)x) / (size.width - 2 * BORDER_SIZE);
          float brightness = 1.0f - ((float)y) / (size.height - 2 * BORDER_SIZE);

          setHSBValue(myHue, saturation, brightness, myOpacity);
        }
      });

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          final Dimension size = getSize();
          final int x = e.getX() - BORDER_SIZE;
          final int y = e.getY() - BORDER_SIZE;

          float saturation = ((float)x) / (size.width - 2 * BORDER_SIZE);
          float brightness = 1.0f - ((float)y) / (size.height - 2 * BORDER_SIZE);

          setHSBValue(myHue, saturation, brightness, myOpacity);
        }
      });
    }

    private void setHSBValue(float h, float s, float b, int opacity) {
      myHue = h;
      mySaturation = s;
      myBrightness = b;
      myOpacity = opacity;
      myColor = ColorUtil.toAlpha(Color.getHSBColor(h, s, b), opacity);

      fireColorChanged(this);

      repaint();
    }

    private void setColor(Color color, Object source) {
      float[] hsb = new float[3];
      Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);
      myColor = color;
      myHue = hsb[0];
      mySaturation = hsb[1];
      myBrightness = hsb[2];
      myOpacity = color.getAlpha();

      fireColorChanged(source);

      repaint();
    }

    public void addListener(ColorListener listener) {
      myListeners.add(listener);
    }

    private void fireColorChanged(Object source) {
      for (ColorListener listener : myListeners) {
        listener.colorChanged(myColor, source);
      }
    }

    public void setOpacity(int opacity) {
      if (opacity != myOpacity) {
        setHSBValue(myHue, mySaturation, myBrightness, opacity);
      }
    }

    public void setHue(float hue) {
      if (hue != myHue) {
        setHSBValue(hue, mySaturation, myBrightness, myOpacity);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(250, 170);
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(150, 170);
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2d = (Graphics2D)g;

      final Dimension size = getSize();

      myComponent = new Rectangle(BORDER_SIZE, BORDER_SIZE, size.width, size.height);
      myImage = createImage(new SaturationBrightnessImageProducer(size.width - BORDER_SIZE * 2, size.height - BORDER_SIZE * 2, myHue));

      g.setColor(UIManager.getColor("Panel.background"));
      g.fillRect(0, 0, getWidth(), getHeight());

      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ((float)myOpacity) / 255f));
      g.drawImage(myImage, myComponent.x, myComponent.y, null);

      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, 1.0f));

      final int x = (int)(mySaturation * (myComponent.width - 2 * BORDER_SIZE));
      final int y = (int)((myComponent.height - 2 * BORDER_SIZE) * (1.0f - myBrightness));

      g.setColor(Color.WHITE);
      int knobX = BORDER_SIZE + x;
      int knobY = BORDER_SIZE + y;
      g.fillRect(knobX - 2, knobY - 2, 4, 4);
      g.setColor(Color.BLACK);
      g.drawRect(knobX - 2, knobY - 2, 4, 4);
    }

    public void dropImage() {
      myImage = null;
    }

    @VisibleForTesting
    protected Color getColor(){
      return myColor;
    }
  }

  private static class ColorPreviewComponent extends JComponent {
    private Color myColor;

    private ColorPreviewComponent() {
      setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(100, 32);
    }

    public void setColor(Color c) {
      myColor = c;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      final Insets i = getInsets();
      final Rectangle r = getBounds();

      final int width = r.width - i.left - i.right;
      final int height = r.height - i.top - i.bottom;

      g.setColor(Color.WHITE);
      g.fillRect(i.left, i.top, width, height);

      g.setColor(myColor);
      g.fillRect(i.left, i.top, width, height);

      g.setColor(Color.BLACK);
      g.drawRect(i.left, i.top, width - 1, height - 1);

      g.setColor(Color.WHITE);
      g.drawRect(i.left + 1, i.top + 1, width - 3, height - 3);
    }
  }

  public class NumberDocument extends PlainDocument {

    private final boolean myHex;
    private JTextField mySrc;

    public NumberDocument(boolean hex) {
      myHex = hex;
    }

    void setSource(JTextField field) {
      mySrc = field;
    }

    @Override
    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
      final boolean rgb = isRGBMode();
      char[] source = str.toCharArray();
      if (mySrc != null) {
        final int selected = mySrc.getSelectionEnd() - mySrc.getSelectionStart();
        int newLen = mySrc.getText().length() -  selected + str.length();
        if (newLen > (myHex ? (isARGBMode() ? 8 : 6) : 3)) {
          Toolkit.getDefaultToolkit().beep();
          return;
        }
      }
      char[] result = new char[source.length];
      int j = 0;
      for (int i = 0; i < result.length; i++) {
        if (myHex ? "0123456789abcdefABCDEF".indexOf(source[i]) >= 0 : Character.isDigit(source[i])) {
          result[j++] = source[i];
        }
        else {
          Toolkit.getDefaultToolkit().beep();
        }
      }
      final String toInsert = StringUtil.toUpperCase(new String(result, 0, j));
      final String res = new StringBuilder(mySrc.getText()).insert(offs, toInsert).toString();
      try {
        if (!myHex) {
          final int num = Integer.parseInt(res);
          if (rgb) {
            if (num > 255) {
              Toolkit.getDefaultToolkit().beep();
              return;
            }
          } else {
            if ((mySrc == myRed && num > 359)
              || ((mySrc == myGreen || mySrc == myBlue) && num > 100)) {
              Toolkit.getDefaultToolkit().beep();
              return;
            }
          }
        }
      }
      catch (NumberFormatException ignore) {
      }
      super.insertString(offs, toInsert, a);
    }
  }

  private class RecentColorsComponent extends JComponent {
    private static final int WIDTH = 10 * 30 + 13;
    private static final int HEIGHT = 62 + 3;

    private List<Color> myRecentColors = new ArrayList<Color>();

    private RecentColorsComponent(final ColorListener listener, boolean restoreColors) {
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          Color color = getColor(e);
          if (color != null) {
            listener.colorChanged(color, RecentColorsComponent.this);
          }
        }
      });

      if (restoreColors) {
        restoreColors();
      }
    }

    @Nullable
    public Color getMostRecentColor() {
      return myRecentColors.isEmpty() ? null : myRecentColors.get(myRecentColors.size() - 1);
    }

    private void restoreColors() {
      final String value = PropertiesComponent.getInstance().getValue(COLOR_CHOOSER_COLORS_KEY);
      if (value != null) {
        final List<String> colors = StringUtil.split(value, ",,,");
        for (String color : colors) {
          if (color.contains("-")) {
            List<String> components = StringUtil.split(color, "-");
            if (components.size() == 4) {
              myRecentColors.add(new Color(Integer.parseInt(components.get(0)),
                                           Integer.parseInt(components.get(1)),
                                           Integer.parseInt(components.get(2)),
                                           Integer.parseInt(components.get(3))));
            }
          }
          else {
            myRecentColors.add(new Color(Integer.parseInt(color)));
          }
        }
      }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      Color color = getColor(event);
      if (color != null) {
        return String.format("R: %d G: %d B: %d A: %s", color.getRed(), color.getGreen(), color.getBlue(),
                             String.format("%.2f", (float)(color.getAlpha() / 255.0)));
      }

      return super.getToolTipText(event);
    }

    @Nullable
    private Color getColor(MouseEvent event) {
      Pair<Integer, Integer> pair = pointToCellCoords(event.getPoint());
      if (pair != null) {
        int ndx = pair.second + pair.first * 10;
        if (myRecentColors.size() > ndx) {
          return myRecentColors.get(ndx);
        }
      }

      return null;
    }

    public void saveColors() {
      final List<String> values = new ArrayList<String>();
      for (Color recentColor : myRecentColors) {
        if (recentColor == null) break;
        values
          .add(String.format("%d-%d-%d-%d", recentColor.getRed(), recentColor.getGreen(), recentColor.getBlue(), recentColor.getAlpha()));
      }

      PropertiesComponent.getInstance().setValue(COLOR_CHOOSER_COLORS_KEY, StringUtil.join(values, ",,,"));
    }

    public void appendColor(Color c) {
      if (!myRecentColors.contains(c)) {
        myRecentColors.add(c);
      }

      if (myRecentColors.size() > 20) {
        myRecentColors = new ArrayList<Color>(myRecentColors.subList(myRecentColors.size() - 20, myRecentColors.size()));
      }
    }

    @Nullable
    private Pair<Integer, Integer> pointToCellCoords(Point p) {
      int x = p.x;
      int y = p.y;

      final Insets i = getInsets();
      final Dimension d = getSize();

      final int left = i.left + (d.width - i.left - i.right - WIDTH) / 2;
      final int top = i.top + (d.height - i.top - i.bottom - HEIGHT) / 2;

      int col = (x - left - 2) / 31;
      col = col > 9 ? 9 : col;
      int row = (y - top - 2) / 31;
      row = row > 1 ? 1 : row;

      return row >= 0 && col >= 0 ? Pair.create(row, col) : null;
    }

    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(WIDTH, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
      final Insets i = getInsets();

      final Dimension d = getSize();

      final int left = i.left + (d.width - i.left - i.right - WIDTH) / 2;
      final int top = i.top + (d.height - i.top - i.bottom - HEIGHT) / 2;

      g.setColor(Color.WHITE);
      g.fillRect(left, top, WIDTH, HEIGHT);

      g.setColor(Color.GRAY);
      g.drawLine(left + 1, i.top + HEIGHT / 2, left + WIDTH - 3, i.top + HEIGHT / 2);
      g.drawRect(left + 1, top + 1, WIDTH - 3, HEIGHT - 3);


      for (int k = 1; k < 10; k++) {
        g.drawLine(left + 1 + k * 31, top + 1, left + 1 + k * 31, top + HEIGHT - 3);
      }

      for (int r = 0; r < myRecentColors.size(); r++) {
        int row = r / 10;
        int col = r % 10;
        Color color = myRecentColors.get(r);
        g.setColor(color);
        g.fillRect(left + 2 + col * 30 + col + 1, top + 2 + row * 30 + row + 1, 28, 28);
      }
    }
  }

  static class ColorPickerDialog extends DialogWrapper {

    private final Color myPreselectedColor;
    private final ColorPickerListener[] myListeners;
    private ColorPicker myColorPicker;
    private final boolean myEnableOpacity;
    private ColorPipette myPicker;
    private final boolean myOpacityInPercent;

    public ColorPickerDialog(Component parent,
                             String caption,
                             @Nullable Color preselectedColor,
                             boolean enableOpacity,
                             @Nullable ColorPickerListener[] listeners,
                             boolean opacityInPercent) {
      super(parent, true);
      myListeners = listeners;
      setTitle(caption);
      myPreselectedColor = preselectedColor;
      myEnableOpacity = enableOpacity;
      myOpacityInPercent = opacityInPercent;
      setResizable(false);
      setOKButtonText("Choose");
      init();
      addMouseListener((MouseMotionListener)new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          myPicker.cancelPipette();
        }

        @Override
        public void mouseExited(MouseEvent e) {
          myPicker.pick();
        }
      });

    }

    @Override
    protected JComponent createCenterPanel() {
      if (myColorPicker == null) {
        myColorPicker = new ColorPicker(myDisposable, myPreselectedColor, true, myEnableOpacity, myListeners, myOpacityInPercent);
        myColorPicker.pickARGB();
      }

      return myColorPicker;
    }

    public Color getColor() {
      return myColorPicker.getColor();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myColorPicker.getPreferredFocusedComponent();
    }

    @Override
    protected void doOKAction() {
      myColorPicker.appendRecentColor();
      myColorPicker.saveRecentColors();

      super.doOKAction();
    }

    @Override
    public void show() {
      super.show();
      myColorPicker.fireClosed(getExitCode() == DialogWrapper.OK_EXIT_CODE ? getColor() : null);
    }
  }

  public static class SaturationBrightnessImageProducer extends MemoryImageSource {
    private int[] myPixels;
    private int myWidth;
    private int myHeight;

    private float[] mySat;
    private float[] myBrightness;

    private float myHue;

    public SaturationBrightnessImageProducer(int w, int h, float hue) {
      super(w, h, null, 0, w);
      myPixels = new int[w * h];
      myWidth = w;
      myHeight = h;
      myHue = hue;
      generateLookupTables();
      newPixels(myPixels, ColorModel.getRGBdefault(), 0, w);
      setAnimated(true);
      generateComponent();
    }

    public int getRadius() {
      return Math.min(myWidth, myHeight) / 2 - 2;
    }

    private void generateLookupTables() {
      mySat = new float[myWidth * myHeight];
      myBrightness = new float[myWidth * myHeight];
      for (int x = 0; x < myWidth; x++) {
        for (int y = 0; y < myHeight; y++) {
          int index = x + y * myWidth;
          mySat[index] = ((float)x) / myWidth;
          myBrightness[index] = 1.0f - ((float)y) / myHeight;
        }
      }
    }

    public void generateComponent() {
      for (int index = 0; index < myPixels.length; index++) {
        myPixels[index] = Color.HSBtoRGB(myHue, mySat[index], myBrightness[index]);
      }
      newPixels();
    }
  }

  private static class ColorPipette implements ImageObserver {
    private Dialog myPickerFrame;
    private final JComponent myParent;
    private Color myOldColor;
    private Timer myTimer;

    private Point myPoint = new Point();
    private Point myPickOffset;
    private Robot myRobot = null;
    private Color myPreviousColor;
    private Point myPreviousLocation;
    private Rectangle myCaptureRect;
    private Graphics2D myGraphics;
    private BufferedImage myImage;
    private Point myHotspot;
    private Point myCaptureOffset;
    private BufferedImage myMagnifierImage;
    private Color myTransparentColor = new Color(0, true);
    private Rectangle myZoomRect;
    private Rectangle myGlassRect;
    private ColorListener myColorListener;
    private BufferedImage myMaskImage;
    private Alarm myColorListenersNotifier = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    private ColorPipette(JComponent parent, Color oldColor) {
      myParent = parent;
      myOldColor = oldColor;

      try {
        myRobot = new Robot();
      }
      catch (AWTException e) {
        // should not happen
      }
    }

    public void setListener(ColorListener colorListener) {
      myColorListener = colorListener;
    }

    public void pick() {
      Dialog picker = getPicker();
      picker.setVisible(true);
      myTimer.start();
      // it seems like it's the lowest value for opacity for mouse events to be processed correctly
      WindowManager.getInstance().setAlphaModeRatio(picker, SystemInfo.isMac ? 0.95f : 0.99f);
    }

    @Override
    public boolean imageUpdate(Image img, int flags, int x, int y, int width, int height) {
      return false;
    }

    private Dialog getPicker() {
      if (myPickerFrame == null) {
        Window owner = SwingUtilities.getWindowAncestor(myParent);
        if (owner instanceof Dialog) {
          myPickerFrame = new JDialog((Dialog)owner);
        }
        else if (owner instanceof Frame) {
          myPickerFrame = new JDialog((Frame)owner);
        }
        else {
          myPickerFrame = new JDialog(new JFrame());
        }

        myPickerFrame.addMouseListener(new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            e.consume();
            pickDone();
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            e.consume();
          }

          @Override
          public void mouseExited(MouseEvent e) {
            updatePipette();
          }
        });

        myPickerFrame.addMouseMotionListener(new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            updatePipette();
          }
        });

        myPickerFrame.addFocusListener(new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            cancelPipette();
          }
        });

        myPickerFrame.setSize(50, 50);
        myPickerFrame.setUndecorated(true);
        myPickerFrame.setAlwaysOnTop(true);

        JRootPane rootPane = ((JDialog)myPickerFrame).getRootPane();
        rootPane.putClientProperty("Window.shadow", Boolean.FALSE);

        myGlassRect = new Rectangle(0, 0, 32, 32);
        myPickOffset = new Point(0, 0);
        myCaptureRect = new Rectangle(-4, -4, 8, 8);
        myCaptureOffset = new Point(myCaptureRect.x, myCaptureRect.y);
        myHotspot = new Point(14, 16);

        myZoomRect = new Rectangle(0, 0, 32, 32);

        myMaskImage = UIUtil.createImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D maskG = myMaskImage.createGraphics();
        maskG.setColor(Color.BLUE);
        maskG.fillRect(0, 0, 32, 32);

        maskG.setColor(Color.RED);
        maskG.setComposite(AlphaComposite.SrcOut);
        maskG.fillRect(0, 0, 32, 32);
        maskG.dispose();

        myMagnifierImage = UIUtil.createImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = myMagnifierImage.createGraphics();

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        graphics.setColor(Color.BLACK);
        //graphics.drawOval(1, 1, 30, 30);
        //graphics.drawOval(2, 2, 28, 28);
        //
        //graphics.drawLine(2, 16, 12, 16);
        //graphics.drawLine(20, 16, 30, 16);
        //
        //graphics.drawLine(16, 2, 16, 12);
        //graphics.drawLine(16, 20, 16, 30);
        AllIcons.Ide.Pipette.paintIcon(null, graphics, 14, 0);

        graphics.dispose();

        myImage = myParent.getGraphicsConfiguration().createCompatibleImage(myMagnifierImage.getWidth(), myMagnifierImage.getHeight(),
                                                                            Transparency.TRANSLUCENT);

        myGraphics = (Graphics2D)myImage.getGraphics();
        myGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        myPickerFrame.addKeyListener(new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
              case KeyEvent.VK_ESCAPE:
                cancelPipette();
                break;
              case KeyEvent.VK_ENTER:
                pickDone();
                break;
            }
          }
        });

        myTimer = new Timer(5, new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            updatePipette();
          }
        });
      }

      return myPickerFrame;
    }

    private void cancelPipette() {
      myTimer.stop();
      myPickerFrame.setVisible(false);
      if (myColorListener != null && myOldColor != null) {
        myColorListener.colorChanged(myOldColor, this);
      }
    }

    public void pickDone() {
      PointerInfo pointerInfo = MouseInfo.getPointerInfo();
      Point location = pointerInfo.getLocation();
      Color pixelColor = myRobot.getPixelColor(location.x + myPickOffset.x, location.y + myPickOffset.y);
      cancelPipette();
      if (myColorListener != null) {
        myColorListener.colorChanged(pixelColor, this);
        myOldColor = pixelColor;
      }
    }

    private void updatePipette() {
      if (myPickerFrame != null && myPickerFrame.isShowing()) {
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        Point mouseLoc = pointerInfo.getLocation();
        myPickerFrame.setLocation(mouseLoc.x - myPickerFrame.getWidth() / 2, mouseLoc.y - myPickerFrame.getHeight() / 2);

        myPoint.x = mouseLoc.x + myPickOffset.x;
        myPoint.y = mouseLoc.y + myPickOffset.y;

        final Color c = myRobot.getPixelColor(myPoint.x, myPoint.y);
        if (!c.equals(myPreviousColor) || !mouseLoc.equals(myPreviousLocation)) {
          myPreviousColor = c;
          myPreviousLocation = mouseLoc;
          myCaptureRect.setLocation(mouseLoc.x - 2/*+ myCaptureOffset.x*/, mouseLoc.y - 2/*+ myCaptureOffset.y*/);
          myCaptureRect.setBounds(mouseLoc.x -2, mouseLoc.y -2, 5, 5);

          BufferedImage capture = myRobot.createScreenCapture(myCaptureRect);

          // Clear the cursor graphics
          myGraphics.setComposite(AlphaComposite.Src);
          myGraphics.setColor(myTransparentColor);
          myGraphics.fillRect(0, 0, myImage.getWidth(), myImage.getHeight());

          myGraphics.drawImage(capture, myZoomRect.x, myZoomRect.y, myZoomRect.width, myZoomRect.height, this);

          // cropping round image
          myGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT));
          myGraphics.drawImage(myMaskImage, myZoomRect.x, myZoomRect.y, myZoomRect.width, myZoomRect.height, this);

          // paint magnifier
          myGraphics.setComposite(AlphaComposite.SrcOver);
          myGraphics.drawImage(myMagnifierImage, 0, 0, this);

          // We need to create a new subImage. This forces that
          // the color picker uses the new imagery.
          //BufferedImage subImage = myImage.getSubimage(0, 0, myImage.getWidth(), myImage.getHeight());
          myPickerFrame.setCursor(myParent.getToolkit().createCustomCursor(myImage, myHotspot, "ColorPicker"));
          if (myColorListener != null) {
            myColorListenersNotifier.cancelAllRequests();
            myColorListenersNotifier.addRequest(new Runnable() {
              @Override
              public void run() {
                myColorListener.colorChanged(c, ColorPipette.this);
              }
            }, 300);
          }
        }
      }
    }

    //public static void pickColor(ColorListener listener, JComponent c) {
    //  new ColorPipette(c, new ColorListener() {
    //    @Override
    //    public void colorChanged(Color color, Object source) {
    //      ColorPicker.this.setColor(color, my);
    //    }
    //  }).pick(listener);
    //}

    public static boolean isAvailable() {
      try {
        Robot robot = new Robot();
        robot.createScreenCapture(new Rectangle(0, 0, 1, 1));
        return WindowManager.getInstance().isAlphaModeSupported();
      }
      catch (AWTException e) {
        return false;
      }
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        showDialog(null, "", null, true, null, false);
      }
    });
  }
}
interface ColorListener {
  void colorChanged(Color color, Object source);
}

class SlideComponent extends JComponent {
  private static final int OFFSET = 11;
  protected int myPointerValue = 0;
  private int myValue = 0;
  private final boolean myVertical;
  private final String myTitle;

  private final List<Consumer<Integer>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private LightweightHint myTooltipHint;
  private final JLabel myLabel = new JLabel();
  private Unit myUnit = Unit.LEVEL;

  private Color myColor;

  enum Unit {
    PERCENT,
    LEVEL;

    private static final float PERCENT_MAX_VALUE = 100f;
    private static final float LEVEL_MAX_VALUE = 255f;

    private static float getMaxValue(Unit unit) {
      return LEVEL.equals(unit) ? LEVEL_MAX_VALUE : PERCENT_MAX_VALUE;
    }

    private static String formatValue(int value, Unit unit) {
      return String.format("%d%s", (int) (getMaxValue(unit) / LEVEL_MAX_VALUE * value),
                           unit.equals(PERCENT) ? "%" : "");
    }
  }

  void setUnits(Unit unit) {
    myUnit = unit;
  }

  SlideComponent(String title, boolean vertical) {
    myTitle = title;
    myVertical = vertical;

    addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseDragged(MouseEvent e) {
        processMouse(e);
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        processMouse(e);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        updateBalloonText();
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        updateBalloonText();
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        updateBalloonText();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (myTooltipHint != null) {
          myTooltipHint.hide();
          myTooltipHint = null;
        }
      }
    });

    addMouseWheelListener(new MouseWheelListener() {
      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        final int amount = e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL ? e.getUnitsToScroll() * e.getScrollAmount() :
                           e.getWheelRotation() < 0 ? -e.getScrollAmount() : e.getScrollAmount();
        int pointerValue = myPointerValue + amount;
        pointerValue = pointerValue < OFFSET ? OFFSET : pointerValue;
        int size = myVertical ? getHeight() : getWidth();
        pointerValue = pointerValue > (size - 12) ? size - 12 : pointerValue;

        myPointerValue = pointerValue;
        myValue = pointerValueToValue(myPointerValue);

        repaint();
        fireValueChanged();
      }
    });

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        setValue(getValue());
        fireValueChanged();
        repaint();
      }
    });
  }

  public void setColor(Color color) {
    myColor = color;
  }

  private void updateBalloonText() {
    final Point point = myVertical ? new Point(0, myPointerValue) : new Point(myPointerValue, 0);
    myLabel.setText(myTitle + ": " + Unit.formatValue(myValue, myUnit));
    if (myTooltipHint == null) {
      myTooltipHint = new LightweightHint(myLabel);
      myTooltipHint.setCancelOnClickOutside(false);
      myTooltipHint.setCancelOnOtherWindowOpen(false);

      final HintHint hint = new HintHint(this, point)
        .setPreferredPosition(myVertical ? Balloon.Position.atLeft : Balloon.Position.above)
        .setBorderColor(Color.BLACK)
        .setAwtTooltip(true)
        .setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD))
        .setTextBg(HintUtil.INFORMATION_COLOR)
        .setShowImmediately(true);

      final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      myTooltipHint.show(this, point.x, point.y, owner instanceof JComponent ? (JComponent)owner : null, hint);
    }
    else {
      myTooltipHint.setLocation(new RelativePoint(this, point));
    }
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    super.processMouseMotionEvent(e);
    updateBalloonText();
  }

  private void processMouse(MouseEvent e) {
    int pointerValue = myVertical ? e.getY() : e.getX();
    pointerValue = pointerValue < OFFSET ? OFFSET : pointerValue;
    int size = myVertical ? getHeight() : getWidth();
    pointerValue = pointerValue > (size - 12) ? size - 12 : pointerValue;

    myPointerValue = pointerValue;

    myValue = pointerValueToValue(myPointerValue);

    repaint();
    fireValueChanged();
  }

  public void addListener(Consumer<Integer> listener) {
    myListeners.add(listener);
  }

  private void fireValueChanged() {
    for (Consumer<Integer> listener : myListeners) {
      listener.consume(myValue);
    }
  }

  // 0 - 255
  public void setValue(int value) {
    myPointerValue = valueToPointerValue(value);
    myValue = value;
  }

  public int getValue() {
    return myValue;
  }

  private int pointerValueToValue(int pointerValue) {
    pointerValue -= OFFSET;
    final int size = myVertical ? getHeight() : getWidth();
    float proportion = (size - 23) / 255f;
    return (int)(pointerValue / proportion);
  }

  private int valueToPointerValue(int value) {
    final int size = myVertical ? getHeight() : getWidth();
    float proportion = (size - 23) / 255f;
    return OFFSET + (int)(value * proportion);
  }

  @Override
  public Dimension getPreferredSize() {
    return myVertical ? new Dimension(22, 100) : new Dimension(100, 22);
  }

  @Override
  public Dimension getMinimumSize() {
    return myVertical ? new Dimension(22, 50) : new Dimension(50, 22);
  }

  @Override
  public Dimension getMaximumSize() {
    return myVertical ? new Dimension(getPreferredSize().width, Integer.MAX_VALUE) : new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
  }

  @Override
  public final void setToolTipText(String text) {
    //disable tooltips
  }

  @Override
  protected void paintComponent(Graphics g) {
    final Graphics2D g2d = (Graphics2D)g;
    Color color = new Color(myColor.getRGB());
    Color transparent = ColorUtil.toAlpha(Color.WHITE, 0);

    if (myVertical) {
      g2d.setPaint(UIUtil.getGradientPaint(0f, 0f, transparent, 0f, getHeight(), color));
      g.fillRect(7, 10, 12, getHeight() - 20);
    }
    else {
      g2d.setPaint(UIUtil.getGradientPaint(0f, 0f, transparent, getWidth(), 0f, color));
      g.fillRect(10, 7, getWidth() - 20, 12);
    }

    drawKnob(g2d, myVertical ? 7 : myPointerValue, myVertical ? myPointerValue : 7, myVertical);
  }

  protected static void drawKnob(Graphics2D g2d, int x, int y, boolean vertical) {
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (vertical) {
      y -= 6;

      Polygon arrowShadow = new Polygon();
      arrowShadow.addPoint(x - 5, y + 1);
      arrowShadow.addPoint(x + 7, y + 7);
      arrowShadow.addPoint(x - 5, y + 13);

      g2d.setColor(new Color(0, 0, 0, 70));
      g2d.fill(arrowShadow);

      Polygon arrowHead = new Polygon();
      arrowHead.addPoint(x - 6, y);
      arrowHead.addPoint(x + 6, y + 6);
      arrowHead.addPoint(x - 6, y + 12);

      g2d.setColor(new Color(153, 51, 0));
      g2d.fill(arrowHead);
    }
    else {
      x -= 6;

      Polygon arrowShadow = new Polygon();
      arrowShadow.addPoint(x + 1, y - 5);
      arrowShadow.addPoint(x + 13, y - 5);
      arrowShadow.addPoint(x + 7, y + 7);

      g2d.setColor(new Color(0, 0, 0, 70));
      g2d.fill(arrowShadow);

      Polygon arrowHead = new Polygon();
      arrowHead.addPoint(x, y - 6);
      arrowHead.addPoint(x + 12, y - 6);
      arrowHead.addPoint(x + 6, y + 6);

      g2d.setColor(new Color(153, 51, 0));
      g2d.fill(arrowHead);
    }
  }
}

class HueSlideComponent extends SlideComponent {
  private final Color[] myColors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
  private final float[] myPoints = new float[myColors.length];

  HueSlideComponent(String title) {
    super(title, false);
    int i = 0;
    for (Color color : myColors) {
      if (color.equals(Color.RED) && i != 0) {
        myPoints[i++] = 1.0f;
      }
      else {
        myPoints[i++] = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[0];
      }
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    final Graphics2D g2d = (Graphics2D)g;

    g2d.setPaint(new LinearGradientPaint(new Point2D.Double(0, 0), new Point2D.Double(getWidth() - 30, 0), myPoints, myColors));
    g.fillRect(10, 7, getWidth() - 20, 12);
    drawKnob(g2d, myPointerValue, 7, false);
  }
}

