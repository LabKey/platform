package org.labkey.biotrue.controllers;

import org.labkey.api.view.*;

public class BtOverviewWebPart extends HtmlView
{
    static public final WebPartFactory FACTORY = new WebPartFactory("BioTrue Connector Overview")
    {
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            return new BtOverviewWebPart(portalCtx);
        }
    };

    public BtOverviewWebPart(ViewContext portalCtx) throws Exception
    {
        super(new BtOverview(portalCtx.getUser(), portalCtx.getContainer()).toString());
        setTitle("Server Management");
    }

}
