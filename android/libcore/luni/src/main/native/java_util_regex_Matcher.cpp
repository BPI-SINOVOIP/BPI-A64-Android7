/*
 * Copyright (C) 2010 The Android Open Source Project
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

#define LOG_TAG "Matcher"

#include <stdlib.h>

#include "IcuUtilities.h"
#include "JNIHelp.h"
#include "JniConstants.h"
#include "JniException.h"
#include "ScopedPrimitiveArray.h"
#include "jni.h"
#include "unicode/parseerr.h"
#include "unicode/regex.h"

// ICU documentation: http://icu-project.org/apiref/icu4c/classRegexMatcher.html

static icu::RegexMatcher* toRegexMatcher(jlong address) {
    return reinterpret_cast<icu::RegexMatcher*>(static_cast<uintptr_t>(address));
}

/**
 * We use ICU4C's RegexMatcher class, but our input is on the Java heap and potentially moving
 * around between calls. This wrapper class ensures that our RegexMatcher is always pointing at
 * the current location of the char[]. Earlier versions of Android simply copied the data to the
 * native heap, but that's wasteful and hides allocations from the garbage collector.
 */
class MatcherAccessor {
public:
    MatcherAccessor(JNIEnv* env, jlong address, jstring javaInput, bool reset) {
        init(env, address);

        mJavaInput = javaInput;
        mChars = env->GetStringChars(mJavaInput, NULL);
        if (mChars == NULL) {
            return;
        }

        mUText = utext_openUChars(NULL, mChars, env->GetStringLength(mJavaInput), &mStatus);
        if (mUText == NULL) {
            return;
        }

        if (reset) {
            mMatcher->reset(mUText);
        } else {
            mMatcher->refreshInputText(mUText, mStatus);
        }
    }

    MatcherAccessor(JNIEnv* env, jlong address) {
        init(env, address);
    }

    ~MatcherAccessor() {
        utext_close(mUText);
        if (mJavaInput) {
            mEnv->ReleaseStringChars(mJavaInput, mChars);
        }
        maybeThrowIcuException(mEnv, "utext_close", mStatus);
    }

    icu::RegexMatcher* operator->() {
        return mMatcher;
    }

    UErrorCode& status() {
        return mStatus;
    }

    void updateOffsets(jintArray javaOffsets) {
        ScopedIntArrayRW offsets(mEnv, javaOffsets);
        if (offsets.get() == NULL) {
            return;
        }

        for (size_t i = 0, groupCount = mMatcher->groupCount(); i <= groupCount; ++i) {
            offsets[2*i + 0] = mMatcher->start(i, mStatus);
            offsets[2*i + 1] = mMatcher->end(i, mStatus);
        }
    }

private:
    void init(JNIEnv* env, jlong address) {
        mEnv = env;
        mJavaInput = NULL;
        mMatcher = toRegexMatcher(address);
        mChars = NULL;
        mStatus = U_ZERO_ERROR;
        mUText = NULL;
    }

    JNIEnv* mEnv;
    jstring mJavaInput;
    icu::RegexMatcher* mMatcher;
    const jchar* mChars;
    UErrorCode mStatus;
    UText* mUText;

    // Disallow copy and assignment.
    MatcherAccessor(const MatcherAccessor&);
    void operator=(const MatcherAccessor&);
};

static void Matcher_free(void* address) {
    delete reinterpret_cast<icu::RegexMatcher*>(address);
}

static jlong Matcher_getNativeFinalizer(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(&Matcher_free);
}

// Return a guess of the amount of native memory to be deallocated by a typical call to
// Matcher_free().
static jint Matcher_nativeSize(JNIEnv*, jclass) {
    return 200;  // Very rough guess based on a quick look at the implementation.
}

static jint Matcher_findImpl(JNIEnv* env, jclass, jlong addr, jstring javaText, jint startIndex, jintArray offsets) {
    MatcherAccessor matcher(env, addr, javaText, false);
    UBool result = matcher->find(startIndex, matcher.status());
    if (result) {
        matcher.updateOffsets(offsets);
    }
    return result;
}

