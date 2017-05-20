/*
* Copyright (c) 2008-2016 Allwinner Technology Co. Ltd.
* All rights reserved.
*
* File : AwOMXPlugin.cpp
* Description :
* History :
*   Author  : AL3
*   Date    : 2013/05/05
*   Comment : init the design code
*/

#include "log.h"

#include "AwOMXPlugin.h"

#include <dlfcn.h>

#include <HardwareAPI.h>

namespace android
{

extern "C" OMXPluginBase* createOMXPlugin()
{
    return new AwOMXPlugin;
}

extern "C" void destroyOMXPlugin(OMXPluginBase *plugin) {
    delete plugin;
}

extern "C" {

typedef void VDPluginFun(void);

static void InitVDLib(const char *lib)
{
    void *libFd = NULL;
    if(lib == NULL)
    {
        loge(" open lib == NULL ");
        return;
    }

    libFd = dlopen(lib, RTLD_NOW);

    VDPluginFun *PluginInit = NULL;

    if (libFd == NULL)
    {
        loge("dlopen '%s' fail: %s", lib, dlerror());
        return ;
    }

    PluginInit = (VDPluginFun*)dlsym(libFd, "CedarPluginVDInit");
    if (PluginInit == NULL)
    {
        logw("Invalid plugin, CedarPluginVDInit not found.");
        return;
    }
    logd("vdecoder open lib: %s", lib);
    PluginInit(); /* init plugin */
    return ;
}

static void AddVDLib(void)
{
    InitVDLib("/system/lib/libawh265.so");
    InitVDLib("/system/lib/libawh264.so");
    InitVDLib("/system/lib/libawmjpeg.so");
    InitVDLib("/system/lib/libawmjpegplus.so");
    InitVDLib("/system/lib/libawmpeg2.so");
    InitVDLib("/system/lib/libawmpeg4base.so");
    InitVDLib("/system/lib/libawmpeg4normal.so");
    InitVDLib("/system/lib/libawmpeg4vp6.so");
    InitVDLib("/system/lib/libawmpeg4dx.so");
    InitVDLib("/system/lib/libawmpeg4h263.so");
    InitVDLib("/system/lib/libawvp8.so");

    return;
}
}

AwOMXPlugin::AwOMXPlugin()
    : mOmxLibHandle(dlopen("libOmxCore.so", RTLD_NOW)),
      mInitFunc(NULL),
      mDeinitFunc(NULL),
      mComponentNameEnumFunc(NULL),
      mGetHandleFunc(NULL),
      mFreeHandleFunc(NULL),
      mGetRolesOfComponentHandleFunc(NULL)
{
    if (mOmxLibHandle != NULL)
    {
        AddVDLib();
        mInitFunc                      = (OmxInitFunc)dlsym(mOmxLibHandle, "OMX_Init");
        mDeinitFunc                    = (OmxDeinitFunc)dlsym(mOmxLibHandle, "OMX_Deinit");
        mComponentNameEnumFunc         = (OmxComponentNameEnumFunc)dlsym(mOmxLibHandle,
                                                                         "OMX_ComponentNameEnum");
        mGetHandleFunc                 = (OmxGetHandleFunc)dlsym(mOmxLibHandle, "OMX_GetHandle");
        mFreeHandleFunc                = (OmxFreeHandleFunc)dlsym(mOmxLibHandle, "OMX_FreeHandle");
        mGetRolesOfComponentHandleFunc = (OmxGetRolesOfComponentFunc)dlsym(mOmxLibHandle,
                                                                        "OMX_GetRolesOfComponent");

        (*mInitFunc)();
    }
}

AwOMXPlugin::~AwOMXPlugin()
{
    if(mOmxLibHandle != NULL)
    {
        (*mDeinitFunc)();
        dlclose(mOmxLibHandle);
        mOmxLibHandle = NULL;
    }
}

OMX_ERRORTYPE AwOMXPlugin::makeComponentInstance(const char* pName,
                                                 const OMX_CALLBACKTYPE* pCallbacks,
                                                 OMX_PTR pAppData,
                                                 OMX_COMPONENTTYPE** ppComponent)
{
    logv("step 1.");
    if (mOmxLibHandle == NULL)
    {
        return OMX_ErrorUndefined;
    }
    logv("step 2.");
    return (*mGetHandleFunc)(reinterpret_cast<OMX_HANDLETYPE *>(ppComponent),
                         const_cast<char *>(pName),
                         pAppData,
                         const_cast<OMX_CALLBACKTYPE *>(pCallbacks));
}

OMX_ERRORTYPE AwOMXPlugin::destroyComponentInstance(OMX_COMPONENTTYPE* pComponent)
{
    if (mOmxLibHandle == NULL)
    {
        return OMX_ErrorUndefined;
    }
    return (*mFreeHandleFunc)(reinterpret_cast<OMX_HANDLETYPE *>(pComponent));
}

OMX_ERRORTYPE AwOMXPlugin::enumerateComponents(OMX_STRING pName, size_t nSize, OMX_U32 uIndex)
{
    if (mOmxLibHandle == NULL)
    {
        return OMX_ErrorUndefined;
    }
    OMX_ERRORTYPE res = (*mComponentNameEnumFunc)(pName, nSize, uIndex);
    if (res != OMX_ErrorNone)
    {
        return res;
    }
    return OMX_ErrorNone;
}

OMX_ERRORTYPE AwOMXPlugin::getRolesOfComponent(const char *pName, Vector<String8> *vRoles)
{
    vRoles->clear();

    if (mOmxLibHandle == NULL)
    {
        return OMX_ErrorUndefined;
    }

    OMX_U32 uNumRoles;
    OMX_ERRORTYPE eErr = (*mGetRolesOfComponentHandleFunc)(const_cast<OMX_STRING>(pName),
                                                           &uNumRoles, NULL);

    if (eErr != OMX_ErrorNone)
    {
        return eErr;
    }

    if (uNumRoles > 0)
    {
        OMX_U8** ppArray = new OMX_U8 *[uNumRoles];
        for (OMX_U32 i = 0; i < uNumRoles; ++i)
        {
            ppArray[i] = new OMX_U8[OMX_MAX_STRINGNAME_SIZE];
        }

        OMX_U32 uNumRoles2 = uNumRoles;
        eErr = (*mGetRolesOfComponentHandleFunc)(const_cast<OMX_STRING>(pName),
                                                 &uNumRoles2, ppArray);

        if (eErr == OMX_ErrorNone && uNumRoles != uNumRoles2)
        {
            eErr = OMX_ErrorUndefined;
        }

        for (OMX_U32 i = 0; i < uNumRoles; ++i)
        {
            if (eErr == OMX_ErrorNone)
            {
                String8 s((const char *)ppArray[i]);
                vRoles->push(s);
            }

            delete [] ppArray[i];
            ppArray[i] = NULL;
        }

        delete [] ppArray;
        ppArray = NULL;
    }

    return eErr;
}

//* not call this function
static void fixStaticCheckWarning()
{
    CEDARC_UNUSE(fixStaticCheckWarning);
    CEDARC_UNUSE(createOMXPlugin);
    CEDARC_UNUSE(destroyOMXPlugin);

    AwOMXPlugin* tmp = new AwOMXPlugin;
    tmp->makeComponentInstance(NULL, NULL, NULL, NULL);
    tmp->destroyComponentInstance(NULL);
    tmp->enumerateComponents(NULL, 0, 0);
    tmp->getRolesOfComponent(NULL, NULL);
    delete tmp;
}

}  // namespace android
