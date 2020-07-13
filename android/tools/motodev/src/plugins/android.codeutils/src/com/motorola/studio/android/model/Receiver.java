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
package com.motorola.studio.android.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.osgi.util.NLS;

import com.motorola.studio.android.codeutils.i18n.CodeUtilsNLS;
import com.motorola.studio.android.common.IAndroidConstants;
import com.motorola.studio.android.common.exception.AndroidException;
import com.motorola.studio.android.manifest.AndroidProjectManifestFile;
import com.motorola.studio.android.model.java.BroadcastReceiverClass;
import com.motorola.studio.android.model.manifest.AndroidManifestFile;
import com.motorola.studio.android.model.manifest.dom.ActionNode;
import com.motorola.studio.android.model.manifest.dom.ApplicationNode;
import com.motorola.studio.android.model.manifest.dom.CategoryNode;
import com.motorola.studio.android.model.manifest.dom.IntentFilterNode;
import com.motorola.studio.android.model.manifest.dom.ManifestNode;
import com.motorola.studio.android.model.manifest.dom.ReceiverNode;
import com.motorola.studio.android.model.manifest.dom.UsesPermissionNode;
import com.motorola.studio.android.model.resources.ResourceFile;
import com.motorola.studio.android.model.resources.types.AbstractResourceNode.NodeType;
import com.motorola.studio.android.model.resources.types.ResourcesNode;
import com.motorola.studio.android.model.resources.types.StringNode;
import com.motorola.studio.android.resources.AndroidProjectResources;

/**
 * Receiver Class.
 */
public class Receiver extends Launcher
{
    private static final String RECEIVER_RESOURCE_LABEL_SUFFIX = "BroadcastReceiverLabel";

    private static final int MANIFEST_UPDATING_STEPS = 6;

    private static final int RESOURCES_UPDATING_STEPS = 3;

    /**
     * Default constructor.
     */
    public Receiver()
    {
        super(IAndroidConstants.CLASS_BROADCASTRECEIVER);
    }

    /*
     * (non-Javadoc)
     * @see com.motorola.studio.android.model.BuildingBlockModel#getStatus()
     */
    @Override
    public IStatus getStatus()
    {
        return super.getStatus();
    }

    /*
     * (non-Javadoc)
     * @see com.motorola.studio.android.model.IWizardModel#needMoreInformation()
     */
    public boolean needMoreInformation()
    {
        return false;
    }

    /**
     * Create the broadcast receiver class and add it to the manifest file.
     * @return True if the broadcast receiver class was successfully created and added to the manifest file.
     * */
    @Override
    public boolean save(IWizardContainer container, IProgressMonitor monitor)
            throws AndroidException
    {
        boolean classCreated = createReceiverClass(monitor);
        boolean addedOnManifest = false;

        if (classCreated)
        {
            addedOnManifest = createReceiverOnManifest(monitor);
        }

        // Logs all permissions used in UDC log
        super.save(container, monitor);

        return classCreated && addedOnManifest;
    }

    /*
     * Creates the Broadcast Receiver java class
     * 
     * @param monitor the progress monitor
     * 
     * @return true if the class has been created or false otherwise
     * @throws AndroidException
     */
    private boolean createReceiverClass(IProgressMonitor monitor) throws AndroidException
    {
        boolean created = false;

        monitor.subTask(CodeUtilsNLS.UI_Receiver_CreatingTheReceiverJavaClass);

        BroadcastReceiverClass receiverClass =
                new BroadcastReceiverClass(getName(), getPackageFragment().getElementName());

        try
        {
            createJavaClassFile(receiverClass, monitor);
            created = true;
        }
        catch (JavaModelException e)
        {
            String errMsg =
                    NLS.bind(CodeUtilsNLS.EXC_Receiver_CannotCreateTheReceiverClass, getName(),
                            e.getLocalizedMessage());

            throw new AndroidException(errMsg);
        }
        catch (AndroidException e)
        {
            String errMsg =
                    NLS.bind(CodeUtilsNLS.EXC_Receiver_CannotCreateTheReceiverClass, getName(),
                            e.getLocalizedMessage());
            throw new AndroidException(errMsg);
        }

        return created;
    }

