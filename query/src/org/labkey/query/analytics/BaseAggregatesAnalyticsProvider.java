package org.labkey.query.analytics;

import org.labkey.api.analytics.ColumnAnalyticsProvider;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.util.List;
import java.util.Set;

public abstract class BaseAggregatesAnalyticsProvider extends ColumnAnalyticsProvider
{
    public abstract Aggregate.Type getAggregateType();

    @Override
    public String getName()
    {
        return "AGG_" + getAggregateType().toString();
    }

    @Override
    public String getLabel()
    {
        return getAggregateType().getFriendlyName();
    }

    @Override
    public String getDescription()
    {
        return "Summary statistic " + getLabel() + " value function to apply to a given column.";
    }

    @Override
    public String getGroupingHeader()
    {
        return "Summary Statistics";
    }

    @Override
    public String getIconCls(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        List<Aggregate> colAggregates = ctx.getAggregatesByFieldKey(col.getFieldKey());
        if (!colAggregates.isEmpty())
        {
            for (Aggregate colAggregate : colAggregates)
            {
                if (colAggregate.getType() == getAggregateType())
                    return "fa fa-check-square-o";
            }
        }

        return null;
    }

    @Override
    public String getGroupingHeaderIconCls()
    {
        return "fa fa-calculator";
    }

    @Override
    public ActionURL getActionURL(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return null;
    }

    @Override
    public String getScript(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return "LABKEY.ColumnQueryAnalytics.applyAggregateFromDataRegion(" +
                PageFlowUtil.jsString(ctx.getCurrentRegion().getName()) + "," +
                PageFlowUtil.jsString(col.getFieldKey().toString()) + "," +
                PageFlowUtil.jsString(getAggregateType().name()) +
            ");";
    }

    @Override
    public boolean requiresPageReload()
    {
        return true;
    }

    @Override
    public void addClientDependencies(Set<ClientDependency> dependencies)
    {
        dependencies.add(ClientDependency.fromPath("query/ColumnQueryAnalytics.js"));
    }
}
