/*
 * Copyright (c) 2006-2007 LabKey Corporation
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

import org.labkey.api.study.WellData;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.DilutionCurve;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * User: brittp
 * Date: Oct 25, 2006
 * Time: 4:45:19 PM
 */
public class WellGroupCurveImpl implements DilutionCurve
{
    protected static final int CURVE_SEGMENT_COUNT = 100;

    protected WellGroup _wellGroup;
    private DoublePoint[] _curve = null;
    private boolean assumeDecreasing;
    private FitType _fitType;
    private Double _fitError;
    private Double _slope;
    private WellSummary[] _wellSummaries = null;

    private class FitParameters implements Cloneable
    {
        public Double fitError;
        public double asymmetry;
        public double EC50;
        public double slope;
        public double max;
        public double min;

        public FitParameters copy()
        {
            try
            {
                return (FitParameters) super.clone();
            }
            catch (CloneNotSupportedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public WellGroupCurveImpl(WellGroup wellGroup, boolean assumeDecreasing, PercentCalculator percentCalculator, DilutionCurve.FitType fitType)
    {
        _wellGroup = wellGroup;
        this.assumeDecreasing = assumeDecreasing;
        _fitType = fitType;

        List<WellData> data = getWellData();
        _wellSummaries = new WellSummary[data.size()];
        for (int i = 0; i < data.size(); i++)
        {
            WellData well = data.get(i);
            _wellSummaries[i] = new WellSummary(percentCalculator.getPercent(_wellGroup, data.get(i)), well.getDilution());
        }
    }

    public double getMaxPercentage()
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

    public double getMinPercentage()
    {
        DoublePoint[] curve = getCurve();
        double min = curve[0].getY();
        for (int i = 1; i < CURVE_SEGMENT_COUNT; i++)
        {
            double percent = curve[i].getY();
            if (percent < min)
                min = percent;
        }
        return min;
    }

    public double getMaxDilution()
    {
        List<WellData> datas = getWellData();
        double max = datas.get(0).getDilution();
        for (WellData data : datas)
        {
            if (data.getDilution() > max)
                max = data.getDilution();
        }
        return max;
    }

    public double getMinDilution()
    {
        List<WellData> datas = getWellData();
        double min = datas.get(0).getDilution();
        for (WellData data : datas)
        {
            if (data.getDilution() < min)
                min = data.getDilution();
        }
        return min;
    }

    private List<WellData> getWellData()
    {
        return _wellGroup.getWellData(true);
    }

    private class WellSummary
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
        List<Double> possibleMatches = new ArrayList<Double>();
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

    public double getCutoffDilution(double cutoff)
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

    private void ensureCurve()
    {
        getCurve();
    }

    public DoublePoint[] getCurve()
    {
        if (_curve == null)
            _curve = renderCurve();
        return _curve;
    }

    private DoublePoint[] renderCurve()
    {
        List<WellData> wellDatas = getWellData();
        List<Double> percentages = new ArrayList<Double>(wellDatas.size());
        for (WellSummary well : _wellSummaries)
        {
            double percentage = 100 * well.getNeutralization();
            percentages.add(percentage);
        }
        Collections.sort(percentages);

        // use relative percentage rather than fixed 50% value.  Divide by 2*100 to average
        // and convert back to 0.0-1.0 percentage form:
        double minPercentage = percentages.get(0);
        double maxPercentage = percentages.get(percentages.size() - 1);

        FitParameters parameters = calculateFitParameters(minPercentage, maxPercentage);
        _fitError = parameters.fitError;
        _slope = parameters.slope;
        DoublePoint[] curveData = new DoublePoint[CURVE_SEGMENT_COUNT];
        double logX = Math.log10(_wellGroup.getMinDilution());
        double logInterval = (Math.log10(_wellGroup.getMaxDilution()) - logX) / (CURVE_SEGMENT_COUNT - 1);
        for (int i = 0; i < CURVE_SEGMENT_COUNT; i++)
        {
            double x = Math.pow(10, logX);
            double y = fitCurve(x, parameters);
            curveData[i] = new DoublePoint(x, y);
            logX += logInterval;
        }
        return curveData;
    }

    private double fitCurve(double x, FitParameters parameters)
    {
        return parameters.min + ((parameters.max - parameters.min) /
                Math.pow(1 + Math.pow(10, (Math.log10(parameters.EC50) - Math.log10(x)) * parameters.slope), parameters.asymmetry));
    }

    private FitParameters calculateFitParameters(double minPercentage, double maxPercentage)
    {
        FitParameters bestFit = null;
        FitParameters parameters = new FitParameters();
        double step = 10;
        if (_fitType == FitType.FOUR_PARAMETER)
            parameters.asymmetry = 1;
        
        for (double min = minPercentage; bestFit == null || min > 0 - step; min -= step )
        {
            parameters.min = min;
            for (double max = maxPercentage; bestFit == null || max <= 100 + step; max += step )
            {
                double absoluteCutoff = min + (0.5 * (max - min));
                double relativeEC50 = getInterpolatedCutoffDilution(absoluteCutoff/100);
                parameters.max = max;
                parameters.EC50 = relativeEC50;
                for (double slopeRadians = 0; slopeRadians < Math.PI; slopeRadians += Math.PI / 30)
                {
                    parameters.slope = Math.tan(slopeRadians);
                    switch (_fitType)
                    {
                        case FIVE_PARAMETER:
                            for (double asymmetryFactor = 0; asymmetryFactor < Math.PI; asymmetryFactor += Math.PI / 30)
                            {
                                parameters.asymmetry = asymmetryFactor;
                                parameters.fitError = calculateFitError(parameters);
                                if (bestFit == null || parameters.fitError < bestFit.fitError)
                                    bestFit = parameters.copy();
                            }
                            break;
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
        return bestFit;
    }

    private double calculateFitError(FitParameters parameters)
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

    public double getFitError()
    {
        ensureCurve();
        return _fitError;
    }

    public double getSlope()
    {
        ensureCurve();
        return _slope;
    }
}
