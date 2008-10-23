/*
 * Copyright (c) 2006-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.common.tools;

import org.apache.log4j.Logger;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import org.labkey.common.tools.ApplicationContext;
import org.labkey.common.util.Pair;

/**
 * A class with generic methods for basic stats.  Add methods as needed.
 * A lot of things are written twice, once for floats and once for doubles.  That's annoying.
 * But it would be wasteful of space and time to do conversions
 */
public class BasicStatistics
{
    /**
     * Calculate the standard deviation of the inputs
     * @param inputs
     * @return
     */
    public static float standardDeviation(float[] inputs)
    {
        if (inputs.length < 2)
        return 0;
        float variance = variance(inputs);

        return (float) Math.sqrt(variance);
    }

    /**
     * Calculate the standard deviation of the inputs
     * @param inputs
     * @return
     */
    public static double standardDeviation(double[] inputs)
    {
        if (inputs.length < 2)
        return 0;
        double variance = variance(inputs);

        return Math.sqrt(variance);
    }

    public static double standardDeviation(List<Double> inputs)
    {
        double[] inputsAsArray = new double[inputs.size()];
        for (int i=0; i<inputs.size(); i++)
            inputsAsArray[i] = inputs.get(i);
        return standardDeviation(inputsAsArray);
    }
    

    /**
     * Calculate the variance of the inputs
     * @param inputs
     * @return
     */
    public static float variance(float[] inputs)
    {
        if (inputs.length < 2)
            return 0;

        float mean = mean(inputs);

        float varianceNumerator = 0;
        for (int i=0; i<inputs.length; i++)
        {
            float diffFromMean = inputs[i] - mean;
            varianceNumerator += (diffFromMean * diffFromMean);
        }
        return varianceNumerator / (inputs.length-1);
    }

    /**
     * Calculate the variance of the inputs
     * @param inputs
     * @return
     */
    public static double variance(double[] inputs)
    {
        if (inputs.length < 2)
            return 0;

        double mean = mean(inputs);

        double varianceNumerator = 0;
        for (int i=0; i<inputs.length; i++)
        {
            double diffFromMean = inputs[i] - mean;
            varianceNumerator += (diffFromMean * diffFromMean);
        }
        return varianceNumerator / (inputs.length-1);
    }

    /**
     * Calculate the mean of the inputs
     * @param inputs
     * @return
     */
    public static double mean(int[] inputs)
    {
        float sum = 0;
        for (int i=0; i<inputs.length; i++)
        {
            sum += inputs[i];
        }
        float mean = sum / inputs.length;
        return mean;
    }

    /**
     * Calculate the mean of the inputs
     * @param inputs
     * @return
     */
    public static float mean(float[] inputs)
    {
        float sum = 0;
        for (int i=0; i<inputs.length; i++)
        {
            sum += inputs[i];
        }
        float mean = sum / inputs.length;
        return mean;
    }


    /**
     * Calculate the mean of the inputs
     * @param inputs
     * @return
     */
    public static double mean(List<Double> inputs)
    {
        float sum = 0;
        for (int i=0; i<inputs.size(); i++)
        {
            sum += inputs.get(i);
        }
        float mean = sum / inputs.size();
        return mean;
    }


    /**
     * Calculate the mean of the inputs
     * @param inputs
     * @return
     */
    public static float mean(List<Float> inputs)
    {
        float sum = 0;
        for (int i=0; i<inputs.size(); i++)
        {
            sum += inputs.get(i);
        }
        float mean = sum / inputs.size();
        return mean;
    }

    /**
     * Calculate the mean of the inputs
     * @param inputs
     * @return
     */
    public static double mean(double[] inputs)
    {
        double sum = 0;
        for (int i=0; i<inputs.length; i++)
        {
            sum += inputs[i];
        }
        double mean = sum / inputs.length;
        return mean;
    }

    public static double median(List<Double> inputs)
    {
        double[] inputsArray = new double[inputs.size()];
        for (int i=0; i<inputs.size(); i++)
            inputsArray[i] = inputs.get(i);
        return median(inputsArray);
    }

