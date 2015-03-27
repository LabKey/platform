/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2012-2015 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */


/**
 * @name LABKEY.vis.Plot
 * @class Plot which allows a user to programmatically create a plot/visualization.
 * @description
 * @param {Object} config An object that contains the following properties.
 * @param {String} config.renderTo The id of the div/span to insert the svg element into.
 * @param {Number} config.width The plot width in pixels. This is the width of the entire plot, including margins, the
 *      legend, and labels.
 * @param {Number} config.height The plot height in pixels. This is the height of the entire plot, including margins and
 *      labels.
 * @param {Array} [config.data] (Optional) The array of data used while rendering the plot. This array will be used in
 *      layers that do not have any data specified. <em>Note:</em> While config.data is optional, if it is not present
 *      in the Plot object it must be defined within each {@link LABKEY.vis.Layer}. Data must be array based, with each
 *      row of data being an item in the array. The format of each row does not matter, you define how the data is
 *      accessed within the <strong>config.aes</strong> object.
 * @param {Object} [config.aes] (Optional) An object containing all of the requested aesthetic mappings. Like
 *      <em>config.data</em>, config.aes is optional at the plot level because it can be defined at the layer level as
 *      well, however, if config.aes is not present at the plot level it must be defined within each
 *      {@link LABKEY.vis.Layer}}. The aesthetic mappings required depend entirely on the {@link LABKEY.vis.Geom}s being
 *      used in the plot. The only maps required are <strong><em>config.aes.x</em></strong> and
 *      <em><strong>config.aes.y</strong> (or alternatively yLeft or yRight)</em>. To find out the available aesthetic
 *      mappings for your plot, please see the documentation for each Geom you are using.
 * @param {Array} config.layers An array of {@link LABKEY.vis.Layer} objects.
 * @param {Object} [config.scales] (Optional) An object that describes the scales needed for each axis or dimension. If
 *      not defined by the user we do our best to create a default scale determined by the data given, however in some
 *      cases we will not be able to construct a scale, and we will display or throw an error. The possible scales are
 *      <strong>x</strong>, <strong>y (or yLeft)</strong>, <strong>yRight</strong>, <strong>color</strong>,
 *      <strong>shape</strong>, and <strong>size</strong>. Each scale object will have the following properties:
 *      <ul>
 *          <li><strong>scaleType:</strong> possible values "continuous" or "discrete".</li>
 *          <li><strong>trans:</strong> with values "linear" or "log". Controls the transformation of the data on
 *          the grid.</li>
 *          <li><strong>min:</strong> (<em>deprecated, use domain</em>) the minimum expected input value. Used to control what is visible on the grid.</li>
 *          <li><strong>max:</strong> (<em>deprecated, use domain</em>) the maximum expected input value. Used to control what is visible on the grid.</li>
 *          <li><strong>domain:</strong> For continuous scales it is an array of [min, max]. For discrete scales
 *              it is an an array of all possible input values to the scale.</li>
 *          <li><strong>range:</strong> An array of values that all input values (the domain) will be mapped to. Not
 *          used for any axis scales. For continuous color scales it is an array[min, max] hex values.</li>
 *      </ul>
 * @param {Object} [config.labels] (Optional) An object with the following properties: main, x, y (or yLeft), yRight.
 *      Each property can have a {String} value, {Boolean} lookClickable, and {Object} listeners. The value is the text
 *      that will appear on the label. lookClickable toggles if the label will appear clickable. The listeners property
 *      allows the user to specify listeners on the labels such as click, hover, etc, as well as the functions to
 *      execute when the events occur. Each label will be an object that has the following properties:
 *      <ul>
 *          <li>
 *              <strong>value:</strong> The string value of the label (i.e. "Weight Over Time").
 *          </li>
 *          <li>
 *              <strong>fontSize:</strong> The font-size in pixels.
 *          </li>
 *          <li>
 *              <strong>position:</strong> The number of pixels from the edge to render the label.
 *          </li>
 *          <li>
 *              <strong>lookClickable:</strong> If true it styles the label so that it appears to be clickable. Defaults
 *              to false.
 *          </li>
 *          <li>
 *              <strong>listeners:</strong> An object with properties for each listener the user wants attached
 *              to the label. The value of each property is the function to be called when the event occurs. The
 *              available listeners are: click, dblclick, hover, mousedown, mouseup, mousemove, mouseout, mouseover,
 *              touchcancel, touchend, touchmove, and touchstart.
 *          </li>
 *      </ul>
 * @param {Object} [config.margins] (Optional) Margin sizes in pixels. It can be useful to set the margins if the tick
 *      marks on an axis are overlapping with your axis labels. Defaults to top: 75px, right: 75px, bottom: 50px, and
 *      left: 75px. The right side my have a margin of 150px if a legend is needed.
 *      The object may contain any of the following properties:
 *      <ul>
 *          <li><strong>top:</strong> Size of top margin in pixels.</li>
 *          <li><strong>bottom:</strong> Size of bottom margin in pixels.</li>
 *          <li><strong>left:</strong> Size of left margin in pixels.</li>
 *          <li><strong>right:</strong> Size of right margin in pixels.</li>
 *      </ul>
 * @param {String} [config.legendPos] (Optional) Used to specify where the legend will render. Currently only supports
 *      "none" to disable the rendering of the legend. There are future plans to support "left" and "right" as well.
 *      Defaults to "right".
 * @param {String} [config.bgColor] (Optional) The string representation of the background color. Defaults to white.
 * @param {String} [config.gridColor] (Optional) The string representation of the grid color. Defaults to white.
 * @param {String} [config.gridLineColor] (Optional) The string representation of the line colors used as part of the grid.
 *      Defaults to grey (#dddddd).
 * @param {Boolean} [config.clipRect] (Optional) Used to toggle the use of a clipRect, which prevents values that appear
 *      outside of the specified grid area from being visible. Use of clipRect can negatively affect performance, do not
 *      use if there is a large amount of elements on the grid. Defaults to false.
 * @param {Boolean} [config.throwErrors] (Optional) Used to toggle between the plot throwing errors or displaying errors.
 *      If true the plot will throw an error instead of displaying an error when necessary and possible. Defaults to
 *      false.
 *
 *
  @example
 In this example we will create a simple scatter plot.
 
 &lt;div id='plot'&gt;
 &lt;/div id='plot'&gt;
 &lt;script type="text/javascript"&gt;
var scatterData = [];

// Here we're creating some fake data to create a plot with.
for(var i = 0; i < 1000; i++){
    var point = {
        x: {value: parseInt((Math.random()*(150)))},
        y: Math.random() * 1500
    };
    scatterData.push(point);
}

// Create a new layer object.
var pointLayer = new LABKEY.vis.Layer({
	geom: new LABKEY.vis.Geom.Point()
});


// Create a new plot object.
var scatterPlot = new LABKEY.vis.Plot({
	renderTo: 'plot',
	width: 900,
	height: 700,
	data: scatterData,
	layers: [pointLayer],
	aes: {
		// Aesthetic mappings can be functions or strings.
		x: function(row){return row.x.value},
		y: 'y'
	}
});

scatterPlot.render();
 &lt;/script&gt;

 @example
 In this example we create a simple box plot.

 &lt;div id='plot'&gt;
 &lt;/div id='plot'&gt;
 &lt;script type="text/javascript"&gt;
    // First let's create some data.

var boxPlotData = [];

for(var i = 0; i < 6; i++){
    var group = "Group "+(i+1);
    for(var j = 0; j < 25; j++){
        boxPlotData.push({
            group: group,
            //Compute a random age between 25 and 55
            age: parseInt(25+(Math.random()*(55-25))),
            gender: parseInt((Math.random()*2)) === 0 ? 'male' : 'female'
        });
    }
    for(j = 0; j < 3; j++){
        boxPlotData.push({
            group: group,
            //Compute a random age between 75 and 95
            age: parseInt(75+(Math.random()*(95-75))),
            gender: parseInt((Math.random()*2)) === 0 ? 'male' : 'female'
        });
    }
    for(j = 0; j < 3; j++){
        boxPlotData.push({
            group: group,
            //Compute a random age between 1 and 16
            age: parseInt(1+(Math.random()*(16-1))),
            gender: parseInt((Math.random()*2)) === 0 ? 'male' : 'female'
        });
    }
}


// Now we create the Layer.
var boxLayer = new LABKEY.vis.Layer({
    geom: new LABKEY.vis.Geom.Boxplot({
    	// Customize the Boxplot Geom to fit our needs.
		position: 'jitter',
		outlierOpacity: '1',
		outlierFill: 'red',
		showOutliers: true,
		opacity: '.5',
		outlierColor: 'red'
    }),
    aes: {
        hoverText: function(x, stats){
            return x + ':\nMin: ' + stats.min + '\nMax: ' + stats.max + '\nQ1: ' +
                stats.Q1 + '\nQ2: ' + stats.Q2 + '\nQ3: ' + stats.Q3;
        },
        outlierHoverText: function(row){
            return "Group: " + row.group + ", Age: " + row.age;
        },
        outlierShape: function(row){return row.gender;}
    }
});


// Create a new Plot object.
var boxPlot = new LABKEY.vis.Plot({
    renderTo: 'plot',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Example Box Plot'},
        yLeft: {value: 'Age'},
        x: {value: 'Groups of People'}
    },
    data: boxPlotData,
    layers: [boxLayer],
    aes: {
        yLeft: 'age',
        x: 'group'
    },
    scales: {
        x: {
            scaleType: 'discrete'
        },
        yLeft: {
            scaleType: 'continuous',
            trans: 'linear'
        }
    },
    margins: {
        bottom: 75
    }
});

boxPlot.render();
 &lt;/script&gt;

 */
