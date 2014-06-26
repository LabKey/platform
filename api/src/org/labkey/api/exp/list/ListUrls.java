package org.labkey.api.exp.list;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

/**
 * User: kevink
 * Date: 6/25/14
 */
public interface ListUrls extends UrlProvider
{
    ActionURL getManageListsURL(Container c);
    ActionURL getCreateListURL(Container c);
}
