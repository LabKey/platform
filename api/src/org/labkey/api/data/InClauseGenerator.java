package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: adam
 * Date: 8/3/12
 * Time: 1:07 PM
 */

// Implementors generate and append SQL that acts as an "is one of" filter. This can be an actual IN clause or a
// database-specific implementation that scales or performs better (e.g., arrays or in-line parameter expansion)
// TODO: Remove colInfo and urlClause; consider removing negated, includeNull, and alias as well.
public interface InClauseGenerator
{
    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Object[] params, @Nullable ColumnInfo colInfo,
                                         String alias, boolean negated, boolean includeNull, boolean urlClause);
}
