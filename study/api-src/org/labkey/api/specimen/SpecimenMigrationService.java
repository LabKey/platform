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

    ActionURL getAutoReportListURL(Container c);
    ActionURL getConfigureRequestabilityRulesURL(Container c);
    ActionURL getManageActorsURL(Container c);
    ActionURL getManageDisplaySettings(Container c);
    ActionURL getManageNotificationsURL(Container c);
    ActionURL getManageRepositorySettingsURL(Container c);
    ActionURL getManageDefaultReqsSettingsURL(Container c);
    ActionURL getManageRequestStatusURL(Container c, int requestId);
    ActionURL getManageStatusesURL(Container c);
    ActionURL getOverviewURL(Container c);
    ActionURL getShowGroupMembersURL(Container c, int rowId, @Nullable Integer locationId, @Nullable ActionURL returnUrl);
    ActionURL getShowSearchURL(Container c, boolean showVials);
    ActionURL getSpecimensURL(Container c);
    ActionURL getSpecimensURL(Container c, boolean showVials);
    ActionURL getSpecimenEventsURL(Container c, ActionURL returnUrl);
    ActionURL getSpecimenRequestConfigRequiredURL(Container c);
    ActionURL getSpecimenRequestEventDownloadURL(SpecimenRequestEvent event, String name);
    ActionURL getUploadSpecimensURL(Container c);
    ActionURL getViewRequestsURL(Container c);
    ActionURL getManageRequestURL(Container c, int requestId, @Nullable ActionURL returnUrl);

    Class<? extends Controller> getShowCreateSpecimenRequestActionClass();
    Class<? extends Controller> getShowAPICreateSpecimenRequestActionClass();
    Class<? extends Controller> getExtendedSpecimenRequestActionClass();
    Class<? extends Controller> getRemoveRequestSpecimensActionClass();
    Class<? extends Controller> getImportVialIdsActionClass();
    Class<? extends Controller> getManageRequestActionClass();
    Class<? extends Controller> getClearCommentsActionClass();
    Class<? extends Controller> getUpdateCommentsActionClass();
    Class<? extends Controller> getDeleteRequestActionClass();
    Class<? extends Controller> getSubmitRequestActionClass();
}
