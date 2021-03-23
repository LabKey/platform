package org.labkey.api.query.column;

import org.jetbrains.annotations.NotNull;

public interface ConceptURIColumnDecorator extends ColumnDecorator
{
    @NotNull String getConceptURI();
}