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

import com.android.SdkConstants;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.ResourceFolderRegistry;
import com.android.tools.idea.rendering.ResourceFolderRepository;
import com.android.tools.idea.rendering.ResourceNameValidator;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.android.tools.idea.templates.Template.*;

/**
 * Parameter represents an external input to a template. It consists of an ID used to refer to it within the template,
 * human-readable information to be displayed in the UI, and type and validation specifications that can be used in the UI to assist in
 * data entry.
 */
public class Parameter {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.templates.Parameter");
  private static final Set<String> typeValues = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

  public enum Type {
    STRING,
    BOOLEAN,
    ENUM,
    SEPARATOR,
    EXTERNAL,
    CUSTOM;
    // TODO: Numbers?

    public static Type get(String name) {
      try {
        if (typeValues.isEmpty()) {
          for(Type t : Type.values()) {
            typeValues.add(t.name().toUpperCase(Locale.US));
          }
        }

        String upperCaseName = name.toUpperCase(Locale.US);
        if (!typeValues.contains(upperCaseName)) {
          return Type.CUSTOM;
        }
        return Type.valueOf(upperCaseName);
      } catch (IllegalArgumentException e) {
        LOG.error("Unexpected template type '" + name + "'");
        LOG.error("Expected one of :");
        for (Type s : Type.values()) {
          LOG.error("  " + s.name().toLowerCase(Locale.US));
        }
      }

      return STRING;
    }
  }

  /**
   * Constraints that can be applied to a parameter which helps the UI add a
   * validator etc for user input. These are typically combined into a set
   * of constraints via an EnumSet.
   */
  public enum Constraint {
    /**
     * This value must be unique. This constraint usually only makes sense
     * when other constraints are specified, such as {@link #LAYOUT}, which
     * means that the parameter should designate a name that does not
     * represent an existing layout resource name
     */
    UNIQUE,

    /**
     * This value must already exist. This constraint usually only makes sense
     * when other constraints are specified, such as {@link #LAYOUT}, which
     * means that the parameter should designate a name that already exists as
     * a resource name.
     */
    EXISTS,

    /** The associated value must not be empty */
    NONEMPTY,

    /** The associated value is allowed to be empty */
    EMPTY,

    /** The associated value should represent a fully qualified activity class name */
    ACTIVITY,

    /** The associated value should represent an API level */
    APILEVEL,

    /** The associated value should represent a valid class name */
    CLASS,

    /** The associated value should represent a valid package name */
    PACKAGE,

    /** The associated value should represent a valid Android application package name */
    APP_PACKAGE,

    /** The associated value should represent a valid Module name */
    MODULE,

    /** The associated value should represent a valid layout resource name */
    LAYOUT,

    /** The associated value should represent a valid drawable resource name */
    DRAWABLE,

    /** The associated value should represent a valid id resource name */
    ID,

    /** The associated value should represent a valid source directory name */
    SOURCE_SET_FOLDER,

    /** The associated value should represent a valid string resource name */
    STRING;

    public static Constraint get(String name) {
      try {
        return Constraint.valueOf(name.toUpperCase(Locale.US));
      } catch (IllegalArgumentException e) {
        LOG.error("Unexpected template constraint '" + name + "'");
        if (name.indexOf(',') != -1) {
          LOG.error("Use | to separate constraints");
        } else {
          LOG.error("Expected one of :");
          for (Constraint s : Constraint.values()) {
            LOG.error("  " + s.name().toLowerCase(Locale.US));
          }
        }
      }

      return NONEMPTY;
    }
  }

  /** The template defining the parameter */
  public final TemplateMetadata template;

  /** The type of parameter */
  @NotNull
  public final Type type;

  /** The unique id of the parameter (not displayed to the user) */
  @Nullable
  public final String id;

  /** The display name for this parameter */
  @Nullable
  public final String name;

  /**
   * The initial value for this parameter (see also {@link #suggest} for more
   * dynamic defaults
   */
  @Nullable
  public final String initial;

  /**
   * A template expression using other template parameters for producing a
   * default value based on other edited parameters, if possible.
   */
  @Nullable
  public final String suggest;

  /**
   * A template expression using other template parameters for dynamically changing
   * the visibility of this parameter to the user.
   */
  @Nullable
  public final String visibility;

  /**
   * A template expression using other template parameters for dynamically changing
   * whether this parameter is enabled for the user.
   */
  @Nullable
  public final String enabled;

  /**
   * A URL for externally sourced values.
   */
  @Nullable
  public final String sourceUrl;

  /** Help for the parameter, if any */
  @Nullable
  public final String help;

  /** The element defining this parameter */
  @NotNull
  public final Element element;

  /** The constraints applicable for this parameter */
  @NotNull
  public final EnumSet<Constraint> constraints;

