package org.labkey.api.study.model;

public class ParticipantInfo
{
    private final String _containerId;
    private final String _alternateId;
    private final int _dateOffset;

    public ParticipantInfo(String containerId, String alternateId, int dateOffset)
    {
        _containerId = containerId;
        _alternateId = alternateId;
        _dateOffset = dateOffset;
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public String getAlternateId()
    {
        return _alternateId;
    }

    public int getDateOffset()
    {
        return _dateOffset;
    }
}
