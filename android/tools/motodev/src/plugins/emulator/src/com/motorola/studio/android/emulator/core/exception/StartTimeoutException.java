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
 * Represents the exception thrown when the emulator start process cannot end
 * within the set timeout
 */
@SuppressWarnings("serial")
public class StartTimeoutException extends AndroidException
{
    /**
     * Creates a new StartTimeoutException object.
     */
    public StartTimeoutException()
    {
    }

    /**
     * @param message the message used by the Exception.
     */
    public StartTimeoutException(String message)
    {
        super(message);
    }

    /**
     * @param cause the associated cause.
     */
    public StartTimeoutException(Throwable cause)
    {
        super(cause);
    }

    /**
     * @param message the message used by the Exception.
     * @param cause the associated cause.
     */
    public StartTimeoutException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
