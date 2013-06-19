/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
 * User: klum
 * Date: Sep 2, 2009
 */
public abstract class ParameterCurveImpl extends WellGroupCurveImpl
{
    private FitParameters _fitParameters;
    private DilutionCurve.FitType _fitType;

    public interface SigmoidalParameters extends DilutionCurve.Parameters
    {
        double getAsymmetry();

        double getInflection();

        double getSlope();

        double getMax();

        double getMin();
    }

    public static class FitParameters implements Cloneable, SigmoidalParameters
    {
        public Double fitError;
        public double asymmetry;
        public double inflection;
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
    }

    public ParameterCurveImpl(List<? extends WellGroup> wellGroups, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator, DilutionCurve.FitType fitType) throws DilutionCurve.FitFailedException
    {
        super(wellGroups, assumeDecreasing, percentCalculator);
        _fitType = fitType;
    }

    public ParameterCurveImpl(List<? extends WellGroup> wellGroups, boolean assumeDecreasing, FitParameters params, FitType fitType) throws FitFailedException
    {
        this(wellGroups, assumeDecreasing, (DilutionCurve.PercentCalculator)null, fitType);
        _fitParameters = params;
    }

    protected DilutionCurve.DoublePoint[] renderCurve() throws DilutionCurve.FitFailedException
    {
        Map<WellData, WellGroup> wellDatas = getWellData();

        if (_fitParameters == null)
        {
            ensureWellSummaries();
            List<Double> percentages = new ArrayList<>(wellDatas.size());
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

            _fitParameters = calculateFitParameters(minPercentage, maxPercentage);
            _fitError = _fitParameters.fitError;
        }
        
        DilutionCurve.DoublePoint[] curveData = new DilutionCurve.DoublePoint[WellGroupCurveImpl.CURVE_SEGMENT_COUNT];
        double logX = Math.log10(getMinDilution());
        double logInterval = (Math.log10(getMaxDilution()) - logX) / (WellGroupCurveImpl.CURVE_SEGMENT_COUNT - 1);
        for (int i = 0; i < WellGroupCurveImpl.CURVE_SEGMENT_COUNT; i++)
        {
            double x = Math.pow(10, logX);
            double y = fitCurve(x, _fitParameters);
            curveData[i] = new DilutionCurve.DoublePoint(x, y);
            logX += logInterval;
        }
        return curveData;
    }

    public double fitCurve(double x, DilutionCurve.Parameters params)
    {
        if (params instanceof SigmoidalParameters)
        {
            SigmoidalParameters parameters = (SigmoidalParameters)params;
            return parameters.getMin() + ((parameters.getMax() - parameters.getMin()) /
                    Math.pow(1 + Math.pow(10, (Math.log10(parameters.getInflection()) - Math.log10(x)) * parameters.getSlope()), parameters.getAsymmetry()));
        }
        throw new IllegalArgumentException("params is not an instance of SigmoidalParameters");
    }

    private FitParameters calculateFitParameters(double minPercentage, double maxPercentage) throws DilutionCurve.FitFailedException
    {
        FitParameters bestFit = null;
        FitParameters parameters = new FitParameters();
        double step = 10;
        if (_fitType == DilutionCurve.FitType.FOUR_PARAMETER)
            parameters.asymmetry = 1;
        // try reasonable variants of max and min, in case there's a better fit.  We'll keep going past "reasonable" if
        // we haven't found a single bestFit option, but we need to bail out at some point.  We currently quit once max
        // reaches 200 or min reaches -100, since these values don't seem biologically reasonable.
        for (double min = minPercentage; (bestFit == null || min > 0 - step) && min > (minPercentage - 100); min -= step )
        {
            parameters.min = min;
            for (double max = maxPercentage; (bestFit == null || max <= 100 + step) && max < (maxPercentage + 100); max += step )
            {
                double absoluteCutoff = min + (0.5 * (max - min));
                double relativeEC50 = getInterpolatedCutoffDilution(absoluteCutoff/100);
                if (relativeEC50 != Double.POSITIVE_INFINITY && relativeEC50 != Double.NEGATIVE_INFINITY)
                {
                    parameters.max = max;
                    parameters.inflection = relativeEC50;
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
        }
        if (bestFit == null)
        {
            throw new DilutionCurve.FitFailedException("Unable to find any parameters to fit a curve to wellgroup " + _wellGroups.get(0).getName() +
                    ".  Your plate template may be invalid.  Please contact an administrator to report this problem.  " +
                    "Debug info: minPercentage = " + minPercentage + ", maxPercentage = " + maxPercentage + ", fitType = " +
                    _fitType.getLabel() + ", num data points = " + _wellSummaries.length);
        }
        return bestFit;
    }

    public DilutionCurve.Parameters getParameters() throws DilutionCurve.FitFailedException
    {
        ensureCurve();
        return _fitParameters;
    }

    public static class FourParameterCurve extends ParameterCurveImpl
    {
        public FourParameterCurve(List<? extends WellGroup> wellGroups, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator) throws DilutionCurve.FitFailedException
        {
            super(wellGroups, assumeDecreasing, percentCalculator, DilutionCurve.FitType.FOUR_PARAMETER);
        }

        public FourParameterCurve(List<? extends WellGroup> wellGroups, boolean assumeDecreasing, FitParameters params) throws DilutionCurve.FitFailedException
        {
            super(wellGroups, assumeDecreasing, params, DilutionCurve.FitType.FOUR_PARAMETER);
        }
    }

    public static class FiveParameterCurve extends ParameterCurveImpl
    {
        public FiveParameterCurve(List<? extends WellGroup> wellGroups, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator) throws DilutionCurve.FitFailedException
        {
            super(wellGroups, assumeDecreasing, percentCalculator, DilutionCurve.FitType.FIVE_PARAMETER);
        }

        public FiveParameterCurve(List<? extends WellGroup> wellGroups, boolean assumeDecreasing, FitParameters params) throws DilutionCurve.FitFailedException
        {
            super(wellGroups, assumeDecreasing, params, DilutionCurve.FitType.FIVE_PARAMETER);
        }
    }
}