    public static float median(List<Float> inputs)
    {
        double[] inputsArray = new double[inputs.size()];
        for (int i=0; i<inputs.size(); i++)
            inputsArray[i] = inputs.get(i);
        return (float) median(inputsArray);
    }

    /**
     * Calculate the median of the inputs.
     * This is median as defined in R -- average of the middle two points.  NOT necessarily
     * one of the inputs.
     * Clones before sorting, so as not to disrupt input
     * @param inputs
     * @return
     */
    public static double median(double[] inputs)
    {
        //clone before sorting
        double[] inputsClone = inputs.clone();
        Arrays.sort(inputsClone);

        if (inputs.length == 0)
            return 0;
        //if odd, return the middle element
        if (inputsClone.length % 2 == 1)
            return inputsClone[(inputsClone.length-1)/2];
        //if got here, then even.  Average the middle two elements
        return (inputsClone[(inputsClone.length)/2 - 1] +
                inputsClone[(inputsClone.length)/2])  / 2;
    }

    /**
     * Calculate the interquartile range.  I think this is right.
     *
     * Calculate the median, then calculate the median of everything under the
     * median and everything over the median.
     * @param inputs
     * @return
     */
    public static Pair<Double,Double> interQuartileRange(double[] inputs)
    {
        double median = median(inputs);
        List<Double> underMedian = new ArrayList<Double>();
        List<Double> overMedian = new ArrayList<Double>();

        for (Double input : inputs)
        {
            if (input <= median)
                underMedian.add(input);
            else
                overMedian.add(input);
        }

        double q1 = median(underMedian);
        double q3 = median(overMedian);

        return new Pair<Double,Double>(q1,q3);
    }

    public static Pair<Double,Double> interQuartileRange(List<Double> inputs)
    {
        double[] inputsArray = new double[inputs.size()];
        for (int i=0; i<inputs.size(); i++)
            inputsArray[i] = inputs.get(i);
        return interQuartileRange(inputsArray);
    }

    public static float min(List<Float> values)
    {
        float minValue = Float.MAX_VALUE;
        for (float value : values)
            if (value < minValue)
                minValue = value;
        return minValue;
    }

    public static float max(List<Float> values)
    {
        float maxValue = Float.MIN_VALUE;
        for (float value : values)
            if (value > maxValue)
                maxValue = value;
        return maxValue;        
    }

    /**
     * calculate percentile p of values in list
     * @param values
     * @param p
     * @return
     */
    public static float percentile(List<Float> values, double p)
    {
        double[] inputsArray = new double[values.size()];
        for (int i=0; i<values.size(); i++)
            inputsArray[i] = values.get(i);
        return (float) percentile(inputsArray, p);
    }

    /**
     * Returns an estimate of the <code>p</code>th percentile of the values
     * in the <code>values</code> array, starting with the element in (0-based)
     * position <code>begin</code> in the array and including <code>length</code>
     * values.
     * <p>
     * Calls to this method do not modify the array
     *
     * Copied from org.apache.commons.math.stat
     *
     * todo: figure out for sure whether having median() call this is OK
     *
     * @param values array of input values
     * @param p  the percentile to compute
     * @return  the percentile value
     * @throws IllegalArgumentException if the parameters are not valid or the
     * input array is null
     */
    public static double percentile(double[] values, double p)
    {
        int length = values.length;

        if ((p > 100) || (p <= 0))
        {
            throw new IllegalArgumentException("invalid quantile value: " + p);
        }
        double n = (double) length;
        if (n == 0)
        {
            return Double.NaN;
        }
        if (n == 1)
        {
            return values[0]; // always return single value for n = 1
        }
        double pos = p * (n + 1) / 100;
        double fpos = Math.floor(pos);
        int intPos = (int) fpos;
        double dif = pos - fpos;
        double[] sorted = new double[length];
        System.arraycopy(values, 0, sorted, 0, length);
        Arrays.sort(sorted);

        if (pos < 1)
        {
            return sorted[0];
        }
        if (pos >= n)
        {
            return sorted[length - 1];
        }
        double lower = sorted[intPos - 1];
        double upper = sorted[intPos];
        return lower + dif * (upper - lower);
    }


