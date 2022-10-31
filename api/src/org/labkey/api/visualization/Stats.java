/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.visualization;

import java.util.Arrays;
import java.util.Objects;

public class Stats
{

    public static final double MOVING_RANG_EPSILON = 0.0000001;
    public static final int CUSUM_CONTROL_LIMIT = 5;
    public static final double MOVING_RANGE_UPPER_LIMIT_WEIGHT = 3.268;


    /**
     * CUSUM_WEIGHT_FACTOR of 0.5 and CUSUM_CONTROL_LIMIT of 5 to achieve a 3*stdDev boundary
     */
    public static final double CUSUM_WEIGHT_FACTOR = 0.5;
    public static final double CUSUM_EPSILON = 0.0000001;

    /**
     * Returns the average value.
     *
     * @param values An array of double.
     * @returns {double}
     */
    public static double getMean(Double[] values)
    {
        if (values == null || values.length == 0)
            return 0;

        double sum = Arrays.stream(values).filter(Objects::nonNull).mapToDouble(i -> i).sum();
        return sum / values.length;
    }

    /**
     * Returns the standard deviation.
     *
     * @param values An array of values. Nulls are ignored.
     */
    public static double getStdDev(Double[] values, boolean isBiasCorrected)
    {
        if (values == null || values.length == 0)
            return 0;

        double mean = getMean(values);
        double sumSquareDiffs = 0.0;
        int count = 0;

        for (Double value : values)
        {
            if (value != null)
            {
                double diff = value - mean;
                sumSquareDiffs += diff * diff;
                count++;
            }
        }

        return Math.sqrt(sumSquareDiffs / (double)(count - (isBiasCorrected ? 1 : 0)));
    }

    /**
     * Calculates a variety of cumulative sums for a data array.
     *
     * @param values              Array of data values to calculate from
     * @param negative            True to calculate CUSUM-, false to calculate CUSUM+. (default to false)
     * @param transform           True to calculate CUSUMv (Variability CUSUM), false to calculate CUSUMm (Mean CUSUM). (default to false)
     * @param forcePositiveResult True to force all result values to be no less than a specified positive value, usually used for log scale. (default to false)
     * @param epsilon             The smallest value that all returned value can be, only used if forcePositiveResult is true. (default to LABKEY.vis.Stat.CUSUM_EPSILON)
     * @returns {number[]}
     */
    public static double[] getCUSUMS(Double[] values, boolean negative, boolean transform, boolean forcePositiveResult, Double epsilon)
    {
        if (values == null || values.length < 2)
            return new double[0];

        double mean = getMean(values);
        double stdDEV = getStdDev(values, false);
        if (stdDEV == 0) // in the case when all values are equal, calculation has to abort, special case CUSUM to all be 0
        {
            return new double[values.length];
        }

        double[] cusums = new double[values.length+1];
        cusums[0] = 0;
        for (int i = 1; i <= values.length; i++)
        {
            if(values[i-1] != null)
            {
                double standardized = (values[i-1] - mean) / stdDEV; //standard value (z-score)
                if (transform)
                    standardized = (Math.sqrt(Math.abs(standardized)) - 0.822) / 0.349; //the transformed standardize normal quantity value so that it is sensitive to variability changes
                if (negative)
                    standardized = standardized * -1;
                double cusum = Math.max(0, standardized - CUSUM_WEIGHT_FACTOR + cusums[i - 1]);
                cusums[i] = cusum;
            }
        }
        int n = cusums.length-1;
        double[] shiftCusums = new double[n];
        System.arraycopy(cusums, 1, shiftCusums, 0, n);
        if (forcePositiveResult)
        {
            double lowerBound = epsilon != null ? epsilon : CUSUM_EPSILON;
            for (int j = 0; j < cusums.length; j++)
            {
                cusums[j] = Math.max(cusums[j], lowerBound);
            }
        }
        return shiftCusums;
    }

    /**
     * Calculate the moving range values for a data array, which are sequential differences between two successive values.
     *
     * @param values              Array of data values to calculate from
     * @param forcePositiveResult True to force all result values to be no less than a specified positive value, usually used for log scale. (default to false)
     * @param epsilon             The smallest value that all returned value can be, only used if forcePositiveResult is true. (default to LABKEY.vis.Stat.MOVING_RANGE_EPSILON)
     * @returns {double[]}
     */
    public static Double[] getMovingRanges(Double[] values, boolean forcePositiveResult, Double epsilon)
    {
        if (values == null || values.length <= 1)
            return new Double[0];

        Double[] mR = new Double[values.length];
        mR[0] = Double.valueOf(0); // mR[0] is always 0
        for (int i = 1; i < values.length; i++)
        {
            if(values[i] != null && values[i-1] != null)
                mR[i] = Math.abs(values[i] - values[i - 1]);
        }

        if (forcePositiveResult)
        {
            double lowerBound = epsilon != null ? epsilon : MOVING_RANG_EPSILON;
            for (int j = 0; j < mR.length; j++)
            {
                mR[j] = Math.max(lowerBound, mR[j]);
            }
        }
        return mR;
    }

    public static Double[] getTrailingMeans(Double[] values, int N)
    {
        if (values == null || values.length <= 1)
            return new Double[0];

        int numOfTrailingValues = values.length - N + 1;
        Double[] trailingMeans = new Double[numOfTrailingValues];
        int start = 0;
        int end = N;
        for (int i = 0; i < numOfTrailingValues; i++)
        {
            trailingMeans[i] = getMean(getValuesFromRange(values, start, end));
            start++;
            end++;
        }

        return trailingMeans;
    }

    public static Double[] getTrailingCVs(Double[] values, int N)
    {
        if (values == null || values.length <= 1)
            return new Double[0];

        int numOfTrailingValues = values.length - N + 1;
        Double[] trailingCVs = new Double[numOfTrailingValues];
        int start = 0;
        int end = N;
        for (int i = 0; i < numOfTrailingValues; i++)
        {
            Double[] vals = getValuesFromRange(values, start, end);
            double sd = getStdDev(vals, false);
            double mean = getMean(vals);
            trailingCVs[i] = sd/mean;
            start++;
            end++;
        }

        return trailingCVs;
    }

    private static Double[] getValuesFromRange(Double[] values, int start, int end)
    {
        Double[] valuesFromRange = new Double[end-start];
        if (end - start >= 0)
        {
            System.arraycopy(values, start, valuesFromRange, start, end - start);
        }
        return valuesFromRange;
    }
}
