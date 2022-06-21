package org.labkey.api.protein;

public class PeptideCharacteristic
{
    private String _sequence;
    private Double _intensity;
    private int _intensityRank;
    private String _intensityColor;
    private Double _confidence;
    private int _confidenceRank;
    private String _confidenceColor;
    private String _color;
    private String _foregroundColor;

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

    public int getIntensityRank()
    {
        return _intensityRank;
    }

    public void setIntensityRank(int intensityRank)
    {
        _intensityRank = intensityRank;
    }

    public String getIntensityColor()
    {
        return _intensityColor;
    }

    public void setIntensityColor(String intensityColor)
    {
        _intensityColor = intensityColor;
    }

    public Double getConfidence()
    {
        return _confidence;
    }

    public void setConfidence(Double confidence)
    {
        _confidence = confidence;
    }

    public int getConfidenceRank()
    {
        return _confidenceRank;
    }

    public void setConfidenceRank(int confidenceRank)
    {
        _confidenceRank = confidenceRank;
    }

    public String getConfidenceColor()
    {
        return _confidenceColor;
    }

    public void setConfidenceColor(String confidenceColor)
    {
        _confidenceColor = confidenceColor;
    }

    public String getColor()
    {
        return _color;
    }

    public void setColor(String color)
    {
        _color = color;
    }

    public String getForegroundColor()
    {
        return _foregroundColor;
    }

    public void setForegroundColor(String foregroundColor)
    {
        _foregroundColor = foregroundColor;
    }
}
