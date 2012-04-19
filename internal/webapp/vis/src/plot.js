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
	this.renderTo = config.renderTo ? config.renderTo : null; // The id of the DOM element to render the plot to, required.
	this.grid = {
		width: config.width ? config.width : null, // height of the grid where points/lines/etc gets plotted.
		height: config.height ? config.height: null // widht of the grid.
	};
	this.width = config.width ? config.width : null; // total plot width.
	this.height = config.height ? config.height : null; // total plot height.
	this.aes = config.aes ? config.aes : null;
    this.xTitle = config.xTitle ? config.xTitle : null;
    this.leftTitle = config.leftTitle ? config.leftTitle : null;
    this.rightTitle = config.rightTitle ? config.rightTitle : null;
    this.mainTitle = config.mainTitle ? config.mainTitle : null;

	if(this.renderTo == null){
		console.error("Unable to create plot, renderTo not specified");
		return null;
	}

	if(this.width == null){
		console.error("Unable to create plot, width not specified");
		return null;
	}

	if(this.height == null){
		console.error("Unable to create plot, height not specified");
		return null;
	}

	this.title = config.title ? config.title : null;	// The title of the Plot, optional
	this.data = config.data ? config.data : null; // An array of rows, required. Each row could have several pieces of data. (e.g. {subjectId: '249534596', hemoglobin: '350', CD4:'1400', day:'120'})
	this.layers = config.layers ? config.layers : []; // An array of layers, required. (e.g. a layer for a CD4 line chart over time, and a layer for a Hemoglobin line chart over time).
	this.paper = new Raphael(this.renderTo, this.width, this.height);
	this.scales = {};

	var initScales = function(){
		var leftMargin = 75;
		var rightMargin = 150;
		var bottomMargin = 50;
		var topMargin = 75;

		this.grid.leftEdge = leftMargin;
		this.grid.rightEdge = this.grid.width - rightMargin + 10; // Add 10 units of space to top and right of scale as a little padding for the grid area.
		this.grid.topEdge = this.grid.height - topMargin + 10;
		this.grid.bottomEdge = bottomMargin;

		var getAesScaleInfo = function(aes, data){
			//Gets the information necessary to create a scale for each aesthetic in a layer.
			scaleInfo = {};

			if(aes.x){
				scaleInfo.x = {};
				scaleInfo.x.scaleType = aes.x.scaleType;
				scaleInfo.x.trans = aes.x.trans;
				scaleInfo.x.max = d3.max(data, aes.x.value);
				scaleInfo.x.min = d3.min(data, aes.x.value);
			}

			if(aes.left){
				scaleInfo.left = {};
				scaleInfo.left.scaleType = aes.left.scaleType;
				scaleInfo.left.trans = aes.left.trans;
				scaleInfo.left.max = d3.max(data, aes.left.value);
				scaleInfo.left.min = d3.min(data, aes.left.value);

			}

			if(aes.right){
				scaleInfo.right = {};
				scaleInfo.right.scaleType = aes.right.scaleType;
				scaleInfo.right.trans = aes.right.trans;
				scaleInfo.right.max = d3.max(data, aes.right.value);
				scaleInfo.right.min = d3.min(data, aes.right.value);
			}

			if(aes.color){
				scaleInfo.color = {};
				scaleInfo.color.scaleType = aes.color.scaleType;
			}

			// Also need to take care of size and point scales.
			return scaleInfo;
		};

		var compareAxes = function(allMaps, layerMap){
			// This only takes care of the aesthetic maps that will go on the axes.
			// We still need to take care of things like color and pointType.
			var axes = ['x', 'left', 'right'];
			for(var i = 0; i < axes.length; i++){
				var axis = axes[i];

				if(layerMap[axis]){
					if(!allMaps[axis]){
						allMaps[axis] = layerMap[axis];
					} else {
						if(allMaps[axis].scaleType != layerMap[axis].scaleType){
							console.error("Aesthetic scale types on the same axis must match.");
						}
						if(layerMap[axis].max > allMaps[axis].max){
							allMaps[axis].max = layerMap[axis].max;
						}
						if(layerMap[axis].min < allMaps[axis].min){
							allMaps[axis].min = layerMap[axis].min;
						}
						if(layerMap[axis].trans == 'log'){
							// If one of the layers specifies log then we over ride all of them to use log.
							allMaps[axis].trans = 'log';
						}
					}
				}
			}
		};

		var all_aes_mappings = getAesScaleInfo(this.aes, this.data);

		for(var i = 0; i < this.layers.length; i++){
			//Cycle through the layers, gather info for the needed scales (min, max, scaleType, etc).
			var layerAesInfo = this.layers[i].aes ? getAesScaleInfo(this.layers[i].aes, this.data) : null;
			compareAxes(all_aes_mappings, layerAesInfo);
		}

		if(all_aes_mappings.x){
			if(!this.aes.x){
				this.aes.x = all_aes_mappings.x;
			}
			if(all_aes_mappings.x.scaleType = 'continuous'){
				this.aes.x.scale = new LABKEY.vis.Scale.Continuous(all_aes_mappings.x.trans, this.data, all_aes_mappings.x.value, [all_aes_mappings.x.min, all_aes_mappings.x.max], [leftMargin, this.grid.width - rightMargin]);
			} else {
				// Use d3 ordinal scales.
			}
		}

		if(all_aes_mappings.left){
			if(!this.aes.left){
				this.aes.left = all_aes_mappings.left;
			}
			if(all_aes_mappings.left.scaleType == 'continuous'){
				this.aes.left.scale = new LABKEY.vis.Scale.Continuous(all_aes_mappings.left.trans, this.data, all_aes_mappings.left.value, [all_aes_mappings.left.min, all_aes_mappings.left.max], [bottomMargin, this.grid.height - topMargin]);
			} else {
				// Use d3 ordinal scales.
			}

		}

		if(all_aes_mappings.right){
			if(!this.aes.right){
				this.aes.right = all_aes_mappings.right;
			}
			if(all_aes_mappings.right.scaleType == 'continuous'){
				this.aes.right.scale = new LABKEY.vis.Scale.Continuous(all_aes_mappings.right.trans, this.data, all_aes_mappings.right.value, [all_aes_mappings.right.min, all_aes_mappings.right.max], [bottomMargin, this.grid.height - topMargin]);
			} else {
				// Use d3 ordinal scales.
			}
		}

		if(all_aes_mappings.color){
			if(this.aes.color.scaleType == 'continuous') {
				// Dont have this scale type setup yet.
			} else {
				this.aes.color.scale = LABKEY.vis.Scale.ColorDiscrete();
			}
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

        var xTicks = this.aes.x.scale.ticks(7);
        for(var i = 0; i < xTicks.length; i++){
            //Plot x-axis ticks.
            var x1 = x2 = Math.floor(this.aes.x.scale(xTicks[i])) +.5;
            var y1 = -this.grid.bottomEdge + 8;
            var y2 = -this.grid.bottomEdge;

            var tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2)).transform("t0," + this.grid.height);
            var gridLine = this.paper.path(LABKEY.vis.makeLine(x1, -this.grid.bottomEdge, x2, -this.grid.topEdge)).attr('stroke', '#DDD').transform("t0," + this.grid.height);
            var text = this.paper.text(this.aes.x.scale(xTicks[i])+.5, -this.grid.bottomEdge + 15, xTicks[i]).transform("t0," + this.grid.height);
        }

		if(this.aes.left){
            var leftTicks = this.aes.left.scale.ticks(10);
			this.paper.path(LABKEY.vis.makeLine(this.grid.leftEdge +.5, -this.grid.bottomEdge + 1, this.grid.leftEdge+.5, -this.grid.topEdge)).attr('stroke', '#000').attr('stroke-width', '1').transform("t0," + this.grid.height);

            if(this.leftTitle){
                this.paper.text(this.grid.leftEdge - 55, this.grid.height / 2, this.leftTitle).attr({font: "14px Arial, sans-serif"}).transform("t0," + this.h+"r270");
            }

			for(var i = 0; i < leftTicks.length; i++){
				var x1 = this.grid.leftEdge  - 8;
				var y1 = y2 = -Math.floor(this.aes.left.scale(leftTicks[i])) + .5; // Floor it and add .5 to keep the lines sharp.
				var x2 = this.grid.leftEdge;

                if(y1 == -this.grid.bottomEdge + .5) continue; // Dont draw a line on top of the x-axis line.

				var tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2)).transform("t0," + this.grid.height);
				var gridLine = this.paper.path(LABKEY.vis.makeLine(x2 + 1, y1, this.grid.rightEdge, y2)).attr('stroke', '#DDD').transform("t0," + this.grid.height);
				var text = this.paper.text(x1 - 15, y1, leftTicks[i]).transform("t0," + this.grid.height);
			}
		}
		
		if(this.aes.right){
            var rightTicks = this.aes.right.scale.ticks(10);
            this.paper.path(LABKEY.vis.makeLine(this.grid.rightEdge + .5, -this.grid.bottomEdge + 1, this.grid.rightEdge + .5, -this.grid.topEdge)).attr('stroke', '#000').attr('stroke-width', '1').transform("t0," + this.grid.height);

            if(this.rightTitle){
                this.paper.text(this.grid.rightEdge + 55, this.grid.height / 2, this.rightTitle).attr({font: "14px Arial, sans-serif"}).transform("t0," + this.h+"r90");
            }

			for(var i = 0; i < rightTicks.length; i++){
				var x1 = this.grid.rightEdge;
				var y1 = y2 = -Math.floor(this.aes.right.scale(rightTicks[i])) + .5;
				var x2 = this.grid.rightEdge + 8;

                if(y1 == -this.grid.bottomEdge + .5) continue; // Dont draw a line on top of the x-axis line.

				var tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2)).transform("t0," + this.grid.height);
				var gridLine = this.paper.path(LABKEY.vis.makeLine(this.grid.leftEdge + 1, y1, x1, y2)).attr('stroke', '#DDD').transform("t0," + this.grid.height);
				var text = this.paper.text(x2 + 15, y1, rightTicks[i]).transform("t0," + this.grid.height);
			}
		}
	};

	this.render = function(){

		if(!initScales.call(this)){  // Sets up the scales.
			return false; // if we have a critical error when trying to initialize the scales we don't continue with rendering.
		}

		initGrid.call(this); // renders the grid (axes, grid lines, titles).

        for(var i = 0; i < this.layers.length; i++){
            this.layers[i].render(this.paper, this.grid, this.data, this.aes);
        }

        // renderLegend(); // Renders the legend, we do this after the layers render because data is grouped at render time.
    };

	this.addLayer = function(layer){
		layer.parent = this; // Set the parent of each layer to the plot so we can grab things like data from it later.
		this.layers.push(layer);
	};

	return this;
};
