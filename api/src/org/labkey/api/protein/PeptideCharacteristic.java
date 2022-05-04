package org.labkey.api.protein;

public class PeptideCharacteristic
{
    private String _sequence;
    private Double _intensity;
    private Double _confidence;

    public String getSequence()
    {
        return _sequence;
    }

    public void setSequence(String sequence)
    {
        _sequence = sequence;
    }

    public Double getIntensity()
    {
        return _intensity;
    }

    public void setIntensity(Double intensity)
    {
        _intensity = intensity;
    }

    public Double getConfidence()
    {
        return _confidence;
    }

    public void setConfidence(Double confidence)
    {
        _confidence = confidence;
    }
}
