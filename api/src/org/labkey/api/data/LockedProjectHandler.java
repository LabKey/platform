package org.labkey.api.data;

import org.labkey.api.data.Container.LockState;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.Role;

import java.util.Set;

public interface LockedProjectHandler
{
    boolean isForbidden(Container project, User user, Set<Role> contextualRoles, LockState lockState);
}
