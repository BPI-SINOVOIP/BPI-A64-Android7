/*
 * Copyright (C) 2012 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.deskclock.timer;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.deskclock.R;


public class TimerView extends LinearLayout {

    private TextView mHoursTens, mHoursOnes;
    private TextView mMinutesTens, mMinutesOnes;
    private TextView mSeconds;
    private final Typeface mAndroidClockMonoThin;
    private Typeface mOriginalHoursTypeface;
    private Typeface mOriginalMinutesTypeface;
    private final int mWhiteColor, mGrayColor;

    @SuppressWarnings("unused")
    public TimerView(Context context) {
        this(context, null);
    }

    public TimerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mAndroidClockMonoThin =
                Typeface.createFromAsset(context.getAssets(), "fonts/AndroidClockMono-Thin.ttf");

        final Resources resources = context.getResources();
        mWhiteColor = resources.getColor(R.color.clock_white);
        mGrayColor = resources.getColor(R.color.clock_gray);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHoursTens = (TextView) findViewById(R.id.hours_tens);

        mHoursOnes = (TextView) findViewById(R.id.hours_ones);
        mOriginalHoursTypeface = mHoursOnes.getTypeface();

        mMinutesTens = (TextView) findViewById(R.id.minutes_tens);
        addStartPadding(mMinutesTens);

        mMinutesOnes = (TextView) findViewById(R.id.minutes_ones);
        mOriginalMinutesTypeface = mMinutesOnes.getTypeface();

        mSeconds = (TextView) findViewById(R.id.seconds);
        addStartPadding(mSeconds);
    }

    /**
     * Measure the text and add a start padding to the view
     * @param textView view to measure and onb to which add start padding
     */
    private void addStartPadding(TextView textView) {
        final float gapPadding = 0.45f;
        // allDigits will contain ten digits: "0123456789" in the default locale
        String allDigits = String.format("%010d", 123456789);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textView.getTextSize());
        paint.setTypeface(textView.getTypeface());

        float widths[] = new float[allDigits.length()];
        int ll = paint.getTextWidths(allDigits, widths);
        int largest = 0;
        for (int ii = 1; ii < ll; ii++) {
            if (widths[ii] > widths[largest]) {
                largest = ii;
            }
        }
        // Add left padding to the view - Note: layout inherits LTR
        textView.setPadding((int) (gapPadding * widths[largest]), 0, 0, 0);
    }

    public void setTime(int hoursTensDigit, int hoursOnesDigit, int minutesTensDigit,
                        int minutesOnesDigit, int seconds) {
        if (hoursTensDigit == -1) {
            mHoursTens.setText("-");
            mHoursTens.setTypeface(mAndroidClockMonoThin);
            mHoursTens.setTextColor(mGrayColor);
        } else {
            mHoursTens.setText(String.valueOf(hoursTensDigit));
            mHoursTens.setTypeface(mOriginalHoursTypeface);
            mHoursTens.setTextColor(mWhiteColor);
        }

        if (hoursOnesDigit == -1) {
            mHoursOnes.setText("-");
            mHoursOnes.setTypeface(mAndroidClockMonoThin);
            mHoursOnes.setTextColor(mGrayColor);
        } else {
            mHoursOnes.setText(String.valueOf(hoursOnesDigit));
            mHoursOnes.setTypeface(mOriginalHoursTypeface);
            mHoursOnes.setTextColor(mWhiteColor);
        }

        if (minutesTensDigit == -1) {
            mMinutesTens.setText("-");
            mMinutesTens.setTypeface(mAndroidClockMonoThin);
            mMinutesTens.setTextColor(mGrayColor);
        } else {
            mMinutesTens.setText(String.valueOf(minutesTensDigit));
            mMinutesTens.setTypeface(mOriginalMinutesTypeface);
            mMinutesTens.setTextColor(mWhiteColor);
        }

        if (minutesOnesDigit == -1) {
            mMinutesOnes.setText("-");
            mMinutesOnes.setTypeface(mAndroidClockMonoThin);
            mMinutesOnes.setTextColor(mGrayColor);
        } else {
            mMinutesOnes.setText(String.valueOf(minutesOnesDigit));
            mMinutesOnes.setTypeface(mOriginalMinutesTypeface);
            mMinutesOnes.setTextColor(mWhiteColor);
        }

        mSeconds.setText(String.format("%02d", seconds));
    }
}
