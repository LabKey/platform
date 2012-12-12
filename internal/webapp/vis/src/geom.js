/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if(!LABKEY.vis.Geom){
    /**
     * @namespace The Geom namespace. Used for all geoms, such as {@link LABKEY.vis.Geom.Point},
     * {@link LABKEY.vis.Geom.Path}, etc.
     */
	LABKEY.vis.Geom = {};
}

/**
 * @class The base XY geom. Extended by all geoms that will be plotted on a grid using a Cartesian [X,Y] coordinate system.
 *      This is a base class meant to be used internally only.
 */
LABKEY.vis.Geom.XY = function(){
    this.type = "XY";
    return this;
};
LABKEY.vis.Geom.XY.prototype.initAesthetics = function(scales, layerAes, parentAes){
    this.xMap = layerAes.x ? layerAes.x : parentAes.x;
    if(!this.xMap){
        console.error('x aesthetic is required for ' + this.type + ' geom to render.');
        return false;
    }

    if(layerAes.yLeft){
        this.yMap = layerAes.yLeft;
        this.yMap.side = 'left';
    } else if(layerAes.yRight){
        this.yMap = layerAes.yRight;
        this.yMap.side = 'right';
    } else if(parentAes.yLeft){
        this.yMap = parentAes.yLeft;
        this.yMap.side = 'left';
    } else if(parentAes.yRight){
        this.yMap = parentAes.yRight;
        this.yMap.side = 'right';
    }

    if(!this.yMap){
        console.error('y aesthetic is required for ' + this.type + ' geom to render.');
        return false;
    }

    this.colorMap = layerAes.color ? layerAes.color : parentAes.color;

    return true;
};

LABKEY.vis.Geom.XY.prototype.getVal = function(scale, map, row){
    // Takes a row, returns the scaled y value.
    var value = map.getValue(row);

    if(!LABKEY.vis.isValid(value)){
        if(this.plotNullPoints){
            return scale(scale.domain()[0]) - 5;
        } else {
            return null;
        }
    } else {
        return scale(value);
    }
};

LABKEY.vis.Geom.XY.prototype.getX = function(scale, row){
    // Takes a row, returns the scaled x value.
    return this.getVal(scale, this.xMap, row);
};

LABKEY.vis.Geom.XY.prototype.getY = function(scale, row){
    // Takes a row, returns the scaled x value.
    return this.getVal(scale, this.yMap, row);
};

/**
 * @class The Point geom, used to generate points of data on a plot such as points on a line or points in a scatter plot.
 *      This geom supports the use of size, color, shape, hoverText, and pointClickFn aesthetics from the
 *      {@link LABKEY.vis.Layer} and/or {@link LABKEY.vis.Plot} objects.
 * @param {Object} config An object with the following properties:
 * @param {String} [config.color] Optional. String used to determine the color of all points. Defaults to black (#000000).
 * @param {Number} [config.size] Optional. Number used to determine the size of all points.  Defaults to 5.
 * @param {Number} [config.opacity] Optional. Number between 0 and 1, used to determine the opacity of all points. Useful if
 *      there are overlapping points in the data. Defaults to 1.
 * @param {Boolean} [config.plotNullPoints] Optional. Used to toggle whether or not a row of data with the value of null will be
 *      plotted. If true null or undefined values will be plotted just outside the axis with data. For example if a
 *      row of data looks like {x: 50, y: null} the point would appear at 50 on the x-axis, and just below the x axis.
 *      If both x and y values are null the point will be drawn to the bottom left of the origin. Defaults to false.
 * @param {String} [config.position] Optional. String with possible value "jitter". If config.position is "jitter" and the
 *      x or y scale is discrete it will be moved just before or after the position on the grid by a random amount.
 *      Useful if there is overlapping data. Defaults to undefined.
 */
