package org.labkey.api.analytics;

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

        if ((a == null && b == null) || (a != null && a.equals(b)))
            return this.getName().compareTo(o.getName());
        else
            return a != null ? a.compareTo(b) : 1;
    }
}
