package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.TableInfo;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

// A temporary variant of QueryUpdateForm for actions that rely on deserializing old values. Goal is to identify and
// substitute this class to tag the few actions that need deserialization... and then fix those actions to not rely on this.
public class QueryUpdateFormHack extends QueryUpdateForm
{
    public QueryUpdateFormHack(@NotNull TableInfo table, @NotNull ViewContext ctx)
    {
        super(table, ctx);
    }

    public QueryUpdateFormHack(@NotNull TableInfo table, @NotNull ViewContext ctx, boolean ignorePrefix)
    {
        super(table, ctx, ignorePrefix);
    }

    public QueryUpdateFormHack(@NotNull TableInfo table, @NotNull ViewContext ctx, @Nullable BindException errors)
    {
        super(table, ctx, errors);
    }

    @Override
    protected boolean deserializeOldValues()
    {
        return true;
    }
}
