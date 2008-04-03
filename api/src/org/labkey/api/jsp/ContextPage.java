package org.labkey.api.jsp;

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

abstract public class ContextPage extends JspBase
{
    private ViewContext _context;
    public ContextPage()
    {
    }

    public void setViewContext(ViewContext context)
    {
        _context = context;
    }

    public ViewContext getViewContext()
    {
        return _context;
    }

    public ActionURL getActionURL()
    {
        return _context.getActionURL();
    }

    public Container getContainer()
    {
        return _context.getContainer();
    }

    public User getUser()
    {
        return _context.getUser();
    }

    public boolean hasPermission(int perm)
    {
        return getContainer().hasPermission(_context.getUser(), perm);
    }
}
