package org.labkey.core.analytics;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.analytics.AnalyticsProvider;
import org.labkey.api.analytics.AnalyticsProviderRegistry;
import org.labkey.api.analytics.ColumnAnalyticsProvider;
import org.labkey.api.analytics.QueryAnalyticsProvider;
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
        REGISTERED_PROVIDERS.add(analyticsProvider);
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
}
