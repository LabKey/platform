package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface ObjectReferencer
{
    default Collection<Long> getItemsWithReferences(Collection<Long> referencedRowIds, @NotNull String referencedSchemaName)
    {
        return getItemsWithReferences(referencedRowIds, referencedSchemaName, null);
    }

    @NotNull Collection<Long> getItemsWithReferences(Collection<Long> referencedRowIds, @NotNull String referencedSchemaName, @Nullable String referencedQueryName);

    @Nullable
    String getObjectReferenceDescription(Class referencedClass);
}
