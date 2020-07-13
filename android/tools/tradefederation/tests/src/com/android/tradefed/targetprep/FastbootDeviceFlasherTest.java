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

package com.android.tradefed.targetprep;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.IDeviceFlasher.UserDataFlashOption;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;

/**
 * Unit tests for {@link FastbootDeviceFlasher}.
 */
public class FastbootDeviceFlasherTest extends TestCase {

    /** a temp 'don't care value' string to use */
    private static final String TEST_STRING = "foo";
    private FastbootDeviceFlasher mFlasher;
    private ITestDevice mMockDevice;
    private IDeviceBuildInfo mMockBuildInfo;
    private IFlashingResourcesRetriever mMockRetriever;
    private IFlashingResourcesParser mMockParser;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn(TEST_STRING);
        EasyMock.expect(mMockDevice.getProductType()).andStubReturn(TEST_STRING);
        EasyMock.expect(mMockDevice.getBuildId()).andStubReturn("1");
        EasyMock.expect(mMockDevice.getBuildFlavor()).andStubReturn("test-debug");
        mMockBuildInfo = new DeviceBuildInfo("0", TEST_STRING, TEST_STRING);
        mMockBuildInfo.setDeviceImageFile(new File(TEST_STRING), "0");
        mMockBuildInfo.setUserDataImageFile(new File(TEST_STRING), "0");
        mMockRetriever = EasyMock.createNiceMock(IFlashingResourcesRetriever.class);
        mMockParser = EasyMock.createNiceMock(IFlashingResourcesParser.class);

