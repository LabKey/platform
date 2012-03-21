package org.labkey.visualization.sql;

import org.labkey.api.data.Aggregate;
import org.labkey.api.view.ViewContext;

import java.util.Map;

/**
 * User: brittp
 * Date: Jan 27, 2011 11:14:10 AM
 */
class VisualizationAggregateColumn extends VisualizationSourceColumn
{
    private Aggregate.Type _aggregate;

    VisualizationAggregateColumn(ViewContext context, Map<String, Object> properties)
    {
        super(context, properties);
        String aggregate = (String) properties.get("aggregate");
        if (aggregate == null)
            aggregate = "MAX";
        _aggregate = Aggregate.Type.valueOf(aggregate);
    }

    public Aggregate.Type getAggregate()
    {
        return _aggregate;
    }

    @Override
    public String getAlias()
    {
        return super.getAlias() + "_" + _aggregate.name();
    }
}
