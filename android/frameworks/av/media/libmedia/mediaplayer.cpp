/*
**
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaPlayer"

#include <fcntl.h>
#include <inttypes.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <utils/Log.h>

#include <binder/IServiceManager.h>
#include <binder/IPCThreadState.h>

#include <gui/Surface.h>

#include <media/mediaplayer.h>
#include <media/AudioResamplerPublic.h>
#include <media/AudioSystem.h>
#include <media/AVSyncSettings.h>
#include <media/IDataSource.h>

#include <binder/MemoryBase.h>

#include <utils/KeyedVector.h>
#include <utils/String8.h>

#include <system/audio.h>
#include <system/window.h>

namespace android {

MediaPlayer::MediaPlayer()
{
    ALOGV("constructor");
    mListener = NULL;
    mCookie = NULL;
    mStreamType = AUDIO_STREAM_MUSIC;
    mAudioAttributesParcel = NULL;
    mCurrentPosition = -1;
    mSeekPosition = -1;
    mCurrentState = MEDIA_PLAYER_IDLE;
    mPrepareSync = false;
    mPrepareStatus = NO_ERROR;
    mLoop = false;
    mLeftVolume = mRightVolume = 1.0;
    mVideoWidth = mVideoHeight = 0;
    mLockThreadId = 0;
    mAudioSessionId = (audio_session_t) AudioSystem::newAudioUniqueId(AUDIO_UNIQUE_ID_USE_SESSION);
    AudioSystem::acquireAudioSessionId(mAudioSessionId, -1);
    mSendLevel = 0;
    mRetransmitEndpointValid = false;

	mAWExtendDirId = -1;
	mAWExtendDp = NULL;
	mBDFolderPlayMode = 0;
}

MediaPlayer::~MediaPlayer()
{
    ALOGV("destructor");
    if (mAudioAttributesParcel != NULL) {
        delete mAudioAttributesParcel;
        mAudioAttributesParcel = NULL;
    }
    AudioSystem::releaseAudioSessionId(mAudioSessionId, -1);
    disconnect();
    IPCThreadState::self()->flushCommands();
}

void MediaPlayer::disconnect()
{
    ALOGV("disconnect");
    sp<IMediaPlayer> p;
    {
        Mutex::Autolock _l(mLock);
        p = mPlayer;
        mPlayer.clear();
    }

    if (p != 0) {
        p->disconnect();
    }
}

// always call with lock held
void MediaPlayer::clear_l()
{
    mCurrentPosition = -1;
    mSeekPosition = -1;
    mVideoWidth = mVideoHeight = 0;
    mRetransmitEndpointValid = false;
}

status_t MediaPlayer::setListener(const sp<MediaPlayerListener>& listener)
{
    ALOGV("setListener");
    Mutex::Autolock _l(mLock);
    mListener = listener;
    return NO_ERROR;
}


status_t MediaPlayer::attachNewPlayer(const sp<IMediaPlayer>& player)
{
    status_t err = UNKNOWN_ERROR;
    sp<IMediaPlayer> p;
    { // scope for the lock
        Mutex::Autolock _l(mLock);

        if ( !( (mCurrentState & MEDIA_PLAYER_IDLE) ||
                (mCurrentState == MEDIA_PLAYER_STATE_ERROR ) ) ) {
            ALOGE("attachNewPlayer called in state %d", mCurrentState);
            return INVALID_OPERATION;
        }

        clear_l();
        p = mPlayer;
        mPlayer = player;
        if (player != 0) {
            mCurrentState = MEDIA_PLAYER_INITIALIZED;
            err = NO_ERROR;
        } else {
            ALOGE("Unable to create media player");
        }
    }

    if (p != 0) {
        p->disconnect();
    }

    return err;
}

status_t MediaPlayer::setDataSource(
        const sp<IMediaHTTPService> &httpService,
        const char *url, const KeyedVector<String8, String8> *headers)
{
    ALOGV("setDataSource(%s)", url);
    status_t err = BAD_VALUE;
    if (url != NULL) {
        const sp<IMediaPlayerService> service(getMediaPlayerService());
        if (service != 0) {
            sp<IMediaPlayer> player(service->create(this, mAudioSessionId));
			if(player != NULL)
			{
				ALOGD("(f:%s, l:%d) set BDFolderPlay[%d]", __FUNCTION__, __LINE__, mBDFolderPlayMode);
				player->generalInterface(MEDIAPLAYER_CMD_SET_BD_FOLDER_PLAY_MODE, mBDFolderPlayMode, 0, 0, NULL);
			}
            if ((NO_ERROR != doSetRetransmitEndpoint(player)) ||
                (NO_ERROR != player->setDataSource(httpService, url, headers))) {
                player.clear();
            }
            err = attachNewPlayer(player);
        }
    }
    return err;
}

status_t MediaPlayer::setDataSource(int fd, int64_t offset, int64_t length)
{
    ALOGV("setDataSource(%d, %" PRId64 ", %" PRId64 ")", fd, offset, length);
    status_t err = UNKNOWN_ERROR;
    const sp<IMediaPlayerService> service(getMediaPlayerService());
    if (service != 0) {
        sp<IMediaPlayer> player(service->create(this, mAudioSessionId));
        if ((NO_ERROR != doSetRetransmitEndpoint(player)) ||
            (NO_ERROR != player->setDataSource(fd, offset, length))) {
            player.clear();
        }
        err = attachNewPlayer(player);
    }
    return err;
}

status_t MediaPlayer::setDataSource(const sp<IStreamSource> &source)
{
    ALOGV("setDataSource");
    status_t err = UNKNOWN_ERROR;
    const sp<IMediaPlayerService>& service(getMediaPlayerService());
    if (service != 0) {
        sp<IMediaPlayer> player(service->create(this, mAudioSessionId));
        if ((NO_ERROR != doSetRetransmitEndpoint(player)) ||
            (NO_ERROR != player->setDataSource(source))) {
            player.clear();
        }
        err = attachNewPlayer(player);
    }
    return err;
}

status_t MediaPlayer::setDataSource(const sp<IDataSource> &source)
{
    ALOGV("setDataSource(IDataSource)");
    status_t err = UNKNOWN_ERROR;
    const sp<IMediaPlayerService> service(getMediaPlayerService());
    if (service != 0) {
        sp<IMediaPlayer> player(service->create(this, mAudioSessionId));
        if ((NO_ERROR != doSetRetransmitEndpoint(player)) ||
            (NO_ERROR != player->setDataSource(source))) {
            player.clear();
        }
        err = attachNewPlayer(player);
    }
    return err;
}

status_t MediaPlayer::invoke(const Parcel& request, Parcel *reply)
{
    Mutex::Autolock _l(mLock);
    const bool hasBeenInitialized =
            (mCurrentState != MEDIA_PLAYER_STATE_ERROR) &&
            ((mCurrentState & MEDIA_PLAYER_IDLE) != MEDIA_PLAYER_IDLE);
    if ((mPlayer != NULL) && hasBeenInitialized) {
        ALOGV("invoke %zu", request.dataSize());
        return  mPlayer->invoke(request, reply);
    }
    ALOGE("invoke failed: wrong state %X, mPlayer(%p)", mCurrentState, mPlayer.get());
    return INVALID_OPERATION;
}

status_t MediaPlayer::setMetadataFilter(const Parcel& filter)
{
    ALOGD("setMetadataFilter");
    Mutex::Autolock lock(mLock);
    if (mPlayer == NULL) {
        return NO_INIT;
    }
    return mPlayer->setMetadataFilter(filter);
}

status_t MediaPlayer::getMetadata(bool update_only, bool apply_filter, Parcel *metadata)
{
    ALOGD("getMetadata");
    Mutex::Autolock lock(mLock);
    if (mPlayer == NULL) {
        return NO_INIT;
    }
    return mPlayer->getMetadata(update_only, apply_filter, metadata);
}

status_t MediaPlayer::setVideoSurfaceTexture(
        const sp<IGraphicBufferProducer>& bufferProducer)
{
    ALOGV("setVideoSurfaceTexture");
    Mutex::Autolock _l(mLock);
    if (mPlayer == 0) return NO_INIT;
    return mPlayer->setVideoSurfaceTexture(bufferProducer);
}

// must call with lock held
status_t MediaPlayer::prepareAsync_l()
{
    if ( (mPlayer != 0) && ( mCurrentState & (MEDIA_PLAYER_INITIALIZED | MEDIA_PLAYER_STOPPED) ) ) {
        if (mAudioAttributesParcel != NULL) {
            mPlayer->setParameter(KEY_PARAMETER_AUDIO_ATTRIBUTES, *mAudioAttributesParcel);
        } else {
            mPlayer->setAudioStreamType(mStreamType);
        }
        mCurrentState = MEDIA_PLAYER_PREPARING;
        return mPlayer->prepareAsync();
    }
    ALOGE("prepareAsync called in state %d, mPlayer(%p)", mCurrentState, mPlayer.get());
    return INVALID_OPERATION;
}

// TODO: In case of error, prepareAsync provides the caller with 2 error codes,
// one defined in the Android framework and one provided by the implementation
// that generated the error. The sync version of prepare returns only 1 error
// code.
status_t MediaPlayer::prepare()
{
    ALOGV("prepare");
    Mutex::Autolock _l(mLock);
    mLockThreadId = getThreadId();
    if (mPrepareSync) {
        mLockThreadId = 0;
        return -EALREADY;
    }
    mPrepareSync = true;
    status_t ret = prepareAsync_l();
    if (ret != NO_ERROR) {
        mLockThreadId = 0;
        return ret;
    }

    if (mPrepareSync) {
        mSignal.wait(mLock);  // wait for prepare done
        mPrepareSync = false;
    }
    ALOGV("prepare complete - status=%d", mPrepareStatus);
    mLockThreadId = 0;
    return mPrepareStatus;
}

status_t MediaPlayer::prepareAsync()
{
    ALOGV("prepareAsync");
    Mutex::Autolock _l(mLock);
    return prepareAsync_l();
}

status_t MediaPlayer::start()
{
    ALOGV("start");

    status_t ret = NO_ERROR;
    Mutex::Autolock _l(mLock);

    mLockThreadId = getThreadId();

    if (mCurrentState & MEDIA_PLAYER_STARTED) {
        ret = NO_ERROR;
    } else if ( (mPlayer != 0) && ( mCurrentState & ( MEDIA_PLAYER_PREPARED |
                    MEDIA_PLAYER_PLAYBACK_COMPLETE | MEDIA_PLAYER_PAUSED ) ) ) {
        mPlayer->setLooping(mLoop);
        mPlayer->setVolume(mLeftVolume, mRightVolume);
        mPlayer->setAuxEffectSendLevel(mSendLevel);
        mCurrentState = MEDIA_PLAYER_STARTED;
        ret = mPlayer->start();
        if (ret != NO_ERROR) {
            mCurrentState = MEDIA_PLAYER_STATE_ERROR;
        } else {
            if (mCurrentState == MEDIA_PLAYER_PLAYBACK_COMPLETE) {
                ALOGV("playback completed immediately following start()");
            }
        }
    } else {
        ALOGE("start called in state %d, mPlayer(%p)", mCurrentState, mPlayer.get());
        ret = INVALID_OPERATION;
    }

    mLockThreadId = 0;

    return ret;
}

status_t MediaPlayer::stop()
{
    ALOGV("stop");
    Mutex::Autolock _l(mLock);
    if (mCurrentState & MEDIA_PLAYER_STOPPED) return NO_ERROR;
    if ( (mPlayer != 0) && ( mCurrentState & ( MEDIA_PLAYER_STARTED | MEDIA_PLAYER_PREPARED |
                    MEDIA_PLAYER_PAUSED | MEDIA_PLAYER_PLAYBACK_COMPLETE ) ) ) {
        status_t ret = mPlayer->stop();
        if (ret != NO_ERROR) {
            mCurrentState = MEDIA_PLAYER_STATE_ERROR;
        } else {
            mCurrentState = MEDIA_PLAYER_STOPPED;
        }
        return ret;
    }
    ALOGE("stop called in state %d, mPlayer(%p)", mCurrentState, mPlayer.get());
    return INVALID_OPERATION;
}

status_t MediaPlayer::pause()
{
    ALOGV("pause");
    Mutex::Autolock _l(mLock);
    if (mCurrentState & (MEDIA_PLAYER_PAUSED|MEDIA_PLAYER_PLAYBACK_COMPLETE))
        return NO_ERROR;
    if ((mPlayer != 0) && (mCurrentState & MEDIA_PLAYER_STARTED)) {
        status_t ret = mPlayer->pause();
        if (ret != NO_ERROR) {
            mCurrentState = MEDIA_PLAYER_STATE_ERROR;
        } else {
            mCurrentState = MEDIA_PLAYER_PAUSED;
        }
        return ret;
    }
    ALOGE("pause called in state %d, mPlayer(%p)", mCurrentState, mPlayer.get());
    return INVALID_OPERATION;
}

bool MediaPlayer::isPlaying()
{
    Mutex::Autolock _l(mLock);
    if (mPlayer != 0) {
        bool temp = false;
        mPlayer->isPlaying(&temp);
        ALOGV("isPlaying: %d", temp);
        if ((mCurrentState & MEDIA_PLAYER_STARTED) && ! temp) {
            ALOGE("internal/external state mismatch corrected");
            mCurrentState = MEDIA_PLAYER_PAUSED;
        } else if ((mCurrentState & MEDIA_PLAYER_PAUSED) && temp) {
            ALOGE("internal/external state mismatch corrected");
            mCurrentState = MEDIA_PLAYER_STARTED;
        }
        return temp;
    }
    ALOGV("isPlaying: no active player");
    return false;
}

status_t MediaPlayer::setPlaybackSettings(const AudioPlaybackRate& rate)
{
    ALOGV("setPlaybackSettings: %f %f %d %d",
            rate.mSpeed, rate.mPitch, rate.mFallbackMode, rate.mStretchMode);
    // Negative speed and pitch does not make sense. Further validation will
    // be done by the respective mediaplayers.
    if (rate.mSpeed < 0.f || rate.mPitch < 0.f) {
        return BAD_VALUE;
    }
    Mutex::Autolock _l(mLock);
    if (mPlayer == 0 || (mCurrentState & MEDIA_PLAYER_STOPPED)) {
        return INVALID_OPERATION;
    }

    if (rate.mSpeed != 0.f && !(mCurrentState & MEDIA_PLAYER_STARTED)
            && (mCurrentState & (MEDIA_PLAYER_PREPARED | MEDIA_PLAYER_PAUSED
                    | MEDIA_PLAYER_PLAYBACK_COMPLETE))) {
        mPlayer->setLooping(mLoop);
        mPlayer->setVolume(mLeftVolume, mRightVolume);
        mPlayer->setAuxEffectSendLevel(mSendLevel);
    }

    status_t err = mPlayer->setPlaybackSettings(rate);
    if (err == OK) {
        if (rate.mSpeed == 0.f && mCurrentState == MEDIA_PLAYER_STARTED) {
            mCurrentState = MEDIA_PLAYER_PAUSED;
        } else if (rate.mSpeed != 0.f
                && (mCurrentState & (MEDIA_PLAYER_PREPARED | MEDIA_PLAYER_PAUSED
                    | MEDIA_PLAYER_PLAYBACK_COMPLETE))) {
            mCurrentState = MEDIA_PLAYER_STARTED;
        }
    }
    return err;
}

status_t MediaPlayer::getPlaybackSettings(AudioPlaybackRate* rate /* nonnull */)
{
    Mutex::Autolock _l(mLock);
    if (mPlayer == 0) return INVALID_OPERATION;
    return mPlayer->getPlaybackSettings(rate);
}

