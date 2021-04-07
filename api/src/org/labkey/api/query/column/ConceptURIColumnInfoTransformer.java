package org.labkey.api.query.column;

import org.jetbrains.annotations.NotNull;

public interface ConceptURIColumnInfoTransformer extends ColumnInfoTransformer
{
    @NotNull String getConceptURI();

}
