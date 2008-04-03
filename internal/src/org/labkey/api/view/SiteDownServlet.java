package org.labkey.api.view;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import java.io.IOException;
import java.io.Writer;

/**
 * User: jeckels
 * Date: Jul 12, 2006
 */
public class SiteDownServlet extends HttpServlet
{
    private String _message = "This LabKey Server is currently down for maintenance.";

    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        _message = config.getInitParameter("message");
    }

    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        Writer writer = resp.getWriter();
        writer.write("<html><hread><title>LabKey Server currently unavailable</title></head>\n");
        writer.write("<body>" + _message + "</body></html>");
    }
}