status_t MediaPlayer::setSyncSettings(const AVSyncSettings& sync, float videoFpsHint)
{
    ALOGV("setSyncSettings: %u %u %f %f",
            sync.mSource, sync.mAudioAdjustMode, sync.mTolerance, videoFpsHint);
    Mutex::Autolock _l(mLock);
    if (mPlayer == 0) return INVALID_OPERATION;
    return mPlayer->setSyncSettings(sync, videoFpsHint);
}

status_t MediaPlayer::getSyncSettings(
        AVSyncSettings* sync /* nonnull */, float* videoFps /* nonnull */)
{
    Mutex::Autolock _l(mLock);
    if (mPlayer == 0) return INVALID_OPERATION;
    return mPlayer->getSyncSettings(sync, videoFps);
}

status_t MediaPlayer::getVideoWidth(int *w)
{
    ALOGV("getVideoWidth");
    Mutex::Autolock _l(mLock);
    if (mPlayer == 0) return INVALID_OPERATION;
    *w = mVideoWidth;
    return NO_ERROR;
}

status_t MediaPlayer::getVideoHeight(int *h)
{
    ALOGV("getVideoHeight");
    Mutex::Autolock _l(mLock);
    if (mPlayer == 0) return INVALID_OPERATION;
    *h = mVideoHeight;
    return NO_ERROR;
}

