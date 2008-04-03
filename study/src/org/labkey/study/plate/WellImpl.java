package org.labkey.study.plate;

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

    public WellImpl()
    {
        // no-param constructor for reflection
        super(null, -1, -1);
    }

    public WellImpl(PlateImpl plate, int row, int col, double value)
    {
        super(plate.getContainer(), row, col);
        _plate = plate;
        _value = value;
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
        return _value;
    }

    public double getMean()
    {
        return _value;
    }

    public double getMin()
    {
        return _value;
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
}
