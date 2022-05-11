package org.labkey.api.security;

import org.labkey.api.services.ServiceRegistry;

public interface LimitActiveUserService
{
    static void setInstance(LimitActiveUserService impl)
    {
        ServiceRegistry.get().registerService(LimitActiveUserService.class, impl);
    }

    static LimitActiveUserService get()
    {
        return ServiceRegistry.get().getService(LimitActiveUserService.class);
    }

    boolean isUserLimitReached();
}
