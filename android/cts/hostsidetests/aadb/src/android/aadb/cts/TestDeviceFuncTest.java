/*
 * Copyright (C) 2013 The Android Open Source Project
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
package android.aadb.cts;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import java.awt.image.BufferedImage;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.TimeZone;

import javax.imageio.ImageIO;

/**
 * Functional tests for adb connection
 * <p/>
 * Requires a physical device to be connected.
 */
public class TestDeviceFuncTest extends DeviceTestCase {

    private ITestDevice mTestDevice;
    /** Expect bugreports to be at least a meg. */
    private static final int mMinBugreportBytes = 1024 * 1024;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestDevice = getDevice();
    }

    /**
     * Simple testcase to ensure that the grabbing a bugreport from a real TestDevice works.
     */
    public void testBugreport() throws Exception {
        String data = StreamUtil.getStringFromStream(
                mTestDevice.getBugreport().createInputStream());
        assertTrue(String.format("Expected at least %d characters; only saw %d", mMinBugreportBytes,
                data.length()), data.length() >= mMinBugreportBytes);
        // TODO: check the captured report more extensively, perhaps using loganalysis
    }

    /**
     * Simple normal case test for
     * {@link TestDevice#executeShellCommand(String)}.
     * <p/>
     * Do a 'shell ls' command, and verify /data and /system are listed in result.
     */
    public void testExecuteShellCommand() throws IOException, DeviceNotAvailableException {
        CLog.i("testExecuteShellCommand");
        assertSimpleShellCommand();
    }

    /**
     * Verify that a simple {@link TestDevice#executeShellCommand(String)} command is successful.
     */
    private void assertSimpleShellCommand() throws DeviceNotAvailableException {
    	// Check for expected contents of device's root directory
        final String output = mTestDevice.executeShellCommand("ls /");
        assertTrue(output.contains("data"));
        assertTrue(output.contains("system"));
    }


    /**
     * Push and then pull a file from device, and verify contents are as expected.
     */
    public void testPushPull_normal() throws IOException, DeviceNotAvailableException {
        CLog.i("testPushPull");
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
        CLog.i("testPushPull");
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
        CLog.i("testPull_noexist");

        // make sure the root path is valid
        String externalStorePath =  mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        assertNotNull(externalStorePath);
        String deviceFilePath = String.format("%s/%s", externalStorePath, "thisfiledoesntexist");
        assertFalse(String.format("%s exists", deviceFilePath),
                mTestDevice.doesFileExist(deviceFilePath));
        assertNull(mTestDevice.pullFile(deviceFilePath));
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

            // adjust 1st file's last-modified timestamp according to persist.sys.timezone
            String deviceTimezone = mTestDevice.getProperty("persist.sys.timezone");
            if (deviceTimezone != null) {
                TimeZone tz = TimeZone.getTimeZone(deviceTimezone);
                long timestamp = tmpFile.lastModified() + tz.getRawOffset();
                if (tz.observesDaylightTime()) {
                    timestamp += tz.getDSTSavings();
                }
                tmpFile.setLastModified(timestamp);
            }

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
     * Basic test for {@link TestDevice#getScreenshot()}.
     * <p/>
     * Grab a screenshot and perform a cursory size check to ensure its valid.
     */
    public void testGetScreenshot() throws DeviceNotAvailableException, IOException {
        InputStreamSource source = getDevice().getScreenshot();
        assertNotNull(source);
        InputStream inputStream = source.createInputStream();
        try {
            BufferedImage screenshotImage = ImageIO.read(inputStream);
            CLog.i("testGetScreenshot w=%d, h=%d",
                    screenshotImage.getWidth(), screenshotImage.getHeight());
            assertTrue(screenshotImage.getWidth() > 0);
            assertTrue(screenshotImage.getHeight() > 0);
        } finally {
            StreamUtil.cancel(source);
            StreamUtil.close(inputStream);
        }
    }

    /**
     * Basic test for {@link TestDevice#getLogcat(long)}.
     * <p/>
     * Dumps a bunch of messages to logcat, calls getLogcat(), and verifies size of capture file is
     * equal to provided data.
     */
    public void testGetLogcat_size() throws DeviceNotAvailableException, IOException {
        CLog.i("testGetLogcat_size");
        for (int i = 0; i < 100; i++) {
            getDevice().executeShellCommand(String.format("log testGetLogcat_size log dump %d", i));
        }
        boolean passed = false;
        int retry = 0;
        while (!passed) {
            // sleep a small amount of time to ensure last log message makes it into capture
            RunUtil.getDefault().sleep(10);
            InputStreamSource source = getDevice().getLogcat(100 * 1024);
            assertNotNull(source);
            File tmpTxtFile = FileUtil.createTempFile("logcat", ".txt");
            try {
                FileUtil.writeToFile(source.createInputStream(), tmpTxtFile);
                CLog.i("Created file at %s", tmpTxtFile.getAbsolutePath());
                // ensure last log message is present in log
                String s = FileUtil.readStringFromFile(tmpTxtFile);
                if (s.contains("testGetLogcat_size log dump 99")) {
                    passed = true;
                }
            } finally {
                FileUtil.deleteFile(tmpTxtFile);
                source.cancel();
            }
            retry++;
            if ((retry > 100) && !passed) {
                fail("last log message is not in captured logcat");
            }
        }
    }
}
