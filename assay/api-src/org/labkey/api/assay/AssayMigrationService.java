package org.labkey.api.assay;

import org.labkey.api.services.ServiceRegistry;

@AssayMigration
public interface AssayMigrationService
{
    static AssayMigrationService get()
    {
        return ServiceRegistry.get().getService(AssayMigrationService.class);
    }

    static void setInstance(AssayMigrationService impl)
    {
        ServiceRegistry.get().registerService(AssayMigrationService.class, impl);
    }
}
