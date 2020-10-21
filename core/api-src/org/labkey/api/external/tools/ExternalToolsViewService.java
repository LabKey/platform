package org.labkey.api.external.tools;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.services.ServiceRegistry;

import java.util.Collection;

public interface ExternalToolsViewService
{
    static @NotNull ExternalToolsViewService get()
    {
        return ServiceRegistry.get().getService(ExternalToolsViewService.class);
    }

    static void setInstance(ExternalToolsViewService impl)
    {
        ServiceRegistry.get().registerService(ExternalToolsViewService.class, impl);
    }

    /**
     * @param provider Module specific ExternalToolsViewProvider implementation
     */
    void registerExternalAccessViewProvider(ExternalToolsViewProvider provider);

    /**
     * @return Collection of module specific ExternalToolsViewProviders
     */
    Collection<ExternalToolsViewProvider> getExternalAccessViewProviders();
}
