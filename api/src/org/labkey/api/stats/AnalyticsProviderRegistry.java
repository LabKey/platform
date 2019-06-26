/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
package org.labkey.api.stats;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.services.ServiceRegistry;

import java.util.Collection;

public interface AnalyticsProviderRegistry
{
    static AnalyticsProviderRegistry get()
    {
        return ServiceRegistry.get().getService(AnalyticsProviderRegistry.class);
    }

    static void setInstance(AnalyticsProviderRegistry impl)
    {
        ServiceRegistry.get().registerService(AnalyticsProviderRegistry.class, impl);
    }

    void registerProvider(AnalyticsProvider analyticsProvider);
    ColumnAnalyticsProvider getColumnAnalyticsProvider(@NotNull String name);
    Collection<ColumnAnalyticsProvider> getColumnAnalyticsProviders(@Nullable ColumnInfo columnInfo, boolean sort);
    Collection<QueryAnalyticsProvider> getQueryAnalyticsProviders(@Nullable QuerySettings settings);
    Collection<AnalyticsProvider> getAllAnalyticsProviders();
}
