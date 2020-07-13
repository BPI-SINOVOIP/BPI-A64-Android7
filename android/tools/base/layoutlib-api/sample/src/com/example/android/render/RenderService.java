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

package com.example.android.render;

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.DrawableParams;
import com.android.ide.common.rendering.api.IImageFactory;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.IProjectCallback;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.resources.ScreenOrientation;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;


/**
 * The {@link RenderService} provides rendering service and easy config.
 */
public class RenderService {

    // The following fields are set through the constructor and are required.

    private final IProjectCallback mProjectCallback;
    private final ResourceResolver mResourceResolver;
    private final LayoutLibrary mLayoutLib;
    private final FolderConfiguration mConfig;

    // The following fields are optional or configurable using the various chained
    // setters:

    private int mWidth;
    private int mHeight;
    private int mMinSdkVersion = -1;
    private int mTargetSdkVersion = -1;
    private float mXdpi = -1;
    private float mYdpi = -1;
    private RenderingMode mRenderingMode = RenderingMode.NORMAL;
    private LayoutLog mLogger;
    private Integer mOverrideBgColor;
    private boolean mShowDecorations = true;
    private String mAppLabel;
    private String mAppIconName;
    private IImageFactory mImageFactory;

    /** Use the {@link RenderServiceFactory#create} factory instead */
    RenderService(LayoutLibrary layoutLibrary,
            ResourceResolver resources,
            FolderConfiguration config,
            IProjectCallback projectCallback) {
        mLayoutLib = layoutLibrary;
        mResourceResolver = resources;
        mConfig = config;
        mProjectCallback = projectCallback;
    }

    /**
     * Sets the {@link LayoutLog} to be used during rendering. If none is specified, a
     * silent logger will be used.
     *
     * @param logger the log to be used
     * @return this (such that chains of setters can be stringed together)
     */
    public RenderService setLog(LayoutLog logger) {
        mLogger = logger;
        return this;
    }

    /**
     * Sets the {@link RenderingMode} to be used during rendering. If none is specified,
     * the default is {@link RenderingMode#NORMAL}.
     *
     * @param renderingMode the rendering mode to be used
     * @return this (such that chains of setters can be stringed together)
     */
    public RenderService setRenderingMode(RenderingMode renderingMode) {
        mRenderingMode = renderingMode;
        return this;
    }

    /**
     * Sets the overriding background color to be used, if any. The color should be a
     * bitmask of AARRGGBB. The default is null.
     *
     * @param overrideBgColor the overriding background color to be used in the rendering,
     *            in the form of a AARRGGBB bitmask, or null to use no custom background.
     * @return this (such that chains of setters can be stringed together)
     */
    public RenderService setOverrideBgColor(Integer overrideBgColor) {
        mOverrideBgColor = overrideBgColor;
        return this;
    }

    /**
     * Sets whether the rendering should include decorations such as a system bar, an
     * application bar etc depending on the SDK target and theme. The default is true.
     *
     * @param showDecorations true if the rendering should include system bars etc.
     * @return this (such that chains of setters can be stringed together)
     */
    public RenderService setDecorations(boolean showDecorations) {
        mShowDecorations = showDecorations;
        return this;
    }

    public RenderService setAppInfo(String label, String icon) {
        mAppLabel = label;
        mAppIconName = icon;
        return this;
    }

    public RenderService setMinSdkVersion(int minSdkVersion) {
        mMinSdkVersion = minSdkVersion;
        return this;
    }

    public RenderService setTargetSdkVersion(int targetSdkVersion) {
        mTargetSdkVersion = targetSdkVersion;
        return this;
    }

    public RenderService setExactDeviceDpi(float xdpi, float ydpi) {
        mXdpi = xdpi;
        mYdpi = ydpi;
        return this;
    }

    public RenderService setImageFactory(IImageFactory imageFactory) {
        mImageFactory = imageFactory;
        return this;
    }

    /** Initializes any remaining optional fields after all setters have been called */
    private void finishConfiguration() {
        if (mLogger == null) {
            // Silent logging
            mLogger = new LayoutLog();
        }
    }

