/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.view.UnauthorizedException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Anti-cross-site request forgery utility methods
 * User: matthewb
 * Date: May 10, 2010
 * Time: 1:53:33 PM
 */
public class CSRFUtil
{
    public static final String csrfName = "X-LABKEY-CSRF";
    public static final String csrfHeader = "X-LABKEY-CSRF";
    private static final boolean useSessionId = true;
    
    public static String getExpectedToken(HttpServletRequest request)
    {
        HttpSession s = request.getSession(true);
        String ret = (String)s.getAttribute(csrfName);
        if (null == ret)
        {
            // we call setAttribute() to make value available to jspContext.getAttribute();
            String t = useSessionId ? s.getId() : GUID.makeHash(s.getId());
            s.setAttribute(csrfName, t);
            ret = (String)s.getAttribute(csrfName);
        }
        return ret;
    }

    public static void validate(HttpServletRequest request) throws UnauthorizedException
    {
        String expected = getExpectedToken(request);
        String provided = request.getParameter(csrfName);
        if (null == provided || provided.length() == 0)
            provided = request.getHeader(csrfHeader);
        if (!expected.equals(provided))
            throw new CSRFException();
    }
}
