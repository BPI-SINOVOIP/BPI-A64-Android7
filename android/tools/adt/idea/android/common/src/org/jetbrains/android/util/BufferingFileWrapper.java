package org.jetbrains.android.util;

import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.StreamException;
import com.google.common.base.Objects;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * @author Eugene.Kudelevsky
 */
public class BufferingFileWrapper implements IAbstractFile {
  private final File myFile;

  public BufferingFileWrapper(@NotNull File file) {
    myFile = file;
  }

  @Override
  public InputStream getContents() throws StreamException {
    // it's not very good idea to return unclosed InputStream and entrust its closing to library, so let's read whole file
    try {
      final byte[] content = readFile();
      return new ByteArrayInputStream(content);
    }
    catch (IOException e) {
      throw new StreamException(e, this);
    }
  }

  private byte[] readFile() throws IOException {
    DataInputStream is = new DataInputStream(new FileInputStream(myFile));
    try {
      byte[] data = new byte[(int)myFile.length()];
      //noinspection ResultOfMethodCallIgnored
      is.readFully(data);
      return data;
    }
    finally {
      is.close();
    }
  }

  @NotNull
  public File getFile() {
    return myFile;
  }

  @Override
  public void setContents(InputStream source) throws StreamException {
    throw new UnsupportedOperationException();
  }

  @Override
  public OutputStream getOutputStream() throws StreamException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PreferredWriteMode getPreferredWriteMode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getModificationStamp() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    return myFile.getName();
  }

  @Override
  public String getOsLocation() {
    return myFile.getAbsolutePath();
  }

  @Override
  public boolean exists() {
    return myFile.isFile();
  }

  @Nullable
  @Override
  public IAbstractFolder getParentFolder() {
    final File parentFile = myFile.getParentFile();
    return parentFile != null ? new BufferingFolderWrapper(parentFile) : null;
  }

  @Override
  public boolean delete() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BufferingFileWrapper wrapper = (BufferingFileWrapper)o;

    return FileUtil.filesEqual(myFile, wrapper.myFile);
  }

  @Override
  public int hashCode() {
    return FileUtil.fileHashCode(myFile);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this.getClass()).add("file", myFile).toString();
  }
}
