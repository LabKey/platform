/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.gwt.server;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.module.ModuleLoader;
import org.apache.log4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.Vector;
import java.io.IOException;

/**
 * User: brittp
 * Date: Feb 2, 2007
 * Time: 2:30:51 PM
 */
public abstract class BaseRemoteService extends RemoteServiceServlet
{
    private static Logger _log = Logger.getLogger(BaseRemoteService.class);

    protected ViewContext _context;
    public BaseRemoteService(ViewContext context)
    {
        _context = context;
    }

    public Container getContainer()
    {
        return _context.getContainer();
    }

    public User getUser()
    {
        return _context.getUser();
    }

    public ActionURL getActionURL()
    {
        return _context.getActionURL();
    }

    @Override
    public String getServletName()
    {
        return getServletConfig().getServletName();
    }

    public ServletConfig getServletConfig()
    {
        return new ServletConfig()
        {
            public String getInitParameter(String string)
            {
                return null;
            }

            public Enumeration<String> getInitParameterNames()
            {
                return new Vector<String>().elements();
            }

            public ServletContext getServletContext()
            {
                return ModuleLoader.getServletContext();
            }

            public String getServletName()
            {
                return "BaseRemoteService";
            }
        };
    }

    protected void doUnexpectedFailure(Throwable failure)
    {
        failure = ExceptionUtil.unwrapException(failure);
        ExceptionUtil.logExceptionToMothership(getThreadLocalRequest(), failure);
        _log.error("GWT Service Error", failure);

        HttpServletResponse response = getThreadLocalResponse();
        try
        {
            int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            if (failure instanceof UnauthorizedException)
            {
                response.setHeader("WWW-Authenticate", "Basic realm=\"" + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDescription() + "\"");
                status = HttpServletResponse.SC_UNAUTHORIZED;
            }
            response.setContentType("text/plain");
            response.setStatus(status);
            response.getWriter().write("There was an error processing the request.");
            if (failure.getMessage() != null)
            {
                response.getWriter().write("\n" + failure.getMessage());
            }
            response.getWriter().write("\n(Error type: " + failure.getClass().getName() + ")");
        }
        catch (IOException e)
        {
            // Give up
        }
        catch (IllegalStateException e)
        {
            // Give up, response is already committed
        }

    }

    public ViewContext getViewContext()
    {
        return _context;
    }
}
