package org.labkey.core.statistics;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.json.old.JSONObject;
import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;

import java.util.Map;

public class LinearCurveFit extends DefaultCurveFit<LinearCurveFit.LinearParameters> implements CurveFit<LinearCurveFit.LinearParameters>
{
    public static class LinearParameters implements CurveFit.Parameters
    {
        private final double _slope;
        private final double _intercept;

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

        public static LinearParameters fromJSON(JSONObject json)
        {
            return new LinearParameters(json.getDouble("slope"), json.getDouble("intercept"));
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
    public void setParameters(JSONObject json)
    {
        _parameters = LinearParameters.fromJSON(json);
    }

    @Override
    protected LinearParameters computeParameters()
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
    public double fitCurve(double x, LinearParameters parameters)
    {
        if (parameters != null)
        {
            double xValue = hasXLogScale() ? Math.log10(x) : x;
            return xValue * parameters.getSlope() + parameters.getIntercept();
        }
        throw new IllegalArgumentException("No curve fit parameters for LinearCurveFit");
    }

    @Override
    public double solveForX(double y)
    {
        try
        {
            LinearParameters parameters = getParameters();
            if (parameters != null)
            {
                return (y - parameters.getIntercept()) / parameters.getSlope();
            }
            throw new IllegalArgumentException("No curve fit parameters for LinearCurveFit");
        }
        catch (FitFailedException e)
        {
            throw new RuntimeException(e);
        }
    }
}
