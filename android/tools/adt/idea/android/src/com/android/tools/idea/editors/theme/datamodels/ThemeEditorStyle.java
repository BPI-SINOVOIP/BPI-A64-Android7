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
package com.android.tools.idea.editors.theme.datamodels;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ResourceResolverCache;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.ThemeResolver;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.ProjectResourceRepository;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Wrapper for style configurations that allows modifying attributes directly in the XML file.
 */
public class ThemeEditorStyle {
  private static final Logger LOG = Logger.getInstance(ThemeEditorStyle.class);

  private final @NotNull StyleResourceValue myStyleResourceValue;
  private final @NotNull Configuration myConfiguration;
  private final Project myProject;

  /**
   * Source module of the theme, set to null if the theme comes from external libraries or the framework.
   * For currently edited theme stored in {@link ThemeEditorContext#getCurrentContextModule()}.
   */
  private final @Nullable Module mySourceModule;

  public ThemeEditorStyle(final @NotNull Configuration configuration,
                          final @NotNull StyleResourceValue styleResourceValue,
                          final @Nullable Module sourceModule) {
    myStyleResourceValue = styleResourceValue;
    myConfiguration = configuration;
    myProject = configuration.getModule().getProject();
    mySourceModule = sourceModule;
  }

  public boolean isProjectStyle() {
    if (myStyleResourceValue.isFramework()) {
      return false;
    }
    ProjectResourceRepository repository = ProjectResourceRepository.getProjectResources(myConfiguration.getModule(), true);
    assert repository != null : myConfiguration.getModule().getName();
    return repository.hasResourceItem(ResourceType.STYLE, myStyleResourceValue.getName());
  }

  /**
   * Returns StyleResourceValue for current Configuration
   */
  @NotNull
  private StyleResourceValue getStyleResourceValue() {
    return myStyleResourceValue;
  }

  /**
   * Returns all the {@link ResourceItem} where this style is defined. This includes all the definitions in the
   * different resource folders.
   */
  @NotNull
  private List<ResourceItem> getStyleResourceItems() {
    assert !isFramework();

    final ImmutableList.Builder<ResourceItem> resourceItems = ImmutableList.builder();
    if (isProjectStyle()) {
      final Module module = getModuleForAcquiringResources();
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null : module.getName() + " module doesn't have AndroidFacet";
      ThemeEditorUtils.acceptResourceResolverVisitor(facet, new ThemeEditorUtils.ResourceFolderVisitor() {
        @Override
        public void visitResourceFolder(@NotNull LocalResourceRepository resources, @NotNull String variantName, boolean isSourceSelected) {
          if (!isSourceSelected) {
            // Currently we ignore the source sets that are not active
            // TODO: Process all source sets
            return;
          }

          List<ResourceItem> items = resources.getResourceItem(ResourceType.STYLE, myStyleResourceValue.getName());
          if (items == null) {
            return;
          }
          resourceItems.addAll(items);
        }
      });
    } else {
      LocalResourceRepository resourceRepository = AppResourceRepository.getAppResources(getModuleForAcquiringResources(), true);
      assert resourceRepository != null;
      List<ResourceItem> items = resourceRepository.getResourceItem(ResourceType.STYLE, getName());
      if (items != null) {
        resourceItems.addAll(items);
      }
    }
    return resourceItems.build();
  }

  /**
   * Get a Module instance that should be used for resolving all possible resources that could constitute values
   * of this theme's attributes. Returns source module of a theme if it's available and rendering context module
   * otherwise.
   */
  private Module getModuleForAcquiringResources() {
    return mySourceModule != null ? mySourceModule : myConfiguration.getModule();
  }

  /**
   * Returns whether this style is editable.
   */
  public boolean isReadOnly() {
    return !isProjectStyle();
  }

  /**
   * Returns the style name. If this is a framework style, it will include the "android:" prefix.
   * Can be null, if there is no corresponding StyleResourceValue
   */
  @NotNull
  public String getQualifiedName() {
    return ResolutionUtils.getQualifiedStyleName(myStyleResourceValue);
  }

