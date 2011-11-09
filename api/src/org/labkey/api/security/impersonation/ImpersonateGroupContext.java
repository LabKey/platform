package org.labkey.api.security.impersonation;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

/**
 * User: adam
 * Date: 11/9/11
 * Time: 10:53 AM
 */
public class ImpersonateGroupContext implements ImpersonationContext
{
    @Override
    public boolean isImpersonated()
    {
        return true;
    }

    @Override
    public boolean isAllowedRoles()
    {
        return false;
    }

    @Override
    public Container getStartingProject()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Container getImpersonationProject()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public User getImpersonatingUser()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getNavTreeCacheKey()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public URLHelper getReturnURL()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
