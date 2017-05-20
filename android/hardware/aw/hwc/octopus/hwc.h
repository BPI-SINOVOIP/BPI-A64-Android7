#ifndef __HWCOMPOSER_PRIV_H__
#define __HWCOMPOSER_PRIV_H__

#include <hardware/hardware.h>
#include <hardware/hwcomposer.h>

#include <hardware/hal_public.h>
#include "sunxi_display2.h"
#include "sunxi_tr.h"
#include <fb.h>

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
#include <sys/mman.h>
#include <math.h>
#include <system/graphics.h>
#include <sync/sync.h>
#include <utils/Condition.h>
#include <utils/Mutex.h>

#define HAL_PIXEL_FORMAT_AW_NV12    0x101
#define HAL_PIXEL_FORMAT_BGRX_8888  0x1ff
#define ION_IOC_SUNXI_PHYS_ADDR     7
#define STOP_HWC GRALLOC_USAGE_PRIVATE_3

#define ALIGN(x,a)	(((x) + (a) - 1L) & ~((a) - 1L))
#define HW_ALIGN	32
#define YV12_ALIGN 16
#define ROTATE_ALIGN 32

#define HDMI_USED 1
#define HAS_HW_ROT
#define WB_FORMAT  HAL_PIXEL_FORMAT_YCrCb_420_SP
#define CURSOR_POS_ASYNC

#define HAS_HDCP 0
//#define HWC_1_3
//#define HWC_DEBUG
#define UI_SCALE
#define UI_FACTOR 16

//#define FORCE_SET_RESOLUTION  //for debug, sometimes you also need to modify lcd desity
#define FORCE_RESOLUTION_WIDTH 1536
#define FORCE_RESOLUTION_HEIGHT 2048

#define NUMBEROFDISPLAY   2
#define NUMLAYEROFCHANNEL 4
#define NUMCHANNELOFVIDEO 1
#define NUMCHANNELOFDSP   4

#define PVRSRV_MEM_CONTIGUOUS				(1U<<15)

#define MAX_FREE_CACHE 3
#define MIN_FREE_CACHE 2

#define VIDEO_ROTATE_COUNT 3 /* must >=3  be careful*/
#define ION_HOLD_CNT    3
#define CURSOR_CACHE_COUNT 6

#define container_of(type, member)({\
    type tmp; \
    (char *)(&tmp.member) - (char *)(&tmp);})

#define type_container_of(ptr,type,offset)({\
    (type*)((char*)ptr -(char*)offset);})

#define HWC_UNREFERENCED_PARAMETER(param) (param) = (param)

typedef enum
{
    ASSIGN_INIT = 0,
    ASSIGN_GPU,
	ASSIGN_OVERLAY,
	ASSIGN_CURSOR,
 
	ASSIGN_NEEDREASSIGNED,
    ASSIGN_FAILED,
} HwcAssignStatus;

/*for kernel dev_composer.c*/ 
enum {
    HWC_SYNC_NEED = -2,
    HWC_SYNC_INIT = -1,
    HWC_DISP0 = 0,
    HWC_DISP1 = 1,
    HWC_OTHER0 = 2,
    HWC_OTHER1 = 3,
    CNOUTDISPSYNC = 4,
};

typedef enum
{
    HWC_IOCTL_FENCEFD = 0,
    HWC_IOCTL_COMMIT = 1,
    HWC_IOCTL_CKWB = 2,
    HWC_IOCTL_SETPRIDIP,
} HWC_IOCTL;

typedef enum
{
#define AssignDUETO(x)    x,
    AssignDUETO(I_OVERLAY)
    AssignDUETO(D_NULL_BUF)
    AssignDUETO(D_CONTIG_MEM)
	AssignDUETO(D_VIDEO_PD)
	AssignDUETO(D_CANNT_SCALE)
	AssignDUETO(D_SKIP_LAYER)
    AssignDUETO(D_NO_FORMAT)
    AssignDUETO(D_BACKGROUND)
    AssignDUETO(D_TR_N_0)
    AssignDUETO(D_ALPHA)
    AssignDUETO(D_X_FB)
    AssignDUETO(D_SW_OFTEN)
    AssignDUETO(D_SCALE_OUT)
    AssignDUETO(D_STOP_HWC)
    AssignDUETO(D_NO_PIPE)
    AssignDUETO(D_NO_MEM)
#undef AssignDUETO
    DEFAULT,

} AssignDUETO_T;

enum
{
	FPS_SHOW = 1,
	LAYER_DUMP = 2,
	SHOW_ALL = 3,
};

