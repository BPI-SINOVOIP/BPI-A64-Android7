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
package com.motorola.studio.android.emulator.core.exception;

import com.motorola.studio.android.common.exception.AndroidException;

/**
 * Represents an exception thrown if the emulator instance cannot be stopped 
 */
@SuppressWarnings("serial")
public class InstanceStopException extends AndroidException
{
    /**
     * Creates a new InstanceStopException object.
     */
    public InstanceStopException()
    {
    }

    /**
     * @param message the message used by the Exception.
     */
    public InstanceStopException(String message)
    {
        super(message);
    }

    /**
     * @param cause the associated cause.
     */
    public InstanceStopException(Throwable cause)
    {
        super(cause);
    }

    /**
     * @param message the message used by the Exception.
     * @param cause the associated cause.
     */
    public InstanceStopException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
