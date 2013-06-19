/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.api.assay.dilution;

import org.labkey.api.study.WellData;
import org.labkey.api.study.WellGroup;

import java.util.*;

/**
 * User: brittp
 * Date: Oct 25, 2006
 * Time: 4:45:19 PM
 */
public abstract class WellGroupCurveImpl implements DilutionCurve
{
    protected static final int CURVE_SEGMENT_COUNT = 100;

    protected List<? extends WellGroup> _wellGroups;
    protected DoublePoint[] _curve = null;
    protected boolean assumeDecreasing;
    private final PercentCalculator _percentCalculator;
    protected Double _fitError;
    protected WellSummary[] _wellSummaries = null;
    protected Map<AUCType, Double> _aucMap = new HashMap<>();
    protected List<AUCRange> _ranges = new ArrayList<>();

    public WellGroupCurveImpl(List<? extends WellGroup> wellGroups, boolean assumeDecreasing, PercentCalculator percentCalculator) throws FitFailedException
    {
        _wellGroups = wellGroups;
        this.assumeDecreasing = assumeDecreasing;
        _percentCalculator = percentCalculator;
    }

    protected WellSummary[] ensureWellSummaries() throws FitFailedException
    {
        Map<WellData, WellGroup> data = getWellData();
        _wellSummaries = new WellSummary[data.size()];
        int index = 0;
        for (Map.Entry<WellData, WellGroup> entry : data.entrySet())
        {
            WellData well = entry.getKey();
            _wellSummaries[index++] = new WellSummary(_percentCalculator.getPercent(entry.getValue(), well), well.getDilution());
        }
        return _wellSummaries;
    }

    private double getMaxPercentage() throws FitFailedException
    {
        DoublePoint[] curve = getCurve();
        double max = curve[0].getY();
        for (int i = 1; i < CURVE_SEGMENT_COUNT; i++)
        {
            double percent = curve[i].getY();
            if (percent > max)
                max = percent;
        }
        return max;
    }

    public double getMaxDilution()
    {
        Set<WellData> datas = getWellData().keySet();
        Double max = null;
        for (WellData data : datas)
        {
            if (max == null || (data.getDilution() != null && data.getDilution() > max))
                max = data.getDilution();
        }
        if (max == null)
            throw new IllegalStateException("Expected non-null dilution.");
        return max;
    }

    public double getMinDilution()
    {
        Set<WellData> datas = getWellData().keySet();
        Double min = null;
        for (WellData data : datas)
        {
            if (min == null || (data.getDilution() != null && data.getDilution() < min))
                min = data.getDilution();
        }
        if (min == null)
            throw new IllegalStateException("Expected non-null dilution.");
        return min;
    }

    protected Map<WellData, WellGroup> getWellData()
    {
        Map<WellData, WellGroup> dataMap = new LinkedHashMap<>();
        for (WellGroup wellgroup : _wellGroups)
        {
            for (WellData data : wellgroup.getWellData(true))
                dataMap.put(data, wellgroup);
        }
        return dataMap;
    }

    protected class WellSummary
    {
        private double _neutralization;
        private double _dilution;

        public WellSummary(double neutralization, double dilution)
        {
            _dilution = dilution;
            _neutralization = neutralization;
        }

        public double getNeutralization()
        {
            return _neutralization;
        }

        public double getDilution()
        {
            return _dilution;
        }
    }

