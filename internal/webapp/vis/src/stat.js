/*
 * Copyright (c) 2012 LabKey Corporation
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
LABKEY.vis.Stat.median = LABKEY.vis.Stat.Q2;


/**
 * Returns the 3rd quartile for a sorted (asc) array.
 * @param numbers An array of numbers.
 * @returns {Number}
 */
LABKEY.vis.Stat.Q3 = function(numbers){
    return d3.quantile(numbers,0.75);
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
