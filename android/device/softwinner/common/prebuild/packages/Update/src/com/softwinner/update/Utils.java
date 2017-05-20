package com.softwinner.update;

import android.os.SystemProperties;
public class Utils {
    public static final String GROBLE_TAG = "SoftwinnerUpdater";

    //public static final String SERVER_URL_USE_DOMAIN = "http://120.24.158.141:8080/controller/controller.shtml";
    //public static final String SERVER_URL_USE_IP = "http://120.24.158.141:8080/controller/controller.shtml";

    public static final String DOWNLOAD_PATH = "/sdcard/ota.zip";
    public static final boolean DEBUG = false;  //false
    public static final int CHECK_CYCLE_DAY=1;
    public static final String UNKNOWN = "unknown";
    
    public static final String SERVER_URL_USE_DOMAIN = getString("ro.ota.ip")+"/ota/controller/controller.shtml"; 
    public static final String SERVER_URL_USE_IP = getString("ro.ota.ip")+"/ota/controller/controller.shtml";
    private static String getString(String property) {
        return SystemProperties.get(property, UNKNOWN);
    }

}
