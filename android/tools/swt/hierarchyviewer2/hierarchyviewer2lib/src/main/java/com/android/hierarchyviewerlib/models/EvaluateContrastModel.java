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

import com.android.annotations.Nullable;
import com.google.common.collect.Lists;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;

import java.awt.Color;
import java.awt.Rectangle;
import java.lang.Math;
import java.lang.Integer;
import java.lang.String;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

/**
 * <p>
 * This class uses the Web Content Accessibility Guidelines (WCAG) 2.0 (http://www.w3.org/TR/WCAG20)
 * to evaluate the contrast ratio between the text and background colors of a view.
 * </p>
 * <p>
 * Given an image of a view and the bounds of where the view is within the image (x, y, width,
 * height), this class will extract the luminance values (measure of brightness) of the entire view
 * to try and determine the text and background color. If known, the constructor accepts text size
 * and text color to provide a more accurate result.
 * </p>
 * <p>
 * The {@link #calculateLuminance(int)} method calculates the luminance value of an {@code int}
 * representation of a {@link Color}. We use two of these values, the text and background
 * luminances, to determine the contrast ratio.</p>
 * </p>
 * <p>
 * "Sufficient contrast" is defined as having a contrast ratio of 4.5:1 in general, except for:
 * <ul>
 * <li>Large text (>= 18 points, or >= 14 points and bold),
 * which can have a contrast ratio of only 3:1.</li>
 * <li>Inactive components or pure decorations.</li>
 * <li>Text that is part of a logo or brand name.</li>
 * </ul>
 */
public class EvaluateContrastModel {

    public enum ContrastResult {
        PASS,
        FAIL,
        INDETERMINATE
    }

    public static final String CONTRAST_RATIO_FORMAT = "%.2f:1";
    public static final double CONTRAST_RATIO_NORMAL_TEXT = 4.5;
    public static final double CONTRAST_RATIO_LARGE_TEXT = 3.0;
    public static final int NORMAL_TEXT_SZ_PTS = 18;
    public static final int NORMAL_TEXT_BOLD_SZ_PTS = 14;

    public static final String NOT_APPLICABLE = "N/A";

    private static final double MAX_RGB_VALUE = 255.0;

    private ImageData mImageData;
    /** The bounds of the view within the image */
    private Rectangle mViewBounds;

    /** Maps an int representation of a {@link Color} to its luminance value. */
    private HashMap<Integer, Double> mLuminanceMap;
    /** Keeps track of how many times a luminance value occurs in this view. */
    private HashMap<Double, Integer> mLuminanceHistogram;
    private final List<Integer> mBackgroundColors;
    private final List<Integer> mForegroundColors;
    private double mBackgroundLuminance;
    private double mForegroundLuminance;

    private double mContrastRatio;
    private Integer mTextColor;
    private Double mTextSize;
    private boolean mIsBold;

    /**
     * <p>
     * Constructs an EvaluateContrastModel to extract and process properties from the image related
     * to contrast and luminance.
     * </p>
     * <p>
     * NOTE: Invoking this constructor performs image processing tasks, which are relatively
     * heavywheight.
     * </p>
     *
     * @param image Screenshot of the view.
     * @param textColor Color of the text. If null, we will estimate the color of the text.
     * @param textSize Size of the text. If null, we may have an indeterminate result where it
     *                 passes only one of the tests.
     * @param x Starting x-coordinate of the view in the image.
     * @param y Starting y-coordinate of the view in the image.
     * @param width The width of the view in the image.
     * @param height The height of the view in the image.
     * @param isBold True if we know the text is bold, false otherwise.
     */
    public EvaluateContrastModel(Image image, @Nullable Integer textColor,
            @Nullable Double textSize, int x, int y, int width, int height, boolean isBold) {
        mImageData = image.getImageData();
        mTextColor = textColor;
        mTextSize = textSize;
        mViewBounds = new Rectangle(x, y, width, height);
        mIsBold = isBold;

        mBackgroundColors = new LinkedList<Integer>();
        mForegroundColors = new LinkedList<Integer>();
        mLuminanceMap = new HashMap<Integer, Double>();
        mLuminanceHistogram = new HashMap<Double, Integer>();

        processSwatch();
    }

