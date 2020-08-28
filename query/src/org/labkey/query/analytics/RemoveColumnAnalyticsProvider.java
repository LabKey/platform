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
package org.labkey.query.analytics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.stats.ColumnAnalyticsProvider;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.util.Set;

public class RemoveColumnAnalyticsProvider extends ColumnAnalyticsProvider
{
    @Override
    public String getName()
    {
        return "COL_REMOVE";
    }

    @Override
    public String getLabel()
    {
        return "Remove Column";
    }

    @Override
    public String getDescription()
    {
        return "Remove the selected column from the view.";
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
        return "LABKEY.ColumnQueryAnalytics.removeColumnFromDataRegion(" +
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
        dependencies.add(ClientDependency.fromPath("query/ColumnQueryAnalytics.js"));
    }

    @Override
    public Integer getSortOrder()
    {
        return 100;
    }
}
