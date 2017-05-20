
#ifndef __HAL_CALLBACK_NOTIFIER_H__
#define __HAL_CALLBACK_NOTIFIER_H__

#include "BufferListManager.h"
#include "CameraPlatform.h"
/*
 * Contains declaration of a class CallbackNotifier that manages callbacks set
 * via set_callbacks, enable_msg_type, and disable_msg_type camera HAL API.
 */
#ifdef __CEDARX_FRAMEWORK_1__
extern "C" int JpegEnc(void * pBufOut, int * bufSize, JPEG_ENC_t *jpeg_enc);

#elif defined __CEDARX_FRAMEWORK_2__
#include "vencoder.h"
#include "MetadataBufferType.h"
#endif	
	
	enum ThreadState {
		THREAD_STATE_NULL,		// The thread has not been created.
		THREAD_STATE_PAUSED,	// The thread is paused for waiting some signal.
		THREAD_STATE_RUNNING,	// The thread is in running.
		THREAD_STATE_EXIT,		// The thread will exit.
	};

namespace android {

class V4L2CameraDevice;

/* Manages callbacks set via set_callbacks, enable_msg_type, and disable_msg_type
 * camera HAL API.
 *
 * Objects of this class are contained in CameraHardware objects, and handle
 * relevant camera API callbacks.
 * Locking considerations. Apparently, it's not allowed to call callbacks
 * registered in this class, while holding a lock: recursion is quite possible,
 * which will cause a deadlock.
 */
class CallbackNotifier {
public:
    /* Constructs CallbackNotifier instance. */
    CallbackNotifier();

    /* Destructs CallbackNotifier instance. */
    ~CallbackNotifier();

    /****************************************************************************
     * Camera API
     ***************************************************************************/

public:
    /* Actual handler for camera_device_ops_t::set_callbacks callback.
     * This method is called by the containing V4L2Camera object when it is
     * handing the camera_device_ops_t::set_callbacks callback.
     */
    void setCallbacks(camera_notify_callback notify_cb,
                      camera_data_callback data_cb,
                      camera_data_timestamp_callback data_cb_timestamp,
                      camera_request_memory get_memory,
                      void* user);

    /* Actual handler for camera_device_ops_t::enable_msg_type callback.
     * This method is called by the containing V4L2Camera object when it is
     * handing the camera_device_ops_t::enable_msg_type callback.
     */
    void enableMessage(uint msg_type);

    /* Actual handler for camera_device_ops_t::disable_msg_type callback.
     * This method is called by the containing V4L2Camera object when it is
     * handing the camera_device_ops_t::disable_msg_type callback.
     */
    void disableMessage(uint msg_type);

    void enableSmartMessage(uint msg_type);
    void disableSmartMessage(uint msg_type);
    /* Actual handler for camera_device_ops_t::store_meta_data_in_buffers
     * callback. This method is called by the containing V4L2Camera object
     * when it is handing the camera_device_ops_t::store_meta_data_in_buffers
     * callback.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    status_t storeMetaDataInBuffers(bool enable);

    /* Enables video recording.
     * This method is called by the containing V4L2Camera object when it is
     * handing the camera_device_ops_t::start_recording callback.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    status_t enableVideoRecording();

    /* Disables video recording.
     * This method is called by the containing V4L2Camera object when it is
     * handing the camera_device_ops_t::stop_recording callback.
     */
    void disableVideoRecording();

    /* Actual handler for camera_device_ops_t::msg_type_enabled callback.
     * This method is called by the containing V4L2Camera object when it is
     * handing the camera_device_ops_t::msg_type_enabled callback.
     * Note: this method doesn't grab a lock while checking message status, since
     * upon exit the status would be undefined anyway. So, grab a lock before
     * calling this method if you care about persisting a defined message status.
     * Return:
     *  0 if message is disabled, or non-zero value, if message is enabled.
     */
    inline int isMessageEnabled(uint msg_type)
    {
        return mMessageEnabler & msg_type;
    }
    inline int isSmartMessageEnabled(uint msg_type)
    {
        return (mSmartMessageEnabler & 0x0fff) & msg_type;
    }

