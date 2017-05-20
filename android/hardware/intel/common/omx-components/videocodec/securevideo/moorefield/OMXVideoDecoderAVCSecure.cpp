/*
* Copyright (c) 2009-2012 Intel Corporation.  All rights reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/


//#define LOG_NDEBUG 0
#define LOG_TAG "OMXVideoDecoderAVCSecure"
#include <wrs_omxil_core/log.h>
#include "OMXVideoDecoderAVCSecure.h"
#include <time.h>
#include <signal.h>
#include <pthread.h>
#include <sys/mman.h>
#include <cutils/ashmem.h>
#include <OMX_IntelIndexExt.h>
#include <OMXComponentCodecBase.h>

#include "LogDumpHelper.h"
#include "VideoFrameInfo.h"
#include "ProtectedDataBuffer.h"


// Be sure to have an equal string in VideoDecoderHost.cpp (libmix)
static const char* AVC_MIME_TYPE = "video/avc";
static const char* AVC_SECURE_MIME_TYPE = "video/avc-secure";

#define INPORT_BUFFER_SIZE  sizeof(ProtectedDataBuffer)
#define DATA_BUFFER_INITIAL_OFFSET      0 //1024
#define DATA_BUFFER_SIZE                (8 * 1024 * 1024)
#define KEEP_ALIVE_INTERVAL             5 // seconds
#define DRM_KEEP_ALIVE_TIMER            1000000
#define WV_SESSION_ID                   0x00000011
#define NALU_HEADER_LENGTH              1024 // THis should be changed to 4K
#define FLUSH_WAIT_INTERVAL             (30 * 1000) //30 ms

#define DRM_SCHEME_NONE     0
#define DRM_SCHEME_WVC      1
#define DRM_SCHEME_CENC     2
#define DRM_SCHEME_PRASF    3

bool OMXVideoDecoderAVCSecure::EnableIEDSession(bool enable)
{
    if (mDrmDevFd <= 0) {
        ALOGE("invalid mDrmDevFd");
        return false;
    }
    int request = enable ?  DRM_PSB_ENABLE_IED_SESSION : DRM_PSB_DISABLE_IED_SESSION;
    int ret = drmCommandNone(mDrmDevFd, request);
    return ret == 0;
}

OMXVideoDecoderAVCSecure::OMXVideoDecoderAVCSecure()
    : mKeepAliveTimer(0),
      mNumInportBuffers(0),
      mSessionPaused(false){
    ALOGV("OMXVideoDecoderAVCSecure is constructed.");
    if (drm_vendor_api_init(&drm_vendor_api)) {
        ALOGE("drm_vendor_api_init failed");
    }
    mVideoDecoder = createVideoDecoder(AVC_SECURE_MIME_TYPE);
    if (!mVideoDecoder) {
        ALOGE("createVideoDecoder failed for \"%s\"", AVC_SECURE_MIME_TYPE);
    }
    // Override default native buffer count defined in the base class
    mNativeBufferCount = OUTPORT_NATIVE_BUFFER_COUNT;

    BuildHandlerList();

    mDrmDevFd = open("/dev/card0", O_RDWR, 0);
    if (mDrmDevFd <= 0) {
        ALOGE("Failed to open drm device.");
    }
}

OMXVideoDecoderAVCSecure::~OMXVideoDecoderAVCSecure() {
    ALOGI("OMXVideoDecoderAVCSecure is destructed.");
    if (drm_vendor_api_deinit(&drm_vendor_api)) {
        ALOGE("drm_vendor_api_deinit failed");
    }
    if (mDrmDevFd > 0) {
        close(mDrmDevFd);
        mDrmDevFd = 0;
    }
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::InitInputPortFormatSpecific(OMX_PARAM_PORTDEFINITIONTYPE *paramPortDefinitionInput) {
    // OMX_PARAM_PORTDEFINITIONTYPE
    paramPortDefinitionInput->nBufferCountActual = INPORT_ACTUAL_BUFFER_COUNT;
    paramPortDefinitionInput->nBufferCountMin = INPORT_MIN_BUFFER_COUNT;
    paramPortDefinitionInput->nBufferSize = INPORT_BUFFER_SIZE;
    paramPortDefinitionInput->format.video.cMIMEType = (OMX_STRING)AVC_MIME_TYPE;
    paramPortDefinitionInput->format.video.eCompressionFormat = OMX_VIDEO_CodingAVC;

    // OMX_VIDEO_PARAM_AVCTYPE
    memset(&mParamAvc, 0, sizeof(mParamAvc));
    SetTypeHeader(&mParamAvc, sizeof(mParamAvc));
    mParamAvc.nPortIndex = INPORT_INDEX;
    // TODO: check eProfile/eLevel
    mParamAvc.eProfile = OMX_VIDEO_AVCProfileHigh; //OMX_VIDEO_AVCProfileBaseline;
    mParamAvc.eLevel = OMX_VIDEO_AVCLevel41; //OMX_VIDEO_AVCLevel1;

    this->ports[INPORT_INDEX]->SetMemAllocator(MemAllocDataBuffer, MemFreeDataBuffer, this);

    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::ProcessorInit(void) {
    mSessionPaused = false;
    if (drm_vendor_api.handle == NULL) {
        return OMX_ErrorUndefined;
    }
    return OMXVideoDecoderBase::ProcessorInit();
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::ProcessorDeinit(void) {

    WaitForFrameDisplayed();
    // Session should be torn down in ProcessorStop, delayed to ProcessorDeinit
    // to allow remaining frames completely rendered.
    ALOGI("Calling Drm_DestroySession.");
    uint32_t ret = drm_vendor_api.drm_stop_playback();
    if (ret != DRM_WV_MOD_SUCCESS) {
        ALOGE("drm_stop_playback failed: (0x%x)", ret);
    }
    EnableIEDSession(false);
    return OMXVideoDecoderBase::ProcessorDeinit();
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::ProcessorStart(void) {

    EnableIEDSession(true);
    uint32_t ret = drm_vendor_api.drm_start_playback();
    if (ret != DRM_WV_MOD_SUCCESS) {
        ALOGE("drm_start_playback failed: (0x%x)", ret);
    }

    mSessionPaused = false;
    return OMXVideoDecoderBase::ProcessorStart();
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::ProcessorStop(void) {
    if (mKeepAliveTimer != 0) {
        timer_delete(mKeepAliveTimer);
        mKeepAliveTimer = 0;
    }

    return OMXVideoDecoderBase::ProcessorStop();
}


OMX_ERRORTYPE OMXVideoDecoderAVCSecure::ProcessorFlush(OMX_U32 portIndex) {
    return OMXVideoDecoderBase::ProcessorFlush(portIndex);
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::ProcessorProcess(
        OMX_BUFFERHEADERTYPE ***pBuffers,
        buffer_retain_t *retains,
        OMX_U32 numberBuffers) {

    int ret_value;

    OMX_BUFFERHEADERTYPE *pInput = *pBuffers[INPORT_INDEX];
    native_handle_t *native_handle = (native_handle_t *)pInput->pBuffer;
    ProtectedDataBuffer *dataBuffer = (ProtectedDataBuffer *) native_handle->data[1];

    // Check that we are dealing with the right buffer
    if (dataBuffer->magic != PROTECTED_DATA_BUFFER_MAGIC)
    {
        if (pInput->nFlags & OMX_BUFFERFLAG_CODECCONFIG)
        {
            // Processing codec data, which is not in ProtectedDataBuffer format
            ALOGV("%s: received AVC codec data (%" PRIu32 " bytes).", __FUNCTION__, pInput->nFilledLen);
            DumpBuffer2("OMX: AVC codec data: ", pInput->pBuffer, pInput->nFilledLen);
            return OMX_ErrorNone;
        }
        else
        {
            // Processing non-codec data, but this buffer is not in ProtectedDataBuffer format
            ALOGE("%s: protected data buffer pointer %p doesn't have the right magic", __FUNCTION__, dataBuffer);
            return OMX_ErrorBadParameter;
        }
    }

    if((dataBuffer->drmScheme == DRM_SCHEME_WVC) && (!mKeepAliveTimer)){
        struct sigevent sev;
        memset(&sev, 0, sizeof(sev));
        sev.sigev_notify = SIGEV_THREAD;
        sev.sigev_value.sival_ptr = this;
        sev.sigev_notify_function = KeepAliveTimerCallback;

        ret_value = timer_create(CLOCK_REALTIME, &sev, &mKeepAliveTimer);
        if (ret_value != 0) {
            ALOGE("Failed to create timer.");
        } else {
            struct itimerspec its;
            its.it_value.tv_sec = -1; // never expire
            its.it_value.tv_nsec = 0;
            its.it_interval.tv_sec = KEEP_ALIVE_INTERVAL;
            its.it_interval.tv_nsec = 0;

            ret_value = timer_settime(mKeepAliveTimer, TIMER_ABSTIME, &its, NULL);
            if (ret_value != 0) {
                ALOGE("Failed to set timer.");
            }
        }
    }

    if (dataBuffer->size == 0) {
        // error occurs during decryption.
        ALOGW("size of returned data buffer is 0, decryption fails.");
        mVideoDecoder->flush();
        usleep(FLUSH_WAIT_INTERVAL);
        OMX_BUFFERHEADERTYPE *pOutput = *pBuffers[OUTPORT_INDEX];
        pOutput->nFilledLen = 0;
        // reset Data buffer size
        dataBuffer->size = INPORT_BUFFER_SIZE;
        this->ports[INPORT_INDEX]->FlushPort();
        this->ports[OUTPORT_INDEX]->FlushPort();
        return OMX_ErrorNone;
    }

    OMX_ERRORTYPE ret;
    ret = OMXVideoDecoderBase::ProcessorProcess(pBuffers, retains, numberBuffers);
    if (ret != OMX_ErrorNone) {
        ALOGE("OMXVideoDecoderBase::ProcessorProcess failed. Result: %#x", ret);
        return ret;
    }

    if (mSessionPaused && (retains[OUTPORT_INDEX] == BUFFER_RETAIN_GETAGAIN)) {
        retains[OUTPORT_INDEX] = BUFFER_RETAIN_NOT_RETAIN;
        OMX_BUFFERHEADERTYPE *pOutput = *pBuffers[OUTPORT_INDEX];
        pOutput->nFilledLen = 0;
        this->ports[INPORT_INDEX]->FlushPort();
        this->ports[OUTPORT_INDEX]->FlushPort();
    }

    return ret;
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::ProcessorPause(void) {
    return OMXVideoDecoderBase::ProcessorPause();
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::ProcessorResume(void) {
    return OMXVideoDecoderBase::ProcessorResume();
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::PrepareConfigBuffer(VideoConfigBuffer *p) {
    OMX_ERRORTYPE ret;
	ret = OMXVideoDecoderBase::PrepareConfigBuffer(p);
    CHECK_RETURN_VALUE("OMXVideoDecoderBase::PrepareConfigBuffer");
    p->flag |=  WANT_SURFACE_PROTECTION;
    return ret;
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::PrepareWVCDecodeBuffer(OMX_BUFFERHEADERTYPE *buffer, buffer_retain_t *retain, VideoDecodeBuffer *p)
{

   OMX_ERRORTYPE ret = OMX_ErrorNone;
   (void) retain; // unused parameter

   p->flag |= HAS_COMPLETE_FRAME;

   if (buffer->nOffset != 0) {
       ALOGW("buffer offset %u is not zero!!!", buffer->nOffset);
   }
   native_handle_t *native_handle = (native_handle_t *)buffer->pBuffer;
   ProtectedDataBuffer *dataBuffer = (ProtectedDataBuffer *) native_handle->data[1];
   if (dataBuffer->clear) {
       p->data = dataBuffer->data + buffer->nOffset;
       p->size = buffer->nFilledLen;
   } else {
       dataBuffer->size = NALU_BUFFER_SIZE;
       struct drm_wv_nalu_headers nalu_headers;
       nalu_headers.p_enc_ciphertext = dataBuffer->data;

       // TODO: NALU Buffer is supposed to be 4k but using 1k, fix it once chaabi fix is there

       nalu_headers.hdrs_buf_len = NALU_HEADER_LENGTH;
       nalu_headers.frame_size = buffer->nFilledLen;
       // Make sure that NALU header frame size is 16 bytes aligned
       nalu_headers.frame_size = (nalu_headers.frame_size + 0xF) & (~0xF);
       // Use same video buffer to fill NALU headers returned by chaabi,
       // Adding 4 because the first 4 bytes after databuffer will be used to store length of NALU headers
       if((nalu_headers.frame_size + NALU_HEADER_LENGTH) > INPORT_BUFFER_SIZE){
           ALOGE("Not enough buffer for NALU headers");
           return OMX_ErrorOverflow;
       }

       nalu_headers.p_hdrs_buf = (uint8_t *)(dataBuffer->data + nalu_headers.frame_size + 4);
       nalu_headers.parse_size = buffer->nFilledLen;

       uint32_t res = drm_vendor_api.drm_wv_return_naluheaders(WV_SESSION_ID, &nalu_headers);
       if (res == DRM_FAIL_FW_SESSION) {
           ALOGW("Drm_WV_ReturnNALUHeaders failed. Session is disabled.");
           mSessionPaused = true;
           ret =  OMX_ErrorNotReady;
       } else if (res != 0) {
           mSessionPaused = false;
           ALOGE("Drm_WV_ReturnNALUHeaders failed. Error = %#x, frame_size: %d, len = %u", res, nalu_headers.frame_size, buffer->nFilledLen);
           ret = OMX_ErrorHardware;
       } else {
           mSessionPaused = false;

           // If chaabi returns 0 NALU headers fill the frame size to zero.
           if (!nalu_headers.hdrs_buf_len) {
               p->size = 0;
               return ret;
           }
           else{
               // NALU headers are appended to encrypted video bitstream
               // |...encrypted video bitstream (16 bytes aligned)...| 4 bytes of header size |...NALU headers..|
               uint32_t *ptr = (uint32_t*)(dataBuffer->data + nalu_headers.frame_size);
               *ptr = nalu_headers.hdrs_buf_len;
               p->data = dataBuffer->data;
               p->size = nalu_headers.frame_size;
               p->flag |= IS_SECURE_DATA;
           }
       }
   }

   // reset Data size
   dataBuffer->size = NALU_BUFFER_SIZE;
   return ret;
}
OMX_ERRORTYPE OMXVideoDecoderAVCSecure::PrepareCENCDecodeBuffer(OMX_BUFFERHEADERTYPE *buffer, buffer_retain_t *retain, VideoDecodeBuffer *p)
{
    OMX_ERRORTYPE ret = OMX_ErrorNone;
    (void) retain; // unused parameter

    // OMX_BUFFERFLAG_CODECCONFIG is an optional flag
    // if flag is set, buffer will only contain codec data.
    if (buffer->nFlags & OMX_BUFFERFLAG_CODECCONFIG) {
        ALOGI("Received AVC codec data.");
    //    return ret;
    }
    p->flag |= HAS_COMPLETE_FRAME | IS_SUBSAMPLE_ENCRYPTION;

    if (buffer->nOffset != 0) {
        ALOGW("buffer offset %u is not zero!!!", buffer->nOffset);
    }

    native_handle_t *native_handle = (native_handle_t *)buffer->pBuffer;
    ProtectedDataBuffer *dataBuffer = (ProtectedDataBuffer *) native_handle->data[1];

    p->data = dataBuffer->data;
    p->size = sizeof(frame_info_t);
    p->flag |= IS_SECURE_DATA;
    return ret;
}


OMX_ERRORTYPE OMXVideoDecoderAVCSecure::PreparePRASFDecodeBuffer(OMX_BUFFERHEADERTYPE *buffer, buffer_retain_t *retain, VideoDecodeBuffer *p)
{
    OMX_ERRORTYPE ret = OMX_ErrorNone;
    (void) retain; // unused parameter

    // OMX_BUFFERFLAG_CODECCONFIG is an optional flag
    // if flag is set, buffer will only contain codec data.
    if (buffer->nFlags & OMX_BUFFERFLAG_CODECCONFIG) {
        ALOGV("PR: Received codec data.");
        return ret;
    }
    p->flag |= HAS_COMPLETE_FRAME;

    if (buffer->nOffset != 0) {
        ALOGW("PR:buffer offset %u is not zero!!!", buffer->nOffset);
    }

    native_handle_t *native_handle = (native_handle_t *)buffer->pBuffer;
    ProtectedDataBuffer *dataBuffer = (ProtectedDataBuffer *) native_handle->data[1];
    if (dataBuffer->clear) {
        p->data = dataBuffer->data + buffer->nOffset;
        p->size = buffer->nFilledLen;
    } else {
        dataBuffer->size = NALU_BUFFER_SIZE;
        struct drm_nalu_headers nalu_headers;
        nalu_headers.p_enc_ciphertext = dataBuffer->data;

        // TODO: NALU Buffer is supposed to be 4k but using 1k, fix it once chaabi fix is there
        nalu_headers.hdrs_buf_len = NALU_HEADER_LENGTH;
        nalu_headers.frame_size = buffer->nFilledLen;
        // Make sure that NALU header frame size is 16 bytes aligned
        nalu_headers.frame_size = (nalu_headers.frame_size + 0xF) & (~0xF);
        // Use same video buffer to fill NALU headers returned by chaabi,
        // Adding 4 because the first 4 bytes after databuffer will be used to store length of NALU headers
        if((nalu_headers.frame_size + NALU_HEADER_LENGTH) > INPORT_BUFFER_SIZE){
            ALOGE("Not enough buffer for NALU headers");
            return OMX_ErrorOverflow;
        }

        nalu_headers.p_hdrs_buf = (uint8_t *)(dataBuffer->data + nalu_headers.frame_size + 4);
        nalu_headers.parse_size = buffer->nFilledLen;

        uint32_t res = drm_vendor_api.drm_pr_return_naluheaders(dataBuffer->session_id, &nalu_headers);

        if (res == DRM_FAIL_FW_SESSION || !nalu_headers.hdrs_buf_len) {
            ALOGW("drm_ReturnNALUHeaders failed. Session is disabled.");
            mSessionPaused = true;
            ret =  OMX_ErrorNotReady;
        } else if (res != 0) {
            mSessionPaused = false;
            ALOGE("drm_pr_return_naluheaders failed. Error = %#x, frame_size: %d, len = %u", res, nalu_headers.frame_size, buffer->nFilledLen);
            ret = OMX_ErrorHardware;
        } else {
           mSessionPaused = false;

           // If chaabi returns 0 NALU headers fill the frame size to zero.
           if (!nalu_headers.hdrs_buf_len) {
               p->size = 0;
               return ret;
           }
           else{
               // NALU headers are appended to encrypted video bitstream
               // |...encrypted video bitstream (16 bytes aligned)...| 4 bytes of header size |...NALU headers..|
               uint32_t *ptr = (uint32_t*)(dataBuffer->data + nalu_headers.frame_size);
               *ptr = nalu_headers.hdrs_buf_len;
               p->data = dataBuffer->data;
               p->size = nalu_headers.frame_size;
               p->flag |= IS_SECURE_DATA;
           }
       }
    }

    // reset Data size
    dataBuffer->size = NALU_BUFFER_SIZE;
    return ret;
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::PrepareDecodeBuffer(OMX_BUFFERHEADERTYPE *buffer, buffer_retain_t *retain, VideoDecodeBuffer *p) {
    OMX_ERRORTYPE ret;

    ret = OMXVideoDecoderBase::PrepareDecodeNativeHandleBuffer(buffer, retain, p);
    CHECK_RETURN_VALUE("OMXVideoDecoderBase::PrepareDecodeBuffer");

    if (buffer->nFilledLen == 0) {
        return OMX_ErrorNone;
    }
    native_handle_t *native_handle = (native_handle_t *)buffer->pBuffer;

    ProtectedDataBuffer *dataBuffer = (ProtectedDataBuffer *) native_handle->data[1];

    // Check that we are dealing with the right buffer
    if (dataBuffer->magic != PROTECTED_DATA_BUFFER_MAGIC)
    {
        if (buffer->nFlags & OMX_BUFFERFLAG_CODECCONFIG)
        {
            // Processing codec data, which is not in ProtectedDataBuffer format
            ALOGI("%s: received AVC codec data (%" PRIu32 " bytes).", __FUNCTION__, buffer->nFilledLen);
            DumpBuffer2("OMX: AVC codec data: ", buffer->pBuffer, buffer->nFilledLen) ;
            return OMX_ErrorNone;
        }
        else
        {
            // Processing non-codec data, but this buffer is not in ProtectedDataBuffer format
            ALOGE("%s: protected data buffer pointer %p doesn't have the right magic", __FUNCTION__, dataBuffer);
            return OMX_ErrorBadParameter;
        }
    }
    // End of magic check

    if(dataBuffer->drmScheme == DRM_SCHEME_WVC){

        // OMX_BUFFERFLAG_CODECCONFIG is an optional flag
        // if flag is set, buffer will only contain codec data.
        mDrmScheme = DRM_SCHEME_WVC;
        if (buffer->nFlags & OMX_BUFFERFLAG_CODECCONFIG) {
               ALOGV("Received AVC codec data.");
               return ret;
        }
        return PrepareWVCDecodeBuffer(buffer, retain, p);
    }
    else if(dataBuffer->drmScheme == DRM_SCHEME_CENC) {
        mDrmScheme = DRM_SCHEME_CENC;
        return PrepareCENCDecodeBuffer(buffer, retain, p);
    }
    else if(dataBuffer->drmScheme == DRM_SCHEME_PRASF)
    {
        mDrmScheme = DRM_SCHEME_PRASF;
        return  PreparePRASFDecodeBuffer(buffer, retain, p);
    }
    return ret;
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::BuildHandlerList(void) {
    OMXVideoDecoderBase::BuildHandlerList();
    AddHandler(OMX_IndexParamVideoAvc, GetParamVideoAvc, SetParamVideoAvc);
    AddHandler(OMX_IndexParamVideoProfileLevelQuerySupported, GetParamVideoAVCProfileLevel, SetParamVideoAVCProfileLevel);
    AddHandler(static_cast<OMX_INDEXTYPE>(OMX_IndexExtAllocateNativeHandle), GetExtAllocateNativeHandle, SetExtAllocateNativeHandle);
    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::GetParamVideoAvc(OMX_PTR pStructure) {
    OMX_ERRORTYPE ret;
    OMX_VIDEO_PARAM_AVCTYPE *p = (OMX_VIDEO_PARAM_AVCTYPE *)pStructure;
    CHECK_TYPE_HEADER(p);
    CHECK_PORT_INDEX(p, INPORT_INDEX);

    memcpy(p, &mParamAvc, sizeof(*p));
    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::SetParamVideoAvc(OMX_PTR pStructure) {
    OMX_ERRORTYPE ret;
    OMX_VIDEO_PARAM_AVCTYPE *p = (OMX_VIDEO_PARAM_AVCTYPE *)pStructure;
    CHECK_TYPE_HEADER(p);
    CHECK_PORT_INDEX(p, INPORT_INDEX);
    CHECK_SET_PARAM_STATE();

    // TODO: do we need to check if port is enabled?
    // TODO: see SetPortAvcParam implementation - Can we make simple copy????
    memcpy(&mParamAvc, p, sizeof(mParamAvc));
    return OMX_ErrorNone;
}


OMX_ERRORTYPE OMXVideoDecoderAVCSecure::GetParamVideoAVCProfileLevel(OMX_PTR pStructure) {
    OMX_ERRORTYPE ret;
    OMX_VIDEO_PARAM_PROFILELEVELTYPE *p = (OMX_VIDEO_PARAM_PROFILELEVELTYPE *)pStructure;
    CHECK_TYPE_HEADER(p);
    CHECK_PORT_INDEX(p, INPORT_INDEX);

    struct ProfileLevelTable {
        OMX_U32 profile;
        OMX_U32 level;
    } plTable[] = {
        {OMX_VIDEO_AVCProfileBaseline, OMX_VIDEO_AVCLevel42},
        {OMX_VIDEO_AVCProfileMain, OMX_VIDEO_AVCLevel42},
        {OMX_VIDEO_AVCProfileHigh, OMX_VIDEO_AVCLevel42}
    };

    OMX_U32 count = sizeof(plTable)/sizeof(ProfileLevelTable);
    CHECK_ENUMERATION_RANGE(p->nProfileIndex,count);

    p->eProfile = plTable[p->nProfileIndex].profile;
    p->eLevel = plTable[p->nProfileIndex].level;

    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::SetParamVideoAVCProfileLevel(OMX_PTR pStructure) {
    ALOGW("SetParamVideoAVCProfileLevel is not supported.");
    (void) pStructure; // unused parameter
    return OMX_ErrorUnsupportedSetting;
}


OMX_ERRORTYPE OMXVideoDecoderAVCSecure::GetExtAllocateNativeHandle(OMX_PTR pStructure) {
    (void) pStructure; // unused parameter

    return OMX_ErrorNone;

}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::SetExtAllocateNativeHandle(OMX_PTR pStructure) {
    OMX_ERRORTYPE ret;
    android:: EnableAndroidNativeBuffersParams  *p = (android::EnableAndroidNativeBuffersParams  *)pStructure;
    CHECK_TYPE_HEADER(p);
    CHECK_SET_PARAM_STATE();

    return OMX_ErrorNone;
}
OMX_U8* OMXVideoDecoderAVCSecure::MemAllocDataBuffer(OMX_U32 nSizeBytes, OMX_PTR pUserData) {
    OMXVideoDecoderAVCSecure* p = (OMXVideoDecoderAVCSecure *)pUserData;
    if (p) {
        return p->MemAllocDataBuffer(nSizeBytes);
    }
    ALOGE("NULL pUserData.");
    return NULL;
}

void OMXVideoDecoderAVCSecure::MemFreeDataBuffer(OMX_U8 *pBuffer, OMX_PTR pUserData) {
    OMXVideoDecoderAVCSecure* p = (OMXVideoDecoderAVCSecure *)pUserData;
    if (p) {
        p->MemFreeDataBuffer(pBuffer);
        return;
    }
    ALOGE("NULL pUserData.");
}

OMX_U8* OMXVideoDecoderAVCSecure::MemAllocDataBuffer(OMX_U32 nSizeBytes) {

    ALOGW_IF(nSizeBytes != INPORT_BUFFER_SIZE,
        "%s: size of memory to allocate is %" PRIu32 ", but will allocate %zu",
        __FUNCTION__, nSizeBytes, sizeof(ProtectedDataBuffer));
    
    if (mNumInportBuffers >= INPORT_ACTUAL_BUFFER_COUNT)
    {
        ALOGE("%s: cannot allocate buffer: number of inport buffers is %u, which is already at maximum",
            __FUNCTION__, mNumInportBuffers);
        return NULL;
    }


    int fd = ashmem_create_region("protectd-content-buffer", sizeof(ProtectedDataBuffer));
    if(fd < 0) {
        ALOGE("Unable to create ashmem region");
        return NULL;
    }

    native_handle_t *native = native_handle_create(1, 2);

    native->data[0] = fd;
    ProtectedDataBuffer *pBuffer =(ProtectedDataBuffer *) mmap(NULL, sizeof(ProtectedDataBuffer), PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (pBuffer == MAP_FAILED) {
        ALOGE("%s: mmap failed", __FUNCTION__);
        return NULL;
    }
    native->data[1] = (int) pBuffer;
    // Use a random value as the buffer id
    native->data[2] = rand();
    ++mNumInportBuffers;

    Init_ProtectedDataBuffer(pBuffer);
    
    pBuffer->size = INPORT_BUFFER_SIZE;

    ALOGV("Allocating native=[%p] buffer = %#x, data = %#x data_end=  %#x size=%d",(OMX_U8 *)native,(uint32_t)pBuffer, (uint32_t)pBuffer->data, (uint32_t)pBuffer->data + sizeof(ProtectedDataBuffer) ,sizeof(ProtectedDataBuffer));
    return (OMX_U8 *) native;
}

void OMXVideoDecoderAVCSecure::MemFreeDataBuffer(OMX_U8 *pBuffer) {

    if (pBuffer == NULL)
    {
        ALOGE("%s: trying to free NULL pointer", __FUNCTION__);
        return;
    }

    if (mNumInportBuffers == 0)
    {
        ALOGE("%s: allocated inport buffer count is already 0, cannot delete buffer %p",
            __FUNCTION__, pBuffer);
        return;
    }
    
    native_handle_t *native_handle = (native_handle_t *) pBuffer;

    ProtectedDataBuffer *dataBuffer = (ProtectedDataBuffer *) native_handle->data[1];
    if (dataBuffer->magic != PROTECTED_DATA_BUFFER_MAGIC)
    {
        ALOGE("%s: attempting to free buffer with a wrong magic 0x%08x", __FUNCTION__, dataBuffer->magic);
        return;
    }

    if (munmap(dataBuffer, sizeof(ProtectedDataBuffer)) != 0) {
        ALOGE("%s: Faild to munmap %p",__FUNCTION__, dataBuffer);
        return;
    }
    ALOGV("Free databuffer %p with data = %p", dataBuffer, dataBuffer->data);
    --mNumInportBuffers;
}

void OMXVideoDecoderAVCSecure::KeepAliveTimerCallback(sigval v) {
    OMXVideoDecoderAVCSecure *p = (OMXVideoDecoderAVCSecure *)v.sival_ptr;
    if (p) {
        p->KeepAliveTimerCallback();
    }
}

void OMXVideoDecoderAVCSecure::KeepAliveTimerCallback() {
    uint32_t timeout = DRM_KEEP_ALIVE_TIMER;
    uint32_t sepres =  drm_vendor_api.drm_keep_alive(WV_SESSION_ID, &timeout);
    if (sepres != 0) {
        ALOGE("Drm_KeepAlive failed. Result = %#x", sepres);
    }
}

void OMXVideoDecoderAVCSecure::WaitForFrameDisplayed() {
    if (mDrmDevFd <= 0) {
        ALOGE("Invalid mDrmDevFd");
        return;
    }

    // Wait up to 200ms until both overlay planes are disabled
    int status = 3;
    int retry = 20;
    while (retry--) {
        for (int i = 0; i < 2; i++) {
            if (status & (1 << i)) {
                struct drm_psb_register_rw_arg arg;
                memset(&arg, 0, sizeof(struct drm_psb_register_rw_arg));
                arg.get_plane_state_mask = 1;
                arg.plane.type = DC_OVERLAY_PLANE;
                arg.plane.index = i;
                int ret = drmCommandWriteRead(mDrmDevFd, DRM_PSB_REGISTER_RW, &arg, sizeof(arg));
                if (ret != 0) {
                    ALOGE("Failed to query status of overlay plane %d, ret = %d", i, ret);
                    status &= ~(1 << i);
                } else if (arg.plane.ctx == PSB_DC_PLANE_DISABLED) {
                    status &= ~(1 << i);
                }
            }
        }
        if (status == 0) {
            break;
        }
        // Sleep 10ms then query again
        usleep(10000);
    }

    if (status != 0) {
        ALOGE("Overlay planes not disabled, status %d", status);
    }
}

OMX_ERRORTYPE OMXVideoDecoderAVCSecure::SetMaxOutputBufferCount(OMX_PARAM_PORTDEFINITIONTYPE *p) {
    OMX_ERRORTYPE ret;
    CHECK_TYPE_HEADER(p);
    CHECK_PORT_INDEX(p, OUTPORT_INDEX);

    p->nBufferCountActual = OUTPORT_NATIVE_BUFFER_COUNT;
    return OMXVideoDecoderBase::SetMaxOutputBufferCount(p);
}
DECLARE_OMX_COMPONENT("OMX.Intel.VideoDecoder.AVC.secure", "video_decoder.avc", OMXVideoDecoderAVCSecure);
