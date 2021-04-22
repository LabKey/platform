package org.labkey.api.specimen.report;

import java.util.Set;

public class SummaryByVisitType extends SpecimenCountSummary
{
    private String _primaryType;
    private String _derivative;
    private String _additive;
    private Long _participantCount;
    private Set<String> _participantIds;

    public String getPrimaryType()
    {
        return _primaryType;
    }

    public void setPrimaryType(String primaryType)
    {
        _primaryType = primaryType;
    }

    public String getDerivative()
    {
        return _derivative;
    }

    public void setDerivative(String derivative)
    {
        _derivative = derivative;
    }

    public Long getParticipantCount()
    {
        return _participantCount;
    }

    public void setParticipantCount(Long participantCount)
    {
        _participantCount = participantCount;
    }

    public Set<String> getParticipantIds()
    {
        return _participantIds;
    }

    public void setParticipantIds(Set<String> participantIds)
    {
        _participantIds = participantIds;
    }

    public String getAdditive()
    {
        return _additive;
    }

    public void setAdditive(String additive)
    {
        _additive = additive;
    }
}
