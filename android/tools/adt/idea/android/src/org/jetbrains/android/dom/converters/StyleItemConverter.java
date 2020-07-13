/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.dom.converters;

import com.intellij.util.xml.Converter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.WrappingConverter;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.resources.StyleItem;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 11, 2009
 * Time: 6:19:55 PM
 * To change this template use File | Settings | File Templates.
 */
public class StyleItemConverter extends WrappingConverter {
  @Nullable
  private static ResolvingConverter findConverterForAttribute(String nsPrefix,
                                                              String localName,
                                                              @NotNull AndroidFacet facet,
                                                              @NotNull GenericDomValue element) {
    ResourceManager manager = facet.getResourceManager("android".equals(nsPrefix)
                                                       ? AndroidUtils.SYSTEM_RESOURCE_PACKAGE
                                                       : null, element.getXmlElement());
    if (manager != null) {
      AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
      if (attrDefs != null) {
        AttributeDefinition attr = attrDefs.getAttrDefByName(localName);
        if (attr != null) {
          return AndroidDomUtil.getConverter(attr);
        }
      }
    }
    return null;
  }

  @Override
  public Converter getConverter(@NotNull GenericDomValue element) {
    StyleItem item = (StyleItem)element;
    String name = item.getName().getValue();
    if (name != null) {
      String[] strs = name.split(":");
      if (strs.length == 1 || strs.length == 2) {
        AndroidFacet facet = AndroidFacet.getInstance(element);
        if (facet != null) {
          String namespacePrefix = strs.length == 2 ? strs[0] : null;
          String localName = strs[strs.length - 1];
          return findConverterForAttribute(namespacePrefix, localName, facet, element);
        }
      }
    }
    return null;
  }
}
