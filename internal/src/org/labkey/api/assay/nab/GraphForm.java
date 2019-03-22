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
package org.labkey.api.assay.nab;

import org.labkey.api.assay.nab.view.RunDetailOptions;

/**
 * User: klum
 * Date: 5/15/13
 */
public class GraphForm extends RenderAssayForm
{
    private int _firstSample = 0;
    private int _maxSamples = -1;
    private int _height = -1;
    private int _width = -1;
    private RunDetailOptions.DataIdentifier _dataIdentifier = RunDetailOptions.DataIdentifier.DefaultFormat;

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

    public RunDetailOptions.DataIdentifier getDataIdentifier()
    {
        return _dataIdentifier;
    }

    public void setDataIdentifier(RunDetailOptions.DataIdentifier dataIdentifier)
    {
        _dataIdentifier = dataIdentifier;
    }
}
