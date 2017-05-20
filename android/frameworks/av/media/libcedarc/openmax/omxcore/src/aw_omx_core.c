/*
* Copyright (c) 2008-2016 Allwinner Technology Co. Ltd.
* All rights reserved.
*
* File : Aw_omx_core.c
* Description :
* History :
*   Author  : AL3
*   Date    : 2013/05/05
*   Comment : complete the design code
*/

/*============================================================================
                            O p e n M A X   w r a p p e r s
                             O p e n  M A X   C o r e

  This module contains the implementation of the OpenMAX core.

*//*========================================================================*/

//////////////////////////////////////////////////////////////////////////////
//                             Include Files
//////////////////////////////////////////////////////////////////////////////

#include "log.h"

#include <dlfcn.h>           // dynamic library
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#include "aw_omx_core.h"
#include "omx_core_cmp.h"

extern omx_core_cb_type core[];
extern const unsigned int NUM_OF_CORE;

static pthread_mutex_t g_mutex_core_info = PTHREAD_MUTEX_INITIALIZER;

#ifndef __ANDROID__

/*
 * Copy src to string dst of size siz.  At most siz-1 characters
 * will be copied.  Always NUL terminates (unless siz == 0).
 * Returns strlen(src); if retval >= siz, truncation occurred.
 */

static size_t strlcpy(char *dst, const char *src, size_t size)
{
    register char *pDst = dst;
    register const char *pSrc = src;
    register size_t nCpySize = size;

    /* Copy as many bytes as will fit */
    if (nCpySize != 0 && --nCpySize != 0) {
        do {
            if ((*pDst++ = *pSrc++) == 0)
                break;
        } while (--nCpySize != 0);
    }

    /* Not enough room in dst, add NUL and traverse rest of src */
    if (nCpySize == 0) {
        if (size != 0)
            *pDst = '\0';       /* NUL-terminate dst */
        while (*pSrc++)
            ;
    }

    return(pSrc - src - 1);    /* count does not include NUL */
}

#endif

/* ======================================================================
FUNCTION
  omx_core_load_cmp_library

DESCRIPTION
  Loads up the library name mentioned in the argument

PARAMETERS
  None

RETURN VALUE
  Constructor for creating component instances.
========================================================================== */

static create_aw_omx_component omx_core_load_cmp_library(int idx)
{
    create_aw_omx_component fn_ptr = NULL;

    pthread_mutex_lock(&g_mutex_core_info);

    if(core[idx].so_lib_handle == NULL)
    {
        logi("Dynamically Loading the library : %s\n",core[idx].so_lib_name);

        core[idx].so_lib_handle = dlopen(core[idx].so_lib_name, RTLD_NOW);
    }

    if(core[idx].so_lib_handle)
    {
        if(core[idx].fn_ptr == NULL)
        {
            core[idx].fn_ptr = (create_aw_omx_component)dlsym(core[idx].so_lib_handle,
                                                              "get_omx_component_factory_fn");

            if(core[idx].fn_ptr == NULL)
            {
                logi("Error: Library %s incompatible as QCOM OMX component loader - %s\n",
                      core[idx].so_lib_name, dlerror());
            }
        }
    }
    else
    {
        logi("Error: Couldn't load %s: %s\n",core[idx].so_lib_name,dlerror());
    }

    fn_ptr = core[idx].fn_ptr;

    pthread_mutex_unlock(&g_mutex_core_info);

    return fn_ptr;
}

/* ======================================================================
FUNCTION
  OMX_Init

DESCRIPTION
  This is the first function called by the application.
  There is nothing to do here since components shall be loaded
  whenever the get handle method is called.

PARAMETERS
  None

RETURN VALUE
  None.
========================================================================== */
OMX_API OMX_ERRORTYPE OMX_APIENTRY OMX_Init()
{
    CEDARC_UNUSE(OMX_Init);
    logi("OMXCORE API - OMX_Init \n");

    /* Nothing to do here ; shared objects shall be loaded at the get handle method */
    return OMX_ErrorNone;
}

/* ======================================================================
FUNCTION
  get_cmp_index

DESCRIPTION
  Obtains the  index associated with the name.

PARAMETERS
  None

RETURN VALUE
  Error None.
========================================================================== */