LABKEY.vis.Geom.Point = function(config){
    this.type = "Point";

    if(!config){
        config = {};
    }
    this.color = ('color' in config && config.color != null && config.color != undefined) ? config.color : '#000000';
    this.size = ('size' in config && config.size != null && config.size != undefined) ? config.size : 5;
    this.opacity = ('opacity' in config && config.opacity != null && config.opacity != undefined) ? config.opacity : 1;
    this.plotNullPoints = ('plotNullPoints' in config && config.plotNullPoints != null && config.plotNullPoints != undefined) ? config.plotNullPoints : false;
    this.position = ('position' in config && config.position != null && config.position != undefined) ? config.position : null;

    return this;
};
LABKEY.vis.Geom.Point.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Point.prototype.render = function(paper, grid, scales, data, layerAes, parentAes, name){
    if(!this.initAesthetics(scales, layerAes, parentAes)){
        return false;
    }
    var hoverText = layerAes.hoverText ? layerAes.hoverText : parentAes.hoverText;
    var sizeMap = layerAes.size ? layerAes.size : parentAes.size;
    var pointClickFn = layerAes.pointClickFn ? layerAes.pointClickFn : parentAes.pointClickFn;
    var yScale = this.yMap.side == "left" ? scales.yLeft : scales.yRight;
    var xBinWidth = null, yBinWidth = null;
    if(scales.x.scaleType == 'discrete'){
        xBinWidth = ((grid.rightEdge - grid.leftEdge) / (scales.x.scale.domain().length)) / 2;
    }

    if(yScale.scaleType == 'discrete'){
        yBinWidth = ((grid.topEdge - grid.bottomEdge) / (yScale.scale.domain().length)) / 2;
    }

    if(layerAes.shape){
        this.shapeMap = layerAes.shape;
    } else if(parentAes.shape){
        this.shapeMap = parentAes.shape;
    }

    for(var i = 0; i < data.length; i++){
        var y = this.getY(yScale.scale, data[i]);
        var x = this.getX(scales.x.scale, data[i]);

        if(!x || !y){
            continue;
        }

        if(xBinWidth != null && this.position == 'jitter'){
            x = (x - (xBinWidth / 2)) +(Math.random()*(xBinWidth));
        }

        if(yBinWidth != null && this.position == 'jitter'){
            y = (y - (yBinWidth / 2)) +(Math.random()*(yBinWidth));
        }

        var color = this.colorMap ? scales.color.scale(this.colorMap.getValue(data[i]) + name) : this.color;
        var size = sizeMap ? scales.size.scale(sizeMap.getValue(data[i])) : this.size;

        if(size === null || size === undefined || isNaN(size)){
            size = this.size;
        }

        var shapeFunction = this.shapeMap ?
                scales.shape.scale(this.shapeMap.getValue(data[i]) + name) :
                function(paper, x, y, r){return paper.circle(x, y, r)};

        var point = shapeFunction(paper, x, -y, size)
                .attr('fill', color)
                .attr('fill-opacity', this.opacity)
                .attr('stroke', color)
                .attr('stroke-opacity', this.opacity);

        if(hoverText){
            point.attr('title', hoverText.value(data[i]));
        }

        if (pointClickFn) {
            point.data = data[i];
            point.click(function(clickEvent) {
                pointClickFn.value(clickEvent, this.data);
            });
        }
    }

    return true;
};

/**
 * @class The path geom, used to draw paths in a plot. In order to get multiple lines for a set of data the user must define an
 * accessor with the name "group" in the config.aes object of the {LABKEY.vis.Plot} or {LABKEY.vis.Layer} object. For
 * example if the data looked like {x: 12, y: 35, name: "Alan"} the config.aes.group accessor could be "Alan", or a
 * function: function(row){return row.name}. Each unique name would get a separate line. The geom also supports color
 * and size aesthetics from the {LABKEY.vis.Plot} and/or {LABKEY.vis.Layer} objects.
 * @param {Object} config An object with the following properties:
 * @param {String} [config.color] Optional. String used to determine the color of all paths. Defaults to black (#000000).
 * @param {Number} [config.size] Optional. Number used to determine the size of all paths.  Defaults to 3.
 * @param {Number} [config.opacity]. Optional. Number between 0 and 1, used to determine the opacity of all paths. Useful
 *      if there are many overlapping paths. Defaults to 1.
 */
