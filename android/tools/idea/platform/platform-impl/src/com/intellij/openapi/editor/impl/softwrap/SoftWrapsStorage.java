/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.ex.SoftWrapChangeListener;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds registered soft wraps and provides monitoring and management facilities for them.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jun 29, 2010 3:04:20 PM
 */
public class SoftWrapsStorage {

  private final List<SoftWrapImpl>        myWraps     = new ArrayList<SoftWrapImpl>();
  private final List<SoftWrapImpl>        myWrapsView = Collections.unmodifiableList(myWraps);
  private final List<SoftWrapChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  /**
   * @return    <code>true</code> if there is at least one soft wrap registered at the current storage; <code>false</code> otherwise
   */
  public boolean isEmpty() {
    return myWraps.isEmpty();
  }

  @Nullable
  public SoftWrap getSoftWrap(int offset) {
    int i = getSoftWrapIndex(offset);
    return i >= 0 ? myWraps.get(i) : null;
  }

  /**
   * @return    view for registered soft wraps sorted by offset in ascending order if any; empty collection otherwise
   */
  @NotNull
  public List<SoftWrapImpl> getSoftWraps() {
    return myWrapsView;
  }

  /**
   * Tries to find index of the target soft wrap stored at {@link #myWraps} collection. <code>'Target'</code> soft wrap is the one
   * that starts at the given offset.
   *
   * @param offset    target offset
   * @return          index that conforms to {@link Collections#binarySearch(List, Object)} contract, i.e. non-negative returned
   *                  index points to soft wrap that starts at the given offset; <code>'-(negative value) - 1'</code> points
   *                  to position at {@link #myWraps} collection where soft wrap for the given index should be inserted
   */
  public int getSoftWrapIndex(int offset) {
    int start = 0;
    int end = myWraps.size() - 1;

    // We use custom inline implementation of binary search here because profiling shows that standard Collections.binarySearch()
    // is a bottleneck. The most probable reason is a big number of interface calls.
    while (start <= end) {
      int i = (start + end) >>> 1;
      SoftWrap softWrap = myWraps.get(i);
      int softWrapOffset = softWrap.getStart();
      if (softWrapOffset > offset) {
        end = i - 1;
      }
      else if (softWrapOffset < offset) {
        start = i + 1;
      }
      else {
        return i;
      }
    }
    return -(start + 1);
  }

  /**
   * Allows to answer how many soft wraps which {@link TextChange#getStart() start offsets} belong to given
   * <code>[start; end]</code> interval are registered withing the current storage.
   * 
   * @param startOffset   target start offset (inclusive)
   * @param endOffset     target end offset (inclusive)
   * @return              number of soft wraps which {@link TextChange#getStart() start offsets} belong to the target range
   */
  public int getNumberOfSoftWrapsInRange(int startOffset, int endOffset) {
    int startIndex = getSoftWrapIndex(startOffset);
    if (startIndex < 0) {
      startIndex = -startIndex - 1;
    }

    if (startIndex >= myWraps.size()) {
      return 0;
    }
    int result = 0;
    int endIndex = startIndex;
    for (; endIndex < myWraps.size(); endIndex++) {
      SoftWrap softWrap = myWraps.get(endIndex);
      if (softWrap.getStart() > endOffset) {
        break;
      }
      result++;
    }
    return result;
  }
  
  /**
   * Inserts given soft wrap to {@link #myWraps} collection at the given index.
   *
   * @param softWrap          soft wrap to store
   * @param notifyListeners   flag that indicates if registered listeners should be notified about soft wrap registration
   * @return                  previous soft wrap object stored for the same offset if any; <code>null</code> otherwise
   */
  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  @Nullable
  public SoftWrap storeOrReplace(SoftWrapImpl softWrap, boolean notifyListeners) {
    int i = getSoftWrapIndex(softWrap.getStart());
    if (i >= 0) {
      return myWraps.set(i, softWrap);
    }

    i = -i - 1;
    myWraps.add(i, softWrap);
    if (notifyListeners) {
      // Use explicit loop as profiling shows that iterator-based processing is quite slow.
      for (int j = 0; j < myListeners.size(); j++) {
        SoftWrapChangeListener listener = myListeners.get(j);
        listener.softWrapAdded(softWrap);
      }
    }
    return null;
  }

  /**
   * Allows to remove all soft wraps registered at the current storage with offsets from <code>[start; end)</code> range if any.
   *
   * @param startOffset   start offset to use (inclusive)
   * @param endOffset     end offset to use (exclusive)
   */
  public void removeInRange(int startOffset, int endOffset) {
    //CachingSoftWrapDataMapper.log(String.format("xxxxxxxxxx SoftWrapsStorage.removeInRange(%d, %d). Current number: %d", startOffset, endOffset, myWraps.size()));
    int startIndex = getSoftWrapIndex(startOffset);
    if (startIndex < 0) {
      startIndex = -startIndex - 1;
    }

    if (startIndex >= myWraps.size()) {
      return;
    }

    int endIndex = startIndex;
    for (; endIndex < myWraps.size(); endIndex++) {
      SoftWrap softWrap = myWraps.get(endIndex);
      if (softWrap.getStart() >= endOffset) {
        break;
      }
    }

    if (endIndex > startIndex) {
      myWraps.subList(startIndex, endIndex).clear();
      notifyListenersAboutRemoval();
    }
    
    //CachingSoftWrapDataMapper.log(String.format("xxxxxxxxxx SoftWrapsStorage.removeInRange(%d, %d). Remaining: %d", startOffset, endOffset, myWraps.size()));
  }

  /**
   * Removes all soft wraps registered at the current storage.
   */
  public void removeAll() {
    myWraps.clear();
    notifyListenersAboutRemoval();
  }

  /**
   * Registers given listener within the current model
   *
   * @param listener    listener to register
   * @return            <code>true</code> if given listener was not registered before; <code>false</code> otherwise
   */
  public boolean addSoftWrapChangeListener(@NotNull SoftWrapChangeListener listener) {
    return myListeners.add(listener);
  }

  private void notifyListenersAboutRemoval() {
    for (SoftWrapChangeListener listener : myListeners) {
      listener.softWrapsRemoved();
    }
  }
}
