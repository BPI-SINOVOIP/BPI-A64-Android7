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
package com.motorolamobility.studio.android.certmanager.exception;

import com.motorola.studio.android.common.exception.AndroidException;
import com.motorolamobility.studio.android.certmanager.core.KeyStoreManager;

/**
 * Exception thrown when trying to access the {@link KeyStoreManager}.
 * */
@SuppressWarnings("serial")
public class KeyStoreManagerException extends AndroidException
{
    /*
     * Create a new empty exception.
     * */
    @SuppressWarnings("unused")
    private KeyStoreManagerException()
    {
        //prevent methods to throw this exception without further information
    }

    /**
     * Create a new exception with a message indicating the problem.
     * */
    public KeyStoreManagerException(String message)
    {
        super(message);
    }

    /**
     * Create a new exception with a message indicating the problem,
     * and append some other exception that is being replaced by this one.
     * */
    public KeyStoreManagerException(String message, Throwable cause)
    {
        super(message, cause);
    }

}
