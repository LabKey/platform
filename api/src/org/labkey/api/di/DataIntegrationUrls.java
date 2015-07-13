package org.labkey.api.di;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

public interface DataIntegrationUrls extends UrlProvider
{
    ActionURL getBeginURL(Container container);
}