    /*
     * Creates the Broadcast Receiver class entry on AndroidManifest.xml file
     * 
     * @param monitor the progress monitor
     * 
     * @return true if the entry has been added or false otherwise
     * @throws AndroidException
     */
    private boolean createReceiverOnManifest(IProgressMonitor monitor) throws AndroidException
    {
        boolean created = false;

        try
        {
            int totalWork = MANIFEST_UPDATING_STEPS + RESOURCES_UPDATING_STEPS;

            monitor.beginTask("", totalWork);

            monitor.subTask(CodeUtilsNLS.UI_Common_UpdatingTheAndroidManifestXMLFile);

            AndroidManifestFile androidManifestFile =
                    AndroidProjectManifestFile.getFromProject(getProject());

            monitor.worked(1);

            ManifestNode manifestNode =
                    androidManifestFile != null ? androidManifestFile.getManifestNode() : null;
            ApplicationNode applicationNode =
                    manifestNode != null ? manifestNode.getApplicationNode() : null;

            monitor.worked(1);

            if (applicationNode != null)
            {

                // Adds the added permission nodes to manifest file         
                List<String> permissionsNames = new ArrayList<String>();
                for (UsesPermissionNode i : manifestNode.getUsesPermissionNodes())
                {
                    permissionsNames.add(i.getName());
                }

                for (String intentFilterPermission : getIntentFilterPermissionsAsArray())
                {
                    if (!permissionsNames.contains(intentFilterPermission))
                    {
                        manifestNode.addChild(new UsesPermissionNode(intentFilterPermission));
                    }
                }

                boolean receiverExists = false;

                String classQualifier =
                        (getPackageFragment().getElementName()
                                .equals(manifestNode.getPackageName()) ? "" : getPackageFragment()
                                .getElementName())
                                + ".";

                for (ReceiverNode receiverNode : applicationNode.getReceiverNodes())
                {
                    if (receiverNode.getName().equals(getName()))
                    {
                        receiverExists = true;
                        break;
                    }
                }

                monitor.worked(1);

                if (!receiverExists)
                {
                    ReceiverNode receiverNode = new ReceiverNode(classQualifier + getName());

                    String receiverLabel = createReceiverLabel(monitor);

                    if (receiverLabel != null)
                    {
                        receiverNode.setLabel(AndroidProjectResources.STRING_CALL_PREFIX
                                + receiverLabel);
                    }

                    IntentFilterNode intentFilterNode = new IntentFilterNode();

                    for (String intentFilterAction : getIntentFilterActionsAsArray())
                    {
                        intentFilterNode.addActionNode(new ActionNode(intentFilterAction));
                    }

                    for (String intentFilterCategory : getIntentFilterCategoriesAsArray())
                    {
                        intentFilterNode.addCategoryNode(new CategoryNode(intentFilterCategory));
                    }

                    if (intentFilterNode.getChildren().length > 0)
                    {
                        receiverNode.addIntentFilterNode(intentFilterNode);
                    }

                    applicationNode.addReceiverNode(receiverNode);

                    monitor.worked(1);

                    monitor.subTask(CodeUtilsNLS.UI_Common_SavingTheAndroidManifestXMLFile);

                    AndroidProjectManifestFile.saveToProject(getProject(), androidManifestFile,
                            true);

                    created = true;

                    monitor.worked(1);
                }
            }
        }
        catch (AndroidException e)
        {
            String errMsg =
                    NLS.bind(CodeUtilsNLS.EXC_Receiver_CannotUpdateTheManifestFile, getName(),
                            e.getLocalizedMessage());
            throw new AndroidException(errMsg);
        }
        catch (CoreException e)
        {
            String errMsg =
                    NLS.bind(CodeUtilsNLS.EXC_Receiver_CannotUpdateTheManifestFile, getName(),
                            e.getLocalizedMessage());
            throw new AndroidException(errMsg);
        }
        finally
        {
            monitor.done();
        }

        return created;
    }

    /*
     * Adds the Receiver label value on the strings resource file
     * 
     * @param monitor the progress monitor
     * 
     * @return The label value if it has been added to the strings resource file or null otherwise
     * @throws AndroidException
     */
    private String createReceiverLabel(IProgressMonitor monitor) throws AndroidException
    {
        String resLabel = null;

        if ((getLabel() != null) && (getLabel().trim().length() > 0))
        {
            try
            {
                monitor.subTask(CodeUtilsNLS.UI_Common_UpdatingTheStringsResourceFile);

                ResourceFile stringsFile =
                        AndroidProjectResources.getResourceFile(getProject(), NodeType.String);

                monitor.worked(1);

                if (stringsFile.getResourcesNode() == null)
                {
                    stringsFile.addResourceEntry(new ResourcesNode());
                }

                resLabel =
                        stringsFile.getNewResourceName(getName() + RECEIVER_RESOURCE_LABEL_SUFFIX);

                StringNode strNode = new StringNode(resLabel);
                strNode.setNodeValue(getLabel());

                stringsFile.getResourcesNode().addChildNode(strNode);

                monitor.worked(1);

                AndroidProjectResources
                        .saveResourceFile(getProject(), stringsFile, NodeType.String);

                monitor.worked(1);
            }
            catch (CoreException e)
            {
                String errMsg =
                        NLS.bind(CodeUtilsNLS.EXC_Activity_CannotCreateTheActivityLabel,
                                e.getLocalizedMessage());
                throw new AndroidException(errMsg);
            }
            catch (AndroidException e)
            {
                String errMsg =
                        NLS.bind(CodeUtilsNLS.EXC_Activity_CannotCreateTheActivityLabel,
                                e.getLocalizedMessage());
                throw new AndroidException(errMsg);
            }
        }

        return resLabel;
    }
}
