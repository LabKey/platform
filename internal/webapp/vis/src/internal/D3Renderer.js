/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!LABKEY.vis.internal) {
    LABKEY.vis.internal = {};
}

LABKEY.vis.internal.D3Renderer = function(plot){
    var labelElements = {};

    var initLabelElements = function(){
        var labels, mainLabel = {}, yLeftLabel = {}, yRightLabel = {}, xLabel = {};

        labels = this.canvas.append('g').attr('class', plot.renderTo + '-labels');

        mainLabel.dom = labels.append('text')
                .attr('x', plot.grid.width / 2)
                .attr('y', 30)
                .attr('text-anchor', 'middle')
                .style('font', '18px verdana, arial, helvetica, sans-serif');

        yLeftLabel.dom = labels.append('text')
                .attr('text-anchor', 'middle')
                .attr('transform', 'translate(' + (plot.grid.leftEdge - 55) + ',' + (plot.grid.height / 2) + ')rotate(270)')
                .style('font', '14px verdana, arial, helvetica, sans-serif');

        yRightLabel.dom = labels.append('text')
                .attr('text-anchor', 'middle')
                .attr('transform', 'translate(' + (plot.grid.rightEdge + 45) + ',' + (plot.grid.height / 2) + ')rotate(90)')
                .style('font', '14px verdana, arial, helvetica, sans-serif');

        xLabel.dom = labels.append('text')
                .attr('x', plot.grid.leftEdge + (plot.grid.rightEdge - plot.grid.leftEdge) / 2)
                .attr('y', plot.grid.height - 10)
                .attr('text-anchor', 'middle')
                .style('font', '14px verdana, arial, helvetica, sans-serif');

        labelElements.main = mainLabel;
        labelElements.y = yLeftLabel;
        labelElements.yLeft = yLeftLabel;
        labelElements.yRight = yRightLabel;
        labelElements.x = xLabel;

        le = labelElements;
    };

    var initCanvas = function(){
        this.canvas = d3.select('#' + plot.renderTo).append('svg')
                .attr('width', plot.grid.width)
                .attr('height', plot.grid.height);
        initLabelElements.call(this);
    };

    var renderError = function(msg){

    };

    var renderGrid = function(){

    };

    var renderClickArea = function(name) {
        var box, bx, by, bh, bTransform, triangle, tx, ty, tPath,
                pad = 10, r = 4,
                labelEl = labelElements[name],
                bbox = labelEl.dom.node().getBBox(),
                bw = bbox.width + (pad * 2) + (3 * r),
                bh = bbox.height,
                labels = this.canvas.selectAll('.' + plot.renderTo + '-labels');

        if(labelEl.clickArea) {
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

        box.on('mouseover', function(){
            box.attr('stroke', '#777777');
            triangle.attr('stroke', '#777777').attr('fill', '#777777');
        });

        box.on('mouseout', function(){
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
                // but we'll keep them scoped separately.
                console.log(labelElements[label].dom);
                labelElements[label].dom.on(listenerName + '.user', listenerFn);
                if(labelElements[label].clickArea) {
                    labelElements[label].clickArea.on(listenerName + '.user', listenerFn);
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

    var renderLabel = function(name){
        if ((name == 'y' || name == 'yLeft') && (!plot.scales.yLeft || (plot.scales.yLeft && !plot.scales.yLeft.scale))) {
            return;
        }

        if (name == 'yRight' && (!plot.scales.yRight || (plot.scales.yRight && !plot.scales.yRight.scale))) {
            return;
        }

        if (this.canvas && plot.labels[name] && plot.labels[name].value) {
            labelElements[name].dom.text(plot.labels[name].value);
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

    var renderLabels = function(){
        for (var name in plot.labels) {
            if (plot.labels.hasOwnProperty(name)) {
                renderLabel.call(this, name);
            }
        }
    };

    var clearGrid = function(){};

    var renderLegend = function(){};

    var renderPointGeom = function(data, geom){};

    var renderErrorBarGeom = function(data, geom){};

    var renderBoxPlotGeom = function(data, geom){};

    var renderLineGeom = function(data, geom){};

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
        renderLineGeom: renderLineGeom,
        renderErrorBarGeom: renderErrorBarGeom,
        renderBoxPlotGeom: renderBoxPlotGeom
    };
};
