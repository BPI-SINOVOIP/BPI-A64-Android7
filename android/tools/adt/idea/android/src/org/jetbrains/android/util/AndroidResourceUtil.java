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

package org.jetbrains.android.util;

import com.android.SdkConstants;
import com.android.ide.common.res2.ValueXmlHelper;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.ResourceHelper;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModulePackageIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.AndroidFileTemplateProvider;
import org.jetbrains.android.actions.CreateTypedResourceFileAction;
import org.jetbrains.android.augment.AndroidPsiElementFinder;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.Item;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.dom.resources.ScalarResourceElement;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static com.android.resources.ResourceType.ATTR;
import static com.android.resources.ResourceType.STYLEABLE;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.util.AndroidResourceUtil");

  public static final Set<ResourceType> VALUE_RESOURCE_TYPES = EnumSet.of(ResourceType.DRAWABLE, ResourceType.COLOR, ResourceType.DIMEN,
                                                                          ResourceType.STRING, ResourceType.STYLE, ResourceType.ARRAY,
                                                                          ResourceType.PLURALS, ResourceType.ID, ResourceType.BOOL,
                                                                          ResourceType.INTEGER, ResourceType.FRACTION,
                                                                          // For aliases only
                                                                          ResourceType.LAYOUT);

  public static final Set<ResourceType> ALL_VALUE_RESOURCE_TYPES = EnumSet.noneOf(ResourceType.class);

  public static final Set<ResourceType> REFERRABLE_RESOURCE_TYPES = EnumSet.noneOf(ResourceType.class);
  public static final Set<ResourceType> XML_FILE_RESOURCE_TYPES = EnumSet.of(ResourceType.ANIM, ResourceType.ANIMATOR,
                                                                             ResourceType.INTERPOLATOR, ResourceType.LAYOUT,
                                                                             ResourceType.MENU, ResourceType.XML, ResourceType.COLOR,
                                                                             ResourceType.DRAWABLE);
  static final String ROOT_TAG_PROPERTY = "ROOT_TAG";
  static final String LAYOUT_WIDTH_PROPERTY = "LAYOUT_WIDTH";
  static final String LAYOUT_HEIGHT_PROPERTY = "LAYOUT_HEIGHT";

  /**
   * Comparator which orders {@link com.intellij.psi.PsiElement} items into a priority order most suitable for presentation
   * to the user; for example, it prefers base resource folders such as {@code values/} over resource
   * folders such as {@code values-en-rUS}
   */
  public static final Comparator<PsiElement> RESOURCE_ELEMENT_COMPARATOR = new Comparator<PsiElement>() {
    @Override
    public int compare(PsiElement e1, PsiElement e2) {
      if (e1 instanceof LazyValueResourceElementWrapper && e2 instanceof LazyValueResourceElementWrapper) {
        return ((LazyValueResourceElementWrapper)e1).compareTo((LazyValueResourceElementWrapper)e2);
      }

      PsiFile file1 = e1.getContainingFile();
      PsiFile file2 = e2.getContainingFile();
      int delta = compareResourceFiles(file1, file2);
      if (delta != 0) {
        return delta;
      }
      return e1.getTextOffset() - e2.getTextOffset();
    }
  };

  private AndroidResourceUtil() {
  }

  @NotNull
  public static String normalizeXmlResourceValue(@NotNull String value) {
    return ValueXmlHelper.escapeResourceString(value, false);
  }

  static {
    REFERRABLE_RESOURCE_TYPES.addAll(Arrays.asList(ResourceType.values()));
    REFERRABLE_RESOURCE_TYPES.remove(ATTR);
    REFERRABLE_RESOURCE_TYPES.remove(STYLEABLE);

    ALL_VALUE_RESOURCE_TYPES.addAll(VALUE_RESOURCE_TYPES);
    ALL_VALUE_RESOURCE_TYPES.add(ATTR);
    ALL_VALUE_RESOURCE_TYPES.add(STYLEABLE);
  }

  @NotNull
  public static PsiField[] findResourceFields(@NotNull AndroidFacet facet,
                                              @NotNull String resClassName,
                                              @NotNull String resourceName,
                                              boolean onlyInOwnPackages) {
    resourceName = getRJavaFieldName(resourceName);
    final List<PsiJavaFile> rClassFiles = findRJavaFiles(facet, onlyInOwnPackages);
    final List<PsiField> result = new ArrayList<PsiField>();

    for (PsiJavaFile rClassFile : rClassFiles) {
      if (rClassFile == null) {
        continue;
      }
      final PsiClass rClass = findClass(rClassFile.getClasses(), AndroidUtils.R_CLASS_NAME);
      findResourceFieldsFromClass(rClass, resClassName, Collections.singleton(resourceName), result);
    }
    PsiClass inMemoryRClass = facet.getLightRClass();
    if (inMemoryRClass != null) {
      findResourceFieldsFromClass(inMemoryRClass, resClassName, Collections.singleton(resourceName), result);
    }

    return result.toArray(new PsiField[result.size()]);
  }

  /**
   * Like {@link #findResourceFields(org.jetbrains.android.facet.AndroidFacet, String, String, boolean)} but
   * can match than more than a single field name
   */
  @NotNull
  public static PsiField[] findResourceFields(@NotNull AndroidFacet facet,
                                              @NotNull String resClassName,
                                              @NotNull Collection<String> resourceNames,
                                              boolean onlyInOwnPackages) {
    final List<PsiJavaFile> rClassFiles = findRJavaFiles(facet, onlyInOwnPackages);
    final List<PsiField> result = new ArrayList<PsiField>();

    for (PsiJavaFile rClassFile : rClassFiles) {
      if (rClassFile == null) {
        continue;
      }
      findResourceFieldsFromClass(findClass(rClassFile.getClasses(), AndroidUtils.R_CLASS_NAME),
          resClassName, resourceNames, result);
    }
    PsiClass inMemoryRClass = facet.getLightRClass();
    if (inMemoryRClass != null) {
      findResourceFieldsFromClass(inMemoryRClass, resClassName, resourceNames, result);
    }

    return result.toArray(new PsiField[result.size()]);
  }

  private static void findResourceFieldsFromClass(@Nullable PsiClass rClass,
      @NotNull String resClassName, @NotNull Collection<String> resourceNames,
      @NotNull List<PsiField> result) {

    if (rClass != null) {
      final PsiClass resourceTypeClass = findClass(rClass.getInnerClasses(), resClassName);

      if (resourceTypeClass != null) {
        for (String resourceName : resourceNames) {
          String fieldName = getRJavaFieldName(resourceName);
          final PsiField field = resourceTypeClass.findFieldByName(fieldName, false);

          if (field != null) {
            result.add(field);
          }
        }
      }
    }
  }

  @NotNull
  private static List<PsiJavaFile> findRJavaFiles(@NotNull AndroidFacet facet, boolean onlyInOwnPackages) {
    final Module module = facet.getModule();
    final Project project = module.getProject();
    final Manifest manifest = facet.getManifest();

    if (manifest == null) {
      return Collections.emptyList();
    }
    final Set<PsiDirectory> dirs = new HashSet<PsiDirectory>();
    collectDirsForPackage(module, project, null, dirs, new HashSet<Module>(), onlyInOwnPackages);

    final List<PsiJavaFile> rJavaFiles = new ArrayList<PsiJavaFile>();

    for (PsiDirectory dir : dirs) {
      final VirtualFile file = dir.getVirtualFile().findChild(AndroidCommonUtils.R_JAVA_FILENAME);

      if (file != null) {
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

        if (psiFile instanceof PsiJavaFile) {
          rJavaFiles.add((PsiJavaFile)psiFile);
        }
      }
    }
    return rJavaFiles;
  }

  private static void collectDirsForPackage(Module module,
                                            final Project project,
                                            @Nullable String packageName,
                                            final Set<PsiDirectory> dirs,
                                            Set<Module> visitedModules,
                                            boolean onlyInOwnPackages) {
    if (!visitedModules.add(module)) {
      return;
    }

    if (packageName != null) {
      ModulePackageIndex.getInstance(module).getDirsByPackageName(packageName, false).forEach(new Processor<VirtualFile>() {
        @Override
        public boolean process(final VirtualFile directory) {
          final PsiDirectory psiDir = PsiManager.getInstance(project).findDirectory(directory);

          if (psiDir != null) {
            dirs.add(psiDir);
          }
          return true;
        }
      });
    }
    final AndroidFacet ownFacet = AndroidFacet.getInstance(module);
    String ownPackageName = null;

    if (ownFacet != null) {
      final Manifest ownManifest = ownFacet.getManifest();
      ownPackageName = ownManifest != null ? ownManifest.getPackage().getValue() : null;

      if (ownPackageName != null && !ownPackageName.equals(packageName)) {
        ModulePackageIndex.getInstance(module).getDirsByPackageName(ownPackageName, false).forEach(new Processor<VirtualFile>() {
          @Override
          public boolean process(final VirtualFile directory) {
            final PsiDirectory psiDir = PsiManager.getInstance(project).findDirectory(directory);

            if (psiDir != null) {
              dirs.add(psiDir);
            }
            return true;
          }
        });
      }
    }

    for (Module otherModule : ModuleManager.getInstance(project).getModules()) {
      if (ModuleRootManager.getInstance(otherModule).isDependsOn(module)) {
        collectDirsForPackage(otherModule, project, packageName != null || onlyInOwnPackages ? packageName : ownPackageName, dirs,
                              visitedModules, onlyInOwnPackages);
      }
    }
  }

  @Nullable
  private static PsiClass findClass(@NotNull PsiClass[] classes, @NotNull String name) {
    for (PsiClass c : classes) {
      if (name.equals(c.getName())) {
        return c;
      }
    }
    return null;
  }

  @NotNull
  public static PsiField[] findResourceFieldsForFileResource(@NotNull PsiFile file, boolean onlyInOwnPackages) {
    final AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return PsiField.EMPTY_ARRAY;
    }

    final String resourceType = facet.getLocalResourceManager().getFileResourceType(file);
    if (resourceType == null) {
      return PsiField.EMPTY_ARRAY;
    }

    final String resourceName = AndroidCommonUtils.getResourceName(resourceType, file.getName());
    return findResourceFields(facet, resourceType, resourceName, onlyInOwnPackages);
  }

  @NotNull
  public static PsiField[] findResourceFieldsForValueResource(XmlTag tag, boolean onlyInOwnPackages) {
    final AndroidFacet facet = AndroidFacet.getInstance(tag);
    if (facet == null) {
      return PsiField.EMPTY_ARRAY;
    }

    ResourceFolderType fileResType = ResourceHelper.getFolderType(tag.getContainingFile());
    final String resourceType = fileResType == ResourceFolderType.VALUES
                                ? getResourceTypeByValueResourceTag(tag)
                                : null;
    if (resourceType == null) {
      return PsiField.EMPTY_ARRAY;
    }

    String name = tag.getAttributeValue(SdkConstants.ATTR_NAME);
    if (name == null) {
      return PsiField.EMPTY_ARRAY;
    }

    return findResourceFields(facet, resourceType, name, onlyInOwnPackages);
  }

  @NotNull
  public static PsiField[] findStyleableAttributeFields(XmlTag tag, boolean onlyInOwnPackages) {
    String tagName = tag.getName();
    if (SdkConstants.TAG_DECLARE_STYLEABLE.equals(tagName)) {
      String styleableName = tag.getAttributeValue(SdkConstants.ATTR_NAME);
      if (styleableName == null) {
        return PsiField.EMPTY_ARRAY;
      }
      AndroidFacet facet = AndroidFacet.getInstance(tag);
      if (facet == null) {
        return PsiField.EMPTY_ARRAY;
      }
      Set<String> names = Sets.newHashSet();
      for (XmlTag attr : tag.getSubTags()) {
        if (SdkConstants.TAG_ATTR.equals(attr.getName())) {
          String attrName = attr.getAttributeValue(SdkConstants.ATTR_NAME);
          if (attrName != null) {
            names.add(styleableName + '_' + attrName);
          }
        }
      }
      if (!names.isEmpty()) {
        return findResourceFields(facet, STYLEABLE.getName(), names, onlyInOwnPackages);
      }
    } else if (SdkConstants.TAG_ATTR.equals(tagName)) {
      XmlTag parentTag = tag.getParentTag();
      if (parentTag != null && SdkConstants.TAG_DECLARE_STYLEABLE.equals(parentTag.getName())) {
        String styleName = parentTag.getAttributeValue(SdkConstants.ATTR_NAME);
        String attributeName = tag.getAttributeValue(SdkConstants.ATTR_NAME);
        AndroidFacet facet = AndroidFacet.getInstance(tag);
        if (facet != null && styleName != null && attributeName != null) {
          return findResourceFields(facet, STYLEABLE.getName(), styleName + '_' + attributeName, onlyInOwnPackages);
        }
      }
    }

    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public static String getRJavaFieldName(@NotNull String resourceName) {
    if (resourceName.indexOf('.') == -1) {
      return resourceName;
    }
    final String[] identifiers = resourceName.split("\\.");
    final StringBuilder result = new StringBuilder(resourceName.length());

    for (int i = 0, n = identifiers.length; i < n; i++) {
      result.append(identifiers[i]);
      if (i < n - 1) {
        result.append('_');
      }
    }
    return result.toString();
  }

  public static boolean isCorrectAndroidResourceName(@NotNull String resourceName) {
    // TODO: No, we need to check per resource folder type here. There is a validator for this!
    if (resourceName.length() == 0) {
      return false;
    }
    if (resourceName.startsWith(".") || resourceName.endsWith(".")) {
      return false;
    }
    final String[] identifiers = resourceName.split("\\.");

    for (String identifier : identifiers) {
      if (!StringUtil.isJavaIdentifier(identifier)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static String getResourceTypeByValueResourceTag(@NotNull XmlTag tag) {
    String resClassName = tag.getName();
    resClassName = resClassName.equals("item")
                   ? tag.getAttributeValue("type", null)
                   : AndroidCommonUtils.getResourceTypeByTagName(resClassName);
    if (resClassName != null) {
      final String resourceName = tag.getAttributeValue("name");
      return resourceName != null ? resClassName : null;
    }
    return null;
  }

  @Nullable
  public static ResourceType getResourceForResourceTag(@NotNull XmlTag tag) {
    String typeName = getResourceTypeByValueResourceTag(tag);
    if (typeName != null) {
      return ResourceType.getEnum(typeName);
    }

    return null;
  }

  @Nullable
  public static String getResourceClassName(@NotNull PsiField field) {
    final PsiClass resourceClass = field.getContainingClass();

    if (resourceClass != null) {
      final PsiClass parentClass = resourceClass.getContainingClass();

      if (parentClass != null &&
          AndroidUtils.R_CLASS_NAME.equals(parentClass.getName()) &&
          parentClass.getContainingClass() == null) {
        return resourceClass.getName();
      }
    }
    return null;
  }

  // result contains XmlAttributeValue or PsiFile
  @NotNull
  public static List<PsiElement> findResourcesByField(@NotNull PsiField field) {
    final AndroidFacet facet = AndroidFacet.getInstance(field);
    return facet != null
           ? facet.getLocalResourceManager().findResourcesByField(field)
           : Collections.<PsiElement>emptyList();
  }

  public static boolean isResourceField(@NotNull PsiField field) {
    PsiClass c = field.getContainingClass();
    if (c == null) return false;
    c = c.getContainingClass();
    if (c != null && AndroidUtils.R_CLASS_NAME.equals(c.getName())) {
      AndroidFacet facet = AndroidFacet.getInstance(field);
      if (facet != null) {
        PsiFile file = c.getContainingFile();
        if (file != null && isRJavaFile(facet, file)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public static PsiField[] findIdFields(@NotNull XmlAttributeValue value) {
    if (value.getParent() instanceof XmlAttribute) {
      return findIdFields((XmlAttribute)value.getParent());
    }
    return PsiField.EMPTY_ARRAY;
  }

  public static boolean isIdDeclaration(@Nullable String attrValue) {
    return attrValue != null && attrValue.startsWith(SdkConstants.NEW_ID_PREFIX);
  }

  public static boolean isIdReference(@Nullable String attrValue) {
    return attrValue != null && attrValue.startsWith(SdkConstants.ID_PREFIX);
  }

  public static boolean isIdDeclaration(@NotNull XmlAttributeValue value) {
    return isIdDeclaration(value.getValue());
  }

  @NotNull
  public static PsiField[] findIdFields(@NotNull XmlAttribute attribute) {
    final XmlAttributeValue value = attribute.getValueElement();

    if (value != null && isIdDeclaration(value)) {
      final String id = getResourceNameByReferenceText(attribute.getValue());

      if (id != null) {
        final AndroidFacet facet = AndroidFacet.getInstance(attribute);

        if (facet != null) {
          return findResourceFields(facet, ResourceType.ID.getName(), id, false);
        }
      }
    }
    return PsiField.EMPTY_ARRAY;
  }

  @Nullable
  public static String getResourceNameByReferenceText(@NotNull String text) {
    int i = text.indexOf('/');
    if (i < text.length() - 1) {
      return text.substring(i + 1, text.length());
    }
    return null;
  }

  @NotNull
  public static ResourceElement addValueResource(@NotNull final ResourceType resType, @NotNull final Resources resources,
                                                 @Nullable final String value) {
    switch (resType) {
      case STRING:
        return resources.addString();
      case PLURALS:
        return resources.addPlurals();
      case DIMEN:
        if (value != null && value.trim().endsWith("%")) {
          // Deals with dimension values in the form of percentages, e.g. "65%"
          final Item item = resources.addItem();
          item.getType().setStringValue(ResourceType.DIMEN.getName());
          return item;
        }
        if (value != null && value.indexOf('.') > 0) {
          // Deals with dimension values in the form of floating-point numbers, e.g. "0.24"
          final Item item = resources.addItem();
          item.getType().setStringValue(ResourceType.DIMEN.getName());
          item.getFormat().setStringValue("float");
          return item;
        }
        return resources.addDimen();
      case COLOR:
        return resources.addColor();
      case DRAWABLE:
        return resources.addDrawable();
      case STYLE:
        return resources.addStyle();
      case ARRAY:
        // todo: choose among string-array, integer-array and array
        return resources.addStringArray();
      case INTEGER:
        return resources.addInteger();
      case FRACTION:
        return resources.addFraction();
      case BOOL:
        return resources.addBool();
      case ID:
        final Item item = resources.addItem();
        item.getType().setValue(ResourceType.ID.getName());
        return item;
      case ATTR:
        return resources.addAttr();
      case STYLEABLE:
        return resources.addDeclareStyleable();
      default:
        throw new IllegalArgumentException("Incorrect resource type");
    }
  }

  @NotNull
  public static List<VirtualFile> getResourceSubdirs(@Nullable String resourceType, @NotNull VirtualFile[] resourceDirs) {
    if (resourceType != null && ResourceFolderType.getTypeByName(resourceType) == null) {
      return Collections.emptyList();
    }
    final List<VirtualFile> dirs = new ArrayList<VirtualFile>();

    for (VirtualFile resourcesDir : resourceDirs) {
      if (resourcesDir == null) {
        return dirs;
      }
      if (resourceType == null) {
        ContainerUtil.addAll(dirs, resourcesDir.getChildren());
      }
      else {
        for (VirtualFile child : resourcesDir.getChildren()) {
          String type = AndroidCommonUtils.getResourceTypeByDirName(child.getName());
          if (resourceType.equals(type)) dirs.add(child);
        }
      }
    }
    return dirs;
  }

  @Nullable
  public static String getDefaultResourceFileName(@NotNull ResourceType type) {
    if (ResourceType.PLURALS == type || ResourceType.STRING == type) {
      return "strings.xml";
    }
    if (VALUE_RESOURCE_TYPES.contains(type)) {

      if (type == ResourceType.LAYOUT
          // Lots of unit tests assume drawable aliases are written in "drawables.xml" but going
          // forward lets combine both layouts and drawables in refs.xml as is done in the templates:
          || type == ResourceType.DRAWABLE && !ApplicationManager.getApplication().isUnitTestMode()) {
        return "refs.xml";
      }

      return type.getName() + "s.xml";
    }
    if (ATTR == type ||
        STYLEABLE == type) {
      return "attrs.xml";
    }
    return null;
  }

  @NotNull
  public static List<ResourceElement> getValueResourcesFromElement(@NotNull String resourceType, @NotNull Resources resources) {
    final List<ResourceElement> result = new ArrayList<ResourceElement>();

    if (resourceType.equals(ResourceType.STRING.getName())) {
      result.addAll(resources.getStrings());
    }
    else if (resourceType.equals(ResourceType.PLURALS.getName())) {
      result.addAll(resources.getPluralss());
    }
    else if (resourceType.equals(ResourceType.DRAWABLE.getName())) {
      result.addAll(resources.getDrawables());
    }
    else if (resourceType.equals(ResourceType.COLOR.getName())) {
      result.addAll(resources.getColors());
    }
    else if (resourceType.equals(ResourceType.DIMEN.getName())) {
      result.addAll(resources.getDimens());
    }
    else if (resourceType.equals(ResourceType.STYLE.getName())) {
      result.addAll(resources.getStyles());
    }
    else if (resourceType.equals(ResourceType.ARRAY.getName())) {
      result.addAll(resources.getStringArrays());
      result.addAll(resources.getIntegerArrays());
      result.addAll(resources.getArrays());
    }
    else if (resourceType.equals(ResourceType.INTEGER.getName())) {
      result.addAll(resources.getIntegers());
    }
    else if (resourceType.equals(ResourceType.FRACTION.getName())) {
      result.addAll(resources.getFractions());
    }
    else if (resourceType.equals(ResourceType.BOOL.getName())) {
      result.addAll(resources.getBools());
    }
    for (Item item : resources.getItems()) {
      String type = item.getType().getValue();
      if (resourceType.equals(type)) {
        result.add(item);
      }
    }
    return result;
  }

  public static boolean isInResourceSubdirectory(@NotNull PsiFile file, @Nullable String resourceType) {
    file = file.getOriginalFile();
    PsiDirectory dir = file.getContainingDirectory();
    if (dir == null) return false;
    return isResourceSubdirectory(dir, resourceType);
  }

  public static boolean isResourceSubdirectory(@NotNull PsiDirectory directory, @Nullable String resourceType) {
    PsiDirectory dir = directory;

    final String dirName = dir.getName();
    if (resourceType != null && !dirName.equals(resourceType) && !dirName.startsWith(resourceType + '-')) {
      return false;
    }
    dir = dir.getParent();

    if (dir == null) {
      return false;
    }
    if ("default".equals(dir.getName())) {
      dir = dir.getParentDirectory();
    }
    return dir != null && isResourceDirectory(dir);
  }

  public static boolean isLocalResourceDirectory(@NotNull VirtualFile dir, @NotNull Project project) {
    final Module module = ModuleUtil.findModuleForFile(dir, project);

    if (module != null) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      return facet != null && facet.getLocalResourceManager().isResourceDir(dir);
    }
    return false;
  }

  public static boolean isResourceDirectory(@NotNull PsiDirectory directory) {
    PsiDirectory dir = directory;
    // check facet settings
    VirtualFile vf = dir.getVirtualFile();

    if (isLocalResourceDirectory(vf, dir.getProject())) {
      return true;
    }

    // method can be invoked for system resource dir, so we should check it
    if (!SdkConstants.FD_RES.equals(dir.getName())) return false;
    dir = dir.getParent();
    if (dir != null) {
      if (dir.findFile(SdkConstants.FN_ANDROID_MANIFEST_XML) != null) {
        return true;
      }
      dir = dir.getParent();
      if (dir != null) {
        if (containsAndroidJar(dir)) return true;
        dir = dir.getParent();
        if (dir != null) {
          return containsAndroidJar(dir);
        }
      }
    }
    return false;
  }

  private static boolean containsAndroidJar(@NotNull PsiDirectory psiDirectory) {
    return psiDirectory.findFile(SdkConstants.FN_FRAMEWORK_LIBRARY) != null;
  }

  public static boolean isRJavaFile(@NotNull AndroidFacet facet, @NotNull PsiFile file) {
    if (file.getName().equals(AndroidCommonUtils.R_JAVA_FILENAME) && file instanceof PsiJavaFile) {
      final PsiJavaFile javaFile = (PsiJavaFile)file;

      final Manifest manifest = facet.getManifest();
      if (manifest == null) {
        return false;
      }

      final String manifestPackage = manifest.getPackage().getValue();
      if (manifestPackage != null && javaFile.getPackageName().equals(manifestPackage)) {
        return true;
      }

      for (String aPackage : AndroidUtils.getDepLibsPackages(facet.getModule())) {
        if (javaFile.getPackageName().equals(aPackage)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isManifestJavaFile(@NotNull AndroidFacet facet, @NotNull PsiFile file) {
    if (file.getName().equals(AndroidCommonUtils.MANIFEST_JAVA_FILE_NAME) && file instanceof PsiJavaFile) {
      final Manifest manifest = facet.getManifest();
      final PsiJavaFile javaFile = (PsiJavaFile)file;
      return manifest != null && javaFile.getPackageName().equals(manifest.getPackage().getValue());
    }
    return false;
  }

  public static List<String> getNames(@NotNull Collection<ResourceType> resourceTypes) {
    if (resourceTypes.size() == 0) {
      return Collections.emptyList();
    }
    final List<String> result = new ArrayList<String>();

    for (ResourceType type : resourceTypes) {
      result.add(type.getName());
    }
    return result;
  }

  @NotNull
  public static String[] getNamesArray(@NotNull Collection<ResourceType> resourceTypes) {
    final List<String> names = getNames(resourceTypes);
    return ArrayUtil.toStringArray(names);
  }

  public static boolean createValueResource(@NotNull Module module,
                                            @NotNull String resourceName,
                                            @Nullable String resourceValue,
                                            @NotNull final ResourceType resourceType,
                                            @NotNull String fileName,
                                            @NotNull List<String> dirNames,
                                            @NotNull Processor<ResourceElement> afterAddedProcessor) {
    final Project project = module.getProject();
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;

    try {
      return addValueResource(facet, resourceName, resourceType, fileName, dirNames, resourceValue, afterAddedProcessor);
    }
    catch (Exception e) {
      final String message = CreateElementActionBase.filterMessage(e.getMessage());

      if (message == null || message.length() == 0) {
        LOG.error(e);
      }
      else {
        LOG.info(e);
        AndroidUtils.reportError(project, message);
      }
      return false;
    }
  }

  public static boolean createValueResource(@NotNull Module module,
                                            @NotNull String resourceName,
                                            @NotNull final ResourceType resourceType,
                                            @NotNull String fileName,
                                            @NotNull List<String> dirNames,
                                            @NotNull final String value) {
    return createValueResource(module, resourceName, resourceType, fileName, dirNames, value, null);
  }

  public static boolean createValueResource(@NotNull Module module,
                                            @NotNull String resourceName,
                                            @NotNull final ResourceType resourceType,
                                            @NotNull String fileName,
                                            @NotNull List<String> dirNames,
                                            @NotNull final String value,
                                            @Nullable final List<ResourceElement> outTags) {
    return createValueResource(module, resourceName, value, resourceType, fileName, dirNames, new Processor<ResourceElement>() {
      @Override
      public boolean process(ResourceElement element) {
        if (value.length() > 0) {
          final String s = resourceType == ResourceType.STRING ? normalizeXmlResourceValue(value) : value;
          element.setStringValue(s);
        }
        else if (resourceType == STYLEABLE || resourceType == ResourceType.STYLE) {
          element.setStringValue("value");
          element.getXmlTag().getValue().setText("");
        }

        if (outTags != null) {
          outTags.add(element);
        }
        return true;
      }
    });
  }

  private static boolean addValueResource(@NotNull AndroidFacet facet,
                                          @NotNull final String resourceName,
                                          @NotNull final ResourceType resourceType,
                                          @NotNull String fileName,
                                          @NotNull List<String> dirNames,
                                          @Nullable final String resourceValue,
                                          @NotNull final Processor<ResourceElement> afterAddedProcessor) throws Exception {
    if (dirNames.size() == 0) {
      return false;
    }
    final VirtualFile[] resFiles = new VirtualFile[dirNames.size()];

    for (int i = 0, n = dirNames.size(); i < n; i++) {
      final VirtualFile resFile = findOrCreateResourceFile(facet, fileName, dirNames.get(i));
      if (resFile == null) {
        return false;
      }
      resFiles[i] = resFile;
    }

    if (!ReadonlyStatusHandler.ensureFilesWritable(facet.getModule().getProject(), resFiles)) {
      return false;
    }
    final Resources[] resourcesElements = new Resources[resFiles.length];

    for (int i = 0; i < resFiles.length; i++) {
      final Resources resources = AndroidUtils.loadDomElement(facet.getModule(), resFiles[i], Resources.class);
      if (resources == null) {
        AndroidUtils.reportError(facet.getModule().getProject(), AndroidBundle.message("not.resource.file.error", fileName));
        return false;
      }
      resourcesElements[i] = resources;
    }

    List<PsiFile> psiFiles = Lists.newArrayListWithExpectedSize(resFiles.length);
    Project project = facet.getModule().getProject();
    PsiManager manager = PsiManager.getInstance(project);
    for (VirtualFile file : resFiles) {
      PsiFile psiFile = manager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }
    PsiFile[] files = psiFiles.toArray(new PsiFile[psiFiles.size()]);
    WriteCommandAction<Void> action = new WriteCommandAction<Void>(project, "Add Resource", files) {
      @Override
      protected void run(@NotNull Result<Void> result) {
        for (Resources resources : resourcesElements) {
          final ResourceElement element = addValueResource(resourceType, resources, resourceValue);
          element.getName().setValue(resourceName);
          afterAddedProcessor.process(element);
        }
      }
    };
    action.execute();

    return true;
  }

  public static boolean changeColorResource(@NotNull AndroidFacet facet,
                                            @NotNull final String colorName,
                                            @NotNull final String newValue,
                                            @NotNull String fileName,
                                            @NotNull List<String> dirNames) {
    if (dirNames.isEmpty()) {
      return false;
    }
    ArrayList<VirtualFile> resFiles = Lists.newArrayListWithExpectedSize(dirNames.size());

    for (String dirName : dirNames) {
      final VirtualFile resFile = findResourceFile(facet, fileName, dirName);
      if (resFile != null) {
        resFiles.add(resFile);
      }
    }

    if (!ensureFilesWritable(facet.getModule().getProject(), resFiles)) {
      return false;
    }
    final Resources[] resourcesElements = new Resources[resFiles.size()];

    for (int i = 0; i < resFiles.size(); i++) {
      final Resources resources = AndroidUtils.loadDomElement(facet.getModule(), resFiles.get(i), Resources.class);
      if (resources == null) {
        AndroidUtils.reportError(facet.getModule().getProject(), AndroidBundle.message("not.resource.file.error", fileName));
        return false;
      }
      resourcesElements[i] = resources;
    }

    List<PsiFile> psiFiles = Lists.newArrayListWithExpectedSize(resFiles.size());
    Project project = facet.getModule().getProject();
    PsiManager manager = PsiManager.getInstance(project);
    for (VirtualFile file : resFiles) {
      PsiFile psiFile = manager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }
    PsiFile[] files = psiFiles.toArray(new PsiFile[psiFiles.size()]);
    WriteCommandAction<Boolean> action = new WriteCommandAction<Boolean>(project, "Change Color Resource", files) {
      @Override
      protected void run(@NotNull Result<Boolean> result) throws Throwable {
        result.setResult(false);
        for (Resources resources : resourcesElements) {
          for (ScalarResourceElement colorElement : resources.getColors()) {
            String colorValue = colorElement.getName().getStringValue();
            if (StringUtil.equalsIgnoreCase(colorValue, colorName)) {
              colorElement.setStringValue(newValue);
              result.setResult(true);
            }
          }
        }
      }
    };

    return action.execute().getResultObject();
  }

  @Nullable
  private static VirtualFile findResourceFile(@NotNull AndroidFacet facet,
                                              @NotNull final String fileName,
                                              @NotNull String dirName) {
    final VirtualFile resDir = facet.getPrimaryResourceDir();

    if (resDir == null) {
      return null;
    }
    VirtualFile dir = resDir.findChild(dirName);

    if (dir == null) {
      return null;
    }
    return dir.findChild(fileName);
  }

  @Nullable
  private static VirtualFile findOrCreateResourceFile(@NotNull AndroidFacet facet,
                                                      @NotNull final String fileName,
                                                      @NotNull String dirName) throws Exception {
    final Module module = facet.getModule();
    final Project project = module.getProject();
    final VirtualFile resDir = facet.getPrimaryResourceDir();

    if (resDir == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("check.resource.dir.error", module.getName()));
      return null;
    }
    final VirtualFile dir = AndroidUtils.createChildDirectoryIfNotExist(project, resDir, dirName);
    final String dirPath = FileUtil.toSystemDependentName(resDir.getPath() + '/' + dirName);

    if (dir == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("android.cannot.create.dir.error", dirPath));
      return null;
    }

    final VirtualFile file = dir.findChild(fileName);
    if (file != null) {
      return file;
    }

    AndroidFileTemplateProvider
      .createFromTemplate(project, dir, AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE, fileName);
    final VirtualFile result = dir.findChild(fileName);
    if (result == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("android.cannot.create.file.error", dirPath + File.separatorChar + fileName));
    }
    return result;
  }

  @Nullable
  public static MyReferredResourceFieldInfo getReferredResourceOrManifestField(@NotNull AndroidFacet facet,
                                                                               @NotNull PsiReferenceExpression exp,
                                                                               boolean localOnly) {
    return getReferredResourceOrManifestField(facet, exp, null, localOnly);
  }

  @Nullable
  public static MyReferredResourceFieldInfo getReferredResourceOrManifestField(@NotNull AndroidFacet facet,
                                                                               @NotNull PsiReferenceExpression exp,
                                                                               @Nullable String className,
                                                                               boolean localOnly) {
    final String resFieldName = exp.getReferenceName();
    if (resFieldName == null || resFieldName.length() == 0) {
      return null;
    }

    PsiExpression qExp = exp.getQualifierExpression();
    if (!(qExp instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression resClassReference = (PsiReferenceExpression)qExp;

    final String resClassName = resClassReference.getReferenceName();
    if (resClassName == null || resClassName.length() == 0 ||
        className != null && !className.equals(resClassName)) {
      return null;
    }

    qExp = resClassReference.getQualifierExpression();
    if (!(qExp instanceof PsiReferenceExpression)) {
      return null;
    }

    final PsiElement resolvedElement = ((PsiReferenceExpression)qExp).resolve();
    if (!(resolvedElement instanceof PsiClass)) {
      return null;
    }
    final PsiClass aClass = (PsiClass)resolvedElement;
    final String classShortName = aClass.getName();
    final boolean fromManifest = AndroidUtils.MANIFEST_CLASS_NAME.equals(classShortName);

    if (!fromManifest && !AndroidUtils.R_CLASS_NAME.equals(classShortName)) {
      return null;
    }
    if (!localOnly) {
      final String qName = aClass.getQualifiedName();

      if (SdkConstants.CLASS_R.equals(qName) || AndroidPsiElementFinder.INTERNAL_R_CLASS_QNAME.equals(qName)) {
        return new MyReferredResourceFieldInfo(resClassName, resFieldName, true, false);
      }
    }
    final PsiFile containingFile = resolvedElement.getContainingFile();
    if (containingFile == null) {
      return null;
    }
    if (fromManifest ? !isManifestJavaFile(facet, containingFile) : !isRJavaFile(facet, containingFile)) {
      return null;
    }
    return new MyReferredResourceFieldInfo(resClassName, resFieldName, false, fromManifest);
  }

  /**
   * Utility method suitable for Comparator implementations which order resource files,
   * which will sort files by base folder followed by alphabetical configurations. Prioritizes
   * XML files higher than non-XML files.
   */
  public static int compareResourceFiles(@Nullable VirtualFile file1, @Nullable VirtualFile file2) {
    if (file1 != null && file2 != null && file1 != file2) {
      boolean xml1 = file1.getFileType() == StdFileTypes.XML;
      boolean xml2 = file2.getFileType() == StdFileTypes.XML;
      if (xml1 != xml2) {
        return xml1 ? -1 : 1;
      }
      VirtualFile parent1 = file1.getParent();
      VirtualFile parent2 = file2.getParent();
      if (parent1 != null && parent2 != null && parent1 != parent2) {
        boolean qualifier1 = parent1.getName().indexOf('-') != -1;
        boolean qualifier2 = parent2.getName().indexOf('-') != -1;
        if (qualifier1 != qualifier2) {
          return qualifier1 ? 1 : -1;
        }
      }

      return file1.getPath().compareTo(file2.getPath());
    } else if (file1 != null) {
      return -1;
    } else if (file2 != null) {
      return 1;
    }

    return 0;
  }

  /**
   * Utility method suitable for Comparator implementations which order resource files,
   * which will sort files by base folder followed by alphabetical configurations. Prioritizes
   * XML files higher than non-XML files.
   */
  public static int compareResourceFiles(@Nullable PsiFile file1, @Nullable PsiFile file2) {
    if (file1 != null && file2 != null && file1 != file2) {
      boolean xml1 = file1.getFileType() == StdFileTypes.XML;
      boolean xml2 = file2.getFileType() == StdFileTypes.XML;
      if (xml1 != xml2) {
        return xml1 ? -1 : 1;
      }
      PsiDirectory parent1 = file1.getParent();
      PsiDirectory parent2 = file2.getParent();
      if (parent1 != null && parent2 != null && parent1 != parent2) {
        boolean qualifier1 = parent1.getName().indexOf('-') != -1;
        boolean qualifier2 = parent2.getName().indexOf('-') != -1;

        // TODO: Sort in FolderConfiguration order!

        if (qualifier1 != qualifier2) {
          return qualifier1 ? 1 : -1;
        }
      }

      int delta = file1.getName().compareTo(file2.getName());
      if (delta != 0) {
        return delta;
      }
    } else if (file1 != null) {
      return -1;
    } else if (file2 != null) {
      return 1;
    }

    return 0;
  }

  public static boolean ensureFilesWritable(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
    return !ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files).hasReadonlyFiles();
  }

  public static class MyReferredResourceFieldInfo {
    private final String myClassName;
    private final String myFieldName;
    private final boolean mySystem;
    private final boolean myFromManifest;

    public MyReferredResourceFieldInfo(@NotNull String className, @NotNull String fieldName, boolean system, boolean fromManifest) {
      myClassName = className;
      myFieldName = fieldName;
      mySystem = system;
      myFromManifest = fromManifest;
    }

    @NotNull
    public String getClassName() {
      return myClassName;
    }

    @NotNull
    public String getFieldName() {
      return myFieldName;
    }

    public boolean isSystem() {
      return mySystem;
    }

    public boolean isFromManifest() {
      return myFromManifest;
    }
  }

  @NotNull
  public static XmlFile createFileResource(@NotNull String fileName,
                                           @NotNull PsiDirectory resSubdir,
                                           @NotNull String rootTagName,
                                           @NotNull String resourceType,
                                           boolean valuesResourceFile) throws Exception {
    FileTemplateManager manager = FileTemplateManager.getInstance(resSubdir.getProject());
    String templateName = getTemplateName(resourceType, valuesResourceFile, rootTagName);
    FileTemplate template = manager.getJ2eeTemplate(templateName);
    Properties properties = new Properties();
    if (!valuesResourceFile) {
      properties.setProperty(ROOT_TAG_PROPERTY, rootTagName);
    }

    if (ResourceType.LAYOUT.getName().equals(resourceType)) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(resSubdir);
      final AndroidPlatform platform = module != null ? AndroidPlatform.getInstance(module) : null;
      final int apiLevel = platform != null ? platform.getApiLevel() : -1;

      final String value = apiLevel == -1 || apiLevel >= 8
                           ? "match_parent" : "fill_parent";
      properties.setProperty(LAYOUT_WIDTH_PROPERTY, value);
      properties.setProperty(LAYOUT_HEIGHT_PROPERTY, value);
    }
    PsiElement createdElement = FileTemplateUtil.createFromTemplate(template, fileName, properties, resSubdir);
    assert createdElement instanceof XmlFile;
    return (XmlFile)createdElement;
  }

  private static String getTemplateName(String resourceType, boolean valuesResourceFile, String rootTagName) {
    if (valuesResourceFile) {
      return AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE;
    }
    if (ResourceType.LAYOUT.getName().equals(resourceType)) {
      return AndroidUtils.TAG_LINEAR_LAYOUT.equals(rootTagName)
             ? AndroidFileTemplateProvider.LAYOUT_RESOURCE_VERTICAL_FILE_TEMPLATE
             : AndroidFileTemplateProvider.LAYOUT_RESOURCE_FILE_TEMPLATE;
    }
    return AndroidFileTemplateProvider.RESOURCE_FILE_TEMPLATE;
  }

  @NotNull
  public static String getFieldNameByResourceName(@NotNull String fieldName) {
    return fieldName.replace('.', '_').replace('-', '_').replace(':', '_');
  }

  /**
   * Finds and returns the resource files named stateListName in the directories listed in dirNames.
   * If some of the directories do not contain a file with that name, creates such a resource file.
   * @param module Module containing the directories under investigation
   * @param folderType Type of the directories under investigation
   * @param resourceType Type of the resource file to create if necessary
   * @param stateListName Name of the resource files to be returned
   * @param dirNames List of directory names to look into
   * @return List of found and created files
   */
  @Nullable
  public static List<VirtualFile> findOrCreateStateListFiles(@NotNull Module module, @NotNull final ResourceFolderType folderType,
                                                             @NotNull final ResourceType resourceType, @NotNull final String stateListName,
                                                             @NotNull final List<String> dirNames) {
    final Project project = module.getProject();
    final PsiManager manager = PsiManager.getInstance(project);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    final VirtualFile resDir = facet.getPrimaryResourceDir();

    if (resDir == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("check.resource.dir.error", module.getName()));
      return null;
    }

    final List<VirtualFile> files = Lists.newArrayListWithCapacity(dirNames.size());
    boolean foundFiles = new WriteCommandAction<Boolean>(project, "Find statelists files") {
      @Override
      protected void run(@NotNull Result<Boolean> result) {
        result.setResult(true);
        try {
          String fileName = stateListName;
          if (!stateListName.endsWith(SdkConstants.DOT_XML)) {
            fileName += SdkConstants.DOT_XML;
          }

          for (String dirName : dirNames) {
            String dirPath = FileUtil.toSystemDependentName(resDir.getPath() + '/' + dirName);
            final VirtualFile dir;

            dir = AndroidUtils.createChildDirectoryIfNotExist(project, resDir, dirName);
            if (dir == null) {
              throw new IOException("cannot make " + resDir + File.separatorChar + dirName);
            }

            VirtualFile file = dir.findChild(fileName);
            if (file != null) {
              files.add(file);
              continue;
            }

            PsiDirectory directory = manager.findDirectory(dir);
            if (directory == null) {
              throw new IOException("cannot find " + resDir + File.separatorChar + dirName);
            }

            createFileResource(fileName, directory, CreateTypedResourceFileAction.getDefaultRootTagByResourceType(folderType),
                               resourceType.getName(), false);

            file = dir.findChild(fileName);
            if (file == null) {
              throw new IOException("cannot find " + Joiner.on(File.separatorChar).join(resDir, dirPath, fileName));
            }
            files.add(file);
          }
        }
        catch (Exception e) {
          LOG.error(e.getMessage());
          result.setResult(false);
        }
      }
    }.execute().getResultObject();

    return foundFiles ? files : null;
  }
}
