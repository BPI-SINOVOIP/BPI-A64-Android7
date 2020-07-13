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
package com.intellij.openapi.vcs.actions;

import com.intellij.ide.util.PropertiesComponent;

/**
* @author Konstantin Bulenkov
*/
public enum ShortNameType {
  LASTNAME("lastname", "Last Name"),
  FIRSTNAME("firstname", "First Name"),
  NONE("full", "Full name");

  private static final String KEY = "annotate.short.names.type";
  private final String myId;
  private final String myDescription;

  ShortNameType(String id, String description) {
    myId = id;
    myDescription = description;
  }

  public String getDescription() {
    return myDescription;
  }

  boolean isSet() {
    return myId.equals(PropertiesComponent.getInstance().getValue(KEY));
  }

  void set(boolean enable) {
    if (enable) {
      PropertiesComponent.getInstance().setValue(KEY, myId);
    } else {
      PropertiesComponent.getInstance().unsetValue(KEY);
    }
  }
}