  /** The dsl name of the type that will be created in the ui for the user to enter this parameter.
   *  This should correspond to a name registered by an ExternalWizardParameterFactory extension. */
  public String externalTypeName;

  Parameter(@NotNull TemplateMetadata template, @NotNull Element parameter) {
    this.template = template;
    element = parameter;

    String typeName = parameter.getAttribute(Template.ATTR_TYPE);
    assert typeName != null && !typeName.isEmpty() : Template.ATTR_TYPE;
    type = Type.get(typeName);

    id = parameter.getAttribute(ATTR_ID);
    initial = parameter.getAttribute(ATTR_DEFAULT);
    suggest = parameter.getAttribute(ATTR_SUGGEST);
    visibility = parameter.getAttribute(ATTR_VISIBILITY);
    enabled = parameter.getAttribute(ATTR_ENABLED);
    sourceUrl = type == Type.EXTERNAL ? parameter.getAttribute(ATTR_SOURCE_URL) : null;
    name = parameter.getAttribute(ATTR_NAME);
    help = parameter.getAttribute(ATTR_HELP);
    if (type == Type.CUSTOM) {
        externalTypeName = typeName;
    }
    else {
      externalTypeName = null;
    }
    String constraintString = parameter.getAttribute(ATTR_CONSTRAINTS);
    if (constraintString != null && !constraintString.isEmpty()) {
      EnumSet<Constraint> constraintSet = null;
      for (String s : Splitter.on('|').omitEmptyStrings().split(constraintString)) {
        Constraint constraint = Constraint.get(s);
        if (constraintSet == null) {
          constraintSet = EnumSet.of(constraint);
        } else {
          constraintSet = EnumSet.copyOf(constraintSet);
          constraintSet.add(constraint);
        }
      }
      constraints = constraintSet;
    } else {
      constraints = EnumSet.noneOf(Constraint.class);
    }
  }

  Parameter(
    @NotNull TemplateMetadata template,
    @NotNull Type type,
    @NotNull String id) {
    this.template = template;
    this.type = type;
    this.id = id;
    element = null;
    initial = null;
    suggest = null;
    visibility = null;
    enabled = null;
    sourceUrl = null;
    name = id;
    help = null;
    constraints = EnumSet.noneOf(Constraint.class);
  }

  public List<Element> getOptions() {
    return TemplateUtils.getChildren(element);
  }

  @Nullable
  public String validate(@Nullable Project project, @Nullable String packageName, @Nullable Object value) {
    return validate(project, null, null, packageName, value);
  }

  @Nullable
  public String validate(@Nullable Project project, @Nullable Module module, @Nullable String packageName, @Nullable Object value) {
    return validate(project, module, null, packageName, value);
  }

  @Nullable
  public String validate(@Nullable Project project, @Nullable Module module, @Nullable SourceProvider provider,
                         @Nullable String packageName, @Nullable Object value) {
    switch (type) {
      case EXTERNAL:
      case CUSTOM:
      case STRING:
        return getErrorMessageForStringType(project, module, provider, packageName, value.toString());
      case BOOLEAN:
      case ENUM:
      case SEPARATOR:
      default:
        return null;
    }
  }

  /**
   * Validate the given value for this parameter and list any reasons why the given value is invalid.
   * @param project
   * @param packageName
   * @param value
   * @return An error message detailing why the given value is invalid.
   */
  @Nullable
  protected String getErrorMessageForStringType(@Nullable Project project, @Nullable Module module, @Nullable SourceProvider provider,
                                                @Nullable String packageName, @Nullable String value) {
    Collection<Constraint> violations = validateStringType(project, module, provider, packageName, value);

    if (violations.contains(Constraint.NONEMPTY)) {
      return "Please specify " + name;

    } else if (violations.contains(Constraint.ACTIVITY)) {
      return name + " is not a valid activity name";

    } else if (violations.contains(Constraint.APILEVEL)) {
      // TODO: validity check
    } else if (violations.contains(Constraint.CLASS)) {
      return name + " is not a valid class name";

    } else if (violations.contains(Constraint.PACKAGE)) {
      return name + " is not a valid package name";

    } else if (violations.contains(Constraint.MODULE)) {
      return name + " is not a valid module name";

    } else if (violations.contains(Constraint.APP_PACKAGE) && value != null) {
      String message = AndroidUtils.validateAndroidPackageName(value);
      if (message != null) {
        return  message;

      }
    } else if (violations.contains(Constraint.LAYOUT) && value != null) {
      String resourceNameError = ResourceNameValidator.create(false, ResourceFolderType.LAYOUT).getErrorText(value);
      if (resourceNameError != null) {
        return name + " is not a valid resource name. " + resourceNameError;

      }
    } else if (violations.contains(Constraint.DRAWABLE)) {
      String resourceNameError = ResourceNameValidator.create(false, ResourceFolderType.DRAWABLE).getErrorText(value);
      if (resourceNameError != null) {
        return name + " is not a valid resource name. " + resourceNameError;

      }
    } else if (violations.contains(Constraint.ID)) {
      return name + " is not a valid id.";

    } else if (violations.contains(Constraint.STRING)) {
      String resourceNameError = ResourceNameValidator.create(false, ResourceFolderType.VALUES).getErrorText(value);
      if (resourceNameError != null) {
        return name + " is not a valid resource name. " + resourceNameError;

      }
    }

    if (violations.contains(Constraint.UNIQUE)) {
      return  name + " must be unique";

    } else if (violations.contains(Constraint.EXISTS)) {
      return  name + " must already exist";

    }

    return null;
  }

