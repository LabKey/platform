package org.labkey.api.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.PlateTypeHandler;
import org.labkey.api.study.assay.AssaySchema;

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

    AssaySchema getAssaySchema(User user, Container container, @Nullable Container targetStudy);

    PlateTypeHandler getPlateTypeHandler(String plateTypeName);
}
