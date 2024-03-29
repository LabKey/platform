/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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
import org.jetbrains.annotations.Nullable;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Signals to the HTTP client that the request is not authorized, via a 401 status code.
 */
public class UnauthorizedException extends HttpStatusException
{
    /** Options for how the client should be informed of not being allowed to see a resource */
    public enum Type
    {
        /** Redirect the browser to a different URL to render a login form */
        redirectToLogin,
        /** Send a 401, but signal that the server would accept HTTP BasicAuth credentials */
        sendBasicAuth,
        /** Send a 401 and don't solicit BasicAuth credentials */
        sendUnauthorized
    };

    Type _type = Type.redirectToLogin;

    public UnauthorizedException()
    {
        this(null);
    }

    public UnauthorizedException(String message)
    {
        super(StringUtils.defaultIfEmpty(message, "User does not have permission to perform this operation."), null, HttpServletResponse.SC_UNAUTHORIZED);
    }

    public UnauthorizedException(String message, Throwable cause)
    {
        super(StringUtils.defaultIfEmpty(message, "User does not have permission to perform this operation."), cause, HttpServletResponse.SC_UNAUTHORIZED);
    }

    public void setType(Type type)
    {
        _type = type;
    }

    public Type getType()
    {
        return _type;
    }

    /**
     * An additional sub-message to show on the error page. If null no additional message is shown.
     * @return A String with additional advice for the unauthorized user
     */
    public @Nullable String getAdvice()
    {
        return "Please contact this server's administrator to gain access.";
    }
}