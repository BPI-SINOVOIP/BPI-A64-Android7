/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.ProjectCodeStyleSettingsManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyCodeStyleSettingsFacade;

public class GroovyCodeStyleSettingsFacadeImpl extends GroovyCodeStyleSettingsFacade {
  private final Project myProject;

  public GroovyCodeStyleSettingsFacadeImpl(Project project, ProjectCodeStyleSettingsManager codeStyleSettingsManager) {
    myProject = project;
  }

  private GroovyCodeStyleSettings getSettings() {
    return CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().getCustomSettings(GroovyCodeStyleSettings.class);
  }

  @Override
  public boolean useFqClassNames() {
    return getSettings().USE_FQ_CLASS_NAMES;
  }

  @Override
  public boolean useFqClassNamesInJavadoc() {
    return getSettings().USE_FQ_CLASS_NAMES_IN_JAVADOC;
  }

  @Override
  public int staticFieldsOrderWeight() {
    return CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().STATIC_FIELDS_ORDER_WEIGHT;
  }

  @Override
  public int fieldsOrderWeight() {
    return CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().FIELDS_ORDER_WEIGHT;
  }

  @Override
  public int staticMethodsOrderWeight() {
    return CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().STATIC_METHODS_ORDER_WEIGHT;
  }

  @Override
  public int methodsOrderWeight() {
    return CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().METHODS_ORDER_WEIGHT;
  }

  @Override
  public int staticInnerClassesOrderWeight() {
    return CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().STATIC_INNER_CLASSES_ORDER_WEIGHT;
  }

  @Override
  public int innerClassesOrderWeight() {
    return CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().INNER_CLASSES_ORDER_WEIGHT;
  }

  @Override
  public int constructorsOrderWeight() {
    return CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().CONSTRUCTORS_ORDER_WEIGHT;
  }

  @Override
  public boolean insertInnerClassImports() {
    return getSettings().INSERT_INNER_CLASS_IMPORTS;
  }
}
