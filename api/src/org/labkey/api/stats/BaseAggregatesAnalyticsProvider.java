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
package org.labkey.api.stats;

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
    public static final String  PREFIX = "AGG_";

    public abstract Aggregate.Type getAggregateType();

    @Override
    public String getName()
    {
        return PREFIX + getAggregateType().getName();
    }

    @Override
    public String getLabel()
    {
        return getAggregateType().getFullLabel();
    }

    @Override
    public String getDescription()
    {
        return "Summary statistic " + getLabel() + " value function to apply to a given column.";
    }

    @Override
    public boolean isVisible(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return false; // these aggregates will be displayed as part of the SummaryStatisticsAnalyticsProvider
    }

    public List<Aggregate.Type> getAdditionalAggregateTypes()
    {
        return null;
    }

    @Override
    public String getGroupingHeader()
    {
        return "Summary Statistics";
    }

    @Override
    public String getIconCls(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return ctx.containsAnalyticsProvider(col.getFieldKey(), getName()) ? "fa fa-check-square-o" : null;
    }

    @Override
    public boolean alwaysEnabled()
    {
        return true;
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
        return "LABKEY.ColumnQueryAnalytics.applySummaryStatFromDataRegion(" +
                PageFlowUtil.jsString(ctx.getCurrentRegion().getName()) + "," +
                PageFlowUtil.jsString(col.getFieldKey().toString()) + "," +
                PageFlowUtil.jsString(getName()) +
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
