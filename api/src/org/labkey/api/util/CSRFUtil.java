/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.PageContext;

/**
 * Anti-cross-site request forgery utility methods
 * User: matthewb
 * Date: May 10, 2010
 * Time: 1:53:33 PM
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
        if (null == csrf || csrf.length() != 32 || !StringUtils.containsOnly(csrf,"0123456789abcdef"))
            csrf = null;

        if (null == csrf)
        {
            csrf = GUID.makeHash();
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

        // try JESSIONID also for backward compatibility
        String session = PageFlowUtil.getCookieValue(request.getCookies(), SESSION_COOKIE_NAME,null);
        if (provided.equals(session))
            return;

        throw new CSRFException(request);
    }


    public static void validate(ViewContext context) throws UnauthorizedException
    {
        validate(context.getRequest(), context.getResponse());
    }
}
