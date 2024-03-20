package org.labkey.api.assay.plate;

import java.util.Objects;

public class PlateSetEdge
{
    private int _fromPlateSetId;
    private int _rootPlateSetId;
    private int _toPlateSetId;

    // Necessary for database serialization
    public PlateSetEdge()
    {
    }

    public PlateSetEdge(int fromPlateSetId, int toPlateSetId, int rootPlateSetId)
    {
        _fromPlateSetId = fromPlateSetId;
        _toPlateSetId = toPlateSetId;
        _rootPlateSetId = rootPlateSetId;
    }

    public int getFromPlateSetId()
    {
        return _fromPlateSetId;
    }

    public void setFromPlateSetId(int fromPlateSetId)
    {
        _fromPlateSetId = fromPlateSetId;
    }

    public int getRootPlateSetId()
    {
        return _rootPlateSetId;
    }

    public void setRootPlateSetId(int rootPlateSetId)
    {
        _rootPlateSetId = rootPlateSetId;
    }

    public int getToPlateSetId()
    {
        return _toPlateSetId;
    }

    public void setToPlateSetId(int toPlateSetId)
    {
        _toPlateSetId = toPlateSetId;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(_fromPlateSetId, _toPlateSetId, _rootPlateSetId);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof PlateSetEdge edge))
            return false;

        return (
            Objects.equals(_fromPlateSetId, edge.getFromPlateSetId()) &&
            Objects.equals(_toPlateSetId, edge.getToPlateSetId()) &&
            Objects.equals(_rootPlateSetId, edge.getRootPlateSetId())
        );
    }
}
