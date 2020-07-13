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
package com.android.tools.idea.editors.navigation.macros;

import com.android.tools.idea.editors.navigation.NavigationEditorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import java.util.IdentityHashMap;
import java.util.Map;

public class Macros {
  private static final String CREATE_INTENT =
    "void macro(Context context, Class activityClass) { new android.content.Intent(context, activityClass); }";

  private static final String DEFINE_INNER_CLASS =
    "void macro(Class $Interface, Void $method, Class $Type, Object $arg, final Statement $f) {" +
    "    new $Interface() {" +
    "        public void $method($Type $arg) {" +
    "            $f.$();" +
    "        }" +
    "    };" +
    "}";

  private static final String INSTALL_CLICK_LISTENER =
    "void macro(android.view.View $view, Statement $f) {" +
    "    $view.setOnClickListener(new android.view.View.OnClickListener() {" +
    "        @Override" +
    "        public void onClick(android.view.View view) {" +
    "            $f.$();" +
    "        }" +
    "    });" +
    "}";

  private static final String INSTALL_MENU_ITEM_CLICK_LISTENER =
    "void macro(android.view.MenuItem $menuItem, final Statement $f, final boolean $consume) {" +
    "    $menuItem.setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {" +
    "        @Override" +
    "        public boolean onMenuItemClick(android.view.MenuItem menuItem) {" +
    "            $f.$();" +
    "            return $consume;" +
    "        }" +
    "    });" +
    "}";

  private static final String INSTALL_ITEM_CLICK_LISTENER =
    "void macro(ListView $listView, final Statement $f) {" +
    "    $listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {" +
    "        @Override" +
    "        public void onItemClick(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {" +
    "            $f.$$();" +
    "        }" +
    "    });" +
    "}";

  private static final String FIND_MENU_ITEM =
    "void macro(Menu $menu, int $id) {" +
    "    $menu.findItem(R.id.$id);" +
    "}";

  private static final String FIND_VIEW_BY_ID_1 =
    "void macro(int $id) { findViewById(R.id.$id);}";

  private static final String FIND_VIEW_BY_ID_2 =
    "void macro(Object $finder, int $id) { $finder.findViewById(R.id.$id);}";

  public static final String FIND_FRAGMENT_BY_TAG =
    "void macro(void $fragmentManager, int $tag) { $fragmentManager.findFragmentByTag($tag);}";

  private static final String LAUNCH_ACTIVITY_WITH_ARG =
    "<T extends Serializable> void macro(Context context, Class activityClass, String name, T value) {" +
    "    context.startActivity(new android.content.Intent(context, activityClass).putExtra(name, value));" +
    "}";

  public final MultiMatch createIntent;
  public final MultiMatch installClickAndCallMacro;
  public final MultiMatch installItemClickAndCallMacro;
  public final MultiMatch installMenuItemClickAndCallMacro;
  public final MultiMatch defineInnerClassToLaunchActivityMacro;
  public final MultiMatch findMenuItem;
  public final MultiMatch findViewById1;
  public final MultiMatch findViewById2;
  public final MultiMatch findFragmentByTag;
  private static Map<Project, Macros> ourProjectToMacros = new IdentityHashMap<Project, Macros>();
  private final Project myProject;

  public static Macros getInstance(Project project) {
    Macros result = ourProjectToMacros.get(project);
    if (result == null) {
      ourProjectToMacros.put(project, result = new Macros(project));
    }
    return result;
  }

  public MultiMatch createMacro(String methodDefinition) {
    return new MultiMatch(CodeTemplate.fromMethod(getMethodFromText(methodDefinition)));
  }

  private PsiMethod getMethodFromText(String definition) {
    return NavigationEditorUtils.createMethodFromText(myProject, definition, null);
  }

  private Macros(Project project) {
    myProject = project;

    createIntent = createMacro(CREATE_INTENT);
    findMenuItem = createMacro(FIND_MENU_ITEM);
    findViewById1 = createMacro(FIND_VIEW_BY_ID_1);
    findViewById2 = createMacro(FIND_VIEW_BY_ID_2);
    findFragmentByTag = createMacro(FIND_FRAGMENT_BY_TAG);

    installClickAndCallMacro = createMacro(INSTALL_CLICK_LISTENER);
    installItemClickAndCallMacro = createMacro(INSTALL_ITEM_CLICK_LISTENER);
    installMenuItemClickAndCallMacro = createMacro(INSTALL_MENU_ITEM_CLICK_LISTENER);

    defineInnerClassToLaunchActivityMacro = createMacro(DEFINE_INNER_CLASS);
    defineInnerClassToLaunchActivityMacro.addSubMacro("$f", CodeTemplate.fromMethod(getMethodFromText(LAUNCH_ACTIVITY_WITH_ARG)));
  }
}
