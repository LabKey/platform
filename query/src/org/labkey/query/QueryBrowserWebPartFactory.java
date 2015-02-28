package org.labkey.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartConfigurationException;
import org.labkey.api.view.WebPartView;

public class QueryBrowserWebPartFactory extends BaseWebPartFactory
{
    public static final String NAME = "Query Browser";

    public QueryBrowserWebPartFactory()
    {
        super(NAME);
    }

    @Override
    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart) throws WebPartConfigurationException
    {
        JspView view = new JspView("/org/labkey/query/view/browse.jsp");
        view.setTitle(NAME);
        view.setFrame(WebPartView.FrameType.PORTAL);
        return view;
    }
}
