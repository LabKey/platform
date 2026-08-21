/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.api.view;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.SkipMothershipLogging;

/**
 * Exception that should convey a specific HTTP status code in the response to the client, and doesn't indicate
 * a bug or other type of server-side problem
 */
public class HttpStatusException extends RuntimeException implements SkipMothershipLogging
{
    final int status;

    public HttpStatusException(String message, @Nullable Throwable x, int status)
    {
        super(message, x);
        this.status = status;
    }


    /** @return the HTTP status code to be used for the response */
    public int getStatus()
    {
        return status;
    }

}
