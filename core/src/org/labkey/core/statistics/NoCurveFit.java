package org.labkey.core.statistics;

import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;

/**
 * Created by klum on 10/10/2014.
 */
public class NoCurveFit extends DefaultCurveFit
{
    public NoCurveFit(DoublePoint[] data)
    {
        super(data);
    }

    @Override
    protected Parameters computeParameters()
    {
        return null;
    }

    @Override
    public double fitCurve(double x)
    {
        return 0;
    }

    @Override
    public double fitCurve(double x, Parameters parameters)
    {
        return 0;
    }
}