    /**
     * Calculate the median of the inputs.
     * This is median as defined in R -- average of the middle two points.  NOT necessarily
     * one of the inputs.
     * Clones before sorting, so as not to disrupt input
     * @param inputs
     * @return
     */
    public static float median(float[] inputs)
    {
        //clone before sorting
        float[] inputsClone = inputs.clone();
        Arrays.sort(inputsClone);

        if (inputsClone.length == 0)
            return 0;
        //if odd, return the middle element
        if (inputsClone.length % 2 == 1)
            return inputsClone[(inputsClone.length-1)/2];
        //if got here, then even.  Average the middle two elements
        return (inputsClone[(inputsClone.length)/2 - 1] +
                inputsClone[(inputsClone.length)/2])  / 2;
    }

    /**
     * Compute the leverage of each input:
     * (x-xbar)^2/((n-1)sigmax^2))
     * @param inputs
     * @return
     */
    public static double[] leverages(double[] inputs)
    {
        int n = inputs.length;

        double sigmaInputsSquared = variance(inputs);
        double meanInput = BasicStatistics.mean(inputs);

        double leverageDenomenator = (n-1) * sigmaInputsSquared;

        double[] leverages = new double[n];

        for (int i=0; i < n; i++)
        {
            leverages[i] = (1.0 / (double) n) +
                           (Math.pow(inputs[i] - meanInput,2) / leverageDenomenator);
        }

        return leverages;
    }

    /**
     * Compute the leverage of each input:
     * (x-xbar)^2/((n-1)sigmax^2))
     * @param inputs
     * @return
     */
    public static float[] leverages(float[] inputs)
    {
        int n = inputs.length;

        double sigmaInputsSquared = variance(inputs);
        double meanInput = BasicStatistics.mean(inputs);

        double leverageDenomenator = (n-1) * sigmaInputsSquared;

        float[] leverages = new float[n];

        for (int i=0; i < n; i++)
        {
            leverages[i] = (float) (Math.pow(inputs[i] - meanInput,2) /
                               leverageDenomenator);
        }

        return leverages;
    }

    /**
     * Convenience method
     * @param xValues
     * @param residuals
     * @return
     */
    public static double[] studentizedResiduals(double[] xValues,
                                                double[] residuals)
    {
        double[] leverages = leverages(xValues);
        return studentizedResiduals(xValues, residuals, leverages);                
    }

    /**
     * Calculate the studentized residuals of arrays of somethingorother
     * represented here by their X values, leverages (based on X values),
     * and residuals.
     * @param xValues
     * @param residuals
     * @param leverages
     * @return
     */
    public static double[] studentizedResiduals(double[] xValues,
                                                double[] residuals,
                                                double[] leverages)
    {
        int n = xValues.length;

        if (leverages == null)
            leverages = leverages(xValues);

        //sigmaError requires a denomenator of n-2, so can't use
        //BasicStatistics.standardDeviation()
        double sumSquaredResiduals=0;
        for (double residual : residuals)
            sumSquaredResiduals += Math.pow(residual, 2);

        double sigmaError = Math.sqrt(sumSquaredResiduals / (n - 2));
        //1 + 1/n.  Same for all inputs, compute once
        double onePlusOneOverN = 1 + 1/n;

        double[] studentizedResiduals = new double[n];

        //Find the "studentized residual" of every feature:
        for (int i=0; i < n; i++)
        {
            double scaledResidual = residuals[i] / sigmaError;
            studentizedResiduals[i] = scaledResidual /
                    Math.sqrt(onePlusOneOverN + leverages[i]);
        }

        return studentizedResiduals;
    }

    public static double sum(double[] values)
    {
        double result = 0;
        for (double value : values)
            result += value;
        return result;
    }

    public static double sum(List<Double> values)
    {
        double result = 0;
        for (double value : values)
            result += value;
        return result;
    }

    public static float sum(List<Float> values)
    {
        float result = 0;
        for (float value : values)
            result += value;
        return result;
    }

    public static float sum(float[] values)
    {
        float result = 0;
        for (float value : values)
            result += value;
        return result;
    }

