package org.labkey.api.data.statistics;

import org.labkey.api.util.Pair;

/**
* Created by klum on 1/20/14.
*/
public class DoublePoint extends Pair<Double, Double>
{
    public DoublePoint(double x, double y)
    {
        super(x, y);
    }

    public double getX()
    {
        return getKey();
    }

    public double getY()
    {
        return getValue();
    }
}