    /**
     * Renders the model and returns the result as a {@link RenderSession}.
     * @return the {@link RenderSession} resulting from rendering the current model
     * @throws XmlPullParserException
     * @throws FileNotFoundException
     */
    public RenderSession createRenderSession(String layoutName) throws FileNotFoundException,
            XmlPullParserException {
        finishConfiguration();

        if (mResourceResolver == null) {
            // Abort the rendering if the resources are not found.
            return null;
        }

        // find the layout to run
        ResourceValue value = mResourceResolver.getProjectResource(ResourceType.LAYOUT, layoutName);
        if (value == null || value.getValue() == null) {
            throw new IllegalArgumentException("layout does not exist");
        }

        File layoutFile = new File(value.getValue());

        ILayoutPullParser parser = null;
        parser = new XmlParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new FileInputStream(layoutFile), "UTF-8"); //$NON-NLS-1$

        figureSomeValuesOut();

        SessionParams params = new SessionParams(
                parser,
                mRenderingMode,
                this /* projectKey */,
                mWidth, mHeight,
                mConfig.getDensityQualifier().getValue(),
                mXdpi, mYdpi,
                mResourceResolver,
                mProjectCallback,
                mMinSdkVersion,
                mTargetSdkVersion,
                mLogger);

        // Request margin and baseline information.
        // TODO: Be smarter about setting this; start without it, and on the first request
        // for an extended view info, re-render in the same session, and then set a flag
        // which will cause this to create extended view info each time from then on in the
        // same session
        params.setExtendedViewInfoMode(true);

        if (!mShowDecorations) {
            params.setForceNoDecor();
        } else {
            if (mAppLabel == null) {
                mAppLabel = "Random App";
            }

            params.setAppLabel(mAppLabel);
            params.setAppIcon(mAppIconName); // ok to be null
        }

        params.setConfigScreenSize(mConfig.getScreenSizeQualifier().getValue());

        if (mOverrideBgColor != null) {
            params.setOverrideBgColor(mOverrideBgColor.intValue());
        }

        // set the Image Overlay as the image factory.
        params.setImageFactory(mImageFactory);

        try {
            return mLayoutLib.createSession(params);
        } catch (RuntimeException t) {
            // Exceptions from the bridge
            mLogger.error(null, t.getLocalizedMessage(), t, null);
            throw t;
        }
    }

    private void figureSomeValuesOut() {
        int size1 = mConfig.getScreenDimensionQualifier().getValue1();
        int size2 = mConfig.getScreenDimensionQualifier().getValue2();
        ScreenOrientation orientation = mConfig.getScreenOrientationQualifier().getValue();
        switch (orientation) {
            case LANDSCAPE:
                mWidth = size1 < size2 ? size2 : size1;
                mHeight = size1 < size2 ? size1 : size2;
                break;
            case PORTRAIT:
                mWidth = size1 < size2 ? size1 : size2;
                mHeight = size1 < size2 ? size2 : size1;
                break;
            case SQUARE:
                mWidth = mHeight = size1;
                break;
        }

        if (mMinSdkVersion == -1) {
            mMinSdkVersion = mConfig.getVersionQualifier().getVersion();
        }

        if (mTargetSdkVersion == -1) {
            mTargetSdkVersion = mConfig.getVersionQualifier().getVersion();
        }

        if (mXdpi == -1) {
            mXdpi = mConfig.getDensityQualifier().getValue().getDpiValue();
        }

        if (mYdpi == -1) {
            mYdpi = mConfig.getDensityQualifier().getValue().getDpiValue();
        }
    }

    /**
     * Renders the given resource value (which should refer to a drawable) and returns it
     * as an image
     *
     * @param drawableResourceValue the drawable resource value to be rendered, or null
     * @return the image, or null if something went wrong
     */
    public BufferedImage renderDrawable(ResourceValue drawableResourceValue) {
        if (drawableResourceValue == null) {
            return null;
        }

        finishConfiguration();

        figureSomeValuesOut();

        DrawableParams params = new DrawableParams(drawableResourceValue, this, mWidth, mHeight,
                mConfig.getDensityQualifier().getValue(),
                mXdpi, mYdpi, mResourceResolver, mProjectCallback, mMinSdkVersion,
                mTargetSdkVersion, mLogger);
        params.setForceNoDecor();
        Result result = mLayoutLib.renderDrawable(params);
        if (result != null && result.isSuccess()) {
            Object data = result.getData();
            if (data instanceof BufferedImage) {
                return (BufferedImage) data;
            }
        }

        return null;
    }
}
