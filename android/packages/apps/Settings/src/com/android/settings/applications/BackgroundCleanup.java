package com.android.settings.applications;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
 
 
import android.support.v7.preference.Preference.OnPreferenceClickListener;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.lang.StringBuilder;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import com.android.settings.InstrumentedFragment;
import com.android.settings.R;

public class BackgroundCleanup extends BackgroundCleanupBase implements OnPreferenceClickListener {
    private static final String KEY_APP_LIST = "app_list";
    private PackageManager mPackageManager;
    private InputMethodManager mInputMethodManager;
    private String[] mSystemWhitelist;
    private boolean mIsWhitelist;
    private Handler mHandler;

    private ArrayList<BackgroundCleanupAppPreference> mAppList;
    private PreferenceGroup mAppListGroup;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setHasOptionsMenu(true);

        mPackageManager = getActivity().getPackageManager();
        mInputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        mIsWhitelist = getResources().getBoolean(
                 com.android.internal.R.bool.kill_all_background_services);
        mSystemWhitelist = getResources().getStringArray(
                com.android.internal.R.array.background_services_whitelist);
        mHandler = new Handler();
        mAppListGroup = (PreferenceGroup) findPreference(KEY_APP_LIST);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.background_cleanup_menu, menu);
    }

    private void addNotAvailableMessage() {
        Preference notAvailable = new Preference(getActivity());
        notAvailable.setTitle(R.string.background_cleanup_list_not_available);
        mAppListGroup.addPreference(notAvailable);
    }

    public void refreshUi() {
        mAppListGroup.removeAll();
        if (mLimit == 0) {
            addNotAvailableMessage();
        } else {
            mAppList = getAppList();
            for (BackgroundCleanupAppPreference pref : mAppList) {
                mAppListGroup.addPreference(pref);
                pref.setOnPreferenceClickListener(this);
            }
        }
    }

    private ArrayList<BackgroundCleanupAppPreference> getAppList() {
        ArrayList<BackgroundCleanupAppPreference> tempList = new ArrayList<BackgroundCleanupAppPreference>();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> lists = mPackageManager.queryIntentActivities(mainIntent, 0);
        List<InputMethodInfo> imis = mInputMethodManager.getInputMethodList();
        List<String> userList = Arrays.asList(getUserList());
        for (ResolveInfo app : lists) {
            try {
                String packageName = app.activityInfo.packageName;
                boolean skip = false;
                for (String item : mSystemWhitelist) {
                    if (packageName.startsWith(item)) {
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    continue;
                }
                for (InputMethodInfo info : imis) {
                    if (packageName.equals(info.getPackageName())) {
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    continue;
                }

                ComponentName cn = new ComponentName(packageName, app.activityInfo.name);
                ActivityInfo info = mPackageManager.getActivityInfo(cn, 0);
                final CharSequence title = info.loadLabel(mPackageManager);
                final Drawable icon = info.loadIcon(mPackageManager);
                final BackgroundCleanupAppPreference pref = new BackgroundCleanupAppPreference(getActivity(), packageName);
                pref.setIcon(icon != null ? icon : new ColorDrawable(0));
                pref.setTitle(title);
                pref.setChecked(!userList.contains(packageName));
                tempList.add(pref);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        Collections.sort(tempList, getAppNameComparator());
        return tempList;
    }

    private String[] getUserList() {
        String value = Settings.System.getString(getActivity().getContentResolver(),
                Settings.System.KILL_BACKGROUND_SERVICES_LIST);
        if (value == null || value.length() == 0) {
            return new String[]{""};
        }
        return value.split(",");
    }

    private Comparator<BackgroundCleanupAppPreference> getAppNameComparator() {
        final Collator collator = Collator.getInstance();
        return new Comparator<BackgroundCleanupAppPreference>() {
            public final int compare(BackgroundCleanupAppPreference a, BackgroundCleanupAppPreference b) {
                int result = collator.compare(a.getTitle().toString().trim(),
                            b.getTitle().toString().trim());
                return result;
            }
        };
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.background_cleanup_select_all_menu) {
            for (BackgroundCleanupAppPreference pref : mAppList) {
                pref.setChecked(true);
            }
            updateUserList();
            return true;
        } else if (item.getItemId() == R.id.background_cleanup_select_inverse_menu) {
            for (BackgroundCleanupAppPreference pref : mAppList) {
                pref.setChecked(false);
            }
            updateUserList();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (mAppList != null && mAppList.contains(preference)) {
            ((BackgroundCleanupAppPreference)preference).doSwitch();
            updateUserList();
            return true;
        }
        return false;
    }

    private void updateUserList() {
        mHandler.removeCallbacks(setUserListRunnable);
        mHandler.postDelayed(setUserListRunnable, 1000);
    }

    private Runnable setUserListRunnable = new Runnable() {
        @Override
        public void run() {
            StringBuilder sb = new StringBuilder();
            if (mAppList != null) {
                boolean firstTime = true;
                for (BackgroundCleanupAppPreference pref : mAppList) {
                    if (!pref.getChecked()) {
                        if (firstTime) {
                            firstTime = false;
                        } else {
                            sb.append(",");
                        }
                        sb.append(pref.getPackageName());
                    }
                }
            }
            Settings.System.putString(getActivity().getContentResolver(),
                    Settings.System.KILL_BACKGROUND_SERVICES_LIST, sb.toString());
        }
    };

    @Override
    protected int getMetricsCategory() {
        return InstrumentedFragment.BACKGROUND_CLEANUP;
    }
}
