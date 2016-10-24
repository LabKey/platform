package org.labkey.query.analytics;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.analytics.BaseAggregatesAnalyticsProvider;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;

public class AggregatesMinAnalyticsProvider extends BaseAggregatesAnalyticsProvider
{
    @Override
    public Aggregate.Type getAggregateType()
    {
        return Aggregate.BaseType.MIN;
    }

    @Override
    public boolean isApplicable(@NotNull ColumnInfo col)
    {
        return (col.isNumericType() && !col.isLookup()) || col.isDateTimeType();
    }

    @Override
    public Integer getSortOrder()
    {
        return 203;
    }
}
