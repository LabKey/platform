/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/********** Geoms **********/

if(!LABKEY.vis.Geom){
	LABKEY.vis.Geom = {};
}
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

    if(layerAes.size){
        this.sizeMap = layerAes.size;
    } else if(parentAes.size){
        if(typeof parentAes.size.value != 'function'){
            // We only want to accept a size aesthetic from the parent if it isn't a function becuase the point geom
            // and path geoms take different parameters for size functions which could result in failures.
            this.sizeMap = parentAes.size;
        }
    }

    return true;
};

LABKEY.vis.Geom.Point = function(config){
    this.type = "Point";

    if(!config){
        config = {};
    }
    this.color = config.color ? config.color : '#000000';
    this.size = config.size ? config.size : 5;
    this.opacity = config.opacity ? config.opacity : 1;
    this.plotNullPoints = config.plotNullPoints ? config.plotNullPoints : false;

    return this;
};
LABKEY.vis.Geom.Point.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Point.prototype.render = function(paper, grid, scales, data, layerAes, parentAes, name){
    if(!this.initAesthetics(scales, layerAes, parentAes)){
        return false;
    }
    var hoverText = layerAes.hoverText ? layerAes.hoverText : parentAes.hoverText;
    var yScale = this.yMap.side == "left" ? scales.yLeft.scale : scales.yRight.scale

    if(layerAes.shape){
        this.shapeMap = layerAes.shape;
    } else if(parentAes.shape){
        this.shapeMap = parentAes.shape;
    }

    for(var i = 0; i < data.length; i++){
        var xVal = this.xMap.getValue(data[i]);
        var yVal = this.yMap.getValue(data[i]);
        var x, y;
        if(this.plotNullPoints){
             x = (xVal != null && xVal != undefined) ? scales.x.scale(xVal) : grid.leftEdge - 5;
             y = (yVal != null && yVal != undefined) ? yScale(yVal) : grid.bottomEdge - 5;
        } else {
            if(xVal == undefined || xVal == null || (typeof xVal == "number" && isNaN(xVal)) || yVal == undefined || yVal == null || (typeof yVal == "number" && isNaN(yVal))){
                continue;
            } else {
                x = scales.x.scale(xVal);
                y = yScale(yVal);
            }
        }

        var color = this.colorMap ? scales.color.scale(this.colorMap.getValue(data[i]) + ' ' + name) : this.color;
        var size = this.sizeMap ? this.sizeMap.getValue(data[i]) : this.size;
        var shapeFunction = this.shapeMap ? scales.shape.scale(this.shapeMap.getValue(data[i]) + ' ' + name) : function(paper, x, y, r){return paper.circle(x, y, r)};
        var point = shapeFunction(paper, x, -y, size).attr('fill', color).attr('stroke', color).attr('stroke-opacity', this.opacity/2).attr('fill-opacity', this.opacity);

        if(hoverText){
            point.attr('title', hoverText.value(data[i]));
        }
    }

    return true;
};

LABKEY.vis.Geom.Path = function(config){
    this.type = "Path";
    
    if(!config){
        config = {};
    }
    this.color = config.color ? config.color : '#000000';
    this.size = config.size ? config.size : 3;
    this.opacity = config.opacity ? config.opacity : 1;

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
    var xAccessor = function(row){return scales.x.scale(pathScope.xMap.getValue(row));};
    var yAccessor = function(row){return -yScale(pathScope.yMap.getValue(row));};

    if(this.group){
        //Split data into groupedData so we can render 1 path per group.
        var groupedData = LABKEY.vis.groupData(data, this.group.getValue);

        for(var group in groupedData){
            var groupData = groupedData[group];
            var color = this.color;

            if(this.colorMap && this.colorMap.name == this.group.name){
                // If we have a colorMap and it maps to the same thing as groupedData, then we pass in the group to get the desired color.
                color = scales.color.scale(group + ' ' + name);
            }

            if(this.sizeMap){
                size = this.sizeMap.getValue(groupData);
            }

            paper.path(LABKEY.vis.makePath(groupData, xAccessor, yAccessor)).attr('stroke', color).attr('stroke-width', size).attr('opacity', this.opacity);
        }
    } else {
        // No groups, connect all the points.
        if(this.sizeMap){
            size = this.sizeMap.getValue(data); // This would allow a user to look at all of the rows in a group and calculate size (i.e. a user could average the CD4 in every group and make a line based on that average.)
        }
        paper.path(LABKEY.vis.makePath(data, xAccessor, yAccessor)).attr('stroke-width', size).attr('opacity', this.opacity).attr('stroke', this.color);
    }

    return true;
};

