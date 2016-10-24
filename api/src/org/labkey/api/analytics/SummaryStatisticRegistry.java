package org.labkey.api.analytics;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Aggregate;

public interface SummaryStatisticRegistry
{
    void register(@NotNull Aggregate.Type summaryStatistic);
    Aggregate.Type getByName(@NotNull String name);
}
