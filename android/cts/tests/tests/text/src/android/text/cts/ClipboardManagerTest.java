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

package android.text.cts;


import android.content.Context;
import android.test.InstrumentationTestCase;
import android.test.UiThreadTest;
import android.text.ClipboardManager;

/**
 * Test {@link ClipboardManager}.
 */
public class ClipboardManagerTest extends InstrumentationTestCase {

    private Context mContext;

    @Override
    public void setUp() {
        mContext = getInstrumentation().getContext();
    }

    @UiThreadTest
    public void testAccessText() {
        ClipboardManager clipboardManager =
                (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);

        // set the expected value
        CharSequence expected = "test";
        clipboardManager.setText(expected);
        assertEquals(expected, clipboardManager.getText());
    }

    @UiThreadTest
    public void testHasText() {
        ClipboardManager clipboardManager =
                (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);

        clipboardManager.setText("");
        assertFalse(clipboardManager.hasText());

        clipboardManager.setText("test");
        assertTrue(clipboardManager.hasText());

        clipboardManager.setText(null);
        assertFalse(clipboardManager.hasText());
    }
}
