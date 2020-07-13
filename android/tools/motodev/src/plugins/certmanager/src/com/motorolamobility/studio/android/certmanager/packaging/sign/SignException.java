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

package com.motorolamobility.studio.android.certmanager.packaging.sign;

/**
 * Signing exception
 */
@SuppressWarnings("serial")
public class SignException extends Exception
{
    /**
     * Creates a SignException with a detail message
     * 
     * @param message the detail message
     */
    public SignException(String message)
    {
        super(message);
    }

    /**
     * Creates a SignException with a detail message and a cause
     * 
     * @param message the detail message
     * @param cause the cause
     */
    public SignException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
