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
package com.android.tools.idea.editors.strings;

import com.android.builder.model.ProductFlavor;
import com.android.builder.model.Variant;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.ide.common.xml.ManifestData;
import com.android.io.FileWrapper;
import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.editors.strings.table.*;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.Locale;
import com.google.common.collect.ImmutableSet;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.table.JBTable;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class StringResourceViewPanel implements HyperlinkListener {
  private static final boolean HIDE_TRANSLATION_ORDER_LINK = Boolean.getBoolean("hide.order.translations");

  private final AndroidFacet myFacet;
  private JPanel myContainer;
  private JBLoadingPanel myLoadingPanel;
  private JBTable myTable;

  private JTextField myKey;
  private TextFieldWithBrowseButton myDefaultValueWithBrowseBtn;
  private final JTextField myDefaultValue;
  private TextFieldWithBrowseButton myTranslationWithBrowseBtn;
  private final JTextField myTranslation;

  private JPanel myToolbarPanel;

  private LocalResourceRepository myResourceRepository;
  private long myModificationCount;

  private StringResourceData myData;
  private final StringResourceTableModel myTableModel;

  public StringResourceViewPanel(AndroidFacet facet, Disposable parentDisposable) {
    myFacet = facet;

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), parentDisposable, 200);
    myLoadingPanel.add(myContainer);

    ActionToolbar toolbar = createToolbar();
    myToolbarPanel.add(toolbar.getComponent(), BorderLayout.CENTER);

    if (!HIDE_TRANSLATION_ORDER_LINK) {
      HyperlinkLabel hyperlinkLabel = new HyperlinkLabel("Order a translation...");
      myToolbarPanel.add(hyperlinkLabel, BorderLayout.EAST);
      hyperlinkLabel.addHyperlinkListener(this);
      myToolbarPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    }

    myDefaultValueWithBrowseBtn.setButtonIcon(AllIcons.Actions.ShowViewer);
    myTranslationWithBrowseBtn.setButtonIcon(AllIcons.Actions.ShowViewer);

    ActionListener showMultilineActionListener = new ShowMultilineActionListener();
    myDefaultValueWithBrowseBtn.addActionListener(showMultilineActionListener);
    myTranslationWithBrowseBtn.addActionListener(showMultilineActionListener);

    myDefaultValue = myDefaultValueWithBrowseBtn.getTextField();
    myTranslation = myTranslationWithBrowseBtn.getTextField();

    initEditPanel();
    initTable();

    myTableModel = new StringResourceTableModel(null);
    myTable.setModel(myTableModel);
    myLoadingPanel.setLoadingText("Loading string resource data");
    myLoadingPanel.startLoading();
    new ParseTask("Loading string resource data").queue();
  }

  public void reloadData() {
    myLoadingPanel.setLoadingText("Updating string resource data");
    myLoadingPanel.startLoading();
    new ParseTask("Updating string resource data").queue();
  }

  private ActionToolbar createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);

    final AnAction addKeyAction = new AnAction("Add Key", "", AllIcons.ToolbarDecorator.Add) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myData != null);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        NewStringKeyDialog dialog = new NewStringKeyDialog(myFacet, ImmutableSet.copyOf(myData.getKeys()));
        if (dialog.showAndGet()) {
          StringsWriteUtils.createItem(myFacet, dialog.getResFolder(), null, dialog.getKey(), dialog.getDefaultValue(), true);
          reloadData();
        }
      }
    };
    group.add(addKeyAction);

    final AnAction addLocaleAction = new AnAction("Add Locale", "", AndroidIcons.Globe) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myData != null);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        List<Locale> currentLocales = myData.getLocales();
        List<Locale> missingLocales = LocaleMenuAction.getAllLocales();
        missingLocales.removeAll(currentLocales);
        Collections.sort(missingLocales, Locale.LANGUAGE_NAME_COMPARATOR);

        final JBList list = new JBList(missingLocales);
        list.setFixedCellHeight(20);
        list.setCellRenderer(new ColoredListCellRenderer<Locale>() {
          @Override
          protected void customizeCellRenderer(JList list, Locale value, int index, boolean selected, boolean hasFocus) {
            append(LocaleMenuAction.getLocaleLabel(value, false));
            setIcon(value.getFlagImage());
          }
        });
        new ListSpeedSearch(list) {
          @Override
          protected String getElementText(Object element) {
            if (element instanceof Locale) {
              return LocaleMenuAction.getLocaleLabel((Locale)element, false);
            }
            return super.getElementText(element);
          }
        };

        JBPopupFactory.getInstance().createListPopupBuilder(list).setItemChoosenCallback(new Runnable() {
          @Override
          public void run() {
            // pick some value to add to this locale
            Map<String, ResourceItem> defaultValues = myData.getDefaultValues();
            String key = "app_name";
            ResourceItem defaultValue = defaultValues.get(key);

            if (defaultValue == null) {
              Map.Entry<String, ResourceItem> firstEntry = defaultValues.entrySet().iterator().next();
              key = firstEntry.getKey();
              defaultValue = firstEntry.getValue();
            }

            Locale l = (Locale)list.getSelectedValue();
            StringsWriteUtils.createItem(myFacet, myFacet.getPrimaryResourceDir(), l, key,
                                         StringResourceData.resourceToString(defaultValue), true);
            reloadData();
          }
        }).createPopup().showUnderneathOf(toolbar.getComponent());
      }
    };
    group.addAction(addLocaleAction);

    return toolbar;
  }

  public boolean dataIsCurrent() {
    return myResourceRepository != null && myModificationCount >= myResourceRepository.getModificationCount();
  }

  private void initEditPanel() {
    KeyListener keyListener = new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        JTextComponent component = (JTextComponent)e.getComponent();

        if (component.isEditable()) {
          onTextFieldUpdate(component);
        }
      }
    };
    myKey.addKeyListener(keyListener);
    myDefaultValue.addKeyListener(keyListener);
    myTranslation.addKeyListener(keyListener);
  }

  private void onTextFieldUpdate(JTextComponent component) {
    StringResourceTableModel model = (StringResourceTableModel)myTable.getModel();
    if (myTable.getSelectedColumnCount() != 1 || myTable.getSelectedRowCount() != 1) {
      return;
    }

    int row = myTable.getSelectedRow();
    int column;

    if (component == myKey) {
      column = ConstantColumn.KEY.ordinal();
    }
    else if (component == myDefaultValue) {
      column = ConstantColumn.DEFAULT_VALUE.ordinal();
    }
    else {
      assert component == myTranslation;
      column = myTable.getSelectedColumn();
    }

    String value = component.getText();
    model.setValueAt(value, row, column);
  }

  private void initTable() {
    myTable.setCellSelectionEnabled(true);
    myTable.getTableHeader().setReorderingAllowed(false);

    MouseAdapter headerListener = new HeaderCellSelectionListener(myTable);
    myTable.getTableHeader().addMouseListener(headerListener);
    myTable.getTableHeader().addMouseMotionListener(headerListener);

    CellSelectionListener selectionListener = new CellSelectionListener();
    myTable.getSelectionModel().addListSelectionListener(selectionListener);
    myTable.getColumnModel().getSelectionModel().addListSelectionListener(selectionListener);

    myTable.setDefaultEditor(String.class, new StringsCellEditor());
    myTable.getParent().addComponentListener(new ResizeListener(myTable));
  }

  @NotNull
  public JPanel getComponent() {
    return myLoadingPanel;
  }

  @NotNull
  public JBTable getPreferredFocusedComponent() {
    return myTable;
  }

  private void createUIComponents() {
    myTable = new JBTable();
  }

  public JTable getTable() {
    return myTable;
  }

  @Override
  public void hyperlinkUpdate(HyperlinkEvent e) {
    StringBuilder sb = new StringBuilder("https://translate.google.com/manager/android_studio/");

    // Application Version
    sb.append("?asVer=");
    ApplicationInfo ideInfo = ApplicationInfo.getInstance();
    sb.append(ideInfo.getMajorVersion()).append('.')
      .append(ideInfo.getMinorVersion()).append('.')
      .append(ideInfo.getMicroVersion()).append('.')
      .append(ideInfo.getPatchVersion());


    // Package name
    String pkg = ManifestInfo.get(myFacet.getModule(), false).getPackage();
    if (pkg != null) {
      sb.append("&pkgName=");
      sb.append(pkg.replace('.', '_'));
    }

    // Application ID
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(myFacet);
    String appId = moduleInfo.getPackage();
    if (appId != null) {
      sb.append("&appId=");
      sb.append(appId.replace('.', '_'));
    }

    // Version code
    String versionCode = null;
    if (myFacet.requiresAndroidModel() && myFacet.getAndroidModel() != null) {
        Variant variant = myFacet.getAndroidModel().getSelectedVariant();
      ProductFlavor flavor = variant.getMergedFlavor();
      if (flavor.getVersionCode() != null) {
        versionCode = flavor.getVersionCode().toString();
      }
    }
    if (versionCode == null) {
      VirtualFile manifestFile = AndroidRootUtil.getPrimaryManifestFile(myFacet);
      if (manifestFile != null) {
        File file = VfsUtilCore.virtualToIoFile(manifestFile);
        try {
          ManifestData manifest = AndroidManifestParser.parse(new FileWrapper(file));
          if (manifest.getVersionCode() != null) {
            versionCode = manifest.getVersionCode().toString();
          }
        }
        catch (Exception ignore) {
        }
      }
    }
    if (versionCode != null) {
      sb.append("&apkVer=");
      sb.append(versionCode);
    }

    // If we support additional IDE languages, we can send the language used in the IDE here
    //sb.append("&lang=en");

    BrowserUtil.browse(sb.toString());
  }

  private class ParseTask extends Task.Backgroundable {
    private AtomicReference<LocalResourceRepository> myResourceRepositoryRef = new AtomicReference<LocalResourceRepository>(null);
    private AtomicReference<StringResourceData> myResourceDataRef = new AtomicReference<StringResourceData>(null);

    public ParseTask(String description) {
      super(myFacet.getModule().getProject(), description, false);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      LocalResourceRepository moduleResources = myFacet.getModuleResources(true);
      myResourceRepositoryRef.set(moduleResources);
      myResourceDataRef.set(StringResourceParser.parse(myFacet, moduleResources));
    }

    @Override
    public void onSuccess() {
      myLoadingPanel.stopLoading();

      myData = myResourceDataRef.get();
      myResourceRepository = myResourceRepositoryRef.get();
      myModificationCount = myResourceRepository.getModificationCount();

      myTableModel.setData(myData);
      myTableModel.fireTableStructureChanged();
      ColumnUtil.setColumns(myTable);
    }

    @Override
    public void onCancel() {
      myLoadingPanel.stopLoading();
    }
  }

  private class CellSelectionListener implements ListSelectionListener {
    @Override
    public void valueChanged(ListSelectionEvent e) {
      if (e.getValueIsAdjusting()) {
        return;
      }

      if (myTable.getSelectedColumnCount() != 1 || myTable.getSelectedRowCount() != 1) {
        setTextAndEditable(myKey, "", false);
        setTextAndEditable(myDefaultValue, "", false);
        setTextAndEditable(myTranslation, "", false);
        myDefaultValueWithBrowseBtn.getButton().setEnabled(false);
        myTranslationWithBrowseBtn.getButton().setEnabled(false);
        return;
      }

      StringResourceTableModel model = (StringResourceTableModel) myTable.getModel();

      int row = myTable.getSelectedRow();
      int column = myTable.getSelectedColumn();
      Locale locale = model.localeOfColumn(column);

      String key = model.keyOfRow(row);
      setTextAndEditable(myKey, key, false); // TODO: keys are not editable, we want them to be refactor operations

      String defaultValue = (String)model.getValueAt(row, ConstantColumn.DEFAULT_VALUE.ordinal());
      boolean defaultValueEditable = !StringUtil.containsChar(defaultValue, '\n'); // don't allow editing multiline chars in a text field
      setTextAndEditable(myDefaultValue, defaultValue, defaultValueEditable);
      myDefaultValueWithBrowseBtn.getButton().setEnabled(true);

      boolean translationEditable = false;
      String translation = "";
      if (locale != null) {
        translation = (String)model.getValueAt(row, column);
        translationEditable = !StringUtil.containsChar(translation, '\n'); // don't allow editing multiline chars in a text field
      }
      setTextAndEditable(myTranslation, translation, translationEditable);
      myTranslationWithBrowseBtn.getButton().setEnabled(locale != null);
    }

    private void setTextAndEditable(@NotNull JTextComponent component, @NotNull String text, boolean editable) {
      component.setText(text);
      component.setCaretPosition(0);
      component.setEditable(editable);
      // If a text component is not editable when it gains focus and becomes editable while still focused,
      // the caret does not appear, so we need to set the caret visibility manually
      component.getCaret().setVisible(editable && component.hasFocus());

      component.setFont(FontUtil.getFontAbleToDisplay(text, component.getFont()));
    }
  }

  private class ShowMultilineActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (myTable.getSelectedRowCount() != 1 || myTable.getSelectedColumnCount() != 1) {
        return;
      }

      int row = myTable.getSelectedRow();
      int column = myTable.getSelectedColumn();

      StringResourceTableModel model = (StringResourceTableModel)myTable.getModel();
      String key = model.keyOfRow(row);
      String value = (String)model.getValueAt(row, ConstantColumn.DEFAULT_VALUE.ordinal());

      Locale locale = model.localeOfColumn(column);
      String translation = locale == null ? null : (String)model.getValueAt(row, column);

      MultilineStringEditorDialog d = new MultilineStringEditorDialog(myFacet, key, value, locale, translation);
      if (d.showAndGet()) {
        if (!StringUtil.equals(value, d.getDefaultValue())) {
          model.setValueAt(d.getDefaultValue(), row, ConstantColumn.DEFAULT_VALUE.ordinal());
        }

        if (locale != null && !StringUtil.equals(translation, d.getTranslation())) {
          model.setValueAt(d.getTranslation(), row, column);
        }
      }
    }
  }
}
