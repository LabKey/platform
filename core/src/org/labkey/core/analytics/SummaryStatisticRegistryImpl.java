package org.labkey.core.analytics;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.analytics.SummaryStatisticRegistry;
import org.labkey.api.data.Aggregate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SummaryStatisticRegistryImpl implements SummaryStatisticRegistry
{
    private static final Map<String, Aggregate.Type> REGISTERED_STATS = new ConcurrentHashMap<>();

    @Override
    public void register(@NotNull Aggregate.Type summaryStatistic)
    {
        String name = summaryStatistic.getName().toUpperCase();
        if (REGISTERED_STATS.containsKey(name))
            throw new IllegalStateException("A summary statistic has already been registered for this name: " + name);

        REGISTERED_STATS.put(name, summaryStatistic);
    }

    @Override
    public Aggregate.Type getByName(@NotNull String name)
    {
        // backwards compatibility for mapping AVG -> MEAN
        name = "AVG".equalsIgnoreCase(name) ? "MEAN" : name;

        return REGISTERED_STATS.get(name.toUpperCase());
    }
}
