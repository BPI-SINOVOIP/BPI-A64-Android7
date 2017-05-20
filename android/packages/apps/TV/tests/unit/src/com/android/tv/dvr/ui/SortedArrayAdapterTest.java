/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tv.dvr.ui;

import android.support.test.filters.SmallTest;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.ObjectAdapter;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * Tests for {@link SortedArrayAdapter}.
 */
@SmallTest
public class SortedArrayAdapterTest extends TestCase {

    public static final TestData P1 = TestData.create(1, "one");
    public static final TestData P2 = TestData.create(2, "before");
    public static final TestData P3 = TestData.create(3, "other");
    private TestSortedArrayAdapter mAdapter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAdapter = new TestSortedArrayAdapter();
    }

    public void testContents_empty() {
        assertEmpty();
    }

    public void testAdd_one() {
        mAdapter.add(P1);
        assertNotEmpty();
        assertContentsInOrder(mAdapter, P1);
    }

    public void testAdd_two() {
        mAdapter.add(P1);
        mAdapter.add(P2);
        assertNotEmpty();
        assertContentsInOrder(mAdapter, P2, P1);
    }

    public void testAddAll_two() {
        mAdapter.addAll(Arrays.asList(P1, P2));
        assertNotEmpty();
        assertContentsInOrder(mAdapter, P2, P1);
    }

    public void testRemove() {
        mAdapter.add(P1);
        mAdapter.add(P2);
        assertNotEmpty();
        assertContentsInOrder(mAdapter, P2, P1);
        mAdapter.remove(P3);
        assertContentsInOrder(mAdapter, P2, P1);
        mAdapter.remove(P2);
        assertContentsInOrder(mAdapter, P1);
        mAdapter.remove(P1);
        assertEmpty();
    }

    public void testChange_sorting() {
        TestData p2_changed = TestData.create(2, "z changed");
        mAdapter.add(P1);
        mAdapter.add(P2);
        assertNotEmpty();
        assertContentsInOrder(mAdapter, P2, P1);
        mAdapter.change(p2_changed);
        assertContentsInOrder(mAdapter, P1, p2_changed);
    }

    public void testChange_new() {
        mAdapter.change(P1);
        assertNotEmpty();
        assertContentsInOrder(mAdapter, P1);
    }

    private void assertEmpty() {
        assertEquals("empty", true, mAdapter.isEmpty());
        assertContentsInOrder(mAdapter, EmptyHolder.EMPTY_HOLDER);
    }

    private void assertNotEmpty() {
        assertEquals("empty", false, mAdapter.isEmpty());
    }

    private static void assertContentsInOrder(ObjectAdapter adapter, Object... contents) {
        int ex = contents.length;
        assertEquals("size", ex, adapter.size());
        for (int i = 0; i < ex; i++) {
            assertEquals("element " + 1, contents[i], adapter.get(i));
        }
    }

    private static class TestData {
        @Override
        public String toString() {
            return "TestData[" + mId + "]{" + mText + '}';
        }

        static TestData create(long first, String text) {
            return new TestData(first, text);
        }

        private final long mId;
        private final String mText;

        private TestData(long id, String second) {
            this.mId = id;
            this.mText = second;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestData)) return false;
            TestData that = (TestData) o;
            return mId == that.mId && Objects.equals(mText, that.mText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mId, mText);
        }
    }

    private static class TestSortedArrayAdapter extends SortedArrayAdapter<TestData> {

        private static final Comparator<TestData> TEXT_COMPARATOR = new Comparator<TestData>() {
            @Override
            public int compare(TestData lhs, TestData rhs) {
                return lhs.mText.compareTo(rhs.mText);
            }
        };

        TestSortedArrayAdapter() {
            super(new ClassPresenterSelector(), TEXT_COMPARATOR);
        }

        @Override
        long getId(TestData item) {
            return item.mId;
        }

    }
}
