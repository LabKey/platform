/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY", "LABKEY.vis");

/**
 * Each of these is a color set defined for "categorical" color schemes from ColorBrewer
 * See ColorBrewer http://colorbrewer2.org/ and large palettes at http://learnr.wordpress.com/2009/04/15/ggplot2-qualitative-colour-palettes/
 */
LABKEY.vis.Colors = {
    "SET3": ["#8DD3C7", "#FFFFB3", "#BEBADA", "#FB8072", "#80B1D3", "#FDB462", "#B3DE69", "#FCCDE5", "#D9D9D9", "#BC80BD", "#CCEBC5", "#FFED6F"],
    "PASTEL1":["#FBB4AE", "#B3CDE3", "#CCEBC5", "#DECBE4", "#FED9A6", "#FFFFCC", "#E5D8BD", "#FDDAEC", "#F2F2F2"],
    "SET1": ["#E41A1C", "#377EB8", "#4DAF4A", "#984EA3", "#FF7F0", "#FFFF33", "#A65628", "#F781BF", "#999999"],
    "PASTEL2": [ "#B3E2CD", "#FDCDAC", "#CBD5E8", "#F4CAE4", "#E6F5C9", "#FFF2AE", "#F1E2CC", "#CCCCCC"],
    "SET2": [ "#66C2A5", "#FC8D62", "#8DA0CB", "#E78AC3", "#A6D854", "#FFD92F", "#E5C494", "#B3B3B3"],
    "DARK2": ["#1B9E77", "#D95F2", "#7570B3", "#E7298A", "#66A61E", "#E6AB2", "#A6761D", "#666666"]
};

//By default we use a different shape for every series to help deal with colorblindness
//Only 5 shapes. For non-colorblind people we get 40 different shape/color combinations
LABKEY.vis.Shapes =[{name:"cross", markSize:20, lineWidth:4},
    {name:"triangle", markSize:20, lineWidth:1},
    {name:"diamond", markSize:20, lineWidth:1},
    {name:"square", markSize:20, lineWidth:1},
    {name:"circle", markSize:20, lineWidth:1}];

//See issue 11788. Want to reuse same shape for same series over time on same page, even thru redraws.
//We'll keep a mapping of series caption to series style here.
LABKEY.vis.SeriesStyleMap = {};

/**
 * A singleton class to convert SVG text to downloadable images
 * Note that this cannot be used to generate an image to be loaded into an image tag as a POST is required
 */
LABKEY.vis.SVGConverter = {
    FORMAT_PDF: "pdf",
    FORMAT_PNG: "png",

    /**
     * Converts the passed in svg string to a static image that the user will be prompted to save
     * @param svg SVG as String or Dom Node. 
     * @param format Either LABKEY.vis.SVGConverter.FORMAT_PDF ("pdf") or LABKEY.vis.SVGConverter.FORMAT_PNG ("png")
     */
    convert: function(svg, format) {
        if (null != svg && typeof svg != "string")
            svg = this.svgToStr(svg);

        if (null == svg)
        {
            alert("Export not supported on this browser");
            return;
        }
        
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
        {tag:"form", method:"POST", action:LABKEY.ActionURL.buildURL("visualization", action), target:'_blank',
            children:[{tag:"input", type:"hidden", name:"svg"}]});
        newForm.svg.value = svg;
        newForm.submit();
    },

    /** Transforms the given svg root node and all of its children into an XML string,
     *
     * @param node DOM node to turn into a string
     * @returns legal SVG string.
     */
    svgToStr: function(node)
    {

        var xml;
        var svgns = 'http://www.w3.org/2000/svg';

        if (typeof XMLSerializer != 'undefined')
        { // non-IE browsers
            xml = (new XMLSerializer().serializeToString(node));
        }
        else
        {
            xml = node.xml;
        }
        // add our namespace declarations
        var nsString = '';
        if (xml.indexOf('xmlns=') == -1)
        {
            nsString = 'xmlns="' + svgns + '" ';
        }

        xml = xml.replace(/<([^ ]+)/, '<$1 ' + nsString + ' ');

        return xml;
    }

};

