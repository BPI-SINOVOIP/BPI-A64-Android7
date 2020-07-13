/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.TestAppConstants;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import org.easymock.EasyMock;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Functional tests for {@link TestDevice}.
 * <p/>
 * Requires a physical device to be connected.
 */
public class TestDeviceFuncTest extends DeviceTestCase {

    private static final String LOG_TAG = "TestDeviceFuncTest";
    private TestDevice mTestDevice;
    private IDeviceStateMonitor mMonitor;
    /** Expect bugreports to be at least a meg. */
    private static final int mMinBugreportBytes = 1024 * 1024;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestDevice = (TestDevice)getDevice();
        mMonitor = mTestDevice.getDeviceStateMonitor();
    }

    /**
     * Simple testcase to ensure that the grabbing a bugreport from a real TestDevice works.
     */
    public void testBugreport() throws Exception {
        String data = StreamUtil.getStringFromStream(
                mTestDevice.getBugreport().createInputStream());
        assertTrue(String.format("Expected at least %d characters; only saw %d", mMinBugreportBytes,
                data.length()), data.length() >= mMinBugreportBytes);
    }

    /**
     * Simple normal case test for
     * {@link TestDevice#executeShellCommand(String)}.
     * <p/>
     * Do a 'shell ls' command, and verify /data and /system are listed in result.
     */
    public void testExecuteShellCommand() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testExecuteShellCommand");
        assertSimpleShellCommand();
    }

    /**
     * Verify that a simple {@link TestDevice#executeShellCommand(String)} command is successful.
     */
    private void assertSimpleShellCommand() throws DeviceNotAvailableException {
        final String output = mTestDevice.executeShellCommand("ls");
        assertTrue(output.contains("data"));
        assertTrue(output.contains("system"));
    }

    /**
     * Test install and uninstall of package
     */
    public void testInstallUninstall() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testInstallUninstall");

        // use the wifi util apk
        File tmpFile = WifiHelper.extractWifiUtilApk();
        assertWifiApkInstall(tmpFile);
    }

    /**
     * Verifies that the given wifi util apk can be installed and uninstalled successfully
     */
    void assertWifiApkInstall(File tmpFile) throws DeviceNotAvailableException {
        try {
            mTestDevice.uninstallPackage(WifiHelper.INSTRUMENTATION_PKG);
            assertFalse(mTestDevice.getInstalledPackageNames().contains(
                    WifiHelper.INSTRUMENTATION_PKG));
            assertNull(mTestDevice.installPackage(tmpFile, false));
            assertTrue(mTestDevice.getInstalledPackageNames().contains(
                    WifiHelper.INSTRUMENTATION_PKG));
            assertFalse("apk file was not cleaned up after install",
                    mTestDevice.doesFileExist(String.format("/data/local/tmp/%s",
                            tmpFile.getName())));
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test install and uninstall of package with spaces in file name
     */
    public void testInstallUninstall_space() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testInstallUninstall_space");

        File tmpFile = WifiHelper.extractWifiUtilApk();
        File tmpFileSpaces = null;
        try {
            tmpFileSpaces = FileUtil.createTempFile("wifi util (3)", ".apk");
            FileUtil.copyFile(tmpFile, tmpFileSpaces);
            assertWifiApkInstall(tmpFileSpaces);
        } finally {
            FileUtil.deleteFile(tmpFileSpaces);
        }
    }

    /**
     * Push and then pull a file from device, and verify contents are as expected.
     */
    public void testPushPull_normal() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testPushPull");
        File tmpFile = null;
        File tmpDestFile = null;
        String deviceFilePath = null;

        try {
            tmpFile = createTempTestFile(null);
            String externalStorePath = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
            assertNotNull(externalStorePath);
            deviceFilePath = String.format("%s/%s", externalStorePath, "tmp_testPushPull.txt");
            // ensure file does not already exist
            mTestDevice.executeShellCommand(String.format("rm %s", deviceFilePath));
            assertFalse(String.format("%s exists", deviceFilePath),
                    mTestDevice.doesFileExist(deviceFilePath));

            assertTrue(mTestDevice.pushFile(tmpFile, deviceFilePath));
            assertTrue(mTestDevice.doesFileExist(deviceFilePath));
            tmpDestFile = FileUtil.createTempFile("tmp", "txt");
            assertTrue(mTestDevice.pullFile(deviceFilePath, tmpDestFile));
            assertTrue(compareFiles(tmpFile, tmpDestFile));
        } finally {
            if (tmpDestFile != null) {
                tmpDestFile.delete();
            }
            if (deviceFilePath != null) {
                mTestDevice.executeShellCommand(String.format("rm %s", deviceFilePath));
            }
        }
    }

    /**
     * Push and then pull a file from device, and verify contents are as expected.
     * <p />
     * This variant of the test uses "${EXTERNAL_STORAGE}" in the pathname.
     */
    public void testPushPull_extStorageVariable() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testPushPull");
        File tmpFile = null;
        File tmpDestFile = null;
        File tmpDestFile2 = null;
        String deviceFilePath = null;
        final String filename = "tmp_testPushPull.txt";

        try {
            tmpFile = createTempTestFile(null);
            String externalStorePath = "${EXTERNAL_STORAGE}";
            assertNotNull(externalStorePath);
            deviceFilePath = String.format("%s/%s", externalStorePath, filename);
            // ensure file does not already exist
            mTestDevice.executeShellCommand(String.format("rm %s", deviceFilePath));
            assertFalse(String.format("%s exists", deviceFilePath),
                    mTestDevice.doesFileExist(deviceFilePath));

            assertTrue(mTestDevice.pushFile(tmpFile, deviceFilePath));
            assertTrue(mTestDevice.doesFileExist(deviceFilePath));
            tmpDestFile = FileUtil.createTempFile("tmp", "txt");
            assertTrue(mTestDevice.pullFile(deviceFilePath, tmpDestFile));
            assertTrue(compareFiles(tmpFile, tmpDestFile));

            tmpDestFile2 = mTestDevice.pullFileFromExternal(filename);
            assertNotNull(tmpDestFile2);
            assertTrue(compareFiles(tmpFile, tmpDestFile2));
        } finally {
            if (tmpDestFile != null) {
                tmpDestFile.delete();
            }
            if (tmpDestFile2 != null) {
                tmpDestFile2.delete();
            }
            if (deviceFilePath != null) {
                mTestDevice.executeShellCommand(String.format("rm %s", deviceFilePath));
            }
        }
    }

    /**
     * Test pulling a file from device that does not exist.
     * <p/>
     * Expect {@link TestDevice#pullFile(String)} to return <code>false</code>
     */
    public void testPull_noexist() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testPull_noexist");

        // make sure the root path is valid
        String externalStorePath =  mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        assertNotNull(externalStorePath);
        String deviceFilePath = String.format("%s/%s", externalStorePath, "thisfiledoesntexist");
        assertFalse(String.format("%s exists", deviceFilePath),
                mTestDevice.doesFileExist(deviceFilePath));
        assertNull(mTestDevice.pullFile(deviceFilePath));
    }

    /**
     * Test pulling a file from device into a local file that cannot be written to.
     * <p/>
     * Expect {@link TestDevice#pullFile(String, File)} to return <code>false</code>
     */
    public void testPull_nopermissions() throws IOException, DeviceNotAvailableException {
        CLog.i("testPull_nopermissions");

        // make sure the root path is valid
        String externalStorePath =  mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        assertNotNull(externalStorePath);
        String deviceFilePath = String.format("%s/%s", externalStorePath, "testPull_nopermissions");
        // first push a file so we have something to retrieve
        assertTrue(mTestDevice.pushString("test data", deviceFilePath));
        assertTrue(String.format("%s does not exist", deviceFilePath),
                mTestDevice.doesFileExist(deviceFilePath));
        File tmpFile = null;
        try {
            tmpFile = FileUtil.createTempFile("testPull_nopermissions", ".txt");
            tmpFile.setReadOnly();
            assertFalse(mTestDevice.pullFile(deviceFilePath, tmpFile));
        } finally {
            if (tmpFile != null) {
                tmpFile.setWritable(true);
                FileUtil.deleteFile(tmpFile);
            }
        }
    }

    /**
     * Test pushing a file onto device that does not exist.
     * <p/>
     * Expect {@link TestDevice#pushFile(String)} to return <code>false</code>
     */
    public void testPush_noexist() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testPush_noexist");

        // make sure the root path is valid
        String externalStorePath =  mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        assertNotNull(externalStorePath);
        String deviceFilePath = String.format("%s/%s", externalStorePath, "remotepath");
        assertFalse(mTestDevice.pushFile(new File("idontexist"), deviceFilePath));
    }

    private File createTempTestFile(File dir) throws IOException {
        File tmpFile = null;
        try {
            final String fileContents = "this is the test file contents";
            tmpFile = FileUtil.createTempFile("tmp", ".txt", dir);
            FileUtil.writeToFile(fileContents, tmpFile);
            return tmpFile;
        } catch (IOException e) {
            if (tmpFile != null) {
                tmpFile.delete();
            }
            throw e;
        }
    }

    /**
     * Utility method to do byte-wise content comparison of two files.
     */
    private boolean compareFiles(File file1, File file2) throws IOException {
        BufferedInputStream stream1 = null;
        BufferedInputStream stream2 = null;

        try {
            stream1 = new BufferedInputStream(new FileInputStream(file1));
            stream2 = new BufferedInputStream(new FileInputStream(file2));
            boolean eof = false;
            while (!eof) {
                int byte1 = stream1.read();
                int byte2 = stream2.read();
                if (byte1 != byte2) {
                    return false;
                }
                eof = byte1 == -1;
            }
            return true;
        } finally {
            if (stream1 != null) {
                stream1.close();
            }
            if (stream2 != null) {
                stream2.close();
            }
        }
    }

    /**
     * Make sure that we can correctly index directories that have a symlink in the middle.  This
     * verifies a ddmlib bugfix which added/fixed this functionality.
     */
    public void testListSymlinkDir() throws Exception {
        final String extStore = "/data/local";

        // Clean up after potential failed run
        mTestDevice.executeShellCommand(String.format("rm %s/testdir", extStore));
        mTestDevice.executeShellCommand(String.format("rm %s/testdir2/foo.txt", extStore));
        mTestDevice.executeShellCommand(String.format("rmdir %s/testdir2", extStore));

        try {
            assertEquals("",
                    mTestDevice.executeShellCommand(String.format("mkdir %s/testdir2",
                    extStore)));
            assertEquals("", mTestDevice.executeShellCommand(
                    String.format("touch %s/testdir2/foo.txt", extStore)));
            assertEquals("",
                    mTestDevice.executeShellCommand(String.format("ln -s %s/testdir2 %s/testdir",
                    extStore, extStore)));

            assertNotNull(mTestDevice.getFileEntry(String.format("%s/testdir/foo.txt", extStore)));
        } finally {
            mTestDevice.executeShellCommand(String.format("rm %s/testdir", extStore));
            mTestDevice.executeShellCommand(String.format("rm %s/testdir2/foo.txt", extStore));
            mTestDevice.executeShellCommand(String.format("rmdir %s/testdir2", extStore));
        }
    }

    /**
     * Test syncing a single file using {@link TestDevice#syncFiles(File, String)}.
     */
    public void testSyncFiles_normal() throws Exception {
        doTestSyncFiles(mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE));
    }

    /**
     * Test syncing a single file using {@link TestDevice#syncFiles(File, String)}.
     * <p />
     * This variant of the test uses "${EXTERNAL_STORAGE}" in the pathname.
     */
    public void testSyncFiles_extStorageVariable() throws Exception {
        doTestSyncFiles("${EXTERNAL_STORAGE}");
    }

    /**
     * Test syncing a single file using {@link TestDevice#syncFiles(File, String)}.
     */
    public void doTestSyncFiles(String externalStorePath) throws Exception {
        String expectedDeviceFilePath = null;

        // create temp dir with one temp file
        File tmpDir = FileUtil.createTempDir("tmp");
        try {
            File tmpFile = createTempTestFile(tmpDir);
            // set last modified to 10 minutes ago
            tmpFile.setLastModified(System.currentTimeMillis() - 10*60*1000);
            assertNotNull(externalStorePath);
            expectedDeviceFilePath = String.format("%s/%s/%s", externalStorePath,
                    tmpDir.getName(), tmpFile.getName());

            assertTrue(mTestDevice.syncFiles(tmpDir, externalStorePath));
            assertTrue(mTestDevice.doesFileExist(expectedDeviceFilePath));

            // get 'ls -l' attributes of file which includes timestamp
            String origTmpFileStamp = mTestDevice.executeShellCommand(String.format("ls -l %s",
                    expectedDeviceFilePath));
            // now create another file and verify that is synced
            File tmpFile2 = createTempTestFile(tmpDir);
            tmpFile2.setLastModified(System.currentTimeMillis() - 10*60*1000);
            assertTrue(mTestDevice.syncFiles(tmpDir, externalStorePath));
            String expectedDeviceFilePath2 = String.format("%s/%s/%s", externalStorePath,
                    tmpDir.getName(), tmpFile2.getName());
            assertTrue(mTestDevice.doesFileExist(expectedDeviceFilePath2));

            // verify 1st file timestamp did not change
            String unchangedTmpFileStamp = mTestDevice.executeShellCommand(String.format("ls -l %s",
                    expectedDeviceFilePath));
            assertEquals(origTmpFileStamp, unchangedTmpFileStamp);

            // now modify 1st file and verify it does change remotely
            String testString = "blah";
            FileOutputStream stream = new FileOutputStream(tmpFile);
            stream.write(testString.getBytes());
            stream.close();

            assertTrue(mTestDevice.syncFiles(tmpDir, externalStorePath));
            String tmpFileContents = mTestDevice.executeShellCommand(String.format("cat %s",
                    expectedDeviceFilePath));
            assertTrue(tmpFileContents.contains(testString));
        } finally {
            if (expectedDeviceFilePath != null && externalStorePath != null) {
                // note that expectedDeviceFilePath has externalStorePath prepended at definition
                mTestDevice.executeShellCommand(String.format("rm -r %s", expectedDeviceFilePath));
            }
            FileUtil.recursiveDelete(tmpDir);
        }
    }

    /**
     * Test pushing a directory
     */
    public void testPushDir() throws IOException, DeviceNotAvailableException {
        String expectedDeviceFilePath = null;
        String externalStorePath = null;
        File rootDir = FileUtil.createTempDir("tmp");
        // create temp dir with one temp file
        try {
            File tmpDir = FileUtil.createTempDir("tmp", rootDir);
            File tmpFile = createTempTestFile(tmpDir);
            externalStorePath = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
            assertNotNull(externalStorePath);
            expectedDeviceFilePath = String.format("%s/%s/%s", externalStorePath,
                    tmpDir.getName(), tmpFile.getName());

            assertTrue(mTestDevice.pushDir(rootDir, externalStorePath));
            assertTrue(mTestDevice.doesFileExist(expectedDeviceFilePath));

        } finally {
            if (expectedDeviceFilePath != null && externalStorePath != null) {
                mTestDevice.executeShellCommand(String.format("rm -r %s/%s", externalStorePath,
                        expectedDeviceFilePath));
            }
            FileUtil.recursiveDelete(rootDir);
        }
    }

    /**
     * Test {@link TestDevice#executeFastbootCommand(String...)} when device is in adb mode.
     * <p/>
     * Expect fastboot recovery to be invoked, which will boot device back to fastboot mode and
     * command will succeed.
     */
    public void testExecuteFastbootCommand_deviceInAdb() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testExecuteFastbootCommand_deviceInAdb");
        int origTimeout = mTestDevice.getCommandTimeout();
        try {
            assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
            // reset operation timeout to small value to make test run quicker
            mTestDevice.setCommandTimeout(5*1000);
            assertEquals(CommandStatus.SUCCESS,
                    mTestDevice.executeFastbootCommand("getvar", "product").getStatus());
            assertEquals(TestDeviceState.FASTBOOT, mMonitor.getDeviceState());
        } finally {
            mTestDevice.setCommandTimeout(origTimeout);
            mTestDevice.reboot();
            assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
        }
    }

    /**
     * Test {@link TestDevice#executeFastbootCommand(String...)} when an invalid command is passed.
     * <p/>
     * Expect the result indicate failure, and recovery not to be invoked.
     */
    public void testExecuteFastbootCommand_badCommand() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testExecuteFastbootCommand_badCommand");
        IDeviceRecovery origRecovery = mTestDevice.getRecovery();
        try {
            mTestDevice.rebootIntoBootloader();
            assertEquals(TestDeviceState.FASTBOOT, mMonitor.getDeviceState());
            // substitute recovery mechanism to ensure recovery is not called when bad command is
            // passed
            IDeviceRecovery mockRecovery = EasyMock.createStrictMock(IDeviceRecovery.class);
            mTestDevice.setRecovery(mockRecovery);
            EasyMock.replay(mockRecovery);
            assertEquals(CommandStatus.FAILED,
                    mTestDevice.executeFastbootCommand("badcommand").getStatus());
        } finally {
            mTestDevice.setRecovery(origRecovery);
            mTestDevice.reboot();
            assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
        }
    }

    /**
     * Verify device can be rebooted into bootloader and back to adb.
     */
    public void testRebootIntoBootloader() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRebootIntoBootloader");
        try {
            mTestDevice.rebootIntoBootloader();
            assertEquals(TestDeviceState.FASTBOOT, mMonitor.getDeviceState());
        } finally {
            mTestDevice.reboot();
            assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
        }
    }

    /**
     * Verify device can be rebooted into adb.
     */
    public void testReboot() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testReboot");
        mTestDevice.reboot();
        assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
        // check that device has root
        assertTrue(mTestDevice.executeShellCommand("id").contains("root"));
    }

    /**
     * Verify device can be rebooted into adb recovery.
     */
    public void testRebootIntoRecovery() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRebootIntoRecovery");
        try {
            mTestDevice.rebootIntoRecovery();
            assertEquals(TestDeviceState.RECOVERY, mMonitor.getDeviceState());
        } finally {
            mTestDevice.reboot();
        }
    }

    /**
     * Verify that {@link TestDevice#clearErrorDialogs()} can successfully clear an error dialog
     * from screen.
     * <p/>
     * This is done by running a test app which will crash, then running another app that
     * does UI based tests.
     * <p/>
     * Assumes DevTools and TradeFedUiApp are currently installed.
     */
    public void testClearErrorDialogs_crash() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testClearErrorDialogs_crash");
        // now cause a crash dialog to appear
        getDevice().executeShellCommand("am start -W -n " + TestAppConstants.CRASH_ACTIVITY);
        getDevice().clearErrorDialogs();
        assertTrue(runUITests());
    }

    /**
     * Verify the steps taken to disable keyguard after reboot are successfully
     * <p/>
     * This is done by rebooting then run a app that does UI based tests.
     * <p/>
     * Assumes DevTools and TradeFedUiApp are currently installed.
     */
    public void testDisableKeyguard() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testDisableKeyguard");
        getDevice().reboot();
        assertTrue(runUITests());
    }

    /**
     * Test that TradeFed can successfully recover from the adb host daemon process being killed
     */
    public void testExecuteShellCommand_adbKilled() throws DeviceNotAvailableException {
        // FIXME: adb typically does not recover, and this causes rest of tests to fail
        //Log.i(LOG_TAG, "testExecuteShellCommand_adbKilled");
        //CommandResult result = RunUtil.getInstance().runTimedCmd(30*1000, "adb", "kill-server");
        //assertEquals(CommandStatus.SUCCESS, result.getStatus());
        //assertSimpleShellCommand();
    }

    /**
     * Basic test for {@link TestDevice#getScreenshot()}.
     * <p/>
     * Grab a screenshot, save it to a file, and perform a cursory size check to ensure its valid.
     */
    public void testGetScreenshot() throws DeviceNotAvailableException, IOException {
        CLog.i(LOG_TAG, "testGetScreenshot");
        InputStreamSource source = getDevice().getScreenshot();
        assertNotNull(source);
        File tmpPngFile = FileUtil.createTempFile("screenshot", ".png");
        try {
            FileUtil.writeToFile(source.createInputStream(), tmpPngFile);
            CLog.i("Created file at %s", tmpPngFile.getAbsolutePath());
            assertTrue("Saved png file is less than 16K - is it invalid?",
                    tmpPngFile.length() > 16*1024);
            // TODO: add more stringent checks
        } finally {
            FileUtil.deleteFile(tmpPngFile);
            source.cancel();
        }
    }

    /**
     * Basic test for {@link TestDevice#getLogcat(long)}.
     * <p/>
     * Dumps a bunch of messages to logcat, calls getLogcat(), and verifies size of capture file is
     * equal to provided data.
     */
    public void testGetLogcat_size() throws DeviceNotAvailableException, IOException {
        CLog.i(LOG_TAG, "testGetLogcat_size");
        for (int i = 0; i < 100; i++) {
            getDevice().executeShellCommand(String.format("log testGetLogcat_size log dump %d", i));
        }
        // sleep a small amount of time to ensure last log message makes it into capture
        RunUtil.getDefault().sleep(10);
        InputStreamSource source = getDevice().getLogcat(100 * 1024);
        assertNotNull(source);
        File tmpTxtFile = FileUtil.createTempFile("logcat", ".txt");
        try {
            FileUtil.writeToFile(source.createInputStream(), tmpTxtFile);
            CLog.i("Created file at %s", tmpTxtFile.getAbsolutePath());
            assertEquals("Saved text file is not equal to buffer size", 100 * 1024,
                    tmpTxtFile.length());
            // ensure last log message is present in log
            String s = FileUtil.readStringFromFile(tmpTxtFile);
            assertTrue("last log message is not in captured logcat",
                    s.contains("testGetLogcat_size log dump 99"));
        } finally {
            FileUtil.deleteFile(tmpTxtFile);
            source.cancel();
        }
    }

    /**
     * Basic test for encryption if encryption is supported.
     * <p>
     * Calls {@link TestDevice#encryptDevice(boolean)}, {@link TestDevice#unlockDevice()}, and
     * {@link TestDevice#unencryptDevice()}, as well as reboots the device while the device is
     * encrypted.
     * </p>
     * @throws DeviceNotAvailableException
     */
    public void testEncryption() throws DeviceNotAvailableException {
        CLog.i("testEncryption");

        if (!getDevice().isEncryptionSupported()) {
            CLog.i("Encrypting userdata is not supported. Skipping test.");
            return;
        }

        assertTrue(getDevice().unencryptDevice());
        assertFalse(getDevice().isDeviceEncrypted());
        assertTrue(getDevice().encryptDevice(false));
        assertTrue(getDevice().isDeviceEncrypted());
        assertTrue(getDevice().unlockDevice());
        // TODO: decryptUserData() can be called more than once, the framework should only be
        // restarted on the first call.
        assertTrue(getDevice().unlockDevice());
        getDevice().reboot();
        assertTrue(getDevice().unencryptDevice());
        assertFalse(getDevice().isDeviceEncrypted());
    }

    /**
     * Test that {@link TestDevice#getProperty(String)} works after a reboot.
     */
    public void testGetProperty() throws Exception {
        assertNotNull(getDevice().getProperty("ro.hardware"));
        getDevice().rebootUntilOnline();
        assertNotNull(getDevice().getProperty("ro.hardware"));
    }

    /**
     * Test that {@link TestDevice#getPropertySync(String)} works for volatile properties.
     */
    @SuppressWarnings("deprecation")
    public void testGetPropertySync() throws Exception {
        getDevice().executeShellCommand("setprop prop.test 0");
        assertEquals("0", getDevice().getPropertySync("prop.test"));
        getDevice().executeShellCommand("setprop prop.test 1");
        assertEquals("1", getDevice().getPropertySync("prop.test"));

    }

    /**
     * Test that the recovery mechanism works in {@link TestDevice#getFileEntry(String)}
     */
    public void testGetFileEntry_recovery() throws Exception {
        try {
            getDevice().rebootIntoBootloader();
            // expect recovery to kick in, and reboot device back to adb so the call works
            IFileEntry entry = getDevice().getFileEntry("/data");
            assertNotNull(entry);
        } finally {
            getDevice().reboot();
        }
    }

    /**
     * Run the test app UI tests and return true if they all pass.
     */
    private boolean runUITests() throws DeviceNotAvailableException {
        RemoteAndroidTestRunner uirunner = new RemoteAndroidTestRunner(
                TestAppConstants.UITESTAPP_PACKAGE, getDevice().getIDevice());
        CollectingTestListener uilistener = new CollectingTestListener();
        getDevice().runInstrumentationTests(uirunner, uilistener);
        return TestAppConstants.UI_TOTAL_TESTS == uilistener.getNumTestsInState(TestStatus.PASSED);
    }
}
