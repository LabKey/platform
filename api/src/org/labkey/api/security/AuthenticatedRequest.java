/*
 * Copyright (c) 2009 LabKey Corporation
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Map;

/**
 * User: matthewb
 * Date: Feb 5, 2009
 * Time: 8:51:07 AM
 */
public class AuthenticatedRequest extends HttpServletRequestWrapper
{
    private final User _user;

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

    // Methods below use reflection to pull Tomcat-specific implementation bits out of the request.  This can be helpful
    // for low-level, temporary debugging, but it's not portable across servlet containers or versions.

    public HttpServletRequest getInnermostRequest() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, NoSuchFieldException
    {
        Object innerRequest = invokeMethod(this, HttpServletRequestWrapper.class, "_getHttpServletRequest");
        return (HttpServletRequest)getFieldValue(innerRequest, "request");
    }

    public Map getAttributeMap() throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        return (Map)getFieldValue(getInnermostRequest(), "attributes");
    }

    // Uses reflection to access public or private fields by name.
    private Object getFieldValue(Object o, String fieldName) throws NoSuchFieldException, IllegalAccessException
    {
        Field field = o.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(o);
    }

    // Uses reflection to invoke public or private methods by name.
    private Object invokeMethod(Object o, Class clazz, String methodName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        Method method = clazz.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(o);
    }
}
