package org.labkey.query.analytics;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;

public class AggregatesAverageAnalyticsProvider extends BaseAggregatesAnalyticsProvider
{
    @Override
    public Aggregate.Type getAggregateType()
    {
        return Aggregate.Type.AVG;
    }

    @Override
    public boolean isApplicable(@NotNull ColumnInfo col)
    {
        return col.isNumericType() && !col.isKeyField() && !col.isLookup();
    }

    @Override
    public Integer getSortOrder()
    {
        return 202;
    }
}
