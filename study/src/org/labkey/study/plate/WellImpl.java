/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.study.plate;

import org.labkey.api.study.PositionImpl;
import org.labkey.api.study.Well;

/**
 * User: brittp
* Date: Oct 20, 2006
* Time: 10:25:20 AM
*/
public class WellImpl extends PositionImpl implements Well
{
    private double _value;
    private Double _dilution;
    protected PlateImpl _plate;
    private boolean _excluded;

    public WellImpl()
    {
        // no-param constructor for reflection
        super(null, -1, -1);
    }

    public WellImpl(PlateImpl plate, int row, int col, double value, boolean excluded)
    {
        super(plate.getContainer(), row, col);
        _plate = plate;
        _value = value;
        _excluded = excluded;
        setPlateId(plate.getRowId());
    }

    public double getValue()
    {
        return _value;
    }

    public void setValue(double value)
    {
        _value = value;
    }

    public double getMax()
    {
        return _excluded ? Double.NaN : _value;
    }

    public double getMean()
    {
        return  _excluded ? Double.NaN : _value;
    }

    public double getMin()
    {
        return  _excluded ? Double.NaN : _value;
    }

    public double getStdDev()
    {
        return 0;
    }

    public Double getDilution()
    {
        return _dilution;
    }

    public void setDilution(Double dilution)
    {
        _dilution = dilution;
    }

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
}
