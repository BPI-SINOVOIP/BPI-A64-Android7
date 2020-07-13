/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.motorolamobility.studio.android.db.core.ui.action;

import java.util.List;

import org.eclipse.core.runtime.IStatus;

import com.motorolamobility.studio.android.db.core.model.TableModel;
import com.motorolamobility.studio.android.db.core.ui.IDbNode;
import com.motorolamobility.studio.android.db.core.ui.ITreeNode;

public interface IDbCreatorNode extends ITreeNode
{

    IStatus createDb(String dbName);

    IStatus createDb(String dbName, List<TableModel> tables);

    IStatus deleteDb(IDbNode dbNode);

}