  /**
   * Returns the style name without namespaces or prefixes.
   */
  @NotNull
  public String getName() {
    return getStyleResourceValue().getName();
  }

  private static Collection<FolderConfiguration> getFolderConfigurationsFromResourceItems(@NotNull Collection<ResourceItem> items) {
    ImmutableList.Builder<FolderConfiguration> listBuilder = ImmutableList.builder();
    for (ResourceItem item : items) {
      listBuilder.add(item.getConfiguration());
    }

    return listBuilder.build();
  }

  @NotNull
  public Collection<FolderConfiguration> getFolderConfigurations() {
    if (isFramework()) {
      return ImmutableList.of(new FolderConfiguration());
    }

    return getFolderConfigurationsFromResourceItems(getStyleResourceItems());
  }

  /**
   * Returns all the style attributes and it's values. For each attribute, multiple {@link ConfiguredItemResourceValue} can be returned
   * representing the multiple values in different configurations for each item.
   */
  @NotNull
  public Multimap<String, ConfiguredItemResourceValue> getConfiguredValues() {
    // Get a list of all the items indexed by the item name. Each item contains a list of the
    // possible values in this theme in different configurations.
    //
    // If item1 has multiple values in different configurations, there will be an
    // item1 = {folderConfiguration1 -> value1, folderConfiguration2 -> value2}
    final Multimap<String, ConfiguredItemResourceValue> itemResourceValues = ArrayListMultimap.create();

    if (isFramework()) {
      assert myConfiguration.getFrameworkResources() != null;

      com.android.ide.common.resources.ResourceItem styleItem =
        myConfiguration.getFrameworkResources().getResourceItem(ResourceType.STYLE, myStyleResourceValue.getName());
      // Go over all the files containing the resource.
      for (ResourceFile file : styleItem.getSourceFileList()) {
        ResourceValue styleResourceValue = styleItem.getResourceValue(ResourceType.STYLE, file.getConfiguration(), true);
        FolderConfiguration folderConfiguration = file.getConfiguration();

        if (styleResourceValue instanceof StyleResourceValue) {
          for (final ItemResourceValue value : ((StyleResourceValue)styleResourceValue).getValues()) {
            itemResourceValues
              .put(ResolutionUtils.getQualifiedItemName(value), new ConfiguredItemResourceValue(folderConfiguration, value, this));
          }
        }
      }
    }
    else {
      LocalResourceRepository repository = AppResourceRepository.getAppResources(getModuleForAcquiringResources(), true);
      assert repository != null;
      // Find every definition of this style and get all the attributes defined
      List<ResourceItem> styleDefinitions = repository.getResourceItem(ResourceType.STYLE, myStyleResourceValue.getName());
      assert styleDefinitions != null; // Style doesn't exist anymore?
      for (ResourceItem styleDefinition : styleDefinitions) {
        ResourceValue styleResourceValue = styleDefinition.getResourceValue(isFramework());
        FolderConfiguration folderConfiguration = styleDefinition.getConfiguration();

        if (styleResourceValue instanceof StyleResourceValue) {
          for (final ItemResourceValue value : ((StyleResourceValue)styleResourceValue).getValues()) {
            // We use the qualified name since apps and libraries can use the same attribute name twice with and without "android:"
            itemResourceValues
              .put(ResolutionUtils.getQualifiedItemName(value), new ConfiguredItemResourceValue(folderConfiguration, value, this));
          }
        }
      }
    }

    return itemResourceValues;
  }

  public boolean hasItem(@Nullable EditedStyleItem item) {
    //TODO: add isOverriden() method to EditedStyleItem
    return item != null && getStyleResourceValue().getItem(item.getName(), item.isFrameworkAttr()) != null;
  }

  /**
   * See {@link #getParent(ThemeResolver)}
   */
  public ThemeEditorStyle getParent() {
    return getParent(null);
  }