typedef struct
{
    HWC_IOCTL   cmd;
    void        *arg;
} hwc_ioctl_arg;

typedef struct {

    int   speed;
    int   limit;
}mem_speed_limit_t;

typedef struct list_head{

    struct list_head     *prev;
    struct list_head     *next;
}list_head_t;

typedef struct{
        long long left;
        long long right;
	    long long top;
	    long long bottom;
}rect64;
 
typedef struct {
    int                 fd;
    unsigned int        sync_cnt;
    int                 share_fd;//ion_handle share_fd
    int                 size_buffer;
    bool                valid;
    bool                is_secure;
}hwc_cache_t;

typedef struct {
    disp_pixel_format   format;
    tr_mode             tr;
    int                 width;
    int                 height;
    hwc_rect_t          crop;
    hwc_cache_t         buffer_cache;
}wb_data_t;

typedef struct {
    int                 aquirefencefd;
    unsigned int        tr;
    int                 share_fd;
    bool                needsync;//for sw_write
    bool                iscursor;
    bool                is_secure;
    disp_layer_config   hwc_layer_info;
}hwc_commit_layer_t;

typedef struct {
    int                 *handle_array;
    int                 num_array;
    int                 num_used;
    unsigned int        sync_count;
}hwc_ion_hold_t;

typedef struct {
    buffer_handle_t *vir_handle;
    int             outbufAcquireFenceFd;
}vir_data_t;

typedef struct 
{
    list_head_t             commit_head;
    list_head_t             manage_head;
    int                     layer_num_inused[NUMBEROFDISPLAY];
    int                     layer_num_max[NUMBEROFDISPLAY];
    hwc_commit_layer_t      *hwc_layer_info[NUMBEROFDISPLAY];
    bool                    same_display;
    bool                    force_flip[NUMBEROFDISPLAY];
    bool                    in_used;
    bool				    abandon;
    bool                    second_wb;
    int                     cursor_in_disp[NUMBEROFDISPLAY];
    int                     tr_in_disp;
    int                     tr_layer;
    int                     first_disp;
    int                     releasefencefd[CNOUTDISPSYNC];
    unsigned int            sync_count;
    vir_data_t              vir_wb_data;
}hwc_dispc_data_t;

typedef struct
{
    disp_layer_config       *hwc_layer_info[NUMBEROFDISPLAY];
    int                     releasefencefd[CNOUTDISPSYNC];
    disp_capture_info       *data;
    bool                    force_flip[NUMBEROFDISPLAY];
}hwc_commit_data_t;

typedef struct
{
    int         x_pos;
    int         y_pos;
    int         ture_disp;
    unsigned int sync_count;
    unsigned int write_read_lock;
    bool        vaild;
}hwc_cursor_async_t;

typedef struct layer_info {
    HwcAssignStatus assigned;
    signed char     hwchannel;
    signed char     virchannel;
    signed char     HwzOrder;
    signed char     OrigOrder;
    bool            isvideo;
    bool            is3D;
    bool            is_cursor;
    bool            need_sync;
    bool            is_secure;
    int             shared_fd;
    AssignDUETO_T   info;
    hwc_layer_1_t   *psLayer;
}layer_info_t;

typedef struct ChannelInfo{
    bool            isFB;
    char            hasBlend;
    char            hasVideo;

    float           WTScaleFactor;
    float           HTScaleFactor;
    int             iCHFormat;
    unsigned char   planeAlpha;
    int             HwLayerCnt;//0~n ,0 is the first,  current will used
    int             memthruput;
    hwc_rect_t      rectx[NUMLAYEROFCHANNEL-1];//we need 2 actually
    layer_info_t   *HwLayer[NUMLAYEROFCHANNEL];
} ChannelInfo_t;

typedef struct{
    int                 VirtualToHWDisplay;
    bool                VsyncEnable;
    bool                issecure;
    bool                active;

    int                 max_thruput;
    int                 fb_thruput;
    
    int                 HwChannelNum;
    int                 LayerNumofCH;
    int                 VideoCHNum;

    int                 InitDisplayWidth;
    int    	            InitDisplayHeight;
	int    	            VarDisplayWidth;
	int    	            VarDisplayHeight;
    uint64_t            mytimestamp;
    unsigned int        DiplayDPI_X;
    unsigned int        DiplayDPI_Y;
    unsigned int        DisplayVsyncP;

    unsigned char       SetPersentWidth;
    unsigned char       SetPersentHeight;
    unsigned char       PersentWidth;
    unsigned char       PersentHeight;
    
    int                 DisplayType;
    disp_tv_mode        DisplayMode;
    __display_3d_mode   Current3DMode;
    hwc_cursor_async_t  cursor_cache[CURSOR_CACHE_COUNT];

}DisplayInfo;

