/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.cts;

import android.content.Context;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.cts.util.CTSResult;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.BitmapDrawable;
import android.os.Parcelable;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.BaseSavedState;
import android.view.View.MeasureSpec;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.OnHierarchyChangeListener;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.LayoutAnimationController;
import android.view.animation.RotateAnimation;
import android.view.animation.Transformation;
import android.view.cts.util.XmlUtils;
import android.widget.TextView;

import java.util.ArrayList;

public class ViewGroupTest extends InstrumentationTestCase implements CTSResult{

    private Context mContext;
    private MotionEvent mMotionEvent;
    private int mResultCode;

    private Sync mSync = new Sync();
    private static class Sync {
        boolean mHasNotify;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
    }

    public void testConstructor() {
        new MockViewGroup(mContext);
        new MockViewGroup(mContext, null);
        new MockViewGroup(mContext, null, 0);
    }

    public void testAddFocusables() {
        MockViewGroup vg = new MockViewGroup(mContext);
        vg.setFocusable(true);

        ArrayList<View> list = new ArrayList<View>();
        TextView textView = new TextView(mContext);
        list.add(textView);
        vg.addView(textView);
        vg.addFocusables(list, 0);

        assertEquals(2, list.size());

        list = new ArrayList<View>();
        list.add(textView);
        vg.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        vg.setFocusable(false);
        vg.addFocusables(list, 0);
        assertEquals(1, list.size());
    }

