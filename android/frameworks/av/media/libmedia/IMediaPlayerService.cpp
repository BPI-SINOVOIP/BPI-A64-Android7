/*
**
** Copyright 2008, The Android Open Source Project
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

#include <stdint.h>
#include <sys/types.h>

#include <binder/Parcel.h>
#include <binder/IMemory.h>
#include <media/IHDCP.h>
#include <media/IMediaCodecList.h>
#include <media/IMediaHTTPService.h>
#include <media/IMediaPlayerService.h>
#include <media/IMediaRecorder.h>
#include <media/IOMX.h>
#include <media/IRemoteDisplay.h>
#include <media/IRemoteDisplayClient.h>
#include <media/IStreamSource.h>

#include <utils/Errors.h>  // for status_t
#include <utils/String8.h>

namespace android {

enum {
    CREATE = IBinder::FIRST_CALL_TRANSACTION,
    CREATE_MEDIA_RECORDER,
    CREATE_METADATA_RETRIEVER,
    GET_OMX,
    MAKE_HDCP,
    ADD_BATTERY_DATA,
    PULL_BATTERY_DATA,
    LISTEN_FOR_REMOTE_DISPLAY,
    GET_CODEC_LIST,

	/* add by Gary. start {{----------------------------------- */
    /* 2012-03-12 */
    /* add the global interfaces to control the subtitle gate  */
    SET_GLOBAL_SUB_GATE,
    GET_GLOBAL_SUB_GATE,
    /* add by Gary. end   -----------------------------------}} */
    /* add by Gary. start {{----------------------------------- */
    /* 2012-4-24 */
    /* add two general interfaces for expansibility */
    GENERAL_GLOBAL_INTERFACE,
    /* add by Gary. end   -----------------------------------}} */
	/* add by aw. start {{----------------------------------- */
	GET_MEDIAPLAYER_LIST,
	GET_MEDIAPLAYER_INFO,
	/* add by aw. end   -----------------------------------}} */
};

class BpMediaPlayerService: public BpInterface<IMediaPlayerService>
{
public:
    BpMediaPlayerService(const sp<IBinder>& impl)
        : BpInterface<IMediaPlayerService>(impl)
    {
    }

