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

package com.motorola.studio.android.mat.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.mat.ui.editor.IMultiPaneEditorContributor;
import org.eclipse.mat.ui.editor.MultiPaneEditor;

@SuppressWarnings("restriction")
public class HeapEditorContributions implements IMultiPaneEditorContributor
{

    private Action openMotodevPane;

    public void contributeToToolbar(IToolBarManager manager)
    {
        manager.add(new Separator());

        manager.add(openMotodevPane);

    }

    public void init(MultiPaneEditor editor)
    {
        openMotodevPane = new OpenMotodevPaneAction();

    }

    public void dispose()
    {
        // do nothing
    }

}
