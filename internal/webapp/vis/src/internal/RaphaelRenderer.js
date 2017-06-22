/*
 * Copyright (c) 2013-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!LABKEY.vis.internal) {
    LABKEY.vis.internal = {};
}

LABKEY.vis.internal.RaphaelRenderer = function(plot) {
    /*
        GRID STUFF BELOW HERE
     */
    var labelElements = {};

    var initCanvas = function() {
        if (!this.paper) {
            this.paper = new Raphael(plot.renderTo, plot.grid.width, plot.grid.height);
        } else if (this.paper.width != plot.grid.width || this.paper.height != plot.grid.height) {
            this.paper.setSize(plot.grid.width, plot.grid.height);
        }

        this.paper.clear();
    };

    var renderError = function(msg) {
        if (this.paper) {
            this.paper.clear();
            this.paper.text(this.paper.width / 2, this.paper.height / 2, "Error rendering chart:\n" + msg).attr('font-size', '12px').attr('fill', 'red');
        }
    };

    var adjustRotatedTicks = function(tickSet) {
        // Raphael rotates the text around the center, so after we rotate the tick text we need to lower it so the text
        // doesn't run into the grid above.
        for (var i = 0; i < tickSet.length; i++) {
            tickSet[i].attr('y', tickSet[i].getBBox().y2);
        }
    }

    var renderGrid = function() {
        var i, x, x1, y1, x2, y2, tick, tickText, tickHoverText, tickCls, text, gridLine, tickFontSize;
        if (this.bgColor) {
            this.paper.rect(0, 0, plot.grid.width, plot.grid.height).attr('fill', plot.bgColor).attr('stroke', 'none');
        }

        if (plot.gridColor) {
            this.paper.rect(plot.grid.leftEdge, (plot.grid.height - plot.grid.topEdge),(plot.grid.rightEdge - plot.grid.leftEdge), (plot.grid.topEdge - plot.grid.bottomEdge)).attr('fill', plot.gridColor).attr('fill', plot.gridColor).attr('stroke', 'none');
        }

        // Now that we have all the scales situated we need to render the axis lines, tick marks, and titles.
        this.paper.path(LABKEY.vis.makeLine(plot.grid.leftEdge, plot.grid.bottomEdge +.5, plot.grid.rightEdge, plot.grid.bottomEdge+.5)).attr('stroke', '#000').attr('stroke-width', '1');

        var xTicks;
        var xTicksSet = this.paper.set();
        x = plot.aes.xSub ? 'xSub' : 'x';
        if (plot.scales.x.scaleType == 'continuous') {
            xTicks = plot.scales[x].scale.ticks(7);
        } else {
            xTicks = plot.scales[x].domain;
        }

        for (i = 0; i < xTicks.length; i++) {
            //Plot x-axis ticks.
            x1 = x2 = Math.floor(plot.scales[x].scale(xTicks[i])) +.5;
            y1 = plot.grid.bottomEdge + 8;
            y2 = plot.grid.bottomEdge;

            tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2));
            tick.attr('stroke-width', plot.tickWidth || 1);
            tickText = plot.scales[x].tickFormat ? plot.scales[x].tickFormat(xTicks[i]) : xTicks[i];
            // add hover for x-axis tick mark descriptions
            tickHoverText = plot.scales[x].tickHoverText ? plot.scales[x].tickHoverText(xTicks[i]) : null;
            tickCls = plot.scales[x].tickCls ? plot.scales[x].tickCls(xTicks[i]) : null;
            tickFontSize = plot.scales[x].fontSize ? plot.scales[x].fontSize : null;
            text = this.paper.text(plot.scales[x].scale(xTicks[i])+.5, plot.grid.bottomEdge + 15, tickText);
            text.attr('fill', plot.tickTextColor || '#000000');
            if (tickHoverText)
                text.attr("title", tickHoverText);
            if (tickCls)
                text.attr("class", tickCls);
            if (tickFontSize)
                text.attr({ "font-size": tickFontSize });

            xTicksSet.push(text);

            if (x1 - .5 == plot.grid.leftEdge || x1 - .5 == plot.grid.rightEdge) continue;

            gridLine = this.paper.path(LABKEY.vis.makeLine(x1, plot.grid.bottomEdge, x2, plot.grid.topEdge)).attr('stroke', '#DDD');
            if (plot.gridLineColor) {
                gridLine.attr('stroke', plot.gridLineColor);
            }
        }

        for (i = 0; i < xTicksSet.length-1; i++) {
            var curBBox = xTicksSet[i].getBBox(),
                    nextBBox = xTicksSet[i+1].getBBox();
            if (curBBox.x2 >= nextBBox[x]) {
                var rotation = plot.tickOverlapRotation ? plot.tickOverlapRotation : 15;
                xTicksSet.attr('text-anchor', 'start').transform('t0,0r'+rotation);
                adjustRotatedTicks(xTicksSet);
                break;
            }
        }

        if (plot.scales.yLeft && plot.scales.yLeft.scale) {
            var leftTicks;
            if (plot.scales.yLeft.scaleType == 'continuous') {
                leftTicks = plot.scales.yLeft.scale.ticks(10);
            } else {
                leftTicks = plot.scales.yLeft.scale.domain();
            }

            this.paper.path(LABKEY.vis.makeLine(plot.grid.leftEdge +.5, plot.grid.bottomEdge + 1, plot.grid.leftEdge+.5, plot.grid.topEdge))
                    .attr('stroke', '#000').attr('stroke-width', '1');

            for (i = 0; i < leftTicks.length; i++) {
                x1 = plot.grid.leftEdge  - 8;
                y1 = y2 = Math.floor(plot.scales.yLeft.scale(leftTicks[i])) + .5; // Floor it and add .5 to keep the lines sharp.
                x2 = plot.grid.leftEdge;
                tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2));
                tick.attr('stroke-width', plot.tickWidth || 1);
                tickText = plot.scales.yLeft.tickFormat ? plot.scales.yLeft.tickFormat(leftTicks[i]) : leftTicks[i];
                if (y1 !== plot.grid.bottomEdge + .5) {
                    // Dont draw a grid line on top of the x-axis border.
                    gridLine = this.paper.path(LABKEY.vis.makeLine(x2 + 1, y1, plot.grid.rightEdge, y2)).attr('stroke', '#DDD');
                }
                if (plot.gridLineColor) {
                    gridLine.attr('stroke', plot.gridLineColor);
                }
                text = this.paper.text(x1 - 15, y1, tickText);
                text.attr('fill', plot.tickTextColor || '#000000');
            }
        }

        if (plot.scales.yRight && plot.scales.yRight.scale) {
            var rightTicks;
            
            if (plot.scales.yRight.scaleType == 'continuous') {
                rightTicks = plot.scales.yRight.scale.ticks(10);
            } else {
                rightTicks = plot.scales.yRight.scale.domain();
            }

            this.paper.path(LABKEY.vis.makeLine(plot.grid.rightEdge + .5, plot.grid.bottomEdge + 1, plot.grid.rightEdge + .5, plot.grid.topEdge))
                    .attr('stroke', '#000').attr('stroke-width', '1');

            for (i = 0; i < rightTicks.length; i++) {
                x1 = plot.grid.rightEdge;
                y1 = y2 = Math.floor(plot.scales.yRight.scale(rightTicks[i])) + .5;
                x2 = plot.grid.rightEdge + 8;

                tick = this.paper.path(LABKEY.vis.makeLine(x1, y1, x2, y2));
                tickText = plot.scales.yRight.tickFormat ? plot.scales.yRight.tickFormat(rightTicks[i]) : rightTicks[i];
                if (y1 !== plot.grid.bottomEdge + .5) {
                    // Dont draw a line on top of the x-axis line.
                    gridLine = this.paper.path(LABKEY.vis.makeLine(plot.grid.leftEdge + 1, y1, x1, y2)).attr('stroke', '#DDD');
                }

                if (plot.gridLineColor) {
                    gridLine.attr('stroke', plot.gridLineColor);
                }

                text = this.paper.text(x2 + 15, y1, tickText);
            }
        }
    };

    var clearGrid = function() {
        this.paper.clear();
        renderGrid.call(this);
        renderLabels.call(this);
    };

    var renderClickArea = function(name, labelEl) {
        var bbox = labelEl.text.getBBox();
        var clickArea = this.paper.set();
        var box, triangle;
        var wPad = 10, x = bbox.x, y = bbox.y, height = bbox.height, width = bbox.width;
        var tx, ty, r = 4, tFn;
        if (name == 'x' || name == 'main') {
            width = width + height + (wPad * 2);
            x = x - wPad;
            tx = x + width - r - (wPad / 2);
            ty = y + (height / 2);
        } else if (name == 'y' || name == 'yLeft' || name == 'yRight') {
            height = height + width + (wPad * 2);
            y = y - width - wPad;
            tx = x + (width /2);
            ty = y + r + (wPad / 2);
        }

        if (name == 'main') {
            //down arrow
            tFn = function(x, y, r) {
                var yBottom = y + r, yTop = y - r, xLeft = x - r, xRight = x + r;
                return 'M ' + x + ' ' + yBottom + ' L ' + xLeft + ' ' + yTop + ' L ' + xRight + ' ' + yTop + ' L ' + x + ' ' + yBottom + ' Z';
            };

        }else if (name == 'x') {
            // up arrow
            tFn = function(x, y, r) {
                var yBottom = y + r, yTop = y - r, xLeft = x - r, xRight = x + r;
                return 'M ' + x + ' ' + yTop + ' ' + ' L ' + xRight + ' ' + yBottom + ' L ' + xLeft + ' ' + yBottom + ' L ' + x + ' ' + yTop + ' Z';
            };
        } else if (name == 'y' || name == 'yLeft') {
            // right arrow
            tFn = function(x, y, r) {
                var yBottom = y + r, yTop = y - r, xLeft = x - r, xRight = x + r;
                return 'M ' + xRight + ' ' + y + ' L ' + xLeft + ' ' + yBottom + ' L ' + xLeft + ' ' + yTop + ' L ' + xRight + ' ' + y + ' Z';
            };
        } else if (name == 'yRight') {
            // left arrow
            tFn = function(x, y, r) {
                var yBottom = y + r, yTop = y - r, xLeft = x - r, xRight = x + r;
                return 'M ' + xLeft + ' ' + y + ' L ' + xRight + ' ' + yTop + ' L ' + xRight + ' ' + yBottom + ' L ' + xLeft + ' ' + y + ' Z';
            };
        }
        triangle = this.paper.path(tFn(tx, ty, r)).attr('fill', 'black');
        clickArea.push(triangle);

        box = this.paper.rect(Math.floor(x) + .5, Math.floor(y) + .5, width, height)
            .attr('fill', '#FFFFFF').attr('fill-opacity', 0);

        clickArea.push(box);

        box.mouseover(function() {
            this.attr('stroke', '#777777');
            triangle.attr('fill', '#777777').attr('stroke', '#777777');
        });
        box.mouseout(function() {
            this.attr('stroke', '#000000');
            triangle.attr('fill', '#000000').attr('stroke', '#000000');
        });

        labelEl.clickArea = clickArea;
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

                if (plot.labels[label].listeners[listenerName]) {
                    // There is already a listener of the requested type, so we should purge it.
                    var unEvent = 'un' + listenerName;
                    labelElements[label].text[unEvent].call(labelElements[label].text, plot.labels[label].listeners[listenerName]);
                    if (labelElements[label].clickArea) {
                        labelElements[label].clickArea[unEvent].call(labelElements[label].clickArea, plot.labels[label].listeners[listenerName]);
                    }
                }

                plot.labels[label].listeners[listenerName] = listenerFn;

                // Need to call the listener function and keep it within the scope of the Raphael object that we're accessing,
                // so we pass itself into the call function as the scope object. It's essentially doing something like:
                // labelElements.x.text.click.call(labelElements.x.text, fn);
                labelElements[label].text[listenerName].call(labelElements[label].text, listenerFn);
                if (labelElements[label].clickArea) {
                    labelElements[label].clickArea[listenerName].call(labelElements[label].clickArea, listenerFn);
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
        if ((name == 'y' || name == 'yLeft') && (!plot.scales.yLeft || (plot.scales.yLeft && !plot.scales.yLeft.scale))) {
            return;
        }

        if (name == 'yRight' && (!plot.scales.yRight || (plot.scales.yRight && !plot.scales.yRight.scale))) {
            return;
        }

        // Only attempt to render if we have a paper object on which we can render and a value.
        if (this.paper && plot.labels[name] && plot.labels[name].value) {
            var x, y, labelEl;
            var fontSize = plot.labels[name].fontSize;
            var fontFamily = plot.fontFamily ? plot.fontFamily : "Roboto, arial, helvetica, sans-serif";

            if (labelElements[name] && labelElements[name].text) {
                labelElements[name].text.remove();
            } else if (!labelElements[name]) {
                labelElements[name] = {};
            }

            labelEl = labelElements[name];

            if (name == 'main') {
                x = plot.grid.width / 2;
                y = plot.labels[name].position != undefined ? plot.labels[name].position : 30;
                fontSize = fontSize != undefined ? fontSize : 18;
                labelEl.text = this.paper.text(x, y, plot.labels[name].value)
                        .attr({font: fontSize + "px " + fontFamily});
            } else if (name == 'subtitle') {
                x = plot.grid.width / 2;
                y = plot.labels[name].position != undefined ? plot.labels[name].position : 50;
                fontSize = fontSize != undefined ? fontSize : 16;
                labelEl.text = this.paper.text(x, y, plot.labels[name].value)
                        .attr({font: fontSize + "px " + fontFamily});
            } else if (name == 'y' || name == 'yLeft') {
                x = plot.grid.leftEdge - (plot.labels[name].position != undefined ? plot.labels[name].position : 55);
                y = plot.grid.height / 2;
                fontSize = fontSize != undefined ? fontSize : 14;
                labelEl.text = this.paper.text(x, y, plot.labels[name].value)
                        .attr({font: fontSize + "px " + fontFamily})
                        .transform("t0,r270");
            } else if (name == 'yRight') {
                x = plot.grid.rightEdge + (plot.labels[name].position != undefined ? plot.labels[name].position : 45);
                y = plot.grid.height / 2;
                fontSize = fontSize != undefined ? fontSize : 14;
                labelEl.text = this.paper.text(x, y, plot.labels[name].value)
                        .attr({font: fontSize + "px " + fontFamily})
                        .transform("t0,r90");
            } else if (name = 'x') {
                x = plot.grid.leftEdge + (plot.grid.rightEdge - plot.grid.leftEdge) / 2;
                y = plot.grid.height - (plot.labels[name].position != undefined ? plot.labels[name].position : 10);
                fontSize = fontSize != undefined ? fontSize : 14;
                labelEl.text = this.paper.text(x, y, plot.labels[name].value)
                        .attr({font: fontSize + "px " + fontFamily})
                        .attr({'text-anchor': 'middle'});

                // Create a rect that goes behind the x label because sometimes the x-axis tick marks get in the way.
                var bbox = labelEl.text.getBBox();
                this.paper.rect(x - bbox.width/2, y - bbox.height/2, bbox.width, bbox.height)
                        .attr({'stroke-width': 0, fill: plot.bgColor? plot.bgColor : '#fff'});
                labelEl.text.toFront();
            }

            if (labelEl.clickArea) {
                labelEl.clickArea.remove();
            }

            if (plot.labels[name].lookClickable == true) {
                // Make it clickable.
                renderClickArea.call(this, name, labelEl);
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

    /*
        GEOM STUFF BELOW HERE
     */
    var renderPointGeom = function(data, geom) {
        var i, x, y, color, size, shape, xBinWidth = null, yBinWidth = null, jitterIndex = {};
        var defaultShape = function(s) { // It's a circle.
            return "M0," + s + "A" + s + "," + s + " 0 1,1 0," + -s + "A" + s + "," + s + " 0 1,1 0," + s + "Z";
        };
        if (plot.clipRect) {
            var crx, cry, crw, crh;
            crx = (plot.grid.leftEdge - 10);
            cry = (plot.grid.topEdge - 10);
            crw = (plot.grid.rightEdge - plot.grid.leftEdge  + 20);
            crh = (plot.grid.bottomEdge - plot.grid.topEdge + 20);
        }

        if (geom.xScale.scaleType == 'discrete') {
            xBinWidth = ((plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length)) / 2;
        }

        if (geom.yScale.scaleType == 'discrete') {
            yBinWidth = ((plot.grid.topEdge - plot.grid.bottomEdge) / (geom.yScale.scale.domain().length)) / 2;
        }

        for (i = 0; i < data.length; i++) {
            x = geom.getX(data[i]);
            y = geom.getY(data[i]);
            size = geom.sizeAes && geom.sizeScale ? geom.sizeScale.scale(geom.sizeAes.getValue(data[i])) : geom.size;
            shape = geom.shapeAes && geom.shapeScale ? geom.shapeScale.scale(geom.shapeAes.getValue(data[i]) + geom.layerName) : defaultShape;
            color = geom.colorAes && geom.colorScale ? geom.colorScale.scale(geom.colorAes.getValue(data[i]) + geom.layerName) : geom.color;

            if (x == null || y == null) {
                continue;
            }

            if (xBinWidth != null && geom.position == 'jitter') {
                // don't jitter the first data point for the given X (i.e. if we only have one it shouldn't be jittered)
                var xIndex = geom.xAes.getValue(data[i]);
                x = jitterIndex[xIndex] ? (x - (xBinWidth / 2)) +(Math.random()*(xBinWidth)) : x;
                jitterIndex[xIndex] = true;
            }

            if (yBinWidth != null && geom.position == 'jitter') {
                y = (y - (yBinWidth / 2)) +(Math.random()*(yBinWidth));
            }

            var point = this.paper.path(shape(size))
                    .attr('fill', color)
                    .attr('fill-opacity', geom.opacity)
                    .attr('stroke', color)
                    .attr('stroke-opacity', geom.opacity);

            if (plot.clipRect) {
                // Have to apply the clip rect before we apply the transform or else the points don't show up.
                point.attr('clip-rect', crx + "," + cry + "," + crw + "," + crh);
            }

            point.transform('t' + x + ',' + y);

            if (geom.hoverTextAes) {
                point.attr('title', geom.hoverTextAes.getValue(data[i]));
            }

            if (geom.pointClickFnAes) {
                point.data = data[i];
                point.click(function(clickEvent) {
                    geom.pointClickFnAes.value(clickEvent, this.data);
                });
            }
        }
    };

    var renderPath = function(data, geom, groupName) {
        var xAcc = function(d) {return geom.getX(d);},
            yAcc = function(d) {var val = geom.getY(d); return val == null ? null : val;},
            path = LABKEY.vis.makePath(data, xAcc, yAcc),
            size = geom.sizeAes && geom.sizeScale ? geom.sizeScale.scale(geom.sizeAes.getValue(data)) : geom.size,
            color;

        if (geom.pathColorAes && geom.colorScale) {
            color = geom.colorScale.scale(geom.pathColorAes.getValue(data) + geom.layerName);
        } else {
            color = geom.color;
        }

        this.paper.path(path)
                .attr('stroke', color)
                .attr('stroke-width', size)
                .attr('opacity', geom.opacity);
    };

    var renderPathGeom = function(data, geom) {
        this.paper.setStart();

        if (geom.groupAes) {
            var groupedData = LABKEY.vis.groupData(data, geom.groupAes.getValue);
            for (var group in groupedData) {
                if (groupedData.hasOwnProperty(group)) {
                    renderPath.call(this, groupedData[group], geom, group);
                }
            }
        } else { // No groupAes specified, so we connect all points.
            renderPath.call(this, data, geom, null);
        }

        if (plot.clipRect) {
            var x, y, w, h;
            var geomSet = this.paper.setFinish();
            x = (plot.grid.leftEdge - 10);
            y = (plot.grid.topEdge - 10);
            w = (plot.grid.rightEdge - plot.grid.leftEdge  + 20);
            h = (plot.grid.bottomEdge - plot.grid.topEdge + 20);

            geomSet.attr('clip-rect', x + "," + y + "," + w + "," + h);
        }
    };

    var renderErrorBarGeom = function(data, geom) {
        var i, x, y, color, altColor, yBottom, yTop, error, topBar, bottomBar, middleBar;
        this.paper.setStart();

        for (i = 0; i < data.length; i ++) {
            x = geom.getX(data[i]);
            y = geom.yAes.getValue(data[i]);
            error = geom.errorAes.getValue(data[i]);
            yBottom = geom.yScale.scale(y - error);
            // if we have a log scale, yBottom will be null for negative values so set to scale min
            if (yBottom == null && geom.yScale.trans == "log") {
                yBottom = geom.yScale.range[0];
            }
            yTop = geom.yScale.scale(y + error);
            color = geom.colorAes && geom.colorScale ? geom.colorScale.scale(geom.colorAes.getValue(data[i]) + geom.layerName) : geom.color;
            altColor = geom.altColor ? geom.altColor : color;

            if (LABKEY.vis.isValid(y) && LABKEY.vis.isValid(yBottom) && LABKEY.vis.isValid(yTop)) {
                topBar = LABKEY.vis.makeLine(x-geom.width, yTop, x+geom.width, yTop);
                bottomBar = LABKEY.vis.makeLine(x-geom.width, yBottom, x+geom.width, yBottom);
                this.paper.path(topBar + bottomBar)
                        .attr('stroke-width', geom.size)
                        .attr('stroke-dasharray', geom.dashed ? "-" : "")
                        .attr('stroke', color);

                middleBar = LABKEY.vis.makeLine(x, yBottom, x, yTop);
                this.paper.path(middleBar)
                        .attr('stroke-width', geom.size)
                        .attr('stroke', altColor);
            }
        }

        if (plot.clipRect) {
            var x, y, w, h;
            var geomSet = this.paper.setFinish();
            x = (plot.grid.leftEdge - 10);
            y = (plot.grid.topEdge - 10);
            w = (plot.grid.rightEdge - plot.grid.leftEdge  + 20);
            h = (plot.grid.bottomEdge - plot.grid.topEdge + 20);

            geomSet.attr('clip-rect', x + "," + y + "," + w + "," + h);
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

    var renderBox = function(geom, group, summary) {
        var x = geom.xScale.scale(group),
            binWidth = (plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length),
            width = binWidth / 2,
            leftX = Math.floor(x - (width/2)) + .5,
            topWhisker = getTopWhisker(summary),
            bottomWhisker = getBottomWhisker(summary),
            whiskerLeft = x - (width / 4),
            whiskerRight = x + (width / 4),
            boxBottom = Math.floor(geom.yScale.scale(summary.Q1)) + .5,
            boxMiddle = Math.floor(geom.yScale.scale(summary.Q2)) + .5,
            boxTop = Math.floor(geom.yScale.scale(summary.Q3)) + .5,
            hoverText = geom.hoverTextAes ? geom.hoverTextAes.value(group, summary) : null;

        this.paper.setStart();

        var box = this.paper.rect(leftX, boxTop, width, Math.abs(boxTop - boxBottom))
                .attr('fill', geom.fill)
                .attr('fill-opacity', geom.opacity)
                .attr('stroke', geom.color)
                .attr('stroke-width', geom.lineWidth);

        if (hoverText) {
            box.attr('title', hoverText);
        }

        this.paper.path(LABKEY.vis.makeLine(leftX, boxMiddle, leftX + width, boxMiddle))
                .attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);

        if (bottomWhisker) {
            bottomWhisker = Math.floor(geom.yScale.scale(bottomWhisker)) +.5;
            this.paper.path(LABKEY.vis.makeLine(x, boxBottom, x, bottomWhisker))
                    .attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);
            this.paper.path(LABKEY.vis.makeLine(whiskerLeft, bottomWhisker, whiskerRight, bottomWhisker))
                    .attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);
        }

        if (topWhisker) {
            topWhisker = Math.floor(geom.yScale.scale(topWhisker)) +.5;
            this.paper.path(LABKEY.vis.makeLine(x, boxTop, x, topWhisker))
                    .attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);
            this.paper.path(LABKEY.vis.makeLine(whiskerLeft, topWhisker, whiskerRight, topWhisker))
                    .attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);
        }
        if (plot.clipRect) {
            var x, y, w, h;
            var geomSet = this.paper.setFinish();
            x = (plot.grid.leftEdge - 10);
            y = (plot.grid.topEdge - 10);
            w = (plot.grid.rightEdge - plot.grid.leftEdge  + 20);
            h = (plot.grid.bottomEdge - plot.grid.topEdge + 20);

            geomSet.attr('clip-rect', x + "," + y + "," + w + "," + h);
        }
    };

    var renderOutliers = function(geom, group, summary, data) {
        var i, y, color, shape, outlier, hoverText,
            x = geom.xScale.scale(group),
            binWidth = (plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length),
            width = binWidth / 2,
            smallestNotOutlier = summary.Q1 - (1.5 * summary.IQR),
            largestNotOutlier = summary.Q3 + (1.5 * summary.IQR),
            defaultShape = function(s) { // It's a circle.
                return "M0," + s + "A" + s + "," + s + " 0 1,1 0," + -s + "A" + s + "," + s + " 0 1,1 0," + s + "Z";
            };

        if(plot.clipRect) {
            var crx, cry, crw, crh;
            crx = (plot.grid.leftEdge - 10);
            cry = (plot.grid.topEdge - 10);
            crw = (plot.grid.rightEdge - plot.grid.leftEdge  + 20);
            crh = (plot.grid.bottomEdge - plot.grid.topEdge + 20);
        }

        if (geom.showOutliers) {
            for (i = 0; i < data.length; i++) {
                y = geom.yAes.getValue(data[i]);
                if (y != null && (y > largestNotOutlier || y < smallestNotOutlier)) {
                    geom.position == 'jitter' ? x - (width / 2) + (Math.random() * width) : x;
                    y = geom.yScale.scale(y); // scale the y value before plotting.
                    shape = geom.outlierShapeAes && geom.shapeScale ? geom.shapeScale.scale(geom.outlierShapeAes.getValue(data[i]) + geom.layerName) : defaultShape;
                    color = geom.outlierColorAes && geom.colorScale ? geom.colorScale.scale(geom.outlierColorAes.getValue(data[i]) + geom.layerName) : geom.outlierFill;
                    hoverText = geom.outlierHoverTextAes ? geom.outlierHoverTextAes.getValue(data[i]) : null;
                    outlier = this.paper.path(shape(geom.outlierSize))
                            .attr('fill', color)
                            .attr('fill-opacity', geom.outlierOpacity)
                            .attr('stroke', color)
                            .attr('stroke-opacity', geom.outlierOpacity);

                    if (plot.clipRect) {
                        // Have to apply the clip rect before we apply the transform or else the points don't show up.
                        outlier.attr('clip-rect', crx + "," + cry + "," + crw + "," + crh);
                    }

                    outlier.transform('t' + x + ',' + y);

                    if (hoverText) {
                        outlier.attr('title', hoverText);
                    }

                    if (geom.pointClickFnAes) {
                        outlier.data = data[i];
                        outlier.click(function(clickEvent) {
                            geom.pointClickFnAes.value(clickEvent, this.data);
                        });
                    }
                }
            }
        }
    };

    var renderBoxPlotGeom = function(data, geom) {
        var groupedData, group, summary;

        if (geom.xScale.scaleType == 'continuous') {
            console.error('Box Plots not supported for continuous data yet.');
            return;
        }

        groupedData = LABKEY.vis.groupData(data, geom.xAes.getValue);

        for (group in groupedData) {
            if (groupedData.hasOwnProperty(group)) {
                summary = LABKEY.vis.Stat.summary(groupedData[group], geom.yAes.getValue);
                if (summary.sortedValues.length > 0) {
                    renderBox.call(this, geom, group, summary);
                    renderOutliers.call(this, geom, group, summary, groupedData[group]);
                }
            }
        }
    };

    var renderBarPlotGeom = function(data, geom)
    {
        var x, y, barLeft, binWidth, barWidth, offsetWidth, barHeight,
                grouped, numXCategories, numXSubCategories, xOffsetFn, colorAcc;

        if (geom.xScale.scaleType == 'continuous')
        {
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

        xOffsetFn = function(i) {
            for (var j = 0; j < numXCategories; j++)
            {
                if (geom.xScale.domain[j] === data[i].label)
                    return barWidth * j;
            }
            return 0;
        };

        colorAcc = function (i) {
            if (geom.colorAes && geom.colorScale)
                return geom.colorScale.scale(geom.colorAes.getValue(data[i]) + geom.layerName);
            else
                return geom.fill;
        };

        for (var i = 0; i < data.length; i++)
        {
            x = grouped ? geom.xSubScale.scale(geom.xSubAes.getValue(data[i])) + xOffsetFn(i) : geom.xScale.scale(geom.xAes.getValue(data[i]));
            y = geom.yScale.scale(geom.yAes.getValue(data[i]));

            this.paper.setStart();

            // add the bars and styling for the counts
            barLeft = x - offsetWidth;
            barHeight = plot.grid.bottomEdge - y;
            var bar = this.paper.rect(barLeft, y, barWidth, barHeight)
                    .attr('title', geom.yAes.getValue(data[i]))
                    .attr('fill', colorAcc(i))
                    .attr('fill-opacity', geom.opacity)
                    .attr('stroke', geom.color)
                    .attr('stroke-width', geom.lineWidth);

            // add the bars and styling for the totals
            if (geom.showCumulativeTotals)
            {
                y = geom.yScale.scale(data[i].total);
                barLeft = x + (offsetWidth - barWidth);
                barHeight = plot.grid.bottomEdge - y;
                var bar = this.paper.rect(barLeft, y, barWidth, barHeight)
                        .attr('title', data[i].total)
                        .attr('fill', geom.fillTotal)
                        .attr('fill-opacity', geom.opacityTotal)
                        .attr('stroke', geom.colorTotal)
                        .attr('stroke-width', geom.lineWidthTotal);

                // Render legend for Individual vs Total bars
                plot.legendData = [{text: 'Total', color: geom.fillTotal}, {text: 'Individual', color: geom.fill}];
            }
        }
    };

    /*
        LEGEND STUFF BELOW HERE
     */
    var wrapText = function(text, legendWidth, fontFamily) {
        var tempText = '',
            newLines = 0,
            words = text.split(' '),
            textObj = this.paper.text(-100, -100)
                .attr('text-anchor', 'start')
                .attr('font-family', fontFamily);

        for (var i = 0; i < words.length; i++) {
            textObj.attr('text', tempText + ' ' + words[i]);
            if (textObj.getBBox().width > legendWidth && i > 0) {
                tempText = tempText + '\n' + words[i];
                newLines++;
            } else {
                tempText = tempText + ' ' + words[i];
            }
        }

        textObj.remove();

        return {text: tempText, newLines: newLines};
    };

    var renderLegend = function() {
        var i, path, color, convertedText,
            xPad = plot.scales.yRight && plot.scales.yRight.scale ? 35 : 0,
            x = plot.grid.rightEdge + 30 + xPad,
            y = plot.grid.topEdge - 6,
            legendWidth = this.paper.width - x - 25,
            defaultColor = '#333333',
            defaultPath = "M" + -5 + "," + -2.5 + "L" + 5 + "," + -2.5 + " " + 5 + "," + 2.5 + " " + -5 + "," + 2.5 + "Z",
            legendData = plot.legendData || plot.getLegendData(),
            fontFamily = plot.fontFamily ? plot.fontFamily : "Roboto, arial, helvetica, sans-serif";

        if (legendData.length > 0) {
            for (i = 0; i < legendData.length; i++) {
                color = legendData[i].color == null ? defaultColor : legendData[i].color;
                path = legendData[i].shape == null ? defaultPath : legendData[i].shape(5);
                convertedText = wrapText.call(this, legendData[i].text, legendWidth, fontFamily);

                this.paper.path(path)
                        .attr('fill', color)
                        .attr('stroke', color)
                        .transform('t' + x + ',' + y);

                y = y + (convertedText.newLines * 5);

                this.paper.text(x + 20, y, convertedText.text)
                        .attr('text-anchor', 'start')
                        .attr('title', legendData[i].text)
                        .attr({"font-family": fontFamily});

                y = y + 16 + (convertedText.newLines * 10);
            }
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
        renderLegend: renderLegend,
        renderPointGeom: renderPointGeom,
        renderPathGeom: renderPathGeom,
        renderErrorBarGeom: renderErrorBarGeom,
        renderBoxPlotGeom: renderBoxPlotGeom,
        renderBarPlotGeom: renderBarPlotGeom
    };
};
