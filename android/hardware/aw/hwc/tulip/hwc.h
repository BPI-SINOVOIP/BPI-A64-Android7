#ifndef __HWCOMPOSER_PRIV_H__
#define __HWCOMPOSER_PRIV_H__

#include <hardware/hardware.h>
#include <hardware/hwcomposer.h>

#include <hardware/hal_public.h>
#include "sunxi_display2.h"
#include "sunxi_tr.h"
//#include "gralloc_priv.h"
//#include "fb.h"

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
#define HW_ALIGN	16
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

#define ROTATE_CACHE_COUNT 3 /* must >=3  be careful*/
#define ION_HOLD_CNT    3
#define CURSOR_CACHE_COUNT 6
#define SETUP_INTERVAL_DE 20000
#define SETUP_INTERVAL_TR 100000
#define SETUP_TIMES  60
/* mali400 power is DE 15 times*/
#define DE_GPU_PW_FACTOR  15 
#define DE_GPU_MEM_FACTOR  2

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
	ASSIGN_FORCE_GPU,
 
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
    AssignDUETO(D_MEM_CTRL)
#undef AssignDUETO
    DEFAULT,

} AssignDUETO_T;

enum
{
	FPS_SHOW = 1,
	LAYER_DUMP = 2,
	PAUSE = 4,
	SHOW_ALL = 7,
};

typedef struct
{
    HWC_IOCTL cmd;
    void *arg;
} hwc_ioctl_arg;

typedef struct {

    int speed;
    int limit;
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
    list_head_t head;
    hwc_cache_t rotate_cache[ROTATE_CACHE_COUNT];
} rotate_cache_t;

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
    int                     local_mem[NUMBEROFDISPLAY];
    int                     layer_num_max[NUMBEROFDISPLAY];
    hwc_commit_layer_t      *hwc_layer_info[NUMBEROFDISPLAY];
    bool                    same_display;
    bool                    force_flip[NUMBEROFDISPLAY];
    bool                    in_used;
    bool				    abandon;
    bool                    second_wb;
    bool                    dueto_no_mem;
    bool                    has_fb[NUMBEROFDISPLAY];
    unsigned char           has_tr[NUMBEROFDISPLAY];
    int                     cursor_in_disp[NUMBEROFDISPLAY];
    int                     first_disp;
    int                     releasefencefd[CNOUTDISPSYNC];
    int                     current_mem_limit;
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
    format_info     form_info;
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
    bool                force_gpu;

    int                 wb_tr;
    unsigned char       has_tr; //has tr count that use hardware tr.
    unsigned char       hasVideo;
    signed char         HwCHUsedCnt;//0~n ,0 is the first,  current is used
    signed char         VideoCHCnt;//0~n, 0 is the first,  current is used
    unsigned char       video_cnt;
    unsigned char       unasignedVideo;
    float               WidthScaleFactor;
    float               HighetScaleFactor;
    
    layer_info_t        *psAllLayer;
    int                 malloc_layer;
    int                 numberofLayer;
    int                 countofhwlayer;
    const DisplayInfo  *psDisplayInfo;
    ChannelInfo_t       ChannelInfo[NUMCHANNELOFDSP];  //zOrder 0~3
    /* memory ctrl */
    int                 cur_de_thruput;
    int                 cur_all_thruput;
    int                 cur_fb_thruput;
    int                 cur_gpu_thruput;
    int                 video_mem;
    int                 prememvideo;
    unsigned int        tr_mem;
    /* end memory ctrl */
}HwcDisContext_t;

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
    int                 has_tr_mem;
    unsigned int        has_tr_cnt;
    int                 tr_mem_limit;
    int                 tr_max_mem_limit;
    int                 tr_time_out;

    int                 rotate_hold_cnt;
    list_head_t         rotate_cache_list;;
    hwc_ion_hold_t      ion_hold[ION_HOLD_CNT];

    int                 sw_syncfd;
    unsigned int        rotate_timeline;
    unsigned int        rotate_next;

    unsigned int        HWCFramecount;
    int                 currentmem;
    int                 memlimit;
    int                 max_mem_limit;

    bool                CanForceGPUCom;   
    bool                ForceGPUComp[NUMBEROFDISPLAY];
    bool                stop_hwc;
    bool                stop_rotate_hw;
    bool                has_cursor;
    bool                hot_plug;

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
    /* for hardware cursor */
    unsigned int        cursor_sync;
    hwc_commit_layer_t  cursor_rotate_layer[ROTATE_CACHE_COUNT];// 0 is 90, 1 is 180,2 is 270;

    /* for Debug */
    bool                hwcdebug;
    bool                pause;
    bool                dump_layer;
    bool                close_layer;
    bool                fps_layer;
    bool                show_layer;
    int                 disp_st;
    int                 channel_st;
    int                 layer_st;
    /* end Debug */
    /* for mem limit */
    float               fps[3];//0 for 0~20st, 1 for 20~40st,2 for 40~60st
    float               last_fps20;
    unsigned int		uiBeginFrame;
	double				fBeginTime;
    bool                in_stabilization;
    int                 updata_mem_cnt;
    int                 cur_all_diff;
    int                 fb_pre_mem;
    /* end mem limit */
	int					unblank_flag;
	bool                isFreeFB;
	bool                isNeedSecureBuffer;
	unsigned char       has_secure;
	unsigned char       has_3D;

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

