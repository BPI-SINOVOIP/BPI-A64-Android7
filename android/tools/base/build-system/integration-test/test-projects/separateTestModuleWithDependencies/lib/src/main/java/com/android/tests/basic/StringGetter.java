package com.android.tests.basic;

import com.android.tests.jarDep.JarDependencyUtil;

public class StringGetter{

    /**
     * Public method that will not get obfuscated
     */
    public static String getString(int foo) {
         return getStringInternal(foo);
    }

    /**
     * Public method that will get obfuscated by the app project.
     */
    public static String getString2(int foo) {
        return getStringInternal(foo);
    }

    /**
     * method that will get obfuscated by the library.
     */
    public static String getStringInternal(int foo) {
        return Integer.toString(foo);
    }

    public static String getStringFromJarDep() {
        return JarDependencyUtil.getString();
    }
}
