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
package com.intellij.application.options.editor;

import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.xml.XmlBundle;

/**
 * @author yole
 */
public class WebEditorAppearanceConfigurable extends BeanConfigurable<WebEditorOptions> implements UnnamedConfigurable {
  public WebEditorAppearanceConfigurable() {
    super(WebEditorOptions.getInstance());
    checkBox("breadcrumbsEnabled", XmlBundle.message("xml.editor.options.breadcrumbs.title"));
    checkBox("breadcrumbsEnabledInXml", XmlBundle.message("xml.editor.options.breadcrumbs.for.xml.title"));
    checkBox("showCssColorPreviewInGutter", "Show CSS color preview icon in gutter");
    checkBox("showCssInlineColorPreview", "Show CSS color preview as background");
  }
}
