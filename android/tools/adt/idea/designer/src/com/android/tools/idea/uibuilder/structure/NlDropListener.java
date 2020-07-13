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
package com.android.tools.idea.uibuilder.structure;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.structure.NlComponentTree.InsertionPoint;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.utils.Pair;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.uibuilder.structure.NlComponentTree.InsertionPoint.*;

/**
 * Enable drop of components dragged onto the component tree.
 * Both internal moves and drags from the palette and other structure panes are supported.
 */
public class NlDropListener extends DropTargetAdapter {
  private static final Logger LOG = Logger.getInstance(NlDropListener.class);

  private final List<NlComponent> myDragged;
  private final NlComponentTree myTree;
  private DnDTransferItem myTransferItem;
  private NlComponent myDragReceiver;
  private NlComponent myNextDragSibling;

  public NlDropListener(@NonNull NlComponentTree tree) {
    myDragged = new ArrayList<NlComponent>(10);
    myTree = tree;
  }

  @Override
  public void dragEnter(@NonNull DropTargetDragEvent dragEvent) {
    NlDropEvent event = new NlDropEvent(dragEvent);
    captureDraggedComponents(event, true /* preview */);
    updateInsertionPoint(event);
  }

  @Override
  public void dragOver(@NonNull DropTargetDragEvent dragEvent) {
    NlDropEvent event = new NlDropEvent(dragEvent);
    updateInsertionPoint(event);
  }

  @Override
  public void dragExit(@NonNull DropTargetEvent event) {
    clearInsertionPoint();
    clearDraggedComponents();
  }

  @Override
  public void drop(@NonNull DropTargetDropEvent dropEvent) {
    NlDropEvent event = new NlDropEvent(dropEvent);
    InsertType insertType = captureDraggedComponents(event, false /* not as preview */);
    if (findInsertionPoint(event) != null) {
      performDrop(dropEvent, insertType);
    }
    clearInsertionPoint();
    clearDraggedComponents();
  }

  @Nullable
  private InsertType captureDraggedComponents(@NonNull NlDropEvent event, boolean isPreview) {
    clearDraggedComponents();
    ScreenView screenView = myTree.getScreenView();
    if (screenView == null) {
      return null;
    }
    NlModel model = screenView.getModel();
    if (event.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)) {
      try {
        myTransferItem = (DnDTransferItem)event.getTransferable().getTransferData(ItemTransferable.DESIGNER_FLAVOR);
        InsertType insertType = determineInsertType(event, isPreview);
        if (insertType.isMove()) {
          myDragged.addAll(model.getSelectionModel().getSelection());
        } else {
          List<NlComponent> captured = model.createComponents(screenView, myTransferItem, insertType);
          if (captured != null) {
            myDragged.addAll(captured);
          }
        }
        return insertType;
      }
      catch (IOException ex) {
      }
      catch (UnsupportedFlavorException ex) {
      }
    }
    return null;
  }

  @NonNull
  private InsertType determineInsertType(@NonNull NlDropEvent event, boolean isPreview) {
    NlModel model = myTree.getDesignerModel();
    if (model == null || myTransferItem == null) {
      return InsertType.MOVE_INTO;
    }
    DragType dragType = event.getDropAction() == DnDConstants.ACTION_COPY ? DragType.COPY : DragType.MOVE;
    return model.determineInsertType(dragType, myTransferItem, isPreview);
  }

  private void clearDraggedComponents() {
    myDragged.clear();
  }

  private void updateInsertionPoint(@NonNull NlDropEvent event) {
    Pair<TreePath, InsertionPoint> point = findInsertionPoint(event);
    if (point == null) {
      clearInsertionPoint();
      event.reject();
    } else {
      myTree.markInsertionPoint(point.getFirst(), point.getSecond());

      // This determines the icon presented to the user while dragging.
      // If we are dragging a component from the palette then use the icon for a copy, otherwise show the icon
      // that reflect the users choice i.e. controlled by the modifier key.
      event.accept(determineInsertType(event, true).isCreate() ? DnDConstants.ACTION_COPY : event.getDropAction());
    }
  }

  @Nullable
  private Pair<TreePath, InsertionPoint> findInsertionPoint(@NonNull NlDropEvent event) {
    myDragReceiver = null;
    myNextDragSibling = null;
    TreePath path = myTree.getClosestPathForLocation(event.getLocation().x, event.getLocation().y);
    if (path == null) {
      return null;
    }
    ScreenView screenView = myTree.getScreenView();
    if (screenView == null) {
      return null;
    }
    NlModel model = screenView.getModel();
    ViewHandlerManager handlerManager = ViewHandlerManager.get(screenView.getModel().getProject());
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    NlComponent component = (NlComponent)node.getUserObject();
    Rectangle bounds = myTree.getPathBounds(path);
    if (bounds == null) {
      return null;
    }
    InsertionPoint insertionPoint = findTreeStateInsertionPoint(event.getLocation().y, bounds);
    ViewHandler handler = handlerManager.getHandler(component);
    if (insertionPoint == INSERT_INTO && handler instanceof ViewGroupHandler) {
      if (!model.canAddComponents(myDragged, component, component.getChild(0))) {
        return null;
      }
      myDragReceiver = component;
      myNextDragSibling = component.getChild(0);
    } else if (component.getParent() == null) {
      return null;
    } else {
      handler = handlerManager.getHandler(component.getParent());
      if (handler == null || !model.canAddComponents(myDragged, component.getParent(), component)) {
        return null;
      }
      insertionPoint = event.getLocation().y > bounds.getCenterY() ? INSERT_AFTER : INSERT_BEFORE;
      myDragReceiver = component.getParent();
      if (insertionPoint == INSERT_BEFORE) {
        myNextDragSibling = component;
      } else {
        myNextDragSibling = component.getNextSibling();
      }
    }
    return Pair.of(path, insertionPoint);
  }

  @NonNull
  private static InsertionPoint findTreeStateInsertionPoint(@SwingCoordinate int y, @SwingCoordinate @NonNull Rectangle bounds) {
    int delta = bounds.height / 9;
    if (bounds.y + delta > y) {
      return INSERT_BEFORE;
    }
    if (bounds.y + bounds.height - delta < y) {
      return INSERT_AFTER;
    }
    return INSERT_INTO;
  }

  private void clearInsertionPoint() {
    myTree.markInsertionPoint(null, INSERT_BEFORE);
  }

  private void performDrop(@NonNull final DropTargetDropEvent event, final InsertType insertType) {
    NlModel model = myTree.getDesignerModel();
    assert model != null;
    try {
      model.addComponents(myDragged, myDragReceiver, myNextDragSibling, insertType);

      // This determines how the DnD source acts to a completed drop.
      // If we set the accepted drop action to DndConstants.ACTION_MOVE then the source should delete the source component.
      // When we move a component within the current designer the addComponents call will remove the source component in the transaction.
      // Only when we move a component from a different designer (handled as a InsertType.COPY) would we mark this as a ACTION_MOVE.
      event.acceptDrop(insertType == InsertType.COPY ? event.getDropAction() : DnDConstants.ACTION_COPY);

      event.dropComplete(true);
      model.notifyModified();
    }
    catch (Exception ignore) {
      LOG.debug(ignore);
      event.rejectDrop();
    }
  }
}
