package org.labkey.api.compliance;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryAction;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;

/**
 * Created by davebradlee on 7/27/17.
 */
public interface ComplianceService
{
    String LOGGING_QUERY_TAG = " /* COMPLIANCE-LOGGABLE-QUERY */";

    static ComplianceService get()
    {
        return ServiceRegistry.get(ComplianceService.class);
    }

    static void setInstance(ComplianceService instance)
    {
        ServiceRegistry.get().registerService(ComplianceService.class, instance);
    }

    String getModuleName();
    ActionURL urlFor(Container container, QueryAction action, ActionURL queryBasedUrl);
    boolean hasElecSignPermission(@NotNull Container container, @NotNull User user);
    boolean hasViewSignedSnapshotsPermission(@NotNull Container container, @NotNull User user);
}
