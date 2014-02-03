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

import Jama.Matrix;
import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by klum on 1/16/14.
 */
public class PolynomialCurveFit extends DefaultCurveFit implements CurveFit
{
    private static int ORDER = 3;       // the order of the polynomial

    private static class PolynomialParameters implements CurveFit.Parameters
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

    public PolynomialCurveFit(DoublePoint[] data)
    {
        super(data);
    }

    /**
     * Calculates the coefficients of an n-order polynomial using a least squares fit
     */
    @Override
    protected Parameters computeParameters()
    {
        assert getData() != null;

        DoublePoint[] data = getData();
        Matrix matrixA = new Matrix(data.length, ORDER);
        Matrix matrixB = new Matrix(data.length, 1);

        for (int i=0; i < ORDER; i++)
        {
            int j=0;
            for (DoublePoint point : data)
            {
                double xvalue = hasXLogScale() ? Math.log10(point.getX()) : point.getX();
                matrixA.set(j++, i, Math.pow(xvalue, i));
            }
        }

        int i=0;
        for (DoublePoint point : data)
            matrixB.set(i++, 0, point.getY());


        double[] ab = matrixA.solve(matrixB).getRowPackedCopy();
        return new PolynomialParameters(ab);
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
    public double fitCurve(double x, Parameters curveParameters)
    {
        if (curveParameters instanceof PolynomialParameters)
        {
            double[] params = ((PolynomialParameters)curveParameters).getCoefficients();
            double y = 0;
            double xValue = 0;

            for (int i=0; i < params.length; i++)
            {
                xValue = hasXLogScale() ? Math.log10(x) : x;
                y += params[i] * Math.pow(xValue, i);
            }

            return y;
        }
        throw new IllegalArgumentException("curveParameters must be an instance of PolynomialParameters");
    }
}
