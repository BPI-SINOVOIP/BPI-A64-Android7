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
package com.android.tools.idea.npw;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.actions.NewAndroidComponentAction;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.ui.ASGallery;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.intellij.openapi.Disposable;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

import static com.android.tools.idea.wizard.WizardConstants.DEFAULT_GALLERY_THUMBNAIL_SIZE;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;

/**
 * Gallery of Android activity templates.
 */
public class ActivityGalleryStep extends DynamicWizardStepWithDescription {
  @SuppressWarnings("unchecked")
  public static final Key<TemplateEntry[]> KEY_TEMPLATES =
    ScopedStateStore.createKey("template.list", ScopedStateStore.Scope.STEP, TemplateEntry[].class);
  private final FormFactorUtils.FormFactor myFormFactor;
  private final Key<TemplateEntry> myCurrentSelectionKey;
  private final boolean myShowSkipEntry;
  private ASGallery<Optional<TemplateEntry>> myGallery;

  public ActivityGalleryStep(@NotNull FormFactorUtils.FormFactor formFactor, boolean showSkipEntry,
                             @NotNull Key<TemplateEntry> currentSelectionKey, @NotNull Disposable disposable) {
    super(disposable);
    myFormFactor = formFactor;
    myCurrentSelectionKey = currentSelectionKey;
    myShowSkipEntry = showSkipEntry;
    setBodyComponent(createGallery());
  }

  private static String format(ScopedStateStore state, String formatString, Key<?>... keys) {
    Object[] arguments = new Object[keys.length];
    int i = 0;
    for (Key<?> key : keys) {
      arguments[i++] = state.get(key);
    }
    return String.format(formatString, arguments);
  }

