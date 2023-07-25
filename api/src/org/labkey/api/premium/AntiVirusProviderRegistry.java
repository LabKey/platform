package org.labkey.api.premium;

import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ViewBackgroundInfo;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

public interface AntiVirusProviderRegistry
{
    AntiVirusProviderRegistry NO_OP_REGISTRY = avp -> {};

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

    default CommonsMultipartResolver getMultipartResolver(ViewBackgroundInfo info)
    {
        CommonsMultipartResolver result = new CommonsMultipartResolver();
        // Issue 47362 - configure a limit for the number of files per request
        result.getFileUpload().setFileCountMax(1_000);
        return result;
    }
}