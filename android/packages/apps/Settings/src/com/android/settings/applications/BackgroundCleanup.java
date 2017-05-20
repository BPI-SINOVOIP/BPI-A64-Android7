package com.android.settings.applications;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.StringBuilder;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;

import com.android.settings.InstrumentedFragment;
import com.android.settings.R;
import android.util.Log;

public class BackgroundCleanup extends InstrumentedFragment {
    private PackageManager mPackageManager;
    private InputMethodManager mInputMethodManager;
    private String[] mWhitelist;
    private ArrayList<AppInfo> mAppList;

    private Handler mHandler;

    private View mContentView;
    private ListView mListView;
    private MyAppAdapter mAppAdapter;
    private boolean mIsWhitelist;

    private static final String TAG = "BackgroundCleanup";
    private static final boolean DEBUG = true;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.background_cleanup_list_layout, null);

        mPackageManager = getActivity().getPackageManager();
        mInputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        mIsWhitelist = getResources().getBoolean(
                 com.android.internal.R.bool.kill_all_background_services);
        mWhitelist = getResources().getStringArray(
                com.android.internal.R.array.background_services_whitelist);
        mAppList = getAppList();

        mHandler = new Handler();

        mListView = (ListView) mContentView.findViewById(R.id.app_list);

        mAppAdapter = new MyAppAdapter(getActivity(), mAppList, mIsWhitelist);
        mListView.setAdapter(mAppAdapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                AppInfo appInfo = mAppList.get(position);
                appInfo.setIsChecked(!appInfo.isChecked());
                updateBlacklist();
            }
        });
        return mContentView;
    }

    @Override
    protected int getMetricsCategory() {
        return BACKGROUND_CLEANUP;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.background_cleanup_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.background_cleanup_select_all_menu) {
            for (AppInfo appInfo : mAppList) {
                appInfo.setIsChecked(!mIsWhitelist);
            }
            updateBlacklist();
            return true;
        } else if (item.getItemId() == R.id.background_cleanup_select_inverse_menu) {
            for (AppInfo appInfo : mAppList) {
                appInfo.setIsChecked(!appInfo.isChecked());
            }
            updateBlacklist();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private ArrayList<AppInfo> getAppList() {
        ArrayList<AppInfo> tempList = new ArrayList<AppInfo>();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> lists = mPackageManager.queryIntentActivities(
                        mainIntent, 0);
        List<InputMethodInfo> imis = mInputMethodManager.getInputMethodList();
        AuthenticatorDescription[] authDescs = AccountManager.get(getActivity()).getAuthenticatorTypesAsUser(
                UserHandle.USER_OWNER);
        List<String> blacklist = Arrays.asList(getKillBackgroundServicesList());
        for (ResolveInfo app : lists) {
            try {
                String packageName = app.activityInfo.packageName;
                boolean skip = false;
                if (DEBUG) Log.d(TAG, "getAppList app.activityInfo.packageName: " + packageName );
                for (String item : mWhitelist) {
                	if (DEBUG) Log.d(TAG, "getAppList mWhitelist: " + mWhitelist );
                    if (packageName.startsWith(item)) {
                        skip = true;
                        if (DEBUG) Log.d(TAG, "getAppList mWhitelist skip: " + skip );
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
                for (AuthenticatorDescription authDesc : authDescs) {
                    if (packageName.equals(authDesc.packageName)) {
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    continue;
                }
                ComponentName cn = new ComponentName(packageName, app.activityInfo.name);
                ActivityInfo info = mPackageManager.getActivityInfo(cn, 0);
                AppInfo appInfo = new AppInfo(info.loadLabel(mPackageManager)
                                .toString(),info.loadIcon(mPackageManager), packageName, false);
                if (blacklist.contains(packageName)) {
                    appInfo.setIsChecked(true);
                }
                tempList.add(appInfo);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        Collections.sort(tempList, getAppNameComparator());
        return tempList;
    }

    private String[] getKillBackgroundServicesList() {
        String value = Settings.System.getString(getActivity().getContentResolver(),
                Settings.System.KILL_BACKGROUND_SERVICES_LIST);
        if (value == null || value.length() == 0) {
            return new String[]{""};
        }
        return value.split(",");
    }

    private Comparator<AppInfo> getAppNameComparator() {
        final Collator collator = Collator.getInstance();
        return new Comparator<AppInfo>() {
            public final int compare(AppInfo a, AppInfo b) {
                int result = collator.compare(a.getName().toString().trim(),
                            b.getName().toString().trim());
                return result;
            }
        };
    }

    private void updateBlacklist() {
        mAppAdapter.notifyDataSetChanged();
        mHandler.removeCallbacks(setBlacklistRunnable);
        mHandler.postDelayed(setBlacklistRunnable, 1000);
    }

    private Runnable setBlacklistRunnable = new Runnable() {
        @Override
        public void run() {
            StringBuilder sb = new StringBuilder();
            boolean firstTime = true;
            for (AppInfo appinfo : mAppList) {
                if (appinfo.isChecked()) {
                    if (firstTime) {
                        firstTime = false;
                    } else {
                        sb.append(",");
                    }
                    sb.append(appinfo.getPackageName());
                }
            }
            Settings.System.putString(getActivity().getContentResolver(),
                    Settings.System.KILL_BACKGROUND_SERVICES_LIST, sb.toString());
        }
    };

    private class AppInfo {

        private String name;
        private Drawable icon;
        private String packageName;
        private boolean isCheck;

        public AppInfo(String name, Drawable icon, String packageName) {
            this.name = name;
            this.icon = icon;
            this.packageName = packageName;
            this.isCheck = false;
        }

        public AppInfo(String name, Drawable icon, String packageName, boolean isCheck) {
            this.name = name;
            this.icon = icon;
            this.packageName = packageName;
            this.isCheck = isCheck;
        }

        public String getName() {
            return this.name;
        }

        public String getPackageName() {
            return this.packageName;
        }

        public Drawable getIcon() {
            return this.icon;
        }

        public boolean isChecked() {
            return this.isCheck;
        }

        public void setIsChecked(boolean isCheck) {
            this.isCheck = isCheck;
        }
    }

    class MyAppAdapter extends BaseAdapter {

        private Context context;
        private LayoutInflater inflater;
        private ArrayList<AppInfo> lists;
        private boolean whitelist;

        public MyAppAdapter(Context context, ArrayList<AppInfo> lists, boolean whitelist) {
            super();
            this.context = context;
            this.lists = lists;
            this.whitelist = whitelist;
            inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return lists.size();
        }

        @Override
        public Object getItem(int arg0) {
            return arg0;
        }

        @Override
        public long getItemId(int arg0) {
            return arg0;
        }

        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.background_cleanup_list_item,
                        parent, false);
                holder = new ViewHolder();
                holder.icon = (ImageView) convertView.findViewById(R.id.app_icon);
                holder.name = (TextView) convertView.findViewById(R.id.app_name);
                holder.checkbox = (CheckBox) convertView.findViewById(R.id.app_cb);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            AppInfo appInfo = lists.get(position);
            holder.icon.setBackground(appInfo.getIcon());
            holder.name.setText(appInfo.getName());
            if(appInfo.isChecked()) {
                holder.checkbox.setChecked(!whitelist);
            } else {
                holder.checkbox.setChecked(whitelist);
            }
            return convertView;
        }
    }

    class ViewHolder {
        ImageView icon;
        TextView name;
        CheckBox checkbox;
    }
}
