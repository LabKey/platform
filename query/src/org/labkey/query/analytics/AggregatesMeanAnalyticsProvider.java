package org.labkey.query.analytics;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.analytics.BaseAggregatesAnalyticsProvider;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;

public class AggregatesMeanAnalyticsProvider extends BaseAggregatesAnalyticsProvider
{
    @Override
    public Aggregate.Type getAggregateType()
    {
        return Aggregate.BaseType.MEAN;
    }

    @Override
    public boolean isApplicable(@NotNull ColumnInfo col)
    {
        return col.isNumericType() && !col.isKeyField() && !col.isLookup()
                && !"serial".equalsIgnoreCase(col.getSqlTypeName());
    }

    @Override
    public Integer getSortOrder()
    {
        return 202;
    }
}
