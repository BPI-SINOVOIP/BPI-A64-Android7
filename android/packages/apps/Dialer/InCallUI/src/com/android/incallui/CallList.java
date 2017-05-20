/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui;

import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;

import com.android.contacts.common.testing.NeededForTesting;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.logging.Logger;
import com.android.incallui.util.TelecomCallUtil;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Maintains the list of active calls and notifies interested classes of changes to the call list
 * as they are received from the telephony stack. Primary listener of changes to this class is
 * InCallPresenter.
 */
public class CallList {

    private static final int DISCONNECTED_CALL_SHORT_TIMEOUT_MS = 200;
    private static final int DISCONNECTED_CALL_MEDIUM_TIMEOUT_MS = 2000;
    private static final int DISCONNECTED_CALL_LONG_TIMEOUT_MS = 5000;

    private static final int EVENT_DISCONNECTED_TIMEOUT = 1;
    private static final long BLOCK_QUERY_TIMEOUT_MS = 1000;

    private static CallList sInstance = new CallList();

    private final HashMap<String, Call> mCallById = new HashMap<>();
    private final HashMap<android.telecom.Call, Call> mCallByTelecomCall = new HashMap<>();
    private final HashMap<String, List<String>> mCallTextReponsesMap = Maps.newHashMap();
    /**
     * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is
     * load factor before resizing, 1 means we only expect a single thread to
     * access the map so make only a single shard
     */
    private final Set<Listener> mListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<Listener, Boolean>(8, 0.9f, 1));
    private final HashMap<String, List<CallUpdateListener>> mCallUpdateListenerMap = Maps
            .newHashMap();
    private final Set<Call> mPendingDisconnectCalls = Collections.newSetFromMap(
            new ConcurrentHashMap<Call, Boolean>(8, 0.9f, 1));
    private FilteredNumberAsyncQueryHandler mFilteredQueryHandler;

    /**
     * Static singleton accessor method.
     */
    public static CallList getInstance() {
        return sInstance;
    }

    /**
     * USED ONLY FOR TESTING
     * Testing-only constructor.  Instance should only be acquired through getInstance().
     */
    @NeededForTesting
    CallList() {
    }

    public void onCallAdded(final android.telecom.Call telecomCall) {
        Trace.beginSection("onCallAdded");
        final Call call = new Call(telecomCall);
        Log.d(this, "onCallAdded: callState=" + call.getState());

        if (call.getState() == Call.State.INCOMING ||
                call.getState() == Call.State.CALL_WAITING) {
            onIncoming(call, call.getCannedSmsResponses());
        } else {
            onUpdate(call);
        }

        call.logCallInitiationType();
        Trace.endSection();
    }

    public void onCallRemoved(android.telecom.Call telecomCall) {
        if (mCallByTelecomCall.containsKey(telecomCall)) {
            Call call = mCallByTelecomCall.get(telecomCall);
            Logger.logCall(call);
            if (updateCallInMap(call)) {
                Log.w(this, "Removing call not previously disconnected " + call.getId());
            }
            updateCallTextMap(call, null);
        }
    }

    /**
     * Called when a single call disconnects.
     */
    public void onDisconnect(Call call) {
        if (updateCallInMap(call)) {
            Log.i(this, "onDisconnect: " + call);
            // notify those listening for changes on this specific change
            notifyCallUpdateListeners(call);
            // notify those listening for all disconnects
            notifyListenersOfDisconnect(call);
        }
    }

    /**
     * Called when a single call has changed.
     */
    public void onIncoming(Call call, List<String> textMessages) {
        if (updateCallInMap(call)) {
            Log.i(this, "onIncoming - " + call);
        }
        updateCallTextMap(call, textMessages);

        for (Listener listener : mListeners) {
            listener.onIncomingCall(call);
        }
    }

    public void onUpgradeToVideo(Call call){
        Log.d(this, "onUpgradeToVideo call=" + call);
        for (Listener listener : mListeners) {
            listener.onUpgradeToVideo(call);
        }
    }
    /**
     * Called when a single call has changed.
     */
    public void onUpdate(Call call) {
        Trace.beginSection("onUpdate");
        onUpdateCall(call);
        notifyGenericListeners();
        Trace.endSection();
    }

    /**
     * Called when a single call has changed session modification state.
     *
     * @param call The call.
     * @param sessionModificationState The new session modification state.
     */
    public void onSessionModificationStateChange(Call call, int sessionModificationState) {
        final List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(call.getId());
        if (listeners != null) {
            for (CallUpdateListener listener : listeners) {
                listener.onSessionModificationStateChange(sessionModificationState);
            }
        }
    }

    /**
     * Called when the last forwarded number changes for a call.  With IMS, the last forwarded
     * number changes due to a supplemental service notification, so it is not pressent at the
     * start of the call.
     *
     * @param call The call.
     */
    public void onLastForwardedNumberChange(Call call) {
        final List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(call.getId());
        if (listeners != null) {
            for (CallUpdateListener listener : listeners) {
                listener.onLastForwardedNumberChange();
            }
        }
    }

    /**
     * Called when the child number changes for a call.  The child number can be received after a
     * call is initially set up, so we need to be able to inform listeners of the change.
     *
     * @param call The call.
     */
    public void onChildNumberChange(Call call) {
        final List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(call.getId());
        if (listeners != null) {
            for (CallUpdateListener listener : listeners) {
                listener.onChildNumberChange();
            }
        }
    }

    public void notifyCallUpdateListeners(Call call) {
        final List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(call.getId());
        if (listeners != null) {
            for (CallUpdateListener listener : listeners) {
                listener.onCallChanged(call);
            }
        }
    }

    /**
     * Add a call update listener for a call id.
     *
     * @param callId The call id to get updates for.
     * @param listener The listener to add.
     */
    public void addCallUpdateListener(String callId, CallUpdateListener listener) {
        List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(callId);
        if (listeners == null) {
            listeners = new CopyOnWriteArrayList<CallUpdateListener>();
            mCallUpdateListenerMap.put(callId, listeners);
        }
        listeners.add(listener);
    }

    /**
     * Remove a call update listener for a call id.
     *
     * @param callId The call id to remove the listener for.
     * @param listener The listener to remove.
     */
    public void removeCallUpdateListener(String callId, CallUpdateListener listener) {
        List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(callId);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public void addListener(Listener listener) {
        Preconditions.checkNotNull(listener);

        mListeners.add(listener);

        // Let the listener know about the active calls immediately.
        listener.onCallListChange(this);
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    /**
     * TODO: Change so that this function is not needed. Instead of assuming there is an active
     * call, the code should rely on the status of a specific Call and allow the presenters to
     * update the Call object when the active call changes.
     */
    public Call getIncomingOrActive() {
        Call retval = getIncomingCall();
        if (retval == null) {
            retval = getActiveCall();
        }
        return retval;
    }

    public Call getOutgoingOrActive() {
        Call retval = getOutgoingCall();
        if (retval == null) {
            retval = getActiveCall();
        }
        return retval;
    }

    /**
     * A call that is waiting for {@link PhoneAccount} selection
     */
    public Call getWaitingForAccountCall() {
        return getFirstCallWithState(Call.State.SELECT_PHONE_ACCOUNT);
    }

    public Call getPendingOutgoingCall() {
        return getFirstCallWithState(Call.State.CONNECTING);
    }

    public Call getOutgoingCall() {
        Call call = getFirstCallWithState(Call.State.DIALING);
        if (call == null) {
            call = getFirstCallWithState(Call.State.REDIALING);
        }
        return call;
    }

    public Call getActiveCall() {
        return getFirstCallWithState(Call.State.ACTIVE);
    }

    public Call getSecondActiveCall() {
        return getCallWithState(Call.State.ACTIVE, 1);
    }

    public Call getBackgroundCall() {
        return getFirstCallWithState(Call.State.ONHOLD);
    }

    public Call getDisconnectedCall() {
        return getFirstCallWithState(Call.State.DISCONNECTED);
    }

    public Call getDisconnectingCall() {
        return getFirstCallWithState(Call.State.DISCONNECTING);
    }

    public Call getSecondBackgroundCall() {
        return getCallWithState(Call.State.ONHOLD, 1);
    }

    public Call getActiveOrBackgroundCall() {
        Call call = getActiveCall();
        if (call == null) {
            call = getBackgroundCall();
        }
        return call;
    }

    public Call getIncomingCall() {
        Call call = getFirstCallWithState(Call.State.INCOMING);
        if (call == null) {
            call = getFirstCallWithState(Call.State.CALL_WAITING);
        }

        return call;
    }

    public Call getFirstCall() {
        Call result = getIncomingCall();
        if (result == null) {
            result = getPendingOutgoingCall();
        }
        if (result == null) {
            result = getOutgoingCall();
        }
        if (result == null) {
            result = getFirstCallWithState(Call.State.ACTIVE);
        }
        if (result == null) {
            result = getDisconnectingCall();
        }
        if (result == null) {
            result = getDisconnectedCall();
        }
        return result;
    }

    public boolean hasLiveCall() {
        Call call = getFirstCall();
        if (call == null) {
            return false;
        }
        return call != getDisconnectingCall() && call != getDisconnectedCall();
    }

    /**
     * Returns the first call found in the call map with the specified call modification state.
     * @param state The session modification state to search for.
     * @return The first call with the specified state.
     */
    public Call getVideoUpgradeRequestCall() {
        for(Call call : mCallById.values()) {
            if (call.getSessionModificationState() ==
                    Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
                return call;
            }
        }
        return null;
    }

    public Call getCallById(String callId) {
        return mCallById.get(callId);
    }

    public Call getCallByTelecomCall(android.telecom.Call telecomCall) {
        return mCallByTelecomCall.get(telecomCall);
    }

    public List<String> getTextResponses(String callId) {
        return mCallTextReponsesMap.get(callId);
    }

    /**
     * Returns first call found in the call map with the specified state.
     */
    public Call getFirstCallWithState(int state) {
        return getCallWithState(state, 0);
    }

    /**
     * Returns the [position]th call found in the call map with the specified state.
     * TODO: Improve this logic to sort by call time.
     */
    public Call getCallWithState(int state, int positionToFind) {
        Call retval = null;
        int position = 0;
        for (Call call : mCallById.values()) {
            if (call.getState() == state) {
                if (position >= positionToFind) {
                    retval = call;
                    break;
                } else {
                    position++;
                }
            }
        }

        return retval;
    }

    /**
     * This is called when the service disconnects, either expectedly or unexpectedly.
     * For the expected case, it's because we have no calls left.  For the unexpected case,
     * it is likely a crash of phone and we need to clean up our calls manually.  Without phone,
     * there can be no active calls, so this is relatively safe thing to do.
     */
    public void clearOnDisconnect() {
        for (Call call : mCallById.values()) {
            final int state = call.getState();
            if (state != Call.State.IDLE &&
                    state != Call.State.INVALID &&
                    state != Call.State.DISCONNECTED) {

                call.setState(Call.State.DISCONNECTED);
                call.setDisconnectCause(new DisconnectCause(DisconnectCause.UNKNOWN));
                updateCallInMap(call);
            }
        }
        notifyGenericListeners();
    }

    /**
     * Called when the user has dismissed an error dialog. This indicates acknowledgement of
     * the disconnect cause, and that any pending disconnects should immediately occur.
     */
    public void onErrorDialogDismissed() {
        final Iterator<Call> iterator = mPendingDisconnectCalls.iterator();
        while (iterator.hasNext()) {
            Call call = iterator.next();
            iterator.remove();
            finishDisconnectedCall(call);
        }
    }

    /**
     * Processes an update for a single call.
     *
     * @param call The call to update.
     */
    private void onUpdateCall(Call call) {
        Log.d(this, "\t" + call);
        if (updateCallInMap(call)) {
            Log.i(this, "onUpdate - " + call);
        }
        updateCallTextMap(call, call.getCannedSmsResponses());
        notifyCallUpdateListeners(call);
    }

    /**
     * Sends a generic notification to all listeners that something has changed.
     * It is up to the listeners to call back to determine what changed.
     */
    private void notifyGenericListeners() {
        for (Listener listener : mListeners) {
            listener.onCallListChange(this);
        }
    }

    private void notifyListenersOfDisconnect(Call call) {
        for (Listener listener : mListeners) {
            listener.onDisconnect(call);
        }
    }

    /**
     * Updates the call entry in the local map.
     * @return false if no call previously existed and no call was added, otherwise true.
     */
    private boolean updateCallInMap(Call call) {
        Preconditions.checkNotNull(call);

        boolean updated = false;

        if (call.getState() == Call.State.DISCONNECTED) {
            // update existing (but do not add!!) disconnected calls
            if (mCallById.containsKey(call.getId())) {
                // For disconnected calls, we want to keep them alive for a few seconds so that the
                // UI has a chance to display anything it needs when a call is disconnected.

                // Set up a timer to destroy the call after X seconds.
                final Message msg = mHandler.obtainMessage(EVENT_DISCONNECTED_TIMEOUT, call);
                mHandler.sendMessageDelayed(msg, getDelayForDisconnect(call));
                mPendingDisconnectCalls.add(call);

                mCallById.put(call.getId(), call);
                mCallByTelecomCall.put(call.getTelecomCall(), call);
                updated = true;
            }
        } else if (!isCallDead(call)) {
            mCallById.put(call.getId(), call);
            mCallByTelecomCall.put(call.getTelecomCall(), call);
            updated = true;
        } else if (mCallById.containsKey(call.getId())) {
            mCallById.remove(call.getId());
            mCallByTelecomCall.remove(call.getTelecomCall());
            updated = true;
        }

        return updated;
    }

    private int getDelayForDisconnect(Call call) {
        Preconditions.checkState(call.getState() == Call.State.DISCONNECTED);


        final int cause = call.getDisconnectCause().getCode();
        final int delay;
        switch (cause) {
            case DisconnectCause.LOCAL:
                delay = DISCONNECTED_CALL_SHORT_TIMEOUT_MS;
                break;
            case DisconnectCause.REMOTE:
            case DisconnectCause.ERROR:
                delay = DISCONNECTED_CALL_MEDIUM_TIMEOUT_MS;
                break;
            case DisconnectCause.REJECTED:
            case DisconnectCause.MISSED:
            case DisconnectCause.CANCELED:
                // no delay for missed/rejected incoming calls and canceled outgoing calls.
                delay = 0;
                break;
            default:
                delay = DISCONNECTED_CALL_LONG_TIMEOUT_MS;
                break;
        }

        return delay;
    }

    private void updateCallTextMap(Call call, List<String> textResponses) {
        Preconditions.checkNotNull(call);

        if (!isCallDead(call)) {
            if (textResponses != null) {
                mCallTextReponsesMap.put(call.getId(), textResponses);
            }
        } else if (mCallById.containsKey(call.getId())) {
            mCallTextReponsesMap.remove(call.getId());
        }
    }

    private boolean isCallDead(Call call) {
        final int state = call.getState();
        return Call.State.IDLE == state || Call.State.INVALID == state;
    }

    /**
     * Sets up a call for deletion and notifies listeners of change.
     */
    private void finishDisconnectedCall(Call call) {
        if (mPendingDisconnectCalls.contains(call)) {
            mPendingDisconnectCalls.remove(call);
        }
        call.setState(Call.State.IDLE);
        updateCallInMap(call);
        notifyGenericListeners();
    }

    /**
     * Notifies all video calls of a change in device orientation.
     *
     * @param rotation The new rotation angle (in degrees).
     */
    public void notifyCallsOfDeviceRotation(int rotation) {
        for (Call call : mCallById.values()) {
            // First, ensure that the call videoState has video enabled (there is no need to set
            // device orientation on a voice call which has not yet been upgraded to video).
            // Second, ensure a VideoCall is set on the call so that the change can be sent to the
            // provider (a VideoCall can be present for a call that does not currently have video,
            // but can be upgraded to video).

            // NOTE: is it necessary to use this order because getVideoCall references the class
            // VideoProfile which is not available on APIs <23 (M).
            if (VideoUtils.isVideoCall(call) && call.getVideoCall() != null) {
                call.getVideoCall().setDeviceOrientation(rotation);
            }
        }
    }

    /**
     * Handles the timeout for destroying disconnected calls.
     */
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_DISCONNECTED_TIMEOUT:
                    Log.d(this, "EVENT_DISCONNECTED_TIMEOUT ", msg.obj);
                    finishDisconnectedCall((Call) msg.obj);
                    break;
                default:
                    Log.wtf(this, "Message not expected: " + msg.what);
                    break;
            }
        }
    };

    public void setFilteredNumberQueryHandler(FilteredNumberAsyncQueryHandler handler) {
        mFilteredQueryHandler = handler;
    }

    /**
     * Listener interface for any class that wants to be notified of changes
     * to the call list.
     */
    public interface Listener {
        /**
         * Called when a new incoming call comes in.
         * This is the only method that gets called for incoming calls. Listeners
         * that want to perform an action on incoming call should respond in this method
         * because {@link #onCallListChange} does not automatically get called for
         * incoming calls.
         */
        public void onIncomingCall(Call call);
        /**
         * Called when a new modify call request comes in
         * This is the only method that gets called for modify requests.
         */
        public void onUpgradeToVideo(Call call);
        /**
         * Called anytime there are changes to the call list.  The change can be switching call
         * states, updating information, etc. This method will NOT be called for new incoming
         * calls and for calls that switch to disconnected state. Listeners must add actions
         * to those method implementations if they want to deal with those actions.
         */
        public void onCallListChange(CallList callList);

        /**
         * Called when a call switches to the disconnected state.  This is the only method
         * that will get called upon disconnection.
         */
        public void onDisconnect(Call call);


    }

    public interface CallUpdateListener {
        // TODO: refactor and limit arg to be call state.  Caller info is not needed.
        public void onCallChanged(Call call);

        /**
         * Notifies of a change to the session modification state for a call.
         *
         * @param sessionModificationState The new session modification state.
         */
        public void onSessionModificationStateChange(int sessionModificationState);

        /**
         * Notifies of a change to the last forwarded number for a call.
         */
        public void onLastForwardedNumberChange();

        /**
         * Notifies of a change to the child number for a call.
         */
        public void onChildNumberChange();
    }
}
