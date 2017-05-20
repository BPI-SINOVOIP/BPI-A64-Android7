/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.cts.verifier.notifications;

import static com.android.cts.verifier.notifications.MockListener.JSON_AMBIENT;
import static com.android.cts.verifier.notifications.MockListener.JSON_MATCHES_ZEN_FILTER;
import static com.android.cts.verifier.notifications.MockListener.JSON_TAG;

import android.app.Notification;
import android.content.ContentProviderOperation;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.service.notification.NotificationListenerService;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import com.android.cts.verifier.R;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AttentionManagementVerifierActivity
        extends InteractiveVerifierActivity {
    private static final String TAG = "NoListenerAttentionVerifier";

    private static final String ALICE = "Alice";
    private static final String ALICE_PHONE = "+16175551212";
    private static final String ALICE_EMAIL = "alice@_foo._bar";
    private static final String BOB = "Bob";
    private static final String BOB_PHONE = "+16505551212";;
    private static final String BOB_EMAIL = "bob@_foo._bar";
    private static final String CHARLIE = "Charlie";
    private static final String CHARLIE_PHONE = "+13305551212";
    private static final String CHARLIE_EMAIL = "charlie@_foo._bar";
    private static final int MODE_NONE = 0;
    private static final int MODE_URI = 1;
    private static final int MODE_PHONE = 2;
    private static final int MODE_EMAIL = 3;
    private static final int SEND_A = 0x1;
    private static final int SEND_B = 0x2;
    private static final int SEND_C = 0x4;
    private static final int SEND_ALL = SEND_A | SEND_B | SEND_C;


    private Uri mAliceUri;
    private Uri mBobUri;
    private Uri mCharlieUri;

    @Override
    int getTitleResource() {
        return R.string.attention_test;
    }

    @Override
    int getInstructionsResource() {
        return R.string.attention_info;
    }

    // Test Setup

    @Override
    protected List<InteractiveTestCase> createTestItems() {
        List<InteractiveTestCase> tests = new ArrayList<>(17);
        tests.add(new IsEnabledTest());
        tests.add(new ServiceStartedTest());
        tests.add(new InsertContactsTest());
        tests.add(new SetModeNoneTest());
        tests.add(new NoneInterceptsAllTest());
        tests.add(new SetModeAllTest());
        tests.add(new SetModePriorityTest());
        tests.add(new PriorityInterceptsSomeTest());
        tests.add(new SetModeAllTest());
        tests.add(new AllInterceptsNothingTest());
        tests.add(new DefaultOrderTest());
        tests.add(new PrioritytOrderTest());
        tests.add(new InterruptionOrderTest());
        tests.add(new AmbientBitsTest());
        tests.add(new LookupUriOrderTest());
        tests.add(new EmailOrderTest());
        tests.add(new PhoneOrderTest());
        tests.add(new DeleteContactsTest());
        return tests;
    }

    // Tests

    protected class InsertContactsTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.attention_create_contacts);
        }

        @Override
        void setUp() {
            insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, true);
            insertSingleContact(BOB, BOB_PHONE, BOB_EMAIL, false);
            // charlie is not in contacts
            status = READY;
            // wait for insertions to move through the system
            delay();
        }

        @Override
        void test() {
            mAliceUri = lookupContact(ALICE_PHONE);
            mBobUri = lookupContact(BOB_PHONE);
            mCharlieUri = lookupContact(CHARLIE_PHONE);

            status = PASS;
            if (mAliceUri == null) { status = FAIL; }
            if (mBobUri == null) { status = FAIL; }
            if (mCharlieUri != null) { status = FAIL; }

            if (status == PASS && !isStarred(mAliceUri)) {
                status = RETEST;
                Log.i("InsertContactsTest", "Alice is not yet starred");
                delay();
            } else {
                Log.i("InsertContactsTest", "Alice is: " + mAliceUri);
                Log.i("InsertContactsTest", "Bob is: " + mBobUri);
                Log.i("InsertContactsTest", "Charlie is: " + mCharlieUri);
                next();
            }
        }
    }

    protected class DeleteContactsTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.attention_delete_contacts);
        }

        @Override
        void test() {
            final ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
            operationList.add(ContentProviderOperation.newDelete(mAliceUri).build());
            operationList.add(ContentProviderOperation.newDelete(mBobUri).build());
            try {
                mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operationList);
                status = READY;
            } catch (RemoteException e) {
                Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                status = FAIL;
            } catch (OperationApplicationException e) {
                Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                status = FAIL;
            }
            status = PASS;
            next();
        }
    }

    protected class SetModeNoneTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createRetryItem(parent, R.string.attention_filter_none);
        }

        @Override
        void test() {
            MockListener.probeFilter(mContext,
                    new MockListener.IntegerResultCatcher() {
                        @Override
                        public void accept(int mode) {
                            if (mode == NotificationListenerService.INTERRUPTION_FILTER_NONE) {
                                status = PASS;
                                next();
                            } else {
                                Log.i("SetModeNoneTest", "waiting, current mode is: " + mode);
                                status = WAIT_FOR_USER;
                            }
                        }
                    });
        }

        @Override
        void tearDown() {
            mNm.cancelAll();
            MockListener.resetListenerData(mContext);
            delay();
        }
    }

    protected class NoneInterceptsAllTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.attention_all_are_filtered);
        }

        @Override
        void setUp() {
            sendNotifications(MODE_URI, false, false);
            status = READY;
            // wait for notifications to move through the system
            delay();
        }

        @Override
        void test() {
            MockListener.probeListenerPayloads(mContext,
                    new MockListener.StringListResultCatcher() {
                        @Override
                        public void accept(List<String> result) {
                            Set<String> found = new HashSet<String>();
                            if (result == null || result.size() == 0) {
                                status = FAIL;
                                next();
                                return;
                            }
                            boolean pass = true;
                            for (String payloadData : result) {
                                try {
                                    JSONObject payload = new JSONObject(payloadData);
                                    String tag = payload.getString(JSON_TAG);
                                    boolean zen = payload.getBoolean(JSON_MATCHES_ZEN_FILTER);
                                    Log.e(TAG, tag + (zen ? "" : " not") + " intercepted");
                                    if (found.contains(tag)) {
                                        // multiple entries for same notification!
                                        pass = false;
                                    } else if (ALICE.equals(tag)) {
                                        found.add(ALICE);
                                        pass &= !zen;
                                    } else if (BOB.equals(tag)) {
                                        found.add(BOB);
                                        pass &= !zen;
                                    } else if (CHARLIE.equals(tag)) {
                                        found.add(CHARLIE);
                                        pass &= !zen;
                                    }
                                } catch (JSONException e) {
                                    pass = false;
                                    Log.e(TAG, "failed to unpack data from mocklistener", e);
                                }
                            }
                            pass &= found.size() == 3;
                            status = pass ? PASS : FAIL;
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            mNm.cancelAll();
            MockListener.resetListenerData(mContext);
            delay();
        }

    }

    protected class SetModeAllTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createRetryItem(parent, R.string.attention_filter_all);
        }

        @Override
        void test() {
            MockListener.probeFilter(mContext,
                    new MockListener.IntegerResultCatcher() {
                        @Override
                        public void accept(int mode) {
                            if (mode == NotificationListenerService.INTERRUPTION_FILTER_ALL) {
                                status = PASS;
                                next();
                            } else {
                                Log.i("SetModeAllTest", "waiting, current mode is: " + mode);
                                status = WAIT_FOR_USER;
                            }
                        }
                    });
        }
    }

    protected class AllInterceptsNothingTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.attention_none_are_filtered);
        }

        @Override
        void setUp() {
            sendNotifications(MODE_URI, false, false);
            status = READY;
            // wait for notifications to move through the system
            delay();
        }

        @Override
        void test() {
            MockListener.probeListenerPayloads(mContext,
                    new MockListener.StringListResultCatcher() {
                        @Override
                        public void accept(List<String> result) {
                            Set<String> found = new HashSet<String>();
                            if (result == null || result.size() == 0) {
                                status = FAIL;
                                return;
                            }
                            boolean pass = true;
                            for (String payloadData : result) {
                                try {
                                    JSONObject payload = new JSONObject(payloadData);
                                    String tag = payload.getString(JSON_TAG);
                                    boolean zen = payload.getBoolean(JSON_MATCHES_ZEN_FILTER);
                                    Log.e(TAG, tag + (zen ? "" : " not") + " intercepted");
                                    if (found.contains(tag)) {
                                        // multiple entries for same notification!
                                        pass = false;
                                    } else if (ALICE.equals(tag)) {
                                        found.add(ALICE);
                                        pass &= zen;
                                    } else if (BOB.equals(tag)) {
                                        found.add(BOB);
                                        pass &= zen;
                                    } else if (CHARLIE.equals(tag)) {
                                        found.add(CHARLIE);
                                        pass &= zen;
                                    }
                                } catch (JSONException e) {
                                    pass = false;
                                    Log.e(TAG, "failed to unpack data from mocklistener", e);
                                }
                            }
                            pass &= found.size() == 3;
                            status = pass ? PASS : FAIL;
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            mNm.cancelAll();
            MockListener.resetListenerData(mContext);
            delay();
        }
    }

    protected class SetModePriorityTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createRetryItem(parent, R.string.attention_filter_priority);
        }

        @Override
        void test() {
            MockListener.probeFilter(mContext,
                    new MockListener.IntegerResultCatcher() {
                        @Override
                        public void accept(int mode) {
                            if (mode == NotificationListenerService.INTERRUPTION_FILTER_PRIORITY) {
                                status = PASS;
                                next();
                            } else {
                                Log.i("SetModePriorityTest", "waiting, current mode is: " + mode);
                                status = WAIT_FOR_USER;
                            }
                        }
                    });
        }
    }

    protected class PriorityInterceptsSomeTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.attention_some_are_filtered);
        }

        @Override
        void setUp() {
            sendNotifications(MODE_URI, false, false);
            status = READY;
            // wait for notifications to move through the system
            delay();
        }

        @Override
        void test() {
            MockListener.probeListenerPayloads(mContext,
                    new MockListener.StringListResultCatcher() {
                        @Override
                        public void accept(List<String> result) {
                            Set<String> found = new HashSet<String>();
                            if (result == null || result.size() == 0) {
                                status = FAIL;
                                return;
                            }
                            boolean pass = true;
                            for (String payloadData : result) {
                                try {
                                    JSONObject payload = new JSONObject(payloadData);
                                    String tag = payload.getString(JSON_TAG);
                                    boolean zen = payload.getBoolean(JSON_MATCHES_ZEN_FILTER);
                                    Log.e(TAG, tag + (zen ? "" : " not") + " intercepted");
                                    if (found.contains(tag)) {
                                        // multiple entries for same notification!
                                        pass = false;
                                    } else if (ALICE.equals(tag)) {
                                        found.add(ALICE);
                                        pass &= zen;
                                    } else if (BOB.equals(tag)) {
                                        found.add(BOB);
                                        pass &= !zen;
                                    } else if (CHARLIE.equals(tag)) {
                                        found.add(CHARLIE);
                                        pass &= !zen;
                                    }
                                } catch (JSONException e) {
                                    pass = false;
                                    Log.e(TAG, "failed to unpack data from mocklistener", e);
                                }
                            }
                            pass &= found.size() == 3;
                            status = pass ? PASS : FAIL;
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            mNm.cancelAll();
            MockListener.resetListenerData(mContext);
            delay();
        }
    }

    // ordered by time: C, B, A
    protected class DefaultOrderTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.attention_default_order);
        }

        @Override
        void setUp() {
            sendNotifications(MODE_NONE, false, false);
            status = READY;
            // wait for notifications to move through the system
            delay();
        }

        @Override
        void test() {
            MockListener.probeListenerOrder(mContext,
                    new MockListener.StringListResultCatcher() {
                        @Override
                        public void accept(List<String> orderedKeys) {
                            int rankA = findTagInKeys(ALICE, orderedKeys);
                            int rankB = findTagInKeys(BOB, orderedKeys);
                            int rankC = findTagInKeys(CHARLIE, orderedKeys);
                            if (rankC < rankB && rankB < rankA) {
                                status = PASS;
                            } else {
                                logFail(rankA + ", " + rankB + ", " + rankC);
                                status = FAIL;
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            mNm.cancelAll();
            MockListener.resetListenerData(mContext);
            delay();
        }
    }

    // ordered by priority: B, C, A
    protected class PrioritytOrderTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.attention_priority_order);
        }

        @Override
        void setUp() {
            sendNotifications(MODE_NONE, true, false);
            status = READY;
            // wait for notifications to move through the system
            delay();
        }

        @Override
        void test() {
            MockListener.probeListenerOrder(mContext,
                    new MockListener.StringListResultCatcher() {
                        @Override
                        public void accept(List<String> orderedKeys) {
                            int rankA = findTagInKeys(ALICE, orderedKeys);
                            int rankB = findTagInKeys(BOB, orderedKeys);
                            int rankC = findTagInKeys(CHARLIE, orderedKeys);
                            if (rankB < rankC && rankC < rankA) {
                                status = PASS;
                            } else {
                                logFail(rankA + ", " + rankB + ", " + rankC);
                                status = FAIL;
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            mNm.cancelAll();
            MockListener.resetListenerData(mContext);
            delay();
        }
    }

    // A starts at the top then falls to the bottom
    protected class InterruptionOrderTest extends InteractiveTestCase {
        boolean mSawElevation = false;

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.attention_interruption_order);
        }

        @Override
        void setUp() {
            // send B & C noisy
            sendNotifications(SEND_B | SEND_C, MODE_NONE, false, true);
            status = READY;
            // wait for then to not be recently noisy any more
            delay(15000);
        }

        @Override
        void test() {
            if (status == READY) {
                // send A noisy
                sendNotifications(SEND_A, MODE_NONE, false, true);
                status = RETEST;
                delay();
            } else if (status == RETEST) {
                MockListener.probeListenerOrder(mContext,
                        new MockListener.StringListResultCatcher() {
                            @Override
                            public void accept(List<String> orderedKeys) {
                                int rankA = findTagInKeys(ALICE, orderedKeys);
                                int rankB = findTagInKeys(BOB, orderedKeys);
                                int rankC = findTagInKeys(CHARLIE, orderedKeys);
                                if (!mSawElevation) {
                                    if (rankA < rankB && rankA < rankC) {
                                        mSawElevation = true;
                                        status = RETEST;
                                        delay(15000);
                                    } else {
                                        logFail("noisy notification did not sort to top.");
                                        status = FAIL;
                                        next();
                                    }
                                } else {
                                    if (rankA > rankB && rankA > rankC) {
                                        status = PASS;
                                    } else {
                                        logFail("noisy notification did not fade back into the list.");
                                        status = FAIL;
                                    }
                                }
                            }
                        });
                delay();  // in case the catcher never returns
           }
        }

        @Override
        void tearDown() {
            mNm.cancelAll();
            MockListener.resetListenerData(mContext);
            delay();
        }
    }

    // B & C above the fold, A below
    protected class AmbientBitsTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.attention_ambient_bit);
        }

        @Override
        void setUp() {
            sendNotifications(MODE_NONE, true, false);
            status = READY;
            // wait for notifications to move through the system
            delay();
        }

        @Override
        void test() {
            MockListener.probeListenerPayloads(mContext,
                    new MockListener.StringListResultCatcher() {
                        @Override
                        public void accept(List<String> result) {
                            Set<String> found = new HashSet<String>();
                            if (result == null || result.size() == 0) {
                                status = FAIL;
                                return;
                            }
                            boolean pass = true;
                            for (String payloadData : result) {
                                try {
                                    JSONObject payload = new JSONObject(payloadData);
                                    String tag = payload.getString(JSON_TAG);
                                    boolean ambient = payload.getBoolean(JSON_AMBIENT);
                                    Log.e(TAG, tag + (ambient ? " is" : " isn't") + " ambient");
                                    if (found.contains(tag)) {
                                        // multiple entries for same notification!
                                        pass = false;
                                    } else if (ALICE.equals(tag)) {
                                        found.add(ALICE);
                                        pass &= ambient;
                                    } else if (BOB.equals(tag)) {
                                        found.add(BOB);
                                        pass &= !ambient;
                                    } else if (CHARLIE.equals(tag)) {
                                        found.add(CHARLIE);
                                        pass &= !ambient;
                                    }
                                } catch (JSONException e) {
                                    pass = false;
                                    Log.e(TAG, "failed to unpack data from mocklistener", e);
                                }
                            }
                            pass &= found.size() == 3;
                            status = pass ? PASS : FAIL;
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            mNm.cancelAll();
            MockListener.resetListenerData(mContext);
            delay();
        }
    }

    // ordered by contact affinity: A, B, C
    protected class LookupUriOrderTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.attention_lookup_order);
        }

        @Override
        void setUp() {
            sendNotifications(MODE_URI, false, false);
            status = READY;
            // wait for notifications to move through the system
            delay();
        }

        @Override
        void test() {
            MockListener.probeListenerOrder(mContext,
                    new MockListener.StringListResultCatcher() {
                        @Override
                        public void accept(List<String> orderedKeys) {
                            int rankA = findTagInKeys(ALICE, orderedKeys);
                            int rankB = findTagInKeys(BOB, orderedKeys);
                            int rankC = findTagInKeys(CHARLIE, orderedKeys);
                            if (rankA < rankB && rankB < rankC) {
                                status = PASS;
                            } else {
                                logFail(rankA + ", " + rankB + ", " + rankC);
                                status = FAIL;
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            mNm.cancelAll();
            MockListener.resetListenerData(mContext);
            delay();
        }
    }

    // ordered by contact affinity: A, B, C
    protected class EmailOrderTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.attention_email_order);
        }

        @Override
        void setUp() {
            sendNotifications(MODE_EMAIL, false, false);
            status = READY;
            // wait for notifications to move through the system
            delay();
        }

        @Override
        void test() {
            MockListener.probeListenerOrder(mContext,
                    new MockListener.StringListResultCatcher() {
                        @Override
                        public void accept(List<String> orderedKeys) {
                            int rankA = findTagInKeys(ALICE, orderedKeys);
                            int rankB = findTagInKeys(BOB, orderedKeys);
                            int rankC = findTagInKeys(CHARLIE, orderedKeys);
                            if (rankA < rankB && rankB < rankC) {
                                status = PASS;
                            } else {
                                logFail(rankA + ", " + rankB + ", " + rankC);
                                status = FAIL;
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            mNm.cancelAll();
            MockListener.resetListenerData(mContext);
            delay();
        }
    }

    // ordered by contact affinity: A, B, C
    protected class PhoneOrderTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.attention_phone_order);
        }

        @Override
        void setUp() {
            sendNotifications(MODE_PHONE, false, false);
            status = READY;
            // wait for notifications to move through the system
            delay();
        }

        @Override
        void test() {
            MockListener.probeListenerOrder(mContext,
                    new MockListener.StringListResultCatcher() {
                        @Override
                        public void accept(List<String> orderedKeys) {
                            int rankA = findTagInKeys(ALICE, orderedKeys);
                            int rankB = findTagInKeys(BOB, orderedKeys);
                            int rankC = findTagInKeys(CHARLIE, orderedKeys);
                            if (rankA < rankB && rankB < rankC) {
                                status = PASS;
                            } else {
                                logFail(rankA + ", " + rankB + ", " + rankC);
                                status = FAIL;
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            mNm.cancelAll();
            MockListener.resetListenerData(mContext);
            delay();
        }
    }

    // Utilities

    // usePriorities true: B, C, A
    // usePriorities false:
    //   MODE_NONE: C, B, A
    //   otherwise: A, B ,C
    private void sendNotifications(int annotationMode, boolean uriMode, boolean noisy) {
        sendNotifications(SEND_ALL, annotationMode, uriMode, noisy);
    }

    private void sendNotifications(int which, int uriMode, boolean usePriorities, boolean noisy) {
        // C, B, A when sorted by time.  Times must be in the past
        long whenA = System.currentTimeMillis() - 4000000L;
        long whenB = System.currentTimeMillis() - 2000000L;
        long whenC = System.currentTimeMillis() - 1000000L;

        // B, C, A when sorted by priorities
        int priorityA = usePriorities ? Notification.PRIORITY_MIN : Notification.PRIORITY_DEFAULT;
        int priorityB = usePriorities ? Notification.PRIORITY_MAX : Notification.PRIORITY_DEFAULT;
        int priorityC = usePriorities ? Notification.PRIORITY_LOW : Notification.PRIORITY_DEFAULT;

        if ((which & SEND_B) != 0) {
            Notification.Builder bob = new Notification.Builder(mContext)
                    .setContentTitle(BOB)
                    .setContentText(BOB)
                    .setSmallIcon(R.drawable.ic_stat_bob)
                    .setPriority(priorityB)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setWhen(whenB);
            bob.setDefaults(noisy ? Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE : 0);
            addPerson(uriMode, bob, mBobUri, BOB_PHONE, BOB_EMAIL);
            mNm.notify(BOB, NOTIFICATION_ID + 2, bob.build());
        }
        if ((which & SEND_C) != 0) {
            Notification.Builder charlie = new Notification.Builder(mContext)
                    .setContentTitle(CHARLIE)
                    .setContentText(CHARLIE)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setPriority(priorityC)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setWhen(whenC);
            charlie.setDefaults(noisy ? Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE : 0);
            addPerson(uriMode, charlie, mCharlieUri, CHARLIE_PHONE, CHARLIE_EMAIL);
            mNm.notify(CHARLIE, NOTIFICATION_ID + 3, charlie.build());
        }
        if ((which & SEND_A) != 0) {
            Notification.Builder alice = new Notification.Builder(mContext)
                    .setContentTitle(ALICE)
                    .setContentText(ALICE)
                    .setSmallIcon(R.drawable.ic_stat_alice)
                    .setPriority(priorityA)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setWhen(whenA);
            alice.setDefaults(noisy ? Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE : 0);
            addPerson(uriMode, alice, mAliceUri, ALICE_PHONE, ALICE_EMAIL);
            mNm.notify(ALICE, NOTIFICATION_ID + 1, alice.build());
        }
    }

    private void addPerson(int mode, Notification.Builder note,
            Uri uri, String phone, String email) {
        if (mode == MODE_URI && uri != null) {
            note.addPerson(uri.toString());
        } else if (mode == MODE_PHONE) {
            note.addPerson(Uri.fromParts("tel", phone, null).toString());
        } else if (mode == MODE_EMAIL) {
            note.addPerson(Uri.fromParts("mailto", email, null).toString());
        }
    }

    private void insertSingleContact(String name, String phone, String email, boolean starred) {
        final ArrayList<ContentProviderOperation> operationList =
                new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder =
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI);
        builder.withValue(ContactsContract.RawContacts.STARRED, starred ? 1 : 0);
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, 0);
        builder.withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name);
        operationList.add(builder.build());

        if (phone != null) {
            builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
            builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
            builder.withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
            builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
            builder.withValue(Phone.NUMBER, phone);
            builder.withValue(ContactsContract.Data.IS_PRIMARY, 1);
            operationList.add(builder.build());
        }
        if (email != null) {
            builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
            builder.withValueBackReference(Email.RAW_CONTACT_ID, 0);
            builder.withValue(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
            builder.withValue(Email.TYPE, Email.TYPE_HOME);
            builder.withValue(Email.DATA, email);
            operationList.add(builder.build());
        }

        try {
            mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    private Uri lookupContact(String phone) {
        Cursor c = null;
        try {
            Uri phoneUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phone));
            String[] projection = new String[] { ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY };
            c = mContext.getContentResolver().query(phoneUri, projection, null, null, null);
            if (c != null && c.getCount() > 0) {
                c.moveToFirst();
                int lookupIdx = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY);
                int idIdx = c.getColumnIndex(ContactsContract.Contacts._ID);
                String lookupKey = c.getString(lookupIdx);
                long contactId = c.getLong(idIdx);
                return ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Problem getting content resolver or performing contacts query.", t);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    private boolean isStarred(Uri uri) {
        Cursor c = null;
        boolean starred = false;
        try {
            String[] projection = new String[] { ContactsContract.Contacts.STARRED };
            c = mContext.getContentResolver().query(uri, projection, null, null, null);
            if (c != null && c.getCount() > 0) {
                int starredIdx = c.getColumnIndex(ContactsContract.Contacts.STARRED);
                while (c.moveToNext()) {
                    starred |= c.getInt(starredIdx) == 1;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Problem getting content resolver or performing contacts query.", t);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return starred;
    }

    /** Search a list of notification keys for a givcen tag. */
    private int findTagInKeys(String tag, List<String> orderedKeys) {
        for (int i = 0; i < orderedKeys.size(); i++) {
            if (orderedKeys.get(i).contains(tag)) {
                return i;
            }
        }
        return -1;
    }
}
