/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!LABKEY.vis.internal) {
    LABKEY.vis.internal = {};
}

LABKEY.vis.internal.Axis = function() {
    // This emulates a lot of the d3.svg.axis() functionality, but adds in the ability for us to have tickHovers,
    // different colored tick & gridlines, etc.
    var scale, orientation, tickFormat = function(v) {return v}, tickHover, ticks, axisSel, tickSel, textSel,
            gridLineSel, borderSel, grid;
    var tickColor = '#000000', tickTextColor = '#000000', gridLineColor = '#DDDDDD', borderColor = '#000000';
    var tickPadding = 0, tickLength = 8, tickWidth = 1, gridLineWidth = 1, borderWidth = 1;

    var axis = function(selection) {
        var data, textAnchor, textXFn, textYFn, gridLineFn, tickFn, border, gridLineData, hasOverlap, bBoxA, bBoxB, i,
                tickEls, gridLineEls, textAnchors, textEls;

        if (scale.ticks) {
            data = scale.ticks(ticks);
        } else {
            data = scale.domain();
        }
        gridLineData = data;

        if (orientation == 'left') {
            textAnchor = 'end';
            textYFn = function(v) {return Math.floor(scale(v)) + .5;};
            textXFn = function() {return grid.leftEdge - tickLength - 2 - tickPadding};
            tickFn = function(v) {v = Math.floor(scale(v)) + .5; return 'M' + grid.leftEdge + ',' + v + 'L' + (grid.leftEdge - tickLength) + ',' + v + 'Z';};
            gridLineFn = function(v) {v = Math.floor(scale(v)) + .5; return 'M' + grid.rightEdge + ',' + v + 'L' + grid.leftEdge + ',' + v + 'Z';};
            border = 'M' + (Math.floor(grid.leftEdge) + .5) + ',' + (grid.bottomEdge + 1) + 'L' + (Math.floor(grid.leftEdge) + .5) + ',' + grid.topEdge + 'Z';
            // Don't overlap gridlines with x axis border.
            if (Math.floor(scale(data[0])) == Math.floor(grid.bottomEdge)) {
                gridLineData = gridLineData.slice(1);
            }
        } else if (orientation == 'right') {
            textAnchor = 'start';
            textYFn = function(v) {return Math.floor(scale(v)) + .5;};
            textXFn = function() {return grid.rightEdge + tickLength + 2 + tickPadding};
            tickFn = function(v) {v = Math.floor(scale(v)) + .5; return 'M' + grid.rightEdge + ',' + v + 'L' + (grid.rightEdge + tickLength) + ',' + v + 'Z';};
            gridLineFn = function(v) {v = Math.floor(scale(v)) + .5; return 'M' + grid.rightEdge + ',' + v + 'L' + (grid.leftEdge + 1) + ',' + v + 'Z';};
            border = 'M' + (Math.floor(grid.rightEdge) + .5) + ',' + (grid.bottomEdge + 1) + 'L' + (Math.floor(grid.rightEdge) + .5) + ',' + grid.topEdge + 'Z';
            // Don't overlap gridlines with x axis border.
            if (Math.floor(scale(data[0])) == Math.floor(grid.bottomEdge)) {
                gridLineData = gridLineData.slice(1);
            }
        } else {
            // Assume bottom otherwise.
            orientation = 'bottom';
            textAnchor = 'middle';
            textXFn = function(v) {return (Math.floor(scale(v)) +.5);};
            textYFn = function() {return grid.bottomEdge + tickLength + 2 + tickPadding};
            tickFn = function(v) {v = (Math.floor(scale(v)) +.5); return 'M' + v + ',' + grid.bottomEdge + 'L' + v + ',' + (grid.bottomEdge + tickLength) + 'Z';};
            gridLineFn = function(v) {v = (Math.floor(scale(v)) +.5); return 'M' + v + ',' + grid.bottomEdge + 'L' + v + ',' + grid.topEdge + 'Z';};
            border = 'M' + grid.leftEdge + ',' + (Math.floor(grid.bottomEdge) + .5) + 'L' + grid.rightEdge + ',' + (Math.floor(grid.bottomEdge) + .5) + 'Z';
            // Don't overlap gridlines with y-left axis border.
            if (scale(data[0]) == grid.leftEdge) {
                gridLineData = gridLineData.slice(1);
            }
        }

        if (!axisSel) {
            axisSel = selection.append('g').attr('class', 'axis');
        }

        if (!tickSel) {
            tickSel = axisSel.append('g').attr('class', 'tick');
        }

        if (!gridLineSel) {
            gridLineSel = axisSel.append('g').attr('class', 'grid-line');
        }

        if (!textSel) {
            textSel = axisSel.append('g').attr('class', 'tick-text');
        }

        if (tickLength > 0) {
            tickEls = tickSel.selectAll('path').data(data);
            tickEls.exit().remove();
            tickEls.enter().append('path');
            tickEls.attr('d', tickFn)
                    .attr('stroke', tickColor)
                    .attr('stroke-width', tickWidth);
        }

        if (gridLineWidth > 0) {
            gridLineEls = gridLineSel.selectAll('path').data(gridLineData);
            gridLineEls.exit().remove();
            gridLineEls.enter().append('path');
            gridLineEls.attr('d', gridLineFn)
                    .attr('stroke', gridLineColor)
                    .attr('stroke-width', gridLineWidth);
        }

        textAnchors = textSel.selectAll('a').data(data);
        textAnchors.exit().remove();
        textAnchors.enter().append('a').append('text');
        textEls = textAnchors.select('text');
        textEls.text(tickFormat)
                .attr('x', textXFn)
                .attr('y', textYFn)
                .attr('text-anchor', textAnchor)
                .attr('fill', tickTextColor)
                .style('font', '10px arial, verdana, helvetica, sans-serif');

        if (orientation == 'bottom') {
            hasOverlap = false;
            for (i = 0; i < textEls[0].length-1; i++) {
                bBoxA = textEls[0][i].getBBox();
                bBoxB = textEls[0][i+1].getBBox();
                if (bBoxA.x + bBoxA.width >= bBoxB.x) {
                    hasOverlap = true;
                    break;
                }
            }

            if (hasOverlap) {
                textEls.attr('transform', function(v) {return 'rotate(15,' + textXFn(v) + ',' + textYFn(v) + ')';})
                        .attr('text-anchor', 'start');
            } else {
                textEls.attr('transform', '');
            }
        }

        if (!borderSel) {
            borderSel = axisSel.append('g').attr('class', 'border');
        }

        borderSel.selectAll('path').remove();
        borderSel.append('path')
                .attr('stroke', borderColor)
                .attr('stroke-width', borderWidth)
                .attr('d', border);
    };

    // Grid is a reference to the plot's grid object that stores the grid dimensions and positions of edges.
    axis.grid = function(g) {grid = g; return axis;};
    axis.scale = function(s) {scale = s; return axis;};
    axis.orient = function(o) {orientation = o; return axis;};
    axis.ticks = function(t) {ticks = t; return axis;};
    axis.tickFormat = function(f) {tickFormat = f; return axis;};
    axis.tickHover = function(h) {tickHover = h; return axis;};

    axis.tickPadding = function(p) {
        if (p !== null && p !== undefined) {
            tickPadding = p;
        }
        return axis;
    };
    axis.tickColor = function(c) {
        if (c !== undefined && c !== null) {
            tickColor = c;
        }
        return axis;
    };
    axis.tickWidth = function(w) {
        if (w !== undefined && w !== null) {
            tickWidth = w;
        }
        return axis;
    };
    axis.tickLength = function(len) {
        if (len !== undefined && len !== null) {
            tickLength = len;
        }
        return axis;
    };
    axis.gridLineColor = function(c) {
        if (c !== undefined && c !== null) {
            gridLineColor = c;
        }
        return axis;
    };
    axis.gridLineWidth = function(w) {
        if (w !== undefined && w !== null) {
            gridLineWidth = w;
        }
        return axis;
    };
    axis.borderColor = function(c) {
        if (c !== undefined && c !== null) {
            borderColor = c;
        }
        return axis;
    };
    axis.borderWidth = function (w) {
        if (w !== undefined && w !== null) {
            borderWidth = w;
        }
        return axis;
    };
    axis.tickTextColor = function(c) {
        if (c !== undefined && c !== null) {
            tickTextColor = c;
        }
        return axis;
    };

    return axis;
};

