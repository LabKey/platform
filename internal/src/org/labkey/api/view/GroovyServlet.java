/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import org.apache.log4j.Category;
import org.labkey.api.data.ContainerManager;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.io.IOException;


public class GroovyServlet extends HttpServlet
{
    static Category _log = Category.getInstance(ViewServlet.class);

    public static String URL_HELPER = "_urlHelper";
    private static ServletContext _servletContext = null;


    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        try
        {
            response.setContentType("text/html;charset=UTF-8");
            response.setHeader("Expires", "Sun, 01 Jan 2000 00:00:00 GMT");
            response.setHeader("Pragma", "no-cache");

            GroovyView groovyView = new GroovyView(request.getServletPath());
            ViewContext context = groovyView.getViewContext();
            context.setContainer(ContainerManager.getRoot()); // UNDONE: avoids a stack-overlow in GroovyView

            HttpView.include(groovyView, request, response);
        }
        catch (Exception x)
        {
            Throwable root = x;
            if (x instanceof ServletException && null != x.getCause())
                root = x.getCause();

            // this is probably redundant if the expected error handling works properly
            log("GroovyServlet.service", root);

            if (x instanceof ServletException)
                throw (ServletException) x;
            if (x instanceof IOException)
                throw (IOException) x;

            ServletException wrapper = new ServletException(x);
            wrapper.initCause(x);
            throw wrapper;
        }
    }


    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        _servletContext = config.getServletContext();
        _log.info("ViewServlet.init: " + config);
    }


    public static ServletContext getViewServletContext()
    {
        return _servletContext;
    }


    public static void _logError(String s, Throwable t)
    {
        _log.error(s, t);
        _servletContext.log(s, t);
    }


    public void log(String s, Throwable t)
    {
        _logError(s, t);
    }

}
