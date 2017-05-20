/*
 * Copyright (C) 2011 The Android Open Source Project
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

package dalvik.system;

import java.io.FilenameFilter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import libcore.io.Streams;
import junit.framework.TestCase;

/**
 * Tests for the class {@link DexClassLoader}.
 */
public class DexClassLoaderTest extends TestCase {
    private static final String PACKAGE_PATH = "dalvik/system/";

    private File srcDir;
    private File dex1;
    private File dex2;
    private File jar1;
    private File jar2;
    private File optimizedDir;

    protected void setUp() throws Exception {
        srcDir = File.createTempFile("src", "");
        assertTrue(srcDir.delete());
        assertTrue(srcDir.mkdirs());

        dex1 = new File(srcDir, "loading-test.dex");
        dex2 = new File(srcDir, "loading-test2.dex");
        jar1 = new File(srcDir, "loading-test.jar");
        jar2 = new File(srcDir, "loading-test2.jar");

        copyResource("loading-test.dex", dex1);
        copyResource("loading-test2.dex", dex2);
        copyResource("loading-test.jar", jar1);
        copyResource("loading-test2.jar", jar2);

        optimizedDir = File.createTempFile("optimized", "");
        assertTrue(optimizedDir.delete());
        assertTrue(optimizedDir.mkdirs());
    }

    protected void tearDown() {
        cleanUpDir(srcDir);
        cleanUpDir(optimizedDir);
    }