status_t MediaPlayer::getCurrentPosition(int *msec)
{
    ALOGV("getCurrentPosition");
    Mutex::Autolock _l(mLock);
    if (mPlayer != 0) {
        if (mCurrentPosition >= 0) {
            ALOGV("Using cached seek position: %d", mCurrentPosition);
            *msec = mCurrentPosition;
            return NO_ERROR;
        }
        return mPlayer->getCurrentPosition(msec);
    }
    return INVALID_OPERATION;
}

status_t MediaPlayer::getDuration_l(int *msec)
{
    ALOGV("getDuration_l");
    bool isValidState = (mCurrentState & (MEDIA_PLAYER_PREPARED | MEDIA_PLAYER_STARTED |
            MEDIA_PLAYER_PAUSED | MEDIA_PLAYER_STOPPED | MEDIA_PLAYER_PLAYBACK_COMPLETE));
    if (mPlayer != 0 && isValidState) {
        int durationMs;
        status_t ret = mPlayer->getDuration(&durationMs);

        if (ret != OK) {
            // Do not enter error state just because no duration was available.
            durationMs = -1;
            ret = OK;
        }

        if (msec) {
            *msec = durationMs;
        }
        return ret;
    }
    ALOGE("Attempt to call getDuration in wrong state: mPlayer=%p, mCurrentState=%u",
            mPlayer.get(), mCurrentState);
    return INVALID_OPERATION;
}

