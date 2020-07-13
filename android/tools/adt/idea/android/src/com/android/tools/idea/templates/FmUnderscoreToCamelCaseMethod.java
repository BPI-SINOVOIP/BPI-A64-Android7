/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.templates;

import freemarker.template.*;

import java.util.List;

/**
 * Method invoked by FreeMarker to convert a CamelCase word into
 * underscore_names.
 */
public class FmUnderscoreToCamelCaseMethod implements TemplateMethodModelEx {
  @Override
  public TemplateModel exec(List args) throws TemplateModelException {
    if (args.size() != 1) {
      throw new TemplateModelException("Wrong arguments");
    }
    String argString = ((TemplateScalarModel)args.get(0)).getAsString();
    return new SimpleScalar(TemplateUtils.underlinesToCamelCase(argString));
  }
}