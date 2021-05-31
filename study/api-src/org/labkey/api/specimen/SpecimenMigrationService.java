package org.labkey.api.specimen;

import org.labkey.api.data.Container;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.specimen.model.SpecimenRequestEvent;
import org.labkey.api.view.ActionURL;

// Temporary service that provides entry points to ease migration of code from study module to specimen module
// These should all go away once the migration is complete
public interface SpecimenMigrationService
{
    static SpecimenMigrationService get()
    {
        return ServiceRegistry.get().getService(SpecimenMigrationService.class);
    }

    static void setInstance(SpecimenMigrationService impl)
    {
        ServiceRegistry.get().registerService(SpecimenMigrationService.class, impl);
    }

    ActionURL getSpecimenRequestEventDownloadURL(SpecimenRequestEvent event, String name);
    ActionURL getOverviewURL(Container c);
}