status_t MediaPlayer::getDuration(int *msec)
{
    Mutex::Autolock _l(mLock);
    return getDuration_l(msec);
}

status_t MediaPlayer::seekTo_l(int msec)
{
    ALOGV("seekTo %d", msec);
    if ((mPlayer != 0) && ( mCurrentState & ( MEDIA_PLAYER_STARTED | MEDIA_PLAYER_PREPARED |
            MEDIA_PLAYER_PAUSED |  MEDIA_PLAYER_PLAYBACK_COMPLETE) ) ) {
        if ( msec < 0 ) {
            ALOGW("Attempt to seek to invalid position: %d", msec);
            msec = 0;
        }

        int durationMs;
        status_t err = mPlayer->getDuration(&durationMs);

        if (err != OK) {
            ALOGW("Stream has no duration and is therefore not seekable.");
            return err;
        }

        if (msec > durationMs) {
            ALOGW("Attempt to seek to past end of file: request = %d, "
                  "durationMs = %d",
                  msec,
                  durationMs);

            msec = durationMs;
        }

        // cache duration
        mCurrentPosition = msec;
        if (mSeekPosition < 0) {
            mSeekPosition = msec;
            return mPlayer->seekTo(msec);
        }
        else {
            ALOGV("Seek in progress - queue up seekTo[%d]", msec);
            return NO_ERROR;
        }
    }
    ALOGE("Attempt to perform seekTo in wrong state: mPlayer=%p, mCurrentState=%u", mPlayer.get(),
            mCurrentState);
    return INVALID_OPERATION;
}

status_t MediaPlayer::seekTo(int msec)
{
    mLockThreadId = getThreadId();
    Mutex::Autolock _l(mLock);
    status_t result = seekTo_l(msec);
    mLockThreadId = 0;

    return result;
}

status_t MediaPlayer::reset_l()
{
    mLoop = false;
    if (mCurrentState == MEDIA_PLAYER_IDLE) return NO_ERROR;
    mPrepareSync = false;
    if (mPlayer != 0) {
        status_t ret = mPlayer->reset();
        if (ret != NO_ERROR) {
            ALOGE("reset() failed with return code (%d)", ret);
            mCurrentState = MEDIA_PLAYER_STATE_ERROR;
        } else {
            mPlayer->disconnect();
            mCurrentState = MEDIA_PLAYER_IDLE;
        }
        // setDataSource has to be called again to create a
        // new mediaplayer.
        mPlayer = 0;
        return ret;
    }
    clear_l();
    return NO_ERROR;
}

status_t MediaPlayer::doSetRetransmitEndpoint(const sp<IMediaPlayer>& player) {
    Mutex::Autolock _l(mLock);

    if (player == NULL) {
        return UNKNOWN_ERROR;
    }

    if (mRetransmitEndpointValid) {
        return player->setRetransmitEndpoint(&mRetransmitEndpoint);
    }

    return OK;
}

status_t MediaPlayer::reset()
{
    ALOGV("reset");
    Mutex::Autolock _l(mLock);
    return reset_l();
}

status_t MediaPlayer::setAudioStreamType(audio_stream_type_t type)
{
    ALOGV("MediaPlayer::setAudioStreamType");
    Mutex::Autolock _l(mLock);
    if (mStreamType == type) return NO_ERROR;
    if (mCurrentState & ( MEDIA_PLAYER_PREPARED | MEDIA_PLAYER_STARTED |
                MEDIA_PLAYER_PAUSED | MEDIA_PLAYER_PLAYBACK_COMPLETE ) ) {
        // Can't change the stream type after prepare
        ALOGE("setAudioStream called in state %d", mCurrentState);
        return INVALID_OPERATION;
    }
    // cache
    mStreamType = type;
    return OK;
}

status_t MediaPlayer::getAudioStreamType(audio_stream_type_t *type)
{
    ALOGV("getAudioStreamType");
    Mutex::Autolock _l(mLock);
    *type = mStreamType;
    return OK;
}

status_t MediaPlayer::setLooping(int loop)
{
    ALOGV("MediaPlayer::setLooping");
    Mutex::Autolock _l(mLock);
    mLoop = (loop != 0);
    if (mPlayer != 0) {
        return mPlayer->setLooping(loop);
    }
    return OK;
}

bool MediaPlayer::isLooping() {
    ALOGV("isLooping");
    Mutex::Autolock _l(mLock);
    if (mPlayer != 0) {
        return mLoop;
    }
    ALOGV("isLooping: no active player");
    return false;
}

