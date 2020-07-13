/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.phone.vvm.omtp.scheduling;

import android.annotation.CallSuper;
import android.annotation.MainThread;
import android.annotation.WorkerThread;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.telephony.SubscriptionManager;
import com.android.phone.Assert;
import com.android.phone.NeededForTesting;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides common utilities for task implementations, such as execution time and managing {@link
 * Policy}
 */
public abstract class BaseTask implements Task {

    private static final String EXTRA_SUB_ID = "extra_sub_id";

    private Context mContext;

    private int mId;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    private boolean mHasStarted;
    private volatile boolean mHasFailed;

    @NonNull
    private final List<Policy> mPolicies = new ArrayList<>();

    private long mExecutionTime;

    private static Clock sClock = new Clock();

    protected BaseTask(int id) {
        mId = id;
        mExecutionTime = getTimeMillis();
    }

    /**
     * Modify the task ID to prevent arbitrary task from executing. Can only be called before {@link
     * #onCreate(Context, Intent, int, int)} returns.
     */
    @MainThread
    public void setId(int id) {
        Assert.isMainThread();
        mId = id;
    }

    @MainThread
    public boolean hasStarted() {
        Assert.isMainThread();
        return mHasStarted;
    }

    @MainThread
    public boolean hasFailed() {
        Assert.isMainThread();
        return mHasFailed;
    }

    public Context getContext() {
        return mContext;
    }

    public int getSubId() {
        return mSubId;
    }
    /**
     * Should be call in the constructor or {@link Policy#onCreate(BaseTask, Intent, int, int)} will
     * be missed.
     */
    @MainThread
    public BaseTask addPolicy(Policy policy) {
        Assert.isMainThread();
        mPolicies.add(policy);
        return this;
    }

    /**
     * Indicate the task has failed. {@link Policy#onFail()} will be triggered once the execution
     * ends. This mechanism is used by policies for actions such as determining whether to schedule
     * a retry. Must be call inside {@link #onExecuteInBackgroundThread()}
     */
    @WorkerThread
    public void fail() {
        Assert.isNotMainThread();
        mHasFailed = true;
    }

    @MainThread
    public void setExecutionTime(long timeMillis) {
        Assert.isMainThread();
        mExecutionTime = timeMillis;
    }

    public long getTimeMillis() {
        return sClock.getTimeMillis();
    }

    /**
     * Creates an intent that can be used to restart the current task. Derived class should build
     * their intent upon this.
     */
    public Intent createRestartIntent() {
        return createIntent(getContext(), this.getClass(), mSubId);
    }

    /**
     * Creates an intent that can be used to start the {@link TaskSchedulerService}. Derived class
     * should build their intent upon this.
     */
    public static Intent createIntent(Context context, Class<? extends BaseTask> task, int subId) {
        Intent intent = TaskSchedulerService.createIntent(context, task);
        intent.putExtra(EXTRA_SUB_ID, subId);
        return intent;
    }

    @Override
    public TaskId getId() {
        return new TaskId(mId, mSubId);
    }

    @Override
    @CallSuper
    public void onCreate(Context context, Intent intent, int flags, int startId) {
        mContext = context;
        mSubId = intent.getIntExtra(EXTRA_SUB_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        for (Policy policy : mPolicies) {
            policy.onCreate(this, intent, flags, startId);
        }
    }

    @Override
    public long getReadyInMilliSeconds() {
        return mExecutionTime - getTimeMillis();
    }

    @Override
    @CallSuper
    public void onBeforeExecute() {
        for (Policy policy : mPolicies) {
            policy.onBeforeExecute();
        }
        mHasStarted = true;
    }

    @Override
    @CallSuper
    public void onCompleted() {
        if (mHasFailed) {
            for (Policy policy : mPolicies) {
                policy.onFail();
            }
        }

        for (Policy policy : mPolicies) {
            policy.onCompleted();
        }
    }

    @Override
    public void onDuplicatedTaskAdded(Task task) {
        for (Policy policy : mPolicies) {
            policy.onDuplicatedTaskAdded();
        }
    }

    @NeededForTesting
    static class Clock {

        public long getTimeMillis() {
            return SystemClock.elapsedRealtime();
        }
    }

    /**
     * Used to replace the clock with an deterministic clock
     */
    @NeededForTesting
    static void setClockForTesting(Clock clock) {
        sClock = clock;
    }
}
