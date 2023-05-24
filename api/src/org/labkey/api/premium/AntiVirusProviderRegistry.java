package org.labkey.api.premium;

import org.labkey.api.services.ServiceRegistry;

public interface AntiVirusProviderRegistry
{
    AntiVirusProviderRegistry NO_OP_REGISTRY = avp -> {
    };

    static AntiVirusProviderRegistry get()
    {
        AntiVirusProviderRegistry registry = ServiceRegistry.get().getService(AntiVirusProviderRegistry.class);
        if (null == registry)
            registry = NO_OP_REGISTRY;

        return registry;
    }

    static void setInstance(AntiVirusProviderRegistry impl)
    {
        ServiceRegistry.get().registerService(AntiVirusProviderRegistry.class, impl);
    }

    void registerAntiVirusProvider(AntiVirusProvider avp);
}