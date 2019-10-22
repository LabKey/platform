package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.Pair;

import java.util.List;

public interface NestedPropertyDisplayColumn
{
    @NotNull List<Pair<RenderContext, DisplayColumn>> getNestedDisplayColumns(RenderContext ctx);

    @NotNull String getNestedColumnKey(@NotNull DisplayColumn nestedCol);
}
