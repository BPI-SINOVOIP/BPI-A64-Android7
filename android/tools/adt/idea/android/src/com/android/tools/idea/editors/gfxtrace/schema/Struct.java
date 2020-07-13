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
package com.android.tools.idea.editors.gfxtrace.schema;

import com.android.tools.idea.editors.gfxtrace.rpc.StructInfo;

/**
 * A structure instance unpacked using a schema description.
 */
public class Struct {
  public final StructInfo info;
  public final Field[] fields;

  public Struct(StructInfo info, Field[] fields) {
    this.info = info;
    this.fields = fields;
  }
}
