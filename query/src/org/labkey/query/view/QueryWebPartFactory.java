package org.labkey.query.view;

import org.labkey.api.view.*;
import org.labkey.api.query.QueryWebPart;
import org.labkey.api.data.Container;

import java.lang.reflect.InvocationTargetException;

public class QueryWebPartFactory extends WebPartFactory
{
    public QueryWebPartFactory()
    {
        super("Query", null, true, true);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
    {
        QueryWebPart ret = new QueryWebPart(portalCtx, webPart);
        populateProperties(ret, webPart.getPropertyMap());
        return ret;
    }

    public HttpView getEditView(Portal.WebPart webPart)
    {
        return new EditQueryView(webPart);
    }

    public boolean isAvailable(Container c, String location)
    {
        return location.equals(getDefaultLocation());
    }
}
