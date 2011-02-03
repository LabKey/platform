/*
 * Copyright (c) 2010-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY", "LABKEY.vis");

LABKEY.vis.Colors = {
    "SET3": ["#8DD3C7", "#FFFFB3", "#BEBADA", "#FB8072", "#80B1D3", "#FDB462", "#B3DE69", "#FCCDE5", "#D9D9D9", "#BC80BD", "#CCEBC5", "#FFED6F"],
    "PASTEL1":["#FBB4AE", "#B3CDE3", "#CCEBC5", "#DECBE4", "#FED9A6", "#FFFFCC", "#E5D8BD", "#FDDAEC", "#F2F2F2"],
    "SET1": ["#E41A1C", "#377EB8", "#4DAF4A", "#984EA3", "#FF7F0", "#FFFF33", "#A65628", "#F781BF", "#999999"],
    "PASTEL2": [ "#B3E2CD", "#FDCDAC", "#CBD5E8", "#F4CAE4", "#E6F5C9", "#FFF2AE", "#F1E2CC", "#CCCCCC"],
    "SET2": [ "#66C2A5", "#FC8D62", "#8DA0CB", "#E78AC3", "#A6D854", "#FFD92F", "#E5C494", "#B3B3B3"],
    "DARK2": ["#1B9E77", "#D95F2", "#7570B3", "#E7298A", "#66A61E", "#E6AB2", "#A6761D", "#666666"]
};

/**
 * A singleton class to convert SVG text to downloadable images
 * Note that this cannot be used to generate an image to be loaded into an image tag as a POST is required
 */
LABKEY.vis.SVGConverter = {
    FORMAT_PDF: "pdf",
    FORMAT_PNG: "png",

    /**
     * Converts the passed in svg string to a static image that the user will be prompted to save
     * @param svg SVG as String. Can be retrieved using innerHTML
     * @param format Either LABKEY.vis.SVGConverter.FORMAT_PDF ("pdf") or LABKEY.vis.SVGConverter.FORMAT_PNG ("png")
     */
    convert: function(svg, format) {
            // Insert a hidden <form> into to page, put the JSON into it, and submit it - the server's response
            // will make the browser pop up a dialog

        var action;
        var convertTo = format ? format.toLowerCase() : this.FORMAT_PNG;
        if (this.FORMAT_PDF == convertTo)
            action = "exportPDF";
        else if (this.FORMAT_PNG == convertTo)
            action = "exportImage";
        else
            throw "Unknown format: " + format;

        var newForm = Ext.DomHelper.append(document.getElementsByTagName('body')[0],
        {tag:"form", method:"POST", action:LABKEY.ActionURL.buildURL("visualization", action),
            children:[{tag:"input", type:"hidden", name:"svg"}]});
        newForm.svg.value = svg;
        newForm.submit();
    }
};

