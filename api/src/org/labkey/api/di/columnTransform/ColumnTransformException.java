/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.di.columnTransform;

import org.jetbrains.annotations.NotNull;

/**
 * Wrapping class for exceptions caught in the Supplier to ColumnTransform.addOutputColumn(), such as implementations of
 * ColumnTransform.doTransform(). Must be a RuntimeException b/c Java 8 Suppliers don't directly
 * support throwing checked exceptions.
 *
 * A thrown instance of this exception will be automatically unwrapped for the ETL log to show the cause and stack
 * trace of the original underlying exception.
 */
public class ColumnTransformException extends RuntimeException
{
    /**
     *
     * @param message An explanatory message, as with any other thrown exception
     * @param cause The caught exception (don't unwrap in the call to the constructor; pass the exception as-is)
     */
    public ColumnTransformException(String message, @NotNull Throwable cause)
    {
        super(message, cause);
    }

    /**
     *
     * @param cause The caught exception (don't unwrap in the call to the constructor; pass the exception as-is)
     */
    public ColumnTransformException(@NotNull Throwable cause)
    {
        super(cause);
    }
}
