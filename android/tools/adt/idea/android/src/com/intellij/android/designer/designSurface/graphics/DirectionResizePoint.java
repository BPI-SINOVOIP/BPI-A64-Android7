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
package com.intellij.android.designer.designSurface.graphics;

import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * A replacement of the ui-core DirectionResizePoint which uses a logical drawing style instead of
 * a hardcoded color.
 */
public class DirectionResizePoint extends com.intellij.designer.designSurface.selection.DirectionResizePoint {
  private final DrawingStyle myStyle;

  public DirectionResizePoint(DrawingStyle style, int direction, Object type, @Nullable String description) {
    //noinspection UseJBColor
    super(Color.RED /* should not be used */, Color.RED /* should not be used */, direction, type, description);
    myStyle = style;
  }

  @Override
  protected void paint(DecorationLayer layer, Graphics2D g, RadComponent component) {
    Point location = getLocation(layer, component);
    int size = getSize();
    DesignerGraphics.drawStrokeFilledRect(myStyle, g, location.x, location.y, size, size);
  }

  @Override
  protected int getSize() {
    return 7;
  }

  @Override
  protected int getNeighborhoodSize() {
    return 2;
  }

  @Override
  protected Point getLocation(DecorationLayer layer, RadComponent component) {
    Point location = super.getLocation(layer, component);
    if (myXSeparator == 0) {
      location.x++;
    }
    if (myYSeparator == 0) {
      location.y++;
    }

    return location;
  }
}
