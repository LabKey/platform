package org.labkey.api.assay;

import org.labkey.api.services.ServiceRegistry;
import org.labkey.study.assay.ModuleAssayCollections;

/**
 * Exposes code in assay-src to classes in study, to help with assay migration process
 */
@AssayMigration
public interface AssayToStudyMigrationService
{
    static AssayToStudyMigrationService get()
    {
        return ServiceRegistry.get().getService(AssayToStudyMigrationService.class);
    }

    static void setInstance(AssayToStudyMigrationService impl)
    {
        ServiceRegistry.get().registerService(AssayToStudyMigrationService.class, impl);
    }

    ModuleAssayCollections getModuleAssayCollections();
}