static int get_cmp_index(char *cmp_name)
{
    int rc = -1,i=0;

    logi("before get_cmp_index **********%d\n", rc);

    pthread_mutex_lock(&g_mutex_core_info);

    for(i=0; i< (int)NUM_OF_CORE; i++)
    {
        logi("get_cmp_index: cmp_name = %s , core[i].name = %s ,count = %d \n",
              cmp_name,core[i].name,i);

        if(!strcmp(cmp_name, core[i].name))
        {
            rc = i;
            break;
        }
    }

    pthread_mutex_unlock(&g_mutex_core_info);

    logi("returning index %d\n", rc);
    return rc;
}

/* ======================================================================
FUNCTION
  clear_cmp_handle

DESCRIPTION
  Clears the component handle from the component table.

PARAMETERS
  None

RETURN VALUE
  None.
========================================================================== */
static void clear_cmp_handle(OMX_HANDLETYPE inst)
{
    unsigned i = 0,j=0;

    if(NULL == inst)
        return;

    pthread_mutex_lock(&g_mutex_core_info);

    for(i=0; i< NUM_OF_CORE; i++)
    {
        for(j=0; j< OMX_COMP_MAX_INST; j++)
        {
            if(inst == core[i].inst[j])
            {
                core[i].inst[j] = NULL;

                pthread_mutex_unlock(&g_mutex_core_info);
                return;
            }
        }
    }

    pthread_mutex_unlock(&g_mutex_core_info);

    return;
}

/* ======================================================================
FUNCTION
  is_cmp_handle_exists

DESCRIPTION
  Check if the component handle already exists or not.

PARAMETERS
  None

RETURN VALUE
  index pointer if the handle exists
  negative value otherwise
========================================================================== */
static int is_cmp_handle_exists(OMX_HANDLETYPE inst)
{
    unsigned i=0,j=0;
    int rc = -1;

    if(NULL == inst)
        return rc;

    pthread_mutex_lock(&g_mutex_core_info);

    for(i=0; i< NUM_OF_CORE; i++)
    {
        for(j=0; j< OMX_COMP_MAX_INST; j++)
        {
            if(inst == core[i].inst[j])
            {
                rc = i;

                pthread_mutex_unlock(&g_mutex_core_info);
                return rc;
            }
        }
    }

    pthread_mutex_unlock(&g_mutex_core_info);

    return rc;
}

/* ======================================================================
FUNCTION
  get_comp_handle_index

DESCRIPTION
  Gets the index to store the next handle for specified component name.

PARAMETERS
  cmp_name : Component Name

RETURN VALUE
  Index of next handle to be stored
========================================================================== */

#if 0
static int get_comp_handle_index(char *cmp_name)
{
    unsigned i=0,j=0;
    int rc = -1;

    pthread_mutex_lock(&g_mutex_core_info);

    for(i=0; i< NUM_OF_CORE; i++)
    {
        if(!strcmp(cmp_name, core[i].name))
        {
            for(j=0; j< OMX_COMP_MAX_INST; j++)
            {
                if(NULL == core[i].inst[j])
                {
                    rc = j;
                    logi("free handle slot exists %d\n", rc);

                    pthread_mutex_unlock(&g_mutex_core_info);
                    return rc;
                }
            }

            break;
        }
    }

    pthread_mutex_unlock(&g_mutex_core_info);

    return rc;
}

#endif

static int set_comp_handle(char *cmp_name, void *handle)
{
    unsigned i=0,j=0;
    int rc = -1;

    pthread_mutex_lock(&g_mutex_core_info);

    for(i=0; i< NUM_OF_CORE; i++)
    {
        if(!strcmp(cmp_name, core[i].name))
        {
            for(j=0; j< OMX_COMP_MAX_INST; j++)
            {
                if(NULL == core[i].inst[j])
                {
                    rc = j;
                    logi("free handle slot exists %d\n", rc);

                    core[i].inst[j] = handle;
                    pthread_mutex_unlock(&g_mutex_core_info);
                    return rc;
                }
            }

            break;
        }
    }

    pthread_mutex_unlock(&g_mutex_core_info);

    return rc;
}

