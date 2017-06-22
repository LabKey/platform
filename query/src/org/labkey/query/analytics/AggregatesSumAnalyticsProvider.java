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
package org.labkey.query.analytics;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.stats.BaseAggregatesAnalyticsProvider;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;

public class AggregatesSumAnalyticsProvider extends BaseAggregatesAnalyticsProvider
{
    @Override
    public Aggregate.Type getAggregateType()
    {
        return Aggregate.BaseType.SUM;
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
        return 201;
    }
}
