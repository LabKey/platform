package org.labkey.api.settings;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.services.ServiceRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

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
        private static final Collection<CustomLabelProvider> REGISTERED_PROVIDERS = new CopyOnWriteArrayList<>();

        @Override
        public void registerProvider(CustomLabelProvider customLabelProvider)
        {
            REGISTERED_PROVIDERS.add(customLabelProvider);
        }

        @Override
        public CustomLabelProvider getCustomLabelProvider(@NotNull String name)
        {
            for (CustomLabelProvider registeredProvider : REGISTERED_PROVIDERS)
            {
                if (name.equalsIgnoreCase(registeredProvider.getName()))
                {
                    return registeredProvider;
                }
            }
            return null;
        }

        @Override
        public Collection<CustomLabelProvider> getCustomLabelProviders()
        {
            return Collections.unmodifiableCollection(REGISTERED_PROVIDERS);
        }
    }
}
