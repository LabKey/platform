/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.vis.Plot = function(config){

    this.error = function(msg){
        console.error(msg);
        if(console.trace){
            console.trace();
        }
        if(this.paper){
            this.paper.clear();
            this.paper.text(this.paper.width / 2, this.paper.height / 2, "Error rendering chart:\n" + msg).attr('font-size', '12px').attr('fill', 'red');
        }
    };

    var copyUserScales = function(origScales){
        // This copies the user's scales, but not the max/min because we don't want to over-write that, so we store the original
        // scales separately (this.originalScales).
        var scales = {};
        for(var scale in origScales){
            var newScaleName = (scale == 'y') ? 'yLeft' : scale;
            scales[newScaleName] = {};
            scales[newScaleName].scaleType = origScales[scale].scaleType ? origScales[scale].scaleType : null;
            scales[newScaleName].trans = origScales[scale].trans ? origScales[scale].trans : null;
            scales[newScaleName].tickFormat = origScales[scale].tickFormat ? origScales[scale].tickFormat : null;
        }
        return scales;
    };

    var setDefaultMargins = function(margins){
        var top = 75, right = 75, bottom = 50, left = 75; // Defaults.

        if(!margins){
            margins = {};
        }

        if(!margins.top || margins.top < 0){
            margins.top = top;
        }
        if(!margins.right || margins.right < 0){
            margins.right = right;
        }
        if(!margins.bottom || margins.bottom < 0){
            margins.bottom = bottom;
        }
        if(!margins.left || margins.left < 0){
            margins.left = left;
        }

        return margins;
    };

    var labelElements = {}; // These are all of the Raphael elements for the labels.
    var gridSet = null;
	this.renderTo = config.renderTo ? config.renderTo : null; // The id of the DOM element to render the plot to, required.
	this.grid = {
		width: config.width ? config.width : null, // height of the grid where shapes/lines/etc gets plotted.
		height: config.height ? config.height: null // widht of the grid.
	};
	this.originalScales = config.scales ? config.scales : {};
    this.scales = copyUserScales(this.originalScales);
	this.originalAes = config.aes ? config.aes : null;
    this.aes = LABKEY.vis.convertAes(this.originalAes);
    this.labels = config.labels ? config.labels : {};
	this.data = config.data ? config.data : null; // An array of rows, required. Each row could have several pieces of data. (e.g. {subjectId: '249534596', hemoglobin: '350', CD4:'1400', day:'120'})
	this.layers = config.layers ? config.layers : []; // An array of layers, required. (e.g. a layer for a CD4 line chart over time, and a layer for a Hemoglobin line chart over time).
    this.bgColor = config.bgColor ? config.bgColor : null;
    this.gridColor = config.gridColor ? config.gridColor : null;
    this.gridLineColor = config.gridLineColor ? config.gridLineColor : null;
    this.clipRect = config.clipRect ? config.clipRect : false;
    var margins = setDefaultMargins(config.margins);

    if(this.labels.y){
        this.labels.yLeft = this.labels.y;
    }

    if(this.grid.width == null){
		this.error("Unable to create plot, width not specified");
		return null;
	}

	if(this.grid.height == null){
		this.error("Unable to create plot, height not specified");
		return null;
	}

	if(this.renderTo == null){
		this.error("Unable to create plot, renderTo not specified");
		return null;
	}

    for(var aesthetic in this.aes){
        LABKEY.vis.createGetter(this.aes[aesthetic]);
    }

	var initScales = function(){
        // initScales sets up default scales if needed, gets the domain of each required scale,
        // and news up all required scales. Returns true if there were no problems, returns false if there were problems.
        for(var scale in this.scales){
            if(this.scales[scale].scale){
                delete this.scales[scale].scale;
            }

            if(this.scales[scale].min && (this.originalScales[scale] && !this.originalScales[scale].min)){
                delete this.scales[scale].min;
            }

            if(this.scales[scale].max && (this.originalScales[scale] && !this.originalScales[scale].max)){
                delete this.scales[scale].max;
            }
        }

        var setupDefaultScales = function(scales, aes){
            for(aesthetic in aes){
                if(!scales[aesthetic]){
                    if(aesthetic == 'x' || aesthetic == 'yLeft' || aesthetic == 'yRight'){
                        scales[aesthetic] = {scaleType: 'continuous', trans: 'linear'};
                    } else if(aesthetic == 'color' || aesthetic == 'shape'){
                        scales[aesthetic] = {scaleType: 'discrete'};
                    }
                }
            }
        };

        var getDomain = function(origScales, scales, data, aes){
            // Gets the domains for a given set of aesthetics and data.

            if(!data){
                return;
            }

            for(var scale in scales){
                if(aes[scale]){
                    if(scales[scale].scaleType == 'continuous'){
                        if(origScales[scale] && origScales[scale].min != null && origScales[scale].min != undefined){
                            scales[scale].min = origScales[scale].min;
                        } else {
                            var tempMin = d3.min(data, aes[scale].getValue);
                            if(scales[scale].min == null || scales[scale].min == undefined || tempMin < scales[scale].min){
                                scales[scale].min = tempMin;
                            }
                        }

                        if(origScales[scale] && origScales[scale].max != null && origScales[scale].max != undefined){
                            scales[scale].max = origScales[scale].max;
                        } else {
                            var tempMax = d3.max(data, aes[scale].getValue);
                            if(scales[scale].max == null || scales[scale].max == undefined || tempMax > scales[scale].max){
                                scales[scale].max = tempMax;
                            }
                        }
                    } else if((scale != 'shape' && scale != 'color' ) && (scales[scale].scaleType == 'ordinal' || scales[scale].scaleType == 'discrete' || scales[scale].scaleType == 'categorical')){
                        if(origScales[scale] && origScales[scale].domain){
                            if(!scales[scale].domain){
                                // If we already have a domain then we need to set it from the user input.
                                scales[scale].domain = origScales[scale].domain;
                            }
                        } else {
                            if(!scales[scale].domain){
                                scales[scale].domain = [];
                            }

                            for(var i = 0; i < data.length; i++){
                                // Cycle through the data and add the unique values.
                                var val = aes[scale].getValue(data[i]);
                                if(scales[scale].domain.indexOf(val) == -1){
                                    scales[scale].domain.push(val);
                                }
                            }
                        }
                    }
                }
            }
        };

        setupDefaultScales(this.scales, this.aes);
        for(var i = 0; i < this.layers.length; i++){
            setupDefaultScales(this.scales, this.layers[i].aes);
        }
        //layerData ? layerData : parentData
        getDomain(this.originalScales, this.scales, this.data, this.aes);
        for(var i = 0; i < this.layers.length; i++){
            getDomain(this.originalScales, this.scales, this.layers[i].data ? this.layers[i].data : this.data, this.layers[i].aes);
        }

        if(this.scales.color || this.scales.shape){
            margins.right = margins.right + 150;
        }

        if(this.scales.yRight){
            margins.right = margins.right + 25;
        }
        
		this.grid.leftEdge = margins.left;

        this.grid.rightEdge = this.grid.width - margins.right; // Add 10 units of space to top and right of scale as a little padding for the grid area.
        this.grid.topEdge = this.grid.height - margins.top + 10;
        this.grid.bottomEdge = margins.bottom;

        for(var scaleName in this.scales){
            var domain = null;
            var range = null;
            var scale = this.scales[scaleName];
            if(scaleName == 'x'){
                if(scale.scaleType == 'continuous'){
                    domain = [scale.min, scale.max];
                    range = [margins.left, this.grid.width - margins.right];
                    scale.scale = new LABKEY.vis.Scale.Continuous(scale.trans, null, null, domain, range);
                } else if(scale.scaleType == 'ordinal' || scale.scaleType == 'discrete' || scale.scaleType == 'categorical'){
                    if(scale.domain){
                        scale.scale = new LABKEY.vis.Scale.Discrete(scale.domain, [this.grid.leftEdge, this.grid.rightEdge]);
                    }
                }
            } else if(scaleName == 'yLeft' || scaleName == 'yRight'){
                range = [margins.bottom, this.grid.height - margins.top];
                if(scale.scaleType == 'continuous' && (scale.min != null && scale.min != undefined) && (scale.max != null && scale.max != undefined)){
                    domain = [scale.min, scale.max];
                    scale.scale = new LABKEY.vis.Scale.Continuous(scale.trans, null, null, domain, range);
                } else {
                    if(scale.domain){
                        scale.scale = new LABKEY.vis.Scale.Discrete(scale.domain, range);
                    }
                }
            } else if(scaleName == 'color'){
                if(!scale.scaleType || scale.scaleType == 'discrete') {
                    scale.scale = LABKEY.vis.Scale.ColorDiscrete();
                }
            } else if(scaleName == 'shape'){
                if(!scale.scaleType || scale.scaleType == 'discrete') {
                    scale.scale = LABKEY.vis.Scale.Shape();
                }
            }
        }

        if(!this.scales.x || !this.scales.x.scale){
            this.error('Unable to create an x scale, rendering aborted.');
            return false;
        }

        if(!this.scales.yLeft.scale && !this.scales.yRight.scale){
            this.error("Unable to create a y scale, rendering aborted.");
            return false;
        }

		return true;
	};

	var initGrid = function(){
        var i, x1, y1, x2, y2, tick, tickText, text, gridLine;
        if(this.bgColor){
            this.paper.rect(0, 0, this.grid.width, this.grid.height).attr('fill', this.bgColor).attr('stroke', 'none');
        }

        if(this.gridColor){
            this.paper.rect(this.grid.leftEdge, (this.grid.height - this.grid.topEdge),(this.grid.rightEdge - this.grid.leftEdge), (this.grid.topEdge - this.grid.bottomEdge)).attr('fill', this.gridColor).attr('fill', this.gridColor).attr('stroke', 'none');
        }

        if(this.labels.main && this.labels.main.value){
            this.setMainLabel(this.labels.main.value);
        }

		// Now that we have all the scales situated we need to render the axis lines, tick marks, and titles.
		this.paper.path(LABKEY.vis.makeLine(this.grid.leftEdge, -this.grid.bottomEdge +.5, this.grid.rightEdge, -this.grid.bottomEdge+.5)).attr('stroke', '#000').attr('stroke-width', '1').transform("t0," + this.grid.height);

        if(this.labels.x && this.labels.x.value){
            this.setXLabel(this.labels.x.value);
        }

        var xTicks;
        var xTicksSet = this.paper.set();
        if(this.scales.x.scaleType == 'continuous'){
            xTicks = this.scales.x.scale.ticks(7);
        } else {
            xTicks = this.scales.x.domain;
        }

        for(i = 0; i < xTicks.length; i++){
            //Plot x-axis ticks.
            x1 = x2 = Math.floor(this.scales.x.scale(xTicks[i])) +.5;
            y1 = -this.grid.bottomEdge + 8;
            y2 = -this.grid.bottomEdge;

            tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2)).transform("t0," + this.grid.height);
            tickText = this.scales.x.tickFormat ? this.scales.x.tickFormat(xTicks[i]) : xTicks[i];
            text = this.paper.text(this.scales.x.scale(xTicks[i])+.5, -this.grid.bottomEdge + 15, tickText).transform("t0," + this.grid.height);
            xTicksSet.push(text);

            if(x1 - .5 == this.grid.leftEdge || x1 - .5 == this.grid.rightEdge) continue;

            gridLine = this.paper.path(LABKEY.vis.makeLine(x1, -this.grid.bottomEdge, x2, -this.grid.topEdge)).attr('stroke', '#DDD').transform("t0," + this.grid.height);
            if(this.gridLineColor){
                gridLine.attr('stroke', this.gridLineColor);
            }
        }

        for(var i = 0; i < xTicksSet.length-1; i++){
            var curBBox = xTicksSet[i].getBBox(),
                nextBBox = xTicksSet[i+1].getBBox();
            if(curBBox.x2 >= nextBBox.x){
                xTicksSet.attr('text-anchor', 'start').transform('t-25,' + (this.grid.height + 12)+'r15');
                break;
            }
        }

		if(this.scales.yLeft && this.scales.yLeft.scale){
            var leftTicks;
            if(this.scales.yLeft.scaleType == 'continuous'){
                leftTicks = this.scales.yLeft.scale.ticks(10);
            } else {
                leftTicks = this.scales.yLeft.domain();
            }

			this.paper.path(LABKEY.vis.makeLine(this.grid.leftEdge +.5, -this.grid.bottomEdge + 1, this.grid.leftEdge+.5, -this.grid.topEdge)).attr('stroke', '#000').attr('stroke-width', '1').transform("t0," + this.grid.height);

            if(this.labels.yLeft && this.labels.yLeft.value){
                this.setYLeftLabel(this.labels.yLeft.value);
            }

			for(i = 0; i < leftTicks.length; i++){
				x1 = this.grid.leftEdge  - 8;
				y1 = y2 = -Math.floor(this.scales.yLeft.scale(leftTicks[i])) + .5; // Floor it and add .5 to keep the lines sharp.
				x2 = this.grid.leftEdge;

                if(y1 == -this.grid.bottomEdge + .5) continue; // Dont draw a line on top of the x-axis line.

				tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2)).transform("t0," + this.grid.height);
                tickText = this.scales.yLeft.tickFormat ? this.scales.yLeft.tickFormat(leftTicks[i]) : leftTicks[i];
				gridLine = this.paper.path(LABKEY.vis.makeLine(x2 + 1, y1, this.grid.rightEdge, y2)).attr('stroke', '#DDD').transform("t0," + this.grid.height);
                if(this.gridLineColor){
                    gridLine.attr('stroke', this.gridLineColor);
                }
				text = this.paper.text(x1 - 15, y1, tickText).transform("t0," + this.grid.height);
			}
		}

		if(this.scales.yRight && this.scales.yRight.scale){
            var rightTicks;
            if(this.scales.yLeft.scaleType == 'continuous'){
                rightTicks = this.scales.yRight.scale.ticks(10);
            } else {
                rightTicks = this.scales.yRight.domain();
            }

            this.paper.path(LABKEY.vis.makeLine(this.grid.rightEdge + .5, -this.grid.bottomEdge + 1, this.grid.rightEdge + .5, -this.grid.topEdge)).attr('stroke', '#000').attr('stroke-width', '1').transform("t0," + this.grid.height);

            if(this.labels.yRight && this.labels.yRight.value){
                this.setYRightLabel(this.labels.yRight.value);
            }

			for(i = 0; i < rightTicks.length; i++){
				x1 = this.grid.rightEdge;
				y1 = y2 = -Math.floor(this.scales.yRight.scale(rightTicks[i])) + .5;
				x2 = this.grid.rightEdge + 8;

                if(y1 == -this.grid.bottomEdge + .5) continue; // Dont draw a line on top of the x-axis line.

				tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2)).transform("t0," + this.grid.height);
                tickText = this.scales.yRight.tickFormat ? this.scales.yRight.tickFormat(leftTicks[i]) : rightTicks[i];
				gridLine = this.paper.path(LABKEY.vis.makeLine(this.grid.leftEdge + 1, y1, x1, y2)).attr('stroke', '#DDD').transform("t0," + this.grid.height);
                if(this.gridLineColor){
                    gridLine.attr('stroke', this.gridLineColor);
                }
				text = this.paper.text(x2 + 15, y1, tickText).transform("t0," + this.grid.height);
			}
		}
	};

    var renderLegend = function(){
        var xPadding = this.scales.yRight ? 25 : 0;
        var series = null;
        var colorRows = null;
        var shapeRows = null;
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

        // Currently we only have 2 discrete scales that will get put on the legend, color and shapes.
        if(this.scales.color && (!this.scales.color.scaleType || this.scales.color.scaleType == 'discrete')){
            colorRows = this.scales.color.scale.domain();
        }

        if(this.scales.shape && (!this.scales.shape.scaleType || this.scales.shape.scaleType == 'discrete')){
            shapeRows = this.scales.shape ? this.scales.shape.scale.domain() : null;
        }

        if(!colorRows && !shapeRows){
            // We have no discrete scales to map on the legend so we'll plot a super basic legend.
            color = "#000000";
            for(var s in series){
                // We'll have 1 row per series.
                textX = this.grid.rightEdge + 75 + xPadding;
                geomX = this.grid.rightEdge + 50;
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
            var legendRows = colorRows ? colorRows : shapeRows;

            for(var i = 0 ; i < legendRows.length; i++){
                textX = this.grid.rightEdge + 75 + xPadding;
                geomX = this.grid.rightEdge + 50 + xPadding;
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
                                if(shapeRows && shapeRows.indexOf(legendRows[i]) !=-1){
                                    var shape = this.scales.shape.scale(shapeRows[shapeRows.indexOf(legendRows[i])]);
                                    shape(this.paper, geomX + 10, y, 5).attr('stroke', color).attr('fill', color);
                                } else if(!shapeRows){
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

        if(!this.paper){
            this.paper = new Raphael(this.renderTo, this.grid.width, this.grid.height);
        } else if(this.paper.width != this.grid.width || this.paper.height != this.grid.height){
            // If the user changed the size of the chart we need to alter the canvas size.
            this.paper.setSize(this.grid.width, this.grid.height);
        }
        this.paper.clear();
        
        if(!this.layers || this.layers.length < 1){
            this.error('No layers added to the plot, nothing to render.');
            return false;
        }

        if(!initScales.call(this)){  // Sets up the scales.
            return false; // if we have a critical error when trying to initialize the scales we don't continue with rendering.
        }

		initGrid.call(this); // renders the grid (axes, grid lines, titles).
        this.paper.setStart();
        for(var i = 0; i < this.layers.length; i++){
            this.layers[i].render(this.paper, this.grid, this.scales, this.data, this.aes);
        }

        gridSet = this.paper.setFinish();
        if(this.clipRect){
            gridSet.attr('clip-rect', (this.grid.leftEdge - 10) + ", " + (this.grid.height - this.grid.topEdge) + ", " + (this.grid.rightEdge - this.grid.leftEdge  + 20) + ", " + (this.grid.topEdge - this.grid.bottomEdge + 12));
        }
        gridSet.transform("t0," + this.grid.height);
        
        this.paper.setStart();
        renderLegend.call(this); // Renders the legend, we do this after the layers render because data is grouped at render time.
        this.paper.setFinish().transform("t0," + this.grid.height);
    };

    var renderLabel = function(x, y, value){
        return this.paper.text(x, y, value);
    };

    var setLabel = function(name, x, y, value, render){
        if(!this.labels[name]){
            this.labels[name] = {};
        }

        this.labels[name].value = value;

        if(render){
            if(labelElements[name]){
                labelElements[name].remove();
            }

            labelElements[name] = renderLabel.call(this, x, y, value);

            // Replace the listeners.
            if(this.labels[name].listeners){
                for(var listener in this.labels[name].listeners){
                    this.addLabelListener(name, listener, this.labels[name].listeners[listener]);

                }
            }

            return labelElements[name];
        }
    };

    this.setMainLabel = function(value){
        if(this.paper){
            setLabel.call(this, 'main', this.grid.width / 2, 30, value, true).attr({font: "18px Georgia, sans-serif"});
        } else {
            setLabel.call(this, 'main', this.grid.width / 2, 30, value, false);
        }
    };

    this.setXLabel = function(value){
        if(this.paper){
            setLabel.call(this, 'x', this.grid.leftEdge + (this.grid.rightEdge - this.grid.leftEdge)/2, this.grid.height - 10, value, true).attr({font: "14px Georgia, sans-serif"}).attr({'text-anchor': 'middle'});
        } else {
            setLabel.call(this, 'x', this.grid.leftEdge + (this.grid.rightEdge - this.grid.leftEdge)/2, this.grid.height - 10, value, false);
        }
    };

    this.setYRightLabel = function(value){
        if(this.paper){
            setLabel.call(this, 'yRight', this.grid.rightEdge + 55, this.grid.height / 2, value, true).attr({font: "14px Georgia, sans-serif"}).transform("t0," + this.h+"r90");
        } else {
            setLabel.call(this, 'yRight', this.grid.rightEdge + 55, this.grid.height / 2, value, false);
        }
    };
    
    this.setYLeftLabel = this.setYLabel = function(value){
        if(this.paper){
            setLabel.call(this, 'yLeft', this.grid.leftEdge - 55, this.grid.height / 2, value, true).attr({font: "14px Georgia, sans-serif"}).transform("t0," + this.h+"r270");
        } else {
            setLabel.call(this, 'yLeft', this.grid.leftEdge - 55, this.grid.height / 2, value, false);
        }
    };

    this.addLabelListener = function(label, listener, fn){
        var availableListeners = {
            click: 'click', dblclick:'dblclick', drag: 'drag', hover: 'hover', mousedown: 'mousedown',
            mousemove: 'mousemove', mouseout: 'mouseout', mouseover: 'mouseover', mouseup: 'mouseup',
            touchcancel: 'touchcancel', touchend: 'touchend', touchmove: 'touchmove', touchstart: 'touchstart'
        };

        if(label == 'y'){
            label = 'yLeft';
        }

        if(availableListeners[listener]){
            if(labelElements[label]){
                // Store the listener in the labels object.
                if(!this.labels[label].listeners){
                    this.labels[label].listeners = {};
                }

                if(this.labels[label].listeners[listener]){
                    // There is already a listener of the requested type, so we should purge it.
                    var unEvent = 'un' + listener;
                    labelElements[label][unEvent].call(labelElements[label], this.labels[label].listeners[listener]);
                }

                this.labels[label].listeners[listener] = fn;

                // Need to call the listener function and keep it within the scope of the Raphael object that we're accessing,
                // so we pass itself into the call function as the scope object. It's essentially doing something like:
                // labelElements.x.click.call(labelElements.x, fn);
                labelElements[label][listener].call(labelElements[label], fn);
                return true;
            } else {
                console.error('The ' + label + ' label is not available.');
                return false;
            }
        } else {
            console.error('The ' + listener + ' listener is not available.');
            return false;
        }
    };

    this.setHeight = function(h, render){
        if(render == null || render == undefined){
            render = true;
        }

        this.grid.height = h;

        if(render){
            this.render();
        }
    };

    this.setWidth = function(w, render){
        if(render == null || render == undefined){
            render = true;
        }

        this.grid.width = w;
        
        if(render){
            this.render();
        }
    };

    this.setSize = function(w, h, render){
        this.setWidth(w, false);
        this.setHeight(h, render);
    };

    this.addLayer = function(layer){
		layer.parent = this; // Set the parent of each layer to the plot so we can grab things like data from it later.
		this.layers.push(layer);
	};

    this.clearGrid = function(){
        gridSet.remove();
    };

    this.setMargins = function(newMargins){
        margins = setDefaultMargins(newMargins);
    };

	return this;
};