LABKEY.vis.Geom.Path = function(config){
    this.type = "Path";
    
    if(!config){
        config = {};
    }
    this.color = ('color' in config && config.color != null && config.color != undefined) ? config.color : '#000000';
    this.size = ('size' in config && config.size != null && config.size != undefined) ? config.size : 3;
    this.opacity = ('opacity' in config && config.opacity != null && config.opacity != undefined) ? config.opacity : 1;

    return this;
};
LABKEY.vis.Geom.Path.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Path.prototype.render = function(paper, grid, scales, data, layerAes, parentAes, name){
    if(!this.initAesthetics(scales, layerAes, parentAes)){
        return false;
    }
    var yScale = this.yMap.side == "left" ? scales.yLeft.scale : scales.yRight.scale;

    this.group = layerAes.group ? layerAes.group : parentAes.group;
    var size = this.size;
    var pathScope = this;
    var xAccessor = function(row){return pathScope.getX(scales.x.scale, row);};
    var yAccessor = function(row){var val = pathScope.getY(yScale, row); return val == null ? null : -val;};

    if(this.group){
        //Split data into groupedData so we can render 1 path per group.
        var groupedData = LABKEY.vis.groupData(data, this.group.getValue);

        for(var group in groupedData){
            var groupData = groupedData[group];
            var color = this.color;
            var path = LABKEY.vis.makePath(groupData, xAccessor, yAccessor);

            if(path != ''){
                if(this.colorMap && this.colorMap.name == this.group.name){
                    // If we have a colorMap and it maps to the same thing as groupedData, then we pass in the group to get the desired color.
                    color = scales.color.scale(group + name);
                }

                if(this.sizeMap){
                    size = this.sizeMap.getValue(groupData);
                }
                paper.path(path).attr('stroke', color).attr('stroke-width', size).attr('opacity', this.opacity);
            }
        }
    } else {
        // No groups, connect all the points.
        if(this.sizeMap){
            size = this.sizeMap.getValue(data); // This would allow a user to look at all of the rows in a group and calculate size (i.e. a user could average the CD4 in every group and make a line based on that average.)
        }
        var path = LABKEY.vis.makePath(data, xAccessor, yAccessor);
        if(path != ''){
            paper.path(path).attr('stroke-width', size).attr('opacity', this.opacity).attr('stroke', this.color);
        }
    }

    return true;
};

/**
 * @class Error bar geom. Generally used in conjunction with a {@link LABKEY.vis.Geom.Point} and/or {@link LABKEY.vis.Geom.Path}
 * geom to show the known amount of error for a given point. In order to work the user must specify an error accessor
 * in the config.aes object of the {LABKEY.vis.Plot} or {LABKEY.vis.Layer} object. This Geom also supports the color
 * aesthetic from the {LABKEY.vis.Plot} and/or {LABKEY.vis.Layer} objects.
 * @param config An object with the following properties:
 * @param {String} [config.color] Optional. String used to determine the color of all paths. Defaults to black (#000000).
 * @param {Number} [config.size] Optional. Number used to determine the size of all paths.  Defaults to 2.
 */
LABKEY.vis.Geom.ErrorBar = function(config){
    this.type = "ErrorBar";

    if(!config){
        config = {};
    }
    this.color = ('color' in config && config.color != null && config.color != undefined) ? config.color : '#000000';
    this.size = ('size' in config && config.size != null && config.size != undefined) ? config.size : 2;

    return this;
};
LABKEY.vis.Geom.ErrorBar.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.ErrorBar.prototype.render = function(paper, grid, scales, data, layerAes, parentAes, name){
    if(!this.initAesthetics(scales, layerAes, parentAes)){
        return false;
    }
    var yScale = this.yMap.side == "left" ? scales.yLeft.scale : scales.yRight.scale;

    this.errorMap = layerAes.error ? layerAes.error : parentAes.error;
    if(!this.errorMap){
        console.error("The error aesthetic is required for the ErrorBar geom.");
        return false;
    }

    for(var i = 0; i < data.length; i++){
        var x = scales.x.scale(this.xMap.getValue(data[i]));
        var y = this.yMap.getValue(data[i]);
        var errorAtPoint = this.errorMap.getValue(data[i]);
        var yBottom = -yScale(y - errorAtPoint);
        var yTop = -yScale(y + errorAtPoint);
        var color;

        if(LABKEY.vis.isValid(yBottom) && LABKEY.vis.isValid(yTop)){
            color = this.colorMap ? scales.color.scale(this.colorMap.getValue(data[i]) + name) : this.color;
            var topBar = LABKEY.vis.makeLine(x - 6, yTop, x+6, yTop),
                middleBar = LABKEY.vis.makeLine(x, yTop, x, yBottom),
                bottomBar = LABKEY.vis.makeLine(x-6, yBottom, x+6, yBottom);

            var errorBar = paper.path(topBar + middleBar + bottomBar).attr('stroke-width', this.size).attr('stroke', color).attr('fill', color);
        }
    }

    return true;
};