LABKEY.vis.getAxisRange = function (values, scaleType) {
    //pv scales blow up on bad values
    if (scaleType == "log")
        values = values.filter(function(n) {return n > 0});
    var scale = new pv.Scale[scaleType || "linear"](values);
    scale.nice();
    var domain = scale.domain();
    return {min:domain[0],max:domain[domain.length-1]};
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
      leftMargin: 10, // this value will be reset later if the left y-axis is needed
      rightMargin: 10, // this value will be reset later if the right y-axis is needed
      titleFont: "bold 18px Arial, sans-serif",
      titleHeight:50,
      axisLabelFont: "bold 14px Arial, sans-serif",
      axisLabelHeight:14,
      legendWidth: 120
    },

    // booleans for which sides of the chart have axes
    side: {
        left: false,
        right: false,
        bottom: true
    },

    //Default style for series. Can be overridden
    seriesStyle : {
        lineWidth: 4,
        markAlpha : .75,
        shape: null, //If null will be assigned automatically using next available shape from LABKEY.vis.Shapes
        markColor: null //If null will be assigned automatically using next available seriesColor
    },

    rootVisPanel: null,

    initComponent: function () {
        LABKEY.vis.XYChartComponent.superclass.initComponent.call(this);
    },

    initSeries: function() {
         var chartComponent = this;
         var seriesIndex = 0;
         this.series.forEach(function (series) {
            series.style = series.style || LABKEY.vis.SeriesStyleMap[series.caption] || {};
            var style = series.style;
            Ext.applyIf(style, chartComponent.seriesStyle);
            if (!style.markColor)
                style.markColor = chartComponent.style.seriesColors(series.caption).alpha(style.markAlpha);
            if (!style.shape)
                style.shape = LABKEY.vis.Shapes[seriesIndex % LABKEY.vis.Shapes.length];
            LABKEY.vis.SeriesStyleMap[series.caption] = style; //Stash this away for later

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

            seriesIndex++;
         });  
    },

    render : function (container, position) {
       LABKEY.vis.XYChartComponent.superclass.render.call(this, container, position);

       this.rootVisPanel = new pv.Panel()
               .width(this.width)
               .height(this.height)
               .canvas(this.id);

        if (this.title)
        {
            this.rootVisPanel.anchor("top").add(pv.Label)
            .data([this.title])
            .bottom(null)
            .top(this.style.smallMargin)
            .font(this.style.titleFont);
        }

       //Chart width is outer width - left margin - legendWidth - 2 * (margin around legend)
       this.chartWidth = this.width - this.style.leftMargin - this.style.rightMargin - this.style.legendWidth - 2 * this.style.smallMargin;
       this.chartHeight = this.height - 2 * this.style.outerMargin;
    },

    /**
     * Export the current rendered chart as a pdf or png
     * @param format Either LABKEY.vis.SVGConverter.FORMAT_PDF ("pdf") or LABKEY.vis.SVGConverter.FORMAT_PNG ("png") Defaults to png
     */
    exportImage: function(format) {
        LABKEY.vis.SVGConverter.convert(this.rootVisPanel.scene.$g, format)
    },

    /**
     * Return true if the chart can be exported. This depends on whether we are using SVG to render
     */
    canExport: function() {
        return pv.Scene == pv.SvgScene;
    },

    //private -- for testing purposes
    getSerializedXML: function () {
        return LABKEY.vis.SVGConverter.svgToStr(this.rootVisPanel.scene.$g);
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
        var end = scale.domain()[1];
        var sides = this.side;

        var rule = this.chartPanel.add(pv.Rule)
                .data(scale.ticks())
                .strokeStyle(function (d) {
                    var ss = style.strokeColor;
                    // add dark stroke for sides that have axis
                    if(((edge == "right" || edge == "left") && sides.bottom && d == start)) // dark stroke on bottom
                        ss = style.firstStrokeColor;
                    else if(edge == "bottom" && sides.left && d == start) // dark stoke on left side
                        ss = style.firstStrokeColor;
                    else if(edge == "bottom" && sides.right && d == end) // dark stoke on right side
                        ss = style.firstStrokeColor;
            
                    return ss;
                });

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
                rule.bottom(scale);
                angle = Math.PI / 2;
                break;
            case "top":
                rule.bottom(scale);
                angle = 0;
                break;
        }

        rule.anchor(edge).add(pv.Label)
                //See issue 11789. Work around bug with pv number formatting
                .text(this.getTickRenderer(scale))
                .visible(function (d) {
                        if (axis.scale == "log")
                            return this.index % 9 == 0;
                        else
                            return d > start || start < 0 });


        if (axis.caption) {
            var label = this.rootVisPanel.anchor(edge).add(pv.Label)
                    .data([axis.caption])
                    .textAngle(angle)
                    .textAlign("center")
                    .font(this.style.axisLabelFont);
            if (edge == "left")
                label.left(this.style.smallMargin + this.style.axisLabelHeight);
            if(edge == "right")
                label.left(this.width - (2 * this.style.smallMargin) - this.style.legendWidth - this.style.axisLabelHeight);
            if (edge == "bottom")
                label.bottom(this.style.smallMargin).top(null).left((this.chartWidth / 2) + this.style.leftMargin);
        }

    },

    /**
     * Return a renderer that uses a consistent number of decimal places and uses exponential notation only when necessary
     * @param scale
     */
    getTickRenderer: function (scale) {
        var tickArray = scale.ticks();
        //Use pv default renderer for dates
        if (Ext.isDate(tickArray[0]))
            return scale.tickFormat;
        
        var maxDecimals = 0;
        tickArray.forEach(function(val) {
            var str = String(val);
            var decimalPos = str.indexOf(".");
            if (str.indexOf("e") == -1 && decimalPos != -1)
            {
                //Can end up with lots of decimals due to inexact tick calculations e.g. 1.6000000002 or 1.59999999998
                if (str.length - decimalPos > 8)
                {
                    str = str.substring(0, decimalPos + 8);
                    if (str.charAt(str.length - 1) == '0')
                        str = str.replace(/0*$/, "");
                    else if (str.charAt(str.length - 1) == '9')
                        str = str.replace(/9*$/, "");
                }

                maxDecimals = Math.max(maxDecimals, str.length - decimalPos -1);
            }
        });

        //Tricky way to repeat a string using array and join...
        var fmtString = maxDecimals > 0 ? "0,0." + new Array(maxDecimals + 1).join("0") : "0,0";
        return function (n) {
            var str = String(n);
            if (str.indexOf("e") == -1 && n < 1e8) //JS will give you up to 20 zeros
                return Ext.util.Format.number(n, fmtString);
            else
                return str;
        }
    },

    drawLegend: function () {
        var legend = this.rootVisPanel.add(pv.Panel)
                .top(this.style.outerMargin)
                .right(this.style.smallMargin)
                .width(this.style.legendWidth);

        legend.add(pv.Dot)
                .data(this.series) //One dot for each series
                .fillStyle(function (d) {return d.style.markColor})
                .strokeStyle(function (d) {return d.style.markColor})
                .top(function() {return this.index * 25;})
                .left(5)
                .size(function (d) {return d.style.shape.markSize})
                .lineWidth(function (d) {return d.style.shape.lineWidth})
                .shape(function (d) {return d.style.shape.name})
                .anchor("right")
                .add(pv.Label)
                .text(function(d) {return d.caption});

    },

    addChartPanel : function () {
        this.chartPanel = this.rootVisPanel.add(pv.Panel)
                .top(this.style.outerMargin)
                .left(this.style.leftMargin)
                .right(this.style.rightMargin)
                .width(this.chartWidth)
                .height(this.chartHeight);

        return this.chartPanel;
    },

    addDataPanel : function () {
        //Add the inner panel where data will be drawn.
        //This aligns with the chartPanel but is clipped differently
        //We use 3 panels for correct alignment/clipping

        //chartPanel: Not clipped, allows ticks/labels to be drawn at negative margins
        //clipPanel: Clipped to data area but just enough "slop" so that points drawn on margin draw correctly
        //dataPanel: Range in which data should be drawn. Clipped by clipPanel so points can just overlap axes

        var slopMargin = 5;
        var clipPanel = this.chartPanel.add(pv.Panel).margin(-slopMargin).overflow("hidden");
        this.dataPanel = clipPanel.add(pv.Panel).margin(slopMargin).overflow("visible");

        return this.dataPanel;
    },

    /**
     * @param axis
     * @param pixels -- number of pixels the scale should map to
     * @param series -- All of the series that are going to be used on this scale.
     * @param getterName -- Name of the function used to get values for the series
     * @param axisName -- Name of the axis which the scale is being applied (left, right)
     */
    getScale : function (axis, pixels, series, getterName, axisName) {

        var domain;
        var scale;

        var min = axis.min;
        var max = axis.max;

        //Both min and max are explicitly defined in axis. Let the person use them. However, still protect logs..
        //Don't use illegal values for log min & max
        var scaleType = axis.scale || "linear"; //default scale
        if (min !== undefined && max !== undefined && (scaleType != "log" || (min > 0 && max > 0)))
        {
            scale =  pv.Scale[scaleType](min, max).range(0, pixels);
            if (scaleType == "log") //Even if scale min & max are fine, still need to pin any possible bad values
                scale._min = Number.MIN_VALUE; 

            return scale;
        }

        //Round using a suitable power of 10
        function round(x, dir) {
            if (x == 0)
                return x;
            var absVal = Math.abs(x);

            var step = Math.pow(10, Math.round(Math.log(absVal) / Math.log(10)) - 1);
            return dir < 0 ? Math.floor(x / step) * step : Math.ceil(x/step) * step;
        }

        domain = this.collect(series, getterName, axisName);
        //If no data, just return something reasonable
        if (domain.length == 0)
            return pv.Scale.linear(0, 1).range(0, pixels);

        if (max === undefined)
            max = round(pv.max(domain), 1);
        if (min === undefined)
            min = round(pv.min(domain), -1);

        //For logs if some values are <=0 and rest are >= 1, pin everything <=0 to 1
        //However, if some values are >0 and < 1 (negative logs), pin <= 0 to smallest non-negative integer.
        ///For each scale we'll need to store a pin value.
        if(scaleType == "log" && min <= 0) {
            var minPos = undefined;
            domain.forEach(function (x) {
                if (x > 0 && (x < minPos || minPos === undefined))
                    minPos = x;
            });

            //It's possible that *all* of the values are <= 0, so be careful...
            if (minPos === undefined)
                minPos = 1;

            if (min < minPos)
                min = minPos;
            if (max < minPos)
                max = minPos;
        }

        if (min == max) //pv blows up if min & max are the same
            max = min + 1;


        scale = pv.Scale[axis.scale || "linear"](min, max).range(0, pixels);
        // Would be nicer to override pv.Scale with a subclass that does this, but don't want to dive into pv source
        scale._min = min; //Stash these where our function can find them
        scale._max = max;
        //If user didn't explicitly specify either min or max, we can use nice() to give us better rounding
        if (axis.min === undefined && axis.max === undefined)
            scale.nice();
        
        return scale;
    },

    /**
     * traverse a list of lists of objects, call the given function on each, return flat list of all returns
     *
     * @param listOfListOfObject [ [{a:1, b:2}, {a:2, b:1}], [{a:3, b:0}, {a:2, b:1}] ]
     * @param getterName Name of function to call on all objects in all lists, such as "getX"
     */
    collect : function (listOfListOfObject, getterName, axisName) {
        var all = [];
        listOfListOfObject.forEach( function (s) {
            s.data.forEach(function (r) {
                if(!axisName || !s.axis || axisName == s.axis){
                    all.push(s[getterName](r));
                }
            }); 
        });
        return all;
    },

    createGetter: function(propName, makeNullsZero) {
        return function (o){
            var val = o[propName];
            if (null != val && typeof val == "object")
                val = ("value" in val ? val.value : val);

            return val == null && makeNullsZero ? 0 : val;
        };
    },

    //In the case of logs, we pin all values to things that are in scale when we draw them.
    //scale._min gets stashed by getScale if we need to pin
    pinMin: function(scale, value) {
        if ("_min" in scale && value < scale._min)
            return 0;
        else
            return scale(value);
    },

    // if there is a left axes, set the leftMargin style value
    setLeftMargin: function() {
        var newMarginVal = 90;
        this.chartWidth -= (newMarginVal - this.style.leftMargin);
        this.style.leftMargin = newMarginVal;
    },

    // if there is a right axes, set the rightMargin style value
    setRightMargin: function() {
        var newMarginVal = 90;
        this.chartWidth -= (newMarginVal - this.style.rightMargin);
        this.style.rightMargin = newMarginVal;
    }
});


