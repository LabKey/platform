package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * User: adam
 * Date: 8/3/12
 * Time: 1:07 PM
 */

// Implementors generate and append SQL that acts as an "is one of" filter. This can be an actual IN clause or a
// database-specific implementation that scales or performs better (e.g., arrays or in-line parameter expansion)
public interface InClauseGenerator
{
    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Collection<?> params);
}
