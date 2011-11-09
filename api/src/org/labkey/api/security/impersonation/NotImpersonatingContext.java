package org.labkey.api.security.impersonation;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

/**
 * User: adam
 * Date: 11/9/11
 * Time: 5:18 AM
 */
public class NotImpersonatingContext implements ImpersonationContext
{
    @Override
    public boolean isImpersonated()
    {
        return false;
    }

    @Override
    public Container getStartingProject()
    {
        return null;
    }

    @Override
    public Container getImpersonationProject()
    {
        return null;
    }

    @Override
    public boolean isAllowedRoles()
    {
        return true;
    }

    @Override
    public User getImpersonatingUser()
    {
        return null;
    }

    @Override
    public String getNavTreeCacheKey()
    {
        return "";
    }

    @Override
    public URLHelper getReturnURL()
    {
        return null;
    }
}