  private JComponent createGallery() {
    myGallery = new ASGallery<Optional<TemplateEntry>>();
    Dimension thumbnailSize = DEFAULT_GALLERY_THUMBNAIL_SIZE;
    myGallery.setThumbnailSize(thumbnailSize);
    myGallery.setMinimumSize(new Dimension(thumbnailSize.width * 2 + 1, thumbnailSize.height));
    myGallery.setLabelProvider(new Function<Optional<TemplateEntry>, String>() {
      @Override
      public String apply(Optional<TemplateEntry> template) {
        if (template.isPresent()) {
          return template.get().getTitle();
        }
        else {
          return getNoTemplateEntryName();
        }
      }
    });
    myGallery.setImageProvider(new Function<Optional<TemplateEntry>, Image>() {
      @Override
      public Image apply(Optional<TemplateEntry> input) {
        return input.isPresent() ? input.get().getImage() : null;
      }
    });
    myGallery.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        saveState(myGallery);
      }
    });
    myGallery.setName("Templates Gallery");
    AccessibleContext accessibleContext = myGallery.getAccessibleContext();
    if (accessibleContext != null) {
      accessibleContext.setAccessibleDescription(getStepTitle());
    }
    JPanel panel = new JPanel(new JBCardLayout());
    panel.add("only card", new JBScrollPane(myGallery));
    return panel;
  }

  protected String getNoTemplateEntryName() {
    return "Add No Activity";
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    //// First time page is shown, controls are narrow hence gallery assumes single column mode.
    //// We need to scroll up.
    UiNotifyConnector.doWhenFirstShown(myGallery, new Runnable() {
      @Override
      public void run() {
        myGallery.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
      }
    });
  }

  @Override
  public boolean validate() {
    TemplateEntry template = myState.get(myCurrentSelectionKey);
    final PageStatus status;
    if (template == null) {
      status = myShowSkipEntry ? PageStatus.OK : PageStatus.NOTHING_SELECTED;
    }
    else if (isIncompatibleMinSdk(template)) {
      status = PageStatus.INCOMPATIBLE_MAIN_SDK;
    }
    else if (isIncompatibleBuildApi(template)) {
      status = PageStatus.INCOMPATIBLE_BUILD_API;
    }
    else {
      status = PageStatus.OK;
    }
    setErrorHtml(status.formatMessage(myState));
    return status.isPageValid();
  }

  private boolean isIncompatibleBuildApi(TemplateEntry template) {
    Integer buildSdk = myState.get(AddAndroidActivityPath.KEY_BUILD_SDK);
    return buildSdk != null && buildSdk < template.getMinBuildApi();
  }

  private boolean isIncompatibleMinSdk(@NotNull TemplateEntry template) {
    AndroidVersion minSdk = myState.get(AddAndroidActivityPath.KEY_MIN_SDK);
    return minSdk != null && minSdk.getApiLevel() < template.getMinSdk();
  }

  @Override
  public void init() {
    super.init();
    String formFactorName = myFormFactor.id;
    TemplateListProvider templateListProvider = new TemplateListProvider(formFactorName, NewAndroidComponentAction.NEW_WIZARD_CATEGORIES,
                                                                         TemplateManager.EXCLUDED_TEMPLATES);
    TemplateEntry[] list = templateListProvider.deriveValue(myState, AddAndroidActivityPath.KEY_IS_LAUNCHER, null);
    myGallery.setModel(JBList.createDefaultListModel((Object[])wrapInOptionals(list)));
    myState.put(KEY_TEMPLATES, list);
    if (list.length > 0) {
      myState.put(myCurrentSelectionKey, list[0]);
    }
    register(myCurrentSelectionKey, myGallery, new ComponentBinding<TemplateEntry, ASGallery<Optional<TemplateEntry>>>() {
      @Override
      public void setValue(TemplateEntry newValue, @NotNull ASGallery<Optional<TemplateEntry>> component) {
        component.setSelectedElement(Optional.fromNullable(newValue));
      }

      @Override
      @Nullable
      public TemplateEntry getValue(@NotNull ASGallery<Optional<TemplateEntry>> component) {
        Optional<TemplateEntry> selection = component.getSelectedElement();
        if (selection != null && selection.isPresent()) {
          return selection.get();
        }
        else {
          return null;
        }
      }
    });
    register(KEY_TEMPLATES, myGallery, new ComponentBinding<TemplateEntry[], ASGallery<Optional<TemplateEntry>>>() {
      @Override
      public void setValue(@Nullable TemplateEntry[] newValue, @NotNull ASGallery<Optional<TemplateEntry>> component) {
        component.setModel(JBList.createDefaultListModel((Object[])wrapInOptionals(newValue)));
      }
    });
    registerValueDeriver(KEY_TEMPLATES, templateListProvider);
  }

  private Optional[] wrapInOptionals(@Nullable TemplateEntry[] newValue) {
    if (newValue == null) {
      return new Optional[0];
    }
    final Optional[] model;
    int i;
    if (myShowSkipEntry) {
      model = new Optional[newValue.length + 1];
      model[0] = Optional.absent();
      i = 1;
    }
    else {
      model = new Optional[newValue.length];
      i = 0;
    }
    for (TemplateEntry entry : newValue) {
      model[i++] = Optional.of(entry);
    }
    return model;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGallery;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Activity Gallery";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "Add an activity to " + myFormFactor.id;
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return null;
  }

  @Nullable
  @Override
  protected Icon getStepIcon() {
    return myFormFactor.getIcon();
  }

  private enum PageStatus {
    OK, INCOMPATIBLE_BUILD_API, INCOMPATIBLE_MAIN_SDK, NOTHING_SELECTED;

    public boolean isPageValid() {
      return this == OK;
    }

    @Nullable
    public String formatMessage(ScopedStateStore state) {
      switch (this) {
        case OK:
          return null;
        case INCOMPATIBLE_BUILD_API:
          return format(state, "Selected activity template has a minimum build API level of %d.", AddAndroidActivityPath.KEY_BUILD_SDK);
        case INCOMPATIBLE_MAIN_SDK:
          return format(state, "Selected activity template has a minimum SDK level of %s.", AddAndroidActivityPath.KEY_MIN_SDK);
        case NOTHING_SELECTED:
          return "No activity template was selected.";
        default:
          throw new IllegalArgumentException(name());
      }
    }

  }
}
