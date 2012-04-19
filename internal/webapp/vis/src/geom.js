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
LABKEY.vis.Geom.XY.prototype.initAesthetics = function(layerAes, parentAes){
    this.xMap = layerAes.x ? layerAes.x : parentAes.x;
    if(!this.xMap){
        console.error('x aesthetic is required for ' + this.type + ' geom to render.');
        return false;
    }

    if(layerAes.left){
        this.yMap = layerAes.left;
    } else if(layerAes.right){
        this.yMap = layerAes.right;
    } else if(parentAes.left){
        this.yMap = parentAes.left;
    } else if(parentAes.right){
        this.yMap = parentAes.right;
    }

    if(!this.yMap){
        console.error('y aesthetic is required for ' + this.type + ' geom to render.');
        return false;
    }

    if(layerAes.color){
        this.colorMap = layerAes.color;
    } else if(parentAes.color){
        this.colorMap = parentAes.color;
    }

    return true;
};

LABKEY.vis.Geom.Point = function(){
    this.type = "Point";
    return this;
};
LABKEY.vis.Geom.Point.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Point.prototype.render = function(paper, grid, data, layerAes, parentAes){
    if(!this.initAesthetics(layerAes, parentAes)){
        return false;
    }

    if(layerAes.pointType){
        this.pointMap = layerAes.pointType;
    } else if(parentAes.pointType){
        this.pointMap = parentAes.pointType;
    }

    paper.setStart();

    for(var i = 0; i < data.length; i++){
        var x = null;
        var y = null;
        var color = null;
        var pointTypeFunction = null;

        if(typeof this.xMap.value === "function"){
            x = this.xMap.scale(this.xMap.value(data[i]));
        } else {
            x = this.xMap.scale(data[i][this.xMap.value]);
        }

        if(typeof this.yMap.value === "function"){
            y = this.yMap.scale(this.yMap.value(data[i]));
        } else {
            y = this.yMap.scale(data[i][this.yMap.value]);
        }

        if(this.colorMap){
            if(typeof this.colorMap.value === 'function'){
                color = this.colorMap.scale(this.colorMap.value(data[i]));
            } else {
                color = this.colorMap.scale(data[i][this.colorMap.value]);
            }
        } else {
            color = "#000000";
        }

        if(this.pointMap){
            if(typeof this.pointMap.scale === 'function'){
                pointTypeFunction = this.pointMap.scale(this.pointMap.value(data[i]));
            } else {
                pointTypeFunction = this.pointMap.scale(data[i][this.pointMap.value]);
            }
        } else {
            pointTypeFunction = function(paper, x, y, r){return paper.circle(x, y, r)};
        }

        var point = pointTypeFunction(paper, x, -y, 5).attr('fill', color).attr('stroke', color);

        if(layerAes.hoverText){
            point.attr('title', layerAes.hoverText.value(data[i]));
        }
    }

    paper.setFinish().transform("t0," + grid.height);

    return true;
};

LABKEY.vis.Geom.Path = function(){
    this.type = "Path";
    return this;
};
LABKEY.vis.Geom.Path.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Path.prototype.render = function(paper, grid, data, layerAes, parentAes){
    if(!this.initAesthetics(layerAes, parentAes)){
        return false;
    }

    this.group = layerAes.group ? layerAes.group : parentAes.group;
    var pathScope = this;
    var line = d3.svg.line().x(function(d){
        if(typeof pathScope.xMap.value === 'function'){
            return pathScope.xMap.scale(pathScope.xMap.value(d));
        } else {
            return d[pathScope.xMap.value];
        }

    }).y(function(d){
        if(typeof pathScope.yMap.value === 'function'){
            return -pathScope.yMap.scale(pathScope.yMap.value(d));
        } else {
            return -pathScope.yMap.scale(d[pathScope.yMap.value]);
        }
    });

    paper.setStart();

    if(this.group){
        //Split data into groups so we can render 1 path per group.
        var groups = {};
        for(var i = 0; i < data.length; i++){
            // Split the data into groups.
            var value = null;
            if(typeof this.group.value === 'function'){
                value = this.group.value(data[i]);
            } else {
                value = data[i][this.group.value];
            }

            if(!groups[value]){
                groups[value] = [];
            }
            groups[value].push(data[i]);

        }

        for(groupTitle in groups){
            var color = "#000000";
            if(this.colorMap){
                color = this.colorMap.scale(groupTitle);
            }
            var groupData = groups[groupTitle];
            paper.path(line(groupData)).attr('stroke', color).transform("t0," + grid.height);
        }

    } else {
        // No groups, connect all the points.
        paper.path(line(data)).attr('shape-rendering', 'crispEdges').transform("t0," + grid.height);
    }

    paper.setFinish().transform("t0," + grid.height);

    return true;
};

