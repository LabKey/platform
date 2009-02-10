/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.security;

import org.labkey.api.util.HString;

import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletRequest;
import java.security.Principal;

/**
 * Created by IntelliJ IDEA.
* User: matthewb
* Date: Feb 5, 2009
* Time: 8:51:07 AM
*/
public class AuthenticatedRequest extends HttpServletRequestWrapper
{
    User _user;

    public AuthenticatedRequest(HttpServletRequest request, User user)
    {
        super(request);
        _user = null == user ? User.guest : user;
    }

    public Principal getUserPrincipal()
    {
        return _user;
    }


    public HString getParameter(HString s)
    {
        return new HString(super.getParameter(s.getSource()), true);
    }


    public HString[] getParameterValues(HString s)
    {
        String[] values = getParameterValues(s.getSource());
        if (values == null)
            return null;
        HString[] t  = new HString[values.length];
        for (int i=0 ; i<values.length ; i++)
            t[i] = new HString(values[i], true);
        return t;
    }
}
