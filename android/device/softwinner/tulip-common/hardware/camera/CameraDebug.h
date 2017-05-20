#ifndef __HAL_CAMERA_DEBUG_H__
#define __HAL_CAMERA_DEBUG_H__

#define CAMERA_HAL_VERSION		"3000130327_V1.0"

#define CAMERA_HAL_MODE_OLD		1
#if	(CAMERA_HAL_MODE_OLD == 1)
#define USE_OLD_MODE
#else
#define USE_NEW_MODE
#endif
#define USE_ION_MEM_ALLOCATOR

#define ALIGN_4K(x) (((x) + (4095)) & ~(4095))
#define ALIGN_32B(x) (((x) + (31)) & ~(31))
#define ALIGN_16B(x) (((x) + (15)) & ~(15))
#define ALIGN_8B(x) (((x) + (7)) & ~(7))

#define DBG_CAMERA_HARDWARE		  0
#define DBG_V4L2_CAMERA			  0
#define DBG_PREVIEW				  0
#define DBG_CALLBACK			  0
#define DBG_CAMERA_FACTORY		  0
#define DBG_CAMERA_CONFIG		  0
#define DBG_BUFFER_LIST			  0
#define DBG_BUFFER_SAVE			  0

/* Defines whether we should trace parameter changes. */
#define DEBUG_PARAM 0

#define DEBUG_MSG	0

// enable all print information
#define LOG_NDEBUG 1

#define F_LOG ALOGV("%s, line: %d", __FUNCTION__, __LINE__);

#define LOGV	ALOGV
#define LOGD	ALOGD
#define LOGW	ALOGW
#define LOGE	ALOGE
#define PATH "/data/camera/"

#define LOGE_IF	ALOGE_IF
#define LOGW_IF	ALOGW_IF

// performance debug
#define DBG_TIME_ENABLE		0
#if DBG_TIME_ENABLE
#define LOG_TIME			ALOGD
#define DBG_TIME_BEGIN(inf, en)											\
	int64_t llt_ms = systemTime() / 1000000;							\
	int64_t lt_ms = llt_ms;												\
	int64_t nt_ms = 0;													\
	if (en)	LOG_TIME("\t[T_DBG_bgn] %s timestamp: %lld", inf, lt_ms);

#define DBG_TIME_DIFF(inf)												\
	nt_ms = systemTime() / 1000000;										\
	LOG_TIME("\t[T_DBG_dff] %s use: %lld(ms)", inf, (nt_ms - lt_ms));	\
	lt_ms = nt_ms;

#define DBG_TIME_END(inf, en)											\
	nt_ms = systemTime() / 1000000;										\
	LOG_TIME("\t[T_DBG_end] %s use: %lld(ms)", inf, (nt_ms - llt_ms));	\
	if (en) LOG_TIME("\t[T_DBG_end] %s timestamp: %lld", inf, nt_ms);
#else
#define DBG_TIME_BEGIN(inf, en)
#define DBG_TIME_DIFF(inf)
#define DBG_TIME_END(inf, en)
#endif

#define DBG_TIME_AVG_ENABLE		0
#if DBG_TIME_AVG_ENABLE
#define LOG_TIME_AVG			ALOGD
#define DBG_TIME_AVG_BEGIN(tag)											\
	static int64_t tag##_time = 0;										\
	static int tag##_cnt = 0;

#define DBG_TIME_AVG_INIT(tag)											\
	tag##_time = 0;	tag##_cnt = 0;

#define DBG_TIME_AVG_AREA_IN(tag)										\
	int64_t tag##_diff = systemTime();
	
#define DBG_TIME_AVG_AREA_OUT(tag)										\
	tag##_diff = systemTime() - tag##_diff;								\
	tag##_time += tag##_diff;											\
	tag##_cnt++;
	
#define DBG_TIME_AVG_END(tag, inf)										\
	if (tag##_cnt > 0) LOG_TIME_AVG("\t[T_DBG_avg] %s cnt: %d, average use: %lld(us)", inf, tag##_cnt, tag##_time/tag##_cnt/1000);
#else
#define DBG_TIME_AVG_BEGIN(tag)
#define DBG_TIME_AVG_INIT(tag)
#define DBG_TIME_AVG_AREA_IN(tag)
#define DBG_TIME_AVG_AREA_OUT(tag)
#define DBG_TIME_AVG_END(tag, inf)
#endif


#ifdef __SUN4I__
#define USE_MP_CONVERT
#endif

#ifdef __SUN6I__
#define USE_MP_CONVERT
#endif

#ifdef __SUN9I__
 
#define USE_MP_CONVERT
#endif /*USE_ION_MEM_ALLOCATOR*/

extern  bool saveframe(char *str,void *p, unsigned int length,bool is_oneframe);
extern bool saveSize(int width, int height);

#endif // __HAL_CAMERA_DEBUG_H__

