/*
* Copyright (c) 2012 Intel Corporation.  All rights reserved.
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

#define LOG_TIME 0
//#define LOG_NDEBUG 0
#define LOG_TAG "OMXVideoDecoderVP9Hybrid"
#include <wrs_omxil_core/log.h>
#include "OMXVideoDecoderVP9Hybrid.h"

#include <system/window.h>
#include <hardware/hardware.h>
#include <hardware/gralloc.h>
#include <system/graphics.h>

#include <hal_public.h>

#define VP9_YV12_ALIGN (128-1)
static const char* VP9_MIME_TYPE = "video/x-vnd.on2.vp9";

OMXVideoDecoderVP9Hybrid::OMXVideoDecoderVP9Hybrid() {
    LOGV("OMXVideoDecoderVP9Hybrid is constructed.");
    mNativeBufferCount = OUTPORT_NATIVE_BUFFER_COUNT;
    BuildHandlerList();
    mLibHandle = NULL;
    mOpenDecoder = NULL;
    mInitDecoder = NULL;
    mCloseDecoder = NULL;
    mSingalRenderDone = NULL;
    mDecoderDecode = NULL;
    mCheckBufferAvailable = NULL;
    mGetOutput = NULL;
    mGetRawDataOutput = NULL;
    mGetFrameResolution = NULL;
    mDeinitDecoder = NULL;
    mLastTimeStamp = 0;
    mWorkingMode = RAWDATA_MODE;
    mDecodedImageWidth = 0;
    mDecodedImageHeight = 0;
    mDecodedImageNewWidth = 0;
    mDecodedImageNewHeight = 0;
}

OMXVideoDecoderVP9Hybrid::~OMXVideoDecoderVP9Hybrid() {
    LOGV("OMXVideoDecoderVP9Hybrid is destructed.");
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::InitInputPortFormatSpecific(
    OMX_PARAM_PORTDEFINITIONTYPE *paramPortDefinitionInput) {
    // OMX_PARAM_PORTDEFINITIONTYPE
    paramPortDefinitionInput->nBufferCountActual = INPORT_ACTUAL_BUFFER_COUNT;
    paramPortDefinitionInput->nBufferCountMin = INPORT_MIN_BUFFER_COUNT;
    paramPortDefinitionInput->nBufferSize = INPORT_BUFFER_SIZE;
    paramPortDefinitionInput->format.video.cMIMEType = (OMX_STRING)VP9_MIME_TYPE;
    paramPortDefinitionInput->format.video.eCompressionFormat = OMX_VIDEO_CodingVP9;
    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::ProcessorInit(void) {
    uint32_t buff[MAX_GRAPHIC_BUFFER_NUM];
    uint32_t i, bufferCount;
    bool gralloc_mode = (mWorkingMode == GRAPHICBUFFER_MODE);
    uint32_t bufferSize, bufferHStride, bufferHeight, bufferVStride, bufferWidth;
    if (!gralloc_mode) {
        bufferHStride = 1920;
        bufferVStride = 1088;
        bufferWidth = 1920;
        bufferHeight = 1080;
        bufferCount = 12;
    } else {
        if (mAPMode == METADATA_MODE) {
            const OMX_PARAM_PORTDEFINITIONTYPE *def_output = this->ports[OUTPORT_INDEX]->GetPortDefinition();
            if (def_output == NULL) {
                return OMX_ErrorBadParameter;
            }
            bufferCount = mMetaDataBuffersNum = def_output->nBufferCountActual;
            mOMXBufferHeaderTypePtrNum = 0;

            mGraphicBufferParam.graphicBufferColorFormat = def_output->format.video.eColorFormat;
            mGraphicBufferParam.graphicBufferHStride = (def_output->format.video.nFrameWidth + VP9_YV12_ALIGN) & ~VP9_YV12_ALIGN;
            mGraphicBufferParam.graphicBufferVStride = (def_output->format.video.nFrameHeight + 0x1f) & ~0x1f;
            mGraphicBufferParam.graphicBufferWidth = def_output->format.video.nFrameWidth;
            mGraphicBufferParam.graphicBufferHeight = def_output->format.video.nFrameHeight;
            mDecodedImageWidth = def_output->format.video.nFrameWidth;
            mDecodedImageHeight = def_output->format.video.nFrameHeight;
        } else{
            bufferCount = mOMXBufferHeaderTypePtrNum;

            for (i = 0; i < bufferCount; i++ ) {
                OMX_BUFFERHEADERTYPE *buf_hdr = mOMXBufferHeaderTypePtrArray[i];
                buff[i] = (uint32_t)(buf_hdr->pBuffer);
            }
        }

        bufferHStride = mGraphicBufferParam.graphicBufferHStride;
        bufferVStride = mGraphicBufferParam.graphicBufferVStride;
        bufferWidth = mGraphicBufferParam.graphicBufferWidth;
        bufferHeight = mGraphicBufferParam.graphicBufferHeight;
    }

    bufferSize = bufferHStride * bufferVStride * 1.5;

    mLibHandle = dlopen("libDecoderVP9Hybrid.so", RTLD_NOW);
    if (mLibHandle == NULL) {
        LOGE("dlopen libDecoderVP9Hybrid.so fail\n");
        return OMX_ErrorBadParameter;
    } else {
        LOGI("dlopen libDecoderVP9Hybrid.so successfully\n");
    }
    mOpenDecoder = (OpenFunc)dlsym(mLibHandle, "Decoder_Open");
    mCloseDecoder = (CloseFunc)dlsym(mLibHandle, "Decoder_Close");
    mInitDecoder = (InitFunc)dlsym(mLibHandle, "Decoder_Init");
    mSingalRenderDone = (SingalRenderDoneFunc)dlsym(mLibHandle, "Decoder_SingalRenderDone");
    mDecoderDecode = (DecodeFunc)dlsym(mLibHandle, "Decoder_Decode");
    mCheckBufferAvailable = (IsBufferAvailableFunc)dlsym(mLibHandle, "Decoder_IsBufferAvailable");
    mGetOutput = (GetOutputFunc)dlsym(mLibHandle, "Decoder_GetOutput");
    mGetRawDataOutput = (GetRawDataOutputFunc)dlsym(mLibHandle, "Decoder_GetRawDataOutput");
    mGetFrameResolution = (GetFrameResolutionFunc)dlsym(mLibHandle, "Decoder_GetFrameResolution");
    mDeinitDecoder = (DeinitFunc)dlsym(mLibHandle, "Decoder_Deinit");
    if (mOpenDecoder == NULL || mCloseDecoder == NULL
        || mInitDecoder == NULL || mSingalRenderDone == NULL
        || mDecoderDecode == NULL || mCheckBufferAvailable == NULL
        || mGetOutput == NULL || mGetRawDataOutput == NULL
        || mGetFrameResolution == NULL || mDeinitDecoder == NULL) {
        return OMX_ErrorBadParameter;
    }

    if (mOpenDecoder(&mCtx,&mHybridCtx) == false) {
        LOGE("open hybrid Decoder fail\n");
        return OMX_ErrorBadParameter;
    }

    // FIXME: The proprietary part of the vp9hybrid decoder should be updated
    //        to take VStride as well as Height. For now it's convenient to
    //        use VStride as that was effectively what was done before..
    mInitDecoder(mHybridCtx, bufferSize, bufferHStride, bufferWidth,
        bufferHeight, bufferCount, gralloc_mode, buff, (uint32_t)mAPMode);
    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::ProcessorReset(void)
{
    uint32_t buff[MAX_GRAPHIC_BUFFER_NUM];
    uint32_t i, bufferCount;
    bool gralloc_mode = (mWorkingMode == GRAPHICBUFFER_MODE);
    uint32_t bufferSize, bufferHStride, bufferHeight, bufferVStride, bufferWidth;
    if (!gralloc_mode) {
        bufferHStride = mDecodedImageWidth;
        bufferVStride = mDecodedImageHeight;
        bufferWidth = mDecodedImageWidth;
        bufferHeight = mDecodedImageHeight;
        bufferSize = bufferHStride * bufferVStride * 1.5;
        bufferCount = 12;
    } else {
        if (mAPMode == METADATA_MODE) {
            const OMX_PARAM_PORTDEFINITIONTYPE *def_output = this->ports[OUTPORT_INDEX]->GetPortDefinition();
            if (def_output == NULL) {
                return OMX_ErrorBadParameter;
            }
            bufferCount = mMetaDataBuffersNum = def_output->nBufferCountActual;
            mOMXBufferHeaderTypePtrNum = 0;

            mGraphicBufferParam.graphicBufferColorFormat = def_output->format.video.eColorFormat;
            mGraphicBufferParam.graphicBufferHStride = (def_output->format.video.nFrameWidth + VP9_YV12_ALIGN) & ~VP9_YV12_ALIGN;
            mGraphicBufferParam.graphicBufferVStride = (def_output->format.video.nFrameHeight + 0x1f) & ~0x1f;
            mGraphicBufferParam.graphicBufferWidth = def_output->format.video.nFrameWidth;
            mGraphicBufferParam.graphicBufferHeight = def_output->format.video.nFrameHeight;
        } else{
            bufferCount = mOMXBufferHeaderTypePtrNum;

            for (i = 0; i < bufferCount; i++ ) {
                OMX_BUFFERHEADERTYPE *buf_hdr = mOMXBufferHeaderTypePtrArray[i];
                buff[i] = (uint32_t)(buf_hdr->pBuffer);
            }
        }
        bufferHStride = mGraphicBufferParam.graphicBufferHStride;
        bufferVStride = mGraphicBufferParam.graphicBufferVStride;
        bufferWidth = mGraphicBufferParam.graphicBufferWidth;
        bufferHeight = mGraphicBufferParam.graphicBufferHeight;
    }

    bufferSize = bufferHStride * bufferVStride * 1.5;

    // FIXME: The proprietary part of the vp9hybrid decoder should be updated
    //        to take VStride as well as Height. For now it's convenient to
    //        use VStride as that was effectively what was done before..
    mInitDecoder(mHybridCtx, bufferSize, bufferHStride, bufferWidth,
        bufferHeight, bufferCount, gralloc_mode, buff, (uint32_t)mAPMode);
    mFormatChanged = false;
    return OMX_ErrorNone;
}

bool OMXVideoDecoderVP9Hybrid::isReallocateNeeded(const uint8_t * data,uint32_t data_sz)
{
    bool gralloc_mode = (mWorkingMode == GRAPHICBUFFER_MODE);
    uint32_t width, height;
    bool ret = true;
    if (gralloc_mode) {
        ret = mGetFrameResolution(data,data_sz, &width, &height);
        if (width == 0 || height == 0)
            return false;

        if (ret) {
            if (mAPMode == METADATA_MODE) {
                ret = (width != mDecodedImageWidth)
                    || (height != mDecodedImageHeight);
            } else {
                ret = width > mGraphicBufferParam.graphicBufferWidth
                    || height > mGraphicBufferParam.graphicBufferHeight;
            }
            if (ret) {
                mDecodedImageNewWidth = width;
                mDecodedImageNewHeight = height;
                return true;
            }
        }
    }

    return ret;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::ProcessorDeinit(void) {
    mCloseDecoder(mCtx,mHybridCtx);
    mOMXBufferHeaderTypePtrNum = 0;
    if (mLibHandle != NULL) {
        dlclose(mLibHandle);
        mLibHandle = NULL;
    }
    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::ProcessorStop(void) {
    return OMXComponentCodecBase::ProcessorStop();
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::ProcessorFlush(OMX_U32 portIndex) {
    if (portIndex == INPORT_INDEX || portIndex == OMX_ALL) {
        // end the last frame
        unsigned int width, height;
        mDecoderDecode(mCtx,mHybridCtx,NULL,0,true);
        mGetOutput(mCtx,mHybridCtx, &width, &height);
    }
    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::ProcessorPreFillBuffer(OMX_BUFFERHEADERTYPE* buffer) {
    if (buffer->nOutputPortIndex == OUTPORT_INDEX){
        unsigned int handle;
        if (mAPMode == METADATA_MODE) {
            bool found = false;
            if (mOMXBufferHeaderTypePtrNum < mMetaDataBuffersNum) {
                for (uint32_t i = 0; i < mOMXBufferHeaderTypePtrNum; i++) {
                    if (mOMXBufferHeaderTypePtrArray[i] == buffer) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    mOMXBufferHeaderTypePtrArray[mOMXBufferHeaderTypePtrNum] = buffer;
                    mOMXBufferHeaderTypePtrNum++;
                }
            } else {
                found = true;
            }

            android::VideoGrallocMetadata *metadata = (android::VideoGrallocMetadata *)(buffer->pBuffer);
            handle = (unsigned int)metadata->pHandle;
            mSingalRenderDone(mHybridCtx, handle, !found);
        } else {
            handle = (unsigned int)buffer->pBuffer;
            mSingalRenderDone(mHybridCtx, handle, false);
        }
    }
    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::ProcessorProcess(
        OMX_BUFFERHEADERTYPE ***pBuffers,
        buffer_retain_t *retains,
        OMX_U32)
{
    OMX_ERRORTYPE ret;
    OMX_BUFFERHEADERTYPE *inBuffer = *pBuffers[INPORT_INDEX];
    OMX_BUFFERHEADERTYPE *outBuffer = *pBuffers[OUTPORT_INDEX];
    OMX_BOOL isResolutionChange = OMX_FALSE;
    bool eos = (inBuffer->nFlags & OMX_BUFFERFLAG_EOS)? true : false;
    eos = eos && (inBuffer->nFilledLen == 0);
    static unsigned char *firstFrame = NULL;
    static uint32_t firstFrameSize = 0;

    if (inBuffer->pBuffer == NULL) {
        LOGE("Buffer to decode is empty.");
        return OMX_ErrorBadParameter;
    }

    if (inBuffer->nFlags & OMX_BUFFERFLAG_CODECCONFIG) {
        LOGI("Buffer has OMX_BUFFERFLAG_CODECCONFIG flag.");
    }

    if (inBuffer->nFlags & OMX_BUFFERFLAG_DECODEONLY) {
        LOGW("Buffer has OMX_BUFFERFLAG_DECODEONLY flag.");
    }

    if (firstFrameSize == 0 && inBuffer->nFilledLen != 0 && inBuffer->nTimeStamp != 0) {
        if (firstFrame != NULL) {
            free(firstFrame);
            firstFrame = NULL;
        }

        firstFrame = (unsigned char *)malloc(inBuffer->nFilledLen);
        memcpy(firstFrame, inBuffer->pBuffer + inBuffer->nOffset, inBuffer->nFilledLen);
        firstFrameSize = inBuffer->nFilledLen;
    }

    if ((mWorkingMode == GRAPHICBUFFER_MODE) && (mAPMode == METADATA_MODE) && (!mFormatChanged)) {
        bool mRet = mGetFrameResolution(inBuffer->pBuffer + inBuffer->nOffset, inBuffer->nFilledLen,
            &mDecodedImageNewWidth,&mDecodedImageNewHeight);

        if (mRet && ((mDecodedImageNewWidth != 0) && (mDecodedImageNewHeight != 0)) &&
            ((mDecodedImageWidth != 0) && (mDecodedImageHeight != 0)) &&
            ((mDecodedImageNewWidth != mDecodedImageWidth || mDecodedImageNewHeight != mDecodedImageHeight))) {
            if (mLastTimeStamp == 0) {
                retains[INPORT_INDEX] = BUFFER_RETAIN_GETAGAIN;
                HandleFormatChange();
                return OMX_ErrorNone;
            } else {
                // Detected format change in time.
                // drain the last frame, keep the current input buffer
                mDecoderDecode(mCtx, mHybridCtx, firstFrame, firstFrameSize, false);
                retains[INPORT_INDEX] = BUFFER_RETAIN_GETAGAIN;

                mFormatChanged = true;

                ret = FillRenderBuffer(pBuffers[OUTPORT_INDEX], &retains[OUTPORT_INDEX],
                    eos ? OMX_BUFFERFLAG_EOS : 0, &isResolutionChange);

                if (ret == OMX_ErrorNone)
                    (*pBuffers[OUTPORT_INDEX])->nTimeStamp = mLastTimeStamp;

                mLastTimeStamp = inBuffer->nTimeStamp;

                free(firstFrame);
                firstFrame = NULL;
                firstFrameSize = 0;
                return ret;
            }
	}
    }

#if LOG_TIME == 1
    struct timeval tv_start, tv_end;
    int32_t time_ms;
    gettimeofday(&tv_start,NULL);
#endif
    int res = mDecoderDecode(mCtx,mHybridCtx,inBuffer->pBuffer + inBuffer->nOffset,inBuffer->nFilledLen, eos);
    if (res != 0) {
        if (res == -2) {
            if (isReallocateNeeded(inBuffer->pBuffer + inBuffer->nOffset,inBuffer->nFilledLen)) {
                if (mAPMode == METADATA_MODE) {
                    mFormatChanged = true;
                } else {
                    retains[INPORT_INDEX] = BUFFER_RETAIN_GETAGAIN;
                    HandleFormatChange();
                    return OMX_ErrorNone;
                }
            }
            // drain the last frame, keep the current input buffer
            res = mDecoderDecode(mCtx,mHybridCtx,NULL,0,true);
            retains[INPORT_INDEX] = BUFFER_RETAIN_GETAGAIN;
        } else if (res == -3) {
            LOGW("on2 decoder skipped to decode the frame.");
            (*pBuffers[OUTPORT_INDEX])->nOffset = 0;
            (*pBuffers[OUTPORT_INDEX])->nFilledLen = 0;
            return OMX_ErrorNone;
        } else {
            LOGE("on2 decoder failed to decode frame.");
            return OMX_ErrorBadParameter;
        }
    }

#if LOG_TIME == 1
    gettimeofday(&tv_end,NULL);
    time_ms = (int32_t)(tv_end.tv_sec - tv_start.tv_sec) * 1000 + (int32_t)(tv_end.tv_usec - tv_start.tv_usec)/1000;
    LOGI("vpx_codec_decode: %d ms", time_ms);
#endif

    ret = FillRenderBuffer(pBuffers[OUTPORT_INDEX],
                           &retains[OUTPORT_INDEX],
                           eos? OMX_BUFFERFLAG_EOS:0,
                           &isResolutionChange);

    if (ret == OMX_ErrorNone) {
        (*pBuffers[OUTPORT_INDEX])->nTimeStamp = mLastTimeStamp;
    }
    mLastTimeStamp = inBuffer->nTimeStamp;

    if (isResolutionChange == OMX_TRUE) {
        HandleFormatChange();
    }
    bool inputEoS = ((*pBuffers[INPORT_INDEX])->nFlags & OMX_BUFFERFLAG_EOS);
    bool outputEoS = ((*pBuffers[OUTPORT_INDEX])->nFlags & OMX_BUFFERFLAG_EOS);
    // if output port is not eos, retain the input buffer
    // until all the output buffers are drained.
    if (inputEoS && !outputEoS && retains[INPORT_INDEX] != BUFFER_RETAIN_GETAGAIN) {
        retains[INPORT_INDEX] = BUFFER_RETAIN_GETAGAIN;
        // the input buffer is retained for draining purpose.
        // Set nFilledLen to 0 so buffer will not be decoded again.
        (*pBuffers[INPORT_INDEX])->nFilledLen = 0;
    }

    if (ret == OMX_ErrorNotReady) {
        retains[OUTPORT_INDEX] = BUFFER_RETAIN_GETAGAIN;
        ret = OMX_ErrorNone;
    }

    return ret;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::FillRenderBuffer(OMX_BUFFERHEADERTYPE **pBuffer,
                                                      buffer_retain_t *retain,
                                                      OMX_U32 inportBufferFlags,
                                                      OMX_BOOL *isResolutionChange)
{
    OMX_BUFFERHEADERTYPE *buffer = *pBuffer;
    OMX_BUFFERHEADERTYPE *buffer_orign = buffer;

    OMX_ERRORTYPE ret = OMX_ErrorNone;

    int fb_index;
    if (mWorkingMode == RAWDATA_MODE) {
        const OMX_PARAM_PORTDEFINITIONTYPE *paramPortDefinitionOutput
                       = this->ports[OUTPORT_INDEX]->GetPortDefinition();
        int32_t stride = paramPortDefinitionOutput->format.video.nStride;
        int32_t height =  paramPortDefinitionOutput->format.video.nFrameHeight;
        int32_t width = paramPortDefinitionOutput->format.video.nFrameWidth;
        unsigned char *dst = buffer->pBuffer;
        fb_index = mGetRawDataOutput(mCtx,mHybridCtx,dst,height,stride);
        if (fb_index == -1) {
            if (inportBufferFlags & OMX_BUFFERFLAG_EOS) {
                // eos frame is non-shown frame
                buffer->nFlags = OMX_BUFFERFLAG_EOS;
                buffer->nOffset = 0;
                buffer->nFilledLen = 0;
                return OMX_ErrorNone;
            }
            LOGV("vpx_codec_get_frame return NULL.");
            return OMX_ErrorNotReady;
        }
        buffer->nOffset = 0;
        buffer->nFilledLen = stride*height*3/2;
        if (inportBufferFlags & OMX_BUFFERFLAG_EOS) {
            buffer->nFlags = OMX_BUFFERFLAG_EOS;
        }
        return OMX_ErrorNone;
    }

    if (mFormatChanged && mAPMode == METADATA_MODE) {
        fb_index = mGetOutput(mCtx,mHybridCtx, &mDecodedImageWidth, &mDecodedImageHeight);
    } else {
        fb_index = mGetOutput(mCtx,mHybridCtx, &mDecodedImageNewWidth, &mDecodedImageNewHeight);
    }
    if (fb_index == -1) {
        if (mFormatChanged && mAPMode == METADATA_MODE) {
            *isResolutionChange = OMX_TRUE;
            return OMX_ErrorNone;
    }

        if (inportBufferFlags & OMX_BUFFERFLAG_EOS) {
            // eos frame is no-shown frame
            buffer->nFlags = OMX_BUFFERFLAG_EOS;
            buffer->nOffset = 0;
            buffer->nFilledLen = 0;
            return OMX_ErrorNone;
        }
        LOGV("vpx_codec_get_frame return NULL.");
        return OMX_ErrorNotReady;
    }
    if (mDecodedImageHeight == 0 && mDecodedImageWidth == 0) {
        mDecodedImageWidth = mDecodedImageNewWidth;
        mDecodedImageHeight = mDecodedImageNewHeight;
        if (mAPMode == LEGACY_MODE)
            *isResolutionChange = OMX_TRUE;
    }

    if (mAPMode == LEGACY_MODE) {
    if ((mDecodedImageNewWidth != mDecodedImageWidth)
        || (mDecodedImageNewHeight!= mDecodedImageHeight))
        *isResolutionChange = OMX_TRUE;
    } else {
        if (mFormatChanged && ((mDecodedImageNewWidth != mDecodedImageWidth)
            || (mDecodedImageNewHeight!= mDecodedImageHeight)))
            *isResolutionChange = OMX_TRUE;
    }
    buffer = *pBuffer = mOMXBufferHeaderTypePtrArray[fb_index];
    buffer->nOffset = 0;
    buffer->nFilledLen = sizeof(OMX_U8*);
    if (inportBufferFlags & OMX_BUFFERFLAG_EOS) {
        buffer->nFlags = OMX_BUFFERFLAG_EOS;
    }

    if (buffer_orign != buffer) {
        *retain = BUFFER_RETAIN_OVERRIDDEN;
    }

    ret = OMX_ErrorNone;

    return ret;

}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::PrepareConfigBuffer(VideoConfigBuffer *) {
    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::PrepareDecodeBuffer(OMX_BUFFERHEADERTYPE *,
                                                         buffer_retain_t *,
                                                         VideoDecodeBuffer *) {
    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::BuildHandlerList(void) {
    OMXVideoDecoderBase::BuildHandlerList();
    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::GetParamVideoVp9(OMX_PTR) {
    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::SetParamVideoVp9(OMX_PTR) {
    return OMX_ErrorNone;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::HandleFormatChange(void)
{
    ALOGE("handle format change from %dx%d to %dx%d",
        mDecodedImageWidth,mDecodedImageHeight,mDecodedImageNewWidth,mDecodedImageNewHeight);
    mDecodedImageWidth = mDecodedImageNewWidth;
    mDecodedImageHeight = mDecodedImageNewHeight;
    // Sync port definition as it may change.
    OMX_PARAM_PORTDEFINITIONTYPE paramPortDefinitionInput, paramPortDefinitionOutput;

    memcpy(&paramPortDefinitionInput,
        this->ports[INPORT_INDEX]->GetPortDefinition(),
        sizeof(paramPortDefinitionInput));

    memcpy(&paramPortDefinitionOutput,
        this->ports[OUTPORT_INDEX]->GetPortDefinition(),
        sizeof(paramPortDefinitionOutput));

    unsigned int width = mDecodedImageWidth;
    unsigned int height = mDecodedImageHeight;
    unsigned int stride = mDecodedImageWidth;
    unsigned int sliceHeight = mDecodedImageHeight;

    unsigned int widthCropped = mDecodedImageWidth;
    unsigned int heightCropped = mDecodedImageHeight;
    unsigned int strideCropped = widthCropped;
    unsigned int sliceHeightCropped = heightCropped;

    if (widthCropped == paramPortDefinitionOutput.format.video.nFrameWidth &&
        heightCropped == paramPortDefinitionOutput.format.video.nFrameHeight) {
        if (mWorkingMode == RAWDATA_MODE) {
            LOGW("Change of portsetting is not reported as size is not changed.");
            return OMX_ErrorNone;
        }
    }

    if (mAPMode == METADATA_MODE) {
       paramPortDefinitionOutput.nBufferCountActual = mNativeBufferCount;
       paramPortDefinitionOutput.nBufferCountMin = mNativeBufferCount - 4;
    }
    paramPortDefinitionInput.format.video.nFrameWidth = width;
    paramPortDefinitionInput.format.video.nFrameHeight = height;
    paramPortDefinitionInput.format.video.nStride = stride;
    paramPortDefinitionInput.format.video.nSliceHeight = sliceHeight;

    if (mWorkingMode == RAWDATA_MODE) {
        paramPortDefinitionOutput.format.video.nFrameWidth = widthCropped;
        paramPortDefinitionOutput.format.video.nFrameHeight = heightCropped;
        paramPortDefinitionOutput.format.video.nStride = strideCropped;
        paramPortDefinitionOutput.format.video.nSliceHeight = sliceHeightCropped;
    } else if (mWorkingMode == GRAPHICBUFFER_MODE) {
        // when the width and height ES parse are not larger than allocated graphic buffer in outport,
        // there is no need to reallocate graphic buffer,just report the crop info to omx client
        if (mAPMode == LEGACY_MODE &&
            width <= mGraphicBufferParam.graphicBufferWidth &&
            height <= mGraphicBufferParam.graphicBufferHeight) {
            this->ports[INPORT_INDEX]->SetPortDefinition(&paramPortDefinitionInput, true);
            this->ports[OUTPORT_INDEX]->ReportOutputCrop();
            return OMX_ErrorNone;
        }

        if (mAPMode == METADATA_MODE ||
            width > mGraphicBufferParam.graphicBufferWidth ||
            height > mGraphicBufferParam.graphicBufferHeight) {
            // update the real decoded resolution to outport instead of display resolution
            // for graphic buffer reallocation
            // when the width and height parsed from ES are larger than allocated graphic buffer in outport,
            paramPortDefinitionOutput.format.video.nFrameWidth = width;
            paramPortDefinitionOutput.format.video.nFrameHeight = height;
            paramPortDefinitionOutput.format.video.eColorFormat = GetOutputColorFormat(
                    paramPortDefinitionOutput.format.video.nFrameWidth);
            paramPortDefinitionOutput.format.video.nStride = stride;
            paramPortDefinitionOutput.format.video.nSliceHeight = sliceHeight;
       }
    }

    paramPortDefinitionOutput.bEnabled = (OMX_BOOL)false;
    mOMXBufferHeaderTypePtrNum = 0;
    mMetaDataBuffersNum = 0;
    memset(&mGraphicBufferParam, 0, sizeof(mGraphicBufferParam));
    mDeinitDecoder(mHybridCtx);

    this->ports[INPORT_INDEX]->SetPortDefinition(&paramPortDefinitionInput, true);
    this->ports[OUTPORT_INDEX]->SetPortDefinition(&paramPortDefinitionOutput, true);

    this->ports[OUTPORT_INDEX]->ReportPortSettingsChanged();
    return OMX_ErrorNone;
}


OMX_COLOR_FORMATTYPE OMXVideoDecoderVP9Hybrid::GetOutputColorFormat(int) {
    LOGV("Output color format is HAL_PIXEL_FORMAT_INTEL_YV12.");
    return (OMX_COLOR_FORMATTYPE)HAL_PIXEL_FORMAT_INTEL_YV12;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::GetDecoderOutputCropSpecific(OMX_PTR pStructure) {

    OMX_ERRORTYPE ret = OMX_ErrorNone;
    OMX_CONFIG_RECTTYPE *rectParams = (OMX_CONFIG_RECTTYPE *)pStructure;

    CHECK_TYPE_HEADER(rectParams);

    if (rectParams->nPortIndex != OUTPORT_INDEX) {
        return OMX_ErrorUndefined;
    }

    const OMX_PARAM_PORTDEFINITIONTYPE *paramPortDefinitionInput
                                      = this->ports[INPORT_INDEX]->GetPortDefinition();

    rectParams->nLeft = VPX_DECODE_BORDER;
    rectParams->nTop = VPX_DECODE_BORDER;
    rectParams->nWidth = paramPortDefinitionInput->format.video.nFrameWidth;
    rectParams->nHeight = paramPortDefinitionInput->format.video.nFrameHeight;

    return ret;
}

OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::GetNativeBufferUsageSpecific(OMX_PTR pStructure) {
    OMX_ERRORTYPE ret;
    android::GetAndroidNativeBufferUsageParams *param =
        (android::GetAndroidNativeBufferUsageParams*)pStructure;
    CHECK_TYPE_HEADER(param);

    param->nUsage |= (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_SW_READ_OFTEN
                     | GRALLOC_USAGE_SW_WRITE_OFTEN | GRALLOC_USAGE_EXTERNAL_DISP);
    return OMX_ErrorNone;

}
OMX_ERRORTYPE OMXVideoDecoderVP9Hybrid::SetNativeBufferModeSpecific(OMX_PTR pStructure) {
    OMX_ERRORTYPE ret;
    android::EnableAndroidNativeBuffersParams *param =
        (android::EnableAndroidNativeBuffersParams*)pStructure;

    CHECK_TYPE_HEADER(param);
    CHECK_PORT_INDEX_RANGE(param);
    CHECK_SET_PARAM_STATE();

    PortVideo *port = NULL;
    port = static_cast<PortVideo *>(this->ports[OUTPORT_INDEX]);
    OMX_PARAM_PORTDEFINITIONTYPE port_def;
    memcpy(&port_def,port->GetPortDefinition(),sizeof(port_def));

    if (!param->enable) {
        mWorkingMode = RAWDATA_MODE;
        LOGI("Raw data mode is used");
        // If it is fallback from native mode the color format has been
        // already set to INTEL format.
        // We need to set back the default color format and Native stuff.
        port_def.format.video.eColorFormat = OMX_COLOR_FormatYUV420SemiPlanar;
        port_def.format.video.pNativeRender = NULL;
        port_def.format.video.pNativeWindow = NULL;
        port->SetPortDefinition(&port_def,true);
        return OMX_ErrorNone;
    }

    mWorkingMode = GRAPHICBUFFER_MODE;
    port_def.nBufferCountMin = mNativeBufferCount - 4;
    port_def.nBufferCountActual = mNativeBufferCount;
    port_def.format.video.cMIMEType = (OMX_STRING)VA_VED_RAW_MIME_TYPE;
    // add borders for libvpx decode need.
    port_def.format.video.nFrameWidth += VPX_DECODE_BORDER * 2;
    port_def.format.video.nFrameHeight += VPX_DECODE_BORDER * 2;
    mDecodedImageWidth = port_def.format.video.nFrameWidth;
    mDecodedImageHeight = port_def.format.video.nFrameHeight;
    port_def.format.video.eColorFormat = GetOutputColorFormat(port_def.format.video.nFrameWidth);
    port->SetPortDefinition(&port_def,true);

     return OMX_ErrorNone;
}


bool OMXVideoDecoderVP9Hybrid::IsAllBufferAvailable(void) {
    bool b = ComponentBase::IsAllBufferAvailable();
    if (b == false) {
        return false;
    }

    PortVideo *port = NULL;
    port = static_cast<PortVideo *>(this->ports[OUTPORT_INDEX]);
    const OMX_PARAM_PORTDEFINITIONTYPE* port_def = port->GetPortDefinition();
     // if output port is disabled, retain the input buffer
    if (!port_def->bEnabled) {
        return false;
    }
    return mCheckBufferAvailable(mHybridCtx);
}

DECLARE_OMX_COMPONENT("OMX.Intel.VideoDecoder.VP9.hybrid", "video_decoder.vp9", OMXVideoDecoderVP9Hybrid);
