/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var CD4PointLayer = new LABKEY.vis.Layer({
	geom: new LABKEY.vis.Geom.Point({size: 5}),
	name: 'CD4+ (cells/mm3)',
	aes: {
		y: function(row){return row.study_LabResults_CD4.value},
		hoverText: function(row){return row.study_LabResults_ParticipantId.value + ' CD4, Day ' + row.Days.value + ", " + row.study_LabResults_CD4.value;}
	}
});

var CD4PathLayer = new LABKEY.vis.Layer({
	geom: new LABKEY.vis.Geom.Path({size: 3, opacity: .4}),
	name: 'CD4+ (cells/mm3)',
	aes: {
		y: function(row){return row.study_LabResults_CD4.value}
	}
});

var hemoglobinPointLayer = new LABKEY.vis.Layer({
	geom: new LABKEY.vis.Geom.Point(),
	name: 'Hemoglobin',
	aes: {
		yRight: function(row){return row.study_LabResults_Hemoglobin.value},
		hoverText: function(row){return row.study_LabResults_ParticipantId.value + ' Hemoglobin, day ' + row.Days.value + ', ' + row.study_LabResults_Hemoglobin.value;}
	}
});

var hemoglobinPathLayer = new LABKEY.vis.Layer({
	geom: new LABKEY.vis.Geom.Path({opacity: .4}),
	name: 'Hemoglobin',
	aes: {
		yRight: function(row){return row.study_LabResults_Hemoglobin.value}
	}
});

var labResultsPlotConfig = {
    rendererType: 'd3',
	renderTo: 'chart',
    labels: {
        x: {value: "Days Since Start Date"},
        y: {value: "CD4+ (cells/mm3)"},
        yRight: {value: "Hemoglobin"},
        main: {value: "Lab Results"}
    },
    width: 900,
	height: 300,
    clipRect: true,
	data: labResultsRows,
	aes: {
		x: function(row){return row.Days.value},
		color: function(row){return row.study_LabResults_ParticipantId.value},
		pathColor: function(rows){return rows[0].study_LabResults_ParticipantId.value},
		group: function(row){return row.study_LabResults_ParticipantId.value},
        shape: function(row){return row.study_LabResults_ParticipantId.value}
	},
    scales: {
        x: {
            scaleType: 'continuous',
			trans: 'linear'
        },
        y: {
            scaleType: 'continuous',
			trans: 'linear',
            domain: [400,1000]
        },
        yRight: {
            domain: [null, null]
        },
        shape: {
            scaleType: 'discrete'
        }
    }
};

var labResultsPlot = new LABKEY.vis.Plot(labResultsPlotConfig);
labResultsPlot.addLayer(CD4PathLayer);
labResultsPlot.addLayer(CD4PointLayer);
labResultsPlot.addLayer(hemoglobinPathLayer);
labResultsPlot.addLayer(hemoglobinPointLayer);

var coffeePointLayer = new LABKEY.vis.Layer({
    name: "Efficiency",
    geom: new LABKEY.vis.Geom.Point(),
    aes: {
        color: 'person',
        shape: 'consumedCoffee',
        hoverText: function(row){return 'Person: ' + row.person + "\n" + row.consumedCoffee + " Consumed \nEfficiency: " + row.efficiency}
    }
});

var coffeePathLayer = new LABKEY.vis.Layer({
    name: "Efficiency",
    geom: new LABKEY.vis.Geom.Path({}),
    aes: {
        pathColor: 'person',
        group: 'person'
    }
});

