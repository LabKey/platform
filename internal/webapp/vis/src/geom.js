/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
    this.typeSubtypeAes = null;
    this.parentNameAes = null;
    this.childNameAes = null;
    this.xScale = null;
    this.yScale = null;
    this.colorAes = null;
    this.colorScale = null;
    return this;
};
LABKEY.vis.Geom.XY.prototype.initAesthetics = function(scales, layerAes, parentAes, layerName, index){
    this.layerName = layerName;
    this.index = index;

    if(layerAes.x){
        this.xAes = layerAes.x;
        this.xScale = scales.x;
    } else if(layerAes.xTop){
        this.xAes = layerAes.xTop;
        this.xScale = scales.xTop;
    }  else if(parentAes.x){
        this.xAes = parentAes.x;
        this.xScale = scales.x;
    } else if(parentAes.xTop){
        this.xAes = parentAes.xTop;
        this.xScale = scales.xTop;
    }

    if (parentAes.xSub && scales.xSub) {
        this.xSubAes = parentAes.xSub;
        this.xSubScale = scales.xSub;
    }

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

    if (parentAes.typeSubtype) {
        this.typeSubtypeAes = parentAes.typeSubtype;
    }

    if (parentAes.parentName) {
        this.parentNameAes = parentAes.parentName;
    }

    if (parentAes.childName) {
        this.childNameAes = parentAes.childName;
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

LABKEY.vis.Geom.XY.prototype.getXSub = function(row) {
    //Takes a row, returns the scaled x subcategory value.
    return this.getVal(this.xSubScale.scale, this.xSubAes, row, false);
};

LABKEY.vis.Geom.XY.prototype.getX = function(row){
    // Takes a row, returns the scaled x value.
    return this.getVal(this.xScale.scale, this.xAes, row, false);
};

LABKEY.vis.Geom.XY.prototype.getY = function(row){
    // Takes a row, returns the scaled y value.
    return this.getVal(this.yScale.scale, this.yAes, row, true);
};

LABKEY.vis.Geom.XY.prototype.getTypeSubtype = function(row){
    // Takes a row, returns the scaled y value.
    return this.getVal(this.yScale.scale, this.typeSubtypeAes, row, true);
};

LABKEY.vis.Geom.XY.prototype.getParentY = function(row){
    // Takes a row, returns the scaled parent name value.
    return this.getVal(this.yScale.scale, this.parentNameAes, row, true);
};

/**
 * @class The Point geom, used to generate points of data on a plot such as points on a line or points in a scatter plot.
 *      This geom supports the use of size, color, shape, hoverText, and pointClickFn aesthetics from the
 *      {@link LABKEY.vis.Layer} and/or {@link LABKEY.vis.Plot} objects.
 * @param {Object} config An object with the following properties:
 * @param {String} [config.color] (Optional) String used to determine the color of all points. Defaults to black (#000000).
 * @param {Number} [config.size] (Optional) Number used to determine the size of all points.  Defaults to 5.
 * @param {Number} [config.opacity] (Optional) Number between 0 and 1, used to determine the opacity of all points. Useful if
 *      there are overlapping points in the data. Defaults to 1.
 * @param {Boolean} [config.plotNullPoints] (Optional) Used to toggle whether or not a row of data with the value of null will be
 *      plotted. If true null or undefined values will be plotted just outside the axis with data. For example if a
 *      row of data looks like {x: 50, y: null} the point would appear at 50 on the x-axis, and just below the x axis.
 *      If both x and y values are null the point will be drawn to the bottom left of the origin. Defaults to false.
 * @param {String} [config.position] (Optional) String with possible value "jitter". If config.position is "jitter" and the
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
    this.mouseUpFnAes = layerAes.mouseUpFn ? layerAes.mouseUpFn : parentAes.mouseUpFn;
    this.pointIdAttrAes = layerAes.pointIdAttr ? layerAes.pointIdAttr : parentAes.pointIdAttr;

    renderer.renderPointGeom(data, this);
    return true;
};

/**
 * @private
 * @class The Bin geom, used to bin a set of data and generate a plot of binned points. Currently, under development and not for external use. Normally, this is used for scatter x-y based data.
 * @param config
 * @returns {LABKEY.vis.Geom.Bin}
 * @constructor
 */
LABKEY.vis.Geom.Bin = function(config) {
    this.type = "Bin";

    if (!config) {
        config = {};
    }

    this.shape = ('shape' in config && config.shape != null && config.shape != undefined) ? config.shape : 'hex';
    this.colorDomain = ('colorDomain' in config && config.colorDomain != null && config.colorDomain != undefined) ? config.colorDomain : undefined;
    this.colorRange = ('colorRange' in config && config.colorRange != null && config.colorRange != undefined) ? config.colorRange : ["#e6e6e6", "#085D90"]; // lightish-gray -> labkey blue
    this.size = ('size' in config && config.size != null && config.size != undefined) ? config.size : 5;
    this.plotNullPoints = ('plotNullPoints' in config && config.plotNullPoints != null && config.plotNullPoints != undefined) ? config.plotNullPoints : false;

    return this;
};
LABKEY.vis.Geom.Bin.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Bin.prototype.render = function(renderer, grid, scales, data, layerAes, parentAes, name, index){
    if(!this.initAesthetics(scales, layerAes, parentAes, name, index)){
        return false;
    }

    this.mouseOverFnAes = layerAes.mouseOverFn ? layerAes.mouseOverFn : parentAes.mouseOverFn;
    this.mouseOutFnAes = layerAes.mouseOutFn ? layerAes.mouseOutFn : parentAes.mouseOutFn;
    this.mouseUpFnAes = layerAes.mouseUpFn ? layerAes.mouseUpFn : parentAes.mouseUpFn;

    renderer.renderBinGeom(data, this);
    return true;
};

/**
 * @class The path geom, used to draw paths in a plot. In order to get multiple lines for a set of data the user must define an
 * accessor with the name "group" in the config.aes object of the {LABKEY.vis.Plot} or {LABKEY.vis.Layer} object. For
 * example if the data looked like {x: 12, y: 35, name: "Alan"} the config.aes.group accessor could be "Alan", or a
 * function: function(row){return row.name}. Each unique name would get a separate line. The geom also supports color
 * and size aesthetics from the {LABKEY.vis.Plot} and/or {LABKEY.vis.Layer} objects.
 * @param {Object} config An object with the following properties:
 * @param {String} [config.color] (Optional) String used to determine the color of all paths. Defaults to black (#000000).
 * @param {Number} [config.size] (Optional) Number used to determine the size of all paths. Defaults to 3.
 * @param {Number} [config.opacity] (Optional) Number between 0 and 1, used to determine the opacity of all paths.
 *                                   Useful if there are many overlapping paths. Defaults to 1.
 * @param {boolean} [config.dashed] (Optional) True for dashed path, false for solid path. Defaults to false.
 */
LABKEY.vis.Geom.Path = function(config){
    this.type = "Path";
    
    if(!config){
        config = {};
    }
    this.color = ('color' in config && config.color != null && config.color != undefined) ? config.color : '#000000';
    this.size = ('size' in config && config.size != null && config.size != undefined) ? config.size : 3;
    this.opacity = ('opacity' in config && config.opacity != null && config.opacity != undefined) ? config.opacity : 1;
    this.dashed = ('dashed' in config && config.dashed != null && config.dashed != undefined) ? config.dashed : false;

    return this;
};
LABKEY.vis.Geom.Path.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Path.prototype.render = function(renderer, grid, scales, data, layerAes, parentAes, name, index){
    if(!this.initAesthetics(scales, layerAes, parentAes, name, index)){
        return false;
    }

    this.groupAes = layerAes.group ? layerAes.group : parentAes.group;
    this.sortFnAes = layerAes.sortFn ? layerAes.sortFn : parentAes.sortFn;
    this.sizeAes = layerAes.size ? layerAes.size : parentAes.size;
    this.pathColorAes = layerAes.pathColor ? layerAes.pathColor : parentAes.pathColor;
    this.sizeScale = scales.size;

    renderer.renderPathGeom(data, this);
    return true;
};

/**
 * @class Control range geom. Generally used in conjunction with a {@link LABKEY.vis.Geom.Point} and/or {@link LABKEY.vis.Geom.Path}
 * geom to show upper and lower control range for a given point. In order to work the user must specify an upper or lower accessor
 * in the config.aes object of the {LABKEY.vis.Plot} or {LABKEY.vis.Layer} object. This Geom also supports the color
 * aesthetic from the {LABKEY.vis.Plot} and/or {LABKEY.vis.Layer} objects.
 * @param config An object with the following properties:
 * @param {String} [config.color] (Optional) String used to determine the color of all paths. Defaults to black (#000000).
 * @param {Number} [config.size] (Optional) Number used to determine the size of all paths.  Defaults to 2.
 * @param {Boolean} [config.dashed] (Optional) Whether or not to use dashed lines for path. Defaults to false.
 * @param {Number} [config.width] (Optional) Number used to determine the length of all paths.  Defaults to 6.
 */
LABKEY.vis.Geom.ControlRange = function(config){
    this.type = "ControlRange";

    if(!config){
        config = {};
    }
    this.color = ('color' in config && config.color != null && config.color != undefined) ? config.color : '#000000';
    this.size = ('size' in config && config.size != null && config.size != undefined) ? config.size : 2;
    this.dashed = ('dashed' in config && config.dashed != null && config.dashed != undefined) ? config.dashed : false;
    this.width = ('width' in config && config.width != null && config.width != undefined) ? config.width : 6;

    return this;
};
LABKEY.vis.Geom.ControlRange.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.ControlRange.prototype.render = function(renderer, grid, scales, data, layerAes, parentAes, name, index){
    if(!this.initAesthetics(scales, layerAes, parentAes, name, index)){
        return false;
    }

    this.upperAes = layerAes.upper ? layerAes.upper : parentAes.upper;
    this.lowerAes = layerAes.lower ? layerAes.lower : parentAes.lower;

    if (!this.upperAes || !this.lowerAes) {
        console.error("The upperAes or lowerAes aesthetic is required for the ControlRange geom.");
        return false;
    }

    renderer.renderControlRangeGeom(data, this);
    return true;
};

/**
 * @class Error bar geom. Generally used in conjunction with a {@link LABKEY.vis.Geom.Point} and/or {@link LABKEY.vis.Geom.Path}
 * geom to show the known amount of error for a given point. In order to work the user must specify an error accessor
 * in the config.aes object of the {LABKEY.vis.Plot} or {LABKEY.vis.Layer} object. This Geom also supports the color
 * aesthetic from the {LABKEY.vis.Plot} and/or {LABKEY.vis.Layer} objects.
 * @param config An object with the following properties:
 * @param {String} [config.color] (Optional) String used to determine the color of all paths. Defaults to black (#000000).
 * @param {Number} [config.size] (Optional) Number used to determine the size of all paths.  Defaults to 2.
 * @param {Boolean} [config.dashed] (Optional) Whether or not to use dashed lines for top and bottom bars. Defaults to false.
 * @param {String} [config.altColor] (Optional) String used to determine the color of the vertical bar. Defaults to config.color.
 */
LABKEY.vis.Geom.ErrorBar = function(config){
    this.type = "ErrorBar";

    if(!config){
        config = {};
    }
    this.color = ('color' in config && config.color != null && config.color != undefined) ? config.color : '#000000';
    this.size = ('size' in config && config.size != null && config.size != undefined) ? config.size : 2;
    this.dashed = ('dashed' in config && config.dashed != null && config.dashed != undefined) ? config.dashed : false;
    this.altColor = ('altColor' in config && config.altColor != null && config.altColor != undefined) ? config.altColor : null;
    this.width = ('width' in config && config.width != null && config.width != undefined) ? config.width : 6;

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
 * @param {String} [config.color] (Optional) A string value used for the line colors in the box plot. Defaults to black
 *      (#000000)
 * @param {String} [config.fill] (Optional) A string value used for the fill color in the box plot. Defaults to white
 *      (#ffffff)
 * @param {Number} [config.lineWidth] (Optional) A used to set the width of the lines used in the box plot. Defaults to 1.
 * @param {Number} [config.opacity] (Optional) A number between 0 and 1 used to set the opacity of the box plot. Defaults
 *      to 1.
 * @param {String} [config.position] (Optional) A string used to determine how to position the outliers. Currently the
 *      only possible value is "jitter", which will move the points to the left or right of the center of the box plot by
 *      a random amount. Defaults to undefined.
 * @param {Boolean} [config.showOutliers] (Optional) Used to toggle whether or not outliers are rendered. Defaults to true.
 * @param {String} [config.outlierFill] (Optional) A string value used to set the fill color of the outliers. Defaults
 *      to black (#000000).
 * @param {Number} [config.outlierOpacity] (Optional) A number between 0 and 1 used to set the opacity of the outliers.
 *      Defaults to 1.
 * @param {Number} [config.outlierSize] (Optional) A used to set the size of outliers. Defaults to 3.
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
    this.pointOpacity = ('outlierOpacity' in config && config.outlierOpacity != null && config.outlierOpacity != undefined) ? config.outlierOpacity : .5;
    this.pointSize = ('outlierSize' in config && config.outlierSize != null && config.outlierSize != undefined) ? config.outlierSize : 3;
    this.size = ('binSize' in config && config.binSize != null && config.binSize != undefined) ? config.binSize : 5;

    // binning geom specific
    this.binRowLimit = ('binRowLimit' in config && config.binRowLimit != null && config.binRowLimit != undefined) ? config.binRowLimit : 5000;
    this.colorRange = ('colorRange' in config && config.colorRange != null && config.colorRange != undefined) ? config.colorRange : ["#e6e6e6", "#000000"]; // lightish-gray -> black

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
    this.mouseOverFnAes = layerAes.mouseOverFn ? layerAes.mouseOverFn : parentAes.mouseOverFn;
    this.mouseOutFnAes = layerAes.mouseOutFn ? layerAes.mouseOutFn : parentAes.mouseOutFn;
    this.mouseUpFnAes = layerAes.mouseUpFn ? layerAes.mouseUpFn : parentAes.mouseUpFn;
    this.boxMouseOverFnAes = layerAes.boxMouseOverFn ? layerAes.boxMouseOverFn : parentAes.boxMouseOverFn;
    this.boxMouseOutFnAes = layerAes.boxMouseOutFn ? layerAes.boxMouseOutFn : parentAes.boxMouseOutFn;
    this.boxMouseUpFnAes = layerAes.boxMouseUpFn ? layerAes.boxMouseUpFn : parentAes.boxMouseUpFn;

    renderer.renderDataspaceBoxPlotGeom(data, this);
    return true;
};

/**
 * @class Bar plot geom, used to generate bar plots for a given set of data.
 * @param config An object with the following properties:
 * @param {Function} [config.clickFn] (Optional) A click function
 * @param {Function} [config.hoverFn] (Optional) A hover function
 * @param {String} [config.color] (Optional) A string value used for the line colors in the bar plot. Defaults to black (#000000)
 * @param {String} [config.fill] (Optional) A string value used for the fill color in the bar plot. Defaults to white (#ffffff)
 * @param {Number} [config.lineWidth] (Optional) A used to set the width of the lines used in the bar plot. Defaults to 1.
 * @param {Number} [config.opacity] (Optional) A number between 0 and 1 used to set the opacity of the bar plot. Defaults to 1.
 * @param {Boolean} [config.showCumulativeTotals] (Optional) True to show cumulative totals next to the individual bars.
 * @param {String} [config.colorTotal] (Optional) A string value used for the line colors in the cumulative bar plot. Defaults to black (#000000)
 * @param {String} [config.fillTotal] (Optional) A string value used for the fill color in the cumulative bar plot. Defaults to black (#000000)
 * @param {Number} [config.lineWidthTotal] (Optional) A used to set the width of the lines used in the cumulative bar plot. Defaults to 1.
 * @param {Number} [config.opacityTotal] (Optional) A number between 0 and 1 used to set the opacity of the cumulative bar plot. Defaults to 1.
 * @param {Boolean} [config.showValues] (Optional) True to show the bar height values as text above the rendered bar.
 */
LABKEY.vis.Geom.BarPlot = function(config){
    this.type = "Barplot";

    if(!config){
        config = {};
    }
    this.clickFn = ('clickFn' in config && config.clickFn != null && config.clickFn != undefined) ? config.clickFn : undefined;
    this.hoverFn = ('hoverFn' in config && config.hoverFn != null && config.hoverFn != undefined) ? config.hoverFn : undefined;
    this.color = ('color' in config && config.color != null && config.color != undefined) ? config.color : '#000000';
    this.colorTotal = ('colorTotal' in config && config.colorTotal != null && config.colorTotal != undefined) ? config.colorTotal : '#000000';
    this.fill = ('fill' in config && config.fill != null && config.fill != undefined) ? config.fill : '#c0c0c0';
    this.fillTotal = ('fillTotal' in config && config.fillTotal != null && config.fillTotal != undefined) ? config.fillTotal : '#000000';
    this.lineWidth = ('lineWidth' in config && config.lineWidth != null && config.lineWidth != undefined) ? config.lineWidth : 1;
    this.lineWidthTotal = ('lineWidthTotal' in config && config.lineWidthTotal != null && config.lineWidthTotal != undefined) ? config.lineWidthTotal : 1;
    this.opacity = ('opacity' in config && config.opacity != null && config.opacity != undefined) ? config.opacity : 1;
    this.opacityTotal = ('opacityTotal' in config && config.opacityTotal != null && config.opacityTotal != undefined) ? config.opacityTotal : 1;
    this.showCumulativeTotals = ('showCumulativeTotals' in config && config.showCumulativeTotals != null && config.showCumulativeTotals != undefined) ? config.showCumulativeTotals : false;
    this.showValues = ('showValues' in config && config.showValues != null && config.showValues != undefined) ? config.showValues : false;

    return this;
};
LABKEY.vis.Geom.BarPlot.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.BarPlot.prototype.render = function(renderer, grid, scales, data, layerAes, parentAes, name, index){

    if(!this.initAesthetics(scales, layerAes, parentAes, name, index)){
        return false;
    }

    renderer.renderBarPlotGeom(data, this);
    return true;
};

/**
 * @class Timeline plot geom, used to generate a timeline plot for a given set of data.
 * @param config An object with the following properties:
 * @param {String} [config.size] (Optional) A numeric value used for the timeline event icon size in pixels. Defaults to 10.
 * @param {String} [config.color] (Optional) A string value used for the timeline event icon border color. Defaults to black (#000000).
 * @param {String} [config.fill] (Optional) A string value used for the timeline event icon fill color. Defaults to black (#000000).
 * @param {String} [config.dateKey] (Optional) property name of the date value that data objects contain. Used to create
 *                  tooltips on hover. Defaults to 'date'.
 * @param {Boolean} [config.isCollapsed] (Optional) If true, the timeline collapses subtypes into their parent rows. Defaults to True.
 * @param {Number} [config.rowHeight] (Optional) The height of individual rows in pixels. For expanded timelines, row height
 *                  will resize to 75% of this value. Defaults to 40px.
 * @param {Object} [config.highlight] (Optional) Special Data object containing information to highlight a specific row
 *                  in the timeline. Must have the same shape & properties as all other input data.
 * @param {String} [config.highlightRowColor] (Optional) Hex color to specify what color the highlighted row will be if,
 *                  found in the data. Defaults to #74B0C4.
 * @param {String} [config.activeEventKey] (Optional) Name of property that is paired with @param config.activeEventIdentifier to
 *                  identify a unique event in the data.
 * @param {String} [config.activeEventIdentifier] (Optional) Name of value that is paired with @param config.activeEventKey
 *                  to identify a unique event in the data.
 * @param {String} [config.activeEventStrokeColor] (Optional) Hex color to specify what color the active event rect's
 *                  stroke will be, if found in the data. Defaults to red.
 * @param {Object} [config.emphasisEvents] (Optional) Object containing key:[value] pairs whose keys are property names
 *                  of a data object and whose value is an array of possible values that should have a highlight line drawn
 *                  on the chart when found. Example: {'type': ['death', 'Withdrawal']}
 * @param {String} [config.tickColor] (Optional) Hex color to specify the color of Axis ticks. Defaults to #DDDDDD.
 * @param {String} [config.emphasisTickColor] (Optional) Hex color to specify the color of emphasis event ticks, if
 *                  found in the data. Defaults to #1a969d.
 * @param {String} [config.timeUnit] (Optional) Unit of time to use when calculating how far an event's date is from
 *                  the start date. Default is years. Valid string values include minutes, hours, days, years, and decades.
 * @param {Number} [config.eventIconSize] (Optional) Size of event square width/height dimensions.
 * @param {String} [config.eventIconColor] (Optional) Hex color of event square stroke.
 * @param {String} [config.eventIconFill] (Optional) Hex color of event square inner fill. Defaults to black (#000000).
 * @param {Number} [config.eventIconOpacity] (Optional) Float between 0 - 1 (inclusive) to specify how transparent the
 *                  fill of event icons will be. Defaults to 1.
 * @param {Array} [config.rowColorDomain] (Optional) Array of length 2 containing string Hex values for the two
 *                  alternating colors of timeline row rectangles. Defaults to ['#f2f2f2', '#ffffff'].
 */
LABKEY.vis.Geom.TimelinePlot = function(config){
    this.type = "TimelinePlot";

    if(!config){
        config = {};
    }

    this.dateKey = ('dateKey' in config && config.dateKey != null && config.dateKey != undefined) ? config.dateKey : 'date';
    this.timeUnit = ('timeUnit' in config && config.timeUnit != null && config.timeUnit != undefined) ? config.timeUnit : 'years';
    this.highlight = ('highlight' in config && config.highlight != null && config.highlight != undefined) ? config.highlight : null;
    this.highlightRowColor = ('highlightRowColor' in config && config.highlightRowColor != null && config.highlightRowColor != undefined) ? config.highlightRowColor : '#74B0C4';
    this.activeEventKey = ('activeEventKey' in config && config.activeEventKey != null && config.activeEventKey != undefined) ? config.activeEventKey : null;
    this.activeEventIdentifier = ('activeEventIdentifier' in config && config.activeEventIdentifier != null && config.activeEventIdentifier != undefined) ? config.activeEventIdentifier : null;
    this.activeEventStrokeColor = ('activeEventStrokeColor' in config && config.activeEventStrokeColor != null && config.activeEventStrokeColor != undefined) ? config.activeEventStrokeColor : 'red';
    this.marginLeft = ('marginLeft' in config && config.marginLeft != null && config.marginLeft != undefined) ? config.marginLeft : 200;
    this.parentName = ('parentName' in config && config.parentName != null && config.parentName != undefined) ? config.parentName : 'type';
    this.childName = ('childName' in config  && config.childName != null && config.childName != undefined) ? config.childName : 'subtype';
    this.width = ('width' in config && config.width != null && config.width != undefined) ? config.width : 900;
    this.height = ('height' in config && config.height != null && config.height != undefined) ? config.height : 500;
    this.rowHeight = ('rowHeight' in config && config.rowHeight != null && config.rowHeight != undefined) ? config.rowHeight : 40;
    this.eventIconSize = ('eventIconSize' in config && config.eventIconSize != null && config.eventIconSize != undefined) ? config.eventIconSize : 10;
    this.eventIconColor = ('eventIconColor' in config && config.eventIconColor != null && config.eventIconColor != undefined) ? config.eventIconColor : '#000000';
    this.eventIconFill = ('eventIconFill' in config && config.eventIconFill != null && config.eventIconFill != undefined) ? config.eventIconFill : '#000000';
    this.rowColorDomain = ('rowColorDomain' in config && config.rowColorDomain != null && config.rowColorDomain != undefined) ? config.rowColorDomain : ['#f2f2f2', '#ffffff'];
    this.eventIconOpacity = ('eventIconOpacity' in config && config.eventIconOpacity != null && config.eventIconOpacity != undefined) ? config.eventIconOpacity : 1;
    this.emphasisEvents = ('emphasisEvents' in config && config.emphasisEvents != null && config.emphasisEvents != undefined) ? config.emphasisEvents : null;
    this.tickColor = ('tickColor' in config && config.tickColor != null && config.tickColor != undefined) ? config.tickColor : '#DDDDDD';
    this.emphasisTickColor = ('emphasisTickColor' in config && config.emphasisTickColor != null && config.emphasisTickColor != undefined) ? config.emphasisTickColor : '#1a969d';
    this.isCollapsed = ('isCollapsed' in config && config.isCollapsed != null && config.isCollapsed != undefined) ? config.isCollapsed : true;
    return this;
};
LABKEY.vis.Geom.TimelinePlot.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.TimelinePlot.prototype.render = function(renderer, grid, scales, data, layerAes, parentAes, name, index){

    if(!this.initAesthetics(scales, layerAes, parentAes, name, index)){
        return false;
    }

    this.mouseOverRowFnAes = layerAes.mouseOverRowFn ? layerAes.mouseOverRowFn : parentAes.mouseOverRowFn;
    this.mouseOutRowFnAes = layerAes.mouseOutRowFn ? layerAes.mouseOutRowFn : parentAes.mouseOutRowFn;
    this.rowClickFnAes = layerAes.rowClickFn ? layerAes.rowClickFn : parentAes.rowClickFn;
    this.eventClickFnAes = layerAes.eventIconClickFn ? layerAes.eventIconClickFn : parentAes.eventIconClickFn;
    this.mouseOverFnAes = layerAes.mouseOverEventIconFn ? layerAes.mouseOverEventIconFn : parentAes.mouseOverEventIconFn;
    this.mouseOutFnAes = layerAes.mouseOutEventIconFn ? layerAes.mouseOutEventIconFn : parentAes.mouseOutEventIconFn;

    if (renderer.renderTimelinePlotGeom)
    {
        renderer.renderTimelinePlotGeom(data, this);
        return true;
    }
    else
    {
        return false;
    }
};