/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
    this.xAes = null;
    this.yAes = null;
    this.xScale = null;
    this.yScale = null;
    this.colorAes = null;
    this.colorScale = null;
    return this;
};
LABKEY.vis.Geom.XY.prototype.initAesthetics = function(scales, layerAes, parentAes, layerName, index){
    this.layerName = layerName;
    this.index = index;
    this.xAes = layerAes.x ? layerAes.x : parentAes.x;
    this.xScale = scales.x;
    if(!this.xAes){
        console.error('x aesthetic is required for ' + this.type + ' geom to render.');
        return false;
    }

    if(layerAes.yLeft){
        this.yAes = layerAes.yLeft;
        this.yScale = scales.yLeft;
    } else if(layerAes.yRight){
        this.yAes = layerAes.yRight;
        this.yScale = scales.yRight;
    } else if(parentAes.yLeft){
        this.yAes = parentAes.yLeft;
        this.yScale = scales.yLeft;
    } else if(parentAes.yRight){
        this.yAes = parentAes.yRight;
        this.yScale = scales.yRight;
    }

    this.colorAes = layerAes.color ? layerAes.color : parentAes.color;
    this.colorScale = scales.color ? scales.color : null;

    if(!this.yAes){
        console.error('y aesthetic is required for ' + this.type + ' geom to render.');
        return false;
    }

    return true;
};

LABKEY.vis.Geom.XY.prototype.getVal = function(scale, map, row, isY){
    // Takes a row, returns the scaled y value.
    var value = map.getValue(row);

    if(!LABKEY.vis.isValid(value)){
        if(this.plotNullPoints){
            return isY ? scale(scale.domain()[0]) + 5 : scale(scale.domain()[0]) - 5;
        } else {
            return null;
        }
    } else {
        return scale(value);
    }
};

LABKEY.vis.Geom.XY.prototype.getX = function(row){
    // Takes a row, returns the scaled x value.
    return this.getVal(this.xScale.scale, this.xAes, row, false);
};