LABKEY.vis.Geom.ErrorBar = function(){
    this.type = "ErrorBar";
    return this;
};
LABKEY.vis.Geom.ErrorBar.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.ErrorBar.prototype.render = function(paper, grid, data, layerAes, parentAes){
    if(!this.initAesthetics(layerAes, parentAes)){
        return false;
    }

    this.errorMap = layerAes.error ? layerAes.error : parentAes.error;
    if(!this.errorMap){
        console.error("The error aesthetic is required for the ErrorBar geom.");
        return false;
    }

    paper.setStart();

    for(var i = 0; i < data.length; i++){
        var x = null;
        var y = null;
        var yTop = null;
        var yBottom= null;
        var color = null;
        var errorAtPoint = null;

        if(typeof this.xMap.value === "function"){
            x = this.xMap.scale(this.xMap.value(data[i]));
        } else {
            x = this.xMap.scale(data[i][this.xMap.value]);
        }

        if(typeof this.errorMap.value === "function"){
            errorAtPoint = this.errorMap.value(data[i]);
        } else {
            errorAtPoint = data[i][this.errorMap.value];
        }

        if(typeof this.yMap.value === "function"){
            y = Math.floor(-this.yMap.scale(this.yMap.value(data[i]))) +.5;
            yTop = Math.floor(-this.yMap.scale(this.yMap.value(data[i]) + errorAtPoint)) +.5;
            yBottom = Math.floor(-this.yMap.scale(this.yMap.value(data[i]) - errorAtPoint)) +.5;
        } else {
            y = -this.yMap.scale(data[i][this.yMap.value]);
            yTop = -this.yMap.scale(data[i][this.yMap.value] + errorAtPoint);
            yBottom = -this.yMap.scale(data[i][this.yMap.value] - errorAtPoint);
        }

        if(this.colorMap){
            if(typeof this.colorMap.value === 'function'){
                color = this.colorMap.scale(this.colorMap.value(data[i]));
            } else {
                color = this.colorMap.scale(data[i][this.colorMap.value]);
            }
        } else {
            color = "#000000";
        }

        var errorBarPath = LABKEY.vis.makeLine(x - 6, yTop, x+6, yTop) + LABKEY.vis.makeLine(x, yTop, x, yBottom) + LABKEY.vis.makeLine(x-6, yBottom, x+6, yBottom); //top bar, middle bar, bottom bar
        var errorBar = paper.path(errorBarPath).attr('stroke-width', 2);

        if(color){
            errorBar.attr('stroke', color).attr('fill', color);
        }
    }

    paper.setFinish().transform("t0," + grid.height);
    return true;
};

LABKEY.vis.Geom.Text = function(){
    this.type = "Text";
    return this;
};
LABKEY.vis.Geom.Text.prototype = new LABKEY.vis.Geom.XY();
LABKEY.vis.Geom.Text.prototype.render = function(paper, grid, data, layerAes, parentAes){
    if(!this.initAesthetics(layerAes, parentAes)){
        return false;
    }

    return true;
};

/********** Helper Functions **********/

LABKEY.vis.makeLine = function(x1, y1, x2, y2){
    //Generates a path between two coordinates.
    return "M " + x1 + " " + y1 + " L " + x2 + " " + y2;
};