/* ======================================================================
FUNCTION
  check_lib_unload

DESCRIPTION
  Check if any component instance is using the library

PARAMETERS
  index: Component Index in core array.

RETURN VALUE
  1: Library Unused and can be unloaded.
  0:  Library used and shouldnt be unloaded.
========================================================================== */
static int check_lib_unload(int index)
{
    unsigned i=0;
    int rc = 1;

    pthread_mutex_lock(&g_mutex_core_info);

    for(i=0; i< OMX_COMP_MAX_INST; i++)
    {
        if(core[index].inst[i])
        {
            rc = 0;
            logi("Library Used \n");
            break;
        }
    }

    if(rc == 1)
    {
        logd(" Unloading the dynamic library for %s\n", core[index].name);
        int err = dlclose(core[index].so_lib_handle);
        if(err)
        {
            logi("Error %d in dlclose of lib %s\n", err,core[index].name);
        }

        core[index].so_lib_handle = NULL;
        core[index].fn_ptr = NULL;
    }

    pthread_mutex_unlock(&g_mutex_core_info);

    return rc;
}

#if 0
/* ======================================================================
FUNCTION
  is_cmp_already_exists

DESCRIPTION
  Check if the component already exists or not. Used in the
  management of component handles.

PARAMETERS
  None

RETURN VALUE
  Error None.
========================================================================== */
static int is_cmp_already_exists(char *cmp_name)
{
    unsigned i=0,j=0;
    int rc = -1;

    for(i=0; i< NUM_OF_CORE; i++)
    {
        if(!strcmp(cmp_name, core[i].name))
        {
            for(j=0; j< OMX_COMP_MAX_INST; j++)
            {
                if(core[i].inst[j])
                {
                    rc = i;
                    logi("Component exists %d\n", rc);
                    return rc;
                }
            }

            break;
        }
    }

    return rc;
}
#endif

#if 0
/* ======================================================================
FUNCTION
  get_cmp_handle

DESCRIPTION
  Get component handle.

PARAMETERS
  None

RETURN VALUE
  Error None.
========================================================================== */
void* get_cmp_handle(char *cmp_name)
{
    unsigned i    =0,j=0;

    logi("get_cmp_handle \n");

    for(i=0; i< NUM_OF_CORE; i++)
    {
        if(!strcmp(cmp_name, core[i].name))
        {
            for(j=0; j< OMX_COMP_MAX_INST; j++)
            {
                if(core[i].inst[j])
                {
                    logi("get_cmp_handle match\n");
                    return core[i].inst[j];
                }
            }
        }
    }

    logi("get_cmp_handle returning NULL \n");

    return NULL;
}
#endif
/* ======================================================================
FUNCTION
  OMX_DeInit

DESCRIPTION
  DeInitialize all the the relevant OMX components.

PARAMETERS
  None

RETURN VALUE
  Error None.
========================================================================== */
OMX_API OMX_ERRORTYPE OMX_APIENTRY OMX_Deinit()
{
    CEDARC_UNUSE(OMX_Deinit);
#if 0

    int err;
    unsigned i=0,j=0;
    OMX_ERRORTYPE eRet;

    /* Free the dangling handles here if any */
    for(i=0; i< NUM_OF_CORE; i++)
    {
        for(j=0; j< OMX_COMP_MAX_INST; j++)
        {
            if(core[i].inst[j])
            {
                logi("OMX DeInit: Freeing handle for %s\n", core[i].name);

                /* Release the component and unload dynmaic library */
                eRet = OMX_FreeHandle(core[i].inst[j]);
                if(eRet != OMX_ErrorNone)
                    return eRet;
            }
        }
    }
#endif

    return OMX_ErrorNone;
}

