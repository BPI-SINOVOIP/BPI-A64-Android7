package com.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class OtaFaultInjectionPreparerTest extends TestCase {

    private IDeviceBuildInfo mMockDeviceBuildInfo;
    private ITestDevice mMockDevice;
    private OtaFaultInjectionPreparer mFaultInjectionPreparer;
    private File mOtaPackage = null;
    private File mStubZipBaseDir = null;
    private ZipFile mZipFile = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mStubZipBaseDir = FileUtil.createTempDir("libotafault");
        File stubChildDir = new File(mStubZipBaseDir, "somedir");
        assertTrue(stubChildDir.mkdir());
        File subFile = new File(stubChildDir, "foo.txt");
        FileUtil.writeToFile("contents", subFile);
        mOtaPackage = ZipUtil.createZip(mStubZipBaseDir);

        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockDeviceBuildInfo = EasyMock.createMock(IDeviceBuildInfo.class);
        EasyMock.expect(mMockDeviceBuildInfo.getOtaPackageFile()).andReturn(mOtaPackage);

        mFaultInjectionPreparer = new OtaFaultInjectionPreparer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mZipFile.close();
        mOtaPackage.delete();
        FileUtil.recursiveDelete(mStubZipBaseDir);
    }

    public void testAddSingleFault_read() throws Exception {
        mFaultInjectionPreparer.mReadFaultFile = "/foo/bar";
        EasyMock.replay(mMockDevice, mMockDeviceBuildInfo);
        mFaultInjectionPreparer.setUp(mMockDevice, mMockDeviceBuildInfo);
        EasyMock.verify(mMockDevice, mMockDeviceBuildInfo);
        mZipFile = new ZipFile(mOtaPackage);
        ZipEntry ze = mZipFile.getEntry(OtaFaultInjectionPreparer.CFG_BASE + "/READ");

        assertTrue(ZipUtil.isZipFileValid(mOtaPackage, true));
        assertNotNull(ze);

        InputStream entryReader = mZipFile.getInputStream(ze);
        byte buf[] = new byte[8];
        int numBytesRead = entryReader.read(buf);

        assertEquals(numBytesRead, 8);
        assertEquals(new String(buf), "/foo/bar");
    }
}

