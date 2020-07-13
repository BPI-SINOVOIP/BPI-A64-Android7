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
package com.android.tradefed.build;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * Concrete implementation of a {@link IFolderBuildInfo}.
 */
public class FolderBuildInfo extends BuildInfo implements IFolderBuildInfo {

    private File mRootDir;

    /**
     * @see {@link BuildInfo#BuildInfo(String, String, String)}
     */
    public FolderBuildInfo(String buildId, String testTarget, String buildName) {
        super(buildId, testTarget, buildName);
    }

    /**
     * @see {@link BuildInfo#BuildInfo(BuildInfo)}
     */
    FolderBuildInfo(BuildInfo buildToCopy) {
        super(buildToCopy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getRootDir() {
        return mRootDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRootDir(File rootDir) {
        mRootDir = rootDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        if (mRootDir != null) {
            FileUtil.recursiveDelete(mRootDir);
        }
        mRootDir = null;
    }

    @Override
    public IBuildInfo clone() {
        FolderBuildInfo copy = new FolderBuildInfo(getBuildId(), getTestTag(), getBuildTargetName());
        copy.addAllBuildAttributes(this);
        try {
            File copyDir = FileUtil.createTempDir("foldercopy");
            linkOrCopy(mRootDir, copyDir);
            copy.setRootDir(copyDir);
            return copy;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void linkOrCopy(File orig, File dest) throws IOException {
        try {
            FileUtil.recursiveHardlink(orig, dest);
            return;
        } catch (IOException e) {
            // fall through
            CLog.w("hardlink of %s %s failed: " + e.toString(), orig.getAbsolutePath(),
                    dest.getAbsolutePath());
            // Clean up after failure.
            FileUtil.recursiveDelete(dest);
        }
        FileUtil.recursiveCopy(orig, dest);
    }
}
