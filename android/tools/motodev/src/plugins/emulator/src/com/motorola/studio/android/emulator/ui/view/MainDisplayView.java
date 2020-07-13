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
package com.motorola.studio.android.emulator.ui.view;

import static com.motorola.studio.android.common.log.StudioLogger.error;

import org.eclipse.sequoyah.vnc.protocol.lib.ProtocolHandle;
import org.eclipse.sequoyah.vnc.vncviewer.config.IPropertiesFileHandler;
import org.eclipse.sequoyah.vnc.vncviewer.config.VNCConfiguration;
import org.eclipse.sequoyah.vnc.vncviewer.graphics.swt.ISWTPainter;
import org.eclipse.sequoyah.vnc.vncviewer.graphics.swt.SWTRemoteDisplay;
import org.eclipse.sequoyah.vnc.vncviewer.graphics.swt.img.SWTRemoteDisplayImg;
import org.eclipse.sequoyah.vnc.vncviewer.network.VNCProtocolData;
import org.eclipse.sequoyah.vnc.vncviewer.registry.VNCProtocolRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.commands.ICommandService;

import com.motorola.studio.android.common.utilities.EclipseUtils;
import com.motorola.studio.android.emulator.EmulatorPlugin;
import com.motorola.studio.android.emulator.core.exception.InstanceStopException;
import com.motorola.studio.android.emulator.core.exception.SkinException;
import com.motorola.studio.android.emulator.core.model.IAndroidEmulatorInstance;
import com.motorola.studio.android.emulator.core.skin.IAndroidSkin;
import com.motorola.studio.android.emulator.core.skin.ISkinKeyXmlTags;
import com.motorola.studio.android.emulator.i18n.EmulatorNLS;
import com.motorola.studio.android.emulator.ui.IUIHelpConstants;
import com.motorola.studio.android.emulator.ui.controls.maindisplay.MainDisplayComposite;
import com.motorola.studio.android.emulator.ui.handlers.IHandlerConstants;

/**
 * This class represents the view that shows the Main Display (without skin)
 * of the emulator to the end user.
 */
public class MainDisplayView extends AbstractAndroidView
{

    /** 
     * The Android Emulator Main Display View view ID
     */
    public static final String EMULATOR_MAIN_DISPLAY_VIEW_ID =
            EmulatorPlugin.PLUGIN_ID + ".mainDisplayView";

    /**
     * @see com.motorola.studio.android.emulator.ui.view.AbstractAndroidView#getViewId()
     */
    @Override
    public String getViewId()
    {
        return EMULATOR_MAIN_DISPLAY_VIEW_ID;
    }

    /**
     * @see com.motorola.studio.android.emulator.ui.view.AbstractAndroidView#getHelpId()
     */
    @Override
    protected String getHelpId()
    {
        return IUIHelpConstants.EMULATOR_VIEW_MAIN_DISPLAY_HELP;
    }

