package org.labkey.api.specimen;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.specimen.model.SpecimenRequestEvent;
import org.labkey.api.view.ActionURL;
import org.springframework.web.servlet.mvc.Controller;

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
    ActionURL getManageRequestStatusURL(Container c, int requestId);
    ActionURL getManageRequestURL(Container c, int requestId, @Nullable ActionURL returnUrl);
    ActionURL getSelectedSpecimensURL(Container c);
    ActionURL getSpecimenEventsURL(Container c, ActionURL returnUrl);
    ActionURL getSpecimenRequestEventDownloadURL(SpecimenRequestEvent event, String name);
    ActionURL getSpecimensURL(Container c);
    ActionURL getUploadSpecimensURL(Container c);
    ActionURL getViewRequestsURL(Container c);

    Class<? extends Controller> getClearCommentsActionClass();
    Class<? extends Controller> getShowCreateSpecimenRequestActionClass();
    Class<? extends Controller> getUpdateCommentsActionClass();
}