(function(){
    var initMargins = function(userMargins, legendPos, allAes, scales){
        var margins = {}, top = 75, right = 75, bottom = 50, left = 75; // Defaults.
        var foundLegendScale = false, foundYRight = false;

        for(var i = 0; i < allAes.length; i++){
            var aes = allAes[i];
            if(!foundLegendScale && (aes.shape || (aes.color && (!scales.color || (scales.color && scales.color.scaleType == 'discrete'))) || aes.outlierColor || aes.outlierShape || aes.pathColor) && legendPos != 'none'){
                foundLegendScale = true;
                right = right + 150;
            }

            if(!foundYRight && aes.yRight){
                foundYRight = true;
                right = right + 25;
            }
        }

        if(!userMargins){
            userMargins = {};
        }

        if(!userMargins.top || userMargins.top < 0){
            margins.top = top;
        } else {
            margins.top = userMargins.top;
        }
        if(!userMargins.right || userMargins.right < 0){
            margins.right = right;
        } else {
            margins.right = userMargins.right;
        }
        if(!userMargins.bottom || userMargins.bottom < 0){
            margins.bottom = bottom;
        } else {
            margins.bottom = userMargins.bottom;
        }
        if(!userMargins.left || userMargins.left < 0){
            margins.left = left;
        } else {
            margins.left = userMargins.left;
        }

        return margins;
    };

    var initGridDimensions = function(grid, margins) {
        grid.leftEdge = margins.left;
        grid.rightEdge = grid.width - margins.right + 10;
        grid.topEdge = margins.top;
        grid.bottomEdge = grid.height - margins.bottom;
        return grid;
    };

    var copyUserScales = function(origScales) {
        // This copies the user's scales, but not the max/min because we don't want to over-write that, so we store the original
        // scales separately (this.originalScales).
        var scales = {}, newScaleName, origScale, newScale;
        for (var scaleName in origScales) {
            if (origScales.hasOwnProperty(scaleName)) {
                if(scaleName == 'y'){
                    origScales.yLeft = origScales.y;
                    newScaleName = (scaleName == 'y') ? 'yLeft' : scaleName;
                } else {
                    newScaleName = scaleName;
                }
                newScale = {};
                origScale = origScales[scaleName];

                newScale.scaleType = origScale.scaleType ? origScale.scaleType : 'continuous';
                newScale.trans = origScale.trans ? origScale.trans : 'linear';
                newScale.tickFormat = origScale.tickFormat ? origScale.tickFormat : null;
                newScale.tickHoverText = origScale.tickHoverText ? origScale.tickHoverText : null;
                newScale.tickCls = origScale.tickCls ? origScale.tickCls : null;
                newScale.domain = origScale.domain ? origScale.domain : null;
                newScale.range = origScale.range ? origScale.range : null;

                if (!origScale.domain &&((origScale.hasOwnProperty('min') && LABKEY.vis.isValid(origScale.min)) ||
                        (origScale.hasOwnProperty('max') && LABKEY.vis.isValid(origScale.max)))) {
                    console.log('scale.min and scale.max are deprecated. Please use scale.domain.');
                    newScale.domain = [origScale.min, origScale.max];
                    origScale.domain = [origScale.min, origScale.max];
                }

                if (newScale.scaleType == 'ordinal' || newScale.scaleType == 'categorical') {
                    newScale.scaleType = 'discrete';
                }

                scales[newScaleName] = newScale;
            }
        }
        return scales;
    };

    var setupDefaultScales = function(scales, aes) {
        for (var aesthetic in aes) {
            if (aes.hasOwnProperty(aesthetic)) {
                if(!scales[aesthetic]){
                    // Not all aesthetics get a scale (like hoverText), so we have to be pretty specific.
                    if(aesthetic === 'x' || aesthetic === 'yLeft' || aesthetic === 'yRight' || aesthetic === 'size'){
                        scales[aesthetic] = {scaleType: 'continuous', trans: 'linear'};
                    } else if (aesthetic == 'color' || aesthetic == 'outlierColor' || aesthetic == 'pathColor') {
                        scales['color'] = {scaleType: 'discrete'};
                    } else if(aesthetic == 'shape' || aesthetic == 'outlierShape'){
                        scales['shape'] = {scaleType: 'discrete'};
                    }
                }
            }
        }
    };

    var getDiscreteAxisDomain = function(data, acc) {
        // If any axis is discerete we need to know the domain before rendering so we render the grid correctly.
        var domain = [], uniques = {}, i, value;

        for (i = 0; i < data.length; i++) {
            value = acc(data[i]);
            uniques[value] = true;
        }

        for (value in uniques) {
            if(uniques.hasOwnProperty(value)) {
                domain.push(value);
            }
        }

        return domain;
    };

    var getContinuousDomain = function(aesName, userScale, data, acc, errorAes) {
        var userMin, userMax, min, max, minAcc, maxAcc;

        if (userScale && userScale.domain) {
            userMin = userScale.domain[0];
            userMax = userScale.domain[1];
        }

        if (LABKEY.vis.isValid(userMin)) {
            min = userMin;
        } else {
            if ((aesName == 'yLeft' || aesName == 'yRight') && errorAes) {
                minAcc = function(d) {
                    if (LABKEY.vis.isValid(acc(d))) {
                        return acc(d) - errorAes.getValue(d);
                    }
                    else {
                        return null;
                    }
                };
            } else {
                minAcc = acc;
            }

            min = d3.min(data, minAcc);
        }

        if (LABKEY.vis.isValid(userMax)) {
            max = userMax;
        } else {
            if ((aesName == 'yLeft' || aesName == 'yRight') && errorAes) {
                maxAcc = function(d) {
                    return acc(d) + errorAes.getValue(d);
                }
            } else {
                maxAcc = acc;
            }
            max = d3.max(data, maxAcc);
        }

        if (min == max ) {
            max = max + 1;
            min = min - 1;
        }

        /*
            TODO: Hack to keep time charts from getting in a bad state. They currently rely on us rendering an empty
            grid when there is an invalid x-axis. We should fix the time chart validation methods and remove this.
         */
        if (isNaN(min) && isNaN(max)) {
            return [0,0];
        }

        return [min, max];
    };

    var getDomain = function(aesName, userScale, scale, data, acc, errorAes) {
        var tempDomain, curDomain = scale.domain, domain = [];

        if (scale.scaleType == 'discrete') {
            tempDomain = getDiscreteAxisDomain(data, acc);
            if (!curDomain) {
                return tempDomain;
            }

            for (var i = 0; i < tempDomain.length; i++) {
                if (curDomain.indexOf(tempDomain[i]) == -1) {
                    curDomain.push(tempDomain[i]);
                }
            }
            return curDomain;
        } else {
            tempDomain = getContinuousDomain(aesName, userScale, data, acc, errorAes);
            if (!curDomain) {
                return tempDomain;
            }

            if (!LABKEY.vis.isValid(curDomain[0]) || tempDomain[0] < curDomain[0]) {
                domain[0] = tempDomain[0];
            } else {
                domain[0] = curDomain[0];
            }

            if (!LABKEY.vis.isValid(curDomain[1]) || tempDomain[1] > curDomain[1]) {
                domain[1] = tempDomain[1];
            } else {
                domain[1] = curDomain[1];
            }
        }

        return domain;
    };

    var requiresDomain = function(name, colorScale) {
        if (name == 'yLeft' || name == 'yRight' || name == 'x' || name == 'size') {
            return true;
        }
        // We only need the domain of the a color scale if it's a continuous one.
        return (name == 'color' || name == 'outlierColor') && colorScale && colorScale.scaleType == 'continuous';
    };

    var calculateDomains = function(userScales, scales, allAes, allData) {
        var i, aesName, scale, userScale;
        for (i = 0; i < allAes.length; i++) {
            for (aesName in allAes[i]) {
                if (allAes[i].hasOwnProperty(aesName) && requiresDomain(aesName, scales.color)) {
                    if (aesName == 'outlierColor') {
                        scale = scales.color;
                        userScale = userScales.color;
                    } else {
                        scale = scales[aesName];
                        userScale = userScales[aesName];
                    }

                    scale.domain = getDomain(aesName, userScale, scale, allData[i], allAes[i][aesName].getValue, allAes[i].error);
                }
            }
        }
    };

    var getDefaultRange = function(scaleName, scale) {
        if (scaleName == 'color' && scale.scaleType == 'continuous') {
            return ['#222222', '#EEEEEE'];
        }

        if (scaleName == 'color' && scale.scaleType == 'discrete') {
            return LABKEY.vis.Scale.ColorDiscrete();
        }

        if (scaleName == 'shape') {
            return LABKEY.vis.Scale.Shape();
        }

        if (scaleName == 'size') {
            return [1, 5];
        }

        return null;
    };

    var calculateAxisScaleRanges = function(scales, grid, margins) {
        var yRange = [grid.bottomEdge, grid.topEdge];

        if (scales.yRight) {
            scales.yRight.range = yRange;
        }

        if (scales.yLeft) {
            scales.yLeft.range = yRange;
        }

        if (scales.x.scaleType == 'continuous') {
            scales.x.range = [margins.left, grid.width - margins.right];
        } else {
            // We don't need extra padding in the discrete case because we use rangeBands which take care of that.
            scales.x.range = [grid.leftEdge, grid.rightEdge];
        }
    };

    var getLogScale = function (domain, range) {
        var scale, scaleWrapper, increment = false;

        if (domain[0] == 0) {
            increment = true;
            domain[0] = domain[0] + 1;
            domain[1] = domain[1] + 1;
        }

        scale = d3.scale.log().domain(domain).range(range);

        // We need to wrap the scale so we can increment by 1 if the minimum value is 0.
        scaleWrapper = function(val) {
            if(val != null && val >= 0) {
                if (increment == true) {
                    return scale(val + 1);
                }

                return scale(val);
            }

            return null;
        };
        scaleWrapper.domain = scale.domain;
        scaleWrapper.range = scale.range;
        scaleWrapper.invert = scale.invert;
        scaleWrapper.base = scale.base;
        scaleWrapper.clamp = scale.clamp;
        scaleWrapper.ticks = function(){
            var allTicks = scale.ticks();
            var ticksToShow = [];

            if (allTicks.length < 2) {
                //make sure that at least 2 tick marks are shown for reference
                return [Math.ceil(scale.domain()[0]), Math.floor(scale.domain()[1])];
            }
            else if(allTicks.length < 10){
                return allTicks;
            }
            else {
                for(var i = 0; i < allTicks.length; i++){
                    if(i % 9 == 0){
                        ticksToShow.push(allTicks[i]);
                    }
                }
                return ticksToShow;
            }
        };

        return scaleWrapper;
    };

    var instantiateScales = function(userScales, scales, grid, margins) {
        var scaleName, scale, userScale;

        calculateAxisScaleRanges(scales, grid, margins);

        for (scaleName in scales) {
            if (scales.hasOwnProperty(scaleName)) {
                scale = scales[scaleName];
                userScale = userScales[scaleName];

                if (scale.scaleType == 'discrete') {
                    if (scaleName == 'x' || scaleName == 'yLeft' || scaleName == 'yRight'){
                        // Setup scale with domain (user provided or calculated) and compute range based off grid dimensions.
                        scale.scale = d3.scale.ordinal().domain(scale.domain).rangeBands(scale.range, 1);
                    } else {
                        // Setup scales with user provided range or default range.
                        if (userScale && userScale.scale) {
                            scale.scale = userScale.scale;
                        }
                        else {
                            scale.scale = d3.scale.ordinal();
                        }

                        if (scale.domain) {
                            scale.scale.domain(scale.domain);
                        }

                        if (!scale.range) {
                            scale.range = getDefaultRange(scaleName, scale);
                        }

                        if (scale.scale.range) {
                            scale.scale.range(scale.range);
                        }
                    }
                } else {
                    if ((scaleName == 'color' || scaleName == 'size') && !scale.range) {
                        scale.range = getDefaultRange(scaleName, scale);
                    }

                    if (scale.range && scale.domain && LABKEY.vis.isValid(scale.domain[0]) && LABKEY.vis.isValid(scale.domain[1])) {
                        if (scale.trans == 'linear') {
                            scale.scale = d3.scale.linear().domain(scale.domain).range(scale.range);
                        } else {
                            scale.scale = getLogScale(scale.domain, scale.range);
                        }
                    }
                }
            }
        }
    };

    var initializeScales = function(userScales, scales, allAes, allData, grid, margins, errorFn) {
        for (var i = 0; i < allAes.length; i++) {
            setupDefaultScales(scales, allAes[i]);
        }

        for (var scaleName in scales) {
            if(scales.hasOwnProperty(scaleName)) {
                if (scales[scaleName].scale) {
                    delete scales[scaleName].scale;
                }

                if (scales[scaleName].domain && (userScales[scaleName] && !userScales[scaleName].domain)) {
                    delete scales[scaleName].domain;
                }

                if (scales[scaleName].range && (userScales[scaleName] && !userScales[scaleName].range)) {
                    delete scales[scaleName].range;
                }
            }
        }

        calculateDomains(userScales, scales, allAes, allData);
        instantiateScales(userScales, scales, grid, margins);

        if (!scales.x.scale) {
            errorFn('Unable to create an x scale, rendering aborted.');
            return false;
        }

        if((!scales.yLeft || !scales.yLeft.scale) && (!scales.yRight ||!scales.yRight.scale)){
            errorFn('Unable to create a y scale, rendering aborted.');
            return false;
        }

        return true;
    };

    var compareDomains  = function(domain1, domain2){
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

    var generateLegendData = function(legendData, domain, colorFn, shapeFn){
        for(var i = 0; i < domain.length; i++) {
            legendData.push({
                text: domain[i],
                color: colorFn != null ? colorFn(domain[i]) : null,
                shape: shapeFn != null ? shapeFn(domain[i]) : null
            });
        }
    };

    LABKEY.vis.Plot = function(config){
        if(config.hasOwnProperty('rendererType') && config.rendererType == 'd3') {
            this.renderer = new LABKEY.vis.internal.D3Renderer(this);
        } else {
            this.renderer = new LABKEY.vis.internal.RaphaelRenderer(this);
        }

        var error = function(msg){
            if (this.throwErrors){
                throw new Error(msg);
            } else {
                console.error(msg);
                if(console.trace){
                    console.trace();
                }

                this.renderer.renderError(msg);
            }
        };

        this.renderTo = config.renderTo ? config.renderTo : null; // The id of the DOM element to render the plot to, required.
        this.grid = {
            width: config.hasOwnProperty('width') ? config.width : null, // height of the grid where shapes/lines/etc gets plotted.
            height: config.hasOwnProperty('height') ? config.height: null // widht of the grid.
        };
        this.originalScales = config.scales ? config.scales : {}; // The scales specified by the user.
        this.scales = copyUserScales(this.originalScales); // The scales used internally.
        this.originalAes = config.aes ? config.aes : null; // The original aesthetic specified by the user.
        this.aes = LABKEY.vis.convertAes(this.originalAes); // The aesthetic object used internally.
        this.labels = config.labels ? config.labels : {};
        this.data = config.data ? config.data : null; // An array of rows, required. Each row could have several pieces of data. (e.g. {subjectId: '249534596', hemoglobin: '350', CD4:'1400', day:'120'})
        this.layers = config.layers ? config.layers : []; // An array of layers, required. (e.g. a layer for a CD4 line chart over time, and a layer for a Hemoglobin line chart over time).
        this.clipRect = config.clipRect ? config.clipRect : false;
        this.legendPos = config.legendPos;
        this.throwErrors = config.throwErrors || false; // Allows the configuration to specify whether chart errors should be thrown or logged (default).
        this.brushing = ('brushing' in config && config.brushing != null && config.brushing != undefined) ? config.brushing : null;
        this.legendData = config.legendData ? config.legendData : null; // An array of rows for the legend text/color/etc. Optional.

        this.bgColor = config.bgColor ? config.bgColor : null;
        this.gridColor = config.gridColor ? config.gridColor : null;
        this.gridLineColor = config.gridLineColor ? config.gridLineColor : null;
        this.tickColor = config.tickColor ? config.tickColor : null;
        this.borderColor = config.borderColor ? config.borderColor : null;
        this.tickTextColor = config.tickTextColor ? config.tickTextColor : null;
        this.tickLength = config.hasOwnProperty('tickLength') ? config.tickLength : null;
        this.tickWidth = config.hasOwnProperty('tickWidth') ? config.tickWidth : null;
        this.tickOverlapRotation = config.tickOverlapRotation ? config.tickOverlapRotation : null;
        this.gridLineWidth = config.hasOwnProperty('gridLineWidth') ? config.gridLineWidth : null;
        this.borderWidth = config.hasOwnProperty('borderWidth') ? config.borderWidth : null;

        // Stash the user's margins so when we re-configure margins during re-renders or setAes we don't forget the user's settings.
        var allAes = [], margins = {}, userMargins = config.margins ? config.margins : {};

        allAes.push(this.aes);

        for(var i = 0; i < this.layers.length; i++){
            if(this.layers[i].aes){
                allAes.push(this.layers[i].aes);
            }
        }

        if(this.labels.y){
            this.labels.yLeft = this.labels.y;
            this.labels.y = null;
        }

        if(this.grid.width == null){
            error.call(this, "Unable to create plot, width not specified");
            return;
        }

        if(this.grid.height == null){
            error.call(this, "Unable to create plot, height not specified");
            return;
        }

        if(this.renderTo == null){
            error.call(this, "Unable to create plot, renderTo not specified");
            return;
        }

        for(var aesthetic in this.aes){
            if (this.aes.hasOwnProperty(aesthetic)) {
                LABKEY.vis.createGetter(this.aes[aesthetic]);
            }
        }

        this.getLegendData = function(){
            var legendData = [];

            if ((this.scales.color && this.scales.color.scaleType === 'discrete') && this.scales.shape) {
                if(compareDomains(this.scales.color.scale.domain(), this.scales.shape.scale.domain())){
                    // The color and shape domains are the same. Merge them in the legend.
                    generateLegendData(legendData, this.scales.color.scale.domain(), this.scales.color.scale, this.scales.shape.scale);
                } else {
                    // The color and shape domains are different.
                    generateLegendData(legendData, this.scales.color.scale.domain(), this.scales.color.scale, null);
                    generateLegendData(legendData, this.scales.shape.scale.domain(), null, this.scales.shape.scale);
                }
            } else if(this.scales.color && this.scales.color.scaleType === 'discrete') {
                generateLegendData(legendData, this.scales.color.scale.domain(), this.scales.color.scale, null);
            } else if(this.scales.shape) {
                generateLegendData(legendData, this.scales.shape.scale.domain(), null, this.scales.shape.scale);
            }

            return legendData;
        };

        /**
         * Renders the plot.
         */
        this.render = function(){
            margins = initMargins(userMargins, this.legendPos, allAes, this.scales);
            this.grid = initGridDimensions(this.grid, margins);
            this.renderer.initCanvas(); // Get the canvas prepped for render time.
            var allData = [this.data];
            var plot = this;
            var errorFn = function(msg) {
                error.call(plot, msg);
            };
            allAes = [this.aes];
            for (var i = 0; i < this.layers.length; i++) {
                // If the layer doesn't have data or aes, then it doesn't need to be considered for any scale calculations.
                if (!this.layers[i].data && !this.layers[i].aes) {continue;}
                allData.push(this.layers[i].data ? this.layers[i].data : this.data);
                allAes.push(this.layers[i].aes ? this.layers[i].aes : this.aes);
            }

            if(!initializeScales(this.originalScales, this.scales, allAes, allData, this.grid, margins, errorFn)){  // Sets up the scales.
                return false; // if we have a critical error when trying to initialize the scales we don't continue with rendering.
            }

            if(!this.layers || this.layers.length < 1){
                error.call(this,'No layers added to the plot, nothing to render.');
                return false;
            }

            this.renderer.renderGrid(); // renders the grid (axes, grid lines).
            this.renderer.renderLabels();

            for(var i = 0; i < this.layers.length; i++){
                this.layers[i].plot = this; // Add reference to the layer so it can trigger a re-render during setAes.
                this.layers[i].render(this.renderer, this.grid, this.scales, this.data, this.aes, i);
            }

            if(!this.legendPos || (this.legendPos && !(this.legendPos == "none"))){
                this.renderer.renderLegend();
            }

            return true;
        };

        var setLabel = function(name, value, lookClickable){
            if(!this.labels[name]){
                this.labels[name] = {};
            }

            this.labels[name].value = value;
            this.labels[name].lookClickable = lookClickable;
            this.renderer.renderLabel(name);
        };

        /**
         * Sets the value of the main label and optionally makes it look clickable.
         * @param {String} value The string value to set the label to.
         * @param {Boolean} lookClickable If true it styles the label to look clickable.
         */
        this.setMainLabel = function(value, lookClickable){
            setLabel.call(this, 'main', value, lookClickable);
        };

        /**
         * Sets the value of the x-axis label and optionally makes it look clickable.
         * @param {String} value The string value to set the label to.
         * @param {Boolean} lookClickable If true it styles the label to look clickable.
         */
        this.setXLabel = function(value, lookClickable){
            setLabel.call(this, 'x', value, lookClickable);
        };

        /**
         * Sets the value of the right y-axis label and optionally makes it look clickable.
         * @param {String} value The string value to set the label to.
         * @param {Boolean} lookClickable If true it styles the label to look clickable.
         */
        this.setYRightLabel = function(value, lookClickable){
            setLabel.call(this, 'yRight', value, lookClickable);
        };

        /**
         * Sets the value of the left y-axis label and optionally makes it look clickable.
         * @param {String} value The string value to set the label to.
         * @param {Boolean} lookClickable If true it styles the label to look clickable.
         */
        this.setYLeftLabel = this.setYLabel = function(value, lookClickable){
            setLabel.call(this, 'yLeft', value, lookClickable);
        };

        /**
         * Adds a listener to a label.
         * @param {String} label string value of label to add a listener to. Valid values are y, yLeft, yRight, x, and main.
         * @param {String} listener the name of the listener to listen on.
         * @param {Function} fn The callback to b called when the event is fired.
         */
        this.addLabelListener = function(label, listener, fn){
            if(label == 'y') {
                label = 'yLeft';
            }
            return this.renderer.addLabelListener(label, listener, fn);
        };

        /**
         * Sets the height of the plot and re-renders if requested.
         * @param {Number} h The height in pixels.
         * @param {Boolean} render Toggles if plot will be re-rendered or not.
         */
        this.setHeight = function(h, render){
            if(render == null || render == undefined){
                render = true;
            }

            this.grid.height = h;

            if(render === true){
                this.render();
            }
        };

        /**
         * Sets the width of the plot and re-renders if requested.
         * @param {Number} w The width in pixels.
         * @param {Boolean} render Toggles if plot will be re-rendered or not.
         */
        this.setWidth = function(w, render){
            if(render == null || render == undefined){
                render = true;
            }

            this.grid.width = w;

            if(render === true){
                this.render();
            }
        };

        /**
         * Changes the size of the plot and renders if requested.
         * @param {Number} w width in pixels.
         * @param {Number} h height in pixels.
         * @param {Boolean} render Toggles if the chart will be re-rendered or not. Defaults to false.
         */
        this.setSize = function(w, h, render){
            this.setWidth(w, false);
            this.setHeight(h, render);
        };

        /**
         * Adds a new layer to the plot.
         * @param {@link LABKEY.vis.Layer} layer
         */
        this.addLayer = function(layer){
            layer.parent = this; // Set the parent of each layer to the plot so we can grab things like data from it later.
            this.layers.push(layer);
        };

        /**
         * Clears the grid.
         */
        this.clearGrid = function(){
            this.renderer.clearGrid();
        };

        /**
         * Sets new margins for the plot and re-renders with the margins.
         * @param {Object} newMargins An object with the following properties:
         *      <ul>
         *          <li><strong>top:</strong> Size of top margin in pixels.</li>
         *          <li><strong>bottom:</strong> Size of bottom margin in pixels.</li>
         *          <li><strong>left:</strong> Size of left margin in pixels.</li>
         *          <li><strong>right:</strong> Size of right margin in pixels.</li>
         *      </ul>
         */
        this.setMargins = function(newMargins, render){
            userMargins = newMargins;
            margins = initMargins(userMargins, this.legendPos, allAes, this.scales);

            if(render !== undefined && render !== null && render === true) {
                this.render();
            }
        };

        this.setAes = function(newAes){
            // Note: this is only valid for plots using the D3Renderer.
            // Used to add or remove aesthetics to a plot. Also availalbe on LABKEY.vis.Layer objects to set aesthetics on
            // specific layers only.
            // To delete an aesthetic set it to null i.e. plot.setAes({color: null});
            LABKEY.vis.mergeAes(this.aes, newAes);
            this.render();
        };

        this.clearBrush = function() {
            if(this.renderer.clearBrush) {
                this.renderer.clearBrush();
            }
        };

        this.getBrushExtent = function() {
            // Returns an array of arrays. First array is xMin, yMin, second array is xMax, yMax
            // If the seleciton is 1D, then the min/max of the non-selected dimension will be null/null.
            return this.renderer.getBrushExtent();
        };

        this.setBrushExtent = function(extent) {
            // Takes a 2D array. First array is xMin, yMin, second array is xMax, yMax. If the seleciton is 1D, then the
            // min/max of the non-selected dimension will be null/null.
            this.renderer.setBrushExtent(extent);
        }

        return this;
    };
})();

/**
 * @name LABKEY.vis.BarPlot
 * @class BarPlot wrapper to allow a user to easily create a simple bar plot without having to preprocess the data.
 * @param {Object} config An object that contains the following properties (in addition to those properties defined
 *      in {@link LABKEY.vis.Plot}).
 * @param {String} [config.renderTo] The id of the div/span to insert the svg element into.
 * @param {Array} [config.data] The array of individual data points to be grouped for the bar plot. The LABKEY.vis.BarPlot
 *      wrapper will aggregate the data in this array based on the xAes function provided to get the individual totals
 *      for each bar in the plot.
 * @param {Function} [config.xAes] The function to determine which groups will be created for the x-axis of the plot.
 * @param {Object} [config.options] (Optional) Display options as defined in {@link LABKEY.vis.Geom.BarPlot}.
 *
 @example
 &lt;div id='bar'&gt;&lt;/div&gt;
 &lt;script type="text/javascript"&gt;
 // Fake data which will be aggregated by the LABKEY.vis.BarPlot wrapper.
 var barPlotData = [
    {gender: 'Male', age: '21'}, {gender: 'Male', age: '43'},
    {gender: 'Male', age: '24'}, {gender: 'Male', age: '54'},
    {gender: 'Female', age: '24'}, {gender: 'Female', age: '33'},
    {gender: 'Female', age: '43'}, {gender: 'Female', age: '43'},
 ];

 // Create a new bar plot object.
 var barChart = new LABKEY.vis.BarPlot({
    renderTo: 'bar',
    rendererType: 'd3',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Example Bar Plot With Cumulative Totals'},
        yLeft: {value: 'Count'},
        x: {value: 'Value'}
    },
    options: {
        color: 'black',
        fill: '#c0c0c0',
        lineWidth: 1.5,
        colorTotal: 'black',
        fillTotal: 'steelblue',
        opacityTotal: .8,
        showCumulativeTotals: true
    },
    xAes: function(row){return row['age']},
    data: barPlotData
 });

 barChart.render();
 &lt;/script&gt;
 */
(function(){

    LABKEY.vis.BarPlot = function(config){

        if(config.renderTo == null){
            throw new Error("Unable to create bar plot, renderTo not specified");
        }

        if(config.data == null){
            throw new Error("Unable to create bar plot, data array not specified");
        }

        if(config.xAes == null){
            throw new Error("Unable to create bar plot, xAes function not specified");
        }

        var countData = LABKEY.vis.groupCountData(config.data, config.xAes);
        var showCumulativeTotals = config.options && config.options.showCumulativeTotals;

        config.layers = [new LABKEY.vis.Layer({
            geom: new LABKEY.vis.Geom.BarPlot(config.options),
            data: countData,
            aes: { x: 'name', y: 'count' }
        })];

        if (!config.scales)
        {
            config.scales = {};
            config.scales.x = { scaleType: 'discrete' };
            config.scales.y = { domain: [0, showCumulativeTotals ? countData[countData.length-1].total : null] };
        }

        if (showCumulativeTotals && !config.margins)
        {
            config.margins = {right: 125};
        }

        return new LABKEY.vis.Plot(config);
    };
})();

/**
 * @name LABKEY.vis.PieChart
 * @class PieChart which allows a user to programmatically create an interactive pie chart visualization (note: D3 rendering only).
 * @description The pie chart visualization is built off of the <a href="http://d3pie.org">d3pie JS library</a>. The config
 *      properties listed below are the only required properties to create a base pie chart. For additional display options
 *      and interaction options, you can provide any of the properties defined in the <a href="http://d3pie.org/#docs">d3pie docs</a>
 *      to the config object.
 * @param {Object} config An object that contains the following properties
 * @param {String} [config.renderTo] The id of the div/span to insert the svg element into.
 * @param {Array} [config.data] The array of chart segment data. Each object is of the form: { label: "label", value: 123 }.
 * @param {Number} [config.width] The chart canvas width in pixels.
 * @param {Number} [config.height] The chart canvas height in pixels.
 *
 @example
 Example of a simple pie chart (only required config properties).

 &lt;div id='pie'&gt;&lt;/div&gt;
 &lt;script type="text/javascript"&gt;

 &lt;/script&gt;
 var pieChartData = [
     {label: "test1", value: 1},
     {label: "test2", value: 2},
     {label: "test3", value: 3},
     {label: "test4", value: 4}
 ];

 var pieChart = new LABKEY.vis.PieChart({
    renderTo: "pie",
    data: pieChartData,
    width: 300,
    height: 250
 });

 Example of a customized pie chart using some d3pie lib properties.

 &lt;div id='pie2'&gt;&lt;/div&gt;
 &lt;script type="text/javascript"&gt;
 var pieChartData = [
     {label: "test1", value: 1},
     {label: "test2", value: 2},
     {label: "test3", value: 3},
     {label: "test4", value: 4}
 ];

 var pieChart2 = new LABKEY.vis.PieChart({
    renderTo: "pie2",
    data: pieChartData,
    width: 300,
    height: 250,
    // d3pie lib config properties
    header: {
        title: {
            text: 'Pie Chart Example'
        }
    },
    labels: {
        outer: {
            format: 'label-value2',
            pieDistance: 15
        },
        inner: {
            hideWhenLessThanPercentage: 10
        },
        lines: {
            style: 'straight',
            color: 'black'
        }
    },
    effects: {
        load: {
            speed: 2000
        },
        pullOutSegmentOnClick: {
            effect: 'linear',
            speed: '1000'
        },
        highlightLuminosity: -0.75
    },
    misc: {
        colors: {
            segments: LABKEY.vis.Scale.DarkColorDiscrete(),
            segmentStroke: '#a1a1a1'
        },
        gradient: {
            enabled: true,
            percentage: 60
        }
    },
    callbacks: {
        onload: function() {
            pieChart2.openSegment(3);
        }
    }
 });
 &lt;/script&gt;
 */
(function(){

    LABKEY.vis.PieChart = function(config){

        if(config.renderTo == null){
            throw new Error("Unable to create pie chart, renderTo not specified");
        }

        if(config.data == null){
            throw new Error("Unable to create pie chart, data not specified");
        }
        else if (Array.isArray(config.data)) {
            config.data = {content : config.data};
        }

        if(config.width == null && (config.size == null || config.size.canvasWidth == null)){
            throw new Error("Unable to create pie chart, width not specified");
        }
        else if(config.height == null && (config.size == null || config.size.canvasHeight == null)){
            throw new Error("Unable to create pie chart, height not specified");
        }

        if (config.size == null) {
            config.size = {}
        }
        config.size.canvasWidth = config.width || config.size.canvasWidth;
        config.size.canvasHeight = config.height || config.size.canvasHeight;

        return new d3pie(config.renderTo, config);
    };
})();

/**
 * @name LABKEY.vis.LeveyJenningsPlot
 * @class LeveyJenningsPlot Wrapper to create a plot which shows data points compared to expected ranges (+/- 3 standard deviations from a mean).
 * @description This helper will take the input data and generate a sequencal x-axis so that all data points are the same distance apart.
 * @param {Object} config An object that contains the following properties
 * @param {String} [config.renderTo] The id of the div/span to insert the svg element into.
 * @param {Number} [config.width] The chart canvas width in pixels.
 * @param {Number} [config.height] The chart canvas height in pixels.
 * @param {Array} [config.data] The array of chart segment data. Each object is of the form: { label: "label", value: 123 }.
 * @param {Object} [config.properties] An object that contains the properties specific to the Levey-Jennings plot
 * @param {String} [config.properties.value] The data property name for the value to be plotted on the y-axis.
 * @param {String} [config.properties.mean] The data property name for the mean of the expected range.
 * @param {String} [config.properties.stdDev] The data property name for the standard deviation of the expected range.
 * @param {String} [config.properties.xTickLabel] The data property name for the x-axis tick label.
 * @param {Number} [config.properties.topMargin] (Optional) The top margin for the plot.
 * @param {Number} [config.properties.xTickTagIndex] (Optional) The index/value of the x-axis label to be tagged (i.e. class="xticktag").
 * @param {Boolean} [config.properites.showTrendLine] (Optional) Whether or not to show a line connecting the data points. Default false.
 * @param {Boolean} [config.properties.disableRangeDisplay] (Optional) Whether or not to show the mean/stdev ranges in the plot. Defaults to false.
 * @param {String} [config.properties.xTick] (Optional) The data property to use for unique x-axis tick marks. Defaults to sequence from 1:data length.
 * @param {String} [config.properties.yAxisScale] (Optional) Whether the y-axis should be plotted with linear or log scale. Default linear.
 * @param {Array} [config.properties.yAxisDomain] (Optional) Y-axis min/max values. Example: [0,20].
 * @param {String} [config.properties.color] (Optional) The data property name for the color to be used for the data point.
 * @param {Array} [config.properties.colorRange] (Optional) The array of color values to use for the data points.
 * @param {Function} [config.properties.hoverTextFn] (Optional) The hover text to display for each data point. The parameter
 *                  to that function will be a row of data with access to all values for that row.
 * @param {Function} [config.properties.pointClickFn] (Optional) The function to call on data point click. The parameters to
 *                  that function will be the click event and the row of data for the selected point.
 */
(function(){

    LABKEY.vis.LeveyJenningsPlot = function(config){

        if(config.renderTo == null){
            throw new Error("Unable to create Levey-Jennings plot, renderTo not specified");
        }

        if(config.data == null){
            throw new Error("Unable to create Levey-Jennings plot, data array not specified");
        }

        if (config.properties == null || config.properties.value == null || config.properties.mean == null
                || config.properties.stdDev == null || config.properties.xTickLabel == null)
        {
            throw new Error("Unable to create Levey-Jennings plot, properties object not specified. "
                    + "Required: value, mean, stdDev, xTickLabel. Optional: topMargin, color, colorRange, hoverTextFn, "
                    + "pointClickFn, showTrendLine, disableRangeDisplay, xTick, yAxisScale, yAxisDomain, xTickTagIndex.");
        }

        // create a sequencial index to use for the x-axis value and keep a map from that index to the tick label
        // also, pull out the meanStdDev data for the unique x-axis values and calculate average values for the trend line data
        var tickLabelMap = {};
        var distinctColorValues = [];
        var index = -1, xTickIndex = {};
        var meanStdDevData = [], groupedTrendlineData = [];
        for (var i = 0; i < config.data.length; i++)
        {
            var row = config.data[i];

            // track the distinct values in the color variable so that we know if we need the legend or not
            if (config.properties.color && distinctColorValues.indexOf(row[config.properties.color]) == -1) {
                distinctColorValues.push(row[config.properties.color]);
            }

            // if we are grouping x values based on the xTick property, only increment index if we have a new xTick value
            if (config.properties.xTick)
            {
                var xTickValue = row[config.properties.xTick];
                if (xTickIndex[xTickValue] == undefined) {
                    xTickIndex[row[config.properties.xTick]] = ++index;
                    groupedTrendlineData.push({seqValue: index, sum: 0, count: 0});
                }

                // calculate average values for the trend line data (used when grouping x by unique value)
                if (row[config.properties.value])
                {
                    groupedTrendlineData[index].sum += row[config.properties.value];
                    groupedTrendlineData[index].count++;
                    groupedTrendlineData[index][config.properties.value] = groupedTrendlineData[index].sum / groupedTrendlineData[index].count;
                }
            }
            else {
                index++;
            }

            tickLabelMap[index] = row[config.properties.xTickLabel];
            row.seqValue = index;

            if (!meanStdDevData[index]) {
                meanStdDevData[index] = row;
            }
        }

        // min x-axis tick length is 10 by default
        var maxSeqValue = config.data.length > 0 ? config.data[config.data.length - 1].seqValue + 1 : 0;
        for (var i = maxSeqValue; i < 10; i++)
        {
            var temp = {seqValue: i};
            temp[config.properties.xTickLabel] = "";
            if (config.properties.color && config.data[0]) {
                temp[config.properties.color] = config.data[0][config.properties.color];
            }
            config.data.push(temp);
        }

        // we only need the color aes if there is > 1 distinct value in the color variable
        if (distinctColorValues.length < 2) {
            config.properties.color = undefined;
        }

        config.tickOverlapRotation = 35;
        config.scales = {
            color: {
                scaleType: 'discrete',
                range: config.properties.colorRange
            },
            x: {
                scaleType: 'discrete',
                tickFormat: function(index) {
                    // only show a max of 35 labels on the x-axis to avoid overlap
                    if (index % Math.ceil(config.data[config.data.length-1].seqValue / 35) == 0) {
                        return tickLabelMap[index];
                    }
                    else {
                        return "";
                    }
                },
                tickCls: function(index) {
                    var baseTag = 'ticklabel';
                    var tagIndex = config.properties.xTickTagIndex;
                    if (tagIndex != undefined && tagIndex == index) {
                        return baseTag+' xticktag';
                    }
                    return baseTag;
                }
            },
            yLeft: {
                scaleType: 'continuous',
                domain: config.properties.yAxisDomain,
                trans: config.properties.yAxisScale || 'linear',
                tickFormat: function(val) {
                    return LABKEY.vis.isValid(val) && (val > 100000 || val < -100000) ? val.toExponential() : val;
                }
            }
        };
        config.margins = {
            top: config.labels && config.labels.main ? 30 : config.properties.topMargin || 10,
            right: config.properties.color || config.legendData ? 150 : 40,
            bottom: config.labels && config.labels.x ? 75 : 55,
            left: config.labels && config.labels.y ? 75 : 55
        };
        config.aes = {
            yLeft: config.properties.value,
            x: 'seqValue'
        };

        // determine the width the error bars
        var barWidth = Math.max(config.width / config.data[config.data.length-1].seqValue / 5, 3);

        // +/- 3 standard deviation displayed using the ErrorBar geom with different colors
        var stdDev3Layer = new LABKEY.vis.Layer({
            geom: new LABKEY.vis.Geom.ErrorBar({size: 1, color: 'red', dashed: true, altColor: 'darkgrey', width: barWidth}),
            data: meanStdDevData,
            aes: {
                error: function(row){return row[config.properties.stdDev] * 3;},
                yLeft: function(row){return row[config.properties.mean];}
            }
        });
        var stdDev2Layer = new LABKEY.vis.Layer({
            geom: new LABKEY.vis.Geom.ErrorBar({size: 1, color: 'blue', dashed: true, altColor: 'darkgrey', width: barWidth}),
            data: meanStdDevData,
            aes: {
                error: function(row){return row[config.properties.stdDev] * 2;},
                yLeft: function(row){return row[config.properties.mean];}
            }
        });
        var stdDev1Layer = new LABKEY.vis.Layer({
            geom: new LABKEY.vis.Geom.ErrorBar({size: 1, color: 'green', dashed: true, altColor: 'darkgrey', width: barWidth}),
            data: meanStdDevData,
            aes: {
                error: function(row){return row[config.properties.stdDev];},
                yLeft: function(row){return row[config.properties.mean];}
            }
        });
        var meanLayer = new LABKEY.vis.Layer({
            geom: new LABKEY.vis.Geom.ErrorBar({size: 1, color: 'darkgrey', width: barWidth}),
            data: meanStdDevData,
            aes: {
                error: function(row){return 0;},
                yLeft: function(row){return row[config.properties.mean];}
            }
        });

        config.layers = config.properties.disableRangeDisplay ? [] : [stdDev3Layer, stdDev2Layer, stdDev1Layer, meanLayer];

        if (config.properties.showTrendLine) {
            var pathLayerConfig = {
                geom: new LABKEY.vis.Geom.Path({
                    opacity: .6,
                    size: 2 }),
                data: config.properties.xTick ? groupedTrendlineData : undefined // default to using the data for the plot
            };
            config.layers.push(new LABKEY.vis.Layer(pathLayerConfig));
        }

        // points based on the data value, color and hover text can be added via params to config
        var pointLayerConfig = {
            geom: new LABKEY.vis.Geom.Point({
                position: 'jitter',
                size: 3
            }),
            aes: {}
        };
        if (config.properties.color) {
            pointLayerConfig.aes.color = function(row){return row[config.properties.color];};
        }
        if (config.properties.hoverTextFn) {
            pointLayerConfig.aes.hoverText = config.properties.hoverTextFn;
        }
        if (config.properties.pointClickFn) {
            pointLayerConfig.aes.pointClickFn = config.properties.pointClickFn;
        }

        // add some mouse over effects to highlight selected point
        pointLayerConfig.aes.mouseOverFn = function(event, pointData, layerSel) {
            d3.select(event.srcElement).transition().duration(800).attr("stroke-width", 5).ease("elastic");
        };
        pointLayerConfig.aes.mouseOutFn = function(event, pointData, layerSel) {
            d3.select(event.srcElement).transition().duration(800).attr("stroke-width", 1).ease("elastic");
        };

        config.layers.push(new LABKEY.vis.Layer(pointLayerConfig));

        return new LABKEY.vis.Plot(config);
    };
})();

/**
 * @name LABKEY.vis.SurvivalCurvePlot
 * @class SurvivalCurvePlot Wrapper to create a plot which shows survival curve step lines and censor points (based on output from R survival package).
 * @description This helper will take the input data and generate stepwise data points for use with the Path geom.
 * @param {Object} config An object that contains the following properties
 * @param {String} [config.renderTo] The id of the div/span to insert the svg element into.
 * @param {Number} [config.width] The chart canvas width in pixels.
 * @param {Number} [config.height] The chart canvas height in pixels.
 * @param {Array} [config.data] The array of step data for the survival curves.
 * @param {String} [config.groupBy] (optional) The data array object property used to group plot lines and points.
 * @param {Array} [config.censorData] The array of censor data to overlay on the survival step lines.
 * @param {Function} [config.censorHoverText] (optional) Function defining the hover text to display for the censor data points.
 */
(function(){

    LABKEY.vis.SurvivalCurvePlot = function(config){

        if (config.renderTo == null){
            throw new Error("Unable to create survival curve plot, renderTo not specified.");
        }

        if (config.data == null || config.censorData == null){
            throw new Error("Unable to create survival curve plot, data and/or censorData array not specified.");
        }

        if (config.aes == null || config.aes.x == null || config.aes.yLeft == null) {
            throw new Error("Unable to create survival curve plot, aes (x and yLeft) not specified.")
        }

        // Convert data array for step-wise line plot
        var stepData = [];
        var groupBy = config.groupBy;
        var aesX = config.aes.x;
        var aesY = config.aes.yLeft;

        for (var i=0; i<config.data.length; i++)
        {
            stepData.push(config.data[i]);

            if ( (i<config.data.length-1) && (config.data[i][groupBy] == config.data[i+1][groupBy])
                    && (config.data[i][aesX] != config.data[i+1][aesX])
                    && (config.data[i][aesY] != config.data[i+1][aesY]))
            {
                var point = {};
                point[groupBy] = config.data[i][groupBy];
                point[aesX] = config.data[i+1][aesX];
                point[aesY] = config.data[i][aesY];
                stepData.push(point);
            }
        }
        config.data = stepData;

        config.layers = [
            new LABKEY.vis.Layer({
                geom: new LABKEY.vis.Geom.Path({size:2, opacity:1}),
                aes: {
                    pathColor: config.groupBy,
                    group: config.groupBy
                }
            }),
            new LABKEY.vis.Layer({
                geom: new LABKEY.vis.Geom.Point({opacity:1}),
                data: config.censorData,
                aes: {
                    color: config.groupBy,
                    hoverText: config.censorHoverText,
                    shape: config.groupBy
                }

            })
        ];

        if (!config.scales) config.scales = {};
        config.scales.x = { scaleType: 'continuous', trans: 'linear' };
        config.scales.yLeft = { scaleType: 'continuous', trans: 'linear', domain: [0, 1] };

        config.aes.mouseOverFn = function(event, pointData, layerSel) {
            mouseOverFn(event, pointData, layerSel, config.groupBy);
        };

        config.aes.mouseOutFn = mouseOutFn;

        return new LABKEY.vis.Plot(config);
    };

    var mouseOverFn = function(event, pointData, layerSel, subjectColumn) {
        var points = d3.selectAll('.point path');
        var lines = d3.selectAll('path.line');

        var opacityAcc = function(d) {
            if (d[subjectColumn] && d[subjectColumn] == pointData[subjectColumn])
            {
                return 1;
            }
            return .3;
        };

        points.attr('fill-opacity', opacityAcc).attr('stroke-opacity', opacityAcc);
        lines.attr('fill-opacity', opacityAcc).attr('stroke-opacity', opacityAcc);
    };

    var mouseOutFn = function(event, pointData, layerSel) {
        d3.selectAll('.point path').attr('fill-opacity', 1).attr('stroke-opacity', 1);
        d3.selectAll('path.line').attr('fill-opacity', 1).attr('stroke-opacity', 1);
    };
})();
