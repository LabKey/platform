package org.labkey.api.exp.api;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;

/**
 * User: jgarms
 * Date: Jan 27, 2008
 */
public interface AdminUrls extends UrlProvider
{
    ActionURL getModuleErrorsUrl(Container container);
}