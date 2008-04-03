package org.labkey.query.reports.view;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.JspView;

import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Mar 2, 2008
 */
public class ReportsWebPartConfig extends HttpView
{
    private Portal.WebPart _webPart;

    public ReportsWebPartConfig(Portal.WebPart webPart)
    {
        _webPart = webPart;
    }

    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        JspView view = new JspView<Portal.WebPart>("/org/labkey/query/reports/view/reportsWebPartConfig.jsp", _webPart);
        include(view);
    }
}
