/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.jsp;

import org.apache.log4j.Logger;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.UnexpectedException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.jsp.HttpJspPage;
import java.util.Enumeration;

public class JspLoader
{
    private static Logger _log = Logger.getLogger(JspLoader.class);
    private static final JspClassLoader _jspClassLoader;

    static
    {
        if (AppProps.getInstance().isRecompileJspEnabled())
        {
            _jspClassLoader = new RecompilingJspClassLoader();
        }
        else
        {
            _jspClassLoader = new JspClassLoader();
        }

        ContextListener.addStartupListener(new StartupListener()
        {
            @Override
            public String getName()
            {
                return "JspLoader";
            }

            @Override
            public void moduleStartupComplete(ServletContext servletContext)
            {
                // tell jspClassLoader to rescan modules
                _jspClassLoader.resetClassLoader();
            }
        });
    }


    /**
     * Create a new JSP page.
     *
     * @param jspFile Full path to the JSP page, starting with "/"
     * @return inited page
     */
    public static HttpJspPage createPage(String jspFile)
    {
        try
        {
            ServletContext context = ModuleLoader.getServletContext();
            Class<HttpJspPage> clazz = _jspClassLoader.loadClass(context, jspFile);
            HttpJspPage ret = clazz.getConstructor().newInstance();
            ret.init(new JspServletConfig(context));
            return ret;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public static Class loadClass(String jspFile)
    {
        try
        {
            ServletContext context = ModuleLoader.getServletContext();
            Class<HttpJspPage> clazz = _jspClassLoader.loadClass(context, jspFile);
            return clazz;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }


    private static class JspServletConfig implements ServletConfig
    {
        private final ServletContext _context;

        public JspServletConfig(ServletContext context)
        {
            _context = context;
        }

        @Override
        public String getServletName()
        {
            return "jsp";
        }

        @Override
        public ServletContext getServletContext()
        {
            return _context;
        }

        @Override
        public String getInitParameter(String name)
        {
            return null;
        }

        @Override
        public Enumeration<String> getInitParameterNames()
        {
            return null;
        }
    }
}