    /**
     * Formula derived from http://gmazzocato.altervista.org/colorwheel/algo.php.
     * More information can be found at http://www.w3.org/TR/WCAG20/relative-luminance.xml.
     */
    public static double calculateLuminance(int color) {
        Color colorObj = new Color(color);
        float[] sRGB = new float[4];
        colorObj.getRGBComponents(sRGB);

        final double[] lumRGB = new double[4];
        for (int i = 0; i < sRGB.length; ++i) {
            lumRGB[i] = (sRGB[i] <= 0.03928d) ? sRGB[i] / 12.92d
                    : Math.pow(((sRGB[i] + 0.055d) / 1.055d), 2.4d);
        }

        return 0.2126d * lumRGB[0] + 0.7152d * lumRGB[1] + 0.0722d * lumRGB[2];
    }

    public static double calculateContrastRatio(double lum1, double lum2) {
        if ((lum1 < 0.0d) || (lum2 < 0.0d)) {
            throw new IllegalArgumentException("Luminance values may not be negative.");
        }

        return (Math.max(lum1, lum2) + 0.05d) / (Math.min(lum1, lum2) + 0.05d);
    }

    public static String intToHexString(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
    }

    private void processSwatch() {
        processLuminanceData();
        extractFgBgData();

        double textLuminance = mTextColor == null ? mForegroundLuminance :
                calculateLuminance(calculateTextColor(mTextColor, mBackgroundColors.get(0)));
        // Two-decimal digits of precision for the contrast ratio
        mContrastRatio = Math.round(calculateContrastRatio(
                textLuminance, mBackgroundLuminance) * 100.0d) / 100.0d;
    }

    private void processLuminanceData() {
        for (int x = mViewBounds.x; x < mViewBounds.width; ++x) {
            for (int y = mViewBounds.y; y < mViewBounds.height; ++y) {
                final int color = mImageData.getPixel(x, y);
                final double luminance = calculateLuminance(color);
                if (!mLuminanceMap.containsKey(color)) {
                    mLuminanceMap.put(color, luminance);
                }

                if (!mLuminanceHistogram.containsKey(luminance)) {
                    mLuminanceHistogram.put(luminance, 0);
                }

                mLuminanceHistogram.put(luminance, mLuminanceHistogram.get(luminance) + 1);
            }
        }
    }

    private void extractFgBgData() {
        if (mLuminanceMap.isEmpty()) {
            // An empty luminance map indicates we've encountered a 0px area
            // image. It has no luminance.
            mBackgroundLuminance = mForegroundLuminance = 0;
            mBackgroundColors.add(0);
            mForegroundColors.add(0);
        } else if (mLuminanceMap.size() == 1) {
            // Deal with views that only contain a single color
            mBackgroundLuminance = mForegroundLuminance = mLuminanceHistogram.keySet().iterator()
                    .next();
            final int singleColor = mLuminanceMap.keySet().iterator().next();
            mForegroundColors.add(singleColor);
            mBackgroundColors.add(singleColor);
        } else {
            // Sort all luminance values seen from low to high
            final ArrayList<Entry<Integer, Double>> colorsByLuminance =
                    Lists.newArrayList(mLuminanceMap.entrySet());
            Collections.sort(colorsByLuminance, new Comparator<Entry<Integer, Double>>() {
                @Override
                public int compare(Entry<Integer, Double> lhs, Entry<Integer, Double> rhs) {
                    return Double.compare(lhs.getValue(), rhs.getValue());
                }
            });

            // Sort luminance values seen by frequency in the image
            final ArrayList<Entry<Double, Integer>> luminanceByFrequency =
                    Lists.newArrayList(mLuminanceHistogram.entrySet());
            Collections.sort(luminanceByFrequency, new Comparator<Entry<Double, Integer>>() {
                @Override
                public int compare(Entry<Double, Integer> lhs, Entry<Double, Integer> rhs) {
                    return lhs.getValue() - rhs.getValue();
                }
            });

            // Find the average luminance value within the set of luminances for
            // purposes of splitting luminance values into high-luminance and
            // low-luminance buckets. This is explicitly not a weighted average.
            double luminanceSum = 0;
            for (Entry<Double, Integer> luminanceCount : luminanceByFrequency) {
                luminanceSum += luminanceCount.getKey();
            }

            final double averageLuminance = luminanceSum / luminanceByFrequency.size();

            // Select the highest and lowest luminance values that contribute to
            // most number of pixels in the image -- our background and
            // foreground colors.
            double lowLuminanceContributor = 0.0d;
            for (int i = luminanceByFrequency.size() - 1; i >= 0; --i) {
                final double luminanceValue = luminanceByFrequency.get(i).getKey();
                if (luminanceValue < averageLuminance) {
                    lowLuminanceContributor = luminanceValue;
                    break;
                }
            }

            double highLuminanceContributor = 1.0d;
            for (int i = luminanceByFrequency.size() - 1; i >= 0; --i) {
                final double luminanceValue = luminanceByFrequency.get(i).getKey();
                if (luminanceValue >= averageLuminance) {
                    highLuminanceContributor = luminanceValue;
                    break;
                }
            }

            // Background luminance is that which occurs more frequently
            if (mLuminanceHistogram.get(highLuminanceContributor)
                    > mLuminanceHistogram.get(lowLuminanceContributor)) {
                mBackgroundLuminance = highLuminanceContributor;
                mForegroundLuminance = lowLuminanceContributor;
            } else {
                mBackgroundLuminance = lowLuminanceContributor;
                mForegroundLuminance = highLuminanceContributor;
            }

            // Determine the contributing colors for those luminance values
            // TODO: Optimize (find an alternative to reiterating through whole image)
            for (Entry<Integer, Double> colorLuminance : mLuminanceMap.entrySet()) {
                if (colorLuminance.getValue() == mBackgroundLuminance) {
                    mBackgroundColors.add(colorLuminance.getKey());
                }

                if (colorLuminance.getValue() == mForegroundLuminance) {
                    mForegroundColors.add(colorLuminance.getKey());
                }
            }
        }
    }

