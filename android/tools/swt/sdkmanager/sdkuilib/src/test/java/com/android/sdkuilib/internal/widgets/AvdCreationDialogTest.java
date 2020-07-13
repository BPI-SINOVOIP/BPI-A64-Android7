/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdkuilib.internal.widgets;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage.LocationType;
import com.android.sdklib.SdkManager;
import com.android.sdklib.SdkManagerTestCase;
import com.android.sdklib.SystemImage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.io.FileOp;
import com.android.sdkuilib.internal.widgets.AvdCreationPresenter.Ctrl;
import com.android.sdkuilib.internal.widgets.AvdCreationPresenter.IWidgetAdapter;
import com.android.testutils.SdkTestCase;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

public class AvdCreationDialogTest extends SdkManagerTestCase {

    private IAndroidTarget mTarget;
    private File mAvdFolder;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        SdkManager sdkMan = getSdkManager();
        mTarget = sdkMan.getTargets()[0];
        mAvdFolder = AvdInfo.getDefaultAvdFolder(getAvdManager(), getName());

        // add 4 system images for the target
        makeSystemImageFolder(new SystemImage(sdkMan, mTarget,
                LocationType.IN_IMAGES_SUBFOLDER,
                SystemImage.DEFAULT_TAG,
                SdkConstants.ABI_ARMEABI_V7A,
                FileOp.EMPTY_FILE_ARRAY), null);
        makeSystemImageFolder(new SystemImage(sdkMan, mTarget,
                LocationType.IN_IMAGES_SUBFOLDER,
                SystemImage.DEFAULT_TAG,
                SdkConstants.ABI_ARM64_V8A,
                FileOp.EMPTY_FILE_ARRAY), null);
        makeSystemImageFolder(new SystemImage(sdkMan, mTarget,
                LocationType.IN_IMAGES_SUBFOLDER,
                SystemImage.DEFAULT_TAG,
                SdkConstants.ABI_INTEL_ATOM,
                FileOp.EMPTY_FILE_ARRAY), null);
        makeSystemImageFolder(new SystemImage(sdkMan, mTarget,
                LocationType.IN_IMAGES_SUBFOLDER,
                SystemImage.DEFAULT_TAG,
                SdkConstants.ABI_INTEL_ATOM64,
                FileOp.EMPTY_FILE_ARRAY), null);

