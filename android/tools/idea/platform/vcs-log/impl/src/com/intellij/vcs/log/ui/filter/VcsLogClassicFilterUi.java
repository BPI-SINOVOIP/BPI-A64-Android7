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
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SearchTextFieldWithStoredHistory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl;
import com.intellij.vcs.log.impl.VcsLogHashFilterImpl;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 */
public class VcsLogClassicFilterUi implements VcsLogFilterUi {

  private static final Logger LOG = Logger.getInstance(VcsLogClassicFilterUi.class);
  private static final String HASH_PATTERN = "[a-fA-F0-9]{7,}";

  @NotNull private final SearchTextField myTextFilter;
  @NotNull private final VcsLogUiImpl myUi;
  @NotNull private final DefaultActionGroup myActionGroup;

  @NotNull private final BranchFilterPopupComponent myBranchFilterComponent;
  @NotNull private final UserFilterPopupComponent myUserFilterComponent;
  @NotNull private final DateFilterPopupComponent myDateFilterComponent;
  @NotNull private final StructureFilterPopupComponent myStructureFilterComponent;

  public VcsLogClassicFilterUi(@NotNull VcsLogUiImpl ui, @NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUiProperties uiProperties,
                               @NotNull DataPack initialDataPack) {
    myUi = ui;

    myTextFilter = new SearchTextFieldWithStoredHistory("Vcs.Log.Text.Filter.History") {
      @Override
      protected void onFieldCleared() {
        applyFilters();
      }
    };
    myTextFilter.getTextEditor().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        applyFilters();
        myTextFilter.addCurrentTextToHistory();
      }
    });

    myBranchFilterComponent = new BranchFilterPopupComponent(this, initialDataPack, uiProperties);
    myUserFilterComponent = new UserFilterPopupComponent(this, logDataHolder, uiProperties);
    myDateFilterComponent  = new DateFilterPopupComponent(this);
    myStructureFilterComponent = new StructureFilterPopupComponent(this, logDataHolder.getRoots());

    myActionGroup = new DefaultActionGroup();
    myActionGroup.add(new TextFilterComponent(myTextFilter));
    myActionGroup.add(new FilterActionComponent(myBranchFilterComponent));
    myActionGroup.add(new FilterActionComponent(myUserFilterComponent));
    myActionGroup.add(new FilterActionComponent(myDateFilterComponent));
    myActionGroup.add(new FilterActionComponent(myStructureFilterComponent));
  }

  public void updateDataPack(@NotNull DataPack dataPack) {
    myBranchFilterComponent.updateDataPack(dataPack);
  }

  /**
   * Returns filter components which will be added to the Log toolbar.
   */
  @NotNull
  public ActionGroup getActionGroup() {
    return myActionGroup;
  }

  @NotNull
  public List<JComponent> getComponents() {
    return Arrays.<JComponent>asList(myTextFilter.getTextEditor(), myBranchFilterComponent, myUserFilterComponent,
                                     myDateFilterComponent, myStructureFilterComponent);
  }

  @NotNull
  @Override
  public VcsLogFilterCollection getFilters() {
    Pair<VcsLogTextFilter, VcsLogHashFilter> filtersFromText = getFiltersFromTextArea(myTextFilter.getText().trim());
    return new VcsLogFilterCollectionImpl(myBranchFilterComponent.getFilter(), myUserFilterComponent.getFilter(),
                                          filtersFromText.second, myDateFilterComponent.getFilter(),
                                          filtersFromText.first, myStructureFilterComponent.getFilter());
  }

  @NotNull
  private static Pair<VcsLogTextFilter, VcsLogHashFilter> getFiltersFromTextArea(@NotNull String text) {
    if (text.isEmpty()) {
      return Pair.empty();
    }
    List<String> hashes = ContainerUtil.newArrayList();
    for (String word : StringUtil.split(text, " ")) {
      if (!StringUtil.isEmptyOrSpaces(word) && word.matches(HASH_PATTERN)) {
        hashes.add(word);
      }
      else {
        break;
      }
    }

    VcsLogTextFilter textFilter;
    VcsLogHashFilterImpl hashFilter;
    if (!hashes.isEmpty()) { // text is ignored if there are hashes in the text
      textFilter = null;
      hashFilter = new VcsLogHashFilterImpl(hashes);
    }
    else {
      textFilter = new VcsLogTextFilterImpl(text);
      hashFilter = null;
    }
    return Pair.<VcsLogTextFilter, VcsLogHashFilter>create(textFilter, hashFilter);
  }

  @Override
  public void setFilter(@NotNull VcsLogFilter filter) {
    if (filter instanceof VcsLogBranchFilter) {
      Collection<String> values = ((VcsLogBranchFilter)filter).getBranchNames();
      myBranchFilterComponent.apply(values, MultipleValueFilterPopupComponent.displayableText(values),
                                    MultipleValueFilterPopupComponent.tooltip(values));
    }
  }

  void applyFilters() {
    myUi.applyFiltersAndUpdateUi();
  }

  private static class TextFilterComponent extends DumbAwareAction implements CustomComponentAction {

    private final SearchTextField mySearchField;

    TextFilterComponent(SearchTextField searchField) {
      mySearchField = searchField;
    }

    @Override
    public JComponent createCustomComponent(Presentation presentation) {
      JPanel panel = new JPanel();
      JLabel filterCaption = new JLabel("Filter:");
      filterCaption.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getInactiveTextColor());
      panel.add(filterCaption);
      panel.add(mySearchField);
      return panel;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }
  }

  private static class FilterActionComponent extends DumbAwareAction implements CustomComponentAction {
    private final FilterPopupComponent myComponent;

    public FilterActionComponent(FilterPopupComponent component) {
      myComponent = component;
    }

    @Override
    public JComponent createCustomComponent(Presentation presentation) {
      return myComponent;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }
  }

}