typedef struct{    
    bool                UsedFB;
    bool                use_wb;
    bool                fb_has_alpha;
    int                 wb_tr;
    unsigned char       has_tr;
    unsigned char       hasVideo;
    signed char         HwCHUsedCnt;//0~n ,0 is the first,  current is used
    signed char         VideoCHCnt;//0~n, 0 is the first,  current is used 
    unsigned char       unasignedVideo;
    int                 prememvideo;
    float               WidthScaleFactor;
    float               HighetScaleFactor;
    int                 current_thruput;
    layer_info         *psAllLayer;
    int                 malloc_layer;
    int                 numberofLayer;
    int                 countofhwlayer;
    const DisplayInfo  *psDisplayInfo;
    ChannelInfo_t       ChannelInfo[NUMCHANNELOFDSP];  //zOrder 0~3

}HwcDisContext_t;

typedef struct
{
    disp_pixel_format   format;
    unsigned char       bpp;
    unsigned char       plannum;
    unsigned char       plnbbp[3];
    unsigned char       planWscale[3];
    unsigned char       planHscale[3];
    unsigned char       align[3];
    bool                swapUV;
}format_info;

typedef struct 
{
    /*
    usually:  display 1: LCD
              display 2:HDMI   fixed
              We assume that all display could hot_plug,but there is only one PrimaryDisplay,0 is the PrimaryDisplay.
    */
	hwc_procs_t	const*  psHwcProcs;
	pthread_t           sVsyncThread;
    pthread_t           CommitThread;

    int                 DisplayFd;
    int                 FBFd;
    int                 IonFd;
    int                 dvfsfd;

    int                 tr_fd;
    unsigned  long      tr_handle;
    int                 has_tr_count;
    int                 tr_count_limit;
    int                 tr_time_out;

    hwc_cache_t         video_cache[VIDEO_ROTATE_COUNT];
    hwc_ion_hold_t      ion_hold[ION_HOLD_CNT];

    int                 sw_syncfd;
    unsigned int        rotate_timeline;
    unsigned int        rotate_next;

    unsigned int        HWCFramecount;
    int                 currentmem;
    int                 memlimit;

    bool                CanForceGPUCom;   
    bool                ForceGPUComp;
    bool                stop_rotate_hw;
    bool                has_cursor;

    unsigned int        cursor_sync;
    char                hwcdebug;
	unsigned int		uiBeginFrame;
	double				fBeginTime;
    
    int                 NumberofDisp;
    DisplayInfo         *SunxiDisplay;// 0 is HWC_DISPLAY_PRIMARY,1 is HWC_DISPLAY_EXTERNAL,2 is HWC_DISPLAY_VIRTUAL 
    HwcDisContext_t     *DisContext;//0 is the DE0, 1 is DE1,2 is   HWC_DISPLAY_VIRTUAL
 
    list_head_t         CommitHead;
    pthread_mutex_t     HeadLock;

    list_head_t         ManageHead;
    int                 NumManagemax;
    int                 NumManageUsed;
    pthread_mutex_t     ManageLock;

    list_head_t         AbandonHead;
    int                 AbandonCount;
    pthread_mutex_t     AbandonLock;

    mutable android::Mutex CommitLock;
    mutable android::Condition   CommitCondition;

    hwc_commit_layer_t cursor_rotate_layer[VIDEO_ROTATE_COUNT];// 0 is 90, 1 is 180,2 is 270;
    int					unblank_flag;
}SUNXI_hwcdev_context_t;

typedef struct 
{
    int             type;// bit3:cvbs, bit2:ypbpr, bit1:vga, bit0:hdmi
    disp_tv_mode    mode;
    int             width;
    int             height;
	int             refreshRate;
    char            support;
}tv_para_t;

typedef enum
{
    WIDTH=2,
    HEIGHT,
    REFRESHRAE,
 
}MODEINFO;

typedef enum{
    FIND_HWDISPNUM=0,
    NULL_DISPLAY,
    SET_DISP,
    FREE_DISP,

}ManageDisp;

inline bool hwc_list_empty(list_head_t *head)
{
    return (head->prev == head) && (head->next == head);
}

inline void hwc_list_init(list_head_t *head)
{
    head->next = head;
    head->prev = head;
}

inline void hwc_list_del(list_head_t *head)
{
    head->next->prev = head->prev;
    head->prev->next = head->next;
}

