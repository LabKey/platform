package org.labkey.api.data.dialect;

import org.labkey.api.services.ServiceRegistry;

public interface PostgreSqlService
{
    static PostgreSqlService get()
    {
        return ServiceRegistry.get().getService(PostgreSqlService.class);
    }

    static void setInstance(PostgreSqlService impl)
    {
        ServiceRegistry.get().registerService(PostgreSqlService.class, impl);
    }

    BasePostgreSqlDialect getDialect();
}
