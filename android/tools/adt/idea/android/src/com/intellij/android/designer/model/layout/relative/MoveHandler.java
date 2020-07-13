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
 * limitations under the License.
 */
package com.intellij.android.designer.model.layout.relative;

import com.android.tools.idea.designer.Segment;
import com.android.tools.idea.designer.SegmentType;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.relative.DependencyGraph.ViewData;
import com.intellij.designer.designSurface.OperationContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.designer.MarginType.NO_MARGIN;
import static com.intellij.android.designer.designSurface.graphics.DrawingStyle.MAX_MATCH_DISTANCE;
import static java.lang.Math.abs;

/**
 * A {@link MoveHandler} is a {@link GuidelineHandler} which handles move and drop
 * gestures, and offers guideline suggestions and snapping.
 * <p/>
 * Unlike the {@link ResizeHandler}, the {@link MoveHandler} looks for matches for all
 * different segment types -- the left edge, the right edge, the baseline, the center
 * edges, and so on -- and picks the best among these.
 */
public class MoveHandler extends GuidelineHandler {
  private int myDraggedBaseline;

  /**
   * Creates a new {@link MoveHandler}.
   *
   * @param layout   the layout element the handler is operating on
   * @param elements the elements being dragged in the move operation
   * @param context  the applicable {@link com.intellij.designer.designSurface.OperationContext}
   */
  public MoveHandler(@NotNull RadViewComponent layout, @NotNull List<RadViewComponent> elements, @NotNull OperationContext context) {
    super(layout, context);

    // Compute list of nodes being dragged within the layout, if any
    List<RadViewComponent> nodes = new ArrayList<RadViewComponent>();
    for (RadViewComponent element : elements) {
      ViewData view = myDependencyGraph.getView(element);
      if (view != null) {
        nodes.add(view.node);
      }
    }
    myDraggedNodes = nodes;

    myHorizontalDeps = myDependencyGraph.dependsOn(nodes, false /* verticalEdge */);
    myVerticalDeps = myDependencyGraph.dependsOn(nodes, true /* verticalEdge */);

    for (RadViewComponent child : RadViewComponent.getViewComponents(layout.getChildren())) {
      JComponent target = myContext.getArea().getFeedbackLayer();
      Rectangle bc = child.getBounds(target);
      if (!bc.isEmpty()) {
        boolean isDragged = myDraggedNodes.contains(child);
        if (!isDragged) {
          String id = child.getId();
          // It's okay for id to be null; if you apply a constraint
          // to a node with a missing id we will generate the id

          boolean addHorizontal = !myHorizontalDeps.contains(child);
          boolean addVertical = !myVerticalDeps.contains(child);

          addBounds(child, id, addHorizontal, addVertical, false /*includePadding*/);
          if (addHorizontal) {
            addBaseLine(child, id);
          }
        }
      }
    }

    String id = layout.getId();
    addBounds(layout, id, true, true, true /*includePadding*/);
    addCenter(layout, id);
  }

  @Override
  protected void snapVertical(Segment vEdge, int x, Rectangle newBounds) {
    int maxDistance = MAX_MATCH_DISTANCE;
    if (myTextDirection.isLeftSegment(vEdge.edgeType)) {
      int margin = !mySnap ? 0 : abs(newBounds.x - x);
      if (margin > maxDistance) {
        myLeftMargin = margin;
      }
      else {
        newBounds.x = x;
      }
    }
    else if (myTextDirection.isRightSegment(vEdge.edgeType)) {
      int margin = !mySnap ? 0 : abs(newBounds.x - (x - newBounds.width));
      if (margin > maxDistance) {
        myRightMargin = margin;
      }
      else {
        newBounds.x = x - newBounds.width;
      }
    }
    else if (vEdge.edgeType == SegmentType.CENTER_VERTICAL) {
      newBounds.x = x - newBounds.width / 2;
    }
    else {
      assert false : vEdge;
    }
  }

  // TODO: Consider unifying this with the snapping logic in ResizeHandler
  @Override
  protected void snapHorizontal(Segment hEdge, int y, Rectangle newBounds) {
    int maxDistance = MAX_MATCH_DISTANCE;
    if (hEdge.edgeType == SegmentType.TOP) {
      int margin = !mySnap ? 0 : abs(newBounds.y - y);
      if (margin > maxDistance) {
        myTopMargin = margin;
      }
      else {
        newBounds.y = y;
      }
    }
    else if (hEdge.edgeType == SegmentType.BOTTOM) {
      int margin = !mySnap ? 0 : abs(newBounds.y - (y - newBounds.height));
      if (margin > maxDistance) {
        myBottomMargin = margin;
      }
      else {
        newBounds.y = y - newBounds.height;
      }
    }
    else if (hEdge.edgeType == SegmentType.CENTER_HORIZONTAL) {
      int margin = !mySnap ? 0 : abs(newBounds.y - (y - newBounds.height / 2));
      if (margin > maxDistance) {
        myTopMargin = margin;
        // or bottomMargin?
      }
      else {
        newBounds.y = y - newBounds.height / 2;
      }
    }
    else if (hEdge.edgeType == SegmentType.BASELINE) {
      newBounds.y = y - myDraggedBaseline;
    }
    else {
      assert false : hEdge;
    }
  }

