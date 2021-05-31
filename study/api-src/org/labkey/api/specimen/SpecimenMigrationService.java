package org.labkey.api.specimen;

import org.jetbrains.annotations.Nullable;
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
    ActionURL getShowGroupMembersURL(Container c, int rowId, @Nullable Integer locationId, @Nullable ActionURL returnUrl);
    ActionURL getUploadSpecimensURL(Container c);
    ActionURL getAutoReportListURL(Container c);
    ActionURL getShowSearchURL(Container c, boolean showVials);
    ActionURL getSpecimenRequestConfigRequiredURL(Container c);
    ActionURL getConfigureRequestabilityRulesURL(Container c);
    ActionURL getViewRequestsURL(Container c);
}
