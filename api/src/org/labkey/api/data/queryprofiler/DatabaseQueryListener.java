package org.labkey.api.data.queryprofiler;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.security.User;

/**
 * User: jeckels
 * Date: 2/13/14
 */
public interface DatabaseQueryListener
{
    public void queryInvoked(DbScope scope, String sql, User user, Container container);
}
