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
 * limitations under the License
 */

package com.android.phone.vvm.omtp;

import android.annotation.Nullable;
import android.content.res.Resources;
import android.os.PersistableBundle;
import android.util.ArrayMap;

import com.android.internal.annotations.VisibleForTesting;
import com.android.phone.R;
import com.android.phone.vvm.omtp.utils.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Load and caches telephony vvm config from res/xml/vvm_config.xml
 */
public class TelephonyVvmConfigManager {

    private static final String TAG = "TelephonyVvmCfgMgr";

    private static final boolean USE_DEBUG_CONFIG = false; //STOPSHIP if true

    private static final String TAG_PERSISTABLEMAP = "pbundle_as_map";

    static final String KEY_MCCMNC = "mccmnc";

    private static Map<String, PersistableBundle> sCachedConfigs;

    private final Map<String, PersistableBundle> mConfigs;

    public TelephonyVvmConfigManager(Resources resources) {
        if (sCachedConfigs == null) {
            sCachedConfigs = loadConfigs(resources.getXml(R.xml.vvm_config));
        }
        mConfigs = sCachedConfigs;
    }

    @VisibleForTesting
    TelephonyVvmConfigManager(XmlPullParser parser) {
        mConfigs = loadConfigs(parser);
    }

    @Nullable
    public PersistableBundle getConfig(String mccMnc) {
        if (USE_DEBUG_CONFIG) {
            return mConfigs.get("TEST");
        }
        return mConfigs.get(mccMnc);
    }

    private static Map<String, PersistableBundle> loadConfigs(XmlPullParser parser) {
        Map<String, PersistableBundle> configs = new ArrayMap<>();
        try {
            ArrayList list = readBundleList(parser);
            for (Object object : list) {
                if (!(object instanceof PersistableBundle)) {
                    throw new IllegalArgumentException("PersistableBundle expected, got " + object);
                }
                PersistableBundle bundle = (PersistableBundle) object;
                String[] mccMncs = bundle.getStringArray(KEY_MCCMNC);
                if (mccMncs == null) {
                    throw new IllegalArgumentException("MCCMNC is null");
                }
                for (String mccMnc : mccMncs) {
                    configs.put(mccMnc, bundle);
                }
            }
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException(e);
        }
        return configs;
    }

    @Nullable
    public static ArrayList readBundleList(XmlPullParser in) throws IOException,
            XmlPullParserException {
        final int outerDepth = in.getDepth();
        int event;
        while (((event = in.next()) != XmlPullParser.END_DOCUMENT) &&
                (event != XmlPullParser.END_TAG || in.getDepth() < outerDepth)) {
            if (event == XmlPullParser.START_TAG) {
                final String startTag = in.getName();
                final String[] tagName = new String[1];
                in.next();
                return XmlUtils.readThisListXml(in, startTag, tagName,
                        new MyReadMapCallback(), false);
            }
        }
        return null;
    }

    public static PersistableBundle restoreFromXml(XmlPullParser in) throws IOException,
            XmlPullParserException {
        final int outerDepth = in.getDepth();
        final String startTag = in.getName();
        final String[] tagName = new String[1];
        int event;
        while (((event = in.next()) != XmlPullParser.END_DOCUMENT) &&
                (event != XmlPullParser.END_TAG || in.getDepth() < outerDepth)) {
            if (event == XmlPullParser.START_TAG) {
                ArrayMap<String, ?> map =
                        XmlUtils.readThisArrayMapXml(in, startTag, tagName,
                                new MyReadMapCallback());
                PersistableBundle result = new PersistableBundle();
                for (Entry<String, ?> entry : map.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Integer) {
                        result.putInt(entry.getKey(), (int) value);
                    } else if (value instanceof Boolean) {
                        result.putBoolean(entry.getKey(), (boolean) value);
                    } else if (value instanceof String) {
                        result.putString(entry.getKey(), (String) value);
                    } else if (value instanceof String[]) {
                        result.putStringArray(entry.getKey(), (String[]) value);
                    } else if (value instanceof PersistableBundle) {
                        result.putPersistableBundle(entry.getKey(), (PersistableBundle) value);
                    }
                }
                return result;
            }
        }
        return PersistableBundle.EMPTY;
    }

    static class MyReadMapCallback implements XmlUtils.ReadMapCallback {

        @Override
        public Object readThisUnknownObjectXml(XmlPullParser in, String tag)
                throws XmlPullParserException, IOException {
            if (TAG_PERSISTABLEMAP.equals(tag)) {
                return restoreFromXml(in);
            }
            throw new XmlPullParserException("Unknown tag=" + tag);
        }
    }
}
