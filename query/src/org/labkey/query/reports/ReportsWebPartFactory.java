package org.labkey.query.reports;

import org.labkey.api.view.*;
import org.labkey.query.reports.view.ReportsWebPart;
import org.labkey.query.reports.view.ReportsWebPartConfig;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Mar 2, 2008
 */
public class ReportsWebPartFactory extends WebPartFactory
{
    public ReportsWebPartFactory()
    {
        super("Report", null, true, true);
    }
    
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        ReportsWebPart wp = new ReportsWebPart(portalCtx, webPart);
        populateProperties(wp, webPart.getPropertyMap());

        return wp;
    }

    public HttpView getEditView(Portal.WebPart webPart)
    {
        return new ReportsWebPartConfig(webPart);
    }
}