    /* Checks id video recording is enabled.
     * This method is called by the containing V4L2Camera object when it is
     * handing the camera_device_ops_t::recording_enabled callback.
     * Note: this method doesn't grab a lock while checking video recordin status,
     * since upon exit the status would be undefined anyway. So, grab a lock
     * before calling this method if you care about persisting of a defined video
     * recording status.
     * Return:
     *  true if video recording is enabled, or false if it is disabled.
     */
    inline bool isVideoRecordingEnabled()
    {
        return mVideoRecEnabled;
    }

    /****************************************************************************
     * Public API
     ***************************************************************************/

public:
    /* Resets the callback notifier. */
    void cleanupCBNotifier();

    /* Next frame is available in the camera device.
     * This is a notification callback that is invoked by the camera device when
     * a new frame is available.
     * Note that most likely this method is called in context of a worker thread
     * that camera device has created for frame capturing.
     * Param:
     *  frame - Captured frame, or NULL if camera device didn't pull the frame
     *      yet. If NULL is passed in this parameter use GetCurrentFrame method
     *      of the camera device class to obtain the next frame. Also note that
     *      the size of the frame that is passed here (as well as the frame
     *      returned from the GetCurrentFrame method) is defined by the current
     *      frame settings (width + height + pixel format) for the camera device.
     * timestamp - Frame's timestamp.
     * camera_dev - Camera device instance that delivered the frame.
     */
    void onNextFrameAvailable(const void* frame,
                              bool hw);

	void onNextFrameHW(const void* frame);

	void onNextFrameSW(const void* frame);

    /* Entry point for notifications that occur in camera device.
     * Param:
     *  err - CAMERA_ERROR_XXX error code.
     */
    void onCameraDeviceError(int err);

    /* Sets JPEG quality used to compress frame during picture taking. */
    void setJpegQuality(int jpeg_quality)
    {
        mJpegQuality = jpeg_quality;
    }

    /****************************************************************************
     * Private API
     ***************************************************************************/

    /****************************************************************************
     * Data members
     ***************************************************************************/

protected:
    /* Locks this instance for data change. */
    Mutex                           mObjectLock;

    /*
     * Callbacks, registered in set_callbacks.
     */

    camera_notify_callback          mNotifyCB;
    camera_data_callback            mDataCB;
    camera_data_timestamp_callback  mDataCBTimestamp;
    camera_request_memory           mGetMemoryCB;
    void*                           mCallbackCookie;

    /* Message enabler. */
    uint32_t                        mMessageEnabler;

    uint32_t                        mSmartMessageEnabler;
    /* Video recording status. */
    bool                            mVideoRecEnabled;

	// -------------------------------------------------------------------------
	// extended interfaces here <***** star *****>
	// -------------------------------------------------------------------------
	
public:
	inline void setPictureSize(int w, int h)
    {
        mPictureWidth = w;
        mPictureHeight = h;
    }

	inline void setExifMake(char * make)
	{
		strcpy(mExifMake, make);
	}
	
	inline void setExifModel(char * model)
	{
		strcpy(mExifModel, model);
	}
	
	// Sets JPEG rotate used to compress frame during picture taking.
    inline void setJpegRotate(int jpeg_rotate)
    {
        mJpegRotate = jpeg_rotate;
    }

	inline void setGPSLatitude(double gpsLatitude)
	{
		mGpsLatitude = gpsLatitude;
	}

	inline void setGPSLongitude(double gpsLongitude)
	{
		mGpsLongitude = gpsLongitude;
	}

	inline void setGPSAltitude(double gpsAltitude)
	{
		mGpsAltitude = gpsAltitude;
	}

	inline void setGPSTimestamp(long gpsTimestamp)
	{
		mGpsTimestamp = gpsTimestamp;
	}

	inline void setGPSMethod(const char * gpsMethod)
	{
		strcpy(mGpsMethod, gpsMethod);
	}

	inline void setJpegThumbnailSize(int w, int h)
	{
		mThumbWidth = w;
		mThumbHeight = h;
	}

	inline void setFocalLenght(double val)
	{
		mFocalLength = val;
	}
	
	inline void setWhiteBalance(int whitebalance)
	{
		mWhiteBalance = whitebalance;
	}
	
	inline void setCallingProcess(char * str)
	{
		strcpy(mCallingProcessName, str);
	}