inline void hwc_list_put(list_head_t *head, list_head_t *anew)
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

inline void hwc_list_move(list_head_t *oldhead, list_head_t *newhead)
{
    hwc_list_init(newhead);
    newhead->next = oldhead->next;
    newhead->prev= oldhead->prev;
    oldhead->next->prev = newhead;
    oldhead->prev->next = newhead;
    hwc_list_init(oldhead);
}

static inline bool check_video(int format)
{
    switch(format) 
    {
        case HAL_PIXEL_FORMAT_YV12:
	    case HAL_PIXEL_FORMAT_YCrCb_420_SP:
        case HAL_PIXEL_FORMAT_AW_NV12:
            return 1;
        default:
            return 0;
    } 
}

static inline int check_usage_sw_read(struct private_handle_t *psHandle)
{
	return (psHandle->usage & GRALLOC_USAGE_SW_READ_MASK);
}

static inline int check_stop_hwc(struct private_handle_t *psHandle)
{
	return psHandle->usage & STOP_HWC;
}

static inline int check_usage_sw_write(struct private_handle_t *psHandle)
{
	return (psHandle->usage & GRALLOC_USAGE_SW_WRITE_MASK);
}

static inline int check_usage_protected(struct private_handle_t *psHandle)
{
	return psHandle->usage & GRALLOC_USAGE_PROTECTED;
}

static inline bool cursor_flags(hwc_layer_1_t *layer)
{
    return layer->flags & HWC_IS_CURSOR_LAYER;
}

static inline int check_cursor_format(int format)
{
    switch(format) 
    {
        case HAL_PIXEL_FORMAT_RGBA_8888:
        case HAL_PIXEL_FORMAT_BGRA_8888:
            return 1;
    }
    return 0;
}

static inline bool check_cursor(hwc_layer_1_t *layer, int order, int count)
{
    struct private_handle_t *handle = (struct private_handle_t *)layer->handle;
    return (layer->flags & HWC_IS_CURSOR_LAYER)
            && (count-order == 2)
            && check_cursor_format(handle->format);
}

static inline int check_valid_format(int format)
{
    switch(format) 
    {
        case HAL_PIXEL_FORMAT_RGBA_8888:
        case HAL_PIXEL_FORMAT_RGBX_8888:
        //case HAL_PIXEL_FORMAT_RGB_888:
        case HAL_PIXEL_FORMAT_RGB_565:
        case HAL_PIXEL_FORMAT_BGRA_8888:
        //case HAL_PIXEL_FORMAT_sRGB_A_8888:
        //case HAL_PIXEL_FORMAT_sRGB_X_8888:
        case HAL_PIXEL_FORMAT_YV12:
	    case HAL_PIXEL_FORMAT_YCrCb_420_SP:
        case HAL_PIXEL_FORMAT_BGRX_8888:
            return 1;
        default:
            return 0;
    }
}

static inline int check_scale_format(int format)
{
    switch(format)
    {
        case HAL_PIXEL_FORMAT_YV12:
	    case HAL_PIXEL_FORMAT_YCrCb_420_SP:
        case HAL_PIXEL_FORMAT_AW_NV12:
            return 1;
#if defined(UI_SCALE)
        case HAL_PIXEL_FORMAT_BGRA_8888:
        case HAL_PIXEL_FORMAT_BGRX_8888:
        case HAL_PIXEL_FORMAT_RGBX_8888:
        case HAL_PIXEL_FORMAT_RGBA_8888:
        case HAL_PIXEL_FORMAT_RGB_565:
        case HAL_PIXEL_FORMAT_RGB_888:
            return 2;
#endif
        default:
            return 0;
    }
}