    virtual sp<IMediaMetadataRetriever> createMetadataRetriever()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        remote()->transact(CREATE_METADATA_RETRIEVER, data, &reply);
        return interface_cast<IMediaMetadataRetriever>(reply.readStrongBinder());
    }

    virtual sp<IMediaPlayer> create(
            const sp<IMediaPlayerClient>& client, audio_session_t audioSessionId) {
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        data.writeStrongBinder(IInterface::asBinder(client));
        data.writeInt32(audioSessionId);

        remote()->transact(CREATE, data, &reply);
        return interface_cast<IMediaPlayer>(reply.readStrongBinder());
    }

    virtual sp<IMediaRecorder> createMediaRecorder(const String16 &opPackageName)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        data.writeString16(opPackageName);
        remote()->transact(CREATE_MEDIA_RECORDER, data, &reply);
        return interface_cast<IMediaRecorder>(reply.readStrongBinder());
    }

    virtual sp<IOMX> getOMX() {
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        remote()->transact(GET_OMX, data, &reply);
        return interface_cast<IOMX>(reply.readStrongBinder());
    }

    virtual sp<IHDCP> makeHDCP(bool createEncryptionModule) {
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        data.writeInt32(createEncryptionModule);
        remote()->transact(MAKE_HDCP, data, &reply);
        return interface_cast<IHDCP>(reply.readStrongBinder());
    }

    virtual void addBatteryData(uint32_t params) {
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        data.writeInt32(params);
        remote()->transact(ADD_BATTERY_DATA, data, &reply);
    }

    virtual status_t pullBatteryData(Parcel* reply) {
        Parcel data;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        return remote()->transact(PULL_BATTERY_DATA, data, reply);
    }

    virtual sp<IRemoteDisplay> listenForRemoteDisplay(const String16 &opPackageName,
            const sp<IRemoteDisplayClient>& client, const String8& iface)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        data.writeString16(opPackageName);
        data.writeStrongBinder(IInterface::asBinder(client));
        data.writeString8(iface);
        remote()->transact(LISTEN_FOR_REMOTE_DISPLAY, data, &reply);
        return interface_cast<IRemoteDisplay>(reply.readStrongBinder());
    }

    virtual sp<IMediaCodecList> getCodecList() const {
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        remote()->transact(GET_CODEC_LIST, data, &reply);
        return interface_cast<IMediaCodecList>(reply.readStrongBinder());
    }
    /* add by Gary. start {{----------------------------------- */
    /* 2012-03-12 */
    /* add the global interfaces to control the subtitle gate  */
    status_t setGlobalSubGate(bool showSub)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        data.writeInt32(showSub);
        remote()->transact(SET_GLOBAL_SUB_GATE, data, &reply);
        return reply.readInt32();
    }
    bool getGlobalSubGate()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        remote()->transact(GET_GLOBAL_SUB_GATE, data, &reply);
        return reply.readInt32();
    }
    /* add by Gary. end   -----------------------------------}} */
	// get mediaplayer list about mediaplayer
	status_t getMediaPlayerList()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        remote()->transact(GET_MEDIAPLAYER_LIST, data, &reply);
        return reply.readInt32();
    }
	// get information about a mediaplayer
    status_t getMediaPlayerInfo(int mediaPlayerId, 
                                   struct MediaPlayerInfo* mediaPlayerInfo) {
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        data.writeInt32(mediaPlayerId);
        remote()->transact(GET_MEDIAPLAYER_INFO, data, &reply);
        mediaPlayerInfo->width = reply.readInt32();
        mediaPlayerInfo->height = reply.readInt32();
		mediaPlayerInfo->codecType = reply.readInt32();
        mediaPlayerInfo->playState = reply.readInt32();
        return reply.readInt32();
    }
    /* add by Gary. start {{----------------------------------- */
    /* 2012-4-24 */
    /* add two general interfaces for expansibility */
    status_t generalGlobalInterface(int cmd, int int1, int /* int2 */ , int /* int3 */ , void *p)
    {
        Parcel data, reply;
        status_t ret;
        data.writeInterfaceToken(IMediaPlayerService::getInterfaceDescriptor());
        
        data.writeInt32(cmd);                               // the first input value MUST be always the command.
        switch(cmd){
            case MEDIAPLAYER_GLOBAL_CMD_TEST:{
                data.writeInt32(int1);
                remote()->transact(GENERAL_GLOBAL_INTERFACE, data, &reply);
                ret = reply.readInt32();
                *((int *)p) = reply.readInt32();
            }break;
			case MEDIAPLAYER_CMD_IS_ROTATABLE:{
                remote()->transact(GENERAL_GLOBAL_INTERFACE, data, &reply);
                ret = reply.readInt32();
                *((int *)p) = reply.readInt32();
			}break;
			case MEDIAPLAYER_CMD_SET_ROTATION:{
                data.writeInt32(int1);
                remote()->transact(GENERAL_GLOBAL_INTERFACE, data, &reply);
                ret = reply.readInt32();
			}break;
            case MEDIAPLAYER_CMD_SET_HDMISTATE:{
                data.writeInt32(int1);
                remote()->transact(GENERAL_GLOBAL_INTERFACE, data, &reply);
                ret = reply.readInt32();
			}break;
            default:
                return BAD_VALUE;
        }
        return ret;
    }
    /* add by Gary. end   -----------------------------------}} */
};

IMPLEMENT_META_INTERFACE(MediaPlayerService, "android.media.IMediaPlayerService");

// ----------------------------------------------------------------------

