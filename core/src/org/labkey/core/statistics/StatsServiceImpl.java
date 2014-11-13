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

import org.apache.commons.math3.random.RandomDataImpl;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.MathStat;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.view.Stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 1/14/14.
 */
public class StatsServiceImpl implements StatsService
{
    @Override
    public MathStat getStats(double[] data)
    {
        return new Stats.DoubleStats(data);
    }

    @Override
    public CurveFit getCurveFit(CurveFitType type, DoublePoint[] data)
    {
        switch (type)
        {
            case FOUR_PARAMETER:
            case FIVE_PARAMETER:
                return new ParameterCurveFit(data, type);
            case POLYNOMIAL:
                return new PolynomialCurveFit(data);
            case NONE:
                return new NoCurveFit(data);
        }
        throw new IllegalArgumentException("Unable to find a CurveFit implementation for type: " + type.name());
    }

    public static class TestCase extends Assert
    {
        private static final int TEST_DATA_SIZE = 100;

        @Test
        public void TestStats() throws Exception
        {
            double[] testData = new double[TEST_DATA_SIZE];
            RandomDataImpl generator = new RandomDataImpl();
            StatsService service = new StatsServiceImpl();
            SummaryStatistics statsChecker = new SummaryStatistics();

            for (int i=0; i < 10; i++)
            {
                // generate some random data to get compute stas over
                for (int j=0; j < TEST_DATA_SIZE; j++)
                    testData[j] = generator.nextGaussian(0.25, 1.2);

                // add the test data to the checker
                statsChecker.clear();
                for (double n : testData)
                    statsChecker.addValue(n);

                MathStat stat = service.getStats(testData);

                // compare results
                assertEquals(stat.getMaximum(), statsChecker.getMax(), 0);
                assertEquals(stat.getMinimum(), statsChecker.getMin(), 0);
                assertEquals(stat.getMean(), statsChecker.getMean(), 0.001);
                assertEquals(stat.getStdDev(), statsChecker.getStandardDeviation(), 0.001);
            }
        }

        @Test
        public void TestCurveFits() throws Exception
        {
            StatsService service = new StatsServiceImpl();

            // set up the validation data and objects to iterate over
            List<CurveValidation> validations = new ArrayList<>();

            CurveValidation v1 = new CurveValidation(new double[]{12.54, 12.04, 9.11, 7.48, .576, -.512, 1.99, -6.60});
            v1.setResults(CurveFitType.POLYNOMIAL, new CurveResults(2, .044, .052));
            v1.setResults(CurveFitType.FOUR_PARAMETER, new CurveResults(2.5, .031, .045));
            v1.setResults(CurveFitType.FIVE_PARAMETER, new CurveResults(2.2, .046, .054));
            validations.add(v1);

            CurveValidation v2 = new CurveValidation(new double[]{93.28, 88.65, 74.12, 46.16, 28.34, 17.41, 6.17, -1.79});
            v2.setResults(CurveFitType.POLYNOMIAL, new CurveResults(5.4, .414, .424));
            v2.setResults(CurveFitType.FOUR_PARAMETER, new CurveResults(3.4, .403, .403));
            v2.setResults(CurveFitType.FIVE_PARAMETER, new CurveResults(3.1, .420, .420));
            validations.add(v2);

            CurveValidation v3 = new CurveValidation(new double[]{10.79, 3.21, .599, 9.96, 9.5, 8.39, 1.56, -5.81});
            v3.setResults(CurveFitType.POLYNOMIAL, new CurveResults(4.1, .055, .056));
            v3.setResults(CurveFitType.FOUR_PARAMETER, new CurveResults(4.7, .048, .049));
            v3.setResults(CurveFitType.FIVE_PARAMETER, new CurveResults(4.6, .080, .082));
            validations.add(v3);

            CurveValidation v4 = new CurveValidation(new double[]{75.94, 58.52, 39.42, 28.84, 19.37, 9.91, 6.04, -7.35});
            v4.setResults(CurveFitType.POLYNOMIAL, new CurveResults(2.4, .259, .273));
            v4.setResults(CurveFitType.FOUR_PARAMETER, new CurveResults(4.5, .226, .247));
            v4.setResults(CurveFitType.FIVE_PARAMETER, new CurveResults(3.7, .245, .262));
            validations.add(v4);

            CurveValidation v5 = new CurveValidation(new double[]{89.34, 74.24, 45.69, 18.34, .365, -1.65, -.77, -16.59});
            v5.setResults(CurveFitType.POLYNOMIAL, new CurveResults(5.9, .207, .263));
            v5.setResults(CurveFitType.FOUR_PARAMETER, new CurveResults(5, .201, .263));
            v5.setResults(CurveFitType.FIVE_PARAMETER, new CurveResults(5.1, .221, .277));
            validations.add(v5);

            for (CurveValidation validation : validations)
            {
                for (CurveFitType fitType : CurveFitType.values())
                {
                    if (fitType != CurveFitType.NONE)
                    {
                        CurveFit fit = service.getCurveFit(fitType, validation.getData());
                        CurveResults results = validation.getResults(fitType);

                        // validate calculated and expected fit error and auc
                        assertEquals(fit.getFitError(), results.getFitError(), 0.05);
                        assertEquals(fit.calculateAUC(AUCType.NORMAL), results.getAuc(), 0.005);
                        assertEquals(fit.calculateAUC(AUCType.POSITIVE), results.getPositiveAuc(), 0.005);
                    }
                }
            }
        }

        private static class CurveValidation
        {
            private double[] _data;
            private Map<CurveFitType, CurveResults> _curveResultsMap = new HashMap<>();

            public CurveValidation(double[] data)
            {
                _data = data;
            }

            public CurveResults getResults(CurveFitType type)
            {
                return _curveResultsMap.get(type);
            }

            public void setResults(CurveFitType type, CurveResults results)
            {
                _curveResultsMap.put(type, results);
            }

            public DoublePoint[] getData()
            {
                DoublePoint[] points = new DoublePoint[_data.length];
                int i=0;
                double dilution = 20;
                for (double neut : _data)
                {
                    points[i++] = new DoublePoint(dilution, neut);
                    dilution *= 4;
                }
                return points;
            }
        }

        private static class CurveResults
        {
            private double _fitError;
            private double _auc;
            private double _positiveAuc;

            public CurveResults(double fitError, double auc, double positiveAuc)
            {
                _fitError = fitError;
                _auc = auc;
                _positiveAuc = positiveAuc;
            }

            public double getFitError()
            {
                return _fitError;
            }

            public double getAuc()
            {
                return _auc;
            }

            public double getPositiveAuc()
            {
                return _positiveAuc;
            }
        }
    }
}