static inline bool check_rotate_format(int format)
{
    switch(format)
    {
        case HAL_PIXEL_FORMAT_YV12:
	    case HAL_PIXEL_FORMAT_YCrCb_420_SP:
        case HAL_PIXEL_FORMAT_AW_NV12:

        case HAL_PIXEL_FORMAT_BGRA_8888:
        case HAL_PIXEL_FORMAT_RGBA_8888:
        case HAL_PIXEL_FORMAT_RGBX_8888:
        case HAL_PIXEL_FORMAT_BGRX_8888:
        case HAL_PIXEL_FORMAT_RGB_565:
            return 1;
        default:
            return 0;
    }
}

bool static inline check_3d_video(const DisplayInfo *PsDisplayInfo, hwc_layer_1_t *psLayer)
{
    struct private_handle_t *handle = (struct private_handle_t *)psLayer->handle;
    if(handle == NULL)
    {
        return 0;
    }
    if(!check_video(handle->format))
    {
        goto no_3d;
    }
    switch(PsDisplayInfo->Current3DMode)
    {
        case DISPLAY_2D_LEFT:
        case DISPLAY_2D_TOP:
        case DISPLAY_3D_LEFT_RIGHT_HDMI:
        case DISPLAY_3D_TOP_BOTTOM_HDMI:
            return 1;
        default:
            return 0;
    }

 no_3d:
    return 0;
}

static inline bool check_support_blending(int format)
{
    switch(format) 
    {
        case HAL_PIXEL_FORMAT_RGBA_8888:
        case HAL_PIXEL_FORMAT_RGBX_8888:
        case HAL_PIXEL_FORMAT_RGB_888:
        case HAL_PIXEL_FORMAT_RGB_565:
        case HAL_PIXEL_FORMAT_BGRA_8888:
        //case HAL_PIXEL_FORMAT_sRGB_A_8888:
        //case HAL_PIXEL_FORMAT_sRGB_X_8888:
        case HAL_PIXEL_FORMAT_BGRX_8888:
            return 1;
        default:
            return 0;
    }
}

bool inline hwc_support_rotate(unsigned int transform)
{
    switch(transform)
    {
        case 0:
        case HAL_TRANSFORM_ROT_90:
        case HAL_TRANSFORM_ROT_180:
        case HAL_TRANSFORM_ROT_270:
            return 1;
        default:
            return 0;
    }
}

static inline int check_is_blending(hwc_layer_1_t *psLayer)
{
	return (psLayer->blending != HWC_BLENDING_NONE);
}

static inline int check_is_premult(hwc_layer_1_t *psLayer)
{
    return (psLayer->blending == HWC_BLENDING_PREMULT);
}

static bool inline check_same_scale(float SRWscaleFac, float SRHscaleFac,
        float DTWscalFac, float DTHscaleFac )
{
    return (((SRWscaleFac - DTWscalFac) > -0.009) && ((SRWscaleFac - DTWscalFac) < 0.009))
           &&(((SRHscaleFac - DTHscaleFac) > -0.009) && ((SRHscaleFac - DTHscaleFac) < 0.009));
}

bool inline hwc_rotate_mem(SUNXI_hwcdev_context_t *Globctx, struct private_handle_t *handle)
{
    if((Globctx->has_tr_mem + handle->width * handle->height) > Globctx->tr_mem_limit)
    {
        return 0;
    }
    return 1;
}

static inline bool check_swap_w_h(unsigned int mode)
{
    return  (mode & HAL_TRANSFORM_ROT_90) == HAL_TRANSFORM_ROT_90;
}

static int inline cal_layer_mem(layer_info_t *hw_layer)
{
    hwc_layer_1_t *psLayer = hw_layer->psLayer;
#ifdef HWC_1_3
    return (psLayer->sourceCropf.right - psLayer->sourceCropf.left) 
             * (psLayer->sourceCropf.bottom - psLayer->sourceCropf.top) 
             * hw_layer->form_info.bpp / 8;
#else
    return (psLayer->sourceCrop.right - psLayer->sourceCrop.left) 
            * (psLayer->sourceCrop.bottom - psLayer->sourceCrop.top)
            * hw_layer->form_info.bpp / 8;
#endif
}

extern SUNXI_hwcdev_context_t gSunxiHwcDevice;

