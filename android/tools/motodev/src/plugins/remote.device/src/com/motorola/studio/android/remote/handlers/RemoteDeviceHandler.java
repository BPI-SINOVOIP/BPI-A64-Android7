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
package com.motorola.studio.android.remote.handlers;

import org.eclipse.sequoyah.device.framework.model.IDeviceLauncher;
import org.eclipse.sequoyah.device.framework.model.IInstance;
import org.eclipse.sequoyah.device.framework.model.handler.IDeviceHandler;

import com.motorola.studio.android.remote.instance.RemoteDeviceInstance;

/**
 * This class represents a TmL IDeviceHandler for Android Remote Device Instances
 */
public class RemoteDeviceHandler implements IDeviceHandler
{
    /**
     * Creates an Android Remote Device Instance with the given id
     * 
     * @param id the instance id
     */
    public IInstance createDeviceInstance(String id)
    {
        IInstance instance = new RemoteDeviceInstance();
        instance.setId(id);
        return instance;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.sequoyah.device.framework.model.handler.IDeviceHandler#createDeviceLauncher(org.eclipse.sequoyah.device.framework.model.IInstance)
     */
    public IDeviceLauncher createDeviceLauncher(IInstance instance)
    {
        return null;
    }
}
