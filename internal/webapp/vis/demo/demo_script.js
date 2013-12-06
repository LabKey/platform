/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var CD4PointLayer = new LABKEY.vis.Layer({
	geom: new LABKEY.vis.Geom.Point({size: 5}),
	name: 'Really Long Name That Gets Wrapped',
	aes: {
		y: function(row){return row.study_LabResults_CD4.value},
		hoverText: function(row){return row.study_LabResults_ParticipantId.value + ' CD4, Day ' + row.Days.value + ", " + row.study_LabResults_CD4.value;}
	}
});

var CD4PathLayer = new LABKEY.vis.Layer({
	geom: new LABKEY.vis.Geom.Path({size: 3, opacity: .2}),
	name: 'Really Long Name That Gets Wrapped',
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
	geom: new LABKEY.vis.Geom.Path({opacity: .2}),
	name: 'Hemoglobin',
	aes: {
		yRight: function(row){return row.study_LabResults_Hemoglobin.value}
	}
});

var plotConfig = {
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
//    legendPos: 'none',
//    bgColor: '#777777',
//    gridColor: '#FF00FF',
//    gridLineColor: "#FFFFFF",
	data: labResultsRows,
	aes: {
		x: function(row){return row.Days.value},
		color: function(row){return row.study_LabResults_ParticipantId.value},
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
            min: 400,
            max: 1000
        },
        yRight: {
            min: null,
            max: null
        },
        shape: {
            scaleType: 'discrete'
        }
    }
};

var plot = new LABKEY.vis.Plot(plotConfig);
plot.addLayer(CD4PathLayer);
plot.addLayer(CD4PointLayer);
//plot.addLayer(hemoglobinPathLayer);
//plot.addLayer(hemoglobinPointLayer);

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
        color: function(row){return row.GroupId.displayValue;}
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

var individualPointLayer = new LABKEY.vis.Layer({
    name: "Weight (kg)",
    geom: new LABKEY.vis.Geom.Point(),
    aes: {
        color: function(row){return row.study_PhysicalExam_ParticipantId.value},
        hoverText: function(row){return row.study_PhysicalExam_ParticipantId.value + ' Temperature, day ' + row.study_PhysicalExam_ParticipantVisitsequencenum.value + ', ' + row.study_PhysicalExam_Weight_kg.value;}
    }
});

var individualPathLayer = new LABKEY.vis.Layer({
    name: "Weight (kg)",
    geom: new LABKEY.vis.Geom.Path(),
    aes: {
        color: function(row){return row.study_PhysicalExam_ParticipantId.value},
        group: function(row){return row.study_PhysicalExam_ParticipantId.value}
    }
});

var errorPlotConfig = {
    renderTo: 'errorChart',
    rendererType: 'd3',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Testing error bar geom'},
        yLeft: {value: 'Temperature (C)'},
        x: {value: 'Sequence Number'}
    },
    data: aggregateData.rows,
//    data: individualData.rows,
    layers: [/*individualPathLayer, individualPointLayer,*/ errorPathLayer, errorBarLayer, errorPointLayer],
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
    geom: new LABKEY.vis.Geom.Path({color: '#66c2a5'}),
    aes: {
        color: 'person',
        group: 'person'
    }
});

var coffeePlot = new LABKEY.vis.Plot({
    renderTo: 'coffeePlot',
    rendererType: 'd3',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Efficiency (%) Over Time'},
        x: {value: 'Efficiency (%)'},
        yLeft: {value: 'Time (PST)'}
    },
    data: coffeeData,
    layers: [coffeePathLayer, coffeePointLayer],
    aes: {
        x: 'time',
        yLeft: 'efficiency'
    },
    scales: {
        x: {
            scaleType: 'discrete'
        },
        yLeft: {
            scaleType: 'continuous',
			trans: 'linear',
            min: 0
        }
    }
});

var boxLayer = new LABKEY.vis.Layer({
    geom: new LABKEY.vis.Geom.Boxplot({
        position: 'jitter',
//        color: 'teal',
//        fill: '#FFFF00',
        outlierOpacity: '1',
        outlierFill: 'red',
        showOutliers: true
//        showOutliers: false
//        opacity: '.5'
//        lineWidth: 3
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
    aes: {x: 'x', y: 'y', color: 'color', group: 'color'},
    data: medianLineData
});

var boxPlot = new LABKEY.vis.Plot({
    renderTo: 'box',
    rendererType: 'd3',
    width: 900,
    height: 300,
    labels: {
        main: {value: 'Example Box Plot'},
        yLeft: {value: 'Age'},
        x: {value: 'Groups of People'}
    },
//    data: labResultsRows,
    data: boxPlotData,
    layers: [boxLayer, medianLineLayer/*, boxPointLayer*/],
    aes: {
        yLeft: 'age',
        x: 'group'
//        yLeft: function(row){return row.study_LabResults_CD4.value},
//        x: function(row){return "All Participants"}
//        x: function(row){return row.study_LabResults_ParticipantId.value}
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
        x: {value: 'Groups of People'}
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
            scaleType: 'discrete'
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

var renderStats = function(){
    var labResultsStats = LABKEY.vis.Stat.summary(labResultsRows, function(row){return row.study_LabResults_CD4.value});
    var statsDiv = document.getElementById('stats');
    var p = document.createElement('p');

    statsDiv.appendChild(document.createElement('p').appendChild(document.createTextNode("Minimum: " + labResultsStats.min + ", Maximum: " + labResultsStats.max)));

    p.appendChild(document.createTextNode("Q1: " + labResultsStats.Q1 + ", Q2 (median): " + labResultsStats.Q2 + ", Q3: " + labResultsStats.Q3 + ", IQR: " + labResultsStats.IQR));
    statsDiv.appendChild(p);

    p = document.createElement('p');
    p.appendChild(document.createTextNode("Sorted Values: " + labResultsStats.sortedValues.join(', ')));
    statsDiv.appendChild(p);
};

plot.render();
boxPlot.render();
errorPlot.render();
coffeePlot.render();
discreteScatter.render();
scatterPlot.render();
colorScatter.render();
statFnPlot.render();
renderStats();
