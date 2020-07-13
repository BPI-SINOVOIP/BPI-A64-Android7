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
package com.motorolamobility.studio.android.certmanager.command;

import java.io.File;
import java.util.Map;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.ui.PlatformUI;

import com.motorola.studio.android.common.utilities.EclipseUtils;
import com.motorolamobility.studio.android.certmanager.core.KeyStoreUtils;
import com.motorolamobility.studio.android.certmanager.event.KeyStoreModelEvent.EventType;
import com.motorolamobility.studio.android.certmanager.event.KeyStoreModelEventManager;
import com.motorolamobility.studio.android.certmanager.exception.InvalidPasswordException;
import com.motorolamobility.studio.android.certmanager.exception.KeyStoreManagerException;
import com.motorolamobility.studio.android.certmanager.ui.dialogs.importks.ConvertKeyStoreTypeDialog;
import com.motorolamobility.studio.android.certmanager.ui.model.IKeyStore;
import com.motorolamobility.studio.android.certmanager.ui.model.ITreeNode;

/**
 * Handler to execute the change keystore type wizard.
 * */
public class ChangeKeyStoreTypeHandler extends AbstractHandler2
{

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        ITreeNode node = getSelection().get(0);

        if (node instanceof IKeyStore)
        {
            ConvertKeyStoreTypeDialog dialog =
                    new ConvertKeyStoreTypeDialog(PlatformUI.getWorkbench()
                            .getModalDialogShellProvider().getShell(), (IKeyStore) node);
            int diagStatus = dialog.open();
            if (diagStatus == Dialog.OK)
            {
                IKeyStore keyStore = dialog.getKeyStore();
                String newType = dialog.getNewType();
                Map<String, String> aliases = dialog.getAliases();
                String password = dialog.getKeystorePassword();

                File keyStoreFile = keyStore.getFile();
                try
                {
                    if (password != null)
                    {
                        //user entered some password
                        KeyStoreUtils.changeKeyStoreType(keyStoreFile, password.toCharArray(),
                                keyStore.getType(), newType, aliases);
                        keyStore.setType(newType);
                        keyStore.forceReload(password.toCharArray(), false);
                        KeyStoreModelEventManager.getInstance().fireEvent((ITreeNode) keyStore,
                                EventType.UPDATE);

                    }
                }
                catch (KeyStoreManagerException e)
                {
                    EclipseUtils.showErrorDialog(e);
                }
                catch (InvalidPasswordException e)
                {
                    EclipseUtils.showErrorDialog(e);
                }
            }
        }

        return null;
    }
}