LABKEY.vis.XYChartComponent = Ext.extend(Ext.BoxComponent, {

    /**
     * @style.markSize width in pixels of data marks (lines, bars, dots) (optional)
     * @style.markAlpha degree of transparency of data marks - 0 is invisible, .5 is ghostly, 1 is solid (optional)
     * @style.seriesColors  a pv.Scale.ordinal of color values to use for different series on a chart. (optional)
     * @style.strokeColor color for the grid strokes on the chart (optional)
     * @style.firstStrokeColor color for the first grid stroke on each axis of the chart (the border lines along each axis) (optional)
     *
     * @axes
     * @series
     *
     */
    style : {
      markSize : 4,
      markAlpha : .5,
      seriesColors : pv.colors.apply(pv, LABKEY.vis.Colors.SET2),
      strokeColor : "#eee",
      firstStrokeColor : "#000",
      outerMargin: 60,
      smallMargin: 10,
      titleFont: "bold 18px Arial, sans-serif",
      titleHeight:50,
      axisLabelFont: "bold 14px Arial, sans-serif",
      axisLabelHeight:14,
      legendWidth: 120
    },

    rootVisPanel: null,

    initComponent: function () {
        LABKEY.vis.XYChartComponent.superclass.initComponent.call(this);
    },

    render : function (container, position) {
       LABKEY.vis.XYChartComponent.superclass.render.call(this, container, position);

       this.rootVisPanel = new pv.Panel()
               .width(this.width)
               .height(this.height)
               .canvas(this.id);

       //Chart width is outer width - left margin - legendWidth - 2 * (margin around legend)
       this.chartWidth = this.width - this.style.outerMargin - this.style.legendWidth - 2 * this.style.smallMargin;
       this.chartHeight = this.height - 2 * this.style.outerMargin;
    },

    /**
     * Export the current rendered chart as a pdf or png
     * @param format Either LABKEY.vis.SVGConverter.FORMAT_PDF ("pdf") or LABKEY.vis.SVGConverter.FORMAT_PNG ("png") Defaults to png
     */
    exportImage: function(format) {
        LABKEY.vis.SVGConverter.convert(this.rootVisPanel.canvas().innerHTML, format)
    },

    /**
     *
     * Draws a rule on the chart with tick marks, values, grid strokes, and a caption
     *
     * @param scale a pv.Scale
     * @param edge what edge of the chart do draw the rule on - one of "left", "right", "top", "bottom"
     * @param axis Axis info as passed ins
     */
    drawRule : function (scale, edge, axis)
    {
        var style = this.style;
        var start = scale.domain()[0];

        var rule = this.chartPanel.add(pv.Rule)
                .data(scale.ticks())
                .strokeStyle(function (d) { return d > start ? style.strokeColor : style.firstStrokeColor });

        if (axis.scale == "log")
            rule.visible(function() {
                return this.index % 9 == 0}); //Don't like this rule, but works with ticks that protovis generates

        var angle;

        switch (edge)
        {
            case "left":
                rule.bottom(scale);
                angle = - Math.PI / 2;
                break;
            case "bottom":
                rule.left(scale);
                angle = 0;
                break;
            case "right":
                rule.top(scale);
                angle = Math.PI / 2;
                break;
            case "top":
                rule.bottom(scale);
                angle = 0;
                break;
        }

        rule.anchor(edge).add(pv.Label)
                .text(scale.tickFormat)
                .visible(function (d) {
                        if (axis.scale == "log")
                            return this.index % 9 == 0;
                        else
                            return d > start || start < 0 });


        if (axis.caption) {
            var label = this.rootVisPanel.anchor(edge).add(pv.Label)
                    .data([axis.caption])
                    .textAngle(angle)
                    .font(this.style.axisLabelFont);
            if (edge == "left")
                label.left(this.style.smallMargin + this.style.axisLabelHeight);
            if (edge == "bottom")
                label.bottom(this.style.smallMargin).top(null);
        }

    },

    /**
     *
     * @param style style object as
     * @param series
     */
    drawLegend: function (style, series) {

        var legend = this.rootVisPanel.add(pv.Panel)
                .top(this.style.outerMargin)
                .right(this.style.smallMargin)
                .width(this.style.legendWidth);

        legend.add(pv.Dot).
                data(series)
                .fillStyle(function (d) {return style.seriesColors(d.caption).alpha(style.markAlpha)})
                .strokeStyle(function (d) {return style.seriesColors(d.caption).alpha(style.markAlpha)})
                .top(function() {return this.index * 25;})
                .left(5)
                .shapeSize(50)
                .shape("square")
                .anchor("right")
                .add(pv.Label)
                .text(function(d) {return d.caption});

    },

    addChartPanel : function () {
        this.chartPanel = this.rootVisPanel.add(pv.Panel)
                .top(this.style.outerMargin)
                .left(this.style.outerMargin)
                .width(this.chartWidth)
                .height(this.chartHeight);

        return this.chartPanel;
    },

    /**
     * @param axis
     * @param pixels -- number of pixels the scale should map to
     * @param series -- All of the series that are going to be used on this scale.
     * @param getterName -- Name of the function used to get values for the series
     * @param pixels
     */
    getScale : function (axis, pixels, series, getterName) {

        var domain;

        if (axis.max)
            domain = [axis.min || 0, axis.max];
        else
            domain = this.collect(series, getterName);

        return pv.Scale[axis.scale || "linear"](domain).nice().range(0, pixels);
    },

    /**
     * traverse a list of lists of objects, call the given function on each, return flat list of all returns
     *
     * @param listOfListOfObject [ [{a:1, b:2}, {a:2, b:1}], [{a:3, b:0}, {a:2, b:1}] ]
     * @param getterName Name of function to call on all objects in all lists, such as "getX"
     */
    collect : function (listOfListOfObject, getterName) {
        var all = [];
        listOfListOfObject.forEach( function (s) { s.data.forEach(function (r) { all.push(s[getterName](r)) }) });
        return all;
    },

    createGetter: function(propName, makeNullsZero) {
        return function (o){
            var val = o[propName];
            if (null != val && typeof val == "object")
                val = ("value" in val ? val.value : val);

            return val == null && makeNullsZero ? 0 : val;
        };
    }
});


