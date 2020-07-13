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
package com.motorolamobility.studio.android.certmanager.ui.action;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.motorolamobility.studio.android.certmanager.ui.model.IKeyStore;
import com.motorolamobility.studio.android.certmanager.ui.model.IKeyStoreEntry;
import com.motorolamobility.studio.android.certmanager.ui.wizards.SignExternalPackageWizard;

/**
 * This class gets the current workspace selection and calls the wizard to Sign an existent package
 */
public class SignCreatedPackageAction extends Action
{

    /*
     * (non-Javadoc)
     * @see org.eclipse.jface.action.Action#run()
     */
    @Override
    public void run()
    {
        IWorkbench workbench = PlatformUI.getWorkbench();

        if ((workbench != null) && !workbench.isClosing())
        {

            IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();

            if (window != null)
            {
                ISelection selection = window.getSelectionService().getSelection();
                IStructuredSelection structureSelection = null;
                IKeyStore selectedIKeyStore = null;
                IKeyStoreEntry selectedEntry = null;
                if (selection instanceof IStructuredSelection)
                {
                    structureSelection = (IStructuredSelection) selection;
                    Object selectedItem = structureSelection.getFirstElement();
                    if (selectedItem instanceof IKeyStore)
                    {
                        selectedIKeyStore = (IKeyStore) selectedItem;
                    }
                    else if (selectedItem instanceof IKeyStoreEntry)
                    {
                        selectedEntry = (IKeyStoreEntry) selectedItem;
                        selectedIKeyStore = selectedEntry.getKeyStoreNode();
                    }
                }
                else
                {
                    structureSelection = new StructuredSelection();
                }
                WizardDialog dialog =
                        new WizardDialog(window.getShell(), new SignExternalPackageWizard(
                                structureSelection, selectedIKeyStore, selectedEntry));
                dialog.open();

            }
        }
    }

}