/**
 * @class The Boxplot Geom, used to generate box plots for a given set of data. In order to get multiple box plots for a set of
 * data with a continuous x-axis scale, the user must define an accessor with the name "group" in the config.aes object
 * of the {LABKEY.vis.Plot} or {LABKEY.vis.Layer} object. For example if the data looked like {x: 12, y: 35, name: "Alan"}
 * the config.aes.group accessor could be "Alan", or a function: function(row){return row.name}. Each unique name would
 * get a separate box plot. If aes.group is not present one boxplot will be generated for all of the data. This geom
 * also supports the outlierColor, outlierShape, hoverText, outlierHoverText, and pointClickFn aesthetics from the
 * {LABKEY.vis.Plot} and/or {LABKEY.vis.Layer} objects.
 *
 * Boxplots are drawn as follows:
 * <ul>
 *      <li>The top line of the box is the first quartile (Q1)</li>
 *      <li>The middle like is the second quartile (Q2, aka median)</li>
 *      <li>The bottom line is the third quartile (Q3)</li>
 *      <li>The whiskers extend to 3/2 times the inner quartile range (Q3 - Q1, aka IQR)</li>
 *      <li>All data points that are greater than 3/2 times the IQR are drawn as outliers</li>
 * </ul>
 *
 * @param {Object} config An object with the following properties:
 * @param {String} [config.color] Optional. A string value used for the line colors in the box plot. Defaults to black
 *      (#000000)
 * @param {String} [config.fill] Optional. A string value used for the fill color in the box plot. Defaults to white
 *      (#ffffff)
 * @param {Number} [config.lineWidth] Optional. A used to set the width of the lines used in the box plot. Defaults to 1.
 * @param {Number} [config.opacity] Optional. A number between 0 and 1 used to set the opacity of the box plot. Defaults
 *      to 1.
 * @param {String} [config.position] Optional. A string used to determine how to position the outliers. Currently the
 *      only possible value is "jitter", which will move the points to the left or right of the center of the box plot by
 *      a random amount. Defaults to undefined.
 * @param {Boolean} [config.showOutliers] Optional. Used to toggle whether or not outliers are rendered. Defaults to true.
 * @param {String} [config.outlierFill] Optional. A string value used to set the fill color of the outliers. Defaults
 *      to black (#000000).
 * @param {Number} [config.outlierOpacity] Optional. A number between 0 and 1 used to set the opacity of the outliers.
 *      Defaults to 1.
 * @param {Number} [config.outlierSize] Optional. A used to set the size of outliers. Defaults to 3.
 */
