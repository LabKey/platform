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

package org.labkey.api.assay.dilution;

import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
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
    protected WellSummary[] _wellSummaries = null;
    private CurveFit _curveFit;

    public WellGroupCurveImpl(List<? extends WellGroup> wellGroups, boolean assumeDecreasing, PercentCalculator percentCalculator, StatsService.CurveFitType fitType) throws FitFailedException
    {
        _wellGroups = wellGroups;
        this.assumeDecreasing = assumeDecreasing;
        _percentCalculator = percentCalculator;
        _curveFit = createCurveFit(fitType);
    }

    protected abstract CurveFit createCurveFit(StatsService.CurveFitType fitType) throws FitFailedException;

    protected DoublePoint[] renderCurve() throws FitFailedException
    {
        return isValid() ? _curveFit.renderCurve(CURVE_SEGMENT_COUNT) : new DoublePoint[0];
    }

    public double fitCurve(double x, CurveFit.Parameters curveParameters)
    {
        return _curveFit.fitCurve(x);
    }

    public CurveFit.Parameters getParameters() throws FitFailedException
    {
        return _curveFit.getParameters();
    }

    @Override
    public double getFitError()
    {
        try {
            return isValid() ? _curveFit.getFitError() : 0;
        }
        catch (FitFailedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double calculateAUC(StatsService.AUCType type) throws FitFailedException
    {
        return isValid() ? _curveFit.calculateAUC(type) : 0;
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
        if (!isValid())
            return 0;

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
        if (!isValid())
            return 0;

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
        if (!isValid())
            return 0;

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
            {
                if (!Double.isNaN(data.getMean()))
                    dataMap.put(data, wellgroup);
            }
        }
        return dataMap;
    }

    @Override
    public boolean isValid()
    {
        Map<WellData, WellGroup> data = getWellData();
        return !data.isEmpty();
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
        if (!isValid())
            return 0;

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
}
