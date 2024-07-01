package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.PlateType;

public class WellLayout
{
    public record Well(int destinationRowIdx, int destinationColIdx, int sourcePlateId, int sourceRowId, int sourceColIdx) {}

    private final PlateType _plateType;
    private final Well[] _wells;

    public WellLayout(@NotNull PlateType plateType)
    {
        _plateType = plateType;
        _wells = new Well[plateType.getWellCount()];
    }

    public Well[] getWells()
    {
        return _wells;
    }

    public void setWell(int destinationRowIdx, int destinationColIdx, int sourcePlateId, int sourceWellRowIdx, int sourceWellColIdx)
    {
        int index = destinationRowIdx * _plateType.getColumns() + destinationColIdx;
        _wells[index] = new Well(destinationRowIdx, destinationColIdx, sourcePlateId, sourceWellRowIdx, sourceWellColIdx);
    }

    public PlateType getPlateType()
    {
        return _plateType;
    }
}
