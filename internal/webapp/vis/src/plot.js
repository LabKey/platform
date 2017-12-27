/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2012-2017 LabKey Corporation
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
 *          <li><strong>sortFn:</strong> If scaleType is "discrete", the sortFn can be used to order the values of the domain</li>
 *          <li><strong>tickFormat:</strong> Add axis label formatting.</li>
 *          <li><strong>tickValues:</strong> Define the axis tick values. Array of values.</li>
 *          <li><strong>tickDigits:</strong> Convert axis tick to exponential form if equal or greater than number of digits</li>
 *          <li><strong>tickLabelMax:</strong> Maximum number of tick labels to show for a categorical axis.</li>
 *          <li><strong>tickHoverText:</strong>: Adds hover text for axis labels.</li>
 *          <li><strong>tickCls:</strong> Add class to axis label.</li>
 *          <li><strong>tickRectCls:</strong> Add class to mouse area rectangle around axis label.</li>
 *          <li><strong>tickRectHeightOffset:</strong> Set axis mouse area rect width. Offset beyond label text width.</li>
 *          <li><strong>tickRectWidthOffset:</strong> Set axis mouse area rect height. Offset beyond label text height.</li>
 *          <li><strong>tickClick:</strong> Handler for axis label click. Binds to mouse area rect around label.</li>
 *          <li><strong>tickMouseOver:</strong> Handler for axis label mouse over. Binds to mouse area rect around label.</li>
 *          <li><strong>tickMouseOut:</strong> Handler for axis label mouse out. Binds to mouse area rect around label.</li>
 *      </ul>
 * @param {Object} [config.labels] (Optional) An object with the following properties: main, subtitle, x, y (or yLeft), yRight.
 *      Each property can have a {String} value, {Boolean} lookClickable, {Object} listeners, and other properties listed below.
 *      The value is the text that will appear on the label, lookClickable toggles if the label will appear clickable, and the
 *      listeners property allows the user to specify listeners on the labels such as click, hover, etc, as well as the functions to
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
 *              <strong>visibility:</strong> The initial visibility state for the label. Defaults to normal.
 *          </li>
 *          <li>
 *              <strong>cls:</strong> Class added to label element.
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
 *      left: 75px. The right side may have a margin of 150px if a legend is needed. Custom define margin size for a
 *      legend that exceeds 150px.
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
 * @param {Boolean} [config.legendNoWrap] (Optional) True to force legend text in a single line.
 *      Defaults to false.
 * @param {String} [config.bgColor] (Optional) The string representation of the background color. Defaults to white.
 * @param {String} [config.gridColor] (Optional) The string representation of the grid color. Defaults to white.
 * @param {String} [config.gridLineColor] (Optional) The string representation of the line colors used as part of the grid.
 *      Defaults to grey (#dddddd).
 * @param {Boolean} [config.clipRect] (Optional) Used to toggle the use of a clipRect, which prevents values that appear
 *      outside of the specified grid area from being visible. Use of clipRect can negatively affect performance, do not
 *      use if there is a large amount of elements on the grid. Defaults to false.
 * @param {String} [config.fontFamily] (Optional) Font-family to use for plot text (labels, legend, etc.).
 * @param {Boolean} [config.throwErrors] (Optional) Used to toggle between the plot throwing errors or displaying errors.
 *      If true the plot will throw an error instead of displaying an error when necessary and possible. Defaults to
 *      false.
 *
 * @param {Boolean} [config.requireYLogGutter] (Optional) Used to indicate that the plot has non-positive data on x dimension
 *      that should be displayed in y log gutter in log scale.
 * @param {Boolean} [config.requireXLogGutter] (Optional) Used to indicate that the plot has non-positive data on y dimension
 *      that should be displayed in x log gutter in log scale.
 * @param {Boolean} [config.isMainPlot] (Optional) Used in combination with requireYLogGutter and requireXLogGutter to
 *      shift the main plot's axis position in order to show log gutters.
 * @param {Boolean} [config.isShowYAxis] (Optional) Used to draw the Y axis to separate positive and negative values
 *      for log scale plot in the undefined X gutter plot.
 * @param {Boolean} [config.isShowXAxis] (Optional) Used to draw the X axis to separate positive and negative values
 *      for log scale plot in the undefined Y gutter plot.
 * @param {Float} [config.minXPositiveValue] (Optional) Used to adjust domains with non-positive lower bound and generate x axis
 *      log scale wrapper for plots that contain <= 0 x value.
 * @param {Float} [config.minYPositiveValue] (Optional) Used to adjust domains with non-positive lower bound and generate y axis
 *      log scale wrapper for plots that contain <= 0 y value.
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
    var initMargins = function(userMargins, legendPos, allAes, scales, labels){
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

        if(typeof userMargins.top === 'undefined'){
            margins.top = top + (labels && labels.subtitle ? 20 : 0);
        } else {
            margins.top = userMargins.top;
        }
        if(typeof userMargins.right === 'undefined'){
            margins.right = right;
        } else {
            margins.right = userMargins.right;
        }
        if(typeof userMargins.bottom === 'undefined'){
            margins.bottom = bottom;
        } else {
            margins.bottom = userMargins.bottom;
        }
        if(typeof userMargins.left === 'undefined'){
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
                newScale.sortFn = origScale.sortFn ? origScale.sortFn : null;
                newScale.trans = origScale.trans ? origScale.trans : 'linear';
                newScale.tickValues = origScale.tickValues ? origScale.tickValues : null;
                newScale.tickFormat = origScale.tickFormat ? origScale.tickFormat : null;
                newScale.tickDigits = origScale.tickDigits ? origScale.tickDigits : null;
                newScale.tickLabelMax = origScale.tickLabelMax ? origScale.tickLabelMax : null;
                newScale.tickHoverText = origScale.tickHoverText ? origScale.tickHoverText : null;
                newScale.tickCls = origScale.tickCls ? origScale.tickCls : null;
                newScale.tickRectCls = origScale.tickRectCls ? origScale.tickRectCls : null;
                newScale.tickRectHeightOffset = origScale.tickRectHeightOffset ? origScale.tickRectHeightOffset : null;
                newScale.tickRectWidthOffset = origScale.tickRectWidthOffset ? origScale.tickRectWidthOffset : null;
                newScale.domain = origScale.domain ? origScale.domain : null;
                newScale.range = origScale.range ? origScale.range : null;
                newScale.fontSize = origScale.fontSize ? origScale.fontSize : null;
                newScale.colorType = origScale.colorType ? origScale.colorType : null;

                newScale.tickClick = origScale.tickClick ? origScale.tickClick : null;
                newScale.tickMouseOver = origScale.tickMouseOver ? origScale.tickMouseOver : null;
                newScale.tickMouseOut = origScale.tickMouseOut ? origScale.tickMouseOut : null;

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
                    if(aesthetic === 'x' || aesthetic === 'xTop' || aesthetic === 'xSub' || aesthetic === 'yLeft'
                            || aesthetic === 'yRight' || aesthetic === 'size'){
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
        // If any axis is discrete we need to know the domain before rendering so we render the grid correctly.
        var domain = [], uniques = {}, i, value;

        for (i = 0; i < data.length; i++) {
            value = acc(data[i]);
            if (value != undefined)
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
            // use *2 and /2 so that we won't end up with <= 0 value for log scale
            if (userScale && userScale.trans && userScale.trans === 'log') {
                max = max * 2;
                min = min / 2;
            }
            else {
                max = max + 1;
                min = min - 1;
            }
        }

        // Keep time charts from getting in a bad state.
        // They currently rely on us rendering an empty grid when there is an invalid x-axis.
        if (isNaN(min) && isNaN(max)) {
            return [0,0];
        }

        return [min, max];
    };

    var getDomain = function(aesName, userScale, scale, data, acc, errorAes) {
        var tempDomain, curDomain = scale.domain, domain = [];

        if (scale.scaleType == 'discrete') {
            tempDomain = getDiscreteAxisDomain(data, acc);

            if (scale.sortFn) {
                tempDomain.sort(scale.sortFn);
            }

            if (!curDomain) {
                return tempDomain;
            }

            for (var i = 0; i < tempDomain.length; i++) {
                if (curDomain.indexOf(tempDomain[i]) == -1) {
                    curDomain.push(tempDomain[i]);
                }
            }

            if (scale.sortFn) {
                curDomain.sort(scale.sortFn);
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
        if (name == 'yLeft' || name == 'yRight' || name == 'x' || name == 'xTop' || name == 'xSub' || name == 'size') {
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

    var getDefaultRange = function(scaleName, scale, userScale) {
        if (scaleName == 'color' && scale.scaleType == 'continuous') {
            return ['#222222', '#EEEEEE'];
        }

        if (scaleName == 'color' && scale.scaleType == 'discrete') {
            var colorType = (userScale ? userScale.colorType : null) || scale.colorType;
            if (LABKEY.vis.Scale[colorType])
                return LABKEY.vis.Scale[colorType]();
            else
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

        var setXAxisRange = function(scale) {
            if (scale.scaleType == 'continuous') {
                scale.range = [margins.left, grid.width - margins.right];
            }
            else {
                // We don't need extra padding in the discrete case because we use rangeBands which take care of that.
                scale.range = [grid.leftEdge, grid.rightEdge];
            }
        };

        if (scales.x) {
            setXAxisRange(scales.x);
        }

        if (scales.xTop) {
            setXAxisRange(scales.xTop);
        }

        if (scales.xSub) {
            setXAxisRange(scales.xSub);
        }
    };

    var getLogScale = function (domain, range, minPositiveValue) {
        var scale, scaleWrapper, increment = 0;

        // Issue 24727: adjust domain range to compensate for log scale fitting error margin
        // With log scale, log transformation is applied before the mapping (fitting) to result range
        // Javascript has binary floating points calculation issues. Use a small error constant to compensate.
        var scaleRoundingEpsilon = 0.0001 * 0.5; // divide by half so that <= 0 value can be distinguashed from > 0 value

        if (minPositiveValue) {
            scaleRoundingEpsilon = minPositiveValue * getLogDomainLowerBoundRatio(domain, range, minPositiveValue);
        }

        // domain must not include or cross zero
        if (domain[0] <= scaleRoundingEpsilon) {
            // Issue 24967: incrementing domain is causing issue with brushing extent
            // Ideally we'd increment as little as possible
            increment = scaleRoundingEpsilon - domain[0];
            domain[0] = domain[0] + increment;
            domain[1] = domain[1] + increment;
        }
        else {
            domain[0] = domain[0] - scaleRoundingEpsilon;
            domain[1] = domain[1] + scaleRoundingEpsilon;
        }

        scale = d3.scale.log().domain(domain).range(range);

        scaleWrapper = function(val) {
            if(val != null) {
                if (increment > 0 && val <= scaleRoundingEpsilon) {
                    // <= 0 points are now part of the main plot, it's illegal to pass negative value to a log scale with positive domain.
                    // Since we don't care about the relative values of those gutter data, we can use domain's lower bound for all <=0 as a mock
                    return scale(scaleRoundingEpsilon);
                }
                // use the original value to calculate the scaled value for all > 0 data
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
                // skip rounding down if rounds down to 0, which is not allowed for log
                return [Math.ceil(scale.domain()[0]), Math.abs(scale.domain()[1]) < 1 ? scale.domain()[1] : Math.floor(scale.domain()[1])];
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

    // The lower domain bound need to adjusted to so that enough space is reserved for log gutter.
    // The calculation takes into account the available plot size (range), max and min values (domain) in the plot.
    var getLogDomainLowerBoundRatio = function(domain, range, minPositiveValue) {
        // use 0.5 as a base ratio, so that plot distributions on edge grids are not skewed
        var ratio = 0.5, logGutterSize = 30;
        var gridNum = Math.ceil(Math.log(domain[1] / minPositiveValue)); // the number of axis ticks, equals order of magnitude diff of positive domain range
        var rangeOrder = Math.floor(Math.abs(range[1] - range[0]) / logGutterSize) + 1; // calculate max allowed grid number, assuming each grid is at least log gutter size

        if (gridNum > rangeOrder) {
            for (var i = 0; i < gridNum - rangeOrder; i++) {
                ratio *= 0.5;
            }
        }
        else{
            var gridSize = Math.abs(range[1] - range[0]) / gridNum; // the actual grid size of each grid

            // adjust ratio so that positive data won't fall into log gutter area
            if (gridSize/2 < logGutterSize){
                ratio = 1 - logGutterSize/gridSize;
            }
        }
        return ratio;
    };

    var instantiateScales = function(plot, margins) {
        var userScales = plot.originalScales, scales = plot.scales, grid = plot.grid, isMainPlot = plot.isMainPlot,
            xLogGutter = plot.xLogGutter, yLogGutter = plot.yLogGutter, minXPositiveValue = plot.minXPositiveValue, minYPositiveValue = plot.minYPositiveValue;

        var scaleName, scale, userScale;

        calculateAxisScaleRanges(scales, grid, margins);

        if (isMainPlot) {
            // adjust the plot range to reserve space for log gutter
            var mainPlotRangeAdjustment = 30;
            if (xLogGutter) {
                if (scales.yLeft && Ext.isArray(scales.yLeft.range)) {
                    scales.yLeft.range = [scales.yLeft.range[0] + mainPlotRangeAdjustment, scales.yLeft.range[1]];
                }
            }
            if (yLogGutter) {
                if (scales.x && Ext.isArray(scales.x.range)) {
                    scales.x.range = [scales.x.range[0] - mainPlotRangeAdjustment, scales.x.range[1]];
                }
            }
        }

        for (scaleName in scales) {
            if (scales.hasOwnProperty(scaleName)) {
                scale = scales[scaleName];
                userScale = userScales[scaleName];

                if (scale.scaleType == 'discrete') {
                    if (scaleName == 'x' || scaleName == 'xTop' || scaleName == 'xSub' || scaleName == 'yLeft' || scaleName == 'yRight'){
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
                            scale.range = getDefaultRange(scaleName, scale, userScale);
                        }

                        if (scale.scale.range) {
                            scale.scale.range(scale.range);
                        }
                    }
                } else {
                    if ((scaleName == 'color' || scaleName == 'size') && !scale.range) {
                        scale.range = getDefaultRange(scaleName, scale, userScale);
                    }

                    if (scale.range && scale.domain && LABKEY.vis.isValid(scale.domain[0]) && LABKEY.vis.isValid(scale.domain[1])) {
                        if (scale.trans == 'linear') {
                            scale.scale = d3.scale.linear().domain(scale.domain).range(scale.range);
                        } else {
                            if (scaleName == 'x' || scaleName == 'xTop') {
                                scale.scale = getLogScale(scale.domain, scale.range, minXPositiveValue);
                            }
                            else {
                                scale.scale = getLogScale(scale.domain, scale.range, minYPositiveValue);
                            }
                        }
                    }
                }
            }
        }
    };

    var initializeScales = function(plot, allAes, allData, margins, errorFn) {
        var userScales = plot.originalScales, scales = plot.scales;

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
        instantiateScales(plot, margins);

        if ((scales.x && !scales.x.scale) || (scales.xTop && !scales.xTop.scale)) {
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
            this.yLogGutter = config.requireYLogGutter ? true : false;
            this.xLogGutter = config.requireXLogGutter ? true : false;
            this.isMainPlot = config.isMainPlot ? true : false;
            this.isShowYAxisGutter = config.isShowYAxis ? true : false;
            this.isShowXAxisGutter = config.isShowXAxis ? true : false;
            this.minXPositiveValue = config.minXPositiveValue;
            this.minYPositiveValue = config.minYPositiveValue;

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
        this.legendNoWrap = config.legendNoWrap;
        this.throwErrors = config.throwErrors || false; // Allows the configuration to specify whether chart errors should be thrown or logged (default).
        this.brushing = ('brushing' in config && config.brushing != null && config.brushing != undefined) ? config.brushing : null;
        this.legendData = config.legendData ? config.legendData : null; // An array of rows for the legend text/color/etc. Optional.
        this.disableAxis = config.disableAxis ? config.disableAxis : {xTop: false, xBottom: false, yLeft: false, yRight: false};
        this.bgColor = config.bgColor ? config.bgColor : null;
        this.gridColor = config.gridColor ? config.gridColor : null;
        this.gridLineColor = config.gridLineColor ? config.gridLineColor : null;
        this.gridLinesVisible = config.gridLinesVisible ? config.gridLinesVisible : null;
        this.fontFamily = config.fontFamily ? config.fontFamily : null;
        this.tickColor = config.tickColor ? config.tickColor : null;
        this.borderColor = config.borderColor ? config.borderColor : null;
        this.tickTextColor = config.tickTextColor ? config.tickTextColor : null;
        this.tickLength = config.hasOwnProperty('tickLength') ? config.tickLength : null;
        this.tickWidth = config.hasOwnProperty('tickWidth') ? config.tickWidth : null;
        this.tickOverlapRotation = config.hasOwnProperty('tickOverlapRotation') ? config.tickOverlapRotation : null;
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
            margins = initMargins(userMargins, this.legendPos, allAes, this.scales, this.labels);
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

            if(!initializeScales(this, allAes, allData, margins, errorFn)){  // Sets up the scales.
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

        this.getHeight = function(){
            return this.grid.height;
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

        this.getWidth = function(){
            return this.grid.width;
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
         * Replaces an existing layer with a new layer. Does not render the plot. Returns the new layer.
         * @param {@link LABKEY.vis.Layer} oldLayer
         * @param {@link LABKEY.vis.Layer} newLayer
         */
        this.replaceLayer = function(oldLayer, newLayer){
            var index = this.layers.indexOf(oldLayer);
            if (index == -1) {
                this.layers.push(newLayer);
            }
            else {
                this.layers.splice(index, 1, newLayer);
            }
            return this.layers;
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
            margins = initMargins(userMargins, this.legendPos, allAes, this.scales, this.labels);

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

        /**
         * Sets and updates the legend with new legend data.
         * @param (Array) Legend data
         */
        this.setLegend = function(newLegend){
            this.renderer.setLegendData(newLegend);
            this.renderer.renderLegend();
        };

        this.setBrushing = function(configBrushing) {
            this.renderer.setBrushing(configBrushing);
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
        };

        this.bindBrushing = function(otherPlots) {
            this.renderer.bindBrushing(otherPlots);
        };

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
        showCumulativeTotals: true,
        showValues: true
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
        if (!config.aes) {
            config.aes = {};
        }
        if (config.xAes) { //backwards compatibility
            config.aes.x = config.xAes;
        }
        if (!config.aes.x) {
            throw new Error("Unable to create bar plot, x Aesthetic not specified");
        }
        if (config.xSubAes) {
            config.aes.xSub = config.xSubAes;
        }
        if (config.yAes) {
            config.aes.y = config.yAes;
        }
        if (!config.options) {
            config.options = {};
        }
        if (!config.aggregateType) {
            config.options.aggregateType = config.aes.y ? 'SUM' : 'COUNT'; //aggregate defaults
        }

        var showCumulativeTotals = config.options && config.options.showCumulativeTotals;
        if (showCumulativeTotals && config.aes.xSub) {
            throw new Error("Unable to render grouped bar chart with cumulative totals shown");
        }

        var aggregateData,
                dimName = config.aes.x,
                subDimName = config.aes.xSub,
                aggType = config.options.aggregateType,
                measureName = config.aes.y,
                includeTotal = config.options.showCumulativeTotals;

        aggregateData = LABKEY.vis.getAggregateData(config.data, dimName, subDimName, measureName, aggType, '[blank]', includeTotal);
        config.aes.y = 'value';
        config.aes.x = 'label';
        if (subDimName) {
            config.aes.xSub = 'subLabel';
            config.aes.color = 'label';
        }

        config.layers = [new LABKEY.vis.Layer({
            geom: new LABKEY.vis.Geom.BarPlot(config.options),
            data: aggregateData,
            aes: config.aes
        })];

        if (!config.scales) {
            config.scales = {};
        }
        if (!config.scales.x) {
            config.scales.x = { scaleType: 'discrete' };
        }
        if (subDimName && !config.scales.xSub) {
            config.scales.xSub = { scaleType: 'discrete' };
        }
        if (!config.scales.y) {
            var domainMax = aggregateData.length == 0 ? 1 : null;
            if (showCumulativeTotals)
                domainMax = aggregateData[aggregateData.length-1].total;

            config.scales.y = {
                scaleType: 'continuous',
                domain: [0, domainMax]
            };
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
            config.data = {content : config.data, sortOrder: 'value-desc'};
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

        // apply default font/colors/etc., it not explicitly set
        if (!config.header) config.header = {};
        if (!config.header.title) config.header.title = {};
        if (!config.header.title.font) config.header.title.font = 'Roboto, arial';
        if (!config.header.title.hasOwnProperty('fontSize')) config.header.title.fontSize = 18;
        if (!config.header.title.color) config.header.title.color = '#000000';
        if (!config.header.subtitle) config.header.subtitle = {};
        if (!config.header.subtitle.font) config.header.subtitle.font = 'Roboto, arial';
        if (!config.header.subtitle.hasOwnProperty('fontSize')) config.header.subtitle.fontSize = 16;
        if (!config.header.subtitle.color) config.header.subtitle.color = '#555555';
        if (!config.footer) config.footer = {};
        if (!config.footer.font) config.footer.font = 'Roboto, arial';
        if (!config.labels) config.labels = {};
        if (!config.labels.mainLabel) config.labels.mainLabel = {};
        if (!config.labels.mainLabel.font) config.labels.mainLabel.font = 'Roboto, arial';
        if (!config.labels.percentage) config.labels.percentage = {};
        if (!config.labels.percentage.font) config.labels.percentage.font = 'Roboto, arial';
        if (!config.labels.percentage.color) config.labels.percentage.color = '#DDDDDD';
        if (!config.labels.outer) config.labels.outer = {};
        if (!config.labels.outer.hasOwnProperty('pieDistance')) config.labels.outer.pieDistance = 10;
        if (!config.labels.inner) config.labels.inner = {};
        if (!config.labels.inner.format) config.labels.inner.format = 'percentage';
        if (!config.labels.inner.hasOwnProperty('hideWhenLessThanPercentage')) config.labels.inner.hideWhenLessThanPercentage = 10;
        if (!config.labels.lines) config.labels.lines = {};
        if (!config.labels.lines.style) config.labels.lines.style = 'straight';
        if (!config.labels.lines.color) config.labels.lines.color = '#222222';
        if (!config.misc) config.misc = {};
        if (!config.misc.colors) config.misc.colors = {};
        if (!config.misc.colors.segments) config.misc.colors.segments = LABKEY.vis.Scale.ColorDiscrete();
        if (!config.misc.colors.segmentStroke) config.misc.colors.segmentStroke = '#222222';
        if (!config.misc.gradient) config.misc.gradient = {};
        if (!config.misc.gradient.enabled) config.misc.gradient.enabled = false;
        if (!config.misc.gradient.hasOwnProperty('percentage')) config.misc.gradient.percentage = 95;
        if (!config.misc.gradient.color) config.misc.gradient.color = "#000000";
        if (!config.effects) config.effects = {};
        if (!config.effects.pullOutSegmentOnClick) config.effects.pullOutSegmentOnClick = {};
        if (!config.effects.pullOutSegmentOnClick.effect) config.effects.pullOutSegmentOnClick.effect = 'none';
        if (!config.tooltips) config.tooltips = {};
        if (!config.tooltips.type) config.tooltips.type = 'placeholder';
        if (!config.tooltips.string) config.tooltips.string = '{label}: {percentage}%';
        if (!config.tooltips.styles) config.tooltips.styles = {backgroundOpacity: 1};

        return new d3pie(config.renderTo, config);
    };
})();

/**
 * @name LABKEY.vis.TrendingLinePlot
 * @class TrendingLinePlot Wrapper to create a plot which shows data points compared to expected ranges
 *                          For LeveyJennings, the range is +/- 3 standard deviations from a mean.
 *                          For MovingRange, the range is [0, 3.268*mean(mR)].
 *                          For CUSUM, the range is [0, +5].
 * @description This helper will take the input data and generate a sequencial x-axis so that all data points are the same distance apart.
 * @param {Object} config An object that contains the following properties
 * @param {String} [config.renderTo] The id of the div/span to insert the svg element into.
 * @param {String} [config.qcPlotType] Specifies the plot type to be one of "LeveyJennings", "CUSUM", "MovingRange". Defaults to "LeveyJennings".
 * @param {Number} [config.width] The chart canvas width in pixels.
 * @param {Number} [config.height] The chart canvas height in pixels.
 * @param {Array} [config.data] The array of chart segment data.
 *                          For LeveyJennings and MovingRange, each object is of the form: { label: "label", value: 123 }.
 *                          For CUSUM, each object is of the form: { label: "label", value: 123, negative: true}.
 * @param {Number} [config.data.value]
 *                          For LeveyJennings, it's the raw value.
 *                          For MovingRange, the calculated rM value, not the raw value.
 *                          For CUSUM, the calculated CUSUM value, not the raw value.
 * @param {String} [config.data.negative] CUSUM plot only. True for CUSUM-, false for CUSUM+. Default false;
 * @param {Object} [config.properties] An object that contains the properties specific to the Levey-Jennings plot
 * @param {String} [config.properties.value] The data property name for the value to be plotted on the left y-axis.
 *                          Used by LeveyJennings.
 * @param {String} [config.properties.valueRight] The data property name for the value to be plotted on the right y-axis.
 *                          Used by LeveyJennings.
 * @param {String} [config.properties.mean] The data property name for the mean of the expected range.
 *                          Used by LeveyJennings.
 * @param {String} [config.properties.stdDev] The data property name for the standard deviation of the expected range.
 *                          Used by LeveyJennings only.
 * @param {String} [config.properties.valueMR] The data property name for the moving range value to be plotted on the left y-axis.
 *                          Used by MovingRange.
 * @param {String} [config.properties.valueRightMR] The data property name for the moving range to be plotted on the right y-axis.
 *                          Used by MovingRange.
 * @param {String} [config.properties.meanMR] The data property name for the mean of the moving range.
 *                          Used MovingRange.
 * @param {String} [config.properties.positiveValue] The data property name for the value to be plotted on the left y-axis for CUSUM+.
 *                          Used by CUSUM only.
 * @param {String} [config.properties.positiveValueRight] The data property name for the value to be plotted on the right y-axis for CUSUM+.
 *                          Used by CUSUM only.
 * @param {String} [config.properties.negativeValue] The data property name for the value to be plotted on the left y-axis for CUSUM-.
 *                          Used by CUSUM only.
 * @param {String} [config.properties.negativeValueRight] The data property name for the value to be plotted on the right y-axis for CUSUM-.
 *                          Used by CUSUM only.
 * @param {String} [config.properties.xTickLabel] The data property name for the x-axis tick label.
 * @param {Number} [config.properties.xTickTagIndex] (Optional) The index/value of the x-axis label to be tagged (i.e. class="xticktag").
 * @param {Boolean} [config.properties.showTrendLine] (Optional) Whether or not to show a line connecting the data points. Default false.
 * @param {Boolean} [config.properties.showDataPoints] (Optional) Whether or not to show the individual data points. Default true.
 * @param {Boolean} [config.properties.disableRangeDisplay] (Optional) Whether or not to show the control ranges in the plot. Defaults to false.
 *                          For LeveyJennings, the range is +/- 3 standard deviations from a mean.
 *                          For MovingRange, the range is [0, 3.268*mean(mR)].
 *                          For CUSUM, the range is [0, +5].
 * @param {String} [config.properties.xTick] (Optional) The data property to use for unique x-axis tick marks. Defaults to sequence from 1:data length.
 * @param {String} [config.properties.yAxisScale] (Optional) Whether the y-axis should be plotted with linear or log scale. Default linear.
 * @param {Array} [config.properties.yAxisDomain] (Optional) Y-axis min/max values. Example: [0,20].
 * @param {String} [config.properties.color] (Optional) The data property name for the color to be used for the data point.
 * @param {Array} [config.properties.colorRange] (Optional) The array of color values to use for the data points.
 * @param {Function} [config.properties.pointOpacityFn] (Optional) A function to be called with the point data to
 *                  return an opacity value for that point.
 * @param {String} [config.groupBy] (optional) The data property name used to group plot lines and points.
 * @param {Function} [config.properties.hoverTextFn] (Optional) The hover text to display for each data point. The parameter
 *                  to that function will be a row of data with access to all values for that row.
 * @param {Function} [config.properties.mouseOverFn] (Optional) The function to call on data point mouse over. The parameters to
 *                  that function will be the click event, the point data, the selection layer, and the DOM element for the point itself.
 * @param {Object} [config.properties.mouseOverFnScope] (Optional) The scope to use for the call to mouseOverFn.
 * @param {Function} [config.properties.pointClickFn] (Optional) The function to call on data point click. The parameters to
 *                  that function will be the click event and the row of data for the selected point.
 */
(function(){
    LABKEY.vis.TrendingLinePlotType = {
        LeveyJennings : 'Levey-Jennings',
        CUSUM : 'CUSUM',
        MovingRange: 'MovingRange'
    };

    LABKEY.vis.TrendingLinePlot = function(config){
        if (!config.qcPlotType)
            config.qcPlotType = LABKEY.vis.TrendingLinePlotType.LeveyJennings;
        var plotTypeLabel = LABKEY.vis.TrendingLinePlotType[config.qcPlotType];

        if(config.renderTo == null) {
            throw new Error("Unable to create " + plotTypeLabel + " plot, renderTo not specified");
        }

        if(config.data == null) {
            throw new Error("Unable to create " + plotTypeLabel + " plot, data array not specified");
        }

        if (config.properties == null || config.properties.xTickLabel == null) {
            throw new Error("Unable to create " + plotTypeLabel + " plot, properties object not specified. ");
        }

        if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.LeveyJennings) {
            if (config.properties.value == null) {
                throw new Error("Unable to create " + plotTypeLabel + " plot, value object not specified. "
                        + "Required: value, xTickLabel. Optional: mean, stdDev, color, colorRange, hoverTextFn, mouseOverFn, "
                        + "pointClickFn, showTrendLine, showDataPoints, disableRangeDisplay, xTick, yAxisScale, yAxisDomain, xTickTagIndex.");
            }
        }
        else if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.CUSUM) {
            if (config.properties.positiveValue == null || config.properties.negativeValue == null) {
                throw new Error("Unable to create " + plotTypeLabel + " plot."
                        + "Required: positiveValue, negativeValue, xTickLabel. Optional: positiveValueRight, negativeValueRight, "
                        + "xTickTagIndex, showTrendLine, showDataPoints, disableRangeDisplay, xTick, yAxisScale, color, colorRange.");
            }
        }
        else if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.MovingRange) {
            if (config.properties.valueMR == null) {
                throw new Error("Unable to create " + plotTypeLabel + " plot, value object not specified. "
                        + "Required: value, xTickLabel. Optional: meanMR, color, colorRange, hoverTextFn, mouseOverFn, "
                        + "pointClickFn, showTrendLine, showDataPoints, disableRangeDisplay, xTick, yAxisScale, yAxisDomain, xTickTagIndex.");
            }
        }
        else {
            throw new Error(plotTypeLabel + " plot type is not supported!");
        }

        // get a sorted array of the unique x-axis labels
        var uniqueXAxisKeys = {}, uniqueXAxisLabels = [];
        for (var i = 0; i < config.data.length; i++) {
            if (!uniqueXAxisKeys[config.data[i][config.properties.xTick]]) {
                uniqueXAxisKeys[config.data[i][config.properties.xTick]] = true;
            }
        }
        uniqueXAxisLabels =  Object.keys(uniqueXAxisKeys).sort();

        // create a sequencial index to use for the x-axis value and keep a map from that index to the tick label
        // also, pull out the meanStdDev data for the unique x-axis values and calculate average values for the (LJ) trend line data
        var tickLabelMap = {}, index = -1, distinctColorValues = [], meanStdDevData = [],
            groupedTrendlineData = [], groupedTrendlineSeriesData = {},
            hasYRightMetric = config.properties.valueRight || config.properties.positiveValueRight || config.properties.valueRightMR;

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
                var addValueToTrendLineData = function(dataArr, seqValue, arrKey, fieldName, rowValue, sumField, countField)
                {
                    if (dataArr[arrKey] == undefined)
                    {
                        dataArr[arrKey] = {
                            seqValue: seqValue
                        };
                    }

                    if (dataArr[arrKey][sumField] == undefined)
                    {
                        dataArr[arrKey][sumField] = 0;
                    }
                    if (dataArr[arrKey][countField] == undefined)
                    {
                        dataArr[arrKey][countField] = 0;
                    }

                    if (rowValue != undefined)
                    {
                        dataArr[arrKey][sumField] += rowValue;
                        dataArr[arrKey][countField]++;
                        dataArr[arrKey][fieldName] = dataArr[arrKey][sumField] / dataArr[arrKey][countField];
                    }
                };

                var addAllValuesToTrendLineData = function(dataArr, seqValue, arrKey, row, hasYRightMetric)
                {
                    var plotValueName = config.properties.value, plotValueNameRight = config.properties.valueRight;
                    var plotValueNamePositive = config.properties.positiveValue, plotValueNameRightPositive = config.properties.positiveValueRight;
                    if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.MovingRange)
                    {
                        plotValueName = config.properties.valueMR;
                        plotValueNameRight = config.properties.valueRightMR;
                    }
                    else if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.CUSUM)
                    {
                        plotValueName = config.properties.negativeValue;
                        plotValueNameRight = config.properties.negativeValueRight;
                    }

                    addValueToTrendLineData(dataArr, seqValue, arrKey, plotValueName, row[plotValueName], 'sum1', 'count1');
                    if (hasYRightMetric)
                    {
                        addValueToTrendLineData(dataArr, seqValue, arrKey, plotValueNameRight, row[plotValueNameRight], 'sum2', 'count2');
                    }

                    if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.CUSUM)
                    {
                        addValueToTrendLineData(dataArr, seqValue, arrKey, plotValueNamePositive, row[plotValueNamePositive], 'sum3', 'count3');
                        if (hasYRightMetric)
                        {
                            addValueToTrendLineData(dataArr, seqValue, arrKey, plotValueNameRightPositive, row[plotValueNameRightPositive], 'sum4', 'count4');
                        }
                    }
                };

                index = uniqueXAxisLabels.indexOf(row[config.properties.xTick]);

                // calculate average values for the trend line data (used when grouping x by unique value)
                addAllValuesToTrendLineData(groupedTrendlineData, index, index, row, hasYRightMetric);

                // calculate average values for trend line data for each series (used when grouping x by unique value with a groupBy series property)
                if (config.properties.groupBy && row[config.properties.groupBy]) {
                    var series = row[config.properties.groupBy];
                    var key = series + '|' + index;

                    addAllValuesToTrendLineData(groupedTrendlineSeriesData, index, key, row, hasYRightMetric);

                    groupedTrendlineSeriesData[key][config.properties.groupBy] = series;
                }
            }
            else {
                index++;
            }

            tickLabelMap[index] = row[config.properties.xTickLabel];
            row.seqValue = index;

            if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.LeveyJennings)
            {
                if (config.properties.mean && config.properties.stdDev && !meanStdDevData[index])
                {
                    meanStdDevData[index] = row;
                }
            }
        }

        // min x-axis tick length is 10 by default
        var maxSeqValue = config.data.length > 0 ? config.data[config.data.length - 1].seqValue + 1 : 0;
        for (var i = maxSeqValue; i < 10; i++)
        {
            var temp = {type: 'empty', seqValue: i};
            temp[config.properties.xTickLabel] = "";
            if (config.properties.color && config.data[0]) {
                temp[config.properties.color] = config.data[0][config.properties.color];
            }
            config.data.push(temp);
        }

        // we only need the color aes if there is > 1 distinct value in the color variable
        if (distinctColorValues.length < 2 && config.properties.groupBy == undefined) {
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

        if (hasYRightMetric)
        {
            config.scales.yRight = {
                scaleType: 'continuous',
                domain: config.properties.yAxisDomain,
                trans: config.properties.yAxisScale || 'linear',
                tickFormat: function(val) {
                    return LABKEY.vis.isValid(val) && (val > 100000 || val < -100000) ? val.toExponential() : val;
                }
            };
        }

        // Issue 23626: map line/point color based on legend data
        if (config.legendData && config.properties.color && !config.properties.colorRange)
        {
            var legendColorMap = {};
            for (var i = 0; i < config.legendData.length; i++)
            {
                if (config.legendData[i].name)
                {
                    legendColorMap[config.legendData[i].name] = config.legendData[i].color;
                }
            }

            if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.CUSUM)
            {
                config.scales.color = {
                    scale: function(group) {
                        var normalizedGroup = group.replace('CUSUMmN', 'CUSUMm').replace('CUSUMmP', 'CUSUMm');
                        normalizedGroup = normalizedGroup.replace('CUSUMvN', 'CUSUMv').replace('CUSUMvP', 'CUSUMv');
                        return legendColorMap[normalizedGroup];
                    }
                };
            }
            else
            {
                config.scales.color = {
                    scale: function(group) {
                        return legendColorMap[group];
                    }
                };
            }
        }

        if(!config.margins) {
            config.margins = {};
        }

        if(!config.margins.top) {
            config.margins.top = config.labels && config.labels.main ? 30 : 10;
        }

        if(!config.margins.right) {
            config.margins.right = (config.properties.color || (config.legendData && config.legendData.length > 0) ? 190 : 40)
                                    + (hasYRightMetric ? 45 : 0);
        }

        if(!config.margins.bottom) {
            config.margins.bottom = config.labels && config.labels.x ? 75 : 55;
        }

        if(!config.margins.left) {
            config.margins.left = config.labels && config.labels.y ? 75 : 55;
        }

        config.aes = {
            x: 'seqValue'
        };

        // determine the width the error bars
        if (config.properties.disableRangeDisplay) {
            config.layers = [];
        }
        else {
            var barWidth = Math.max(config.width / config.data[config.data.length-1].seqValue / 5, 3);

            if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.LeveyJennings) {

                // +/- 3 standard deviation displayed using the ErrorBar geom with different colors
                var stdDev3Layer = new LABKEY.vis.Layer({
                    geom: new LABKEY.vis.Geom.ErrorBar({size: 1, color: 'red', dashed: true, altColor: 'darkgrey', width: barWidth}),
                    data: meanStdDevData,
                    aes: {
                        error: function(row){return row[config.properties.stdDev] * 3;},
                        yLeft: config.properties.mean
                    }
                });
                var stdDev2Layer = new LABKEY.vis.Layer({
                    geom: new LABKEY.vis.Geom.ErrorBar({size: 1, color: 'blue', dashed: true, altColor: 'darkgrey', width: barWidth}),
                    data: meanStdDevData,
                    aes: {
                        error: function(row){return row[config.properties.stdDev] * 2;},
                        yLeft: config.properties.mean
                    }
                });
                var stdDev1Layer = new LABKEY.vis.Layer({
                    geom: new LABKEY.vis.Geom.ErrorBar({size: 1, color: 'green', dashed: true, altColor: 'darkgrey', width: barWidth}),
                    data: meanStdDevData,
                    aes: {
                        error: function(row){return row[config.properties.stdDev];},
                        yLeft: config.properties.mean
                    }
                });
                var meanLayer = new LABKEY.vis.Layer({
                    geom: new LABKEY.vis.Geom.ErrorBar({size: 1, color: 'darkgrey', width: barWidth}),
                    data: meanStdDevData,
                    aes: {
                        error: function(row){return 0;},
                        yLeft: config.properties.mean
                    }
                });
                config.layers = [stdDev3Layer, stdDev2Layer, stdDev1Layer, meanLayer];
            }
            else if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.CUSUM) {
                var range = new LABKEY.vis.Layer({
                    geom: new LABKEY.vis.Geom.ControlRange({size: 1, color: 'red', dashed: true, altColor: 'darkgrey', width: barWidth}),
                    data: config.data,
                    aes: {
                        upper: function(){return LABKEY.vis.Stat.CUSUM_CONTROL_LIMIT;},
                        lower: function(){return LABKEY.vis.Stat.CUSUM_CONTROL_LIMIT_LOWER;},
                        yLeft: config.properties.mean
                    }
                });
                config.layers = [range];
            }
            else if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.MovingRange) {
                var range = new LABKEY.vis.Layer({
                    geom: new LABKEY.vis.Geom.ControlRange({size: 1, color: 'red', dashed: true, altColor: 'darkgrey', width: barWidth}),
                    data: config.data,
                    aes: {
                        upper: function(row){return row[config.properties.meanMR] * LABKEY.vis.Stat.MOVING_RANGE_UPPER_LIMIT_WEIGHT;},
                        lower: function(){return LABKEY.vis.Stat.MOVING_RANGE_LOWER_LIMIT;},
                        yLeft: config.properties.mean
                    }
                });
                config.layers = [range];
            }
        }

        if (config.properties.showTrendLine)
        {
            var getPathLayerConfig = function(ySide, valueName, colorValue, negativeCusum)
            {
                var pathLayerConfig = {
                    geom: new LABKEY.vis.Geom.Path({
                        opacity: .6,
                        size: 2,
                        dashed: config.qcPlotType == LABKEY.vis.TrendingLinePlotType.CUSUM && !negativeCusum
                    }),
                    aes: {}
                };

                pathLayerConfig.aes[ySide] = valueName;

                // if we aren't showing multiple series data via the group by, use the groupedTrendlineData for the path
                if (config.properties.groupBy)
                {
                    // convert the groupedTrendlineSeriesData object into an array of the object values
                    var seriesDataArr = [];
                    for(var i in groupedTrendlineSeriesData) {
                        if (groupedTrendlineSeriesData.hasOwnProperty(i)) {
                            var d = { seqValue: groupedTrendlineSeriesData[i].seqValue };
                            d[config.properties.groupBy] = groupedTrendlineSeriesData[i][config.properties.groupBy] + (hasYRightMetric ? '|' + valueName : '');
                            d[valueName] = groupedTrendlineSeriesData[i][valueName];
                            seriesDataArr.push(d);
                        }
                    }
                    pathLayerConfig.data = seriesDataArr;

                    pathLayerConfig.aes.pathColor = config.properties.groupBy;
                    pathLayerConfig.aes.group = config.properties.groupBy;
                }
                else
                {
                    pathLayerConfig.data = groupedTrendlineData;

                    if (colorValue != undefined)
                    {
                        pathLayerConfig.aes.pathColor = function(data) {
                            return colorValue;
                        }
                    }
                }

                return pathLayerConfig;
            };

            if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.CUSUM)
            {
                if (hasYRightMetric)
                {
                    config.layers.push(new LABKEY.vis.Layer(getPathLayerConfig('yLeft', config.properties.negativeValue, 1, true)));
                    config.layers.push(new LABKEY.vis.Layer(getPathLayerConfig('yRight', config.properties.negativeValueRight, 0, true)));
                    config.layers.push(new LABKEY.vis.Layer(getPathLayerConfig('yLeft', config.properties.positiveValue, 1, false)));
                    config.layers.push(new LABKEY.vis.Layer(getPathLayerConfig('yRight', config.properties.positiveValueRight, 0, false)));
                }
                else
                {
                    config.layers.push(new LABKEY.vis.Layer(getPathLayerConfig('yLeft', config.properties.negativeValue, undefined, true)));
                    config.layers.push(new LABKEY.vis.Layer(getPathLayerConfig('yLeft', config.properties.positiveValue, undefined, false)));
                }
            }
            else if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.MovingRange)
            {
                if (hasYRightMetric)
                {
                    config.layers.push(new LABKEY.vis.Layer(getPathLayerConfig('yLeft', config.properties.valueMR, 0)));
                    config.layers.push(new LABKEY.vis.Layer(getPathLayerConfig('yRight', config.properties.valueRightMR, 1)));
                }
                else
                {
                    config.layers.push(new LABKEY.vis.Layer(getPathLayerConfig('yLeft', config.properties.valueMR)));
                }
            }
            else
            {
                if (hasYRightMetric)
                {
                    config.layers.push(new LABKEY.vis.Layer(getPathLayerConfig('yLeft', config.properties.value, 0)));
                    config.layers.push(new LABKEY.vis.Layer(getPathLayerConfig('yRight', config.properties.valueRight, 1)));
                }
                else
                {
                    config.layers.push(new LABKEY.vis.Layer(getPathLayerConfig('yLeft', config.properties.value)));
                }
            }
        }

        // points based on the data value, color and hover text can be added via params to config
        var getPointLayerConfig = function(ySide, valueName, colorValue)
        {
            var pointLayerConfig = {
                geom: new LABKEY.vis.Geom.Point({
                    position: config.properties.position,
                    opacity: config.properties.pointOpacityFn,
                    size: 3
                }),
                aes: {}
            };

            pointLayerConfig.aes[ySide] = valueName;

            if (config.properties.color) {
                pointLayerConfig.aes.color = function(row) {
                    return row[config.properties.color] + (hasYRightMetric ? '|' + valueName : '');
                };
            }
            else if (colorValue != undefined) {
                pointLayerConfig.aes.color = function(row){ return colorValue; };
            }

            if (config.properties.shape) {
                pointLayerConfig.aes.shape = config.properties.shape;
            }
            if (config.properties.hoverTextFn) {
                pointLayerConfig.aes.hoverText = function(row) {
                    return config.properties.hoverTextFn.call(this, row, valueName);
                };
            }
            if (config.properties.pointClickFn) {
                pointLayerConfig.aes.pointClickFn = config.properties.pointClickFn;
            }

            // add some mouse over effects to highlight selected point
            pointLayerConfig.aes.mouseOverFn = function(event, pointData, layerSel, point) {
                d3.select(event.srcElement).transition().duration(800).attr("stroke-width", 5).ease("elastic");

                if (config.properties.mouseOverFn) {
                    config.properties.mouseOverFn.call(config.properties.mouseOverFnScope || this, event, pointData, layerSel, point, valueName);
                }
            };
            pointLayerConfig.aes.mouseOutFn = function(event, pointData, layerSel) {
                d3.select(event.srcElement).transition().duration(800).attr("stroke-width", 1).ease("elastic");
            };

            if (config.properties.pointIdAttr) {
                pointLayerConfig.aes.pointIdAttr = config.properties.pointIdAttr;
            }

            return pointLayerConfig;
        };

        if (config.properties.showDataPoints == undefined) {
            config.properties.showDataPoints = true;
        }

        if (config.properties.showDataPoints) {
            if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.CUSUM) {
                if (hasYRightMetric) {
                    config.layers.push(new LABKEY.vis.Layer(getPointLayerConfig('yLeft', config.properties.negativeValue, 1)));
                    config.layers.push(new LABKEY.vis.Layer(getPointLayerConfig('yRight', config.properties.negativeValueRight, 0)));
                    config.layers.push(new LABKEY.vis.Layer(getPointLayerConfig('yLeft', config.properties.positiveValue, 1)));
                    config.layers.push(new LABKEY.vis.Layer(getPointLayerConfig('yRight', config.properties.positiveValueRight, 0)));

                }
                else {
                    config.layers.push(new LABKEY.vis.Layer(getPointLayerConfig('yLeft', config.properties.negativeValue)));
                    config.layers.push(new LABKEY.vis.Layer(getPointLayerConfig('yLeft', config.properties.positiveValue)));
                }
            }
            else if (config.qcPlotType == LABKEY.vis.TrendingLinePlotType.MovingRange) {
                if (hasYRightMetric) {
                    config.layers.push(new LABKEY.vis.Layer(getPointLayerConfig('yLeft', config.properties.valueMR, 0)));
                    config.layers.push(new LABKEY.vis.Layer(getPointLayerConfig('yRight', config.properties.valueRightMR, 1)));
                }
                else {
                    config.layers.push(new LABKEY.vis.Layer(getPointLayerConfig('yLeft', config.properties.valueMR)));
                }
            }
            else {
                if (hasYRightMetric) {
                    config.layers.push(new LABKEY.vis.Layer(getPointLayerConfig('yLeft', config.properties.value, 0)));
                    config.layers.push(new LABKEY.vis.Layer(getPointLayerConfig('yRight', config.properties.valueRight, 1)));
                }
                else {
                    config.layers.push(new LABKEY.vis.Layer(getPointLayerConfig('yLeft', config.properties.value)));
                }
            }
        }

        return new LABKEY.vis.Plot(config);
    };

    LABKEY.vis.TrendingLineShape = {
        positiveCUSUM: function(){
            return "M3,-0.5L6,-0.5 6,0.5 3,0.5Z M-3,-0.5L0,-0.5 0,0.5 -3,0.5Z M-9,-0.5L-6,-0.5 -6,0.5 -9,0.5Z";
        },
        negativeCUSUM: function(){
            return "M-9,-0.5L6,-0.5 6,0.5 -9,0.5Z";
        }
    };

    /**
     * @ Deprecated
     */
    LABKEY.vis.LeveyJenningsPlot = LABKEY.vis.TrendingLinePlot;
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
        var points = layerSel.selectAll('.point path');
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
        layerSel.selectAll('.point path').attr('fill-opacity', 1).attr('stroke-opacity', 1);
        d3.selectAll('path.line').attr('fill-opacity', 1).attr('stroke-opacity', 1);
    };
})();