extern bool check_3d_video(const DisplayInfo *PsDisplayInfo, hwc_layer_1_t *psLayer);
extern int hwcdev_reset_device(SUNXI_hwcdev_context_t *psDevice, size_t disp);
extern HwcAssignStatus hwc_try_assign_layer(HwcDisContext_t *ctx, size_t singcout, int zOrder);
extern SUNXI_hwcdev_context_t* hwc_create_device(void);
extern int  _hwcdev_layer_config_3d(const DisplayInfo *PsDisplayInfo, disp_layer_info *layer_info);
extern disp_tv_mode get_suitable_hdmi_mode(int select, disp_tv_mode lastmode);
extern void *vsync_thread_wrapper(void *priv);
extern int hwc_setup_layer(HwcDisContext_t *ctx);
extern int hwc_reset_disp(SUNXI_hwcdev_context_t *ctx);
extern int _hwc_device_set_3d_mode(int disp, __display_3d_mode mode);
extern int _hwc_device_set_backlight(int disp, int on_off, bool half);
extern int _hwc_device_set_enhancemode(int disp, bool on_off, bool half);
extern bool hwc_region_intersect(hwc_rect_t *rect0, hwc_rect_t *rect1, hwc_rect_t *rectx);
extern int hwc_destroy_device(void);
extern SUNXI_hwcdev_context_t* hwc_create_device(void);
extern int get_info_mode(int mode, MODEINFO info);
extern disp_tv_mode checkout_mode(int select, bool reset, int lastmode);
extern unsigned int get_ion_addr(int fd);
extern int hwc_hotplug_switch(int DisplayNum, bool plug, disp_tv_mode set_mode);

extern int _hwc_device_set_output_mode(int disp, int out_type, int out_mode);

extern bool sunxi_prepare(hwc_display_contents_1_t **displays, size_t NumofDisp);
extern bool sunxi_set(hwc_display_contents_1_t** displays, size_t numDisplays);
extern hwc_dispc_data_t* hwc_layer_cache_get(SUNXI_hwcdev_context_t *Globctx, int countdisp);

extern void *commit_thread(void *priv);
extern int aw_get_hdmi_setting(int *HdmiMode);
extern int _hwc_set_persent(int disp, int para0, int para1);

extern int ion_alloc_buffer(int iAllocBytes, unsigned int heap_mask);
extern int ion_free_buffer(int handle);
extern unsigned int ion_get_addr_fromfd(int sharefd);
extern unsigned long ion_get_addr_from_handle(int handle);
extern ion_user_handle_t ion_handle_add_ref(int sharefd);
extern bool hwc_manage_ref_cache(bool cache, hwc_dispc_data_t *dispc_data);

extern bool hwc_rotate_layer_tr(hwc_dispc_data_t *hwc_layer, hwc_commit_data_t *commit_data, int disp, int layer, int cnt_st);
extern bool hwc_rotate_request(void);
extern void hwc_rotate_release(void);
extern void hwc_rotate_cache_free( void);
extern int hwc_manage_display(DisplayInfo **retDisplayInfo, int DispInfo, ManageDisp mode);
extern void hwc_wb_manage(hwc_dispc_data_t *DisplayData, hwc_commit_data_t *commit_data, disp_capture_info *data);
extern int hwc_set_cursor_async(struct hwc_composer_device_1 *dev, int disp, int x_pos, int y_pos);
extern void hwc_cursor_manage(SUNXI_hwcdev_context_t *Globctx, hwc_commit_layer_t *cursor_layer, unsigned int sync_count);
extern bool update_cursor_layer(SUNXI_hwcdev_context_t *Globctx, hwc_commit_layer_t *cursor_layer, hwc_dispc_data_t *DisplayData);
extern bool get_rotate_cursor_layer(SUNXI_hwcdev_context_t *Globctx, hwc_commit_layer_t *commit_layer);
extern void show_displays(HwcDisContext_t *Localctx);
extern void hwc_set_debug(SUNXI_hwcdev_context_t *psCtx);
extern void hwc_debug_close_layer(SUNXI_hwcdev_context_t *psCtx, hwc_commit_data_t *commit_data);
extern void hwc_debug_dump_layer(SUNXI_hwcdev_context_t *psCtx, hwc_commit_layer_t *hwc_layer_info, int disp, int sync_count);
extern bool hwc_debug_pause(SUNXI_hwcdev_context_t *psCtx, int sync_count, bool paused);
extern AssignDUETO_T calculate_memthruput(HwcDisContext_t *Localctx, layer_info_t *psLayer,
                                float WscalFac, float HscaleFac, int channel, bool isFB, bool isvideo);
extern void hwc_down_limit(SUNXI_hwcdev_context_t *Globctx, int local_mem[NUMBEROFDISPLAY], bool force_gpu);
extern void hwc_updata_limit(SUNXI_hwcdev_context_t *Globctx,
            hwc_dispc_data_t *DisplayData, int local_mem[NUMBEROFDISPLAY], unsigned int start_count);
extern bool hwc_format_info(format_info *m_format_info, int format);
extern bool mem_ctrl_power_policy(SUNXI_hwcdev_context_t *Globctx, HwcDisContext_t *localctx);
extern void mem_adjust_moment(SUNXI_hwcdev_context_t *Globctx,
        int diff_all, int lower_limit, int high_limit, bool updata);

#endif
