package org.labkey.api.assay;

import org.labkey.api.gwt.client.assay.AssayService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ViewContext;

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

    AssayService getAssayService(ViewContext context);
}
