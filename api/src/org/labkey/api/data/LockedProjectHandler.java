package org.labkey.api.data;

import org.labkey.api.data.Container.LockState;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.Role;

import java.util.Set;

/**
 * The LockedProjectHandler interface provides a method to determine if a specific project
 * is inaccessible or forbidden for a user based on various conditions. The compliance module has a mechanism
 * for locking projects for most users.
 */
public interface LockedProjectHandler
{
    boolean isForbidden(Container project, User user, Set<Role> contextualRoles, LockState lockState);
}
