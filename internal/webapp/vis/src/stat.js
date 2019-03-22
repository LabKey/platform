/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/********** Stats **********/

if(!LABKEY.vis.Stat){
    /**
     * @namespace The namespace used for statistics related functions.
     */
	LABKEY.vis.Stat = {};
}


/**
 * Calculates a statistical summary of an array of data. The summary includes Quartiles 1, 2, 3, minimum, maximum and
 * the inner quartile range. It is used internally to create box plots.
 * @param {Array} data An array of data. Can be an array of any type of object.
 * @param {Function} accessor A function that is used to access the value of each item in the array.
 * @returns {Object} summary
 * @example
    var data = [],
        accessor,
        summary;

    // Let's generate some data.
    for (var i = 0; i < 500; i++){
        data.push(parseInt(Math.random() * 50));
    }

    // Let's define how we access the data.
    accessor = function(row){
        return row;
    }

    // Now we'll get a summary.
    summary = LABKEY.vis.Stat.summary(data, accessor);

    console.log(summary);
 *
 */
LABKEY.vis.Stat.summary = function(data, accessor){
    /*
        Returns an object with the min, max, Q1, Q2 (median), Q3, interquartile range, and the sorted array of values.
     */
    var summary = {};

    summary.sortedValues = LABKEY.vis.Stat.sortNumericAscending(data, accessor);
    summary.min = summary.sortedValues[0];
    summary.max = summary.sortedValues[summary.sortedValues.length -1];
    summary.Q1 = LABKEY.vis.Stat.Q1(summary.sortedValues);
    summary.Q2 = LABKEY.vis.Stat.Q2(summary.sortedValues);
    summary.Q3 = LABKEY.vis.Stat.Q3(summary.sortedValues);
    summary.IQR = summary.Q3 - summary.Q1;

    return summary;
};

/**
 * Returns the 1st quartile for a sorted (asc) array.
 * @param numbers An array of numbers.
 * @returns {Number}
 */
LABKEY.vis.Stat.Q1 = function(numbers){
    return d3.quantile(numbers,0.25);
};

/**
 * Returns the 2nd quartile (median) for a sorted (asc) array.
 * @param numbers An array of numbers.
 * @returns {Number}
 */
LABKEY.vis.Stat.Q2 = function(numbers){
    return d3.quantile(numbers,0.5);
};

/**
 * An alias for {@link LABKEY.vis.Stat.Q2}
 */
LABKEY.vis.Stat.MEDIAN = LABKEY.vis.Stat.Q2;

/**
 * Returns the 3rd quartile for a sorted (asc) array.
 * @param numbers An array of numbers.
 * @returns {Number}
 */
LABKEY.vis.Stat.Q3 = function(numbers){
    return d3.quantile(numbers,0.75);
};

/**
 * Returns the sum of the array.
 * @param numbers An array of numbers.
 * @returns {Number}
 */
LABKEY.vis.Stat.SUM = function(numbers){
    return d3.sum(numbers);
};

/**
 * Returns the minimum of the array.
 * @param numbers An array of numbers.
 * @returns {Number}
 */
LABKEY.vis.Stat.MIN = function(numbers){
    return d3.min(numbers);
};

/**
 * Returns the maximum of the array.
 * @param numbers An array of numbers.
 * @returns {Number}
 */
LABKEY.vis.Stat.MAX = function(numbers){
    return d3.max(numbers);
};

/**
 * Sorts an array of data in ascending order. Removes null/undefined values.
 * @param {Array} data An array of objects that have numeric values.
 * @param {Function} accessor A function used to access the numeric value that needs to be sorted.
 * @returns {Array}
 */
LABKEY.vis.Stat.sortNumericAscending = function(data, accessor){
    var numbers = [];
    for(var i = 0; i < data.length; i++){
        var value = accessor(data[i]);
        if(value !== null && value !== undefined){
            numbers.push(value);
        }
    }
    numbers.sort(function(a, b){return a-b;});
    return numbers;
};

/**
 * Sorts an array of data in descending order. Removes null/undefined values.
 * @param {Array} data An array of objects that have numeric values.
 * @param {Function} accessor A function used to access the numeric value that needs to be sorted.
 * @returns {Array}
 */
LABKEY.vis.Stat.sortNumericDescending = function(data, accessor){
    var numbers = [];
    for(var i = 0; i < data.length; i++){
        var value = accessor(data[i]);
        if(value !== null && value !== undefined){
            numbers.push(value);
        }
    }
    numbers.sort(function(a, b){return b-a;});
    return numbers;
};

/**
 * Executes a given function n times passing in values between min and max and returns an array of each result. Could
 * be used to generate data to plot a curve fit as part of a plot.
 * @param {Function} fn The function to be executed n times. The function must take one number as a parameter.
 * @param {Number} n The number of times to execute fn.
 * @param {Number} min The minimum value to pass to fn.
 * @param {Number} max The maximum value to pass to fn.
 */
LABKEY.vis.Stat.fn = function(fn, n, min, max){
    if(n === undefined || n === null || n < 2){
        // We need at least 2 points to make a line.
        n = 2;
    }

    var data = [],
        stepSize = Math.abs((max - min) / (n-1)),
        count = min;

    for(var i = 0; i < n; i++){
        data.push({x: count, y: fn(count)});
        count += stepSize;
    }

    return data;
};