  /**
   * Validate the given value for this parameter and list the constraints that the given value violates.
   * @return All constraints of this parameter that are violated by the proposed value.
   */
  @NotNull
  protected Collection<Constraint> validateStringType(@Nullable Project project, @Nullable Module module, @Nullable SourceProvider provider,
                                                      @Nullable String packageName, @Nullable String value) {
    GlobalSearchScope searchScope = module != null ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) :
                                    GlobalSearchScope.EMPTY_SCOPE;

    Set<Constraint> violations = Sets.newHashSet();
    if (value == null || value.isEmpty()) {
      if (constraints.contains(Constraint.NONEMPTY)) {
        violations.add(Constraint.NONEMPTY);
      }
      return violations;
    }
    boolean exists = false;
    String fqName = (packageName != null && value.indexOf('.') == -1 ? packageName + "." : "") + value;

    if (constraints.contains(Constraint.ACTIVITY)) {
      if (!isValidFullyQualifiedJavaIdentifier(fqName)) {
        violations.add(Constraint.ACTIVITY);
      }
      if (project != null) {
        PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(fqName, searchScope);
        PsiClass activityClass = JavaPsiFacade.getInstance(project).findClass(SdkConstants.CLASS_ACTIVITY, GlobalSearchScope.allScope(project));
        exists = aClass != null && activityClass != null && aClass.isInheritor(activityClass, true);
      }
    }
    if (constraints.contains(Constraint.APILEVEL)) {
      // TODO: validity check
    }
    if (constraints.contains(Constraint.CLASS)) {
      if (!isValidFullyQualifiedJavaIdentifier(fqName)) {
        violations.add(Constraint.CLASS);
      }
      if (project != null) {
        exists = existsClassFile(project, searchScope, provider, fqName);
      }
    }
    if (constraints.contains(Constraint.PACKAGE)) {
      if (!isValidFullyQualifiedJavaIdentifier(value)) {
        violations.add(Constraint.PACKAGE);
      }
      if (project != null) {
        exists = existsPackage(project, searchScope, provider, value);
      }
    }
    if (constraints.contains(Constraint.MODULE)) {
      // TODO: validity check
      if (project != null) {
        exists = ModuleManager.getInstance(project).findModuleByName(value) != null;
      }
    }
    if (constraints.contains(Constraint.APP_PACKAGE)) {
      String message = AndroidUtils.validateAndroidPackageName(value);
      if (message != null) {
        violations.add(Constraint.APP_PACKAGE);
      }
      if (project != null) {
        exists = existsPackage(project, searchScope, provider, value);
      }
    }
    if (constraints.contains(Constraint.LAYOUT)) {
      String resourceNameError = ResourceNameValidator.create(false, ResourceFolderType.LAYOUT).getErrorText(value);
      if (resourceNameError != null) {
        violations.add(Constraint.LAYOUT);
      }
      exists = provider != null ? existsResourceFile(provider, module, ResourceFolderType.LAYOUT, ResourceType.LAYOUT, value) :
                                  existsResourceFile(module, ResourceType.LAYOUT, value);
    }
    if (constraints.contains(Constraint.DRAWABLE)) {
      String resourceNameError = ResourceNameValidator.create(false, ResourceFolderType.DRAWABLE).getErrorText(value);
      if (resourceNameError != null) {
        violations.add(Constraint.DRAWABLE);
      }
      exists = provider != null ? existsResourceFile(provider, module, ResourceFolderType.DRAWABLE, ResourceType.DRAWABLE, value) :
                                  existsResourceFile(module, ResourceType.DRAWABLE, value);
    }
    if (constraints.contains(Constraint.ID)) {
      // TODO: validity and existence check
    }
    if (constraints.contains(Constraint.STRING)) {
      String resourceNameError = ResourceNameValidator.create(false, ResourceFolderType.VALUES).getErrorText(value);
      if (resourceNameError != null) {
        violations.add(Constraint.STRING);
      }
      // TODO: Existence check
    }
    if (constraints.contains(Constraint.SOURCE_SET_FOLDER)) {
      if (module != null) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          String modulePath = AndroidRootUtil.getModuleDirPath(module);
          if (modulePath != null) {
            File file = new File(FileUtil.toSystemDependentName(modulePath), value);
            VirtualFile vFile = VfsUtil.findFileByIoFile(file, true);
            exists = !IdeaSourceProvider.getSourceProvidersForFile(facet, vFile, null).isEmpty();
          }
        }
      }
    }

    if (constraints.contains(Constraint.UNIQUE) && exists) {
      violations.add(Constraint.UNIQUE);
    } else if (constraints.contains(Constraint.EXISTS) && !exists) {
      violations.add(Constraint.EXISTS);
    }
    return violations;
  }

  /**
   * Returns true if the given stringType is non-unique when it should be.
   */
  public boolean uniquenessSatisfied(@Nullable Project project, @Nullable Module module, @Nullable SourceProvider provider,
                                     @Nullable String packageName, @Nullable String value) {
    return !validateStringType(project, module, provider, packageName, value).contains(Constraint.UNIQUE);
  }

  private static boolean isValidFullyQualifiedJavaIdentifier(String value) {
    return AndroidUtils.isValidJavaPackageName(value) && value.indexOf('.') != -1;
  }

  public static boolean existsResourceFile(@Nullable Module module, @NotNull ResourceType resourceType, @Nullable String name) {
    if (name == null || name.isEmpty() || module == null) {
      return false;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      AppResourceRepository repository = facet.getAppResources(true);
      return repository.hasResourceItem(resourceType, name);
    }
    return false;
  }

  public static boolean existsResourceFile(@Nullable SourceProvider sourceProvider, @Nullable Module module,
                                           @NotNull ResourceFolderType resourceFolderType, @NotNull ResourceType resourceType,
                                           @Nullable String name) {
    if (name == null || name.isEmpty() || sourceProvider == null) {
      return false;
    }
    AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
    for (File resDir : sourceProvider.getResDirectories()) {
      if (facet != null) {
        VirtualFile virtualResDir = VfsUtil.findFileByIoFile(resDir, false);
        if (virtualResDir != null) {
          ResourceFolderRepository folderRepository = ResourceFolderRegistry.get(facet, virtualResDir);
          List<ResourceItem> resourceItemList = folderRepository.getResourceItem(resourceType, name);
          if (resourceItemList != null && !resourceItemList.isEmpty()) {
            return true;
          }
        }
      } else if (existsResourceFile(resDir, resourceFolderType, name)) {
        return true;
      }
    }
    return false;
  }

  public static boolean existsResourceFile(File resDir, ResourceFolderType resourceType, String name) {
    File[] resTypes = resDir.listFiles();
    if (resTypes != null) {
      for (File resTypeDir : resTypes) {
        if (resTypeDir.isDirectory() && resourceType.equals(ResourceFolderType.getFolderType(resTypeDir.getName()))) {
          File[] files = resTypeDir.listFiles();
          if (files != null) {
            for (File f : files) {
              if (getNameWithoutExtensions(f).equalsIgnoreCase(name)) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  @NotNull
  private static String getNameWithoutExtensions(@NotNull File f) {
    if (f.getName().indexOf('.') == -1) {
      return f.getName();
    } else {
      return f.getName().substring(0, f.getName().indexOf('.'));
    }
  }

  public static boolean existsClassFile(@Nullable Project project, @NotNull GlobalSearchScope searchScope,
                                        @Nullable SourceProvider sourceProvider, @NotNull String fullyQualifiedClassName) {
    if (project == null) {
      return false;
    }
    if (sourceProvider != null) {
      for (File javaDir : sourceProvider.getJavaDirectories()) {
        File classFile = new File(javaDir, fullyQualifiedClassName.replace('.', File.separatorChar) + SdkConstants.DOT_JAVA);
        if (classFile.exists()) {
          return true;
        }
      }
      return false;
    } else if (searchScope != GlobalSearchScope.EMPTY_SCOPE) {
      return JavaPsiFacade.getInstance(project).findClass(fullyQualifiedClassName, searchScope) != null;
    } else {
      return false;
    }
  }

  public static boolean existsPackage(@Nullable Project project, @NotNull GlobalSearchScope searchScope,
                                        @Nullable SourceProvider sourceProvider, @NotNull String packageName) {
    if (project == null) {
      return false;
    }
    if (sourceProvider != null) {
      for (File javaDir : sourceProvider.getJavaDirectories()) {
        File classFile = new File(javaDir, packageName.replace('.', File.separatorChar));
        if (classFile.exists() && classFile.isDirectory()) {
          return true;
        }
      }
      return false;
    } else {
      return JavaPsiFacade.getInstance(project).findPackage(packageName) != null;
    }
  }

  @Override
  public String toString() {
    return "(parameter id: " + id + ")";
  }
}