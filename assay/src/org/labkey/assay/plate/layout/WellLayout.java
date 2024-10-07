package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.PlateType;

public class WellLayout
{
    public record Well(int destinationRowIdx, int destinationColIdx, int sourcePlateId, int sourceRowIdx, int sourceColIdx, Integer sourceSampleId) {}

    private final PlateType _plateType;
    private final boolean _sampleOnly;
    private final Integer _targetTemplateId;
    private final Well[] _wells;

    public WellLayout(@NotNull PlateType plateType)
    {
        this(plateType, false, null);
    }

    public WellLayout(@NotNull PlateType plateType, boolean sampleOnly, @Nullable Integer targetTemplateId)
    {
        _plateType = plateType;
        _sampleOnly = sampleOnly;
        _targetTemplateId = targetTemplateId;
        _wells = new Well[plateType.getWellCount()];
    }

    public @Nullable Integer getTargetTemplateId()
    {
        return _targetTemplateId;
    }

    private int getWellIndex(int destinationRowIdx, int destinationColIdx)
    {
        return destinationRowIdx * _plateType.getColumns() + destinationColIdx;
    }

    public @Nullable Well getWell(int destinationRowIdx, int destinationColIdx)
    {
        int index = getWellIndex(destinationRowIdx, destinationColIdx);
        if (index < _wells.length)
            return _wells[index];
        return null;
    }

    public Well[] getWells()
    {
        return _wells;
    }

    public boolean isSampleOnly()
    {
        return _sampleOnly;
    }

    public void setWell(int destinationRowIdx, int destinationColIdx, int sourcePlateId, int sourceWellRowIdx, int sourceWellColIdx)
    {
        setWell(destinationRowIdx, destinationColIdx, sourcePlateId, sourceWellRowIdx, sourceWellColIdx, null);
    }

    public void setWell(int destinationRowIdx, int destinationColIdx, int sourcePlateId, int sourceWellRowIdx, int sourceWellColIdx, Integer sourceSampleId)
    {
        int index = getWellIndex(destinationRowIdx, destinationColIdx);
        _wells[index] = new Well(destinationRowIdx, destinationColIdx, sourcePlateId, sourceWellRowIdx, sourceWellColIdx, sourceSampleId);
    }

    public PlateType getPlateType()
    {
        return _plateType;
    }
}
