package org.labkey.api.jsp;

import org.labkey.api.util.MemTracker;
import org.labkey.api.view.HttpView;

import javax.servlet.http.HttpServlet;
import javax.servlet.jsp.HttpJspPage;

/**
 * User: adam
 * Date: Aug 10, 2010
 * Time: 4:05:51 PM
 */

// Trivially simple base class for JSP templates that aren't rendering HTML (see JspTemplate)
public abstract class JspContext extends HttpServlet implements HttpJspPage
{
    protected JspContext()
    {
        assert MemTracker.put(this);
    }

    public void jspInit()
    {
    }

    public void jspDestroy()
    {
    }

    public Object getModelBean()
    {
        return HttpView.currentModel();
    }
}