LABKEY.vis.ScatterChart = Ext.extend(LABKEY.vis.XYChartComponent, {

    initComponent : function () {
        LABKEY.vis.ScatterChart.superclass.initComponent.call(this);
        this.initSeries();
    },

   render : function (container, position) {
       LABKEY.vis.ScatterChart.superclass.render.call(this, container, position);

       // if the chart has a left axis, set up the scale for it
       var left;
       if(this.axes["left"]){
           left = this.getScale(this.axes["left"], this.chartHeight, this.series, "getY", "left");
           this.setLeftMargin();
           this.side.left = true;
       }

       // if the chart has a right axis, set up the scale for it
       var right;
       if(this.axes["right"]){
           right = this.getScale(this.axes["right"], this.chartHeight, this.series, "getY", "right");
           this.setRightMargin();
           this.side.right = true;
       }

       var bottom = this.getScale(this.axes["bottom"], this.chartWidth, this.series, "getX");
       var chartPanel = this.addChartPanel();
       this.drawRule(bottom, "bottom", this.axes["bottom"]);
       if(left) this.drawRule(left, "left", this.axes["left"]);
       if(right) this.drawRule(right, "right", this.axes["right"]);

       //To get z ordering right we add the dataPanel after rules etc have been drawn.
       var dataPanel = this.addDataPanel();

       var chartComponent = this;
       this.series.forEach(function (s) {
           var style = s.style;
           var color = style.markColor;
           dataPanel.add(pv.Dot)
            .data(s.data)
            .left(function (d) {return bottom(s.getX(d))})
            .bottom(function (d) {return chartComponent.pinMin(s.axis == "right" ? right : left, s.getY(d))})
            .strokeStyle(color)
            .fillStyle(color)
            .size(style.shape.markSize)
            .title(s.getTitle)
            .lineWidth(style.shape.lineWidth)
            .shape(style.shape.name);
       });

       this.drawLegend();
       this.rootVisPanel.render();

   }
});

