/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.HttpJspPage;
import java.util.Enumeration;

public class JspLoader
{
    private static Logger _log = Logger.getLogger(JspLoader.class);
    private static final JspClassLoader _jspClassLoader;

    static
    {
        if (AppProps.getInstance().isDevMode())
        {
            if (AppProps.getInstance().getJavaVersion().startsWith("1.6"))
            {
                _jspClassLoader = new RecompilingJspClassLoader();
            }
            else
            {
                _log.warn("Disabling auto-recompilation of .jsp files; this feature requires Java 1.6.");
                _jspClassLoader = new JspClassLoader();
            }
        }
        else
        {
            _jspClassLoader = new JspClassLoader();
        }
    }

    /**
     * Create a new JSP page.
     *
     * @param request Used to get the ServletContext
     * @param packageName Dot separated package where the JSP file was in the source tree.  May be null, in which case
     * "jspFile" should be a full path to the page, starting with "/"
     * @param jspFile Path to the JSP from the package in which it resides.  For JSP files that are in the same
     * directory as their controller, pass the package name of the controller, and the filename of the JSP with no
     * "/"
     * @return inited page
     */
    public static HttpJspPage createPage(HttpServletRequest request, String packageName, String jspFile)
    {
        try
        {
            ServletContext context = request.getSession(true).getServletContext();
            Class clazz = _jspClassLoader.loadClass(context, packageName, jspFile);
            HttpJspPage ret = (HttpJspPage) clazz.newInstance();
            ret.init(new JspServletConfig(context));
            return ret;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }


    /**
     * Instantiates a JSP class in a particular directory.
     *
     * @param clazz   A class which is in the same folder as the JSP folder.
     * @param jspFile Name of the JSP file, without the path.
     */
    public static HttpJspPage createPage(HttpServletRequest request, Class clazz, String jspFile)
    {
        return createPage(request, clazz.getPackage().getName(), jspFile);
    }


    public static HttpJspPage createPage(ViewContext context, Class clazz, String jspFile)
    {
        return createPage(context.getRequest(), clazz.getPackage().getName(), jspFile);
    }


    private static class JspServletConfig implements ServletConfig
    {
        ServletContext _context;

        public JspServletConfig(ServletContext context)
        {
            _context = context;
        }

        public String getServletName()
        {
            return "jsp";
        }

        public ServletContext getServletContext()
        {
            return _context;
        }

        public String getInitParameter(String name)
        {
            return null;
        }

        public Enumeration getInitParameterNames()
        {
            return null;
        }
    }
}
