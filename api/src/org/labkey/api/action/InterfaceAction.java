/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.ModelAndView;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.module.ModuleLoader;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletOutputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;

/**
 * User: matthewb
 * Date: Sep 24, 2007
 * Time: 3:48:55 PM
 */
public abstract class InterfaceAction<I> implements Controller
{
    int _defaultVersion = 0;
    Map<String,Method> _methods;

    public abstract I getInstance(int version);

    protected InterfaceAction(Class<I> cls, String moduleName)
    {
        if (!cls.isInterface())
            throw new IllegalArgumentException("interface expected");

        Method[] methods = cls.getMethods();
        _methods = new HashMap<>(methods.length * 2);
        for (Method m : methods)
        {
            if ("getClass".equals(m.getName()))
                continue;
            if (_methods.containsKey(m.getName()))
            {
                Logger.getLogger(InterfaceAction.class).warn("method can not be overloaded: " + m.getName());
                //throw new IllegalArgumentException("method can not be overloaded: " + m.getName());
            }
            _methods.put(m.getName(),m);
        }

        _defaultVersion = (int)(ModuleLoader.getInstance().getModule(moduleName).getVersion()*1000);
    }


    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        ByteArrayOutputStream bufReturn;

        try
        {
            String methodName = StringUtils.trimToNull(request.getParameter("_method"));
            Method method = _methods.get(methodName);
            Object args[] = (Object[])new ObjectInputStream(request.getInputStream()).readObject();

            if (null == method || !"POST".equals(request.getMethod()) || null == args)
            {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return null;
            }

            int version = _defaultVersion; // UNDONE
            Object obj = getInstance(version);
            Object ret = method.invoke(obj, args);

            bufReturn = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bufReturn);
            oos.writeObject(ret);
            oos.close();
        }
        catch (Throwable x)
        {
            bufReturn = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bufReturn);
            oos.writeObject(x);
            oos.close();
        }

        try
        {
            response.setContentType("application/octet-stream");
            response.setContentLength(bufReturn.size());
            ServletOutputStream out = response.getOutputStream();
            bufReturn.writeTo(out);
            out.close();
            return null;
        }
        catch (Throwable t)
        {
            Logger.getLogger(InterfaceAction.class).error("Unexpected error", t);
            t.printStackTrace(System.err);
            return null;
        }
    }
}
