package org.labkey.api.compliance;

import org.labkey.api.data.Container;
import org.labkey.api.query.QueryAction;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;

/**
 * Created by davebradlee on 7/27/17.
 */
public interface ComplianceService
{
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
}
