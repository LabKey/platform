/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.settings;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.services.ServiceRegistry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public interface CustomLabelService
{
    static CustomLabelService get()
    {
        return ServiceRegistry.get().getService(CustomLabelService.class);
    }

    static void setInstance(CustomLabelService impl)
    {
        ServiceRegistry.get().registerService(CustomLabelService.class, impl);
    }

    void registerProvider(CustomLabelProvider customLabelProvider);
    CustomLabelProvider getCustomLabelProvider(@NotNull String name);
    Collection<CustomLabelProvider> getCustomLabelProviders();

    Map<String, Map<String, Integer>> getCustomLabelMetrics();

    Map<String, Map<String, String>> getCustomLabels(Container container);

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

        @Override
        public Map<String, Map<String, Integer>> getCustomLabelMetrics()
        {
            Map<String, Map<String, Integer>> labelCounts = new HashMap<>();
            for (CustomLabelProvider provider : getCustomLabelProviders())
            {
                labelCounts.put(provider.getName(), provider.getMetrics());
            }
            return labelCounts;
        }

        @Override
        public Map<String, Map<String, String>> getCustomLabels(Container container)
        {
            Map<String, Map<String, String>> moduleLabels = new HashMap<>();
            for (CustomLabelProvider provider : getCustomLabelProviders())
            {
                Map<String, String> labels = provider.getCustomLabels(container);
                if (labels != null)
                    moduleLabels.put(provider.getName(), labels);
            }
            return moduleLabels;
        }
    }
}