status_t MediaPlayer::setVolume(float leftVolume, float rightVolume)
{
    ALOGV("MediaPlayer::setVolume(%f, %f)", leftVolume, rightVolume);
    Mutex::Autolock _l(mLock);
    mLeftVolume = leftVolume;
    mRightVolume = rightVolume;
    if (mPlayer != 0) {
        return mPlayer->setVolume(leftVolume, rightVolume);
    }
    return OK;
}

status_t MediaPlayer::setAudioSessionId(audio_session_t sessionId)
{
    ALOGV("MediaPlayer::setAudioSessionId(%d)", sessionId);
    Mutex::Autolock _l(mLock);
    if (!(mCurrentState & MEDIA_PLAYER_IDLE)) {
        ALOGE("setAudioSessionId called in state %d", mCurrentState);
        return INVALID_OPERATION;
    }
    if (sessionId < 0) {
        return BAD_VALUE;
    }
    if (sessionId != mAudioSessionId) {
        AudioSystem::acquireAudioSessionId(sessionId, -1);
        AudioSystem::releaseAudioSessionId(mAudioSessionId, -1);
        mAudioSessionId = sessionId;
    }
    return NO_ERROR;
}

audio_session_t MediaPlayer::getAudioSessionId()
{
    Mutex::Autolock _l(mLock);
    return mAudioSessionId;
}

status_t MediaPlayer::setAuxEffectSendLevel(float level)
{
    ALOGV("MediaPlayer::setAuxEffectSendLevel(%f)", level);
    Mutex::Autolock _l(mLock);
    mSendLevel = level;
    if (mPlayer != 0) {
        return mPlayer->setAuxEffectSendLevel(level);
    }
    return OK;
}

status_t MediaPlayer::attachAuxEffect(int effectId)
{
    ALOGV("MediaPlayer::attachAuxEffect(%d)", effectId);
    Mutex::Autolock _l(mLock);
    if (mPlayer == 0 ||
        (mCurrentState & MEDIA_PLAYER_IDLE) ||
        (mCurrentState == MEDIA_PLAYER_STATE_ERROR )) {
        ALOGE("attachAuxEffect called in state %d, mPlayer(%p)", mCurrentState, mPlayer.get());
        return INVALID_OPERATION;
    }

    return mPlayer->attachAuxEffect(effectId);
}

// always call with lock held
status_t MediaPlayer::checkStateForKeySet_l(int key)
{
    switch(key) {
    case KEY_PARAMETER_AUDIO_ATTRIBUTES:
        if (mCurrentState & ( MEDIA_PLAYER_PREPARED | MEDIA_PLAYER_STARTED |
                MEDIA_PLAYER_PAUSED | MEDIA_PLAYER_PLAYBACK_COMPLETE) ) {
            // Can't change the audio attributes after prepare
            ALOGE("trying to set audio attributes called in state %d", mCurrentState);
            return INVALID_OPERATION;
        }
        break;
    default:
        // parameter doesn't require player state check
        break;
    }
    return OK;
}

status_t MediaPlayer::setParameter(int key, const Parcel& request)
{
    ALOGV("MediaPlayer::setParameter(%d)", key);
    status_t status = INVALID_OPERATION;
    Mutex::Autolock _l(mLock);
    if (checkStateForKeySet_l(key) != OK) {
        return status;
    }
    switch (key) {
    case KEY_PARAMETER_AUDIO_ATTRIBUTES:
        // save the marshalled audio attributes
        if (mAudioAttributesParcel != NULL) { delete mAudioAttributesParcel; };
        mAudioAttributesParcel = new Parcel();
        mAudioAttributesParcel->appendFrom(&request, 0, request.dataSize());
        status = OK;
        break;
    default:
        ALOGV_IF(mPlayer == NULL, "setParameter: no active player");
        break;
    }

    if (mPlayer != NULL) {
        status = mPlayer->setParameter(key, request);
    }
    return status;
}

status_t MediaPlayer::getParameter(int key, Parcel *reply)
{
    ALOGV("MediaPlayer::getParameter(%d)", key);
    Mutex::Autolock _l(mLock);
    if (mPlayer != NULL) {
        return  mPlayer->getParameter(key, reply);
    }
    ALOGV("getParameter: no active player");
    return INVALID_OPERATION;
}

status_t MediaPlayer::setRetransmitEndpoint(const char* addrString,
                                            uint16_t port) {
    ALOGV("MediaPlayer::setRetransmitEndpoint(%s:%hu)",
            addrString ? addrString : "(null)", port);

    Mutex::Autolock _l(mLock);
    if ((mPlayer != NULL) || (mCurrentState != MEDIA_PLAYER_IDLE))
        return INVALID_OPERATION;

    if (NULL == addrString) {
        mRetransmitEndpointValid = false;
        return OK;
    }

    struct in_addr saddr;
    if(!inet_aton(addrString, &saddr)) {
        return BAD_VALUE;
    }

    memset(&mRetransmitEndpoint, 0, sizeof(mRetransmitEndpoint));
    mRetransmitEndpoint.sin_family = AF_INET;
    mRetransmitEndpoint.sin_addr   = saddr;
    mRetransmitEndpoint.sin_port   = htons(port);
    mRetransmitEndpointValid       = true;

    return OK;
}

