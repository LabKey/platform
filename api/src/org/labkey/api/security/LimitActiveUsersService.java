package org.labkey.api.security;

import org.labkey.api.services.ServiceRegistry;

public interface LimitActiveUsersService
{
    static void setInstance(LimitActiveUsersService impl)
    {
        ServiceRegistry.get().registerService(LimitActiveUsersService.class, impl);
    }

    static LimitActiveUsersService get()
    {
        return ServiceRegistry.get().getService(LimitActiveUsersService.class);
    }

    boolean isUserLimitReached();
}
