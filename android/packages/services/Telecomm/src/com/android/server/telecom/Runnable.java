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

package com.android.server.telecom;

/**
 * Encapsulates session logging in a Runnable to reduce code duplication when continuing subsessions
 * in a handler/thread.
 */
public abstract class Runnable {

    private Session mSubsession;
    private final String mSubsessionName;
    private final Object mLock = new Object();
    private final java.lang.Runnable mRunnable = new java.lang.Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
                    try {
                        Log.continueSession(mSubsession, mSubsessionName);
                        loggedRun();
                    } finally {
                        if (mSubsession != null) {
                            Log.endSession();
                            mSubsession = null;
                        }
                    }
                }
            }
        };

    public Runnable(String subsessionName) {
        mSubsessionName = subsessionName;
    }

    /**
     * Return the runnable that will be canceled in the handler queue.
     * @return Runnable object to cancel.
     */
    public final java.lang.Runnable getRunnableToCancel() {
        return mRunnable;
    }

    /**
     * Creates a Runnable and a logging subsession that can be used in a handler/thread. Be sure to
     * call cancel() if this session is never going to be run (removed from a handler queue, for
     * for example).
     * @return A Java Runnable that can be used in a handler queue or thread.
     */
    public java.lang.Runnable prepare() {
        cancel();
        mSubsession = Log.createSubsession();
        return mRunnable;
    }

    /**
     * This method is used to clean up the active session if the Runnable gets removed from a
     * handler and is never run.
     */
    public void cancel() {
        synchronized (mLock) {
            Log.cancelSubsession(mSubsession);
            mSubsession = null;
        }
    }

    /**
     * The method that will be run in the handler/thread.
     */
    abstract public void loggedRun();

}
