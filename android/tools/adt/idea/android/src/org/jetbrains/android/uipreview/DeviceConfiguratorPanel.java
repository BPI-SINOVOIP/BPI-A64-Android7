/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.android.ide.common.resources.LocaleManager;
import com.android.ide.common.resources.configuration.*;
import com.android.resources.*;
import com.android.tools.idea.rendering.FlagManager;
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.android.ide.common.resources.configuration.LocaleQualifier.FAKE_VALUE;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class DeviceConfiguratorPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.DeviceConfiguratorPanel");

  private JBList myAvailableQualifiersList;
  private JButton myAddQualifierButton;
  private JButton myRemoveQualifierButton;
  private JPanel myQualifierOptionsPanel;

  private final Map<String, MyQualifierEditor> myEditors = new HashMap<String, MyQualifierEditor>();

  private final FolderConfiguration myAvailableQualifiersConfig = new FolderConfiguration();
  private final FolderConfiguration myChosenQualifiersConfig = new FolderConfiguration();
  private FolderConfiguration myActualQualifiersConfig = new FolderConfiguration();
  private JBList myChosenQualifiersList;

  private final DocumentListener myUpdatingDocumentListener = new DocumentAdapter() {
    @Override
    protected void textChanged(DocumentEvent e) {
      applyEditors();
    }
  };
  public final ListSelectionListener myUpdatingListListener = new ListSelectionListener() {
    @Override
    public void valueChanged(ListSelectionEvent listSelectionEvent) {
      applyEditors();
    }
  };

  @SuppressWarnings("unchecked")
  public DeviceConfiguratorPanel() {
    super(new BorderLayout());

    createUIComponents();

    createDefaultConfig(myAvailableQualifiersConfig);

    myChosenQualifiersConfig.reset();

    for (ResourceQualifier qualifier : myAvailableQualifiersConfig.getQualifiers()) {
      final String name = qualifier.getShortName();
      if (qualifier instanceof CountryCodeQualifier) {
        myEditors.put(name, new MyCountryCodeEditor());
      }
      else if (qualifier instanceof NetworkCodeQualifier) {
        myEditors.put(name, new MyNetworkCodeEditor());
      }
      else if (qualifier instanceof KeyboardStateQualifier) {
        myEditors.put(name, new MyKeyboardStateEditor());
      }
      else if (qualifier instanceof NavigationMethodQualifier) {
        myEditors.put(name, new MyNavigationMethodEditor());
      }
      else if (qualifier instanceof NavigationStateQualifier) {
        myEditors.put(name, new MyNavigationStateEditor());
      }
      else if (qualifier instanceof DensityQualifier) {
        myEditors.put(name, new MyDensityEditor());
      }
      else if (qualifier instanceof ScreenDimensionQualifier) {
        myEditors.put(name, new MyScreenDimensionEditor());
      }
      else if (qualifier instanceof ScreenOrientationQualifier) {
        myEditors.put(name, new MyScreenOrientationEditor());
      }
      else if (qualifier instanceof ScreenRatioQualifier) {
        myEditors.put(name, new MyScreenRatioEditor());
      }
      else if (qualifier instanceof ScreenSizeQualifier) {
        myEditors.put(name, new MyScreenSizeEditor());
      }
      else if (qualifier instanceof TextInputMethodQualifier) {
        myEditors.put(name, new MyTextInputMethodEditor());
      }
      else if (qualifier instanceof TouchScreenQualifier) {
        myEditors.put(name, new MyTouchScreenEditor());
      }
      else if (qualifier instanceof VersionQualifier) {
        myEditors.put(name, new MyVersionEditor());
      }
      else if (qualifier instanceof NightModeQualifier) {
        myEditors.put(name, new MyNightModeEditor());
      }
      else if (qualifier instanceof UiModeQualifier) {
        myEditors.put(name, new MyUiModeEditor());
      }
      else if (qualifier instanceof LocaleQualifier) {
        myEditors.put(name, new MyLocaleEditor());
      }
      else if (qualifier instanceof SmallestScreenWidthQualifier) {
        myEditors.put(name, new MySmallestScreenWidthEditor());
      }
      else if (qualifier instanceof ScreenWidthQualifier) {
        myEditors.put(name, new MyScreenWidthEditor());
      }
      else if (qualifier instanceof ScreenHeightQualifier) {
        myEditors.put(name, new MyScreenHeightEditor());
      }
      else if (qualifier instanceof LayoutDirectionQualifier) {
        myEditors.put(name, new MyLayoutDirectionEditor());
      } else {
        LOG.info("Missing editor for qualifier " + qualifier);
      }
    }

    for (String name : myEditors.keySet()) {
      final MyQualifierEditor editor = myEditors.get(name);
      myQualifierOptionsPanel.add(editor.getComponent(), name);
    }

    myAvailableQualifiersList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof ResourceQualifier) {
          ResourceQualifier qualifier = (ResourceQualifier)value;
          append(qualifier.getShortName());
          setIcon(getResourceIcon(qualifier));
        }
      }
    });

    myChosenQualifiersList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof ResourceQualifier) {
          final ResourceQualifier qualifier = getActualQualifier((ResourceQualifier)value);
          final String shortDisplayValue = qualifier.getShortDisplayValue();
          if (shortDisplayValue != null && !shortDisplayValue.isEmpty()) {
            append(shortDisplayValue);
          }
          else {
            append(qualifier.getShortName() + " (?)");
          }
          setIcon(getResourceIcon(qualifier));
        }
      }
    });

    myAddQualifierButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final ResourceQualifier selectedQualifier = (ResourceQualifier)myAvailableQualifiersList.getSelectedValue();
        if (selectedQualifier != null) {
          final int index = myAvailableQualifiersList.getSelectedIndex();

          myAvailableQualifiersConfig.removeQualifier(selectedQualifier);
          myChosenQualifiersConfig.addQualifier(selectedQualifier);

          updateLists();
          applyEditors();

          if (index >= 0) {
            myAvailableQualifiersList.setSelectedIndex(Math.min(index, myAvailableQualifiersList.getItemsCount() - 1));
          }
          myChosenQualifiersList.setSelectedValue(selectedQualifier, true);
        }
      }
    });

    myRemoveQualifierButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final ResourceQualifier selectedQualifier = (ResourceQualifier)myChosenQualifiersList.getSelectedValue();
        if (selectedQualifier != null) {
          final int index = myChosenQualifiersList.getSelectedIndex();

          myChosenQualifiersConfig.removeQualifier(selectedQualifier);
          myAvailableQualifiersConfig.addQualifier(selectedQualifier);
          updateLists();
          applyEditors();

          if (index >= 0) {
            myChosenQualifiersList.setSelectedIndex(Math.min(index, myChosenQualifiersList.getItemsCount() - 1));
          }
        }
      }
    });

    myAvailableQualifiersList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });

    myChosenQualifiersList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
        updateQualifierEditor();
      }
    });
  }

  private static final Map<String, Icon> ourIcons = Maps.newHashMapWithExpectedSize(25);
  static {
    ourIcons.put(UiModeQualifier.NAME, AndroidIcons.Configs.Dock);
    ourIcons.put(NightModeQualifier.NAME, AndroidIcons.Configs.Night);
    ourIcons.put(ScreenDimensionQualifier.NAME, AndroidIcons.Configs.Dimension);
    ourIcons.put(DensityQualifier.NAME, AndroidIcons.Configs.Dpi);
    ourIcons.put(ScreenHeightQualifier.NAME, AndroidIcons.Configs.Height);
    ourIcons.put(KeyboardStateQualifier.NAME, AndroidIcons.Configs.Keyboard);
    ourIcons.put(LocaleQualifier.NAME, AndroidIcons.Configs.Locale);
    ourIcons.put(CountryCodeQualifier.NAME, AndroidIcons.Configs.Mcc);
    ourIcons.put(NetworkCodeQualifier.NAME, AndroidIcons.Configs.Mnc);
    ourIcons.put(NavigationStateQualifier.NAME, AndroidIcons.Configs.Navpad);
    ourIcons.put(NavigationMethodQualifier.NAME, AndroidIcons.Configs.NavpadMethod);
    ourIcons.put(ScreenOrientationQualifier.NAME, AndroidIcons.Configs.Orientation);
    ourIcons.put(ScreenRatioQualifier.NAME, AndroidIcons.Configs.Ratio);
    ourIcons.put(ScreenSizeQualifier.NAME, AndroidIcons.Configs.Size);
    ourIcons.put(SmallestScreenWidthQualifier.NAME, AndroidIcons.Configs.SmallestWidth);
    ourIcons.put(ScreenWidthQualifier.NAME, AndroidIcons.Configs.Width);
    ourIcons.put(TextInputMethodQualifier.NAME, AndroidIcons.Configs.TextInput);
    ourIcons.put(TouchScreenQualifier.NAME, AndroidIcons.Configs.Touch);
    ourIcons.put(LayoutDirectionQualifier.NAME, AndroidIcons.Configs.LayoutDirection);

    // TODO: Get dedicated icon for the API version
    ourIcons.put(VersionQualifier.NAME, AndroidIcons.Targets);
  }

  @Nullable
  private static Icon getResourceIcon(ResourceQualifier qualifier) {
    return ourIcons.get(qualifier.getName());
  }

  public void init(@NotNull FolderConfiguration config) {
    myChosenQualifiersConfig.set(config);
    myAvailableQualifiersConfig.substract(config);

    for (ResourceQualifier qualifier : config.getQualifiers()) {
      final MyQualifierEditor editor = myEditors.get(qualifier.getShortName());
      if (editor != null) {
        editor.reset(qualifier);
      }
    }
  }

  protected void createDefaultConfig(FolderConfiguration config) {
    config.createDefault();
  }

  public abstract void applyEditors();

  public void updateAll() {
    updateLists();
    updateButtons();
    updateQualifierEditor();
    applyEditors();
  }

  public void doApplyEditors() throws InvalidOptionValueException {
    try {
      final FolderConfiguration newConfig = new FolderConfiguration();

      for (ResourceQualifier qualifier : myChosenQualifiersConfig.getQualifiers()) {
        final MyQualifierEditor editor = myEditors.get(qualifier.getShortName());
        if (editor != null) {
          newConfig.addQualifier(editor.apply());
        }
      }
      myActualQualifiersConfig = newConfig;
    }
    finally {
      myAvailableQualifiersList.repaint();
      myChosenQualifiersList.repaint();
    }
  }

  public DocumentListener getUpdatingDocumentListener() {
    return myUpdatingDocumentListener;
  }

  private ResourceQualifier getActualQualifier(ResourceQualifier qualifier) {
    for (ResourceQualifier qualifier1 : myActualQualifiersConfig.getQualifiers()) {
      if (Comparing.equal(qualifier1.getShortName(), qualifier.getShortName())) {
        return qualifier1;
      }
    }
    return qualifier;
  }

  private void updateQualifierEditor() {
    final ResourceQualifier selectedQualifier = (ResourceQualifier)myChosenQualifiersList.getSelectedValue();
    if (selectedQualifier != null && myEditors.containsKey(selectedQualifier.getShortName())) {
      final CardLayout layout = (CardLayout)myQualifierOptionsPanel.getLayout();
      layout.show(myQualifierOptionsPanel, selectedQualifier.getShortName());
      myQualifierOptionsPanel.setVisible(true);
    }
    else {
      myQualifierOptionsPanel.setVisible(false);
    }
  }

  private void updateButtons() {
    myAddQualifierButton.setEnabled(myAvailableQualifiersList.getSelectedIndex() >= 0);
    myRemoveQualifierButton.setEnabled(myChosenQualifiersList.getSelectedIndex() >= 0);
  }

  private void updateLists() {
    Object qualifier = myAvailableQualifiersList.getSelectedValue();
    final ResourceQualifier[] availableQualifiers = filterUnsupportedQualifiers(myAvailableQualifiersConfig.getQualifiers());
    myAvailableQualifiersList.setModel(new CollectionListModel(availableQualifiers));
    myAvailableQualifiersList.setSelectedValue(qualifier, true);

    if (myAvailableQualifiersList.getSelectedValue() == null && myAvailableQualifiersList.getItemsCount() > 0) {
      myAvailableQualifiersList.setSelectedIndex(0);
    }

    qualifier = myChosenQualifiersList.getSelectedValue();
    final ResourceQualifier[] chosenQualifiers = filterUnsupportedQualifiers(myChosenQualifiersConfig.getQualifiers());
    myChosenQualifiersList.setModel(new CollectionListModel(chosenQualifiers));
    myChosenQualifiersList.setSelectedValue(qualifier, true);

    if (myChosenQualifiersList.getSelectedValue() == null && myChosenQualifiersList.getItemsCount() > 0) {
      myChosenQualifiersList.setSelectedIndex(0);
    }
  }

  private ResourceQualifier[] filterUnsupportedQualifiers(ResourceQualifier[] qualifiers) {
    final List<ResourceQualifier> result = new ArrayList<ResourceQualifier>();
    for (ResourceQualifier qualifier : qualifiers) {
      if (myEditors.containsKey(qualifier.getShortName())) {
        result.add(qualifier);
      }
    }
    return result.toArray(new ResourceQualifier[result.size()]);
  }

  public FolderConfiguration getConfiguration() {
    return myActualQualifiersConfig;
  }

  private void createUIComponents() {
    myQualifierOptionsPanel = new JPanel(new CardLayout());

    final JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
    myAvailableQualifiersList = new JBList();
    myAvailableQualifiersList.setMinimumSize(new Dimension(10, 10));
    JBLabel label = new JBLabel(AndroidBundle.message("android.layout.preview.edit.configuration.available.qualifiers.label"));
    label.setLabelFor(myAvailableQualifiersList);
    leftPanel.add(label, BorderLayout.NORTH);
    leftPanel.add(new JBScrollPane(myAvailableQualifiersList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                   ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);

    final JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
    myChosenQualifiersList = new JBList();
    myChosenQualifiersList.setMinimumSize(new Dimension(10, 10));
    label = new JBLabel(AndroidBundle.message("android.layout.preview.edit.configuration.choosen.qualifiers.label"));
    label.setLabelFor(myChosenQualifiersList);
    rightPanel.add(label, BorderLayout.NORTH);
    rightPanel.add(new JBScrollPane(myChosenQualifiersList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);

    final JPanel buttonsPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE, 0, 0, true, false));
    myAddQualifierButton = new JButton(">>");
    buttonsPanel.add(myAddQualifierButton);
    myRemoveQualifierButton = new JButton("<<");
    buttonsPanel.add(myRemoveQualifierButton);

    final int gap = 5;

    final JPanel listsPanel = new JPanel(new AbstractLayoutManager() {
      @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
      @Override
      public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
          final Dimension leftPref = leftPanel.getPreferredSize();
          final Dimension rightPref = rightPanel.getPreferredSize();
          final Dimension middlePref = buttonsPanel.getPreferredSize();
          final Insets insets = target.getInsets();

          final int width = leftPref.width + middlePref.width + rightPref.width + insets.left + insets.right + gap * 2;
          final int height = Math
                               .max(leftPref.height, Math.max(rightPref.height, middlePref.height)) + insets.top + insets.bottom;
          return new Dimension(width, height);
        }
      }

      @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
      @Override
      public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
          final Insets insets = target.getInsets();
          int top = insets.top;
          int bottom = target.getHeight() - insets.bottom;
          int left = insets.left;
          int right = target.getWidth() - insets.right;

          final int middleWidth = buttonsPanel.getPreferredSize().width + gap * 2;
          final int listWidth = (right - left - middleWidth) / 2;
          final int height = bottom - top;

          leftPanel.setSize(listWidth, height);
          rightPanel.setSize(listWidth, height);
          buttonsPanel.setSize(middleWidth, height);

          leftPanel.setBounds(left, top, listWidth, height);
          rightPanel.setBounds(right - listWidth, top, listWidth, height);
          buttonsPanel.setBounds(left + listWidth + gap, top, middleWidth - gap * 2, height);
        }
      }
    });

    listsPanel.add(leftPanel);
    listsPanel.add(buttonsPanel);
    listsPanel.add(rightPanel);
    add(listsPanel, BorderLayout.CENTER);
    add(myQualifierOptionsPanel, BorderLayout.EAST);
  }

  public JBList getAvailableQualifiersList() {
    return myAvailableQualifiersList;
  }

  private abstract static class MyQualifierEditor<T extends ResourceQualifier> {
    abstract JComponent getComponent();

    abstract void reset(@NotNull T qualifier);

    @NotNull
    abstract T apply() throws InvalidOptionValueException;
  }

  private class MyCountryCodeEditor extends MyQualifierEditor<CountryCodeQualifier> {
    private final JTextField myTextField = new JTextField(3);

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel("<html><body>Mobile country code<br>(3 digits):</body></html>");
      label.setLabelFor(myTextField);
      myTextField.setColumns(3);
      myTextField.getDocument().addDocumentListener(myUpdatingDocumentListener);
      panel.add(label);
      panel.add(myTextField);
      return panel;
    }

    @Override
    void reset(@NotNull CountryCodeQualifier qualifier) {
      myTextField.setText(Integer.toString(qualifier.getCode()));
    }

    @NotNull
    @Override
    CountryCodeQualifier apply() throws InvalidOptionValueException {
      if (myTextField.getText().length() != 3) {
        throw new InvalidOptionValueException("Country code must contain 3 digits");
      }
      try {
        final int code = Integer.parseInt(myTextField.getText());
        if (code < 100 || code > 999) {
          throw new InvalidOptionValueException("Incorrect country code");
        }
        return new CountryCodeQualifier(code);
      }
      catch (NumberFormatException e) {
        throw new InvalidOptionValueException("Country code must be a number");
      }
    }
  }

  private class MyNetworkCodeEditor extends MyQualifierEditor<NetworkCodeQualifier> {
    private final JTextField myTextField = new JTextField(3);

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel("<html><body>Mobile network code<br>(1-3 digits):</body></html>");
      panel.add(label);
      label.setLabelFor(myTextField);
      myTextField.getDocument().addDocumentListener(myUpdatingDocumentListener);
      panel.add(myTextField);
      return panel;
    }

    @Override
    void reset(@NotNull NetworkCodeQualifier qualifier) {
      myTextField.setText(Integer.toString(qualifier.getCode()));
    }

    @NotNull
    @Override
    NetworkCodeQualifier apply() throws InvalidOptionValueException {
      try {
        final int code = Integer.parseInt(myTextField.getText());
        if (code <= 0 || code >= 1000) {
          throw new InvalidOptionValueException("Incorrect network code");
        }
        return new NetworkCodeQualifier(code);
      }
      catch (NumberFormatException e) {
        throw new InvalidOptionValueException("Network code must be a number");
      }
    }
  }

  private abstract class MyEnumBasedEditor<T extends ResourceQualifier, U extends Enum<U>> extends MyQualifierEditor<T> {
    private final JComboBox myComboBox = new JComboBox();
    private final Class<U> myEnumClass;

    protected MyEnumBasedEditor(@NotNull Class<U> enumClass) {
      myEnumClass = enumClass;
    }

    @Override
    JComponent getComponent() {
      myComboBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          applyEditors();
        }
      });

      myComboBox.setRenderer(new ListCellRendererWrapper() {
        @Override
        public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value instanceof ResourceEnum) {
            setText(((ResourceEnum)value).getShortDisplayValue());
          }
        }
      });

      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel(getCaption());
      label.setLabelFor(myComboBox);
      myComboBox.setModel(createModel());
      panel.add(label);
      panel.add(myComboBox);
      return panel;
    }

    protected ComboBoxModel createModel() {
      return new EnumComboBoxModel<U>(myEnumClass);
    }

    @NotNull
    protected abstract String getCaption();

    @Override
    void reset(@NotNull T qualifier) {
      final U value = getValue(qualifier);
      if (value != null) {
        myComboBox.setSelectedItem(value);
      }
      else if (myComboBox.getItemCount() > 0) {
        myComboBox.setSelectedIndex(0);
      }
    }

    protected abstract U getValue(@NotNull T qualifier);

    @NotNull
    protected abstract T getQualifier(@NotNull U value);

    @NotNull
    protected abstract String getErrorMessage();

    @NotNull
    @Override
    T apply() throws InvalidOptionValueException {
      final U selectedItem = (U)myComboBox.getSelectedItem();
      if (selectedItem == null) {
        throw new InvalidOptionValueException(getErrorMessage());
      }
      return getQualifier(selectedItem);
    }
  }

  private class MyScreenSizeEditor extends MyEnumBasedEditor<ScreenSizeQualifier, ScreenSize> {
    private MyScreenSizeEditor() {
      super(ScreenSize.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Screen size:";
    }

    @NotNull
    @Override
    protected ScreenSize getValue(@NotNull ScreenSizeQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenSizeQualifier getQualifier(@NotNull ScreenSize value) {
      return new ScreenSizeQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify screen size";
    }
  }

  private class MyScreenOrientationEditor extends MyEnumBasedEditor<ScreenOrientationQualifier, ScreenOrientation> {
    private MyScreenOrientationEditor() {
      super(ScreenOrientation.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Screen orientation:";
    }

    @NotNull
    @Override
    protected ScreenOrientation getValue(@NotNull ScreenOrientationQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenOrientationQualifier getQualifier(@NotNull ScreenOrientation value) {
      return new ScreenOrientationQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify screen orientation";
    }
  }

  private class MyScreenRatioEditor extends MyEnumBasedEditor<ScreenRatioQualifier, ScreenRatio> {
    private MyScreenRatioEditor() {
      super(ScreenRatio.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Screen ratio:";
    }

    @NotNull
    @Override
    protected ScreenRatio getValue(@NotNull ScreenRatioQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenRatioQualifier getQualifier(@NotNull ScreenRatio value) {
      return new ScreenRatioQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify screen ratio";
    }
  }

  /**
   * Specialized combo box model which filters out enum values that are marked as not interesting. This
   * is to discourage app developers from creating specialized resource folders for specific densities.
   */
  private static class DensityComboBoxModel extends AbstractListModel implements ComboBoxModel {
    private final List<Density> myList;
    private Density mySelected = null;

    public DensityComboBoxModel() {
      myList = new ArrayList<Density>();
      for (Density density : Density.values()) {
        if (density.isRecommended()) {
          myList.add(density);
        }
      }
      mySelected = myList.get(0);
    }

    @Override
    public int getSize() {
      return myList.size();
    }

    @Override
    public Density getElementAt(int index) {
      return myList.get(index);
    }

    @Override
    public void setSelectedItem(Object item) {
      @SuppressWarnings("unchecked") Density e = (Density)item;
      setSelectedItem(e);
    }

    public void setSelectedItem(Density item) {
      mySelected = item;
      fireContentsChanged(this, 0, getSize());
    }

    @Override
    public Density getSelectedItem() {
      return mySelected;
    }
  }

  private class MyDensityEditor extends MyEnumBasedEditor<DensityQualifier, Density> {
    private MyDensityEditor() {
      super(Density.class);
    }

    @Override
    protected ComboBoxModel createModel() {
      return new DensityComboBoxModel();
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Density:";
    }

    @NotNull
    @Override
    protected Density getValue(@NotNull DensityQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected DensityQualifier getQualifier(@NotNull Density value) {
      return new DensityQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify density";
    }
  }

  private class MyTouchScreenEditor extends MyEnumBasedEditor<TouchScreenQualifier, TouchScreen> {
    private MyTouchScreenEditor() {
      super(TouchScreen.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Touch screen:";
    }

    @NotNull
    @Override
    protected TouchScreen getValue(@NotNull TouchScreenQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected TouchScreenQualifier getQualifier(@NotNull TouchScreen value) {
      return new TouchScreenQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify touch screen";
    }
  }

  private class MyKeyboardStateEditor extends MyEnumBasedEditor<KeyboardStateQualifier, KeyboardState> {
    private MyKeyboardStateEditor() {
      super(KeyboardState.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Keyboard state:";
    }

    @NotNull
    @Override
    protected KeyboardState getValue(@NotNull KeyboardStateQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected KeyboardStateQualifier getQualifier(@NotNull KeyboardState value) {
      return new KeyboardStateQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify keyboard state";
    }
  }

  private class MyTextInputMethodEditor extends MyEnumBasedEditor<TextInputMethodQualifier, Keyboard> {
    private MyTextInputMethodEditor() {
      super(Keyboard.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Text input method:";
    }

    @NotNull
    @Override
    protected Keyboard getValue(@NotNull TextInputMethodQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected TextInputMethodQualifier getQualifier(@NotNull Keyboard value) {
      return new TextInputMethodQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify text input method";
    }
  }

  private class MyNavigationStateEditor extends MyEnumBasedEditor<NavigationStateQualifier, NavigationState> {

    private MyNavigationStateEditor() {
      super(NavigationState.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Navigation state:";
    }

    @NotNull
    @Override
    protected NavigationState getValue(@NotNull NavigationStateQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected NavigationStateQualifier getQualifier(@NotNull NavigationState value) {
      return new NavigationStateQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify navigation state";
    }
  }

  private class MyNavigationMethodEditor extends MyEnumBasedEditor<NavigationMethodQualifier, Navigation> {
    private MyNavigationMethodEditor() {
      super(Navigation.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Navigation method:";
    }

    @NotNull
    @Override
    protected Navigation getValue(@NotNull NavigationMethodQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected NavigationMethodQualifier getQualifier(@NotNull Navigation value) {
      return new NavigationMethodQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify navigation method";
    }
  }

  private class MyLayoutDirectionEditor extends MyEnumBasedEditor<LayoutDirectionQualifier, LayoutDirection> {
    private MyLayoutDirectionEditor() {
      super(LayoutDirection.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Layout direction:";
    }

    @NotNull
    @Override
    protected LayoutDirection getValue(@NotNull LayoutDirectionQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected LayoutDirectionQualifier getQualifier(@NotNull LayoutDirection value) {
      return new LayoutDirectionQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify layout direction";
    }
  }

  private class MyScreenDimensionEditor extends MyQualifierEditor<ScreenDimensionQualifier> {
    private final JTextField mySizeField1 = new JTextField();
    private final JTextField mySizeField2 = new JTextField();

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel("Screen dimension in dp:");
      label.setLabelFor(mySizeField1);
      panel.add(label);
      panel.add(mySizeField1);
      panel.add(mySizeField2);
      mySizeField1.getDocument().addDocumentListener(myUpdatingDocumentListener);
      mySizeField2.getDocument().addDocumentListener(myUpdatingDocumentListener);
      return panel;
    }

    @Override
    void reset(@NotNull ScreenDimensionQualifier qualifier) {
      final int value1 = qualifier.getValue1();
      if (value1 >= 0) {
        mySizeField1.setText(Integer.toString(value1));
      }

      final int value2 = qualifier.getValue2();
      if (value2 >= 0) {
        mySizeField2.setText(Integer.toString(value2));
      }
    }

    @NotNull
    @Override
    ScreenDimensionQualifier apply() throws InvalidOptionValueException {
      try {
        final int size1 = Integer.parseInt(mySizeField1.getText());
        final int size2 = Integer.parseInt(mySizeField2.getText());

        if (size1 <= 0 || size2 <= 0) {
          throw new InvalidOptionValueException("Incorrect screen dimension");
        }
        return new ScreenDimensionQualifier(size1, size2);
      }
      catch (NumberFormatException e) {
        LOG.debug(e);
        throw new InvalidOptionValueException("Incorrect screen dimension");
      }
    }
  }

  private class MyVersionEditor extends MyQualifierEditor<VersionQualifier> {
    private final JTextField myTextField = new JTextField(3);

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel("Platform API level:");
      panel.add(label);
      label.setLabelFor(myTextField);
      myTextField.getDocument().addDocumentListener(myUpdatingDocumentListener);
      panel.add(myTextField);
      return panel;
    }

    @Override
    void reset(@NotNull VersionQualifier qualifier) {
      myTextField.setText(Integer.toString(qualifier.getVersion()));
    }

    @NotNull
    @Override
    VersionQualifier apply() throws InvalidOptionValueException {
      try {
        final int apiLevel = Integer.parseInt(myTextField.getText().trim());
        if (apiLevel < 0) {
          throw new InvalidOptionValueException("Incorrect API level");
        }
        return new VersionQualifier(apiLevel);
      }
      catch (NumberFormatException e) {
        throw new InvalidOptionValueException("Incorrect API level");
      }
    }
  }

  private class MyNightModeEditor extends MyEnumBasedEditor<NightModeQualifier, NightMode> {
    protected MyNightModeEditor() {
      super(NightMode.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Night mode:";
    }

    @Override
    protected NightMode getValue(@NotNull NightModeQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected NightModeQualifier getQualifier(@NotNull NightMode value) {
      return new NightModeQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify night mode";
    }
  }

  private class MyUiModeEditor extends MyEnumBasedEditor<UiModeQualifier, UiMode> {
    private MyUiModeEditor() {
      super(UiMode.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "UI mode:";
    }

    @Override
    protected UiMode getValue(@NotNull UiModeQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected UiModeQualifier getQualifier(@NotNull UiMode value) {
      return new UiModeQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify UI mode";
    }
  }

  // See com.intellij.codeInsight.lookup.impl.Advertiser -- the class used to
  // render tips at the bottom of code completion dialog
  private static Font adFont() {
    Font font = UIUtil.getLabelFont();
    return font.deriveFont((float)(font.getSize() - 2));
  }

  private class MyLocaleEditor extends MyQualifierEditor<LocaleQualifier> {
    private final JBList myLanguageList = new JBList();
    private final JBList myRegionList = new JBList();
    private JBCheckBox myShowAllRegions;
    private JBLabel myWarningsLabel;


    @Override
    JComponent getComponent() {
      GridBagConstraints gridBagConstraints;
      JPanel pane = new JPanel(new GridBagLayout());
      pane.setBorder(new EmptyBorder(0, 20, 0, 0)); // pad 20 pixels on the left hand side to space out the two views

      myShowAllRegions = new JBCheckBox("Show All Regions", false);
      myWarningsLabel = new JBLabel("BCP 47 tags (3-letter languages or regions) will only match on API 21");
      myWarningsLabel.setIcon(AllIcons.General.BalloonWarning);
      myWarningsLabel.setVisible(false);
      JBLabel languageLabel = new JBLabel("Language:");
      JBLabel languageTip = new JBLabel("Tip: Type in list to filter");
      JBLabel regionLabel = new JBLabel("Specific Region Only:");

      SortedListModel<String> languageModel = new SortedListModel<String>(new Comparator<String>() {
        @Override
        public int compare(String s1, String s2) {
          // Special language comparator: We want to prefer 2-letter language codes.
          int delta = s1.length() - s2.length();
          if (delta != 0) {
            return delta;
          }
          return String.CASE_INSENSITIVE_ORDER.compare(s1, s2);
        }
      });
      languageModel.addAll(LocaleManager.getLanguageCodes(true));
      myLanguageList.setModel(languageModel);
      myLanguageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myLanguageList.setCellRenderer(FlagManager.get().getLanguageCodeCellRenderer());
      JBScrollPane scroll = new JBScrollPane(myLanguageList);
      JComponent languagePane = ListWithFilter.wrap(myLanguageList, scroll, FlagManager.getLanguageNameMapper());
      languageLabel.setLabelFor(myLanguageList);
      languageTip.setFont(adFont());

      myRegionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myRegionList.setCellRenderer(FlagManager.get().getRegionCodeCellRenderer());
      updateRegionList(null);
      // We can't enable filter lists on the region mode, since fast search doesn't seem to
      // work on models that can change after creation.
      JComponent regionPane = new JBScrollPane(myRegionList);

      Insets insets = new Insets(0, 20, 0, 0);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
      pane.add(languageLabel, gridBagConstraints);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gridBagConstraints.anchor = GridBagConstraints.LINE_START;
      gridBagConstraints.insets = insets;
      pane.add(regionLabel, gridBagConstraints);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.fill = GridBagConstraints.BOTH;
      gridBagConstraints.weightx = 1;
      gridBagConstraints.weighty = 1;
      pane.add(languagePane, gridBagConstraints);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gridBagConstraints.fill = GridBagConstraints.BOTH;
      gridBagConstraints.weightx = 1;
      gridBagConstraints.weighty = 1;
      gridBagConstraints.insets = insets;
      pane.add(regionPane, gridBagConstraints);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.anchor = GridBagConstraints.EAST;
      pane.add(languageTip, gridBagConstraints);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gridBagConstraints.anchor = GridBagConstraints.LINE_START;
      gridBagConstraints.insets = insets;
      pane.add(myShowAllRegions, gridBagConstraints);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gridBagConstraints.anchor = GridBagConstraints.LINE_START;
      pane.add(myWarningsLabel, gridBagConstraints);

      myLanguageList.addListSelectionListener(myUpdatingListListener);
      myRegionList.addListSelectionListener(myUpdatingListListener);
      myLanguageList.addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent listSelectionEvent) {
          // If selecting languages, attempt to pick relevant regions, if applicable
          updateRegionList((String)myLanguageList.getSelectedValue());
        }
      });
      myShowAllRegions.addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent changeEvent) {
          updateRegionList((String)myLanguageList.getSelectedValue());
        }
      });

      return pane;
    }

    /** Populate the region list based on an optional language selection */
    private void updateRegionList(@Nullable String languageCode) {
      final Ref<String> preferred = new Ref<String>(null);
      SortedListModel<String> regionModel = new SortedListModel<String>(new Comparator<String>() {
        @Override
        public int compare(String s1, String s2) {
          // Sort "Any Region" to the top
          if (s1.equals(FAKE_VALUE)) {
            return -1;
          } else if (s2.equals(FAKE_VALUE)) {
            return 1;
          }
          if (s1.equals(preferred.get())) {
            return -1;
          } else if (s2.equals(preferred.get())) {
            return 1;
          }
          // Special language comparator: We want to prefer 2-letter language codes.
          int delta = s1.length() - s2.length();
          if (delta != 0) {
            return delta;
          }
          return String.CASE_INSENSITIVE_ORDER.compare(s1, s2);
        }
      });
      regionModel.add(FAKE_VALUE);
      if (!myShowAllRegions.isSelected() && languageCode != null) {
        preferred.set(LocaleManager.getLanguageRegion(languageCode));
        final List<String> relevant = LocaleManager.getRelevantRegions(languageCode);
        for (String code : relevant) {
          regionModel.add(code);
        }
      } else {
        for (String code : LocaleManager.getRegionCodes(true)) {
          regionModel.add(code);
        }
      }
      myRegionList.setModel(regionModel);
      if (languageCode != null && regionModel.getSize() > 0) {
        myRegionList.setSelectedIndex(0);
      }
    }

    @Override
    void reset(@NotNull LocaleQualifier qualifier) {
      if (qualifier.isValid() && !qualifier.hasFakeValue()) {
        String language = qualifier.getLanguage();
        String region = qualifier.getRegion();
        ListModel languageModel = myLanguageList.getModel();
        ListModel regionModel = myRegionList.getModel();

        for (int i = 0, n = languageModel.getSize(); i < n; i++) {
          if (language.equals(languageModel.getElementAt(i))) {
            myLanguageList.setSelectedIndex(i);
            break;
          }
        }
        if (region != null) {
          boolean found = false;
          for (int i = 0, n = regionModel.getSize(); i < n; i++) {
            if (region.equals(regionModel.getElementAt(i))) {
              myRegionList.setSelectedIndex(i);
              found = true;
              break;
            }
          }
          if (!found && !myShowAllRegions.isSelected()) {
            myShowAllRegions.setSelected(true);
            for (int i = 0, n = regionModel.getSize(); i < n; i++) {
              if (region.equals(regionModel.getElementAt(i))) {
                myRegionList.setSelectedIndex(i);
                break;
              }
            }
          }
        } else {
          myRegionList.setSelectedIndex(-1);
        }
      } else {
        myLanguageList.setSelectedIndex(-1);
        myRegionList.setSelectedIndex(-1);
      }
    }

    @NotNull
    @Override
    LocaleQualifier apply() throws InvalidOptionValueException {
      String selectedLanguage = (String)myLanguageList.getSelectedValue();
      if (selectedLanguage == null) {
        throw new InvalidOptionValueException("Select a language tag");
      }
      String selectedRegion = (String)myRegionList.getSelectedValue();
      if (FAKE_VALUE.equals(selectedRegion)) {
        selectedRegion = null;
      }
      myWarningsLabel.setVisible(selectedLanguage.length() > 2 || selectedRegion != null && selectedRegion.length() > 2);
      return new LocaleQualifier(null, selectedLanguage, selectedRegion, null);
    }
  }

  private abstract class MySizeEditorBase<T extends ResourceQualifier> extends MyQualifierEditor<T> {
    private final JTextField myTextField = new JTextField(3);
    private String myLabelText;

    protected MySizeEditorBase(String labelText) {
      myLabelText = labelText;
    }

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel(myLabelText);
      panel.add(label);
      label.setLabelFor(myTextField);
      myTextField.getDocument().addDocumentListener(myUpdatingDocumentListener);
      panel.add(myTextField);
      return panel;
    }

    @Override
    void reset(@NotNull T qualifier) {
      myTextField.setText(Integer.toString(getValue(qualifier)));
    }

    protected abstract int getValue(@NotNull T qualifier);

    @NotNull
    protected abstract T createQualifier(int value);

    protected abstract String getErrorMessage();

    @NotNull
    @Override
    T apply() throws InvalidOptionValueException {
      try {
        final int value = Integer.parseInt(myTextField.getText().trim());
        if (value < 0) {
          throw new InvalidOptionValueException(getErrorMessage());
        }
        return createQualifier(value);
      }
      catch (NumberFormatException e) {
        throw new InvalidOptionValueException(getErrorMessage());
      }
    }
  }

  private class MySmallestScreenWidthEditor extends MySizeEditorBase<SmallestScreenWidthQualifier> {
    private MySmallestScreenWidthEditor() {
      super("Smallest screen width:");
    }

    @Override
    protected int getValue(@NotNull SmallestScreenWidthQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected SmallestScreenWidthQualifier createQualifier(int value) {
      return new SmallestScreenWidthQualifier(value);
    }

    @Override
    protected String getErrorMessage() {
      return "Incorrect smallest screen width";
    }
  }

  private class MyScreenWidthEditor extends MySizeEditorBase<ScreenWidthQualifier> {
    private MyScreenWidthEditor() {
      super("Screen width:");
    }

    @Override
    protected int getValue(@NotNull ScreenWidthQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenWidthQualifier createQualifier(int value) {
      return new ScreenWidthQualifier(value);
    }

    @Override
    protected String getErrorMessage() {
      return "Incorrect screen width";
    }
  }

  private class MyScreenHeightEditor extends MySizeEditorBase<ScreenHeightQualifier> {
    private MyScreenHeightEditor() {
      super("Screen height:");
    }

    @Override
    protected int getValue(@NotNull ScreenHeightQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenHeightQualifier createQualifier(int value) {
      return new ScreenHeightQualifier(value);
    }

    @Override
    protected String getErrorMessage() {
      return "Incorrect screen height";
    }
  }
}
