package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

public interface ExpLineageService
{
    static ExpLineageService get()
    {
        return ServiceRegistry.get().getService(ExpLineageService.class);
    }

    static void setInstance(ExpLineageService impl)
    {
        ServiceRegistry.get().registerService(ExpLineageService.class, impl);
    }

    /**
     * Get the lineage for the seed Identifiable object. Typically, the seed object is an ExpMaterial,
     * an ExpData (in a DataClass), or an ExpRun.
     */
    @NotNull
    ExpLineage getLineage(Container c, User user, @NotNull Identifiable start, @NotNull ExpLineageOptions options);
}
