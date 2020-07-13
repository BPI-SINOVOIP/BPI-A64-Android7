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
package com.motorolamobility.studio.android.db.devices.ui.action;

import org.eclipse.core.commands.State;

import com.motorolamobility.studio.android.db.core.DbCoreActivator;
import com.motorolamobility.studio.android.db.core.ui.ITreeNode;
import com.motorolamobility.studio.android.db.devices.model.DeviceNode;

public class PersistentToggleState extends State
{

    public PersistentToggleState()
    {
        setValue(true);
    }

    /* (non-Javadoc)
     * @see org.eclipse.core.commands.State#getValue()
     */
    @Override
    public Object getValue()
    {
        ITreeNode treeNode =
                DbCoreActivator.getMOTODEVDatabaseExplorerView().getSelectedItemOnTree();
        if (treeNode instanceof DeviceNode)
        {
            DeviceNode devNode = (DeviceNode) treeNode;
            boolean filterEnabled = devNode.mustFilterAppsWithDb();
            setValue(filterEnabled);
        }
        return super.getValue();
    }
}
