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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Indicates that the client made a bad HTTP request, typically resulting in a 400 HTTP response code and avoiding much
 * of the standard exception logging code for server-side bugs.
 */
public class BadRequestException extends HttpStatusException
{
    /*
     * HowBad controls how BlockListFilter deals with this exception.
     *   Malicious means this request is prejudged as a potentially malicious request
     *   MaybeBad defers to BlockListFilter.isSuspicious() to decide
     *   LetItGo means this request has been rejected for a benign reason (e.g. missing API parameter)
     */
    public enum HowBad
    {
        Malicious     // treat this like a malicious request
        {
            @Override
            public boolean isSuspiciousRequest(HttpServletRequest req, boolean isSuspicious)
            {
                return true;
            }
        },
        MaybeBad   // treat like NOT FOUND (.e.g look for sql injection etc or signs of malicious request)
        {
            @Override
            public boolean isSuspiciousRequest(HttpServletRequest req, boolean isSuspicious)
            {
                return isSuspicious;
            }
        },
        LetItGo     // Just a bad API call or some such (consider not using BadRequestException?)
        {
            @Override
            public boolean isSuspiciousRequest(HttpServletRequest req, boolean isSuspicious)
            {
                return true;
            }
        };

        abstract boolean isSuspiciousRequest(HttpServletRequest req, boolean isSuspicious);
    };

    private final HowBad severity;

    public BadRequestException()
    {
        this(null);
    }

    public BadRequestException(String message)
    {
        this(message, null, HttpServletResponse.SC_BAD_REQUEST, HowBad.MaybeBad);
    }

    public BadRequestException(String message, @Nullable Exception x)
    {
        this(message, x, HttpServletResponse.SC_BAD_REQUEST, HowBad.MaybeBad);
    }

    public BadRequestException(String message, @Nullable Exception x, int httpStatusCode)
    {
        this(message, x, httpStatusCode, HttpServletResponse.SC_METHOD_NOT_ALLOWED == httpStatusCode ? HowBad.Malicious : HowBad.MaybeBad);
    }

    public BadRequestException(String message, @NotNull HowBad severity)
    {
        this(message, null, HttpServletResponse.SC_BAD_REQUEST, severity);
    }

    BadRequestException(String message, @Nullable Exception x, int httpStatusCode, HowBad severity)
    {
        super(StringUtils.defaultIfEmpty(message, "BAD REQUEST"), x, httpStatusCode);
        this.severity = severity;
    }

    /** isSuspicious is the result of BlockListFilter.isSuspicious() */
    public boolean isSuspiciousRequest(HttpServletRequest req, boolean isSuspicious)
    {
        return severity.isSuspiciousRequest(req, isSuspicious);
    }
}
