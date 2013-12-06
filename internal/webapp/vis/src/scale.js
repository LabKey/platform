/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/********** Scales **********/

if(!LABKEY.vis.Scale){
    /**
     * @namespace Namespace used for scales in {@link LABKEY.vis.Plot} objects.
     */
	LABKEY.vis.Scale = {};
}

/**
 * Discrete scale used in plots. Used internally by the {@link LABKEY.vis.Plot} object.
 * @param {Array} domain Array of discrete/categorical values to be used in the scale.
 * @param {Array} range an array of length two, with min and m ax values in it. The min and max values are the start and
 *      end positions, in pixels, of the grid. Ex: [125, 640].
 */
LABKEY.vis.Scale.Discrete = function(domain, range){
	// This is a discrete scale, used for categorical data (e.g. visits).
	return d3.scale.ordinal().domain(domain).rangeBands(range, 1);
};

/**
 * Continuous scale used in plots. Used internally by the {@link LABKEY.vis.Plot} object.
 * @param {String} trans String with values "linear" or "log".
 * @param {Array} [data] The array of data used to determine domain if not known.
 * @param {Function} value The function used to get the value from each object in the array.
 * @param {Array} [domain] an array of length two, with min and max values in it. The min and max values are from the data
 *      that is going to be plotted. Ex: [0, 550].
 * @param {Array} range an array of length two, with min and m ax values in it. The min and max values are the start and
 *      end positions, in pixels, of the grid. Ex: [125, 640].
 */
LABKEY.vis.Scale.Continuous = function(trans, data, value, domain, range){
	// This is a continuous scale (e.g. dates, numbers).
	var scale = null;

	if(!domain){
		var max = d3.max(data, value);
		var min = d3.min(data, value);
		domain = [min, max];
	}

    if(domain[0] == domain[1]){
        var min = domain[0] - domain[0],
            max = domain[0] * 2;
        domain[0] = min;
        domain[1] = max;
    }

	if(trans == 'linear'){
		scale = d3.scale.linear().domain(domain).range(range);
        return scale;
	} else {
        var increment = false;

        if(domain[0] == 0){
            domain[0] = domain[0] + 1;
            domain[1] = domain[1] + 1;
            increment = true;
        }

		scale = d3.scale.log().domain(domain).range(range);
        var logScale = function(val){
            if(val != 0 && increment === true){
                val = val + 1;
            }
            return val <= 0 ? (scale(scale.domain()[0]) - 5) : scale(val);
        };
        logScale.domain = scale.domain;
        logScale.range = scale.range;
        logScale.ticks = function(){
            var allTicks = scale.ticks();
            var ticksToShow = [];

            if(allTicks.length < 10){
                return allTicks;
            } else {
                for(var i = 0; i < allTicks.length; i++){
                    if(i % 9 == 0){
                        ticksToShow.push(allTicks[i]);
                    }
                }
                return ticksToShow;
            }
        };

        return logScale;
	}
};

/**
 * The default color scale used in plots.
 */
LABKEY.vis.Scale.ColorDiscrete = function(){
	// Used for discrete color scales (color assigned to categorical data)
    return d3.scale.ordinal().range([ "#66C2A5", "#FC8D62", "#8DA0CB", "#E78AC3", "#A6D854", "#FFD92F", "#E5C494", "#B3B3B3"]);
};

/**
 * An alternate darker color scale. Not currently used.
 */
LABKEY.vis.Scale.DarkColorDiscrete = function(){
	// Used for discrete color scales (color assigned to categorical data)
    return d3.scale.ordinal().range(["#378a70", "#f34704", "#4b67a6", "#d53597", "#72a124", "#c8a300", "#d19641", "#808080"]);
};

/**
 * Function that returns a discrete scale used to determine the shape of points in {@link LABKEY.vis.Geom.BoxPlot} and
 * {@link LABKEY.vis.Geom.Point} geoms.
 */
LABKEY.vis.Scale.Shape = function(){
    var circle = function(s){
        return "M0," + s + "A" + s + "," + s + " 0 1,1 0," + -s + "A" + s + "," + s + " 0 1,1 0," + s + "Z";
    };
    var square = function(s){
        return "M" + -s + "," + -s + "L" + s + "," + -s + " " + s + "," + s + " " + -s + "," + s + "Z";
    };
    var diamond = function(s){
        var r = (Math.sqrt(1.5 * Math.pow(s * 2, 2))) / 2;
        return 'M0 ' + r + ' L ' + r + ' 0 L 0 ' + -r + ' L ' + -r + ' 0 Z';
    };
    var triangle = function(s){
        return 'M0,' + s + 'L' + s + ',' + -s + 'L' + -s + ',' + -s + ' Z';
    };
    var x = function(s){
        // TODO: eliminate x and y from this.
        var x = 0;
        var y = 0;
        var r = s / 2;
        return 'M' + (x) + ',' + (y + r) +
                'L' + (x + r) + ',' + (y + 2*r) + 'L' + (x + 2*r) + ',' + (y + r) +
                'L' + (x + r) + ',' + (y) + 'L' + (x + 2*r) + ',' + (y - r) +
                'L' + (x + r) + ',' + (y - 2*r) + 'L' + (x) + ',' + (y - r) +
                'L' + (x - r) + ',' + (y - 2*r) + 'L' + (x - 2*r) + ',' + (y - r) +
                'L' + (x - r) + ',' + (y) + 'L' + (x - 2*r) + ',' + (y + r) +
                'L' + (x - r) + ',' + (y + 2*r) + 'L' + (x) + ',' + (y + r) + 'Z';
    };

    return d3.scale.ordinal().range([circle, triangle, square, diamond, x]);
};
