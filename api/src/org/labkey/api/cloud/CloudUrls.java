package org.labkey.api.cloud;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.view.ActionURL;

/**
 * User: kevink
 * Date: 8/26/13
 */
public interface CloudUrls extends UrlProvider
{
    public ActionURL adminURL();
}