  /**
   * Returns all the possible parents of this style. Parents might differ depending on the folder configuration, this returns all the
   * variants for this style.
   * @param themeResolver ThemeResolver, used for getting resolved themes by name.
   */
  public Collection<ThemeEditorStyle> getAllParents(@NotNull ThemeResolver themeResolver) {
    if (isFramework()) {
      ThemeEditorStyle parent = getParent(themeResolver);

      if (parent != null) {
        return ImmutableList.of(parent);
      } else {
        return Collections.emptyList();
      }
    }

    ResourceResolverCache resolverCache = ResourceResolverCache.create(myConfiguration.getConfigurationManager());
    ImmutableList.Builder<ThemeEditorStyle> parents = ImmutableList.builder();
    Set<String> parentNames = Sets.newHashSet();
    for (ResourceItem item : getStyleResourceItems()) {
      ResourceResolver resolver = resolverCache.getResourceResolver(myConfiguration.getTarget(), getQualifiedName(), item.getConfiguration());
      StyleResourceValue parent = resolver.getParent(myStyleResourceValue);
      String parentName = parent == null ? null : ResolutionUtils.getQualifiedStyleName(parent);
      if (parentName == null || !parentNames.add(parentName)) {
        // The parent name is null or was already added
        continue;
      }

      final ThemeEditorStyle style = themeResolver.getTheme(parentName);

      if (style != null) {
        parents.add(style);
      }
    }
    resolverCache.reset();

    return parents.build();
  }

  /**
   * Returns the style parent or null if this is a root style.
   *
   * @param themeResolver theme resolver that would be used to look up parent theme by name
   *                      Pass null if you don't care about resulting ThemeEditorStyle source module (which would be null in that case)
   */
  @Nullable
  public ThemeEditorStyle getParent(@Nullable ThemeResolver themeResolver) {
    ResourceResolver resolver = myConfiguration.getResourceResolver();
    assert resolver != null;

    StyleResourceValue parent = resolver.getParent(getStyleResourceValue());
    if (parent == null) {
      return null;
    }

    if (themeResolver == null) {
      return ResolutionUtils.getStyle(myConfiguration, ResolutionUtils.getQualifiedStyleName(parent), null);
    }
    else {
      return themeResolver.getTheme(ResolutionUtils.getQualifiedStyleName(parent));
    }
  }

