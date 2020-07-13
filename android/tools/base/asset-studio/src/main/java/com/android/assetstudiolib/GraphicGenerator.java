/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.assetstudiolib;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.utils.SdkUtils;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

/**
 * The base Generator class.
 */
public abstract class GraphicGenerator {
    /**
     * Options used for all generators.
     */
    public static class Options {
        /** Minimum version (API level) of the SDK to generate icons for */
        public int minSdk = 1;

        /** Source image to use as a basis for the icon */
        public BufferedImage sourceImage;

        /** The density to generate the icon with */
        public Density density = Density.XHIGH;

        /** Whether the icon should be written out to the mipmap folder instead of drawable */
        public boolean mipmap;
    }

    /** Shapes that can be used for icon backgrounds */
    public enum Shape {
        /** No background */
        NONE("none"),
        /** Circular background */
        CIRCLE("circle"),
        /** Square background */
        SQUARE("square"),
        /** Vertical rectangular background */
        VRECT("vrect"),
        /** Horizontal rectangular background */
        HRECT("hrect"),
        /** Square background with Dog-ear effect */
        SQUARE_DOG("square_dogear"),
        /** Vertical rectangular background with Dog-ear effect */
        VRECT_DOG("vrect_dogear"),
        /** Horizontal rectangular background with Dog-ear effect */
        HRECT_DOG("hrect_dogear");

        /** Id, used in filenames to identify associated stencils */
        public final String id;

        Shape(String id) {
            this.id = id;
        }
    }

    /** Foreground effects styles */
    public enum Style {
        /** No effects */
        SIMPLE("fore1");

        /** Id, used in filenames to identify associated stencils */
        public final String id;

        Style(String id) {
            this.id = id;
        }
    }

    /**
     * Generate a single icon using the given options
     *
     * @param context render context to use for looking up resources etc
     * @param options options controlling the appearance of the icon
     * @return a {@link BufferedImage} with the generated icon
     */
    public abstract BufferedImage generate(GraphicGeneratorContext context, Options options);

    /**
     * Computes the target filename (relative to the Android project folder)
     * where an icon rendered with the given options should be stored. This is
     * also used as the map keys in the result map used by
     * {@link #generate(String, Map, GraphicGeneratorContext, Options, String)}.
     *
     * @param options the options object used by the generator for the current
     *            image
     * @param name the base name to use when creating the path
     * @return a path relative to the res/ folder where the image should be
     *         stored (will always use / as a path separator, not \ on Windows)
     */
    protected String getIconPath(Options options, String name) {
        return getIconFolder(options) + '/' + getIconName(options, name);
    }

    /**
     * Gets name of the file itself. It is sometimes modified by options, for
     * example in unselected tabs we change foo.png to foo-unselected.png
     */
    protected String getIconName(Options options, String name) {
        if (options.density == Density.ANYDPI) {
            return name + SdkConstants.DOT_XML;
        }
        return name + SdkConstants.DOT_PNG; //$NON-NLS-1$
    }

    /**
     * Gets name of the folder to contain the resource. It usually includes the
     * density, but is also sometimes modified by options. For example, in some
     * notification icons we add in -v9 or -v11.
     */
    protected String getIconFolder(Options options) {
        if (options.density == Density.ANYDPI) {
            return SdkConstants.FD_RES + '/' +
                   ResourceFolderType.DRAWABLE.getName();
        }
        StringBuilder sb = new StringBuilder(50);
        sb.append(SdkConstants.FD_RES);
        sb.append('/');
        if (options.mipmap) {
            sb.append(ResourceFolderType.MIPMAP.getName());
        } else {
            sb.append(ResourceFolderType.DRAWABLE.getName());
        }
        sb.append('-');
        sb.append(options.density.getResourceValue());
        return sb.toString();
    }

    /**
     * Generates a full set of icons into the given map. The values in the map
     * will be the generated images, and each value is keyed by the
     * corresponding relative path of the image, which is determined by the
     * {@link #getIconPath(Options, String)} method.
     *
     * @param category the current category to place images into (if null the
     *            density name will be used)
     * @param categoryMap the map to put images into, should not be null. The
     *            map is a map from a category name, to a map from file path to
     *            image.
     * @param context a generator context which for example can load resources
     * @param options options to apply to this generator
     * @param name the base name of the icons to generate
     */
    public void generate(String category, Map<String, Map<String, BufferedImage>> categoryMap,
            GraphicGeneratorContext context, Options options, String name) {
        // Vector image only need to generate one preview image, so we by pass all the
        // other image densities.
        if (options.density == Density.ANYDPI) {
            generateImageAndUpdateMap(category, categoryMap, context, options, name);
            return;
        }
        Density[] densityValues = Density.values();
        // Sort density values into ascending order
        Arrays.sort(densityValues, new Comparator<Density>() {
            @Override
            public int compare(Density d1, Density d2) {
                return d1.getDpiValue() - d2.getDpiValue();
            }
        });
        for (Density density : densityValues) {
            if (!density.isValidValueForDevice()) {
                continue;
            }
            if (!includeDensity(density)) {
                // Not yet supported -- missing stencil image
                // TODO don't manually check and instead gracefully handle missing stencils.
                continue;
            }
            options.density = density;
            generateImageAndUpdateMap(category, categoryMap, context, options, name);
        }
    }