        mFlasher = new FastbootDeviceFlasher() {
            @Override
            protected IFlashingResourcesParser createFlashingResourcesParser(
                    IDeviceBuildInfo localBuild) {
                return mMockParser;
            }
        };
        mFlasher.setFlashingResourcesRetriever(mMockRetriever);
        mFlasher.setUserDataFlashOption(UserDataFlashOption.RETAIN);
    }

    /**
     * Test {@link FastbootDeviceFlasher#flash(ITestDevice, IDeviceBuildInfo)}
     * when device is not available.
     */
    public void testFlash_deviceNotAvailable() throws DeviceNotAvailableException  {
       try {
            mMockDevice.rebootIntoBootloader();
            // TODO: this is fixed to two arguments - how to set to expect a variable arg amount ?
            mMockDevice.executeFastbootCommand((String)EasyMock.anyObject(),
                    (String)EasyMock.anyObject());
            EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
            EasyMock.replay(mMockDevice);
            mFlasher.flash(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Test DeviceFlasher#flash(ITestDevice, IDeviceBuildInfo)} when required board info is not
     * present.
     */
    public void testFlash_missingBoard() throws DeviceNotAvailableException  {
        mMockDevice.rebootIntoBootloader();
        EasyMock.expect(mMockParser.getRequiredBoards()).andReturn(null);
        EasyMock.replay(mMockDevice);
        try {
            mFlasher.flash(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Test {@link FastbootDeviceFlasher#getImageVersion(ITestDevice, String)}
     */
    public void testGetImageVersion() throws DeviceNotAvailableException, TargetSetupError {
        CommandResult fastbootResult = new CommandResult();
        fastbootResult.setStatus(CommandStatus.SUCCESS);
        // output of getvar is on stderr for some unknown reason
        fastbootResult.setStderr("version-bootloader: 1.0.1\nfinished. total time: 0.001s");
        fastbootResult.setStdout("");
        EasyMock.expect(mMockDevice.executeFastbootCommand("getvar", "version-bootloader")).
                andReturn(fastbootResult);
        EasyMock.replay(mMockDevice);
        String actualVersion = mFlasher.getImageVersion(mMockDevice, "bootloader");
        assertEquals("1.0.1", actualVersion);
    }

    /**
     * Test that a fastboot command is retried if it does not output anything.
     */
    public void testRetryGetVersionCommand() throws DeviceNotAvailableException, TargetSetupError {
        // The first time command is tried, make it return an empty string.
        CommandResult fastbootInValidResult = new CommandResult();
        fastbootInValidResult.setStatus(CommandStatus.SUCCESS);
        // output of getvar is on stderr for some unknown reason
        fastbootInValidResult.setStderr("");
        fastbootInValidResult.setStdout("");

        // Return the correct value on second attempt.
        CommandResult fastbootValidResult = new CommandResult();
        fastbootValidResult.setStatus(CommandStatus.SUCCESS);
        fastbootValidResult.setStderr("version-baseband: 1.0.1\nfinished. total time: 0.001s");
        fastbootValidResult.setStdout("");

        EasyMock.expect(mMockDevice.executeFastbootCommand("getvar", "version-baseband")).
                andReturn(fastbootInValidResult);
        EasyMock.expect(mMockDevice.executeFastbootCommand("getvar", "version-baseband")).
                andReturn(fastbootValidResult);

        EasyMock.replay(mMockDevice);
        String actualVersion = mFlasher.getImageVersion(mMockDevice, "baseband");
        assertEquals("1.0.1", actualVersion);
    }

    /**
     * Test that baseband can be flashed when current baseband version is empty
     */
    public void testFlashBaseband_noVersion()
            throws DeviceNotAvailableException, TargetSetupError, IOException {
        final String newBasebandVersion = "1.0.1";
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        // expect a fastboot getvar version-baseband command
        setFastbootResponseExpectations(mockDevice, "version-baseband: \n");
        setFastbootResponseExpectations(mockDevice, "version-baseband: \n");
        // expect a 'flash radio' command
        setFastbootFlashExpectations(mockDevice, "radio");
        mockDevice.rebootIntoBootloader();
        EasyMock.replay(mockDevice);

        FastbootDeviceFlasher flasher = getFlasherWithParserData(
                String.format("require version-baseband=%s", newBasebandVersion));

        IDeviceBuildInfo build = new DeviceBuildInfo("1234", "target", "build-name");
        build.setBasebandImage(new File("tmp"), newBasebandVersion);
        flasher.checkAndFlashBaseband(mockDevice, build);
        EasyMock.verify(mockDevice);
    }

    /**
     * Test flashing of user data with a tests zip
     *
     * @throws TargetSetupError
     */
    public void testFlashUserData_testsZip() throws DeviceNotAvailableException, TargetSetupError {
        mFlasher.setUserDataFlashOption(UserDataFlashOption.TESTS_ZIP);

        ITestsZipInstaller mockZipInstaller = EasyMock.createMock(ITestsZipInstaller.class);
        mFlasher.setTestsZipInstaller(mockZipInstaller);
        // expect
        mockZipInstaller.pushTestsZipOntoData(EasyMock.eq(mMockDevice), EasyMock.eq(mMockBuildInfo));
        // expect
        mMockDevice.rebootUntilOnline();
        mMockDevice.rebootIntoBootloader();
        EasyMock.expect(mMockDevice.isEncryptionSupported()).andReturn(Boolean.FALSE);

        EasyMock.replay(mMockDevice, mockZipInstaller);
        mFlasher.flashUserData(mMockDevice, mMockBuildInfo);
        EasyMock.verify(mMockDevice, mockZipInstaller);
    }

    /**
     * Test doing a user data with with rm
     *
     * @throws TargetSetupError
     */
    public void testFlashUserData_wipeRm() throws DeviceNotAvailableException, TargetSetupError {
        mFlasher.setUserDataFlashOption(UserDataFlashOption.WIPE_RM);

        ITestsZipInstaller mockZipInstaller = EasyMock.createMock(ITestsZipInstaller.class);
        mFlasher.setTestsZipInstaller(mockZipInstaller);
        // expect
        mockZipInstaller.deleteData(EasyMock.eq(mMockDevice));
        // expect
        mMockDevice.rebootUntilOnline();
        mMockDevice.rebootIntoBootloader();
        EasyMock.expect(mMockDevice.isEncryptionSupported()).andReturn(Boolean.FALSE);

        EasyMock.replay(mMockDevice, mockZipInstaller);
        mFlasher.flashUserData(mMockDevice, mMockBuildInfo);
        EasyMock.verify(mMockDevice, mockZipInstaller);
    }

    /**
     * Set EasyMock expectations to simulate the response to some fastboot command
     *
     * @param mockDevice the EasyMock mock {@link ITestDevice} to configure
     * @param response the fastboot command response to inject
     */
    private static void setFastbootResponseExpectations(ITestDevice mockDevice, String response)
            throws DeviceNotAvailableException {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStderr(response);
        result.setStdout("");
        EasyMock.expect(
                mockDevice.executeFastbootCommand((String)EasyMock.anyObject(),
                        (String)EasyMock.anyObject())).andReturn(result);
    }

    /**
     * Set EasyMock expectations to simulate the response to a fastboot flash command
     *
     * @param image the expected image name to flash
     * @param mockDevice the EasyMock mock {@link ITestDevice} to configure
     */
    private static void setFastbootFlashExpectations(ITestDevice mockDevice, String image)
            throws DeviceNotAvailableException {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStderr("success");
        result.setStdout("");
        EasyMock.expect(
                mockDevice.executeLongFastbootCommand(EasyMock.eq("flash"), EasyMock.eq(image),
                        (String)EasyMock.anyObject())).andReturn(result);
    }

    private FastbootDeviceFlasher getFlasherWithParserData(final String androidInfoData)
            throws IOException {
        FastbootDeviceFlasher flasher = new FastbootDeviceFlasher() {
            @Override
            protected IFlashingResourcesParser createFlashingResourcesParser(
                    IDeviceBuildInfo localBuild) throws TargetSetupError {
                BufferedReader reader = new BufferedReader(new StringReader(androidInfoData));
                try {
                    return new FlashingResourcesParser(reader);
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            protected void flashBootloader(ITestDevice device, File bootloaderImageFile)
                    throws DeviceNotAvailableException, TargetSetupError {
                throw new DeviceNotAvailableException("error");
            }
        };
        flasher.setFlashingResourcesRetriever(EasyMock.createNiceMock(
                IFlashingResourcesRetriever.class));
        return flasher;
    }
}