    private static void cleanUpDir(File dir) {
        if (!dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                cleanUpDir(file);
            } else {
                assertTrue(file.delete());
            }
        }
    }

    /**
     * Copy a resource in the package directory to the indicated
     * target file.
     */
    private static void copyResource(String resourceName,
            File destination) throws IOException {
        ClassLoader loader = DexClassLoaderTest.class.getClassLoader();
        assertFalse(destination.exists());
        InputStream in = loader.getResourceAsStream(PACKAGE_PATH + resourceName);
        if (in == null) {
            throw new IllegalStateException("Resource not found: " + PACKAGE_PATH + resourceName);
        }

        try (FileOutputStream out = new FileOutputStream(destination)) {
            Streams.copy(in, out);
        } finally {
            in.close();
        }
    }

    static final FilenameFilter DEX_FILE_NAME_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(File file, String s) {
            return s.endsWith(".dex");
        }
    };

    /**
     * Helper to construct a DexClassLoader instance to test.
     *
     * @param files The .dex or .jar files to use for the class path.
     */
    private ClassLoader createLoader(File... files) {
        assertNotNull(files);
        assertTrue(files.length > 0);
        String path = files[0].getAbsolutePath();
        for (int i = 1; i < files.length; i++) {
            path += File.pathSeparator + files[i].getAbsolutePath();
        }
        return new DexClassLoader(path, optimizedDir.getAbsolutePath(), null,
            ClassLoader.getSystemClassLoader());
    }

    /**
     * Helper to construct a new DexClassLoader instance to test, using the
     * given files as the class path, and call a named no-argument static
     * method on a named class.
     *
     * @param className The name of the class of the method to call.
     * @param methodName The name of the method to call.
     * @param files The .dex or .jar files to use for the class path.
     */
    public Object createLoaderAndCallMethod(
            String className, String methodName, File... files)
            throws ReflectiveOperationException {
        ClassLoader cl = createLoader(files);
        Class c = cl.loadClass(className);
        Method m = c.getMethod(methodName, (Class[]) null);
        return m.invoke(null, (Object[]) null);
    }

    /**
     * Helper to construct a new DexClassLoader instance to test, using the
     * given files as the class path, and read the contents of the named
     * resource as a String.
     *
     * @param resourceName The name of the resource to get.
     * @param files The .dex or .jar files to use for the class path.
     */
    private String createLoaderAndGetResource(String resourceName, File... files) throws Exception {
        ClassLoader cl = createLoader(files);
        InputStream in = cl.getResourceAsStream(resourceName);
        if (in == null) {
            throw new IllegalStateException("Resource not found: " + resourceName);
        }

        byte[] contents = Streams.readFully(in);
        return new String(contents, "UTF-8");
    }

    // ONE_JAR

    /**
     * Just a trivial test of construction. This one merely makes
     * sure that a valid construction doesn't fail. It doesn't try
     * to verify anything about the constructed instance, other than
     * checking for the existence of optimized dex files.
     */
    public void test_oneJar_init() throws Exception {
        ClassLoader cl = createLoader(jar1);
        File[] files = optimizedDir.listFiles(DEX_FILE_NAME_FILTER);
        assertNotNull(files);
        assertEquals(1, files.length);
    }

    /**
     * Check that a class in the jar/dex file may be used successfully. In this
     * case, a trivial static method is called.
     */
    public void test_oneJar_simpleUse() throws Exception {
        String result = (String) createLoaderAndCallMethod("test.Test1", "test", jar1);
        assertSame("blort", result);
    }

    /*
     * All the following tests are just pass-throughs to test code
     * that lives inside the loading-test dex/jar file.
     */

    public void test_oneJar_constructor() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_constructor", jar1);
    }

    public void test_oneJar_callStaticMethod() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_callStaticMethod", jar1);
    }

    public void test_oneJar_getStaticVariable() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_getStaticVariable", jar1);
    }

    public void test_oneJar_callInstanceMethod() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_callInstanceMethod", jar1);
    }

    public void test_oneJar_getInstanceVariable() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_getInstanceVariable", jar1);
    }

    // ONE_DEX

    public void test_oneDex_init() throws Exception {
        ClassLoader cl = createLoader(dex1);
        File[] files = optimizedDir.listFiles(DEX_FILE_NAME_FILTER);
        assertNotNull(files);
        assertEquals(1, files.length);
    }

    public void test_oneDex_simpleUse() throws Exception {
        String result = (String) createLoaderAndCallMethod("test.Test1", "test", dex1);
        assertSame("blort", result);
    }

    public void test_oneDex_constructor() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_constructor", dex1);
    }

    public void test_oneDex_callStaticMethod() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_callStaticMethod", dex1);
    }

    public void test_oneDex_getStaticVariable() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_getStaticVariable", dex1);
    }

    public void test_oneDex_callInstanceMethod() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_callInstanceMethod", dex1);
    }

    public void test_oneDex_getInstanceVariable() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_getInstanceVariable", dex1);
    }

    // TWO_JAR

    public void test_twoJar_init() throws Exception {
        ClassLoader cl = createLoader(jar1, jar2);
        File[] files = optimizedDir.listFiles(DEX_FILE_NAME_FILTER);
        assertNotNull(files);
        assertEquals(2, files.length);
    }

    public void test_twoJar_simpleUse() throws Exception {
        String result = (String) createLoaderAndCallMethod("test.Test1", "test", jar1, jar2);
        assertSame("blort", result);
    }

    public void test_twoJar_constructor() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_constructor", jar1, jar2);
    }

    public void test_twoJar_callStaticMethod() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_callStaticMethod", jar1, jar2);
    }

    public void test_twoJar_getStaticVariable() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_getStaticVariable", jar1, jar2);
    }

    public void test_twoJar_callInstanceMethod() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_callInstanceMethod", jar1, jar2);
    }

    public void test_twoJar_getInstanceVariable() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_getInstanceVariable", jar1, jar2);
    }

    public void test_twoJar_diff_constructor() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_diff_constructor", jar1, jar2);
    }

    public void test_twoJar_diff_callStaticMethod() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_diff_callStaticMethod", jar1, jar2);
    }

    public void test_twoJar_diff_getStaticVariable() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_diff_getStaticVariable", jar1, jar2);
    }

    public void test_twoJar_diff_callInstanceMethod() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_diff_callInstanceMethod", jar1, jar2);
    }

    public void test_twoJar_diff_getInstanceVariable() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_diff_getInstanceVariable", jar1, jar2);
    }

    // TWO_DEX

    public void test_twoDex_init() throws Exception {
        ClassLoader cl = createLoader(dex1, dex2);
        File[] files = optimizedDir.listFiles(DEX_FILE_NAME_FILTER);
        assertNotNull(files);
        assertEquals(2, files.length);
    }

    public void test_twoDex_simpleUse() throws Exception {
        String result = (String) createLoaderAndCallMethod("test.Test1", "test", dex1, dex2);
        assertSame("blort", result);
    }

    public void test_twoDex_constructor() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_constructor", dex1, dex2);
    }

    public void test_twoDex_callStaticMethod() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_callStaticMethod", dex1, dex2);
    }

    public void test_twoDex_getStaticVariable() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_getStaticVariable", dex1, dex2);
    }

    public void test_twoDex_callInstanceMethod() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_callInstanceMethod", dex1, dex2);
    }

    public void test_twoDex_getInstanceVariable() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_getInstanceVariable", dex1, dex2);
    }

    public void test_twoDex_diff_constructor() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_diff_constructor", dex1, dex2);
    }

    public void test_twoDex_diff_callStaticMethod() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_diff_callStaticMethod", dex1, dex2);
    }

    public void test_twoDex_diff_getStaticVariable() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_diff_getStaticVariable", dex1, dex2);
    }

    public void test_twoDex_diff_callInstanceMethod() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_diff_callInstanceMethod", dex1, dex2);
    }

    public void test_twoDex_diff_getInstanceVariable() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_diff_getInstanceVariable", dex1, dex2);
    }

    /*
     * Tests specifically for resource-related functionality.  Since
     * raw dex files don't contain resources, these test only work
     * with jar files.
     */

    /**
     * Check that a resource in the jar file is retrievable and contains
     * the expected contents.
     */
    public void test_oneJar_directGetResourceAsStream() throws Exception {
        String result = createLoaderAndGetResource("test/Resource1.txt", jar1);
        assertEquals("Muffins are tasty!\n", result);
    }

    /**
     * Check that a resource in the jar file can be retrieved from
     * a class within that jar file.
     */
    public void test_oneJar_getResourceAsStream() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_getResourceAsStream", jar1);
    }

    public void test_twoJar_directGetResourceAsStream() throws Exception {
        String result = createLoaderAndGetResource("test/Resource1.txt", jar1, jar2);
        assertEquals("Muffins are tasty!\n", result);
    }

    public void test_twoJar_getResourceAsStream() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_getResourceAsStream", jar1, jar2);
    }

    /**
     * Check that a resource in the second jar file is retrievable and
     * contains the expected contents.
     */
    public void test_twoJar_diff_directGetResourceAsStream() throws Exception {
        String result = createLoaderAndGetResource("test2/Resource2.txt", jar1, jar2);
        assertEquals("Who doesn't like a good biscuit?\n", result);
    }

    /**
     * Check that a resource in a jar file can be retrieved from
     * a class within the other jar file.
     */
    public void test_twoJar_diff_getResourceAsStream() throws Exception {
        createLoaderAndCallMethod("test.TestMethods", "test_diff_getResourceAsStream", jar1, jar2);
    }

    /**
     * Test that a DexClassLoader can be used to generate optimized code, then
     * a subsequent PathClassLoader can be used to load the optimized code.
     * (b/19937016).
     */
    public void testDexThenPathClassLoader() throws Exception {
        // Use a DexClassLoader to create optimized code.
        File dex = new File(srcDir, "dex-then-path.dex");
        copyResource("loading-test.dex", dex);

        File oatDir = new File(new File(srcDir, "oat"), VMRuntime.getCurrentInstructionSet());
        assertTrue(oatDir.mkdirs());

        DexClassLoader dexloader = new DexClassLoader(dex.getAbsolutePath(),
                oatDir.getAbsolutePath(), null, ClassLoader.getSystemClassLoader());
        Class c1 = dexloader.loadClass("test.Test1");
        Method m1 = c1.getMethod("test", (Class[]) null);
        assertSame("blort", m1.invoke(null, (Object[]) null));

        // Move the optimized code to the right location to be used by a
        // PathClassLoader.
        File odexForDexClassLoader = new File(oatDir, "dex-then-path.dex");
        File odexForPathClassLoader = new File(oatDir, "dex-then-path.odex");
        assertTrue(DexFile.isDexOptNeeded(dex.getAbsolutePath()));
        assertTrue(odexForDexClassLoader.renameTo(odexForPathClassLoader));
        assertFalse(DexFile.isDexOptNeeded(dex.getAbsolutePath()));

        // Use a PathClassLoader that loads and runs the optimized code.
        PathClassLoader pathloader = new PathClassLoader(dex.getAbsolutePath(),
                ClassLoader.getSystemClassLoader());
        Class c2 = pathloader.loadClass("test.Test1");
        Method m2 = c2.getMethod("test", (Class[]) null);
        assertSame("blort", m2.invoke(null, (Object[]) null));
    }
}
