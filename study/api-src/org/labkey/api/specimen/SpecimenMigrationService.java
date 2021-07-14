package org.labkey.api.specimen;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
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

    ActionURL getBeginURL(Container c);
    ActionURL getInsertSpecimenQueryRowURL(Container c, String schemaName, TableInfo table);
    ActionURL getSelectedSpecimensURL(Container c);
    ActionURL getSpecimenEventsURL(Container c, ActionURL returnUrl);
    ActionURL getSpecimenRequestEventDownloadURL(SpecimenRequestEvent event, String name);
    ActionURL getSpecimensURL(Container c);
    ActionURL getUpdateSpecimenQueryRowURL(Container c, String schemaName, TableInfo table);
}
