/*
 * Copyright (c) 2014-2026 LabKey Corporation
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
package org.labkey.core.statistics;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 1/20/14.
 */
public class ParameterCurveFit extends DefaultCurveFit<ParameterCurveFit.SigmoidalParameters> implements CurveFit<ParameterCurveFit.SigmoidalParameters>
{
    protected final StatsService.CurveFitType _fitType;
    private Double _asymptoteMin;
    private Double _asymptoteMax;

    public static class SigmoidalParameters implements CurveFit.Parameters, Cloneable
    {
        public Double fitError;
        public double asymmetry;
        public double inflection;
        public double slope;
        public double max;
        public double min;

        public SigmoidalParameters copy()
        {
            try
            {
                return (SigmoidalParameters) super.clone();
            }
            catch (CloneNotSupportedException e)
            {
                throw new RuntimeException(e);
            }
        }

        public Double getFitError()
        {
            return fitError;
        }

        public double getAsymmetry()
        {
            return asymmetry;
        }

        public double getInflection()
        {
            return inflection;
        }

        public double getSlope()
        {
            return slope;
        }

        public double getMax()
        {
            return max;
        }

        public double getMin()
        {
            return min;
        }

        @Override
        public Map<String, Object> toMap()
        {
            Map<String, Object> params = new HashMap<>();
            params.put("asymmetry", getAsymmetry());
            params.put("inflection", getInflection());
            params.put("slope", getSlope());
            params.put("max", getMax());
            params.put("min", getMin());

            return Collections.unmodifiableMap(params);
        }

        public static SigmoidalParameters fromJSON(JSONObject json)
        {
            SigmoidalParameters parameters = new SigmoidalParameters();

            parameters.asymmetry = json.getDouble("asymmetry");
            parameters.inflection = json.getDouble("inflection");
            parameters.slope = json.getDouble("slope");
            parameters.max = json.getDouble("max");
            parameters.min = json.getDouble("min");

            return parameters;
        }
    }

    public ParameterCurveFit(DoublePoint[] data, StatsService.CurveFitType fitType, @Nullable Double asymptoteMin, @Nullable Double asymptoteMax)
    {
        super(data);
        _fitType = fitType;
        setAsymptoteMin(asymptoteMin);
        setAsymptoteMax(asymptoteMax);
    }

    @Override
    public StatsService.CurveFitType getType()
    {
        return _fitType;
    }

    public Double getAsymptoteMin()
    {
        return _asymptoteMin;
    }

    public void setAsymptoteMin(Double asymptoteMin)
    {
        _asymptoteMin = asymptoteMin;
    }

    public Double getAsymptoteMax()
    {
        return _asymptoteMax;
    }

    public void setAsymptoteMax(Double asymptoteMax)
    {
        _asymptoteMax = asymptoteMax;
    }

    @Override
    protected SigmoidalParameters computeParameters()
    {
        assert getData() != null;

        DoublePoint[] data = getData();
        List<Double> values = new ArrayList<>(data.length);
        for (DoublePoint point : data)
            values.add(point.getY());
        Collections.sort(values);

        double minValue = values.getFirst();
        double maxValue = values.getLast();

        return calculateFitParameters(minValue, maxValue);
    }

    @Override
    public void setParameters(JSONObject json)
    {
        _parameters = SigmoidalParameters.fromJSON(json);
    }

