\# -*- mode: makefile -*-
# This file is included by the top-level libcore Android.mk.
# It's not a normal makefile, so we don't include CLEAR_VARS
# or BUILD_*_LIBRARY.

srcdir := ojluni/src/main/native
LOCAL_SRC_FILES := \
    java_util_zip_ZipFile.c \
    java_util_zip_Inflater.c \
    java_util_zip_Deflater.c \
    java_util_zip_CRC32.c \
    Adler32.c \
    zip_util.c \
    jni_util.c \
    jni_util_md.c \
    io_util.c \
    canonicalize_md.c \
    FileDescriptor_md.c \
    DatagramChannelImpl.c \
    DatagramDispatcher.c \
    Console_md.c \
    IOUtil.c \
    PollArrayWrapper.c \
    InheritedChannel.c \
    SocketChannelImpl.c \
    FileChannelImpl.c \
    FileDispatcherImpl.c \
    FileOutputStream_md.c \
    FileInputStream.c \
    FileSystemPreferences.c \
    io_util_md.c \
    NativeThread.c \
    FileKey.c \
    UnixFileSystem_md.c \
    FileSystem_md.c \
    ObjectStreamClass.c \
    ObjectOutputStream.c \
    ObjectInputStream.c \
    InetAddress.c \
    net_util.c \
    net_util_md.c \
    Net.c \
    MappedByteBuffer.c \
    Inet6Address.c \
    Inet4Address.c \
    linux_close.cpp \
    PlainSocketImpl.c \
    PlainDatagramSocketImpl.c \
    NetworkInterface.c \
    DatagramPacket.c \
    Inet4AddressImpl.c \
    Inet6AddressImpl.c \
    ServerSocketChannelImpl.c \
    SocketInputStream.c \
    SocketOutputStream.c \
    Float.c \
    Double.c \
    StrictMath.c \
    Math.c \
    ProcessEnvironment_md.c \
    System.c \
    Runtime.c \
    Shutdown.c \
    UNIXProcess_md.c \
    Bits.c \
    Character.cpp \
    Register.cpp \
    socket_tagger_util.cpp \

LOCAL_C_INCLUDES += \
       libcore/$(srcdir) \
       external/fdlibm \
       external/openssl/include \
       external/zlib \
       external/icu/icu4c/source/common \
