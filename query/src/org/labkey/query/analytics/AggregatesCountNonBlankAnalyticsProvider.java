package org.labkey.query.analytics;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.analytics.BaseAggregatesAnalyticsProvider;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;

public class AggregatesCountNonBlankAnalyticsProvider extends BaseAggregatesAnalyticsProvider
{
    @Override
    public Aggregate.Type getAggregateType()
    {
        return Aggregate.BaseType.COUNT;
    }

    @Override
    public boolean isApplicable(@NotNull ColumnInfo col)
    {
        return true;
    }

    @Override
    public Integer getSortOrder()
    {
        return 200;
    }
}
