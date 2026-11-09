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
package org.labkey.api.data.statistics;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 1/16/14.
 */
public interface CurveFit<P extends CurveFit.Parameters>
{
    interface Parameters
    {
        /**
         * Returns a map representation of the parameters, used for
         * serialization of parameter information in the Client API
         */
        Map<String, Object> toMap();

        default JSONObject toJSON()
        {
            return new JSONObject(toMap());
        }
    }

    StatsService.CurveFitType getType();

    /**
     * Sets the data that this curve fit will be applied to.
     */
    void setData(DoublePoint[] data);

    /**
     * Returns the data associated with this curve fit.
     */
    DoublePoint[] getData();

    /**
     * Returns the parameters necessary to represent the fitted curve
     */
    P getParameters() throws FitFailedException;

    /**
     * Initialize parameters using an external source
     */
    void setParameters(P parameters);
    void setParameters(JSONObject json);

    /**
     * Sets whether the curve is assumed to be decreasing by default.  It's used as an optimization during 4&5
     * parameter curve fitting and used only if the data points are too chaotic to provide a reasonable guess.
     * @param decreasing the default value is true
     */
    void setAssumeCurveDecreasing(boolean decreasing);

    /**
     * Sets whether all calculations and points generated are performed using a log base 10 X axis scale. By default
     * this is set to true.
     */
    void setLogXScale(boolean logXScale);

    /**
     * Returns the corresponding y value for the specified value on the x axis using
     * the passed in curve fit parameters
     */
    double fitCurve(double x);
    double fitCurve(double x, P parameters);

    /**
     * For a curve fit, return the x value given y. Can be used in assays where dose
     * values need to be calculated from measured responses (known y but unknown x).
     */
    double solveForX(double y);

    /**
     * Calculates the fit error : r squared (or coefficient of determination) of the fitted curve
     */
    double getFitError() throws FitFailedException;

    /**
     * Returns the array of x/y pairs to render the fitted curve. The min and max x range
     * will be calculated as the min and max of the data currently applied to this curve fit.
     *
     * @param totalPoints The number of points in the array to return
     */
    DoublePoint[] renderCurve(int totalPoints) throws FitFailedException;

    /**
     * Returns the array of x/y pairs to render the fitted curve. The min and max x range
     * will be calculated as the min and max of the data currently applied to this curve fit.
     *
     * @param totalPoints The number of points in the array to return
     * @param startX The starting x value to begin generating points for
     * @param endX The ending x value to generate points for
     */
    DoublePoint[] renderCurve(int totalPoints, double startX, double endX) throws FitFailedException;

    /**
     * Calculates the area under the curve represented by this curve fit. The type of AUC calculation can be configured
     * to be either {@code NORMAL}, {@code POSITIVE}, or {@code NEGATIVE}.
     *
     * The min and max x range
     * will be calculated as the min and max of the data currently applied to this curve fit.
     *
     * @param type
     *          <p>{@code NORMAL} - the AUC is computed for both positive and negative segments of the curve.
     *          <br>{@code POSITIVE} - AUC is computed only for the segments of the curve where the y value is positive.
     *          <br>{@code NEGATIVE} - AUC is computed only for the segments of the curve where the y value is negative.
     *
     * @return The integrated area under the curve.
     */
    double calculateAUC(StatsService.AUCType type) throws FitFailedException;

    /**
     * Calculates the area under the curve represented by this curve fit. The type of AUC calculation can be configured
     * to be either {@code NORMAL}, {@code POSITIVE}, or {@code NEGATIVE}.
     *
     * @param type
     *          <p>{@code NORMAL} - the AUC is computed for both positive and negative segments of the curve.
     *          <br>{@code POSITIVE} - AUC is computed only for the segments of the curve where the y value is positive.
     *          <br>{@code NEGATIVE} - AUC is computed only for the segments of the curve where the y value is negative.
     *
     * @param startX The beggining x position on the curve to start the computation.
     * @param endX The ending x position on the curve to compute AUC.
     * @return The integrated area under the curve.
     */
    double calculateAUC(StatsService.AUCType type, double startX, double endX) throws FitFailedException;