inline void hwc_list_put(list_head_t *head,list_head_t *anew)
{
    anew->prev = head->prev;
    anew->next = head;
    head->prev->next = anew;
    head->prev = anew;
}

inline list_head_t *hwc_list_get(list_head_t *head)
{
    list_head_t *old;
    if(hwc_list_empty(head))
    {
        return NULL;
    }
    old = head->next;
    hwc_list_del(old);
    hwc_list_init(old);
    return old;
}

inline void hwc_list_move(list_head_t *oldhead,list_head_t *newhead)
{
    hwc_list_init(newhead);
    newhead->next = oldhead->next;
    newhead->prev= oldhead->prev;
    oldhead->next->prev = newhead;
    oldhead->prev->next = newhead;
    hwc_list_init(oldhead);
}

extern SUNXI_hwcdev_context_t gSunxiHwcDevice;

extern int hwcdev_reset_device(SUNXI_hwcdev_context_t *psDevice, size_t disp);
extern HwcAssignStatus hwc_try_assign_layer(HwcDisContext_t *ctx, size_t  singcout,int zOrder);
extern SUNXI_hwcdev_context_t* hwc_create_device(void);
extern int  _hwcdev_layer_config_3d(const DisplayInfo  *PsDisplayInfo, disp_layer_info *layer_info);
extern disp_tv_mode get_suitable_hdmi_mode(int select,disp_tv_mode lastmode);
extern void *vsync_thread_wrapper(void *priv);
extern int hwc_setup_layer(HwcDisContext_t *ctx);
extern int hwc_reset_disp(SUNXI_hwcdev_context_t *ctx);
extern int _hwc_device_set_3d_mode(int disp, __display_3d_mode mode);
extern int _hwc_device_set_backlight(int disp, int on_off,bool half);
extern int _hwc_device_set_enhancemode(int disp, bool on_off,bool half);
extern int hwc_region_intersect(hwc_rect_t *rect0, hwc_rect_t *rect1);
extern int hwc_destroy_device(void);
extern SUNXI_hwcdev_context_t* hwc_create_device(void);
extern int get_info_mode(int mode,MODEINFO info);
extern disp_tv_mode checkout_mode(int select,bool reset,int lastmode);
extern unsigned int get_ion_addr(int fd);
extern int hwc_hotplug_switch(int DisplayNum, bool plug, disp_tv_mode set_mode);

extern int _hwc_device_set_output_mode(int disp, int out_type, int out_mode);

extern bool sunxi_prepare(hwc_display_contents_1_t **displays ,size_t NumofDisp);
extern bool sunxi_set(hwc_display_contents_1_t** displays, size_t numDisplays);
extern hwc_dispc_data_t* hwc_layer_cache_get(SUNXI_hwcdev_context_t *Globctx, int countdisp);

extern void *commit_thread(void *priv);
extern int aw_get_hdmi_setting(int *HdmiMode);
extern int  _hwc_set_persent(int disp,int para0, int para1);

extern int ion_alloc_buffer(int iAllocBytes, unsigned int heap_mask);
extern int ion_free_buffer(int handle);
extern unsigned int ion_get_addr_fromfd(int sharefd);
extern unsigned long ion_get_addr_from_handle(int handle);
extern ion_user_handle_t ion_handle_add_ref(int sharefd);
extern bool hwc_manage_ref_cache(bool cache, hwc_dispc_data_t *dispc_data);

extern bool hwc_rotate_layer_video(hwc_dispc_data_t *hwc_layer, hwc_commit_data_t *commit_data,int disp, int layer);
extern bool hwc_rotate_request(void);
extern void hwc_rotate_release(void);
extern void hwc_rotate_cache_free( void);
extern int hwc_manage_display(DisplayInfo **retDisplayInfo, int DispInfo, ManageDisp mode);
extern void hwc_wb_manage(hwc_dispc_data_t *DisplayData, hwc_commit_data_t *commit_data, disp_capture_info *data);
extern int hwc_set_cursor_async(struct hwc_composer_device_1 *dev, int disp, int x_pos, int y_pos);
extern void hwc_cursor_manage(SUNXI_hwcdev_context_t *Globctx, hwc_commit_layer_t *cursor_layer, unsigned int sync_count);
extern bool update_cursor_layer(SUNXI_hwcdev_context_t *Globctx, hwc_commit_layer_t *cursor_layer,hwc_dispc_data_t *DisplayData);
extern  bool get_rotate_cursor_layer(SUNXI_hwcdev_context_t *Globctx, hwc_commit_layer_t *commit_layer);

#endif
