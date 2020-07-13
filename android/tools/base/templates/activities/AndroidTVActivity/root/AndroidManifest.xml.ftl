<manifest xmlns:android="http://schemas.android.com/apk/res/android" >

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />

    <uses-feature android:name="android.software.leanback"
        android:required="true" />

    <application>

        <activity android:name="${packageName}.${activityClass}"
            android:icon="@drawable/app_icon_your_company"
            android:logo="@drawable/app_icon_your_company"
            android:screenOrientation="landscape"
            <#if isNewProject>
            android:label="@string/app_name"
            <#else>
            android:label="@string/title_${activityToLayout(activityClass)}"
            </#if>
            <#if buildApi gte 16 && parentActivityClass != "">android:parentActivityName="${parentActivityClass}"</#if>>
            <#if parentActivityClass != "">
            <meta-data android:name="android.support.PARENT_ACTIVITY"
                android:value="${parentActivityClass}" />
            </#if>
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <activity android:name="${packageName}.${detailsActivity}" />
        <activity android:name="PlaybackOverlayActivity" />
        <activity android:name="BrowseErrorActivity" />

    </application>

</manifest>