LABKEY.vis.Geom.Boxplot = function(config){
    this.type = "Boxplot";

    if(!config){
        config = {};
    }
    this.color = ('color' in config && config.color != null && config.color != undefined) ? config.color : '#000000'; // line color
    this.fill = ('fill' in config && config.fill != null && config.fill != undefined) ? config.fill : '#ffffff'; // fill color
    this.lineWidth = ('lineWidth' in config && config.lineWidth != null && config.lineWidth != undefined) ? config.lineWidth : 1;
    this.opacity = ('opacity' in config && config.opacity != null && config.opacity != undefined) ? config.opacity : 1;
    this.position = ('position' in config && config.position != null && config.position != undefined) ? config.position : null;
    this.showOutliers = ('showOutliers' in config && config.showOutliers != null && config.showOutliers != undefined) ? config.showOutliers : true;
    this.outlierFill = ('outlierFill' in config && config.outlierFill != null && config.outlierFill != undefined) ? config.outlierFill : '#000000';
    this.outlierOpacity = ('outlierOpacity' in config && config.outlierOpacity != null && config.outlierOpacity != undefined) ? config.outlierOpacity : .5;
    this.outlierSize = ('outlierSize' in config && config.outlierSize != null && config.outlierSize != undefined) ? config.outlierSize : 3;

    return this;
};
LABKEY.vis.Geom.Boxplot.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Boxplot.prototype.render = function(paper, grid, scales, data, layerAes, parentAes, name){
    if(!this.initAesthetics(scales, layerAes, parentAes)){
        return false;
    }

    var yScale = this.yMap.side == "left" ? scales.yLeft.scale : scales.yRight.scale;
    var hoverText = layerAes.hoverText ? layerAes.hoverText : parentAes.hoverText;
    var outlierHoverText = layerAes.outlierHoverText ? layerAes.outlierHoverText : parentAes.outlierHoverText;
    var pointClickFn = layerAes.pointClickFn ? layerAes.pointClickFn : parentAes.pointClickFn;
    var outlierColorMap = layerAes.outlierColor ? layerAes.outlierColor : parentAes.outlierColor;
    var outlierShapeMap = layerAes.outlierShape ? layerAes.outlierShape : parentAes.outlierShape;
    var groupedData = null;
    var binWidth = null;

    var plotBox = function(x, width, top, bottom, middleY, topWhisker, bottomWhisker, hoverText){
        var middleX = Math.floor(x+(width/2)) + .5;
        var whiskerLeft = middleX - (width / 4);
        var whiskerRight = middleX + (width / 4);
        var boxSet = paper.set();
        var box = paper.rect(x, top, width, Math.abs(top - bottom)).attr('fill', this.fill).attr('fill-opacity', this.opacity);
        if(hoverText){
            box.attr('title', hoverText.value(group, stats));
        }
        boxSet.push(
                // Construct the box.
                box,
                paper.path(LABKEY.vis.makeLine(x, middleY, x+width, middleY)),
                // Construct the Whiskers.
                paper.path(LABKEY.vis.makeLine(middleX, bottom, middleX, bottomWhisker)),
                paper.path(LABKEY.vis.makeLine(whiskerLeft, bottomWhisker, whiskerRight, bottomWhisker)),
                paper.path(LABKEY.vis.makeLine(middleX, top, middleX, topWhisker)),
                paper.path(LABKEY.vis.makeLine(whiskerLeft, topWhisker, whiskerRight, topWhisker))
        );
        boxSet.attr('stroke', this.color).attr('stroke-width', this.lineWidth);
    };

    if(scales.x.scaleType == 'continuous'){
        //Split data into groupedData so we can render 1 path per group.
        if(this.group){
            groupedData = LABKEY.vis.groupData(data, this.group.getValue);
        } else {
            console.error('No group specified for a continuous scale, cannot render boxes.');
            return;
        }
    } else {
        // Categorical data will always get grouped by X axis value
        groupedData = LABKEY.vis.groupData(data, this.xMap.getValue);
        binWidth = (grid.rightEdge - grid.leftEdge) / (scales.x.scale.domain().length); // Should be able to use scale.rangeBand() but in this version of d3 it seems to be broken.
    }

    for(var group in groupedData){
        // Create a box.
        var stats = LABKEY.vis.Stat.summary(groupedData[group], this.yMap.getValue);
        var width = binWidth / 2;
        var middleX = Math.floor(scales.x.scale(group)) + .5;
        var x = scales.x.scale(group) - width/2;
        var bottom = Math.floor(-yScale(stats.Q1)) + .5;
        var top = Math.floor(-yScale(stats.Q3)) + .5;
        var middleY = Math.floor(-yScale(stats.Q2)) + .5;
        var smallestNotOutlier = stats.Q1 - (1.5 * stats.IQR);
        var biggestNotOutlier = stats.Q3 + (1.5 * stats.IQR);
        var i = 0;
        
        while(stats.sortedValues[i] < smallestNotOutlier){
            i++;
        }
        var bottomWhisker = Math.floor(-yScale(stats.sortedValues[i])) + .5;

        i = stats.sortedValues.length - 1;
        while(stats.sortedValues[i] > biggestNotOutlier){
            i--;
        }
        var topWhisker = Math.floor(-yScale(stats.sortedValues[i])) + .5;

        plotBox.call(this, x, width, top, bottom, middleY, topWhisker, bottomWhisker, hoverText);

        if(this.showOutliers){
            for(i = 0; i < groupedData[group].length; i++){
                var val = this.yMap.getValue(groupedData[group][i]);
                if(val > biggestNotOutlier || val < smallestNotOutlier){
                    var outlier;
                    var outlierX = (this.position == 'jitter') ? x+(Math.random()*(width)) : middleX;

                    var color = outlierColorMap && scales.color ?
                            scales.color.scale(outlierColorMap.getValue(groupedData[group][i])) :
                            this.outlierFill;
                    
                    var shapeFunction = outlierShapeMap && scales.shape ?
                            scales.shape.scale(outlierShapeMap.getValue(groupedData[group][i]) + name) :
                            function(paper, x, y, r){return paper.circle(x, y, r)};

                    outlier = shapeFunction(paper, outlierX, -yScale(val), this.outlierSize)
                            .attr('fill', color)
                            .attr('fill-opacity', this.outlierOpacity)
                            .attr('stroke', color)
                            .attr('stroke-opacity', this.outlierOpacity);

                    if(outlierHoverText){
                        outlier.attr('title', outlierHoverText.getValue(groupedData[group][i]));
                    }

                    if (pointClickFn) {
                        outlier.data = groupedData[group][i];
                        outlier.click(function(clickEvent) {
                            pointClickFn.value(clickEvent, this.data);
                        });
                    }
                }
            }
        }
    }

    return true;
};

// The text Geom is not yet complete, do not use it.

LABKEY.vis.Geom.Text = function(config){
    this.type = "Text";

    if(!config){
        config = {};
    }
    this.color = config.color ? config.color : '#000000';
    this.fontSize = config.size ? config.fontSize : 12;

    return this;
};
LABKEY.vis.Geom.Text.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Text.prototype.render = function(paper, grid, scales, data, layerAes, parentAes){
    if(!this.initAesthetics(scales, layerAes, parentAes)){
        return false;
    }
    var yScale = this.yMap.side == "left" ? scales.yLeft.scale : scales.yRight.scale;
    
    return true;
};
