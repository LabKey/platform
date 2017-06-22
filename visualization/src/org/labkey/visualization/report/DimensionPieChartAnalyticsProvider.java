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
package org.labkey.visualization.report;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.stats.ColumnAnalyticsProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.settings.FolderSettingsCache;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.util.Set;

public class DimensionPieChartAnalyticsProvider extends ColumnAnalyticsProvider
{
    @Override
    public String getName()
    {
        return "VIS_PIE";
    }

    @Override
    public String getLabel()
    {
        return "Pie Chart";
    }

    @Override
    public String getDescription()
    {
        return "View a pie chart of the selected dimension column's data values.";
    }

    @Override
    public boolean isApplicable(@NotNull ColumnInfo col)
    {
        return (col.isDimension() || !col.isNumericType() || col.isLookup())
            && !"entityid".equalsIgnoreCase(col.getSqlTypeName());
    }

    @Override
    public boolean isVisible(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return !FolderSettingsCache.areRestrictedColumnsEnabled(ctx.getContainer()) || col.isDimension();
    }

    @Nullable
    @Override
    public String getIconCls(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return "fa fa-pie-chart";
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
