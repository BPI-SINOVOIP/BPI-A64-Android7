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
package com.android.tools.idea.uibuilder.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.FillPolicy;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SegmentType;

import java.util.List;

/**
 * Handler for views that are layout managers.
 */
public class ViewGroupHandler extends ViewHandler {
  /**
   * Returns whether the given layout accepts the given proposed child.
   *
   * @param layout   the layout being inserted into (which does not yet contain the
   *                 newly created node in its child list)
   * @param newChild the newly created component
   * @return true if the proposed child is accepted
   */
  public boolean acceptsChild(@NonNull NlComponent layout,
                              @NonNull NlComponent newChild) {
    return true;
  }

  /**
   * Called when one or more children are about to be deleted by the user.
   *
   * @param parent  the parent of the deleted children (which still contains
   *                the children since this method is called before the deletion
   *                is performed)
   * @param deleted a nonempty list of children about to be deleted
   * @return true if the children have been fully deleted by this participant; false
   *         if normal deletion should resume. Note that even though an implementation may return
   *         false from this method, that does not mean it did not perform any work. For example,
   *         a RelativeLayout handler could remove constraints pointing to now deleted components,
   *         but leave the overall deletion of the elements to the core designer.
   */
  public boolean deleteChildren(@NonNull NlComponent parent, @NonNull List<NlComponent> deleted) {
    return false;
  }

  /**
   * Creates a new drag handler for this view, if the view accepts children or allows them to be reconfigured.
   *
   * @param editor     the associated IDE editor
   * @param layout     the layout being dragged over/into
   * @param components the components being dragged
   * @param type       the <b>initial</b> type of drag, which can change along the way
   *
   * @return a new drag handler, or null if this view does not accept children or does not allow them to be reconfigured
   */
  @Nullable
  public DragHandler createDragHandler(@NonNull ViewEditor editor,
                                       @NonNull NlComponent layout,
                                       @NonNull List<NlComponent> components,
                                       @NonNull DragType type) {
    return null;
  }

  /**
   * Creates a new resize handler for the given resizable component child of the given layout
   *
   * @param editor             the associated IDE editor
   * @param component          the component being resized
   * @param horizontalEdgeType the horizontal (top or bottom) edge being resized, if any
   * @param verticalEdgeType   the vertical (left or right) edge being resized, if any
   * @return a new resize handler, or null if the layout does not allow the child to be resized or if the child is not resizable
   */
  @Nullable
  public ResizeHandler createResizeHandler(@NonNull ViewEditor editor,
                                           @NonNull NlComponent component,
                                           @Nullable SegmentType horizontalEdgeType,
                                           @Nullable SegmentType verticalEdgeType) {
    return null;
  }

  /**
   * Called when a child for this view has been created and is being inserted into the
   * view parent for which this {@linkplain ViewHandler} applies. Allows the parent to perform
   * customizations of the object. As with {@link ViewHandler#onCreate}, the {@link InsertType}
   * parameter can be used to handle new creation versus moves versus copy/paste
   * operations differently.
   *
   * @param layout     the layout being inserted into (which may not yet contain the
   *                   newly created node in its child list)
   * @param newChild   the newly created component
   * @param insertType whether this node was created as part of a newly created view, or
   *                   as a copy, or as a move, etc.
   */
  public void onChildInserted(@NonNull NlComponent layout,
                              @NonNull NlComponent newChild,
                              @NonNull InsertType insertType) {
  }

  @Override
  public FillPolicy getFillPolicy() {
    return FillPolicy.BOTH;
  }
}
