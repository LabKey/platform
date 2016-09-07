package org.labkey.visualization.report;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.analytics.ColumnAnalyticsProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.visualization.GenericChartReport.RenderType;
import org.labkey.api.visualization.VisualizationUrls;

public class QuickChartAnalyticsProvider extends ColumnAnalyticsProvider
{
    @Override
    public String getName()
    {
        return "COL_QUICK_CHART";
    }

    @Override
    public String getLabel()
    {
        return "Quick Chart";
    }

    @Override
    public String getDescription()
    {
        return "Quick chart option based on the specified column type.";
    }

    @Override
    public boolean isApplicable(@NotNull ColumnInfo col)
    {
        return true;
    }

    @Override
    public String getIconCls(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return RenderType.SCATTER_PLOT.getIconCls();
    }

    @Override
    public ActionURL getActionURL(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        VisualizationUrls urlProvider = PageFlowUtil.urlProvider(VisualizationUrls.class);
        if (urlProvider != null && settings != null && settings.getSchemaName() != null && settings.getQueryName() != null)
        {
            RenderType renderType = col.isNumericType() ? RenderType.BOX_PLOT : RenderType.BAR_PLOT;
            ActionURL url = urlProvider.getGenericChartDesignerURL(ctx.getContainer(), ctx.getViewContext().getUser(), settings, renderType);

            String autoColParam = col.isNumericType() ? "autoColumnYName" : "autoColumnName";
            url.addParameter(autoColParam, col.getName());

            return url;
        }

        return null;
    }

    @Override
    public String getScript(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return null;
    }

    @Override
    public Integer getSortOrder()
    {
        return null;
    }
}
