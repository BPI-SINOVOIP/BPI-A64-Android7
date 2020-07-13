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

package com.android.tools.idea.javadoc;

import com.android.SdkConstants;
import com.android.builder.model.*;
import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.*;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.rendering.*;
import com.android.utils.HtmlBuilder;
import com.android.utils.SdkUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import org.jetbrains.android.AndroidColorAnnotator;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.Locale;

import static com.android.SdkConstants.PREFIX_ANDROID;
import static com.android.ide.common.resources.ResourceResolver.MAX_RESOURCE_INDIRECTION;
import static com.android.utils.SdkUtils.hasImageExtension;

public class AndroidJavaDocRenderer {
  /** Renders the Javadoc for a resource of given type and name. If configuration is not null, it will be used to resolve the resource.  */
  @Nullable
  public static String render(@NotNull Module module, @Nullable Configuration configuration, @NotNull ResourceType type, @NotNull String name, boolean framework) {
    return render(module, configuration, ResourceUrl.create(type, name, framework, false));
  }

  /** Renders the Javadoc for a resource of given type and name. */
  @Nullable
  public static String render(@NotNull Module module, @NotNull ResourceType type, @NotNull String name, boolean framework) {
    return render(module, null, type, name, framework);
  }

  /** Renders the Javadoc for a resource of given type and name. If configuration is not null, it will be used to resolve the resource. */
  @Nullable
  public static String render(@NotNull Module module, @Nullable Configuration configuration, @NotNull ResourceUrl url) {
    ResourceValueRenderer renderer = ResourceValueRenderer.create(url.type, module, configuration);
    boolean framework = url.framework;
    if (renderer == null || framework && renderer.getFrameworkResources() == null || !framework && renderer.getAppResources() == null) {
      return null;
    }

    return renderer.render(url);
  }

  /** Renders the Javadoc for a resource of given type and name. */
  @Nullable
  public static String render(@NotNull Module module, @NotNull ResourceUrl url) {
    return render(module, null, url);
  }

  @NotNull
  private static String renderAttributeDoc(Configuration configuration, ItemResourceValue resValue) {
    AttributeDefinition def = ResolutionUtils.getAttributeDefinition(configuration, resValue);
    String doc = (def == null) ? null : def.getDocValue(null);
    HtmlBuilder builder = new HtmlBuilder();
    builder.beginBold();
    builder.add(ResolutionUtils.getQualifiedItemName(resValue));
    builder.endBold();
    builder.addHtml("<br/>");

    if (!StringUtil.isEmpty(doc)) {
      builder.addHtml(doc);
      builder.addHtml("<br/>");
    }
    builder.addHtml("<hr/>");
    return builder.getHtml();
  }

  @NotNull
  private static String renderValue(@NotNull Module module, @Nullable Configuration configuration, ItemResourceValue resValue) {
    String value = resValue.getValue();

    final Color color = ResourceHelper.parseColor(value);
    if (color != null) {
      return renderColor(module, color);
    }

    ResourceUrl resUrl = ResourceUrl.parse(value);

    // Render value as a string
    if (resUrl == null) {
      return renderText(value);
    }

    if (!resUrl.framework && resValue.isFramework()) {
      // sometimes the framework people forgot to put android: in the value, so we need to fix for this.
      // To do that, we just reparse the resource adding the android: namespace.
      resUrl = ResourceUrl.parse(resUrl.toString().replace(resUrl.type.getName(), SdkConstants.PREFIX_ANDROID + resUrl.type.getName()));
    }

    String render = render(module, configuration, resUrl);

    // Render value as a string
    if (render == null) {
      return renderText(value);
    }

    return render;
  }

  /** Renders the Javadoc for a resValue. If configuration is not null, it will be used to resolve the resource.
   *  In addition, displays attribute documentation for resValue
   **/
  @NotNull
  public static String renderItemResourceWithDoc(@NotNull Module module, @Nullable Configuration configuration, @NotNull ItemResourceValue resValue) {
    String doc = renderAttributeDoc(configuration, resValue);
    String render = renderValue(module, configuration, resValue);

    String bodyTag = "<body>";
    int bodyIndex = render.indexOf(bodyTag);

    // Appending doc after <body>
    return render.substring(0, bodyIndex + bodyTag.length()) + doc + render.substring(bodyIndex + bodyTag.length());
  }

  /** Renders the Javadoc for a color resource and name. */
  private static String renderColor(Module module, @NotNull Color color) {
    ColorValueRenderer renderer = (ColorValueRenderer) ResourceValueRenderer.create(ResourceType.COLOR, module, null);
    HtmlBuilder builder = new HtmlBuilder();
    builder.openHtmlBody();
    renderer.renderColorToHtml(builder, color);
    builder.closeHtmlBody();
    return builder.getHtml();
  }

