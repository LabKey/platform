/*
 * Copyright (c) 2014 LabKey Corporation
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
 * Created by klum on 1/16/14.
 */
public abstract class DefaultCurveFit implements CurveFit
{
    private static double EPSILON = 0.00001;

    protected Map<StatsService.AUCType, Double> _aucMap = new HashMap<>();
    protected List<AUCRange> _ranges = new ArrayList<>();
    protected Parameters _parameters;
    protected DoublePoint[] _data;
    protected DoublePoint _minX;        // first data point
    protected DoublePoint _maxX;        // last data point

    protected boolean _hasXLogScale = true;
    // Whether the curve is assumed to be decreasing by default.  Used only if the data points are too chaotic to provide a reasonable guess.
    protected boolean _assumeDecreasing = true;

    protected abstract Parameters computeParameters();

    public DefaultCurveFit(DoublePoint[] data)
    {
        _data = data;
    }

    @Override
    public void setData(DoublePoint[] data)
    {
        _data = data;
        _parameters = null;
    }

    @Override
    public DoublePoint[] getData()
    {
        return _data;
    }

    @Override
    public Parameters getParameters() throws FitFailedException
    {
        ensureCurve();
        return _parameters;
    }

    protected boolean hasXLogScale()
    {
        return _hasXLogScale;
    }

    @Override
    public void setAssumeCurveDecreasing(boolean decreasing)
    {
        _assumeDecreasing = decreasing;
    }

    @Override
    public void setLogXScale(boolean logXScale)
    {
        _hasXLogScale = logXScale;
    }

    private void ensureCurve() throws FitFailedException
    {
        if (_parameters == null)
        {
            try {
                _parameters = computeParameters();
            }
            catch (Exception e)
            {
                throw new FitFailedException("Failure calculating fit parameters: " + e.getMessage());
            }
        }
    }

    protected DoublePoint getMinimumX()
    {
        if (_minX == null)
        {
            DoublePoint min = null;
            for (DoublePoint point : getData())
            {
                if (min == null || point.getX() < min.getX())
                    min = point;
            }

            if (min == null)
                throw new IllegalStateException("Expected non-null minimum X.");

            _minX = min;
        }
        return _minX;
    }

    protected DoublePoint getMaximumX()
    {
        if (_maxX == null)
        {
            DoublePoint max = null;
            for (DoublePoint point : getData())
            {
                if (max == null || point.getX() > max.getX())
                    max = point;
            }

            if (max == null)
                throw new IllegalStateException("Expected non-null maximum X.");

            _maxX = max;
        }
        return _maxX;
    }

    @Override
    public double calculateAUC(StatsService.AUCType type) throws FitFailedException
    {
        return calculateAUC(type, getMinimumX().getX(), getMaximumX().getX());
    }

    /**
     * Calculate the area under the curve
     */
    public double calculateAUC(StatsService.AUCType type, double startX, double endX) throws FitFailedException
    {
        ensureCurve();
        if (!_aucMap.containsKey(type))
        {
            double auc = 0;

            for (AUCRange range : getRanges(startX, endX, type))
            {
                double factor;
                if (hasXLogScale())
                    factor = (1/(Math.log10(endX) - Math.log10(startX)));
                else
                    factor = (1/(endX - startX));

                if (range.getType() == type)
                {
                    double start = range.getStart();
                    double end = range.getEnd();

                    auc += factor * integrate(start, end, EPSILON, 10) / 100.0;
                }
            }
            _aucMap.put(type, auc);
        }
        return _aucMap.get(type);
    }

    private List<AUCRange> getRanges(double start, double end, StatsService.AUCType type)
    {
        if (type == StatsService.AUCType.NORMAL)
        {
            return Collections.singletonList(new AUCRange(start, end, StatsService.AUCType.NORMAL));
        }
        else
        {
            if (_ranges.isEmpty())
            {
                try {
                    Parameters parameters = getParameters();
                    AUCRange currentRange = null;

                    double step = (end - start) / 200;
                    double y;
                    double yPrev = 0;

                    for (double p = start; p < end; p += step)
                    {
                        y = fitCurve(p, parameters);

                        if (currentRange == null)
                        {
                            if (y != 0)
                                currentRange = new AUCRange(p, y > 0 ? StatsService.AUCType.POSITIVE : StatsService.AUCType.NEGATIVE);
                        }
                        else if (currentRange.isEnd(y))
                        {
                            // compute a more accurate estimate of the root
                            if (Math.abs(y) > .001)
                                p = findRoot(p-step, p, yPrev, y, parameters, 10, .001);
                            currentRange.setEnd(p);
                            StatsService.AUCType nextType = currentRange.getType() == StatsService.AUCType.POSITIVE ? StatsService.AUCType.NEGATIVE : StatsService.AUCType.POSITIVE;

                            _ranges.add(currentRange);
                            currentRange = new AUCRange(p, nextType);
                        }
                        yPrev = y;
                    }

                    if (currentRange != null)
                    {
                        currentRange.setEnd(end);
                        _ranges.add(currentRange);
                    }
                }
                catch (FitFailedException e)
                {
                    throw new RuntimeException(e);
                }
            }
            return _ranges;
        }
    }

