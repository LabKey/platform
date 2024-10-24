/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.assay.plate;

import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.Well;

public class WellImpl extends PositionImpl implements Well
{
    private Double _dilution;
    private boolean _excluded;
    protected PlateImpl _plate;
    private Integer _sampleId;
    private Double _value;

    public WellImpl()
    {
        // no-param constructor for reflection
        super(null, -1, -1);
    }

    public WellImpl(PlateImpl plate, int row, int col, Double value, boolean excluded)
    {
        super(plate.getContainer(), row, col);
        _plate = plate;
        _value = value;
        _excluded = excluded;
        setPlateId(plate.getRowId());
    }

    @Override
    public double getValue()
    {
        return _value;
    }

    public void setValue(Double value)
    {
        _value = value;
    }

    @Override
    public double getMax()
    {
        return _excluded ? Double.NaN : _value;
    }

    @Override
    public double getMean()
    {
        return _excluded ? Double.NaN : _value;
    }

    @Override
    public double getMin()
    {
        return _excluded ? Double.NaN : _value;
    }

    @Override
    public double getStdDev()
    {
        return 0;
    }

    @Override
    public Double getDilution()
    {
        return _dilution;
    }

    @Override
    public void setDilution(Double dilution)
    {
        _dilution = dilution;
    }

    @Override
    public PlateImpl getPlate()
    {
        return _plate;
    }

    @Override
    public boolean isExcluded()
    {
        return _excluded;
    }

    public void setExcluded(boolean excluded)
    {
        _excluded = excluded;
    }

    @Override
    public Integer getSampleId()
    {
        return _sampleId;
    }

    public void setSampleId(Integer sampleId)
    {
        _sampleId = sampleId;
    }
}
