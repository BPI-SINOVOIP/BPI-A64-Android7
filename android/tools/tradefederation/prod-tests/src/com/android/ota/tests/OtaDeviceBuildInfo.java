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

package com.android.ota.tests;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.VersionedFile;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/**
 * A {@link IDeviceBuildInfo} used for over-the-air update testing. It is composed of two device
 * builds:
 * <ul>
 * <li>a baseline build image (the build to OTA from).</li>
 * <li>a OTA build (a build to OTA to). Should contain necessary build attributes and associated
 * OTA package.</li>
 * </ul>
 * <var>this</var> contains the baseline build, and {@link #getOtaBuild()} returns the OTA
 * build.
 */
public class OtaDeviceBuildInfo implements IDeviceBuildInfo {

    private IDeviceBuildInfo mOtaBuild;
    private IDeviceBuildInfo mBaselineBuild;

    public void setOtaBuild(IDeviceBuildInfo otaBuild) {
        mOtaBuild = otaBuild;
    }

    public void setBaselineBuild(IDeviceBuildInfo baselineBuild) {
        mBaselineBuild = baselineBuild;
    }

    public IDeviceBuildInfo getOtaBuild() {
        return mOtaBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildId() {
        return mBaselineBuild.getBuildId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuildId(String buildId) {
        mBaselineBuild.setBuildId(buildId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTestTag() {
        return mBaselineBuild.getTestTag();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildTargetName() {
        return mBaselineBuild.getBuildTargetName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildFlavor() {
        return mBaselineBuild.getBuildFlavor();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceSerial() {
        return mBaselineBuild.getDeviceSerial();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuildFlavor(String buildFlavor) {
        mBaselineBuild.setBuildFlavor(buildFlavor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildBranch() {
        return mBaselineBuild.getBuildBranch();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuildBranch(String branch) {
        mBaselineBuild.setBuildBranch(branch);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceSerial(String serial) {
        mBaselineBuild.setDeviceSerial(serial);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getBuildAttributes() {
        return mBaselineBuild.getBuildAttributes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addBuildAttribute(String attributeName, String attributeValue) {
        mBaselineBuild.addBuildAttribute(attributeName, attributeValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getFile(String name) {
        return mBaselineBuild.getFile(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getVersion(String name) {
        return mBaselineBuild.getVersion(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFile(String name, File file, String version) {
        mBaselineBuild.setFile(name, file, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceBuildId() {
        return mBaselineBuild.getDeviceBuildId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getDeviceImageFile() {
        return mBaselineBuild.getDeviceImageFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceImageVersion() {
        return mBaselineBuild.getDeviceImageVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceImageFile(File deviceImageFile, String version) {
        mBaselineBuild.setDeviceImageFile(deviceImageFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getUserDataImageFile() {
        return mBaselineBuild.getUserDataImageFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserDataImageVersion() {
        return mBaselineBuild.getUserDataImageVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUserDataImageFile(File userDataFile, String version) {
        mBaselineBuild.setUserDataImageFile(userDataFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getTestsDir() {
        return mBaselineBuild.getTestsDir();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTestsDirVersion() {
        return mBaselineBuild.getTestsDirVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestsDir(File testsZipFile, String version) {
        mBaselineBuild.setTestsDir(testsZipFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getBasebandImageFile() {
        return mBaselineBuild.getBasebandImageFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBasebandVersion() {
        return mBaselineBuild.getBasebandVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBasebandImage(File basebandFile, String version) {
        mBaselineBuild.setBasebandImage(basebandFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getBootloaderImageFile() {
        return mBaselineBuild.getBootloaderImageFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBootloaderVersion() {
        return mBaselineBuild.getBootloaderVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBootloaderImageFile(File bootloaderImgFile, String version) {
        mBaselineBuild.setBootloaderImageFile(bootloaderImgFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getOtaPackageFile() {
        return mBaselineBuild.getOtaPackageFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOtaPackageVersion() {
        return mBaselineBuild.getOtaPackageVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOtaPackageFile(File otaFile, String version) {
        mBaselineBuild.setOtaPackageFile(otaFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getMkbootimgFile() {
        return mBaselineBuild.getMkbootimgFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMkbootimgVersion() {
        return mBaselineBuild.getMkbootimgVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMkbootimgFile(File mkbootimg, String version) {
        mBaselineBuild.setMkbootimgFile(mkbootimg, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getRamdiskFile() {
        return mBaselineBuild.getRamdiskFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRamdiskVersion() {
        return mBaselineBuild.getRamdiskVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRamdiskFile(File ramdisk, String version) {
        mBaselineBuild.setRamdiskFile(ramdisk, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        mBaselineBuild.cleanUp();
        mOtaBuild.cleanUp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo clone() {
        OtaDeviceBuildInfo clone = new OtaDeviceBuildInfo();
        if (mBaselineBuild != null) {
            clone.setBaselineBuild((IDeviceBuildInfo)mBaselineBuild.clone());
        }
        if (mOtaBuild != null) {
            clone.setOtaBuild((IDeviceBuildInfo)mOtaBuild.clone());
        }
        return clone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<VersionedFile> getFiles() {
        Collection<VersionedFile> combinedFiles = new LinkedList<VersionedFile>();
        combinedFiles.addAll(mBaselineBuild.getFiles());
        combinedFiles.addAll(mOtaBuild.getFiles());
        return combinedFiles;
    }
}
