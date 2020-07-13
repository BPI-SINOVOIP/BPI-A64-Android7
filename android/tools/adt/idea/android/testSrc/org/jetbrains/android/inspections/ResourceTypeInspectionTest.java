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
package org.jetbrains.android.inspections;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.idea.startup.ExternalAnnotationsSupport;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.LightInspectionTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ResourceTypeInspectionTest extends LightInspectionTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    //noinspection StatementWithEmptyBody
    if (getName().equals("testNotAndroid")) {
      // Don't add an Android facet here; we're testing that we're a no-op outside of Android projects
      // since the inspection is registered at the .java source type level
      return;
    }

    // Module must have Android facet or resource type inspection will become a no-op
    if (AndroidFacet.getInstance(myModule) == null) {
      String sdkPath = AndroidTestBase.getDefaultTestSdkPath();
      String platform = AndroidTestBase.getDefaultPlatformDir();
      AndroidTestCase.addAndroidFacet(myModule, sdkPath, platform, true);
      Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
      assertNotNull(sdk);
      @SuppressWarnings("SpellCheckingInspection") SdkModificator sdkModificator = sdk.getSdkModificator();
      ExternalAnnotationsSupport.attachJdkAnnotations(sdkModificator);
      sdkModificator.commitChanges();
    }

    // Required by testLibraryRevocablePermissions (but placing it there leads to
    // test ordering issues)
    myFixture.addFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML,
                               "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                               "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                               "    package=\"test.pkg.permissiontest\">\n" +
                               "\n" +
                               "    <uses-sdk android:minSdkVersion=\"17\" android:targetSdkVersion=\"23\" />" +
                               "\n" +
                               "    <permission\n" +
                               "        android:name=\"my.normal.P1\"\n" +
                               "        android:protectionLevel=\"normal\" />\n" +
                               "\n" +
                               "    <permission\n" +
                               "        android:name=\"my.dangerous.P2\"\n" +
                               "        android:protectionLevel=\"dangerous\" />\n" +
                               "\n" +
                               "    <uses-permission android:name=\"my.normal.P1\" />\n" +
                               "    <uses-permission android:name=\"my.dangerous.P2\" />\n" +
                               "\n" +
                               "</manifest>\n");
  }

  public void testTypes() {
    doCheck("import android.annotation.SuppressLint;\n" +
            "import android.annotation.TargetApi;\n" +
            "import android.app.Notification;\n" +
            "import android.content.Context;\n" +
            "import android.content.Intent;\n" +
            "import android.content.ServiceConnection;\n" +
            "import android.content.res.Resources;\n" +
            "import android.os.Build;\n" +
            "import android.support.annotation.DrawableRes;\n" +
            "import android.view.View;\n" +
            "\n" +
            "import static android.content.Context.CONNECTIVITY_SERVICE;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "public class X {\n" +
            "    public void testResourceTypeParameters(Context context, int unknown) {\n" +
            "        Resources resources = context.getResources();\n" +
            "        String ok1 = resources.getString(R.string.app_name);\n" +
            "        String ok2 = resources.getString(unknown);\n" +
            "        String ok3 = resources.getString(android.R.string.ok);\n" +
            "        int ok4 = resources.getColor(android.R.color.black);\n" +
            "        if (testResourceTypeReturnValues(context, true) == R.drawable.ic_launcher) { // ok\n" +
            "        }\n" +
            "\n" +
            "        //String ok2 = resources.getString(R.string.app_name, 1, 2, 3);\n" +
            "        float error1 = resources.getDimension(/*Expected resource of type dimen*/R.string.app_name/**/);\n" +
            "        boolean error2 = resources.getBoolean(/*Expected resource of type bool*/R.string.app_name/**/);\n" +
            "        boolean error3 = resources.getBoolean(/*Expected resource of type bool*/android.R.drawable.btn_star/**/);\n" +
            "        if (testResourceTypeReturnValues(context, true) == /*Expected resource of type drawable*/R.string.app_name/**/) {\n" +
            "        }\n" +
            "        @SuppressWarnings(\"UnnecessaryLocalVariable\")\n" +
            "        int flow = R.string.app_name;\n" +
            "        @SuppressWarnings(\"UnnecessaryLocalVariable\")\n" +
            "        int flow2 = flow;\n" +
            "        boolean error4 = resources.getBoolean(/*Expected resource of type bool*/flow2/**/);\n" +
            "    }\n" +
            "\n" +
            "    @android.support.annotation.DrawableRes\n" +
            "    public int testResourceTypeReturnValues(Context context, boolean useString) {\n" +
            "        if (useString) {\n" +
            "            return /*Expected resource of type drawable*/R.string.app_name/**/; // error\n" +
            "        } else {\n" +
            "            return R.drawable.ic_launcher; // ok\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static final class R {\n" +
            "        public static final class drawable {\n" +
            "            public static final int ic_launcher=0x7f020057;\n" +
            "        }\n" +
            "        public static final class string {\n" +
            "            public static final int app_name=0x7f0a000e;\n" +
            "        }\n" +
            "    }\n" +
            "}\n");
  }

  public void testTypes2() {
        doCheck("package test.pkg;\n" +
                "\n" +
                "import android.support.annotation.DrawableRes;\n" +
                "import android.support.annotation.StringRes;\n" +
                "\n" +
                "enum X {\n" +
                "\n" +
                "    SKI(/*Expected resource of type drawable*/1/**/, /*Expected resource of type string*/2/**/),\n" +
                "    SNOWBOARD(/*Expected resource of type drawable*/3/**/, /*Expected resource of type string*/4/**/);\n" +
                "\n" +
                "    private final int mIconResId;\n" +
                "    private final int mLabelResId;\n" +
                "\n" +
                "    X(@DrawableRes int iconResId, @StringRes int labelResId) {\n" +
                "        mIconResId = iconResId;\n" +
                "        mLabelResId = labelResId;\n" +
                "    }\n" +
                "\n" +
                "}");
  }

  public void testIntDef() {
    doCheck("import android.annotation.SuppressLint;\n" +
            "import android.annotation.TargetApi;\n" +
            "import android.app.Notification;\n" +
            "import android.content.Context;\n" +
            "import android.content.Intent;\n" +
            "import android.content.ServiceConnection;\n" +
            "import android.content.res.Resources;\n" +
            "import android.os.Build;\n" +
            "import android.support.annotation.DrawableRes;\n" +
            "import android.view.View;\n" +
            "\n" +
            "import static android.content.Context.CONNECTIVITY_SERVICE;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "public class X {\n" +
            "\n" +
            "    @TargetApi(Build.VERSION_CODES.KITKAT)\n" +
            "    public void testStringDef(Context context, String unknown) {\n" +
            "        Object ok1 = context.getSystemService(unknown);\n" +
            "        Object ok2 = context.getSystemService(Context.CLIPBOARD_SERVICE);\n" +
            "        Object ok3 = context.getSystemService(android.content.Context.WINDOW_SERVICE);\n" +
            "        Object ok4 = context.getSystemService(CONNECTIVITY_SERVICE);\n" +
            "    }\n" +
            "\n" +
            "    @SuppressLint(\"UseCheckPermission\")\n" +
            "    @TargetApi(Build.VERSION_CODES.KITKAT)\n" +
            "    public void testIntDef(Context context, int unknown, View view) {\n" +
            "        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // OK\n" +
            "        view.setLayoutDirection(/*Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE*/View.TEXT_ALIGNMENT_TEXT_START/**/); // Error\n" +
            "        view.setLayoutDirection(/*Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE*/View.LAYOUT_DIRECTION_RTL | View.LAYOUT_DIRECTION_RTL/**/); // Error\n" +
            "    }\n" +
            "\n" +
            "    @TargetApi(Build.VERSION_CODES.KITKAT)\n" +
            "    public void testIntDefFlags(Context context, int unknown, Intent intent,\n" +
            "                           ServiceConnection connection) {\n" +
            "        // Flags\n" +
            "        Object ok1 = context.bindService(intent, connection, 0);\n" +
            "        Object ok2 = context.bindService(intent, connection, -1);\n" +
            "        Object ok3 = context.bindService(intent, connection, Context.BIND_ABOVE_CLIENT);\n" +
            "        Object ok4 = context.bindService(intent, connection, Context.BIND_ABOVE_CLIENT\n" +
            "                | Context.BIND_AUTO_CREATE);\n" +
            "        int flags1 = Context.BIND_ABOVE_CLIENT | Context.BIND_AUTO_CREATE;\n" +
            "        Object ok5 = context.bindService(intent, connection, flags1);\n" +
            "\n" +
            "        Object error1 = context.bindService(intent, connection,\n" +
            "                /*Must be one or more of: Context.BIND_AUTO_CREATE, Context.BIND_DEBUG_UNBIND, Context.BIND_NOT_FOREGROUND, Context.BIND_ABOVE_CLIENT, Context.BIND_ALLOW_OOM_MANAGEMENT, Context.BIND_WAIVE_PRIORITY, Context.BIND_IMPORTANT, Context.BIND_ADJUST_WITH_ACTIVITY*/Context.BIND_ABOVE_CLIENT | Context.CONTEXT_IGNORE_SECURITY/**/);\n" +
            "        int flags2 = Context.BIND_ABOVE_CLIENT | Context.CONTEXT_IGNORE_SECURITY;\n" +
            "        Object error2 = context.bindService(intent, connection, /*Must be one or more of: Context.BIND_AUTO_CREATE, Context.BIND_DEBUG_UNBIND, Context.BIND_NOT_FOREGROUND, Context.BIND_ABOVE_CLIENT, Context.BIND_ALLOW_OOM_MANAGEMENT, Context.BIND_WAIVE_PRIORITY, Context.BIND_IMPORTANT, Context.BIND_ADJUST_WITH_ACTIVITY*/flags2/**/);\n" +
            "    }\n" +
            "}\n");
  }

  public void testFlow() {
    doCheck("import android.content.res.Resources;\n" +
            "import android.support.annotation.DrawableRes;\n" +
            "import android.support.annotation.StringRes;\n" +
            "import android.support.annotation.StyleRes;\n" +
            "\n" +
            "import java.util.Random;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "public class X {\n" +
            "    public void testLiterals(Resources resources) {\n" +
            "        resources.getDrawable(0); // OK\n" +
            "        resources.getDrawable(-1); // OK\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/10/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testConstants(Resources resources) {\n" +
            "        resources.getDrawable(R.drawable.my_drawable); // OK\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/R.string.my_string/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testFields(String fileExt, Resources resources) {\n" +
            "        int mimeIconId = MimeTypes.styleAndDrawable;\n" +
            "        resources.getDrawable(mimeIconId); // OK\n" +
            "\n" +
            "        int s1 = MimeTypes.style;\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/s1/**/); // ERROR\n" +
            "        int s2 = MimeTypes.styleAndDrawable;\n" +
            "        resources.getDrawable(s2); // OK\n" +
            "        int w3 = MimeTypes.drawable;\n" +
            "        resources.getDrawable(w3); // OK\n" +
            "\n" +
            "        // Direct reference\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/MimeTypes.style/**/); // ERROR\n" +
            "        resources.getDrawable(MimeTypes.styleAndDrawable); // OK\n" +
            "        resources.getDrawable(MimeTypes.drawable); // OK\n" +
            "    }\n" +
            "\n" +
            "    public void testCalls(String fileExt, Resources resources) {\n" +
            "        int mimeIconId = MimeTypes.getIconForExt(fileExt);\n" +
            "        resources.getDrawable(mimeIconId); // OK\n" +
            "        resources.getDrawable(MimeTypes.getInferredString()); // OK (wrong but can't infer type)\n" +
            "        resources.getDrawable(MimeTypes.getInferredDrawable()); // OK\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/MimeTypes.getAnnotatedString()/**/); // Error\n" +
            "        resources.getDrawable(MimeTypes.getAnnotatedDrawable()); // OK\n" +
            "        resources.getDrawable(MimeTypes.getUnknownType()); // OK (unknown/uncertain)\n" +
            "    }\n" +
            "\n" +
            "    private static class MimeTypes {\n" +
            "        @android.support.annotation.StyleRes\n" +
            "        @android.support.annotation.DrawableRes\n" +
            "        public static int styleAndDrawable;\n" +
            "\n" +
            "        @android.support.annotation.StyleRes\n" +
            "        public static int style;\n" +
            "\n" +
            "        @android.support.annotation.DrawableRes\n" +
            "        public static int drawable;\n" +
            "\n" +
            "        @android.support.annotation.DrawableRes\n" +
            "        public static int getIconForExt(String ext) {\n" +
            "            return R.drawable.my_drawable;\n" +
            "        }\n" +
            "\n" +
            "        public static int getInferredString() {\n" +
            "            // Implied string - can we handle this?\n" +
            "            return R.string.my_string;\n" +
            "        }\n" +
            "\n" +
            "        public static int getInferredDrawable() {\n" +
            "            // Implied drawable - can we handle this?\n" +
            "            return R.drawable.my_drawable;\n" +
            "        }\n" +
            "\n" +
            "        @android.support.annotation.StringRes\n" +
            "        public static int getAnnotatedString() {\n" +
            "            return R.string.my_string;\n" +
            "        }\n" +
            "\n" +
            "        @android.support.annotation.DrawableRes\n" +
            "        public static int getAnnotatedDrawable() {\n" +
            "            return R.drawable.my_drawable;\n" +
            "        }\n" +
            "\n" +
            "        public static int getUnknownType() {\n" +
            "            return new Random(1000).nextInt();\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static final class R {\n" +
            "        public static final class drawable {\n" +
            "            public static final int my_drawable =0x7f020057;\n" +
            "        }\n" +
            "        public static final class string {\n" +
            "            public static final int my_string =0x7f0a000e;\n" +
            "        }\n" +
            "    }\n" +
            "}\n");
  }

  public void testColorAsDrawable() {
    doCheck("package p1.p2;\n" +
            "\n" +
            "import android.content.Context;\n" +
            "import android.view.View;\n" +
            "\n" +
            "public class X {\n" +
            "    static void test(Context context) {\n" +
            "        View separator = new View(context);\n" +
            "        separator.setBackgroundResource(android.R.color.black);\n" +
            "    }\n" +
            "}\n");
  }

  public void testMipmap() {
    doCheck("package p1.p2;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "\n" +
            "public class X extends Activity {\n" +
            "  public void test() {\n" +
            "    Object o = getResources().getDrawable(R.mipmap.ic_launcher);\n" +
            "  }\n" +
            "\n" +
            "  public static final class R {\n" +
            "    public static final class drawable {\n" +
            "      public static int icon=0x7f020000;\n" +
            "    }\n" +
            "    public static final class mipmap {\n" +
            "      public static int ic_launcher=0x7f020001;\n" +
            "    }\n" +
            "  }\n" +
            "}");
  }

  public void testRanges() {
    doCheck("import android.support.annotation.FloatRange;\n" +
            "import android.support.annotation.IntRange;\n" +
            "import android.support.annotation.Size;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "class X {\n" +
            "    public void printExact(@Size(5) String arg) { System.out.println(arg); }\n" +
            "    public void printMin(@Size(min=5) String arg) { }\n" +
            "    public void printMax(@Size(max=8) String arg) { }\n" +
            "    public void printRange(@Size(min=4,max=6) String arg) { }\n" +
            "    public void printExact(@Size(5) int[] arg) { }\n" +
            "    public void printMin(@Size(min=5) int[] arg) { }\n" +
            "    public void printMax(@Size(max=8) int[] arg) { }\n" +
            "    public void printRange(@Size(min=4,max=6) int[] arg) { }\n" +
            "    public void printMultiple(@Size(multiple=3) int[] arg) { }\n" +
            "    public void printMinMultiple(@Size(min=4,multiple=3) int[] arg) { }\n" +
            "    public void printAtLeast(@IntRange(from=4) int arg) { }\n" +
            "    public void printAtMost(@IntRange(to=7) int arg) { }\n" +
            "    public void printBetween(@IntRange(from=4,to=7) int arg) { }\n" +
            "    public void printAtLeastInclusive(@FloatRange(from=2.5) float arg) { }\n" +
            "    public void printAtLeastExclusive(@FloatRange(from=2.5,fromInclusive=false) float arg) { }\n" +
            "    public void printAtMostInclusive(@FloatRange(to=7) double arg) { }\n" +
            "    public void printAtMostExclusive(@FloatRange(to=7,toInclusive=false) double arg) { }\n" +
            "    public void printBetweenFromInclusiveToInclusive(@FloatRange(from=2.5,to=5.0) float arg) { }\n" +
            "    public void printBetweenFromExclusiveToInclusive(@FloatRange(from=2.5,to=5.0,fromInclusive=false) float arg) { }\n" +
            "    public void printBetweenFromInclusiveToExclusive(@FloatRange(from=2.5,to=5.0,toInclusive=false) float arg) { }\n" +
            "    public void printBetweenFromExclusiveToExclusive(@FloatRange(from=2.5,to=5.0,fromInclusive=false,toInclusive=false) float arg) { }\n" +
            "    public static final int MINIMUM = -1;\n" +
            "    public static final int MAXIMUM = 42;\n" +
            "    public static final int SIZE = 5;\n" +
            "    public void printIndirect(@IntRange(from = MINIMUM, to = MAXIMUM) int arg) { }\n" +
            "    public void printIndirectSize(@Size(SIZE) String arg) { }\n" +
            "\n" +
            "    public void testLength() {\n" +
            "        String arg = \"1234\";\n" +
            "        printExact(/*Length must be exactly 5*/arg/**/); // ERROR\n" +
            "\n" +
            "\n" +
            "        printExact(/*Length must be exactly 5*/\"1234\"/**/); // ERROR\n" +
            "        printExact(\"12345\"); // OK\n" +
            "        printExact(/*Length must be exactly 5*/\"123456\"/**/); // ERROR\n" +
            "\n" +
            "        printMin(/*Length must be at least 5 (was 4)*/\"1234\"/**/); // ERROR\n" +
            "        printMin(\"12345\"); // OK\n" +
            "        printMin(\"123456\"); // OK\n" +
            "\n" +
            "        printMax(\"123456\"); // OK\n" +
            "        printMax(\"1234567\"); // OK\n" +
            "        printMax(\"12345678\"); // OK\n" +
            "        printMax(/*Length must be at most 8 (was 9)*/\"123456789\"/**/); // ERROR\n" +
            "        printAtMost(1 << 2); // OK\n" +
            "        printMax(\"123456\" + \"\"); //OK\n" +
            "        printAtMost(/*Value must be ≤ 7 (was 8)*/1 << 2 + 1/**/); // ERROR\n" +
            "        printAtMost(/*Value must be ≤ 7 (was 32)*/1 << 5/**/); // ERROR\n" +
            "        printMax(/*Length must be at most 8 (was 11)*/\"123456\" + \"45678\"/**/); //ERROR\n" +
            "\n" +
            "        printRange(/*Length must be at least 4 and at most 6 (was 3)*/\"123\"/**/); // ERROR\n" +
            "        printRange(\"1234\"); // OK\n" +
            "        printRange(\"12345\"); // OK\n" +
            "        printRange(\"123456\"); // OK\n" +
            "        printRange(/*Length must be at least 4 and at most 6 (was 7)*/\"1234567\"/**/); // ERROR\n" +
            "        printIndirectSize(/*Length must be exactly 5*/\"1234567\"/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testSize() {\n" +
            "        printExact(/*Size must be exactly 5*/new int[]{1, 2, 3, 4}/**/); // ERROR\n" +
            "        printExact(new int[]{1, 2, 3, 4, 5}); // OK\n" +
            "        printExact(/*Size must be exactly 5*/new int[]{1, 2, 3, 4, 5, 6}/**/); // ERROR\n" +
            "\n" +
            "        printMin(/*Size must be at least 5 (was 4)*/new int[]{1, 2, 3, 4}/**/); // ERROR\n" +
            "        printMin(new int[]{1, 2, 3, 4, 5}); // OK\n" +
            "        printMin(new int[]{1, 2, 3, 4, 5, 6}); // OK\n" +
            "\n" +
            "        printMax(new int[]{1, 2, 3, 4, 5, 6}); // OK\n" +
            "        printMax(new int[]{1, 2, 3, 4, 5, 6, 7}); // OK\n" +
            "        printMax(new int[]{1, 2, 3, 4, 5, 6, 7, 8}); // OK\n" +
            "        printMax(new int[]{1, 2, 3, 4, 5, 6, 7, 8}); // OK\n" +
            "        printMax(/*Size must be at most 8 (was 9)*/new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}/**/); // ERROR\n" +
            "\n" +
            "        printRange(/*Size must be at least 4 and at most 6 (was 3)*/new int[] {1,2,3}/**/); // ERROR\n" +
            "        printRange(new int[] {1,2,3,4}); // OK\n" +
            "        printRange(new int[] {1,2,3,4,5}); // OK\n" +
            "        printRange(new int[] {1,2,3,4,5,6}); // OK\n" +
            "        printRange(/*Size must be at least 4 and at most 6 (was 7)*/new int[] {1,2,3,4,5,6,7}/**/); // ERROR\n" +
            "\n" +
            "        printMultiple(new int[] {1,2,3}); // OK\n" +
            "        printMultiple(/*Size must be a multiple of 3 (was 4)*/new int[] {1,2,3,4}/**/); // ERROR\n" +
            "        printMultiple(/*Size must be a multiple of 3 (was 5)*/new int[] {1,2,3,4,5}/**/); // ERROR\n" +
            "        printMultiple(new int[] {1,2,3,4,5,6}); // OK\n" +
            "        printMultiple(/*Size must be a multiple of 3 (was 7)*/new int[] {1,2,3,4,5,6,7}/**/); // ERROR\n" +
            "\n" +
            "        printMinMultiple(new int[] {1,2,3,4,5,6}); // OK\n" +
            "        printMinMultiple(/*Size must be at least 4 and a multiple of 3 (was 3)*/new int[]{1, 2, 3}/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testSize2(int[] unknownSize) {\n" +
            "        int[] location1 = new int[5];\n" +
            "        printExact(location1);\n" +
            "        int[] location2 = new int[6];\n" +
            "        printExact(/*Size must be exactly 5*/location2/**/);\n" +
            "        printExact(unknownSize);\n" +
            "    }\n" +
            "\n" +
            "    public void testIntRange() {\n" +
            "        printAtLeast(/*Value must be ≥ 4 (was 3)*/3/**/); // ERROR\n" +
            "        printAtLeast(4); // OK\n" +
            "        printAtLeast(5); // OK\n" +
            "\n" +
            "        printAtMost(5); // OK\n" +
            "        printAtMost(6); // OK\n" +
            "        printAtMost(7); // OK\n" +
            "        printAtMost(/*Value must be ≤ 7 (was 8)*/8/**/); // ERROR\n" +
            "\n" +
            "        printBetween(/*Value must be ≥ 4 and ≤ 7 (was 3)*/3/**/); // ERROR\n" +
            "        printBetween(4); // OK\n" +
            "        printBetween(5); // OK\n" +
            "        printBetween(6); // OK\n" +
            "        printBetween(7); // OK\n" +
            "        printBetween(/*Value must be ≥ 4 and ≤ 7 (was 8)*/8/**/); // ERROR\n" +
            "        int value = 8;\n" +
            "        printBetween(/*Value must be ≥ 4 and ≤ 7 (was 8)*/value/**/); // ERROR\n" +
            "        printBetween(/*Value must be ≥ 4 and ≤ 7 (was -7)*/-7/**/);\n" +
            "        printIndirect(/*Value must be ≥ -1 and ≤ 42 (was -2)*/-2/**/);\n" +
            "    }\n" +
            "\n" +
            "    public void testFloatRange() {\n" +
            "        printAtLeastInclusive(/*Value must be ≥ 2.5 (was 2.49f)*/2.49f/**/); // ERROR\n" +
            "        printAtLeastInclusive(2.5f); // OK\n" +
            "        printAtLeastInclusive(2.6f); // OK\n" +
            "\n" +
            "        printAtLeastExclusive(/*Value must be > 2.5 (was 2.49f)*/2.49f/**/); // ERROR\n" +
            "        printAtLeastExclusive(/*Value must be > 2.5 (was 2.5f)*/2.5f/**/); // ERROR\n" +
            "        printAtLeastExclusive(2.501f); // OK\n" +
            "        printAtLeastExclusive(/*Value must be > 2.5 (was -10.0)*/-10/**/);\n" +
            "\n" +
            "        printAtMostInclusive(6.8f); // OK\n" +
            "        printAtMostInclusive(6.9f); // OK\n" +
            "        printAtMostInclusive(7.0f); // OK\n" +
            "        printAtMostInclusive(/*Value must be ≤ 7.0 (was 7.1f)*/7.1f/**/); // ERROR\n" +
            "\n" +
            "        printAtMostExclusive(6.9f); // OK\n" +
            "        printAtMostExclusive(6.99f); // OK\n" +
            "        printAtMostExclusive(/*Value must be < 7.0 (was 7.0f)*/7.0f/**/); // ERROR\n" +
            "        printAtMostExclusive(/*Value must be < 7.0 (was 7.1f)*/7.1f/**/); // ERROR\n" +
            "\n" +
            "        printBetweenFromInclusiveToInclusive(/*Value must be ≥ 2.5 and ≤ 5.0 (was 2.4f)*/2.4f/**/); // ERROR\n" +
            "        printBetweenFromInclusiveToInclusive(2.5f); // OK\n" +
            "        printBetweenFromInclusiveToInclusive(3f); // OK\n" +
            "        printBetweenFromInclusiveToInclusive(5.0f); // OK\n" +
            "        printBetweenFromInclusiveToInclusive(/*Value must be ≥ 2.5 and ≤ 5.0 (was 5.1f)*/5.1f/**/); // ERROR\n" +
            "\n" +
            "        printBetweenFromExclusiveToInclusive(/*Value must be > 2.5 and ≤ 5.0 (was 2.4f)*/2.4f/**/); // ERROR\n" +
            "        printBetweenFromExclusiveToInclusive(/*Value must be > 2.5 and ≤ 5.0 (was 2.5f)*/2.5f/**/); // ERROR\n" +
            "        printBetweenFromExclusiveToInclusive(5.0f); // OK\n" +
            "        printBetweenFromExclusiveToInclusive(/*Value must be > 2.5 and ≤ 5.0 (was 5.1f)*/5.1f/**/); // ERROR\n" +
            "\n" +
            "        printBetweenFromInclusiveToExclusive(/*Value must be ≥ 2.5 and < 5.0 (was 2.4f)*/2.4f/**/); // ERROR\n" +
            "        printBetweenFromInclusiveToExclusive(2.5f); // OK\n" +
            "        printBetweenFromInclusiveToExclusive(3f); // OK\n" +
            "        printBetweenFromInclusiveToExclusive(4.99f); // OK\n" +
            "        printBetweenFromInclusiveToExclusive(/*Value must be ≥ 2.5 and < 5.0 (was 5.0f)*/5.0f/**/); // ERROR\n" +
            "\n" +
            "        printBetweenFromExclusiveToExclusive(/*Value must be > 2.5 and < 5.0 (was 2.4f)*/2.4f/**/); // ERROR\n" +
            "        printBetweenFromExclusiveToExclusive(/*Value must be > 2.5 and < 5.0 (was 2.5f)*/2.5f/**/); // ERROR\n" +
            "        printBetweenFromExclusiveToExclusive(2.51f); // OK\n" +
            "        printBetweenFromExclusiveToExclusive(4.99f); // OK\n" +
            "        printBetweenFromExclusiveToExclusive(/*Value must be > 2.5 and < 5.0 (was 5.0f)*/5.0f/**/); // ERROR\n" +
            "    }\n" +
            "}\n");
  }

  public void testColorInt() {
    doCheck("import android.app.Activity;\n" +
            "import android.graphics.Paint;\n" +
            "import android.widget.TextView;\n" +
            "\n" +
            "public class X extends Activity {\n" +
            "    public void foo(TextView textView, int foo) {\n" +
            "        Paint paint2 = new Paint();\n" +
            "        paint2.setColor(/*Should pass resolved color instead of resource id here: `getResources().getColor(R.color.blue)`*/R.color.blue/**/);\n" +
            "        // Wrong\n" +
            "        textView.setTextColor(/*Should pass resolved color instead of resource id here: `getResources().getColor(R.color.red)`*/R.color.red/**/);\n" +
            "        textView.setTextColor(/*Should pass resolved color instead of resource id here: `getResources().getColor(android.R.color.black)`*/android.R.color.black/**/);\n" +
            "        textView.setTextColor(/*Should pass resolved color instead of resource id here: `getResources().getColor(foo > 0 ? R.color.green : R.color.blue)`*/foo > 0 ? R.color.green : R.color.blue/**/);\n" +
            "        // OK\n" +
            "        textView.setTextColor(getResources().getColor(R.color.red));\n" +
            "        // OK\n" +
            "        foo1(R.color.blue);\n" +
            "        foo2(0xffff0000);\n" +
            "        // Wrong\n" +
            "        foo1(/*Expected resource of type color*/0xffff0000/**/);\n" +
            "        foo2(/*Should pass resolved color instead of resource id here: `getResources().getColor(R.color.blue)`*/R.color.blue/**/);\n" +
            "    }\n" +
            "\n" +
            "    private void foo1(@android.support.annotation.ColorRes int c) {\n" +
            "    }\n" +
            "\n" +
            "    private void foo2(@android.support.annotation.ColorInt int c) {\n" +
            "    }\n" +
            "\n" +
            "    private static class R {\n" +
            "        private static class color {\n" +
            "            public static final int red=0x7f060000;\n" +
            "            public static final int green=0x7f060001;\n" +
            "            public static final int blue=0x7f060002;\n" +
            "        }\n" +
            "    }\n" +
            "}\n");
  }

  public void testColorInt2() {
    doCheck("package test.pkg;\n" +
            "import android.content.Context;\n" +
            "import android.content.res.Resources;\n" +
            "import android.support.annotation.ColorInt;\n" +
            "import android.support.annotation.ColorRes;\n" +
            "\n" +
            "public abstract class X {\n" +
            "    @ColorInt\n" +
            "    public abstract int getColor1();\n" +
            "    public abstract void setColor1(@ColorRes int color);\n" +
            "    @ColorRes\n" +
            "    public abstract int getColor2();\n" +
            "    public abstract void setColor2(@ColorInt int color);\n" +
            "\n" +
            "    public void test1(Context context) {\n" +
            "        int actualColor = getColor1();\n" +
            "        setColor1(/*Expected resource of type color*/actualColor/**/); // ERROR\n" +
            "        setColor1(/*Expected resource of type color*/getColor1()/**/); // ERROR\n" +
            "        setColor1(getColor2()); // OK\n" +
            "    }\n" +
            "    public void test2(Context context) {\n" +
            "        int actualColor = getColor2();\n" +
            "        setColor2(/*Should pass resolved color instead of resource id here: `getResources().getColor(actualColor)`*/actualColor/**/); // ERROR\n" +
            "        setColor2(/*Should pass resolved color instead of resource id here: `getResources().getColor(getColor2())`*/getColor2()/**/); // ERROR\n" +
            "        setColor2(getColor1()); // OK\n" +
            "    }\n" +
            "}\n");
  }

  public void testCheckResult() {
    doCheck("import android.Manifest;\n" +
            "import android.content.Context;\n" +
            "import android.content.pm.PackageManager;\n" +
            "import android.graphics.Bitmap;\n" +
            "\n" +
            "public class X {\n" +
            "    private void foo(Context context) {\n" +
            "        /*The result of 'checkCallingOrSelfPermission' is not used; did you mean to call 'enforceCallingOrSelfPermission(String,String)'?*/context.checkCallingOrSelfPermission(Manifest.permission.INTERNET)/**/; // WRONG\n" +
            "        /*The result of 'checkPermission' is not used; did you mean to call 'enforcePermission(String,int,int,String)'?*/context.checkPermission(Manifest.permission.INTERNET, 1, 1)/**/;\n" +
            "        check(context.checkCallingOrSelfPermission(Manifest.permission.INTERNET)); // OK\n" +
            "        int check = context.checkCallingOrSelfPermission(Manifest.permission.INTERNET); // OK\n" +
            "        if (context.checkCallingOrSelfPermission(Manifest.permission.INTERNET) // OK\n" +
            "                != PackageManager.PERMISSION_GRANTED) {\n" +
            "            showAlert(context, \"Error\",\n" +
            "                    \"Application requires permission to access the Internet\");\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    private Bitmap checkResult(Bitmap bitmap) {\n" +
            "        /*The result 'extractAlpha' is not used*/bitmap.extractAlpha()/**/; // WARNING\n" +
            "        Bitmap bitmap2 = bitmap.extractAlpha(); // OK\n" +
            "        call(bitmap.extractAlpha()); // OK\n" +
            "        return bitmap.extractAlpha(); // OK\n" +
            "    }\n" +
            "\n" +
            "    private void showAlert(Context context, String error, String s) {\n" +
            "    }\n" +
            "\n" +
            "    private void check(int i) {\n" +
            "    }\n" +
            "    private void call(Bitmap bitmap) {\n" +
            "    }\n" +
            "}");
  }

  public void testMissingPermission() {
    doCheck("import android.Manifest;\n" +
            "import android.content.Context;\n" +
            "import android.content.pm.PackageManager;\n" +
            "import android.graphics.Bitmap;\n" +
            "import android.support.annotation.RequiresPermission;\n" +
            "\n" +
            "import static android.Manifest.permission.ACCESS_COARSE_LOCATION;\n" +
            "import static android.Manifest.permission.ACCESS_FINE_LOCATION;\n" +
            "\n" +
            "public class X {\n" +
            "    private static void foo(Context context, LocationManager manager) {\n" +
            "        /*Missing permissions required by LocationManager.myMethod: android.permission.ACCESS_FINE_LOCATION or android.permission.ACCESS_COARSE_LOCATION*/manager.myMethod(\"myprovider\")/**/;\n" +
            "    }\n" +
            "\n" +
            "    @SuppressWarnings(\"UnusedDeclaration\")\n" +
            "    public abstract class LocationManager {\n" +
            "        @RequiresPermission(anyOf = {ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION})\n" +
            "        public abstract Location myMethod(String provider);\n" +
            "        public class Location {\n" +
            "        }\n" +
            "    }\n" +
            "}");
  }

  public void testImpliedPermissions() {
    // Regression test for
    //   https://code.google.com/p/android/issues/detail?id=177381
    doCheck("package test.pkg;\n" +
            "import android.support.annotation.RequiresPermission;\n" +
            "\n" +
            "public class X {\n" +
            "    @RequiresPermission(allOf = {\"my.permission.PERM1\",\"my.permission.PERM2\"})\n" +
            "    public void method1() {\n" +
            "    }\n" +
            "\n" +
            "    @RequiresPermission(\"my.permission.PERM1\")\n" +
            "    public void method2() {\n" +
            "        /*Missing permissions required by X.method1: my.permission.PERM2*/method1()/**/;\n" +
            "    }\n" +
            "\n" +
            "    @RequiresPermission(allOf = {\"my.permission.PERM1\",\"my.permission.PERM2\"})\n" +
            "    public void method3() {\n" +
            "        // The above @RequiresPermission implies that we are holding these\n" +
            "        // permissions here, so the call to method1() should not be flagged as\n" +
            "        // missing a permission!\n" +
            "        method1();\n" +
            "    }\n" +
            "}\n");
  }

  public void testLibraryRevocablePermission() {
    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.support.annotation.RequiresPermission;\n" +
            "\n" +
            "public class X {\n" +
            "    public void something() {\n" +
            "        /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with `checkPermission`) or handle a potential `SecurityException`*/methodRequiresDangerous()/**/;\n" +
            "        methodRequiresNormal();\n" +
            "    }\n" +
            "\n" +
            "    @RequiresPermission(\"my.normal.P1\")\n" +
            "    public void methodRequiresNormal() {\n" +
            "    }\n" +
            "\n" +
            "    @RequiresPermission(\"my.dangerous.P2\")\n" +
            "    public void methodRequiresDangerous() {\n" +
            "    }\n" +
            "}\n");
  }

  public void testIntentsAndContentResolvers() {
    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.Manifest;\n" +
            "import android.app.Activity;\n" +
            "import android.content.ContentResolver;\n" +
            "import android.content.Context;\n" +
            "import android.content.Intent;\n" +
            "import android.net.Uri;\n" +
            "import android.support.annotation.RequiresPermission;\n" +
            "\n" +
            "import static android.Manifest.permission.READ_HISTORY_BOOKMARKS;\n" +
            "import static android.Manifest.permission.WRITE_HISTORY_BOOKMARKS;\n" +
            "\n" +
            "@SuppressWarnings({\"deprecation\", \"unused\"})\n" +
            "public class X {\n" +
            "    @RequiresPermission(Manifest.permission.CALL_PHONE)\n" +
            "    public static final String ACTION_CALL = \"android.intent.action.CALL\";\n" +
            "\n" +
            "    @RequiresPermission.Read(@RequiresPermission(READ_HISTORY_BOOKMARKS))\n" +
            "    @RequiresPermission.Write(@RequiresPermission(WRITE_HISTORY_BOOKMARKS))\n" +
            "    public static final Uri BOOKMARKS_URI = Uri.parse(\"content://browser/bookmarks\");\n" +
            "\n" +
            "    public static final Uri COMBINED_URI = Uri.withAppendedPath(BOOKMARKS_URI, \"bookmarks\");\n" +
            "\n" +
            "    public static void activities1(Activity activity) {\n" +
            "        Intent intent = new Intent(Intent.ACTION_CALL);\n" +
            "        intent.setData(Uri.parse(\"tel:1234567890\"));\n" +
            "        // This one will only be flagged if we have framework metadata on Intent.ACTION_CALL\n" +
            "        activity.startActivity(intent);\n" +
            "    }\n" +
            "\n" +
            "    public static void activities2(Activity activity) {\n" +
            "        Intent intent = new Intent(ACTION_CALL);\n" +
            "        intent.setData(Uri.parse(\"tel:1234567890\"));\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivity(intent)/**/;\n" +
            "    }\n" +
            "\n" +
            "    public static void activities3(Activity activity) {\n" +
            "        Intent intent;\n" +
            "        intent = new Intent(ACTION_CALL);\n" +
            "        intent.setData(Uri.parse(\"tel:1234567890\"));\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivity(intent)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivity(intent, null)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivityForResult(intent, 0)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivityFromChild(activity, intent, 0)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivityIfNeeded(intent, 0)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivityFromFragment(null, intent, 0)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startNextMatchingActivity(intent)/**/;\n" +
            "        startActivity(\"\"); // Not an error!\n" +
            "    }\n" +
            "\n" +
            "    public static void broadcasts(Context context) {\n" +
            "        Intent intent;\n" +
            "        intent = new Intent(ACTION_CALL);\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/context.sendBroadcast(intent)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/context.sendBroadcast(intent, \"\")/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/context.sendBroadcastAsUser(intent, null)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/context.sendStickyBroadcast(intent)/**/;\n" +
            "    }\n" +
            "\n" +
            "    public static void contentResolvers(Context context, ContentResolver resolver) {\n" +
            "        // read\n" +
            "        /*Missing permissions required to read X.BOOKMARKS_URI: com.android.browser.permission.READ_HISTORY_BOOKMARKS*/resolver.query(BOOKMARKS_URI, null, null, null, null)/**/;\n" +
            "\n" +
            "        // write\n" +
            "        /*Missing permissions required to write X.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS*/resolver.insert(BOOKMARKS_URI, null)/**/;\n" +
            "        /*Missing permissions required to write X.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS*/resolver.delete(BOOKMARKS_URI, null, null)/**/;\n" +
            "        /*Missing permissions required to write X.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS*/resolver.update(BOOKMARKS_URI, null, null, null)/**/;\n" +
            "\n" +
            "        // Framework (external) annotation\n" +
            "        /*Missing permissions required to write Browser.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS*/resolver.update(android.provider.Browser.BOOKMARKS_URI, null, null, null)/**/;\n" +
            "\n" +
            "        // URI manipulations\n" +
            "        /*Missing permissions required to write X.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS*/resolver.insert(COMBINED_URI, null)/**/;\n" +
            "    }\n" +
            "\n" +
            "    public static void startActivity(Object other) {\n" +
            "        // Unrelated\n" +
            "    }\n" +
            "}\n");
  }

  public void testWrongThread() {
    doCheck("import android.support.annotation.MainThread;\n" +
            "import android.support.annotation.UiThread;\n" +
            "import android.support.annotation.WorkerThread;\n" +
            "\n" +
            "public class X {\n" +
            "    public AsyncTask testTask() {\n" +
            "\n" +
            "        return new AsyncTask() {\n" +
            "            final CustomView view = new CustomView();\n" +
            "\n" +
            "            @Override\n" +
            "            protected void doInBackground(Object... params) {\n" +
            "                /*Method onPreExecute must be called from the main thread, currently inferred thread is worker*/onPreExecute()/**/; // ERROR\n" +
            "                /*Method paint must be called from the UI thread, currently inferred thread is worker*/view.paint()/**/; // ERROR\n" +
            "                publishProgress(); // OK\n" +
            "            }\n" +
            "\n" +
            "            @Override\n" +
            "            protected void onPreExecute() {\n" +
            "                /*Method publishProgress must be called from the worker thread, currently inferred thread is main*/publishProgress()/**/; // ERROR\n" +
            "                onProgressUpdate(); // OK\n" +
            "            }\n" +
            "        };\n" +
            "    }\n" +
            "\n" +
            "    @UiThread\n" +
            "    public static class View {\n" +
            "        public void paint() {\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static class CustomView extends View {\n" +
            "    }\n" +
            "\n" +
            "    public static abstract class AsyncTask {\n" +
            "        @WorkerThread\n" +
            "        protected abstract void doInBackground(Object... params);\n" +
            "\n" +
            "        @MainThread\n" +
            "        protected void onPreExecute() {\n" +
            "        }\n" +
            "\n" +
            "        @MainThread\n" +
            "        protected void onProgressUpdate(Object... values) {\n" +
            "        }\n" +
            "\n" +
            "        @WorkerThread\n" +
            "        protected final void publishProgress(Object... values) {\n" +
            "        }\n" +
            "    }\n" +
            "}\n");
  }

  public void testCombinedIntDefAndIntRange() throws Exception {
    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.support.annotation.IntDef;\n" +
            "import android.support.annotation.IntRange;\n" +
            "\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "\n" +
            "@SuppressWarnings({\"UnusedParameters\", \"unused\", \"SpellCheckingInspection\"})\n" +
            "public class X {\n" +
            "\n" +
            "    public static final int UNRELATED = 500;\n" +
            "\n" +
            "    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})\n" +
            "    @IntRange(from = 10)\n" +
            "    @Retention(RetentionPolicy.SOURCE)\n" +
            "    public @interface Duration {}\n" +
            "\n" +
            "    public static final int LENGTH_INDEFINITE = -2;\n" +
            "    public static final int LENGTH_SHORT = -1;\n" +
            "    public static final int LENGTH_LONG = 0;\n" +
            "    public void setDuration(@Duration int duration) {\n" +
            "    }\n" +
            "\n" +
            "    public void test() {\n" +
            "        setDuration(/*Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG or value must be ≥ 10 (was 500)*/UNRELATED/**/); /// ERROR: Not right intdef, even if it's in the right number range\n" +
            "        setDuration(/*Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG or value must be ≥ 10 (was -5)*/-5/**/); // ERROR (not right int def or value\n" +
            "        setDuration(/*Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG or value must be ≥ 10 (was 8)*/8/**/); // ERROR (not matching number range)\n" +
            "        setDuration(8000); // OK (@IntRange applies)\n" +
            "        setDuration(LENGTH_INDEFINITE); // OK (@IntDef)\n" +
            "        setDuration(LENGTH_LONG); // OK (@IntDef)\n" +
            "        setDuration(LENGTH_SHORT); // OK (@IntDef)\n" +
            "    }\n" +
            "}\n");
  }

  @Override
  protected String[] getEnvironmentClasses() {
    @Language("JAVA")
    String header = "package android.support.annotation;\n" +
                    "\n" +
                    "import java.lang.annotation.Documented;\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.Target;\n" +
                    "\n" +
                    "import static java.lang.annotation.ElementType.ANNOTATION_TYPE;\n" +
                    "import static java.lang.annotation.ElementType.CONSTRUCTOR;\n" +
                    "import static java.lang.annotation.ElementType.FIELD;\n" +
                    "import static java.lang.annotation.ElementType.LOCAL_VARIABLE;\n" +
                    "import static java.lang.annotation.ElementType.METHOD;\n" +
                    "import static java.lang.annotation.ElementType.PARAMETER;\n" +
                    "import static java.lang.annotation.ElementType.TYPE;\n" +
                    "import static java.lang.annotation.RetentionPolicy.SOURCE;\n" +
                    "import static java.lang.annotation.RetentionPolicy.CLASS;\n" +
                    "\n";

    List<String> classes = Lists.newArrayList();
    @Language("JAVA")
    String floatRange = "@Retention(CLASS)\n" +
                        "@Target({CONSTRUCTOR,METHOD,PARAMETER,FIELD,LOCAL_VARIABLE})\n" +
                        "public @interface FloatRange {\n" +
                        "    double from() default Double.NEGATIVE_INFINITY;\n" +
                        "    double to() default Double.POSITIVE_INFINITY;\n" +
                        "    boolean fromInclusive() default true;\n" +
                        "    boolean toInclusive() default true;\n" +
                        "}";
    classes.add(header + floatRange);

    @Language("JAVA")
    String intRange = "@Retention(CLASS)\n" +
                      "@Target({CONSTRUCTOR,METHOD,PARAMETER,FIELD,LOCAL_VARIABLE,ANNOTATION_TYPE})\n" +
                      "public @interface IntRange {\n" +
                      "    long from() default Long.MIN_VALUE;\n" +
                      "    long to() default Long.MAX_VALUE;\n" +
                      "}";
    classes.add(header + intRange);

    @Language("JAVA")
    String size = "@Retention(CLASS)\n" +
                  "@Target({PARAMETER, LOCAL_VARIABLE, METHOD, FIELD})\n" +
                  "public @interface Size {\n" +
                  "    long value() default -1;\n" +
                  "    long min() default Long.MIN_VALUE;\n" +
                  "    long max() default Long.MAX_VALUE;\n" +
                  "    long multiple() default 1;\n" +
                  "}";
    classes.add(header + size);

    @Language("JAVA")
    String permission = "@Retention(SOURCE)\n" +
                        "@Target({ANNOTATION_TYPE,METHOD,CONSTRUCTOR,FIELD})\n" +
                        "public @interface RequiresPermission {\n" +
                        "    String value() default \"\";\n" +
                        "    String[] allOf() default {};\n" +
                        "    String[] anyOf() default {};\n" +
                        "    boolean conditional() default false;\n" +
                        "    @Target(FIELD)\n" +
                        "    @interface Read {\n" +
                        "        RequiresPermission value();\n" +
                        "    }\n" +
                        "    @Target(FIELD)\n" +
                        "    @interface Write {\n" +
                        "        RequiresPermission value();\n" +
                        "    }\n" +
                        "}\n";
    classes.add(header + permission);

    @Language("JAVA")
    String uiThread = "@Retention(SOURCE)\n" +
                      "@Target({METHOD,CONSTRUCTOR,TYPE})\n" +
                      "public @interface UiThread {\n" +
                      "}";
    classes.add(header + uiThread);

    @Language("JAVA")
    String mainThread = "@Retention(SOURCE)\n" +
                        "@Target({METHOD,CONSTRUCTOR,TYPE})\n" +
                        "public @interface MainThread {\n" +
                        "}";
    classes.add(header + mainThread);

    @Language("JAVA")
    String workerThread = "@Retention(SOURCE)\n" +
                          "@Target({METHOD,CONSTRUCTOR,TYPE})\n" +
                          "public @interface WorkerThread {\n" +
                          "}";
    classes.add(header + workerThread);

    @Language("JAVA")
    String binderThread = "@Retention(SOURCE)\n" +
                          "@Target({METHOD,CONSTRUCTOR,TYPE})\n" +
                          "public @interface BinderThread {\n" +
                          "}";
    classes.add(header + binderThread);

    @Language("JAVA")
    String colorInt = "@Retention(SOURCE)\n" +
                      "@Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE})\n" +
                      "public @interface ColorInt {\n" +
                      "}";
    classes.add(header + colorInt);

    @Language("JAVA")
    String intDef = "@Retention(SOURCE)\n" +
                    "@Target({ANNOTATION_TYPE})\n" +
                    "public @interface IntDef {\n" +
                    "    long[] value() default {};\n" +
                    "    boolean flag() default false;\n" +
                    "}\n";
    classes.add(header + intDef);

    for (ResourceType type : ResourceType.values()) {
      if (type == ResourceType.FRACTION || type == ResourceType.PUBLIC) {
        continue;
      }
      @Language("JAVA")
      String resourceTypeAnnotation = "@Documented\n" +
                                      "@Retention(SOURCE)\n" +
                                      "@Target({METHOD, PARAMETER, FIELD})\n" +
                                      "public @interface " + StringUtil.capitalize(type.getName()) + "Res {\n" +
                                      "}";
      classes.add(header + resourceTypeAnnotation);
    }
    String anyRes = "@Documented\n" +
                    "@Retention(SOURCE)\n" +
                    "@Target({METHOD, PARAMETER, FIELD})\n" +
                    "public @interface AnyRes {\n" +
                    "}";
    classes.add(header + anyRes);
    return ArrayUtil.toStringArray(classes);
  }

  // Like doTest in parent class, but uses <error> instead of <warning>
  protected final void doCheck(@Language("JAVA") @NotNull @NonNls String classText) {
    @NonNls final StringBuilder newText = new StringBuilder();
    int start = 0;
    int end = classText.indexOf("/*");
    while (end >= 0) {
      newText.append(classText, start, end);
      start = end + 2;
      end = classText.indexOf("*/", end);
      if (end < 0) {
        throw new IllegalArgumentException("invalid class text");
      }
      final String warning = classText.substring(start, end);
      if (warning.isEmpty()) {
        newText.append("</error>");
      }
      else {
        newText.append("<error descr=\"").append(warning).append("\">");
      }
      start = end + 2;
      end = classText.indexOf("/*", end + 1);
    }
    newText.append(classText, start, classText.length());

    // Now delegate to the real test implementation (it won't find comments to replace with <warning>)
    super.doTest(newText.toString());
  }

  protected void checkQuickFix(@NotNull String quickFixName, @NotNull String expected) {
    final IntentionAction quickFix = myFixture.getAvailableIntention(quickFixName);
    assertNotNull(quickFix);
    myFixture.launchAction(quickFix);
    myFixture.checkResult(expected);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ResourceTypeInspection();
  }
}
