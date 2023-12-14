/*
 * Copyright (c) 2010-2018 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.JspContext;
import jakarta.servlet.jsp.PageContext;

/**
 * Anti-cross-site request forgery utility methods
 */
public class CSRFUtil
{
    public  static final String csrfName =    "X-LABKEY-CSRF";
    public  static final String csrfHeader =  "X-LABKEY-CSRF";
    private static final String csrfCookie =  "X-LABKEY-CSRF";
    public static final String SESSION_COOKIE_NAME = "JSESSIONID";

    public static String getExpectedToken(HttpServletRequest request, @Nullable HttpServletResponse response)
    {
        String csrf = (String)request.getAttribute(csrfName);
        if (null != csrf)
            return csrf;

        csrf = PageFlowUtil.getCookieValue(request.getCookies(), csrfCookie, null);
        if (null == csrf || !GUID.looksLikeAValidLongHash(csrf))
            csrf = null;

        if (null == csrf)
        {
            csrf = GUID.makeLongHash();
            if (response != null)
            {
                try
                {
                    Cookie c = new Cookie(csrfName, csrf);
                    // Issue 32938 - Make CSRF cookie secure (only sent by browser over HTTPS) when possible
                    if (AppProps.getInstance().isSSLRequired() || request.isSecure())
                    {
                        c.setSecure(true);
                    }
                    c.setPath(StringUtils.defaultIfEmpty(request.getContextPath(),"/"));
                    response.addCookie(c);
                }
                catch (Exception x)
                {
                    // response already committed or something I suppose
                    ExceptionUtil.logExceptionToMothership(request, x);
                }
            }
        }

        request.setAttribute(csrfName, csrf);
        return csrf;
    }


    public static String getExpectedToken(ViewContext c)
    {
        return getExpectedToken(c.getRequest(), c.getResponse());
    }


    public static String getExpectedToken(JspContext jspc)
    {
        return (String)jspc.getAttribute(csrfName, PageContext.REQUEST_SCOPE);
    }


    public static void validate(HttpServletRequest request, HttpServletResponse response) throws UnauthorizedException
    {
        String provided = request.getParameter(csrfName);
        if (StringUtils.isEmpty(provided))
            provided = request.getHeader(csrfHeader);
        if (StringUtils.isEmpty(provided))
            throw new CSRFException(request);

        String expected = getExpectedToken(request, response);
        if (provided.equals(expected))
            return;

        throw new CSRFException(request);
    }


    public static void validate(ViewContext context) throws UnauthorizedException
    {
        validate(context.getRequest(), context.getResponse());
    }
}