var coffeePlot = new LABKEY.vis.Plot({
    renderTo: 'coffeePlot',
    rendererType: 'd3',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Efficiency'},
        subtitle: {value: '(% over time)', color: '#777777'},
        xTop: {value: 'Time (PST)'},
        yLeft: {value: 'Efficiency (%)'}
    },
    data: coffeeData,
    layers: [coffeePathLayer, coffeePointLayer],
    aes: {
        xTop: 'time',
        yLeft: 'efficiency'
    },
    legendData: [
        {text: 'LabKey Dev 1', color: '#0000A0'},
        {text: 'LabKey Dev 2', color: '#ADD8E6'},
        {text: 'No Coffee Consumed', shape: LABKEY.vis.Scale.Shape()[0]},
        {text: 'Coffee Consumed', shape: LABKEY.vis.Scale.Shape()[1]}
    ],
    scales: {
        color: {
            scaleType: 'discrete',
            scale: function(group) {
                return group == 'LabKey Dev 1 Efficiency' ? '#0000A0' : '#ADD8E6';
            }
        },
        xTop: {
            scaleType: 'discrete'
        },
        yLeft: {
            scaleType: 'continuous',
            trans: 'linear',
            domain: [0, null]
        }
    },
    margins: {
        bottom: 15
    }
});

var boxLayer = new LABKEY.vis.Layer({
    geom: new LABKEY.vis.Geom.Boxplot({
        position: 'jitter',
        outlierOpacity: '1',
        outlierFill: 'red',
        showOutliers: true
    }),
    aes: {
        hoverText: function(x, stats){
            return x + ':\nMin: ' + stats.min + '\nMax: ' + stats.max + '\nQ1: ' + stats.Q1 + '\nQ2: ' + stats.Q2 +
                    '\nQ3: ' + stats.Q3;
        },
        outlierHoverText: function(row){return "Group: " + row.group + ", Age: " + row.age;},
        outlierColor: function(row){return "outlier";},
        outlierShape: function(row){return row.gender;},
        pointClickFn: function(event, data){
            console.log(data);
        }
    }
});

var boxPointLayer = new LABKEY.vis.Layer({
    geom: new LABKEY.vis.Geom.Point({
        position: 'jitter',
        color: 'orange',
        opacity: .6,
        size: 3
    }),
    aes: {
        hoverText: function(row){return row.group + ", Age: " + row.age;}
    }
});

var medianLineLayer = new LABKEY.vis.Layer({
    geom: new LABKEY.vis.Geom.Path({size: 2}),
    aes: {x: 'x', y: 'y', pathColor: 'color', group: 'color'},
    data: medianLineData
});

