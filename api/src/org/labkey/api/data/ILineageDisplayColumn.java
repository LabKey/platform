package org.labkey.api.data;

public interface ILineageDisplayColumn
{
    DisplayColumn getInnerDisplayColumn();
    ColumnInfo getInnerBoundColumn();
}
