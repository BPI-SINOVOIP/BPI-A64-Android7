package android.os;

import android.util.Log;
import android.os.IBinder;
import android.os.Binder;
import android.os.IDynamicPManager;
import android.content.Intent;


/**
 * Dynamic Power Manager
 */
/** @hide */
public class DynamicPManager
{
       private static final String TAG                          = "DynamicPManager";
       public  static final String DPM_SERVICE          = "DynamicPManager";
    //must be same as DynamicPManagerService
    public static final String BOOST_UPERF_4KLOCALVIDEO = "mode_4klocalvideo";
    public static final String BOOST_UPERF_LOCALVIDEO   = "mode_localvideo";
    public static final String BOOST_UPERF_NORMAL      = "mode_normal";
    public static final String BOOST_UPERF_EXTREME     = "mode_extreme";
    public static final String BOOST_UPERF_HOMENTER    = "mode_home_enter";
    public static final String BOOST_UPERF_HOMEXIT     = "mode_home_exit";
    public static final String BOOST_UPERF_BGMUSIC     = "mode_bgmusic_enter";
    public static final String BOOST_UPERF_ROTATENTER  = "mode_rotation_enter";
    public static final String BOOST_UPERF_ROTATEXIT   = "mode_rotation_exit";
    public static final String BOOST_UPERF_USBENTER  = "mode_usb_enter";
    public static final String BOOST_UPERF_USBEXIT   = "mode_usb_exit";

    private IDynamicPManager mService;
       private static DynamicPManager mDPM = null;

       public static DynamicPManager getInstance() {
        if (mDPM == null)
                       mDPM = new DynamicPManager();
               return mDPM;
       }

       private DynamicPManager() {
               IBinder b = ServiceManager.getService(DPM_SERVICE);

        if (mService == null) {
            mService     = IDynamicPManager.Stub.asInterface(b);
        }
       }

       public void notifyDPM(Intent intent){
               try{
            if (mService == null) {
                IBinder b = ServiceManager.getService(DPM_SERVICE);
                mService         = IDynamicPManager.Stub.asInterface(b);
            }
            if (mService != null) {
                           mService.notifyDPM(intent);
            } else {
                Log.e(TAG,"DynamicPManager mService is null-------");
            }
               }catch(RemoteException e){
               }
       }

}
