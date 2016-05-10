package org.labkey.api.analytics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.util.Collections;
import java.util.Set;

public abstract class ColumnAnalyticsProvider extends AnalyticsProvider
{
    public abstract boolean isApplicable(@NotNull ColumnInfo col);

    @Nullable
    public abstract ActionURL getActionURL(RenderContext ctx, QuerySettings settings, ColumnInfo col);

    @Nullable
    public abstract String getScript(RenderContext ctx, QuerySettings settings, ColumnInfo col);

    @NotNull
    public Set<ClientDependency> getClientDependencies()
    {
        return Collections.emptySet();
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
}