    private double findRoot(double x1, double x2, double y1, double y2, CurveFit.Parameters parameters, int maxRecursionDepth, double error)
    {
        double mid = x1 + (x2 - x1) / 2;
        double y = fitCurve(mid, parameters);

        if (Math.abs(y) < error || maxRecursionDepth < 0)
            return mid;

        if ((y1 > 0 && y < 0) || (y1 < 0 && y > 0))
            return findRoot(x1, mid, y1, y, parameters, maxRecursionDepth-1, error);
        else
            return findRoot(mid, x2, y, y2, parameters, maxRecursionDepth-1, error);
    }

    /**
     * Approximate the integral of the curve using an adaptive simpsons rule.
     *
     * @param a lower bounds to integrate over
     * @param b upper bounds to integrate over
     * @param epsilon the error tolerance
     * @param maxRecursionDepth the maximum depth the algorithm will recurse to
     */
    private double integrate(double a, double b, double epsilon, int maxRecursionDepth)
    {
        try {
            Parameters parameters = getParameters();

            double c = (a + b)/2;
            double h = hasXLogScale() ? Math.log10(b) - Math.log10(a) : (b-a);
            double fa = fitCurve(a, parameters);
            double fb = fitCurve(b, parameters);
            double fc = fitCurve(c, parameters);
            double s = (h/6)*(fa + 4*fc + fb);

            return _integrate(a, b, epsilon, s, fa, fb, fc, maxRecursionDepth, parameters);
        }
        catch (FitFailedException e)
        {
            throw new RuntimeException(e);
        }
    }

    private double _integrate(double a, double b, double epsilon, double s,
                              double fa, double fb, double fc, int bottom, Parameters parameters)
    {
        double c = (a + b)/2;
        double h = hasXLogScale() ? Math.log10(b) - Math.log10(a) : (b-a);
        double d = (a + c)/2;
        double e = (c + b)/2;
        double fd = fitCurve(d, parameters);
        double fe = fitCurve(e, parameters);
        double sLeft = (h/12)*(fa + 4*fd + fc);
        double sRight = (h/12)*(fc + 4*fe + fb);
        double s2 = sLeft + sRight;

        if (bottom <= 0 || Math.abs(s2 - s) <= 15*epsilon)
            return s2 + (s2 - s)/15;
        return _integrate(a, c, epsilon/2, sLeft,  fa, fc, fd, bottom-1, parameters) +
                _integrate(c, b, epsilon/2, sRight, fc, fb, fe, bottom-1, parameters);
    }

    @Override
    public double getFitError() throws FitFailedException
    {
        ensureCurve();
        return calculateFitError(getParameters());
    }

    protected double calculateFitError(Parameters parameters)
    {
        double deviationValue = 0;
        for (DoublePoint point : getData())
        {
            double expectedValue = point.getY();
            double foundValue = fitCurve(point.getX(), parameters);
            deviationValue += Math.pow(foundValue - expectedValue, 2);
        }
        return Math.sqrt(deviationValue / getData().length);
    }

    @Override
    public DoublePoint[] renderCurve(int totalPoints) throws FitFailedException
    {
        return renderCurve(totalPoints, getMinimumX().getX(), getMaximumX().getX());
    }

    @Override
    public DoublePoint[] renderCurve(int totalPoints, double startX, double endX) throws FitFailedException
    {
        ensureCurve();
        DoublePoint[] curveData = new DoublePoint[totalPoints];
        double xValue = hasXLogScale() ? Math.log10(startX) : startX;
        double xInterval;
        if (hasXLogScale())
            xInterval = (Math.log10(endX) - xValue) / (totalPoints - 1);
        else
            xInterval = (endX - xValue) / (totalPoints - 1);

        for (int j = 0; j < totalPoints; j++)
        {
            double x = hasXLogScale() ? Math.pow(10, xValue) : xValue;
            double y = fitCurve(x);
            curveData[j] = new DoublePoint(x, y);
            xValue += xInterval;
        }
        return curveData;
    }

    private static class AUCRange
    {
        private StatsService.AUCType _type;
        private double _start;
        private double _end;

        public AUCRange(double start, double end, StatsService.AUCType type)
        {
            _start = start;
            _end = end;
            _type = type;
        }

        public AUCRange(double start, StatsService.AUCType type)
        {
            this(start, 0, type);
        }

        public boolean isEnd(double y)
        {
            return (_type == StatsService.AUCType.NEGATIVE && y >= 0) ||
                    (_type == StatsService.AUCType.POSITIVE && y < 0);
        }

        public StatsService.AUCType getType()
        {
            return _type;
        }

        public double getStart()
        {
            return _start;
        }

        public double getEnd()
        {
            return _end;
        }

        public void setEnd(double end)
        {
            _end = end;
        }
    }
}
