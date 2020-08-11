/*
 * Copyright (c) 2014-2016 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.util.SkipMothershipLogging;

import javax.servlet.http.HttpServletResponse;

/**
 * Indicates that the client made a bad HTTP request, resulting in a 400 HTTP response code and avoiding much
 * of the standard exception logging code for server-side bugs.
 */
public class BadRequestException extends RuntimeException implements SkipMothershipLogging
{
    final int status;
    boolean _useBasicAuthentication = false;

    public BadRequestException(String message, Exception x)
    {
        this(StringUtils.defaultIfEmpty(message, "BAD REQUEST"), HttpServletResponse.SC_BAD_REQUEST, x);
    }

    public BadRequestException(String message, int status)
    {
        super(message);
        this.status = status;
    }

    public BadRequestException(String message, int status, Exception x)
    {
        super(message, x);
        this.status = status;
    }

    public int getStatus()
    {
        return status;
    }
}