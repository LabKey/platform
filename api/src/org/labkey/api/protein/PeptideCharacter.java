package org.labkey.api.protein;

public class PeptideCharacter
{
    private String _sequence;
    private Integer _intesity;
    private Double _confifence;

    public String getSequence()
    {
        return _sequence;
    }

    public void setSequence(String sequence)
    {
        _sequence = sequence;
    }

    public Integer getIntesity()
    {
        return _intesity;
    }

    public void setIntesity(Integer intesity)
    {
        _intesity = intesity;
    }

    public Double getConfifence()
    {
        return _confifence;
    }

    public void setConfifence(Double confifence)
    {
        _confifence = confifence;
    }
}