/* ======================================================================
FUNCTION
  OMX_GetHandle

DESCRIPTION
  Constructs requested component. Relevant library is loaded if needed.

PARAMETERS
  None

RETURN VALUE
  Error None  if everything goes fine.
========================================================================== */
OMX_API OMX_ERRORTYPE OMX_APIENTRY OMX_GetHandle(OMX_OUT OMX_HANDLETYPE* handle,
                                                   OMX_IN OMX_STRING componentName,
                                                   OMX_IN OMX_PTR appData,
                                                   OMX_IN OMX_CALLBACKTYPE* callBacks)
{
    CEDARC_UNUSE(OMX_GetHandle);

    OMX_ERRORTYPE  eRet = OMX_ErrorNone;

    create_aw_omx_component fn_ptr = NULL;

    logi("OMXCORE API :  Get Handle %x %s %x\n",
         (unsigned) handle, componentName, (unsigned) appData);

    if(handle)
    {
        int cmp_index = get_cmp_index(componentName);

        if(cmp_index >= 0)
        {
            logi("getting fn pointer\n");

            // dynamically load the so

            // logi("core[cmp_index].fn_ptr: %x", core[cmp_index].fn_ptr);

            fn_ptr = omx_core_load_cmp_library(cmp_index);

            if(fn_ptr)
            {
                // Construct the component requested
                // Function returns the opaque handle

                void* pThis = (*fn_ptr)();
                if(pThis)
                {
                    void *hComp = NULL;
                    hComp = aw_omx_create_component_wrapper((OMX_PTR)pThis);
                    if((eRet = aw_omx_component_init(hComp, componentName)) != OMX_ErrorNone)
                    {
                        logd("Component not created succesfully\n");
                        return eRet;
                    }

                    aw_omx_component_set_callbacks(hComp, callBacks, appData);

                    int hnd_index = set_comp_handle(componentName, hComp);
                    if(hnd_index >= 0)
                    {
                        *handle = (OMX_HANDLETYPE) hComp;
                    }
                    else
                    {
                        logd("OMX_GetHandle:NO free slot available to store Component Handle\n");
                        *handle = NULL;
                        aw_omx_component_deinit(hComp);
                        check_lib_unload(cmp_index);
                        return OMX_ErrorInsufficientResources;
                    }

                    logi("Component %x Successfully created\n",(unsigned)*handle);
                }
                else
                {
                    eRet = OMX_ErrorInsufficientResources;
                    logd("Component Creation failed\n");
                }
            }
            else
            {
                eRet = OMX_ErrorNotImplemented;
                logi("library couldnt return create instance fn\n");
            }
        }
        else
        {
            eRet = OMX_ErrorNotImplemented;
            logi("ERROR: Already another instance active  ;rejecting \n");
        }
    }
    else
    {
        eRet =  OMX_ErrorBadParameter;
        logi("\n OMX_GetHandle: NULL handle \n");
    }

    return eRet;
}

/* ======================================================================
FUNCTION
  OMX_FreeHandle

DESCRIPTION
  Destructs the component handles.

PARAMETERS
  None

RETURN VALUE
  Error None.
========================================================================== */
OMX_API OMX_ERRORTYPE OMX_APIENTRY OMX_FreeHandle(OMX_IN OMX_HANDLETYPE hComp)
{
    CEDARC_UNUSE(OMX_FreeHandle);

    OMX_ERRORTYPE eRet = OMX_ErrorNone;
    int i = 0;

    logi("OMXCORE API :  Free Handle %x\n",(unsigned) hComp);

    // 0. Check that we have an active instance
    if((i= is_cmp_handle_exists(hComp)) >=0)
    {
        // 1. Delete the component
        if ((eRet = aw_omx_component_deinit(hComp)) == OMX_ErrorNone)
        {

            clear_cmp_handle(hComp);

            /* Unload component library */
            check_lib_unload(i);
        }
        else
        {
            logi(" OMX_FreeHandle failed on %x\n",(unsigned) hComp);
            return eRet;
        }
    }
    else
    {
        logi("OMXCORE Warning: Free Handle called with no active instances\n");
    }

    return OMX_ErrorNone;
}

