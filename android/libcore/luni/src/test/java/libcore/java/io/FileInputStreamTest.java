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

package libcore.java.io;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.system.ErrnoException;
import android.system.OsConstants;
import junit.framework.TestCase;

import libcore.io.IoUtils;
import libcore.io.Libcore;

public final class FileInputStreamTest extends TestCase {
    private static final int TOTAL_SIZE = 1024;
    private static final int SKIP_SIZE = 100;

    private static class DataFeeder extends Thread {
        private FileDescriptor mOutFd;

        public DataFeeder(FileDescriptor fd) {
            mOutFd = fd;
        }

        @Override
        public void run() {
            try {
                FileOutputStream fos = new FileOutputStream(mOutFd);
                try {
                    byte[] buffer = new byte[TOTAL_SIZE];
                    for (int i = 0; i < buffer.length; ++i) {
                        buffer[i] = (byte) i;
                    }
                    fos.write(buffer);
                } finally {
                    IoUtils.closeQuietly(fos);
                    IoUtils.close(mOutFd);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void verifyData(FileInputStream is, int start, int count) throws IOException {
        byte buffer[] = new byte[count];
        assertEquals(count, is.read(buffer));
        for (int i = 0; i < count; ++i) {
            assertEquals((byte) (i + start), buffer[i]);
        }
    }

    public void testSkipInPipes() throws Exception {
        FileDescriptor[] pipe = Libcore.os.pipe2(0);
        DataFeeder feeder = new DataFeeder(pipe[1]);
        try {
            feeder.start();
            FileInputStream fis = new FileInputStream(pipe[0]);
            fis.skip(SKIP_SIZE);
            verifyData(fis, SKIP_SIZE, TOTAL_SIZE - SKIP_SIZE);
            assertEquals(-1, fis.read());
            feeder.join(1000);
            assertFalse(feeder.isAlive());
        } finally {
            IoUtils.closeQuietly(pipe[0]);
        }
    }

    public void testDirectories() throws Exception {
        try {
            new FileInputStream(".");
            fail();
        } catch (FileNotFoundException expected) {
        }
    }

    private File makeFile() throws Exception {
        File tmp = File.createTempFile("FileOutputStreamTest", "tmp");
        FileOutputStream fos = new FileOutputStream(tmp);
        fos.write(1);
        fos.write(1);
        fos.close();
        return tmp;
    }

    public void testFileDescriptorOwnership() throws Exception {
        File tmp = makeFile();

        FileInputStream fis1 = new FileInputStream(tmp);
        FileInputStream fis2 = new FileInputStream(fis1.getFD());

        // Close the second FileDescriptor and check we can't use it...
        fis2.close();

        try {
            fis2.available();
            fail();
        } catch (IOException expected) {
        }
        try {
            fis2.read();
            fail();
        } catch (IOException expected) {
        }
        try {
            fis2.read(new byte[1], 0, 1);
            fail();
        } catch (IOException expected) {
        }
        try {
            fis2.skip(1);
            fail();
        } catch (IOException expected) {
        }
        // ...but that we can still use the first.
        assertTrue(fis1.getFD().valid());
        assertFalse(fis1.read() == -1);

        // Close the first FileDescriptor and check we can't use it...
        fis1.close();
        try {
            fis1.available();
            fail();
        } catch (IOException expected) {
        }
        try {
            fis1.read();
            fail();
        } catch (IOException expected) {
        }
        try {
            fis1.read(new byte[1], 0, 1);
            fail();
        } catch (IOException expected) {
        }
        try {
            fis1.skip(1);
            fail();
        } catch (IOException expected) {
        }

        // FD is no longer owned by any stream, should be invalidated.
        assertFalse(fis1.getFD().valid());
    }

    public void testClose() throws Exception {
        File tmp = makeFile();
        FileInputStream fis = new FileInputStream(tmp);

        // Closing an already-closed stream is a no-op...
        fis.close();
        fis.close();

        // But any explicit activity is an error.
        try {
            fis.available();
            fail();
        } catch (IOException expected) {
        }
        try {
            fis.read();
            fail();
        } catch (IOException expected) {
        }
        try {
            fis.read(new byte[1], 0, 1);
            fail();
        } catch (IOException expected) {
        }
        try {
            fis.skip(1);
            fail();
        } catch (IOException expected) {
        }
        // Including 0-byte skips...
        try {
            fis.skip(0);
            fail();
        } catch (IOException expected) {
        }
        // ...but not 0-byte reads...
        fis.read(new byte[0], 0, 0);
    }

    // http://b/26117827
    public void testReadProcVersion() throws IOException {
        File file = new File("/proc/version");
        FileInputStream input = new FileInputStream(file);
        assertTrue(input.available() == 0);
    }

    // http://b/25695227
    public void testFdLeakWhenOpeningDirectory() throws Exception {
        File phile = IoUtils.createTemporaryDirectory("test_bug_25695227");

        try {
            new FileInputStream(phile);
            fail();
        } catch (FileNotFoundException expected) {
        }

        assertTrue(getOpenFdsForPrefix("test_bug_25695227").isEmpty());
    }

    // http://b/28192631
    public void testSkipOnLargeFiles() throws Exception {
        File largeFile = File.createTempFile("FileInputStreamTest_testSkipOnLargeFiles", "");
        FileOutputStream fos = new FileOutputStream(largeFile);
        try {
            byte[] buffer = new byte[1024 * 1024]; // 1 MB
            for (int i = 0; i < 3 * 1024; i++) { // 3 GB
                fos.write(buffer);
            }
        } finally {
            fos.close();
        }

        FileInputStream fis = new FileInputStream(largeFile);
        long lastByte = 3 * 1024 * 1024 * 1024L - 1;
        assertEquals(0, Libcore.os.lseek(fis.getFD(), 0, OsConstants.SEEK_CUR));
        assertEquals(lastByte, fis.skip(lastByte));

        // Proactively cleanup - it's a pretty large file.
        assertTrue(largeFile.delete());
    }

    private static List<Integer> getOpenFdsForPrefix(String path) throws Exception {
        File[] fds = new File("/proc/self/fd").listFiles();
        List<Integer> list = new ArrayList<>();
        for (File fd : fds) {
            try {
                File fdPath = new File(android.system.Os.readlink(fd.getAbsolutePath()));
                if (fdPath.getName().startsWith(path)) {
                    list.add(Integer.valueOf(fd.getName()));
                }
            } catch (ErrnoException e) {
                if (e.errno != OsConstants.ENOENT) {
                    throw e.rethrowAsIOException();
                }
            }
        }

        return list;
    }
}
