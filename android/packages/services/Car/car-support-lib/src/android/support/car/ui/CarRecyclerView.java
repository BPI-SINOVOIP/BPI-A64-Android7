/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.support.car.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Custom {@link RecyclerView} that helps {@link CarLayoutManager} properly fling and paginate.
 *
 * It also has the ability to fade children as they scroll off screen that can be set
 * with {@link #setFadeLastItem(boolean)}.
 */
public class CarRecyclerView extends RecyclerView {
    private static final String PARCEL_CLASS = "android.os.Parcel";
    private static final String SAVED_STATE_CLASS =
            "android.support.v7.widget.RecyclerView.SavedState";
    private boolean mFadeLastItem;
    private Constructor<?> mSavedStateConstructor;
    /**
     * If the user releases the list with a velocity of 0, {@link #fling(int, int)} will not be
     * called. However, we want to make sure that the list still snaps to the next page when this
     * happens.
     */
    private boolean mWasFlingCalledForGesture;

    public CarRecyclerView(Context context) {
        this(context, null);
    }

    public CarRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CarRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setFocusableInTouchMode(false);
        setFocusable(false);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state.getClass().getClassLoader() != getClass().getClassLoader()) {
            if (mSavedStateConstructor == null) {
                mSavedStateConstructor = getSavedStateConstructor();
            }
            // Class loader mismatch, recreate from parcel.
            Parcel obtain = Parcel.obtain();
            state.writeToParcel(obtain, 0);
            try {
                Parcelable newState = (Parcelable) mSavedStateConstructor.newInstance(obtain);
                super.onRestoreInstanceState(newState);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                // Fail loudy here.
                throw new RuntimeException(e);
            }
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Override
    public boolean fling(int velocityX, int velocityY) {
        mWasFlingCalledForGesture = true;
        return ((CarLayoutManager) getLayoutManager()).settleScrollForFling(this, velocityY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // We want the parent to handle all touch events. There's a lot going on there,
        // and there is no reason to overwrite that functionality. If we do, bad things will happen.
        final boolean ret = super.onTouchEvent(e);

        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_UP) {
            if (!mWasFlingCalledForGesture) {
                ((CarLayoutManager) getLayoutManager()).settleScrollForFling(this, 0);
            }
            mWasFlingCalledForGesture = false;
        }

        return ret;
    }

    @Override
    public boolean drawChild(@NonNull Canvas canvas, @NonNull View child, long drawingTime) {
        if (mFadeLastItem) {
            float onScreen = 1f;
            if ((child.getTop() < getBottom() && child.getBottom() > getBottom())) {
                onScreen = ((float) (getBottom() - child.getTop())) / (float) child.getHeight();
            } else if ((child.getTop() < getTop() && child.getBottom() > getTop())) {
                onScreen = ((float) (child.getBottom() - getTop())) / (float) child.getHeight();
            }
            float alpha = 1 - (1 - onScreen) * (1 - onScreen);
            fadeChild(child, alpha);
        }

        return super.drawChild(canvas, child, drawingTime);
    }

    public void setFadeLastItem(boolean fadeLastItem) {
        mFadeLastItem = fadeLastItem;
    }

    public void pageUp() {
        CarLayoutManager lm = (CarLayoutManager) getLayoutManager();
        int pageUpPosition = lm.getPageUpPosition();
        if (pageUpPosition == -1) {
            return;
        }

        smoothScrollToPosition(pageUpPosition);
    }

    public void pageDown() {
        CarLayoutManager lm = (CarLayoutManager) getLayoutManager();
        int pageDownPosition = lm.getPageDownPosition();
        if (pageDownPosition == -1) {
            return;
        }

        smoothScrollToPosition(pageDownPosition);
    }

    /**
     * Sets {@link #mSavedStateConstructor} to private SavedState constructor.
     */
    private Constructor<?> getSavedStateConstructor() {
        Class<?> savedStateClass = null;
        // Find package private subclass RecyclerView$SavedState.
        for (Class<?> c : RecyclerView.class.getDeclaredClasses()) {
            if (c.getCanonicalName().equals(SAVED_STATE_CLASS)) {
                savedStateClass = c;
                break;
            }
        }
        if (savedStateClass == null) {
            throw new RuntimeException("RecyclerView$SavedState not found!");
        }
        // Find constructor that takes a {@link Parcel}.
        for (Constructor<?> c : savedStateClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = c.getParameterTypes();
            if (parameterTypes.length == 1
                    && parameterTypes[0].getCanonicalName().equals(PARCEL_CLASS)) {
                mSavedStateConstructor = c;
                mSavedStateConstructor.setAccessible(true);
                break;
            }
        }
        if (mSavedStateConstructor == null) {
            throw new RuntimeException("RecyclerView$SavedState constructor not found!");
        }
        return mSavedStateConstructor;
    }

    /**
     * Fades child by alpha. If child is a {@link android.view.ViewGroup} then it will recursively fade its
     * children instead.
     */
    private void fadeChild(@NonNull View child, float alpha) {
        if (child instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) child;
            for (int i = 0; i < vg.getChildCount(); i++) {
                fadeChild(vg.getChildAt(i), alpha);
            }
        } else {
            child.setAlpha(alpha);
        }
    }
}
