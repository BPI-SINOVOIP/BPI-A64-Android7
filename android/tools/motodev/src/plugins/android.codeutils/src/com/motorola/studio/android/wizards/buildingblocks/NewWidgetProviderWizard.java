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
package com.motorola.studio.android.wizards.buildingblocks;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PartInitException;

import com.motorola.studio.android.codeutils.CodeUtilsActivator;
import com.motorola.studio.android.codeutils.i18n.CodeUtilsNLS;
import com.motorola.studio.android.common.exception.AndroidException;
import com.motorola.studio.android.common.log.StudioLogger;
import com.motorola.studio.android.common.utilities.EclipseUtils;
import com.motorola.studio.android.model.BuildingBlockModel;
import com.motorola.studio.android.model.WidgetProvider;

/**
 * Class that implements the Widget Provider building block wizard.
 */
public class NewWidgetProviderWizard extends NewBuildingBlocksWizard
{

    private static final String WIZ_BANNER_ICON = "icons/wizban/widget_provider_block_wiz.png";

    private WidgetProvider widgetProvider = new WidgetProvider();

    @Override
    protected BuildingBlockModel getBuildingBlock()
    {
        return widgetProvider;
    }

    @Override
    public boolean performFinish()
    {
        boolean saved = false;

        try
        {
            DoSave doSave = new DoSave();

            getContainer().run(false, false, doSave);

            if (doSave.exception != null)
            {
                throw doSave.exception;
            }
            else
            {
                saved = doSave.saved;
            }
        }
        catch (AndroidException e)
        {
            IStatus status =
                    new Status(IStatus.ERROR, CodeUtilsActivator.PLUGIN_ID, e.getLocalizedMessage());
            EclipseUtils.showErrorDialog(CodeUtilsNLS.UI_GenericErrorDialogTitle,
                    CodeUtilsNLS.ERR_BuildingBlockCreation_ErrorMessage, status);
        }
        catch (InvocationTargetException e)
        {
            IStatus status =
                    new Status(IStatus.ERROR, CodeUtilsActivator.PLUGIN_ID, e.getLocalizedMessage());
            EclipseUtils.showErrorDialog(CodeUtilsNLS.UI_GenericErrorDialogTitle,
                    CodeUtilsNLS.ERR_BuildingBlockCreation_ErrorMessage, status);
        }
        catch (InterruptedException e)
        {
            IStatus status =
                    new Status(IStatus.ERROR, CodeUtilsActivator.PLUGIN_ID, e.getLocalizedMessage());
            EclipseUtils.showErrorDialog(CodeUtilsNLS.UI_GenericErrorDialogTitle,
                    CodeUtilsNLS.ERR_BuildingBlockCreation_ErrorMessage, status);
        }

        if (saved)
        {
            ICompilationUnit javaFile =
                    getBuildingBlock().getPackageFragment().getCompilationUnit(
                            getBuildingBlock().getName() + ".java");

            if ((javaFile != null) && javaFile.exists())
            {
                try
                {
                    JavaUI.openInEditor(javaFile);
                }
                catch (PartInitException e)
                {
                    // Do nothing
                    StudioLogger.error(NewWidgetProviderWizard.class,
                            "Could not open the widget provider " + getBuildingBlock().getName()
                                    + " on an editor.", e);
                }
                catch (JavaModelException e)
                {
                    // Do nothing
                    StudioLogger.error(NewWidgetProviderWizard.class,
                            "Could not open the widget provider " + getBuildingBlock().getName()
                                    + " on an editor.", e);
                }
            }
        }

        if (saved)
        {
            // Collecting usage data for statistical purposes
            try
            {
                StudioLogger.collectUsageData(StudioLogger.WHAT_BUILDINGBLOCK_WIDGET_PROVIDER,
                        StudioLogger.KIND_BUILDINGBLOCK, StudioLogger.DESCRIPTION_DEFAULT,
                        CodeUtilsActivator.PLUGIN_ID, CodeUtilsActivator.getDefault().getBundle()
                                .getVersion().toString());
            }
            catch (Throwable e)
            {
                //Do nothing, but error on the log should never prevent app from working
            }
        }

        return saved;
    }

    public void init(IWorkbench workbench, IStructuredSelection selection)
    {
        setWindowTitle(CodeUtilsNLS.UI_NewWidgetProviderWizard_WizardTitle);
        setNeedsProgressMonitor(true);
        setDefaultPageImageDescriptor(CodeUtilsActivator.getImageDescriptor(WIZ_BANNER_ICON));
        widgetProvider.configure(selection);

    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.wizard.Wizard#addPages()
     */
    @Override
    public void addPages()
    {
        addPage(new NewWidgetProviderMainPage(widgetProvider));
    }

    /*
     * IRunnableWithProgress object to create the broadcast receiver
     */
    private class DoSave implements IRunnableWithProgress
    {
        AndroidException exception = null;

        boolean saved = false;

        public void run(IProgressMonitor monitor) throws InvocationTargetException,
                InterruptedException
        {
            try
            {
                saved = getBuildingBlock().save(getContainer(), monitor);
            }
            catch (AndroidException e)
            {
                exception = e;
            }
        }
    }

}