/**
 * Returns the average value.
 * @param values An array of numbers.
 * @returns {Number}
 */
LABKEY.vis.Stat.getMean = function(values)
{
    if (values == null || values.length == 0)
        throw "invalid input";
    return values.map(function(x,i,arr){return x/arr.length}).reduce(function(a,b){return a + b});
};

/**
 * An alias for LABKEY.vis.Stat.getMean
 */
LABKEY.vis.Stat.MEAN = LABKEY.vis.Stat.getMean;

/**
 * Returns the standard deviation.
 * @param values An array of numbers.
 * @returns {Number}
 */
LABKEY.vis.Stat.getStdDev = function(values)
{
    if (values == null)
        throw "invalid input";
    var mean = LABKEY.vis.Stat.getMean(values);
    var squareDiffs =  values.map(function(value){
        var diff = value - mean;
        return diff * diff;
    });
    var avgSquareDiff = LABKEY.vis.Stat.getMean(squareDiffs);
    return Math.sqrt(avgSquareDiff);
};

// CUSUM_WEIGHT_FACTOR of 0.5 and CUSUM_CONTROL_LIMIT of 5 to achieve a 3*stdDev boundary
LABKEY.vis.Stat.CUSUM_WEIGHT_FACTOR = 0.5;
LABKEY.vis.Stat.CUSUM_CONTROL_LIMIT = 5;
LABKEY.vis.Stat.CUSUM_CONTROL_LIMIT_LOWER = 0;
LABKEY.vis.Stat.CUSUM_EPSILON = 0.0000001;

/**
 * Calculates a variety of cumulative sums for a data array.
 * @param values Array of data values to calculate from
 * @param negative True to calculate CUSUM-, false to calculate CUSUM+. (default to false)
 * @param transform True to calculate CUSUMv (Variability CUSUM), false to calculate CUSUMm (Mean CUSUM). (default to false)
 * @param forcePositiveResult True to force all result values to be no less than a specified positive value, usually used for log scale. (default to false)
 * @param epsilon The smallest value that all returned value can be, only used if forcePositiveResult is true. (default to LABKEY.vis.Stat.CUSUM_EPSILON)
 * @returns {number[]}
 */
LABKEY.vis.Stat.getCUSUM = function(values, negative, transform, forcePositiveResult, epsilon)
{
    if (values == null || values.length < 2)
        return [];
    var mean = LABKEY.vis.Stat.getMean(values);
    var stdDev = LABKEY.vis.Stat.getStdDev(values);
    if (stdDev == 0) // in the case when all values are equal, calculation has to abort, special case CUSUM to all be 0
    {
        var edgeCaseResults = [];
        for (var k = 0; k < values.length; k++)
            edgeCaseResults.push(0);
        return edgeCaseResults;
    }
    var cusums = [0];
    for (var i = 0; i < values.length; i++)
    {
        var standardized = (values[i] - mean) / stdDev; //standard value (z-score)
        if (transform)
            standardized = (Math.sqrt(Math.abs(standardized)) - 0.822) / 0.349; //the transformed standardize normal quantity value so that it is sensitive to variability changes
        if (negative)
            standardized = standardized * -1;
        var cusum = Math.max(0, standardized - LABKEY.vis.Stat.CUSUM_WEIGHT_FACTOR + cusums[i]);
        cusums.push(cusum);
    }
    cusums.shift(); // remove the initial 0 value
    if (forcePositiveResult)
    {
        var lowerBound = epsilon ? epsilon : LABKEY.vis.Stat.CUSUM_EPSILON;
        for (var j = 0; j < cusums.length; j++)
        {
            cusums[j] = Math.max(cusums[j], lowerBound);
        }
    }
    return cusums;
};

// MOVING_RANGE_UPPER_LIMIT_WEIGHT is chosen to provide a type I error rate of 0.0027 which guarantees 3*stdDev
LABKEY.vis.Stat.MOVING_RANGE_UPPER_LIMIT_WEIGHT = 3.268;
LABKEY.vis.Stat.MOVING_RANGE_LOWER_LIMIT = 0;
LABKEY.vis.Stat.MOVING_RANGE_EPSILON = 0.0000001;

/**
 * Calculate the moving range values for a data array, which are sequential differences between two successive values.
 * @param values Array of data values to calculate from
 * @param forcePositiveResult True to force all result values to be no less than a specified positive value, usually used for log scale. (default to false)
 * @param epsilon The smallest value that all returned value can be, only used if forcePositiveResult is true. (default to LABKEY.vis.Stat.MOVING_RANGE_EPSILON)
 * @returns {number[]}
 */
LABKEY.vis.Stat.getMovingRanges = function(values, forcePositiveResult, epsilon)
{
    if (values == null || values.length < 1)
        return [];
    var mR = [0]; //mR[0] is always 0
    for (var i = 1; i < values.length; i++)
    {
        mR.push(Math.abs(values[i] - values[i-1]));
    }
    if (forcePositiveResult)
    {
        var lowerBound = epsilon ? epsilon : LABKEY.vis.Stat.MOVING_RANGE_EPSILON;
        for (var j = 0; j < mR.length; j++)
        {
            mR[j] = Math.max(lowerBound, mR[j]);
        }
    }
    return mR;
};