/**
 * @name LABKEY.vis.TimelinePlot
 * @class TimelinePlot Wrapper to create a plot which shows timeline events with event types on the y-axis and days/time on the x-axis.
 * @param {Object} config An object that contains the following properties
 * @param {String} [config.renderTo] The id of the div/span to insert the svg element into.
 * @param {Number} [config.width] The chart canvas width in pixels.
 * @param {Number} [config.height] The chart canvas height in pixels.
 * @param {Array} [config.data] The array of event data including event types and subtypes for the plot.
 * @param {String} [config.gridLinesVisible] Possible options are 'y', 'x', and 'both' to determine which sets of
 *                  grid lines are rendered on the plot. Default is 'both'.
 * @param {Object} [config.disableAxis] Object specifying whether to disable rendering of any Axes on the plot. Default: {xTop: false, xBottom: false, yLeft: false, yRight: false}
 * @param {Date} [config.options.startDate] (Optional) The start date to use to calculate number of days until event date.
 * @param {Object} [config.options] (Optional) Display options as defined in {@link LABKEY.vis.Geom.TimelinePlot}.
 * @param {Boolean} [config.options.isCollapsed] (Optional) If true, the timeline collapses subtypes into their parent rows. Defaults to True.
 * @param {Number} [config.options.rowHeight] (Optional) The height of individual rows in pixels. For expanded timelines,
 *                  row height will resize to 75% of this value. Defaults to 1.
 * @param {Object} [config.options.highlight] (Optional) Special Data object containing information to highlight a specific
 *                  row in the timeline. Must have the same shape & properties as all other input data.
 * @param {String} [config.options.highlightRowColor] (Optional) Hex color to specify what color the highlighted row will
 *                  be if, found in the data. Defaults to #74B0C4.
 * @param {String} [config.options.activeEventKey] (Optional) Name of property that is paired with
 *                  @param config.options.activeEventIdentifier to identify a unique event in the data.
 * @param {String} [config.options.activeEventIdentifier] (Optional) Name of value that is paired with
 *                  @param config.options.activeEventKey to identify a unique event in the data.
 * @param {String} [config.options.activeEventStrokeColor] (Optional) Hex color to specify what color the active event
 *                  rect's stroke will be, if found in the data. Defaults to Red.
 * @param {Object} [config.options.emphasisEvents] (Optional) Object containing key:[value] pairs whose keys are property
 *                  names of a data object and whose value is an array of possible values that should have a highlight
*                   line drawn on the chart when found. Example: {'type': ['death', 'Withdrawal']}
 * @param {String} [config.options.tickColor] (Optional) Hex color to specify the color of Axis ticks. Defaults to #DDDDDD.
 * @param {String} [config.options.emphasisTickColor] (Optional) Hex color to specify the color of emphasis event ticks,
 *                  if found in the data. Defaults to #1a969d.
 * @param {String} [config.options.timeUnit] (Optional) Unit of time to use when calculating how far an event's date
 *                  is from the start date. Default is years. Valid string values include minutes, hours, days, years, and decades.
 * @param {Number} [config.options.eventIconSize] (Optional) Size of event square width/height dimensions.
 * @param {String} [config.options.eventIconColor] (Optional) Hex color of event square stroke. Defaults to black (#0000000).
 * @param {String} [config.options.eventIconFill] (Optional) Hex color of event square inner fill. Defaults to black (#000000)..
 * @param {Float} [config.options.eventIconOpacity] (Optional) Float between 0 - 1 (inclusive) to specify how transparent the
 *                  fill of event icons will be. Defaults to 1.
 * @param {Array} [config.options.rowColorDomain] (Optional) Array of length 2 containing string Hex values for the two
 *                  alternating colors of timeline row rectangles. Defaults to ['#f2f2f2', '#ffffff'].
 */
