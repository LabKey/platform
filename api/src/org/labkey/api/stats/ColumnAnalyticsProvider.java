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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.util.Set;

public abstract class ColumnAnalyticsProvider implements AnalyticsProvider, Comparable<ColumnAnalyticsProvider>
{
    public abstract boolean isApplicable(@NotNull ColumnInfo col);

    public boolean isVisible(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return true;
    }

    @Override
    public String getLabel()
    {
        return getName();
    }

    @Nullable
    public abstract ActionURL getActionURL(RenderContext ctx, QuerySettings settings, ColumnInfo col);

    @Nullable
    public abstract String getScript(RenderContext ctx, QuerySettings settings, ColumnInfo col);

    public boolean requiresPageReload()
    {
        return false;
    }

    @SuppressWarnings("UnusedParameters")
    public void addClientDependencies(Set<ClientDependency> dependencies)
    {
    }

    @Nullable
    public String getIconCls(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return null;
    }

    public boolean alwaysEnabled()
    {
        return false;
    }

    @Nullable
    public String getGroupingHeader()
    {
        return null;
    }

    @Nullable
    public String getGroupingHeaderIconCls()
    {
        return null;
    }

    @Override
    public int compareTo(ColumnAnalyticsProvider o)
    {
        Integer a = this.getSortOrder();
        Integer b = o.getSortOrder();

        // if the sort order Integer values match, sort by name
        if ((a == null && b == null) || (a != null && a.equals(b)))
            return this.getName().compareTo(o.getName());

        return b == null ? -1 : (a == null ? 1 : a.compareTo(b));
    }
}