  /**
   * Returns the XmlTag that contains the value for a given attribute in the current style.
   * @param attribute The style attribute name.
   * @return The {@link XmlTag} or null if the attribute does not exist in this theme.
   */
  @Nullable
  private XmlTag getValueTag(@NotNull XmlTag sourceTag, @NotNull final String attribute) {
    if (!isProjectStyle()) {
      // Non project styles do not contain local values.
      return null;
    }

    final Ref<XmlTag> resultXmlTag = new Ref<XmlTag>();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    sourceTag.acceptChildren(new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);

        if (!(element instanceof XmlTag)) {
          return;
        }

        final XmlTag tag = (XmlTag)element;
        if (SdkConstants.TAG_ITEM.equals(tag.getName()) && attribute.equals(tag.getAttributeValue(SdkConstants.ATTR_NAME))) {
          resultXmlTag.set(tag);
        }
      }
    });

    return resultXmlTag.get();
  }

  /**
   * Returns the {@link ResourceItem}s that are above minAcceptableApi
   * @param minProjectApi the project min API level
   * @param minAcceptableApi the minimum acceptable API level
   * @param styleResourceItems the ResourceItems
   */
  @NotNull
  private static Iterable<ResourceItem> filterStylesByApiLevel(final int minProjectApi, final int minAcceptableApi, @NotNull Iterable<ResourceItem> styleResourceItems) {
    return Iterables.filter(styleResourceItems, new Predicate<ResourceItem>() {
      @Override
      public boolean apply(@Nullable ResourceItem input) {
        assert input != null;

        FolderConfiguration itemConfiguration = input.getConfiguration();

        if (itemConfiguration.isDefault() && minProjectApi < minAcceptableApi) {
          // We can not add the parent to the default version (which we assume is == minProjectApi)
          return false;
        }

        VersionQualifier versionQualifier = itemConfiguration.getVersionQualifier();
        if (versionQualifier == null) {
          if (minProjectApi < minAcceptableApi) {
            // The folder doesn't have version qualifier and minProjectApi < minAcceptable so we can not add the item here
            return false;
          }
        } else if (versionQualifier.getVersion() < minAcceptableApi) {
          // We can not add the attribute to this version.
          return false;
        }

        return true;
      }
    });
  }

  /**
   * Filters out {@link ResourceItem}s that are not contained in the passed folders
   */
  @NotNull
  private static Iterable<ResourceItem> filterStylesByFolder(@NotNull Collection<FolderConfiguration> selectedFolders, @NotNull Iterable<ResourceItem> styleResourceItems) {
    final Set<FolderConfiguration> foldersSet = Sets.newHashSet(selectedFolders);

    return Iterables.filter(styleResourceItems, new Predicate<ResourceItem>() {
      @Override
      public boolean apply(@Nullable ResourceItem input) {
        assert input != null;
        return foldersSet.contains(input.getConfiguration());
      }
    });
  }

  /**
   * Returns the {@link XmlTag}s associated to the passed {@link ResourceItem}s.
   */
  @NotNull
  private static Iterable<XmlTag> getXmlTagsFromStyles(@NotNull final Project project, @NotNull Iterable<ResourceItem> styleResourceItems) {
    return FluentIterable.from(styleResourceItems)
      .transform(new Function<ResourceItem, XmlTag>() {
        @Override
        public XmlTag apply(@Nullable ResourceItem input) {
          assert input != null;
          return LocalResourceRepository.getItemTag(project, input);
        }
      })
      .filter(Predicates.notNull());
  }

  @NotNull
  private static PsiFile[] getPsiFilesFromXmlTags(@NotNull Iterable<XmlTag> stylesXmlTags) {
    return FluentIterable.from(stylesXmlTags)
      .transform(new Function<XmlTag, PsiFile>() {
        @Override
        public PsiFile apply(@Nullable XmlTag input) {
          assert input != null;
          return input.getContainingFile();
        }
      })
      .filter(Predicates.notNull())
      .toArray(PsiFile.class);
  }

  /**
   * Sets the value of a given attribute in all possible folders. If an attribute is only declared in certain API level, folders below that
   * level won't be modified.
   * @param attribute The style attribute name.
   * @param value The attribute value.
   */
  public void setValue(@NotNull final String attribute, @NotNull final String value) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }

    setValue(getFolderConfigurations(), attribute, value);
  }

  /**
   * Sets the value of a given attribute in a specific folder.
   * @param attribute The style attribute name.
   * @param value The attribute value.
   */
  public void setValue(@NotNull FolderConfiguration currentFolder, @NotNull final String attribute, @NotNull final String value) {
    setValue(ImmutableList.of(currentFolder), attribute, value);
  }

  /**
   * Sets the attribute value in the given folders
   * @param attribute The style attribute name.
   * @param value The attribute value.
   */
  public void setValue(@NotNull Collection<FolderConfiguration> selectedFolders, @NotNull final String attribute, @NotNull final String value) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }

    if (selectedFolders.isEmpty()) {
      return;
    }

    // The API level where the attribute was defined
    int attributeDefinitionApi = Math.max(ThemeEditorUtils.getOriginalApiLevel(attribute, myProject),
                                          ThemeEditorUtils.getOriginalApiLevel(value, myProject));

    final int minProjectApi = ThemeEditorUtils.getMinApiLevel(myConfiguration.getModule());
    final int minAcceptableApi = attributeDefinitionApi != -1 ? attributeDefinitionApi : 1;
    final List<ResourceItem> styleResourceItems = getStyleResourceItems();
    final FolderConfiguration sourceConfiguration = findAcceptableSourceFolderConfiguration(myConfiguration.getModule(), minAcceptableApi,
                                                                                            getFolderConfigurationsFromResourceItems(
                                                                                              styleResourceItems));

    // Find a valid source style that we can copy to the new API level
    final ResourceItem sourceStyle = Iterables.find(styleResourceItems, new Predicate<ResourceItem>() {
      @Override
      public boolean apply(@Nullable ResourceItem input) {
        assert input != null;
        return input.getConfiguration().equals(sourceConfiguration);
      }
    }, null);

    Iterable<ResourceItem> filteredStyles = filterStylesByFolder(selectedFolders, styleResourceItems);
    filteredStyles = filterStylesByApiLevel(minProjectApi, minAcceptableApi, filteredStyles);
    final Iterable<XmlTag> stylesXmlTags = getXmlTagsFromStyles(myProject, filteredStyles);
    PsiFile[] toBeEdited = getPsiFilesFromXmlTags(stylesXmlTags);

    new WriteCommandAction.Simple(myProject, "Setting value of " + attribute, toBeEdited) {
      @Override
      protected void run() {
        // Makes the command global even if only one xml file is modified
        // That way, the Undo is always available from the theme editor
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);

        boolean copyStyle = true;
        for (XmlTag sourceXml : stylesXmlTags) {
          // TODO: Check if the current value is defined by one of the parents and remove the attribute.
          XmlTag tag = getValueTag(sourceXml, attribute);
          if (tag != null) {
            tag.getValue().setEscapedText(value);
            // If the attribute has already been overridden, assume it has been done everywhere the user deemed necessary.
            // So do not create new api folders in that case.
            copyStyle = false;
          }
          else {
            // The value didn't exist, add it.
            XmlTag child = sourceXml.createChildTag(SdkConstants.TAG_ITEM, sourceXml.getNamespace(), value, false);
            child.setAttribute(SdkConstants.ATTR_NAME, attribute);
            sourceXml.addSubTag(child, false);
          }
        }

        if (copyStyle && sourceStyle != null) {
          final VersionQualifier qualifier = new VersionQualifier(minAcceptableApi);

          // Does the theme already exist at the minimum acceptable API level?
          boolean acceptableApiExists = Iterables.any(styleResourceItems, new Predicate<ResourceItem>() {
            @Override
            public boolean apply(ResourceItem input) {
              return input.getQualifiers().contains(qualifier.getFolderSegment());
            }
          });

          if (!acceptableApiExists) {
            XmlTag sourceXmlTag = LocalResourceRepository.getItemTag(myProject, sourceStyle);
            assert sourceXmlTag != null;
            // copy this theme at the minimum api level for this attribute
            ThemeEditorUtils.copyTheme(minAcceptableApi, sourceXmlTag);

            AndroidFacet facet = AndroidFacet.getInstance(getModuleForAcquiringResources());
            if (facet != null) {
              facet.refreshResources();
            }
          }

          List<ResourceItem> newResources = getStyleResourceItems();
          for (ResourceItem resourceItem : newResources) {
            if (resourceItem.getQualifiers().contains(qualifier.getFolderSegment())) {
              final XmlTag sourceXml = LocalResourceRepository.getItemTag(myProject, resourceItem);
              assert sourceXml != null;

              XmlTag child = getValueTag(sourceXml, attribute);
              if (child == null) {
                child = sourceXml.createChildTag(SdkConstants.TAG_ITEM, sourceXml.getNamespace(), value, false);
              }
              child.setAttribute(SdkConstants.ATTR_NAME, attribute);
              sourceXml.addSubTag(child, false);
              break;
            }
          }
        }
      }
    }.execute();
  }

  /**
   * Changes the name of the themes in all the xml files
   * The theme needs to be reloaded in ThemeEditorComponent for the change to be complete
   * THIS METHOD DOES NOT DIRECTLY MODIFY THE VALUE ONE GETS WHEN EVALUATING getParent()
   */
  public void setParent(@NotNull final String newParent) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }

    setParent(getFolderConfigurations(), newParent);
  }

  /**
   * Changes the name of the themes in the given folder
   * The theme needs to be reloaded in ThemeEditorComponent for the change to be complete
   * THIS METHOD DOES NOT DIRECTLY MODIFY THE VALUE ONE GETS WHEN EVALUATING getParent()
   */
  public void setParent(@NotNull FolderConfiguration currentFolder, @NotNull final String newParent) {
    setParent(ImmutableList.of(currentFolder), newParent);
  }

  /**
   * Changes the name of the themes in given folders
   * The theme needs to be reloaded in ThemeEditorComponent for the change to be complete
   * THIS METHOD DOES NOT DIRECTLY MODIFY THE VALUE ONE GETS WHEN EVALUATING getParent()
   */
  public void setParent(@NotNull Collection<FolderConfiguration> selectedFolders, @NotNull final String newParent) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }
    final int minProjectApi = ThemeEditorUtils.getMinApiLevel(myConfiguration.getModule());
    final int minAcceptableApi = ThemeEditorUtils.getOriginalApiLevel(newParent, myProject);
    final FolderConfiguration sourceConfiguration = findAcceptableSourceFolderConfiguration(myConfiguration.getModule(), minAcceptableApi,
                                                                                            getFolderConfigurations());
    List<ResourceItem> styleResourceItems = getStyleResourceItems();

    // Find a valid source style that we can copy to the new API level
    final ResourceItem sourceStyle = Iterables.find(styleResourceItems, new Predicate<ResourceItem>() {
      @Override
      public boolean apply(@Nullable ResourceItem input) {
        assert input != null;
        return input.getConfiguration().equals(sourceConfiguration);
      }
    }, null);
    Iterable<ResourceItem> filteredStyles = filterStylesByFolder(selectedFolders, styleResourceItems);
    filteredStyles = filterStylesByApiLevel(minProjectApi, minAcceptableApi, filteredStyles);
    final Iterable<XmlTag> stylesXmlTags = getXmlTagsFromStyles(myProject, filteredStyles);
    PsiFile[] toBeEdited = getPsiFilesFromXmlTags(stylesXmlTags);

    new WriteCommandAction.Simple(myProject, "Updating parent to " + newParent, toBeEdited) {
      @Override
      protected void run() {
        // Makes the command global even if only one xml file is modified
        // That way, the Undo is always available from the theme editor
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);

        for (XmlTag sourceXml : stylesXmlTags) {
          sourceXml.setAttribute(SdkConstants.ATTR_PARENT, newParent);
        }

        if (sourceStyle != null) {
          XmlTag sourceXmlTag = LocalResourceRepository.getItemTag(myProject, sourceStyle);
          assert sourceXmlTag != null;
          // copy this theme at the minimum api level for this attribute
          ThemeEditorUtils.copyTheme(minAcceptableApi, sourceXmlTag);

          AndroidFacet facet = AndroidFacet.getInstance(myConfiguration.getModule());
          if (facet != null) {
            facet.refreshResources();
          }
          List<ResourceItem> newResources = getStyleResourceItems();
          VersionQualifier qualifier = new VersionQualifier(minAcceptableApi);
          for (ResourceItem resourceItem : newResources) {
            if (resourceItem.getQualifiers().contains(qualifier.getFolderSegment())) {
              final XmlTag sourceXml = LocalResourceRepository.getItemTag(myProject, resourceItem);
              assert sourceXml != null;

              sourceXml.setAttribute(SdkConstants.ATTR_PARENT, newParent);
              break;
            }
          }
        }
      }
    }.execute();
  }

  @Override
  public String toString() {
    if (!isReadOnly()) {
      return "[" + getName() + "]";
    }

    return getName();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || (!(obj instanceof ThemeEditorStyle))) {
      return false;
    }

    return getQualifiedName().equals(((ThemeEditorStyle)obj).getQualifiedName());
  }

  @Override
  public int hashCode() {
    return getQualifiedName().hashCode();
  }

  @NotNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  /**
   * Deletes an attribute of that particular style from all the relevant xml files
   */
  public void removeAttribute(@NotNull final String attribute) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }

    Collection<PsiFile> toBeEdited = new HashSet<PsiFile>();
    final Collection<XmlTag> toBeRemoved = new HashSet<XmlTag>();
    for (ResourceItem resourceItem : getStyleResourceItems()) {
      final XmlTag sourceXml = LocalResourceRepository.getItemTag(myProject, resourceItem);
      assert sourceXml != null;
      final XmlTag tag = getValueTag(sourceXml, attribute);
      if (tag != null) {
        toBeEdited.add(tag.getContainingFile());
        toBeRemoved.add(tag);
      }
    }

    new WriteCommandAction.Simple(myProject, "Removing " + attribute, toBeEdited.toArray(new PsiFile[toBeEdited.size()])) {
      @Override
      protected void run() {
        // Makes the command global even if only one xml file is modified
        // That way, the Undo is always available from the theme editor
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);

        for (XmlTag tag : toBeRemoved) {
          tag.delete();
        }
      }
    }.execute();
  }

  /**
   * Returns a PsiElement of the name attribute for this theme
   * made from a RANDOM sourceXml
   */
  @Nullable
  public PsiElement getNamePsiElement() {
    List<ResourceItem> resources = getStyleResourceItems();
    if (resources.isEmpty()){
      return null;
    }
    // Any sourceXml will do to get the name attribute from
    final XmlTag sourceXml = LocalResourceRepository.getItemTag(myProject, resources.get(0));
    assert sourceXml != null;
    final XmlAttribute nameAttribute = sourceXml.getAttribute("name");
    if (nameAttribute == null) {
      return null;
    }

    XmlAttributeValue attributeValue = nameAttribute.getValueElement();
    if (attributeValue == null) {
      return null;
    }

    return new ValueResourceElementWrapper(attributeValue);
  }

  /**
   * Plain getter, see {@link #mySourceModule} for field description.
   */
  @Nullable
  public Module getSourceModule() {
    return mySourceModule;
  }

  /**
   * Checks all the passed source folders and find an acceptable source to copy to the folder with minAcceptableApi.
   * If this method returns null, there is no need to copy the folder since it already exists.
   */
  @Nullable
  private static FolderConfiguration findAcceptableSourceFolderConfiguration(@NotNull Module module,
                                                                             int minAcceptableApi,
                                                                             @NotNull Collection<FolderConfiguration> folders) {
    int minProjectApiLevel = ThemeEditorUtils.getMinApiLevel(module);
    int highestNonAllowedApi = 0;
    FolderConfiguration toBeCopied = null;

    if (minAcceptableApi < minProjectApiLevel) {
      // Do not create a theme for an api level inferior to the min api level of the project
      return null;
    }

    for (FolderConfiguration folderConfiguration : folders) {
      int version = minProjectApiLevel;

      VersionQualifier versionQualifier = folderConfiguration.getVersionQualifier();
      if (versionQualifier != null && versionQualifier.isValid()) {
        version = versionQualifier.getVersion();
      }

      if (version < minAcceptableApi) {
        // The attribute is not defined for (version)
        // attribute not defined for api levels less than minAcceptableApi
        if (version > highestNonAllowedApi) {
          highestNonAllowedApi = version;
          toBeCopied = folderConfiguration;
        }
        continue;
      }

      if (version == minAcceptableApi) {
        // This theme already exists at its minimum api level, no need to create it
        return null;
      }
    }

    return toBeCopied;
  }

  /**
   * Returns whether this style is public.
   */
  public boolean isPublic() {
    if (!isFramework()) {
      return true;
    }

    IAndroidTarget target = myConfiguration.getTarget();
    if (target == null) {
      LOG.error("Unable to get IAndroidTarget.");
      return false;
    }

    AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, myConfiguration.getModule());
    if (androidTargetData == null) {
      LOG.error("Unable to get AndroidTargetData.");
      return false;
    }

    return androidTargetData.isResourcePublic(ResourceType.STYLE.getName(), getName());
  }

  public boolean isFramework() {
    return myStyleResourceValue.isFramework();
  }

  @NotNull
  public FolderConfiguration findBestConfiguration(@NotNull FolderConfiguration configuration) {
    Collection<FolderConfiguration> folderConfigurations = getFolderConfigurations();
    Configurable bestMatch = configuration.findMatchingConfigurable(ImmutableList.copyOf(Collections2.transform(folderConfigurations, new Function<FolderConfiguration, Configurable>() {
      @Nullable
      @Override
      public Configurable apply(final FolderConfiguration input) {
        assert input != null;
        return new Configurable() {
          @Override
          public FolderConfiguration getConfiguration() {
            return input;
          }
        };
      }
    })));

    return bestMatch == null ? folderConfigurations.iterator().next() : bestMatch.getConfiguration();
  }
}
