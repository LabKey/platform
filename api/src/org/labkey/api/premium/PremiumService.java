package org.labkey.api.premium;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

public interface PremiumService
{
    static @NotNull PremiumService get()
    {
        // Return default service if premium module not registered
        PremiumService service = ServiceRegistry.get().getService(PremiumService.class);
        if (null == service)
            service = new DefaultPremiumService();
        return service;
    }

    boolean isDisableFileUploadSupported();

    boolean isFileUploadDisabled();

    boolean isFileWatcherSupported();

    default CommonsMultipartResolver getMultipartResolver()
    {
        return new CommonsMultipartResolver();
    }

    void registerAntiVirusProvider(AntiVirusProvider avp);

    static void setInstance(PremiumService instance)
    {
        ServiceRegistry.get().registerService(PremiumService.class, instance);
    }

    interface AntiVirusProvider
    {
        @NotNull String getId();             // something unique e.g. className
        @NotNull String getDescription();    // e.g. ClamAV Daemon
        @Nullable ActionURL getConfigurationURL();
        @NotNull AntiVirusService getService();
    }

    class DefaultPremiumService implements PremiumService
    {
        @Override
        public boolean isDisableFileUploadSupported()
        {
            return false;
        }

        @Override
        public boolean isFileUploadDisabled()
        {
            return false;
        }

        @Override
        public boolean isFileWatcherSupported()
        {
            return false;
        }

        @Override
        public void registerAntiVirusProvider(AntiVirusProvider avp) {}
    }
}