LABKEY.vis.ScatterChart = Ext.extend(LABKEY.vis.XYChartComponent, {

    initComponent : function () {
        LABKEY.vis.ScatterChart.superclass.initComponent.call(this);
    },

   render : function (container, position) {
       LABKEY.vis.ScatterChart.superclass.render.call(this, container, position);

       var x = this.getScale(this.axes["x"], this.chartWidth, this.series, "getX");
       var y = this.getScale(this.axes["y"], this.chartHeight, this.series, "getY");
       var dataPanel = this.addChartPanel();
       this.drawRule(x, "bottom", this.axes["x"]);
       this.drawRule(y, "left", this.axes["y"]);

       var style = this.style;

       this.series.forEach(function (s) {
           var color = style.seriesColors(s.caption).alpha(style.markAlpha);
           dataPanel.add(pv.Dot)
             .data(s.data)
            .left(function (d) {return x(s.getX(d))})
            .bottom(function (d) {return y(s.getY(d))})
            .strokeStyle(color)
            .fillStyle(color)
            .shapeSize(style.markSize);
       });

       this.drawLegend(style, this.series);
       this.rootVisPanel.render();

   },

    initSeries: function() {
         var chartComponent = this;
         this.series.forEach(function (series) {
             if (series.xProperty && !series.getX)
                 series.getX = chartComponent.createGetter(series.xProperty, true);
             if (series.yProperty && !series.getY)
                 series.getY = chartComponent.createGetter(series.yProperty, true);
     });
    }

});

LABKEY.vis.LineChart = Ext.extend(LABKEY.vis.XYChartComponent, {


    initComponent : function () {
        LABKEY.vis.LineChart.superclass.initComponent.call(this);
        this.initSeries();
    },

   render : function (container, position) {
       LABKEY.vis.LineChart.superclass.render.call(this, container, position);

       var x = this.getScale(this.axes["x"], this.chartWidth, this.series, "getX");
       var y = this.getScale(this.axes["y"], this.chartHeight, this.series, "getY");
       var dataPanel = this.addChartPanel();
       this.drawRule(x, "bottom", this.axes["x"]);
       this.drawRule(y, "left", this.axes["y"]);

       var style = this.style;
       this.series.forEach(function (s) {
           var color = style.seriesColors(s.caption).alpha(style.markAlpha);
           dataPanel.add(pv.Line)
             .data(s.data)
            .left(function (d) {return x(s.getX(d))})
            .bottom(function (d) {return y(s.getY(d))})
            .strokeStyle(color)
            .lineWidth(style.markSize)
            .add(pv.Dot)
                .shapeSize(style.markSize)
                .title(s.getTitle)
       }, this);

       this.drawLegend(style, this.series);
       this.rootVisPanel.render();
   },

   initSeries: function() {
        var chartComponent = this;
        this.series.forEach(function (series) {
            if (series.xProperty && !series.getX)
                series.getX = chartComponent.createGetter(series.xProperty, false);
            if (series.yProperty && !series.getY)
                series.getY = chartComponent.createGetter(series.yProperty, false);

            if (!series.getTitle)
                series.getTitle = function (d) {return series.caption + ": " + series.getX(d) + ",  " + series.getY(d)};

            //The graphing doesn't work with missing values, so we strip them out of the series in the first place.
            //Consider, replace series data with static x/y values so don't have to call the getter so often
            var cleanData = [];
            series.data.forEach(function (d) {
                var x = series.getX(d);
                var y = series.getY(d);

                if (null != x && !isNaN(x) && null != y && !isNaN(y))
                    cleanData.push(d);
            });
            series.data = cleanData;
    });
   }
});

LABKEY.vis.BarChart = Ext.extend(LABKEY.vis.XYChartComponent, {


    initComponent : function () {
        LABKEY.vis.BarChart.superclass.initComponent.call(this);
    },

   render : function (container, position) {
       LABKEY.vis.BarChart.superclass.render.call(this, container, position);

       var x = this.getScale(this.axes["x"], this.chartWidth, this.series, "getX");
       var y = pv.Scale.ordinal(this.collect(this.series, "getY")).splitBanded(0, this.height, 0.8);

       this.drawRule(x, "bottom", this.axes["x"]);
       //this.drawRule(y, "bottom", this.axes["y"].caption);

       var style = this.style;
       var dataPanel = this.addChartPanel();
       var series = this.series;

       var bar = dataPanel
               .data(series)
                .top(function() { return y(this.index)})
                .height(y.range().band)
               .add(pv.Bar)
                .data(function(s) {return s.data})
                .top(function () {
                    return this.parent.index * y.range().band / series.length
                    } )
                .height(y.range().band / series.length)
                .left(0)
                .width(function (d) {return x(d.x) });
                //.fillStyle(style.seriesColors.by(parent.data().caption))


       this.drawLegend(style, this.series);
       this.rootVisPanel.render();
   }
});
