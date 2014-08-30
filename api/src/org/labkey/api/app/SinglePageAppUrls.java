package org.labkey.api.app;


import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

/**
 * Created by cnathe on 8/29/14.
 */
public interface SinglePageAppUrls extends UrlProvider
{
    ActionURL getManageAppURL(Container container);
}
