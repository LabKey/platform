package org.labkey.api.specimen.report;

public class SummaryByVisitParticipant extends SpecimenCountSummary
{
    private String _participantId;
    private String _cohort;

    public String getParticipantId()
    {
        return _participantId;
    }

    public void setParticipantId(String participantId)
    {
        _participantId = participantId;
    }

    public String getCohort()
    {
        return _cohort;
    }

    public void setCohort(String cohort)
    {
        _cohort = cohort;
    }
}
