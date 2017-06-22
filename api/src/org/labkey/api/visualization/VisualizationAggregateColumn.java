/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.visualization;

import org.labkey.api.stats.SummaryStatisticRegistry;
import org.labkey.api.data.Aggregate;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ViewContext;

/**
 * User: brittp
 * Date: Jan 27, 2011 11:14:10 AM
 */
public class VisualizationAggregateColumn extends VisualizationSourceColumn
{
    private Aggregate.Type _aggregate;

    public VisualizationAggregateColumn(ViewContext context, VisDataRequest.Measure measure)
    {
        super(context, measure);
        String aggregate = measure.getAggregate();
        if (aggregate == null)
            aggregate = "MAX";

        SummaryStatisticRegistry registry = ServiceRegistry.get().getService(SummaryStatisticRegistry.class);
        _aggregate = registry != null ? registry.getByName(aggregate) : null;

        if (_aggregate == null)
            throw new IllegalArgumentException("Invalid aggregate type: '" + aggregate + "'.");
    }

    public Aggregate.Type getAggregate()
    {
        return _aggregate;
    }

    @Override
    public String getAlias()
    {
        return super.getAlias() + "_" + _aggregate.getName();
    }
}
