/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.vis.GenericChartHelper = new function(){
    var getChartType = function(renderType, xAxisType) {
        if (renderType === "box_plot" || renderType === "scatter_plot") {
            return renderType;
        }

        return (xAxisType === 'string' || xAxisType === 'boolean') ? 'box_plot' : 'scatter_plot';
    };

    var generateLabels = function(labels, measures) {
        return {
            main: {
                value: labels.main ? labels.main : ''
            },
            x: {
                value: labels.x ? labels.x : Ext4.util.Format.htmlEncode(measures.x.label)
            },
            y: {
                value: labels.y ? labels.y : Ext4.util.Format.htmlEncode(measures.y.label)
            }
        };
    };

    var generateScales = function(chartType, measures, savedScales, aes, responseData, defaultFormatFn) {
        var scales = {};
        var data = responseData.rows;
        var fields = responseData.metaData.fields;

        if (chartType === "box_plot") {
            scales.x = {scaleType: 'discrete'}; // Force discrete x-axis scale for box plots.
            var yMin = d3.min(data, aes.y);
            var yMax = d3.max(data, aes.y);
            var yPadding = ((yMax - yMin) * .1);
            if (savedScales.y.trans == "log"){
                // When subtracting padding we have to make sure we still produce valid values for a log scale.
                // log([value less than 0]) = NaN.
                // log(0) = -Infinity.
                if(yMin - yPadding > 0){
                    yMin = yMin - yPadding;
                }
            } else {
                yMin = yMin - yPadding;
            }

            scales.y = {min: yMin, max: yMax + yPadding, scaleType: 'continuous', trans: savedScales.y.trans};
        } else {
            if (measures.x.normalizedType == "float" || measures.x.normalizedType == "int") {
                scales.x = {scaleType: 'continuous', trans: savedScales.x.trans};
            } else {
                scales.x = {scaleType: 'discrete'};
            }

            scales.y = {scaleType: 'continuous', trans: savedScales.y.trans};
        }

        for (var i = 0; i < fields.length; i++) {
            var type = fields[i].displayFieldJsonType ? fields[i].displayFieldJsonType : fields[i].type;

            if (type == 'int' || type == 'float') {
                if (fields[i].name == measures.x.name) {
                    if (fields[i].extFormatFn) {
                        scales.x.tickFormat = eval(fields[i].extFormatFn);
                    } else if (defaultFormatFn) {
                        scales.x.tickFormat = defaultFormatFn;
                    }
                }

                if (fields[i].name == measures.y.name) {
                    if (fields[i].extFormatFn) {
                        scales.y.tickFormat = eval(fields[i].extFormatFn);
                    } else if (defaultFormatFn) {
                        scales.y.tickFormat = defaultFormatFn;
                    }
                }
            }
        }

        return scales;
    };

    var generateAes = function(chartType, measures, schemaName, queryName) {
        var aes = {};

        if (measures.x.normalizedType == "float" || measures.x.normalizedType == "int") {
            aes.x = generateContinuousAcc(measures.x.name);
        } else {
            aes.x = generateDiscreteAcc(measures.x.name, measures.x.label);
        }

        if (measures.y.normalizedType == "float" || measures.y.normalizedType == "int") {
            aes.y = generateContinuousAcc(measures.y.name);
        } else {
            aes.y = generateDiscreteAcc(measures.y.name, measures.y.label);
        }

        if (chartType === "scatter_plot") {
            if (measures.color) {
                aes.color = generateGroupingAcc(measures.color.name);
            }

            if (measures.shape) {
                aes.shape = generateGroupingAcc(measures.shape.name);
            }

            aes.hoverText = generatePointHover(measures);
        } else if (chartType === "box_plot") {
            if (measures.color) {
                aes.outlierColor = generateGroupingAcc(measures.color.name);
            }

            if (measures.shape) {
                aes.outlierShape = generateGroupingAcc(measures.shape.name);
            }

            aes.hoverText = generateBoxplotHover();
            aes.outlierHoverText = generatePointHover(measures);
        }

        if (measures.pointClickFn) {
            aes.pointClickFn = generatePointClickFn(
                    measures,
                    schemaName,
                    queryName,
                    measures.pointClickFn
            );
        }

        return aes;
    };

    var generatePointHover = function(measures){
        return function(row) {
            var hover = measures.x.label + ': ';

            if(row[measures.x.name].displayValue){
                hover = hover + row[measures.x.name].displayValue;
            } else {
                hover = hover + row[measures.x.name].value;
            }


            hover = hover + ', \n' + measures.y.label + ': ' + row[measures.y.name].value;

            if(measures.color){
                hover = hover +  ', \n' + measures.color.label + ': ';
                if(row[measures.color.name]){
                    if(row[measures.color.name].displayValue){
                        hover = hover + row[measures.color.name].displayValue;
                    } else {
                        hover = hover + row[measures.color.name].value;
                    }
                }
            }

            if(measures.shape && !(measures.color && measures.color.name == measures.shape.name)){
                hover = hover +  ', \n' + measures.shape.label + ': ';
                if(row[measures.shape.name]){
                    if(row[measures.shape.name].displayValue){
                        hover = hover + row[measures.shape.name].displayValue;
                    } else {
                        hover = hover + row[measures.shape.name].value;
                    }
                }
            }
            return hover;
        };
    };

    var generateBoxplotHover = function() {
        return function(xValue, stats) {
            return xValue + ':\nMin: ' + stats.min + '\nMax: ' + stats.max + '\nQ1: ' + stats.Q1 + '\nQ2: ' + stats.Q2 +
                    '\nQ3: ' + stats.Q3;
        };
    };

    var generateDiscreteAcc = function(measureName, measureLabel) {
        return function(row){
            var valueObj = row[measureName];
            var value = null;

            if(valueObj){
                value = valueObj.displayValue ? valueObj.displayValue : valueObj.value;
            } else {
                return undefined;
            }

            if(value === null){
                value = "Not in " + measureLabel;
            }

            return value;
        };
    };

    var generateContinuousAcc = function(measureName){
        return function(row){
            var value = null;

            if(row[measureName]){
                value = row[measureName].value;

                if(Math.abs(value) === Infinity){
                    value = null;
                }

                if(value === false || value === true){
                    value = value.toString();
                }

                return value;
            } else {
                return undefined;
            }
        }
    };

    var generateGroupingAcc = function(measureName){
        return function(row) {
            var measureObj = row[measureName];
            var value = null;

            if(measureObj){
                value = measureObj.displayValue ? measureObj.displayValue : measureObj.value;
            }

            if(value === null || value === undefined){
                value = "n/a";
            }

            return value;
        };
    };

    var generatePointClickFn = function(measures, schemaName, queryName, fnString){
        var measureInfo = {
            schemaName: schemaName,
            queryName: queryName,
            xAxis: measures.x.name,
            yAxis: measures.y.name
        };

        if (measures.shape) {
            measureInfo.shapeName = measures.shape.name;
        }

        if (measures.color) {
            measureInfo.pointName = measures.shape.name
        }

        // using new Function is quicker than eval(), even in IE.
        var pointClickFn = new Function('return ' + fnString)();
        return function(clickEvent, data){
            pointClickFn(data, measureInfo, clickEvent);
        };
    };

    var generateGeom = function(chartType, chartOptions) {
        if (chartType == "box_plot") {
            return new LABKEY.vis.Geom.Boxplot({
                lineWidth: chartOptions.lineWidth,
                outlierOpacity: chartOptions.opacity,
                outlierFill: '#' + chartOptions.pointFillColor,
                outlierSize: chartOptions.pointSize,
                color: '#' + chartOptions.lineColor,
                fill: '#' + chartOptions.boxFillColor
            });
        } else if (chartType == "scatter_plot") {
            return new LABKEY.vis.Geom.Point({
                opacity: chartOptions.opacity,
                size: chartOptions.pointSize,
                color: '#' + chartOptions.pointFillColor
            });
        }
    };

    return {
        getChartType: getChartType,
        generateLabels: generateLabels,
        generateScales: generateScales,
        generateAes: generateAes,
        generatePointHover: generatePointHover,
        generateBoxplotHover: generateBoxplotHover,
        generateDiscreteAcc: generateDiscreteAcc,
        generateContinuousAcc: generateContinuousAcc,
        generateGroupingAcc: generateGroupingAcc,
        generatePointClickFn: generatePointClickFn,
        generateGeom: generateGeom
    };
};