    public double getInterpolatedCutoffDilution(double cutoff)
    {
        boolean dataAbove = false;
        List<Double> possibleMatches = new ArrayList<>();
        for (int i = 1; i < _wellSummaries.length; i++)
        {
            double high = _wellSummaries[i - 1].getNeutralization();
            double low = _wellSummaries[i].getNeutralization();
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
                double logDilHigh = Math.log10(_wellSummaries[i - 1].getDilution());
                double logDilLow = Math.log10(_wellSummaries[i].getDilution());
                if (reverseCurve)
                {
                    double temp = logDilHigh;
                    logDilHigh = logDilLow;
                    logDilLow = temp;
                }
                if (low != high)
                    possibleMatches.add(Math.pow(10, logDilLow - (low - cutoff) * (logDilLow - logDilHigh) / (low - high)));
                else
                    possibleMatches.add((logDilLow + logDilHigh) / 2);
            }
            else if (low > cutoff)
                dataAbove = true;
        }
        if (possibleMatches.size() > 0)
        {
            double total = 0;
            for (Double d : possibleMatches)
                total += d;
            return total / possibleMatches.size();
        }
        return getOutOfRangeValue(dataAbove);
    }

    public double getCutoffDilution(double cutoff) throws FitFailedException
    {
        DoublePoint[] curve = getCurve();

        // convert from decimal to percent, to match our curve values:
        cutoff *= 100;
        if (cutoff > getMaxPercentage())
            return getOutOfRangeValue(false);

        for (int i = 1; i < CURVE_SEGMENT_COUNT; i++)
        {
            double highPercent = curve[i - 1].getY();
            double lowPercent = curve[i].getY();
            if (highPercent < lowPercent)
            {
                double temp = highPercent;
                highPercent = lowPercent;
                lowPercent = temp;
            }
            if (highPercent >= cutoff && lowPercent <= cutoff)
            {
                double logDilHigh = Math.log10(curve[i - 1].getX());
                double logDilLow = Math.log10(curve[i].getX());
                if (logDilHigh < logDilLow)
                {
                    double temp = logDilHigh;
                    logDilHigh = logDilLow;
                    logDilLow = temp;
                }
                return Math.pow(10, logDilLow - (lowPercent - cutoff) * (logDilLow - logDilHigh) / (lowPercent - highPercent));
            }
        }

        return getOutOfRangeValue(true);
    }

    private double getOutOfRangeValue(boolean alwaysAboveCutoff)
    {
        if (alwaysAboveCutoff)
        {
            if (assumeDecreasing)
                return Double.POSITIVE_INFINITY;
            else
                return Double.NEGATIVE_INFINITY;
        }
        else
        {
            if (assumeDecreasing)
                return Double.NEGATIVE_INFINITY;
            else
                return Double.POSITIVE_INFINITY;
        }
    }

    protected void ensureCurve() throws FitFailedException
    {
        getCurve();
    }

    public DoublePoint[] getCurve() throws FitFailedException
    {
        // NAb caches the assay's data in the HTTP session, so multiple threads may be requesting its data at the
        // same time
        synchronized (this)
        {
            if (_curve == null)
                _curve = renderCurve();
            return _curve;
        }
    }

    protected abstract DoublePoint[] renderCurve() throws FitFailedException;

    public abstract Parameters getParameters() throws FitFailedException;

    public double getFitError() throws FitFailedException
    {
        ensureCurve();
        return _fitError;
    }

    /**
     * Calculate the area under the curve
     */
    public double calculateAUC(AUCType type) throws FitFailedException
    {
        if (!_aucMap.containsKey(type))
        {
            double min = getMinDilution();
            double max = getMaxDilution();
            double auc = 0;

            for (AUCRange range : getRanges(min, max, type))
            {
                double factor = (1/(Math.log10(max) - Math.log10(min)));

                if (range.getType() == type)
                {
                    double start = range.getStart();
                    double end = range.getEnd();

                    auc += factor * integrate(start, end, 0.00001, 10) / 100.0;
                }
            }
            _aucMap.put(type, auc);
        }
        return _aucMap.get(type);
    }

    private List<AUCRange> getRanges(double start, double end, AUCType type) throws FitFailedException
    {
        if (type == AUCType.NORMAL)
        {
            return Collections.singletonList(new AUCRange(start, end, AUCType.NORMAL));
        }
        else
        {
            if (_ranges.isEmpty())
            {
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
                            currentRange = new AUCRange(p, y > 0 ? AUCType.POSITIVE : AUCType.NEGATIVE);
                    }
                    else if (currentRange.isEnd(y))
                    {
                        // compute a more accurate estimate of the root
                        if (Math.abs(y) > .001)
                            p = findRoot(p-step, p, yPrev, y, parameters, 10, .001);
                        currentRange.setEnd(p);
                        AUCType nextType = currentRange.getType() == AUCType.POSITIVE ? AUCType.NEGATIVE : AUCType.POSITIVE;

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
            return _ranges;
        }
    }

    private double findRoot(double x1, double x2, double y1, double y2, Parameters parameters, int maxRecursionDepth, double error)
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

    private static class AUCRange
    {
        private AUCType _type;
        private double _start;
        private double _end;

        public AUCRange(double start, double end, AUCType type)
        {
            _start = start;
            _end = end;
            _type = type;
        }

        public AUCRange(double start, AUCType type)
        {
            this(start, 0, type);
        }

        public boolean isEnd(double y)
        {
            return (_type == AUCType.NEGATIVE && y >= 0) ||
                   (_type == AUCType.POSITIVE && y < 0);
        }

        public AUCType getType()
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

    /**
     * Approximate the integral of the curve using an adaptive simpsons rule.
     *
     * @param a lower bounds to integrate over
     * @param b upper bounds to integrate over
     * @param epsilon the error tolerance
     * @param maxRecursionDepth the maximum depth the algorithm will recurse to
     *
     * @throws FitFailedException
     */
    private double integrate(double a, double b, double epsilon, int maxRecursionDepth) throws FitFailedException
    {
        Parameters parameters = getParameters();

        double c = (a + b)/2;
        double h = Math.log10(b) - Math.log10(a);
        double fa = fitCurve(a, parameters);
        double fb = fitCurve(b, parameters);
        double fc = fitCurve(c, parameters);
        double s = (h/6)*(fa + 4*fc + fb);

        return _integrate(a, b, epsilon, s, fa, fb, fc, maxRecursionDepth, parameters);
    }

    private double _integrate(double a, double b, double epsilon, double s,
                              double fa, double fb, double fc, int bottom, Parameters parameters) throws FitFailedException
    {
        double c = (a + b)/2;
        double h = Math.log10(b) - Math.log10(a);
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

    protected double calculateFitError(Parameters parameters)
    {
        double deviationValue = 0;
        for (WellSummary well : _wellSummaries)
        {
            double dilution = well.getDilution();
            double expectedPercentage = 100 * well.getNeutralization();
            double foundPercentage = fitCurve(dilution, parameters);
            deviationValue += Math.pow(foundPercentage - expectedPercentage, 2);
        }
        return Math.sqrt(deviationValue / _wellSummaries.length);
    }
}
