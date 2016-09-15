package org.labkey.visualization.report;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.analytics.ColumnAnalyticsProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.settings.FolderSettingsCache;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.util.Set;

public class MeasureBoxPlotAnalyticsProvider extends ColumnAnalyticsProvider
{
    @Override
    public String getName()
    {
        return "VIS_BOX";
    }

    @Override
    public String getLabel()
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
        return col.isNumericType() &&
                !col.isLookup() &&
                !col.getSqlTypeName().equalsIgnoreCase("serial") &&
                !col.getSqlTypeName().equalsIgnoreCase("entityid");
    }

    @Override
    public boolean isVisible(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        if (FolderSettingsCache.areRestrictedColumnsEnabled(ctx.getContainer()))
        {
            return col.isMeasure();
        }
        else
        {
            return true;
        }
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
        return "LABKEY.ColumnVisualizationAnalytics.showMeasureFromDataRegion(" +
                PageFlowUtil.jsString(ctx.getCurrentRegion().getName()) + "," +
                PageFlowUtil.jsString(col.getName()) + "," +
                PageFlowUtil.jsString(col.getFieldKey().toString()) + "," +
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
