/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import org.labkey.api.util.SkipMothershipLogging;

/**
 * Signals the client API caller that they somehow made an invalid request. These errors are not reported to the
 * mothership.
 * User: jeckels
 * Date: Oct 5, 2010
 */
public class ApiUsageException extends RuntimeException implements SkipMothershipLogging
{
    public ApiUsageException()
    {
        super();
    }

    public ApiUsageException(String message)
    {
        super(message);
    }

    public ApiUsageException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ApiUsageException(Throwable cause)
    {
        super(cause.getMessage() == null ? cause.toString() : cause.getMessage(), cause);
    }
}
