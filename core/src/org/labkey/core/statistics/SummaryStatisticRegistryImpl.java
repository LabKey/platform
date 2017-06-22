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
import org.labkey.api.stats.SummaryStatisticRegistry;
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