void MediaPlayer::notify(int msg, int ext1, int ext2, const Parcel *obj, Parcel *replyObj)
{
    ALOGV("message received msg=%d, ext1=%d, ext2=%d", msg, ext1, ext2);
    bool send = true;
    bool locked = false;

    // TODO: In the future, we might be on the same thread if the app is
    // running in the same process as the media server. In that case,
    // this will deadlock.
    //
    // The threadId hack below works around this for the care of prepare,
    // seekTo and start within the same process.
    // FIXME: Remember, this is a hack, it's not even a hack that is applied
    // consistently for all use-cases, this needs to be revisited.
    if (mLockThreadId != getThreadId()) {
        mLock.lock();
        locked = true;
    }

    //aw extend. Process AWEXTEND_MEDIA_INFO msg here. Don't notify JNIMediaPlayerListener. eric_wang. 20140303.
    if (msg == AWEXTEND_MEDIA_INFO && ext1 == AWEXTEND_MEDIA_INFO_REQUEST_OPEN_FILE) 
    {
        ALOGV("notify(%d, %d, %d) callback on mediaplayer, mCurrentState[0x%x]", msg, ext1, ext2, mCurrentState);
        int     nFileFd;
        int     FilePathLen;
        char    FilePath[4096];
        obj->setDataPosition(0);
        FilePathLen = obj->readInt32();
        if(FilePathLen < 4096)
        {
            const char* strdata = (const char*)obj->readInplace(FilePathLen);
            memcpy(FilePath, strdata, FilePathLen);
            FilePath[FilePathLen] = 0;
            //ALOGD("(f:%s, l:%d) FilePath[%s]", __FUNCTION__, __LINE__, FilePath);
            nFileFd  = open(FilePath, O_RDONLY);
            if(nFileFd >= 0)
            {
                //ALOGD("(f:%s, l:%d) open fd[%d] success, FilePath[%s]", __FUNCTION__, __LINE__, nFileFd, FilePath);
                replyObj->writeInt32(true);
                replyObj->writeDupFileDescriptor(nFileFd);
                close(nFileFd);
            }
            else
            {
                //ALOGW("(f:%s, l:%d) open fd[%d] fail, FilePath[%s]", __FUNCTION__, __LINE__, nFileFd, FilePath);
                replyObj->writeInt32(false);
            }
        }
        else
        {
            ALOGW("fatal error! FilePathLen[%d] >= maxLen[4096]", FilePathLen);
            replyObj->writeInt32(false);
        }

        if (locked) mLock.unlock();   // release the lock when done.
        return;
    }
    else if(msg == AWEXTEND_MEDIA_INFO && ext1 == AWEXTEND_MEDIA_INFO_REQUEST_OPEN_DIR)
    {
        ALOGV("notify(%d, %d, %d) callback on mediaplayer, mCurrentState[0x%x]", msg, ext1, ext2, mCurrentState);

        int     nDirPathLen;
        char    DirPath[4096];
        if(mAWExtendDirId >= 0)
        {
            ALOGD("(f:%s, l:%d) fatal error! already open one directory[%d], nested open is not support now!", __FUNCTION__, __LINE__, mAWExtendDirId);
            replyObj->writeInt32(-1);
            goto __end_open_dir;
        }
        obj->setDataPosition(0);
        nDirPathLen = obj->readInt32();
        if(nDirPathLen < 4096)
        {
            const char* strdata = (const char*)obj->readInplace(nDirPathLen);
            memcpy(DirPath, strdata, nDirPathLen);
            DirPath[nDirPathLen] = 0;
            ALOGD("(f:%s, l:%d) AWEXTEND_MEDIA_INFO_REQUEST_OPEN_DIR, DirPath[%s]", __FUNCTION__, __LINE__, DirPath);
            mAWExtendDp = opendir((char*)DirPath);
            if (mAWExtendDp)
            {
                mAWExtendDirId = 0;
                replyObj->writeInt32(mAWExtendDirId);
            }
            else
            {
                ALOGW("(f:%s, l:%d) open directory[%s] error\n", __FUNCTION__, __LINE__, DirPath);
                replyObj->writeInt32(-1);
            }
        }
        else
        {
            ALOGW("fatal error! nDirPathLen[%d] >= maxLen[4096]", nDirPathLen);
            replyObj->writeInt32(-1);
        }
        
__end_open_dir:
        if (locked) mLock.unlock();   // release the lock when done.
        return;
    }
    else if(msg == AWEXTEND_MEDIA_INFO && ext1 == AWEXTEND_MEDIA_INFO_REQUEST_READ_DIR)
    {
        ALOGV("notify(%d, %d, %d) callback on mediaplayer, mCurrentState[0x%x]", msg, ext1, ext2, mCurrentState);
        int     nDirId;
        struct dirent *filename;
        int     nFileNameLen;
        obj->setDataPosition(0);
        nDirId = obj->readInt32();
        if(nDirId==mAWExtendDirId && nDirId>=0)
        {
            if(mAWExtendDp)
            {
                filename=readdir(mAWExtendDp);
                if(filename!=NULL)
                {
                    nFileNameLen = strlen(filename->d_name);
                    if(nFileNameLen > 0)
                    {
                        replyObj->writeInt32(0);
                        replyObj->writeInt32(nFileNameLen);
                        replyObj->write(filename->d_name, nFileNameLen);
                    }
                    else
                    {
                        ALOGW("(f:%s, l:%d) fatal error! why filename is empty string?", __FUNCTION__, __LINE__);
                        replyObj->writeInt32(-1);
                    }
                }
                else
                {
                    ALOGD("(f:%s, l:%d) nDirId[%d] has read over!", __FUNCTION__, __LINE__, nDirId);
                    replyObj->writeInt32(-1);
                }
            }
            else
            {
                ALOGW("(f:%s, l:%d) fatal error! nDirId[%d] is match, but mAWExtendDp==NULL", __FUNCTION__, __LINE__, nDirId);
                replyObj->writeInt32(-1);
            }
        }
        else
        {
            ALOGW("(f:%s, l:%d) fatal error! nDirId[%d] is not match mAWExtendDirId[%d]", __FUNCTION__, __LINE__, nDirId, mAWExtendDirId);
            replyObj->writeInt32(-1);
        }

        if (locked) mLock.unlock();   // release the lock when done.
        return;
    }
    else if(msg == AWEXTEND_MEDIA_INFO && ext1 == AWEXTEND_MEDIA_INFO_REQUEST_CLOSE_DIR)
    {
        ALOGV("notify(%d, %d, %d) callback on mediaplayer, mCurrentState[0x%x]", msg, ext1, ext2, mCurrentState);
        int     nDirId;
        obj->setDataPosition(0);
        nDirId = obj->readInt32();
        if(nDirId==mAWExtendDirId && nDirId>=0)
        {
            if(mAWExtendDp)
            {
                closedir(mAWExtendDp);
                mAWExtendDp = NULL;
                replyObj->writeInt32(0);
            }
            else
            {
                ALOGW("(f:%s, l:%d) fatal error! nDirId[%d] is match, but mAWExtendDp==NULL", __FUNCTION__, __LINE__, nDirId);
                replyObj->writeInt32(-1);
            }
        }
        else
        {
            ALOGW("(f:%s, l:%d) fatal error! nDirId[%d] is not match mAWExtendDirId[%d]", __FUNCTION__, __LINE__, nDirId, mAWExtendDirId);
            replyObj->writeInt32(-1);
        }

        if (locked) mLock.unlock();   // release the lock when done.
        return;
    }
	else if(msg == AWEXTEND_MEDIA_INFO && ext1 == AWEXTEND_MEDIA_INFO_CHECK_ACCESS_RIGHRS)
	{
        ALOGV("notify(%d, %d, %d) callback on mediaplayer, mCurrentState[0x%x]", msg, ext1, ext2, mCurrentState);
        int     isAccessable = -1;
        int     FilePathLen;
        char    FilePath[4096];
		int 	mode;
        obj->setDataPosition(0);
        FilePathLen = obj->readInt32();
        if(FilePathLen < 4096)
        {
            const char* strdata = (const char*)obj->readInplace(FilePathLen);
            memcpy(FilePath, strdata, FilePathLen);
            FilePath[FilePathLen] = 0;
            //ALOGD("(f:%s, l:%d) FilePath[%s]", __FUNCTION__, __LINE__, FilePath);
			mode = obj->readInt32();
            isAccessable = access((const char *)FilePath, mode);
        }
        else
        {
            ALOGW("(f:%s, l:%d) fatal error! FilePathLen[%d] >=4096", __FUNCTION__, __LINE__, FilePathLen);
        }
		replyObj->writeInt32(isAccessable);

        if (locked) mLock.unlock();   // release the lock when done.
        return;
	}
    //aw extend end. Process AWEXTEND_MEDIA_INFO msg here. Don't notify JNIMediaPlayerListener. eric_wang. 20140303.
    
    // Allows calls from JNI in idle state to notify errors
    if (!(msg == MEDIA_ERROR && mCurrentState == MEDIA_PLAYER_IDLE) && mPlayer == 0) {
        ALOGV("notify(%d, %d, %d) callback on disconnected mediaplayer", msg, ext1, ext2);
        if (locked) mLock.unlock();   // release the lock when done.
        return;
    }

    switch (msg) {
    case MEDIA_NOP: // interface test message
        break;
    case MEDIA_PREPARED:
        ALOGV("prepared");
        mCurrentState = MEDIA_PLAYER_PREPARED;
        if (mPrepareSync) {
            ALOGV("signal application thread");
            mPrepareSync = false;
            mPrepareStatus = NO_ERROR;
            mSignal.signal();
        }
        break;
    case MEDIA_PLAYBACK_COMPLETE:
        ALOGV("playback complete");
        if (mCurrentState == MEDIA_PLAYER_IDLE) {
            ALOGE("playback complete in idle state");
        }
        if (!mLoop) {
            mCurrentState = MEDIA_PLAYER_PLAYBACK_COMPLETE;
        }
        break;
    case MEDIA_ERROR:
        // Always log errors.
        // ext1: Media framework error code.
        // ext2: Implementation dependant error code.
        ALOGE("error (%d, %d)", ext1, ext2);
        mCurrentState = MEDIA_PLAYER_STATE_ERROR;
        if (mPrepareSync)
        {
            ALOGV("signal application thread");
            mPrepareSync = false;
            mPrepareStatus = ext1;
            mSignal.signal();
            send = false;
        }
        break;
    case MEDIA_INFO:
        // ext1: Media framework error code.
        // ext2: Implementation dependant error code.
        if (ext1 != MEDIA_INFO_VIDEO_TRACK_LAGGING) {
            ALOGW("info/warning (%d, %d)", ext1, ext2);
        }
        break;
    case MEDIA_SEEK_COMPLETE:
        ALOGV("Received seek complete");
        if (mSeekPosition != mCurrentPosition) {
            ALOGV("Executing queued seekTo(%d)", mSeekPosition);
            mSeekPosition = -1;
            seekTo_l(mCurrentPosition);
        }
        else {
            ALOGV("All seeks complete - return to regularly scheduled program");
            mCurrentPosition = mSeekPosition = -1;
        }
        break;
    case MEDIA_BUFFERING_UPDATE:
        ALOGV("buffering %d", ext1);
        break;
    case MEDIA_SET_VIDEO_SIZE:
        ALOGV("New video size %d x %d", ext1, ext2);
        mVideoWidth = ext1;
        mVideoHeight = ext2;
        break;
    case MEDIA_TIMED_TEXT:
        ALOGV("Received timed text message");
        break;
    case MEDIA_SUBTITLE_DATA:
        ALOGV("Received subtitle data message");
        break;
    case MEDIA_META_DATA:
        ALOGV("Received timed metadata message");
        break;
    default:
        ALOGV("unrecognized message: (%d, %d, %d)", msg, ext1, ext2);
        break;
    }

    sp<MediaPlayerListener> listener = mListener;
    if (locked) mLock.unlock();

    // this prevents re-entrant calls into client code
    if ((listener != 0) && send) {
        Mutex::Autolock _l(mNotifyLock);
        ALOGV("callback application");
        listener->notify(msg, ext1, ext2, obj);
        ALOGV("back from callback");
    }
}

