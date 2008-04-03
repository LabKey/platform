package org.labkey.api.view;

import org.labkey.api.security.ACL;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 8, 2006
 * Time: 10:59:13 AM
 */
public class DefaultWebPartFactory extends WebPartFactory
{
    Class<? extends WebPartView> cls;

    public DefaultWebPartFactory(String name, Class<? extends WebPartView> cls)
    {
        super(name);
        this.cls = cls;
    }
    
    public DefaultWebPartFactory(String name, String location, Class<? extends WebPartView> cls)
    {
        super(name, location);
        this.cls = cls;
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
    {
        if (!portalCtx.hasPermission(ACL.PERM_READ))
            return new HtmlView("Datasets", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

        return cls.newInstance();
    }
}
