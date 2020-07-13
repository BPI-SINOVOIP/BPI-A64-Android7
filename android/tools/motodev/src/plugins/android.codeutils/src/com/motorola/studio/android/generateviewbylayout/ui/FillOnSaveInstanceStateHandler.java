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
package com.motorola.studio.android.generateviewbylayout.ui;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import com.motorola.studio.android.codeutils.i18n.CodeUtilsNLS;
import com.motorola.studio.android.common.utilities.EclipseUtils;

/**
 * Command handler to Save UI state
 */
public class FillOnSaveInstanceStateHandler extends AbstractCodeGeneratorHandler
{

    /* (non-Javadoc)
     * @see org.eclipse.core.commands.IHandler#execute(org.eclipse.core.commands.ExecutionEvent)
     */

    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        SelectionBean selectionBean = resolveSelection(event);

        if (selectionBean.isProject() || selectionBean.isAllowedClassInstance())
        {
            final IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
            executeCodeGenerationWizard(event, selectionBean.getJavaFile(),
                    selectionBean.getJavaProject(),
                    new FillOnSaveInstanceStateDialog(window.getShell()));
        }
        else
        {
            EclipseUtils
                    .showErrorDialog(
                            CodeUtilsNLS.GenerateViewBasedOnLayoutHandler_FillJavaActivityBasedOnLayout,
                            CodeUtilsNLS.GenerateViewBasedOnLayoutHandler_SelectedClassNeitherActivityFragment);
        }

        return null;
    }

}