    private void generateImageAndUpdateMap(String category,
                                           Map<String, Map<String, BufferedImage>> categoryMap,
                                           GraphicGeneratorContext context,
                                           Options options,
                                           String name) {
        BufferedImage image = generate(context, options);
        if (image != null) {
            String mapCategory = category;
            if (mapCategory == null) {
                mapCategory = options.density.getResourceValue();
            }
            Map<String, BufferedImage> imageMap = categoryMap.get(mapCategory);
            if (imageMap == null) {
                imageMap = new LinkedHashMap<String, BufferedImage>();
                categoryMap.put(mapCategory, imageMap);
            }
            imageMap.put(getIconPath(options, name), image);
        }
    }

    protected boolean includeDensity(@NonNull Density density) {
        return density.isRecommended() && density != Density.LOW && density != Density.XXXHIGH;
    }

    /**
     * Returns the scale factor to apply for a given MDPI density to compute the
     * absolute pixel count to use to draw an icon of the given target density
     *
     * @param density the density
     * @return a factor to multiple mdpi distances with to compute the target density
     */
    public static float getMdpiScaleFactor(Density density) {
        if (density == Density.ANYDPI) {
            density = Density.XXXHIGH;
        }
        return density.getDpiValue() / (float) Density.MEDIUM.getDpiValue();
    }

    /**
     * Returns one of the built in stencil images, or null
     *
     * @param relativePath stencil path such as "launcher-stencil/square/web/back.png"
     * @return the image, or null
     * @throws IOException if an unexpected I/O error occurs
     */
    public static BufferedImage getStencilImage(String relativePath) throws IOException {
        InputStream is = GraphicGenerator.class.getResourceAsStream(relativePath);
        if (is == null) {
          return null;
        }
        try {
            return ImageIO.read(is);
        } finally {
            Closeables.close(is, true /* swallowIOException */);
        }
    }

    /**
     * Returns the icon (32x32) for a given clip art image.
     *
     * @param name the name of the image to be loaded (which can be looked up via
     *            {@link #getClipartNames()})
     * @return the icon image
     * @throws IOException if the image cannot be loaded
     */
    public static BufferedImage getClipartIcon(String name) throws IOException {
        InputStream is = GraphicGenerator.class.getResourceAsStream(
                "/images/clipart/small/" + name);
        try {
            return ImageIO.read(is);
        } finally {
            Closeables.close(is, true /* swallowIOException */);
        }
    }

    /**
     * Returns the full size clip art image for a given image name.
     *
     * @param name the name of the image to be loaded (which can be looked up via
     *            {@link #getClipartNames()})
     * @return the clip art image
     * @throws IOException if the image cannot be loaded
     */
    public static BufferedImage getClipartImage(String name) throws IOException {
        InputStream is = GraphicGenerator.class.getResourceAsStream(
                "/images/clipart/big/" + name);
        try {
            return ImageIO.read(is);
        } finally {
            Closeables.close(is, true /* swallowIOException */);
        }
    }

    /**
     * Returns the names of available clip art images which can be obtained by passing the
     * name to {@link #getClipartIcon(String)} or
     * {@link GraphicGenerator#getClipartImage(String)}
     *
     * @return an iterator for the available image names
     */
    public static Iterator<String> getResourcesNames(String pathPrefix, String filenameExtension) {
        List<String> names = new ArrayList<String>(80);
        try {
            ZipFile zipFile = null;
            ProtectionDomain protectionDomain = GraphicGenerator.class.getProtectionDomain();
            URL url = protectionDomain.getCodeSource().getLocation();
            if (url != null) {
                File file = SdkUtils.urlToFile(url);
                zipFile = new JarFile(file);
            } else {
                Enumeration<URL> en =
                        GraphicGenerator.class.getClassLoader().getResources(pathPrefix);
                if (en.hasMoreElements()) {
                    url = en.nextElement();
                    URLConnection urlConnection = url.openConnection();
                    if (urlConnection instanceof JarURLConnection) {
                        JarURLConnection urlConn = (JarURLConnection)(urlConnection);
                        zipFile = urlConn.getJarFile();
                    } else if ("file".equals(url.getProtocol())) { //$NON-NLS-1$
                        File directory = new File(url.getPath());
                        return Lists.newArrayList(directory.list()).iterator();
                    }
                }
            }
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry zipEntry = enumeration.nextElement();
                String name = zipEntry.getName();
                if (!name.startsWith(pathPrefix) || !name.endsWith(filenameExtension)) { //$NON-NLS-1$
                    continue;
                }

                int lastSlash = name.lastIndexOf('/');
                if (lastSlash != -1) {
                    name = name.substring(lastSlash + 1);
                }
                names.add(name);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

        return names.iterator();
    }
}
