package org.labkey.api.gwt.server;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;
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

    public ServletConfig getServletConfig()
    {
        return new ServletConfig()
        {

            public String getInitParameter(String string)
            {
                return null;
            }

            public Enumeration getInitParameterNames()
            {
                return new Vector().elements();
            }

            public ServletContext getServletContext()
            {
                return _context.getRequest().getSession().getServletContext();
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
            response.setContentType("text/plain");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
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
}