/* ======================================================================
FUNCTION
  OMX_SetupTunnel

DESCRIPTION
  Not Implemented.

PARAMETERS
  None

RETURN VALUE
  None.
========================================================================== */
OMX_API OMX_ERRORTYPE OMX_APIENTRY OMX_SetupTunnel(OMX_IN OMX_HANDLETYPE outputComponent,
                                                   OMX_IN OMX_U32 outputPort,
                                                   OMX_IN OMX_HANDLETYPE inputComponent,
                                                   OMX_IN OMX_U32 inputPort)
{
    CEDARC_UNUSE(OMX_SetupTunnel);
    /* Not supported right now */
    logi("OMXCORE API: OMX_SetupTunnel Not implemented \n");

    CEDARC_UNUSE(outputComponent);
    CEDARC_UNUSE(inputComponent);
    CEDARC_UNUSE(outputPort);
    CEDARC_UNUSE(inputPort);
    return OMX_ErrorNotImplemented;
}

/* ======================================================================
FUNCTION
  OMX_GetContentPipe

DESCRIPTION
  Not Implemented.

PARAMETERS
  None

RETURN VALUE
  None.
========================================================================== */
OMX_API OMX_ERRORTYPE OMX_GetContentPipe(OMX_OUT OMX_HANDLETYPE* pipe, OMX_IN OMX_STRING uri)
{
    CEDARC_UNUSE(OMX_GetContentPipe);
    /* Not supported right now */
    logi("OMXCORE API: OMX_GetContentPipe Not implemented \n");

    CEDARC_UNUSE(pipe);
    CEDARC_UNUSE(uri);
    return OMX_ErrorNotImplemented;
}

/* ======================================================================
FUNCTION
  OMX_GetComponentNameEnum

DESCRIPTION
  Returns the component name associated with the index.

PARAMETERS
  None

RETURN VALUE
  None.
========================================================================== */
OMX_API OMX_ERRORTYPE OMX_APIENTRY OMX_ComponentNameEnum(OMX_OUT OMX_STRING componentName,
                                                         OMX_IN OMX_U32 nameLen,
                                                         OMX_IN OMX_U32 index)
{
    CEDARC_UNUSE(OMX_ComponentNameEnum);

    OMX_ERRORTYPE eRet = OMX_ErrorNone;
    logi("OMXCORE API - OMX_ComponentNameEnum %x %d %d\n",
         (unsigned) componentName, (unsigned)nameLen, (unsigned)index);
    if(index < NUM_OF_CORE)
    {
        strlcpy(componentName, core[index].name, nameLen);
    }
    else
    {
        eRet = OMX_ErrorNoMore;
    }

    return eRet;
}

/* ======================================================================
FUNCTION
  OMX_GetComponentsOfRole

DESCRIPTION
  Returns the component name which can fulfill the roles passed in the
  argument.

PARAMETERS
  None

RETURN VALUE
  None.
========================================================================== */
OMX_API OMX_ERRORTYPE OMX_GetComponentsOfRole(OMX_IN OMX_STRING role,
                                               OMX_INOUT OMX_U32* numComps,
                                               OMX_INOUT OMX_U8** compNames)
{
    CEDARC_UNUSE(OMX_GetComponentsOfRole);

    OMX_ERRORTYPE eRet = OMX_ErrorNone;
    unsigned i,j;

    printf(" Inside OMX_GetComponentsOfRole \n");

    /*If CompNames is NULL then return*/
    if (compNames == NULL)
    {
        if (numComps == NULL)
        {
            eRet = OMX_ErrorBadParameter;
        }
        else
        {
            *numComps = 0;
            for (i=0; i<NUM_OF_CORE;i++)
            {
                for(j=0; j<OMX_CORE_MAX_CMP_ROLES && core[i].roles[j] ; j++)
                {
                    if(!strcmp(role,core[i].roles[j]))
                    {
                        (*numComps)++;
                    }
                }
            }
        }

        return eRet;
    }

    if(numComps)
    {
        unsigned namecount = *numComps;

        if(namecount == 0)
        {
            return OMX_ErrorBadParameter;
        }

        *numComps = 0;

        for (i=0; i<NUM_OF_CORE;i++)
        {
            for(j=0; j<OMX_CORE_MAX_CMP_ROLES && core[i].roles[j] ; j++)
            {
                if(!strcmp(role,core[i].roles[j]))
                {
                    strlcpy((char *)compNames[*numComps], core[i].name, OMX_MAX_STRINGNAME_SIZE);
                    (*numComps)++;
                    break;
                }
            }

            if (*numComps == namecount)
            {
                break;
            }
        }
    }
    else
    {
        eRet = OMX_ErrorBadParameter;
    }

    printf(" Leaving OMX_GetComponentsOfRole \n");
    return eRet;
}