status_t BnMediaPlayerService::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    switch (code) {
        case CREATE: {
            CHECK_INTERFACE(IMediaPlayerService, data, reply);
            sp<IMediaPlayerClient> client =
                interface_cast<IMediaPlayerClient>(data.readStrongBinder());
            audio_session_t audioSessionId = (audio_session_t) data.readInt32();
            sp<IMediaPlayer> player = create(client, audioSessionId);
            reply->writeStrongBinder(IInterface::asBinder(player));
            return NO_ERROR;
        } break;
        case CREATE_MEDIA_RECORDER: {
            CHECK_INTERFACE(IMediaPlayerService, data, reply);
            const String16 opPackageName = data.readString16();
            sp<IMediaRecorder> recorder = createMediaRecorder(opPackageName);
            reply->writeStrongBinder(IInterface::asBinder(recorder));
            return NO_ERROR;
        } break;
        case CREATE_METADATA_RETRIEVER: {
            CHECK_INTERFACE(IMediaPlayerService, data, reply);
            sp<IMediaMetadataRetriever> retriever = createMetadataRetriever();
            reply->writeStrongBinder(IInterface::asBinder(retriever));
            return NO_ERROR;
        } break;
        case GET_OMX: {
            CHECK_INTERFACE(IMediaPlayerService, data, reply);
            sp<IOMX> omx = getOMX();
            reply->writeStrongBinder(IInterface::asBinder(omx));
            return NO_ERROR;
        } break;
        case MAKE_HDCP: {
            CHECK_INTERFACE(IMediaPlayerService, data, reply);
            bool createEncryptionModule = data.readInt32();
            sp<IHDCP> hdcp = makeHDCP(createEncryptionModule);
            reply->writeStrongBinder(IInterface::asBinder(hdcp));
            return NO_ERROR;
        } break;
        case ADD_BATTERY_DATA: {
            CHECK_INTERFACE(IMediaPlayerService, data, reply);
            uint32_t params = data.readInt32();
            addBatteryData(params);
            return NO_ERROR;
        } break;
        case PULL_BATTERY_DATA: {
            CHECK_INTERFACE(IMediaPlayerService, data, reply);
            pullBatteryData(reply);
            return NO_ERROR;
        } break;
        case LISTEN_FOR_REMOTE_DISPLAY: {
            CHECK_INTERFACE(IMediaPlayerService, data, reply);
            const String16 opPackageName = data.readString16();
            sp<IRemoteDisplayClient> client(
                    interface_cast<IRemoteDisplayClient>(data.readStrongBinder()));
            if (client == NULL) {
                reply->writeStrongBinder(NULL);
                return NO_ERROR;
            }
            String8 iface(data.readString8());
            sp<IRemoteDisplay> display(listenForRemoteDisplay(opPackageName, client, iface));
            reply->writeStrongBinder(IInterface::asBinder(display));
            return NO_ERROR;
        } break;
        case GET_CODEC_LIST: {
            CHECK_INTERFACE(IMediaPlayerService, data, reply);
            sp<IMediaCodecList> mcl = getCodecList();
            reply->writeStrongBinder(IInterface::asBinder(mcl));
            return NO_ERROR;
        } break;
        
       //* add the global interfaces to control the subtitle gate  
        case SET_GLOBAL_SUB_GATE: {
            CHECK_INTERFACE(IMediaPlayer, data, reply);
            reply->writeInt32(setGlobalSubGate(data.readInt32()));
            return NO_ERROR;
        } break;
        case GET_GLOBAL_SUB_GATE: {
            CHECK_INTERFACE(IMediaPlayer, data, reply);
            reply->writeInt32(getGlobalSubGate());
            return NO_ERROR;
        } break;
		case GET_MEDIAPLAYER_LIST: {
            CHECK_INTERFACE(IMediaPlayer, data, reply);
            reply->writeInt32(getMediaPlayerList());
            return NO_ERROR;
        } break;
		case GET_MEDIAPLAYER_INFO: {
            CHECK_INTERFACE(IMediaPlayer, data, reply);
			struct MediaPlayerInfo mediaPlayerInfo;
            memset(&mediaPlayerInfo, 0, sizeof(mediaPlayerInfo));
            status_t result = getMediaPlayerInfo(data.readInt32(), &mediaPlayerInfo);
            reply->writeInt32(mediaPlayerInfo.width);
            reply->writeInt32(mediaPlayerInfo.height);
			reply->writeInt32(mediaPlayerInfo.codecType);
            reply->writeInt32(mediaPlayerInfo.playState);
            reply->writeInt32(result);
            return NO_ERROR;
        } break;
        //* add general interfaces for expansibility 
        case GENERAL_GLOBAL_INTERFACE: {      
            CHECK_INTERFACE(IMediaPlayer, data, reply);
            int cmd;
            int int1 = 0;
            int int2 = 0;
            int int3 = 0;
            void *p  = NULL;
            status_t ret;
            
            cmd = data.readInt32();
            switch(cmd){
                case MEDIAPLAYER_GLOBAL_CMD_TEST:{
                    int1 = data.readInt32();
                    int data = 2;
                    p = &data;
                    ret = generalGlobalInterface(cmd, int1, int2, int3, p);
                    reply->writeInt32(ret);
                    reply->writeInt32(data);
                }break;
                case MEDIAPLAYER_CMD_IS_ROTATABLE:{
                    int rotatable;
                    p = &rotatable;
                    ret = generalGlobalInterface(cmd, int1, int2, int3, p);
                    reply->writeInt32(ret);
                    reply->writeInt32(rotatable);
                }break;
				case MEDIAPLAYER_CMD_SET_ROTATION:{
                    int1 = data.readInt32();
                    ret = generalGlobalInterface(cmd, int1, int2, int3, p);
                    reply->writeInt32(ret);
				}break;
                case MEDIAPLAYER_CMD_SET_HDMISTATE:{
                    int1 = data.readInt32();
                    ret = generalGlobalInterface(cmd, int1, int2, int3, p);
                    reply->writeInt32(ret);
				}break;
                default:
                    return BAD_VALUE;
            }
            return NO_ERROR;
        } break;
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}

// ----------------------------------------------------------------------------

} // namespace android
