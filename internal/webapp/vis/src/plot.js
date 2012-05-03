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

LABKEY.vis.Plot = function(config){
    var copyUserScales = function(origScales){
        // This copies the user's scales, but not the max/min because we don't want to over-write that, so we store the original
        // scales separately (this.originalScales).
        var scales = {};
        for(scale in origScales){
            scales[scale] = {};
            scales[scale].scaleType = origScales[scale].scaleType ? origScales[scale].scaleType : null;
            scales[scale].trans = origScales[scale].trans ? origScales[scale].trans : null;
            scales[scale].tickFormat = origScales[scale].tickFormat ? origScales[scale].tickFormat : null;
        }
        return scales;
    };
	this.renderTo = config.renderTo ? config.renderTo : null; // The id of the DOM element to render the plot to, required.
	this.grid = {
		width: config.width ? config.width : null, // height of the grid where points/lines/etc gets plotted.
		height: config.height ? config.height: null // widht of the grid.
	};
	this.originalScales = config.scales ? config.scales : null;
    this.scales = copyUserScales(this.originalScales);
	this.originalAes = config.aes ? config.aes : null;
    this.aes = LABKEY.vis.convertAes(this.originalAes);
    this.xTitle = config.xTitle ? config.xTitle : null;
    this.leftTitle = config.leftTitle ? config.leftTitle : null;
    this.rightTitle = config.rightTitle ? config.rightTitle : null;
    this.mainTitle = config.mainTitle ? config.mainTitle : null;
	this.data = config.data ? config.data : null; // An array of rows, required. Each row could have several pieces of data. (e.g. {subjectId: '249534596', hemoglobin: '350', CD4:'1400', day:'120'})
	this.layers = config.layers ? config.layers : []; // An array of layers, required. (e.g. a layer for a CD4 line chart over time, and a layer for a Hemoglobin line chart over time).

    if(this.grid.width == null){
		console.error("Unable to create plot, width not specified");
		return null;
	}

	if(this.grid.height == null){
		console.error("Unable to create plot, height not specified");
		return null;
	}

	if(this.renderTo == null){
		console.error("Unable to create plot, renderTo not specified");
		return null;
	} else {
        this.paper = new Raphael(this.renderTo, this.grid.width, this.grid.height);
    }

    for(var aesthetic in this.aes){
        LABKEY.vis.createGetter(this.aes[aesthetic]);
    }

	var initScales = function(){
        var defaultAxisScale = {
            scaleType: 'continuous',
            trans: 'linear'
        };

        if(!this.scales.x){
            this.scales.x = defaultAxisScale;
        }

        if(!this.scales.yLeft){
            this.scales.yLeft = defaultAxisScale;
        }

        if(!this.scales.yRight){
            this.scales.yRight = defaultAxisScale;
        }

		var leftMargin = 75;
		var rightMargin = 250;
		var bottomMargin = 50;
		var topMargin = 75;

		this.grid.leftEdge = leftMargin;
		this.grid.rightEdge = this.grid.width - rightMargin + 10; // Add 10 units of space to top and right of scale as a little padding for the grid area.
		this.grid.topEdge = this.grid.height - topMargin + 10;
		this.grid.bottomEdge = bottomMargin;

        var getMinMax = function(origScales, scales, parentData, layerData, aes){
            var data = layerData ? layerData : parentData;
            if(!data){
                return;
            }

            for(var scale in scales){
                if(aes[scale]){
                    if(scales[scale].scaleType == 'continuous'){
                        if(origScales[scale] && origScales[scale].min){
                            scales[scale].min = origScales[scale].min;
                        } else {
                            var tempMin = d3.min(data, aes[scale].value);
                            if(scales[scale].min == null || scales[scale].min == undefined || tempMin < scales[scale].min){
                                scales[scale].min = tempMin;
                            }
                        }

                        if(origScales[scale] && origScales[scale].max){
                            scales[scale].max = origScales[scale].max;
                        } else {
                            var tempMax = d3.max(data, aes[scale].value);
                            if(scales[scale].max == null || scales[scale].max == undefined || tempMax > scales[scale].max){
                                scales[scale].max = tempMax;
                            }
                        }
                    }
                }
            }
        };

        getMinMax(this.originalScales, this.scales, this.data, null, this.aes);

        for(var i = 0; i < this.layers.length; i++){
            getMinMax(this.originalScales, this.scales, this.data, this.layers[i].data, this.layers[i].aes);
        }

        for(var scaleName in this.scales){
            var domain = null;
            var range = null;
            var scale = this.scales[scaleName];
            if(scaleName == 'x'){
                if(scale.scaleType == 'continuous'){
                    domain = [scale.min, scale.max];
                    range = [leftMargin, this.grid.width - rightMargin];
                    scale.scale = new LABKEY.vis.Scale.Continuous(scale.trans, null, null, domain, range);
                } else {

                }
            } else if(scaleName == 'yLeft' || scaleName == 'yRight'){
                if(scale.scaleType == 'continuous' && (scale.min != null && scale.min != undefined) && (scale.max != null && scale.max != undefined)){
                    domain = [scale.min, scale.max];
                    range = [bottomMargin, this.grid.height - topMargin];
                    scale.scale = new LABKEY.vis.Scale.Continuous(scale.trans, null, null, domain, range);
                } else {

                }
            } else if(scaleName == 'color'){
                if(!scale.scaleType || scale.scaleType == 'discrete') {
                    scale.scale = LABKEY.vis.Scale.ColorDiscrete();
                }
            } else if(scaleName == 'pointType'){
                if(!scale.scaleType || scale.scaleType == 'discrete') {
                    scale.scale = LABKEY.vis.Scale.PointType();
                }
            }
        }

        if(!this.scales.x.scale){
            console.error("Unable to create an x scale.");
            return false;
        }

        if(!this.scales.yLeft.scale && !this.scales.yRight.scale){
            console.error("Unable to create a y scale");
            return false;
        }

		return true; // Return false if we want to cancel rendering. (i.e. no x scale).
	};

	var initGrid = function(){
        if(this.mainTitle){
            this.paper.text(this.grid.width / 2, 30, this.mainTitle).attr({font: "18px Arial, sans-serif"});
        }

		// Now that we have all the scales situated we need to render the axis lines, tick marks, and titles.
		this.paper.path(LABKEY.vis.makeLine(this.grid.leftEdge, -this.grid.bottomEdge +.5, this.grid.rightEdge, -this.grid.bottomEdge+.5)).attr('stroke', '#000').attr('stroke-width', '1').transform("t0," + this.grid.height);

        if(this.xTitle){
            this.paper.text(this.grid.rightEdge/2, this.grid.height - 10, this.xTitle).attr({font: "14px Arial, sans-serif"});
        }

        var xTicks = this.scales.x.scale.ticks(7);
        for(var i = 0; i < xTicks.length; i++){
            //Plot x-axis ticks.
            var x1 = x2 = Math.floor(this.scales.x.scale(xTicks[i])) +.5;
            var y1 = -this.grid.bottomEdge + 8;
            var y2 = -this.grid.bottomEdge;

            var tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2)).transform("t0," + this.grid.height);
            var tickText = this.scales.x.tickFormat ? this.scales.x.tickFormat(xTicks[i]) : xTicks[i];
            var text = this.paper.text(this.scales.x.scale(xTicks[i])+.5, -this.grid.bottomEdge + 15, tickText).transform("t0," + this.grid.height);

            if(x1 - .5 == this.grid.leftEdge || x1 - .5 == this.grid.rightEdge) continue;

            var gridLine = this.paper.path(LABKEY.vis.makeLine(x1, -this.grid.bottomEdge, x2, -this.grid.topEdge)).attr('stroke', '#DDD').transform("t0," + this.grid.height);
        }

		if(this.scales.yLeft.scale){
            var leftTicks = this.scales.yLeft.scale.ticks(10);
			this.paper.path(LABKEY.vis.makeLine(this.grid.leftEdge +.5, -this.grid.bottomEdge + 1, this.grid.leftEdge+.5, -this.grid.topEdge)).attr('stroke', '#000').attr('stroke-width', '1').transform("t0," + this.grid.height);

            if(this.leftTitle){
                this.paper.text(this.grid.leftEdge - 55, this.grid.height / 2, this.leftTitle).attr({font: "14px Arial, sans-serif"}).transform("t0," + this.h+"r270");
            }

			for(var i = 0; i < leftTicks.length; i++){
				var x1 = this.grid.leftEdge  - 8;
				var y1 = y2 = -Math.floor(this.scales.yLeft.scale(leftTicks[i])) + .5; // Floor it and add .5 to keep the lines sharp.
				var x2 = this.grid.leftEdge;

                if(y1 == -this.grid.bottomEdge + .5) continue; // Dont draw a line on top of the x-axis line.

				var tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2)).transform("t0," + this.grid.height);
                var tickText = this.scales.yLeft.tickFormat ? this.scales.yLeft.tickFormat(leftTicks[i]) : leftTicks[i];
				var gridLine = this.paper.path(LABKEY.vis.makeLine(x2 + 1, y1, this.grid.rightEdge, y2)).attr('stroke', '#DDD').transform("t0," + this.grid.height);
				var text = this.paper.text(x1 - 15, y1, tickText).transform("t0," + this.grid.height);
			}
		}

		if(this.scales.yRight.scale){
            var rightTicks = this.scales.yRight.scale.ticks(10);
            this.paper.path(LABKEY.vis.makeLine(this.grid.rightEdge + .5, -this.grid.bottomEdge + 1, this.grid.rightEdge + .5, -this.grid.topEdge)).attr('stroke', '#000').attr('stroke-width', '1').transform("t0," + this.grid.height);

            if(this.rightTitle){
                this.paper.text(this.grid.rightEdge + 55, this.grid.height / 2, this.rightTitle).attr({font: "14px Arial, sans-serif"}).transform("t0," + this.h+"r90");
            }

			for(var i = 0; i < rightTicks.length; i++){
				var x1 = this.grid.rightEdge;
				var y1 = y2 = -Math.floor(this.scales.yRight.scale(rightTicks[i])) + .5;
				var x2 = this.grid.rightEdge + 8;

                if(y1 == -this.grid.bottomEdge + .5) continue; // Dont draw a line on top of the x-axis line.

				var tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2)).transform("t0," + this.grid.height);
                var tickText = this.scales.yRight.tickFormat ? this.scales.yRight.tickFormat(leftTicks[i]) : rightTicks[i];
				var gridLine = this.paper.path(LABKEY.vis.makeLine(this.grid.leftEdge + 1, y1, x1, y2)).attr('stroke', '#DDD').transform("t0," + this.grid.height);
				var text = this.paper.text(x2 + 15, y1, tickText).transform("t0," + this.grid.height);
			}
		}
	};

    var renderLegend = function(){
        var series = null;
        var colorRows = null;
        var pointRows = null;
        var legendY = 0;
        var textX = null;
        var geomX = null;
        var y = null;
        var color = null;

        if(this.legendPos && this.legendPos == "none"){
            return;
        }

        for(var i = 0; i < this.layers.length; i++){
            if(this.layers[i].name){
                if(!series){
                    series = {};
                }
                if(!series[this.layers[i].name]){
                    series[this.layers[i].name] = {
                        name: this.layers[i].name,
                        layers: [this.layers[i]]
                    };
                } else {
                    series[this.layers[i].name].layers.push(this.layers[i]);
                }
            }
        }

        if(!series){
            return; //None of the layers were named, we have no knowledge of series, we cannot continue.
        }

        // Currently we only have 2 discrete scales that will get put on the legend, color and points.
        if(this.scales.color && (!this.scales.color.scaleType || this.scales.color.scaleType == 'discrete')){
            colorRows = this.scales.color.scale.domain();
        }

        if(this.scales.pointType && (!this.scales.pointType.scaleType || this.scales.pointType.scaleType == 'discrete')){
            pointRows = this.scales.pointType ? this.scales.pointType.scale.domain() : null;
        }

        if(!colorRows && !pointRows){
            // We have no discrete scales to map on the legend so we'll plot a super basic legend.
            color = "#000000";
            for(var s in series){
                // We'll have 1 row per series.
                textX = this.grid.rightEdge + 100;
                geomX = this.grid.rightEdge + 75;
                y = -(this.grid.topEdge - legendY) +.5;
                for(var i = 0; i < series[s].layers.length; i++){
                    this.paper.text(textX, y, s).attr('text-anchor', 'start');

                    if(series[s].layers[i].geom.type == "Point"){
                        this.paper.circle(geomX + 10, y, 5).attr('stroke', color).attr('fill', color);
                    }

                    if(series[s].layers[i].geom.type == "Path"){
                        this.paper.path(LABKEY.vis.makeLine(geomX, y, geomX + 20, y)).attr('stroke-width', 3).attr('opacity', .6).attr('stroke', color);
                    }
                }
                legendY = legendY + 18;
            }
        } else {
            var legendRows = colorRows ? colorRows : pointRows;

            for(var i = 0 ; i < legendRows.length; i++){
                textX = this.grid.rightEdge + 100;
                geomX = this.grid.rightEdge + 75;
                y = -(this.grid.topEdge - legendY) +.5;
                for(var s in series){
                    if(legendRows[i].indexOf(s) != -1){
                        this.paper.text(textX, y, legendRows[i]).attr('text-anchor', 'start');
                        color = "#000000";
                        if(colorRows && colorRows.indexOf(legendRows[i]) != -1){
                            color = this.scales.color.scale(colorRows[colorRows.indexOf(legendRows[i])]);
                        }

                        for(var j = 0; j < series[s].layers.length; j++){
                            if(series[s].layers[j].geom.type == "Point"){
                                if(pointRows && pointRows.indexOf(legendRows[i]) !=-1){
                                    var point = this.scales.pointType.scale(pointRows[pointRows.indexOf(legendRows[i])]);
                                    point(this.paper, geomX + 10, y, 5).attr('stroke', color).attr('fill', color);
                                } else if(!pointRows){
                                    this.paper.circle(geomX + 10, y, 5).attr('stroke', color).attr('fill', color);
                                }

                            }

                            if(series[s].layers[j].geom.type == "Path"){
                                this.paper.path(LABKEY.vis.makeLine(geomX, y, geomX + 20, y)).attr('stroke-width', 3).attr('opacity', .6).attr('stroke', color);
                            }
                        }
                    }
                }

                legendY = legendY + 18;
            }
        }
    };

	this.render = function(){
        this.paper.clear();

        if(!initScales.call(this)){  // Sets up the scales.
            return false; // if we have a critical error when trying to initialize the scales we don't continue with rendering.
        }

		initGrid.call(this); // renders the grid (axes, grid lines, titles).
        this.paper.setStart();
        for(var i = 0; i < this.layers.length; i++){
            this.layers[i].render(this.paper, this.grid, this.scales, this.data, this.aes);
        }

        var gridSet = this.paper.setFinish().transform("t0," + this.grid.height);
        if(Raphael.svg){
            // Currently the clip-rect attribute only seems to work with SVG. 
            gridSet.attr('clip-rect', (this.grid.leftEdge - 6) + ", " + (-this.grid.topEdge) + ", " + (this.grid.rightEdge - this.grid.leftEdge  + 10) + ", " + (this.grid.topEdge - this.grid.bottomEdge + 9));
        }

        this.paper.setStart();
        renderLegend.call(this); // Renders the legend, we do this after the layers render because data is grouped at render time.
        this.paper.setFinish().transform("t0," + this.grid.height);
    };

	this.addLayer = function(layer){
		layer.parent = this; // Set the parent of each layer to the plot so we can grab things like data from it later.
		this.layers.push(layer);
	};

	return this;
};
