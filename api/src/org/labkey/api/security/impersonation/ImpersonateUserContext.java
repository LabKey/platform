package org.labkey.api.security.impersonation;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.GUID;
import org.labkey.api.util.URLHelper;

/**
 * User: adam
 * Date: 11/9/11
 * Time: 4:25 AM
 */
public class ImpersonateUserContext implements ImpersonationContext
{
    private @Nullable Container _project;
    private User _impersonatingUser;
    private URLHelper _returnURL;

    public ImpersonateUserContext(@Nullable Container project, User impersonatingUser, URLHelper returnURL)
    {
        _project = project;
        _impersonatingUser = impersonatingUser;
        _returnURL = returnURL;
    }

    @Override
    public boolean isImpersonated()
    {
        return true;
    }

    @Override
    public Container getStartingProject()
    {
        return _project;
    }

    @Override
    public Container getImpersonationProject()
    {
        return _project;
    }

    @Override
    public boolean isAllowedRoles()
    {
        // Don't allow global roles (site admin, developer, etc.) if user is being impersonated within a project
        return null == _project;
    }

    @Override
    public User getImpersonatingUser()
    {
        return _impersonatingUser;
    }

    @Override
    public String getNavTreeCacheKey()
    {
        // NavTree for user being impersonated will be different per impersonating user per project
        String suffix = "/impersonatingUser=" + getImpersonatingUser().getUserId();

        if (null != _project)
            suffix += "/impersonationProject=" + getImpersonationProject().getId();

        return suffix;
    }

    @Override
    public URLHelper getReturnURL()
    {
        return _returnURL;
    }

    // We stash simple properties (container and user id) in session and turn them into a context with objects on each request
    public static class ImpersonateUserContextFactory implements ImpersonationContextFactory
    {
        private final @Nullable GUID _projectId;
        private final int _impersonatingUserId;
        private final URLHelper _returnURL;

        public ImpersonateUserContextFactory(@Nullable Container project, User impersonatingUser, URLHelper returnURL)
        {
            _projectId = null != project ? project.getEntityId() : null;
            _impersonatingUserId = impersonatingUser.getUserId();
            _returnURL = returnURL;
        }

        @Override
        public ImpersonationContext getImpersonationContext()
        {
            Container project = (null != _projectId ? ContainerManager.getForId(_projectId) : null);
            User impersonatingUser = UserManager.getUser(_impersonatingUserId);
            return new ImpersonateUserContext(project, impersonatingUser, _returnURL);
        }
    }
}