var boxPlot = new LABKEY.vis.Plot({
    renderTo: 'box',
    rendererType: 'd3',
    clipRect: true,
    width: 900,
    height: 300,
    bgColor: '#dddddd',
    gridLineColor: '#777777',
    labels: {
        main: {value: 'Example Box Plot'},
        yLeft: {value: 'Age'},
        x: {value: 'Race'}
    },
    data: boxPlotData,
    layers: [boxLayer, medianLineLayer],
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

var discreteScatter = new LABKEY.vis.Plot({
    renderTo: 'discreteScatter',
    rendererType: 'd3',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Scatterplot With Jitter'},
        yLeft: {value: 'Age'},
        x: {value: 'Race'}
    },
    data: boxPlotData,
    layers: [new LABKEY.vis.Layer({
        geom: new LABKEY.vis.Geom.Point({
            position: 'jitter',
            color: 'teal',
            size: 3
        })
    })],
    aes: {
        yLeft: 'age',
        x: 'group',
        color: 'group',
        hoverText: function(row) {
            return row.age + '\n' + row.group;
        },
        pointClickFn: function(event, data){
            console.log(data);
        }
    },
    scales: {
        x: {
            scaleType: 'discrete',
            domain: ['Alien / Martian'],
            sortFn: function(a, b) {
                return a.localeCompare(b);
            }
        },
        yLeft: {
//            scaleType: 'discrete',
//            domain: ['40', '50']
            scaleType: 'continuous',
            trans: 'linear'
        }
    },
    margins: {
        bottom: 75
    }
});
var pGeom = new LABKEY.vis.Geom.Point({
    plotNullPoints: true,
    size: 2,
    opacity: .5,
    color: '#FF33FF'
});

var scatterPlot = new LABKEY.vis.Plot({
    renderTo: 'scatter',
    rendererType: 'd3',
    width: 900,
    height: 700,
    clipRect: false,
    labels: {
        main: {
            value:'Scatter With Null Points & Size Scale',
            lookClickable: true,
            listeners: {
                click: function(){console.log("Main Label clicked!")}
            }
        },
        x: {
            value: "X Axis",
            lookClickable: true,
            listeners: {
                click: function(){console.log("Clicking the X Axis!")}
            }
        },
        yRight: {
            value: "y-right",
            lookClickable: true,
            listeners: {
                click: function(){console.log("Clicking the Y-Right Axis!")}
            }
        },
        y: {
            value:"Y Axis",
            lookClickable: true,
            listeners: {
                click: function(){console.log("Clicking the Y-Left Axis!")}
            }
        }
    },
    layers: [new LABKEY.vis.Layer({
        data: scatterData,
        geom: pGeom,
        aes: {
            x:'x',
            y: 'y',
//            yRight: 'y',
            size: 'z'
        }
    })],
    scales: {
        y: {scaleType: 'continuous', trans: 'log'},
        size: {scaleType: 'continuous', trans: 'linear', range: [1, 10]}
    }
});

var colorScatter = new LABKEY.vis.Plot({
    renderTo: 'colorScatter',
    rendererType: 'd3',
    width: 900,
    height: 700,
    clipRect: false,
    labels: {
        main: {
            value:'Scatter With Continuous Color Scale'
        },
        x: {
            value: "X Axis"
        },
        y: {
            value:"Y Axis"
        }
    },
    layers: [new LABKEY.vis.Layer({
        data: scatterData,
        geom: new LABKEY.vis.Geom.Point(),
        aes: {x:'x', y: 'y', color: 'y'}
    })],
    scales: {
        y: {scaleType: 'continuous', trans: 'linear'},
        color: {scaleType: 'continuous', trans: 'linear', range: ['#660000', '#FF6666']}
    }
});

var binScatter = new LABKEY.vis.Plot({
    renderTo: 'binScatter',
    rendererType: 'd3',
    width: 900,
    height: 700,
    clipRect: false,
    labels: {
        main: { value: 'Scatter With Hex Binning' },
        x: { value: "X Axis" },
        y: { value: "Y Axis" }
    },
    layers: [new LABKEY.vis.Layer({
        data: scatterData,
        geom: new LABKEY.vis.Geom.Bin({
            // hex is default 'shape'
            size: 15
        }),
        aes: {x:'x', y: 'y', color: 'y'}
    })],
    scales: {
        y: {scaleType: 'continuous', trans: 'linear'},
        color: {scaleType: 'continuous', trans: 'linear'}
    }
});

var binScatter2 = new LABKEY.vis.Plot({
    renderTo: 'binScatter2',
    rendererType: 'd3',
    width: 900,
    height: 700,
    clipRect: false,
    labels: {
        main: { value: 'Scatter With Square Binning & Color Range' },
        x: { value: "X Axis" },
        y: { value: "Y Axis" }
    },
    layers: [new LABKEY.vis.Layer({
        data: scatterData,
        geom: new LABKEY.vis.Geom.Bin({
            shape: 'square',
            size: 15,
            colorRange: ['white', 'orange']
        }),
        aes: {x:'x', y: 'y', color: 'y'}
    })],
    scales: {
        y: {scaleType: 'continuous', trans: 'linear'},
        color: {scaleType: 'continuous', trans: 'linear'}
    }
});


var selectionMade = false;
var brushScatter = new LABKEY.vis.Plot({
    renderTo: 'brushing',
    rendererType: 'd3',
    width: 900,
    height: 700,
    clipRect: false,
    legendPos: 'none',
    brushing: {
        brushstart: function(event, data, extent, plot, layerSelections) {
            selectionMade = true;
        },
        brush: function(event, data, extent, plot, layerSelections) {
            var points = layerSelections[0].selectAll('.point path');
            var colorScale = plot.scales.color.scale;
            var colorAcc = function(d) {
                var x = d.x, y = d.y;
                d.isSelected = (x > extent[0][0] && x < extent[1][0] && y > extent[0][1] && y < extent[1][1])
                if (d.isSelected) {
                    return '#14C9CC';
                }
                return colorScale(d.ptid);
            };
            var strokeAcc = function(d) {
                if (d.isSelected){
                    return '#00393A';
                } else {
                    return colorScale(d.ptid);
                }
            };
            var strokeWidthAcc = function(d) {
                if (d.isSelected){
                    return .5;
                } else {
                    return 1;
                }
            };
            var opacityAcc = function(d) {
                if (d.isSelected) {
                    return 1;
                } else {
                    return .8;
                }
            };
            points.attr('fill', colorAcc)
                    .attr('stroke', strokeAcc)
                    .attr('stroke-width', strokeWidthAcc)
                    .attr('fill-opacity', opacityAcc);
        },
        brushend: function(event, data, extent, plot, layerSelections) {
        },
        brushclear: function(event, data, plot, layerSelections) {
            layerSelections[0].selectAll('.point path').attr('fill-opacity', 1);
            selectionMade = false;
        }
    },
    labels: {
        main: {
            value:'Scatter With Brushing'
        },
        x: {
            value: "X Axis"
        },
        y: {
            value:"Y Axis"
        }
    },
    layers: [new LABKEY.vis.Layer({
        data: scatterData,
        geom: new LABKEY.vis.Geom.Point({}),
        aes: {
            x:'x',
            y: 'y',
            color: 'ptid',
            shape: 'ptid',
            mouseOverFn: function(event, pointData, layerSel) {
                if (selectionMade) {return;}
                var points = layerSel.selectAll('.point path');
                var colorScale = brushScatter.scales.color.scale;
                var strokeWidthAcc = function(d) {
                    if (d.ptid == pointData.ptid) {
                        return 2;
                    }
                    return 1;
                };
                var strokeColorAcc = function(d) {
                    if (d.ptid == pointData.ptid) {
                        return '#00EAFF';
                    }
                    return colorScale(d.ptid);
                };
                var fillAcc = function(d) {
                    if (d.ptid == pointData.ptid) {
                        return '#01BFC2'
                    }
                    return colorScale(d.ptid);
                };

                points.attr('fill', fillAcc)
                        .attr('stroke-width', strokeWidthAcc)
                        .attr('stroke', strokeColorAcc);

                points.each(function(d){
                    var node = this.parentNode;
                    if (pointData.ptid === d.ptid) {node.parentNode.appendChild(node);}
                });
            },
            mouseOutFn: function(event, pointData, layerSel) {
                if (selectionMade) {return;}
                var points = layerSel.selectAll('.point path');
                var colorScale = brushScatter.scales.color.scale;

                points.attr('stroke-width', 1)
                        .attr('fill', function(d){return colorScale(d.ptid);})
                        .attr('stroke', function(d){return colorScale(d.ptid);});
            }
        }
    })],
    scales: {
        y: {scaleType: 'continuous', trans: 'linear'/*, domain: [0, 1000]*/},
        color: {
            scaleType: 'discrete',
            range: LABKEY.vis.Scale.DataspaceColor()
        },
        shape: {
            scaleType: 'discrete',
            range: LABKEY.vis.Scale.DataspaceShape()
        }
    }
});

var lineLayerSel = null;
var mouseEventPlot = new LABKEY.vis.Plot({
    rendererType: 'd3',
    renderTo: 'mouseEvents',
    width: 900,
    height: 500,
    legendPos: 'none',
    data: dateData,
    tickColor: '#FF33CC',
    borderColor: '#FF33CC',
    gridLineColor: '#FF99FF',
    tickTextColor: '#33AA61',
    gridLineWidth: 1,
    borderWidth: 3,
    tickWidth: 1,
    tickLength: 10,
    labels: {main: {value: 'Mouse Events Plot'}},
    layers: [new LABKEY.vis.Layer({
        geom: new LABKEY.vis.Geom.Point({
            color: '#FF4499'
        })
    })],
    aes: {
        x: function(row){return row.x},
        y: function(row){return row.y},
        color: function(row){return row.ptid},
        mouseOverFn: function(event, pointData, layerSel){
            var subjectData = [], path,
                    xScale = mouseEventPlot.scales.x.scale,
                    yScale = mouseEventPlot.scales.yLeft.scale,
                    xAcc = function(d){
                        return xScale(mouseEventPlot.aes.x.getValue(d));
                    },
                    yAcc = function(d){
                        return yScale(mouseEventPlot.aes.yLeft.getValue(d));
                    },
                    opacityFn = function(d){
                        return d.ptid == pointData.ptid ? 1 : .4;
                    };

            for (var i = 0; i < dateData.length; i++) {
                if (dateData[i].ptid === pointData.ptid) {
                    subjectData.push(dateData[i]);
                }
            }

            if (!lineLayerSel) {
                lineLayerSel = d3.select('#mouseEvents svg').append('g').attr('class', 'line-layer');
            }

            path = LABKEY.vis.makePath(subjectData, xAcc, yAcc);
            lineLayerSel.append('path').attr('class', 'user-line')
                    .attr('d', path)
                    .attr('stroke', '#666666')
                    .attr('stroke-width', 1)
                    .attr('stroke-opacity',.5)
                    .attr('fill', 'none');

            layerSel.selectAll('.point path').attr('fill-opacity', opacityFn).attr('stroke-opacity', opacityFn);

            layerSel.selectAll('.point path').each(function(d){
                if(d.ptid == pointData.ptid) {
                    var node = this.parentNode;
                    node.parentNode.appendChild(node);
                }
            });

        },
        mouseOutFn: function(event, pointData, layerSel){
            lineLayerSel.selectAll('path').remove();
            layerSel.selectAll('.point path').attr('fill-opacity', 1).attr('stroke-opacity', 1);
        }
    },
    scales: {
        x: {
            tickFormat: function(v){
                var d = new Date(v);
                return d.toDateString();
            }
        },
        y: {domain: [0, 120]}
    }
});

var errorPointLayer = new LABKEY.vis.Layer({
    name: "Weight (kg)",
    geom: new LABKEY.vis.Geom.Point(),
    data: aggregateData.rows,
    aes: {
        color: function(row){return row.GroupId.displayValue;},
        hoverText: function(row){return row.GroupId.displayValue + ' Temperature, day ' + row.study_PhysicalExam_ParticipantVisitsequencenum.value + ', ' + row.study_PhysicalExam_Weight_kg.value;}
    }
});

var errorPathLayer = new LABKEY.vis.Layer({
    name: "Weight (kg)",
    geom: new LABKEY.vis.Geom.Path(),
    data: aggregateData.rows,
    aes: {
        group: function(row){return row.GroupId.displayValue;},
        pathColor: function(rows){return rows[0].GroupId.displayValue;}
    }
});

var errorBarLayer = new LABKEY.vis.Layer({
    name: "Weight (kg)",
    geom: new LABKEY.vis.Geom.ErrorBar(),
    data: aggregateData.rows,
    aes: {
        error: function(row){return row.study_PhysicalExam_Weight_kg_STDDEV.value},
//        error: function(row){return row.study_PhysicalExam_Weight_kg_STDERR.value},
        color: function(row){return row.GroupId.displayValue},
        yLeft: function(row){return row.study_PhysicalExam_Weight_kg.value;}
    }
});

var errorPlotConfig = {
    renderTo: 'errorChart',
    rendererType: 'd3',
    width: 900,
    height: 300,
    clipRect: true,
    labels: {
        main: {value: 'Testing error bar geom'},
        yLeft: {value: 'Temperature (C)'},
        x: {value: 'Sequence Number'}
    },
    data: aggregateData.rows,
    layers: [errorPathLayer, errorBarLayer, errorPointLayer],
    aes: {
        yLeft: function(row){
            if(row.study_PhysicalExam_Weight_kg.value < 40){
                console.log(row.study_PhysicalExam_Weight_kg.value);
            }
            return row.study_PhysicalExam_Weight_kg.value;
        },
        x: function(row){return row.study_PhysicalExam_ParticipantVisitsequencenum.value}
    },
    scales: {
        x: {
            scaleType: 'continuous',
            trans: 'linear',
            tickHoverText: function(value) {
                return "HOVER: " + value;
            },
            //tickValues: [0,1,2,3,4,5,6,7,8,9,10,11,12,13],
            tickValues: [0,4,8,12],
            tickFormat: function(value){
                if(value > 0) {
                    return "Day " + value;
                } else {
                    return "Baseline";
                }
            }
        },
        yLeft: {
            scaleType: 'continuous',
            trans: 'linear'
        },
        color: {
            scaleType: 'discrete'
        }
    }
};
var errorPlot = new LABKEY.vis.Plot(errorPlotConfig);

var statFnPlot = new LABKEY.vis.Plot({
    renderTo: 'statFn',
    rendererType: 'd3',
    width: 900,
    height: 300,
    clipRect: false,
    labels: {
        main: {value: 'Line Plot with LABKEY.vis.Stat.fn'}
    },
    layers: [new LABKEY.vis.Layer({
        geom: new LABKEY.vis.Geom.Path({color: '#8ABEDE'})
    }), new LABKEY.vis.Layer({
        geom: new LABKEY.vis.Geom.Point({color: '#8ABEDE'}),
        aes: {hoverText: function(row){return row.x;}}
    })],
    data: LABKEY.vis.Stat.fn(function(x){return Math.log(x) * 2;}, 20, 1, 15),
    aes: {x: 'x', y: 'y'}
});

var barChart = new LABKEY.vis.Plot({
    renderTo: 'bar',
    rendererType: 'd3',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Example Bar Plot With Path'},
        yLeft: {value: 'Count'}
    },
    layers : [
        new LABKEY.vis.Layer({
            geom: new LABKEY.vis.Geom.BarPlot({})
        }),
        new LABKEY.vis.Layer({
            geom: new LABKEY.vis.Geom.Path({size: 3, color: 'steelblue'}),
            aes: { x: 'name', y: 'count' }
        })
    ],
    aes: { x: 'name', y: 'count' },
    scales : {
        x : { scaleType: 'discrete' },
        y : { domain: [-20, 20] }
    },
    data: [
        {name: 'test1', count: 16}, {name: 'test2', count: 8}, {name: 'test3', count: 4}, {name: 'test4', count: 2}, {name: 'test5', count: 1},
        {name: 'test6', count: -1}, {name: 'test7', count: -2}, {name: 'test8', count: -4}, {name: 'test9', count: -8}, {name: 'test10', count: -16}
    ]
});

