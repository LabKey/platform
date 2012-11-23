package org.labkey.api.laboratory;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 11/21/12
 * Time: 5:07 PM
 */
public interface ImportingNavItem extends NavItem
{
    public ActionURL getImportUrl(Container c, User u);

    public ActionURL getSearchUrl(Container c, User u);

    public ActionURL getBrowseUrl(Container c, User u);

    public boolean isImportIntoWorkbooks();
}
