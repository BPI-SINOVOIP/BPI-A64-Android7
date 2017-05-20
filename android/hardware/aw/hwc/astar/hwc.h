#ifndef __HWCOMPOSER_PRIV_H__
#define __HWCOMPOSER_PRIV_H__

#include <hardware/hardware.h>
#include <hardware/hwcomposer.h>
#include "gralloc_priv.h"
#include "drv_display.h"
#include <linux/fb.h>

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

#define HWC_FPS_REPORT

typedef enum
{
	ASSIGN_OK,
	ASSIGN_FAILED,
	ASSIGN_ERROR
} IMG_hwcdev_pipe_assign_status_t;

enum
{
    HWC_FORCE_SGX_REASON_STILL0                     = 0x00000001,
    HWC_FORCE_SGX_REASON_STILL1                     = 0x00000002,
    HWC_FORCE_SGX_REASON_OTHERS                     = 0x00000004,
};

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
#define DISPLAY_MAX_LAYER_NUM 4

typedef struct
{
	int pipeInUse;
	int pipeType;//0:de pipe 0; 1:de pipe1
	int pipeNo;
	hwc_layer_1_t *assignedLayer;
	int assignedLayerZOrder;
	int display_type;//0: primary, 1:external
} SUNXI_hwcdev_pipe_t;

typedef struct ion_list {
    int frame_index;
    int fd;
    struct ion_handle* handle;
    struct ion_list* prev;
    struct ion_list* next;
}ion_list_t;

typedef struct 
{
	size_t pipeCount[2];
	SUNXI_hwcdev_pipe_t *pipes[2];
	head_list_t         FBLayerHead;
	head_list_t        HwcLayerHead;
	int frame_num;

	hwc_procs_t	const*  psHwcProcs;
	pthread_t           sVsyncThread;

    int                 disp_fp;
    int                 fb_fp[2];

    uint32_t            app_width;
    uint32_t            app_height;
    uint32_t            display_width[2];
    uint32_t            display_height[2];
    int                 out_type[2];
    int                 out_mode[2];
    uint32_t            display_persent[2];

    int                 vsync_en;

    int                 hdmi_hpd;
    int                 hdmi_hpd_active;

    int                 force_sgx[2];
    int                 use_fb[2];
    int                 have_ovl[2];
    uint32_t            de_pipe_used[2];
    bool 								used_fe;
    hwc_rect_t          de_pipe_region[2][2];
    uint32_t            de_fe_used[2];
    hwc_rect_t          frame[2];
    int                 time_stamp;
    int                 show_black[2];

    __display_3d_mode   cur_3d_mode[2];
    int                 hint_hdmi_mode;// 4:720p50, 5:720p60, 8:1080p24, 9:1080p50, 10:1080p60, 255:auto

    void*               pvPrivateData;
    unsigned int	    uiPrivateDataSize;
    volatile unsigned int        HWCFramecount;
    char                hwcdebug;
		int                 ion_fd;
#ifdef HWC_FPS_REPORT
	unsigned int		bReportFpsEnabled;
	unsigned int		uiBeginFrame;
#endif /* HWC_FPS_REPORT */
	ion_list*           IonHandleHead;
	pthread_mutex_t     IonRefLock;
}SUNXI_hwcdev_context_t;

typedef struct 
{
    int type;// bit3:cvbs, bit2:ypbpr, bit1:vga, bit0:hdmi
    int mode;
    int width;
    int height;
}tv_para_t;

#ifndef __LECACY_USED__ 
typedef disp_layer_info __disp_layer_info_t;
typedef disp_tv_mode __disp_tv_mode_t;
#endif

extern SUNXI_hwcdev_context_t gSunxiHwcDevice;

extern int hwcdev_reset_device(SUNXI_hwcdev_context_t *psDevice, size_t disp);
extern IMG_hwcdev_pipe_assign_status_t hwcdev_try_to_assign_pipe(SUNXI_hwcdev_context_t *ctx, SUNXI_hwcdev_pipe_t *psPipe, hwc_layer_1_t *psLayer, int zOrder, int disp);
extern SUNXI_hwcdev_context_t* hwcdev_create_device(void);
extern int _hwcdev_layer_config_3d(int disp, __disp_layer_info_t *layer_info);
extern __disp_tv_mode_t get_suitable_hdmi_mode(void);
extern int _hwc_device_set_3d_mode(int disp, __display_3d_mode mode);
extern int  get_width_from_mode(int mode);
extern int  get_height_from_mode(int mode);
extern void *VsyncThreadWrapper(void *priv);
extern int hwcdev_generate_private_data(SUNXI_hwcdev_context_t *ctx);
extern int hwcdev_free_private_data(SUNXI_hwcdev_context_t *ctx);
extern int _hwc_device_set_backlight_mode(int disp, int mode);
extern  int _hwc_device_set_backlight_demomode(int disp, int mode);
extern int _hwc_device_set_enhancemode(int disp, int mode);
extern int _hwc_device_set_enhancedemomode(int disp, int mode);
extern int InitAddLayerTail(head_list_t* LayerHead,hwc_layer_1_t *psLayer, int Order,int pipe,bool feused);
extern unsigned int IonGetAddr(void *handle);
extern int IonHandleAddRef(hwc_layer_1_t *layer);
extern int IonHandleDecRef(void);

#endif
