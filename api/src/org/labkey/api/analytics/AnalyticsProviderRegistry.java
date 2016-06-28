package org.labkey.api.analytics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.QuerySettings;

import java.util.Collection;

public interface AnalyticsProviderRegistry
{
    void registerProvider(AnalyticsProvider analyticsProvider);
    ColumnAnalyticsProvider getColumnAnalyticsProvider(@NotNull String name);
    Collection<ColumnAnalyticsProvider> getColumnAnalyticsProviders(@Nullable ColumnInfo columnInfo, boolean sort);
    Collection<QueryAnalyticsProvider> getQueryAnalyticsProviders(@Nullable QuerySettings settings);
}
