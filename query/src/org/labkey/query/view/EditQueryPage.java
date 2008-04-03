package org.labkey.query.view;

import org.labkey.api.view.Portal;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.jsp.JspBase;
import org.labkey.api.data.Container;

abstract public class EditQueryPage extends JspBase
{
    protected ViewContext _context;
    protected Portal.WebPart _part;

    public Portal.WebPart getWebPart()
    {
        return _part;
    }

    public void setWebPart(Portal.WebPart part)
    {
        _part = part;
    }

    public void setContext(ViewContext context)
    {
        _context = context;
    }

    public ViewContext getContext()
    {
        return _context;
    }

    public Container getContainer()
    {
        return _context.getContainer();
    }

    public User getUser()
    {
        return _context.getUser();
    }
}
