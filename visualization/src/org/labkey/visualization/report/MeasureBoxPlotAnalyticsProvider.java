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

public class MeasureBoxPlotAnalyticsProvider extends ColumnAnalyticsProvider
{
    @Override
    public String getName()
    {
        return "Box & Whisker";
    }

    @Override
    public String getDescription()
    {
        return "View a box and whisker plot of the selected measure column's data values.";
    }

    @Override
    public boolean isApplicable(@NotNull ColumnInfo col)
    {
        return AppProps.getInstance().isExperimentalFeatureEnabled(VisualizationModule.EXPERIMENTAL_VISUALIZATION_ANALYTICS_PROVIDER)
                && col.isMeasure();
    }

    @Nullable
    @Override
    public String getIconCls(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return "fa fa-sliders fa-rotate-90";
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
        return "LABKEY.ColumnVisualizationAnalytics.showMeasureFromDataRegion(this, " +
                PageFlowUtil.jsString(ctx.getCurrentRegion().getName()) + "," +
                PageFlowUtil.jsString(col.getName()) +
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
