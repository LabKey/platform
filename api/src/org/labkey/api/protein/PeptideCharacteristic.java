package org.labkey.api.protein;

public class PeptideCharacteristic
{
    private String _sequence;
    private Double _intensity;
    private String _intensityColor;
    private Double _confidence;
    private String _confidenceColor;
    private boolean _isIntensityView;
    private boolean _isConfidenceView;

    public boolean isConfidenceView()
    {
        return _isConfidenceView;
    }

    public void setConfidenceView(boolean confidenceView)
    {
        _isConfidenceView = confidenceView;
    }

    public boolean isIntensityView()
    {
        return _isIntensityView;
    }

    public void setIntensityView(boolean intensityView)
    {
        _isIntensityView = intensityView;
    }

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

    public String getConfidenceColor()
    {
        return _confidenceColor;
    }

    public void setConfidenceColor(String confidenceColor)
    {
        _confidenceColor = confidenceColor;
    }
}
