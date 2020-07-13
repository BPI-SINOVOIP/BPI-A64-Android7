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
package com.android.tools.idea.uibuilder.handlers;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;

import java.util.List;

import static com.android.SdkConstants.*;

/** Handler for the {@code <ScrollView>} widget */
public class ScrollViewHandler extends ViewGroupHandler {
  @Override
  public void onChildInserted(@NonNull NlComponent parent, @NonNull NlComponent child,
                              @NonNull InsertType insertType) {
    child.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
    child.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
  }

  @Override
  public boolean onCreate(@NonNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NonNull NlComponent node,
                          @NonNull InsertType insertType) {
    if (insertType.isCreate()) {
      // Insert a default linear layout (which will in turn be registered as
      // a child of this node and the create child method above will set its
      // fill parent attributes, its id, etc.
      NlComponent linear = node.createChild(editor, FQCN_LINEAR_LAYOUT, null, InsertType.VIEW_HANDLER);
      linear.setAttribute(ANDROID_URI, ATTR_ORIENTATION, VALUE_VERTICAL);
    }

    return true;
  }

  @Nullable
  @Override
  public DragHandler createDragHandler(@NonNull ViewEditor editor,
                                       @NonNull NlComponent layout,
                                       @NonNull List<NlComponent> components,
                                       @NonNull DragType type) {
    return new OneChildDragHandler(editor, this, layout, components, type);
  }

  static class OneChildDragHandler extends DragHandler {
    public OneChildDragHandler(@NonNull ViewEditor editor,
                               @NonNull ViewGroupHandler handler,
                               @NonNull NlComponent layout,
                               @NonNull List<NlComponent> components,
                               @NonNull DragType type) {
      super(editor, handler, layout, components, type);
    }

    @Nullable
    @Override
    public String update(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
      super.update(x, y, modifiers);

      if (layout.getChildCount() > 0 || components.size() > 1) {
        return "Layout only allows 1 child";
      }

      return null;
    }

    @Override
    public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    }

    @Override
    public void paint(@NonNull NlGraphics graphics) {
      if (layout.getChildCount() == 0) {
        graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);
        graphics.drawRect(layout.x, layout.y, layout.w, layout.h);
      }
    }
  }
}
