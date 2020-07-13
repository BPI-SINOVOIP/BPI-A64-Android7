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
package com.android.tools.idea.editors.hprof.descriptors;

import com.android.tools.idea.editors.hprof.jdi.ArrayReferenceImpl;
import com.android.tools.idea.editors.hprof.jdi.ClassObjectReferenceImpl;
import com.android.tools.idea.editors.hprof.jdi.ObjectReferenceImpl;
import com.android.tools.idea.editors.hprof.jdi.StringReferenceImpl;
import com.android.tools.perflib.heap.*;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InstanceFieldDescriptorImpl extends HprofFieldDescriptorImpl {
  private static final int MAX_VALUE_TEXT_LENGTH = 1024;
  @NotNull private ObjectReferenceImpl myObjectReference;
  @Nullable private String myTruncatedValueText;

  public InstanceFieldDescriptorImpl(@NotNull Project project, @NotNull Field field, @Nullable Instance instance, int memoryOrdering) {
    super(project, field, instance, memoryOrdering);
    myObjectReference = initObjectReference();
  }

  @Nullable
  public Instance getInstance() {
    return (Instance)myValueData;
  }

  @Override
  public boolean isString() {
    return myValueData != null &&
           ((Instance)myValueData).getClassObj() != null &&
           "java.lang.String".equals(((Instance)myValueData).getClassObj().getClassName());
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public boolean isExpandable() {
    if (myValueData instanceof ClassObj) {
      return false;
    }
    else if (myValueData instanceof ArrayInstance) {
      return ((ArrayInstance)myValueData).getSize() > 0;
    }
    else {
      return !isNull();
    }
  }

  @Override
  public Value getValue() {
    return calcValue(null);
  }

  @Override
  @NotNull
  public String getIdLabel() {
    Instance instance = getInstance();
    if (instance == null) {
      return "";
    }

    return String.format("%s (0x%x)", ValueDescriptorImpl.getIdLabel(myObjectReference), instance.getUniqueId());
  }

  @NotNull
  @Override
  public String getValueText() {
    if (myTruncatedValueText != null) {
      return myTruncatedValueText;
    }

    if (myValueData == null) {
      myTruncatedValueText = "null";
    }
    else if (myValueData instanceof ClassObj) {
      myTruncatedValueText = String.format(" \"class %s\"", ((ClassObj)myValueData).getClassName());
    }
    else if (isString()) {
      int count = -1;
      int offset = 0;
      ArrayInstance charBufferArray = null;
      assert (myValueData instanceof ClassInstance);
      ClassInstance classInstance = (ClassInstance)myValueData;
      for (ClassInstance.FieldValue entry : classInstance.getValues()) {
        if (charBufferArray == null && "value".equals(entry.getField().getName())) {
          if (entry.getValue() instanceof ArrayInstance && ((ArrayInstance)entry.getValue()).getArrayType() == Type.CHAR) {
            charBufferArray = (ArrayInstance)entry.getValue();
          }
        }
        else if ("count".equals(entry.getField().getName())) {
          if (entry.getValue() instanceof Integer) {
            count = (Integer)entry.getValue();
          }
        }
        else if ("offset".equals(entry.getField().getName())) {
          if (entry.getValue() instanceof Integer) {
            offset = (Integer)entry.getValue();
          }
        }
      }

      if (charBufferArray != null) {
        char[] stringChars = charBufferArray.asCharArray(offset >= 0 ? offset : 0, Math.max(Math.min(count, MAX_VALUE_TEXT_LENGTH), 0));
        int charLength = stringChars.length;
        StringBuilder builder = new StringBuilder(6 + charLength);
        builder.append(" \"");
        if (charLength == MAX_VALUE_TEXT_LENGTH) {
          builder.append(stringChars, 0, charLength - 1).append("...");
        }
        else {
          builder.append(stringChars);
        }
        builder.append("\"");
        myTruncatedValueText = builder.toString();
      }
      else {
        myTruncatedValueText = " ...<invalid string value>...";
      }
    }
    else {
      myTruncatedValueText = "";
    }

    return myTruncatedValueText;
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) {
    return myObjectReference;
  }

  private ObjectReferenceImpl initObjectReference() {
    if (isString()) {
      return new StringReferenceImpl(myField, (Instance)myValueData);
    }
    else if (myValueData instanceof ArrayInstance) {
      //noinspection ConstantConditions
      return new ArrayReferenceImpl(myField, (ArrayInstance)myValueData);
    }
    else if (myValueData instanceof ClassObj) {
      return new ClassObjectReferenceImpl(myField, (ClassObj)myValueData);
    }
    else {
      return new ObjectReferenceImpl(myField, (Instance)myValueData);
    }
  }
}
