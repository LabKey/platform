package org.labkey.api.migration;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.services.ServiceRegistry;

import java.util.Collection;

public interface ExperimentDeleteService
{
    static @NotNull ExperimentDeleteService get()
    {
        ExperimentDeleteService ret = ServiceRegistry.get().getService(ExperimentDeleteService.class);
        if (ret == null)
            throw new IllegalStateException("ExperimentDeleteService not found");
        return ret;
    }

    static void setInstance(ExperimentDeleteService impl)
    {
        ServiceRegistry.get().registerService(ExperimentDeleteService.class, impl);
    }

    /**
     * Deletes all rows from exp.Data, exp.Object, and related tables associated with the provided ObjectIds
     */
    void deleteDataRows(Collection<Long> objectIds);
}
