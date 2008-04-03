package org.labkey.query.view;

import org.labkey.api.jsp.JspLoader;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.WebPartView;

import java.io.PrintWriter;

public class EditQueryView extends WebPartView
{
    Portal.WebPart _part;
    public EditQueryView(Portal.WebPart part)
    {
        _part = part;
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        EditQueryPage page = (EditQueryPage) JspLoader.createPage(getViewContext().getRequest(), EditQueryView.class, "editQueryWebPart.jsp");
        page.setWebPart(_part);
        page.setContext(getViewContext());
        page.include(new JspView(page), out);
    }
}
