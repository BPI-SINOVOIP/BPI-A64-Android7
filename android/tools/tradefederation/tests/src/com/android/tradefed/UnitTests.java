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
package com.android.tradefed;

import com.android.tradefed.build.BuildInfoTest;
import com.android.tradefed.build.DeviceBuildDescriptorTest;
import com.android.tradefed.build.DeviceBuildInfoTest;
import com.android.tradefed.build.FileDownloadCacheTest;
import com.android.tradefed.build.KernelBuildInfoTest;
import com.android.tradefed.build.KernelDeviceBuildInfoTest;
import com.android.tradefed.build.OtaZipfileBuildProviderTest;
import com.android.tradefed.build.SdkBuildInfoTest;
import com.android.tradefed.command.CommandFileParserTest;
import com.android.tradefed.command.CommandSchedulerTest;
import com.android.tradefed.command.ConsoleTest;
import com.android.tradefed.command.VerifyTest;
import com.android.tradefed.command.remote.RemoteOperationTest;
import com.android.tradefed.config.ArgsOptionParserTest;
import com.android.tradefed.config.ConfigurationDefTest;
import com.android.tradefed.config.ConfigurationFactoryTest;
import com.android.tradefed.config.ConfigurationTest;
import com.android.tradefed.config.ConfigurationXmlParserTest;
import com.android.tradefed.config.OptionCopierTest;
import com.android.tradefed.config.OptionSetterTest;
import com.android.tradefed.config.OptionUpdateRuleTest;
import com.android.tradefed.device.CpuStatsCollectorTest;
import com.android.tradefed.device.DeviceManagerTest;
import com.android.tradefed.device.DeviceSelectionOptionsTest;
import com.android.tradefed.device.DeviceStateMonitorTest;
import com.android.tradefed.device.DeviceUtilStatsMonitorTest;
import com.android.tradefed.device.DumpsysPackageReceiverTest;
import com.android.tradefed.device.FastbootHelperTest;
import com.android.tradefed.device.ManagedDeviceListTest;
import com.android.tradefed.device.ReconnectingRecoveryTest;
import com.android.tradefed.device.TestDeviceTest;
import com.android.tradefed.device.WaitDeviceRecoveryTest;
import com.android.tradefed.device.WifiHelperTest;
import com.android.tradefed.invoker.TestInvocationTest;
import com.android.tradefed.log.FileLoggerTest;
import com.android.tradefed.log.LogRegistryTest;
import com.android.tradefed.log.TerribleFailureEmailHandlerTest;
import com.android.tradefed.result.BugreportCollectorTest;
import com.android.tradefed.result.CollectingTestListenerTest;
import com.android.tradefed.result.ConsoleResultReporterTest;
import com.android.tradefed.result.DeviceFileReporterTest;
import com.android.tradefed.result.EmailResultReporterTest;
import com.android.tradefed.result.FailureEmailResultReporterTest;
import com.android.tradefed.result.FileSystemLogSaverTest;
import com.android.tradefed.result.InvocationFailureEmailResultReporterTest;
import com.android.tradefed.result.InvocationToJUnitResultForwarderTest;
import com.android.tradefed.result.JUnitToInvocationResultForwarderTest;
import com.android.tradefed.result.LogFileSaverTest;
import com.android.tradefed.result.SnapshotInputStreamSourceTest;
import com.android.tradefed.result.TestFailureEmailResultReporterTest;
import com.android.tradefed.result.TestSummaryTest;
import com.android.tradefed.result.XmlResultReporterTest;
import com.android.tradefed.targetprep.BuildInfoAttributePreparerTest;
import com.android.tradefed.targetprep.DefaultTestsZipInstallerTest;
import com.android.tradefed.targetprep.DeviceFlashPreparerTest;
import com.android.tradefed.targetprep.DeviceSetupTest;
import com.android.tradefed.targetprep.FastbootDeviceFlasherTest;
import com.android.tradefed.targetprep.FlashingResourcesParserTest;
import com.android.tradefed.targetprep.InstrumentationPreparerTest;
import com.android.tradefed.targetprep.KernelFlashPreparerTest;
import com.android.tradefed.targetprep.OtaFaultInjectionPreparerTest;
import com.android.tradefed.targetprep.SdkAvdPreparerTest;
import com.android.tradefed.targetprep.StopServicesSetupTest;
import com.android.tradefed.targetprep.SystemUpdaterDeviceFlasherTest;
import com.android.tradefed.testtype.AndroidJUnitTestTest;
import com.android.tradefed.testtype.DeviceTestCaseTest;
import com.android.tradefed.testtype.DeviceTestSuite;
import com.android.tradefed.testtype.FakeTestTest;
import com.android.tradefed.testtype.GTestResultParserTest;
import com.android.tradefed.testtype.GTestTest;
import com.android.tradefed.testtype.HostTestTest;
import com.android.tradefed.testtype.InstrumentationFileTestTest;
import com.android.tradefed.testtype.InstrumentationSerialTestTest;
import com.android.tradefed.testtype.InstrumentationTestTest;
import com.android.tradefed.testtype.NativeBenchmarkTestParserTest;
import com.android.tradefed.testtype.NativeStressTestParserTest;
import com.android.tradefed.testtype.NativeStressTestTest;
import com.android.tradefed.testtype.testdefs.XmlDefsParserTest;
import com.android.tradefed.testtype.testdefs.XmlDefsTestTest;
import com.android.tradefed.util.AaptParserTest;
import com.android.tradefed.util.AbiFormatterTest;
import com.android.tradefed.util.ArrayUtilTest;
import com.android.tradefed.util.ByteArrayListTest;
import com.android.tradefed.util.ConditionPriorityBlockingQueueTest;
import com.android.tradefed.util.EmailTest;
import com.android.tradefed.util.FileUtilTest;
import com.android.tradefed.util.JUnitXmlParserTest;
import com.android.tradefed.util.ListInstrumentationParserTest;
import com.android.tradefed.util.LogcatUpdaterEventParserTest;
import com.android.tradefed.util.MultiMapTest;
import com.android.tradefed.util.NullUtilTest;
import com.android.tradefed.util.PairTest;
import com.android.tradefed.util.QuotationAwareTokenizerTest;
import com.android.tradefed.util.RegexTrieTest;
import com.android.tradefed.util.RunUtilTest;
import com.android.tradefed.util.SizeLimitedOutputStreamTest;
import com.android.tradefed.util.net.HttpHelperTest;
import com.android.tradefed.util.net.HttpMultipartPostTest;
import com.android.tradefed.util.xml.AndroidManifestWriterTest;