    /**
     *
     * Covariance = (sum(x*y) - ((sumx)(sumy)/n)) / (n-1)
     * @param xvalues
     * @param yvalues
     * @return
     */
    public static double covariance(double[] xvalues, double[] yvalues)
    {
        int n = xvalues.length;

        double[] xtimesy = new double[n];
        for (int i=0; i<n; i++)
            xtimesy[i] = xvalues[i] * yvalues[i];

        double numerator = sum(xtimesy) - (((sum(xvalues) * sum(yvalues))) / n);
        return numerator / (double) (n - 1);
    }

    public static double correlationCoefficient(List<Float> xvaluesList, List<Float> yvaluesList)
    {
        double[] xvalues = new double[xvaluesList.size()];
        double[] yvalues = new double[xvaluesList.size()];

        for (int i=0; i<xvalues.length; i++)
        {
            xvalues[i] = xvaluesList.get(i);
            yvalues[i] = yvaluesList.get(i);
        }
        return correlationCoefficient(xvalues, yvalues);
    }

    /**
     * covariance(xy) / (sx * sy)    
     */
    public static double correlationCoefficient(double[] xvalues, double[] yvalues)
    {
        return covariance(xvalues, yvalues) /
                (standardDeviation(xvalues) * standardDeviation(yvalues));
    }

    /**
     * Geometric mean is defined as ((x1)(x2)(x3)...(xk)) ^ 1/k
     * @param values
     * @return
     */
    public static double geometricMean(double[] values)
    {
        double result = 1.0;
        for(double value : values)
            result *= value;
        return Math.pow(result, 1.0/(double)values.length);
    }

    public static double geometricMean(List<Double> inputs)
    {
        double result = 1.0;
        for(double value : inputs)
            result *= value;
        return Math.pow(result, 1.0/(double)inputs.size());
    }

    public static float geometricMean(float[] values)
    {
        float result = 1.0f;
        for(float value : values)
            result *= value;
        return (float) Math.pow(result, 1.0f/(float)values.length);
    }

    public static double geometricMean(double value1, double value2)
    {
        double[] values = new double[2];
        values[0] = value1;
        values[1] = value2;

        return geometricMean(values);
    }

    /**
     * Calculate a standard Gaussian with mean 0, variance 1
     * @param x
     * @return
     */
    public static double standardGaussian(double x)
    {
        //sqrt(2pi) = 2.50662827463
        return (1.0 / (2.50662827463)) * Math.exp(-Math.pow(x,2) / 2);
    }

    /**
     * Calculate the density of the normal distribution function with parameters mu and sigma,
     * at point x
     * @param mu
     * @param sigma
     * @param x
     * @return
     */
    public static double calcNormalDistDensity(double mu, double sigma, double x)
    {
        //sqrt(2pi) = 2.50662827463
        return Math.pow(Math.E, -.5 * Math.pow((x - mu) / sigma, 2)) / (sigma * 2.50662827463);
    }

    /**
     * Error function for standard normal distribution
     * @param z
     * @return
     */
    public static double erf(double z)
    {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(z));

        // use Horner's method
        double ans = 1 - t * Math.exp( -z*z   -   1.26551223 +
                                            t * ( 1.00002368 +
                                            t * ( 0.37409196 +
                                            t * ( 0.09678418 +
                                            t * (-0.18628806 +
                                            t * ( 0.27886807 +
                                            t * (-1.13520398 +
                                            t * ( 1.48851587 +
                                            t * (-0.82215223 +
                                            t * ( 0.17087277))))))))));
        if (z >= 0)
            return  ans;
        else
            return -ans;
    }

    /**
     * Calculate the <i>cumulative</i> density of the standard normal distribution at point x
     * @param z
     * @return
     */
    public static double calcStandardNormalCumDensity(double z)
    {
        return 0.5 * (1.0 + erf(z / (Math.sqrt(2.0))));
    }


    /**
     * Calculate the <i>cumulative</i> density of the normal distribution function with parameters mu and sigma,
     * at point x
     * @param mu
     * @param sigma
     * @param x
     * @return
     */
    public static double calcNormalCumDensity(double mu, double sigma, double x)
    {
        return calcStandardNormalCumDensity((x - mu) / sigma);
    }
}