void MediaPlayer::died()
{
    ALOGV("died");
    notify(MEDIA_ERROR, MEDIA_ERROR_SERVER_DIED, 0);
}

status_t MediaPlayer::setNextMediaPlayer(const sp<MediaPlayer>& next) {
    Mutex::Autolock _l(mLock);
    if (mPlayer == NULL) {
        return NO_INIT;
    }

    if (next != NULL && !(next->mCurrentState &
            (MEDIA_PLAYER_PREPARED | MEDIA_PLAYER_PAUSED | MEDIA_PLAYER_PLAYBACK_COMPLETE))) {
        ALOGE("next player is not prepared");
        return INVALID_OPERATION;
    }

    return mPlayer->setNextPlayer(next == NULL ? NULL : next->mPlayer);
}

status_t MediaPlayer::getMediaPlayerList()
{
    ALOGV("getMediaPlayerList");
	
	const sp<IMediaPlayerService>& service(getMediaPlayerService());
    if (service != 0) {
        return service->getMediaPlayerList();

    }else {
        return BAD_VALUE;
    }
}

status_t MediaPlayer::getMediaPlayerInfo(int mediaPlayerId, struct MediaPlayerInfo* mediaPlayerInfo)
{
    ALOGV("getMediaPlayerInfo");
	
	const sp<IMediaPlayerService>& service(getMediaPlayerService());
    if (service != 0) {
        return service->getMediaPlayerInfo(mediaPlayerId, mediaPlayerInfo);

    }else {
        return BAD_VALUE;
    }
}

