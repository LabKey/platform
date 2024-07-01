package org.labkey.api.assay.plate;

public interface PlateType
{
    Integer getRowId();
    String getDescription();
    Integer getRows();
    Integer getColumns();
    Integer getWellCount();
}