    /**
     * Creates the scrolled composite, the detached composite and the main 
     * display related to emulator. 
     * 
     * @param tab the tab item that will hold the graphical elements that 
     * represents the emulator
     * @param instance the emulator instance
     * @param emulatorData the object to be defined with the elements created. 
     */
    @Override
    protected void createWidgets(TabItem tab, final IAndroidEmulatorInstance instance,
            final AndroidViewData tabData)
    {
        try
        {
            tabData.loadSkin(instance);
            IAndroidSkin skin = tabData.getSkin();

            ProtocolHandle handle = instance.getProtocolHandle();
            VNCProtocolData data = VNCProtocolRegistry.getInstance().get(handle);
            if (data != null)
            {

                int baseWidth =
                        skin.getSkinBean(instance.getCurrentLayout()).getSkinPropertyValue(
                                ISkinKeyXmlTags.SKIN_INTERNAL_VIEW_WIDTH);
                int baseHeight =
                        skin.getSkinBean(instance.getCurrentLayout()).getSkinPropertyValue(
                                ISkinKeyXmlTags.SKIN_INTERNAL_VIEW_HEIGHT);

                ScrolledComposite scrolledComposite =
                        new ScrolledComposite(tab.getParent(), SWT.H_SCROLL | SWT.V_SCROLL
                                | SWT.BACKGROUND);

                final MainDisplayComposite composite =
                        new MainDisplayComposite(scrolledComposite, SWT.BACKGROUND, baseWidth,
                                baseHeight, instance);
                composite.setLayout(new FillLayout());

                final SWTRemoteDisplay mainDisplay =
                        createMainDisplay(composite, skin, instance, (ISWTPainter) data
                                .getVncPainter());
                composite.setSize(baseWidth, baseHeight);

                scrolledComposite.setContent(composite);
                tab.setControl(scrolledComposite);

                scrolledComposite.addDisposeListener(new DisposeListener()
                {
                    public void widgetDisposed(DisposeEvent e)
                    {
                        composite.dispose();
                    }
                });

                tabData.setCliDisplay(null);
                tabData.setComposite(composite);
                tabData.setMainDisplay(mainDisplay);
            }
            else
            {
                error("The protocol object set in the device instance is not supported. Stopping the emulator instance...");
                EclipseUtils.showErrorDialog(EmulatorNLS.GEN_Error,
                        EmulatorNLS.ERR_AndroidView_ProtocolImplementerNotSupported);

                try
                {
                    instance.stop(true);
                }
                catch (InstanceStopException e)
                {
                    error("Error while running service for stopping virtual machine");
                    EclipseUtils.showErrorDialog(EmulatorNLS.GEN_Error,
                            EmulatorNLS.EXC_General_CannotRunStopService);
                }
            }
        }
        catch (SkinException e)
        {
            error("The skin associated to this instance (" + instance.getName()
                    + ") is not installed or is corrupted.");
            EclipseUtils.showErrorDialog(e);

            try
            {
                instance.stop(true);
            }
            catch (InstanceStopException e1)
            {
                error("Error while running service for stopping virtual machine");
                EclipseUtils.showErrorDialog(EmulatorNLS.GEN_Error,
                        EmulatorNLS.EXC_General_CannotRunStopService);
            }
        }

    }

    /**
     * Creates the main display used by this instance
     *
     * @param parent The parent composite where to place the widgets
     * @param skin The object containing skin information used to draw the widgets
     * @param instance the emulator instance
     * @param painter the painter used to draw the image
     */
    private SWTRemoteDisplay createMainDisplay(Composite parent, IAndroidSkin skin,
            IAndroidEmulatorInstance instance, ISWTPainter painter)
    {

        // gets and reads the configuration files of the VNC session
        IPropertiesFileHandler handler;
        String configFile;

        handler = IAndroidSkin.DEFAULT_PROPS_HANDLER;
        configFile = IAndroidSkin.DEFAULT_VNC_CONFIG_FILE;

        VNCConfiguration config = new VNCConfiguration(configFile, handler);

        SWTRemoteDisplay mainDisplay =
                new SWTRemoteDisplayImg(parent, config.getConfigurationProperties(), handler,
                        painter);
        mainDisplay.setBackground(new Color(mainDisplay.getDisplay(), 0, 0, 0));
        mainDisplay.setLayout(new FillLayout());

        return mainDisplay;
    }

    /**
     * Forces the refreshing of the menu elements. 
     */
    @Override
    protected void refreshMenuElements()
    {
        IViewSite viewSite = getViewSite();

        // Update the radio button selection in the zoom menu 
        ICommandService service = (ICommandService) viewSite.getService(ICommandService.class);
        service.refreshElements(IHandlerConstants.CHANGE_EMULATOR_ZOOM_COMMAND, null);
        service.refreshElements(IHandlerConstants.CHANGE_EMULATOR_ORIENTATION_COMMAND, null);

    }

}