LABKEY.vis.internal.D3Renderer = function(plot) {
    var errorMsg, labelElements = null, xAxis = null, leftAxis = null, rightAxis = null, brush = null, brushSel = null,
        brushSelectionType = null, xHandleBrush = null, xHandleSel = null, yHandleBrush = null, yHandleSel = null;

    var initLabelElements = function() {
        labelElements = {};
        var labels, mainLabel = {}, yLeftLabel = {}, yRightLabel = {}, xLabel = {};

        labels = this.canvas.append('g').attr('class', plot.renderTo + '-labels');

        mainLabel.dom = labels.append('text').attr('text-anchor', 'middle')
                .style('font', '18px verdana, arial, helvetica, sans-serif');

        xLabel.dom = labels.append('text').attr('text-anchor', 'middle')
                .style('font', '14px verdana, arial, helvetica, sans-serif');

        yLeftLabel.dom = labels.append('text').attr('text-anchor', 'middle')
                .style('font', '14px verdana, arial, helvetica, sans-serif');

        yRightLabel.dom = labels.append('text').attr('text-anchor', 'middle')
                .style('font', '14px verdana, arial, helvetica, sans-serif');

        labelElements.main = mainLabel;
        labelElements.y = yLeftLabel;
        labelElements.yLeft = yLeftLabel;
        labelElements.yRight = yRightLabel;
        labelElements.x = xLabel;
    };

    var initClipRect = function() {
        if (!this.clipRect) {
            var clipPath = this.canvas.append('defs').append('clipPath').attr('id', plot.renderTo + '-clipPath');
            this.clipRect = clipPath.append('rect');
        }

        this.clipRect.attr('x', plot.grid.leftEdge - 10)
                .attr('y', plot.grid.topEdge - 10)
                .attr('width', plot.grid.rightEdge - plot.grid.leftEdge + 20)
                .attr('height', plot.grid.bottomEdge - plot.grid.topEdge + 20);
    };

    var applyClipRect = function(selection) {
        // TODO: Right now we apply this individually to every geom as it renders. It might be better to apply this to a
        // top level svg group that we put all geom items in.
        selection.attr('clip-path', 'url(#'+ plot.renderTo + '-clipPath)');
    };

    var initCanvas = function() {
        if (!this.canvas) {
            this.canvas = d3.select('#' + plot.renderTo).append('svg');
        }

        if (errorMsg) {
            errorMsg.text('');
        }

        this.canvas.attr('width', plot.grid.width).attr('height', plot.grid.height);

        if (plot.bgColor) {
            if (!this.bgRect) {
                this.bgRect = this.canvas.append('g').attr('class', 'bg-rect').append('rect');
            }

            this.bgRect.attr('width', plot.grid.width).attr('height', plot.grid.height).attr('fill', plot.bgColor);
        }
    };

    var renderError = function(msg) {
        // Don't attempt to render an error if we haven't initialized the canvas.
        if (this.canvas) {
            // Clear the canvas first.
            this.canvas.selectAll('*').remove();

            if (!errorMsg) {
                errorMsg = this.canvas.append('text')
                        .attr('class', 'vis-error')
                        .attr('x', plot.grid.width / 2)
                        .attr('y', plot.grid.height / 2)
                        .attr('text-anchor', 'middle')
                        .attr('fill', 'red');
            }

            errorMsg.text(msg);
        }
    };

    var configureAxis = function(ax){
        ax.grid(plot.grid)
            .gridLineColor(plot.gridLineColor)
            .gridLineWidth(plot.gridLineWidth)
            .tickColor(plot.tickColor)
            .tickLength(plot.tickLength)
            .tickWidth(plot.tickWidth)
            .borderColor(plot.borderColor)
            .borderWidth(plot.borderWidth)
            .tickTextColor(plot.tickTextColor)
    };

    var renderXAxis = function() {
        if (!xAxis) {
            xAxis = LABKEY.vis.internal.Axis().orient('bottom');
        }

        xAxis.scale(plot.scales.x.scale).tickPadding(10).ticks(7);
        configureAxis(xAxis);

        if (plot.scales.x.tickFormat) {
            xAxis.tickFormat(plot.scales.x.tickFormat);
        }

        if (plot.scales.x.tickHoverText) {
            xAxis.tickHover(plot.scales.x.tickHoverText);
        }

        this.canvas.call(xAxis);
    };

    var renderYLeftAxis = function() {
        if (!leftAxis) {
            leftAxis = LABKEY.vis.internal.Axis().orient('left');
        }

        if (plot.scales.yLeft && plot.scales.yLeft.scale) {
            leftAxis.scale(plot.scales.yLeft.scale).ticks(10);
            configureAxis(leftAxis);

            if (plot.scales.yLeft.tickFormat) {
                leftAxis.tickFormat(plot.scales.yLeft.tickFormat);
            }

            if (plot.scales.yLeft.tickHoverText) {
                leftAxis.tickHover(plot.scales.yLeft.tickHoverText);
            }

            this.canvas.call(leftAxis);
        }
    };

    var renderYRightAxis = function() {
        if (!rightAxis) {
            rightAxis = LABKEY.vis.internal.Axis().orient('right');
        }
        if (plot.scales.yRight && plot.scales.yRight.scale) {
            rightAxis.scale(plot.scales.yRight.scale).ticks(10);
            configureAxis(rightAxis);

            if (plot.scales.yRight.tickFormat) {
                rightAxis.tickFormat(plot.scales.yRight.tickFormat);
            }

            if (plot.scales.yRight.tickHoverText) {
                rightAxis.tickHover(plot.scales.yRight.tickHoverText);
            }

            this.canvas.call(rightAxis);
        }
    };

    var getAllData = function(){
        var allData = [];
        for (var i = 0; i < plot.layers.length; i++) {
            if(plot.layers[i].data) {
                allData.push(plot.layers[i].data);
            } else {
                allData.push(plot.data);
            }
        }

        return allData;
    };

    var getAllLayerSelections = function() {
        var allSels = [];

        for (var i = 0; i < plot.layers.length; i++) {
            allSels.push(d3.select('#' + plot.renderTo + '-' + plot.layers[i].geom.index));
        }

        return allSels;
    };

    var addBrushHandles = function(brush, brushSel){
        // TODO: Brushing from handles when there are discrete axes is broken. Should probably just disable brushing on
        // discrete axes (i.e. only allow 1D selection)...
        var xBrushStart, xBrush, xBrushEnd, yBrushStart, yBrush, yBrushEnd;

        if (!xHandleSel) {
            xHandleSel = this.canvas.insert('g', '.brush').attr('class', 'x-axis-handle');
            xHandleBrush = d3.svg.brush();
        }

        if (!yHandleSel) {
            yHandleSel = this.canvas.insert('g', '.brush').attr('class', 'y-axis-handle');
            yHandleBrush = d3.svg.brush();
        }

        xHandleBrush.x(brush.x());
        xHandleSel.call(xHandleBrush);
        yHandleBrush.y(brush.y());
        yHandleSel.call(yHandleBrush);

        xBrushStart = function(){
            if (brushSelectionType == 'y' || brushSelectionType === null) {
                brushSelectionType = 'x';
                yHandleBrush.clear();
                yHandleBrush(yHandleSel);
            }

            brush.on('brushstart')('x');
        };

        xBrush = function() {
            var bEx = brush.extent(),
                xEx = xHandleBrush.extent(),
                yEx = [],
                newEx = [];

            if (yHandleBrush.empty()) {
                yEx = brush.y().domain();
            } else {
                yEx = [bEx[0][1], bEx[1][1]];
            }

            newEx[0] = [xEx[0], yEx[0]];
            newEx[1] = [xEx[1], yEx[1]];

            brush.extent(newEx);
            brush(brushSel);
            brush.on('brush')('x');
        };

        xBrushEnd = function() {
            if (xHandleBrush.empty()) {
                brushSelectionType = null;
                yHandleBrush.clear();
                yHandleBrush(yHandleSel);
                brush.clear();
                brush(brushSel);
                brush.on('brush')();
            }

            brush.on('brushend')();
        };

        yBrushStart = function() {
            if (brushSelectionType == 'x' || brushSelectionType == null) {
                brushSelectionType = 'y';
                xHandleBrush.clear();
                xHandleBrush(xHandleSel);
            }

            brush.on('brushstart')('y');
        };

        yBrush = function() {
            var bEx = brush.extent(),
                yEx = yHandleBrush.extent(),
                xEx = [],
                newEx = [];

            if (xHandleBrush.empty()) {
                xEx = brush.x().domain();
            } else {
                xEx = [bEx[0][0], bEx[1][0]];
            }

            newEx[0] = [xEx[0], yEx[0]];
            newEx[1] = [xEx[1], yEx[1]];

            brush.extent(newEx);
            brush(brushSel);
            brush.on('brush')('y');
        };

        yBrushEnd = function() {
            if (yHandleBrush.empty()) {
                brushSelectionType = null;
                xHandleBrush.clear();
                xHandleBrush(xHandleSel);
                brush.clear();
                brush(brushSel);
                brush.on('brush')();
            }

            brush.on('brushend')();
        };

        xHandleSel.attr('transform', 'translate(0,' + plot.grid.bottomEdge + ')');
        xHandleSel.selectAll('rect').attr('height', 30);
        xHandleSel.selectAll('.extent').attr('opacity', 0);
        xHandleSel.select('.resize.e rect').attr('fill', '#14C9CC').attr('style', null);
        xHandleSel.select('.resize.w rect').attr('fill', '#14C9CC').attr('style', null);
        xHandleBrush.on('brushstart', xBrushStart);
        xHandleBrush.on('brush', xBrush);
        xHandleBrush.on('brushend', xBrushEnd);

        yHandleSel.attr('transform', 'translate(' + (plot.grid.leftEdge - 30) + ',0)');
        yHandleSel.selectAll('rect').attr('width', 30);
        yHandleSel.selectAll('.extent').attr('opacity', 0);
        yHandleSel.select('.resize.n rect').attr('fill-opacity', 1).attr('fill', '#14C9CC').attr('style', null);
        yHandleSel.select('.resize.s rect').attr('fill-opacity', 1).attr('fill', '#14C9CC').attr('style', null);
        yHandleBrush.on('brushstart', yBrushStart);
        yHandleBrush.on('brush', yBrush);
        yHandleBrush.on('brushend', yBrushEnd);
    };

    var handleMove = function(handle){
        var ex = brush.extent();
        if (handle === undefined) {
            // Brush event fired from main brush surface
            if (brushSelectionType == 'x' || brushSelectionType == 'both') {
                xHandleBrush.extent([ex[0][0], ex[1][0]]);
                xHandleBrush(xHandleSel);
            }

            if (brushSelectionType == 'y' || brushSelectionType == 'both') {
                yHandleBrush.extent([ex[0][1], ex[1][1]]);
                yHandleBrush(yHandleSel);
            }
        } else if (handle === 'x' && brushSelectionType == 'both') {
            // Only update the x handle if not in a 1D selection.
            yHandleBrush.extent([ex[0][1], ex[1][1]]);
            yHandleBrush(yHandleSel);
        } else if (handle === 'y' && brushSelectionType == 'both') {
            // Only update the y handle if not in a 1D selection.
            xHandleBrush.extent([ex[0][0], ex[1][0]]);
            xHandleBrush(xHandleSel);
        }
    };

    var handleResize = function(handle){
        var ex = brush.extent(), xEx = [ex[0][0], ex[1][0]], yEx = [ex[0][1], ex[1][1]], yD = yHandleBrush.y().domain(),
                xD = xHandleBrush.x().domain();

        if (handle === undefined) {// Brush event fired from main brush surface

            // If we have 1D selection, but the user adjusts the other dimension, change to a 2D selection.
            if (brushSelectionType == 'x') {
                if (yEx[0] > yD[0] || yEx[1] < yD[1]) {
                    brushSelectionType = 'both';
                }
            }

            // If we have 1D selection, but the user adjusts the other dimension, change to a 2D selection.
            if (brushSelectionType == 'y') {
                if (xEx[0] > xD[0] || xEx[1] < xD[1]) {
                    brushSelectionType = 'both';
                }
            }

            if (brushSelectionType == 'x' || brushSelectionType == 'both') {
                xHandleBrush.extent([ex[0][0], ex[1][0]]);
                xHandleBrush(xHandleSel);
            }

            if (brushSelectionType == 'y' || brushSelectionType == 'both') {
                yHandleBrush.extent([ex[0][1], ex[1][1]]);
                yHandleBrush(yHandleSel);
            }
        } else if (handle === 'x' && brushSelectionType == 'both') {
            // Only update the x handle if not in a 1D selection.
            yHandleBrush.extent([ex[0][1], ex[1][1]]);
            yHandleBrush(yHandleSel);
        } else if (handle === 'y' && brushSelectionType == 'both') {
            // Only update the y handle if not in a 1D selection.
            xHandleBrush.extent([ex[0][0], ex[1][0]]);
            xHandleBrush(xHandleSel);
        }
    };

    var addBrush = function(){
        if (plot.brushing != null && (plot.brushing.brushstart || plot.brushing.brush || plot.brushing.brushend ||
                plot.brushing.brushclear)) {
            var xScale, yScale;

            if(brush == null) {
                brush = d3.svg.brush();
                brushSel = this.canvas.insert('g', '.layer').attr('class', 'brush');
            }

            if (plot.scales.x.scaleType == 'continuous' && plot.scales.x.trans == 'linear') {
                // We need to add some padding to the scale in order for us to actually be able to select all of the points.
                // If we don't, any points that lie exactly at the edge of the chart will be unselectable.
                xScale = plot.scales.x.scale.copy();
                xScale.domain([xScale.invert(plot.grid.leftEdge - 5), xScale.invert(plot.grid.rightEdge + 5)]);
                xScale.range([plot.grid.leftEdge - 5, plot.grid.rightEdge + 5]);
            } else {
                xScale = plot.scales.x.scale;
            }

            if (plot.scales.yLeft.scaleType == 'continuous' && plot.scales.yLeft.trans == 'linear') {
                // See the note above.
                yScale = plot.scales.yLeft.scale.copy();
                yScale.domain([yScale.invert(plot.grid.bottomEdge + 5), yScale.invert(plot.grid.topEdge - 5)]);
                yScale.range([plot.grid.bottomEdge + 5, plot.grid.topEdge - 5]);
            } else {
                yScale = plot.scales.yLeft.scale;
            }

            brush.x(xScale).y(yScale);
            brushSel.call(brush);
            brushSel.selectAll('.extent').attr('opacity', .75).attr('fill', '#EBF7F8').attr('stroke', '#14C9CC')
                    .attr('stroke-width', 1.5);

            // The brush handles require the main brush is initialized first, because they use the same scales.
            addBrushHandles.call(this, brush, brushSel);

            brush.on('brushstart', function(handle){
                if (plot.brushing.brushstart) {
                    plot.brushing.brushstart(d3.event, getAllData(), getBrushExtent(), plot, getAllLayerSelections());
                }
            });

            brush.on('brush', function(handle){
                var event = d3.event;

                if ((brushSelectionType === 'x' && handle === 'y') ||
                        (brushSelectionType === 'y' && handle === 'x') ||
                        (brushSelectionType === null && handle === undefined)) {
                    brushSelectionType = 'both';
                }

                // event will be null when we call clearBrush.
                if (event) {
                    if (event.mode === 'move') {
                        handleMove(handle);
                    } else if (event.mode === 'resize') {
                        handleResize(handle);
                    }
                }

                if (plot.brushing.brush !== null) {
                    plot.brushing.brush(event, getAllData(), getBrushExtent(), plot, getAllLayerSelections());
                }
            });

            brush.on('brushend', function(){
                var allData = getAllData();
                var extent = brush.extent();
                var event = d3.event;

                if (brush.empty()) {
                    brushSelectionType = null;
                    xHandleBrush.clear();
                    xHandleBrush(xHandleSel);
                    yHandleBrush.clear();
                    yHandleBrush(yHandleSel);
                    if (plot.brushing.brushclear) {
                        plot.brushing.brushclear(event, allData, plot, getAllLayerSelections());
                    }
                } else {
                    if (plot.brushing.brushend) {
                        plot.brushing.brushend(event, allData, getBrushExtent(), plot, getAllLayerSelections());
                    }
                }
            });
        }
    };

    var getBrushExtent = function() {
        if (brush) {
            var extent = brush.extent();

            if (brushSelectionType == 'both') {
                return extent;
            } else if (brushSelectionType == 'x') {
                return [[extent[0][0], null], [extent[1][0], null]];
            } else if (brushSelectionType == 'y') {
                return [[null, extent[0][1]], [null, extent[1][1]]];
            }
        }

        return null;
    };

    var validateExtent = function(extent) {
        if (extent.length < 2 || extent[0].length < 2 || extent[1].length < 2) {
            throw Error("The extent must be a 2d array in the form of [[xMin, yMin], [xMax, yMax]]");
        }

        var xMin = extent[0][0], xMax = extent[1][0], yMin = extent[0][1], yMax = extent[1][1];

        if ((xMin === null && xMax !== null) || (xMin !== null && xMax === null)) {
            throw Error("The xMin and xMax both have to be valid numbers, or both have to be null.");
        }

        if ((yMin === null && yMax !== null) || (yMin !== null && yMax === null)) {
            throw Error("The yMin and yMax both have to be valid numbers, or both have to be null.");
        }
    };

    var setBrushExtent = function(extent) {
        validateExtent(extent);
        var xIsNull = extent[0][0] === null && extent[1][0] === null,
            yIsNull = extent[0][1] === null && extent[1][1] === null;

        if (xIsNull && yIsNull) {
            clearBrush.call(this);
        } else if (!xIsNull && yIsNull) {
            brushSelectionType = 'x';
            xHandleBrush.extent([extent[0][0], extent[1][0]]);
            xHandleBrush(xHandleSel);
            yHandleBrush.clear();
            yHandleBrush(yHandleSel);
            xHandleBrush.on('brush')();
            xHandleBrush.on('brushend')();
        } else if (xIsNull && !yIsNull) {
            brushSelectionType = 'y';
            yHandleBrush.extent([extent[0][1], extent[1][1]]);
            yHandleBrush(yHandleSel);
            xHandleBrush.clear();
            xHandleBrush(xHandleSel);
            yHandleBrush.on('brush')();
            yHandleBrush.on('brushend')();
        } else if (!xIsNull && !yIsNull) {
            brushSelectionType = 'both';
            xHandleBrush.extent([extent[0][0], extent[1][0]]);
            xHandleBrush(xHandleSel);
            yHandleBrush.extent([extent[0][1], extent[1][1]]);
            yHandleBrush(yHandleSel);
            brush.on('brush')();
            brush.on('brushend')();
        }
    };

    var clearBrush = function(){
        brush.clear();
        brush(brushSel);
        brush.on('brush')();
        brush.on('brushend')();
    };

    var renderGrid = function() {
        if (plot.clipRect) {
            initClipRect.call(this);
        }

        if (plot.gridColor) {
            if (!this.gridRect) {
                this.gridRect = this.canvas.append('g').attr('class', 'grid-rect').append('rect');
            }

            this.gridRect.attr('width', plot.grid.rightEdge - plot.grid.leftEdge)
                    .attr('height', plot.grid.bottomEdge - plot.grid.topEdge)
                    .attr('x', plot.grid.leftEdge)
                    .attr('y', plot.grid.topEdge)
                    .attr('fill', plot.gridColor);
        }

        renderXAxis.call(this);
        renderYLeftAxis.call(this);
        renderYRightAxis.call(this);
        addBrush.call(this);
    };

    var renderClickArea = function(name) {
        var box, bx, by, bTransform, triangle, tx, ty, tPath,
                pad = 10, r = 4,
                labelEl = labelElements[name],
                bbox = labelEl.dom.node().getBBox(),
                bw = bbox.width + (pad * 2) + (3 * r),
                bh = bbox.height,
                labels = this.canvas.selectAll('.' + plot.renderTo + '-labels');

        if (labelEl.clickArea) {
            labelEl.clickArea.remove();
            labelEl.triangle.remove();
        }

        if (name == 'main') {
            tx = Math.floor(bbox.x + bbox.width + (3 * r)) + .5;
            ty = Math.floor(bbox.y + (bbox.height / 2)) + .5;
            tPath = 'M' + tx + ',' + (ty + r) + ' L ' + (tx - r) + ',' + (ty - r) + ' L ' + (tx + r) + ',' + (ty - r) + 'Z';
            bx = Math.floor(bbox.x - pad) +.5;
            by = Math.floor(bbox.y) +.5;
            bTransform = 'translate(' + bx + ',' + by +')';
        } else if (name == 'x') {
            tx = Math.floor(bbox.x + bbox.width + (3 * r)) + .5;
            ty = Math.floor(bbox.y + (bbox.height / 2)) + .5;
            tPath = 'M' + tx + ',' + (ty - r) + ' L ' + (tx - r) + ',' + (ty + r) + ' L ' + (tx + r) + ',' + (ty + r) + 'Z';
            bx = Math.floor(bbox.x - pad) +.5;
            by = Math.floor(bbox.y) +.5;
            bTransform = 'translate(' + bx + ',' + by +')';
        } else if (name == 'y' || name == 'yLeft') {
            tx = Math.floor(plot.grid.leftEdge - (3*r) - 48) + .5;
            ty = Math.floor((plot.grid.height / 2) - (bbox.width / 2) - (3*r)) + .5;
            tPath = 'M' + (tx + r) + ',' + (ty) + ' L ' + (tx - r) + ',' + (ty - r) + ' L ' + (tx - r) + ',' + (ty + r) + ' Z';
            bx = Math.floor(plot.grid.leftEdge - (bbox.height) - 52) + .5;
            by = Math.floor((plot.grid.height / 2) + (bbox.width / 2) + pad) + .5;
            bTransform = 'translate(' + bx + ',' + by +')rotate(270)';
        } else if (name == 'yRight') {
            tx = Math.floor(plot.grid.rightEdge + (2*r) + 40) + .5;
            ty = Math.floor((plot.grid.height / 2) - (bbox.width / 2) - (3*r)) + .5;
            tPath = 'M' + (tx - r) + ',' + (ty) + ' L ' + (tx + r) + ',' + (ty - r) + ' L ' + (tx + r) + ',' + (ty + r) + ' Z';
            bx = Math.floor(plot.grid.rightEdge + 23 + bbox.height) + .5;
            by = Math.floor((plot.grid.height / 2) + (bbox.width / 2) + pad) + .5;
            bTransform = 'translate(' + bx + ',' + by +')rotate(270)';
        }

        triangle = labels.append('path')
                .attr('d', tPath)
                .attr('stroke', '#000000')
                .attr('fill', '#000000');

        box = labels.append('rect')
                .attr('width', bw)
                .attr('height', bh)
                .attr('transform', bTransform)
                .attr('fill-opacity', 0)
                .attr('stroke', '#000000');

        box.on('mouseover', function() {
            box.attr('stroke', '#777777');
            triangle.attr('stroke', '#777777').attr('fill', '#777777');
        });

        box.on('mouseout', function() {
            box.attr('stroke', '#000000');
            triangle.attr('stroke', '#000000').attr('fill', '#000000');
        });

        labelEl.clickArea = box;
        labelEl.triangle = triangle;
    };

    var addLabelListener = function(label, listenerName, listenerFn) {
        var availableListeners = {
            click: 'click', dblclick:'dblclick', drag: 'drag', hover: 'hover', mousedown: 'mousedown',
            mousemove: 'mousemove', mouseout: 'mouseout', mouseover: 'mouseover', mouseup: 'mouseup',
            touchcancel: 'touchcancel', touchend: 'touchend', touchmove: 'touchmove', touchstart: 'touchstart'
        };

        if (availableListeners[listenerName]) {
            if (labelElements[label]) {
                // Store the listener in the labels object.
                if (!plot.labels[label].listeners) {
                    plot.labels[label].listeners = {};
                }

                plot.labels[label].listeners[listenerName] = listenerFn;
                // Add a .user to the listenerName to keep it scoped by itself. This way we can add our own listeners as well
                // but we'll keep them scoped separately. We have to wrap the listenerFn to we can pass it the event.
                labelElements[label].dom.on(listenerName + '.user', function(){listenerFn(d3.event);});
                if (labelElements[label].clickArea) {
                    labelElements[label].clickArea.on(listenerName + '.user', function(){listenerFn(d3.event);});
                }
                return true;
            } else {
                console.error('The ' + label + ' label is not available.');
                return false;
            }
        } else {
            console.error('The ' + listenerName + ' listener is not available.');
            return false;
        }
    };

    var renderLabel = function(name) {
        var x, y, rotate, translate = false;
        if (!labelElements) {
            initLabelElements.call(this);
        }

        if ((name == 'y' || name == 'yLeft')) {
            if (!plot.scales.yLeft || (plot.scales.yLeft && !plot.scales.yLeft.scale)) {
                return;
            }
            translate = true;
            rotate = 270;
            x = plot.grid.leftEdge - 55;
            y = plot.grid.height / 2
        } else if (name == 'yRight') {
            if (!plot.scales.yRight || (plot.scales.yRight && !plot.scales.yRight.scale)) {
                return;
            }
            translate = true;
            rotate = 90;
            x = plot.grid.rightEdge + 45;
            y = plot.grid.height / 2;
        } else if (name == 'x') {
            x = plot.grid.leftEdge + (plot.grid.rightEdge - plot.grid.leftEdge) / 2;
            y = plot.grid.height - 10;
        } else if (name == 'main') {
            x = plot.grid.width / 2;
            y = 30;
        }

        if (this.canvas && plot.labels[name] && plot.labels[name].value) {
            labelElements[name].dom.text(plot.labels[name].value);
            if (translate) {
                labelElements[name].dom.attr('transform', 'translate(' + x + ',' + y + ')rotate(' + rotate + ')')
            } else {
                labelElements[name].dom.attr('x', x);
                labelElements[name].dom.attr('y', y);
            }
            if (plot.labels[name].lookClickable == true) {
                renderClickArea.call(this, name);
            }

            if (plot.labels[name].listeners) {
                for (var listener in plot.labels[name].listeners) {
                    if (plot.labels[name].listeners.hasOwnProperty(listener)) {
                        addLabelListener.call(this, name, listener, plot.labels[name].listeners[listener]);
                    }
                }
            }
        }
    };

    var renderLabels = function() {
        for (var name in plot.labels) {
            if (plot.labels.hasOwnProperty(name)) {
                renderLabel.call(this, name);
            }
        }
    };
    
    var clearGrid = function() {
        this.canvas.selectAll('.layer').remove();
        this.canvas.selectAll('.legend').remove();
    };

    var appendTSpans = function(selection, width) {
        var i, words = selection.datum().text.split(' '), segments = [], partial = '', start = 0;

        for (i = 0; i < words.length; i++) {
            partial = partial + words[i] + ' ';
            selection.text(partial);
            if (selection.node().getBBox().width > width) {
                segments.push(words.slice(start, i).join(' '));
                partial = words[i];
                start = i;
            }
        }

        selection.text('');

        segments.push(words.slice(start, i).join(' '));

        selection.selectAll('tspan').data(segments).enter().append('tspan')
                .text(function(d){return d})
                .attr('dy', function(d, i){return i > 0 ? 12 : 0;})
                .attr('x', selection.attr('x'));
    };

    var renderLegendItem = function(selection, plot) {
        var i, xPad, glyphX, textX, yAcc, colorAcc, shapeAcc, textNodes, currentItem, cBBox, pBBox;

        selection.attr('font-family', 'verdana, arial, helvetica, sans-serif').attr('font-size', '10px');

        xPad = plot.scales.yRight && plot.scales.yRight.scale ? 40 : 0;
        glyphX = plot.grid.rightEdge + 30 + xPad;
        textX = glyphX + 15;
        yAcc = function(d, i) {return plot.grid.topEdge + (i * 15);};
        colorAcc = function(d) {return d.color ? d.color : '#000';};
        shapeAcc = function(d) {
            if (d.shape) {
                return d.shape(5);
            }
            return "M" + -5 + "," + -2.5 + "L" + 5 + "," + -2.5 + " " + 5 + "," + 2.5 + " " + -5 + "," + 2.5 + "Z";
        };
        textNodes = selection.append('text').attr('x', textX).attr('y', yAcc);
        textNodes.each(function(){d3.select(this).call(appendTSpans, plot.grid.width - textX);});

        // Now that we've rendered the text, iterate through the nodes and adjust the y values so they no longer overlap.
        // Instead of using selection.each we do this because we need access to the neighboring items.
        for (i = 1; i < selection[0].length; i++) {
            currentItem = selection[0][i];
            cBBox = currentItem.getBBox();
            pBBox = selection[0][i-1].getBBox();

            if (pBBox.y + pBBox.height >= cBBox.y) {
                var newY = parseInt(currentItem.childNodes[0].getAttribute('y')) + Math.abs((pBBox.y + pBBox.height + 3) - cBBox.y);
                currentItem.childNodes[0].setAttribute('y', newY);
            }
        }

        // Append the glyphs.
        selection.append('path')
                .attr('d', shapeAcc)
                .attr('stroke', colorAcc)
                .attr('fill', colorAcc)
                .attr('transform', function() {
                    var sibling = this.parentNode.childNodes[0],
                        y = Math.floor(parseInt(sibling.getAttribute('y'))) - 3.5;
                    return 'translate(' + glyphX + ',' + y + ')';
                });
    };

    var renderLegend = function() {
        var legendData = plot.getLegendData(), legendGroup, legendItems;
        if (legendData.length > 0) {
            if (this.canvas.selectAll('.legend').size() == 0) {
                this.canvas.append('g').attr('class', 'legend');
            }
            legendGroup = this.canvas.select('.legend');
            legendGroup.selectAll('.legend-item').remove(); // remove previous legend items.
            legendItems = legendGroup.selectAll('.legend-item').data(legendData);
            legendItems.exit().remove();
            legendItems.enter().append('g').attr('class', 'legend-item').call(renderLegendItem, plot);
        } else {
            // Remove the legend if it was there previously. Issue 19351.
            this.canvas.select('.legend').remove();
        }
    };

    var getLayer = function(geom) {
        var id = plot.renderTo + '-' + geom.index;
        var layer = this.canvas.select('#' + id);

        if (layer.size() == 0) {
            layer = this.canvas.append('g').attr('class', 'layer').attr('id', id);
        }

        return layer;
    };

    var renderPointGeom = function(data, geom) {
        var layer, anchorSel, pointsSel, xAcc, yAcc, xBinWidth = null, yBinWidth = null, defaultShape, translateAcc,
                colorAcc, sizeAcc, shapeAcc, hoverTextAcc;
        layer = getLayer.call(this, geom);

        if (geom.xScale.scaleType == 'discrete' && geom.position == 'jitter') {
            xBinWidth = ((plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length)) / 2;
            xAcc = function(row) {
                var value = geom.getX(row);
                if (value == null) {return null;}
                return value - (xBinWidth / 2) + (Math.random() * xBinWidth);
            };
        } else {
            xAcc = function(row) {return geom.getX(row);};
        }

        if (geom.yScale.scaleType == 'discrete' && geom.position == 'jitter') {
            yBinWidth = ((plot.grid.topEdge - plot.grid.bottomEdge) / (geom.yScale.scale.domain().length)) / 2;
            yAcc = function(row) {
                var value = geom.getY(row);
                if (value == null || isNaN(value)) {return null;}
                return (value - (yBinWidth / 2) + (Math.random() * yBinWidth));
            }
        } else {
            yAcc = function(row) {
                var value = geom.getY(row);
                if (value == null || isNaN(value)) {return null;}
                return value;
            };
        }

        translateAcc = function(row) {
            var x = xAcc(row), y = yAcc(row);
            if (x == null || isNaN(x) || y == null || isNaN(y)) {
                // If x or y isn't a valid value then we just don't translate the node.
                return null;
            }
            return 'translate(' + xAcc(row) + ',' + yAcc(row) + ')';
        };
        colorAcc = geom.colorAes && geom.colorScale ? function(row) {
            return geom.colorScale.scale(geom.colorAes.getValue(row) + geom.layerName);
        } : geom.color;
        sizeAcc = geom.sizeAes && geom.sizeScale ? function(row) {
            return geom.sizeScale.scale(geom.sizeAes.getValue(row));
        } : function() {return geom.size};
        defaultShape = function(row) {
            var circle = function(s) {return "M0," + s + "A" + s + "," + s + " 0 1,1 0," + -s + "A" + s + "," + s + " 0 1,1 0," + s + "Z";};
            return circle(sizeAcc(row));
        };
        shapeAcc = geom.shapeAes && geom.shapeScale ? function(row) {
            return geom.shapeScale.scale(geom.shapeAes.getValue(row) + geom.layerName)(sizeAcc(row));
        } : defaultShape;
        hoverTextAcc = geom.hoverTextAes ? geom.hoverTextAes.getValue : null;

        data = data.filter(function(d) {
            var x = xAcc(d), y = yAcc(d);
            // Note: while we don't actually use the color or shape here, we need to calculate them so they show up in
            // the legend, even if the points are null.
            if (typeof colorAcc == 'function') { colorAcc(d); }
            if (shapeAcc != defaultShape) { shapeAcc(d);}
            return x !== null && y !== null;
        }, this);

        anchorSel = layer.selectAll('.point').data(data);
        anchorSel.exit().remove();
        anchorSel.enter().append('a').attr('class', 'point').append('path');
        anchorSel.attr('xlink:title', hoverTextAcc);
        pointsSel = anchorSel.select('path');
        pointsSel.attr('d', shapeAcc)
                .attr('fill', colorAcc)
                .attr('stroke', colorAcc)
                .attr('fill-opacity', geom.opacity)
                .attr('stroke-opacity', geom.opacity)
                .attr('transform', translateAcc);

        if (geom.pointClickFnAes) {
            pointsSel.on('click', function(data) {
                geom.pointClickFnAes.value(d3.event, data, layer);
            });
        } else {
            pointsSel.on('click', null);
        }

        if (geom.mouseOverFnAes) {
            pointsSel.on('mouseover', function(data) {
                geom.mouseOverFnAes.value(d3.event, data, layer);
            });
        } else {
            pointsSel.on('mouseover', null);
        }

        if (geom.mouseOutFnAes) {
            pointsSel.on('mouseout', function(data) {
                geom.mouseOutFnAes.value(d3.event, data, layer);
            });
        } else {
            pointsSel.on('mouseout', null);
        }

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    var renderErrorBar = function(layer, plot, geom, data) {
        var colorAcc, sizeAcc, topFn, bottomFn, middleFn, selection, newBars;

        colorAcc = geom.colorAes && geom.colorScale ? function(row) {return geom.colorScale.scale(geom.colorAes.getValue(row) + geom.layerName);} : geom.color;
        sizeAcc = geom.sizeAes && geom.sizeScale ? function(row) {return geom.sizeScale.scale(geom.sizeAes.getValue(row));} : geom.size;
        topFn = function(d) {
            var x, y, value, error;
            x = geom.getX(d);
            value = geom.yAes.getValue(d);
            error = geom.errorAes.getValue(d);
            y = geom.yScale.scale(value + error);
            return LABKEY.vis.makeLine(x - 6, y, x + 6, y);
        };
        bottomFn = function(d) {
            var x, y, value, error;
            x = geom.getX(d);
            value = geom.yAes.getValue(d);
            error = geom.errorAes.getValue(d);
            y = geom.yScale.scale(value - error);
            return LABKEY.vis.makeLine(x - 6, y, x + 6, y);
        };
        middleFn = function(d) {
            var x, value, error;
            x = geom.getX(d);
            value = geom.yAes.getValue(d);
            error = geom.errorAes.getValue(d);
            return LABKEY.vis.makeLine(x, geom.yScale.scale(value + error), x, geom.yScale.scale(value - error));
        };

        data.filter(function(d) {
            // Note: while we don't actually use the color here, we need to calculate it so they show up in the legend,
            // even if the points are null.
            var x = geom.getX(d), y = geom.yAes.getValue(d), error = geom.errorAes.getValue(d);
            if (typeof colorAcc == 'function') { colorAcc(d); }
            return (isNaN(x) || x == null || isNaN(y) || y == null || isNaN(error) || error == null);
        });

        selection = layer.selectAll('.error-bar').data(data);
        selection.exit().remove();

        newBars = selection.enter().append('g').attr('class', 'error-bar');
        newBars.append('path').attr('class','error-bar-top');
        newBars.append('path').attr('class','error-bar-mid');
        newBars.append('path').attr('class','error-bar-bottom');

        selection.selectAll('.error-bar-top').attr('d', topFn).attr('stroke', colorAcc).attr('stroke-width', sizeAcc);
        selection.selectAll('.error-bar-mid').attr('d', bottomFn).attr('stroke', colorAcc).attr('stroke-width', sizeAcc);
        selection.selectAll('.error-bar-bottom').attr('d', middleFn).attr('stroke', colorAcc).attr('stroke-width', sizeAcc);
    };

    var renderErrorBarGeom = function(data, geom) {
        var layer = getLayer.call(this, geom);
        layer.call(renderErrorBar, plot, geom, data);

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    var getBottomWhisker = function(summary) {
        var i = 0, smallestNotOutlier = summary.Q1 - (1.5 * summary.IQR);

        while (summary.sortedValues[i] < smallestNotOutlier) {i++;}

        return summary.sortedValues[i] < summary.Q1 ? summary.sortedValues[i] : null;
    };

    var getTopWhisker = function(summary) {
        var i = summary.sortedValues.length - 1,  largestNotOutlier = summary.Q3 + (1.5 * summary.IQR);

        while (summary.sortedValues[i] > largestNotOutlier) {i--;}

        return summary.sortedValues[i] > summary.Q3 ? summary.sortedValues[i] : null;
    };

    var renderBoxes = function(layer, plot, geom, data) {
        var binWidth, width, whiskers, medians, rects, whiskerFn, heightFn, mLineFn, hoverFn, boxWrappers, xAcc, yAcc;

        binWidth = (plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length);
        width = binWidth / 2;

        xAcc = function(d){return geom.xScale.scale(d.name) - (binWidth / 4)};
        yAcc = function(d){return Math.floor(geom.yScale.scale(d.summary.Q3)) + .5};
        whiskerFn = function(d) {
            var x, top, bottom, offset;
            x = geom.xScale.scale(d.name);
            top = Math.floor(geom.yScale.scale(getTopWhisker(d.summary))) + .5;
            bottom = Math.floor(geom.yScale.scale(getBottomWhisker(d.summary))) + .5;
            offset = width / 4;

            return 'M ' + (x - offset) + ' ' + top +
                    ' L ' + (x + offset) + ' ' + top +
                    ' M ' + x + ' ' + top +
                    ' L ' +  x + ' ' + bottom +
                    ' M ' +  (x - offset) + ' ' + bottom +
                    ' L ' + (x + offset) + ' ' + bottom + ' Z';
        };
        heightFn = function(d) {
            return Math.floor(geom.yScale.scale(d.summary.Q1) - geom.yScale.scale(d.summary.Q3));
        };
        mLineFn = function(d) {
            var x, y;
            x = geom.xScale.scale(d.name);
            y = Math.floor(geom.yScale.scale(d.summary.Q2)) + .5;
            return LABKEY.vis.makeLine(x - (width/2), y, x + (width/2), y);
        };
        hoverFn = geom.hoverTextAes ? function(d) {return geom.hoverTextAes.value(d.name, d.summary)} : null;

        // Here we group each box with an a tag. Each part of a box plot (rect, whisker, etc.) gets its own class.
        boxWrappers = layer.selectAll('a.box').data(data);
        boxWrappers.exit().remove();
        boxWrappers.enter().append('a').attr('class', 'box');
        boxWrappers.attr('xlink:title', hoverFn);

        // Add and style the whiskers
        whiskers = boxWrappers.selectAll('path.box-whisker').data(function(d){return [d];});
        whiskers.exit().remove();
        whiskers.enter().append('path').attr('class', 'box-whisker');
        whiskers.attr('d', whiskerFn).attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);

        // Add and style the boxes.
        rects = boxWrappers.selectAll('rect.box-rect').data(function(d){return [d];});
        rects.exit().remove();
        rects.enter().append('rect').attr('class', 'box-rect');
        rects.attr('x', xAcc).attr('y', yAcc)
                .attr('width', width).attr('height', heightFn)
                .attr('stroke', geom.color).attr('stroke-width', geom.lineWidth)
                .attr('fill', geom.fill).attr('fill-opacity', geom.opacity);

        // Add and style the median lines.
        medians = boxWrappers.selectAll('path.box-mline').data(function(d){return [d];});
        medians.exit().remove();
        medians.enter().append('path').attr('class', 'box-mline');
        medians.attr('d', mLineFn).attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);
    };

    var renderOutliers = function(layer, plot, geom, data) {
        var xAcc, yAcc, xBinWidth = null, yBinWidth = null, defaultShape, translateAcc, colorAcc, shapeAcc, hoverAcc,
                outlierSel, pathSel;

        data = data.filter(function(d) {
            var x = geom.getX(d), y = geom.getY(d);
            return x !== null && y !== null;
        });

        defaultShape = function() {
            var circle = function(s) {return "M0," + s + "A" + s + "," + s + " 0 1,1 0," + -s + "A" + s + "," + s + " 0 1,1 0," + s + "Z";};
            return circle(geom.outlierSize);
        };

        if (geom.xScale.scaleType == 'discrete' && geom.position == 'jitter') {
            xBinWidth = ((plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length)) / 2;
            xAcc = function(row) {return geom.getX(row) - (xBinWidth / 2) + (Math.random() * xBinWidth);};
        } else {
            xAcc = function(row) {return geom.getX(row);};
        }

        if (geom.yScale.scaleType == 'discrete' && geom.position == 'jitter') {
            yBinWidth = ((plot.grid.topEdge - plot.grid.bottomEdge) / (geom.yScale.scale.domain().length)) / 2;
            yAcc = function(row) {return (geom.getY(row) - (yBinWidth / 2) + (Math.random() * yBinWidth));}
        } else {
            yAcc = function(row) {return geom.getY(row);};
        }

        translateAcc = function(row) {return 'translate(' + xAcc(row) + ',' + yAcc(row) + ')';};
        colorAcc = geom.outlierColorAes && geom.colorScale ? function(row) {
            return geom.colorScale.scale(geom.outlierColorAes.getValue(row) + geom.layerName);
        } : geom.outlierFill;
        shapeAcc = geom.outlierShapeAes && geom.shapeScale ? function(row) {
            return geom.shapeScale.scale(geom.outlierShapeAes.getValue(row))(geom.outlierSize);
        } : defaultShape;
        hoverAcc = geom.outlierHoverTextAes ? geom.outlierHoverTextAes.getValue : null;

        outlierSel = layer.selectAll('.outlier').data(data);
        outlierSel.exit().remove();
        outlierSel.enter().append('a').attr('class', 'outlier').append('path');
        outlierSel.attr('xlink:title', hoverAcc).attr('class', 'outlier');

        pathSel = outlierSel.selectAll('path');
        pathSel.attr('d', shapeAcc)
                .attr('transform', translateAcc)
                .attr('fill', colorAcc)
                .attr('stroke', colorAcc)
                .attr('fill-opacity', geom.outlierOpacity)
                .attr('stroke-opacity', geom.outlierOpacity);

        if (geom.pointClickFnAes) {
            pathSel.on('click', function(data) {
               geom.pointClickFnAes.value(d3.event, data);
            });
        }
    };

    var getOutliers = function(rollups, geom) {
        var outliers = [], smallestNotOutlier, largestNotOutlier, y;

        for (var i = 0; i < rollups.length; i++) {
            smallestNotOutlier = rollups[i].summary.Q1 - (1.5 * rollups[i].summary.IQR);
            largestNotOutlier = rollups[i].summary.Q3 + (1.5 * rollups[i].summary.IQR);
            for (var j = 0; j < rollups[i].rawData.length; j++) {
                y = geom.yAes.getValue(rollups[i].rawData[j]);
                if (y != null && (y > largestNotOutlier || y < smallestNotOutlier)) {
                    outliers.push(rollups[i].rawData[j])
                }
            }
        }

        return outliers;
    };

    var prepBoxPlotData = function(data, geom) {
        var groupName, groupedData, summary, summaries = [];

        groupedData = LABKEY.vis.groupData(data, geom.xAes.getValue);
        for (groupName in groupedData) {
            if (groupedData.hasOwnProperty(groupName)) {
                summary = LABKEY.vis.Stat.summary(groupedData[groupName], geom.yAes.getValue);
                if (summary.sortedValues.length > 0) {
                    summaries.push({
                        name: groupName,
                        summary: summary,
                        rawData: groupedData[groupName]
                    });
                }
            }
        }

        return summaries;
    };

    var renderBoxPlotGeom = function(data, geom) {
        var layer = getLayer.call(this, geom), summaries = [];

        if (geom.xScale.scaleType == 'continuous') {
            console.error('Box Plots not supported for continuous data yet.');
            return;
        }

        summaries = prepBoxPlotData(data, geom);

        if (summaries.length > 0) {
            // Render box
            layer.call(renderBoxes, plot, geom, summaries);
            if (geom.showOutliers) {
                // Render the outliers.
                layer.call(renderOutliers, plot, geom, getOutliers(summaries, geom));
            }
        }

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    var renderPaths = function(layer, data, geom) {
        var xAcc = function(d) {return geom.getX(d);},
            yAcc = function(d) {var val = geom.getY(d); return val == null ? null : val;},
            size = geom.sizeAes && geom.sizeScale ? geom.sizeScale.scale(geom.sizeAes.getValue(data)) : function() {return geom.size},
            color = geom.color,
            line = function(d) {
                var path = LABKEY.vis.makePath(d.data, xAcc, yAcc);
                return path.length == 0 ? null : path;
            };
        if (geom.pathColorAes && geom.colorScale) {
            color = function(d) {return geom.colorScale.scale(geom.pathColorAes.getValue(d.data) + geom.layerName);};
        }

        data = data.filter(function(d) {
            return line(d) !== null;
        });

        var pathSel = layer.selectAll('path').data(data);
        pathSel.exit().remove();
        pathSel.enter().append('path');
        pathSel.attr('d', line)
                .attr('class', 'line')
                .attr('stroke', color)
                .attr('stroke-width', size)
                .attr('stroke-opacity', geom.opacity)
                .attr('fill', 'none');
    };

    var renderPathGeom = function(data, geom) {
        var layer = getLayer.call(this, geom);
        var renderableData = [];

        if (geom.groupAes) {
            var groupedData = LABKEY.vis.groupData(data, geom.groupAes.getValue);
            for (var group in groupedData) {
                if (groupedData.hasOwnProperty(group)) {
                    renderableData.push({
                        group: group,
                        data: groupedData[group]
                    });
                }
            }
        } else { // No groupAes specified, so we connect all points.
            renderableData = [{group: null, data: data}];
        }

        layer.call(renderPaths, renderableData, geom);

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    var renderDataspaceBoxes = function(selection, plot, geom) {
        var xBinWidth, padding, boxWidth, boxWrappers, rects, medians, whiskers, xAcc, yAcc, hAcc, whiskerAcc,
                medianAcc, hoverAcc;

        xBinWidth = ((plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length)) / 2;
        padding = Math.max(xBinWidth * .05, 5); // Pad the space between the box and points.
        boxWidth = xBinWidth / 4;

        hAcc = function(d) {return Math.floor(geom.yScale.scale(d.summary.Q1) - geom.yScale.scale(d.summary.Q3));};
        xAcc = function(d) {return geom.xScale.scale(d.name) - boxWidth - padding;};
        yAcc = function(d) {return Math.floor(geom.yScale.scale(d.summary.Q3)) + .5};
        hoverAcc = geom.hoverTextAes ? function(d) {return geom.hoverTextAes.value(d.name, d.summary)} : null;

        whiskerAcc = function(d) {
            var x, top, bottom, offset;
            x = geom.xScale.scale(d.name) - (boxWidth /2) - padding;
            top = Math.floor(geom.yScale.scale(getTopWhisker(d.summary))) +.5;
            bottom = Math.floor(geom.yScale.scale(getBottomWhisker(d.summary))) +.5;
            offset = boxWidth / 4;

            return 'M ' + (x - offset) + ' ' + top +
                    ' L ' + (x + offset) + ' ' + top +
                    ' M ' + x + ' ' + top +
                    ' L ' +  x + ' ' + bottom +
                    ' M ' +  (x - offset) + ' ' + bottom +
                    ' L ' + (x + offset) + ' ' + bottom + ' Z';
        };

        medianAcc = function(d) {
            var x1, x2, y;
            x1 = geom.xScale.scale(d.name) - boxWidth - padding;
            x2 = geom.xScale.scale(d.name) - padding;
            y = Math.floor(geom.yScale.scale(d.summary.Q2)) + .5;
            return LABKEY.vis.makeLine(x1, y, x2, y);
        };

        boxWrappers = selection.selectAll('a.dataspace-box-plot').data(function(d){return [d];});
        boxWrappers.exit().remove();
        boxWrappers.enter().append('a').attr('class', 'dataspace-box-plot');
        boxWrappers.attr('xlink:title', hoverAcc);

        whiskers = boxWrappers.selectAll('path.box-whisker').data(function(d){return [d]});
        whiskers.exit().remove();
        whiskers.enter().append('path').attr('class', 'box-whisker');
        whiskers.attr('d', whiskerAcc).attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);

        rects = boxWrappers.selectAll('rect.box-rect').data(function(d){return [d]});
        rects.exit().remove();
        rects.enter().append('rect').attr('class', 'box-rect');
        rects.attr('x', xAcc).attr('y', yAcc)
                .attr('width', boxWidth).attr('height', hAcc)
                .attr('stroke', geom.color).attr('stroke-width', geom.lineWidth)
                .attr('fill', geom.fill).attr('fill-opacity', geom.opacity);

        medians = boxWrappers.selectAll('path.box-mline').data(function(d){return [d]});
        medians.exit().remove();
        medians.enter().append('path').attr('class', 'box-mline');
        medians.attr('d', medianAcc).attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);
    };

    var renderDataspacePoints = function(selection, plot, geom) {
        var pointWrapper, points, xBinWidth, padding, xAcc, yAcc, translateAcc, defaultShape, colorAcc, sizeAcc,
                shapeAcc, hoverAcc;

        xBinWidth = ((plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length)) / 2;
        padding = Math.max(xBinWidth * .05, 5);

        xAcc = function(row) {
            var x, offset;
            x = geom.getX(row) + padding;
            offset = xBinWidth / 4;

            if (x == null) {return null;}
            return x + (Math.random() * offset);
        };

        yAcc = function(row) {
            var value = geom.getY(row);
            if (value == null || isNaN(value)) {return null;}
            return value;
        };

        translateAcc = function(row) {
            var x = xAcc(row), y = yAcc(row);
            if (x == null || isNaN(x) || y == null || isNaN(y)) {
                // If x or y isn't a valid value then we just don't translate the node.
                return null;
            }
            return 'translate(' + xAcc(row) + ',' + yAcc(row) + ')';
        };

        colorAcc = geom.colorAes && geom.colorScale ? function(row) {
            return geom.colorScale.scale(geom.colorAes.getValue(row) + geom.layerName);
        } : geom.color;

        sizeAcc = geom.sizeAes && geom.sizeScale ? function(row) {
            return geom.sizeScale.scale(geom.sizeAes.getValue(row));
        } : function() {return geom.pointSize};

        defaultShape = function(row) {
            var circle = function(s) {return "M0," + s + "A" + s + "," + s + " 0 1,1 0," + -s + "A" + s + "," + s + " 0 1,1 0," + s + "Z";};
            return circle(sizeAcc(row));
        };

        shapeAcc = geom.shapeAes && geom.shapeScale ? function(row) {
            return geom.shapeScale.scale(geom.shapeAes.getValue(row) + geom.layerName)(sizeAcc(row));
        } : defaultShape;

        hoverAcc = geom.pointHoverTextAes ? geom.pointHoverTextAes.getValue : null;

        pointWrapper = selection.selectAll('a.dataspace-point').data(function(d){
            return d.rawData.filter(function(d){
                var x = geom.getX(d), y = geom.getY(d);
                return x !== null && y !== null;
            });
        });
        pointWrapper.exit().remove();
        pointWrapper.enter().append('a').attr('class', 'dataspace-point');
        pointWrapper.attr('xlink:title', hoverAcc);

        points = pointWrapper.selectAll('path').data(function(d){return [d];});
        points.exit().remove();
        points.enter().append('path');
        points.attr('d', shapeAcc).attr('transform', translateAcc)
                .attr('fill', colorAcc).attr('stroke', colorAcc)
                .attr('fill-opacity', geom.pointOpacity).attr('stroke-opacity', geom.pointOpacity);
    };

    var renderDataspaceBoxGoups = function(layer, plot, geom, summaries) {
        var boxGroups;
        boxGroups = layer.selectAll('g.dataspace-box-group').data(summaries);
        boxGroups.exit().remove();
        boxGroups.enter().append('g').attr('class', 'dataspace-box-group');
        boxGroups.call(renderDataspaceBoxes, plot, geom);
        boxGroups.call(renderDataspacePoints, plot, geom);
    };

    var renderDataspaceBoxPlotGeom = function(data, geom) {
        var layer = getLayer.call(this, geom), summaries;
        if (geom.xScale.scaleType == 'continuous') {
            console.error('Box Plots not supported for continuous data yet.');
            return;
        }

        summaries = prepBoxPlotData(data, geom);

        if (summaries.length > 0) {
            layer.call(renderDataspaceBoxGoups, plot, geom, summaries);
        }
    };

    return {
        initCanvas: initCanvas,
        renderError: renderError,
        renderGrid: renderGrid,
        clearGrid: clearGrid,
        renderLabel: renderLabel,
        renderLabels: renderLabels,
        addLabelListener: addLabelListener,
        clearBrush: clearBrush,
        setBrushExtent: setBrushExtent,
        getBrushExtent: getBrushExtent,
        renderLegend: renderLegend,
        renderPointGeom: renderPointGeom,
        renderPathGeom: renderPathGeom,
        renderErrorBarGeom: renderErrorBarGeom,
        renderBoxPlotGeom: renderBoxPlotGeom,
        renderDataspaceBoxPlotGeom: renderDataspaceBoxPlotGeom
    };
};