var barChart2 = new LABKEY.vis.BarPlot({
    renderTo: 'bar2',
    rendererType: 'd3',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Example Bar Plot With Cumulative Totals'},
        yLeft: {value: 'Count'},
        x: {value: 'Value'}
    },
    options: {
        color: 'red',
        fill: 'orange',
        colorTotal: 'blue',
        fillTotal: 'green',
        lineWidthTotal: 2,
        opacityTotal: .5,
        showCumulativeTotals: true
    },
    xAes: function(row){return row['age']},
    data: barPlotData
});

var barChart3 = new LABKEY.vis.BarPlot({
    renderTo: 'bar3',
    rendererType: 'd3',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Example Bar Plot with Grouped Bars'},
        yLeft: {value: 'Count'}
    },
    aes: {
        x: 'gender',
        xSub: 'group'
    },
    scales : {
        x : {scaleType: 'discrete'},
        xSub : {scaleType: 'discrete'}
    },
    options: {
        color: '#c0c0c0',
        showValues: true
    },
    data: barPlotData
});

var pieChart = new LABKEY.vis.PieChart({
    renderTo: "pie",
    rendererType: 'd3',
    data: pieChartData,
    width: 300,
    height: 250
});

var pieChart2 = new LABKEY.vis.PieChart({
    renderTo: "pie2",
    rendererType: 'd3',
    data: pieChartData,
    width: 300,
    height: 250,
    // d3pie lib config properties
    header: {
        title: {
            text: 'Pie Chart Example'
        },
        subtitle: {
            text: 'using the d3pie lib',
            color: 'gray'
        },
        titleSubtitlePadding: 1
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

var pieChart3 = new LABKEY.vis.PieChart({
    renderTo: "pie3",
    rendererType: 'd3',
    width: 300,
    height: 250,
    // d3pie lib config properties
    header: {
        title: {
            text: 'Center Title'
        },
        location: 'pie-center'
    },
    footer: {
        text: 'Pie chart footer example',
        color: 'steelblue',
        fontSize: 10,
        location: 'bottom-left'
    },
    size: {
        pieInnerRadius: '85%'
    },
    data: {
        content: pieChartData,
        sortOrder: 'value-desc'
    },
    labels: {
        outer: {
            pieDistance: 4
        },
        inner: {
            format: 'none'
        },
        lines: {
            enabled: false
        }
    },
    effects: {
        load: {
            effect: 'none'
        },
        pullOutSegmentOnClick: {
            effect: 'none'
        },
        highlightLuminosity: 0.99
    },
    misc: {
        colors: {
            segments: LABKEY.vis.Scale.ColorDiscrete(),
            segmentStroke: 'black'
        }
    },
    callbacks: {
        onClickSegment: function(info) {
            alert('Pie info: label = ' + info.data.label + ", value = " + info.data.value + ", index = " + info.index);
        }
    }
});

var leveyJenningsPlot = LABKEY.vis.LeveyJenningsPlot({
    renderTo: 'leveyJennings',
    rendererType: 'd3',
    width: 900,
    height: 300,
    data: leveyJenningsData,
    properties: {
        value: 'value',
        mean: 'mean',
        stdDev: 'stddev',
        xTickLabel: 'xaxislabel',
        xTick: 'xaxislabel',
        yAxisDomain: [57500,69500],
        showTrendLine: true,
        color: 'colorlabel',
        colorRange: ['red', 'blue', 'green', 'brown'],
        hoverTextFn: function(row){return 'X-Value: ' + row.xaxislabel + '\nColor: ' + row.colorlabel + '\nY-Value: ' + row.value;}
    },
    gridLineColor: 'white',
    margins : {
        top: 50
    },
    labels: {
        main: {value: 'Example Levey-Jennings Plot'},
        y: {value: 'Value'},
        x: {value: 'Assay'}
    }
});

var CUSUMPlot = LABKEY.vis.TrendingLinePlot({
    renderTo: 'meanCUSUM',
    qcPlotType: LABKEY.vis.TrendingLinePlotType.CUSUM,
    rendererType: 'd3',
    width: 900,
    height: 300,
    data: CUSUMData,
    properties: {
        negativeValue: 'CUSUMmN',
        positiveValue: 'CUSUMmP',
        xTickLabel: 'xaxislabel',
        xTick: 'xaxislabel',
        yAxisDomain: [0, 6],
        showTrendLine: true,
        color: 'colorlabel',
        colorRange: ['red', 'blue', 'green', 'brown'],
        hoverTextFn: function(row, type){
            var tip = 'X-Value: ' + row.xaxislabel + '\nColor: ' + row.colorlabel + '\nY-Value: ';
            if (type == 'CUSUMmN')
            {
                tip += row.CUSUMmN + '\nGroup: CUSUM-';
            }
            else
            {
                tip += row.CUSUMmP + '\nGroup: CUSUM+';
            }
            return tip;
        }
    },
    gridLineColor: 'white',
    margins : {
        top: 50
    },
    labels: {
        main: {value: 'Example Mean CUSUM Plot'},
        y: {value: 'Value'},
        x: {value: 'Sample Date'}
    },
    legendData: [
        {"text":"CUSUM Group","separator":true},
        {"text":"CUSUM-","color":"#000000", shape: function(){
            return "M-9,-0.5L6,-0.5 6,0.5 -9,0.5Z"
        }},
        {"text":"CUSUM+","color":"#000000", shape: function(){
            return " M3,-0.5L6,-0.5 6,0.5 3,0.5Z M-3,-0.5L0,-0.5 0,0.5 -3,0.5Z M-9,-0.5L-6,-0.5 -6,0.5 -9,0.5Z ";
        }}
    ],
    legendNoWrap: true
});

var survivalCurvePlot = LABKEY.vis.SurvivalCurvePlot({
    renderTo: 'survivalCurve',
    rendererType: 'd3',
    width: 900,
    height: 300,
    data: survivalData,
    censorData: survivalCensorData,
    groupBy: 'group',
    aes: {x: 'time', yLeft: 'percent'},
    labels: {
        main: {value: 'Example Survival Curve Plot'},
        yLeft: {value: 'Survival Probability'},
        x: {value: 'Time (Months)'}
    }
});

var timelinePlot = LABKEY.vis.TimelinePlot({
    renderTo: 'timeline',
    rendererType: 'd3',
    width: 900,
    labels: {
        main: {value: 'Example Timeline Report'}
    },
    margins: {
        top: 40,
        left: 200
    },
    aes: {
        x: 'date',
        parentName: 'event',
        childName: 'subevent',
        mouseOverEventIconFn: function(event, data, layerSel) {
            layerSel.selectAll('rect.timeline-event-rect').attr('fill', function(d) {
                d.origFill = this.getAttribute('fill');
                return d == data ? 'yellow' : d.origFill;
            });
        },
        mouseOutEventIconFn: function(event, data, layerSel) {
            layerSel.selectAll('rect.timeline-event-rect').attr('fill', function(d){
                return d.origFill ? d.origFill : this.getAttribute('fill');
            });
        }
    },
    data: timelineData,
    disableAxis: {yLeft: true},
    gridLinesVisible: 'y',
    options: {
        startDate: new Date('2008-06-10'),
        isCollapsed: false,
        rowHeight: 50,
        eventIconSize: 12,
        timeUnit: 'years',
        emphasisEvents: {
            'event': ['Withdrawal']
        },
        emphasisTickColor: '##1a969d',
        eventIconColor: '#6a0fbd',
        eventIconFill: '#6a0fbd'
    }
});

var renderStats = function(){
    var labResultsStats = LABKEY.vis.Stat.summary(labResultsRows, function(row){return row.study_LabResults_CD4.value});
    var statsDiv = document.getElementById('stats');
    statsDiv.innerHTML = '<h3>Lab Results CD4 Statistics:</h3>';
    var p = document.createElement('p');

    statsDiv.appendChild(document.createElement('p').appendChild(document.createTextNode("Minimum: " + labResultsStats.min + ", Maximum: " + labResultsStats.max)));

    p.appendChild(document.createTextNode("Q1: " + labResultsStats.Q1 + ", Q2 (median): " + labResultsStats.Q2 + ", Q3: " + labResultsStats.Q3 + ", IQR: " + labResultsStats.IQR));
    statsDiv.appendChild(p);

    p = document.createElement('p');
    p.appendChild(document.createTextNode("Sorted Values: " + labResultsStats.sortedValues.join(', ')));
    statsDiv.appendChild(p);
};

var start = new Date().getTime();
labResultsPlot.render();
coffeePlot.render();
boxPlot.render();
discreteScatter.render();
scatterPlot.render();
colorScatter.render();
binScatter.render();
binScatter2.render();
brushScatter.render();
mouseEventPlot.render();
errorPlot.render();
statFnPlot.render();
barChart.render();
barChart2.render();
barChart3.render();
leveyJenningsPlot.render();
CUSUMPlot.render();
survivalCurvePlot.render();
timelinePlot.render();
console.log(new Date().getTime() - start);
renderStats();
