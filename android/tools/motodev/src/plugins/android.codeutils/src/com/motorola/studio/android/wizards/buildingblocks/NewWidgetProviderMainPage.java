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

import com.motorola.studio.android.codeutils.CodeUtilsActivator;
import com.motorola.studio.android.codeutils.i18n.CodeUtilsNLS;
import com.motorola.studio.android.common.exception.AndroidException;
import com.motorola.studio.android.common.utilities.AndroidUtils;
import com.motorola.studio.android.model.WidgetProvider;

/**
 * Class that implements the Widget Provider Wizard Main Page.
 */
public class NewWidgetProviderMainPage extends NewLauncherWizardPage
{

    private static final String NEW_WIDGET_PROVIDER_HELP = CodeUtilsActivator.PLUGIN_ID
            + ".newwidgtprvd";

    /**
     * Default constructor
     * 
     * @param buildBlock The broadcast receiver model
     */
    protected NewWidgetProviderMainPage(WidgetProvider buildBlock)
    {
        super(buildBlock, CodeUtilsNLS.UI_NewWidgetProviderMainPage_PageTitle);
    }

    /* (non-Javadoc)
     * @see com.motorola.studio.android.wizards.buildingblocks.NewLauncherWizardPage#getBuildBlock()
     */
    @Override
    public WidgetProvider getBuildBlock()
    {
        return (WidgetProvider) super.getBuildBlock();
    }

    /* (non-Javadoc)
     * @see com.motorola.studio.android.wizards.buildingblocks.NewBuildingBlocksWizardPage#getMethods()
     */
    @Override
    protected Method[] getMethods()
    {
        return new Method[0];
    }

    /* (non-Javadoc)
     * @see com.motorola.studio.android.wizards.buildingblocks.NewBuildingBlocksWizardPage#getDefaultMessage()
     */
    @Override
    public String getDefaultMessage()
    {
        return CodeUtilsNLS.UI_NewWidgetProviderMainPage_DefaultWizardDescription;
    }

    /* (non-Javadoc)
     * @see com.motorola.studio.android.wizards.buildingblocks.NewBuildingBlocksWizardPage#getWizardTitle()
     */
    @Override
    public String getWizardTitle()
    {
        return CodeUtilsNLS.UI_NewWidgetProviderMainPage_WizardTitle;
    }

    /* (non-Javadoc)
     * @see com.motorola.studio.android.wizards.buildingblocks.NewLauncherWizardPage#getIntentFiltersActions()
     */
    @Override
    protected String[] getIntentFiltersActions()
    {
        String[] receiverActions = new String[0];
        try
        {
            receiverActions = AndroidUtils.getReceiverActions(getBuildBlock().getProject());
        }
        catch (AndroidException e)
        {
            setErrorMessage(e.getMessage());
        }
        return receiverActions;
    }

    /**
     * Gets the help ID to be used for attaching
     * context sensitive help. 
     * 
     * Classes that extends this class and want to set
     * their on help should override this method
     */
    @Override
    protected String getHelpId()
    {
        return NEW_WIDGET_PROVIDER_HELP;
    }
}
