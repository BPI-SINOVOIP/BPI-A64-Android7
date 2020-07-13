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
package com.motorola.studio.android.handset.ui;

import java.util.Properties;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ui.IWorkbenchPropertyPage;

import com.motorola.studio.android.adt.ISerialNumbered;
import com.motorola.studio.android.devices.AbstractDevicePropertyPage;
import com.motorola.studio.android.handset.AndroidHandsetInstance;

/**
 * 
 * 
 * @author xrgc84
 */
public class DevicePropertiesPage extends AbstractDevicePropertyPage implements
        IWorkbenchPropertyPage
{

    private ISerialNumbered androidIntance;

    @Override
    public void setElement(IAdaptable element)
    {

        this.androidIntance = (ISerialNumbered) element;

        super.setElement(element);
    }

    @Override
    protected Properties getDeviceProperties()
    {
        return ((AndroidHandsetInstance) androidIntance).getProperties();
    }
}
