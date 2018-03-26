package org.labkey.api.view.template;

import org.labkey.api.services.ServiceRegistry;

import java.util.function.Consumer;

public interface WarningService
{
    static WarningService get()
    {
        return ServiceRegistry.get().getService(WarningService.class);
    }

    static void setInstance(WarningService impl)
    {
        ServiceRegistry.get().registerService(WarningService.class, impl);
    }

    void register(WarningProvider provider);
    void forEachProvider(Consumer<WarningProvider> consumer);
}