/* add by Gary. start {{----------------------------------- */
/* 2012-03-07 */
/* set audio channel mute */
status_t MediaPlayer::setChannelMuteMode(int muteMode)
{
    Mutex::Autolock lock(mLock);
    mMuteMode = muteMode;
    if (mPlayer == NULL) {
        return OK;
    }
    return mPlayer->setChannelMuteMode(muteMode);
}


int MediaPlayer::getChannelMuteMode()
{
    Mutex::Autolock lock(mLock);
    if (mPlayer == NULL) {
        return 0xFFFFFFFF;
    }
    return mPlayer->getChannelMuteMode();
}
/* add by Gary. end   -----------------------------------}} */

status_t MediaPlayer::setSubDelay(int time)
{
    Mutex::Autolock lock(mLock);
    mSubDelay = time;
    if (mPlayer == NULL) {
        return OK;
    }
    return mPlayer->setSubDelay(time);
}


int MediaPlayer::getSubDelay()
{
    Mutex::Autolock lock(mLock);
    if (mPlayer == NULL) {
        return -1;
    }
    return mPlayer->getSubDelay();
}


status_t MediaPlayer::setSubCharset(const char *charset)
{
    Mutex::Autolock lock(mLock);
    strcpy(mSubCharset, charset);
    if (mPlayer == NULL) {
        return OK;
    }
    return mPlayer->setSubCharset(charset);
}


status_t MediaPlayer::getSubCharset(char *charset)
{
    Mutex::Autolock lock(mLock);
    if (mPlayer == NULL) {
        return NO_INIT;
    }
    return mPlayer->getSubCharset(charset);
}

/* add by Gary. start {{----------------------------------- */
/* 2011-11-14 */
/* support scale mode */
status_t MediaPlayer::enableScaleMode(bool enable, int width, int height)
{
    Mutex::Autolock lock(mLock);
    if (mPlayer == NULL) {
        return NO_INIT;
    }
    return mPlayer->enableScaleMode(enable, width, height);
}

/* add by Gary. end   -----------------------------------}} */
/* add by Gary. start {{----------------------------------- */
/* 2012-03-12 */
/* add the global interfaces to control the subtitle gate  */

bool MediaPlayer::getGlobalSubGate()
{
    const sp<IMediaPlayerService>& service(getMediaPlayerService());
    if (service != 0) {
        return service->getGlobalSubGate();
    }else {
        return -1;
    }
}

status_t MediaPlayer::setGlobalSubGate(bool showSub)
{
    const sp<IMediaPlayerService>& service(getMediaPlayerService());
    if (service != 0) {
        return service->setGlobalSubGate(showSub);
    }else {
        return BAD_VALUE;
    }
}
/* add by Gary. end   -----------------------------------}} */

/* add by Gary. start {{----------------------------------- */
/* 2012-4-24 */
/* add two general interfaces for expansibility */
status_t MediaPlayer::generalInterface(int cmd, int int1, int int2, int int3, void *p)
{
    Mutex::Autolock lock(mLock);
	if(cmd==MEDIAPLAYER_CMD_SET_BD_FOLDER_PLAY_MODE)
	{
	    mBDFolderPlayMode = int1;
        return OK;
    }
    else if(cmd==MEDIAPLAYER_CMD_GET_BD_FOLDER_PLAY_MODE)
    {
        *((int*)p) = mBDFolderPlayMode;
		return OK;
    }
    if (mPlayer == NULL) {
        return NO_INIT;
    }
    return mPlayer->generalInterface(cmd, int1, int2, int3, p);
}

status_t MediaPlayer::generalGlobalInterface(int cmd, int int1, int int2, int int3, void *p)
{
    const sp<IMediaPlayerService>& service(getMediaPlayerService());
    if (service != 0) {
        return service->generalGlobalInterface(cmd, int1, int2, int3, p);
    }else {
        return NO_INIT;
    }
}

/* add by Gary. end   -----------------------------------}} */
} // namespace android