  private static String renderText(String text) {
    HtmlBuilder builder = new HtmlBuilder();
    builder.openHtmlBody();
    builder.add(text);
    builder.closeHtmlBody();
    return builder.getHtml();
  }

  /** Combines external javadoc into the documentation rendered by the {@link #render} method */
  @Nullable
  public static String injectExternalDocumentation(@Nullable String rendered, @Nullable String external) {
    if (rendered == null) {
      return external;
    } else if (external == null) {
      return rendered;
    }
    // Strip out HTML tags from the external documentation
    external = external.replace("<HTML>","").replace("</HTML>","");
    // Strip out styles.
    int styleStart = external.indexOf("<style");
    int styleEnd = external.indexOf("</style>");
    if (styleStart != -1 && styleEnd != -1) {
      String style = external.substring(styleStart, styleEnd + "</style>".length());
      external = external.substring(0, styleStart) + external.substring(styleEnd + "</style>".length());
      // Insert into our own head
      int insert = rendered.indexOf("<body>");
      if (insert != -1) {
        int headEnd = rendered.lastIndexOf("</head>", insert);
        if (headEnd != -1) {
          insert = headEnd;
          rendered = rendered.substring(0, insert) + style + rendered.substring(insert);
        } else {
          rendered = rendered.substring(0, insert) + "<head>" + style + "</head>" + rendered.substring(insert);
        }
      }
    }

    int bodyEnd = rendered.indexOf("</body>");
    if (bodyEnd != -1) {
      rendered = rendered.substring(0, bodyEnd) + external + rendered.substring(bodyEnd);
    }

    return rendered;
  }

  private static abstract class ResourceValueRenderer implements ResourceItemResolver.ResourceProvider {
    protected final Module myModule;
    protected final Configuration myConfiguration;
    protected AppResourceRepository myAppResources;
    protected ResourceResolver myResourceResolver;
    protected boolean mySmall;
    protected ResourceRepository myFrameworkResources;

    protected ResourceValueRenderer(@NotNull Module module, @Nullable Configuration configuration) {
      myModule = module;
      myConfiguration = configuration;
    }
    protected ResourceValueRenderer(Module module) {
      this(module, null);
    }

    public void setSmall(boolean small) {
      mySmall = small;
    }

    public abstract void renderToHtml(@NotNull HtmlBuilder builder, @NotNull ItemInfo item, @NotNull ResourceUrl url,
                                      boolean showResolution, @Nullable ResourceValue resourceValue);

    /** Creates a renderer suitable for the given resource type */
    @Nullable
    public static ResourceValueRenderer create(@NotNull ResourceType type, @NotNull Module module, @Nullable Configuration configuration) {
      switch (type) {
        case ATTR:
        case STRING:
        case DIMEN:
        case INTEGER:
        case BOOL:
        case STYLE:
          return new TextValueRenderer(module, configuration);
        case ARRAY:
          return new ArrayRenderer(module, configuration);
        case MIPMAP:
        case DRAWABLE:
          return new DrawableValueRenderer(module, configuration);
        case COLOR:
          return new ColorValueRenderer(module, configuration);
        default:
          // Ignore
          return null;
      }
    }

    /**
     * Returns a {@link FrameworkResources} instance that allows accessing the framework public resources of the highest available SDK.
     */
    @Nullable
    private static FrameworkResources getLatestPublicFrameworkResources(Module module) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        return null;
      }

      IAndroidTarget target = facet.getConfigurationManager().getDefaultTarget();
      if (target == null) {
        return null;
      }

      AndroidTargetData targetData = AndroidTargetData.getTargetData(target, module);
      if (targetData != null) {
        try {
          return targetData.getFrameworkResources();
        }
        catch (IOException e) {
          // Ignore docs
        }
      }