    @Override
    public double fitCurve(double x)
    {
        try
        {
            return fitCurve(x, getParameters());
        }
        catch (FitFailedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double fitCurve(double x, SigmoidalParameters params)
    {
        if (params != null)
        {
            if (hasXLogScale())
                return params.getMin() + ((params.getMax() - params.getMin()) /
                        Math.pow(1 + Math.pow(10, (Math.log10(params.getInflection()) - Math.log10(x)) * params.getSlope()), params.getAsymmetry()));
            else
                return params.getMin() + ((params.getMax() - params.getMin()) /
                        Math.pow(1 + ((params.getInflection() - x) * params.getSlope()), params.getAsymmetry()));

        }
        throw new IllegalArgumentException("No curve fit parameters for " + _fitType.name());
    }

    @Override
    public double solveForX(double y)
    {
        try
        {
            SigmoidalParameters params = getParameters();
            if (params != null)
            {
                double exp = (params.getMax()-params.getMin())/(y- params.getMin());

                return params.getInflection() - ((Math.pow(exp, 1d/params.getAsymmetry())- 1) / params.getSlope());
            }
            throw new IllegalArgumentException("No curve fit parameters for " + _fitType.name());
        }
        catch (FitFailedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double adjustedRSquared(SigmoidalParameters parameters)
    {
        return switch (_fitType)
        {
            case THREE_PARAMETER, THREE_PARAMETER_ALT -> adjustedRSquared(parameters, 3);
            case FOUR_PARAMETER -> adjustedRSquared(parameters, 4);
            case FIVE_PARAMETER, FIVE_PARAMETER_ALT -> adjustedRSquared(parameters, 5);
            default -> throw new IllegalStateException("Unsupported curve fit type: " + _fitType.name());
        };
    }

    private boolean is3Parameter()
    {
        return _fitType == StatsService.CurveFitType.THREE_PARAMETER || _fitType == StatsService.CurveFitType.THREE_PARAMETER_ALT;
    }

    private boolean is4Parameter()
    {
        return _fitType == StatsService.CurveFitType.FOUR_PARAMETER;
    }

    protected SigmoidalParameters calculateFitParameters(double minValue, double maxValue)
    {
        SigmoidalParameters bestFit = null;
        SigmoidalParameters parameters = new SigmoidalParameters();
        Double asymptoteDiff = getAsymptoteMax() != null && getAsymptoteMin() != null ? Math.abs(getAsymptoteMax() - getAsymptoteMin()) : null;
        double step = asymptoteDiff != null ? Math.min(asymptoteDiff / 100, 10) : 10;
        if (is3Parameter() || is4Parameter())
            parameters.asymmetry = 1;

        double slopeRadiansSteps = Math.PI / 30;
        if (is3Parameter())
            slopeRadiansSteps = Math.PI / 180;

        // try reasonable variants of max and min, in case there's a better fit.  We'll keep going past "reasonable" if
        // we haven't found a single bestFit option, but we need to bail out at some point.  We currently quit once max
        // reaches 200 or min reaches -100, since these values don't seem biologically reasonable.
        for (double min = minValue; (bestFit == null || min > 0 - step) && min > (minValue - 100); min -= step )
        {
            parameters.min = getAsymptoteMin() != null ? getAsymptoteMin() : min;
            for (double max = maxValue; (bestFit == null || max <= 100 + step) && max < (maxValue + 100); max += step )
            {
                double absoluteCutoff = min + (0.5 * (max - min));
                double relativeEC50 = getInterpolatedCutoffXValue(absoluteCutoff);
                if (!Double.isInfinite(relativeEC50) && !Double.isNaN(relativeEC50))
                {
                    parameters.max = getAsymptoteMax() != null ? getAsymptoteMax() : max;
                    parameters.inflection = relativeEC50;
                    for (double slopeRadians = 0; slopeRadians < Math.PI; slopeRadians += slopeRadiansSteps)
                    {
                        parameters.slope = Math.tan(slopeRadians);
                        switch (_fitType)
                        {
                            case FIVE_PARAMETER:
                            case FIVE_PARAMETER_ALT:
                                for (double asymmetryFactor = 0; asymmetryFactor < Math.PI; asymmetryFactor += Math.PI / 30)
                                {
                                    parameters.asymmetry = asymmetryFactor;
                                    parameters.fitError = calculateFitError(parameters);
                                    if (bestFit == null || parameters.fitError < bestFit.fitError)
                                        bestFit = parameters.copy();
                                }
                                break;
                            case THREE_PARAMETER:
                            case THREE_PARAMETER_ALT:
                            case FOUR_PARAMETER:
                                parameters.asymmetry = 1;
                                parameters.fitError = calculateFitError(parameters);
                                if (bestFit == null || parameters.fitError < bestFit.fitError)
                                    bestFit = parameters.copy();
                                break;
                        }
                    }
                }
            }
        }
        if (bestFit == null)
        {
            // Consider : throwing a specific error or moving DilutionCurve.FitFailedException to this class
            throw new IllegalStateException("Unable to find any parameters to fit a curve to your data. " +
                    "Please contact an administrator to report this problem.  " +
                    "Debug info: minValue = " + minValue + ", maxValue = " + maxValue + ", fitType = " +
                    _fitType.name() + ", num data points = " + getData().length);
        }
        return bestFit;
    }

    public double getInterpolatedCutoffXValue(double cutoff)
    {
        boolean dataAbove = false;
        List<Double> possibleMatches = new ArrayList<>();
        DoublePoint[] data = getData();
        for (int i = 1; i < data.length; i++)
        {
            double high = data[i - 1].getY();
            double low = data[i].getY();
            boolean reverseCurve = false;
            if (high < low)
            {
                double temp = low;
                low = high;
                high = temp;
                reverseCurve = true;
            }
            if (high >= cutoff && low <= cutoff)
            {
                double logXHigh = hasXLogScale() ? Math.log10(data[i-1].getX()) : data[i-1].getX();
                double logXLow = hasXLogScale() ? Math.log10(data[i].getX()) : data[i].getX();

                // ensure the range is a valid number
                logXHigh = (Double.isInfinite(logXHigh) || Double.isNaN(logXHigh)) ? 0 : logXHigh;
                logXLow = (Double.isInfinite(logXLow) || Double.isNaN(logXLow)) ? 0 : logXLow;

                if (reverseCurve)
                {
                    double temp = logXHigh;
                    logXHigh = logXLow;
                    logXLow = temp;
                }
                if (low != high)
                {
                    if (hasXLogScale())
                        possibleMatches.add(Math.pow(10, logXLow - (low - cutoff) * (logXLow - logXHigh) / (low - high)));
                    else
                        possibleMatches.add(logXLow - (low - cutoff) * (logXLow - logXHigh) / (low - high));
                }
                else
                    possibleMatches.add((logXLow + logXHigh) / 2);
            }
            else if (low > cutoff)
                dataAbove = true;
        }
        if (!possibleMatches.isEmpty())
        {
            double total = 0;
            for (Double d : possibleMatches)
                total += d;
            return total / possibleMatches.size();
        }
        return getOutOfRangeValue(dataAbove);
    }

    private double getOutOfRangeValue(boolean alwaysAboveCutoff)
    {
        if (alwaysAboveCutoff)
        {
            if (_assumeDecreasing)
                return Double.POSITIVE_INFINITY;
            else
                return Double.NEGATIVE_INFINITY;
        }
        else
        {
            if (_assumeDecreasing)
                return Double.NEGATIVE_INFINITY;
            else
                return Double.POSITIVE_INFINITY;
        }
    }
}
