/*
 * Copyright (c) 2013-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!LABKEY.vis.internal) {
    LABKEY.vis.internal = {};
}

LABKEY.vis.internal.Axis = function() {
    // This emulates a lot of the d3.svg.axis() functionality, but adds in the ability for us to have tickHovers,
    // different colored tick & gridlines, etc.
    var scale, orientation, tickFormat = function(v) {return v}, tickHover, tickCls, ticks, tickMouseOver, tickMouseOut,
        tickRectCls, tickRectHeightOffset = 12, tickRectWidthOffset = 8, tickClick, axisSel, tickSel, textSel, gridLineSel,
        borderSel, grid, scalesList = [], gridLinesVisible = 'both', tickDigits, tickValues, tickLabelMax,
        tickColor = '#000000', tickTextColor = '#000000', gridLineColor = '#DDDDDD', borderColor = '#000000',
        tickPadding = 0, tickLength = 8, tickWidth = 1, tickOverlapRotation = 15, gridLineWidth = 1, borderWidth = 1,
        fontFamily = 'Roboto, arial, helvetica, sans-serif', fontSize = 11, adjustedStarts, adjustedEnds, xLogGutterBorder = 0, yLogGutterBorder = 0,
        yGutterXOffset = 0, xGutterYOffset = 0, addLogGutterLabel = false, xGridExtension = 0, yGridExtension = 0, logGutterSel;

    var lowerEqualThanZero = '\u22640', emptyDrawing='M0,0L0,0Z', logGutterWidth = 30;

    var axis = function(selection) {
        var data, textAnchor, textXFn, textYFn, gridLineFn, tickFn, border, gridLineData, hasOverlap, bBoxA, bBoxB, i,
                tickEls, gridLineEls, textAnchors, textEls, logGutterBackground;

        if (tickValues) {
            data = tickValues;
        } else if (scale.ticks) {
            data = scale.ticks(ticks);
        } else {
            data = scale.domain();
        }

        // issue 22297: axis values can end up with rounding issues (i.e. 1.4000000000000001)
        for (i = 0; i < data.length; i++) {
            if (typeof data[i] == "number") {
                data[i] = parseFloat(data[i].toFixed(10));
            }
        }

        // Issue 23016: if you have a large number of tick labels (ex. categorical x-axis),
        // only show 25 since overlapping text isn't helpful
        if (tickLabelMax != undefined && data.length > tickLabelMax)
        {
            var factor = Math.floor(data.length / tickLabelMax),
                tempData = [];
            for (var i = 0; i < data.length; i++)
            {
                if (i % factor == 0)
                {
                    tempData.push(data[i]);
                }
            }
            data = tempData;

            // and increase the rotation if we are showing that many labels
            tickOverlapRotation = Math.max(tickOverlapRotation, 45);
        }

        gridLineData = data;

        if (tickDigits)
        {
            var convert = false;
            for (i=0; i<gridLineData.length; i++)
            {
                if (gridLineData[i].toString().replace('.','').length >= tickDigits)
                {
                    convert = true;
                    break;
                }
            }

            if (convert)
            {
                gridLineData.forEach(function (d, i) {
                    gridLineData[i] = d.toExponential();
                });
            }
        }

        if (orientation == 'left') {
            textAnchor = 'end';
            textYFn = function(v) {
                if (v === lowerEqualThanZero)
                    return grid.bottomEdge + logGutterWidth/2;
                return Math.floor(scale(v)) + .5;
            };
            textXFn = function() {return grid.leftEdge - tickLength - 2 - tickPadding};
            tickFn = function(v) {
                if (v === lowerEqualThanZero)
                    return emptyDrawing;
                v = Math.floor(scale(v)) + .5; return 'M' + grid.leftEdge + ',' + v + 'L' + (grid.leftEdge - tickLength) + ',' + v + 'Z';};
            gridLineFn = function(v) {
                if (v === lowerEqualThanZero)
                    return emptyDrawing;
                v = Math.floor(scale(v)) + .5;
                return 'M' + grid.rightEdge + ',' + v + 'L' + (grid.leftEdge - yGridExtension) + ',' + v + 'Z';
            };
            border = function() {
                return 'M' + (Math.floor(grid.leftEdge) + xGutterYOffset + .5) + ',' + (grid.bottomEdge + 1 + xLogGutterBorder) + 'L' + (Math.floor(grid.leftEdge) + xGutterYOffset + .5) + ',' + grid.topEdge + 'Z';
            };
            logGutterBackground = function() {
                return 'M' + (Math.floor(grid.leftEdge) + xGutterYOffset + .5 - logGutterWidth/2) + ',' + (grid.bottomEdge + 1 - 2) + 'L' + (Math.floor(grid.leftEdge) + xGutterYOffset + .5  - logGutterWidth/2) + ',' + grid.topEdge + 'Z';
            };
            // Don't overlap gridlines with x axis border.
            if (scalesList.indexOf('x') != -1 && Math.floor(scale(data[0])) == Math.floor(grid.bottomEdge)) {
                gridLineData = gridLineData.slice(1);
            } else if (scalesList.indexOf('xTop') != -1 && Math.floor(scale(data[gridLineData.length-1])) == Math.floor(grid.topEdge)) {
                gridLineData = gridLineData.slice(0, gridLineData.length-1)
            }
            if (addLogGutterLabel) {
                // hide the first label so that log gutter label is not cluttered
                gridLineData.shift();
                gridLineData.unshift(lowerEqualThanZero);
            }

        } else if (orientation == 'right') {
            textAnchor = 'start';
            textYFn = function(v) {
                if (v === lowerEqualThanZero)
                    return 0;
                return Math.floor(scale(v)) + .5;
            };
            textXFn = function() {return grid.rightEdge + tickLength + 2 + tickPadding};
            tickFn = function(v) {
                if (v === lowerEqualThanZero)
                    return emptyDrawing;
                v = Math.floor(scale(v)) + .5;
                return 'M' + grid.rightEdge + ',' + v + 'L' + (grid.rightEdge + tickLength) + ',' + v + 'Z';
            };
            gridLineFn = function(v) {
                if (v === lowerEqualThanZero)
                    return emptyDrawing;
                v = Math.floor(scale(v)) + .5; return 'M' + grid.rightEdge + ',' + v + 'L' + (grid.leftEdge + 1) + ',' + v + 'Z';
            };
            border = 'M' + (Math.floor(grid.rightEdge) + .5) + ',' + (grid.bottomEdge + 1) + 'L' + (Math.floor(grid.rightEdge) + .5) + ',' + grid.topEdge + 'Z';
            // Don't overlap gridlines with x axis border.
            if (scalesList.indexOf('x') != -1 && Math.floor(scale(data[0])) == Math.floor(grid.bottomEdge)) {
                gridLineData = gridLineData.slice(1);
            } else if (scalesList.indexOf('xTop') != -1 && Math.floor(scale(data[gridLineData.length-1])) == Math.floor(grid.topEdge)) {
                gridLineData = gridLineData.slice(0, gridLineData.length-1)
            }
            if (addLogGutterLabel) {
                gridLineData.shift();
                gridLineData.unshift(lowerEqualThanZero);
            }
        }else if(orientation == 'top') {
            textAnchor = 'middle';
            textXFn = function(v) {
                if (v === lowerEqualThanZero)
                    return 0;
                return (Math.floor(scale(v)) +.5);
            };
            textYFn = function() {return grid.topEdge - tickPadding};
            tickFn = function(v) {
                if (v === lowerEqualThanZero)
                    return emptyDrawing;
                v = (Math.floor(scale(v)) +.5);
                return 'M' + v + ',' + grid.topEdge + 'L' + v + ',' + (grid.topEdge - tickLength) + 'Z';
            };
            gridLineFn = function(v) {
                if (v === lowerEqualThanZero)
                    return emptyDrawing;
                v = (Math.floor(scale(v)) +.5); return 'M' + v + ',' + grid.bottomEdge + 'L' + v + ',' + grid.topEdge + 'Z';
            };
            border = 'M' + grid.leftEdge + ',' + (Math.floor(grid.topEdge) + .5) + 'L' + grid.rightEdge + ',' + (Math.floor(grid.topEdge) + .5) + 'Z';
            // Don't overlap gridlines with y-left axis border.
            if (scale(data[0]) == grid.leftEdge) {
                gridLineData = gridLineData.slice(1);
            }
            if (addLogGutterLabel) {
                gridLineData.shift();
                gridLineData.unshift(lowerEqualThanZero);
            }
        } else {
            // Assume bottom otherwise.
            orientation = 'bottom';
            textAnchor = 'middle';
            textXFn = function(v) {
                if (v === lowerEqualThanZero)
                    return grid.leftEdge - logGutterWidth/2;
                return (Math.floor(scale(v)) + 0.5);
            };
            textYFn = function() {return grid.bottomEdge + tickLength + 2 + tickPadding};
            tickFn = function(v) {
                if (v === lowerEqualThanZero)
                    return emptyDrawing;

                v = (Math.floor(scale(v)) +.5); return 'M' + v + ',' + grid.bottomEdge + 'L' + v + ',' + (grid.bottomEdge + tickLength) + 'Z';
            };
            gridLineFn = function(v) {
                if (v === lowerEqualThanZero)
                    return emptyDrawing;
                v = (Math.floor(scale(v)) +.5); return 'M' + v + ',' + (grid.bottomEdge + xGridExtension) + 'L' + v + ',' + grid.topEdge + 'Z';};
            border = function() {
                return 'M' + (grid.leftEdge - yLogGutterBorder) + ',' + (Math.floor(grid.bottomEdge) - yGutterXOffset + .5) + 'L' + grid.rightEdge + ',' + (Math.floor(grid.bottomEdge) - yGutterXOffset + .5) + 'Z';
            };
            logGutterBackground = function() {
                return 'M' + (grid.leftEdge - yLogGutterBorder) + ',' + (Math.floor(grid.bottomEdge) - yGutterXOffset + .5 + logGutterWidth/2) + 'L' + grid.rightEdge + ',' + (Math.floor(grid.bottomEdge) - yGutterXOffset + .5 + logGutterWidth/2) + 'Z';
            };
            // Don't overlap gridlines with y-left axis border.
            if (scale(data[0]) == grid.leftEdge) {
                gridLineData = gridLineData.slice(1);
            }
            if (addLogGutterLabel) {
                gridLineData.shift();
                gridLineData.unshift(lowerEqualThanZero);
            }
        }

        if (!axisSel) {
            axisSel = selection.append('g').attr('class', 'axis');
        }

        if (xGridExtension > 0 || yGridExtension > 0) {
            if (!logGutterSel) {
                logGutterSel = axisSel.append('g').attr('class', 'log-gutter');
            }
            logGutterSel.selectAll('path').remove();
            logGutterSel.append('path')
                        .attr('stroke', '#FFFFFF')
                        .attr('stroke-width', logGutterWidth)
                        .attr('d', logGutterBackground);
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

        if (gridLineWidth > 0 ) {
            if (gridLinesVisible === 'both'
                    || (gridLinesVisible === 'x' && (orientation === 'left' || orientation === 'right'))
                    || (gridLinesVisible === 'y' && (orientation === 'top' || orientation === 'bottom')))
            {
                gridLineEls = gridLineSel.selectAll('path').data(gridLineData);
                gridLineEls.exit().remove();
                gridLineEls.enter().append('path');
                gridLineEls.attr('d', gridLineFn)
                        .attr('stroke', gridLineColor)
                        .attr('stroke-width', gridLineWidth);
            }
        }

        textAnchors = textSel.selectAll(tickHover ? 'a' : 'g').data(data);
        textAnchors.exit().remove();
        textAnchors.enter().append(tickHover ? 'a' : 'g').append('text');
        textEls = textAnchors.select('text');
        textEls.text(tickFormat)
                .attr('class', tickCls)
                .attr('x', textXFn)
                .attr('y', textYFn)
                .attr('text-anchor', textAnchor)
                .attr('fill', tickTextColor)
                .style('font', fontSize + 'px ' + fontFamily);

        var addEvents = function(bindTo) {
            if (tickHover) {
                bindTo.append('title').text(tickHover);
            }

            if (tickClick) {
                bindTo.on('click', function() {
                    var args = Array.prototype.slice.call(arguments);
                    tickClick.apply(this, [d3.event, selection].concat(args));
                });
            }

            if (tickMouseOver) {
                bindTo.on('mouseover', tickMouseOver);
            }

            if (tickMouseOut) {
                bindTo.on('mouseout', tickMouseOut);
            }
        };

        addEvents(textEls);

        //Issue 27309: Fill in zero values for empty tick marks when calculating the start and end positions of the tick text rect
        var replaceZerosForTickPositions = function(positionArr)
        {
            var prevNonZero = null, zeroCount = 0, step;

            for (var i = 0; i < positionArr.length; i++)
            {
                if (positionArr[i] != 0)
                {
                    if (zeroCount > 0 && prevNonZero != null)
                    {
                        step = (positionArr[i] - prevNonZero) / (zeroCount + 1);
                        for (var j = zeroCount; j > 0; j--)
                        {
                            prevNonZero += step;
                            positionArr[i - j] = prevNonZero;
                        }
                    }

                    prevNonZero = positionArr[i];
                    zeroCount = 0;
                }
                else
                {
                    zeroCount++;
                }
            }
        };

        //Issue 24497: Adjust axis categorical selection area. Change the x and width of hotspot so that selection capability is more discoverable.
        // Each hotspot extends halfway to its neighbor.
        //The first hotspot starts half way between xaxis start and the x of the 1st label. The last hotspot ends halfway between xaxis end and the end of the last label.
        var adjustTickPosition = function() {
            var starts = [];
            var ends = [];
            var axisStart = null, axisEnd = null, defaultSpacer = 0;

            selection.selectAll('.grid-rect').each(function() {
                if (this) {
                    axisStart = this.getBBox().x;
                    axisEnd = this.getBBox().width + axisStart;
                }
            });

            textSel.selectAll(tickHover ? 'a' : 'g').each(function() {
                if (this) {
                    starts.push(this.getBBox().x);
                    ends.push(this.getBBox().width + this.getBBox().x);
                }
            });

            replaceZerosForTickPositions(starts);
            replaceZerosForTickPositions(ends);

            if (starts.length > 1 && ends.length > 0) {
                defaultSpacer = (starts[1] - ends[0]) / 2;
            }

            adjustedStarts = [];
            adjustedEnds = [];
            for (var i = 0; i < starts.length; i++) {
                if (i < (starts.length - 1)) {
                    adjustedEnds.push(ends[i] + ((starts[i + 1] - ends[i]) / 2));
                }
                else {
                    adjustedEnds.push(ends[i] + (axisEnd != null ? ((axisEnd - ends[i]) / 2) : defaultSpacer));
                }
                if (i > 0) {
                    adjustedStarts.push(starts[i] - ((starts[i] - ends[i - 1]) / 2));
                }
                else {
                    adjustedStarts.push(starts[i] - (axisStart != null ? ((starts[i] - axisStart) / 2) : defaultSpacer));
                }
            }

        };

        var addTickAreaRects = function (anchors, needAdjustment)
        {
            anchors.selectAll('rect.' + (tickRectCls?tickRectCls:"tick-rect")).remove();

            //Issue 24497: Adjust axis categorical selection area. Since overlap labels are already crowded on xaxis, there is no need to expand horizontal selection area for them.
            if (needAdjustment) {
                adjustTickPosition();
            }

            var currentIndex = -1;
            anchors.insert("rect", "text")
                    .attr('class', (tickRectCls?tickRectCls:"tick-rect"))
                    .attr('x', function() {
                        currentIndex = currentIndex + 1;
                        var rectX = needAdjustment ? adjustedStarts[currentIndex]
                                : this.nextSibling.getBBox().x - tickRectWidthOffset/2;
                        return Math.max(rectX, 0);
                    })
                    .attr('y', function() {
                        currentIndex = -1;
                        var rectY = this.nextSibling.getBBox().y - tickRectHeightOffset/2;
                        return Math.max(rectY, 0);
                    })
                    .attr('width', function() {
                        currentIndex = currentIndex + 1;
                        var rectWidth = needAdjustment ? adjustedEnds[currentIndex] - adjustedStarts[currentIndex]
                                : this.nextSibling.getBBox().width + tickRectWidthOffset;
                        return Math.max(rectWidth, 0);
                    })
                    .attr('height', function() {
                        var rectHeight = this.nextSibling.getBBox().height + tickRectHeightOffset;
                        return Math.max(rectHeight, 0);
                    })
                    .attr('fill-opacity', 0);

            addEvents(anchors.select('rect.' + (tickRectCls?tickRectCls:"tick-rect")));
        };

        var addHighlightRects = function (anchors)
        {
            anchors.selectAll('rect.highlight').remove();

            anchors.insert("rect", "text")
                    .attr('class', 'highlight')
                    .attr('x', function() { return this.nextSibling.getBBox().x - 4; })
                    .attr('y', function() { return this.nextSibling.getBBox().y - 3; })
                    .attr('width', function() { return this.nextSibling.getBBox().width + 8; })
                    .attr('height', function() { return this.nextSibling.getBBox().height + 6; })
                    .attr('fill-opacity', 0);

            addEvents(anchors.select('rect.highlight'));
        };

        if (orientation == 'bottom' && textEls.length > 0) {
            hasOverlap = false;
            for (i = 0; i < textEls[0].length - 1; i++) {
                bBoxA = textEls[0][i].getBBox();

                // find the next visible x-axis tick label to check for overlap of the bounding box
                bBoxB = textEls[0][i + 1].getBBox();
                while (bBoxB.x <= 0 && i < textEls[0].length - 2) {
                    i++;
                    bBoxB = textEls[0][i + 1].getBBox();
                }

                if (bBoxB.x > 0 && bBoxA.x + bBoxA.width >= bBoxB.x) {
                    hasOverlap = true;
                    break;
                }
            }
        }
        if (tickHover || tickClick || tickMouseOver || tickMouseOut) {
            addTickAreaRects(textAnchors, !hasOverlap);
            addHighlightRects(textAnchors);
        }

        if (orientation == 'bottom') {
            if (hasOverlap) {
                textEls.attr('transform', function(v) {return 'rotate(' + tickOverlapRotation + ',' + textXFn(v) + ',' + textYFn(v) + ')';})
                        .attr('text-anchor', 'start');

                if (tickHover || tickClick || tickMouseOver || tickMouseOut)
                {
                    addTickAreaRects(textAnchors);
                    textAnchors.selectAll("rect." + (tickRectCls ? tickRectCls : "tick-rect"))
                            .attr('transform', function (v)
                            {
                                return 'rotate(' + tickOverlapRotation + ',' + textXFn(v) + ',' + textYFn(v) + ')';
                            });

                    addHighlightRects(textAnchors);
                    textAnchors.selectAll('rect.highlight')
                            .attr('transform', function (v)
                            {
                                return 'rotate(' + tickOverlapRotation + ',' + textXFn(v) + ',' + textYFn(v) + ')';
                            });
                }

            } else {
                textEls.attr('transform', '');
                textAnchors.selectAll('rect.highlight').attr('transform', '');
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
    axis.tickDigits = function(h) {tickDigits = h; return axis;};
    axis.tickValues = function(h) {tickValues = h; return axis;};
    axis.tickLabelMax = function(h) {tickLabelMax = h; return axis;};
    axis.tickHover = function(h) {tickHover = h; return axis;};
    axis.tickCls = function(c) {tickCls = c; return axis;};
    axis.tickRectCls = function(c) {tickRectCls = c; return axis;};
    axis.tickRectWidthOffset = function(c) {tickRectWidthOffset = c; return axis;};
    axis.tickRectHeightOffset = function(c) {tickRectHeightOffset = c; return axis;};
    axis.tickClick = function(h) {tickClick = h; return axis;};
    axis.tickMouseOver = function(h) {tickMouseOver = h; return axis;};
    axis.tickMouseOut = function(h) {tickMouseOut = h; return axis;};
    axis.scalesList = function(h) {scalesList = h; return axis;};

    axis.xGridExtension = function(p) {
        if (p !== null && p !== undefined) {
            xGridExtension = p;
        }
        return axis;
    };

    axis.yGridExtension = function(p) {
        if (p !== null && p !== undefined) {
            yGridExtension = p;
        }
        return axis;
    };

    axis.addLogGutterLabel = function() {
        addLogGutterLabel = true;
        return axis;
    };

    axis.yGutterXOffset = function(p) {
        if (p !== null && p !== undefined) {
            yGutterXOffset = p;
        }
        return axis;
    };

    axis.xGutterYOffset = function(p) {
        if (p !== null && p !== undefined) {
            xGutterYOffset = p;
        }
        return axis;
    };

    axis.xLogGutterBorder = function(p) {
        if (p !== null && p !== undefined) {
            xLogGutterBorder = p;
        }
        return axis;
    };
    axis.yLogGutterBorder = function(p) {
        if (p !== null && p !== undefined) {
            yLogGutterBorder = p;
        }
        return axis;
    };
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
    axis.gridLinesVisible = function(c) {
        if (c !== undefined && c !== null) {
            gridLinesVisible = c;
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
    axis.fontSize = function(c) {
        if (c !== undefined && c !== null) {
            fontSize = c;
        }
        return axis;
    };
    axis.tickOverlapRotation = function(rotate) {
        if (rotate !== undefined && rotate !== null) {
            tickOverlapRotation = rotate;
        }
        return axis;
    };
    axis.fontFamily = function(f) {
        if (f !== undefined && f !== null) {
            fontFamily = f;
        }
        return axis;
    };

    return axis;
};

LABKEY.vis.internal.D3Renderer = function(plot) {
    var errorMsg, labelElements = null, labelBkgds = null,
        xAxis = null, xTopAxis = null, leftAxis = null, rightAxis = null,
        brush = null, brushSel = null, brushSelectionType = null,
        xHandleBrush = null, xHandleSel = null, yHandleBrush = null, yHandleSel = null,
        defaultBrushFillColor = '#EBF7F8', defaultBrushFillOpacity = .75, defaultBrushStrokeColor = '#14C9CC',
        defaultAxisFontSize = 14, defaultMainFontSize = 18, defaultSubtitleFontSize = 16, defaultHandleLen = 30;

    var isMainPlot = plot.isMainPlot, xLogGutter = plot.xLogGutter, yLogGutter = plot.yLogGutter,
        isShowYAxisGutter = plot.isShowYAxisGutter, isShowXAxisGutter = plot.isShowXAxisGutter;

    // adjust the plot range to reserve space for log gutter
    var logGutterWidth = 30;

    // the amount user need to drag in order to brush log gutter,
    // after implementing the 'snap' feature, would want to reduce this to 1
    var logGutterBrushExtent = 15;

    // position log gutter data relative to x or y axis
    var logGutterPointsOffset = 15;

    var initLabelElements = function() {
        labelElements = {}; labelBkgds = {};
        var fontFamily = plot.fontFamily ? plot.fontFamily : 'Roboto, arial, helvetica, sans-serif';
        var labelBkgd = null;
        var labels = this.canvas.append('g').attr('class', plot.renderTo + '-labels');

        var appendLabelElement = function(name, defaultFontSize) {
            var labelEl = {};
            var fontSize = plot.labels[name] && plot.labels[name].fontSize != undefined ? plot.labels[name].fontSize : defaultFontSize;

            labelEl.dom = labels.append('text').attr('text-anchor', 'middle')
                    .attr("visibility", plot.labels[name] && plot.labels[name].visibility ? plot.labels[name].visibility : "visible")
                    .style('font', fontSize + 'px ' + fontFamily);

            if(plot.labels[name] && plot.labels[name].cls) {
                labelEl.dom.attr("class", plot.labels[name].cls);
            }

            return labelEl;
        };

        var appendLabelBkgd = function(name, canvas, defaultWidth, defaultHeight) {
            var labelBkgdEl = {}, width, height, x, y;

            // No need to add rects if no color defined
            if(!plot.labels[name] || !plot.labels[name].bkgdColor) {
                return;
            }

            if(labelBkgd === null || labelBkgd.empty()) {
                labelBkgd = canvas.insert('g', 'g.axis').attr('class', 'labelBkgd');
            }

            if(name === 'x' || name === 'xTop') {
                width = plot.labels[name] && plot.labels[name].bkgdWidth ? plot.labels[name].bkgdWidth : plot.grid.width;
                height = plot.labels[name] && plot.labels[name].bkgdHeight ? plot.labels[name].bkgdHeight : defaultHeight;
            } else {
                width = plot.labels[name] && plot.labels[name].bkgdWidth ? plot.labels[name].bkgdWidth : defaultWidth;
                height = plot.labels[name] && plot.labels[name].bkgdHeight ? plot.labels[name].bkgdHeight : plot.grid.height;
            }

            if(name === 'x') {
                x = plot.grid.leftEdge;
                y = plot.grid.bottomEdge;
            } else if(name === 'xTop') {
                x = plot.grid.leftEdge;
                y = plot.grid.topEdge - height;
            } else if(name === 'yRight') {
                x = plot.grid.rightEdge;
                y = plot.grid.topEdge;
            } else {
                x = plot.grid.leftEdge - width;
                y = plot.grid.topEdge;
            }

            labelBkgdEl.dom = labelBkgd.append('rect')
                    .attr('fill-opacity',plot.labels[name] && plot.labels[name].bkgdColor ? 1 : 0)
                    .attr('height', height)
                    .attr('width', width)
                    .attr('fill', plot.labels[name] && plot.labels[name].bkgdColor ? plot.labels[name].bkgdColor : '#FFFFFF')
                    .attr('x', x)
                    .attr('y', y);

            return labelBkgdEl;
        };

        labelBkgds.x = appendLabelBkgd('x', this.canvas, null, 30);
        labelBkgds.xTop = appendLabelBkgd('xTop', this.canvas, null, 30);
        labelBkgds.y = appendLabelBkgd('yLeft', this.canvas, 30, null);
        labelBkgds.yLeft = labelBkgds.y;
        labelBkgds.yRight = appendLabelBkgd('yRight', this.canvas, 30, null);

        labelElements.main = appendLabelElement('main', defaultMainFontSize);
        labelElements.subtitle = appendLabelElement('subtitle', defaultSubtitleFontSize);
        labelElements.x = appendLabelElement('x', defaultAxisFontSize);
        labelElements.xTop = appendLabelElement('xTop', defaultAxisFontSize);
        labelElements.y = appendLabelElement('yLeft', defaultAxisFontSize);
        labelElements.yLeft = labelElements.y;
        labelElements.yRight = appendLabelElement('yRight', defaultAxisFontSize);
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
            .gridLinesVisible(plot.gridLinesVisible)
            .gridLineWidth(plot.gridLineWidth)
            .tickColor(plot.tickColor)
            .tickLength(plot.tickLength)
            .tickWidth(plot.tickWidth)
            .borderColor(plot.borderColor)
            .borderWidth(plot.borderWidth)
            .tickTextColor(plot.tickTextColor)
            .fontSize(plot.fontSize)
            .tickOverlapRotation(plot.tickOverlapRotation)
            .fontFamily(plot.fontFamily)
            .scalesList(Object.keys(plot.scales))
    };

    var updateIndividualAxisConfig = function(indAxis, name) {
        if (indAxis && plot.scales[name])
        {
            if (plot.scales[name].tickValues) {
                indAxis.tickValues(plot.scales[name].tickValues);
            }
            
            if (plot.scales[name].tickFormat) {
                indAxis.tickFormat(plot.scales[name].tickFormat);
            }

            if (plot.scales[name].tickDigits) {
                indAxis.tickDigits(plot.scales[name].tickDigits);
            }

            if (plot.scales[name].tickLabelMax) {
                indAxis.tickLabelMax(plot.scales[name].tickLabelMax);
            }

            if (plot.scales[name].tickHoverText) {
                indAxis.tickHover(plot.scales[name].tickHoverText);
            }

            if (plot.scales[name].tickCls) {
                indAxis.tickCls(plot.scales[name].tickCls);
            }

            if (plot.scales[name].tickRectCls) {
                indAxis.tickRectCls(plot.scales[name].tickRectCls);
            }

            if (plot.scales[name].tickRectWidthOffset) {
                indAxis.tickRectWidthOffset(plot.scales[name].tickRectWidthOffset);
            }

            if (plot.scales[name].tickRectHeightOffset) {
                indAxis.tickRectHeightOffset(plot.scales[name].tickRectHeightOffset);
            }

            if (plot.scales[name].tickClick) {
                indAxis.tickClick(plot.scales[name].tickClick);
            }

            if (plot.scales[name].tickMouseOver) {
                indAxis.tickMouseOver(plot.scales[name].tickMouseOver);
            }

            if (plot.scales[name].tickMouseOut) {
                indAxis.tickMouseOut(plot.scales[name].tickMouseOut);
            }

            if (plot.scales[name].fontSize) {
                indAxis.fontSize(plot.scales[name].fontSize);
            }
        }
    };

    var renderXAxis = function()
    {
        if (!xAxis)
            xAxis = LABKEY.vis.internal.Axis().orient('bottom');

        if (plot.scales.x && plot.scales.x.scale)
        {
            var padding = 10, xBorderExtension = 0, gridExtension = 0, xScaleName = 'x';

            if (plot.scales.xSub && plot.scales.xSub.scale)
                xScaleName = 'xSub';

            if (xLogGutter && isMainPlot)
            {
                padding = 40;
                gridExtension = logGutterWidth;
            }

            if (yLogGutter)
            {
                if (isMainPlot)
                    xBorderExtension = logGutterWidth;
                xAxis.addLogGutterLabel();
            }

            xAxis.yLogGutterBorder(xBorderExtension).xGridExtension(gridExtension);
            xAxis.scale(plot.scales[xScaleName].scale).tickPadding(padding).ticks(7);
            configureAxis(xAxis);
            updateIndividualAxisConfig(xAxis, xScaleName);
            if (isShowXAxisGutter)
                xAxis.yGutterXOffset(logGutterWidth).borderColor("#CCC8C8");
            this.canvas.call(xAxis);
        }
    };

    var renderXTopAxis = function() {
        if (!xTopAxis) {
            xTopAxis = LABKEY.vis.internal.Axis().orient('top');
        }

        if (yLogGutter) {
            xTopAxis.addLogGutterLabel();
        }

        if (plot.scales.xTop && plot.scales.xTop.scale)
        {
            xTopAxis.scale(plot.scales.xTop.scale).tickPadding(10).ticks(7);
            configureAxis(xTopAxis);
            updateIndividualAxisConfig(xTopAxis, 'xTop');
            this.canvas.call(xTopAxis);
        }
    };

    var renderYLeftAxis = function() {
        if (!leftAxis) {
            leftAxis = LABKEY.vis.internal.Axis().orient('left');
        }

        if (plot.scales.yLeft && plot.scales.yLeft.scale) {
            var padding = 0;
            var yBorderExtension, gridExtension= 0;
            if (yLogGutter && isMainPlot) {
                padding = 30;
                gridExtension = logGutterWidth;
            }

            if (xLogGutter) {
                if (isMainPlot) {
                    yBorderExtension = logGutterWidth;
                    leftAxis.addLogGutterLabel();
                }
            }

            leftAxis.scale(plot.scales.yLeft.scale).tickPadding(padding).ticks(10).xLogGutterBorder(yBorderExtension).yGridExtension(gridExtension);
            configureAxis(leftAxis);
            updateIndividualAxisConfig(leftAxis, 'yLeft');
            if (isShowYAxisGutter) {
                leftAxis.xGutterYOffset(logGutterWidth).borderColor("#CCC8C8");
            }
            this.canvas.call(leftAxis);
        }
    };

    var renderYRightAxis = function() {
        if (!rightAxis) {
            rightAxis = LABKEY.vis.internal.Axis().orient('right');
        }
        if (xLogGutter) {
            rightAxis.addLogGutterLabel();
        }
        if (plot.scales.yRight && plot.scales.yRight.scale) {
            rightAxis.scale(plot.scales.yRight.scale).ticks(10);
            configureAxis(rightAxis);
            updateIndividualAxisConfig(rightAxis, 'yRight');
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
            allSels.push(d3.selectAll('#' + plot.renderTo + '-' + plot.layers[i].geom.index));
        }

        return allSels;
    };

    var addXBrush = function(brush, brushSel) {
        var xBrushStart, xBrush, xBrushEnd,
            brushStrokeColor = plot.brushing.strokeColor || defaultBrushStrokeColor,
            height = plot.brushing.handleLen || defaultHandleLen;

        if (!xHandleSel) {
            xHandleSel = this.canvas.insert('g', '.brush').attr('class', 'x-axis-handle');
            xHandleBrush = d3.svg.brush();
        }

        xHandleBrush.x(brush.x());
        xHandleSel.call(xHandleBrush);

        xBrushStart = function(){
            if (brushSelectionType == 'y' || brushSelectionType === null) {
                brushSelectionType = 'x';
                if (yHandleBrush) {
                    yHandleBrush.clear();
                    yHandleBrush(yHandleSel);
                }
            }

            brush.on('brushstart')('x');
        };

        xBrush = function() {
            var bEx = brush.extent(),
                    xEx = xHandleBrush.extent(),
                    yEx = [],
                    newEx = [];

            if (yHandleBrush) {
                if (yHandleBrush.empty()) {
                    yEx = brush.y().domain();
                } else {
                    yEx = [bEx[0][1], bEx[1][1]];
                }

                newEx[0] = [xEx[0], yEx[0]];
                newEx[1] = [xEx[1], yEx[1]];
            } else {
                newEx = xEx;
            }

            brush.extent(newEx);
            brush(brushSel);
            brush.on('brush')('x');
        };

        xBrushEnd = function() {
            if (xHandleBrush.empty()) {
                brushSelectionType = null;
                if (yHandleBrush) {
                    yHandleBrush.clear();
                    yHandleBrush(yHandleSel);
                }
                brush.clear();
                brush(brushSel);
                brush.on('brush')();
            }

            brush.on('brushend')();
        };

        if (plot.brushing.dimension == 'xTop')
            xHandleSel.attr('transform', 'translate(0, ' + (plot.grid.topEdge - height) + ')');
        else
            xHandleSel.attr('transform', 'translate(0,' + plot.grid.bottomEdge + ')');
        xHandleSel.selectAll('rect').attr('height', height);
        xHandleSel.selectAll('.extent').attr('opacity', 0);
        xHandleSel.select('.resize.e rect')
                .attr('class', 'e-resize-handle-rect')
                .attr('fill', brushStrokeColor).attr('style', null);
        xHandleSel.select('.resize.w rect')
                .attr('class', 'w-resize-handle-rect')
                .attr('fill', brushStrokeColor).attr('style', null);
        xHandleBrush.on('brushstart', xBrushStart);
        xHandleBrush.on('brush', xBrush);
        xHandleBrush.on('brushend', xBrushEnd);
    };

    var addYBrush = function(brush, brushSel) {
        var yBrushStart, yBrush, yBrushEnd, width = plot.brushing.handleLen || defaultHandleLen,
            brushStrokeColor = plot.brushing.strokeColor || defaultBrushStrokeColor;

        if (!yHandleSel) {
            yHandleSel = this.canvas.insert('g', '.brush').attr('class', 'y-axis-handle');
            yHandleBrush = d3.svg.brush();
        }

        yHandleBrush.y(brush.y());
        yHandleSel.call(yHandleBrush);

        yBrushStart = function() {
            if (brushSelectionType == 'x' || brushSelectionType == null) {
                brushSelectionType = 'y';
                if (xHandleBrush) {
                    xHandleBrush.clear();
                    xHandleBrush(xHandleSel);
                }
            }

            brush.on('brushstart')('y');
        };

        yBrush = function() {
            var bEx = brush.extent(),
                    yEx = yHandleBrush.extent(),
                    xEx = [],
                    newEx = [];

            if (xHandleBrush) {
                if (xHandleBrush.empty()) {
                    xEx = brush.x().domain();
                } else {
                    xEx = [bEx[0][0], bEx[1][0]];
                }

                newEx[0] = [xEx[0], yEx[0]];
                newEx[1] = [xEx[1], yEx[1]];
            } else {
                newEx = yEx;
            }

            brush.extent(newEx);
            brush(brushSel);
            brush.on('brush')('y');
        };

        yBrushEnd = function() {
            if (yHandleBrush.empty()) {
                brushSelectionType = null;
                if (xHandleBrush) {
                    xHandleBrush.clear();
                    xHandleBrush(xHandleSel);
                }
                brush.clear();
                brush(brushSel);
                brush.on('brush')();
            }

            brush.on('brushend')();
        };

        if (plot.brushing.dimension == 'yRight')
            yHandleSel.attr('transform', 'translate(' + plot.grid.rightEdge + ',0)');
        else
            yHandleSel.attr('transform', 'translate(' + (plot.grid.leftEdge - width) + ',0)');
        yHandleSel.selectAll('rect').attr('width', width);
        yHandleSel.selectAll('.extent').attr('opacity', 0);
        yHandleSel.select('.resize.n rect').attr('fill-opacity', 1).attr('fill', brushStrokeColor).attr('style', null);
        yHandleSel.select('.resize.s rect').attr('fill-opacity', 1).attr('fill', brushStrokeColor).attr('style', null);
        yHandleBrush.on('brushstart', yBrushStart);
        yHandleBrush.on('brush', yBrush);
        yHandleBrush.on('brushend', yBrushEnd);
    };

    var addBrushHandles = function(brush, brushSel){
        if(!plot.brushing.dimension || plot.brushing.dimension == 'x' || plot.brushing.dimension == 'xTop' || plot.brushing.dimension == 'both') {
            addXBrush.call(this, brush, brushSel);
        }

        if(!plot.brushing.dimension || plot.brushing.dimension == 'y' || plot.brushing.dimension == 'yRight' || plot.brushing.dimension == 'both') {
            addYBrush.call(this, brush, brushSel);
        }
    };

    var handleMove = function(handle){
        var ex = brush.extent();
        if (handle === undefined) {
            // Brush event fired from main brush surface
            if (brushSelectionType == 'x' || brushSelectionType == 'both' && xHandleBrush) {
                if (yHandleBrush) {
                    xHandleBrush.extent([ex[0][0], ex[1][0]]);
                } else {
                    xHandleBrush.extent(ex);
                }
                xHandleBrush(xHandleSel);
            }

            if (brushSelectionType == 'y' || brushSelectionType == 'both' && yHandleBrush) {
                if (xHandleBrush) {
                    yHandleBrush.extent([ex[0][1], ex[1][1]]);
                } else {
                    yHandleBrush.extent(ex);
                }
                yHandleBrush(yHandleSel);
            }
        } else if (handle === 'x' && brushSelectionType == 'both' && yHandleBrush) {
            // Only update the x handle if not in a 1D selection.
            yHandleBrush.extent([ex[0][1], ex[1][1]]);
            yHandleBrush(yHandleSel);
        } else if (handle === 'y' && brushSelectionType == 'both' && xHandleBrush) {
            // Only update the y handle if not in a 1D selection.
            xHandleBrush.extent([ex[0][0], ex[1][0]]);
            xHandleBrush(xHandleSel);
        }
    };

    var handleResize = function(handle){
        var ex = brush.extent(), yD, xD, xEx, yEx;

        if (xHandleBrush && yHandleBrush) {
            xEx = [ex[0][0], ex[1][0]];
            yEx = [ex[0][1], ex[1][1]];
        } else if (xHandleBrush) {
            xEx = ex;
        } else if(yHandleBrush) {
            yEx = ex;
        }

        if (yHandleBrush) {
            yD = yHandleBrush.y().domain();
        }

        if (xHandleBrush) {
            xD = xHandleBrush.x().domain();
        }

        if (handle === undefined) {// Brush event fired from main brush surface

            // If we have 1D selection, but the user adjusts the other dimension, change to a 2D selection.
            if (brushSelectionType == 'x' && yD) {
                if (yEx[0] > yD[0] || yEx[1] < yD[1]) {
                    brushSelectionType = 'both';
                }
            }

            // If we have 1D selection, but the user adjusts the other dimension, change to a 2D selection.
            if (brushSelectionType == 'y' && xD) {
                if (xEx[0] > xD[0] || xEx[1] < xD[1]) {
                    brushSelectionType = 'both';
                }
            }

            if ((brushSelectionType == 'x' || brushSelectionType == 'both') && xHandleBrush) {
                if (xHandleBrush && yHandleBrush) {
                    xHandleBrush.extent([ex[0][0], ex[1][0]]);
                } else {
                    xHandleBrush.extent(ex);
                }

                xHandleBrush(xHandleSel);
            }

            if ((brushSelectionType == 'y' || brushSelectionType == 'both') && yHandleBrush) {
                if (xHandleBrush && yHandleBrush) {
                    yHandleBrush.extent([ex[0][1], ex[1][1]]);
                } else {
                    yHandleBrush.extent(ex);
                }

                yHandleBrush(yHandleSel);
            }
        } else if (handle === 'x' && brushSelectionType == 'both' && yHandleBrush) {
            // Only update the x handle if not in a 1D selection.
            yHandleBrush.extent([ex[0][1], ex[1][1]]);
            yHandleBrush(yHandleSel);
        } else if (handle === 'y' && brushSelectionType == 'both' && xHandleBrush) {
            // Only update the y handle if not in a 1D selection.
            xHandleBrush.extent([ex[0][0], ex[1][0]]);
            xHandleBrush(xHandleSel);
        }
    };

    var addBrush = function(){
        if (plot.brushing != null && (plot.brushing.brushstart || plot.brushing.brush || plot.brushing.brushend ||
                plot.brushing.brushclear)) {
            var xScale, yScale, scalePadding = 1, xAxis, yAxis;

            if(brush == null) {
                brush = d3.svg.brush();
                brushSel = this.canvas.insert('g', '.layer').attr('class', 'brush');
            }

            if (plot.scales.xTop) {
                xAxis = plot.scales.xTop;
            } else {
                xAxis = plot.scales.x;
            }

            if (xAxis.scaleType == 'continuous' && xAxis.trans == 'linear')
            {
                // We need to add some padding to the scale in order for us to actually be able to select all of the points.
                // If we don't, any points that lie exactly at the edge of the chart will be unselectable.
                xScale = xAxis.scale.copy();
                xScale.domain([xScale.invert(plot.grid.leftEdge - scalePadding), xScale.invert(plot.grid.rightEdge + scalePadding)]);
                xScale.range([plot.grid.leftEdge - scalePadding, plot.grid.rightEdge + scalePadding]);
            }
            else
            {
                xScale = xAxis.scale;
                if (isMainPlot && yLogGutter) {
                    xScale.domain([xScale.invert(plot.grid.leftEdge - logGutterWidth), xScale.invert(plot.grid.rightEdge)]);
                    xScale.range([plot.grid.leftEdge - logGutterWidth, plot.grid.rightEdge]);
                }
            }

            if (plot.scales.yLeft) {
                yAxis = plot.scales.yLeft;
            } else {
                yAxis = plot.scales.yRight;
            }

            if (yAxis.scaleType == 'continuous' && yAxis.trans == 'linear')
            {
                // See the note above.
                yScale = yAxis.scale.copy();
                yScale.domain([yScale.invert(plot.grid.bottomEdge + scalePadding), yScale.invert(plot.grid.topEdge - scalePadding)]);
                yScale.range([plot.grid.bottomEdge + scalePadding, plot.grid.topEdge - scalePadding]);
            }
            else
            {
                yScale = yAxis.scale;
                if (isMainPlot && xLogGutter) {
                    yScale.domain([yScale.invert(plot.grid.bottomEdge + logGutterWidth), yScale.invert(plot.grid.topEdge - scalePadding)]);
                    yScale.range([plot.grid.bottomEdge + logGutterWidth, plot.grid.topEdge - scalePadding]);
                }
            }

            if (!plot.brushing.dimension || plot.brushing.dimension == 'x' || plot.brushing.dimension == 'xTop' || plot.brushing.dimension == 'both') {
                brush.x(xScale);
            }

            if (!plot.brushing.dimension || plot.brushing.dimension == 'y' || plot.brushing.dimension == 'yRight' || plot.brushing.dimension == 'both') {
                brush.y(yScale);
            }

            brushSel.call(brush);

            if (plot.brushing.dimension == 'y' || plot.brushing.dimension == 'yRight') {
                // Manually set width of brush.
                brushSel.selectAll('rect')
                    .attr('x', plot.grid.leftEdge)
                    .attr('width', plot.grid.rightEdge - plot.grid.leftEdge);
            } else if(plot.brushing.dimension == 'x' || plot.brushing.dimension == 'xTop') {
                // Manually set height of brush.
                brushSel.selectAll('rect')
                    .attr('y', plot.grid.topEdge)
                    .attr('height', plot.grid.bottomEdge - plot.grid.topEdge);
            }

            brushSel.selectAll('.extent')
                    .attr('opacity', plot.brushing.fillOpacity || defaultBrushFillOpacity)
                    .attr('fill', plot.brushing.fillColor || defaultBrushFillColor)
                    .attr('stroke', plot.brushing.strokeColor || defaultBrushStrokeColor)
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
                var allData = getAllData(), event = d3.event;

                if (brush.empty()) {
                    brushSelectionType = null;

                    if (xHandleBrush) {
                        xHandleBrush.clear();
                        xHandleBrush(xHandleSel);
                    }

                    if (yHandleBrush) {
                        yHandleBrush.clear();
                        yHandleBrush(yHandleSel);
                    }

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
            var yAxisZero = Number.MAX_VALUE;
            var xAxisZero = Number.MAX_VALUE;

            // for log gutter, invert of bottomEdge is the 0 on axis
            // brushing in main plot
            if (xLogGutter && plot.scales.yLeft && plot.scales.yLeft.scale && plot.scales.yLeft.scale.invert) {
                yAxisZero = plot.scales.yLeft.scale.invert(plot.grid.bottomEdge + logGutterBrushExtent);
            }
            // brushing in gutter plot
            else if (xLogGutter && plot.scales.yRight && plot.scales.yRight.scale && plot.scales.yRight.scale.invert) {
                yAxisZero = plot.scales.yRight.scale.invert(plot.grid.bottomEdge - logGutterBrushExtent);
            }

            if (yLogGutter && plot.scales.x && plot.scales.x.scale && plot.scales.x.scale.invert) {
                xAxisZero = plot.scales.x.scale.invert(plot.grid.leftEdge - logGutterBrushExtent);
            }
            else if (yLogGutter && plot.scales.xTop && plot.scales.xTop.scale && plot.scales.xTop.scale.invert) {
                xAxisZero = plot.scales.xTop.scale.invert(plot.grid.leftEdge + logGutterBrushExtent);
            }

            if (xHandleBrush && yHandleBrush) {
                if (xAxisZero !== Number.MAX_VALUE && extent[0][0] < xAxisZero && yLogGutter) {
                    extent[0][0] = Number.NEGATIVE_INFINITY;
                }
                if (yAxisZero !== Number.MAX_VALUE && extent[0][1] < yAxisZero && xLogGutter) {
                    extent[0][1] = Number.NEGATIVE_INFINITY;
                }
            }
            else if (xHandleBrush && yLogGutter) {
                if (xAxisZero !== Number.MAX_VALUE && extent[0] < xAxisZero)
                    extent[0] = Number.NEGATIVE_INFINITY;
            }
            else if (yHandleBrush && xLogGutter) {
                if (yAxisZero !== Number.MAX_VALUE && extent[0] < yAxisZero)
                    extent[0] = Number.NEGATIVE_INFINITY;
            }

            if (brushSelectionType == 'both') {
                if (xHandleBrush && yHandleBrush) {
                    return extent;
                } else if (xHandleBrush) {
                    return [
                        [extent[0], null],
                        [extent[1], null]
                    ];
                } else if (yHandleBrush) {
                    return [
                        [null, extent[0]],
                        [null, extent[1]]
                    ];
                }
            } else if (brushSelectionType == 'x') {
                if (xHandleBrush && yHandleBrush) {
                    return [
                        [extent[0][0], null],
                        [extent[1][0], null]
                    ];
                }
                return [
                    [extent[0], null],
                    [extent[1], null]
                ];
            } else if (brushSelectionType == 'y') {
                if (xHandleBrush && yHandleBrush) {
                    return [
                        [null, extent[0][1]],
                        [null, extent[1][1]]
                    ];
                }
                return [
                    [null, extent[0]],
                    [null, extent[1]]
                ];
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

    var setBrushing = function(brushingConfig) {
        plot.brushing = brushingConfig;
        addBrush.call(this);
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
            if(yHandleBrush) {
                yHandleBrush.clear();
                yHandleBrush(yHandleSel);
            }
            xHandleBrush.on('brush')();
            xHandleBrush.on('brushend')();
        } else if (xIsNull && !yIsNull) {
            brushSelectionType = 'y';
            yHandleBrush.extent([extent[0][1], extent[1][1]]);
            yHandleBrush(yHandleSel);
            if(xHandleBrush) {
                xHandleBrush.clear();
                xHandleBrush(xHandleSel);
            }
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

        if (!plot.disableAxis.xBottom) { renderXAxis.call(this); }
        if (!plot.disableAxis.xTop) { renderXTopAxis.call(this); }
        if (!plot.disableAxis.yLeft) { renderYLeftAxis.call(this); }
        if (!plot.disableAxis.yRight) { renderYRightAxis.call(this); }

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

        if(plot.labels[name] && typeof plot.labels[name].rotate !== 'undefined') {
            translate = true;
            rotate = plot.labels[name].rotate;
        }

        if ((name == 'y' || name == 'yLeft')) {
            if (!plot.scales.yLeft || (plot.scales.yLeft && !plot.scales.yLeft.scale)) {
                return;
            }
            translate = true;
            if(typeof rotate === 'undefined')
                rotate = 270;
            x = plot.grid.leftEdge - (plot.labels[name] && plot.labels[name].position != undefined ? plot.labels[name].position : 55);
            y = plot.grid.height / 2
        } else if (name == 'yRight') {
            if (!plot.scales.yRight || (plot.scales.yRight && !plot.scales.yRight.scale)) {
                return;
            }
            translate = true;
            if(typeof rotate === 'undefined')
                rotate = 90;
            x = plot.grid.rightEdge + (plot.labels[name].position != undefined ? plot.labels[name].position : 55);
            y = plot.grid.height / 2;
        } else if (name == 'x') {
            x = plot.grid.leftEdge + (plot.grid.rightEdge - plot.grid.leftEdge) / 2;
            y = plot.grid.height - (plot.labels[name].position != undefined ? plot.labels[name].position : 10);
        } else if (name == 'xTop') {
            x = plot.grid.leftEdge + (plot.grid.rightEdge - plot.grid.leftEdge) / 2;
            y = plot.grid.topEdge - (plot.labels[name].position != undefined ? plot.labels[name].position : 25);
        } else if (name == 'main') {
            x = plot.grid.width / 2;
            y = plot.labels[name].position != undefined ? plot.labels[name].position : 30;
        }else if (name == 'subtitle') {
            x = plot.grid.width / 2;
            y = plot.labels[name].position != undefined ? plot.labels[name].position : 50;
        }

        if (this.canvas && plot.labels[name] && plot.labels[name].value) {
            if (typeof plot.labels[name].maxCharPerLine !== 'undefined')
            {
                if (plot.labels[name].value.length > plot.labels[name].maxCharPerLine)
                {
                    var fontSize = plot.scales[name].fontSize ? plot.scales[name].fontSize : defaultAxisFontSize;
                    var tspanEl, textWidth = null;

                    // Divide label into maxCharPerLine chunks
                    var regex = new RegExp("(.{1," + plot.labels[name].maxCharPerLine + "})", 'g');
                    var values = plot.labels[name].value.match(regex);

                    // Insert each chunk into tspans
                    for (var i=0; i<values.length; i++)
                    {
                        var calcX, calcY, align = 'middle';

                        if(typeof plot.labels[name].lineWrapAlign !== 'undefined')
                            align = plot.labels[name].lineWrapAlign;


                        tspanEl = labelElements[name].dom.insert('tspan', 'text')
                                .attr('dy',fontSize)
                                .attr('text-anchor',align)
                                .text(values[i]);

                        // Align tspans
                        if(textWidth === null)
                            textWidth = tspanEl[0][0].getComputedTextLength();

                        if (name === 'x' || name === 'xTop')
                        {
                            if(align === 'start') {
                                calcX = x - textWidth/2;
                            }
                            else if(align === 'end') {
                                calcX = x + textWidth/2;
                            }
                            else {
                                calcX = x;
                            }
                            calcY = y + fontSize*i;
                        }
                        else
                        {
                            calcX = 0;
                            calcY = 0 - (fontSize*values.length/2)*(values.length-i-1); //Center vertically
                        }

                        tspanEl.attr('x',calcX).attr('y',calcY);
                    }
                }
            }
            else if (labelElements[name])
            {
                labelElements[name].dom.text(plot.labels[name].value);
                if (plot.labels[name].color) {
                    labelElements[name].dom.attr('fill', plot.labels[name].color);
                }
            }

            if (labelElements[name])
            {
                if (translate)
                {
                    labelElements[name].dom.attr('transform', 'translate(' + x + ',' + y + ')rotate(' + rotate + ')')
                }
                else
                {
                    labelElements[name].dom.attr('x', x);
                    labelElements[name].dom.attr('y', y);
                }
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

    var appendTSpans = function(selection, width, noWrap) {
        var i, words = selection.datum().text.split(' '), segments = [],
            partial = '', start = 0, isSeparator = selection.datum().separator === true;

        for (i = 0; i < words.length; i++) {
            partial = partial + words[i] + ' ';
            selection.text(partial);
            if (selection.node().getBBox().width > width && !noWrap) {
                segments.push(words.slice(start, i).join(' '));
                partial = words[i];
                start = i;
            }
        }

        selection.text('');

        segments.push(words.slice(start, i).join(' '));

        selection.selectAll('tspan').data(segments).enter().append('tspan')
                .text(function(d){return d})
                .attr('font-size', (isSeparator ? '110%' : '100%'))
                .attr('font-weight', (isSeparator ? 'bold' : 'normal'))
                .attr('dy', function(d, i){return i > 0 ? 12 : 0;})
                .attr('x', selection.attr('x'));
    };

    var renderLegendItem = function(selection, plot) {
        var i, xPad, glyphX, textX, yAcc, colorAcc, shapeAcc, hoverAcc, textNodes, currentItem, cBBox, pBBox;

        var fontFamily = plot.fontFamily ? plot.fontFamily : 'Roboto, arial, helvetica, sans-serif';
        selection.attr('font-family', fontFamily).attr('font-size', '11px');

        xPad = plot.scales.yRight && plot.scales.yRight.scale ? 50 : 0;
        glyphX = plot.grid.rightEdge + 30 + xPad;
        textX = glyphX + 15;
        yAcc = function(d, i) {return plot.grid.topEdge + (i * 15);};
        colorAcc = function(d) {
            return d.color ? d.color : (d.separator ? '#FFF' : '#000');
        };
        shapeAcc = function(d) {
            if (d.shape) {
                return d.shape(5);
            }
            return "M" + -5 + "," + -2.5 + "L" + 5 + "," + -2.5 + " " + 5 + "," + 2.5 + " " + -5 + "," + 2.5 + "Z";
        };
        textNodes = selection.append('text').attr('x', textX).attr('y', yAcc).attr('cursor', 'default');

        hoverAcc = function(d) {
            return d.hoverText ? d.hoverText : '';
        };

        // two different ways to add the hover title (so that it works in IE as well)
        selection.attr('xlink:title', hoverAcc);
        selection.append('title').text(hoverAcc);

        textNodes.each(function(){d3.select(this).call(appendTSpans, plot.grid.width - textX, plot.legendNoWrap);});

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

    var setLegendData = function(legendData) {
        plot.legendData = legendData;
    };

    var renderLegend = function() {
        var legendData = plot.legendData || plot.getLegendData(), legendGroup, legendItems;
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
                colorAcc, sizeAcc, shapeAcc, hoverTextAcc, jitterIndex = {};
        layer = getLayer.call(this, geom);

        if (geom.xScale.scaleType == 'discrete' && geom.position == 'jitter') {
            xBinWidth = ((plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length)) / 2;
            xAcc = function(row) {
                var x = geom.xAes.getValue(row);
                var value = geom.getX(row);
                if (value == null) {return null;}
                // don't jitter the first data point for the given X (i.e. if we only have one it shouldn't be jittered)
                value = jitterIndex[x] ? value - (xBinWidth / 2) + (Math.random() * xBinWidth) : value;
                jitterIndex[x] = true;
                return value;
            };
        } else {
            xAcc = function(row) {
                var value = geom.getX(row);
                var originalVal = row.x;
                if (yLogGutter && !isNaN(parseFloat(originalVal)) && originalVal != null && originalVal <= 0) {
                    value = geom.xScale.range[0] + logGutterPointsOffset;
                }
                return value;
            };
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
                var originalVal = row.y;
                if (xLogGutter && !isNaN(parseFloat(originalVal)) && originalVal != null && originalVal <= 0) {
                    value = geom.yScale.range[0] - logGutterPointsOffset;
                }
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
            return 'translate(' + x + ',' + y + ')';
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

            return x !== null && isFinite(x) && y !== null && isFinite(y);
        }, this);

        // reset the jitterIndex since we will call xAcc again via translateAcc
        jitterIndex = {};

        anchorSel = layer.selectAll('.point').data(data);
        anchorSel.exit().remove();
        anchorSel.enter().append('a').attr('class', 'point').append('path');

        // two different ways to add the hover title (so that it works in IE as well)
        anchorSel.attr('xlink:title', hoverTextAcc);
        anchorSel.append('title').text(hoverTextAcc);

        // allow the point id attribute to be added for automated testing purposes
        if (geom.pointIdAttrAes) {
            anchorSel.attr('id', geom.pointIdAttrAes.getValue);
        }

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

        bindMouseEvents(pointsSel, geom, layer);

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    var renderControlRange = function(layer, plot, geom, data) {
        var colorAcc, sizeAcc, topFn, bottomFn, selection, newBars;

        colorAcc = geom.colorAes && geom.colorScale ? function(row) {return geom.colorScale.scale(geom.colorAes.getValue(row) + geom.layerName);} : geom.color;
        sizeAcc = geom.sizeAes && geom.sizeScale ? function(row) {return geom.sizeScale.scale(geom.sizeAes.getValue(row));} : geom.size;
        topFn = function(d) {
            var x, y, control;
            x = geom.getX(d);
            control = geom.upperAes.getValue(d);
            y = geom.yScale.scale(control);
            return control == null || isNaN(x) || isNaN(y) ? null : LABKEY.vis.makeLine(x - geom.width, y, x + geom.width, y);
        };

        bottomFn = function(d) {
            var x, y, control;
            x = geom.getX(d);
            control = geom.lowerAes.getValue(d);
            y = geom.yScale.scale(control);
            return control == null || isNaN(x) || isNaN(y) ? null : LABKEY.vis.makeLine(x - geom.width, y, x + geom.width, y);
        };

        selection = layer.selectAll('.control-bar').data(data);
        selection.exit().remove();

        newBars = selection.enter().append('g').attr('class', 'control-bar');
        if (geom.upperAes)
            newBars.append('path').attr('class','control-line-upper');
        if (geom.lowerAes)
            newBars.append('path').attr('class','control-line-lower');

        if (geom.upperAes)
            selection.selectAll('.control-line-upper').attr('d', topFn).attr('stroke', colorAcc).attr('stroke-width', sizeAcc);
        if (geom.lowerAes)
            selection.selectAll('.control-line-lower').attr('d', bottomFn).attr('stroke', colorAcc).attr('stroke-width', sizeAcc);

        if (geom.dashed) {
            selection.selectAll('.control-line-upper').style("stroke-dasharray", ("2, 1"));
            selection.selectAll('.control-line-lower').style("stroke-dasharray", ("2, 1"));
        }
    };

    var renderControlRangeGeom = function(data, geom) {
        var layer = getLayer.call(this, geom);
        layer.call(renderControlRange, plot, geom, data);

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    var renderErrorBar = function(layer, plot, geom, data) {
        var colorAcc, altColorAcc, sizeAcc, topFn, bottomFn, verticalFn, selection, newBars;

        colorAcc = geom.colorAes && geom.colorScale ? function(row) {return geom.colorScale.scale(geom.colorAes.getValue(row) + geom.layerName);} : geom.color;
        altColorAcc = geom.altColor ? geom.altColor : colorAcc;
        sizeAcc = geom.sizeAes && geom.sizeScale ? function(row) {return geom.sizeScale.scale(geom.sizeAes.getValue(row));} : geom.size;
        topFn = function(d) {
            var x, y, value, error;
            x = geom.getX(d);
            value = geom.yAes.getValue(d);
            error = geom.errorAes.getValue(d);
            y = geom.yScale.scale(value + error);
            return value == null || isNaN(x) || isNaN(y) ? null : LABKEY.vis.makeLine(x - geom.width, y, x + geom.width, y);
        };
        bottomFn = function(d) {
            var x, y, value, error;
            x = geom.getX(d);
            value = geom.yAes.getValue(d);
            error = geom.errorAes.getValue(d);
            y = geom.yScale.scale(value - error);
            // if we have a log scale, y will be null for negative values so don't attempt to plot
            if (y == null && geom.yScale.trans == "log") {
                return null;
            }
            return value == null || isNaN(x) || isNaN(y) ? null : LABKEY.vis.makeLine(x - geom.width, y, x + geom.width, y);
        };
        verticalFn = function(d) {
            var x, y1, y2, value, error;
            x = geom.getX(d);
            value = geom.yAes.getValue(d);
            error = geom.errorAes.getValue(d);
            y1 = geom.yScale.scale(value + error);
            y2 = geom.yScale.scale(value - error);
            // if we have a log scale, y2 will be null for negative values so set to scale min
            if (y2 == null && geom.yScale.trans == "log") {
                y2 = geom.yScale.range[0];
            }
            return isNaN(x) || isNaN(value) || isNaN(error) || !isFinite(y1) || !isFinite(y2) ? null : LABKEY.vis.makeLine(x, y1, x, y2);
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
        newBars.append('path').attr('class','error-bar-bottom');
        newBars.append('path').attr('class','error-bar-vert');

        selection.selectAll('.error-bar-top').attr('d', topFn).attr('stroke', colorAcc).attr('stroke-width', sizeAcc);
        selection.selectAll('.error-bar-bottom').attr('d', bottomFn).attr('stroke', colorAcc).attr('stroke-width', sizeAcc);
        selection.selectAll('.error-bar-vert').attr('d', verticalFn).attr('stroke', altColorAcc).attr('stroke-width', sizeAcc);

        if (geom.dashed) {
            selection.selectAll('.error-bar-top').style("stroke-dasharray", ("2, 1"));
            selection.selectAll('.error-bar-bottom').style("stroke-dasharray", ("2, 1"));
        }
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

        return summary.sortedValues[i] < summary.Q1 ? summary.sortedValues[i] : summary.Q1;
    };

    var getTopWhisker = function(summary) {
        var i = summary.sortedValues.length - 1,  largestNotOutlier = summary.Q3 + (1.5 * summary.IQR);

        while (summary.sortedValues[i] > largestNotOutlier) {i--;}

        return summary.sortedValues[i] > summary.Q3 ? summary.sortedValues[i] : summary.Q3;
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
        boxWrappers.append('title').text(hoverFn);

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
        outlierSel.attr('class', 'outlier').append('title').text(hoverAcc);

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
                var filteredGroupData = filterLogGroupData.call(this, groupedData[groupName], geom);
                summary = LABKEY.vis.Stat.summary(filteredGroupData, geom.yAes.getValue);
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

    // for log scale, box plot is generated using the subset of positive values only
    var filterLogGroupData = function(groupedData, geom) {
        if (!xLogGutter) {
            return groupedData;
        }
        var filteredData = [];
        for(var i = 0; i < groupedData.length; i++){
            var value = groupedData[i];
            if (isNaN(parseFloat(value.y)) || value.y == null || value.y <= 0) {
                continue;
            }
            filteredData.push(value);
        }
        return filteredData;
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

        if (geom.dashed) {
            layer.selectAll('path').style("stroke-dasharray", ("3, 3"));
            layer.selectAll('path').style("stroke-dasharray", ("3, 3"));
        }
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

        if (geom.sortFnAes) {
            for (var i = 0; i < renderableData.length; i++) {
                var rows = renderableData[i].data;
                rows.sort(geom.sortFnAes.getValue);
            }
        }

        layer.call(renderPaths, renderableData, geom);

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    var getMaxBinPointCount = function(data) {
        var maxBinPointCount = 0;

        for (var i = 0; i < data.length; i++)
        {
            if (data[i].length > maxBinPointCount)
                maxBinPointCount = data[i].length;

            // set a maximum for the bin domain
            if (maxBinPointCount > 50)
                return 50;
        }

        return maxBinPointCount;
    };

    var renderHexBin = function(data, geom, points) {
        var hexbin = d3.hexbin().radius(geom.size),
            binData = hexbin(points),
            colorDomain = geom.colorDomain ? geom.colorDomain : [0, getMaxBinPointCount(binData)];

        var color = d3.scale.linear()
                .domain(colorDomain)
                .range(geom.colorRange)
                .interpolate(d3.interpolateLab);

        var hoverTextAcc = function(d){
            return d.length + (d.length == 1 ? " point" : " points");
        };

        var anchorSel = this.selectAll('.vis-bin').data(binData);
        anchorSel.exit().remove();
        anchorSel.enter().append('a').attr('class', 'vis-bin vis-bin-hexagon').append('path');
        anchorSel.append('title').text(hoverTextAcc);

        var hexSel = anchorSel.select('path');
        hexSel.attr("d", hexbin.hexagon())
                .attr("transform", function(d) {
                    return "translate(" + d.x + "," + d.y + ")";
                })
                .style("fill", function(d) { return color(d.length); });

        return {
            sel: hexSel,
            layer: this
        };
    };

    var renderSquareBin = function(data, geom, points) {
        var sqbin = d3.sqbin().side(geom.size),
            binData = sqbin(points),
            colorDomain = geom.colorDomain ? geom.colorDomain : [0, getMaxBinPointCount(binData)];

        var color = d3.scale.linear()
                .domain(colorDomain)
                .range(geom.colorRange)
                .interpolate(d3.interpolateLab);

        var hoverTextAcc = function(d){
            return d.length + (d.length == 1 ? " point" : " points");
        };

        var anchorSel = this.selectAll('.vis-bin').data(binData);
        anchorSel.exit().remove();
        anchorSel.enter().append('a').attr('class', 'vis-bin vis-bin-square').append('path');
        anchorSel.append('title').text(hoverTextAcc);

        var squareSel = anchorSel.select('path');
        squareSel.attr("d", sqbin.square())
                .attr("transform", function(d) {
                    return "translate(" + d.x + "," + d.y + ")";
                })
                .style("fill", function(d) { return color(d.length); });

        return {
            sel: squareSel,
            layer: this
        };
    };

    var renderBinGeom = function(data, geom) {

        var points = translatePointsForBin(geom, data);

        var selLayer;
        var layer = getLayer.call(this, geom);
        switch (geom.shape) {
            case 'square':
                selLayer = renderSquareBin.call(layer, data, geom, points);
                break;
            case 'hex':
            default:
                selLayer = renderHexBin.call(layer, data, geom, points);
        }

        if (selLayer) {
            bindMouseEvents(selLayer.sel, geom, layer);
        }

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    var renderDataspaceBoxes = function(selection, plot, geom) {
        var xBinWidth, padding, boxWidth, boxWrappers, rects, medians, whiskers, xAcc, yAcc, hAcc, whiskerAcc,
                medianAcc, hoverAcc;
        var emptyDrawing='M0,0L0,0Z',

        xBinWidth = ((plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length)) / 2;
        padding = Math.max(xBinWidth * .05, 5); // Pad the space between the box and points.
        boxWidth = xBinWidth / 4;

        hAcc = function(d) {
            if (d.noSummary) {
                return 0;
            }
            return Math.floor(geom.yScale.scale(d.summary.Q1) - geom.yScale.scale(d.summary.Q3));
        };
        xAcc = function(d) {return geom.xScale.scale(d.name) - boxWidth - padding;};
        yAcc = function(d) {
            if (d.noSummary) {
                return 0;
            }
            return Math.floor(geom.yScale.scale(d.summary.Q3)) + .5;
        };
        hoverAcc = geom.hoverTextAes ? function(d) {
                if (d.noSummary) {
                    return '';
                }
                return geom.hoverTextAes.value(d.name, d.summary)
            } : null;

        whiskerAcc = function(d) {
            if (d.noSummary) {
                return emptyDrawing;
            }
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
            if (d.noSummary) {
                return emptyDrawing;
            }
            var x1, x2, y;
            x1 = geom.xScale.scale(d.name) - boxWidth - padding;
            x2 = geom.xScale.scale(d.name) - padding;
            y = Math.floor(geom.yScale.scale(d.summary.Q2)) + .5;
            return LABKEY.vis.makeLine(x1, y, x2, y);
        };

        boxWrappers = selection.selectAll('a.dataspace-box-plot').data(function(d){return [d];});
        boxWrappers.exit().remove();
        boxWrappers.enter().append('a').attr('class', 'dataspace-box-plot');
        boxWrappers.append('title').text(hoverAcc);

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

        if (geom.boxMouseOverFnAes) {
            boxWrappers.on('mouseover', function(data) {
                geom.boxMouseOverFnAes.value(d3.event, this, data);
            });
        }
        if (geom.boxMouseOutFnAes) {
            boxWrappers.on('mouseout', function(data) {
                geom.boxMouseOutFnAes.value(d3.event, this, data);
            });
        }
        if (geom.boxMouseUpFnAes) {
            boxWrappers.on('mouseup', function(data) {
                geom.boxMouseUpFnAes.value(d3.event, this, data);
            });
        }

    };

    var renderDataspacePoints = function(selection, plot, geom, layer) {
        var pointWrapper, points, xBinWidth, padding, xAcc, yAcc, translateAcc, defaultShape, colorAcc, sizeAcc,
                shapeAcc, hoverAcc;

        xBinWidth = ((plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length)) / 2;
        padding = Math.max(xBinWidth * .05, 5);

        xAcc = function(row) {
            var x, offset;
            x = geom.getX(row) + padding;
            offset = xBinWidth / 4;

            if (x == null) {return null;}

            var originalVal = row.x;
            if (yLogGutter && !isNaN(parseFloat(originalVal)) && originalVal != null && originalVal <= 0) {
                x = geom.xScale.range[0] + logGutterPointsOffset;
            }
            return x + (Math.random() * offset);
        };

        yAcc = function(row) {
            var value = geom.getY(row);
            if (value == null || isNaN(value)) {return null;}
            var originalVal = row.y;
            if (xLogGutter && !isNaN(parseFloat(originalVal)) && originalVal != null && originalVal <= 0) {
                value = geom.yScale.range[0] - logGutterPointsOffset;
            }
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

        pointWrapper = selection.selectAll('a.point').data(function(d){
            return d.rawData.filter(function(d){
                var x = geom.getX(d), y = geom.getY(d);
                return x !== null && y !== null;
            });
        });
        pointWrapper.exit().remove();
        pointWrapper.enter().append('a').attr('class', 'point');
        pointWrapper.append('title').text(hoverAcc);

        points = pointWrapper.selectAll('path').data(function(d){return [d];});
        points.exit().remove();
        points.enter().append('path');
        points.attr('d', shapeAcc).attr('transform', translateAcc)
                .attr('fill', colorAcc).attr('stroke', colorAcc)
                .attr('fill-opacity', geom.pointOpacity).attr('stroke-opacity', geom.pointOpacity);

        if (geom.pointClickFnAes) {
            points.on('click', function(data) {
                geom.pointClickFnAes.value(d3.event, data, layer);
            });
        } else {
            points.on('click', null);
        }

        bindMouseEvents(points, geom, layer);
    };

    var renderDataspaceBins = function(selection, plot, geom, layer, data) {

        var points = translatePointsForBin(geom, data);
        var selLayer = renderSquareBin.call(layer, data, geom, points);

        // stretch the bins horizontally
        var xBinHeight = geom.size;
        var xBinWidth = ((plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length)) / 2;
        var padding = Math.max(xBinWidth * .05, 5); // Pad the space between the box and bins.
        var width = (xBinWidth / 4) + (padding / 2);
        var binCorners = [padding+",0", width+",0", width+","+xBinHeight, padding+","+xBinHeight, padding+",0"];
        selLayer.sel.attr('d', "M" + binCorners.join(" "));

        if (selLayer) {
            bindMouseEvents(selLayer.sel, geom, layer);
        }
    };

    var renderDataspaceBoxGroups = function(layer, plot, geom, data, summaries) {
        var boxGroups;
        boxGroups = layer.selectAll('g.dataspace-box-group').data(summaries);
        boxGroups.exit().remove();
        boxGroups.enter().append('g').attr('class', 'dataspace-box-group');
        boxGroups.call(renderDataspaceBoxes, plot, geom);

        if (data.length <= geom.binRowLimit)
            boxGroups.call(renderDataspacePoints, plot, geom, layer);
        else
            boxGroups.call(renderDataspaceBins, plot, geom, layer, data);
    };

    var renderDataspaceBoxPlotGeom = function(data, geom) {
        var layer = getLayer.call(this, geom), summaries;
        if (geom.xScale.scaleType == 'continuous') {
            console.error('Box Plots not supported for continuous data yet.');
            return;
        }

        summaries = prepDataspaceBoxPlotData(data, geom);

        layer.call(renderDataspaceBoxGroups, plot, geom, data, summaries);
    };

    var translatePointsForBin = function(geom, data) {
        var xAcc = function(row) {
            var value =  geom.getX(row);
            var originalVal = row.x;
            if (yLogGutter && !isNaN(parseFloat(originalVal)) && originalVal != null && originalVal <= 0) {
                value = geom.xScale.range[0] + logGutterPointsOffset;
            }
            return value;

        };
        var yAcc = function(row) {
            var value =  geom.getY(row);
            var originalVal = row.y;
            if (xLogGutter && !isNaN(parseFloat(originalVal)) && originalVal != null && originalVal <= 0) {
                value = geom.yScale.range[0] - logGutterPointsOffset;
            }
            return value;
        };

        var points = [], x, y;
        for (var d=0; d < data.length; d++) {
            x = xAcc(data[d]); y = yAcc(data[d]);
            if (x != null && !isNaN(x) && y != null && !isNaN(y)) {
                points.push({x: x, y: y, data: data[d]});
            }
        }

        return points;
    };

    var bindMouseEvents = function(selection, geom, layer) {


        if (geom.mouseOverFnAes) {
            selection.on('mouseover', function(data) {
                geom.mouseOverFnAes.value(d3.event, data, layer, this);
            });
        }
        else {
            selection.on('mouseover', null);
        }

        if (geom.mouseOutFnAes) {
            selection.on('mouseout', function(data) {
                geom.mouseOutFnAes.value(d3.event, data, layer, this);
            });
        }
        else {
            selection.on('mouseout', null);
        }

        if (geom.mouseUpFnAes) {
            selection.on('mouseup', function(data) {
                geom.mouseUpFnAes.value(d3.event, data, layer, this);
            });
        }
        else {
            selection.on('mouseup', null);
        }
    };

    var renderBarPlotGeom = function(data, geom) {
        var layer = getLayer.call(this, geom), barWrappers, grouped, xOffsetFn,
                binWidth, barWidth, numXCategories, numXSubCategories, offsetWidth,
                rects, hoverFn, heightFn, xAcc, colorAcc, yAcc, yZero;

        if (geom.xScale.scaleType == 'continuous') {
            console.error('Bar Plots not supported for continuous data yet.');
            return;
        }

        numXCategories = geom.xScale.scale.domain().length;
        if (geom.xSubScale && geom.xSubAes) {
            grouped = true;
            numXSubCategories = geom.xSubScale.scale.domain().length;
        }

        binWidth = (plot.grid.rightEdge - plot.grid.leftEdge) / (grouped ? numXSubCategories : numXCategories);
        barWidth = grouped ? (binWidth / (numXCategories * 2)) : (binWidth / (geom.showCumulativeTotals ? 4 : 2));
        offsetWidth = (binWidth / (geom.showCumulativeTotals ? 3.5 : 4));

        hoverFn = geom.hoverFn ? geom.hoverFn : function(d) {
            return geom.yAes.getValue(d);
        };

        xOffsetFn = function(d) {
            for (var i = 0; i < numXCategories; i++)
            {
                if (geom.xScale.domain[i] === d[geom.xAes.value])
                    return barWidth * i;
            }
            return 0;
        };

        xAcc = function(d) {
            if (grouped)
                return geom.getXSub(d) + xOffsetFn(d) - offsetWidth;
            else
                return geom.getX(d) - offsetWidth;
        };

        yAcc = function(d){ return geom.getY(d) };

        colorAcc = geom.fill;
        if (geom.colorAes && geom.colorScale)
        {
            colorAcc = function(row) {
                return geom.colorScale.scale(geom.colorAes.getValue(row) + geom.layerName);
            };
        }

        yZero = {};
        yZero[geom.yAes.value] = 0;

        // group each bar with an a tag for hover
        barWrappers = layer.selectAll('a.bar-individual').data(data);
        barWrappers.exit().remove();
        barWrappers.enter().append('a').attr('class', 'bar-individual');
        barWrappers.append('title').text(hoverFn);

        // add the bars and styling for the counts
        rects = barWrappers.selectAll('rect.bar-rect').data(function(d){ return [d] });
        rects.exit().remove();
        heightFn = function(d) {
            if (yAcc(d) === null || yAcc(d) === undefined) {
                return 0;
            }
            return Math.abs(yAcc(d) - yAcc(yZero));
        };
        rects.enter().append('rect').attr('class', 'bar-rect')
                .attr('x', xAcc)
                .attr('y', function(d) {
                    if (yAcc(d) > yAcc(yZero)) {
                        return yAcc(yZero); // negative value
                    } else {
                        return yAcc(d); // positive value
                    }
                })
                .attr('width', barWidth).attr('height', heightFn)
                .attr('stroke', geom.color).attr('stroke-width', geom.lineWidth)
                .attr('fill', colorAcc).attr('fill-opacity', geom.opacity);

        // For selenium testing
        rects.enter().append("text").style('display', (geom.showValues ? 'block' : 'none'))
                .attr('class', 'bar-text')
                .attr('text-anchor', 'middle')
                .attr('x', xAcc)
                .attr('x', function(d) { return xAcc(d) + (barWidth/2); })
                .attr('y', function(d) {
                    var offset = geom.yAes.getValue(d) >=0 ? 4 : -15;
                    return yAcc(d) - offset;
                })
                .text(function(d) { return geom.yAes.getValue(d); });

        if (geom.clickFn) {
            barWrappers.on('click', function(data) {
                geom.clickFn(d3.event, data, layer);
            });
        }

        // draw solid line on x of 0 if the y-domain min is set to be < 0
        if (geom.yScale.domain[0] < 0) {
            layer.append('line')
                    .attr('class', 'zero-line')
                    .attr('x1', plot.grid.leftEdge)
                    .attr('y1', yAcc(yZero))
                    .attr('x2', plot.grid.rightEdge)
                    .attr('y2', yAcc(yZero))
                    .attr('stroke-width', 1)
                    .attr('stroke', "#000000");
        }

        // add the bars and styling for the totals
        if (geom.showCumulativeTotals)
        {
            hoverFn = function(d) { return d.total };
            xAcc = function(d){ return geom.getX(d) + (offsetWidth - barWidth) };
            yAcc = function(d){ return geom.yScale.scale(d.total) };
            heightFn = function(d) { return plot.grid.bottomEdge - geom.yScale.scale(d.total) };

            barWrappers = layer.selectAll('a.bar-total').data(data);
            barWrappers.exit().remove();
            barWrappers.enter().append('a').attr('class', 'bar-total');
            barWrappers.append('title').text(hoverFn);

            rects = barWrappers.selectAll('rect.bar-rect').data(function(d){ return [d] });
            rects.exit().remove();
            rects.enter().append('rect').attr('class', 'bar-rect')
                    .attr('x', xAcc)
                    .attr('y', function(d) {
                        if (yAcc(d) > yAcc(yZero))
                            return yAcc(yZero);
                        else
                            return geom.yScale.scale(d.total);
                    })
                    .attr('width', barWidth).attr('height', heightFn)
                    .attr('stroke', geom.colorTotal).attr('stroke-width', geom.lineWidthTotal)
                    .attr('fill', geom.fillTotal).attr('fill-opacity', geom.opacityTotal);

            // For selenium testing
            rects.enter().append("text").style('display', (geom.showValues ? 'block' : 'none'))
                    .attr('class', 'bar-text')
                    .attr('text-anchor', 'middle')
                    .attr('x', xAcc)
                    .attr('x', function(d) { return xAcc(d) + (barWidth/2); })
                    .attr('y', function(d) {
                        var offset = geom.yAes.getValue(d) >=0 ? 4 : -15;
                        return yAcc(d) - offset;
                    })
                    .text(function(d) { return d.total });

            // Render legend for Individual vs Total bars
            if (!plot.legendData)
                plot.legendData = [{text: 'Total', color: geom.fillTotal}, {text: 'Individual', color: geom.fill}];
        }

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    var renderTimelinePlotGeom = function(data, geom) {
        var gridWidth = plot.grid.rightEdge - plot.grid.leftEdge;
        var labelOffset = Math.floor(gridWidth / 40);
        var perEventYOffset = geom.rowHeight / 2;
        var collapsed = geom.isCollapsed;
        var layer = getLayer.call(this, geom);
        var iconSize = geom.eventIconSize;
        var highlightedObject = geom.highlight;
        var xAcc = function(d){
            return geom.getX(d) - (iconSize / 2);
        };
        var typeSubtypeYAcc = function(d) {
            return geom.getTypeSubtype(d) - geom.rowHeight;
        };
        var parentYAcc = function(d){
            return geom.getParentY(d) - geom.rowHeight;
        };

        d3.selection.prototype.moveToBack = function()
        {
            return this.each(function()
            {
                if (this.parentNode.firstChild)
                {
                    this.parentNode.insertBefore(this, this.parentNode.firstChild);
                }
            });
        };

        //push the grid behind everything and make it invisible
        d3.select('#timeline-0').moveToBack();
        var grid = d3.select('g.grid-rect');
        var gridRect = grid[0][0].firstChild;
        gridRect.setAttribute('fill', 'none');

        //Filters out duplicate values for background bars / event labels
        var getYAxisData = function(data) {
            var yAxisData = data;
            var labels = {};
            var key = collapsed ? geom.parentName : 'typeSubtype';
            yAxisData = yAxisData.filter(function (row)
            {
                if (labels[row[key]])
                {
                    return false;
                }
                labels[row[key]] = true;
                return true;
            });

            if(!collapsed)
            {
                //add dummy entries for parent rows/labels that won't have event squares
                var dummies = [];
                yAxisData.forEach(function (row)
                {
                    if (labels[row[key]] && !labels[row[geom.parentName]]) {
                        labels[row[geom.parentName]] = true;
                        var dummy = {};
                        dummy[geom.parentName] = row[geom.parentName];
                        dummy[geom.childName] = null;
                        dummy['typeSubtype'] = row[geom.parentName];
                        dummies.push(dummy);
                    }
                });
                yAxisData = yAxisData.concat(dummies);
            }
            return yAxisData;
        };

        var renderHorizontalBars = function() {
            var entries, eventbars, count = -1;

            function compareTypeSubtype(a, b) {
                if (a.typeSubtype < b.typeSubtype)
                    return -1;
                if (a.typeSubtype > b.typeSubtype)
                    return 1;
                return 0;
            }

            function compareParentName(a, b) {
                if (a[geom.parentName] < b[geom.parentName])
                    return -1;
                if (a[geom.parentName] > b[geom.parentName])
                    return 1;
                return 0;
            }

            entries = getYAxisData(data);
            if (collapsed) {
                entries.sort(compareParentName);
            } else {
                entries.sort(compareTypeSubtype);
            }
            eventbars = layer.selectAll('rect').data(entries);
            eventbars.exit().remove();
            eventbars.enter().append('rect')
                    .attr('class', 'timeline-bar')
                    .attr('x', plot.grid.leftEdge - geom.marginLeft)
                    .attr('y', collapsed ? parentYAcc : typeSubtypeYAcc)
                    .attr('width', gridWidth + geom.marginLeft)
                    .attr('height', geom.rowHeight)
                    .attr('fill', function(d) {
                        if (collapsed || !d[geom.childName]) {
                            count++;
                        }

                        if (highlightedObject) {
                            if ((!collapsed)) {
                                if ((!highlightedObject[geom.childName] && d[geom.parentName] == highlightedObject[geom.parentName])
                                    || (d.typeSubtype == highlightedObject.typeSubtype)) {
                                    return geom.highlightRowColor;
                                }
                            } else if (highlightedObject[geom.parentName] == d[geom.parentName]) {
                                    return geom.highlightRowColor;
                            }
                        }
                        return count % 2 == 0 ? geom.rowColorDomain[0] : geom.rowColorDomain[1];
                    });

            //bind interaction events
            if (geom.rowClickFnAes) {
                eventbars.on('click', function(d) {
                    geom.rowClickFnAes.value(d3.event, d, layer);
                });
            }
            else { eventbars.on('click', null); }

            if (geom.mouseOverRowFnAes) {
                eventbars.on('mouseover', function(d) {
                    geom.mouseOverRowFnAes.value(d3.event, d, layer);
                });
            }
            else { eventbars.on('mouseover', null); }

            if (geom.mouseOutRowFnAes) {
                eventbars.on('mouseout', function(d) {
                    geom.mouseOutRowFnAes.value(d3.event, d, layer);
                });
            }
            else { eventbars.on('mouseout', null); }
        };

        var renderEventTypeLabels = function() {
            var labelData = getYAxisData(data);
            var eventLabels = layer.selectAll('text.row-label-text').data(labelData);
            eventLabels.exit().remove();
            eventLabels.enter().append('text')
                    .attr('class', 'row-label-text');
            eventLabels.text(function(d) {
                if ( !collapsed && d[geom.childName]) {
                    return d[geom.childName];
                } else {
                    return d[geom.parentName];
                }
            });
            eventLabels.attr('font-size', function(d) {
                    if (d[geom.childName] && !collapsed) {
                        return '0.9em';
                    } else {
                        return '1em';
                    }
                })
                    .attr('x', function(d) {
                    if (d[geom.childName] && !collapsed) {
                        return (plot.grid.leftEdge  + labelOffset) - geom.marginLeft;
                    } else {
                        return (plot.grid.leftEdge + 5)  - geom.marginLeft;
                    }
                })
                    .attr('y', collapsed ? parentYAcc : typeSubtypeYAcc)
                    .attr('dy', perEventYOffset * 1.2);

            //bind interaction events
            if (geom.rowClickFnAes) {
                eventLabels.on('click', function(d) {
                    geom.rowClickFnAes.value(d3.event, d, layer);
                });
            }
            else { eventLabels.on('click', null); }

            if (geom.mouseOverRowFnAes) {
                eventLabels.on('mouseover', function(d) {
                    geom.mouseOverRowFnAes.value(d3.event, d, layer);
                });
            }
            else { eventLabels.on('mouseover', null); }

            if (geom.mouseOutRowFnAes) {
                eventLabels.on('mouseout', function(d) {
                    geom.mouseOutRowFnAes.value(d3.event, d, layer);
                });
            }
            else { eventLabels.on('mouseout', null); }
        };

        var renderEventIcons = function() {
            var eventIcons = layer.selectAll('rect.timeline-event-rect').data(data);
            eventIcons.exit().remove();
            eventIcons.enter().append('rect').attr('class', 'timeline-event-rect')
                    .attr('x', xAcc)
                    .attr('y', function(d) {
                        if (collapsed) {
                            return parentYAcc(d) + perEventYOffset - (iconSize / 2);
                        } else {
                            return typeSubtypeYAcc(d) + perEventYOffset - (iconSize / 2);
                        }
                    })
                    .attr('width', iconSize).attr('height', iconSize)
                    .attr('stroke', function(d) {
                        if (geom.activeEventKey && geom.activeEventIdentifier
                                && d[geom.activeEventKey] == geom.activeEventIdentifier) {
                            return geom.activeEventStrokeColor;
                        }
                        return geom.eventIconColor;
                    })
                    .attr('fill', function(d) {
                        if (highlightedObject) {
                            if (collapsed && d[geom.parentName] == highlightedObject[geom.parentName]) {
                                return geom.eventIconFill;
                            }
                            else if (!collapsed) {
                                if (!highlightedObject[geom.childName] && d[geom.parentName] == highlightedObject[geom.parentName]) {
                                    return geom.eventIconFill;
                                }
                                else if (d.typeSubtype == highlightedObject.typeSubtype) {
                                    return geom.eventIconFill;
                                }
                                else {
                                    return 'none';
                                }
                            }
                             else {
                                return 'none';
                            }
                        }
                        return geom.eventIconFill;
                    })
                    .attr('fill-opacity', geom.eventIconOpacity)
                    .attr('stroke-width', '2')
                    .append('svg:title').text(function (d) {
                        return d[geom.dateKey].toUTCString();
                    });

            //bind events to mouse interaction
            if (geom.eventClickFnAes) {
                eventIcons.on('click', function(d) {
                    geom.eventClickFnAes.value(d3.event, d, layer);
                })
            } else {
                eventIcons.on('click', null);
            }

            bindMouseEvents(eventIcons, geom, layer);
        };

        var renderTickLines = function() {
            //Draw the origin yLeft axis that was previously hidden
            var lineObj = {};
            lineObj[geom.timeUnit] = 0;
            layer.append('line')
                    .attr('class', 'tick-line')
                    .attr('x1', function(d) {return xAcc(lineObj) + (iconSize / 2)})
                    .attr('y1', plot.grid.bottomEdge)
                    .attr('x2', function(d) {return xAcc(lineObj) + (iconSize / 2)})
                    .attr('y2', plot.grid.topEdge)
                    .attr('stroke-witth', 1)
                    .attr('stroke', geom.tickColor);

            //Render the special emphasis ticks if config gives us types/subtypes to check for
            if (geom.emphasisEvents) {
                for (var i = 0; i < data.length; i++) {
                    const row = data[i];
                    var match = false;
                    for (var type in geom.emphasisEvents) {
                        if (geom.emphasisEvents.hasOwnProperty(type)) {
                            var ephasizedVals = geom.emphasisEvents[type];
                            ephasizedVals.forEach(function(value) {
                                if (row[type] == value) {
                                    match = true;
                                }
                            });
                        }
                    }
                    if (match) {
                        layer.append('line')
                                .attr('class', 'emphasis-tick-line')
                                .attr('x1', function(d) {
                                    var n = xAcc(row);
                                    return n + (iconSize / 2)
                                })
                                .attr('y1', plot.grid.bottomEdge)
                                .attr('x2', function(d) {
                                    var n = xAcc(row);
                                    return n + (iconSize / 2)
                                })
                                .attr('y2', plot.grid.topEdge)
                                .attr('stroke-width', 2)
                                .attr('stroke', geom.emphasisTickColor);
                    }
                }
            }
        };

        //call render functions
        renderHorizontalBars();
        renderTickLines();
        renderEventTypeLabels();
        renderEventIcons();

    };



    // <=0 data are filtered out for box plot in log scale but should still show in log gutter
    // it's possible for plot to have no box plot but not empty. use noSummary to turn off box plot
    var prepDataspaceBoxPlotData = function(data, geom) {
        var groupName, groupedData, summary, summaries = [];
        groupedData = LABKEY.vis.groupData(data, geom.xAes.getValue);
        for (groupName in groupedData) {
            if (groupedData.hasOwnProperty(groupName)) {
                var filteredGroupData = filterLogGroupData.call(this, groupedData[groupName], geom);
                summary = LABKEY.vis.Stat.summary(filteredGroupData, geom.yAes.getValue);
                summaries.push({
                    name: groupName,
                    summary: summary,
                    noSummary: summary.sortedValues.length === 0,
                    rawData: groupedData[groupName]
                });
            }
        }

        return summaries;
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
        setBrushing: setBrushing,
        getBrushExtent: getBrushExtent,
        renderLegend: renderLegend,
        renderPointGeom: renderPointGeom,
        renderPathGeom: renderPathGeom,
        renderErrorBarGeom: renderErrorBarGeom,
        renderBoxPlotGeom: renderBoxPlotGeom,
        renderDataspaceBoxPlotGeom: renderDataspaceBoxPlotGeom,
        renderBinGeom: renderBinGeom,
        renderBarPlotGeom: renderBarPlotGeom,
        renderTimelinePlotGeom: renderTimelinePlotGeom,
        renderControlRangeGeom: renderControlRangeGeom,
        setLegendData: setLegendData
    };
};