      return null;
    }


    @Nullable
    public String render(@NotNull ResourceUrl url) {
      List<ItemInfo> items = gatherItems(url);
      if (items != null) {
        Collections.sort(items);
        return renderKeyValues(items, url);
      }

      return null;
    }

    @Nullable
    private List<ItemInfo> gatherItems(@NotNull ResourceUrl url) {
      ResourceType type = url.type;
      String resourceName = url.name;
      boolean framework = url.framework;

      if (framework) {
        List<ItemInfo> results = Lists.newArrayList();
        addItemsFromFramework(null, MASK_NORMAL, 0, type, resourceName, results);
        return results;
      }

      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet == null) {
        return null;
      }

      List<ItemInfo> results = Lists.newArrayList();

      AppResourceRepository resources = getAppResources();
      IdeaAndroidProject androidModel = facet.getAndroidModel();
      if (androidModel != null) {
        assert facet.requiresAndroidModel();
        AndroidProject androidProject = androidModel.getAndroidProject();
        Variant selectedVariant = androidModel.getSelectedVariant();
        Set<SourceProvider> selectedProviders = Sets.newHashSet();

        BuildTypeContainer buildType = androidModel.findBuildType(selectedVariant.getBuildType());
        assert buildType != null;
        SourceProvider sourceProvider = buildType.getSourceProvider();
        String buildTypeName = selectedVariant.getName();
        int rank = 0;
        addItemsFromSourceSet(buildTypeName, MASK_FLAVOR_SELECTED, rank++, sourceProvider, type, resourceName, results, facet);
        selectedProviders.add(sourceProvider);

        List<String> productFlavors = selectedVariant.getProductFlavors();
        // Iterate in *reverse* order
        for (int i = productFlavors.size() - 1; i >= 0; i--) {
          String flavorName = productFlavors.get(i);
          ProductFlavorContainer productFlavor = androidModel.findProductFlavor(flavorName);
          assert productFlavor != null;
          SourceProvider provider = productFlavor.getSourceProvider();
          addItemsFromSourceSet(flavorName, MASK_FLAVOR_SELECTED, rank++, provider, type, resourceName, results, facet);
          selectedProviders.add(provider);
        }

        SourceProvider main = androidProject.getDefaultConfig().getSourceProvider();
        addItemsFromSourceSet("main", MASK_FLAVOR_SELECTED, rank++, main, type, resourceName, results, facet);
        selectedProviders.add(main);

        // Next display any source sets that are *not* in the selected flavors or build types!
        Collection<BuildTypeContainer> buildTypes = androidProject.getBuildTypes();
        for (BuildTypeContainer container : buildTypes) {
          SourceProvider provider = container.getSourceProvider();
          if (!selectedProviders.contains(provider)) {
            addItemsFromSourceSet(container.getBuildType().getName(), MASK_NORMAL, rank++, provider, type, resourceName, results, facet);
            selectedProviders.add(provider);
          }
        }

        Collection<ProductFlavorContainer> flavors = androidProject.getProductFlavors();
        for (ProductFlavorContainer container : flavors) {
          SourceProvider provider = container.getSourceProvider();
          if (!selectedProviders.contains(provider)) {
            addItemsFromSourceSet(container.getProductFlavor().getName(), MASK_NORMAL, rank++, provider, type, resourceName, results, facet);
            selectedProviders.add(provider);
          }
        }

        // Also pull in items from libraries; this will include items from the current module as well,
        // so add them to a temporary list so we can only add the items that are missing
        if (resources != null) {
          for (LocalResourceRepository dependency : resources.getLibraries()) {
              addItemsFromRepository(dependency.getDisplayName(), MASK_NORMAL, rank++, dependency, type, resourceName, results);
          }
        }
      } else if (resources != null) {
        addItemsFromRepository(null, MASK_NORMAL, 0, resources, type, resourceName, results);
      }

      return results;
    }

    private static void addItemsFromSourceSet(@Nullable String flavor,
                                              int mask,
                                              int rank,
                                              @NotNull SourceProvider sourceProvider,
                                              @NotNull ResourceType type,
                                              @NotNull String name,
                                              @NotNull List<ItemInfo> results,
                                              @NotNull AndroidFacet facet) {
      Collection<File> resDirectories = sourceProvider.getResDirectories();
      LocalFileSystem fileSystem = LocalFileSystem.getInstance();
      for (File dir : resDirectories) {
        VirtualFile virtualFile = fileSystem.findFileByIoFile(dir);
        if (virtualFile != null) {
          ResourceFolderRepository resources = ResourceFolderRegistry.get(facet,  virtualFile);
          addItemsFromRepository(flavor, mask, rank, resources, type, name, results);
        }
      }
    }

    private void addItemsFromFramework(@Nullable String flavor,
                                       int mask,
                                       int rank,
                                       @NotNull ResourceType type,
                                       @NotNull String name,
                                       @NotNull List<ItemInfo> results) {
      ResourceRepository frameworkResources = getFrameworkResources();
      if (frameworkResources == null) {
        return;
      }

      if (frameworkResources.hasResourceItem(type, name)) {
        com.android.ide.common.resources.ResourceItem item = frameworkResources.getResourceItem(type, name);
        for (com.android.ide.common.resources.ResourceFile resourceFile : item.getSourceFileList()) {
          FolderConfiguration configuration = resourceFile.getConfiguration();
          ResourceValue value = resourceFile.getValue(type, name);

          String folderName = resourceFile.getFolder().getFolder().getName();
          String folder = renderFolderName(folderName);
          ItemInfo info = new ItemInfo(value, configuration, folder, flavor, rank, mask);
          results.add(info);
        }
      }
    }

    private static void addItemsFromRepository(@Nullable String flavor,
                                               int mask,
                                               int rank,
                                               @NotNull AbstractResourceRepository resources,
                                               @NotNull ResourceType type,
                                               @NotNull String name,
                                               @NotNull List<ItemInfo> results) {
      List<ResourceItem> items = resources.getResourceItem(type, name);
      if (items != null) {
        for (ResourceItem item : items) {
          String folderName = "?";
          ResourceFile source = item.getSource();
          if (source != null) {
            folderName = source.getFile().getParentFile().getName();
          }
          String folder = renderFolderName(folderName);
          ResourceValue value = item.getResourceValue(resources.isFramework());
          ItemInfo info = new ItemInfo(value, item.getConfiguration(), folder, flavor, rank, mask);
          results.add(info);
        }
      }
    }

    @Nullable
    private String renderKeyValues(@NotNull List<ItemInfo> items, @NotNull ResourceUrl url) {
      if (items.isEmpty()) {
        return null;
      }

      markHidden(items);

      HtmlBuilder builder = new HtmlBuilder();
      builder.openHtmlBody();
      if (items.size() == 1) {
        renderToHtml(builder, items.get(0), url, true, items.get(0).value);
      } else {
        //noinspection SpellCheckingInspection
        builder.beginTable("valign=\"top\"");

        boolean haveFlavors = haveFlavors(items);
        if (haveFlavors) {
          builder.addTableRow(true, "Flavor/Library", "Configuration", "Value");
        } else {
          builder.addTableRow(true, "Configuration", "Value");
        }

        String prevFlavor = null;
        boolean showResolution = true;
        for (ItemInfo info : items) {
          String folder = info.folder;
          String flavor = StringUtil.notNullize(info.flavor);
          if (flavor.equals(prevFlavor)) {
            flavor = "";
          } else {
            prevFlavor = flavor;
          }

          builder.addHtml("<tr>");
          if (haveFlavors) {
            // Bold selected flavors?
            String style = ( (info.displayMask & MASK_FLAVOR_SELECTED) != 0) ? "b" : null;
            addTableCell(builder, style, flavor, null, null, false);
          }
          addTableCell(builder, null, folder, null, null, false);
          String style = ( (info.displayMask & MASK_ITEM_HIDDEN) != 0) ? "s" : null;
          addTableCell(builder, style, null, info, url, showResolution);
          showResolution = false; // Only show for first item
          builder.addHtml("</tr>");
        }

        builder.endTable();
      }
      builder.closeHtmlBody();
      return builder.getHtml();
    }

    private void addTableCell(@NotNull HtmlBuilder builder,
                              @Nullable String attribute,
                              @Nullable String text,
                              @Nullable ItemInfo info,
                              @Nullable ResourceUrl url,
                              boolean showResolution) {
      //noinspection SpellCheckingInspection
      builder.addHtml("<td valign=\"top\">");
      if (attribute != null) {
        builder.addHtml("<").addHtml(attribute).addHtml(">");
      }

      if (text != null) {
        builder.add(text);
      } else {
        assert info != null;
        assert url != null;
        renderToHtml(builder, info, url, showResolution, info.value);
      }

      if (attribute != null) {
        builder.addHtml("</").addHtml(attribute).addHtml(">");
      }
      builder.addHtml("</td>");
    }

    @NotNull
    protected ResourceItemResolver createResolver(@NotNull ItemInfo item) {
      ResourceItemResolver resolver = new ResourceItemResolver(item.configuration, this, null);
      List<ResourceValue> lookupChain = Lists.newArrayList();
      lookupChain.add(item.value);
      resolver.setLookupChainList(lookupChain);
      return resolver;
    }

    @Nullable
    protected Object resolveValue(@NotNull ResourceItemResolver resolver, @Nullable ResourceValue itemValue, @NotNull ResourceUrl url) {
      assert resolver.getLookupChain() != null;
      resolver.setLookupChainList(Lists.<ResourceValue>newArrayList());

      if (itemValue != null) {
        String value = itemValue.getValue();
        if (value != null) {
          ResourceUrl parsed = ResourceUrl.parse(value);
          if (parsed != null) {
            ResourceValue v = new ResourceValue(url.type, url.name, url.framework);
            v.setValue(url.toString());
            ResourceValue resourceValue = resolver.resolveResValue(v);
            if (resourceValue != null && resourceValue.getValue() != null) {
              return resourceValue.getValue();
            }
          }
          return value;
        } else {
          ResourceValue v = new ResourceValue(url.type, url.name, url.framework);
          v.setValue(url.toString());
          ResourceValue resourceValue = resolver.resolveResValue(v);
          if (resourceValue != null && resourceValue.getValue() != null) {
            return resourceValue.getValue();
          } else if (resourceValue instanceof StyleResourceValue) {
            return ResourceUrl.create(resourceValue).toString();
          }

          return url.toString();
        }
      }

      return null;
    }

    protected void displayChain(@NotNull ResourceUrl url, @NotNull List<ResourceValue> lookupChain,
                                @NotNull HtmlBuilder builder, boolean newlineBefore, boolean newlineAfter) {
      if (!lookupChain.isEmpty()) {
        if (newlineBefore) {
          builder.newline();
        }
        String text = ResourceItemResolver.getDisplayString(url.toString(), lookupChain);
        builder.add(text);
        builder.newline();
        if (newlineAfter) {
          builder.newline();
        }
      }
    }

    // ---- Implements ResourceItemResolver.ResourceProvider ----

    @Override
    @Nullable
    public ResourceRepository getFrameworkResources() {
      if (myFrameworkResources == null) {
        myFrameworkResources = getLatestPublicFrameworkResources(myModule);
      }

      return myFrameworkResources;
    }

    @Override
    @Nullable
    public AppResourceRepository getAppResources() {
      if (myAppResources == null) {
        myAppResources = AppResourceRepository.getAppResources(myModule, true);
      }

      return myAppResources;
    }

    @Override
    @Nullable
    public ResourceResolver getResolver(boolean createIfNecessary) {
      if (myResourceResolver == null && createIfNecessary) {
        if (myConfiguration != null) {
          myResourceResolver = myConfiguration.getResourceResolver();
          if (myResourceResolver != null) {
            return myResourceResolver;
          }
        }

        AndroidFacet facet = AndroidFacet.getInstance(myModule);
        if (facet != null) {
          VirtualFile layout = AndroidColorAnnotator.pickLayoutFile(myModule, facet);
          if (layout != null) {
            Configuration configuration = facet.getConfigurationManager().getConfiguration(layout);
            myResourceResolver = configuration.getResourceResolver();
          }
        }
      }

      return myResourceResolver;
    }
  }

  private static boolean haveFlavors(List<ItemInfo> items) {
    for (ItemInfo info : items) {
      if (info.flavor != null) {
        return true;
      }
    }

    return false;
  }

  private static void markHidden(List<ItemInfo> items) {
    Set<String> hiddenQualifiers = Sets.newHashSet();
    for (ItemInfo info : items) {
      String folder = info.folder;

      if (hiddenQualifiers.contains(folder)) {
        info.displayMask |= MASK_ITEM_HIDDEN;
      }
      hiddenQualifiers.add(folder);
    }
  }

  private static String renderFolderName(String name) {
    String prefix = SdkConstants.FD_RES_VALUES;

    if (name.equals(prefix)) {
      return "Default";
    }

    if (name.startsWith(prefix + '-')) {
      return name.substring(prefix.length() + 1);
    } else {
      return name;
    }
  }

  private static class TextValueRenderer extends ResourceValueRenderer {
    private TextValueRenderer(@NotNull Module module, @Nullable Configuration configuration) {
      super(module, configuration);
    }

    @Nullable
    @Override
    protected String resolveValue(@NotNull ResourceItemResolver resolver, @Nullable ResourceValue value, @NotNull ResourceUrl url) {
      return (String)super.resolveValue(resolver, value, url);
    }

    @Override
    public void renderToHtml(@NotNull HtmlBuilder builder,
                             @NotNull ItemInfo item,
                             @NotNull ResourceUrl url,
                             boolean showResolution,
                             @Nullable ResourceValue resourceValue) {
      ResourceItemResolver resolver = createResolver(item);
      String value = resolveValue(resolver, resourceValue, url);
      List<ResourceValue> lookupChain = resolver.getLookupChain();

      if (value != null) {
        boolean found = false;
        if (url.theme) {
          // If it's a theme attribute such as ?foo, it might resolve to a value we can
          // preview in a better way, such as a drawable, color or array. In that case,
          // look at the resolution chain and figure out the type of the resolved value,
          // and if appropriate, append a customized rendering.
          if (value.startsWith("#")) {
            Color color = ResourceHelper.parseColor(value);
            if (color != null) {
              found = true;
              ResourceValueRenderer renderer = ResourceValueRenderer.create(ResourceType.COLOR, myModule, myConfiguration);
              assert renderer != null;
              ResourceValue resolved = new ResourceValue(url.type, url.name, url.framework);
              resolved.setValue(value);
              renderer.renderToHtml(builder, item, url, false, resolved);
              builder.newline();
            }
          } else if (value.endsWith(SdkConstants.DOT_PNG)) {
            File f = new File(value);
            if (f.exists()) {
              found = true;
              ResourceValueRenderer renderer = ResourceValueRenderer.create(ResourceType.DRAWABLE, myModule, myConfiguration);
              assert renderer != null;
              ResourceValue resolved = new ResourceValue(url.type, url.name, url.framework);
              resolved.setValue(value);
              renderer.renderToHtml(builder, item, url, false, resolved);
              builder.newline();
            }
          }

          if (!found) {
            assert lookupChain != null;
            for (int i = lookupChain.size() - 1; i >= 0; i--) {
              ResourceValue rv = lookupChain.get(i);
              if (rv != null) {
                String value2 = rv.getValue();
                if (value2 != null) {
                  ResourceUrl resourceUrl = ResourceUrl.parse(value2);
                  if (resourceUrl != null && !resourceUrl.theme) {
                    ResourceValueRenderer renderer = create(resourceUrl.type, myModule, myConfiguration);
                    if (renderer != null && renderer.getClass() != this.getClass()) {
                      found = true;
                      ResourceValue resolved = new ResourceValue(url.type, url.name, url.framework);
                      resolved.setValue(value);
                      renderer.renderToHtml(builder, item, resourceUrl, false, resolved);
                      builder.newline();
                      break;
                    }
                  }
                }
              }
            }
          }
        }

        if (!found && (!showResolution || lookupChain == null || lookupChain.isEmpty())) {
          builder.add(value);
        }
      } else if (item.value != null && item.value.getValue() != null) {
        builder.add(item.value.getValue());
      }

      if (showResolution) {
        assert lookupChain != null;
        displayChain(url, lookupChain, builder, true, true);

        if (!lookupChain.isEmpty()) {
          // See if we resolved to a style; if so, show its attributes
          ResourceValue rv = lookupChain.get(lookupChain.size() - 1);
          if (rv instanceof StyleResourceValue) {
            StyleResourceValue srv = (StyleResourceValue)rv;
            displayStyleValues(builder, item, resolver, srv);
          }
        }
      }
    }

    private void displayStyleValues(HtmlBuilder builder, ItemInfo item, ResourceItemResolver resolver, StyleResourceValue styleValue) {
      List<ResourceValue> lookupChain = resolver.getLookupChain();
      builder.addHtml("<hr>");
      builder.addBold(styleValue.getName()).add(":").newline();

      Set<String> masked = Sets.newHashSet();
      while (styleValue != null) {
        for (ItemResourceValue itemResourceValue : styleValue.getValues()) {
          String name = itemResourceValue.getName();
          if (masked.contains(name)) {
            continue;
          }
          masked.add(name);
          ResourceValue v = styleValue.getItem(name, itemResourceValue.isFrameworkAttr());
          String value = v != null ? v.getValue() : null;

          builder.addNbsps(4);
          if (itemResourceValue.isFrameworkAttr()) {
            builder.add(PREFIX_ANDROID);
          }
          builder.addBold(name).add(" = ").add(v != null ? v.getValue() : "null");
          if (v != null && v.getValue() != null) {
            ResourceUrl url = ResourceUrl.parse(v.getValue());
            if (url != null) {
              ResourceUrl resolvedUrl = url;
              int count = 0;
              while (resolvedUrl != null) {
                if (lookupChain != null) {
                  lookupChain.clear();
                }
                ResourceValue resourceValue;
                boolean framework = resolvedUrl.framework || styleValue.isFramework();
                if (resolvedUrl.theme) {
                  resourceValue = resolver.findItemInTheme(resolvedUrl.name, framework);
                }
                else {
                  resourceValue = resolver.findResValue(resolvedUrl.toString(), framework);
                }
                if (resourceValue == null || resourceValue.getValue() == null) {
                  break;
                }
                url = resolvedUrl;
                value = resourceValue.getValue();
                resolvedUrl = ResourceUrl.parse(value);
                if (count++ == MAX_RESOURCE_INDIRECTION) { // prevent deep recursion (likely an invalid resource cycle)
                  break;
                }
              }

              ResourceValueRenderer renderer = create(url.type, myModule, myConfiguration);
              if (renderer != null && renderer.getClass() != this.getClass()) {
                builder.newline();
                renderer.setSmall(true);
                ResourceValue resolved = new ResourceValue(url.type, url.name, url.framework);
                resolved.setValue(value);
                //noinspection ConstantConditions
                renderer.renderToHtml(builder, item, url, false, resolved);
              }
              else if (value != null) {
                builder.add(" => ");
                builder.add(value);
                builder.newline();
              }
            } else {
              builder.newline();
            }
          }
          else {
            builder.newline();
          }
        }

        styleValue = resolver.getParent(styleValue);
        if (styleValue != null) {
          builder.newline();
          builder.add("Inherits from: ").add(ResourceUrl.create(styleValue).toString()).add(":").newline();
        }
      }
    }
  }

  private static class ArrayRenderer extends ResourceValueRenderer {
    private ArrayRenderer(@NotNull Module module, @Nullable Configuration configuration) {
      super(module, configuration);
    }

    @Nullable
    @Override
    protected Object resolveValue(@NotNull ResourceItemResolver resolver, @Nullable ResourceValue value, @NotNull ResourceUrl url) {
      if (value != null) {
        assert resolver.getLookupChain() != null;
        resolver.setLookupChainList(Lists.<ResourceValue>newArrayList());
        return resolver.resolveResValue(value);
      }

      return null;
    }

    @Override
    public void renderToHtml(@NotNull HtmlBuilder builder,
                             @NotNull ItemInfo item,
                             @NotNull ResourceUrl url,
                             boolean showResolution,
                             @Nullable ResourceValue resourceValue) {
      ResourceItemResolver resolver = createResolver(item);
      Object value = resolveValue(resolver, resourceValue, url);
      if (value instanceof ArrayResourceValue) {
        ArrayResourceValue arv = (ArrayResourceValue)value;
        builder.add(Joiner.on(", ").skipNulls().join(arv));
      } else if (value != null) {
        builder.add(value.toString());
      }

      if (showResolution) {
        List<ResourceValue> lookupChain = resolver.getLookupChain();
        assert lookupChain != null;
        // For arrays we end up pointing to the first element with PsiResourceItem.getValue, so only show the
        // resolution chain if it reveals something interesting (e.g. intermediate aliases)
        if (lookupChain.size() > 1) {
          displayChain(url, lookupChain, builder, true, false);
        }
      }
    }
  }

  private static class DrawableValueRenderer extends ResourceValueRenderer {
    private DrawableValueRenderer(@NotNull Module module, @Nullable Configuration configuration) {
      super(module, configuration);
    }

    @Nullable
    @Override
    protected File resolveValue(@NotNull ResourceItemResolver resolver, @Nullable ResourceValue value, @NotNull ResourceUrl url) {
      assert resolver.getLookupChain() != null;
      resolver.setLookupChainList(Lists.<ResourceValue>newArrayList());
      return ResourceHelper.resolveDrawable(resolver, value, myModule.getProject());
    }

    @Override
    public void renderToHtml(@NotNull HtmlBuilder builder,
                             @NotNull ItemInfo item,
                             @NotNull ResourceUrl url,
                             boolean showResolution,
                             @Nullable ResourceValue resourceValue) {
      ResourceItemResolver resolver = createResolver(item);
      File bitmap = resolveValue(resolver, resourceValue, url);
      if (bitmap != null && bitmap.exists() && hasImageExtension(bitmap.getPath())) {
        URL fileUrl = null;
        try {
          fileUrl = SdkUtils.fileToUrl(bitmap);
        }
        catch (MalformedURLException e) {
          // pass
        }

        if (fileUrl != null) {
          builder.beginDiv("background-color:gray;padding:10px");
          builder.addImage(fileUrl, bitmap.getPath());
          builder.endDiv();

          Dimension size = getSize(bitmap);
          if (size != null) {
            DensityQualifier densityQualifier = item.configuration.getDensityQualifier();
            Density density = densityQualifier == null ? Density.MEDIUM : densityQualifier.getValue();

            builder.addHtml(String.format(Locale.US, "%1$d&#xd7;%2$d px (%3$d&#xd7;%4$d dp @ %5$s)", size.width, size.height,
                                          px2dp(size.width, density), px2dp(size.height, density), density.getResourceValue()));
          }
        }
      } else if (bitmap != null) {
        builder.add(bitmap.getPath());
      }

      if (showResolution) {
        List<ResourceValue> lookupChain = resolver.getLookupChain();
        assert lookupChain != null;
        displayChain(url, lookupChain, builder, true, false);
      }
    }

    private static int px2dp(int px, Density density) {
      return (int)((float)px * Density.MEDIUM.getDpiValue() / density.getDpiValue());
    }
  }

  private static class ColorValueRenderer extends ResourceValueRenderer {
    private ColorValueRenderer(@NotNull Module module, @Nullable Configuration configuration) {
      super(module, configuration);
    }

    @Nullable
    @Override
    protected Color resolveValue(@NotNull ResourceItemResolver resolver, @Nullable ResourceValue value, @NotNull ResourceUrl url) {
      assert resolver.getLookupChain() != null;
      resolver.setLookupChainList(Lists.<ResourceValue>newArrayList());
      return ResourceHelper.resolveColor(resolver, value, myModule.getProject());
    }

    @Override
    public void renderToHtml(@NotNull HtmlBuilder builder,
                             @NotNull ItemInfo item,
                             @NotNull ResourceUrl url,
                             boolean showResolution,
                             @Nullable ResourceValue resourceValue) {
      ResourceItemResolver resolver = createResolver(item);
      Color color = resolveValue(resolver, resourceValue, url);
      if (color != null) {
        renderColorToHtml(builder, color);
      } else if (item.value != null && item.value.getValue() != null) {
        builder.add(item.value.getValue());
      }

      if (showResolution) {
        List<ResourceValue> lookupChain = resolver.getLookupChain();
        assert lookupChain != null;
        displayChain(url, lookupChain, builder, true, false);
      }
    }

    public void renderColorToHtml(@NotNull HtmlBuilder builder, @NotNull Color color) {
      int width = 200;
      int height = 100;
      if (mySmall) {
        int divisor = 3;
        width /= divisor;
        height /= divisor;
      }

      String colorString = String.format(Locale.US, "rgb(%d,%d,%d)", color.getRed(), color.getGreen(), color.getBlue());
      String foregroundColor = ColorUtil.isDark(color) ? "white" : "black";
      String css = "background-color:" + colorString +
                   ";width:" + width + "px;text-align:center;vertical-align:middle;";
      // Use <table> tag such that we can center the color text (Java's HTML renderer doesn't support
      // vertical-align:middle on divs)
      builder.addHtml("<table style=\"" + css + "\" border=\"0\"><tr height=\"" + height + "\">");
      builder.addHtml("<td align=\"center\" valign=\"middle\" height=\"" + height + "\" style=\"color:" + foregroundColor + "\">");
      builder.addHtml(ResourceHelper.colorToString(color));
      builder.addHtml("</td></tr></table>");
    }
  }

  /**
   * Returns the dimensions of an Image if it can be obtained without fully reading it into memory.
   * This is a copy of the method in {@link com.android.tools.lint.checks.IconDetector}.
   */
  @Nullable
  private static Dimension getSize(File file) {
    try {
      ImageInputStream input = ImageIO.createImageInputStream(file);
      if (input != null) {
        try {
          Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
          if (readers.hasNext()) {
            ImageReader reader = readers.next();
            try {
              reader.setInput(input);
              return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
              reader.dispose();
            }
          }
        } finally {
          input.close();
        }
      }

      // Fallback: read the image using the normal means
      //BufferedImage image = ImageIO.read(file);
      //if (image != null) {
      //  return new Dimension(image.getWidth(), image.getHeight());
      //} else {
      //  return null;
      //}
      return null;
    } catch (IOException e) {
      // Pass -- we can't handle all image types, warn about those we can
      return null;
    }
  }

  /** Normal display style */
  private static final int MASK_NORMAL = 0;
  /** Display style for flavor folders that are selected */
  private static final int MASK_FLAVOR_SELECTED = 1;
  /** Display style for items that are hidden by later resource folders */
  private static final int MASK_ITEM_HIDDEN = 2;

  /**
   * Information about {@link ResourceItem} instances to be displayed; in addition to the item and the
   * folder name, we can also record the flavor or library name, as well as display attributes indicating
   * whether the item is from a selected flavor, or whether the item is masked by a higher priority repository
   */
  private static class ItemInfo implements Comparable<ItemInfo> {
    @Nullable public final ResourceValue value;
    @NotNull public final FolderConfiguration configuration;
    @Nullable public final String flavor;
    @NotNull public final String folder;
    public final int rank;
    public int displayMask;

    private ItemInfo(@Nullable ResourceValue value, @NotNull FolderConfiguration configuration,
                     @NotNull String folder, @Nullable String flavor, int rank, int initialMask) {
      this.value = value;
      this.configuration = configuration;
      this.flavor = flavor;
      this.folder = folder;
      this.displayMask = initialMask;
      this.rank = rank;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ItemInfo itemInfo = (ItemInfo)o;

      if (rank != itemInfo.rank) return false;
      if (!configuration.equals(itemInfo.configuration)) return false;
      if (flavor != null ? !flavor.equals(itemInfo.flavor) : itemInfo.flavor != null) return false;
      if (!folder.equals(itemInfo.folder)) return false;
      if (value != null ? !value.equals(itemInfo.value) : itemInfo.value != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = value != null ? value.hashCode() : 0;
      result = 31 * result + configuration.hashCode();
      result = 31 * result + (flavor != null ? flavor.hashCode() : 0);
      result = 31 * result + folder.hashCode();
      result = 31 * result + rank;
      return result;
    }

    @Override
    public int compareTo(@NotNull ItemInfo other) {
      if (rank != other.rank) {
        return rank - other.rank;
      }

      // Special case density: when we're showing multiple drawables for different densities,
      // sort by density value, not alphabetical name.
      DensityQualifier density1 = configuration.getDensityQualifier();
      DensityQualifier density2 = other.configuration.getDensityQualifier();
      if (density1 != null && density2 != null) {
        // Start with the lowest densities to avoid case where you have a giant asset (say xxxhdpi)
        // and you only see the top left corner in the documentation window.
        int delta = density2.getValue().compareTo(density1.getValue());
        if (delta != 0) {
          return delta;
        }
      }

      return configuration.compareTo(other.configuration);
    }
  }
}
