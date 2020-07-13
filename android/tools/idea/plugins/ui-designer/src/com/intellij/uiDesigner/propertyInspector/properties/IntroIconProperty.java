/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ResourceFileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.lw.IconDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IconEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.IconRenderer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Method;

/**
 * @author yole
 */
public class IntroIconProperty extends IntrospectedProperty<IconDescriptor> {
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroIconProperty_";

  private final LabelPropertyRenderer<IconDescriptor> myRenderer = new IconRenderer();
  private IconEditor myEditor;

  public IntroIconProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
  }

  public void write(@NotNull IconDescriptor value, XmlWriter writer) {
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, value.getIconPath());
  }

  @NotNull public PropertyRenderer<IconDescriptor> getRenderer() {
    return myRenderer;
  }

  @Nullable public PropertyEditor<IconDescriptor> getEditor() {
    if (myEditor == null) {
      myEditor = new IconEditor();
    }
    return myEditor;
  }

  @Override public IconDescriptor getValue(final RadComponent component) {
    return (IconDescriptor)component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
  }

  @Override protected void setValueImpl(final RadComponent component, final IconDescriptor value) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), value);
    if (value != null) {
      ensureIconLoaded(component.getModule(), value);
      invokeSetter(component, value.getIcon());
    }
    else {
      invokeSetter(component, null);
    }
  }

  public static void ensureIconLoaded(final Module module, final IconDescriptor value) {
    if (value.getIcon() == null) {
      VirtualFile iconFile = ResourceFileUtil.findResourceFileInScope(value.getIconPath(), module.getProject(),
                                                                module.getModuleWithDependenciesAndLibrariesScope(true));
      if (iconFile != null) {
        loadIconFromFile(iconFile, value);
      }
    }
  }

  public static void loadIconFromFile(final VirtualFile virtualFile, final IconDescriptor descriptor) {
    if (virtualFile != null) {
      try {
        descriptor.setIcon(new ImageIcon(virtualFile.contentsToByteArray()));
      }
      catch (Exception e1) {
        descriptor.setIcon(null);
      }
    }
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), null);
    super.setValueImpl(component, null);
    markTopmostModified(component, false);
  }

}
