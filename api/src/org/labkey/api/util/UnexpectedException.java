/*
 * Copyright (c) 2008-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.util;

import org.labkey.api.data.RuntimeSQLException;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

public class UnexpectedException extends RuntimeException
{
    static public void rethrow(Throwable cause)
    {
        // This case avoids extra wrapping when bad webpart parameters cause exceptions. For example, crawler passing a
        // bogus enrollmentTokenBatches.containerFilterName parameter.
        if (cause instanceof InvocationTargetException && cause.getCause() instanceof RuntimeException)
        {
            throw (RuntimeException) cause.getCause();
        }
        if (cause instanceof RuntimeException)
        {
            throw (RuntimeException) cause;
        }
        if (cause instanceof Error)
        {
            throw (Error) cause;
        }
        throw new UnexpectedException(cause);
    }

    static public RuntimeException wrap(Throwable cause)
    {
        if (cause instanceof RuntimeException)
            return (RuntimeException) cause;
        if (cause instanceof SQLException)
            return new RuntimeSQLException((SQLException)cause);
        return new UnexpectedException(cause);
    }

    static public RuntimeException wrap(Throwable cause, String message)
    {
        return new UnexpectedException(cause, message);
    }

    @Deprecated // use wrap()
    public UnexpectedException(Throwable cause)
    {
        super(cause);
    }

    @Deprecated // use wrap()
    public UnexpectedException(Throwable cause, String message)
    {
        super(message, cause);
    }

    public String toString()
    {
        return super.toString() + ":" + getCause();
    }
}
