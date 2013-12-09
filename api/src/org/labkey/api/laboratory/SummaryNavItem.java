package org.labkey.api.laboratory;

import org.labkey.api.data.Container;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.security.User;

/**
 * User: bimber
 * Date: 11/8/13
 * Time: 7:02 AM
 */
public interface SummaryNavItem extends NavItem
{
    public Long getRowCount(Container c, User u);
}
