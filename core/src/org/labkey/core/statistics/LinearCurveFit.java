package org.labkey.core.statistics;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;

import java.util.Map;

public class LinearCurveFit extends DefaultCurveFit implements CurveFit
{
    private static class LinearParameters implements CurveFit.Parameters
    {
        private double _slope;
        private double _intercept;

        public LinearParameters(double slope, double intercept)
        {
            _slope = slope;
            _intercept = intercept;
        }

        public double getSlope()
        {
            return _slope;
        }

        public double getIntercept()
        {
            return _intercept;
        }

        @Override
        public Map<String, Object> toMap()
        {
            return Map.of("slope", _slope, "intercept", _intercept);
        }
    }

    public LinearCurveFit(DoublePoint[] data)
    {
        super(data);
    }

    @Override
    public StatsService.CurveFitType getType()
    {
        return StatsService.CurveFitType.LINEAR;
    }

    @Override
    protected Parameters computeParameters()
    {
        SimpleRegression regression = new SimpleRegression(true);
        for (DoublePoint point : getData())
        {
            regression.addData(point.getX(), point.getY());
        }

        if (!Double.isNaN(regression.getRSquare()))
        {
            return new LinearParameters(regression.getSlope(), regression.getIntercept());
        }
        return null;
    }

    @Override
    public double fitCurve(double x)
    {
        try {
            return fitCurve(x, getParameters());
        }
        catch (FitFailedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double fitCurve(double x, Parameters parameters)
    {
        if (parameters instanceof LinearParameters)
        {
            double xValue = hasXLogScale() ? Math.log10(x) : x;
            return xValue * ((LinearParameters) parameters).getSlope() + ((LinearParameters) parameters).getIntercept();
        }
        throw new IllegalArgumentException("curveParameters must be an instance of LinearParameters");
    }

    @Override
    public double fitCurveY(double y)
    {
        try
        {
            Parameters parameters = getParameters();
            if (parameters instanceof LinearParameters)
            {
                LinearParameters lp = (LinearParameters)parameters;
                return (y - lp.getIntercept()) / lp.getSlope();
            }
            throw new IllegalArgumentException("curveParameters must be an instance of LinearParameters");
        }
        catch (FitFailedException e)
        {
            throw new RuntimeException(e);
        }
    }
}
