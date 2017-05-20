LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    AudioBufferProviderSource.cpp   \
    AudioStreamOutSink.cpp          \
    AudioStreamInSource.cpp         \
    NBAIO.cpp                       \
    MonoPipe.cpp                    \
    MonoPipeReader.cpp              \
    Pipe.cpp                        \
    PipeReader.cpp                  \
    SourceAudioBufferProvider.cpp

LOCAL_SRC_FILES += NBLog.cpp

# libsndfile license is incompatible; uncomment to use for local debug only
#LOCAL_SRC_FILES += LibsndfileSink.cpp LibsndfileSource.cpp
#LOCAL_C_INCLUDES += path/to/libsndfile/src
#LOCAL_STATIC_LIBRARIES += libsndfile

LOCAL_MODULE := libnbaio

LOCAL_SHARED_LIBRARIES := \
    libaudioutils \
    libbinder \
    libcutils \
    libutils \
    liblog

LOCAL_C_INCLUDES := $(call include-path-for, audio-utils)

LOCAL_CFLAGS := -Werror -Wall

include $(BUILD_SHARED_LIBRARY)
