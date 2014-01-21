/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.assay.nab.view;

import org.labkey.api.data.statistics.StatsService;

/**
 * User: klum
 * Date: 6/11/13
 */
public class GraphSelectedForm
{
    private int _protocolId;
    private int[] _id;
    private String _captionColumn;
    private String _chartTitle;
    private StatsService.CurveFitType _fitType;
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
        _fitType = fitType != null ? StatsService.CurveFitType.valueOf(fitType) : null;
    }

    public StatsService.CurveFitType getFitTypeEnum()
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
