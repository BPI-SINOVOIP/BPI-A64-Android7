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
package com.android.tools.chartlib;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Icon;

import gnu.trove.TIntObjectHashMap;

/**
 * A component to display a TimelineData object. It locks the timeline object to prevent
 * modifications to it while it's begin rendered, but objects of this class should not be accessed
 * from different threads.
 */
public final class TimelineComponent extends AnimatedComponent
        implements ActionListener, HierarchyListener {

    private static final Color TEXT_COLOR = new Color(128, 128, 128);

    private static final int LEFT_MARGIN = 120;

    private static final int RIGHT_MARGIN = 200;

    private static final int TOP_MARGIN = 10;

    private static final int BOTTOM_MARGIN = 30;

    private static final int FPS = 40;

    /**
     * The number of pixels a second in the timeline takes on the screen.
     */
    private static final float X_SCALE = 20;

    private final float mBufferTime;

    @NonNull
    private final TimelineData mData;

    @NonNull
    private final EventData mEvents;

    private final float mInitialMax;

    private final float mAbsoluteMax;

    private final float mInitialMarkerSeparation;

    private String[] mStreamNames;

    private Color[] mStreamColors;

    private Map<Integer, Style> mStyles;

    private boolean mFirstFrame;

    /**
     * The current maximum range in y-axis units.
     */
    private float mCurrentMax;

    /**
     * Marker separation in y-axis units.
     */
    private float mMarkerSeparation;

    /**
     * The current alpha of markers at even positions. When there are not enough/too many markers,
     * the markers at even positions are faded in/out respectively. This tracks the animated alpha
     * of such markers.
     */
    private float mEvenMarkersAlpha;

    /**
     * The current value in pixels where the x-axis is drawn.
     */
    private int mBottom;

    /**
     * The current value in pixels where the right hand side y-axis is drawn.
     */
    private int mRight;

    /**
     * The current scale from y-axis values to pixels.
     */
    private float mYScale;

    /**
     * The current time value at the right edge of the timeline in seconds.
     */
    private float mEndTime;

    /**
     * The current time value at the left edge of the timeline in seconds.
     */
    private float mBeginTime;

    /**
     * How to render each event type.
     */
    private TIntObjectHashMap<EventInfo> mEventsInfo;

    /**
     * The units of the y-axis values.
     */
    private String mUnits;

    /**
     * The number of available local samples.
     */
    private int mSize;

    /**
     * The times at which the samples occurred.
     */
    private float[] mTimes;

    /**
     * The times at which the samples occurred.
     */
    private int[] mTypes;

    /**
     * The render values of the samples depending on the layout mode, as in mValues[stream][sample]
     */
    private final float[][] mValues;

    /**
     * The last values of the samples for each stream
     */
    private final float[] mCurrent;

    /**
     * The number of events to render.
     */
    private int mEventsSize;

    /**
     * The start time of each event.
     */
    private float[] mEventStart;

    /**
     * The end time of each event, if NaN then the event did not end.
     */
    private float[] mEventEnd;

    /**
     * The type of each event.
     */
    private int[] mEventTypes;

    /**
     * The animated angle of an event in progress.
     */
    private float mEventProgressStart;

    /**
     * The direction of the event animation.
     */
    private float mEventProgressDir = 1.0f;

    /**
     * The current state for all in-progress events.
     */
    private float mEventProgress;

    /**
     * Creates a timeline component that renders the given timeline data. It will animate the
     * timeline data by showing the value at the current time on the right y-axis of the graph.
     *
     * @param data                    the data to be displayed.
     * @param bufferTime              the time, in seconds, to lag behind the given {@code data}.
     * @param initialMax              the initial maximum value for the y-axis.
     * @param absoluteMax             the absolute maximum value for the y-axis.
     * @param initialMarkerSeparation the initial separations for the markers on the y-axis.
     */
    public TimelineComponent(
            @NonNull TimelineData data,
            @NonNull EventData events,
            float bufferTime,
            float initialMax,
            float absoluteMax,
            float initialMarkerSeparation) {
        super(FPS);
        mData = data;
        mEvents = events;
        mBufferTime = bufferTime;
        mInitialMax = initialMax;
        mAbsoluteMax = absoluteMax;
        mInitialMarkerSeparation = initialMarkerSeparation;
        int streams = mData.getStreamCount();
        addHierarchyListener(this);
        mStreamNames = new String[streams];
        mStreamColors = new Color[streams];
        mValues = new float[streams][];
        mCurrent = new float[streams];
        mSize = 0;
        for (int i = 0; i < streams; i++) {
            mStreamNames[i] = "Stream " + i;
            mStreamColors[i] = Color.BLACK;
        }
        mStyles = new HashMap<Integer, Style>();
        mUnits = "";
        mEventsInfo = new TIntObjectHashMap<EventInfo>();
        setOpaque(true);
        reset();
    }

    public void configureStream(int stream, String name, Color color) {
        mStreamNames[stream] = name;
        mStreamColors[stream] = color;
    }

    public void configureEvent(int type, int stream, Icon icon, Color color,
            Color progress, boolean range) {
        mEventsInfo.put(type, new EventInfo(type, stream, icon, color, progress, range));
    }

    public void configureType(int type, Style style) {
        mStyles.put(type, style);
    }

    public void configureUnits(String units) {
        mUnits = units;
    }

    public void reset() {
        mCurrentMax = mInitialMax;
        mMarkerSeparation = mInitialMarkerSeparation;
        mEvenMarkersAlpha = 1.0f;
        mFirstFrame = true;
    }

    @Override
    protected void draw(Graphics2D g2d) {

        Dimension dim = getSize();

        mBottom = dim.height - BOTTOM_MARGIN;
        mRight = dim.width - RIGHT_MARGIN;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setFont(DEFAULT_FONT);
        g2d.setClip(0, 0, dim.width, dim.height);
        g2d.setColor(getBackground());
        g2d.fillRect(0, 0, dim.width, dim.height);
        g2d.setClip(LEFT_MARGIN, TOP_MARGIN, mRight - LEFT_MARGIN, mBottom - TOP_MARGIN);
        drawTimelineData(g2d);
        drawEvents(g2d);
        g2d.setClip(0, 0, dim.width, dim.height);
        drawLabels(g2d);
        drawTimeMarkers(g2d);
        drawMarkers(g2d);
        drawGuides(g2d);

        mFirstFrame = false;
    }

    @Override
    protected void debugDraw(Graphics2D g2d) {
        int drawn = 0;
        g2d.setFont(DEFAULT_FONT.deriveFont(5.0f));
        for (int i = 0; i < mSize; ++i) {
            if (mTimes[i] > mBeginTime && mTimes[i] < mEndTime) {
                for (int j = 0; j < mValues.length; ++j) {
                    int x = (int) timeToX(mTimes[i]);
                    int y = (int) valueToY(mValues[j][i]);
                    g2d.setColor(new Color((17 * mTypes[i]) % 255, (121 * mTypes[i]) % 255,
                                                (71 * mTypes[i]) % 255));
                    g2d.drawLine(x, y - 2, x, y + 2);
                    g2d.drawLine(x - 2, y, x + 2, y);
                    g2d.setColor(TEXT_COLOR);
                }
                drawn++;
            }
        }

        addDebugInfo("Drawn samples: %d", drawn);
    }

    private void drawTimelineData(Graphics2D g2d) {
        mYScale = (mBottom - TOP_MARGIN) / mCurrentMax;
        if (mSize > 1) {
            int from = 0;
            // Optimize to not render too many samples since they get clipped.
            while (from < mSize - 1 && mTimes[from + 1] < mBeginTime) {
                from++;
            }
            int to = from;
            while (to + 1 < mSize && mTimes[to] <= mEndTime) {
                to++;
            }
            if (from == to) {
                return;
            }
            int drawnSegments = 0;
            for (int j = mValues.length - 1; j >= 0; j--) {
                Path2D.Float path = new Path2D.Float();
                path.moveTo(timeToX(mTimes[from]), valueToY(0.0f));
                for (int i = from; i <= to; i++) {
                    float val = mValues[j][i];
                    path.lineTo(timeToX(mTimes[i]), valueToY(Math.min(val, mAbsoluteMax)));
                }
                path.lineTo(timeToX(mTimes[to]), valueToY(0.0f));
                g2d.setColor(mStreamColors[j]);
                g2d.fill(path);

                if (!mStyles.isEmpty()) {
                    path = new Path2D.Float();
                    Stroke current = g2d.getStroke();
                    float step = 3.0f;
                    float x0 = timeToX(mTimes[from]);
                    float y0 = valueToY(mValues[j][from]);
                    g2d.setColor(mStreamColors[j].darker());
                    Stroke stroke = null;
                    float strokeScale = Float.NaN;
                    for (int i = from + 1; i <= to; i++) {
                        float x1 = timeToX(mTimes[i]);
                        float y1 = valueToY(mValues[j][i]);
                        Style style = mStyles.get(mTypes[i]);
                        if (style != null && style != Style.NONE) {
                            BasicStroke str = new BasicStroke(1.0f);
                            float scale = 0;
                            if (style == Style.DASHED) {
                                float distance = (float) Point2D.distance(x0, y0, x1, y1);
                                float delta = mTimes[i] * X_SCALE;
                                scale = distance / (x1 - x0);
                                str = new BasicStroke(1.0f, BasicStroke.CAP_ROUND,
                                        BasicStroke.JOIN_ROUND, 0.0f, new float[]{step * scale},
                                        (delta * scale) % (step * scale * 2));
                            }
                            if (scale != strokeScale) {
                                if (stroke != null) {
                                    g2d.setStroke(stroke);
                                    g2d.draw(path);
                                    path.reset();
                                    drawnSegments++;
                                }
                                strokeScale = scale;
                                stroke = str;
                                path.moveTo(x0, y0);
                            }
                            path.lineTo(x1, y1);
                        }
                        x0 = x1;
                        y0 = y1;
                    }
                    if (stroke != null) {
                        g2d.setStroke(stroke);
                        g2d.draw(path);
                        drawnSegments++;
                    }
                    g2d.setStroke(current);
                }
            }
            addDebugInfo("Drawn segments: %d", drawnSegments);
        }
        addDebugInfo("Total samples: %d", mSize);
    }

    private float interpolate(int stream, int sample, float time) {
        int prev = sample > 0 ? sample - 1 : 0;
        int next = sample < mSize ? sample : mSize - 1;
        float a = mValues[stream][prev];
        float b = mValues[stream][next];
        float delta = mTimes[next] - mTimes[prev];
        float ratio = delta != 0 ? (time - mTimes[prev]) / delta : 1.0f;
        return (b - a) * ratio + a;
    }

    private void drawEvents(Graphics2D g2d) {

        if (mSize > 0) {
            int drawnEvents = 0;
            AffineTransform tx = g2d.getTransform();
            Stroke stroke = g2d.getStroke();
            int s = 0;
            int e = 0;
            while (e < mEventsSize) {
                if (s < mSize && mTimes[s] < mEventStart[e]) {
                    s++;
                } else if (Float.isNaN(mEventEnd[e])
                        || mEventEnd[e] > mBeginTime && mEventEnd[e] > mTimes[0]) {
                    drawnEvents++;
                    EventInfo info = mEventsInfo.get(mEventTypes[e]);
                    float x = timeToX(mEventStart[e]);
                    float y = valueToY(interpolate(info.stream, s, mEventStart[e]));
                    AffineTransform dt = new AffineTransform(tx);
                    dt.translate(x, y);
                    g2d.setTransform(dt);
                    info.icon.paintIcon(this, g2d, -info.icon.getIconWidth() / 2,
                            -info.icon.getIconHeight() - 5);
                    g2d.setTransform(tx);

                    g2d.setStroke(
                            new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D.Float p = new Path2D.Float();
                    boolean closed = !Float.isNaN(mEventEnd[e]);
                    if (info.range) {
                        p.moveTo(x, mBottom);
                        p.lineTo(x, y);
                        float endTime = Float.isNaN(mEventEnd[e]) ? mEndTime : mEventEnd[e];
                        int i = s;
                        for (; i < mSize && mTimes[i] < endTime; i++) {
                            float val = mValues[info.stream][i];
                            p.lineTo(timeToX(mTimes[i]), valueToY(val));
                        }
                        p.lineTo(timeToX(endTime), valueToY(interpolate(info.stream, i, endTime)));
                        p.lineTo(timeToX(closed ? mEventEnd[e] : endTime), valueToY(0));
                        if (info.color != null) {
                            g2d.setColor(info.color);
                            g2d.fill(p);
                        }
                        g2d.setColor(info.progress);
                        g2d.draw(p);
                    } else {
                        p.moveTo(x, y - 2.0f);
                        p.lineTo(x, y + 2.0f);
                        g2d.setColor(info.progress);
                        g2d.draw(p);
                    }
                    if (!closed) {
                        g2d.setColor(info.progress);
                        // Draw in progress marker
                        float end = 360 * mEventProgress;
                        float start = mEventProgressStart;
                        if (mEventProgressDir < 0.0f) {
                            start += end;
                            end = 360 - end;
                        }
                        g2d.draw(new Arc2D.Float(
                                x + info.icon.getIconWidth() / 2 + 3,
                                y - info.icon.getIconHeight() - 3,
                                6, 6,
                                start, end, Arc2D.OPEN));

                    }
                    e++;
                } else {
                    e++;
                }
            }
            g2d.setStroke(stroke);
            addDebugInfo("Drawn events: %d", drawnEvents);
        }
    }

    private float valueToY(float val) {
        return mBottom - val * mYScale;
    }

    private float timeToX(float time) {
        return LEFT_MARGIN + (time - mBeginTime) * X_SCALE;
    }

    private void drawLabels(Graphics2D g2d) {
        g2d.setFont(DEFAULT_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        for (int i = 0; i < mStreamNames.length && mSize > 0; i++) {
            g2d.setColor(mStreamColors[i]);
            int y = TOP_MARGIN + 15 + (mStreamNames.length - i - 1) * 20;
            g2d.fillRect(mRight + 20, y, 15, 15);
            g2d.setColor(TEXT_COLOR);
            g2d.drawString(
                    String.format("%s [%.2f %s]", mStreamNames[i], mCurrent[i], mUnits),
                    mRight + 40,
                    y + 7 + metrics.getAscent() * .5f);
        }
    }

    private void drawTimeMarkers(Graphics2D g2d) {
        g2d.setFont(DEFAULT_FONT);
        g2d.setColor(TEXT_COLOR);
        FontMetrics metrics = g2d.getFontMetrics();
        float offset = metrics.stringWidth("000") * 0.5f;
        Path2D.Float lines = new Path2D.Float();
        for (int sec = Math.max((int) Math.ceil(mBeginTime), 0); sec < mEndTime; sec++) {
            float x = timeToX(sec);
            boolean big = sec % 5 == 0;
            if (big) {
                String text = formatTime(sec);
                g2d.drawString(text, x - metrics.stringWidth(text) + offset,
                        mBottom + metrics.getAscent() + 5);
            }
            lines.moveTo(x, mBottom);
            lines.lineTo(x, mBottom + (big ? 5 : 2));
        }
        g2d.draw(lines);
    }

    @VisibleForTesting
    static String formatTime(int seconds) {
        int[] factors = {60, seconds};
        String[] suffix = {"m", "h"};
        String ret = seconds % 60 + "s";
        int t = seconds / 60;
        for (int i = 0; i < suffix.length && t > 0; i++) {
            ret = t % factors[i] + suffix[i] + " " + ret;
            t /= factors[i];
        }
        return ret;
    }

    private void drawMarkers(Graphics2D g2d) {
        if (mYScale <= 0) {
            return;
        }

        int markers = (int) (mCurrentMax / mMarkerSeparation);
        float markerPosition = LEFT_MARGIN - 10;
        for (int i = 0; i < markers + 1; i++) {
            float markerValue = (i + 1) * mMarkerSeparation;
            int y = (int) valueToY(markerValue);
            // Too close to the top
            if (mCurrentMax - markerValue < mMarkerSeparation * 0.5f) {
                markerValue = mCurrentMax;
                //noinspection AssignmentToForLoopParameter
                i = markers;
                y = TOP_MARGIN;
            }
            if (i < markers && i % 2 == 0 && mEvenMarkersAlpha < 1.0f) {
                g2d.setColor(
                        new Color(TEXT_COLOR.getColorSpace(), TEXT_COLOR.getColorComponents(null),
                                mEvenMarkersAlpha));
            } else {
                g2d.setColor(TEXT_COLOR);
            }
            g2d.drawLine(LEFT_MARGIN - 2, y, LEFT_MARGIN, y);

            FontMetrics metrics = getFontMetrics(DEFAULT_FONT);
            String marker = String.format("%.2f %s", markerValue, mUnits);
            g2d.drawString(marker, markerPosition - metrics.stringWidth(marker),
                    y + metrics.getAscent() * 0.5f);
        }
    }

    private void drawGuides(Graphics2D g2d) {
        g2d.setColor(TEXT_COLOR);
        g2d.drawLine(LEFT_MARGIN - 10, mBottom, mRight + 10, mBottom);
        if (mYScale > 0) {
            g2d.drawLine(LEFT_MARGIN, mBottom, LEFT_MARGIN, TOP_MARGIN);
            g2d.drawLine(mRight, mBottom, mRight, TOP_MARGIN);
        }
    }

    @Override
    protected void updateData() {
        long start;
        synchronized (mData) {
            start = mData.getStartTime();
            mSize = mData.size();
            assert mData.getStreamCount() == mValues.length;
            if (mTimes == null || mTimes.length < mSize) {
                int alloc = Math.max(mSize, mTimes == null ? 64 : mTimes.length * 2);
                mTimes = new float[alloc];
                mTypes = new int[alloc];
                for (int j = 0; j < mData.getStreamCount(); ++j) {
                    mValues[j] = new float[alloc];
                }
            }
            for (int i = 0; i < mSize; ++i) {
                TimelineData.Sample sample = mData.get(i);
                mTimes[i] = sample.time;
                mTypes[i] = sample.type;
                float value = 0.0f;
                for (int j = 0; j < mData.getStreamCount(); ++j) {
                    value += sample.values[j];
                    mValues[j][i] = value;
                }
            }
            for (int j = 0; j < mData.getStreamCount(); ++j) {
                mCurrent[j] = mSize > 0 ? mData.get(mSize - 1).values[j] : 0.0f;
            }

            // Calculate begin and end times in seconds.
            mEndTime = mData.getEndTime() - mBufferTime;
            mBeginTime = mEndTime - (mRight - LEFT_MARGIN) / X_SCALE;
            // Animate the current maximum towards the real one.
            float cappedMax = Math.min(mData.getMaxTotal(), mAbsoluteMax);
            if (cappedMax > mCurrentMax) {
                mCurrentMax = lerp(mCurrentMax, cappedMax, mFirstFrame ? 1.f : .95f);
            }

            // Animate the fade in/out of markers.
            FontMetrics metrics = getFontMetrics(DEFAULT_FONT);
            int ascent = metrics.getAscent();
            float distance = mMarkerSeparation * mYScale;
            float evenMarkersTarget = 1.0f;
            if (distance < ascent * 2) { // Too many markers
                if (mEvenMarkersAlpha < 0.1f) {
                    mMarkerSeparation *= 2;
                    mEvenMarkersAlpha = 1.0f;
                } else {
                    evenMarkersTarget = 0.0f;
                }
            } else if (distance > ascent * 5) { // Not enough
                if (mEvenMarkersAlpha > 0.9f) {
                    mMarkerSeparation /= 2;
                    mEvenMarkersAlpha = 0.0f;
                }
            }
            mEvenMarkersAlpha = lerp(mEvenMarkersAlpha, evenMarkersTarget, 0.999f);
        }
        synchronized (mEvents) {
            mEventsSize = mEvents.size();
            if (mEventStart == null || mEventStart.length < mEventsSize) {
                int alloc = Math.max(mEventsSize, mEventStart == null ? 64 : mEventStart.length * 2);
                mEventStart = new float[alloc];
                mEventEnd = new float[alloc];
                mEventTypes = new int[alloc];

            }
            for (int i = 0; i < mEventsSize; i++) {
                EventData.Event event = mEvents.get(i);
                mEventStart[i] = (event.from - start) / 1000.0f;
                mEventEnd[i] = event.to == -1 ? Float.NaN : (event.to - start) / 1000.0f;
                mEventTypes[i] = event.type;
            }

            // Animate events in progress
            if (mEventProgress > 0.95f) {
                mEventProgressDir = -mEventProgressDir;
                mEventProgress = 0.0f;
            }
            mEventProgressStart = (mEventProgressStart + mFrameLength * 200.0f) % 360.0f;
            mEventProgress = lerp(mEventProgress, 1.0f, .99f);
        }
    }

    public enum Style {
        NONE,
        SOLID,
        DASHED
    }

    private static class EventInfo {

        public final int type;

        public final int stream;

        public final Icon icon;

        public final Color color;

        public final Color progress;

        public final boolean range;

        private EventInfo(int type, int stream, Icon icon, Color color,
                Color progress, boolean range) {
            this.type = type;
            this.stream = stream;
            this.icon = icon;
            this.color = color;
            this.progress = progress;
            this.range = range;
        }
    }
}