  /**
   * Updates the handler for the given mouse move
   *
   * @param primary      the primary element being dragged
   * @param newBounds    the new bounds of the primary
   * @param modifierMask the keyboard modifiers pressed during the drag
   */
  public void updateMove(RadViewComponent primary, Rectangle newBounds, int modifierMask) {
    clearSuggestions();
    mySnap = (modifierMask & InputEvent.SHIFT_MASK) == 0;
    myBounds = new Rectangle(newBounds);

    Rectangle b = myBounds;
    Segment edge = new Segment(b.y, b.x, x2(b), null, null, SegmentType.TOP, NO_MARGIN);
    List<Match> horizontalMatches = findClosest(edge, myHorizontalEdges);
    edge = new Segment(y2(b), b.x, x2(b), null, null, SegmentType.BOTTOM, NO_MARGIN);
    addClosest(edge, myHorizontalEdges, horizontalMatches);

    // We add the LEFT and RIGHT segments. Also we add the START and END segments that will change based on the current RTL view context.
    edge = new Segment(b.x, b.y, y2(b), null, null, SegmentType.LEFT, NO_MARGIN);
    List<Match> verticalMatches = findClosest(edge, myVerticalEdges);
    edge = new Segment(b.x, b.y, y2(b), null, null, myTextDirection.getLeftSegment(), NO_MARGIN);
    addClosest(edge, myVerticalEdges, verticalMatches);
    edge = new Segment(x2(b), b.y, y2(b), null, null, SegmentType.RIGHT, NO_MARGIN);
    addClosest(edge, myVerticalEdges, verticalMatches);
    edge = new Segment(x2(b), b.y, y2(b), null, null, myTextDirection.getRightSegment(), NO_MARGIN);
    addClosest(edge, myVerticalEdges, verticalMatches);

    // Match center
    edge = new Segment(centerX(b), b.y, y2(b), null, null, SegmentType.CENTER_VERTICAL, NO_MARGIN);
    addClosest(edge, myCenterVertEdges, verticalMatches);
    edge = new Segment(centerY(b), b.x, x2(b), null, null, SegmentType.CENTER_HORIZONTAL, NO_MARGIN);
    addClosest(edge, myCenterHorizEdges, horizontalMatches);

    // Match baseline
    if (primary != null) {
      int baseline = primary.getBaseline();
      if (baseline != -1) {
        myDraggedBaseline = baseline;
        edge = new Segment(b.y + baseline, b.x, x2(b), primary, null, SegmentType.BASELINE, NO_MARGIN);
        addClosest(edge, myHorizontalEdges, horizontalMatches);
      }
    }
    // TODO: When dragging from the palette, obtain the baseline from the drag preview, if available
    //else {
    //  int baseline = feedback.dragBaseline;
    //  if (baseline != -1) {
    //    myDraggedBaseline = baseline;
    //    edge = new Segment(offsetY + baseline, b.x, x2(b), null, null, BASELINE, NO_MARGIN);
    //    addClosest(edge, myHorizontalEdges, horizontalMatches);
    //  }
    //}

    myHorizontalSuggestions = horizontalMatches;
    myVerticalSuggestions = verticalMatches;
    myTopMargin = myBottomMargin = myLeftMargin = myRightMargin = 0;

    Match match = pickBestMatch(myHorizontalSuggestions);
    if (match != null) {
      if (myHorizontalDeps.contains(match.edge.node)) {
        match.cycle = true;
      }

      // Reset top AND bottom bounds regardless of whether both are bound
      myMoveTop = true;
      myMoveBottom = true;

      // TODO: Consider doing the snap logic on all the possible matches
      // BEFORE sorting, in case this affects the best-pick algorithm (since some
      // edges snap and others don't).
      snapHorizontal(match.with, match.edge.at, myBounds);

      if (match.with.edgeType == SegmentType.TOP) {
        myCurrentTopMatch = match;
      }
      else if (match.with.edgeType == SegmentType.BOTTOM) {
        myCurrentBottomMatch = match;
      }
      else {
        assert match.with.edgeType == SegmentType.CENTER_HORIZONTAL || match.with.edgeType == SegmentType.BASELINE : match.with.edgeType;
        myCurrentTopMatch = match;
      }
    }

    match = pickBestMatch(myVerticalSuggestions);
    if (match != null) {
      if (myVerticalDeps.contains(match.edge.node)) {
        match.cycle = true;
      }

      // Reset left AND right bounds regardless of whether both are bound
      myMoveLeft = true;
      myMoveRight = true;

      snapVertical(match.with, match.edge.at, myBounds);

      if (myTextDirection.isLeftSegment(match.with.edgeType)) {
        myCurrentLeftMatch = match;
      }
      else if (myTextDirection.isRightSegment(match.with.edgeType)) {
        myCurrentRightMatch = match;
      }
      else {
        assert match.with.edgeType == SegmentType.CENTER_VERTICAL;
        myCurrentLeftMatch = match;
      }
    }

    checkCycles();
  }
}
