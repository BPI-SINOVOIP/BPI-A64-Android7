#ifndef __HWCOMPOSER_PRIV_H__
#define __HWCOMPOSER_PRIV_H__

#include <hardware/hardware.h>
#include <hardware/hwcomposer.h>

#include <hardware/hal_public.h>
#include "drv_display.h"
#include "fb.h"

#include <fcntl.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <stdio.h>
#include <cutils/log.h>
#include <cutils/atomic.h>
#include <sys/socket.h>
#include <linux/netlink.h>
#include <poll.h>
#include <cutils/properties.h>
#include <hardware_legacy/uevent.h>
#include <sys/resource.h>
#include <EGL/egl.h>
#include <linux/ion.h>
#include <ion/ion.h>
#include <sys/ioctl.h>

#define NUMBEROFDISPLAY  3
#define NUMBEROFPIPE     2
#define ALLDISPLAY       255
#define DISPLAY_MAX_LAYER_NUM 4



#define AW_DIS_00    0
#define AW_DIS_01   1
#define AW_DIS_02   2

#define HDMI_USED   1

#define HAL_PIXEL_FORMAT_AW_NV12    0x101
#define HAL_PIXEL_FORMAT_BGRX_8888  0x1ff
#define ALIGN(x,a)	(((x) + (a) - 1L) & ~((a) - 1L))
#define HW_ALIGN	32
#define YV12_ALIGN 16
typedef enum
{
	ASSIGN_OK=0,
	ASSIGN_FAILED=1,
	ASSIGN_NOHWCLAYER=2,
	ASSIGN_FB_PIPE=4,
	ASSIGN_NO_DISP=8
	
} HwcPipeAssignStatusType;

enum
{
	FPS_SHOW = 1,
	LAYER_DUMP = 2,
	SHOW_ALL = 3
	
};
typedef struct head_list{
    struct head_list* pre;
    struct head_list* next;
}head_list_t;

typedef struct Layer_list {
    head_list_t         head;
    hwc_layer_1_t*      pslayer;  
    int                 Order;
    int                 pipe;
    bool                usedfe;
}Layer_list_t;


typedef struct
{
    int layer_num[3];
    disp_layer_info layer_info[3][4];
    void* hConfigData;
}setup_dispc_data_t;


typedef struct{
    int                 VirtualToHWDisplay;
    bool                VsyncEnable;
    bool                DisplayPlugin;
    bool                DisplayEnable;
    
    unsigned char       HwLayerNum;
    unsigned char       HwPipeNum;
    
    unsigned int        InitDisplayWidth;
    unsigned int    	InitDisplayHeight;
	unsigned int    	VarDisplayWidth;
	unsigned int    	VarDisplayHeight;
    
    unsigned int        DiplayDPI_X;
    unsigned int        DiplayDPI_Y;
    unsigned int        DisplayVsyncP;

    unsigned char       DisplayPersentWT;
    unsigned char       DisplayPersentHT;
    unsigned char       DisplayPersentW;
    unsigned char       DisplayPersentH;
    
    int                 DisplayType;
    disp_tv_mode        DisplayMode;
    __display_3d_mode   Current3DMode;

}DisplayInfo;

typedef struct 
{
    /*
    usually:  display 1: LCD
              display 2:HDMI   fixed
              display 3: mabe HDMI or other 
              We assume that all display could hot_plug,but there is only one PrimaryDisplay,0 is the PrimaryDisplay.
    */
	hwc_procs_t	const*  psHwcProcs;
	pthread_t           sVsyncThread;
    int                 DisplayFd;
    int                 FBFd;
    int                 IonFd;
   
    unsigned int        HWCFramecount;

    bool                CanForceGPUCom;   
    bool                ForceGPUComp;
    bool                DetectError;
    bool                layer0usfe;
    char                hwcdebug;
    unsigned char       GloFEisUsedCnt;
    unsigned int        TimeStamp;
    unsigned int	    uiPrivateDataSize;
    int                 HDMIMode;
    bool                bDisplayReady;

    DisplayInfo         SunxiDisplay[NUMBEROFDISPLAY];
    head_list_t         HwcLayerHead[NUMBEROFDISPLAY];

	unsigned int		uiBeginFrame;
	double				fBeginTime;

    setup_dispc_data_t* pvPrivateData;
}SUNXI_hwcdev_context_t;

typedef struct{
    unsigned char       FEisUsedCnt;
    unsigned char       HwLayerCnt;    
    unsigned int        UsedFB;
    unsigned char       HwPipeUsedCnt;
    head_list_t         FBLayerHead;
    hwc_rect_t          PipeRegion[NUMBEROFPIPE];
}HwcDevCntContext_t;

typedef struct 
{
    int type;// bit3:cvbs, bit2:ypbpr, bit1:vga, bit0:hdmi
    disp_tv_mode mode;
    int width;
    int height;
	int refreshRate;
}tv_para_t;
typedef enum
{
    WIDTH=2,
    HEIGHT,
    REFRESHRAE,
 
}MODEINFO;

typedef
 enum{
    FIND_HWDISPNUM=0,
    FIND_HWTYPE,
    NULL_DISPLAY,
    SET_DISP,
    FREE_DISP,
    
}ManageDisp;


extern SUNXI_hwcdev_context_t gSunxiHwcDevice;

extern int hwcdev_reset_device(SUNXI_hwcdev_context_t *psDevice, size_t disp);
extern HwcPipeAssignStatusType HwcTrytoAssignLayer(HwcDevCntContext_t *ctx, hwc_layer_1_t *psLayer,size_t disp,int zOrder);
extern SUNXI_hwcdev_context_t* HwcCreateDevice(void);
extern int _hwcdev_layer_config_3d(int disp, disp_layer_info *layer_info);
extern disp_tv_mode get_suitable_hdmi_mode(int i);
extern int  get_width_from_mode(int mode);
extern int  get_height_from_mode(int mode);
extern void *VsyncThreadWrapper(void *priv);
extern int HwcSetupLayer(SUNXI_hwcdev_context_t *ctx, hwc_layer_1_t *layer,int zOrder, size_t disp,int pipe);
extern int HwcResetDispData(SUNXI_hwcdev_context_t *ctx);
extern int _hwc_device_set_3d_mode(int disp, __display_3d_mode mode);
extern int _hwc_device_set_backlight_mode(int disp, int mode);
extern int _hwc_device_set_backlight_demomode(int disp, int mode);
extern int _hwc_device_set_enhancemode(int disp, int mode);
extern int _hwc_device_set_enhancedemomode(int disp, int mode);
extern int HwcTwoRegionIntersect(hwc_rect_t *rect0, hwc_rect_t *rect1);
extern int HwcDestroyDevice(SUNXI_hwcdev_context_t *psDevice);
int GetInfoOfMode(int mode,MODEINFO info);
unsigned int IonGetAddr(int fd);
int hwc_hdmi_switch(int DisplayNum,bool plug,bool update);
DisplayInfo* ManageDisplay(DisplayInfo *HWDisplayInfo, int DispInfo,ManageDisp mode);
int _hwc_device_set_output_mode(int disp, int out_type, int out_mode);
int aw_set_default_hdmi(int hdmi_settings);
int aw_get_hdmi_setting(int *HdmiMode);
int  _hwc_set_persent(int disp,int para0, int para1);
int InitAddLayerTail(head_list_t* LayerHead,hwc_layer_1_t *psLayer, int Order,int pipe,bool feused);

#endif
