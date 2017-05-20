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
package com.android.tv.menu;

import android.media.tv.TvTrackInfo;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.tv.BaseMainActivityTestCase;
import com.android.tv.MainActivity;
import com.android.tv.customization.CustomAction;
import com.android.tv.testing.Constants;
import com.android.tv.testing.testinput.ChannelStateData;
import com.android.tv.testing.testinput.TvTestInputConstants;

import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link TvOptionsRowAdapter}.
 */
@MediumTest
public class TvOptionsRowAdapterTest extends BaseMainActivityTestCase {
    private static final int WAIT_TRACK_SIZE_TIMEOUT_MS = 300;
    public static final int TRACK_SIZE_CHECK_INTERVAL_MS = 10;

    // TODO: Refactor TvOptionsRowAdapter so it does not rely on MainActivity
    private TvOptionsRowAdapter mTvOptionsRowAdapter;

    public TvOptionsRowAdapterTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTvOptionsRowAdapter = new TvOptionsRowAdapter(mActivity,
                Collections.<CustomAction>emptyList());
        tuneToChannel(TvTestInputConstants.CH_1_DEFAULT_DONT_MODIFY);
        waitUntilAudioTracksHaveSize(1);
        mTvOptionsRowAdapter.update();
    }

    public void testUpdateAudioAction_2tracks() {
        ChannelStateData data = new ChannelStateData();
        data.mTvTrackInfos.add(Constants.GENERIC_AUDIO_TRACK);
        updateThenTune(data, TvTestInputConstants.CH_2);
        waitUntilAudioTracksHaveSize(2);

        boolean result = mTvOptionsRowAdapter.updateMultiAudioAction();
        assertEquals("update Action had change", true, result);
        assertEquals("Multi Audio enabled", true,
                MenuAction.SELECT_AUDIO_LANGUAGE_ACTION.isEnabled());
    }

    public void testUpdateAudioAction_1track() {
        ChannelStateData data = new ChannelStateData();
        data.mTvTrackInfos.clear();
        data.mTvTrackInfos.add(Constants.GENERIC_AUDIO_TRACK);
        updateThenTune(data, TvTestInputConstants.CH_2);
        waitUntilAudioTracksHaveSize(1);

        boolean result = mTvOptionsRowAdapter.updateMultiAudioAction();
        assertEquals("update Action had change", false, result);
        assertEquals("Multi Audio enabled", false,
                MenuAction.SELECT_AUDIO_LANGUAGE_ACTION.isEnabled());
    }

    public void testUpdateAudioAction_noTracks() {
        ChannelStateData data = new ChannelStateData();
        data.mTvTrackInfos.clear();
        updateThenTune(data, TvTestInputConstants.CH_2);
        waitUntilAudioTracksHaveSize(0);

        boolean result = mTvOptionsRowAdapter.updateMultiAudioAction();
        assertEquals("update Action had change", false, result);
        assertEquals("Multi Audio enabled", false,
                MenuAction.SELECT_AUDIO_LANGUAGE_ACTION.isEnabled());
    }

    private void waitUntilAudioTracksHaveSize(int expected) {
        long start = SystemClock.elapsedRealtime();
        int size = -1;
        while (SystemClock.elapsedRealtime() < start + WAIT_TRACK_SIZE_TIMEOUT_MS) {
            getInstrumentation().waitForIdleSync();
            List<TvTrackInfo> tracks = mActivity.getTracks(TvTrackInfo.TYPE_AUDIO);
            if (tracks != null) {
                size = tracks.size();
                if (size == expected) {
                    return;
                }
            }
            SystemClock.sleep(TRACK_SIZE_CHECK_INTERVAL_MS);
        }
        fail("Waited for " + WAIT_TRACK_SIZE_TIMEOUT_MS + " milliseconds for track size to be "
                + expected + " but was " + size);
    }
}
