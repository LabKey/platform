package org.labkey.query.analytics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.stats.ColumnAnalyticsProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.util.Set;

public class SummaryStatisticsAnalyticsProvider extends ColumnAnalyticsProvider
{
    @Override
    public String getName()
    {
        return "COL_SUMMARYSTATS";
    }

    @Override
    public String getLabel()
    {
        return "Summary Statistics...";
    }

    @Override
    public String getDescription()
    {
        return "Display summary statistics for the given column with indicators for which ones are enabled/disabled for the given view.";
    }

    @Nullable
    @Override
    public String getIconCls(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return "fa fa-calculator";
    }

    @Override
    public boolean isApplicable(@NotNull ColumnInfo col)
    {
        return true;
    }

    @Nullable
    @Override
    public ActionURL getActionURL(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return null;
    }

    @Override
    public String getScript(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return "LABKEY.ColumnSummaryStatistics.showDialogFromDataRegion(" +
            PageFlowUtil.jsString(ctx.getCurrentRegion().getName()) + "," +
            PageFlowUtil.jsString(col.getFieldKey().toString()) +
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
        dependencies.add(ClientDependency.fromPath("query/ColumnSummaryStatistics"));
    }

    @Override
    public Integer getSortOrder()
    {
        return 199;
    }
}
