package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;

public interface ILineageDisplayColumn
{
    DisplayColumn getInnerDisplayColumn();

    @Nullable
    ColumnInfo getInnerBoundColumn();
}
