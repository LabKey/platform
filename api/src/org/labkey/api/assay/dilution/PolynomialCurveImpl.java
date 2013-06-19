/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

import Jama.Matrix;
import org.labkey.api.study.WellGroup;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * User: klum
 * Date: Sep 2, 2009
 */
public class PolynomialCurveImpl extends WellGroupCurveImpl
{
    private static int ORDER = 3;       // the order of the polynomial
    private PolynomialParameters _parameters;

    private static class PolynomialParameters implements Parameters
    {
        private double[] _coefficients = new double[ORDER];

        public PolynomialParameters(double[] coefficients)
        {
            _coefficients = coefficients;
        }

        public double[] getCoefficients()
        {
            return _coefficients;
        }

        public void setCoefficients(double[] coefficients)
        {
            _coefficients = coefficients;
        }

        public Map<String, Object> toMap()
        {
            Map<String, Object> params = new HashMap<>();

            int i = 0;
            for (double beta : _coefficients)
            {
                params.put("beta" + i++, beta);
            }
            return Collections.unmodifiableMap(params);
        }
    }

    public PolynomialCurveImpl(List<WellGroup> wellGroups, boolean assumeDecreasing, PercentCalculator percentCalculator) throws FitFailedException
    {
        super(wellGroups, assumeDecreasing, percentCalculator);
    }

    protected DoublePoint[] renderCurve() throws FitFailedException
    {
        if (_parameters == null)
        {
            ensureWellSummaries();
            // initialize the data points and calculate the polynomial coefficients
            double[] ya = new double[_wellSummaries.length];
            double[] xa = new double[_wellSummaries.length];

            int i=0;
            for (WellSummary well : _wellSummaries)
            {
                ya[i] = well.getNeutralization() * 100;
                xa[i++] = Math.log10(well.getDilution());
            }

            _parameters = calculateParameters(xa, ya);
            _fitError = calculateFitError(_parameters);
        }

        DoublePoint[] curveData = new DoublePoint[CURVE_SEGMENT_COUNT];
        double logX = Math.log10(getMinDilution());
        double logInterval = (Math.log10(getMaxDilution()) - logX) / (CURVE_SEGMENT_COUNT - 1);
        for (int j = 0; j < CURVE_SEGMENT_COUNT; j++)
        {
            double x = Math.pow(10, logX);
            double y = fitCurve(x, _parameters);
            curveData[j] = new DoublePoint(x, y);
            logX += logInterval;
        }
        return curveData;
    }

    public double fitCurve(double x, Parameters curveParameters)
    {
        if (curveParameters instanceof PolynomialParameters)
        {
            double[] params = ((PolynomialParameters)curveParameters).getCoefficients();
            double y = 0;

            for (int i=0; i < params.length; i++)
                y += params[i] * Math.pow(Math.log10(x), i);

            return y;
        }
        throw new IllegalArgumentException("curveParameters must be an instance of PolynomialParameters");
    }

    /**
     * Calculates the coefficients of an n-order polynomial using a least squares fit
     */
    private PolynomialParameters calculateParameters(double[] x, double[] y)
    {
        Matrix matrixA = new Matrix(x.length, ORDER);
        Matrix matrixB = new Matrix(x.length, 1);

        for (int i=0; i < ORDER; i++)
        {
            for (int j=0; j < x.length; j++)
                matrixA.set(j, i, Math.pow(x[j], i));
        }
        for (int i=0; i < x.length; i++)
            matrixB.set(i, 0, y[i]);

        double[] ab = matrixA.solve(matrixB).getRowPackedCopy();
        return new PolynomialParameters(ab);
    }

    public Parameters getParameters() throws FitFailedException
    {
        ensureCurve();
        return _parameters;
    }
}
