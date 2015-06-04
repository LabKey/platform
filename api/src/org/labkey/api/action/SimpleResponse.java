/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.api.action;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.Nullable;

/**
 * Simple success/fail response message with optional message and optional data.
 *
 * User: kevink
 * Date: 3/30/14
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't serialize null values
public class SimpleResponse<T>
{
    private boolean _success;
    private String _message;
    private T _data;

    public SimpleResponse(boolean success)
    {
        this(success, null, null);
    }

    public SimpleResponse(boolean success, @Nullable String message)
    {
        this(success, message, null);
    }

    public SimpleResponse(boolean success, @Nullable String message, @Nullable T data)
    {
        _success = success;
        _message = message;
        _data = data;
    }

    public boolean isSuccess()
    {
        return _success;
    }

    public String getMessage()
    {
        return _message;
    }

    public T getData()
    {
        return _data;
    }

}
