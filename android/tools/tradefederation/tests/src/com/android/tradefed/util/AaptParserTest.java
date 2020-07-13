/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.tradefed.util;

import junit.framework.TestCase;

/**
 *
 */
public class AaptParserTest extends TestCase {

    public void testParsePackageNameVersionLabel() {
        AaptParser p = new AaptParser();
        p.parse("package: name='com.android.foo' versionCode='13' versionName='2.3'\n" +
            "sdkVersion:'5'\n" +
            "application-label:'Foo'\n" +
            "application-label-fr:'Faa'\n"+
            "uses-permission:'android.permission.INTERNET'");
        assertEquals("com.android.foo", p.getPackageName());
        assertEquals("13", p.getVersionCode());
        assertEquals("2.3", p.getVersionName());
        assertEquals("Foo", p.getLabel());
    }

    public void testParseVersionMultipleFieldsNoLabel() {
        AaptParser p = new AaptParser();
        p.parse("package: name='com.android.foo' versionCode='217173' versionName='1.7173' " +
                "platformBuildVersionName=''\n" +
                "install-location:'preferExternal'\n" +
                "sdkVersion:'10'\n" +
                "targetSdkVersion:'21'\n" +
                "uses-permission: name='android.permission.INTERNET'\n" +
                "uses-permission: name='android.permission.ACCESS_NETWORK_STATE'\n");
        assertEquals("com.android.foo", p.getPackageName());
        assertEquals("217173", p.getVersionCode());
        assertEquals("1.7173", p.getVersionName());
        assertEquals("com.android.foo", p.getLabel());
    }
}
