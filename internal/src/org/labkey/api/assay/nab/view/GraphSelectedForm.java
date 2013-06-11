package org.labkey.api.assay.nab.view;

import org.labkey.api.assay.dilution.DilutionCurve;

/**
* Created by IntelliJ IDEA.
* User: klum
* Date: 6/11/13
*/
public class GraphSelectedForm
{
    private int _protocolId;
    private int[] _id;
    private String _captionColumn;
    private String _chartTitle;
    private DilutionCurve.FitType _fitType;
    private int _height = -1;
    private int _width = -1;

    public int[] getId()
    {
        return _id;
    }

    public void setId(int[] id)
    {
        _id = id;
    }

    public int getProtocolId()
    {
        return _protocolId;
    }

    public void setProtocolId(int protocolId)
    {
        _protocolId = protocolId;
    }

    public String getCaptionColumn()
    {
        return _captionColumn;
    }

    public void setCaptionColumn(String captionColumn)
    {
        _captionColumn = captionColumn;
    }

    public String getChartTitle()
    {
        return _chartTitle;
    }

    public void setChartTitle(String chartTitle)
    {
        _chartTitle = chartTitle;
    }

    public String getFitType()
    {
        return _fitType != null ? _fitType.name() : null;
    }

    public void setFitType(String fitType)
    {
        _fitType = fitType != null ? DilutionCurve.FitType.valueOf(fitType) : null;
    }

    public DilutionCurve.FitType getFitTypeEnum()
    {
        return _fitType;
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
