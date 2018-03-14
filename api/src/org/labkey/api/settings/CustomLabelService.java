package org.labkey.api.settings;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.services.ServiceRegistry;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public interface CustomLabelService
{
    static CustomLabelService get()
    {
        return ServiceRegistry.get(CustomLabelService.class);
    }

    void registerProvider(CustomLabelProvider customLabelProvider);
    CustomLabelProvider getCustomLabelProvider(@NotNull String name);
    Collection<CustomLabelProvider> getCustomLabelProviders();

    class CustomLabelServiceImpl implements CustomLabelService
    {
        private static final ConcurrentMap<String, CustomLabelProvider> REGISTERED_PROVIDERS = new ConcurrentHashMap<>();

        @Override
        public void registerProvider(CustomLabelProvider customLabelProvider)
        {
            if (null != REGISTERED_PROVIDERS.putIfAbsent(customLabelProvider.getName(), customLabelProvider))
                throw new IllegalStateException("There is already a registered CustomLabelProvider with name: " + customLabelProvider.getName());
        }

        @Override
        public CustomLabelProvider getCustomLabelProvider(@NotNull String name)
        {
            return REGISTERED_PROVIDERS.get(name);
        }

        @Override
        public Collection<CustomLabelProvider> getCustomLabelProviders()
        {
            return REGISTERED_PROVIDERS.values();
        }
    }
}