import junit.framework.Test;

/**
 * A test suite for all Trade Federation unit tests.
 * <p/>
 * All tests listed here should be self-contained, and should not require any external dependencies.
 */
public class UnitTests extends DeviceTestSuite {

    public UnitTests() {
        super();
        // build
        addTestSuite(BuildInfoTest.class);
        addTestSuite(DeviceBuildInfoTest.class);
        addTestSuite(DeviceBuildDescriptorTest.class);
        addTestSuite(FileDownloadCacheTest.class);
        addTestSuite(KernelBuildInfoTest.class);
        addTestSuite(KernelDeviceBuildInfoTest.class);
        addTestSuite(OtaZipfileBuildProviderTest.class);
        addTestSuite(SdkBuildInfoTest.class);

        // command
        addTestSuite(CommandFileParserTest.class);
        addTestSuite(CommandSchedulerTest.class);
        addTestSuite(ConsoleTest.class);
        addTestSuite(VerifyTest.class);

        // command.remote
        addTestSuite(RemoteOperationTest.class);

        // config
        addTestSuite(ArgsOptionParserTest.class);
        addTestSuite(ConfigurationDefTest.class);
        addTestSuite(ConfigurationFactoryTest.class);
        addTestSuite(ConfigurationTest.class);
        addTestSuite(ConfigurationXmlParserTest.class);
        addTestSuite(OptionCopierTest.class);
        addTestSuite(OptionSetterTest.class);
        addTestSuite(OptionUpdateRuleTest.class);

        // device
        addTestSuite(CpuStatsCollectorTest.class);
        addTestSuite(DeviceManagerTest.class);
        addTestSuite(DeviceSelectionOptionsTest.class);
        addTestSuite(DeviceStateMonitorTest.class);
        addTestSuite(DeviceUtilStatsMonitorTest.class);
        addTestSuite(DumpsysPackageReceiverTest.class);
        addTestSuite(FastbootHelperTest.class);
        addTestSuite(ManagedDeviceListTest.class);
        addTestSuite(ReconnectingRecoveryTest.class);
        addTestSuite(TestDeviceTest.class);
        addTestSuite(WaitDeviceRecoveryTest.class);
        addTestSuite(WifiHelperTest.class);

        // invoker
        addTestSuite(TestInvocationTest.class);

        // log
        addTestSuite(FileLoggerTest.class);
        addTestSuite(LogRegistryTest.class);
        addTestSuite(TerribleFailureEmailHandlerTest.class);

        // result
        addTestSuite(BugreportCollectorTest.class);
        addTestSuite(ConsoleResultReporterTest.class);
        addTestSuite(CollectingTestListenerTest.class);
        addTestSuite(DeviceFileReporterTest.class);
        addTestSuite(EmailResultReporterTest.class);
        addTestSuite(FailureEmailResultReporterTest.class);
        addTestSuite(FileSystemLogSaverTest.class);
        addTestSuite(InvocationFailureEmailResultReporterTest.class);
        addTestSuite(InvocationToJUnitResultForwarderTest.class);
        addTestSuite(JUnitToInvocationResultForwarderTest.class);
        addTestSuite(LogFileSaverTest.class);
        addTestSuite(SnapshotInputStreamSourceTest.class);
        addTestSuite(TestSummaryTest.class);
        addTestSuite(TestFailureEmailResultReporterTest.class);
        addTestSuite(XmlResultReporterTest.class);

        // targetprep
        addTestSuite(BuildInfoAttributePreparerTest.class);
        addTestSuite(DefaultTestsZipInstallerTest.class);
        addTestSuite(DeviceFlashPreparerTest.class);
        addTestSuite(DeviceSetupTest.class);
        addTestSuite(FastbootDeviceFlasherTest.class);
        addTestSuite(FlashingResourcesParserTest.class);
        addTestSuite(KernelFlashPreparerTest.class);
        addTestSuite(SdkAvdPreparerTest.class);
        addTestSuite(StopServicesSetupTest.class);
        addTestSuite(SystemUpdaterDeviceFlasherTest.class);
        addTestSuite(InstrumentationPreparerTest.class);
        addTestSuite(OtaFaultInjectionPreparerTest.class);

        // testtype
        addTestSuite(AndroidJUnitTestTest.class);
        addTestSuite(DeviceTestCaseTest.class);
        addTestSuite(FakeTestTest.class);
        addTestSuite(GTestResultParserTest.class);
        addTestSuite(GTestTest.class);
        addTestSuite(HostTestTest.class);
        addTestSuite(InstrumentationSerialTestTest.class);
        addTestSuite(InstrumentationFileTestTest.class);
        addTestSuite(InstrumentationTestTest.class);
        addTestSuite(NativeBenchmarkTestParserTest.class);
        addTestSuite(NativeStressTestParserTest.class);
        addTestSuite(NativeStressTestTest.class);

        // testtype/testdefs
        addTestSuite(XmlDefsParserTest.class);
        addTestSuite(XmlDefsTestTest.class);

        // util
        addTestSuite(AaptParserTest.class);
        addTestSuite(AbiFormatterTest.class);
        addTestSuite(ArrayUtilTest.class);
        addTestSuite(ByteArrayListTest.class);
        addTestSuite(ConditionPriorityBlockingQueueTest.class);
        addTestSuite(EmailTest.class);
        addTestSuite(FileUtilTest.class);
        addTestSuite(HttpHelperTest.class);
        addTestSuite(HttpMultipartPostTest.class);
        addTestSuite(JUnitXmlParserTest.class);
        addTestSuite(ListInstrumentationParserTest.class);
        addTestSuite(LogcatUpdaterEventParserTest.class);
        addTestSuite(MultiMapTest.class);
        addTestSuite(NullUtilTest.class);
        addTestSuite(PairTest.class);
        addTestSuite(QuotationAwareTokenizerTest.class);
        addTestSuite(RegexTrieTest.class);
        addTestSuite(RunUtilTest.class);
        addTestSuite(SizeLimitedOutputStreamTest.class);

        // util subdirs
        addTestSuite(AndroidManifestWriterTest.class);
    }

    public static Test suite() {
        return new UnitTests();
    }
}