	inline void setSaveFolderPath(const char * str)
	{
		strcpy(mFolderPath, str);
	}
	
	inline void setSnapPath(const char * str)
	{
		strcpy(mSnapPath, str);
	}
	
	inline void setCBSize(int w, int h)
	{
		mCBWidth = w;
		mCBHeight = h;
	}

	void setPictureMode(bool single)
	{
		mIsSinglePicture = single;
	}
	void setFd(int fd);
	status_t autoFocusMsg(bool success);
	status_t autoFocusContinuousMsg(bool success);
	status_t faceDetectionMsg(camera_frame_metadata_t *face);
    status_t smartDetectionMsg(int32_t type);
	bool takePicture(const void* frame, void *memOpsS, bool is_continuous = false);
	void startContinuousPicture();
	void stopContinuousPicture();

	void setContinuousPictureCnt(int cnt);

	void notifyPictureMsg(const void* frame);

	void getCurrentDateTime();
	
	void setExifInfo(struct isp_exif_attribute exifinfo,int zoom_ratio,int exposure_bias);
	// -------------------------------------------------------------------------
	// continuous picture
	// -------------------------------------------------------------------------
	
protected:

	class DoSavePictureThread : public Thread {
		CallbackNotifier* mCallbackNotifier;
		ThreadState		mThreadStatus;
	public:
		DoSavePictureThread(CallbackNotifier* cb) :
			Thread(false),
			mCallbackNotifier(cb),
			mThreadStatus(THREAD_STATE_NULL) {
		}
		void startThread() {
			mThreadStatus = THREAD_STATE_RUNNING;
			run("DoSavePictureThread", PRIORITY_DISPLAY);
		}
		void stopThread() {
			mThreadStatus = THREAD_STATE_EXIT;
        }
		ThreadState getThreadStatus() {
			return mThreadStatus;
		}
		bool isThreadStarted() {
			return (mThreadStatus == THREAD_STATE_PAUSED) || (mThreadStatus == THREAD_STATE_RUNNING);
		}
		virtual bool threadLoop() {
			return mCallbackNotifier->savePictureThread();
		}
	};

	bool 							savePictureThread();

	sp<DoSavePictureThread> 		mSavePictureThread;
	pthread_mutex_t 				mSavePictureMutex;
	pthread_cond_t					mSavePictureCond;
	int 							mSavePictureCnt;
	int 							mSavePictureMax;
	
	pthread_mutex_t 				mPictureFdMutex;
	pthread_cond_t					mPictureFdCond;
protected:
	// calling process name for some app, such as facelock
	char							mCallingProcessName[128];
	
    // JPEG quality used to compress frame during picture taking.
    int                             mJpegQuality;

	// JPEG rotate used to compress frame during picture taking.
	int								mJpegRotate;

	// JPEG size
	int								mPictureWidth;
	int								mPictureHeight;

	// thumb size
	int								mThumbWidth;
	int								mThumbHeight;

	// gps exif
	double      					mGpsLatitude;
	double							mGpsLongitude;
	double        					mGpsAltitude;
	long        					mGpsTimestamp;
	char							mGpsMethod[100]; 
	
	double							mFocalLength;
	int 							mWhiteBalance;

	char  							mExifMake[64];		//for the cameraMake name
	char  							mExifModel[64];		//for the cameraMode
	char  							mDateTime[64];		//for the data and time
	char                            mFolderPath[128];
	char                            mSnapPath[128];
	int                             mFd;

	//exif info
	rational_t		mExposureTime;
	rational_t		mFNumber;
	short			mISOSpeed;
	rational_t 		mExposureBiasValue;
	short			mMeteringMode;
	short			mFlashUsed;
	rational_t		mFocalLength_r;
	rational_t		mDigitalZoomRatio;
	short			mExposureMode;
	// cb size
	int								mCBWidth;
	int								mCBHeight;

	BufferListManager *				mBufferList;
	bool							mSaveThreadExited;
	bool							mIsSinglePicture;
	int                             mContinuousFd[10];
	int								mContinuousFdIndex;
};

}; /* namespace android */

#endif  /* __HAL_CALLBACK_NOTIFIER_H__ */
