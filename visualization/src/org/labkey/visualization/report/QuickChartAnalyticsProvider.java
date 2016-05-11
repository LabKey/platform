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
        return "Quick Chart";
    }

    @Override
    public String getDescription()
    {
        return "Auto chart option based on the specified column type.";
    }

    @Override
    public boolean isApplicable(@NotNull ColumnInfo col)
    {
        return col.isNumericType();
    }

    @Override
    public String getIconCls(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return RenderType.AUTO_PLOT.getIconCls();
    }

    @Override
    public ActionURL getActionURL(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        VisualizationUrls urlProvider = PageFlowUtil.urlProvider(VisualizationUrls.class);
        if (urlProvider != null && settings != null && settings.getSchemaName() != null && settings.getQueryName() != null)
        {
            ActionURL url = urlProvider.getGenericChartDesignerURL(ctx.getContainer(), ctx.getViewContext().getUser(), settings, RenderType.AUTO_PLOT);
            url.addParameter("autoColumnYName", col.getName());
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