static jint Matcher_findNextImpl(JNIEnv* env, jclass, jlong addr, jstring javaText, jintArray offsets) {
    MatcherAccessor matcher(env, addr, javaText, false);
    if (matcher.status() != U_ZERO_ERROR) {
        return -1;
    }
    UBool result = matcher->find();
    if (result) {
        matcher.updateOffsets(offsets);
    }
    return result;
}

static jint Matcher_groupCountImpl(JNIEnv* env, jclass, jlong addr) {
    MatcherAccessor matcher(env, addr);
    return matcher->groupCount();
}

static jint Matcher_hitEndImpl(JNIEnv* env, jclass, jlong addr) {
    MatcherAccessor matcher(env, addr);
    return matcher->hitEnd();
}

static jint Matcher_lookingAtImpl(JNIEnv* env, jclass, jlong addr, jstring javaText, jintArray offsets) {
    MatcherAccessor matcher(env, addr, javaText, false);
    UBool result = matcher->lookingAt(matcher.status());
    if (result) {
        matcher.updateOffsets(offsets);
    }
    return result;
}

static jint Matcher_matchesImpl(JNIEnv* env, jclass, jlong addr, jstring javaText, jintArray offsets) {
    MatcherAccessor matcher(env, addr, javaText, false);
    UBool result = matcher->matches(matcher.status());
    if (result) {
        matcher.updateOffsets(offsets);
    }
    return result;
}

static jlong Matcher_openImpl(JNIEnv* env, jclass, jlong patternAddr) {
    icu::RegexPattern* pattern = reinterpret_cast<icu::RegexPattern*>(static_cast<uintptr_t>(patternAddr));
    UErrorCode status = U_ZERO_ERROR;
    icu::RegexMatcher* result = pattern->matcher(status);
    maybeThrowIcuException(env, "RegexPattern::matcher", status);
    return reinterpret_cast<uintptr_t>(result);
}

static jint Matcher_requireEndImpl(JNIEnv* env, jclass, jlong addr) {
    MatcherAccessor matcher(env, addr);
    return matcher->requireEnd();
}

static void Matcher_setInputImpl(JNIEnv* env, jclass, jlong addr, jstring javaText, jint start, jint end) {
    MatcherAccessor matcher(env, addr, javaText, true);
    matcher->region(start, end, matcher.status());
}

static void Matcher_useAnchoringBoundsImpl(JNIEnv* env, jclass, jlong addr, jboolean value) {
    MatcherAccessor matcher(env, addr);
    matcher->useAnchoringBounds(value);
}

static void Matcher_useTransparentBoundsImpl(JNIEnv* env, jclass, jlong addr, jboolean value) {
    MatcherAccessor matcher(env, addr);
    matcher->useTransparentBounds(value);
}

static JNINativeMethod gMethods[] = {
    NATIVE_METHOD(Matcher, findImpl, "(JLjava/lang/String;I[I)Z"),
    NATIVE_METHOD(Matcher, findNextImpl, "(JLjava/lang/String;[I)Z"),
    NATIVE_METHOD(Matcher, getNativeFinalizer, "()J"),
    NATIVE_METHOD(Matcher, groupCountImpl, "(J)I"),
    NATIVE_METHOD(Matcher, hitEndImpl, "(J)Z"),
    NATIVE_METHOD(Matcher, lookingAtImpl, "(JLjava/lang/String;[I)Z"),
    NATIVE_METHOD(Matcher, matchesImpl, "(JLjava/lang/String;[I)Z"),
    NATIVE_METHOD(Matcher, nativeSize, "()I"),
    NATIVE_METHOD(Matcher, openImpl, "(J)J"),
    NATIVE_METHOD(Matcher, requireEndImpl, "(J)Z"),
    NATIVE_METHOD(Matcher, setInputImpl, "(JLjava/lang/String;II)V"),
    NATIVE_METHOD(Matcher, useAnchoringBoundsImpl, "(JZ)V"),
    NATIVE_METHOD(Matcher, useTransparentBoundsImpl, "(JZ)V"),
};
void register_java_util_regex_Matcher(JNIEnv* env) {
    jniRegisterNativeMethods(env, "java/util/regex/Matcher", gMethods, NELEM(gMethods));
}
