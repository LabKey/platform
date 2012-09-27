/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.vis.Plot = function(config){

    var error = function(msg){
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
        var scales = {}, newScaleName;
        for(var scale in origScales){
            if(scale == 'y'){
                origScales.yLeft = origScales.y;
                newScaleName = (scale == 'y') ? 'yLeft' : scale;
            } else {
                newScaleName = scale;
            }
            scales[newScaleName] = {};
            scales[newScaleName].scaleType = origScales[scale].scaleType ? origScales[scale].scaleType : 'continuous';
            scales[newScaleName].trans = origScales[scale].trans ? origScales[scale].trans : 'linear';
            scales[newScaleName].tickFormat = origScales[scale].tickFormat ? origScales[scale].tickFormat : null;
            scales[newScaleName].range = origScales[scale].range ? origScales[scale].range : null;
        }
        return scales;
    };

    var setDefaultMargins = function(margins, legendPos, allAes, scales){
        var top = 75, right = 75, bottom = 50, left = 75; // Defaults.
        var foundLegendScale = false, foundYRight = false;

        for(var i = 0; i < allAes.length; i++){
            var aes = allAes[i];

            if(!foundLegendScale && (aes.shape || aes.color || aes.outlierColor || aes.outlierShape) && legendPos != 'none'){
                foundLegendScale = true;
                right = right + 150;
            }

            if(!foundYRight && aes.yRight){
                foundYRight = true;
                right = right + 25;
            }
        }

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
    this.legendPos = config.legendPos;

    var allAes = [];
    if(this.aes){
        allAes.push(this.aes);
    }
    for(var i = 0; i < this.layers.length; i++){
        if(this.layers[i].aes){
            allAes.push(this.layers[i].aes);
        }
    }

    var margins = setDefaultMargins(config.margins, this.legendPos, allAes, this.scales);

    if(this.labels.y){
        this.labels.yLeft = this.labels.y;
    }

    if(this.grid.width == null){
		error("Unable to create plot, width not specified");
		return null;
	}

	if(this.grid.height == null){
		error("Unable to create plot, height not specified");
		return null;
	}

	if(this.renderTo == null){
		error("Unable to create plot, renderTo not specified");
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
                    // Not all aesthetics get a scale (like hoverText), so we have to be pretty specific.
                    if(aesthetic === 'x' || aesthetic === 'yLeft' || aesthetic === 'yRight' || aesthetic === 'size'){
                        scales[aesthetic] = {scaleType: 'continuous', trans: 'linear'};
                    } else if(aesthetic == 'color' || aesthetic == 'shape' || aesthetic == 'outlierColor' || aesthetic == 'outlierShape'){
                        if(aesthetic == 'outlierColor'){
                            scales['color'] = {scaleType: 'discrete'};
                        } else if(aesthetic == 'outlierShape'){
                            scales['shape'] = {scaleType: 'discrete'};
                        } else {
                            scales[aesthetic] = {scaleType: 'discrete'};
                        }
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

        getDomain(this.originalScales, this.scales, this.data, this.aes);
        for(var i = 0; i < this.layers.length; i++){
            getDomain(this.originalScales, this.scales, this.layers[i].data ? this.layers[i].data : this.data, this.layers[i].aes);
        }

		this.grid.leftEdge = margins.left;
        this.grid.rightEdge = this.grid.width - margins.right + 10;
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
                } else {
                    if(!scale.range){
                        scale.range = ['#222222', '#EEEEEE'];
                    }
                    scale.scale = LABKEY.vis.Scale.Continuous(scale.trans, null, null, [scale.min, scale.max], scale.range);
                }
            } else if(scaleName == 'shape'){
                if(!scale.scaleType || scale.scaleType == 'discrete') {
                    scale.scale = LABKEY.vis.Scale.Shape();
                }
            } else if(scaleName == 'size'){
                if(!scale.range){
                    scale.range = [1, 5];
                }
                domain = [scale.min, scale.max];
                scale.scale = LABKEY.vis.Scale.Continuous(scale.trans, null, null, domain, scale.range)
            }
        }

        if(!this.scales.x || !this.scales.x.scale){
            error.call(this, 'Unable to create an x scale, rendering aborted.');
            return false;
        }

        if((!this.scales.yLeft || !this.scales.yLeft.scale) && (!this.scales.yRight ||!this.scales.yRight.scale)){
            error.call(this, "Unable to create a y scale, rendering aborted.");
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
            this.setMainLabel(this.labels.main.value, this.labels.main.lookClickable);
        }

		// Now that we have all the scales situated we need to render the axis lines, tick marks, and titles.
		this.paper.path(LABKEY.vis.makeLine(this.grid.leftEdge, -this.grid.bottomEdge +.5, this.grid.rightEdge, -this.grid.bottomEdge+.5)).attr('stroke', '#000').attr('stroke-width', '1').transform("t0," + this.grid.height);

        if(this.labels.x && this.labels.x.value){
            this.setXLabel(this.labels.x.value, this.labels.x.lookClickable);
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
                xTicksSet.attr('text-anchor', 'start').transform('t0,' + (this.grid.height + 12)+'r15');
                break;
            }
        }

		if(this.scales.yLeft && this.scales.yLeft.scale){
            var leftTicks;
            if(this.scales.yLeft.scaleType == 'continuous'){
                leftTicks = this.scales.yLeft.scale.ticks(10);
            } else {
                leftTicks = this.scales.yLeft.scale.domain();
            }

			this.paper.path(LABKEY.vis.makeLine(this.grid.leftEdge +.5, -this.grid.bottomEdge + 1, this.grid.leftEdge+.5, -this.grid.topEdge)).attr('stroke', '#000').attr('stroke-width', '1').transform("t0," + this.grid.height);

            if(this.labels.yLeft && this.labels.yLeft.value){
                this.setYLeftLabel(this.labels.yLeft.value, this.labels.yLeft.lookClickable);
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
            if(this.scales.yRight.scaleType == 'continuous'){
                rightTicks = this.scales.yRight.scale.ticks(10);
            } else {
                rightTicks = this.scales.yRight.scale.domain();
            }

            this.paper.path(LABKEY.vis.makeLine(this.grid.rightEdge + .5, -this.grid.bottomEdge + 1, this.grid.rightEdge + .5, -this.grid.topEdge)).attr('stroke', '#000').attr('stroke-width', '1').transform("t0," + this.grid.height);

            if(this.labels.yRight && this.labels.yRight.value){
                this.setYRightLabel(this.labels.yRight.value, this.labels.yRight.lookClickable);
            }

			for(i = 0; i < rightTicks.length; i++){
				x1 = this.grid.rightEdge;
				y1 = y2 = -Math.floor(this.scales.yRight.scale(rightTicks[i])) + .5;
				x2 = this.grid.rightEdge + 8;

                if(y1 == -this.grid.bottomEdge + .5) continue; // Dont draw a line on top of the x-axis line.

				tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2)).transform("t0," + this.grid.height);
                tickText = this.scales.yRight.tickFormat ? this.scales.yRight.tickFormat(rightTicks[i]) : rightTicks[i];
				gridLine = this.paper.path(LABKEY.vis.makeLine(this.grid.leftEdge + 1, y1, x1, y2)).attr('stroke', '#DDD').transform("t0," + this.grid.height);
                if(this.gridLineColor){
                    gridLine.attr('stroke', this.gridLineColor);
                }
				text = this.paper.text(x2 + 15, y1, tickText).transform("t0," + this.grid.height);
			}
		}
	};

    var renderLegend = function(){
        var xPadding = this.scales.yRight ? 25 : 0,
            startY = 0,
            defaultColor = function(){return '#333'},
            // We default to a rectangle because it's not one of the shapes in the shape scale.
            defaultShape = function(){
                return function(paper, x, y, size){
                    return paper.rect(x - size, y - (size/2), size*2, size);
                }
            };

        var compareDomains = function(domain1, domain2){
            if(domain1.length != domain2.length){
                return false;
            }

            domain1.sort();
            domain2.sort();

            for(var i = 0; i < domain1.length; i++){
                if(domain1[i] != domain2[i]) {
                    return false;
                }
            }

            return true;
        };

        var renderPartial = function(paper, grid, y, geomX, textX, domain, shapeFn, colorFn){
            for(var i = 0; i < domain.length; i++){
                var translatedY = parseInt(-(grid.topEdge - y)) +.5,
                    color = colorFn(domain[i]),
                    shape = shapeFn(domain[i]);
                
                shape(paper, geomX, translatedY, 5).attr('fill', color).attr('stroke', color);
                paper.text(textX, translatedY, domain[i]).attr('text-anchor', 'start').attr('title', domain[i]);

                y = y + 18;
            }

            // returns the next available y position.
            return y;
        };

        if(this.legendPos && this.legendPos == "none"){
            return;
        }

        if(this.scales.color && this.scales.shape){
            if(compareDomains(this.scales.color.scale.domain(), this.scales.shape.scale.domain())){
                renderPartial(
                        this.paper,
                        this.grid,
                        startY,
                        this.grid.rightEdge + 50,
                        this.grid.rightEdge + 75 + xPadding,
                        this.scales.shape.scale.domain(),
                        this.scales.shape.scale,
                        this.scales.color.scale
                )
            } else {
                // Color
                var lastY = renderPartial(
                        this.paper,
                        this.grid,
                        startY,
                        this.grid.rightEdge + 50,
                        this.grid.rightEdge + 75 + xPadding,
                        this.scales.color.scale.domain(),
                        defaultShape,
                        this.scales.color.scale
                );

                // Shape
                renderPartial(
                        this.paper,
                        this.grid,
                        lastY + 18,
                        this.grid.rightEdge + 50,
                        this.grid.rightEdge + 75 + xPadding,
                        this.scales.shape.scale.domain(),
                        this.scales.shape.scale,
                        defaultColor
                );
            }
        } else if(this.scales.color){
            renderPartial(
                    this.paper, this.grid,
                    startY,
                    this.grid.rightEdge + 50,
                    this.grid.rightEdge + 75 + xPadding,
                    this.scales.color.scale.domain(),
                    defaultShape,
                    this.scales.color.scale
            );
        } else if(this.scales.shape){
            renderPartial(
                    this.paper,
                    this.grid,
                    startY,
                    this.grid.rightEdge + 50,
                    this.grid.rightEdge + 75 + xPadding,
                    this.scales.shape.scale.domain(),
                    this.scales.shape.scale,
                    defaultColor
            );
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
            error.call(this,'No layers added to the plot, nothing to render.');
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

    var renderClickArea = function(bbox, labelName){
        var clickArea = this.paper.set();
        var box, triangle;
        var wPad = 10, x = bbox.x, y = bbox.y, height = bbox.height, width = bbox.width;
        var tx, ty, r = 4, tFn;
        if(labelName == 'x' || labelName == 'main'){
            width = width + height + (wPad * 2);
            x = x - wPad;
            tx = x + width - r - (wPad / 2);
            ty = y + (height / 2);
        } else if(labelName == 'yLeft' || labelName == 'yRight'){
            height = height + width + (wPad * 2);
            y = y - width - wPad;
            tx = x + (width /2);
            ty = y + r + (wPad / 2);
        }

        if(labelName == 'main'){
            //down arrow
            tFn = function(x, y, r){
                var yBottom = y + r, yTop = y - r, xLeft = x - r, xRight = x + r;
                return 'M ' + x + ' ' + yBottom + ' L ' + xLeft + ' ' + yTop + ' L ' + xRight + ' ' + yTop + ' L ' + x + ' ' + yBottom + ' Z';
            };

        }else if(labelName == 'x'){
            // up arrow
            tFn = function(x, y, r){
                var yBottom = y + r, yTop = y - r, xLeft = x - r, xRight = x + r;
                return 'M ' + x + ' ' + yTop + ' ' + ' L ' + xRight + ' ' + yBottom + ' L ' + xLeft + ' ' + yBottom + ' L ' + x + ' ' + yTop + ' Z';
            };
        } else if(labelName == 'yLeft'){
            // right arrow
            tFn = function(x, y, r){
                var yBottom = y + r, yTop = y - r, xLeft = x - r, xRight = x + r;
                return 'M ' + xRight + ' ' + y + ' L ' + xLeft + ' ' + yBottom + ' L ' + xLeft + ' ' + yTop + ' L ' + xRight + ' ' + y + ' Z';
            };
        } else if(labelName == 'yRight'){
            // left arrow
            tFn = function(x, y, r){
                var yBottom = y + r, yTop = y - r, xLeft = x - r, xRight = x + r;
                return 'M ' + xLeft + ' ' + y + ' L ' + xRight + ' ' + yTop + ' L ' + xRight + ' ' + yBottom + ' L ' + xLeft + ' ' + y + ' Z';
            };
        }
        triangle = this.paper.path(tFn(tx, ty, r)).attr('fill', 'black');
        clickArea.push(triangle);

        box = this.paper.rect(Math.floor(x) + .5, Math.floor(y) + .5, width, height);
        box.attr('fill', '#FFFFFF').attr('fill-opacity', 0);
        clickArea.push(box);

        box.mouseover(function(){
            this.attr('stroke', '#777777');
            triangle.attr('fill', '#777777');
            triangle.attr('stroke', '#777777');
        });
        box.mouseout(function(){
            this.attr('stroke', '#000000');
            triangle.attr('fill', '#000000');
            triangle.attr('stroke', '#000000');
        });

        return clickArea;
    };

    var setLabel = function(name, x, y, value, lookClickable, render){
        if(!this.labels[name]){
            this.labels[name] = {};
        }

        if(this.labels[name].value != value){
            this.labels[name].value = value;
        }

        if(this.labels[name].lookClickable != lookClickable){
            this.labels[name].lookClickable = lookClickable;
        }

        if(render){
            if(labelElements[name] && labelElements[name].text){
                labelElements[name].text.remove();
            } else if(!labelElements[name]){
                labelElements[name] = {};
            }

            labelElements[name].text = renderLabel.call(this, x, y, value);
            // TODO: Automatically detect the default font to use for labels.
            if(name == 'main'){
                labelElements[name].text.attr({font: "18px verdana, arial, helvetica, sans-serif"});
            } else if(name == 'x'){
                labelElements[name].text.attr({font: "14px verdana, arial, helvetica, sans-serif"}).attr({'text-anchor': 'middle'});
            } else if(name == 'yRight') {
                labelElements[name].text.attr({font: "14px verdana, arial, helvetica, sans-serif"});
                labelElements[name].text.transform("t0," + this.h+"r90");
            } else if(name == 'yLeft'){
                labelElements[name].text.attr({font: "14px verdana, arial, helvetica, sans-serif"});
                labelElements[name].text.transform("t0," + this.h+"r270");
            }

            if(labelElements[name].clickArea){
                labelElements[name].clickArea.remove();
            }
            if(this.labels[name].lookClickable === true){
                var bbox = labelElements[name].text.getBBox();
                labelElements[name].clickArea = renderClickArea.call(this, bbox, name);
            }

            // Replace the listeners.
            if(this.labels[name].listeners){
                for(var listener in this.labels[name].listeners){
                    this.addLabelListener(name, listener, this.labels[name].listeners[listener]);
                }
            }

            return labelElements[name];
        }
    };

    this.setMainLabel = function(value, lookClickable){
        if(this.paper){
            setLabel.call(this, 'main', this.grid.width / 2, 30, value, lookClickable, true);
        } else {
            setLabel.call(this, 'main', this.grid.width / 2, 30, value, lookClickable, false);
        }
    };

    this.setXLabel = function(value, lookClickable){
        if(this.paper){
           setLabel.call(this, 'x', this.grid.leftEdge + (this.grid.rightEdge - this.grid.leftEdge)/2, this.grid.height - 10, value, lookClickable, true);
        } else {
            setLabel.call(this, 'x', this.grid.leftEdge + (this.grid.rightEdge - this.grid.leftEdge)/2, this.grid.height - 10, value, lookClickable, false);
        }
    };

    this.setYRightLabel = function(value, lookClickable){
        if(this.paper){
            setLabel.call(this, 'yRight', this.grid.rightEdge + 45, this.grid.height / 2, value, lookClickable, true);
        } else {
            setLabel.call(this, 'yRight', this.grid.rightEdge + 45, this.grid.height / 2, value, lookClickable, false);
        }
    };
    
    this.setYLeftLabel = this.setYLabel = function(value, lookClickable){
        if(this.paper){
            setLabel.call(this, 'yLeft', this.grid.leftEdge - 55, this.grid.height / 2, value, lookClickable, true);
        } else {
            setLabel.call(this, 'yLeft', this.grid.leftEdge - 55, this.grid.height / 2, value, lookClickable, false);
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
                    labelElements[label].text[unEvent].call(labelElements[label].text, this.labels[label].listeners[listener]);
                    if(labelElements[label].clickArea){
                        labelElements[label].clickArea[unEvent].call(labelElements[label].clickArea, this.labels[label].listeners[listener]);
                    }
                }

                this.labels[label].listeners[listener] = fn;

                // Need to call the listener function and keep it within the scope of the Raphael object that we're accessing,
                // so we pass itself into the call function as the scope object. It's essentially doing something like:
                // labelElements.x.text.click.call(labelElements.x.text, fn);
                labelElements[label].text[listener].call(labelElements[label].text, fn);
                if(labelElements[label].clickArea){
                    labelElements[label].clickArea[listener].call(labelElements[label].clickArea, fn);
                }
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
        if(gridSet){
            gridSet.remove();
        }
    };

    this.setMargins = function(newMargins){
        margins = setDefaultMargins(newMargins, this.legendPos, allAes, this.scales);
    };

	return this;
};
