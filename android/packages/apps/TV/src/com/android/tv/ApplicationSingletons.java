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

package com.android.tv;

import com.android.tv.analytics.Analytics;
import com.android.tv.analytics.Tracker;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.ProgramDataManager;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.DvrSessionManager;
import com.android.tv.util.TvInputManagerHelper;

/**
 * Interface with getters for application scoped singletons.
 */
public interface ApplicationSingletons {

    Analytics getAnalytics();

    ChannelDataManager getChannelDataManager();

    DvrDataManager getDvrDataManager();

    DvrManager getDvrManager();

    DvrSessionManager getDvrSessionManger();

    ProgramDataManager getProgramDataManager();

    Tracker getTracker();

    TvInputManagerHelper getTvInputManagerHelper();

    MainActivityWrapper getMainActivityWrapper();
}
