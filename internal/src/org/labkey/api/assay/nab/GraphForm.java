package org.labkey.api.assay.nab;

/**
* Created by IntelliJ IDEA.
* User: klum
* Date: 5/15/13
*/
public class GraphForm extends RenderAssayForm
{
    private int _firstSample = 0;
    private int _maxSamples = -1;
    private int _height = -1;
    private int _width = -1;

    public int getFirstSample()
    {
        return _firstSample;
    }

    public void setFirstSample(int firstSample)
    {
        _firstSample = firstSample;
    }

    public int getMaxSamples()
    {
        return _maxSamples;
    }

    public void setMaxSamples(int maxSamples)
    {
        _maxSamples = maxSamples;
    }

    public int getHeight()
    {
        return _height;
    }

    public void setHeight(int height)
    {
        _height = height;
    }

    public int getWidth()
    {
        return _width;
    }

    public void setWidth(int width)
    {
        _width = width;
    }
}