        sdkMan.reloadSdk(getLog());
    }

    public void testInitDialog_NoAvd() {
        AvdCreationPresenter p = new AvdCreationPresenter(getAvdManager(), getLog(), null /*editAvdInfo*/);
        AvdCreationView v = new AvdCreationView(p).initView();
        assertEquals(
                "Title: Create new Android Virtual Device (AVD)\n" +
                "   AVD Name: \n" +
                "   Device: [-1]\n" +
                "       - Nexus 9 (8.86\", 2048 × 1536: xhdpi)\n" +
                "       - Nexus 5 (4.95\", 1080 × 1920: xxhdpi)\n" +
                "       - Nexus 6 (5.96\", 1440 × 2560: 560dpi)\n" +
                "       - Nexus 7 (2012) (7.0\", 800 × 1280: tvdpi)\n" +
                "       - Nexus 4 (4.7\", 768 × 1280: xhdpi)\n" +
                "       - Nexus 10 (10.055\", 2560 × 1600: xhdpi)\n" +
                "       - Nexus 7 (7.02\", 1200 × 1920: xhdpi)\n" +
                "       - Galaxy Nexus (4.65\", 720 × 1280: xhdpi)\n" +
                "       - Nexus S (4.0\", 480 × 800: hdpi)\n" +
                "       - Nexus One (3.7\", 480 × 800: hdpi)\n" +
                "       - 3.7\" WVGA (Nexus One) (480 × 800: hdpi)\n" +
                "       - Android Wear Round Chin (320 × 290: tvdpi)\n" +
                "       - 4\" WVGA (Nexus S) (480 × 800: hdpi)\n" +
                "       - 3.2\" HVGA slider (ADP1) (320 × 480: mdpi)\n" +
                "       - Android TV (1080p) (1920 × 1080: xhdpi)\n" +
                "       - 10.1\" WXGA (Tablet) (1280 × 800: mdpi)\n" +
                "       - 4.7\" WXGA (1280 × 720: xhdpi)\n" +
                "       - 5.4\" FWVGA (480 × 854: mdpi)\n" +
                "       - 4.65\" 720p (Galaxy Nexus) (720 × 1280: xhdpi)\n" +
                "       - 3.7\" FWVGA slider (480 × 854: hdpi)\n" +
                "       - Android Wear Round (320 × 320: hdpi)\n" +
                "       - 3.2\" QVGA (ADP2) (320 × 480: mdpi)\n" +
                "       - 2.7\" QVGA slider (240 × 320: ldpi)\n" +
                "       - 3.4\" WQVGA (240 × 432: ldpi)\n" +
                "       - 7\" WSVGA (Tablet) (1024 × 600: mdpi)\n" +
                "       - 3.3\" WQVGA (240 × 400: ldpi)\n" +
                "       - Android Wear Square (280 × 280: hdpi)\n" +
                "       - 5.1\" WVGA (480 × 800: mdpi)\n" +
                "       - 2.7\" QVGA (240 × 320: ldpi)\n" +
                "       - Android TV (720p) (1280 × 720: tvdpi)\n" +
                "   Target: [-1]\n" +
                "       - Android 0.0 - API Level 0\n" +
                "   CPU/ABI: [-1]\n" +
                "   [ ] Keyboard\n" +
                "   Skin: [-1]\n" +
                "       - Skin with dynamic hardware controls\n" +
                "       - No skin\n" +
                "-- Front Cam: [-1]\n" +
                "-- Back  Cam: [-1]\n" +
                "   RAM: \n" +
                "   VM : \n" +
                "   Data Size: \n" +
                "   Data Unit: [-1]\n" +
                "   [ ] SD by Size\n" +
                "   SD Size: \n" +
                "   SD Unit: [-1]\n" +
                "   [ ] SD by File\n" +
                "-- SD File Name: \n" +
                "-- [Browse SD File]\n" +
                "   Icon: reject_icon16.png\n" +
                "   Status: AVD Name cannot be empty",
                v.renderDialog());
    }

    public void testInitDialog_ExistingAvd() {
        // create an AVD and use it to initialize the dialog

        AvdInfo avdInfo = getAvdManager().createAvd(
                mAvdFolder,
                this.getName(),
                mTarget,
                SystemImage.DEFAULT_TAG,
                SdkConstants.ABI_ARMEABI_V7A,
                null,   // skinFolder
                null,   // skinName
                null,   // sdName
                null,   // hardware properties
                null,   // bootProps
                false,  // createSnapshot
                false,  // removePrevious
                false,  // editExisting
                getLog());

        AvdCreationPresenter p = new AvdCreationPresenter(getAvdManager(), getLog(), avdInfo);
        AvdCreationView v = new AvdCreationView(p).initView();

        assertEquals(
                "Title: Create new Android Virtual Device (AVD)\n" +
                "   AVD Name: \n" +
                "   Device: [-1]\n" +
                "       - Nexus 9 (8.86\", 2048 × 1536: xhdpi)\n" +
                "       - Nexus 5 (4.95\", 1080 × 1920: xxhdpi)\n" +
                "       - Nexus 6 (5.96\", 1440 × 2560: 560dpi)\n" +
                "       - Nexus 7 (2012) (7.0\", 800 × 1280: tvdpi)\n" +
                "       - Nexus 4 (4.7\", 768 × 1280: xhdpi)\n" +
                "       - Nexus 10 (10.055\", 2560 × 1600: xhdpi)\n" +
                "       - Nexus 7 (7.02\", 1200 × 1920: xhdpi)\n" +
                "       - Galaxy Nexus (4.65\", 720 × 1280: xhdpi)\n" +
                "       - Nexus S (4.0\", 480 × 800: hdpi)\n" +
                "       - Nexus One (3.7\", 480 × 800: hdpi)\n" +
                "       - 3.7\" WVGA (Nexus One) (480 × 800: hdpi)\n" +
                "       - Android Wear Round Chin (320 × 290: tvdpi)\n" +
                "       - 4\" WVGA (Nexus S) (480 × 800: hdpi)\n" +
                "       - 3.2\" HVGA slider (ADP1) (320 × 480: mdpi)\n" +
                "       - Android TV (1080p) (1920 × 1080: xhdpi)\n" +
                "       - 10.1\" WXGA (Tablet) (1280 × 800: mdpi)\n" +
                "       - 4.7\" WXGA (1280 × 720: xhdpi)\n" +
                "       - 5.4\" FWVGA (480 × 854: mdpi)\n" +
                "       - 4.65\" 720p (Galaxy Nexus) (720 × 1280: xhdpi)\n" +
                "       - 3.7\" FWVGA slider (480 × 854: hdpi)\n" +
                "       - Android Wear Round (320 × 320: hdpi)\n" +
                "       - 3.2\" QVGA (ADP2) (320 × 480: mdpi)\n" +
                "       - 2.7\" QVGA slider (240 × 320: ldpi)\n" +
                "       - 3.4\" WQVGA (240 × 432: ldpi)\n" +
                "       - 7\" WSVGA (Tablet) (1024 × 600: mdpi)\n" +
                "       - 3.3\" WQVGA (240 × 400: ldpi)\n" +
                "       - Android Wear Square (280 × 280: hdpi)\n" +
                "       - 5.1\" WVGA (480 × 800: mdpi)\n" +
                "       - 2.7\" QVGA (240 × 320: ldpi)\n" +
                "       - Android TV (720p) (1280 × 720: tvdpi)\n" +
                "   Target: [-1]\n" +
                "       - Android 0.0 - API Level 0\n" +
                "   CPU/ABI: [-1]\n" +
                "   [ ] Keyboard\n" +
                "   Skin: [-1]\n" +
                "       - Skin with dynamic hardware controls\n" +
                "       - No skin\n" +
                "-- Front Cam: [-1]\n" +
                "-- Back  Cam: [-1]\n" +
                "   RAM: \n" +
                "   VM : \n" +
                "   Data Size: \n" +
                "   Data Unit: [-1]\n" +
                "   [ ] SD by Size\n" +
                "   SD Size: \n" +
                "   SD Unit: [-1]\n" +
                "   [ ] SD by File\n" +
                "-- SD File Name: \n" +
                "-- [Browse SD File]\n" +
                "   Icon: reject_icon16.png\n" +
                "   Status: AVD Name cannot be empty",
                v.renderDialog());
    }

    public void testCreateAvd() {
        AvdCreationPresenter p = new AvdCreationPresenter(getAvdManager(), getLog(), null /*editAvdInfo*/);
        AvdCreationView v = new AvdCreationView(p).initView();
        IWidgetAdapter a = v.getAdapter();

        String r = v.renderDialog();
        assertEquals(
                "Title: Create new Android Virtual Device (AVD)\n" +
                "   AVD Name: \n" +
                "   Device: [-1]\n" +
                "       - Nexus 9 (8.86\", 2048 × 1536: xhdpi)\n" +
                "       - Nexus 5 (4.95\", 1080 × 1920: xxhdpi)\n" +
                "       - Nexus 6 (5.96\", 1440 × 2560: 560dpi)\n" +
                "       - Nexus 7 (2012) (7.0\", 800 × 1280: tvdpi)\n" +
                "       - Nexus 4 (4.7\", 768 × 1280: xhdpi)\n" +
                "       - Nexus 10 (10.055\", 2560 × 1600: xhdpi)\n" +
                "       - Nexus 7 (7.02\", 1200 × 1920: xhdpi)\n" +
                "       - Galaxy Nexus (4.65\", 720 × 1280: xhdpi)\n" +
                "       - Nexus S (4.0\", 480 × 800: hdpi)\n" +
                "       - Nexus One (3.7\", 480 × 800: hdpi)\n" +
                "       - 3.7\" WVGA (Nexus One) (480 × 800: hdpi)\n" +
                "       - Android Wear Round Chin (320 × 290: tvdpi)\n" +
                "       - 4\" WVGA (Nexus S) (480 × 800: hdpi)\n" +
                "       - 3.2\" HVGA slider (ADP1) (320 × 480: mdpi)\n" +
                "       - Android TV (1080p) (1920 × 1080: xhdpi)\n" +
                "       - 10.1\" WXGA (Tablet) (1280 × 800: mdpi)\n" +
                "       - 4.7\" WXGA (1280 × 720: xhdpi)\n" +
                "       - 5.4\" FWVGA (480 × 854: mdpi)\n" +
                "       - 4.65\" 720p (Galaxy Nexus) (720 × 1280: xhdpi)\n" +
                "       - 3.7\" FWVGA slider (480 × 854: hdpi)\n" +
                "       - Android Wear Round (320 × 320: hdpi)\n" +
                "       - 3.2\" QVGA (ADP2) (320 × 480: mdpi)\n" +
                "       - 2.7\" QVGA slider (240 × 320: ldpi)\n" +
                "       - 3.4\" WQVGA (240 × 432: ldpi)\n" +
                "       - 7\" WSVGA (Tablet) (1024 × 600: mdpi)\n" +
                "       - 3.3\" WQVGA (240 × 400: ldpi)\n" +
                "       - Android Wear Square (280 × 280: hdpi)\n" +
                "       - 5.1\" WVGA (480 × 800: mdpi)\n" +
                "       - 2.7\" QVGA (240 × 320: ldpi)\n" +
                "       - Android TV (720p) (1280 × 720: tvdpi)\n" +
                "   Target: [-1]\n" +
                "       - Android 0.0 - API Level 0\n" +
                "   CPU/ABI: [-1]\n" +
                "   [ ] Keyboard\n" +
                "   Skin: [-1]\n" +
                "       - Skin with dynamic hardware controls\n" +
                "       - No skin\n" +
                "-- Front Cam: [-1]\n" +
                "-- Back  Cam: [-1]\n" +
                "   RAM: \n" +
                "   VM : \n" +
                "   Data Size: \n" +
                "   Data Unit: [-1]\n" +
                "   [ ] SD by Size\n" +
                "   SD Size: \n" +
                "   SD Unit: [-1]\n" +
                "   [ ] SD by File\n" +
                "-- SD File Name: \n" +
                "-- [Browse SD File]\n" +
                "   Icon: reject_icon16.png\n" +
                "   Status: AVD Name cannot be empty",
                r);

        // set name
        a.setText(Ctrl.TEXT_AVD_NAME, "the_avd_name");
        p.onAvdNameModified();

        String last = r;
        r = v.renderDialog();
        assertEquals(
                "@@ -2 +2\n" +
                "-    AVD Name: \n" +
                "+    AVD Name: the_avd_name\n" +
                "@@ -54 +54\n" +
                "-    Status: AVD Name cannot be empty\n" +
                "@@ -55 +54\n" +
                "+    Status: No device selected\n",
                SdkTestCase.getDiff(last, r));

        // select Nexus S device
        a.selectComboIndex(Ctrl.COMBO_DEVICE, 8);
        p.onDeviceComboChanged();

        last = r;
        r = v.renderDialog();
        assertEquals(
                "@@ -3 +3\n" +
                "-    Device: [-1]\n" +
                "+    Device: [8]\n" +
                "@@ -12 +12\n" +
                "-        - Nexus S (4.0\", 480 × 800: hdpi)\n" +
                "+       ** Nexus S (4.0\", 480 × 800: hdpi)\n" +
                "@@ -41 +41\n" +
                "- -- Front Cam: [-1]\n" +
                "- -- Back  Cam: [-1]\n" +
                "-    RAM: \n" +
                "-    VM : \n" +
                "+    Front Cam: [-1]\n" +
                "+    Back  Cam: [-1]\n" +
                "+    RAM: 343\n" +
                "+    VM : 32\n" +
                "@@ -54 +54\n" +
                "-    Status: No device selected\n" +
                "@@ -55 +54\n" +
                "+    Status: No target selected\n",
                SdkTestCase.getDiff(last, r));

        // select target 0
        a.selectComboIndex(Ctrl.COMBO_TARGET, 0);
        p.onTargetComboChanged();

        last = r;
        r = v.renderDialog();
        assertEquals(
                "@@ -34 +34\n" +
                "-    Target: [-1]\n" +
                "-        - Android 0.0 - API Level 0\n" +
                "+    Target: [0]\n" +
                "+       ** Android 0.0 - API Level 0\n" +
                "@@ -37 +37\n" +
                "+        - ARM (arm64-v8a)\n" +
                "+        - ARM (armeabi-v7a)\n" +
                "+        - Intel Atom (x86)\n" +
                "+        - Intel Atom (x86_64)\n" +
                "@@ -41 +45\n" +
                "+        - HVGA\n" +
                "@@ -54 +59\n" +
                "-    Status: No target selected\n" +
                "@@ -55 +59\n" +
                "+    Status: No CPU/ABI system image selected\n",
                SdkTestCase.getDiff(last, r));

        // select cpu/abi
        a.selectComboIndex(Ctrl.COMBO_TAG_ABI, 0);
        p.onTagComboChanged();

        last = r;
        r = v.renderDialog();
        assertEquals(
                "@@ -36 +36\n" +
                "-    CPU/ABI: [-1]\n" +
                "-        - ARM (arm64-v8a)\n" +
                "+    CPU/ABI: [0]\n" +
                "+       ** ARM (arm64-v8a)\n" +
                "@@ -59 +59\n" +
                "-    Status: No CPU/ABI system image selected\n" +
                "@@ -60 +59\n" +
                "+    Status: No skin selected\n",
                SdkTestCase.getDiff(last, r));

        // select dynamic skin
        a.selectComboIndex(Ctrl.COMBO_SKIN, 0);
        p.validatePage();

        last = r;
        r = v.renderDialog();
        assertEquals(
                "@@ -42 +42\n" +
                "-    Skin: [-1]\n" +
                "-        - Skin with dynamic hardware controls\n" +
                "+    Skin: [0]\n" +
                "+       ** Skin with dynamic hardware controls\n" +
                "@@ -59 +59\n" +
                "-    Status: No skin selected\n" +
                "@@ -60 +59\n" +
                "+    Status: Invalid Data partition size.\n",
                SdkTestCase.getDiff(last, r));

        // select internal storage
        a.setText(Ctrl.TEXT_DATA_PART, "200");
        a.selectComboIndex(Ctrl.COMBO_DATA_PART_SIZE, 0);   // MiB
        p.validatePage();

        last = r;
        r = v.renderDialog();
        assertEquals(
                "@@ -50 +50\n" +
                "-    Data Size: \n" +
                "-    Data Unit: [-1]\n" +
                "+    Data Size: 200\n" +
                "+    Data Unit: [0]\n" +
                "@@ -59 +59\n" +
                "-    Status: Invalid Data partition size.\n" +
                "@@ -60 +59\n" +
                "+    Status: SD Card path isn't valid.\n",
                SdkTestCase.getDiff(last, r));

        // select sdcard by size
        a.setChecked(Ctrl.RADIO_SDCARD_FILE, false);
        a.setChecked(Ctrl.RADIO_SDCARD_SIZE, true);
        p.onRadioSdCardSizeChanged();

        a.setText(Ctrl.TEXT_SDCARD_SIZE, "16");
        a.selectComboIndex(Ctrl.COMBO_SDCARD_SIZE, 1);  // MiB unit
        p.validatePage();

        last = r;
        r = v.renderDialog();
        assertEquals(
                "@@ -52 +52\n" +
                "-    [ ] SD by Size\n" +
                "-    SD Size: \n" +
                "-    SD Unit: [-1]\n" +
                "+    [x] SD by Size\n" +
                "+    SD Size: 16\n" +
                "+    SD Unit: [1]\n" +
                "@@ -58 +58\n" +
                "-    Icon: reject_icon16.png\n" +
                "-    Status: SD Card path isn't valid.\n" +
                "@@ -60 +58\n" +
                "+    Icon: null\n" +
                "+    Status:  \n" +
                "+ \n",
                SdkTestCase.getDiff(last, r));

        // final dialog state
        String r2 = v.renderDialog();
        assertEquals(
                "Title: Create new Android Virtual Device (AVD)\n" +
                "   AVD Name: the_avd_name\n" +
                "   Device: [8]\n" +
                "       - Nexus 9 (8.86\", 2048 × 1536: xhdpi)\n" +
                "       - Nexus 5 (4.95\", 1080 × 1920: xxhdpi)\n" +
                "       - Nexus 6 (5.96\", 1440 × 2560: 560dpi)\n" +
                "       - Nexus 7 (2012) (7.0\", 800 × 1280: tvdpi)\n" +
                "       - Nexus 4 (4.7\", 768 × 1280: xhdpi)\n" +
                "       - Nexus 10 (10.055\", 2560 × 1600: xhdpi)\n" +
                "       - Nexus 7 (7.02\", 1200 × 1920: xhdpi)\n" +
                "       - Galaxy Nexus (4.65\", 720 × 1280: xhdpi)\n" +
                "      ** Nexus S (4.0\", 480 × 800: hdpi)\n" +
                "       - Nexus One (3.7\", 480 × 800: hdpi)\n" +
                "       - 3.7\" WVGA (Nexus One) (480 × 800: hdpi)\n" +
                "       - Android Wear Round Chin (320 × 290: tvdpi)\n" +
                "       - 4\" WVGA (Nexus S) (480 × 800: hdpi)\n" +
                "       - 3.2\" HVGA slider (ADP1) (320 × 480: mdpi)\n" +
                "       - Android TV (1080p) (1920 × 1080: xhdpi)\n" +
                "       - 10.1\" WXGA (Tablet) (1280 × 800: mdpi)\n" +
                "       - 4.7\" WXGA (1280 × 720: xhdpi)\n" +
                "       - 5.4\" FWVGA (480 × 854: mdpi)\n" +
                "       - 4.65\" 720p (Galaxy Nexus) (720 × 1280: xhdpi)\n" +
                "       - 3.7\" FWVGA slider (480 × 854: hdpi)\n" +
                "       - Android Wear Round (320 × 320: hdpi)\n" +
                "       - 3.2\" QVGA (ADP2) (320 × 480: mdpi)\n" +
                "       - 2.7\" QVGA slider (240 × 320: ldpi)\n" +
                "       - 3.4\" WQVGA (240 × 432: ldpi)\n" +
                "       - 7\" WSVGA (Tablet) (1024 × 600: mdpi)\n" +
                "       - 3.3\" WQVGA (240 × 400: ldpi)\n" +
                "       - Android Wear Square (280 × 280: hdpi)\n" +
                "       - 5.1\" WVGA (480 × 800: mdpi)\n" +
                "       - 2.7\" QVGA (240 × 320: ldpi)\n" +
                "       - Android TV (720p) (1280 × 720: tvdpi)\n" +
                "   Target: [0]\n" +
                "      ** Android 0.0 - API Level 0\n" +
                "   CPU/ABI: [0]\n" +
                "      ** ARM (arm64-v8a)\n" +
                "       - ARM (armeabi-v7a)\n" +
                "       - Intel Atom (x86)\n" +
                "       - Intel Atom (x86_64)\n" +
                "   [ ] Keyboard\n" +
                "   Skin: [0]\n" +
                "      ** Skin with dynamic hardware controls\n" +
                "       - No skin\n" +
                "       - HVGA\n" +
                "   Front Cam: [-1]\n" +
                "   Back  Cam: [-1]\n" +
                "   RAM: 343\n" +
                "   VM : 32\n" +
                "   Data Size: 200\n" +
                "   Data Unit: [0]\n" +
                "   [x] SD by Size\n" +
                "   SD Size: 16\n" +
                "   SD Unit: [1]\n" +
                "   [ ] SD by File\n" +
                "-- SD File Name: \n" +
                "-- [Browse SD File]\n" +
                "   Icon: null\n" +
                "   Status:  \n" +
                " ",
                r2);
    }


    static class AvdCreationView {

        private final AvdCreationPresenter mPresenter;
        private final EnumMap<Ctrl, Boolean> mEnabled = new EnumMap<Ctrl, Boolean>(Ctrl.class);
        private final EnumMap<Ctrl, Boolean> mChecked = new EnumMap<Ctrl, Boolean>(Ctrl.class);
        private final EnumMap<Ctrl, String> mText = new EnumMap<Ctrl, String>(Ctrl.class);
        private final EnumMap<Ctrl, Integer> mComboIndex = new EnumMap<Ctrl, Integer>(Ctrl.class);
        private final EnumMap<Ctrl, List<String>> mComboLabels = new EnumMap<Ctrl, List<String>>(Ctrl.class);
        private String mTitle;

        private final IWidgetAdapter mAdapter = new IWidgetAdapter() {
            @Override
            public void setTitle(String title) {
                mTitle = title;
            }

            @Override
            public int getComboSize(Ctrl ctrl) {
                List<String> c = mComboLabels.get(ctrl);
                return c == null ? 0 : c.size();
            }

            @Override
            public int getComboIndex(Ctrl ctrl) {
                Integer i = mComboIndex.get(ctrl);
                return i == null ? -1 : i;
            }

            @Override
            public void selectComboIndex(Ctrl ctrl, int index) {
                mComboIndex.put(ctrl, index);
            }

            @Override
            public String getComboItem(Ctrl ctrl, int index) {
                // Throws an NPE if the combo has no items
                // Throws an IOOB if index is not in range
                List<String> c = mComboLabels.get(ctrl);
                return c.get(index);
            }

            @Override
            public void setComboItems(Ctrl ctrl, @Nullable String[] labels) {
                if (labels == null) {
                    mComboLabels.put(ctrl, null);
                } else {
                    mComboLabels.put(ctrl, new ArrayList<String>(Arrays.asList(labels)));
                }
            }

            @Override
            public void addComboItem(Ctrl ctrl, String label) {
                List<String> c = mComboLabels.get(ctrl);
                if (c == null) {
                    c = new ArrayList<String>();
                    mComboLabels.put(ctrl, c);
                }
                c.add(label);
            }

            @Override
            public boolean isEnabled(Ctrl ctrl) {
                // controls are enabled by default
                Boolean b = mEnabled.get(ctrl);
                return b == null || b.booleanValue();
            }

            @Override
            public void setEnabled(Ctrl ctrl, boolean enabled) {
                mEnabled.put(ctrl, enabled);
            }

            @Override
            public boolean isChecked(Ctrl ctrl) {
                // controls are not checked by default
                Boolean b = mChecked.get(ctrl);
                return b != null && b.booleanValue();
            }

            @Override
            public void setChecked(Ctrl ctrl, boolean checked) {
                mChecked.put(ctrl, checked);
            }

            @Override
            public String getText(Ctrl ctrl) {
                return mText.get(ctrl);
            }

            @Override
            public void setText(Ctrl ctrl, String text) {
                mText.put(ctrl, text);
            };

            @Override
            public void setImage(Ctrl ctrl, String imageName) {
                setText(ctrl, imageName);
            }

            @Override
            public String openFileDialog(String string) {
                // no-op
                return null;
            }

            @Override
            public void repack() {
                // no-op
            }

            @Override
            public IMessageBoxLogger newDelayedMessageBoxLog(String title,
                                                             boolean logErrorsOnly) {
                // not implemented yet
                return null;
            }
        };


        public AvdCreationView(AvdCreationPresenter presenter) {
            mPresenter = presenter;
        }

        public IWidgetAdapter getAdapter() {
            return mAdapter;
        }

        public AvdCreationView initView() {
            mEnabled.clear();
            mChecked.clear();
            mText.clear();
            mComboIndex.clear();
            mComboLabels.clear();

            for (Ctrl c : Ctrl.values()) {
                String name = c.name();
                if (name.startsWith("TEXT") ||
                        name.startsWith("COMBO") ||
                        name.startsWith("BUTTON")) {
                    mText.put(c, "");
                }
            }

            mPresenter.setWidgetAdapter(mAdapter);
            mPresenter.onViewInit();
            return this;
        }

        public String renderDialog() {
            StringBuilder sb = new StringBuilder();

            sb.append("Title: ").append(mTitle);

            renderText(sb, "AVD Name", Ctrl.TEXT_AVD_NAME);

            renderCombo(sb, "Device", Ctrl.COMBO_DEVICE);
            renderCombo(sb, "Target", Ctrl.COMBO_TARGET);
            renderCombo(sb, "CPU/ABI", Ctrl.COMBO_TAG_ABI);

            renderCheck(sb, "Keyboard", Ctrl.CHECK_KEYBOARD);
            renderCombo(sb, "Skin", Ctrl.COMBO_SKIN);

            renderCombo(sb, "Front Cam", Ctrl.COMBO_FRONT_CAM);
            renderCombo(sb, "Back  Cam", Ctrl.COMBO_BACK_CAM);

            renderText (sb, "RAM", Ctrl.TEXT_RAM);
            renderText (sb, "VM ", Ctrl.TEXT_VM_HEAP);

            renderText (sb, "Data Size", Ctrl.TEXT_DATA_PART);
            renderCombo(sb, "Data Unit", Ctrl.COMBO_DATA_PART_SIZE);

            renderCheck(sb, "SD by Size", Ctrl.RADIO_SDCARD_SIZE);
            renderText (sb, "SD Size", Ctrl.TEXT_SDCARD_SIZE);
            renderCombo(sb, "SD Unit", Ctrl.COMBO_SDCARD_SIZE);

            renderCheck(sb, "SD by File", Ctrl.RADIO_SDCARD_FILE);
            renderText (sb, "SD File Name", Ctrl.TEXT_SDCARD_FILE);
            renderButton(sb, "Browse SD File", Ctrl.BUTTON_BROWSE_SDCARD);

            renderText (sb, "Icon", Ctrl.ICON_STATUS);
            renderText (sb, "Status", Ctrl.TEXT_STATUS);

            return sb.toString();
        }

        private void renderButton(StringBuilder sb, String title, Ctrl c) {
            boolean en = mAdapter.isEnabled(c);
            sb.append(en ? "\n   " : "\n-- ")
              .append('[').append(title).append("]");
        }

        private void renderText(StringBuilder sb, String title, Ctrl c) {
            boolean en = mAdapter.isEnabled(c);
            sb.append(en ? "\n   " : "\n-- ")
              .append(title).append(": ")
              .append(mAdapter.getText(c));
        }

        private void renderCheck(StringBuilder sb, String title, Ctrl c) {
            boolean en = mAdapter.isEnabled(c);
            boolean cc = mAdapter.isChecked(c);
            sb.append(en ? "\n   " : "\n-- ")
              .append(cc ? "[x] " : "[ ] ")
              .append(title);
        }

        private void renderCombo(StringBuilder sb, String title, Ctrl c) {
            boolean en = mAdapter.isEnabled(c);
            int curr = mAdapter.getComboIndex(c);

            sb.append(en ? "\n   " : "\n-- ")
              .append(title)
              .append(": [").append(curr).append("]");

            List<String> labels = mComboLabels.get(c);
            if (labels != null) {
                for (int i = 0, n = labels.size(); i < n; i++) {
                    sb.append("\n      ").append(i == curr ? "** " : " - ").append(labels.get(i));
                }
            }
        }
    }

}