/* ======================================================================
FUNCTION
  OMX_GetRolesOfComponent

DESCRIPTION
  Returns the primary role of the components supported.

PARAMETERS
  None

RETURN VALUE
  None.
========================================================================== */
OMX_API OMX_ERRORTYPE OMX_GetRolesOfComponent(OMX_IN OMX_STRING compName,
                                              OMX_INOUT OMX_U32* numRoles,
                                              OMX_OUT OMX_U8** roles)
{
    CEDARC_UNUSE(OMX_GetRolesOfComponent);
    /* Not supported right now */
    OMX_ERRORTYPE eRet = OMX_ErrorNone;
    unsigned i,j;
    logi("GetRolesOfComponent %s\n",compName);

    if (roles == NULL)
    {
        if (numRoles == NULL)
        {
            eRet = OMX_ErrorBadParameter;
        }
        else
        {
            *numRoles = 0;
            for(i=0; i< NUM_OF_CORE; i++)
            {
                if(!strcmp(compName,core[i].name))
                {
                    for(j=0; (j<OMX_CORE_MAX_CMP_ROLES) && core[i].roles[j];j++)
                    {
                        (*numRoles)++;
                    }

                    break;
                }
            }
        }

        return eRet;
    }

    if(numRoles)
    {
        if (*numRoles == 0)
        {
            return OMX_ErrorBadParameter;
        }

        unsigned numofroles = *numRoles;
        *numRoles = 0;

        for(i=0; i< NUM_OF_CORE; i++)
        {
            if(!strcmp(compName,core[i].name))
            {
                for(j=0; (j<OMX_CORE_MAX_CMP_ROLES) && core[i].roles[j];j++)
                {
                    if(roles && roles[*numRoles])
                    {
                        strlcpy((char *)roles[*numRoles],core[i].roles[j],OMX_MAX_STRINGNAME_SIZE);
                    }

                    (*numRoles)++;
                    if (numofroles == *numRoles)
                    {
                        break;
                    }
                }

                break;
            }
        }
    }
    else
    {
        logi("ERROR: Both Roles and numRoles Invalid\n");
        eRet = OMX_ErrorBadParameter;
    }

    return eRet;
}

OMX_API OMX_BOOL OMXConfigParser(OMX_PTR aInputParameters, OMX_PTR aOutputParameters)
{
    CEDARC_UNUSE(OMXConfigParser);

    OMX_BOOL Status = OMX_TRUE;
    VideoOMXConfigParserOutputs *aOmxOutputParameters;
    OMXConfigParserInputs *aOmxInputParameters;

    aOmxOutputParameters = (VideoOMXConfigParserOutputs *)aOutputParameters;
    aOmxInputParameters = (OMXConfigParserInputs *)aInputParameters;

    aOmxOutputParameters->width = 176; //setting width to QCIF
    aOmxOutputParameters->height = 144; //setting height to QCIF

    //TODO
    //Qcom component do not use the level/profile from IL client .They are parsing the first buffer
    //sent in ETB so for now setting the defalut values . Going farward we can call
    //QC parser here.
    if (0 == strcmp(aOmxInputParameters->cComponentRole, (OMX_STRING)"video_decoder.avc"))
    {
        //minimum supported h264 profile - setting to baseline profile
        aOmxOutputParameters->profile = 66;
        aOmxOutputParameters->level = 0;  // minimum supported h264 level
    }
    else if((0 == strcmp(aOmxInputParameters->cComponentRole, (OMX_STRING)"video_decoder.mpeg4"))
         ||(0 == strcmp(aOmxInputParameters ->cComponentRole, (OMX_STRING)"video_decoder.h263")))
    {
        aOmxOutputParameters->profile = 8; //minimum supported h263/mpeg4 profile
        aOmxOutputParameters->level = 0; // minimum supported h263/mpeg4 level
    }

    return Status;
}

