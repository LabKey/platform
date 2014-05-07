/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;

/**
 * User: kevink
 * Date: 3/30/14
 *
 * Simple success/fail response message with optional message.
 */
public class SimpleResponse
{
    private boolean _success;
    private String _message;

    public SimpleResponse(boolean success)
    {
        this(success, null);
    }

    public SimpleResponse(boolean success, @Nullable String message)
    {
        _success = success;
        _message = message;
    }

    public boolean isSuccess()
    {
        return _success;
    }

    public String getMessage()
    {
        return _message;
    }
}