    /**
     * Calculates a more accurate text color for how the text in the view appears by using its alpha
     * value to determine how much it needs to be blended into its background color.
     *
     * @param textColor Text color.
     * @param backgroundColor Background color.
     * @return Calculated text color.
     */
    private int calculateTextColor(int textColor, int backgroundColor) {
        Color text = new Color(textColor, true);
        Color background = new Color(backgroundColor, true);

        int alpha = text.getAlpha();
        double alphaPercentage = alpha / MAX_RGB_VALUE;
        double alphaCompliment = 1 - alphaPercentage;

        int red = (int) (alphaPercentage * text.getRed() + alphaCompliment * background.getRed());
        int green = (int) (alphaPercentage * text.getGreen()
                + alphaCompliment * background.getGreen());
        int blue = (int) (alphaPercentage * text.getBlue()
                + alphaCompliment * background.getBlue());

        Color rgb = new Color(red, green, blue, (int) MAX_RGB_VALUE);
        mTextColor = rgb.getRGB();

        return mTextColor;
    }

    public ContrastResult getContrastResult() {
        ContrastResult normalTest = getContrastResultForNormalText();
        ContrastResult largeTest = getContrastResultForLargeText();

        if (normalTest == largeTest) {
            return normalTest;
        } else if (mTextSize == null) {
            return ContrastResult.INDETERMINATE;
        } else if (mTextSize >= NORMAL_TEXT_BOLD_SZ_PTS && mIsBold ||
                mTextSize > NORMAL_TEXT_SZ_PTS) {
            return largeTest;
        } else {
            return normalTest;
        }
    }

    public ContrastResult getContrastResultForLargeText() {
        return mContrastRatio >= CONTRAST_RATIO_LARGE_TEXT ?
                ContrastResult.PASS : ContrastResult.FAIL;
    }

    public ContrastResult getContrastResultForNormalText() {
        if (mIsBold && mTextSize != null && mTextSize >= NORMAL_TEXT_BOLD_SZ_PTS) {
            return getContrastResultForLargeText();
        }
        return mContrastRatio >= CONTRAST_RATIO_NORMAL_TEXT ?
                ContrastResult.PASS : ContrastResult.FAIL;
    }

    public double getContrastRatio() {
        return mContrastRatio;
    }

    public double getBackgroundLuminance() {
        return mBackgroundLuminance;
    }

    public String getTextSize() {
        if (mTextSize == null ){
            return NOT_APPLICABLE;
        }
        return Double.toString(mTextSize);
    }

    public int getTextColor() {
        Integer textColor;

        if (mTextColor != null) {
            textColor = mTextColor;
        } else {
            // assumes that the foreground color is the luminance value that occurs the least
            // frequently; which is also the best estimate we have for text color.
            textColor = mForegroundColors.get(0);
        }

        return textColor.intValue();
    }

    public String getTextColorHex() {
        return intToHexString(getTextColor());
    }

    public int getBackgroundColor() {
        return mBackgroundColors.get(0);
    }

    public String getBackgroundColorHex() {
        return intToHexString(mBackgroundColors.get(0));
    }

    public boolean isIndeterminate() {
        return mTextSize == null && getContrastResult() == ContrastResult.INDETERMINATE;
    }

    public boolean isBold() {
        return mIsBold;
    }

}
