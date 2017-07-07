/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.core.statistics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.stats.AnalyticsProvider;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.stats.ColumnAnalyticsProvider;
import org.labkey.api.stats.QueryAnalyticsProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.QuerySettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AnalyticsProviderRegistryImpl implements AnalyticsProviderRegistry
{
    private static final Collection<AnalyticsProvider> REGISTERED_PROVIDERS = new CopyOnWriteArrayList<>();

    @Override
    public void registerProvider(AnalyticsProvider analyticsProvider)
    {
        // TODO validate that provider name isn't already registered
        REGISTERED_PROVIDERS.add(analyticsProvider);
    }

    @Override
    public ColumnAnalyticsProvider getColumnAnalyticsProvider(@NotNull String name)
    {
        for (AnalyticsProvider registeredProvider : REGISTERED_PROVIDERS)
        {
            if (registeredProvider instanceof ColumnAnalyticsProvider && name.equalsIgnoreCase(registeredProvider.getName()))
            {
                return (ColumnAnalyticsProvider) registeredProvider;
            }
        }
        return null;
    }

    @Override
    public Collection<ColumnAnalyticsProvider> getColumnAnalyticsProviders(@Nullable ColumnInfo columnInfo, boolean sort)
    {
        List<ColumnAnalyticsProvider> providers = new ArrayList<>();
        for (AnalyticsProvider registeredProvider : REGISTERED_PROVIDERS)
        {
            if (registeredProvider instanceof ColumnAnalyticsProvider)
            {
                ColumnAnalyticsProvider colProvider = (ColumnAnalyticsProvider) registeredProvider;
                if (columnInfo == null || colProvider.isApplicable(columnInfo))
                {
                    providers.add(colProvider);
                }
            }
        }

        if (sort)
        {
            Collections.sort(providers);
        }

        return providers;
    }

    @Override
    public Collection<QueryAnalyticsProvider> getQueryAnalyticsProviders(@Nullable QuerySettings settings)
    {
        List<QueryAnalyticsProvider> providers = new ArrayList<>();
        for (AnalyticsProvider registeredProvider : REGISTERED_PROVIDERS)
        {
            if (registeredProvider instanceof QueryAnalyticsProvider)
            {
                QueryAnalyticsProvider queryProvider = (QueryAnalyticsProvider) registeredProvider;
                if (settings == null || queryProvider.isApplicable(settings))
                {
                    providers.add(queryProvider);
                }
            }
        }

        return providers;
    }

    @Override
    public Collection<AnalyticsProvider> getAllAnalyticsProviders()
    {
        return Collections.unmodifiableCollection(REGISTERED_PROVIDERS);
    }
}
