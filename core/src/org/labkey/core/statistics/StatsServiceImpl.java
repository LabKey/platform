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

import org.apache.commons.math3.random.RandomDataImpl;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.MathStat;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.view.Stats;

import java.util.ArrayList;
import java.util.Collection;
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
    public MathStat getStats(Collection<Double> data)
    {
        double[] da = new double[data.size()];
        int i = 0;

        for (Double d : data)
            da[i++] = d.doubleValue();

        return new Stats.DoubleStats(da);
    }

    @Override
    public CurveFit getCurveFit(CurveFitType type, DoublePoint[] data)
    {
        return getCurveFit(type, data, null, null);
    }

    @Override
    public CurveFit getCurveFit(CurveFitType type, DoublePoint[] data, @Nullable Double asymptoteMin, @Nullable Double asymptoteMax)
    {
        return switch (type)
        {
            case THREE_PARAMETER_ALT -> new ThreeParameterCurveFit(data, asymptoteMax);
            case FOUR_PARAMETER_SIMPLEX -> new FourParameterSimplex(data);
            case FIVE_PARAMETER_ALT -> new FiveParameterCurveFit(data, asymptoteMin, asymptoteMax);
            case THREE_PARAMETER -> new ParameterCurveFit(data, type, 0.0, asymptoteMax);
            case FOUR_PARAMETER, FIVE_PARAMETER -> new ParameterCurveFit(data, type, asymptoteMin, asymptoteMax);
            case POLYNOMIAL -> new PolynomialCurveFit(data);
            case LINEAR -> new LinearCurveFit(data);
            case NONE -> new NoCurveFit(data);
        };
    }

    public static class TestCase extends Assert
    {
        private static final int TEST_DATA_SIZE = 100;

        @Test
        public void TestStats()
        {
            double[] testData = new double[TEST_DATA_SIZE];
            RandomDataImpl generator = new RandomDataImpl();
            StatsService service = StatsService.get();
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
            StatsService service = StatsService.get();

            // set up the validation data and objects to iterate over
            List<CurveValidation> validations = new ArrayList<>();

            CurveValidation v1 = new CurveValidation(new double[]{12.54, 12.04, 9.11, 7.48, .576, -.512, 1.99, -6.60});
            v1.setResults(CurveFitType.POLYNOMIAL, new CurveResults(2, .044, .052));
            v1.setResults(CurveFitType.FOUR_PARAMETER_SIMPLEX, new CurveResults(.898, .044, .052));
            v1.setResults(CurveFitType.THREE_PARAMETER, new CurveResults(3.09, .065, .065));
            v1.setResults(CurveFitType.THREE_PARAMETER_ALT, new CurveResults(3.09, .065, .065));
            v1.setResults(CurveFitType.FOUR_PARAMETER, new CurveResults(2.5, .031, .045));
            v1.setResults(CurveFitType.FIVE_PARAMETER_ALT, new CurveResults(2.08, .046, .054));
            v1.setResults(CurveFitType.FIVE_PARAMETER, new CurveResults(2.2, .046, .054));
            v1.setResults(CurveFitType.LINEAR, new CurveResults(6.8, .070, .070));
            validations.add(v1);

            CurveValidation v2 = new CurveValidation(new double[]{93.28, 88.65, 74.12, 46.16, 28.34, 17.41, 6.17, -1.79});
            v2.setResults(CurveFitType.POLYNOMIAL, new CurveResults(5.4, .414, .424));
            v2.setResults(CurveFitType.FOUR_PARAMETER_SIMPLEX, new CurveResults(.994, .419, .419));
            v2.setResults(CurveFitType.THREE_PARAMETER, new CurveResults(2.93, .414, .414));
            v2.setResults(CurveFitType.THREE_PARAMETER_ALT, new CurveResults(2.93, .414, .414));
            v2.setResults(CurveFitType.FOUR_PARAMETER, new CurveResults(3.4, .403, .403));
            v2.setResults(CurveFitType.FIVE_PARAMETER_ALT, new CurveResults(2.44, .420, .420));
            v2.setResults(CurveFitType.FIVE_PARAMETER, new CurveResults(3.1, .420, .420));
            v2.setResults(CurveFitType.LINEAR, new CurveResults(36.8, .553, .553));
            validations.add(v2);

            CurveValidation v3 = new CurveValidation(new double[]{10.79, 3.21, .599, 9.96, 9.5, 8.39, 1.56, -5.81});
            v3.setResults(CurveFitType.POLYNOMIAL, new CurveResults(4.1, .055, .056));
            v3.setResults(CurveFitType.FOUR_PARAMETER_SIMPLEX, new CurveResults(.640, .052, .061));
            v3.setResults(CurveFitType.THREE_PARAMETER, new CurveResults(5.0, .078, .078));
            v3.setResults(CurveFitType.THREE_PARAMETER_ALT, new CurveResults(5.0, .078, .078));
            v3.setResults(CurveFitType.FOUR_PARAMETER, new CurveResults(4.7, .048, .049));
            v3.setResults(CurveFitType.FIVE_PARAMETER_ALT, new CurveResults(4.6, .049, .051));
            v3.setResults(CurveFitType.FIVE_PARAMETER, new CurveResults(4.6, .080, .082));
            v3.setResults(CurveFitType.LINEAR, new CurveResults(5.9, .070, .070));
            validations.add(v3);

            CurveValidation v4 = new CurveValidation(new double[]{75.94, 58.52, 39.42, 28.84, 19.37, 9.91, 6.04, -7.35});
            v4.setResults(CurveFitType.POLYNOMIAL, new CurveResults(2.4, .259, .273));
            v4.setResults(CurveFitType.FOUR_PARAMETER_SIMPLEX, new CurveResults(.994, .258, .271));
            v4.setResults(CurveFitType.THREE_PARAMETER, new CurveResults(4.05, .265, .265));
            v4.setResults(CurveFitType.THREE_PARAMETER_ALT, new CurveResults(4.05, .265, .265));
            v4.setResults(CurveFitType.FOUR_PARAMETER, new CurveResults(4.5, .226, .247));
            v4.setResults(CurveFitType.FIVE_PARAMETER_ALT, new CurveResults(3.29, .245, .251));
            v4.setResults(CurveFitType.FIVE_PARAMETER, new CurveResults(3.7, .245, .262));
            v4.setResults(CurveFitType.LINEAR, new CurveResults(27.5, .374, .374));
            validations.add(v4);

            CurveValidation v5 = new CurveValidation(new double[]{89.34, 74.24, 45.69, 18.34, .365, -1.65, -.77, -16.59});
            v5.setResults(CurveFitType.POLYNOMIAL, new CurveResults(5.9, .207, .263));
            v5.setResults(CurveFitType.FOUR_PARAMETER_SIMPLEX, new CurveResults(.988, .211, .263));
            v5.setResults(CurveFitType.THREE_PARAMETER, new CurveResults(7.86, .281, .281));
            v5.setResults(CurveFitType.THREE_PARAMETER_ALT, new CurveResults(7.86, .281, .281));
            v5.setResults(CurveFitType.FOUR_PARAMETER, new CurveResults(5, .201, .263));
            v5.setResults(CurveFitType.FIVE_PARAMETER_ALT, new CurveResults(4.105, .221, .277));
            v5.setResults(CurveFitType.FIVE_PARAMETER, new CurveResults(5.1, .221, .277));
            v5.setResults(CurveFitType.LINEAR, new CurveResults(38.0, .363, .363));
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
                        assertEquals(fitType.getLabel(), results.getFitError(), fit.getFitError(), 0.05);
                        assertEquals(fitType.getLabel(), results.getAuc(), fit.calculateAUC(AUCType.NORMAL), 0.005);
                        assertEquals(fitType.getLabel(), results.getPositiveAuc(), fit.calculateAUC(AUCType.POSITIVE), 0.005);
                    }
                }
            }
        }

        private static class CurveValidation
        {
            private final double[] _data;
            private final Map<CurveFitType, CurveResults> _curveResultsMap = new HashMap<>();

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
            private final double _fitError;
            private final double _auc;
            private final double _positiveAuc;

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

        @Test
        public void TestCurveFitParameters() throws Exception
        {
            StatsService service = StatsService.get();
            double delta = 0.005;

            DoublePoint[] data1 = new DoublePoint[]{
                    new DoublePoint(0.025, 0.67),
                    new DoublePoint(0.05, 1),
                    new DoublePoint(0.1, 2),
                    new DoublePoint(0.2, 3.17),
            };

            CurveFit fit = service.getCurveFit(CurveFitType.LINEAR, data1);
            fit.setLogXScale(false);
            assertEquals(14.442, (Double) fit.getParameters().toMap().get("slope"), delta);
            assertEquals(0.356, (Double) fit.getParameters().toMap().get("intercept"), delta);
            assertEquals(0.986, fit.rSquared(fit.getParameters()), delta);

            fit = service.getCurveFit(CurveFitType.POLYNOMIAL, data1);
            fit.setLogXScale(false);
            assertEquals(0.088, ((PolynomialCurveFit.PolynomialParameters)fit.getParameters()).getCoefficients()[0], delta);
            assertEquals(21.8001, ((PolynomialCurveFit.PolynomialParameters)fit.getParameters()).getCoefficients()[1], delta);
            assertEquals(-31.785, ((PolynomialCurveFit.PolynomialParameters)fit.getParameters()).getCoefficients()[2], delta);
            assertEquals(0.996, fit.rSquared(fit.getParameters()), delta);

            fit = service.getCurveFit(CurveFitType.THREE_PARAMETER, data1);
            fit.setLogXScale(false);
            verifySigmoidalParameters(fit, 0.0, 3.17, 2.246, 0.096, 1.0);
            assertEquals(-1.667, fit.rSquared(fit.getParameters()), delta);
            fit = service.getCurveFit(CurveFitType.THREE_PARAMETER, data1, 0.0, 4.0);
            fit.setLogXScale(true);
            verifySigmoidalParameters(fit, 0.0, 4.0, 1.540, 0.095, 1.0);
            assertEquals(0.980, fit.rSquared(fit.getParameters()), delta);

            fit = service.getCurveFit(CurveFitType.THREE_PARAMETER_ALT, data1);
            fit.setLogXScale(false);
            verifySigmoidalParameters(fit, 0.0, 3.17, -1.963, 0.096, 1.0);
            assertEquals(0.786, fit.rSquared(fit.getParameters()), delta);
            fit = service.getCurveFit(CurveFitType.THREE_PARAMETER_ALT, data1, 0.0, 4.0);
            fit.setLogXScale(true);
            verifySigmoidalParameters(fit, 0.0, 4.0, -1.540, 0.095, 1.0);
            assertEquals(0.980, fit.rSquared(fit.getParameters()), delta);

            fit = service.getCurveFit(CurveFitType.FOUR_PARAMETER, data1);
            fit.setLogXScale(false);
            verifySigmoidalParameters(fit, 0.67, 3.17, null, 0.096, 1.0);
            assertEquals(-1.138, fit.rSquared(fit.getParameters()), delta);
            fit = service.getCurveFit(CurveFitType.FOUR_PARAMETER, data1, null, 4.0);
            fit.setLogXScale(true);
            verifySigmoidalParameters(fit, 0.67, 4.0, 2.246, 0.095, 1.0);
            assertEquals(0.892, fit.rSquared(fit.getParameters()), delta);

            fit = service.getCurveFit(CurveFitType.FOUR_PARAMETER_SIMPLEX, data1);
            fit.setLogXScale(false);
            verifySigmoidalParameters(fit, 0.581, 3.787, 2.402, 0.110, 0.0);
            assertEquals(1.0, fit.rSquared(fit.getParameters()), delta);
            fit = service.getCurveFit(CurveFitType.FOUR_PARAMETER_SIMPLEX, data1);
            fit.setLogXScale(true);
            verifySigmoidalParameters(fit, 0.581, 3.787, 2.402, 0.110, 0.0);
            assertEquals(1.0, fit.rSquared(fit.getParameters()), delta);

            fit = service.getCurveFit(CurveFitType.FIVE_PARAMETER, data1);
            fit.setLogXScale(false);
            verifySigmoidalParameters(fit, 0.67, 3.17, 1.111, 0.096, 2.932);
            assertEquals(-1.624, fit.rSquared(fit.getParameters()), delta);
            fit = service.getCurveFit(CurveFitType.FIVE_PARAMETER, data1, null, 4.0);
            fit.setLogXScale(true);
            verifySigmoidalParameters(fit, 0.67, 4.0, 2.246, 0.095, 1.466);
            assertEquals(0.997, fit.rSquared(fit.getParameters()), delta);

            fit = service.getCurveFit(CurveFitType.FIVE_PARAMETER_ALT, data1);
            fit.setLogXScale(false);
            verifySigmoidalParameters(fit, 0.67, 3.17, 9.5144, 0.096, 0.2094);
            assertEquals(0.9978, fit.rSquared(fit.getParameters()), delta);
            fit = service.getCurveFit(CurveFitType.FIVE_PARAMETER_ALT, data1, null, 4.0);
            fit.setLogXScale(true);
            verifySigmoidalParameters(fit, 0.67, 4.0, 1.7321, 0.0946, 3.0369);
            assertEquals(0.9207, fit.rSquared(fit.getParameters()), delta);
        }

        @Test
        public void TestCurveFitParametersNAbCase() throws Exception
        {
            StatsService service = StatsService.get();
            double delta = 0.005;

            DoublePoint[] data1 = new DoublePoint[]{
                    new DoublePoint(0.01, 93),
                    new DoublePoint(0.1, 89),
                    new DoublePoint(1, 74),
                    new DoublePoint(10, 46),
                    new DoublePoint(100, 28),
                    new DoublePoint(1000, 17),
                    new DoublePoint(10000, 6),
                    new DoublePoint(100000, -2),
            };

            CurveFit fit = service.getCurveFit(CurveFitType.FIVE_PARAMETER, data1);
            fit.setLogXScale(true);
            verifySigmoidalParameters(fit, -2.0, 93.0, -0.445, 10.661, 0.838);
            assertEquals(3.493, fit.getFitError(), delta);
            assertEquals(0.990, fit.rSquared(fit.getParameters()), delta);

            fit = service.getCurveFit(CurveFitType.FIVE_PARAMETER_ALT, data1);
            fit.setLogXScale(true);
            verifySigmoidalParameters(fit, -2.0, 93.0, -1.1106, 10.6605, 0.2094);
            assertEquals(2.3816, fit.getFitError(), delta);
            assertEquals(0.9954, fit.rSquared(fit.getParameters()), delta);

            fit = service.getCurveFit(CurveFitType.FOUR_PARAMETER, data1);
            fit.setLogXScale(true);
            verifySigmoidalParameters(fit, -2.0, 103.0, -0.325, 6.907, 1.0);
            assertEquals(4.126, fit.getFitError(), delta);
            assertEquals(0.986, fit.rSquared(fit.getParameters()), delta);

            fit = service.getCurveFit(CurveFitType.FOUR_PARAMETER_SIMPLEX, data1);
            fit.setLogXScale(true);
            verifySigmoidalParameters(fit, -2.540, 102.643, -0.374, 9.985, 0.0);
            assertEquals(0.994, fit.getFitError(), delta);
            assertEquals(0.994, fit.rSquared(fit.getParameters()), delta);

            fit = service.getCurveFit(CurveFitType.THREE_PARAMETER, data1);
            fit.setLogXScale(true);
            verifySigmoidalParameters(fit, 0.0, 103.0, -0.384, 6.907, 1.0);
            assertEquals(2.960, fit.getFitError(), delta);
            assertEquals(0.989, fit.rSquared(fit.getParameters()), delta);

            fit = service.getCurveFit(CurveFitType.THREE_PARAMETER_ALT, data1);
            fit.setLogXScale(true);
            verifySigmoidalParameters(fit, 0.0, 103.0, 0.384, 6.907, 1.0);
            assertEquals(2.960, fit.getFitError(), delta);
            assertEquals(0.989, fit.rSquared(fit.getParameters()), delta);
        }

        private void verifySigmoidalParameters(CurveFit fit, Double min, Double max, Double slope, Double inflection, Double asymmetry) throws FitFailedException
        {
            double delta = 0.005;
            Map<String, Object> params = fit.getParameters().toMap();
            assertEquals(min, (Double) params.get("min"), delta);
            assertEquals(max, (Double) params.get("max"), delta);
            if (slope != null) assertEquals(slope, (Double) params.get("slope"), delta);
            assertEquals(inflection, (Double) params.get("inflection"), delta);
            assertEquals(asymmetry, (Double) params.get("asymmetry"), delta);
        }
    }
}