LABKEY.vis.Geom.ErrorBar = function(config){
    this.type = "ErrorBar";

    if(!config){
        config = {};
    }
    this.color = config.color ? config.color : '#000000';
    this.size = config.size ? config.size : 2;

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
        var color = this.colorMap ? scales.color.scale(this.colorMap.getValue(data[i]) + ' ' + name) : this.color;

        var errorBarPath = LABKEY.vis.makeLine(x - 6, yTop, x+6, yTop) + LABKEY.vis.makeLine(x, yTop, x, yBottom) + LABKEY.vis.makeLine(x-6, yBottom, x+6, yBottom); //top bar, middle bar, bottom bar
        var errorBar = paper.path(errorBarPath).attr('stroke-width', this.size).attr('stroke', color).attr('fill', color);
    }

    return true;
};

LABKEY.vis.Geom.Boxplot = function(config){
    this.type = "Boxplot";

    if(!config){
        config = {};
    }
    this.color = config.color ? config.color : '#000000'; // line color
    this.fill = config.fill ? config.fill : '#ffffff'; // fill color
    this.lineWidth = config.lineWidth ? config.lineWidth : 1;
    this.showOutliers = config.showOutliers ? config.showOutliers : true;
    this.outlierSize = config.outlierSize ? config.outlierSize : 3;
    this.outlierOpacity = config.outlierOpacity ? config.outlierOpacity : .5;
    this.opacity = config.opacity ? config.opacity : 1;
    this.outlierFill = config.outlierFill ? config.outlierFill : '#000000';
    this.position = config.position ? config.position : null;

    return this;
};
LABKEY.vis.Geom.Boxplot.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Boxplot.prototype.render = function(paper, grid, scales, data, layerAes, parentAes){
    if(!this.initAesthetics(scales, layerAes, parentAes)){
        return false;
    }

    var yScale = this.yMap.side == "left" ? scales.yLeft.scale : scales.yRight.scale;
    var hoverText = layerAes.hoverText ? layerAes.hoverText : parentAes.hoverText;
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
    
    var plotOutlier = function(x, y, outlierHoverText){
        var outlier = paper.circle(x, -yScale(y), this.outlierSize)
                .attr('fill', this.outlierFill)
                .attr('stroke', 'none')
                .attr('fill-opacity', this.outlierOpacity);
        if(outlierHoverText){
            outlier.attr('title', outlierHoverText.value(group, stats));
        }
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

        var width = binWidth * .5;

        var x = scales.x.scale(group) - width/2;
        var bottom = Math.floor(-yScale(stats.Q1)) + .5;
        var top = Math.floor(-yScale(stats.Q3)) + .5;
        var middleY = Math.floor(-yScale(stats.Q2)) + .5;

        var middleX = Math.floor(x+(width/2)) + .5;
        var whiskerLeft = middleX - (width / 4);
        var whiskerRight = middleX + (width / 4);
        var smallestNotOutlier = stats.Q1 - (1.5 * stats.IQR);
        var biggestNotOutlier = stats.Q3 + (1.5 * stats.IQR);
        
        var i = 0;
        while(stats.sortedValues[i] < smallestNotOutlier){
            i++;
            if(this.showOutliers){
                if(this.position == 'jitter'){
                    plotOutlier.call(this, x+(Math.random()*(width)), stats.sortedValues[i]);
                } else {
                    plotOutlier.call(this, middleX, stats.sortedValues[i]);
                }
            }
        }
        var bottomWhisker = Math.floor(-yScale(stats.sortedValues[i])) + .5;

        i = stats.sortedValues.length - 1;
        while(stats.sortedValues[i] > biggestNotOutlier){
            i--;
            if(this.showOutliers){
                if(this.position == 'jitter'){
                    plotOutlier.call(this, x+(Math.random()*(width)), stats.sortedValues[i]);
                } else {
                    plotOutlier.call(this, middleX, stats.sortedValues[i]);
                }
            }
        }
        var topWhisker = Math.floor(-yScale(stats.sortedValues[i])) + .5;
        
        plotBox.call(this, x, width, top, bottom, middleY, topWhisker, bottomWhisker, hoverText);
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
