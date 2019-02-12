package org.labkey.api.visualization;

import java.util.Arrays;

public class Stats
{

    public static double MOVING_RANG_EPSILON = 0.0000001;
    public static int  CUSUM_CONTROL_LIMIT = 5;
    public static double MOVING_RANGE_UPPER_LIMIT_WEIGHT = 3.268;

    public static Stats _instance = new Stats();

    public static Stats get()
    {
        return _instance;
    }

    /**
     * CUSUM_WEIGHT_FACTOR of 0.5 and CUSUM_CONTROL_LIMIT of 5 to achieve a 3*stdDev boundary
     */
    public static double CUSUM_WEIGHT_FACTOR = 0.5;
    public static double CUSUM_EPSILON = 0.0000001;

    /**
     * Returns the average value.
     * @param values An array of double.
     * @returns {double}
     */
    public double getMean(Double[] values)
    {
        if (values == null || values.length ==0)
            return 0;

        double sum = Arrays.stream(values).mapToDouble(i->i).sum();
        return sum/values.length;
    }

    /**
     * Returns the standard deviation.
     * @param values An array of double.
     * @returns {double}
     */
    public double getStdDev(Double[] values)
    {
        if (values == null || values.length ==0)
            return 0;

        double mean = getMean(values);
        Double[] squareDiffs = new Double[values.length];
        for(int i =0; i< values.length; i++)
        {
            Double diff = values[i] - mean;
            squareDiffs[i] = diff*diff;
        }
        return Math.sqrt(getMean(squareDiffs));
    }

    /**
     * Calculates a variety of cumulative sums for a data array.
     * @param values Array of data values to calculate from
     * @param negative True to calculate CUSUM-, false to calculate CUSUM+. (default to false)
     * @param transform True to calculate CUSUMv (Variability CUSUM), false to calculate CUSUMm (Mean CUSUM). (default to false)
     * @param forcePositiveResult True to force all result values to be no less than a specified positive value, usually used for log scale. (default to false)
     * @param epsilon The smallest value that all returned value can be, only used if forcePositiveResult is true. (default to LABKEY.vis.Stat.CUSUM_EPSILON)
     * @returns {number[]}
     */
    public double[] getCUSUMS(Double[] values, boolean negative, boolean transform, boolean forcePositiveResult, Double epsilon)
    {
        if (values == null || values.length < 2)
            return new double[0];

        double mean = getMean(values);
        double stdDEV = getStdDev(values);
        if(stdDEV == 0) // in the case when all values are equal, calculation has to abort, special case CUSUM to all be 0
        {
            return new double[values.length];
        }

        double[] cusums = new double[values.length];
        cusums[0] = 0;
        for(int i=1; i< values.length; i++)
        {
            double standardized = (values[i]-mean) / stdDEV; //standard value (z-score)
            if (transform)
                standardized = (Math.sqrt(Math.abs(standardized)) - 0.822) / 0.349; //the transformed standardize normal quantity value so that it is sensitive to variability changes
            if (negative)
                standardized = standardized * -1;
            double cusum = Math.max(0, standardized - CUSUM_WEIGHT_FACTOR + cusums[i-1]);
            cusums[i] = cusum;
        }
        if (forcePositiveResult)
        {
            double lowerBound = epsilon != null ? epsilon : CUSUM_EPSILON;
            for (int j = 0; j < cusums.length; j++)
            {
                cusums[j] = Math.max(cusums[j], lowerBound);
            }
        }
        return cusums;
    }

    /**
     * Calculate the moving range values for a data array, which are sequential differences between two successive values.
     * @param values Array of data values to calculate from
     * @param forcePositiveResult True to force all result values to be no less than a specified positive value, usually used for log scale. (default to false)
     * @param epsilon The smallest value that all returned value can be, only used if forcePositiveResult is true. (default to LABKEY.vis.Stat.MOVING_RANGE_EPSILON)
     * @returns {double[]}
     */
    public Double[] getMovingRanges(Double[] values, boolean forcePositiveResult, Double epsilon)
    {
        if(values == null || values.length < 1)
            return new Double[0];

        Double[] mR = new Double[values.length];
        mR[0] = Double.valueOf(0); // mR[0] is always 0
        for(int i=1; i<values.length; i++)
        {
            mR[i] = Math.abs(values[i] - values[i-1]);
        }

        if(forcePositiveResult) {
            double lowerBound = epsilon != null ? epsilon : MOVING_RANG_EPSILON;
            for(int j = 0; j < mR.length; j++)
            {
                mR[j] = Math.max(lowerBound, mR[j]);
            }
        }
        return mR;
    }
}
