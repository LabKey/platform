package org.labkey.api.assay.plate;

import java.util.Objects;

public class PlateSetEdge
{
    private long _fromPlateSetId;
    private long _rootPlateSetId;
    private long _toPlateSetId;

    // Necessary for database serialization
    public PlateSetEdge()
    {
    }

    public PlateSetEdge(long fromPlateSetId, long toPlateSetId, long rootPlateSetId)
    {
        _fromPlateSetId = fromPlateSetId;
        _toPlateSetId = toPlateSetId;
        _rootPlateSetId = rootPlateSetId;
    }

    public long getFromPlateSetId()
    {
        return _fromPlateSetId;
    }

    public void setFromPlateSetId(int fromPlateSetId)
    {
        _fromPlateSetId = fromPlateSetId;
    }

    public long getRootPlateSetId()
    {
        return _rootPlateSetId;
    }

    public void setRootPlateSetId(int rootPlateSetId)
    {
        _rootPlateSetId = rootPlateSetId;
    }

    public long getToPlateSetId()
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
