/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if(!LABKEY){
	var LABKEY = {};
}

if(!LABKEY.vis){
	LABKEY.vis = {};
}

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

LABKEY.vis.Geom.Point = function(){
    this.type = "Point";
    return this;
};
LABKEY.vis.Geom.Point.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Point.prototype.render = function(paper, grid, scales, data, layerAes, parentAes, name){
    if(!this.initAesthetics(scales, layerAes, parentAes)){
        return false;
    }
    var yScale = this.yMap.side == "left" ? scales.yLeft.scale : scales.yRight.scale

    if(layerAes.pointType){
        this.pointMap = layerAes.pointType;
    } else if(parentAes.pointType){
        this.pointMap = parentAes.pointType;
    }

    for(var i = 0; i < data.length; i++){
        var x = scales.x.scale(this.xMap.getValue(data[i]));
        var y = yScale(this.yMap.getValue(data[i]));
        var color = this.colorMap ? scales.color.scale(this.colorMap.getValue(data[i]) + ' ' + name) : "#000000";
        var size = this.sizeMap ? this.sizeMap.getValue(data[i]) : 5;
        var pointTypeFunction = this.pointMap ? scales.pointType.scale(this.pointMap.getValue(data[i]) + ' ' + name) : function(paper, x, y, r){return paper.circle(x, y, r)};
        var point = pointTypeFunction(paper, x, -y, size).attr('fill', color).attr('stroke', color);

        if(layerAes.hoverText){
            point.attr('title', layerAes.hoverText.value(data[i]));
        }
    }

    return true;
};

LABKEY.vis.Geom.Path = function(){
    this.type = "Path";
    return this;
};
LABKEY.vis.Geom.Path.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Path.prototype.render = function(paper, grid, scales, data, layerAes, parentAes, name){
    if(!this.initAesthetics(scales, layerAes, parentAes)){
        return false;
    }
    var yScale = this.yMap.side == "left" ? scales.yLeft.scale : scales.yRight.scale;

    this.group = layerAes.group ? layerAes.group : parentAes.group;
    var size = 3;
    var pathScope = this;
    var line = d3.svg.line().x(function(d){
        return scales.x.scale(pathScope.xMap.getValue(d));
    }).y(function(d){
        return -yScale(pathScope.yMap.getValue(d));
    });

    if(this.group){
        //Split data into groups so we can render 1 path per group.
        var groups = {};
        for(var i = 0; i < data.length; i++){
            // Split the data into groups.
            var value = this.group.getValue(data[i]);

            if(!groups[value]){
                groups[value] = [];
            }
            groups[value].push(data[i]);
        }

        for(var groupTitle in groups){
            var groupData = groups[groupTitle];
            var color = "#000000";

            if(this.colorMap && this.colorMap.name == this.group.name){
                // If we have a colorMap and it maps to the same thing as groups, then we pass in the groupTitle to get the desired color.
                color = scales.color.scale(groupTitle + ' ' + name);
            }

            if(this.sizeMap){
                size = this.sizeMap.getValue(groupData);
            }

            paper.path(line(groupData)).attr('stroke', color).attr('stroke-width', size).attr('opacity', .6);
        }

    } else {
        // No groups, connect all the points.
        if(this.sizeMap){
            size = this.sizeMap.getValue(data); // This would allow a user to look at all of the rows in a group and calculate size (i.e. a user could average the CD4 in every group and make a line based on that average.)
        }
        paper.path(line(data)).attr('stroke-width', size).attr('opacity', .6);
    }

    return true;
};

LABKEY.vis.Geom.ErrorBar = function(){
    this.type = "ErrorBar";
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
        var color = this.colorMap ? scales.color.scale(this.colorMap.getValue(data[i]) + ' ' + name) : "#000000";

        yTop = Math.max(yTop, -grid.topEdge);
        yBottom = Math.min(yBottom, -grid.bottomEdge);

        var errorBarPath = LABKEY.vis.makeLine(x - 6, yTop, x+6, yTop) + LABKEY.vis.makeLine(x, yTop, x, yBottom) + LABKEY.vis.makeLine(x-6, yBottom, x+6, yBottom); //top bar, middle bar, bottom bar
        var errorBar = paper.path(errorBarPath).attr('stroke-width', 2);

        if(color){
            errorBar.attr('stroke', color).attr('fill', color);
        }
    }

    return true;
};

LABKEY.vis.Geom.Text = function(){
    this.type = "Text";
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