LABKEY.vis.Geom.XY.prototype.getY = function(row){
    // Takes a row, returns the scaled x value.
    return this.getVal(this.yScale.scale, this.yAes, row, true);
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
LABKEY.vis.Geom.Point.prototype.render = function(renderer, grid, scales, data, layerAes, parentAes, name, index){
    if(!this.initAesthetics(scales, layerAes, parentAes, name, index)){
        return false;
    }

    this.shapeAes = layerAes.shape ? layerAes.shape : parentAes.shape;
    this.shapeScale = scales.shape;
    this.sizeAes = layerAes.size ? layerAes.size : parentAes.size;
    this.sizeScale = scales.size;
    this.hoverTextAes = layerAes.hoverText ? layerAes.hoverText : parentAes.hoverText;
    this.pointClickFnAes = layerAes.pointClickFn ? layerAes.pointClickFn : parentAes.pointClickFn;
    this.mouseOverFnAes = layerAes.mouseOverFn ? layerAes.mouseOverFn : parentAes.mouseOverFn;
    this.mouseOutFnAes = layerAes.mouseOutFn ? layerAes.mouseOutFn : parentAes.mouseOutFn;

    renderer.renderPointGeom(data, this);
    return true;
};

/**
 * @private
 * @class The HexBin geom, used to bin a set of data and generate a hexagonal plot of binned points. Currently, under development and not for external use. Normally, this is used for scatter x-y based data.
 * @param config
 * @returns {LABKEY.vis.Geom.HexBin}
 * @constructor
 */
LABKEY.vis.Geom.HexBin = function(config) {
    this.type = "HexBin";

    if (!config) {
        config = {};
    }

    // The following configurations were copied from 'Point' geom and most likely not all (none?) of them are needed
    this.color = ('color' in config && config.color != null && config.color != undefined) ? config.color : '#000000';
    this.size = ('size' in config && config.size != null && config.size != undefined) ? config.size : 5;
    this.opacity = ('opacity' in config && config.opacity != null && config.opacity != undefined) ? config.opacity : 1;
    this.plotNullPoints = ('plotNullPoints' in config && config.plotNullPoints != null && config.plotNullPoints != undefined) ? config.plotNullPoints : false;
    this.position = ('position' in config && config.position != null && config.position != undefined) ? config.position : null;

    return this;
};
LABKEY.vis.Geom.HexBin.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.HexBin.prototype.render = function(renderer, grid, scales, data, layerAes, parentAes, name, index){
    if(!this.initAesthetics(scales, layerAes, parentAes, name, index)){
        return false;
    }

    renderer.renderHexBinGeom(data, this);
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
LABKEY.vis.Geom.Path.prototype.render = function(renderer, grid, scales, data, layerAes, parentAes, name, index){
    if(!this.initAesthetics(scales, layerAes, parentAes, name, index)){
        return false;
    }

    this.groupAes = layerAes.group ? layerAes.group : parentAes.group;
    this.sizeAes = layerAes.size ? layerAes.size : parentAes.size;
    this.pathColorAes = layerAes.pathColor ? layerAes.pathColor : parentAes.pathColor;
    this.sizeScale = scales.size;

    renderer.renderPathGeom(data, this);
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
LABKEY.vis.Geom.ErrorBar.prototype.render = function(renderer, grid, scales, data, layerAes, parentAes, name, index){
    if(!this.initAesthetics(scales, layerAes, parentAes, name, index)){
        return false;
    }

    this.errorAes = layerAes.error ? layerAes.error : parentAes.error;

    if (!this.errorAes) {
        console.error("The error aesthetic is required for the ErrorBar geom.");
        return false;
    }

    renderer.renderErrorBarGeom(data, this);
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
LABKEY.vis.Geom.Boxplot.prototype.render = function(renderer, grid, scales, data, layerAes, parentAes, name, index){
    if(!this.initAesthetics(scales, layerAes, parentAes, name, index)){
        return false;
    }
    this.hoverTextAes = layerAes.hoverText ? layerAes.hoverText : parentAes.hoverText;
    this.pointClickFnAes = layerAes.pointClickFn ? layerAes.pointClickFn : parentAes.pointClickFn;
    this.groupAes = layerAes.group ? layerAes.group : parentAes.group;
    this.outlierHoverTextAes = layerAes.outlierHoverText ? layerAes.outlierHoverText : parentAes.outlierHoverText;
    this.outlierColorAes = layerAes.outlierColor ? layerAes.outlierColor : parentAes.outlierColor;
    this.outlierShapeAes = layerAes.outlierShape ? layerAes.outlierShape : parentAes.outlierShape;
    this.shapeScale = scales.shape;

    renderer.renderBoxPlotGeom(data, this);
    return true;
};

LABKEY.vis.Geom.DataspaceBoxPlot = function(config){
    this.type = "DataspaceBoxplot";

    if(!config){
        config = {};
    }

    this.color = ('color' in config && config.color != null && config.color != undefined) ? config.color : '#000000'; // line color
    this.fill = ('fill' in config && config.fill != null && config.fill != undefined) ? config.fill : '#ffffff'; // fill color
    this.lineWidth = ('lineWidth' in config && config.lineWidth != null && config.lineWidth != undefined) ? config.lineWidth : 1;
    this.opacity = ('opacity' in config && config.opacity != null && config.opacity != undefined) ? config.opacity : 1;
    this.showOutliers = ('showOutliers' in config && config.showOutliers != null && config.showOutliers != undefined) ? config.showOutliers : true;
    this.outlierFill = ('outlierFill' in config && config.outlierFill != null && config.outlierFill != undefined) ? config.outlierFill : '#000000';
    this.pointOpacity = ('pointOpacity' in config && config.outlierOpacity != null && config.outlierOpacity != undefined) ? config.outlierOpacity : .5;
    this.pointSize = ('pointSize' in config && config.outlierSize != null && config.outlierSize != undefined) ? config.outlierSize : 3;

    return this;
};
LABKEY.vis.Geom.DataspaceBoxPlot.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.DataspaceBoxPlot.prototype.render = function(renderer, grid, scales, data, layerAes, parentAes, name, index){
    if(!this.initAesthetics(scales, layerAes, parentAes, name, index)){
        return false;
    }

    this.hoverTextAes = layerAes.hoverText ? layerAes.hoverText : parentAes.hoverText;
    this.groupAes = layerAes.group ? layerAes.group : parentAes.group;
    this.pointClickFnAes = layerAes.pointClickFn ? layerAes.pointClickFn : parentAes.pointClickFn;
    this.pointHoverTextAes = layerAes.pointHoverText ? layerAes.pointHoverText : parentAes.pointHoverText;
    this.shapeAes = layerAes.shape ? layerAes.shape : parentAes.shape;
    this.shapeScale = scales.shape;
    this.sizeAes = layerAes.size ? layerAes.size : parentAes.size;
    this.sizeScale = scales.size;
    this.pointClickFnAes = layerAes.pointClickFn ? layerAes.pointClickFn : parentAes.pointClickFn;
    this.mouseOverFnAes = layerAes.mouseOverFn ? layerAes.mouseOverFn : parentAes.mouseOverFn;
    this.mouseOutFnAes = layerAes.mouseOutFn ? layerAes.mouseOutFn : parentAes.mouseOutFn;

    renderer.renderDataspaceBoxPlotGeom(data, this);
    return true;
};