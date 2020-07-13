/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.awt.*;

/**
 * A segment type describes the different roles or positions a segment can have in a node
 */
public enum SegmentType {
  /**
   * Segment is on start side (left if LTR, right if RTL).
   */
  @NonNull START,
  /**
   * Segment is on end side (right if LTR, left if RTL).
   */
  @NonNull END,
  /**
   * Segment is on the left edge
   */
  @NonNull LEFT,
  /**
   * Segment is on the top edge
   */
  @NonNull TOP,
  /**
   * Segment is on the right edge
   */
  @NonNull RIGHT,
  /**
   * Segment is on the bottom edge
   */
  @NonNull BOTTOM,
  /**
   * Segment is along the baseline
   */
  @NonNull BASELINE,
  /**
   * Segment is along the center vertically
   */
  @NonNull CENTER_VERTICAL,
  /**
   * Segment is along the center horizontally
   */
  @NonNull CENTER_HORIZONTAL,
  /**
   * Segment is on an unknown edge
   */
  @NonNull UNKNOWN;

  public boolean isHorizontal() {
    return this == TOP || this == BOTTOM || this == BASELINE || this == CENTER_HORIZONTAL;
  }

  /**
   * Returns the X coordinate for an edge of this type given its bounds
   *
   * @param node   the node containing the edge
   * @param bounds the bounds of the node
   * @return the X coordinate for an edge of this type given its bounds
   */
  public int getX(@NonNull TextDirection textDirection,
                  @SuppressWarnings("UnusedParameters") @Nullable NlComponent node,
                  @NonNull Rectangle bounds) {
    SegmentType me = this;
    switch(this) {
      case START:
        me = textDirection == TextDirection.RIGHT_TO_LEFT ? RIGHT : LEFT;
        break;
      case END:
        me = textDirection == TextDirection.RIGHT_TO_LEFT ? LEFT : RIGHT;
        break;
    }

    // We pass in the bounds rather than look it up via node.getBounds() because
    // during a resize or move operation, we call this method to look up proposed
    // bounds rather than actual bounds
    switch (me) {
      case RIGHT:
        return bounds.x + bounds.width;
      case TOP:
      case BOTTOM:
      case CENTER_VERTICAL:
        return bounds.x + bounds.width / 2;
      case UNKNOWN:
        assert false;
        return bounds.x;
      case LEFT:
      case BASELINE:
      default:
        return bounds.x;
    }
  }

  /**
   * Returns the Y coordinate for an edge of this type given its bounds
   *
   * @param node   the node containing the edge
   * @param bounds the bounds of the node
   * @return the Y coordinate for an edge of this type given its bounds
   */
  public int getY(@Nullable NlComponent node, @NonNull Rectangle bounds) {
    switch (this) {
      case TOP:
        return bounds.y;
      case BOTTOM:
        return bounds.y + bounds.height;
      case BASELINE: {
        int baseline = node != null ? node.getBaseline() : -1;
        if (node == null) {
          // This happens when you are dragging an element and we don't have
          // a node such as on a palette drag. For now just hack it.
          baseline = (int)(bounds.height * 0.8f); // HACK
        }
        return bounds.y + baseline;
      }
      case UNKNOWN:
        assert false;
        return bounds.y;
      case RIGHT:
      case LEFT:
      case CENTER_HORIZONTAL:
      default:
        return bounds.y + bounds.height / 2;
    }
  }

  @Override
  public String toString() {
    return name();
  }
}