LABKEY.vis.LineChart = Ext.extend(LABKEY.vis.XYChartComponent, {


    initComponent : function () {
        LABKEY.vis.LineChart.superclass.initComponent.call(this);
        this.initSeries();
    },

   render : function (container, position) {
       LABKEY.vis.LineChart.superclass.render.call(this, container, position);

       // for backwards-compatability, allow the bottom axes name to be "x" and the left to be "y"
       var bottomAxesName = this.axes["bottom"] ? "bottom" : "x";
       var leftAxesName = this.axes["left"] ? "left" : "y";

       // if the chart has a left axis, set up the scale for it
       var left;
       if(this.axes[leftAxesName]){
           left = this.getScale(this.axes[leftAxesName], this.chartHeight, this.series, "getY", "left");
           this.setLeftMargin();
           this.side.left = true;
       }

       // if the chart has a right axis, set up the scale for it
       var right;
       if(this.axes["right"]){
           right = this.getScale(this.axes["right"], this.chartHeight, this.series, "getY", "right");
           this.setRightMargin();
           this.side.right = true;
       }
       
       var bottom = this.getScale(this.axes[bottomAxesName], this.chartWidth, this.series, "getX");
       var chartPanel = this.addChartPanel();

       this.drawRule(bottom, "bottom", this.axes[bottomAxesName]);
       if(left) this.drawRule(left, "left", this.axes[leftAxesName]);
       if(right) this.drawRule(right, "right", this.axes["right"]);

       //To get z ordering right we add the dataPanel after rules etc have been drawn.
       var dataPanel = this.addDataPanel();

       var chartComponent = this;
       var seriesIndex;
       this.series.forEach(function (s) {
           var style = s.style;
           var color = style.markColor;
           var lines = dataPanel.add(pv.Line)
             .data(s.data)
            .left(function (d) { return bottom(s.getX(d))})
            .bottom(function (d) { return chartComponent.pinMin(s.axis == "right" ? right : left, s.getY(d))})
            .strokeStyle(color)
            .lineWidth(style.lineWidth);
            if(!style.shape.hidden){
               lines.add(pv.Dot)
                    .size(style.shape.markSize)
                    .fillStyle(color)
                    .title(s.getTitle)
                    .lineWidth(style.shape.lineWidth)
                    .shape(style.shape.name);
            }

           seriesIndex++;
       }, this);

       this.drawLegend();
       this.rootVisPanel.render();
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


       this.drawLegend();
       this.rootVisPanel.render();
   }
});

