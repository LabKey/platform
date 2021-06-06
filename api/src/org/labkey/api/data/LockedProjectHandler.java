package org.labkey.api.data;

import org.labkey.api.security.User;

public interface LockedProjectHandler
{
    boolean isForbidden(Container project, User user, Container.LockState lockState);
}
