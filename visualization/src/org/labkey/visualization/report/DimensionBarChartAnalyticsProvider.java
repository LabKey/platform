package org.labkey.visualization.report;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.analytics.ColumnAnalyticsProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.visualization.VisualizationModule;

import java.util.Set;

public class DimensionBarChartAnalyticsProvider extends ColumnAnalyticsProvider
{
    @Override
    public String getName()
    {
        return "VIS_BAR";
    }

    @Override
    public String getLabel()
    {
        return "Bar Chart";
    }

    @Override
    public String getDescription()
    {
        return "View a bar chart of the selected dimension column's data values.";
    }

    @Override
    public boolean isApplicable(@NotNull ColumnInfo col)
    {
        return col.isDimension() && !"entityid".equalsIgnoreCase(col.getSqlTypeName());
    }

    @Nullable
    @Override
    public String getIconCls(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return "fa fa-bar-chart";
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
        return "LABKEY.ColumnVisualizationAnalytics.showDimensionFromDataRegion(" +
                PageFlowUtil.jsString(ctx.getCurrentRegion().getName()) + "," +
                PageFlowUtil.jsString(col.getName()) + "," +
                PageFlowUtil.jsString(getName()) +
            ");";
    }

    @Override
    public void addClientDependencies(Set<ClientDependency> dependencies)
    {
        dependencies.add(ClientDependency.fromPath("vis/vis"));
        dependencies.add(ClientDependency.fromPath("vis/ColumnVisualizationAnalytics.js"));
        dependencies.add(ClientDependency.fromPath("vis/ColumnVisualizationAnalytics.css"));
    }

    @Override
    public Integer getSortOrder()
    {
        return 300;
    }
}
