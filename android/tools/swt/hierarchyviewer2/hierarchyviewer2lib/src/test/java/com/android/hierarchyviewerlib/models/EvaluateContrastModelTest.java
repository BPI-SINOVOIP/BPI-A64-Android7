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

package com.android.hierarchyviewerlib.models;

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.models.EvaluateContrastModel.ContrastResult;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

import junit.framework.TestCase;

public class EvaluateContrastModelTest extends TestCase {

    private static ImageLoader sImageLoader;
    private static final int ARGB_BLACK = 0xFF000000;
    private static final int ARGB_WHITE = 0XFFFFFFFF;
    private static final int ARGB_DARK_GREEN = 0xFF0D6F57;
    private static final int ARGB_LIGHT_GREEN = 0xFF1DE9B6;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        sImageLoader = ImageLoader.getLoader(EvaluateContrastModelTest.class);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        sImageLoader.dispose();
    }

    private static int argbToRgb(int argb) {
        return 0xFFFFFF & argb;
    }

    public void testFailsAllCases() {
        Image allBlack = sImageLoader.loadImage("all_black.png", Display.getDefault());
        Rectangle bounds = allBlack.getBounds();

        // Text color is irrelevant in these tests because it will be calculated.

        // no text size, not bold
        EvaluateContrastModel model = new EvaluateContrastModel(allBlack, null, null, bounds.x,
                bounds.y, bounds.width, bounds.height, false);
        assertEquals(ContrastResult.FAIL, model.getContrastResult());
        assertFalse(model.isIndeterminate());

        // text size is normal, bold
        model = new EvaluateContrastModel(allBlack, null,
                (double) EvaluateContrastModel.NORMAL_TEXT_SZ_PTS, bounds.x, bounds.y, bounds.width,
                bounds.height, true);
        assertEquals(ContrastResult.FAIL, model.getContrastResult());

        // text size is normal for bold, bold
        model = new EvaluateContrastModel(allBlack, null,
                (double) EvaluateContrastModel.NORMAL_TEXT_BOLD_SZ_PTS, bounds.x, bounds.y,
                bounds.width, bounds.height, true);
        assertEquals(ContrastResult.FAIL, model.getContrastResult());

        // text size is large, not bold
        model = new EvaluateContrastModel(allBlack, null,
                (double) EvaluateContrastModel.NORMAL_TEXT_SZ_PTS + 1, bounds.x, bounds.y,
                bounds.width, bounds.height, false);
        assertEquals(ContrastResult.FAIL, model.getContrastResult());
    }

    public void testPassesAllCases() {
        Image[] images = {
                sImageLoader.loadImage("black_on_white.png", Display.getDefault()),
                sImageLoader.loadImage("white_on_black.png", Display.getDefault()),
        };
        int[] textColors = {ARGB_BLACK, ARGB_WHITE};
        Image image;
        Rectangle bounds;

        // Text color is irrelevant in these tests because it will be calculated.
        for (int i = 0; i < images.length; ++i) {
            image = images[i];
            bounds = image.getBounds();

            // no text size, not bold
            EvaluateContrastModel model = new EvaluateContrastModel(image, null, null, bounds.x,
                    bounds.y, bounds.width, bounds.height, false);
            assertEquals(ContrastResult.PASS, model.getContrastResult());
            assertFalse(model.isIndeterminate());

            // text size is normal, bold
            model = new EvaluateContrastModel(image, null,
                    (double) EvaluateContrastModel.NORMAL_TEXT_SZ_PTS, bounds.x, bounds.y,
                    bounds.width, bounds.height, true);
            assertEquals(ContrastResult.PASS, model.getContrastResult());

            // text size is normal for bold, bold
            model = new EvaluateContrastModel(image, null,
                    (double) EvaluateContrastModel.NORMAL_TEXT_BOLD_SZ_PTS, bounds.x, bounds.y,
                    bounds.width, bounds.height, true);
            assertEquals(ContrastResult.PASS, model.getContrastResult());

            // text size is large, not bold
            model = new EvaluateContrastModel(image, null,
                    (double) EvaluateContrastModel.NORMAL_TEXT_SZ_PTS + 1, bounds.x, bounds.y,
                    bounds.width, bounds.height, false);
            assertEquals(ContrastResult.PASS, model.getContrastResult());
        }
    }

    public void testIndeterminateFailsNormalAndPassesLarge() {
        Image greens = sImageLoader.loadImage("dark_on_light_greens.png", Display.getDefault());
        Rectangle bounds = greens.getBounds();

        // Not providing a text size is the main cause for an indeterminate result.
        // Text color is irrelevant because it will be calculated.

        // no text size, not bold
        EvaluateContrastModel model = new EvaluateContrastModel(greens, null, null, bounds.x,
                bounds.y, bounds.width, bounds.height, true);
        assertEquals(ContrastResult.INDETERMINATE, model.getContrastResult());
        assertTrue(model.isIndeterminate());

        // no text size, bold
        model = new EvaluateContrastModel(greens, null, null, bounds.x,
                bounds.y, bounds.width, bounds.height, true);
        assertEquals(ContrastResult.INDETERMINATE, model.getContrastResult());
        assertTrue(model.isIndeterminate());

        // text size normal, not bold
        model = new EvaluateContrastModel(greens, null,
                (double) EvaluateContrastModel.NORMAL_TEXT_SZ_PTS, bounds.x, bounds.y,
                bounds.width, bounds.height, false);
        assertEquals(ContrastResult.FAIL, model.getContrastResult());

        // text size normal, bold
        model = new EvaluateContrastModel(greens, null,
                (double) EvaluateContrastModel.NORMAL_TEXT_SZ_PTS, bounds.x, bounds.y,
                bounds.width, bounds.height, true);
        assertEquals(ContrastResult.PASS, model.getContrastResult());

        // text size normal for bold, not bold
        model = new EvaluateContrastModel(greens, null,
                (double) EvaluateContrastModel.NORMAL_TEXT_BOLD_SZ_PTS, bounds.x, bounds.y,
                bounds.width, bounds.height, false);
        assertEquals(ContrastResult.FAIL, model.getContrastResult());

        // text size normal for bold, bold
        model = new EvaluateContrastModel(greens, null,
                (double) EvaluateContrastModel.NORMAL_TEXT_BOLD_SZ_PTS, bounds.x, bounds.y,
                bounds.width, bounds.height, true);
        assertEquals(ContrastResult.PASS, model.getContrastResult());

        // text size large, not bold
        model = new EvaluateContrastModel(greens, null,
                (double) EvaluateContrastModel.NORMAL_TEXT_SZ_PTS + 1, bounds.x, bounds.y,
                bounds.width, bounds.height, true);
        assertEquals(ContrastResult.PASS, model.getContrastResult());

        // text size large, bold
        model = new EvaluateContrastModel(greens, null,
                (double) EvaluateContrastModel.NORMAL_TEXT_SZ_PTS + 1, bounds.x, bounds.y,
                bounds.width, bounds.height, true);
        assertEquals(ContrastResult.PASS, model.getContrastResult());
    }

    public void testGetBackgroundColor() {
        Image allBlack = sImageLoader.loadImage("all_black.png", Display.getDefault());
        Rectangle bounds = allBlack.getBounds();
        EvaluateContrastModel model = new EvaluateContrastModel(allBlack, null, null, bounds.x,
                bounds.y, bounds.width, bounds.height, false);
        assertEquals(argbToRgb(ARGB_BLACK), model.getBackgroundColor());

        Image blackOnWhite = sImageLoader.loadImage("black_on_white.png", Display.getDefault());
        bounds = blackOnWhite.getBounds();
        model = new EvaluateContrastModel(blackOnWhite, null, null, bounds.x, bounds.y,
                bounds.width, bounds.height, false);
        assertEquals(argbToRgb(ARGB_WHITE), model.getBackgroundColor());

        Image whiteOnBlack = sImageLoader.loadImage("white_on_black.png", Display.getDefault());
        bounds = whiteOnBlack.getBounds();
        model = new EvaluateContrastModel(whiteOnBlack, null, null, bounds.x, bounds.y,
                bounds.width, bounds.height, false);
        assertEquals(argbToRgb(ARGB_BLACK), model.getBackgroundColor());

        Image greens = sImageLoader.loadImage("dark_on_light_greens.png", Display.getDefault());
        bounds = greens.getBounds();
        model = new EvaluateContrastModel(greens, null, null, bounds.x, bounds.y, bounds.width,
                bounds.height, true);
        assertEquals(argbToRgb(ARGB_LIGHT_GREEN), model.getBackgroundColor());
    }

    public void testGetTextColor() {
        Image allBlack = sImageLoader.loadImage("all_black.png", Display.getDefault());
        Rectangle bounds = allBlack.getBounds();
        EvaluateContrastModel model = new EvaluateContrastModel(allBlack, null, null, bounds.x,
                bounds.y, bounds.width, bounds.height, false);
        assertEquals(argbToRgb(ARGB_BLACK), model.getTextColor());
        model = new EvaluateContrastModel(allBlack, ARGB_BLACK, null, bounds.x,
                bounds.y, bounds.width, bounds.height, false);
        assertEquals(ARGB_BLACK, model.getTextColor());

        Image blackOnWhite = sImageLoader.loadImage("black_on_white.png", Display.getDefault());
        bounds = blackOnWhite.getBounds();
        model = new EvaluateContrastModel(blackOnWhite, null, null, bounds.x, bounds.y,
                bounds.width, bounds.height, false);
        assertEquals(argbToRgb(ARGB_BLACK), model.getTextColor());

        Image whiteOnBlack = sImageLoader.loadImage("white_on_black.png", Display.getDefault());
        bounds = whiteOnBlack.getBounds();
        model = new EvaluateContrastModel(whiteOnBlack, null, null, bounds.x, bounds.y,
                bounds.width, bounds.height, false);
        assertEquals(argbToRgb(ARGB_WHITE), model.getTextColor());

        Image greens = sImageLoader.loadImage("dark_on_light_greens.png", Display.getDefault());
        bounds = greens.getBounds();
        model = new EvaluateContrastModel(greens, null, null, bounds.x,
                bounds.y, bounds.width, bounds.height, true);
        assertEquals(argbToRgb(ARGB_DARK_GREEN), model.getTextColor());
    }
}