    public void testAddStatesFromChildren() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);
        vg.addView(textView);
        assertFalse(vg.addStatesFromChildren());

        vg.setAddStatesFromChildren(true);
        textView.performClick();
        assertTrue(vg.addStatesFromChildren());
        assertTrue(vg.isDrawableStateChangedCalled);
    }

    public void testAddTouchables() {
        MockViewGroup vg = new MockViewGroup(mContext);
        vg.setFocusable(true);

        ArrayList<View> list = new ArrayList<View>();
        TextView textView = new TextView(mContext);
        textView.setVisibility(View.VISIBLE);
        textView.setClickable(true);
        textView.setEnabled(true);

        list.add(textView);
        vg.addView(textView);
        vg.addTouchables(list);

        assertEquals(2, list.size());

        View v = vg.getChildAt(0);
        assertSame(textView, v);

        v = vg.getChildAt(-1);
        assertNull(v);

        v = vg.getChildAt(1);
        assertNull(v);

        v = vg.getChildAt(100);
        assertNull(v);

        v = vg.getChildAt(-100);
        assertNull(v);
    }

    public void testAddView() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        assertEquals(0, vg.getChildCount());

        vg.addView(textView);
        assertEquals(1, vg.getChildCount());
    }

    public void testAddViewWithParaViewInt() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        assertEquals(0, vg.getChildCount());

        vg.addView(textView, -1);
        assertEquals(1, vg.getChildCount());
    }

    public void testAddViewWithParaViewLayoutPara() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        assertEquals(0, vg.getChildCount());

        vg.addView(textView, new ViewGroup.LayoutParams(100, 200));

        assertEquals(1, vg.getChildCount());
    }

    public void testAddViewWithParaViewIntInt() {
        final int width = 100;
        final int height = 200;
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        assertEquals(0, vg.getChildCount());

        vg.addView(textView, width, height);
        assertEquals(width, textView.getLayoutParams().width);
        assertEquals(height, textView.getLayoutParams().height);

        assertEquals(1, vg.getChildCount());
    }

    public void testAddViewWidthParaViewIntLayoutParam() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        assertEquals(0, vg.getChildCount());

        vg.addView(textView, -1, new ViewGroup.LayoutParams(100, 200));

        assertEquals(1, vg.getChildCount());
    }

    public void testAddViewInLayout() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        assertEquals(0, vg.getChildCount());

        assertTrue(vg.isRequestLayoutCalled);
        vg.isRequestLayoutCalled = false;
        assertTrue(vg.addViewInLayout(textView, -1, new ViewGroup.LayoutParams(100, 200)));
        assertEquals(1, vg.getChildCount());
        // check that calling addViewInLayout() does not trigger a
        // requestLayout() on this ViewGroup
        assertFalse(vg.isRequestLayoutCalled);
    }

    public void testAttachLayoutAnimationParameters() {
        MockViewGroup vg = new MockViewGroup(mContext);
        ViewGroup.LayoutParams param = new ViewGroup.LayoutParams(10, 10);

        vg.attachLayoutAnimationParameters(null, param, 1, 2);
        assertEquals(2, param.layoutAnimationParameters.count);
        assertEquals(1, param.layoutAnimationParameters.index);
    }

    public void testAttachViewToParent() {
        MockViewGroup vg = new MockViewGroup(mContext);
        vg.setFocusable(true);
        assertEquals(0, vg.getChildCount());

        ViewGroup.LayoutParams param = new ViewGroup.LayoutParams(10, 10);

        TextView child = new TextView(mContext);
        child.setFocusable(true);
        vg.attachViewToParent(child, -1, param);
        assertSame(vg, child.getParent());
        assertEquals(1, vg.getChildCount());
        assertSame(child, vg.getChildAt(0));
    }

    public void testAddViewInLayoutWithParamViewIntLayB() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        assertEquals(0, vg.getChildCount());

        assertTrue(vg.isRequestLayoutCalled);
        vg.isRequestLayoutCalled = false;
        assertTrue(vg.addViewInLayout(textView, -1, new ViewGroup.LayoutParams(100, 200), true));

        assertEquals(1, vg.getChildCount());
        // check that calling addViewInLayout() does not trigger a
        // requestLayout() on this ViewGroup
        assertFalse(vg.isRequestLayoutCalled);
    }

    public void testBringChildToFront() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView1 = new TextView(mContext);
        TextView textView2 = new TextView(mContext);

        assertEquals(0, vg.getChildCount());

        vg.addView(textView1);
        vg.addView(textView2);
        assertEquals(2, vg.getChildCount());

        vg.bringChildToFront(textView1);
        assertEquals(vg, textView1.getParent());
        assertEquals(2, vg.getChildCount());
        assertNotNull(vg.getChildAt(0));
        assertSame(textView2, vg.getChildAt(0));

        vg.bringChildToFront(textView2);
        assertEquals(vg, textView2.getParent());
        assertEquals(2, vg.getChildCount());
        assertNotNull(vg.getChildAt(0));
        assertSame(textView1, vg.getChildAt(0));
    }

    public void testCanAnimate() {
        MockViewGroup vg = new MockViewGroup(mContext);

        assertFalse(vg.canAnimate());

        RotateAnimation animation = new RotateAnimation(0.1f, 0.1f);
        LayoutAnimationController la = new LayoutAnimationController(animation);
        vg.setLayoutAnimation(la);
        assertTrue(vg.canAnimate());
    }

    public void testCheckLayoutParams() {
        MockViewGroup view = new MockViewGroup(mContext);
        assertFalse(view.checkLayoutParams(null));

        assertTrue(view.checkLayoutParams(new ViewGroup.LayoutParams(100, 200)));
    }

    public void testChildDrawableStateChanged() {
        MockViewGroup vg = new MockViewGroup(mContext);
        vg.setAddStatesFromChildren(true);

        vg.childDrawableStateChanged(null);
        assertTrue(vg.isRefreshDrawableStateCalled);
    }

    public void testCleanupLayoutState() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        assertTrue(textView.isLayoutRequested());

        vg.cleanupLayoutState(textView);
        assertFalse(textView.isLayoutRequested());
    }

    public void testClearChildFocus() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        vg.addView(textView);
        vg.requestChildFocus(textView, null);

        View focusedView = vg.getFocusedChild();
        assertSame(textView, focusedView);

        vg.clearChildFocus(textView);
        assertNull(vg.getFocusedChild());
    }

    public void testClearDisappearingChildren() {

        Canvas canvas = new Canvas();
        MockViewGroup vg = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);
        child.setAnimation(new MockAnimation());
        vg.addView(child);
        assertEquals(1, vg.getChildCount());

        assertNotNull(child.getAnimation());
        vg.dispatchDraw(canvas);
        assertEquals(1, vg.drawChildCalledTime);

        child.setAnimation(new MockAnimation());
        vg.removeAllViewsInLayout();

        vg.drawChildCalledTime = 0;
        vg.dispatchDraw(canvas);
        assertEquals(1, vg.drawChildCalledTime);

        child.setAnimation(new MockAnimation());
        vg.clearDisappearingChildren();

        vg.drawChildCalledTime = 0;
        vg.dispatchDraw(canvas);
        assertEquals(0, vg.drawChildCalledTime);
    }

    public void testClearFocus() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);

        vg.addView(textView);
        vg.requestChildFocus(textView, null);
        vg.clearFocus();
        assertTrue(textView.isClearFocusCalled);
    }

    public void testDetachAllViewsFromParent() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        vg.addView(textView);
        assertEquals(1, vg.getChildCount());
        assertSame(vg, textView.getParent());
        vg.detachAllViewsFromParent();
        assertEquals(0, vg.getChildCount());
        assertNull(textView.getParent());
    }

    public void testDetachViewFromParent() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        vg.addView(textView);
        assertEquals(1, vg.getChildCount());

        vg.detachViewFromParent(0);

        assertEquals(0, vg.getChildCount());
        assertNull(textView.getParent());
    }

    public void testDetachViewFromParentWithParamView() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        vg.addView(textView);
        assertEquals(1, vg.getChildCount());
        assertSame(vg, textView.getParent());

        vg.detachViewFromParent(textView);

        assertEquals(0, vg.getChildCount());
        assertNull(vg.getParent());
    }

    public void testDetachViewsFromParent() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView1 = new TextView(mContext);
        TextView textView2 = new TextView(mContext);
        TextView textView3 = new TextView(mContext);

        vg.addView(textView1);
        vg.addView(textView2);
        vg.addView(textView3);
        assertEquals(3, vg.getChildCount());

        vg.detachViewsFromParent(0, 2);

        assertEquals(1, vg.getChildCount());
        assertNull(textView1.getParent());
        assertNull(textView2.getParent());
    }

    public void testDispatchDraw() {
        MockViewGroup vg = new MockViewGroup(mContext);
        Canvas canvas = new Canvas();

        vg.draw(canvas);
        assertTrue(vg.isDispatchDrawCalled);
        assertSame(canvas, vg.canvas);
    }

    @SuppressWarnings("unchecked")
    public void testDispatchFreezeSelfOnly() {
        MockViewGroup vg = new MockViewGroup(mContext);
        vg.setId(1);
        vg.setSaveEnabled(true);

        SparseArray container = new SparseArray();
        assertEquals(0, container.size());
        vg.dispatchFreezeSelfOnly(container);
        assertEquals(1, container.size());
    }

    public void testDispatchKeyEvent() {
        MockViewGroup vg = new MockViewGroup(mContext);
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        assertFalse(vg.dispatchKeyEvent(event));

        MockTextView textView = new MockTextView(mContext);
        vg.addView(textView);
        vg.requestChildFocus(textView, null);
        textView.layout(1, 1, 100, 100);

        assertTrue(vg.dispatchKeyEvent(event));
    }

    @SuppressWarnings("unchecked")
    public void testDispatchSaveInstanceState() {
        MockViewGroup vg = new MockViewGroup(mContext);
        vg.setId(2);
        vg.setSaveEnabled(true);
        MockTextView textView = new MockTextView(mContext);
        textView.setSaveEnabled(true);
        textView.setId(1);
        vg.addView(textView);

        SparseArray array = new SparseArray();
        vg.dispatchSaveInstanceState(array);

        assertTrue(array.size() > 0);
        assertNotNull(array.get(2));

        array = new SparseArray();
        vg.dispatchRestoreInstanceState(array);
        assertTrue(textView.isDispatchRestoreInstanceStateCalled);
    }

    public void testDispatchSetPressed() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);
        vg.addView(textView);

        vg.dispatchSetPressed(true);
        assertTrue(textView.isPressed());

        vg.dispatchSetPressed(false);
        assertFalse(textView.isPressed());
    }

    public void testDispatchSetSelected() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);
        vg.addView(textView);

        vg.dispatchSetSelected(true);
        assertTrue(textView.isSelected());

        vg.dispatchSetSelected(false);
        assertFalse(textView.isSelected());
    }

    @SuppressWarnings("unchecked")
    public void testDispatchThawSelfOnly() {
        MockViewGroup vg = new MockViewGroup(mContext);
        vg.setId(1);
        SparseArray array = new SparseArray();
        array.put(1, BaseSavedState.EMPTY_STATE);

        vg.dispatchThawSelfOnly(array);
        assertTrue(vg.isOnRestoreInstanceStateCalled);

    }

    public void testDispatchTouchEvent() {
        MockViewGroup vg = new MockViewGroup(mContext);

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display d = wm.getDefaultDisplay();
        d.getMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        vg.layout(0, 0, screenWidth, screenHeight);
        vg.setLayoutParams(new ViewGroup.LayoutParams(screenWidth, screenHeight));

        MockTextView textView = new MockTextView(mContext);
        mMotionEvent = null;
        textView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mMotionEvent = event;
                return true;
            }
        });

        textView.setVisibility(View.VISIBLE);
        textView.setEnabled(true);

        vg.addView(textView, new LayoutParams(screenWidth, screenHeight));

        vg.requestDisallowInterceptTouchEvent(true);
        MotionEvent me = MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN,
                screenWidth / 2, screenHeight / 2, 0);

        assertFalse(vg.dispatchTouchEvent(me));
        assertNull(mMotionEvent);

        textView.layout(0, 0, screenWidth, screenHeight);
        assertTrue(vg.dispatchTouchEvent(me));
        assertSame(me, mMotionEvent);
    }

    public void testDispatchTrackballEvent() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MotionEvent me = MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 100, 100,
                0);
        assertFalse(vg.dispatchTrackballEvent(me));

        MockTextView textView = new MockTextView(mContext);
        vg.addView(textView);
        textView.layout(1, 1, 100, 100);
        vg.requestChildFocus(textView, null);
        assertTrue(vg.dispatchTrackballEvent(me));
    }

    public void testDispatchUnhandledMove() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);
        assertFalse(vg.dispatchUnhandledMove(textView, View.FOCUS_DOWN));

        vg.addView(textView);
        textView.layout(1, 1, 100, 100);
        vg.requestChildFocus(textView, null);
        assertTrue(vg.dispatchUnhandledMove(textView, View.FOCUS_DOWN));
    }

    public void testDispatchWindowFocusChanged() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);

        vg.addView(textView);
        textView.setPressed(true);
        assertTrue(textView.isPressed());

        vg.dispatchWindowFocusChanged(false);
        assertFalse(textView.isPressed());
    }

    public void testDispatchWindowVisibilityChanged() {
        int expected = 10;
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);

        vg.addView(textView);
        vg.dispatchWindowVisibilityChanged(expected);
        assertEquals(expected, textView.visibility);
    }

    public void testDrawableStateChanged() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);
        textView.setDuplicateParentStateEnabled(true);

        vg.addView(textView);
        vg.setAddStatesFromChildren(false);
        vg.drawableStateChanged();
        assertTrue(textView.mIsRefreshDrawableStateCalled);
    }

    public void testDrawChild() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);
        vg.addView(textView);

        MockCanvas canvas = new MockCanvas();
        textView.setBackgroundDrawable(new BitmapDrawable(Bitmap.createBitmap(100, 100,
                Config.ALPHA_8)));
        assertFalse(vg.drawChild(canvas, textView, 100));
        // test whether child's draw method is called.
        assertTrue(textView.isDrawCalled);
    }

    public void testFindFocus() {
        MockViewGroup vg = new MockViewGroup(mContext);

        assertNull(vg.findFocus());
        vg.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        vg.setFocusable(true);
        vg.setVisibility(View.VISIBLE);
        vg.setFocusableInTouchMode(true);
        assertTrue(vg.requestFocus(1, new Rect()));

        assertSame(vg, vg.findFocus());
    }

    public void testFitSystemWindows() {
        Rect rect = new Rect(1, 1, 100, 100);
        MockViewGroup vg = new MockViewGroup(mContext);
        assertFalse(vg.fitSystemWindows(rect));

        vg = new MockViewGroup(mContext, null, 0);
        MockView mv = new MockView(mContext);
        vg.addView(mv);
        assertTrue(vg.fitSystemWindows(rect));
    }

    static class MockView extends ViewGroup {

        public int mWidthMeasureSpec;
        public int mHeightMeasureSpec;

        public MockView(Context context) {
            super(context);
        }

        @Override
        public void onLayout(boolean changed, int l, int t, int r, int b) {
        }

        @Override
        public boolean fitSystemWindows(Rect insets) {
            return true;
        }

        @Override
        public void onMeasure(int widthMeasureSpec,
                int heightMeasureSpec) {
            mWidthMeasureSpec = widthMeasureSpec;
            mHeightMeasureSpec = heightMeasureSpec;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public void testFocusableViewAvailable() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MockView child = new MockView(mContext);
        vg.addView(child);

        child.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        child.focusableViewAvailable(vg);

        assertTrue(vg.isFocusableViewAvailable);
    }

    public void testFocusSearch() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);
        MockView child = new MockView(mContext);
        vg.addView(child);
        child.addView(textView);
        assertNotNull(child.focusSearch(textView, 1));
        assertSame(textView, child.focusSearch(textView, 1));
    }

    public void testGatherTransparentRegion() {
        Region region = new Region();
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);
        textView.setAnimation(new AlphaAnimation(mContext, null));
        textView.setVisibility(100);
        vg.addView(textView);
        assertEquals(1, vg.getChildCount());

        assertTrue(vg.gatherTransparentRegion(region));
        assertTrue(vg.gatherTransparentRegion(null));
    }

    public void testGenerateDefaultLayoutParams(){
        MockViewGroup vg = new MockViewGroup(mContext);
        LayoutParams lp = vg.generateDefaultLayoutParams();

        assertEquals(LayoutParams.WRAP_CONTENT, lp.width);
        assertEquals(LayoutParams.WRAP_CONTENT, lp.height);
    }

    public void testGenerateLayoutParamsWithParaAttributeSet() throws Exception{
        MockViewGroup vg = new MockViewGroup(mContext);
        XmlResourceParser set = mContext.getResources().getLayout(
                android.view.cts.R.layout.abslistview_layout);
        XmlUtils.beginDocument(set, "ViewGroup_Layout");
        LayoutParams lp = vg.generateLayoutParams(set);
        assertNotNull(lp);
        assertEquals(25, lp.height);
        assertEquals(25, lp.width);
    }

    public void testGenerateLayoutParams() {
        MockViewGroup vg = new MockViewGroup(mContext);
        LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT);
        LayoutParams generatedParams = vg.generateLayoutParams(p);
        assertEquals(generatedParams.getClass(), p.getClass());
        assertEquals(p.width, generatedParams.width);
        assertEquals(p.height, generatedParams.height);
    }

    public void testGetChildDrawingOrder() {
        MockViewGroup vg = new MockViewGroup(mContext);
        assertEquals(1, vg.getChildDrawingOrder(0, 1));
        assertEquals(2, vg.getChildDrawingOrder(0, 2));
    }

    public void testGetChildMeasureSpec() {
        int spec = 1;
        int padding = 1;
        int childDimension = 1;
        assertEquals(MeasureSpec.makeMeasureSpec(childDimension, MeasureSpec.EXACTLY),
                ViewGroup.getChildMeasureSpec(spec, padding, childDimension));
        spec = 4;
        padding = 6;
        childDimension = 9;
        assertEquals(MeasureSpec.makeMeasureSpec(childDimension, MeasureSpec.EXACTLY),
                ViewGroup.getChildMeasureSpec(spec, padding, childDimension));
    }

    public void testGetChildStaticTransformation() {
        MockViewGroup vg = new MockViewGroup(mContext);
        assertFalse(vg.getChildStaticTransformation(null, null));
    }

    public void testGetChildVisibleRect() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);

        textView.layout(1, 1, 100, 100);
        Rect rect = new Rect(1, 1, 50, 50);
        Point p = new Point();
        assertFalse(vg.getChildVisibleRect(textView, rect, p));

        textView.layout(0, 0, 0, 0);
        vg.layout(20, 20, 60, 60);
        rect = new Rect(10, 10, 40, 40);
        p = new Point();
        assertTrue(vg.getChildVisibleRect(textView, rect, p));
    }

    public void testGetDescendantFocusability() {
        MockViewGroup vg = new MockViewGroup(mContext);
        final int FLAG_MASK_FOCUSABILITY = 0x60000;
        assertFalse((vg.getDescendantFocusability() & FLAG_MASK_FOCUSABILITY) == 0);

        vg.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        assertFalse((vg.getDescendantFocusability() & FLAG_MASK_FOCUSABILITY) == 0);
    }

    public void testGetLayoutAnimation() {
        MockViewGroup vg = new MockViewGroup(mContext);

        assertNull(vg.getLayoutAnimation());
        RotateAnimation animation = new RotateAnimation(0.1f, 0.1f);
        LayoutAnimationController la = new LayoutAnimationController(animation);
        vg.setLayoutAnimation(la);
        assertTrue(vg.canAnimate());
        assertSame(la, vg.getLayoutAnimation());
    }

    public void testGetLayoutAnimationListener() {
        MockViewGroup vg = new MockViewGroup(mContext);

        assertNull(vg.getLayoutAnimationListener());

        AnimationListener al = new AnimationListener() {

            @Override
            public void onAnimationEnd(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationStart(Animation animation) {
            }
        };
        vg.setLayoutAnimationListener(al);
        assertSame(al, vg.getLayoutAnimationListener());
    }

    public void testGetPersistentDrawingCache() {
        MockViewGroup vg = new MockViewGroup(mContext);
        final int mPersistentDrawingCache1 = 2;
        final int mPersistentDrawingCache2 = 3;
        assertEquals(mPersistentDrawingCache1, vg.getPersistentDrawingCache());

        vg.setPersistentDrawingCache(mPersistentDrawingCache2);
        assertEquals(mPersistentDrawingCache2, vg.getPersistentDrawingCache());
    }

    public void testHasFocus() {
        MockViewGroup vg = new MockViewGroup(mContext);
        assertFalse(vg.hasFocus());

        TextView textView = new TextView(mContext);

        vg.addView(textView);
        vg.requestChildFocus(textView, null);

        assertTrue(vg.hasFocus());
    }

    public void testHasFocusable() {
        MockViewGroup vg = new MockViewGroup(mContext);
        assertFalse(vg.hasFocusable());

        vg.setVisibility(View.VISIBLE);
        vg.setFocusable(true);
        assertTrue(vg.hasFocusable());
    }

    public void testIndexOfChild() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        assertEquals(-1, vg.indexOfChild(textView));

        vg.addView(textView);
        assertEquals(0, vg.indexOfChild(textView));
    }

    private void setupActivity(String action) {

        Intent intent = new Intent(getInstrumentation().getTargetContext(),
                ViewGroupCtsActivity.class);
        intent.setAction(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getInstrumentation().getTargetContext().startActivity(intent);
    }

    public void testInvalidateChild() {
        ViewGroupCtsActivity.setResult(this);
        setupActivity(ViewGroupCtsActivity.ACTION_INVALIDATE_CHILD);
        waitForResult();
        assertEquals(CTSResult.RESULT_OK, mResultCode);
    }

    private void waitForResult() {
        synchronized (mSync) {
            while(!mSync.mHasNotify) {
                try {
                    mSync.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    public void testIsAlwaysDrawnWithCacheEnabled() {
        MockViewGroup vg = new MockViewGroup(mContext);

        assertTrue(vg.isAlwaysDrawnWithCacheEnabled());

        vg.setAlwaysDrawnWithCacheEnabled(false);
        assertFalse(vg.isAlwaysDrawnWithCacheEnabled());
        vg.setAlwaysDrawnWithCacheEnabled(true);
        assertTrue(vg.isAlwaysDrawnWithCacheEnabled());
    }

    public void testIsAnimationCacheEnabled() {
        MockViewGroup vg = new MockViewGroup(mContext);

        assertTrue(vg.isAnimationCacheEnabled());

        vg.setAnimationCacheEnabled(false);
        assertFalse(vg.isAnimationCacheEnabled());
        vg.setAnimationCacheEnabled(true);
        assertTrue(vg.isAnimationCacheEnabled());
    }

    public void testIsChildrenDrawnWithCacheEnabled() {
        MockViewGroup vg = new MockViewGroup(mContext);

        assertFalse(vg.isChildrenDrawnWithCacheEnabled());

        vg.setChildrenDrawnWithCacheEnabled(true);
        assertTrue(vg.isChildrenDrawnWithCacheEnabled());
    }

    public void testMeasureChild() {
        final int width = 100;
        final int height = 200;
        MockViewGroup vg = new MockViewGroup(mContext);
        MockView child = new MockView(mContext);
        child.setLayoutParams(new LayoutParams(width, height));
        child.forceLayout();
        vg.addView(child);

        final int parentWidthMeasureSpec = 1;
        final int parentHeightMeasureSpec = 2;
        vg.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec);
        assertEquals(ViewGroup.getChildMeasureSpec(parentWidthMeasureSpec, 0, width),
                child.mWidthMeasureSpec);
        assertEquals(ViewGroup.getChildMeasureSpec(parentHeightMeasureSpec, 0, height),
                child.mHeightMeasureSpec);
    }

    public void testMeasureChildren() {
        final int widthMeasureSpec = 100;
        final int heightMeasureSpec = 200;
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView1 = new MockTextView(mContext);

        vg.addView(textView1);
        vg.measureChildCalledTime = 0;
        vg.measureChildren(widthMeasureSpec, heightMeasureSpec);
        assertEquals(1, vg.measureChildCalledTime);

        MockTextView textView2 = new MockTextView(mContext);
        textView2.setVisibility(View.GONE);
        vg.addView(textView2);

        vg.measureChildCalledTime = 0;
        vg.measureChildren(widthMeasureSpec, heightMeasureSpec);
        assertEquals(1, vg.measureChildCalledTime);
    }

    public void testMeasureChildWithMargins() {
        final int width = 10;
        final int height = 20;
        final int parentWidthMeasureSpec = 1;
        final int widthUsed = 2;
        final int parentHeightMeasureSpec = 3;
        final int heightUsed = 4;
        MockViewGroup vg = new MockViewGroup(mContext);
        MockView child = new MockView(mContext);

        vg.addView(child);
        child.setLayoutParams(new ViewGroup.LayoutParams(width, height));
        try {
            vg.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed,
                    parentHeightMeasureSpec, heightUsed);
            fail("measureChildWithMargins should throw out class cast exception");
        } catch (RuntimeException e) {
        }
        child.setLayoutParams(new ViewGroup.MarginLayoutParams(width, height));

        vg.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec,
                heightUsed);
        assertEquals(ViewGroup.getChildMeasureSpec(parentWidthMeasureSpec, parentHeightMeasureSpec,
                width), child.mWidthMeasureSpec);
        assertEquals(ViewGroup.getChildMeasureSpec(widthUsed, heightUsed, height),
                child.mHeightMeasureSpec);
    }

    public void testOffsetDescendantRectToMyCoords() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);

        try {
            vg.offsetDescendantRectToMyCoords(textView, new Rect());
            fail("offsetDescendantRectToMyCoords should throw out "
                    + "IllegalArgumentException");
        } catch (RuntimeException e) {
            // expected
        }
        vg.addView(textView);
        textView.layout(1, 2, 3, 4);
        Rect rect = new Rect();
        vg.offsetDescendantRectToMyCoords(textView, rect);
        assertEquals(2, rect.bottom);
        assertEquals(2, rect.top);
        assertEquals(1, rect.left);
        assertEquals(1, rect.right);
    }

    public void testOffsetRectIntoDescendantCoords() {
        MockViewGroup vg = new MockViewGroup(mContext);
        vg.layout(10, 20, 30, 40);
        MockTextView textView = new MockTextView(mContext);

        try {
            vg.offsetRectIntoDescendantCoords(textView, new Rect());
            fail("offsetRectIntoDescendantCoords should throw out "
                    + "IllegalArgumentException");
        } catch (RuntimeException e) {
            // expected
        }
        textView.layout(1, 2, 3, 4);
        vg.addView(textView);

        Rect rect = new Rect(5, 6, 7, 8);
        vg.offsetRectIntoDescendantCoords(textView, rect);
        assertEquals(6, rect.bottom);
        assertEquals(4, rect.top);
        assertEquals(4, rect.left);
        assertEquals(6, rect.right);
    }

    public void testOnAnimationEnd() {
        // this function is a call back function it should be tested in ViewGroup#drawChild.
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);
        child.setAnimation(new MockAnimation());
        // this call will make mPrivateFlags |= ANIMATION_STARTED;
        child.onAnimationStart();
        parent.addView(child);

        MockCanvas canvas = new MockCanvas();
        assertFalse(parent.drawChild(canvas, child, 100));
        assertTrue(child.isOnAnimationEndCalled);
    }

    private class MockAnimation extends Animation {
        public MockAnimation() {
            super();
        }

        public MockAnimation(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean getTransformation(long currentTime, Transformation outTransformation) {
           super.getTransformation(currentTime, outTransformation);
           return false;
        }
    }

    public void testOnAnimationStart() {
        // This is a call back method. It should be tested in ViewGroup#drawChild.
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);

        parent.addView(child);

        MockCanvas canvas = new MockCanvas();
        try {
            assertFalse(parent.drawChild(canvas, child, 100));
            assertFalse(child.isOnAnimationStartCalled);
        } catch (Exception e) {
            // expected
        }

        child.setAnimation(new MockAnimation());
        assertFalse(parent.drawChild(canvas, child, 100));
        assertTrue(child.isOnAnimationStartCalled);
    }

    public void testOnCreateDrawableState() {
        MockViewGroup vg = new MockViewGroup(mContext);
        // Call back function. Called in View#getDrawableState()
        int[] data = vg.getDrawableState();
        assertTrue(vg.isOnCreateDrawableStateCalled);
        assertEquals(1, data.length);
    }

    public void testOnInterceptTouchEvent() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MotionEvent me = MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 100, 100,
                0);

        assertFalse(vg.dispatchTouchEvent(me));
        assertTrue(vg.isOnInterceptTouchEventCalled);
    }

    public void testOnLayout() {
        final int left = 1;
        final int top = 2;
        final int right = 100;
        final int bottom = 200;
        MockViewGroup mv = new MockViewGroup(mContext);
        mv.layout(left, top, right, bottom);
        assertEquals(left, mv.left);
        assertEquals(top, mv.top);
        assertEquals(right, mv.right);
        assertEquals(bottom, mv.bottom);
    }

    public void testOnRequestFocusInDescendants() {
        MockViewGroup vg = new MockViewGroup(mContext);

        vg.requestFocus(View.FOCUS_DOWN, new Rect());
        assertTrue(vg.isOnRequestFocusInDescendantsCalled);
    }

    public void testRemoveAllViews() {
        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);
        assertEquals(0, vg.getChildCount());

        vg.addView(textView);
        assertEquals(1, vg.getChildCount());

        vg.removeAllViews();
        assertEquals(0, vg.getChildCount());
        assertNull(textView.getParent());
    }

    public void testRemoveAllViewsInLayout() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);

        assertEquals(0, parent.getChildCount());

        child.addView(textView);
        parent.addView(child);
        assertEquals(1, parent.getChildCount());

        parent.removeAllViewsInLayout();
        assertEquals(0, parent.getChildCount());
        assertEquals(1, child.getChildCount());
        assertNull(child.getParent());
        assertSame(child, textView.getParent());
    }

    public void testRemoveDetachedView() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child1 = new MockViewGroup(mContext);
        MockViewGroup child2 = new MockViewGroup(mContext);
        MockOnHierarchyChangeListener listener = new MockOnHierarchyChangeListener();
        parent.setOnHierarchyChangeListener(listener);
        parent.addView(child1);
        parent.addView(child2);

        parent.removeDetachedView(child1, false);
        assertSame(parent, listener.sParent);
        assertSame(child1, listener.sChild);
    }

    static class MockOnHierarchyChangeListener implements OnHierarchyChangeListener {

        public View sParent;
        public View sChild;

        @Override
        public void onChildViewAdded(View parent, View child) {
        }

        @Override
        public void onChildViewRemoved(View parent, View child) {
            sParent = parent;
            sChild = child;
        }
    }

    public void testRemoveView() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);

        assertEquals(0, parent.getChildCount());

        parent.addView(child);
        assertEquals(1, parent.getChildCount());

        parent.removeView(child);
        assertEquals(0, parent.getChildCount());
        assertNull(child.getParent());
    }

    public void testRemoveViewAt() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);

        assertEquals(0, parent.getChildCount());

        parent.addView(child);
        assertEquals(1, parent.getChildCount());

        try {
            parent.removeViewAt(2);
            fail("should throw out null pointer exception");
        } catch (RuntimeException e) {
            // expected
        }
        assertEquals(1, parent.getChildCount());

        parent.removeViewAt(0);
        assertEquals(0, parent.getChildCount());
        assertNull(child.getParent());
    }

    public void testRemoveViewInLayout() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);

        assertEquals(0, parent.getChildCount());

        parent.addView(child);
        assertEquals(1, parent.getChildCount());

        parent.removeViewInLayout(child);
        assertEquals(0, parent.getChildCount());
        assertNull(child.getParent());
    }

    public void testRemoveViews() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child1 = new MockViewGroup(mContext);
        MockViewGroup child2 = new MockViewGroup(mContext);

        assertEquals(0, parent.getChildCount());
        parent.addView(child1);
        parent.addView(child2);
        assertEquals(2, parent.getChildCount());

        try {
            parent.removeViews(-1, 1); // negative begin
            fail("should fail with IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {}

        try {
            parent.removeViews(0, -1); // negative count
            fail("should fail with IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {}

        try {
            parent.removeViews(1, 2); // past end
            fail("should fail with IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {}
        assertEquals(2, parent.getChildCount()); // child list unmodified

        parent.removeViews(0, 1);
        assertEquals(1, parent.getChildCount());
        assertNull(child1.getParent());

        parent.removeViews(0, 1);
        assertEquals(0, parent.getChildCount());
        assertNull(child2.getParent());
    }

    public void testRemoveViewsInLayout() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child1 = new MockViewGroup(mContext);
        MockViewGroup child2 = new MockViewGroup(mContext);

        assertEquals(0, parent.getChildCount());
        parent.addView(child1);
        parent.addView(child2);
        assertEquals(2, parent.getChildCount());

        try {
            parent.removeViewsInLayout(-1, 1); // negative begin
            fail("should fail with IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {}

        try {
            parent.removeViewsInLayout(0, -1); // negative count
            fail("should fail with IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {}

        try {
            parent.removeViewsInLayout(1, 2); // past end
            fail("should fail with IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {}
        assertEquals(2, parent.getChildCount()); // child list unmodified

        parent.removeViewsInLayout(0, 1);
        assertEquals(1, parent.getChildCount());
        assertNull(child1.getParent());

        parent.removeViewsInLayout(0, 1);
        assertEquals(0, parent.getChildCount());
        assertNull(child2.getParent());
    }

    public void testRequestChildFocus() {
        MockViewGroup vg = new MockViewGroup(mContext);
        TextView textView = new TextView(mContext);

        vg.addView(textView);
        vg.requestChildFocus(textView, null);

        assertNotNull(vg.getFocusedChild());

        vg.clearChildFocus(textView);
        assertNull(vg.getFocusedChild());
    }

    public void testRequestChildRectangleOnScreen() {
        MockViewGroup vg = new MockViewGroup(mContext);
        assertFalse(vg.requestChildRectangleOnScreen(null, null, false));
    }

    public void testRequestDisallowInterceptTouchEvent() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockView child = new MockView(mContext);

        parent.addView(child);
        child.requestDisallowInterceptTouchEvent(true);
        child.requestDisallowInterceptTouchEvent(false);
        assertTrue(parent.isRequestDisallowInterceptTouchEventCalled);
    }

    public void testRequestFocus() {
        MockViewGroup vg = new MockViewGroup(mContext);

        vg.requestFocus(View.FOCUS_DOWN, new Rect());
        assertTrue(vg.isOnRequestFocusInDescendantsCalled);
    }

    public void testRequestTransparentRegion() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockView child1 = new MockView(mContext);
        MockView child2 = new MockView(mContext);
        child1.addView(child2);
        parent.addView(child1);
        child1.requestTransparentRegion(child2);
        assertTrue(parent.isRequestTransparentRegionCalled);
    }

    public void testScheduleLayoutAnimation() {
        MockViewGroup vg = new MockViewGroup(mContext);
        Animation animation = new AlphaAnimation(mContext, null);

        MockLayoutAnimationController al = new MockLayoutAnimationController(animation);
        vg.setLayoutAnimation(al);
        vg.scheduleLayoutAnimation();
        vg.dispatchDraw(new Canvas());
        assertTrue(al.mIsStartCalled);
    }

    static class MockLayoutAnimationController extends LayoutAnimationController {

        public boolean mIsStartCalled;

        public MockLayoutAnimationController(Animation animation) {
            super(animation);
        }

        @Override
        public void start() {
            mIsStartCalled = true;
            super.start();
        }
    }

    public void testSetAddStatesFromChildren() {
        MockViewGroup vg = new MockViewGroup(mContext);
        vg.setAddStatesFromChildren(true);
        assertTrue(vg.addStatesFromChildren());

        vg.setAddStatesFromChildren(false);
        assertFalse(vg.addStatesFromChildren());
    }

    public void testSetChildrenDrawingCacheEnabled() {
        MockViewGroup vg = new MockViewGroup(mContext);

        assertTrue(vg.isAnimationCacheEnabled());

        vg.setAnimationCacheEnabled(false);
        assertFalse(vg.isAnimationCacheEnabled());

        vg.setAnimationCacheEnabled(true);
        assertTrue(vg.isAnimationCacheEnabled());
    }

    public void testSetChildrenDrawnWithCacheEnabled() {
        MockViewGroup vg = new MockViewGroup(mContext);

        assertFalse(vg.isChildrenDrawnWithCacheEnabled());

        vg.setChildrenDrawnWithCacheEnabled(true);
        assertTrue(vg.isChildrenDrawnWithCacheEnabled());

        vg.setChildrenDrawnWithCacheEnabled(false);
        assertFalse(vg.isChildrenDrawnWithCacheEnabled());
    }

    public void testSetClipChildren() {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        MockViewGroup vg = new MockViewGroup(mContext);
        MockTextView textView = new MockTextView(mContext);
        textView.layout(1, 2, 30, 40);
        vg.layout(1, 1, 100, 200);
        vg.setClipChildren(true);

        MockCanvas canvas = new MockCanvas(bitmap);
        vg.drawChild(canvas, textView, 100);
        Rect rect = canvas.getClipBounds();
        assertEquals(0, rect.top);
        assertEquals(100, rect.bottom);
        assertEquals(0, rect.left);
        assertEquals(100, rect.right);
    }

    class MockCanvas extends Canvas {

        public boolean mIsSaveCalled;
        public int mLeft;
        public int mTop;
        public int mRight;
        public int mBottom;

        public MockCanvas() {
            super(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888));
        }

        public MockCanvas(Bitmap bitmap) {
            super(bitmap);
        }

        @Override
        public boolean quickReject(float left, float top, float right,
                float bottom, EdgeType type) {
            super.quickReject(left, top, right, bottom, type);
            return false;
        }

        @Override
        public int save() {
            mIsSaveCalled = true;
            return super.save();
        }

        @Override
        public boolean clipRect(int left, int top, int right, int bottom) {
            mLeft = left;
            mTop = top;
            mRight = right;
            mBottom = bottom;
            return super.clipRect(left, top, right, bottom);
        }
    }

    public void testSetClipToPadding() {
        final int frameLeft = 1;
        final int frameTop = 2;
        final int frameRight = 100;
        final int frameBottom = 200;
        MockViewGroup vg = new MockViewGroup(mContext);
        vg.layout(frameLeft, frameTop, frameRight, frameBottom);

        vg.setClipToPadding(true);
        MockCanvas canvas = new MockCanvas();
        final int paddingLeft = 10;
        final int paddingTop = 20;
        final int paddingRight = 100;
        final int paddingBottom = 200;
        vg.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
        vg.dispatchDraw(canvas);
        //check that the clip region does not contain the padding area
        assertTrue(canvas.mIsSaveCalled);
        assertEquals(10, canvas.mLeft);
        assertEquals(20, canvas.mTop);
        assertEquals(-frameLeft, canvas.mRight);
        assertEquals(-frameTop, canvas.mBottom);

        vg.setClipToPadding(false);
        canvas = new MockCanvas();
        vg.dispatchDraw(canvas);
        assertFalse(canvas.mIsSaveCalled);
        assertEquals(0, canvas.mLeft);
        assertEquals(0, canvas.mTop);
        assertEquals(0, canvas.mRight);
        assertEquals(0, canvas.mBottom);
    }

    public void testSetDescendantFocusability() {
        MockViewGroup vg = new MockViewGroup(mContext);
        final int FLAG_MASK_FOCUSABILITY = 0x60000;
        assertFalse((vg.getDescendantFocusability() & FLAG_MASK_FOCUSABILITY) == 0);

        vg.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        assertFalse((vg.getDescendantFocusability() & FLAG_MASK_FOCUSABILITY) == 0);

        vg.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        assertFalse((vg.getDescendantFocusability() & FLAG_MASK_FOCUSABILITY) == 0);
        assertFalse((vg.getDescendantFocusability() &
                ViewGroup.FOCUS_BEFORE_DESCENDANTS) == 0);
    }

    public void testSetOnHierarchyChangeListener() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);
        MockOnHierarchyChangeListener listener = new MockOnHierarchyChangeListener();
        parent.setOnHierarchyChangeListener(listener);
        parent.addView(child);

        parent.removeDetachedView(child, false);
        assertSame(parent, listener.sParent);
        assertSame(child, listener.sChild);
    }

    public void testSetPadding() {
        final int left = 1;
        final int top = 2;
        final int right = 3;
        final int bottom = 4;

        MockViewGroup vg = new MockViewGroup(mContext);

        assertEquals(0, vg.getPaddingBottom());
        assertEquals(0, vg.getPaddingTop());
        assertEquals(0, vg.getPaddingLeft());
        assertEquals(0, vg.getPaddingRight());
        assertEquals(0, vg.getPaddingStart());
        assertEquals(0, vg.getPaddingEnd());

        vg.setPadding(left, top, right, bottom);

        assertEquals(bottom, vg.getPaddingBottom());
        assertEquals(top, vg.getPaddingTop());
        assertEquals(left, vg.getPaddingLeft());
        assertEquals(right, vg.getPaddingRight());

        assertEquals(left, vg.getPaddingStart());
        assertEquals(right, vg.getPaddingEnd());
        assertEquals(false, vg.isPaddingRelative());

        // force RTL direction
        vg.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        assertEquals(bottom, vg.getPaddingBottom());
        assertEquals(top, vg.getPaddingTop());
        assertEquals(left, vg.getPaddingLeft());
        assertEquals(right, vg.getPaddingRight());

        assertEquals(right, vg.getPaddingStart());
        assertEquals(left, vg.getPaddingEnd());
        assertEquals(false, vg.isPaddingRelative());
    }

    public void testSetPaddingRelative() {
        final int start = 1;
        final int top = 2;
        final int end = 3;
        final int bottom = 4;

        MockViewGroup vg = new MockViewGroup(mContext);

        assertEquals(0, vg.getPaddingBottom());
        assertEquals(0, vg.getPaddingTop());
        assertEquals(0, vg.getPaddingLeft());
        assertEquals(0, vg.getPaddingRight());
        assertEquals(0, vg.getPaddingStart());
        assertEquals(0, vg.getPaddingEnd());

        vg.setPaddingRelative(start, top, end, bottom);

        assertEquals(bottom, vg.getPaddingBottom());
        assertEquals(top, vg.getPaddingTop());
        assertEquals(start, vg.getPaddingLeft());
        assertEquals(end, vg.getPaddingRight());

        assertEquals(start, vg.getPaddingStart());
        assertEquals(end, vg.getPaddingEnd());
        assertEquals(true, vg.isPaddingRelative());

        // force RTL direction after setting relative padding
        vg.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        assertEquals(bottom, vg.getPaddingBottom());
        assertEquals(top, vg.getPaddingTop());
        assertEquals(end, vg.getPaddingLeft());
        assertEquals(start, vg.getPaddingRight());

        assertEquals(start, vg.getPaddingStart());
        assertEquals(end, vg.getPaddingEnd());
        assertEquals(true, vg.isPaddingRelative());

        // force RTL direction before setting relative padding
        vg = new MockViewGroup(mContext);
        vg.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        assertEquals(0, vg.getPaddingBottom());
        assertEquals(0, vg.getPaddingTop());
        assertEquals(0, vg.getPaddingLeft());
        assertEquals(0, vg.getPaddingRight());
        assertEquals(0, vg.getPaddingStart());
        assertEquals(0, vg.getPaddingEnd());

        vg.setPaddingRelative(start, top, end, bottom);

        assertEquals(bottom, vg.getPaddingBottom());
        assertEquals(top, vg.getPaddingTop());
        assertEquals(end, vg.getPaddingLeft());
        assertEquals(start, vg.getPaddingRight());

        assertEquals(start, vg.getPaddingStart());
        assertEquals(end, vg.getPaddingEnd());
        assertEquals(true, vg.isPaddingRelative());
    }

    public void testSetPersistentDrawingCache() {
        MockViewGroup vg = new MockViewGroup(mContext);
        vg.setPersistentDrawingCache(1);
        assertEquals(1 & ViewGroup.PERSISTENT_ALL_CACHES, vg
                .getPersistentDrawingCache());
    }

    public void testShowContextMenuForChild() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);
        parent.addView(child);

        child.showContextMenuForChild(null);
        assertTrue(parent.isShowContextMenuForChildCalled);
    }

    public void testShowContextMenuForChild_WithXYCoords() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);
        parent.addView(child);

        child.showContextMenuForChild(null, 48, 48);
        assertTrue(parent.isShowContextMenuForChildCalledWithXYCoords);
    }

    public void testStartLayoutAnimation() {
        MockViewGroup vg = new MockViewGroup(mContext);
        RotateAnimation animation = new RotateAnimation(0.1f, 0.1f);
        LayoutAnimationController la = new LayoutAnimationController(animation);
        vg.setLayoutAnimation(la);

        vg.layout(1, 1, 100, 100);
        assertFalse(vg.isLayoutRequested());
        vg.startLayoutAnimation();
        assertTrue(vg.isLayoutRequested());
    }

    public void testUpdateViewLayout() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);

        parent.addView(child);
        LayoutParams param = new LayoutParams(100, 200);
        parent.updateViewLayout(child, param);
        assertEquals(param.width, child.getLayoutParams().width);
        assertEquals(param.height, child.getLayoutParams().height);
    }

    public void testDebug() {
        final int EXPECTED = 100;
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);
        parent.addView(child);

        parent.debug(EXPECTED);
        assertEquals(EXPECTED + 1, child.debugDepth);
    }

    public void testDispatchKeyEventPreIme() {
        MockViewGroup vg = new MockViewGroup(mContext);
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        assertFalse(vg.dispatchKeyEventPreIme(event));
        assertFalse(vg.dispatchKeyShortcutEvent(event));
        MockTextView textView = new MockTextView(mContext);

        vg.addView(textView);
        vg.requestChildFocus(textView, null);
        vg.layout(0, 0, 100, 200);
        assertFalse(vg.dispatchKeyEventPreIme(event));
        assertFalse(vg.dispatchKeyShortcutEvent(event));

        vg.requestChildFocus(textView, null);
        textView.layout(0, 0, 50, 50);
        assertTrue(vg.dispatchKeyEventPreIme(event));
        assertTrue(vg.dispatchKeyShortcutEvent(event));

        vg.setStaticTransformationsEnabled(true);
        Canvas canvas = new Canvas();
        vg.drawChild(canvas, textView, 100);
        assertTrue(vg.isGetChildStaticTransformationCalled);
        vg.isGetChildStaticTransformationCalled = false;
        vg.setStaticTransformationsEnabled(false);
        vg.drawChild(canvas, textView, 100);
        assertFalse(vg.isGetChildStaticTransformationCalled);
    }

    public void testStartActionModeForChildRespectsSubclassModeOnPrimary() {
        MockViewGroupSubclass vgParent = new MockViewGroupSubclass(mContext);
        MockViewGroupSubclass vg = new MockViewGroupSubclass(mContext);
        vg.shouldReturnOwnTypelessActionMode = true;
        vgParent.addView(vg);
        MockTextView textView = new MockTextView(mContext);
        vg.addView(textView);

        textView.startActionMode(NO_OP_ACTION_MODE_CALLBACK, ActionMode.TYPE_PRIMARY);

        assertTrue(vg.isStartActionModeForChildTypedCalled);
        assertTrue(vg.isStartActionModeForChildTypelessCalled);
        // Call should not bubble up as we have an intercepting implementation.
        assertFalse(vgParent.isStartActionModeForChildTypedCalled);
    }

    public void testStartActionModeForChildIgnoresSubclassModeOnFloating() {
        MockViewGroupSubclass vgParent = new MockViewGroupSubclass(mContext);
        MockViewGroupSubclass vg = new MockViewGroupSubclass(mContext);
        vg.shouldReturnOwnTypelessActionMode = true;
        vgParent.addView(vg);
        MockTextView textView = new MockTextView(mContext);
        vg.addView(textView);

        textView.startActionMode(NO_OP_ACTION_MODE_CALLBACK, ActionMode.TYPE_FLOATING);

        assertTrue(vg.isStartActionModeForChildTypedCalled);
        assertFalse(vg.isStartActionModeForChildTypelessCalled);
        // Call should bubble up as we have a floating type.
        assertTrue(vgParent.isStartActionModeForChildTypedCalled);
    }

    public void testStartActionModeForChildTypedBubblesUpToParent() {
        MockViewGroupSubclass vgParent = new MockViewGroupSubclass(mContext);
        MockViewGroupSubclass vg = new MockViewGroupSubclass(mContext);
        vgParent.addView(vg);
        MockTextView textView = new MockTextView(mContext);
        vg.addView(textView);

        textView.startActionMode(NO_OP_ACTION_MODE_CALLBACK, ActionMode.TYPE_FLOATING);

        assertTrue(vg.isStartActionModeForChildTypedCalled);
        assertTrue(vgParent.isStartActionModeForChildTypedCalled);
    }

    public void testStartActionModeForChildTypelessBubblesUpToParent() {
        MockViewGroupSubclass vgParent = new MockViewGroupSubclass(mContext);
        MockViewGroupSubclass vg = new MockViewGroupSubclass(mContext);
        vgParent.addView(vg);
        MockTextView textView = new MockTextView(mContext);
        vg.addView(textView);

        textView.startActionMode(NO_OP_ACTION_MODE_CALLBACK);

        assertTrue(vg.isStartActionModeForChildTypedCalled);
        assertTrue(vg.isStartActionModeForChildTypelessCalled);
        assertTrue(vgParent.isStartActionModeForChildTypedCalled);
    }

    public void testTemporaryDetach() {
        // [vgParent]
        //   - [viewParent1]
        //   - [viewParent1]
        //   - [vg]
        //     - [view1]
        //     - [view2]
        MockViewGroupSubclass vgParent = new MockViewGroupSubclass(mContext);
        TemporaryDetachingMockView viewParent1 = new TemporaryDetachingMockView(mContext);
        TemporaryDetachingMockView viewParent2 = new TemporaryDetachingMockView(mContext);
        vgParent.addView(viewParent1);
        vgParent.addView(viewParent2);
        MockViewGroupSubclass vg = new MockViewGroupSubclass(mContext);
        vgParent.addView(vg);
        TemporaryDetachingMockView view1 = new TemporaryDetachingMockView(mContext);
        TemporaryDetachingMockView view2 = new TemporaryDetachingMockView(mContext);
        vg.addView(view1);
        vg.addView(view2);

        // Make sure that no View is temporarity detached in the initial state.
        assertFalse(viewParent1.isTemporarilyDetached());
        assertEquals(0, viewParent1.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnFinishTemporaryDetachCount());
        assertFalse(viewParent2.isTemporarilyDetached());
        assertEquals(0, viewParent2.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnFinishTemporaryDetachCount());
        assertFalse(view1.isTemporarilyDetached());
        assertEquals(0, view1.getDispatchStartTemporaryDetachCount());
        assertEquals(0, view1.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, view1.getOnStartTemporaryDetachCount());
        assertEquals(0, view1.getOnFinishTemporaryDetachCount());
        assertFalse(view2.isTemporarilyDetached());
        assertEquals(0, view2.getDispatchStartTemporaryDetachCount());
        assertEquals(0, view2.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, view2.getOnStartTemporaryDetachCount());
        assertEquals(0, view2.getOnFinishTemporaryDetachCount());

        // [vgParent]
        //   - [viewParent1]
        //   - [viewParent1]
        //   - [vg]           <- dispatchStartTemporaryDetach()
        //     - [view1]
        //     - [view2]
        vg.dispatchStartTemporaryDetach();

        assertFalse(viewParent1.isTemporarilyDetached());
        assertEquals(0, viewParent1.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnFinishTemporaryDetachCount());
        assertFalse(viewParent2.isTemporarilyDetached());
        assertEquals(0, viewParent2.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnFinishTemporaryDetachCount());
        assertTrue(view1.isTemporarilyDetached());
        assertEquals(1, view1.getDispatchStartTemporaryDetachCount());
        assertEquals(0, view1.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, view1.getOnStartTemporaryDetachCount());
        assertEquals(0, view1.getOnFinishTemporaryDetachCount());
        assertTrue(view2.isTemporarilyDetached());
        assertEquals(1, view2.getDispatchStartTemporaryDetachCount());
        assertEquals(0, view2.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, view2.getOnStartTemporaryDetachCount());
        assertEquals(0, view2.getOnFinishTemporaryDetachCount());

        // [vgParent]
        //   - [viewParent1]
        //   - [viewParent1]
        //   - [vg]           <- dispatchFinishTemporaryDetach()
        //     - [view1]
        //     - [view2]
        vg.dispatchFinishTemporaryDetach();

        assertFalse(viewParent1.isTemporarilyDetached());
        assertEquals(0, viewParent1.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnFinishTemporaryDetachCount());
        assertFalse(viewParent2.isTemporarilyDetached());
        assertEquals(0, viewParent2.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnFinishTemporaryDetachCount());
        assertFalse(view1.isTemporarilyDetached());
        assertEquals(1, view1.getDispatchStartTemporaryDetachCount());
        assertEquals(1, view1.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, view1.getOnStartTemporaryDetachCount());
        assertEquals(1, view1.getOnFinishTemporaryDetachCount());
        assertFalse(view2.isTemporarilyDetached());
        assertEquals(1, view2.getDispatchStartTemporaryDetachCount());
        assertEquals(1, view2.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, view2.getOnStartTemporaryDetachCount());
        assertEquals(1, view2.getOnFinishTemporaryDetachCount());

        // [vgParent]         <- dispatchStartTemporaryDetach()
        //   - [viewParent1]
        //   - [viewParent1]
        //   - [vg]
        //     - [view1]
        //     - [view2]
        vgParent.dispatchStartTemporaryDetach();

        assertTrue(viewParent1.isTemporarilyDetached());
        assertEquals(1, viewParent1.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, viewParent1.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnFinishTemporaryDetachCount());
        assertTrue(viewParent2.isTemporarilyDetached());
        assertEquals(1, viewParent2.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, viewParent2.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnFinishTemporaryDetachCount());
        assertTrue(view1.isTemporarilyDetached());
        assertEquals(2, view1.getDispatchStartTemporaryDetachCount());
        assertEquals(1, view1.getDispatchFinishTemporaryDetachCount());
        assertEquals(2, view1.getOnStartTemporaryDetachCount());
        assertEquals(1, view1.getOnFinishTemporaryDetachCount());
        assertTrue(view2.isTemporarilyDetached());
        assertEquals(2, view2.getDispatchStartTemporaryDetachCount());
        assertEquals(1, view2.getDispatchFinishTemporaryDetachCount());
        assertEquals(2, view2.getOnStartTemporaryDetachCount());
        assertEquals(1, view2.getOnFinishTemporaryDetachCount());

        // [vgParent]         <- dispatchFinishTemporaryDetach()
        //   - [viewParent1]
        //   - [viewParent1]
        //   - [vg]
        //     - [view1]
        //     - [view2]
        vgParent.dispatchFinishTemporaryDetach();

        assertFalse(viewParent1.isTemporarilyDetached());
        assertEquals(1, viewParent1.getDispatchStartTemporaryDetachCount());
        assertEquals(1, viewParent1.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, viewParent1.getOnStartTemporaryDetachCount());
        assertEquals(1, viewParent1.getOnFinishTemporaryDetachCount());
        assertFalse(viewParent2.isTemporarilyDetached());
        assertEquals(1, viewParent2.getDispatchStartTemporaryDetachCount());
        assertEquals(1, viewParent2.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, viewParent2.getOnStartTemporaryDetachCount());
        assertEquals(1, viewParent2.getOnFinishTemporaryDetachCount());
        assertFalse(view1.isTemporarilyDetached());
        assertEquals(2, view1.getDispatchStartTemporaryDetachCount());
        assertEquals(2, view1.getDispatchFinishTemporaryDetachCount());
        assertEquals(2, view1.getOnStartTemporaryDetachCount());
        assertEquals(2, view1.getOnFinishTemporaryDetachCount());
        assertFalse(view2.isTemporarilyDetached());
        assertEquals(2, view2.getDispatchStartTemporaryDetachCount());
        assertEquals(2, view2.getDispatchFinishTemporaryDetachCount());
        assertEquals(2, view2.getOnStartTemporaryDetachCount());
        assertEquals(2, view2.getOnFinishTemporaryDetachCount());
    }

    private static final ActionMode.Callback NO_OP_ACTION_MODE_CALLBACK =
            new ActionMode.Callback() {
                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {}

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return false;
                }
            };

    private static final ActionMode NO_OP_ACTION_MODE =
            new ActionMode() {
                @Override
                public void setTitle(CharSequence title) {}

                @Override
                public void setTitle(int resId) {}

                @Override
                public void setSubtitle(CharSequence subtitle) {}

                @Override
                public void setSubtitle(int resId) {}

                @Override
                public void setCustomView(View view) {}

                @Override
                public void invalidate() {}

                @Override
                public void finish() {}

                @Override
                public Menu getMenu() {
                    return null;
                }

                @Override
                public CharSequence getTitle() {
                    return null;
                }

                @Override
                public CharSequence getSubtitle() {
                    return null;
                }

                @Override
                public View getCustomView() {
                    return null;
                }

                @Override
                public MenuInflater getMenuInflater() {
                    return null;
                }
            };

    private static class MockViewGroupSubclass extends ViewGroup {
        boolean isStartActionModeForChildTypedCalled = false;
        boolean isStartActionModeForChildTypelessCalled = false;
        boolean shouldReturnOwnTypelessActionMode = false;

        public MockViewGroupSubclass(Context context) {
            super(context);
        }

        @Override
        public ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback) {
            isStartActionModeForChildTypelessCalled = true;
            if (shouldReturnOwnTypelessActionMode) {
                return NO_OP_ACTION_MODE;
            }
            return super.startActionModeForChild(originalView, callback);
        }

        @Override
        public ActionMode startActionModeForChild(
                View originalView, ActionMode.Callback callback, int type) {
            isStartActionModeForChildTypedCalled = true;
            return super.startActionModeForChild(originalView, callback, type);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            // no-op
        }
    }

    static public int resetRtlPropertiesCount;
    static public int resetResolvedLayoutDirectionCount;
    static public int resetResolvedTextDirectionCount;
    static public int resetResolvedTextAlignmentCount;
    static public int resetResolvedPaddingCount;
    static public int resetResolvedDrawablesCount;


    private static void clearRtlCounters() {
        resetRtlPropertiesCount = 0;
        resetResolvedLayoutDirectionCount = 0;
        resetResolvedTextDirectionCount = 0;
        resetResolvedTextAlignmentCount = 0;
        resetResolvedPaddingCount = 0;
        resetResolvedDrawablesCount = 0;
    }

    public void testResetRtlProperties() {
        clearRtlCounters();

        MockViewGroup vg = new MockViewGroup(mContext);
        MockView2 v1 = new MockView2(mContext);
        MockView2 v2 = new MockView2(mContext);

        MockViewGroup v3 = new MockViewGroup(mContext);
        MockView2 v4 = new MockView2(mContext);

        v3.addView(v4);
        assertEquals(1, resetRtlPropertiesCount);
        assertEquals(1, resetResolvedLayoutDirectionCount);
        assertEquals(1, resetResolvedTextDirectionCount);
        assertEquals(1, resetResolvedTextAlignmentCount);
        assertEquals(1, resetResolvedPaddingCount);
        assertEquals(1, resetResolvedDrawablesCount);

        clearRtlCounters();
        vg.addView(v1);
        vg.addView(v2);
        vg.addView(v3);

        assertEquals(3, resetRtlPropertiesCount); // for v1 / v2 / v3 only
        assertEquals(4, resetResolvedLayoutDirectionCount); // for v1 / v2 / v3 / v4
        assertEquals(4, resetResolvedTextDirectionCount);
        assertEquals(3, resetResolvedTextAlignmentCount); // for v1 / v2 / v3 only
        assertEquals(4, resetResolvedPaddingCount);
        assertEquals(4, resetResolvedDrawablesCount);

        clearRtlCounters();
        vg.resetRtlProperties();
        assertEquals(1, resetRtlPropertiesCount); // for vg only
        assertEquals(5, resetResolvedLayoutDirectionCount); // for all
        assertEquals(5, resetResolvedTextDirectionCount);
        assertEquals(1, resetResolvedTextAlignmentCount); // for vg only as TextAlignment is not inherited (default is Gravity)
        assertEquals(5, resetResolvedPaddingCount);
        assertEquals(5, resetResolvedDrawablesCount);
    }

    static class MockTextView extends TextView {

        public boolean isClearFocusCalled;
        public boolean isDispatchRestoreInstanceStateCalled;
        public int visibility;
        public boolean mIsRefreshDrawableStateCalled;
        public boolean isDrawCalled;

        public MockTextView(Context context) {
            super(context);
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            isDrawCalled = true;
        }

        @Override
        public void clearFocus() {
            isClearFocusCalled = true;
            super.clearFocus();
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return true;
        }

        @Override
        public void dispatchRestoreInstanceState(
                SparseArray<Parcelable> container) {
            isDispatchRestoreInstanceStateCalled = true;
            super.dispatchRestoreInstanceState(container);
        }

        @Override
        public boolean onTrackballEvent(MotionEvent event) {
            return true;
        }

        @Override
        public boolean dispatchUnhandledMove(View focused, int direction) {
            return true;
        }

        @Override
        public void onWindowVisibilityChanged(int visibility) {
            this.visibility = visibility;
            super.onWindowVisibilityChanged(visibility);
        }

        @Override
        public void refreshDrawableState() {
            mIsRefreshDrawableStateCalled = true;
            super.refreshDrawableState();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            super.dispatchTouchEvent(event);
            return true;
        }

        @Override
        public boolean dispatchKeyEventPreIme(KeyEvent event) {
            return true;
        }

        @Override
        public boolean dispatchKeyShortcutEvent(KeyEvent event) {
            return true;
        }
    }

    static class MockViewGroup extends ViewGroup {

        public boolean isRecomputeViewAttributesCalled;
        public boolean isShowContextMenuForChildCalled;
        public boolean isShowContextMenuForChildCalledWithXYCoords;
        public boolean isRefreshDrawableStateCalled;
        public boolean isOnRestoreInstanceStateCalled;
        public boolean isOnCreateDrawableStateCalled;
        public boolean isOnInterceptTouchEventCalled;
        public boolean isOnRequestFocusInDescendantsCalled;
        public boolean isFocusableViewAvailable;
        public boolean isDispatchDrawCalled;
        public boolean isRequestDisallowInterceptTouchEventCalled;
        public boolean isRequestTransparentRegionCalled;
        public boolean isGetChildStaticTransformationCalled;
        public int[] location;
        public int measureChildCalledTime;
        public boolean isOnAnimationEndCalled;
        public boolean isOnAnimationStartCalled;
        public int debugDepth;
        public int drawChildCalledTime;
        public Canvas canvas;
        public boolean isDrawableStateChangedCalled;
        public boolean isRequestLayoutCalled;
        public boolean isOnLayoutCalled;
        public int left;
        public int top;
        public int right;
        public int bottom;

        public MockViewGroup(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        public MockViewGroup(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MockViewGroup(Context context) {
            super(context);
        }

        @Override
        public void onLayout(boolean changed, int l, int t, int r, int b) {
            isOnLayoutCalled = true;
            left = l;
            top = t;
            right = r;
            bottom = b;
        }

        @Override
        public boolean addViewInLayout(View child, int index,
                ViewGroup.LayoutParams params) {
            return super.addViewInLayout(child, index, params);
        }

        @Override
        public boolean addViewInLayout(View child, int index,
                ViewGroup.LayoutParams params, boolean preventRequestLayout) {
            return super.addViewInLayout(child, index, params, preventRequestLayout);
        }

        @Override
        public void attachLayoutAnimationParameters(View child,
                ViewGroup.LayoutParams params, int index, int count) {
            super.attachLayoutAnimationParameters(child, params, index, count);
        }

        @Override
        public void attachViewToParent(View child, int index,
                LayoutParams params) {
            super.attachViewToParent(child, index, params);
        }

        @Override
        public boolean canAnimate() {
            return super.canAnimate();
        }

        @Override
        public boolean checkLayoutParams(LayoutParams p) {
            return super.checkLayoutParams(p);
        }

        @Override
        public void refreshDrawableState() {
            isRefreshDrawableStateCalled = true;
            super.refreshDrawableState();
        }

        @Override
        public void cleanupLayoutState(View child) {
            super.cleanupLayoutState(child);
        }

        @Override
        public void detachAllViewsFromParent() {
            super.detachAllViewsFromParent();
        }

        @Override
        public void detachViewFromParent(int index) {
            super.detachViewFromParent(index);
        }

        @Override
        public void detachViewFromParent(View child) {
            super.detachViewFromParent(child);
        }
        @Override

        public void detachViewsFromParent(int start, int count) {
            super.detachViewsFromParent(start, count);
        }

        @Override
        public void dispatchDraw(Canvas canvas) {
            isDispatchDrawCalled = true;
            super.dispatchDraw(canvas);
            this.canvas = canvas;
        }

        @Override
        public void dispatchFreezeSelfOnly(SparseArray<Parcelable> container) {
            super.dispatchFreezeSelfOnly(container);
        }

        @Override
        public void dispatchRestoreInstanceState(
                SparseArray<Parcelable> container) {
            super.dispatchRestoreInstanceState(container);
        }

        @Override
        public void dispatchSaveInstanceState(
                SparseArray<Parcelable> container) {
            super.dispatchSaveInstanceState(container);
        }

        @Override
        public void dispatchSetPressed(boolean pressed) {
            super.dispatchSetPressed(pressed);
        }

        @Override
        public void dispatchThawSelfOnly(SparseArray<Parcelable> container) {
            super.dispatchThawSelfOnly(container);
        }

        @Override
        public void onRestoreInstanceState(Parcelable state) {
            isOnRestoreInstanceStateCalled = true;
            super.onRestoreInstanceState(state);
        }

        @Override
        public void drawableStateChanged() {
            isDrawableStateChangedCalled = true;
            super.drawableStateChanged();
        }

        @Override
        public boolean drawChild(Canvas canvas, View child, long drawingTime) {
            drawChildCalledTime++;
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        public boolean fitSystemWindows(Rect insets) {
            return super.fitSystemWindows(insets);
        }

        @Override
        public LayoutParams generateDefaultLayoutParams() {
            return super.generateDefaultLayoutParams();
        }

        @Override
        public LayoutParams generateLayoutParams(LayoutParams p) {
            return super.generateLayoutParams(p);
        }

        @Override
        public int getChildDrawingOrder(int childCount, int i) {
            return super.getChildDrawingOrder(childCount, i);
        }

        @Override
        public boolean getChildStaticTransformation(View child,
                Transformation t) {
            isGetChildStaticTransformationCalled = true;
            return super.getChildStaticTransformation(child, t);
        }

        @Override
        public void measureChild(View child, int parentWidthMeasureSpec,
                int parentHeightMeasureSpec) {
            measureChildCalledTime++;
            super.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec);
        }

        @Override
        public void measureChildren(int widthMeasureSpec,
                int heightMeasureSpec) {
            super.measureChildren(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        public void measureChildWithMargins(View child,
                int parentWidthMeasureSpec, int widthUsed,
                int parentHeightMeasureSpec, int heightUsed) {
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed,
                    parentHeightMeasureSpec, heightUsed);
        }

        @Override
        public void onAnimationEnd() {
            isOnAnimationEndCalled = true;
            super.onAnimationEnd();
        }

        @Override
        public void onAnimationStart() {
            super.onAnimationStart();
            isOnAnimationStartCalled = true;
        }

        @Override
        public int[] onCreateDrawableState(int extraSpace) {
            isOnCreateDrawableStateCalled = true;
            return super.onCreateDrawableState(extraSpace);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            isOnInterceptTouchEventCalled = true;
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onRequestFocusInDescendants(int direction,
                Rect previouslyFocusedRect) {
            isOnRequestFocusInDescendantsCalled = true;
            return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
        }

        @Override
        public void recomputeViewAttributes(View child) {
            isRecomputeViewAttributesCalled = true;
            super.recomputeViewAttributes(child);
        }

        @Override
        public void removeDetachedView(View child, boolean animate) {
            super.removeDetachedView(child, animate);
        }

        @Override
        public boolean showContextMenuForChild(View originalView) {
            isShowContextMenuForChildCalled = true;
            return super.showContextMenuForChild(originalView);
        }

        @Override
        public boolean showContextMenuForChild(View originalView, float x, float y) {
            isShowContextMenuForChildCalledWithXYCoords = true;
            return super.showContextMenuForChild(originalView, x, y);
        }

        @Override
        public boolean isInTouchMode() {
            super.isInTouchMode();
            return false;
        }

        @Override
        public void focusableViewAvailable(View v) {
            isFocusableViewAvailable = true;
            super.focusableViewAvailable(v);
        }

        @Override
        public View focusSearch(View focused, int direction) {
            super.focusSearch(focused, direction);
            return focused;
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            isRequestDisallowInterceptTouchEventCalled = true;
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

        @Override
        public void requestTransparentRegion(View child) {
            isRequestTransparentRegionCalled = true;
            super.requestTransparentRegion(child);
        }

        @Override
        public void debug(int depth) {
            debugDepth = depth;
            super.debug(depth);
        }

        @Override
        public void requestLayout() {
            isRequestLayoutCalled = true;
            super.requestLayout();
        }

        @Override
        public void setStaticTransformationsEnabled(boolean enabled) {
            super.setStaticTransformationsEnabled(enabled);
        }

        @Override
        public void resetRtlProperties() {
            super.resetRtlProperties();
            resetRtlPropertiesCount++;
        }

        @Override
        public void resetResolvedLayoutDirection() {
            super.resetResolvedLayoutDirection();
            resetResolvedLayoutDirectionCount++;
        }

        @Override
        public void resetResolvedTextDirection() {
            super.resetResolvedTextDirection();
            resetResolvedTextDirectionCount++;
        }

        @Override
        public void resetResolvedTextAlignment() {
            super.resetResolvedTextAlignment();
            resetResolvedTextAlignmentCount++;
        }

        @Override
        public void resetResolvedPadding() {
            super.resetResolvedPadding();
            resetResolvedPaddingCount++;
        }

        @Override
        protected void resetResolvedDrawables() {
            super.resetResolvedDrawables();
            resetResolvedDrawablesCount++;
        }

        @Override
        public boolean setFrame(int left, int top, int right, int bottom) {
            return super.setFrame(left, top, right, bottom);
        }

        @Override
        public void setChildrenDrawnWithCacheEnabled(boolean enabled) {
            super.setChildrenDrawnWithCacheEnabled(enabled);
        }

        @Override
        public boolean isChildrenDrawnWithCacheEnabled() {
            return super.isChildrenDrawnWithCacheEnabled();
        }
    }

    static class MockView2 extends View {

        public MockView2(Context context) {
            super(context);
        }

        public MockView2(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MockView2(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        public void resetRtlProperties() {
            super.resetRtlProperties();
            resetRtlPropertiesCount++;
        }

        @Override
        public void resetResolvedLayoutDirection() {
            super.resetResolvedLayoutDirection();
            resetResolvedLayoutDirectionCount++;
        }

        @Override
        public void resetResolvedTextDirection() {
            super.resetResolvedTextDirection();
            resetResolvedTextDirectionCount++;
        }

        @Override
        public void resetResolvedTextAlignment() {
            super.resetResolvedTextAlignment();
            resetResolvedTextAlignmentCount++;
        }

        @Override
        public void resetResolvedPadding() {
            super.resetResolvedPadding();
            resetResolvedPaddingCount++;
        }

        @Override
        protected void resetResolvedDrawables() {
            super.resetResolvedDrawables();
            resetResolvedDrawablesCount++;
        }
    }

    static final class TemporaryDetachingMockView extends View {
        private int mDispatchStartTemporaryDetachCount = 0;
        private int mDispatchFinishTemporaryDetachCount = 0;
        private int mOnStartTemporaryDetachCount = 0;
        private int mOnFinishTemporaryDetachCount = 0;

        public TemporaryDetachingMockView(Context context) {
            super(context);
        }

        @Override
        public void dispatchStartTemporaryDetach() {
            super.dispatchStartTemporaryDetach();
            mDispatchStartTemporaryDetachCount += 1;
        }

        @Override
        public void dispatchFinishTemporaryDetach() {
            super.dispatchFinishTemporaryDetach();
            mDispatchFinishTemporaryDetachCount += 1;
        }

        @Override
        public void onStartTemporaryDetach() {
            super.onStartTemporaryDetach();
            mOnStartTemporaryDetachCount += 1;
        }

        @Override
        public void onFinishTemporaryDetach() {
            super.onFinishTemporaryDetach();
            mOnFinishTemporaryDetachCount += 1;
        }

        public int getDispatchStartTemporaryDetachCount() {
            return mDispatchStartTemporaryDetachCount;
        }

        public int getDispatchFinishTemporaryDetachCount() {
            return mDispatchFinishTemporaryDetachCount;
        }

        public int getOnStartTemporaryDetachCount() {
            return mOnStartTemporaryDetachCount;
        }

        public int getOnFinishTemporaryDetachCount() {
            return mOnFinishTemporaryDetachCount;
        }
    }

    @Override
    public void setResult(int resultCode) {
        synchronized (mSync) {
            mSync.mHasNotify = true;
            mSync.notify();
            mResultCode = resultCode;
        }
    }
}
