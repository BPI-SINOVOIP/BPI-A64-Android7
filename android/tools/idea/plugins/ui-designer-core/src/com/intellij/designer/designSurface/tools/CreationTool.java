/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.designSurface.tools;

import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;

import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class CreationTool extends AbstractCreationTool {
  private final ComponentCreationFactory myFactory;

  public CreationTool(boolean canUnload, ComponentCreationFactory factory) {
    super(canUnload);
    myFactory = factory;
  }

  public ComponentCreationFactory getFactory() {
    return myFactory;
  }

  @Override
  public void activate() {
    super.activate();
    myContext.setType(OperationContext.CREATE);

    try {
      myContext.setComponents(Collections.singletonList(myFactory.create()));
    }
    catch (Throwable e) {
      myToolProvider.loadDefaultTool();
    }
  }

  @Override
  protected void updateTarget() {
    if (myTargetOperation != null && myContext != null) {
      List<RadComponent> components = myContext.getComponents();
      if (components != null && !components.isEmpty()) {
        myTargetOperation.setComponent(components.get(0));
      }
    }
  }
}