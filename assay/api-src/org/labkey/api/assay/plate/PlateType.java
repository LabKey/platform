package org.labkey.api.assay.plate;

public interface PlateType
{
    boolean isArchived();
    Integer getRowId();
    String getDescription();
    Integer getRows();
    Integer getColumns();
    Integer getWellCount();
}