    /**
     * Calculates the residual sum of squares (RSS) for the curve fit (https://en.wikipedia.org/wiki/Residual_sum_of_squares)
     * @param parameters the parameters to use for the give calculated curve fit
     * @return the calculated residual sum of squares
     */
    default double residualSumSquares(P parameters)
    {
        double sumSq = 0;
        for (DoublePoint point : getData())
        {
            double expectedValue = point.getY();
            double foundValue = fitCurve(point.getX(), parameters);

            sumSq += Math.pow(foundValue - expectedValue, 2);
        }
        return sumSq;
    }

    /**
     * Calculates the root mean square error (RMSE), or root mean square deviation (RMSD),
     * for the curve fit (https://en.wikipedia.org/wiki/Root_mean_square_deviation)
     * @param parameters the parameters to use for the give calculated curve fit
     * @return the calculated root mean square error
     */
    default double rootMeanSquareError(P parameters)
    {
        return Math.sqrt(residualSumSquares(parameters) / getData().length);
    }

    /**
     * Calculates the total sum of squares (TSS) for the data points (https://en.wikipedia.org/wiki/Total_sum_of_squares).
     * This value is used in the R^2 calculation.
     * @return the calculated total sum of squares
     */
    default double totalSumSquares()
    {
        double sumSq = 0;
        double mean = 0;
        for (DoublePoint point : getData())
            mean += point.getY();
        mean /= getData().length;

        for (DoublePoint point : getData())
        {
            double expectedValue = point.getY();
            sumSq += Math.pow(expectedValue - mean, 2);
        }
        return sumSq;
    }

    /**
     * Calculates the R^2 value for the curve fit (https://en.wikipedia.org/wiki/Coefficient_of_determination)
     * using the residualSumSquares() and totalSumSquares() methods.
     * @param parameters the parameters to use for the give calculated curve fit
     * @return the calculated R^2 value
     */
    default double rSquared(P parameters)
    {
        return 1 - residualSumSquares(parameters) / totalSumSquares();
    }

    // see description below, this version of the method is here so that each applicable curve fit can override it
    // to set the correct p value for the degrees of freedom
    default double adjustedRSquared(P parameters)
    {
        return Double.NaN;
    }

    /**
     * Calculates the adjusted R^2 value for the curve fit (https://en.wikipedia.org/wiki/Coefficient_of_determination)
     * @param parameters the parameters to use for the give calculated curve fit
     * @return the calculated adjusted R^2 value (if possible)
     */
    default double adjustedRSquared(P parameters, int p)
    {
        int n = getData().length;
        return 1 - (1 - rSquared(parameters)) * (n - 1) / (n - p - 1);
    }

    default List<Map<String, Object>> generateCurvePoints(Double xMin, Double xMax, Integer numberOfPoints, boolean logXScale)
    {
        List<Map<String, Object>> generatedPoints = new ArrayList<>();

        if (!logXScale)
        {
            double stepSize = (xMax - xMin) / (numberOfPoints - 1);
            for (int i = 0; i < numberOfPoints; i++)
            {
                double xVal = xMin + (i * stepSize);
                generatedPoints.add(Map.of("x", xVal, "y", fitCurve(xVal)));
            }
        }
        else
        {
            double logXValMin = xMin == 0 ? Math.log10(Double.MIN_VALUE) : Math.log10(xMin);
            double stepSize = (Math.log10(xMax) - logXValMin) / (numberOfPoints - 1);
            for (int i = 0; i < numberOfPoints; i++)
            {
                double logValue = logXValMin + (i * stepSize);
                double xVal = Math.pow(10, logValue);
                if (!Double.isNaN(xVal))
                {
                    double yVal = fitCurve(xVal);
                    if (!Double.isNaN(yVal) && !Double.isInfinite(yVal))
                        generatedPoints.add(Map.of("x", xVal, "y", yVal));
                }
            }
        }

        return generatedPoints;
    }
}
