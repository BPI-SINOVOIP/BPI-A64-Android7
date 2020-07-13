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
package com.android.tools.idea.rendering;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.lang.databinding.DbUtil;
import com.android.tools.lint.detector.api.LintUtils;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColorUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.ide.common.resources.ResourceResolver.MAX_RESOURCE_INDIRECTION;

public class ResourceHelper {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.ResourceHelper");
  public static final String STATE_NAME_PREFIX = "state_";

  /**
   * Returns true if the given style represents a project theme
   *
   * @param style a theme style string
   * @return true if the style string represents a project theme, as opposed
   *         to a framework theme
   */
  public static boolean isProjectStyle(@NotNull String style) {
    assert style.startsWith(STYLE_RESOURCE_PREFIX) || style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX) : style;

    return style.startsWith(STYLE_RESOURCE_PREFIX);
  }

  /**
   * Returns the theme name to be shown for theme styles, e.g. for "@style/Theme" it
   * returns "Theme"
   *
   * @param style a theme style string
   * @return the user visible theme name
   */
  @NotNull
  public static String styleToTheme(@NotNull String style) {
    if (style.startsWith(STYLE_RESOURCE_PREFIX)) {
      style = style.substring(STYLE_RESOURCE_PREFIX.length());
    }
    else if (style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
      style = style.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
    }
    else if (style.startsWith(PREFIX_RESOURCE_REF)) {
      // @package:style/foo
      int index = style.indexOf('/');
      if (index != -1) {
        style = style.substring(index + 1);
      }
    }
    return style;
  }

  /**
   * Is this a resource that can be defined in any file within the "values" folder?
   * <p/>
   * Some resource types can be defined <b>both</b> as a separate XML file as well
   * as defined within a value XML file. This method will return true for these types
   * as well. In other words, a ResourceType can return true for both
   * {@link #isValueBasedResourceType} and {@link #isFileBasedResourceType}.
   *
   * @param type the resource type to check
   * @return true if the given resource type can be represented as a value under the
   *         values/ folder
   */
  public static boolean isValueBasedResourceType(@NotNull ResourceType type) {
    List<ResourceFolderType> folderTypes = FolderTypeRelationship.getRelatedFolders(type);
    for (ResourceFolderType folderType : folderTypes) {
      if (folderType == ResourceFolderType.VALUES) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns the resource name of the given file.
   * <p>
   * For example, {@code getResourceName(</res/layout-land/foo.xml, false) = "foo"}.
   *
   * @param file the file to compute a resource name for
   * @return the resource name
   */
  @NotNull
  public static String getResourceName(@NotNull VirtualFile file) {
    // Note that we use getBaseName here rather than {@link VirtualFile#getNameWithoutExtension}
    // because that method uses lastIndexOf('.') rather than indexOf('.') -- which means that
    // for a nine patch drawable it would include ".9" in the resource name
    return LintUtils.getBaseName(file.getName());
  }

  /**
   * Returns the resource name of the given file.
   * <p>
   * For example, {@code getResourceName(</res/layout-land/foo.xml, false) = "foo"}.
   *
   * @param file the file to compute a resource name for
   * @return the resource name
   */
  @NotNull
  public static String getResourceName(@NotNull PsiFile file) {
    // See getResourceName(VirtualFile)
    // We're replicating that code here rather than just calling
    // getResourceName(file.getVirtualFile());
    // since file.getVirtualFile can return null
    return LintUtils.getBaseName(file.getName());
  }

  /**
   * Returns the resource URL of the given file. The file <b>must</b> be a valid resource
   * file, meaning that it is in a proper resource folder, and it <b>must</b> be a
   * file-based resource (e.g. layout, drawable, menu, etc) -- not a values file.
   * <p>
   * For example, {@code getResourceUrl(</res/layout-land/foo.xml, false) = "@layout/foo"}.
   *
   * @param file the file to compute a resource url for
   * @return the resource url
   */
  @NotNull
  public static String getResourceUrl(@NotNull VirtualFile file) {
    ResourceFolderType type = ResourceFolderType.getFolderType(file.getParent().getName());
    assert type != null && type != ResourceFolderType.VALUES;
    return PREFIX_RESOURCE_REF + type.getName() + '/' + getResourceName(file);
  }

  /**
   * Is this a resource that is defined in a file named by the resource plus the XML
   * extension?
   * <p/>
   * Some resource types can be defined <b>both</b> as a separate XML file as well as
   * defined within a value XML file along with other properties. This method will
   * return true for these resource types as well. In other words, a ResourceType can
   * return true for both {@link #isValueBasedResourceType} and
   * {@link #isFileBasedResourceType}.
   *
   * @param type the resource type to check
   * @return true if the given resource type is stored in a file named by the resource
   */
  public static boolean isFileBasedResourceType(@NotNull ResourceType type) {
    List<ResourceFolderType> folderTypes = FolderTypeRelationship.getRelatedFolders(type);
    for (ResourceFolderType folderType : folderTypes) {
      if (folderType != ResourceFolderType.VALUES) {

        if (type == ResourceType.ID) {
          // The folder types for ID is not only VALUES but also
          // LAYOUT and MENU. However, unlike resources, they are only defined
          // inline there so for the purposes of isFileBasedResourceType
          // (where the intent is to figure out files that are uniquely identified
          // by a resource's name) this method should return false anyway.
          return false;
        }

        return true;
      }
    }

    return false;
  }

  @Nullable
  public static ResourceFolderType getFolderType(@Nullable final PsiFile file) {
    if (file != null) {
      if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
        return ApplicationManager.getApplication().runReadAction(new Computable<ResourceFolderType>() {
          @Nullable
          @Override
          public ResourceFolderType compute() {
            return getFolderType(file);
          }
        });
      }
      if (!file.isValid()) {
        return getFolderType(file.getVirtualFile());
      }
      PsiDirectory parent = file.getParent();
      if (parent != null) {
        return ResourceFolderType.getFolderType(parent.getName());
      }
    }

    return null;
  }

  @Nullable
  public static ResourceFolderType getFolderType(@Nullable VirtualFile file) {
    if (file != null) {
      VirtualFile parent = file.getParent();
      if (parent != null) {
        return ResourceFolderType.getFolderType(parent.getName());
      }
    }

    return null;
  }

  @Nullable
  public static FolderConfiguration getFolderConfiguration(@Nullable final PsiFile file) {
    if (file != null) {
      if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
        return ApplicationManager.getApplication().runReadAction(new Computable<FolderConfiguration>() {
          @Nullable
          @Override
          public FolderConfiguration compute() {
            return getFolderConfiguration(file);
          }
        });
      }
      if (!file.isValid()) {
        return getFolderConfiguration(file.getVirtualFile());
      }
      PsiDirectory parent = file.getParent();
      if (parent != null) {
        return FolderConfiguration.getConfigForFolder(parent.getName());
      }
    }

    return null;
  }

  @Nullable
  public static FolderConfiguration getFolderConfiguration(@Nullable VirtualFile file) {
    if (file != null) {
      VirtualFile parent = file.getParent();
      if (parent != null) {
        return FolderConfiguration.getConfigForFolder(parent.getName());
      }
    }

    return null;
  }

  /**
   * Returns all resource variations for the given file
   *
   * @param file resource file, which should be an XML file in one of the
   *            various resource folders, e.g. res/layout, res/values-xlarge, etc.
   * @param includeSelf if true, include the file itself in the list,
   *            otherwise exclude it
   * @return a list of all the resource variations
   */
  public static List<VirtualFile> getResourceVariations(@Nullable VirtualFile file, boolean includeSelf) {
    if (file == null) {
      return Collections.emptyList();
    }

    // Compute the set of layout files defining this layout resource
    List<VirtualFile> variations = new ArrayList<VirtualFile>();
    String name = file.getName();
    VirtualFile parent = file.getParent();
    if (parent != null) {
      VirtualFile resFolder = parent.getParent();
      if (resFolder != null) {
        String parentName = parent.getName();
        String prefix = parentName;
        int qualifiers = prefix.indexOf('-');

        if (qualifiers != -1) {
          parentName = prefix.substring(0, qualifiers);
          prefix = prefix.substring(0, qualifiers + 1);
        } else {
          prefix += '-';
        }
        for (VirtualFile resource : resFolder.getChildren()) {
          String n = resource.getName();
          if ((n.startsWith(prefix) || n.equals(parentName))
              && resource.isDirectory()) {
            VirtualFile variation = resource.findChild(name);
            if (variation != null) {
              if (!includeSelf && file.equals(variation)) {
                continue;
              }
              variations.add(variation);
            }
          }
        }
      }
    }

    return variations;
  }

  /**
   * Returns true if views with the given fully qualified class name need to include
   * their package in the layout XML tag
   *
   * @param fqcn the fully qualified class name, such as android.widget.Button
   * @return true if the full package path should be included in the layout XML element
   *         tag
   */
  public static boolean viewNeedsPackage(String fqcn) {
    return !(fqcn.startsWith(ANDROID_WIDGET_PREFIX) || fqcn.startsWith(ANDROID_VIEW_PKG) || fqcn.startsWith(ANDROID_WEBKIT_PKG));
  }

  /**
   * Tries to resolve the given resource value to an actual RGB color. For state lists
   * it will pick the simplest/fallback color.
   *
   * @param resources the resource resolver to use to follow color references
   * @param colorValue the color to resolve
   * @param project the current project
   * @return the corresponding {@link Color} color, or null
   */
  @Nullable
  public static Color resolveColor(@NotNull RenderResources resources, @Nullable ResourceValue colorValue, @NotNull Project project) {
    if (colorValue != null) {
      colorValue = resources.resolveResValue(colorValue);
    }
    if (colorValue == null) {
      return null;
    }

    StateList stateList = resolveStateList(resources, colorValue, project);
    if (stateList != null) {
      List<StateListState> states = stateList.getStates();

      // Getting the last color of the state list, because it's supposed to be the simplest / fallback one
      StateListState state = states.get(states.size() - 1);

      Color stateColor = parseColor(state.getValue());
      if (stateColor == null) {
        stateColor = resolveColor(resources, resources.findResValue(state.getValue(), false), project);
      }
      if (stateColor == null) {
        return null;
      }
      try {
        return makeColorWithAlpha(resources, stateColor, state.getAlpha());
      }
      catch (NumberFormatException e) {
        LOG.error(String.format("The alpha attribute in %s/%s does not resolve to a floating point number", stateList.getDirName(),
                                stateList.getFileName()));
      }
    }

    return parseColor(colorValue.getValue());
  }

  /**
   * Tries to resolve colors from given resource value. When state list is encountered all
   * possibilities are explored.
   */
  @NotNull
  public static List<Color> resolveMultipleColors(@NotNull RenderResources resources, @Nullable ResourceValue value,
                                                  @NotNull Project project) {
    if (value != null) {
      value = resources.resolveResValue(value);
    }
    if (value == null) {
      return Collections.emptyList();
    }

    final List<Color> result = new ArrayList<Color>();

    StateList stateList = resolveStateList(resources, value, project);
    if (stateList != null) {
      for (StateListState state : stateList.getStates()) {
        List<Color> stateColors;
        ResourceValue resolvedStateResource = resources.findResValue(state.getValue(), false);
        if (resolvedStateResource != null) {
          stateColors = resolveMultipleColors(resources, resolvedStateResource, project);
        }
        else {
          Color color = parseColor(state.getValue());
          stateColors = color == null ? Collections.<Color>emptyList() : ImmutableList.of(color);
        }
        for (Color color : stateColors) {
          try {
            result.add(makeColorWithAlpha(resources, color, state.getAlpha()));
          }
          catch (NumberFormatException e) {
            LOG.error(String.format("The alpha attribute in %s/%s does not resolve to a floating point number", stateList.getDirName(),
                                    stateList.getFileName()));
          }
        }
      }
    }
    else {
      Color color = parseColor(value.getValue());
      if (color != null) {
        result.add(color);
      }
    }
    return result;
  }

  @NotNull
  public static String resolveStringValue(@NotNull RenderResources resources, @NotNull String value) {
    ResourceValue resValue = resources.findResValue(value, false);
    if (resValue == null) {
      return value;
    }
    ResourceValue finalValue = resources.resolveResValue(resValue);
    if (finalValue == null || finalValue.getValue() == null) {
      return value;
    }
    return finalValue.getValue();
  }

  @NotNull
  private static Color makeColorWithAlpha(@NotNull RenderResources resources, @NotNull Color color, @Nullable String alphaValue)
    throws NumberFormatException {
    float alpha = 1.0f;
    if (alphaValue != null) {
      alpha = Float.parseFloat(resolveStringValue(resources, alphaValue));
    }

    int combinedAlpha = (int)(color.getAlpha() * alpha);
    if (combinedAlpha < 0) {
      combinedAlpha = 0;
    }
    if (combinedAlpha > 255) {
      combinedAlpha = 255;
    }
    return ColorUtil.toAlpha(color, combinedAlpha);
  }

  /**
   * Returns a {@link StateList} description of the state list value, or null if value is not a state list.
   */
  @Nullable
  public static StateList resolveStateList(@NotNull RenderResources renderResources,
                                           @NotNull ResourceValue value,
                                           @NotNull Project project) {
    if (value.getValue().startsWith(PREFIX_RESOURCE_REF)) {
      final ResourceValue resValue = renderResources.findResValue(value.getValue(), value.isFramework());
      if (resValue != null) {
        return resolveStateList(renderResources, resValue, project);
      }
    }
    else {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(value.getValue());
      if (virtualFile != null) {
        PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, virtualFile);
        if (psiFile instanceof XmlFile) {
          // Parse
          try {
            XmlTag rootTag = ((XmlFile)psiFile).getRootTag();
            if (rootTag != null && TAG_SELECTOR.equals(rootTag.getName())) {
              StateList stateList = new StateList(psiFile.getName(), psiFile.getContainingDirectory().getName());
              for (XmlTag subTag : rootTag.findSubTags(TAG_ITEM)) {
                stateList.addState(createStateListState(subTag, value.isFramework()));
              }
              return stateList;
            }
          }
          catch (IllegalArgumentException e) {
            LOG.error(String.format("%1$s is not a valid state list file", virtualFile.getName()));
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns a {@link StateListState} representing the state in tag.
   */
  @NotNull
  private static StateListState createStateListState(XmlTag tag, boolean isFramework) {
    String stateValue = null;
    String alphaValue = null;
    Map<String, Boolean> stateAttributes = new HashMap<String, Boolean>();
    XmlAttribute[] attributes = tag.getAttributes();
    for (XmlAttribute attr : attributes) {
      String name = attr.getLocalName();
      String value = attr.getValue();
      if (value != null && (SdkConstants.ATTR_COLOR.equals(name) || SdkConstants.ATTR_DRAWABLE.equals(name))) {
        ResourceUrl url = ResourceUrl.parse(value, isFramework);
        stateValue = url != null ? url.toString() : value;
      }
      else if (value != null && "alpha".equals(name)) {
        ResourceUrl url = ResourceUrl.parse(value, isFramework);
        alphaValue = url != null ? url.toString() : value;
      }
      else if (name.startsWith(STATE_NAME_PREFIX)) {
        stateAttributes.put(name, Boolean.valueOf(value));
      }
    }
    if (stateValue == null) {
      throw new IllegalArgumentException("Not a valid item");
    }
    return new StateListState(stateValue, stateAttributes, alphaValue);
  }

  /**
   * Converts the supported color formats (#rgb, #argb, #rrggbb, #aarrggbb to a Color
   * http://developer.android.com/guide/topics/resources/more-resources.html#Color
   */
  @SuppressWarnings("UseJBColor")
  @Nullable
  public static Color parseColor(String s) {
    if (StringUtil.isEmpty(s)) {
      return null;
    }

    if (s.charAt(0) == '#') {
      long longColor;
      try {
        longColor = Long.parseLong(s.substring(1), 16);
      }
      catch (NumberFormatException e) {
        return null;
      }

      if (s.length() == 4 || s.length() == 5) {
        long a = s.length() == 4 ? 0xff : extend((longColor & 0xf000) >> 12);
        long r = extend((longColor & 0xf00) >> 8);
        long g = extend((longColor & 0x0f0) >> 4);
        long b = extend((longColor & 0x00f));
        longColor = (a << 24) | (r << 16) | (g << 8) | b;
        return new Color((int)longColor, true);
      }

      if (s.length() == 7) {
        longColor |= 0x00000000ff000000;
      }
      else if (s.length() != 9) {
        return null;
      }
      return new Color((int)longColor, true);
    }

    return null;
  }

  /**
   * Converts a color to hex-string representation, including alpha channel.
   * If alpha is FF then the output is #RRGGBB with no alpha component.
   */
  public static String colorToString(Color color) {
    long longColor = (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    if (color.getAlpha() != 0xFF) {
      longColor |= (long)color.getAlpha() << 24;
      return String.format("#%08x", longColor);
    }
    else {
      return String.format("#%06x", longColor);
    }
  }

  private static long extend(long nibble) {
    return nibble | nibble << 4;
  }

  /**
   * Tries to resolve the given resource value to an actual drawable bitmap file. For state lists
   * it will pick the simplest/fallback drawable.
   *
   * @param resources the resource resolver to use to follow drawable references
   * @param drawable the drawable to resolve
   * @param project the current project
   * @return the corresponding {@link File}, or null
   */
  @Nullable
  public static File resolveDrawable(@NotNull RenderResources resources, @Nullable ResourceValue drawable, @NotNull Project project) {
    if (drawable != null) {
      drawable = resources.resolveResValue(drawable);
    }
    if (drawable == null) {
      return null;
    }

    String result = drawable.getValue();

    StateList stateList = resolveStateList(resources, drawable, project);
    if (stateList != null) {
      List<StateListState> states = stateList.getStates();
      StateListState state = states.get(states.size() - 1);
      result = state.getValue();
    }

    final File file = new File(result);
    return file.isFile() ? file : null;
  }

  /**
   * Tries to resolve the given resource value to an actual layout file.
   *
   * @param resources the resource resolver to use to follow layout references
   * @param layout the layout to resolve
   * @return the corresponding {@link File}, or null
   */
  @Nullable
  public static File resolveLayout(@NotNull RenderResources resources, @Nullable ResourceValue layout) {
    if (layout != null) {
      layout = resources.resolveResValue(layout);
    }
    if (layout == null) {
      return null;
    }
    String value = layout.getValue();

    int depth = 0;
    while (value != null && depth < MAX_RESOURCE_INDIRECTION) {
      if (value.startsWith(PREFIX_BINDING_EXPR)) {
        value = DbUtil.getBindingExprDefault(value);
        if (value == null) {
          return null;
        }
      }
      if (value.startsWith(PREFIX_RESOURCE_REF)) {
        boolean isFramework = layout.isFramework();
        layout = resources.findResValue(value, isFramework);
        if (layout != null) {
          value = layout.getValue();
        } else {
          break;
        }
      } else {
        File file = new File(value);
        if (file.exists()) {
          return file;
        } else {
          return null;
        }
      }

      depth++;
    }

    return null;
  }

  /**
   * Returns the given resource name, and possibly prepends a project-configured prefix to the name
   * if set on the Gradle module (but only if it does not already start with the prefix).
   *
   * @param module the corresponding module
   * @param name the resource name
   * @return the resource name, possibly with a new prefix at the beginning of it
   */
  @Contract("_, !null -> !null")
  @Nullable
  public static String prependResourcePrefix(@Nullable Module module, @Nullable String name) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        IdeaAndroidProject androidModel = facet.getAndroidModel();
        if (androidModel != null) {
          String resourcePrefix = LintUtils.computeResourcePrefix(androidModel.getAndroidProject());
          if (resourcePrefix != null) {
            if (name != null) {
              return name.startsWith(resourcePrefix) ? name : LintUtils.computeResourceName(resourcePrefix, name);
            } else {
              return resourcePrefix;
            }
          }
        }
      }
    }

    return name;
  }

  /**
   * Stores the information contained in a resource state list.
   */
  public static class StateList {
    private final String myFileName;
    private final String myDirName;
    private final List<StateListState> myStates;

    public StateList(@NotNull String fileName, @NotNull String dirName) {
      myFileName = fileName;
      myDirName = dirName;
      myStates = new ArrayList<StateListState>();
    }

    @NotNull
    public String getFileName() {
      return myFileName;
    }

    @NotNull
    public String getDirName() {
      return myDirName;
    }

    @NotNull
    public ResourceFolderType getType() {
      return ResourceFolderType.getFolderType(myDirName);
    }

    @NotNull
    public List<StateListState> getStates() {
      return myStates;
    }

    public void addState(@NotNull StateListState state) {
      myStates.add(state);
    }
  }

  /**
   * Stores information about a particular state of a resource state list.
   */
  public static class StateListState {
    private String myValue;
    private String myAlpha;
    private final Map<String, Boolean> myAttributes;

    public StateListState(@NotNull String value, @NotNull Map<String, Boolean> attributes, @Nullable String alpha) {
      myValue = value;
      myAttributes = attributes;
      myAlpha = alpha;
    }

    public void setValue(@NotNull String value) {
      myValue = value;
    }

    public void setAlpha(String alpha) {
      myAlpha = alpha;
    }

    @NotNull
    public String getValue() {
      return myValue;
    }

    @Nullable
    public String getAlpha() {
      return myAlpha;
    }

    @NotNull
    public Map<String, Boolean> getAttributes() {
      return myAttributes;
    }
  }
}