(function(){

    LABKEY.vis.TimelinePlot = function(config)
    {
        if (config.renderTo == undefined || config.renderTo == null) { throw new Error("Unable to create timeline plot, renderTo not specified."); }

        if (config.data == undefined || config.data == null) { throw new Error("Unable to create timeline plot, data array not specified."); }

        if (config.width == undefined || config.width == null) { throw new Error("Unable to create timeline plot, width not specified."); }

        if (!config.aes.y) {
            config.aes.y = 'key';
        }

        if (!config.options) {
            config.options = {};
        }

        //default x scale is in years
        if (!config.options.timeUnit) {
            config.options.timeUnit = 'years';
        }

        //set default left margin to make room for event label text
        if (!config.margins.left) {
            config.margins.left = 200
        }

        //default row height value
        if (!config.options.rowHeight) {
            config.options.rowHeight = 40;
        }

        //override default plot values if not set
        if (!config.margins.top) {
            config.margins.top = 40;
        }
        if (!config.margins.bottom) {
            config.margins.bottom = 50;
        }
        if (!config.gridLineWidth) {
            config.gridLineWidth = 1;
        }
        if (!config.gridColor) {
            config.gridColor = '#FFFFFF';
        }
        if (!config.borderColor) {
            config.borderColor = '#DDDDDD';
        }
        if (!config.tickColor) {
            config.tickColor = '#DDDDDD';
        }

        config.rendererType = 'd3';
        config.options.marginLeft = config.margins.left;
        config.options.parentName = config.aes.parentName;
        config.options.childName = config.aes.childName;
        config.options.dateKey = config.aes.x;

        config.scales = {
            x: {
                scaleType: 'continuous'
            },
            yLeft: {
                scaleType: 'discrete'
            }
        };

        var millis;
        switch(config.options.timeUnit.toLowerCase())
        {
            case 'minutes':
                millis = 1000 * 60;
                break;
            case 'hours':
                millis = 1000 * 60 * 60;
                break;
            case 'days':
                millis = 1000 * 60 * 60 * 24;
                break;
            case 'months':
                millis = 1000 * 60 * 60 * 24 * 30.42;
                break;
            case 'years':
                millis = 1000 * 60 * 60 * 24 * 365;
                break;
            case 'decades':
                millis = 1000 * 60 * 60 * 24 * 365 * 10;
                break;
            default:
                millis = 1000;
        }

        //find the earliest occurring date in the data if startDate is not already specified
        var min = config.options.startDate ? config.options.startDate : null;
        if (min == null)
        {
            for (var i = 0; i < config.data.length; i++)
            {
                config.data[i][config.aes.x] = new Date(config.data[i][config.aes.x]);
                if (min == null)
                {
                    min = config.data[i][config.aes.x];
                }
                min = config.data[i][config.aes.x] < min ? config.data[i][config.aes.x] : min;
            }
        }

        //Loop through the data and do calculations for each entry
        var max = 0;
        var parents = new Set();
        var children = new Set();
        var types = new Set();
        var domain = [];
        for (var j = 0; j < config.data.length; j++)
        {
            //calculate difference in time units
            var d = config.data[j];
            d[config.aes.x] = config.options.startDate ? new Date(d[config.aes.x]) : d[config.aes.x];
            var timeDifference = (d[config.aes.x] - min) / millis;
            d[config.options.timeUnit] = timeDifference;

            //update unique counts
            parents.add(d[config.aes.parentName]);
            children.add(d[config.aes.childName]);

            //update domain
            if (!config.options.isCollapsed) {
                var str;
                if (d[config.aes.parentName] != null && d[config.aes.parentName] != 'null' && d[config.aes.parentName] != undefined) {
                    str = d[config.aes.parentName];
                    if (!types.has(str) && str != undefined) {
                        domain.push(str);
                        types.add(str);
                    }
                    d.typeSubtype = str;
                }
                if (d[config.aes.childName] != null && d[config.aes.childName] != 'null' && d[config.aes.childName] != undefined) {
                    str += '-' + d[config.aes.childName];
                }
                if (!types.has(str) && str != undefined) {
                    domain.push(str);
                    types.add(str);
                }

                //typeSubtype will be a simple unique identifier for this type & subtype of event
                d.typeSubtype = str;
            } else {
                if (!types.has(d[config.aes.parentName])) {
                    domain.push(d[config.aes.parentName]);
                    types.add(d[config.aes.parentName]);
                }
            }

            //update max value
            max = timeDifference > max ? timeDifference : max;
        }
        var numParentChildUniques = parents.size + children.size;
        if (children.has(null)) {
            numParentChildUniques--;
        }
        var numParentUniques = parents.size;
        domain.sort().reverse();

        //For a better looking title
        function capitalizeFirstLetter(string) {
            return string.charAt(0).toUpperCase() + string.slice(1);
        }

        //Update x label to include the start date for better context
        config.labels.x = {value: capitalizeFirstLetter(config.options.timeUnit) + " Since " + min.toDateString()};

        if (!config.options.isCollapsed) {
            config.aes.typeSubtype = "typeSubtype";

            config.scales.yLeft.domain = domain;
            var chartHeightMultiplier = numParentChildUniques !== numParentUniques ? Math.floor(config.options.rowHeight * .75) : config.options.rowHeight;
            config.height = (chartHeightMultiplier * numParentChildUniques) + config.margins.top + config.margins.bottom;
            config.options.rowHeight = (config.height - (config.margins.top + config.margins.bottom)) / numParentChildUniques;
            if (numParentChildUniques < 10) {
                //small visual adjustment for short charts without many data points
                config.options.rowHeight = config.options.rowHeight - (12 - numParentChildUniques);
            }
        } else {
            config.scales.yLeft.domain = domain;
            config.height = (config.options.rowHeight * numParentUniques) + config.margins.top + config.margins.bottom;

            config.options.rowHeight = (config.height - (config.margins.top + config.margins.bottom)) / numParentUniques;
            if (numParentUniques < 10) {
                config.options.rowHeight = config.options.rowHeight - (12 - numParentUniques);
            }
        }

        config.scales.x.domain = [0, Math.ceil(max)];
        config.aes.x = config.options.timeUnit;
        config.layers = [
            new LABKEY.vis.Layer({
                geom: new LABKEY.vis.Geom.TimelinePlot(config.options)
            })
        ];

        return new LABKEY.vis.Plot(config);
    };
})();

